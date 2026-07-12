

#include <errno.h>
#include <fcntl.h>
#include <linux/videodev2.h>
#include <linux/v4l2-controls.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <stdbool.h>
#include <math.h>

#include <opencv4/opencv2/opencv.hpp>
#include <opencv4/opencv2/core/core.hpp>
#include <opencv4/opencv2/core/core_c.h>
#include <opencv4/opencv2/highgui.hpp>
#include <opencv4/opencv2/imgproc/imgproc.hpp>
#include <iostream>
#include "thermometry.h"
#include "SimplePictureProcessing.h"

using namespace std;
using namespace cv;
// shit...
/**
 * OUTPUTMODE 4:配合libthermometry.so可以输出全局温度数据。
 *              配合simple.so使用专业级图像算法，可得到优秀画质的图像，但是需要主频1.8ghz。
 *              也可配合代码里的线性图像算法，画质稍低于高性能算法，但是对主频几乎没要求。
 *              输出数据格式：按照yuyv的形式，实际输出每个像素16bit中14bit为灰度数据，最后四行为参数。
 * OUTPUTMODE 4:Combine with libthermometry.so to output all around temperature data.
 *              Obtain high quality image with professional algorithms from simple.so, requires basic frequency at least 1.8ghz.
 *              Linear Algorithms produce lower quality image than professional algorithms but there are almost no requirements in basic frequency.
 *              Output data format: in yuyu form, the actual transferred is 16bit NUC data with 14 bits to store the wide dynamic grayscale value of one pixel.
 *              The last four lines of yuyu data are parameters.
 *
 *
 * OUTPUTMODE 5:配合libthermometry.so，可以直接输出中心点，最高温，最低温和额外指定的三个点的信息，不可输出全帧温度数据。
 *              输出数据格式：输出yuyv格式的图像，最后四行为参数。
 *  OUTPUTMODE 5:With libthermometry.so to directly output temperature of the center, highest, lowest and extra three points. Can't output full frame temperature data.
 *               Output data format: graphs in yuyv format, the last four lines are parameters.
 */
#define OUTPUTMODE 4


#define TRUE 1
#define FALSE 0
#define MAX_BUFFER 2048

#define FILE_VIDEO1 "video"
#define FILE_DEV "/dev"





int IMAGEWIDTH = 384;
int IMAGEHEIGHT = 292;


static int fd;
struct v4l2_streamparm stream_para;
struct v4l2_capability cap;

struct v4l2_fmtdesc fmtdesc;
struct v4l2_format fmt,fmtack;

struct v4l2_requestbuffers req;
struct v4l2_buffer buf;

struct buffer
{
    void * start;
    unsigned int length;
    long long int timestamp;
} *buffers;

struct irBuffer
{
    size_t** midVar;
    unsigned char* destBuffer;
} *irBuffers;

/**
 *temperatureData:最终输出的温度数据，采用10+全帧温度数据格式；例如10+384（宽）×288（高），前10个格式如下
 *     The final output temperature data, in the form of "10 + Full Frame Temperature Data"; such as 10+384(width)×288(height), the top 10 as below
 *temperatureData[0]=centerTmp;
 *temperatureData[1]=(float)maxx1;
 *temperatureData[2]=(float)maxy1;
 *temperatureData[3]=maxTmp;
 *temperatureData[4]=(float)minx1;
 *temperatureData[5]=(float)miny1;
 *temperatureData[6]=minTmp;
 *temperatureData[7]=point1Tmp;
 *temperatureData[8]=point2Tmp;
 *temperatureData[9]=point3Tmp;
 *根据8004或者8005模式来查表，8005模式下仅输出以上注释的10个参数，8004模式下数据以上参数+全局温度数据
 *比如（x，y）点，第x行y列，位置在temperatureData[10+width*x+y],原点为（0，0）
 *Search on the table with 8004 or 8005 mode, 8005 mode only outputs the 10 parameters above, 8004 mode include above parameters with overall temperature data
 *For example, point (x, y), row x, column y, location in temperatureData[10+width*x+y], origin is (0,0)
 *参见：thermometrySearch函数 Refer to function thermometrySearch
 */
