/*
 * gui.c  -  GTK4 remote-mouse control panel for btmouse.
 *
 * btmouse turns this machine into a Bluetooth HID pointing device; a host
 * (e.g. an infotainment unit) pairs with it and receives mouse reports.
 * The daemon (main.c) already exposes two IPC endpoints:
 *
 *     /tmp/btmouse.fifo     - a world-writable command pipe. One command
 *                             per line, the same protocol as its stdin:
 *                               m <dx> <dy>   move pointer (relative)
 *                               s <w>         scroll wheel
 *                               d <l|r|m>     button down
 *                               u <l|r|m>     button up
 *                               c <l|r|m>     click (down+up)
 *     /tmp/btmouse.status   - "WAITING" or "CONNECTED"
 *
 * This program is a thin GTK4 client that drives those endpoints, so it runs
 * as the normal user and never needs root or Bluetooth privileges itself.
 * Start the daemon (`sudo ./btmouse`) first, then launch this GUI.
 *
 * It offers:
 *   - a large trackpad-style surface: press-drag to move the pointer,
 *     tap to left-click, flick-to-accelerate feel, with live touch feedback;
 *   - dedicated Left / Middle / Right buttons that use press+release, so a
 *     tap is a click and press-hold-then-drag-on-the-pad is a click-drag;
 *   - a scroll strip on the right edge (drag up/down) plus mouse-wheel
 *     scrolling over the pad;
 *   - a pointer-speed slider, a natural-scroll toggle, and a drag-lock
 *     toggle (holds the left button down for the next pad drag);
 *   - a live connection indicator driven by the status file.
 *
 * Build:  gcc -O2 -Wall gui.c -o btmouse-gui `pkg-config --cflags --libs gtk4`
 *         (or `make gui`)
 */
#define _GNU_SOURCE
#include <gtk/gtk.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <math.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>

/* ------------------------------------------------------------------ *
 *  IPC endpoints exposed by the btmouse daemon.
 * ------------------------------------------------------------------ */
#define FIFO_PATH   "/tmp/btmouse.fifo"
#define STATUS_PATH "/tmp/btmouse.status"

/* A single HID report carries a signed 8-bit delta per axis. */
#define HID_DELTA_MAX 127

/* ------------------------------------------------------------------ *
 *  Application state
 * ------------------------------------------------------------------ */
typedef struct {
    /* IPC */
    int      fifo_fd;           /* write end of the command pipe, or -1     */
    gboolean daemon_up;         /* pipe currently writable                  */

    /* pointer-motion tuning */
    double   sensitivity;       /* base pointer speed multiplier            */
    double   acc_x, acc_y;      /* sub-pixel remainder carried between sends */

    /* touch/drag bookkeeping (main pad) */
    double   last_off_x, last_off_y;
    gint64   press_time_us;     /* when the current drag began              */
    gboolean pad_dragging;
    double   touch_x, touch_y;  /* current finger position, for feedback    */
    double   ripple;            /* feedback circle radius                   */
    gboolean holding_left;      /* drag-lock currently holding left button  */

    /* scroll strip bookkeeping */
    double   scroll_last_y;
    double   scroll_accum;

    /* options */
    gboolean natural_scroll;
    gboolean drag_lock;

    /* widgets we update from callbacks */
    GtkWidget *pad;
    GtkWidget *status_dot;
    GtkWidget *status_label;
} AppState;

/* ================================================================== *
 *  IPC: write commands to the daemon's FIFO (reconnect on demand).
 * ================================================================== */

/* Open the FIFO write end if we don't already hold it. The daemon keeps the
 * pipe open O_RDWR, so a reader is always present while it runs and this
 * non-blocking open succeeds; if the daemon is down the open fails and we
 * simply report "not running". */
static gboolean fifo_ensure(AppState *st)
{
    if (st->fifo_fd >= 0) return TRUE;
    int fd = open(FIFO_PATH, O_WRONLY | O_NONBLOCK | O_CLOEXEC);
    if (fd < 0) return FALSE;
    st->fifo_fd = fd;
    return TRUE;
}

