/*
 * btmouse.c  -  Bluetooth Classic (BR/EDR) HID mouse / trackpad emulator
 *
 * Turns this Linux machine into a Bluetooth HID pointing device. A host
 * (your infotainment computer) pairs with it exactly like a real BT mouse,
 * and this program feeds it relative-motion / button / scroll reports.
 *
 * Approach (the standard one used by real HID-device emulators):
 *   1. Register a HID SDP service record (report descriptor + PSMs) so the
 *      host discovers us as a mouse.
 *   2. Listen on the two L2CAP PSMs the HID profile uses:
 *          0x11 (17) = HID Control
 *          0x13 (19) = HID Interrupt   <- input reports go here
 *   3. When a host connects, stream 4-byte mouse reports over the
 *      interrupt channel, and answer basic control-channel requests.
 *
 * Wire up your touchpad hardware by calling hid_send_report() (or feed the
 * simple stdin command protocol described in usage()).
 *
 * Build:   gcc -O2 -Wall -o btmouse btmouse.c -lbluetooth
 * Needs:   libbluetooth-dev  (Debian/Ubuntu) or bluez-libs-devel (Fedora)
 *
 * See the accompanying notes for the required runtime setup
 * (bluetoothd --compat, class-of-device, pairable/discoverable, root/caps).
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <poll.h>
#include <ctype.h>

#include <sys/socket.h>
#include <sys/ioctl.h>

#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/hci_lib.h>
#include <bluetooth/l2cap.h>
#include <bluetooth/sdp.h>
#include <bluetooth/sdp_lib.h>

#include "bt_setup.h"
#include "bt_agent.h"
#include "spinner.h"

/* ------------------------------------------------------------------ *
 *  Protocol constants
 * ------------------------------------------------------------------ */
#define PSM_HIDP_CTRL   0x11        /* HID control  L2CAP PSM */
#define PSM_HIDP_INTR   0x13        /* HID interrupt L2CAP PSM */

/* HIDP transaction header (high nibble) */
#define HIDP_TRANS_HANDSHAKE     0x00
#define HIDP_TRANS_HID_CONTROL   0x10
#define HIDP_TRANS_GET_REPORT    0x40
#define HIDP_TRANS_SET_REPORT    0x50
#define HIDP_TRANS_GET_PROTOCOL  0x60
#define HIDP_TRANS_SET_PROTOCOL  0x70
#define HIDP_TRANS_DATA          0xA0

/* HIDP report type (low nibble, for DATA / GET / SET) */
#define HIDP_DATA_RTYPE_INPUT    0x01
#define HIDP_DATA_RTYPE_OUTPUT   0x02
#define HIDP_DATA_RTYPE_FEATURE  0x03

/* Handshake results */
#define HIDP_HSHK_SUCCESSFUL             0x00
#define HIDP_HSHK_ERR_UNKNOWN            0x0E
#define HIDP_HSHK_ERR_UNSUPPORTED_REQ    0x03

/* HID SDP attribute IDs (defined here so we don't depend on header version) */
#define ATTR_HID_DEVICE_RELEASE_NUMBER   0x0200
#define ATTR_HID_PARSER_VERSION          0x0201
#define ATTR_HID_DEVICE_SUBCLASS         0x0202
#define ATTR_HID_COUNTRY_CODE            0x0203
#define ATTR_HID_VIRTUAL_CABLE           0x0204
#define ATTR_HID_RECONNECT_INITIATE      0x0205
#define ATTR_HID_DESCRIPTOR_LIST         0x0206
#define ATTR_HID_LANG_ID_BASE_LIST       0x0207
#define ATTR_HID_BOOT_DEVICE             0x020E
#define ATTR_HID_PROFILE_VERSION         0x020B