float* temperatureData;
/**
 *设置三个单独点 Set three points
 *温度会出现在temperatureData[7]，temperatureData[8]，temperatureData[9] shows temperature
 *0<=viewX1<IMAGEWIDTH
 *0<=viewY1<IMAGEHEIGHT-4
 */
void setPoint(int viewX1,int viewY1,int indexOfPoint);
enum   v4l2_buf_type type;
struct v4l2_control ctrl;

/**
 *temperatureTable:温度映射表
 */
float temperatureTable[16384];

int init_v4l2(string videoX);
int v4l2_grab(void);
int v4l2_control(int);
int traversalVideo(void);


int delayy;
void sendCorrection(float correction);

void sendReflection(float reflection);

void sendAmb(float amb);

void sendHumidity(float humidity);

void sendEmissivity(float emiss);

void sendDistance(unsigned short distance);
void savePara();
int v4l2_release();

int main()
{


    printf("first~~\n");
    if(traversalVideo() == FALSE)
    {
        printf("Init fail~~\n");
        exit(EXIT_FAILURE);
    }


    irBuffers = (irBuffer*)malloc(4 * sizeof(*irBuffers));
    if(!irBuffers)
    {
        printf("Out of memory\n");
        return 0;
    }
    if(OUTPUTMODE==4)
    {
        SimplePictureProcessingInit(IMAGEWIDTH,(IMAGEHEIGHT-4));
        SetParameter(100,0.5f,0.1f,0.1f,1.0f,3.5f);
    }
    unsigned int n_buffers;
    for(n_buffers = 0; n_buffers < 4; n_buffers++)
    {
        if(OUTPUTMODE==4)
        {
            irBuffers[n_buffers].midVar=(size_t**)calloc (7,sizeof(size_t*));
            SimplePictureProcessingInitMidVar(irBuffers[n_buffers].midVar);
        }
        irBuffers[n_buffers].destBuffer=(unsigned char*)calloc(IMAGEWIDTH*(IMAGEHEIGHT-4)*4,sizeof(unsigned char));
    }


    temperatureData=(float*)calloc(IMAGEWIDTH*(IMAGEHEIGHT-4)+10,sizeof(float));


    printf("second~~\n");
    if(v4l2_grab() == FALSE)
    {
        printf("grab fail~~\n");
        exit(EXIT_FAILURE);
    }

    printf("fourth~~\n");
    if(OUTPUTMODE==4)
    {
        if(v4l2_control(0x8004) == FALSE)
        {
            printf("control fail~~\n");
            exit(EXIT_FAILURE);
        }
    }
    else
    {
        if(v4l2_control(0x8005) == FALSE)
        {
            printf("control fail~~\n");
            exit(EXIT_FAILURE);
        }
    }

    delayy=0;
    buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    buf.memory = V4L2_MEMORY_MMAP;
    printf("third~~\n");
    int i = 100;
    double t;
    long long int extra_time = 0;
    long long int cur_time = 0;
    long long int last_time = 0;
    cv::Mat rgbImg(IMAGEHEIGHT-4, IMAGEWIDTH,CV_8UC4);


    cv::Mat orgLumiImgL(IMAGEHEIGHT-4, IMAGEWIDTH,CV_8UC1);
    cv::Mat orgRgbImgP(IMAGEHEIGHT-4, IMAGEWIDTH,CV_8UC4);

	cv::Mat rgbImgP(IMAGEHEIGHT-4, IMAGEWIDTH,CV_8UC4);

    cv::Mat yuvImg;
    yuvImg.create(IMAGEHEIGHT-4, IMAGEWIDTH, CV_8UC2);


    int rangeMode=120;
    float floatFpaTmp;
    float correction;
    float Refltmp;
    float Airtmp;
    float humi;
    float emiss;
    unsigned short distance;


    int cameraLens=130;
    float shutterFix=0;


    char sn[32];
    char cameraSoftVersion[16];
    unsigned short shutTemper;
    float floatShutTemper;
    unsigned short coreTemper;
    float floatCoreTemper;
    const unsigned char* paletteIronRainbow = getPalette(0);
    const unsigned char* palette3 = getPalette(1);
    const unsigned char* paletteRainbow = getPalette(2);
    const unsigned char* paletteHighRainbow = getPalette(3);
    const unsigned char* paletteHighContrast = getPalette(4);
    while(1)
    {
        for(n_buffers = 0; n_buffers < 4; n_buffers++)
        {
            t = (double)cvGetTickCount();

            buf.index = n_buffers;
            ioctl(fd, VIDIOC_DQBUF, &buf);
            delayy++;
            printf ("delayy:%d\n", delayy);


            if(buf.bytesused!=IMAGEHEIGHT*IMAGEWIDTH*2)
            {

                ioctl(fd, VIDIOC_QBUF, &buf);
                printf("throw err data\n");
                break;
            }
            if(delayy==51||delayy==52||delayy==53)
            {

                ioctl(fd, VIDIOC_QBUF, &buf);
                printf("throw data\n");
                break;
            }

            if(delayy==126||delayy==127||delayy==128)
            {

                ioctl(fd, VIDIOC_QBUF, &buf);
                printf("throw data\n");
                break;
            }


            buffers[n_buffers].timestamp = buf.timestamp.tv_sec*1000000+buf.timestamp.tv_usec;
            cur_time = buffers[n_buffers].timestamp;
            extra_time = cur_time - last_time;
            last_time = cur_time;



            unsigned short* orgData=(unsigned short *)buffers[n_buffers].start;
            unsigned short* fourLinePara=orgData+IMAGEWIDTH*(IMAGEHEIGHT-4);

            int amountPixels=0;
            switch (IMAGEWIDTH)
            {
            case 384:
                amountPixels=IMAGEWIDTH*(4-1);
                break;
            case 240:
                amountPixels=IMAGEWIDTH*(4-3);
                break;
            case 256:
                amountPixels=IMAGEWIDTH*(4-3);
                break;
            case 640:
                amountPixels=IMAGEWIDTH*(4-1);
                break;
            }
            memcpy(&shutTemper,fourLinePara+amountPixels+1,sizeof(unsigned short));

            floatShutTemper=shutTemper/10.0f-273.15f;
            memcpy(&coreTemper,fourLinePara+amountPixels+2,sizeof(unsigned short));

            floatCoreTemper=coreTemper/10.0f-273.15f;
            printf("cpyPara  floatShutTemper:%f,floatCoreTemper:%f,floatFpaTmp:%f\n",floatShutTemper,floatCoreTemper,floatFpaTmp);
            memcpy((unsigned short*)cameraSoftVersion,fourLinePara+amountPixels+24,16*sizeof(uint8_t));
            printf("cameraSoftVersion:%s\n",cameraSoftVersion);
            memcpy((unsigned short*)sn,fourLinePara+amountPixels+32,32*sizeof(uint8_t));
            printf("sn:%s\n",sn);
            int userArea=amountPixels+127;
            memcpy(&correction,fourLinePara+userArea,sizeof( float));
            userArea=userArea+2;
            memcpy(&Refltmp,fourLinePara+userArea,sizeof( float));
            userArea=userArea+2;
            memcpy(&Airtmp,fourLinePara+userArea,sizeof( float));
            userArea=userArea+2;
            memcpy(&humi,fourLinePara+userArea,sizeof( float));
            userArea=userArea+2;
            memcpy(&emiss,fourLinePara+userArea,sizeof( float));
            userArea=userArea+2;
            memcpy(&distance,fourLinePara+userArea,sizeof(unsigned short));
            printf ("Airtmp:%f,correction:%f,distance:%d,emiss:%f,Refltmp:%f,humi:%f\n", Airtmp,correction,distance,emiss,Refltmp,humi);


            if(delayy%4500==30)
            {
                if(v4l2_control(0x8000) == FALSE)
                {
                    printf("shutter fail~~\n");
                }
            }
            if(delayy%4500==25)
            {
                /*thermometryT(           IMAGEWIDTH,
                		    IMAGEHEIGHT,
                		    temperatureTable,
                		    orgData,
                		    &floatFpaTmp,
                		    & correction,
                		    & Refltmp,
                		    & Airtmp,
                		    & humi,
                		    & emiss,
                		    & distance,
                		    cameraLens,
                		    shutterFix
                                    rangeMode);*/

                thermometryT4Line(IMAGEWIDTH,
                                  IMAGEHEIGHT,
                                  temperatureTable,
                                  fourLinePara,
                                  &floatFpaTmp,
                                  &correction,
                                  &Refltmp,
                                  &Airtmp,
                                  &humi,
                                  &emiss,
                                  &distance,
                                  cameraLens,
                                  shutterFix,
                                  rangeMode);
                if(delayy>9000)
                {
                    delayy=0;
                }
            }






            /*temperatureData[0]=centerTmp;
            temperatureData[1]=(float)maxx1;
            temperatureData[2]=(float)maxy1;
            temperatureData[3]=maxTmp;
            temperatureData[4]=(float)minx1;
            temperatureData[5]=(float)miny1;
            temperatureData[6]=minTmp;
            temperatureData[7]=point1Tmp;
            temperatureData[8]=point2Tmp;
            temperatureData[9]=point3Tmp;*/


            thermometrySearch(IMAGEWIDTH,IMAGEHEIGHT,temperatureTable,orgData,temperatureData,rangeMode,OUTPUTMODE);
            printf("centerTmp:%.2f,maxTmp:%.2f,minTmp:%.2f,avgTmp:%.2f,point1Tmp:%.2f\n",temperatureData[0],temperatureData[3],temperatureData[6],temperatureData[9],temperatureData[7]);

            /**
             * 线性图像算法 linear algorithm
			 * 图像效果不及专业级算法，但是处理效率快，对主频几乎没要求
             * Poor images than professional algorithm, but runs faster and no requirement in basic frequency
             *
             */

            amountPixels=IMAGEWIDTH*(IMAGEHEIGHT-4);
            unsigned short detectAvg=orgData[amountPixels];

            amountPixels++;
            unsigned short fpaTmp=orgData[amountPixels];
            amountPixels++;
            unsigned short maxx1=orgData[amountPixels];
            amountPixels++;
            unsigned short maxy1=orgData[amountPixels];
            amountPixels++;
            unsigned short max=orgData[amountPixels];

            amountPixels++;
            unsigned short minx1=orgData[amountPixels];
            amountPixels++;
            unsigned short miny1=orgData[amountPixels];
            amountPixels++;
            unsigned short min=orgData[amountPixels];
            amountPixels++;
            unsigned short avg=orgData[amountPixels];

            unsigned char* orgOutput = orgRgbImgP.data;
            int ro = (max - min)>0?(max - min):1;
            int avgSubMin=(avg-min)>0?(avg-min):1;
            int maxSubAvg=(max-avg)>0?(max-avg):1;
            int ro1=(avg-min)>97?97:(avg-min);
            int ro2=(max-avg)>157?157:(max-avg);

            for(int i=0; i<IMAGEHEIGHT-4; i++)
            {
                for(int j=0; j<IMAGEWIDTH; j++)
                {



                    int gray=0;
                    if(orgData[i*IMAGEWIDTH+j]>avg)
                    {
                        gray = (int)(ro2*(orgData[i*IMAGEWIDTH+j]-avg)/maxSubAvg+97);
                    }
                    else
                    {
                        gray = (int)(ro1*(orgData[i*IMAGEWIDTH+j]-avg)/avgSubMin+97);
                    }
                    orgLumiImgL.at<uchar>(i,j) = (uchar)gray;
                    int intGray=(int)gray;
                    int paletteNum=3*intGray;
                    orgOutput[4*(i*IMAGEWIDTH+j)]=(unsigned char)paletteIronRainbow[paletteNum+2];
                    orgOutput[4*(i*IMAGEWIDTH+j)+1]=(unsigned char)paletteIronRainbow[paletteNum+1];
                    orgOutput[4*(i*IMAGEWIDTH+j)+2]=(unsigned char)paletteIronRainbow[paletteNum];
                    orgOutput[4*(i*IMAGEWIDTH+j)+3]=1;
                }
            }
            cv::imshow("orgLumiImgL", orgLumiImgL);

            cv::imshow("orgRgbImgP", orgRgbImgP);


            orgOutput = rgbImgP.data;
            ro = (max - min)>0?(max - min):1;
            avgSubMin=(avg-min)>0?(avg-min):1;
            maxSubAvg=(max-avg)>0?(max-avg):1;
            ro1=(avg-min)>170?170:(avg-min);
            ro2=(max-avg)>276?276:(max-avg);
            for(int i=0; i<IMAGEHEIGHT-4; i++)
            {
                for(int j=0; j<IMAGEWIDTH; j++)
                {


                    int gray=0;
                    if(orgData[i*IMAGEWIDTH+j]>avg)
                    {
                        gray = (int)(ro2*(orgData[i*IMAGEWIDTH+j]-avg)/maxSubAvg+170);
                    }
                    else
                    {
                        gray = (int)(ro1*(orgData[i*IMAGEWIDTH+j]-avg)/avgSubMin+170);
                    }
                    orgLumiImgL.at<uchar>(i,j) = (uchar)gray;
                    int intGray=(int)gray;
                    int paletteNum=3*intGray;

                    orgOutput[4*(i*IMAGEWIDTH+j)]=(unsigned char)paletteHighContrast[paletteNum+2];
                    orgOutput[4*(i*IMAGEWIDTH+j)+1]=(unsigned char)paletteHighContrast[paletteNum+1];
                    orgOutput[4*(i*IMAGEWIDTH+j)+2]=(unsigned char)paletteHighContrast[paletteNum];
                    orgOutput[4*(i*IMAGEWIDTH+j)+3]=1;
                }
            }
            cv::imshow("rgbImgP", rgbImgP);



            /*float* temperatureData2=(float*)calloc(10,sizeof(float));
            thermometrySearchCMM(IMAGEWIDTH,IMAGEHEIGHT,temperatureTable,fourLinePara,temperatureData2,rangeMode);
            	    printf("centerTmp:%.2f,maxx1:%.2f,maxy1:%.2f\n",temperatureData2[0],temperatureData2[1],temperatureData2[2]);
                                 free(temperatureData2);*/


            /*int count=20;
            unsigned short* queryData=orgData+IMAGEWIDTH*(IMAGEHEIGHT-4)/2;
            float* temperatureData3=(float*)calloc(count,sizeof(float));
            thermometrySearchSingle(IMAGEWIDTH,IMAGEHEIGHT,temperatureTable,fourLinePara,count,queryData,temperatureData3,rangeMode);
            printf("temperatureData3[0]:%.2f,temperatureData3[1]:%.2f,temperatureData3[2]:%.2f\n",temperatureData3[0],temperatureData3[1],temperatureData3[2]);
                                 free(temperatureData3);*/



            if(delayy==50)
            {





                sendDistance(3);
            }
            if(delayy==60)
            {
                setPoint(IMAGEWIDTH/2,(IMAGEHEIGHT-4)/2,0);
            }

            if(delayy==110)
            {
                if(v4l2_control(0x8000) == FALSE)
                {
                    printf("shutter fail~~\n");
                }
            }
            if(delayy==120)
            {

                thermometryT4Line(IMAGEWIDTH,
                                  IMAGEHEIGHT,
                                  temperatureTable,
                                  fourLinePara,
                                  &floatFpaTmp,
                                  &correction,
                                  &Refltmp,
                                  &Airtmp,
                                  &humi,
                                  &emiss,
                                  &distance,
                                  cameraLens,
                                  shutterFix,
                                  rangeMode);
            }
            if(delayy==125)
            {
                savePara();
                printf("savePara\n");
            }



            if(OUTPUTMODE==5)
            {
                memcpy(yuvImg.data,(unsigned char*)orgData,(IMAGEHEIGHT-4)*2* IMAGEWIDTH*sizeof(unsigned char));
                cv::cvtColor(yuvImg, rgbImg,  cv::COLOR_YUV2RGB_YUYV);
            }



            if(OUTPUTMODE==4)
            {
                Compute(orgData,rgbImg.data,0,irBuffers[n_buffers].midVar);
            }
            cv::imshow("rgbImg", rgbImg);


            ioctl(fd,VIDIOC_QBUF,&buf);







            t=(double)cvGetTickCount()-t;

			if((cv::waitKey(1)&255) == 27)    exit(0);

        }
    }


    for(n_buffers = 0; n_buffers < 4; n_buffers++)
    {

        if(OUTPUTMODE==4)
        {
            SimplePictureProcessingDeinit();
            if(irBuffers[n_buffers].midVar!=NULL)
            {
                SimplePictureProcessingDeinitMidVar(irBuffers[n_buffers].midVar);
                free(irBuffers[n_buffers].midVar);
                irBuffers[n_buffers].midVar=NULL;
            }
        }
        if(irBuffers[n_buffers].destBuffer!=NULL)
        {
            free(irBuffers[n_buffers].destBuffer);
            irBuffers[n_buffers].destBuffer=NULL;
        }


        if(temperatureData!=NULL)
        {
            free(temperatureData);
            temperatureData=NULL;
        }
    }

    v4l2_release();

    return 0;
}
int v4l2_release()
{
    unsigned int n_buffers;
    enum v4l2_buf_type type;


    type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    ioctl(fd, VIDIOC_STREAMOFF, &type);


    for(n_buffers=0; n_buffers<4; n_buffers++)
    {
        munmap(buffers[n_buffers].start,buffers[n_buffers].length);
    }


    free(buffers);


    close(fd);
    return TRUE;
}