static void fifo_close(AppState *st)
{
    if (st->fifo_fd >= 0) { close(st->fifo_fd); st->fifo_fd = -1; }
}

/* Send one newline-terminated command. Motion is transient, so if the pipe
 * is momentarily full (EAGAIN) we drop the write rather than block the UI.
 * On a broken pipe (daemon restarted) we reopen once and retry. */
static void fifo_sendf(AppState *st, const char *fmt, ...)
{
    if (!fifo_ensure(st)) return;

    char buf[128];
    va_list ap;
    va_start(ap, fmt);
    int n = vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    if (n <= 0) return;
    if (n > (int)sizeof(buf)) n = sizeof(buf);

    ssize_t w = write(st->fifo_fd, buf, (size_t)n);
    if (w >= 0) return;
    if (errno == EAGAIN || errno == EWOULDBLOCK) return;   /* pipe full: drop */

    /* Reader gone or fd stale: reopen once and retry. */
    fifo_close(st);
    if (fifo_ensure(st)) {
        ssize_t w2 = write(st->fifo_fd, buf, (size_t)n);
        (void)w2;
    }
}

/* Emit a pointer move, split into per-report chunks of at most 127. */
static void send_move(AppState *st, int dx, int dy)
{
    while (dx != 0 || dy != 0) {
        int sx = dx, sy = dy;
        if (sx >  HID_DELTA_MAX) sx =  HID_DELTA_MAX;
        if (sx < -HID_DELTA_MAX) sx = -HID_DELTA_MAX;
        if (sy >  HID_DELTA_MAX) sy =  HID_DELTA_MAX;
        if (sy < -HID_DELTA_MAX) sy = -HID_DELTA_MAX;
        fifo_sendf(st, "m %d %d\n", sx, sy);
        dx -= sx;
        dy -= sy;
    }
}

static void send_scroll(AppState *st, int w)  { if (w) fifo_sendf(st, "s %d\n", w); }
static void send_down (AppState *st, char b)  { fifo_sendf(st, "d %c\n", b); }
static void send_up   (AppState *st, char b)  { fifo_sendf(st, "u %c\n", b); }
static void send_click(AppState *st, char b)  { fifo_sendf(st, "c %c\n", b); }

/* ================================================================== *
 *  Trackpad surface
 * ================================================================== */

/* Draw the pad: rounded slate background, a faint dot grid, a centre
 * crosshair, and a soft feedback circle under the finger while dragging. */
static void pad_draw(GtkDrawingArea *area, cairo_t *cr,
                     int width, int height, gpointer user_data)
{
    (void)area;
    AppState *st = user_data;
    const double r = 18.0;

    /* rounded-rect background */
    cairo_new_sub_path(cr);
    cairo_arc(cr, width - r, r,          r, -G_PI_2, 0);
    cairo_arc(cr, width - r, height - r, r, 0,        G_PI_2);
    cairo_arc(cr, r,         height - r, r, G_PI_2,   G_PI);
    cairo_arc(cr, r,         r,          r, G_PI,     3 * G_PI_2);
    cairo_close_path(cr);
    cairo_set_source_rgb(cr, 0.13, 0.15, 0.19);
    cairo_fill_preserve(cr);
    cairo_set_source_rgb(cr, 0.22, 0.25, 0.31);
    cairo_set_line_width(cr, 1.5);
    cairo_stroke(cr);

    /* dot grid */
    cairo_set_source_rgba(cr, 1, 1, 1, 0.05);
    for (int y = 28; y < height - 10; y += 28)
        for (int x = 28; x < width - 10; x += 28) {
            cairo_arc(cr, x, y, 1.1, 0, 2 * G_PI);
            cairo_fill(cr);
        }

    /* centre crosshair */
    cairo_set_source_rgba(cr, 1, 1, 1, 0.10);
    cairo_set_line_width(cr, 1.0);
    cairo_move_to(cr, width / 2.0 - 10, height / 2.0);
    cairo_line_to(cr, width / 2.0 + 10, height / 2.0);
    cairo_move_to(cr, width / 2.0, height / 2.0 - 10);
    cairo_line_to(cr, width / 2.0, height / 2.0 + 10);
    cairo_stroke(cr);

    /* live touch feedback */
    if (st->pad_dragging && st->ripple > 0) {
        cairo_set_source_rgba(cr, 0.30, 0.68, 1.0, 0.18);
        cairo_arc(cr, st->touch_x, st->touch_y, st->ripple, 0, 2 * G_PI);
        cairo_fill(cr);
        cairo_set_source_rgba(cr, 0.30, 0.68, 1.0, 0.55);
        cairo_arc(cr, st->touch_x, st->touch_y, 6, 0, 2 * G_PI);
        cairo_fill(cr);
    } else if (!st->daemon_up) {
        /* hint when the daemon isn't reachable */
        cairo_set_source_rgba(cr, 1, 1, 1, 0.25);
        cairo_select_font_face(cr, "Sans", CAIRO_FONT_SLANT_NORMAL,
                               CAIRO_FONT_WEIGHT_NORMAL);
        cairo_set_font_size(cr, 15);
        const char *msg = "start  sudo ./btmouse  first";
        cairo_text_extents_t ext;
        cairo_text_extents(cr, msg, &ext);
        cairo_move_to(cr, width / 2.0 - ext.width / 2.0, height / 2.0 + 34);
        cairo_show_text(cr, msg);
    }
}