/* ------------------------------------------------------------------ *
 *  HID report descriptor: a 3-button mouse with X/Y and a wheel.
 *  Report layout (4 bytes, no report ID):
 *      byte0: bit0=left bit1=right bit2=middle (bits3-7 padding)
 *      byte1: dX   (-127..127, relative)
 *      byte2: dY   (-127..127, relative)
 *      byte3: wheel(-127..127, relative)
 * ------------------------------------------------------------------ */
static const uint8_t hid_report_descriptor[] = {
    0x05, 0x01,   /* Usage Page (Generic Desktop)        */
    0x09, 0x02,   /* Usage (Mouse)                       */
    0xA1, 0x01,   /* Collection (Application)            */
    0x09, 0x01,   /*   Usage (Pointer)                   */
    0xA1, 0x00,   /*   Collection (Physical)             */
    0x05, 0x09,   /*     Usage Page (Buttons)            */
    0x19, 0x01,   /*     Usage Minimum (1)               */
    0x29, 0x03,   /*     Usage Maximum (3)               */
    0x15, 0x00,   /*     Logical Minimum (0)             */
    0x25, 0x01,   /*     Logical Maximum (1)             */
    0x95, 0x03,   /*     Report Count (3)                */
    0x75, 0x01,   /*     Report Size (1)                 */
    0x81, 0x02,   /*     Input (Data,Var,Abs) - 3 btns   */
    0x95, 0x01,   /*     Report Count (1)                */
    0x75, 0x05,   /*     Report Size (5)                 */
    0x81, 0x03,   /*     Input (Const)       - padding   */
    0x05, 0x01,   /*     Usage Page (Generic Desktop)    */
    0x09, 0x30,   /*     Usage (X)                       */
    0x09, 0x31,   /*     Usage (Y)                       */
    0x09, 0x38,   /*     Usage (Wheel)                   */
    0x15, 0x81,   /*     Logical Minimum (-127)          */
    0x25, 0x7F,   /*     Logical Maximum (127)           */
    0x75, 0x08,   /*     Report Size (8)                 */
    0x95, 0x03,   /*     Report Count (3)                */
    0x81, 0x06,   /*     Input (Data,Var,Rel) X,Y,Wheel  */
    0xC0,         /*   End Collection                    */
    0xC0          /* End Collection                      */
};

/* ------------------------------------------------------------------ *
 *  Globals
 * ------------------------------------------------------------------ */
static sdp_session_t *g_sdp_session = NULL;
static uint32_t       g_sdp_handle  = 0;
static volatile sig_atomic_t g_running = 1;

/* current button state, so partial updates (move while held) work */
static uint8_t g_buttons = 0;

static void on_signal(int sig) { (void)sig; g_running = 0; }

/* ------------------------------------------------------------------ *
 *  Set the local adapter's Class of Device so it looks like a mouse.
 *  0x002580 = Peripheral (major) + Pointing device (minor).
 *  Best-effort: failure is non-fatal (you can also set it with
 *  `sudo hciconfig hci0 class 0x002580`).
 * ------------------------------------------------------------------ */
static void set_class_of_device(int dev_id)
{
    int dd = hci_open_dev(dev_id);
    if (dd < 0) {
        fprintf(stderr, "warn: cannot open hci%d to set class: %s\n",
                dev_id, strerror(errno));
        return;
    }
    uint32_t cod = 0x002580;
    if (hci_write_class_of_dev(dd, cod, 2000) < 0)
        fprintf(stderr, "warn: hci_write_class_of_dev failed: %s "
                "(try: sudo hciconfig hci%d class 0x002580)\n",
                strerror(errno), dev_id);
    else
        printf("Class of device set to 0x%06x (pointing device)\n", cod);
    hci_close_dev(dd);
}

/* ------------------------------------------------------------------ *
 *  Register the HID SDP record with the local SDP server.
 *  Requires bluetoothd to be running in compat mode (--compat) so the
 *  legacy SDP socket /var/run/sdp is available.
 * ------------------------------------------------------------------ */