int init_v4l2(string videoX)
{
    const char* videoXConst=videoX.c_str();
    if ((fd = open(videoXConst, O_RDWR)) == -1)
    {
        printf("Opening video device error\n");
        return FALSE;
    }
    if (ioctl(fd, VIDIOC_QUERYCAP, &cap) == -1)
    {
        printf("unable Querying Capabilities\n");
        return FALSE;
    }
    else

    {
        printf( "Driver Caps:\n"
                "  Driver: \"%s\"\n"
                "  Card: \"%s\"\n"
                "  Bus: \"%s\"\n"
                "  Version: %d\n"
                "  Capabilities: %x\n",
                cap.driver,
                cap.card,
                cap.bus_info,
                cap.version,
                cap.capabilities);
        string str="";
        str=(char*)cap.card;
        /*if(!str.find("T3S")){
        	close(fd);
        	return FALSE;
        }*/
    }
    /* if((cap.capabilities & V4L2_CAP_VIDEO_CAPTURE) == V4L2_CAP_VIDEO_CAPTURE){
         printf("Camera device %s: support capture\n",FILE_VIDEO1);
     }
     if((cap.capabilities & V4L2_CAP_STREAMING) == V4L2_CAP_STREAMING){
         printf("Camera device %s: support streaming.\n",FILE_VIDEO1);
     }
    */
    fmtdesc.index = 0;
    fmtdesc.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    printf("Support format: \n");
    while(ioctl(fd,VIDIOC_ENUM_FMT,&fmtdesc) != -1)
    {
        printf("\t%d. %s\n",fmtdesc.index+1,fmtdesc.description);
        fmtdesc.index++;
    }

    fmt.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    fmt.fmt.pix.width = IMAGEWIDTH;
    fmt.fmt.pix.height = IMAGEHEIGHT;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;

    fmt.fmt.pix.field       = V4L2_FIELD_INTERLACED;
    if (ioctl(fd, VIDIOC_S_FMT, &fmt) == -1)
    {
        printf("Setting Pixel Format error\n");
        return FALSE;
    }
    if(ioctl(fd,VIDIOC_G_FMT,&fmt) == -1)
    {
        printf("Unable to get format\n");
        return FALSE;
    }
    IMAGEWIDTH = fmt.fmt.pix.width;
    IMAGEHEIGHT = fmt.fmt.pix.height;
    printf("IMAGEWIDTH:%d,IMAGEHEIGHT:%d\n",IMAGEWIDTH,IMAGEHEIGHT);
    memset(&stream_para, 0, sizeof(struct v4l2_streamparm));
    stream_para.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    stream_para.parm.capture.timeperframe.denominator = 25;
    stream_para.parm.capture.timeperframe.numerator = 1;

    if(ioctl(fd, VIDIOC_S_PARM, &stream_para) == -1)
    {
        printf("Unable to set frame rate\n");
        return FALSE;
    }
    if(ioctl(fd, VIDIOC_G_PARM, &stream_para) == -1)
    {
        printf("Unable to get frame rate\n");
        return FALSE;
    }
    {
        printf("numerator:%d\ndenominator:%d\n",stream_para.parm.capture.timeperframe.numerator,stream_para.parm.capture.timeperframe.denominator);
    }


    /*        {
                printf("fmt.type:\t%d\n",fmt.type);
                printf("pix.pixelformat:\t%c%c%c%c\n",fmt.fmt.pix.pixelformat & 0xFF,(fmt.fmt.pix.pixelformat >> 8) & 0xFF,\
                       (fmt.fmt.pix.pixelformat >> 16) & 0xFF, (fmt.fmt.pix.pixelformat >> 24) & 0xFF);
                printf("pix.height:\t%d\n",fmt.fmt.pix.height);
                printf("pix.field:\t%d\n",fmt.fmt.pix.field);
            }
    */
    return TRUE;
}