/* --- pad drag: relative pointer motion + tap-to-click --- */

static void pad_drag_begin(GtkGestureDrag *g, double x, double y, gpointer ud)
{
    (void)g;
    AppState *st = ud;
    st->last_off_x = st->last_off_y = 0;
    st->acc_x = st->acc_y = 0;
    st->press_time_us = g_get_monotonic_time();
    st->pad_dragging = TRUE;
    st->touch_x = x;
    st->touch_y = y;
    st->ripple  = 26;

    if (st->drag_lock) {          /* hold left down for a touch-drag gesture */
        send_down(st, 'l');
        st->holding_left = TRUE;
    }
    gtk_widget_queue_draw(st->pad);
}

static void pad_drag_update(GtkGestureDrag *g, double ox, double oy, gpointer ud)
{
    (void)g;
    AppState *st = ud;

    double dx = ox - st->last_off_x;
    double dy = oy - st->last_off_y;
    st->last_off_x = ox;
    st->last_off_y = oy;

    /* Gentle pointer acceleration: small motions stay precise, quick flicks
     * cover more ground - the feel people expect from a laptop trackpad. */
    double mag    = hypot(dx, dy);
    double accel  = 1.0 + fmin(mag * 0.06, 2.5);
    double factor = st->sensitivity * accel;

    st->acc_x += dx * factor;
    st->acc_y += dy * factor;

    int mx = (int)st->acc_x;   /* whole pixels to send now */
    int my = (int)st->acc_y;
    st->acc_x -= mx;
    st->acc_y -= my;

    if (mx || my) send_move(st, mx, my);

    /* update finger feedback */
    st->touch_x += dx;
    st->touch_y += dy;
    gtk_widget_queue_draw(st->pad);
}

static void pad_drag_end(GtkGestureDrag *g, double ox, double oy, gpointer ud)
{
    (void)g;
    AppState *st = ud;
    st->pad_dragging = FALSE;

    if (st->holding_left) {       /* release a drag-lock hold */
        send_up(st, 'l');
        st->holding_left = FALSE;
    } else {
        /* Tap-to-click: a short press that barely moved is a left click. */
        gint64 dt_ms = (g_get_monotonic_time() - st->press_time_us) / 1000;
        if (dt_ms < 220 && hypot(ox, oy) < 12.0)
            send_click(st, 'l');
    }
    gtk_widget_queue_draw(st->pad);
}

/* --- pad wheel scrolling (real mouse wheel / two-finger over the pad) --- */
static gboolean pad_scroll(GtkEventControllerScroll *c,
                           double dx, double dy, gpointer ud)
{
    (void)c; (void)dx;
    AppState *st = ud;
    int steps = (int)lround(dy);
    if (steps == 0) steps = (dy > 0) - (dy < 0);      /* ensure a notch */
    /* GTK dy>0 means scrolling down; HID positive wheel scrolls up. */
    int w = -steps;
    if (st->natural_scroll) w = -w;
    send_scroll(st, w);
    return TRUE;
}