static int sdp_register(void)
{
    sdp_record_t *rec = sdp_record_alloc();
    if (!rec) { fprintf(stderr, "sdp_record_alloc failed\n"); return -1; }

    uuid_t root_uuid, hid_uuid, l2cap_uuid, hidp_uuid;
    sdp_list_t *root_list;

    /* ---- browse group: public root ---- */
    sdp_uuid16_create(&root_uuid, PUBLIC_BROWSE_GROUP);
    root_list = sdp_list_append(NULL, &root_uuid);
    sdp_set_browse_groups(rec, root_list);

    /* ---- ServiceClassIDList = HID ---- */
    sdp_uuid16_create(&hid_uuid, HID_SVCLASS_ID);        /* 0x1124 */
    sdp_list_t *svc_class = sdp_list_append(NULL, &hid_uuid);
    sdp_set_service_classes(rec, svc_class);

    /* ---- BluetoothProfileDescriptorList = HID v1.0 ---- */
    sdp_profile_desc_t profile;
    sdp_uuid16_create(&profile.uuid, HID_PROFILE_ID);    /* 0x1124 */
    profile.version = 0x0100;
    sdp_list_t *profiles = sdp_list_append(NULL, &profile);
    sdp_set_profile_descs(rec, profiles);

    sdp_uuid16_create(&l2cap_uuid, L2CAP_UUID);          /* 0x0100 */
    sdp_uuid16_create(&hidp_uuid,  HIDP_UUID);           /* 0x0011 */

    /* ---- ProtocolDescriptorList: L2CAP(ctrl PSM) + HIDP ---- */
    uint16_t ctrl_psm = PSM_HIDP_CTRL;
    sdp_data_t *psm_ctrl = sdp_data_alloc(SDP_UINT16, &ctrl_psm);
    sdp_list_t *l2cap_ctrl = sdp_list_append(NULL, &l2cap_uuid);
    l2cap_ctrl = sdp_list_append(l2cap_ctrl, psm_ctrl);
    sdp_list_t *proto_ctrl = sdp_list_append(NULL, l2cap_ctrl);
    sdp_list_t *hidp_ctrl  = sdp_list_append(NULL, &hidp_uuid);
    proto_ctrl = sdp_list_append(proto_ctrl, hidp_ctrl);
    sdp_list_t *access_ctrl = sdp_list_append(NULL, proto_ctrl);
    sdp_set_access_protos(rec, access_ctrl);

    /* ---- AdditionalProtocolDescriptorList: L2CAP(intr PSM) + HIDP ---- */
    uint16_t intr_psm = PSM_HIDP_INTR;
    sdp_data_t *psm_intr = sdp_data_alloc(SDP_UINT16, &intr_psm);
    sdp_list_t *l2cap_intr = sdp_list_append(NULL, &l2cap_uuid);
    l2cap_intr = sdp_list_append(l2cap_intr, psm_intr);
    sdp_list_t *proto_intr = sdp_list_append(NULL, l2cap_intr);
    sdp_list_t *hidp_intr  = sdp_list_append(NULL, &hidp_uuid);
    proto_intr = sdp_list_append(proto_intr, hidp_intr);
    sdp_list_t *access_intr = sdp_list_append(NULL, proto_intr);
    sdp_set_add_access_protos(rec, access_intr);

    /* ---- Human-readable strings ---- */
    sdp_set_info_attr(rec, "BT Virtual Trackpad", "OpenSource",
                      "Bluetooth HID pointing device");

    /* ---- HID-specific attributes ---- */
    uint16_t u16;
    uint8_t  u8;
    uint8_t  boolt = 1, boolf = 0;

    u16 = 0x0100;
    sdp_attr_add_new(rec, ATTR_HID_DEVICE_RELEASE_NUMBER, SDP_UINT16, &u16);
    u16 = 0x0111;   /* HID parser version 1.1.1 */
    sdp_attr_add_new(rec, ATTR_HID_PARSER_VERSION, SDP_UINT16, &u16);
    u8 = 0x80;      /* device subclass: mouse (peripheral, pointing) */
    sdp_attr_add_new(rec, ATTR_HID_DEVICE_SUBCLASS, SDP_UINT8, &u8);
    u8 = 0x00;      /* country code: not localized */
    sdp_attr_add_new(rec, ATTR_HID_COUNTRY_CODE, SDP_UINT8, &u8);
    sdp_attr_add_new(rec, ATTR_HID_VIRTUAL_CABLE,    SDP_BOOL, &boolt);
    sdp_attr_add_new(rec, ATTR_HID_RECONNECT_INITIATE, SDP_BOOL, &boolt);
    sdp_attr_add_new(rec, ATTR_HID_BOOT_DEVICE,      SDP_BOOL, &boolf);
    u16 = 0x0100;
    sdp_attr_add_new(rec, ATTR_HID_PROFILE_VERSION, SDP_UINT16, &u16);

    /* ---- HIDDescriptorList: seq{ seq{ uint8 0x22, string report_desc } } ---- */
    {
        uint8_t desc_type = 0x22;   /* 0x22 = Report descriptor */
        sdp_data_t *dtype = sdp_data_alloc(SDP_UINT8, &desc_type);
        sdp_data_t *dval  = sdp_data_alloc_with_length(
                                SDP_TEXT_STR8,
                                (void *)hid_report_descriptor,
                                sizeof(hid_report_descriptor));
        /* chain the two into the inner sequence */
        dtype->next = dval;
        sdp_data_t *inner = sdp_data_alloc(SDP_SEQ8, dtype);
        /* outer sequence wraps the inner one */
        sdp_data_t *outer = sdp_data_alloc(SDP_SEQ8, inner);
        sdp_attr_add(rec, ATTR_HID_DESCRIPTOR_LIST, outer);
    }

    /* ---- HIDLANGIDBaseList: seq{ seq{ uint16 0x0409, uint16 0x0100 } } ---- */
    {
        uint16_t lang = 0x0409;     /* English (US) */
        uint16_t base = 0x0100;
        sdp_data_t *ld = sdp_data_alloc(SDP_UINT16, &lang);
        sdp_data_t *bd = sdp_data_alloc(SDP_UINT16, &base);
        ld->next = bd;
        sdp_data_t *inner = sdp_data_alloc(SDP_SEQ8, ld);
        sdp_data_t *outer = sdp_data_alloc(SDP_SEQ8, inner);
        sdp_attr_add(rec, ATTR_HID_LANG_ID_BASE_LIST, outer);
    }

    /* ---- connect to local SDP server and register ---- */
    g_sdp_session = sdp_connect(BDADDR_ANY, BDADDR_LOCAL, SDP_RETRY_IF_BUSY);
    if (!g_sdp_session) {
        fprintf(stderr,
            "sdp_connect failed: %s\n"
            "  Is bluetoothd running with --compat? (needed for /var/run/sdp)\n",
            strerror(errno));
        sdp_record_free(rec);
        return -1;
    }

    if (sdp_record_register(g_sdp_session, rec, 0) < 0) {
        fprintf(stderr, "sdp_record_register failed: %s\n", strerror(errno));
        sdp_close(g_sdp_session);
        g_sdp_session = NULL;
        sdp_record_free(rec);
        return -1;
    }

    g_sdp_handle = rec->handle;
    printf("HID SDP record registered (handle 0x%08x)\n", g_sdp_handle);
    /* NOTE: rec is now owned by the session; freed on sdp_close. */
    return 0;
}