int v4l2_grab(void)
{


    req.count = 4;
    req.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    req.memory = V4L2_MEMORY_MMAP;

    if (ioctl(fd, VIDIOC_REQBUFS, &req) == -1)
    {
        printf("Requesting Buffer error\n");
        return FALSE;
    }

    buffers = (buffer*)malloc(req.count * sizeof(*buffers));
    if(!buffers)
    {
        printf("Out of memory\n");
        return FALSE;
    }
    unsigned int n_buffers;
    for(n_buffers = 0; n_buffers < req.count; n_buffers++)
    {

        buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buf.memory = V4L2_MEMORY_MMAP;
        buf.index = n_buffers;
        if(ioctl(fd, VIDIOC_QUERYBUF, &buf) == -1)
        {

            printf("Querying Buffer error\n");
            return FALSE;
        }
        buffers[n_buffers].length = buf.length;

        buffers[n_buffers].start = (unsigned char*)mmap (NULL, buf.length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, buf.m.offset);

        if(buffers[n_buffers].start == MAP_FAILED)
        {
            printf("buffer map error\n");
            return FALSE;
        }
    }

    for(n_buffers = 0; n_buffers <req.count; n_buffers++)
    {
        buf.index = n_buffers;
        buf.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
        buf.memory = V4L2_MEMORY_MMAP;
        if(ioctl(fd,VIDIOC_QBUF,&buf))
        {
            printf("query buffer error\n");
            return FALSE;
        }
    }

    type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if(ioctl(fd,VIDIOC_STREAMON,&type) == -1)
    {
        printf("stream on error\n");
        return FALSE;
    }
    return TRUE;
}
int v4l2_control(int value)
{
    ctrl.id=V4L2_CID_ZOOM_ABSOLUTE;
    ctrl.value=value;

    if (ioctl(fd, VIDIOC_S_CTRL, &ctrl) == -1)
    {
        printf("v4l2_control error\n");
        return FALSE;
    }
    return TRUE;
}
int traversalVideo(void)
{
    string device="/dev";
    string video="video";
    char* KEY_PTR=(char *)video.c_str();
    char* FILE_PTR=(char *)device.c_str();

    DIR *dir;
    struct dirent *ptr;
    char base[1000];

    if ((dir=opendir(FILE_PTR)) == NULL)
    {
        perror("Open dir error...");
        exit(1);
    }

    while ((ptr=readdir(dir)) != NULL)
    {
        string name="";
        name=(char*)ptr->d_name;
        if(name.find("video")!= string::npos)
        {
            string allName=device+"/"+name;
            if(init_v4l2(allName))
            {
                closedir(dir);
                return 1;
            }
        }
        printf("d_name:%s/%s\n",FILE_PTR,ptr->d_name);
    }

    closedir(dir);
    return 0;

}