/* ================================================================== *
 *  Scroll strip (right edge): drag up/down to scroll.
 * ================================================================== */
static void strip_draw(GtkDrawingArea *area, cairo_t *cr,
                       int width, int height, gpointer ud)
{
    (void)area; (void)ud;
    const double r = 14.0;
    cairo_new_sub_path(cr);
    cairo_arc(cr, width - r, r,          r, -G_PI_2, 0);
    cairo_arc(cr, width - r, height - r, r, 0,        G_PI_2);
    cairo_arc(cr, r,         height - r, r, G_PI_2,   G_PI);
    cairo_arc(cr, r,         r,          r, G_PI,     3 * G_PI_2);
    cairo_close_path(cr);
    cairo_set_source_rgb(cr, 0.11, 0.13, 0.17);
    cairo_fill(cr);

    /* up / down chevrons + grip dots to signal "drag to scroll" */
    cairo_set_source_rgba(cr, 1, 1, 1, 0.35);
    cairo_set_line_width(cr, 2.0);
    double cx = width / 2.0;
    cairo_move_to(cr, cx - 7, 26); cairo_line_to(cr, cx, 18);
    cairo_line_to(cr, cx + 7, 26); cairo_stroke(cr);
    cairo_move_to(cr, cx - 7, height - 26); cairo_line_to(cr, cx, height - 18);
    cairo_line_to(cr, cx + 7, height - 26); cairo_stroke(cr);
    cairo_set_source_rgba(cr, 1, 1, 1, 0.18);
    for (int i = -2; i <= 2; i++) {
        cairo_arc(cr, cx, height / 2.0 + i * 10, 1.6, 0, 2 * G_PI);
        cairo_fill(cr);
    }
}

static void strip_drag_begin(GtkGestureDrag *g, double x, double y, gpointer ud)
{
    (void)g; (void)x; (void)y;
    AppState *st = ud;
    st->scroll_last_y = 0;
    st->scroll_accum  = 0;
}

static void strip_drag_update(GtkGestureDrag *g, double ox, double oy, gpointer ud)
{
    (void)g; (void)ox;
    AppState *st = ud;
    double dy = oy - st->scroll_last_y;
    st->scroll_last_y = oy;

    st->scroll_accum += dy;
    const double STEP = 16.0;                   /* pixels per scroll notch */
    while (fabs(st->scroll_accum) >= STEP) {
        int dir = (st->scroll_accum > 0) ? 1 : -1;
        st->scroll_accum -= dir * STEP;
        /* finger down (dir>0) scrolls content down => HID negative wheel */
        int w = -dir;
        if (st->natural_scroll) w = -w;
        send_scroll(st, w);
    }
}

/* ================================================================== *
 *  Click buttons: press+release semantics (tap = click, hold = drag).
 * ================================================================== */
typedef struct { AppState *st; char btn; } BtnCtx;

static void btn_pressed(GtkGestureClick *g, int n, double x, double y, gpointer ud)
{
    (void)g; (void)n; (void)x; (void)y;
    BtnCtx *c = ud;
    send_down(c->st, c->btn);
}
static void btn_released(GtkGestureClick *g, int n, double x, double y, gpointer ud)
{
    (void)g; (void)n; (void)x; (void)y;
    BtnCtx *c = ud;
    send_up(c->st, c->btn);
}
static void btn_cancel(GtkGesture *g, GdkEventSequence *seq, gpointer ud)
{
    (void)g; (void)seq;
    BtnCtx *c = ud;                 /* pointer left while held: don't stick */
    send_up(c->st, c->btn);
}

