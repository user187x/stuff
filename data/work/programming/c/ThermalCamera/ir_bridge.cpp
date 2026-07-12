#include "thermometry.h"
#include <string.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <linux/videodev2.h>
#include <linux/v4l2-controls.h>
#include <string>

static int fd;
struct buffer {
    void * start;
    unsigned int length;
} *buffers;

float temperatureTable[16384];
float temperatureData[384 * 288 + 10]; 

extern "C" {

    int v4l2_control(int value) {
        struct v4l2_control ctrl;
        ctrl.id = V4L2_CID_ZOOM_ABSOLUTE;
        ctrl.value = value;
        if (ioctl(fd, VIDIOC_S_CTRL, &ctrl) == -1) return 0;
        return 1;
    }

    int init_thermal_camera(const char* devicePath) {
        fd = open(devicePath, O_RDWR);
        if (fd == -1) return -1; // Missing permissions or invalid path

        struct v4l2_format fmt;
        memset(&fmt, 0, sizeof(fmt));
        fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        
        // The original resolution is 384x288; when capturing data, the resolution must set as 384x292[cite: 2].
        fmt.fmt.pix.width = 384;
        fmt.fmt.pix.height = 292; 
        fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;
        fmt.fmt.pix.field = V4L2_FIELD_INTERLACED;
        
        if (ioctl(fd, VIDIOC_S_FMT, &fmt) == -1) return -2;

        struct v4l2_requestbuffers req;
        memset(&req, 0, sizeof(req));
        req.count = 4;
        req.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        req.memory = V4L2_MEMORY_MMAP;

        if (ioctl(fd, VIDIOC_REQBUFS, &req) == -1) return -3;

        buffers = (buffer*)calloc(req.count, sizeof(*buffers));
        for (unsigned int n = 0; n < req.count; ++n) {
            struct v4l2_buffer buf;
            memset(&buf, 0, sizeof(buf));
            buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
            buf.memory = V4L2_MEMORY_MMAP;
            buf.index = n;
            ioctl(fd, VIDIOC_QUERYBUF, &buf);
            buffers[n].length = buf.length;
            buffers[n].start = mmap(NULL, buf.length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, buf.m.offset);
            ioctl(fd, VIDIOC_QBUF, &buf);
        }

        enum v4l2_buf_type type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        if (ioctl(fd, VIDIOC_STREAMON, &type) == -1) return -4;

        // In 0x8005 mode, the output data is in YUYV format[cite: 2].
        if (v4l2_control(0x8005) == 0) return -5; 

        // invoke v4l2_control(0x8000) when sending shutter calibration command 0x8000[cite: 2].
        v4l2_control(0x8000); 

        return 1;
    }

    float get_center_temperature() {
        struct v4l2_buffer buf;
        memset(&buf, 0, sizeof(buf));
        buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buf.memory = V4L2_MEMORY_MMAP;
        
        if (ioctl(fd, VIDIOC_DQBUF, &buf) == -1) return -999.0f;

        unsigned short* orgData = (unsigned short*)buffers[buf.index].start;
        
        // there 4 lines are set for data transferring[cite: 2].
        unsigned short* fourLinePara = orgData + (384 * (292 - 4)); 

        float floatFpaTmp, correction = 0, Refltmp = 26.0f, Airtmp = 26.0f, humi = 0.45f, emiss = 0.98f;
        unsigned short distance = 3;

        // thermometryT4Line uses address of the last 4 lines of parameters[cite: 2].
        thermometryT4Line(384, 292, temperatureTable, fourLinePara, 
                          &floatFpaTmp, &correction, &Refltmp, &Airtmp, &humi, 
                          &emiss, &distance, 130, 0, 120);

        // thermometrySearchXXX function as searching table[cite: 2].
        thermometrySearch(384, 292, temperatureTable, orgData, temperatureData, 120, 5);

        ioctl(fd, VIDIOC_QBUF, &buf);

        // You can obtain Temperature of center[cite: 2].
        return temperatureData[0]; 
    }

    void shutdown_thermal_camera() {
        enum v4l2_buf_type type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        ioctl(fd, VIDIOC_STREAMOFF, &type);
        for(int i = 0; i < 4; i++) munmap(buffers[i].start, buffers[i].length);
        free(buffers);
        close(fd);
    }
}