void sendFloatCommand(int position, unsigned char value0, unsigned char value1, unsigned char value2, unsigned char value3, int interval0,
                      int interval1, int interval2, int interval3, int interval4)
{
    int psitionAndValue0 = (position << 8) | (0x000000ff & value0);
    printf("psitionAndValue0:%X\n",psitionAndValue0);

    if(v4l2_control(psitionAndValue0) == FALSE)
    {
        printf("control fail psitionAndValue0~~\n");
        exit(EXIT_FAILURE);
    }
    int psitionAndValue1 = ((position + 1) << 8) | (0x000000ff & value1);
    printf("psitionAndValue1:%X\n",psitionAndValue1);
    if(v4l2_control(psitionAndValue1) == FALSE)
    {
        printf("control fail psitionAndValue1~~\n");
        exit(EXIT_FAILURE);
    }
    int psitionAndValue2 = ((position + 2) << 8) | (0x000000ff & value2);
    printf("psitionAndValue2:%X\n",psitionAndValue2);
    if(v4l2_control(psitionAndValue2) == FALSE)
    {
        printf("control fail psitionAndValue2~~\n");
        exit(EXIT_FAILURE);
    }
    int psitionAndValue3 = ((position + 3) << 8) | (0x000000ff & value3);
    printf("psitionAndValue3:%X\n",psitionAndValue3);
    if(v4l2_control(psitionAndValue3) == FALSE)
    {
        printf("control fail psitionAndValue3~~\n");
        exit(EXIT_FAILURE);
    }
}
void sendUshortCommand(int position, unsigned char value0, unsigned char value1)
{
    int psitionAndValue0 = (position << 8) | (0x000000ff & value0);
    printf("psitionAndValue0:%X\n",psitionAndValue0);

    if(v4l2_control(psitionAndValue0) == FALSE)
    {
        printf("control fail psitionAndValue0~~\n");
        exit(EXIT_FAILURE);
    }
    int psitionAndValue1 = ((position + 1) << 8) | (0x000000ff & value1);
    printf("psitionAndValue1:%X\n",psitionAndValue1);
    if(v4l2_control(psitionAndValue1) == FALSE)
    {
        printf("control fail psitionAndValue1~~\n");
        exit(EXIT_FAILURE);
    }
}
void sendByteCommand(int position, unsigned char value0, int interval0)
{
    int psitionAndValue0 = (position << 8) | (0x000000ff & value0);
    v4l2_control(psitionAndValue0);
}