static GtkWidget *make_click_button(AppState *st, const char *label,
                                    const char *css, char btn)
{
    GtkWidget *b = gtk_button_new_with_label(label);
    gtk_widget_add_css_class(b, "mousebtn");
    if (css) gtk_widget_add_css_class(b, css);
    gtk_widget_set_hexpand(b, TRUE);
    gtk_widget_set_can_focus(b, FALSE);

    BtnCtx *ctx = g_new0(BtnCtx, 1);
    ctx->st = st; ctx->btn = btn;

    /* A click controller in the capture phase gives us press/release before
     * the button's own handling; we leave "clicked" unconnected so nothing
     * fires twice. The visual depress still provides feedback. */
    GtkGesture *click = gtk_gesture_click_new();
    gtk_event_controller_set_propagation_phase(
        GTK_EVENT_CONTROLLER(click), GTK_PHASE_CAPTURE);
    g_signal_connect(click, "pressed",  G_CALLBACK(btn_pressed),  ctx);
    g_signal_connect(click, "released", G_CALLBACK(btn_released), ctx);
    g_signal_connect(click, "cancel",   G_CALLBACK(btn_cancel),   ctx);
    g_object_set_data_full(G_OBJECT(b), "btnctx", ctx, g_free);
    gtk_widget_add_controller(b, GTK_EVENT_CONTROLLER(click));
    return b;
}

/* ================================================================== *
 *  Option controls
 * ================================================================== */
static void on_sensitivity(GtkRange *r, gpointer ud)
{
    ((AppState *)ud)->sensitivity = gtk_range_get_value(r);
}
static void on_natural(GtkCheckButton *c, gpointer ud)
{
    ((AppState *)ud)->natural_scroll = gtk_check_button_get_active(c);
}
static void on_draglock(GtkCheckButton *c, gpointer ud)
{
    ((AppState *)ud)->drag_lock = gtk_check_button_get_active(c);
}

/* ================================================================== *
 *  Connection status poller (reads the daemon's status file).
 * ================================================================== */
static gboolean poll_status(gpointer ud)
{
    AppState *st = ud;
    char state[32] = "";

    FILE *f = fopen(STATUS_PATH, "r");
    if (f) {
        if (fgets(state, sizeof(state), f)) state[strcspn(state, "\n")] = '\0';
        fclose(f);
    }

    const char *text; const char *css;
    if (!f) {
        text = "btmouse not running";  css = "dot-off";
        st->daemon_up = FALSE;
        fifo_close(st);                 /* force a fresh open when it returns */
    } else if (strcmp(state, "CONNECTED") == 0) {
        text = "Host connected";        css = "dot-on";
        st->daemon_up = TRUE;
    } else {
        text = "Waiting for host\u2026"; css = "dot-wait";
        st->daemon_up = TRUE;
    }

    gtk_label_set_text(GTK_LABEL(st->status_label), text);
    gtk_widget_remove_css_class(st->status_dot, "dot-on");
    gtk_widget_remove_css_class(st->status_dot, "dot-wait");
    gtk_widget_remove_css_class(st->status_dot, "dot-off");
    gtk_widget_add_css_class(st->status_dot, css);

    if (!st->pad_dragging) gtk_widget_queue_draw(st->pad);
    return G_SOURCE_CONTINUE;
}

/* ================================================================== *
 *  Styling
 * ================================================================== */