static void sdp_unregister(void)
{
    if (g_sdp_session) {
        sdp_close(g_sdp_session);
        g_sdp_session = NULL;
    }
}

/* ------------------------------------------------------------------ *
 *  Open an L2CAP server socket bound to BDADDR_ANY on the given PSM.
 * ------------------------------------------------------------------ */
static int l2cap_listen(uint16_t psm)
{
    int sk = socket(AF_BLUETOOTH, SOCK_SEQPACKET, BTPROTO_L2CAP);
    if (sk < 0) { perror("socket(L2CAP)"); return -1; }

    struct sockaddr_l2 addr;
    memset(&addr, 0, sizeof(addr));
    addr.l2_family = AF_BLUETOOTH;
    addr.l2_psm    = htobs(psm);
    bacpy(&addr.l2_bdaddr, BDADDR_ANY);

    if (bind(sk, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        fprintf(stderr, "bind PSM 0x%02x failed: %s\n", psm, strerror(errno));
        close(sk);
        return -1;
    }
    if (listen(sk, 1) < 0) {
        fprintf(stderr, "listen PSM 0x%02x failed: %s\n", psm, strerror(errno));
        close(sk);
        return -1;
    }
    return sk;
}

/* Accept one connection; return client fd, fill peer if non-NULL. */
static int l2cap_accept(int server, bdaddr_t *peer)
{
    struct sockaddr_l2 raddr;
    socklen_t alen = sizeof(raddr);
    int cli = accept(server, (struct sockaddr *)&raddr, &alen);
    if (cli < 0) {
        if (errno != EINTR) perror("accept");
        return -1;
    }
    if (peer) bacpy(peer, &raddr.l2_bdaddr);
    return cli;
}

/* ------------------------------------------------------------------ *
 *  Send a mouse input report over the interrupt channel.
 *  buttons: bit0 L, bit1 R, bit2 M.  dx/dy/wheel: -127..127 relative.
 * ------------------------------------------------------------------ */
static int hid_send_report(int intr_fd, uint8_t buttons,
                           int dx, int dy, int wheel)
{
    if (dx    >  127) dx    =  127;
    if (dx    < -127) dx    = -127;
    if (dy    >  127) dy    =  127;
    if (dy    < -127) dy    = -127;
    if (wheel >  127) wheel =  127;
    if (wheel < -127) wheel = -127;

    uint8_t pkt[5];
    pkt[0] = HIDP_TRANS_DATA | HIDP_DATA_RTYPE_INPUT;   /* 0xA1 */
    pkt[1] = buttons & 0x07;
    pkt[2] = (uint8_t)(int8_t)dx;
    pkt[3] = (uint8_t)(int8_t)dy;
    pkt[4] = (uint8_t)(int8_t)wheel;

    ssize_t n = send(intr_fd, pkt, sizeof(pkt), 0);
    if (n < 0) { perror("send(report)"); return -1; }
    return 0;
}

/* ------------------------------------------------------------------ *
 *  Minimal HID control-channel handler. Real hosts occasionally send
 *  SET_PROTOCOL / GET_REPORT / HID_CONTROL. We keep the link healthy by
 *  answering with a HANDSHAKE (or an input report for GET_REPORT).
 * ------------------------------------------------------------------ */
static void handle_control(int ctrl_fd, int intr_fd)
{
    uint8_t buf[64];
    ssize_t n = recv(ctrl_fd, buf, sizeof(buf), 0);
    if (n <= 0) return;

    uint8_t hdr  = buf[0] & 0xF0;
    uint8_t hshk[1];

    switch (hdr) {
    case HIDP_TRANS_SET_PROTOCOL:
    case HIDP_TRANS_SET_REPORT:
    case HIDP_TRANS_HID_CONTROL:
        hshk[0] = HIDP_TRANS_HANDSHAKE | HIDP_HSHK_SUCCESSFUL;
        send(ctrl_fd, hshk, 1, 0);
        break;
    case HIDP_TRANS_GET_REPORT: {
        /* reply on control channel with a zeroed input report */
        uint8_t rep[5] = { HIDP_TRANS_DATA | HIDP_DATA_RTYPE_INPUT,
                           g_buttons, 0, 0, 0 };
        send(ctrl_fd, rep, sizeof(rep), 0);
        (void)intr_fd;
        break;
    }
    case HIDP_TRANS_GET_PROTOCOL: {
        uint8_t rep[2] = { HIDP_TRANS_DATA | HIDP_DATA_RTYPE_INPUT, 0x01 };
        send(ctrl_fd, rep, sizeof(rep), 0);
        break;
    }
    default:
        hshk[0] = HIDP_TRANS_HANDSHAKE | HIDP_HSHK_ERR_UNSUPPORTED_REQ;
        send(ctrl_fd, hshk, 1, 0);
        break;
    }
}

/* ------------------------------------------------------------------ *
 *  Parse one line of the demo command protocol and act on it.
 *  Replace / bypass this with your real touchpad input source.
 * ------------------------------------------------------------------ */
static int button_bit(char c)
{
    switch (tolower(c)) {
    case 'l': return 0x01;
    case 'r': return 0x02;
    case 'm': return 0x04;
    default:  return 0;
    }
}

static void process_command(int intr_fd, char *line)
{
    char cmd;
    if (sscanf(line, " %c", &cmd) != 1) return;

    switch (tolower(cmd)) {
    case 'm': {                       /* m dx dy  -> move */
        int dx = 0, dy = 0;
        sscanf(line, " %*c %d %d", &dx, &dy);
        hid_send_report(intr_fd, g_buttons, dx, dy, 0);
        break;
    }
    case 's': {                       /* s w      -> scroll wheel */
        int w = 0;
        sscanf(line, " %*c %d", &w);
        hid_send_report(intr_fd, g_buttons, 0, 0, w);
        break;
    }
    case 'd': {                       /* d l|r|m  -> button down */
        char b = 'l';
        sscanf(line, " %*c %c", &b);
        g_buttons |= button_bit(b);
        hid_send_report(intr_fd, g_buttons, 0, 0, 0);
        break;
    }
    case 'u': {                       /* u l|r|m  -> button up */
        char b = 'l';
        sscanf(line, " %*c %c", &b);
        g_buttons &= ~button_bit(b);
        hid_send_report(intr_fd, g_buttons, 0, 0, 0);
        break;
    }
    case 'c': {                       /* c l|r|m  -> click */
        char b = 'l';
        sscanf(line, " %*c %c", &b);
        int bit = button_bit(b);
        hid_send_report(intr_fd, g_buttons | bit, 0, 0, 0);
        usleep(15000);
        hid_send_report(intr_fd, g_buttons, 0, 0, 0);
        break;
    }
    case 'q':
        g_running = 0;
        break;
    default:
        break;
    }
}

static void usage_hint(void)
{
    printf(
"\nConnected. Command protocol on stdin (one per line):\n"
"  m <dx> <dy>   move pointer by dx,dy   (e.g.  m 10 -5)\n"
"  s <w>         scroll wheel by w       (e.g.  s 1)\n"
"  d <l|r|m>     press button            (e.g.  d l)\n"
"  u <l|r|m>     release button          (e.g.  u l)\n"
"  c <l|r|m>     click (press+release)   (e.g.  c l)\n"
"  q             quit\n\n");
}

/* ------------------------------------------------------------------ *
 *  Serve one connected host until it disconnects or we're told to stop.
 * ------------------------------------------------------------------ */
static void serve_session(int ctrl_fd, int intr_fd)
{
    usage_hint();

    struct pollfd fds[3];
    fds[0].fd = intr_fd;  fds[0].events = POLLIN;   /* host -> us (rare)  */
    fds[1].fd = ctrl_fd;  fds[1].events = POLLIN;   /* control requests   */
    fds[2].fd = STDIN_FILENO; fds[2].events = POLLIN;

    char linebuf[256];

    while (g_running) {
        int r = poll(fds, 3, 1000);
        if (r < 0) { if (errno == EINTR) continue; perror("poll"); break; }
        if (r == 0) continue;

        if (fds[1].revents & POLLIN)
            handle_control(ctrl_fd, intr_fd);

        if (fds[0].revents & (POLLHUP | POLLERR)) {
            printf("Interrupt channel closed by host.\n");
            break;
        }
        if (fds[0].revents & POLLIN) {
            uint8_t tmp[64];
            ssize_t n = recv(intr_fd, tmp, sizeof(tmp), 0);
            if (n <= 0) { printf("Host disconnected.\n"); break; }
            /* host->device output reports (e.g. LEDs) ignored for a mouse */
        }
        if ((fds[1].revents & (POLLHUP | POLLERR))) {
            printf("Control channel closed by host.\n");
            break;
        }

        if (fds[2].revents & POLLIN) {
            if (!fgets(linebuf, sizeof(linebuf), stdin)) { g_running = 0; break; }
            process_command(intr_fd, linebuf);
        }
    }
}

/* ------------------------------------------------------------------ */
int main(int argc, char **argv)
{
    int dev_id = 0;
    if (argc > 1) {
        dev_id = hci_devid(argv[1]);            /* accept "hci0" or "0" */
        if (dev_id < 0) dev_id = atoi(argv[1]);
        if (dev_id < 0) dev_id = 0;
    }

    /* Prepare the whole runtime environment on the user's behalf:
     * root (via sudo), bluetoothd --compat, adapter power, pairable +
     * discoverable. Re-execs under sudo if we are not root. */
    BtSetup bt;
    bt_setup_init(&bt, (argc > 1) ? argv[1] : NULL);
    if (bt_setup_run_all(&bt, argc, argv) != 0) {
        fprintf(stderr, "Environment setup failed; see messages above.\n");
        return 1;
    }

    signal(SIGINT,  on_signal);
    signal(SIGTERM, on_signal);
    signal(SIGPIPE, SIG_IGN);

    /* Start the zero-interaction pairing agent so hosts can pair and
     * reconnect with no prompts. Non-fatal if it can't start. */
    bt_agent_start();

    set_class_of_device(dev_id);

    if (sdp_register() < 0) {
        fprintf(stderr, "Failed to register HID SDP record. Aborting.\n");
        bt_agent_stop();
        return 1;
    }

    int ctrl_srv = l2cap_listen(PSM_HIDP_CTRL);
    int intr_srv = l2cap_listen(PSM_HIDP_INTR);
    if (ctrl_srv < 0 || intr_srv < 0) {
        fprintf(stderr,
            "Could not open L2CAP server sockets.\n"
            "  Run as root or grant CAP_NET_RAW/CAP_NET_BIND_SERVICE, and make\n"
            "  sure nothing else owns PSM 0x11/0x13.\n");
        sdp_unregister();
        bt_agent_stop();
        return 1;
    }

    printf("Listening for a host on L2CAP PSM 0x11 (ctrl) and 0x13 (intr).\n");
    printf("Everything is automated: the adapter is discoverable/pairable and\n");
    printf("a zero-prompt pairing agent is running. Just pair from the host\n");
    printf("(your infotainment unit) and it will reconnect automatically after.\n");
    printf("\nPress Ctrl-C to stop the program.\n");

    while (g_running) {
        spinner_start("btmouse running \u2014 waiting for a host to connect");

        bdaddr_t peer;
        int ctrl_fd = l2cap_accept(ctrl_srv, &peer);
        spinner_stop();
        if (ctrl_fd < 0) { if (!g_running) break; continue; }

        char addr[18];
        ba2str(&peer, addr);
        printf("Control channel from %s. Waiting for interrupt channel...\n",
               addr);

        int intr_fd = l2cap_accept(intr_srv, NULL);
        if (intr_fd < 0) { close(ctrl_fd); if (!g_running) break; continue; }

        printf("Host %s fully connected.\n", addr);
        g_buttons = 0;

        serve_session(ctrl_fd, intr_fd);

        close(intr_fd);
        close(ctrl_fd);
        printf("Session ended.\n");
    }

    spinner_stop();
    close(intr_srv);
    close(ctrl_srv);
    sdp_unregister();
    bt_agent_stop();
    printf("Shut down cleanly.\n");
    return 0;
}