void sendCorrection(float correction)
{
    unsigned char iputCo[4];
    memcpy(iputCo,&correction,sizeof(float));
    sendFloatCommand(0 * 4, iputCo[0], iputCo[1], iputCo[2], iputCo[3], 20, 40, 60, 80, 120);
    printf("sendCorrection 0:%d,1:%d,2:%d,3:%d\n",iputCo[0],iputCo[1],iputCo[2],iputCo[3]);

}
void sendReflection(float reflection)
{
    unsigned char iputRe[4];
    memcpy(iputRe,&reflection,sizeof(float));
    sendFloatCommand(1 * 4, iputRe[0], iputRe[1], iputRe[2], iputRe[3], 20, 40, 60, 80, 120);

}
void sendAmb(float amb)
{
    unsigned char iputAm[4];
    memcpy(iputAm,&amb,sizeof(float));
    sendFloatCommand(2 * 4, iputAm[0], iputAm[1], iputAm[2], iputAm[3], 20, 40, 60, 80, 120);

}
void sendHumidity(float humidity)
{
    unsigned char iputHu[4];
    memcpy(iputHu,&humidity,sizeof(float));
    sendFloatCommand(3 * 4, iputHu[0], iputHu[1], iputHu[2], iputHu[3], 20, 40, 60, 80, 120);

}
void sendEmissivity(float emiss)
{
    unsigned char iputEm[4];
    memcpy(iputEm,&emiss,sizeof(float));
    sendFloatCommand(4 * 4, iputEm[0], iputEm[1], iputEm[2], iputEm[3], 20, 40, 60, 80, 120);
}

void sendDistance(unsigned short distance)
{
    unsigned char iputDi[2];
    memcpy(iputDi,&distance,sizeof(unsigned short));
    sendUshortCommand(5 * 4,iputDi[0],iputDi[1]);
}
void savePara()
{
    v4l2_control(0x80ff);
}
void setPoint(int viewX1,int viewY1,int indexOfPoint)
{
    int x1,y1;
    switch (indexOfPoint)
    {
    case 0:
        x1=0xf000+viewX1;
        y1=0xf200+viewY1;
        break;
    case 1:
        x1=0xf400+viewX1;
        y1=0xf600+viewY1;
        break;
    case 2:
        x1=0xf800+viewX1;
        y1=0xfa00+viewY1;
        break;
    }
    v4l2_control(x1);
    v4l2_control(y1);
}