static void load_css(void)
{
    static const char *CSS =
        "window { background:#0e1013; }"
        ".title { font-size:15px; font-weight:700; color:#e8eef7; }"
        ".subtle { color:#8a93a3; font-size:12px; }"
        ".status { color:#c7d0dc; font-size:13px; font-weight:600; }"
        ".dot { min-width:12px; min-height:12px; border-radius:8px;"
        "       background:#555; }"
        ".dot-on   { background:#39d353; box-shadow:0 0 8px #39d353; }"
        ".dot-wait { background:#e3b341; box-shadow:0 0 8px #e3b341; }"
        ".dot-off  { background:#f04747; }"
        ".mousebtn { font-size:16px; font-weight:700; color:#e8eef7;"
        "  padding:20px 8px; border-radius:14px; border:1px solid #2a2f3a;"
        "  background:linear-gradient(#232833,#1a1e26); }"
        ".mousebtn:hover  { background:linear-gradient(#2b313d,#1f242d); }"
        ".mousebtn:active { background:#3b82f6; color:#ffffff;"
        "                   border-color:#3b82f6; }"
        ".lmb:active { background:#3b82f6; }"
        ".rmb:active { background:#8b5cf6; border-color:#8b5cf6; }"
        ".mmb { min-width:64px; }"
        "scale { margin:0 4px; }";

    GtkCssProvider *p = gtk_css_provider_new();
    gtk_css_provider_load_from_string(p, CSS);
    gtk_style_context_add_provider_for_display(
        gdk_display_get_default(),
        GTK_STYLE_PROVIDER(p),
        GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
    g_object_unref(p);
}

/* ================================================================== *
 *  Window construction
 * ================================================================== */
static void on_activate(GtkApplication *app, gpointer ud)
{
    AppState *st = ud;
    load_css();

    GtkWidget *win = gtk_application_window_new(app);
    gtk_window_set_title(GTK_WINDOW(win), "btmouse \u2014 remote trackpad");
    gtk_window_set_default_size(GTK_WINDOW(win), 460, 620);

    GtkWidget *root = gtk_box_new(GTK_ORIENTATION_VERTICAL, 14);
    gtk_widget_set_margin_top(root, 16);
    gtk_widget_set_margin_bottom(root, 16);
    gtk_widget_set_margin_start(root, 16);
    gtk_widget_set_margin_end(root, 16);
    gtk_window_set_child(GTK_WINDOW(win), root);

    /* --- header: title + live status --- */
    GtkWidget *header = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 10);
    GtkWidget *title  = gtk_label_new("Remote Trackpad");
    gtk_widget_add_css_class(title, "title");
    gtk_widget_set_halign(title, GTK_ALIGN_START);
    gtk_widget_set_hexpand(title, TRUE);

    st->status_dot = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
    gtk_widget_add_css_class(st->status_dot, "dot");
    gtk_widget_add_css_class(st->status_dot, "dot-off");
    gtk_widget_set_valign(st->status_dot, GTK_ALIGN_CENTER);
    st->status_label = gtk_label_new("\u2026");
    gtk_widget_add_css_class(st->status_label, "status");

    gtk_box_append(GTK_BOX(header), title);
    gtk_box_append(GTK_BOX(header), st->status_dot);
    gtk_box_append(GTK_BOX(header), st->status_label);
    gtk_box_append(GTK_BOX(root), header);

    /* --- pad + scroll strip --- */
    GtkWidget *padrow = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 10);
    gtk_widget_set_vexpand(padrow, TRUE);

    st->pad = gtk_drawing_area_new();
    gtk_widget_set_hexpand(st->pad, TRUE);
    gtk_widget_set_vexpand(st->pad, TRUE);
    gtk_drawing_area_set_draw_func(GTK_DRAWING_AREA(st->pad),
                                   pad_draw, st, NULL);

    GtkGesture *pdrag = gtk_gesture_drag_new();
    g_signal_connect(pdrag, "drag-begin",  G_CALLBACK(pad_drag_begin),  st);
    g_signal_connect(pdrag, "drag-update", G_CALLBACK(pad_drag_update), st);
    g_signal_connect(pdrag, "drag-end",    G_CALLBACK(pad_drag_end),    st);
    gtk_widget_add_controller(st->pad, GTK_EVENT_CONTROLLER(pdrag));

    GtkEventController *pscroll = gtk_event_controller_scroll_new(
        GTK_EVENT_CONTROLLER_SCROLL_VERTICAL |
        GTK_EVENT_CONTROLLER_SCROLL_DISCRETE);
    g_signal_connect(pscroll, "scroll", G_CALLBACK(pad_scroll), st);
    gtk_widget_add_controller(st->pad, pscroll);

    GtkWidget *strip = gtk_drawing_area_new();
    gtk_widget_set_size_request(strip, 44, -1);
    gtk_widget_set_vexpand(strip, TRUE);
    gtk_drawing_area_set_draw_func(GTK_DRAWING_AREA(strip),
                                   strip_draw, st, NULL);
    GtkGesture *sdrag = gtk_gesture_drag_new();
    g_signal_connect(sdrag, "drag-begin",  G_CALLBACK(strip_drag_begin),  st);
    g_signal_connect(sdrag, "drag-update", G_CALLBACK(strip_drag_update), st);
    gtk_widget_add_controller(strip, GTK_EVENT_CONTROLLER(sdrag));

    gtk_box_append(GTK_BOX(padrow), st->pad);
    gtk_box_append(GTK_BOX(padrow), strip);
    gtk_box_append(GTK_BOX(root), padrow);

    GtkWidget *hint = gtk_label_new(
        "Drag to move \u00b7 tap to click \u00b7 strip or wheel to scroll");
    gtk_widget_add_css_class(hint, "subtle");
    gtk_box_append(GTK_BOX(root), hint);

    /* --- mouse buttons --- */
    GtkWidget *btnrow = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 10);
    GtkWidget *lmb = make_click_button(st, "Left",   "lmb", 'l');
    GtkWidget *mmb = make_click_button(st, "\u25cf", "mmb", 'm');
    GtkWidget *rmb = make_click_button(st, "Right",  "rmb", 'r');
    gtk_widget_set_tooltip_text(mmb, "Middle click");
    gtk_box_append(GTK_BOX(btnrow), lmb);
    gtk_box_append(GTK_BOX(btnrow), mmb);
    gtk_box_append(GTK_BOX(btnrow), rmb);
    gtk_box_append(GTK_BOX(root), btnrow);

    /* --- options: pointer speed, natural scroll, drag lock --- */
    GtkWidget *opt = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 10);
    GtkWidget *slbl = gtk_label_new("Speed");
    gtk_widget_add_css_class(slbl, "subtle");
    GtkWidget *scale = gtk_scale_new_with_range(
        GTK_ORIENTATION_HORIZONTAL, 0.5, 4.0, 0.1);
    gtk_range_set_value(GTK_RANGE(scale), st->sensitivity);
    gtk_widget_set_hexpand(scale, TRUE);
    gtk_scale_set_draw_value(GTK_SCALE(scale), FALSE);
    g_signal_connect(scale, "value-changed", G_CALLBACK(on_sensitivity), st);

    GtkWidget *nat = gtk_check_button_new_with_label("Natural scroll");
    gtk_widget_add_css_class(nat, "subtle");
    g_signal_connect(nat, "toggled", G_CALLBACK(on_natural), st);

    GtkWidget *lock = gtk_check_button_new_with_label("Drag lock");
    gtk_widget_add_css_class(lock, "subtle");
    gtk_widget_set_tooltip_text(lock,
        "Hold the left button down for the next pad drag (click-and-drag).");
    g_signal_connect(lock, "toggled", G_CALLBACK(on_draglock), st);

    gtk_box_append(GTK_BOX(opt), slbl);
    gtk_box_append(GTK_BOX(opt), scale);
    gtk_box_append(GTK_BOX(opt), nat);
    gtk_box_append(GTK_BOX(opt), lock);
    gtk_box_append(GTK_BOX(root), opt);

    /* status poll: start now and every 400 ms */
    poll_status(st);
    g_timeout_add(400, poll_status, st);

    gtk_window_present(GTK_WINDOW(win));
}

static void on_shutdown(GApplication *app, gpointer ud)
{
    (void)app;
    AppState *st = ud;
    if (st->holding_left) send_up(st, 'l');   /* never leave a button stuck */
    fifo_close(st);
}

int main(int argc, char **argv)
{
    signal(SIGPIPE, SIG_IGN);   /* writing to a dead pipe must not kill us */

    AppState st;
    memset(&st, 0, sizeof(st));
    st.fifo_fd     = -1;
    st.sensitivity = 1.8;

    GtkApplication *app =
        gtk_application_new("org.btmouse.gui", G_APPLICATION_DEFAULT_FLAGS);
    g_signal_connect(app, "activate", G_CALLBACK(on_activate), &st);
    g_signal_connect(app, "shutdown", G_CALLBACK(on_shutdown), &st);

    int status = g_application_run(G_APPLICATION(app), argc, argv);
    g_object_unref(app);
    return status;
}
