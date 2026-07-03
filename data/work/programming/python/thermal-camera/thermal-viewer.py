#!/usr/bin/env python3
"""
TC001 THERMAL // touch console  (v3)
------------------------------------
Modernized touchscreen viewer for the Topdon TC001 (256x192 YUYV
thermal camera).

The video pipeline is based on Les Wright's PyThermalCamera
(https://youtube.com/leslaboratory, 21 June 2023), which displays the
camera's ONBOARD-PROCESSED image stream (top half of each frame — the
TC001 denoises and auto-gains this in hardware, so it looks far
cleaner than raw sensor data), then applies:

    contrast -> bicubic 3x upscale -> blur -> colormap

Full credit to Les Wright for the pipeline and to LeoDJ (EEVblog) for
decoding the radiometric format.

What this build adds on top:
  * Touch UI: rotate (< / >), palette, contrast +/-, blur +/-,
    snapshot, record, HUD — no keyboard required.
  * Measurements come from the RADIOMETRIC half using full 16-bit
    temperatures (the reference script's avg-temp math only used the
    high byte reliably; this is exact).
  * Floating hot/cold spot labels, shown only when they exceed the
    scene average by a threshold (per the reference behaviour).
  * Live histogram, temperature scale bar (true frame min/max),
    FPS, clock, REC timer.
  * Touch coordinates remapped through the real window size, so taps
    land correctly when resized or fullscreen.

Keyboard fallback (mostly matching the reference script):
  a/z blur   f/v contrast   s/x spot-label threshold   m palette
  r/e rotate cw/ccw   k record   t stop   p snapshot   h hud
  w fullscreen   q quit

Run with  --test  to render a synthetic frame headlessly (no camera).
"""

import argparse
import os
import time

import cv2
import numpy as np

# ----------------------------------------------------------------------
# Theme
# ----------------------------------------------------------------------
BG        = (24, 18, 14)      # near-black, warm
PANEL     = (40, 32, 26)
PANEL_HI  = (58, 46, 36)
ACCENT    = (255, 214, 0)     # cyan  (BGR)
ACCENT2   = (60, 160, 255)    # amber (BGR)
REC_RED   = (70, 70, 235)
TEXT      = (235, 230, 220)
TEXT_DIM  = (150, 140, 128)
FONT      = cv2.FONT_HERSHEY_SIMPLEX

CANVAS_W, CANVAS_H = 1024, 600
RAIL_W             = 216                       # right-hand touch rail
VIEW_W, VIEW_H     = CANVAS_W - RAIL_W, CANVAS_H

SENSOR_W, SENSOR_H = 256, 192
SCALE = 3                                       # bicubic upscale factor

# Palette set from the reference script (incl. inverted rainbow)
COLORMAPS = [
    ("JET",         cv2.COLORMAP_JET,     False),
    ("HOT",         cv2.COLORMAP_HOT,     False),
    ("MAGMA",       cv2.COLORMAP_MAGMA,   False),
    ("INFERNO",     cv2.COLORMAP_INFERNO, False),
    ("PLASMA",      cv2.COLORMAP_PLASMA,  False),
    ("BONE",        cv2.COLORMAP_BONE,    False),
    ("SPRING",      cv2.COLORMAP_SPRING,  False),
    ("AUTUMN",      cv2.COLORMAP_AUTUMN,  False),
    ("VIRIDIS",     cv2.COLORMAP_VIRIDIS, False),
    ("PARULA",      cv2.COLORMAP_PARULA,  False),
    ("INV RAINBOW", cv2.COLORMAP_RAINBOW, True),   # True = BGR->RGB swap
]

ROTATIONS = [
    ("0",   None),
    ("90",  cv2.ROTATE_90_CLOCKWISE),
    ("180", cv2.ROTATE_180),
    ("270", cv2.ROTATE_90_COUNTERCLOCKWISE),
]

WIN = "Thermal"


def text(img, s, org, scale=0.45, color=TEXT, thick=1, font=FONT):
    cv2.putText(img, s, org, font, scale, color, thick, cv2.LINE_AA)


def outlined_text(img, s, org, scale=0.45, color=(0, 255, 255)):
    """Reference-script style label: black outline + colored fill."""
    cv2.putText(img, s, org, FONT, scale, (0, 0, 0), 2, cv2.LINE_AA)
    cv2.putText(img, s, org, FONT, scale, color, 1, cv2.LINE_AA)


def rounded_rect(img, p1, p2, color, r=10, fill=True, thick=1):
    x1, y1 = p1
    x2, y2 = p2
    if fill:
        cv2.rectangle(img, (x1 + r, y1), (x2 - r, y2), color, -1)
        cv2.rectangle(img, (x1, y1 + r), (x2, y2 - r), color, -1)
        for cx, cy in ((x1 + r, y1 + r), (x2 - r, y1 + r),
                       (x1 + r, y2 - r), (x2 - r, y2 - r)):
            cv2.circle(img, (cx, cy), r, color, -1, cv2.LINE_AA)
    else:
        cv2.line(img, (x1 + r, y1), (x2 - r, y1), color, thick, cv2.LINE_AA)
        cv2.line(img, (x1 + r, y2), (x2 - r, y2), color, thick, cv2.LINE_AA)
        cv2.line(img, (x1, y1 + r), (x1, y2 - r), color, thick, cv2.LINE_AA)
        cv2.line(img, (x2, y1 + r), (x2, y2 - r), color, thick, cv2.LINE_AA)
        for cx, cy, a in ((x1 + r, y1 + r, 180), (x2 - r, y1 + r, 270),
                          (x2 - r, y2 - r, 0), (x1 + r, y2 - r, 90)):
            cv2.ellipse(img, (cx, cy), (r, r), a, 0, 90, color, thick, cv2.LINE_AA)


class Button:
    def __init__(self, label, action, sub="", toggle=False):
        self.label, self.sub, self.action = label, sub, action
        self.toggle = toggle
        self.active = False
        self.rect = (0, 0, 0, 0)
        self.flash_until = 0.0

    def hit(self, x, y):
        bx, by, bw, bh = self.rect
        return bx <= x <= bx + bw and by <= y <= by + bh

    def draw(self, img):
        x, y, w, h = self.rect
        hot = time.time() < self.flash_until
        base = PANEL_HI if (hot or (self.toggle and self.active)) else PANEL
        rounded_rect(img, (x, y), (x + w, y + h), base, r=12)
        edge = ACCENT if (self.toggle and self.active) else (90, 76, 62)
        rounded_rect(img, (x, y), (x + w, y + h), edge, r=12, fill=False, thick=1)
        text(img, self.label, (x + 14, y + 27), 0.55,
             ACCENT if (self.toggle and self.active) else TEXT, 1)
        if self.sub:
            text(img, self.sub, (x + 14, y + h - 12), 0.42, TEXT_DIM, 1)


# ----------------------------------------------------------------------
# Camera helpers
# ----------------------------------------------------------------------
def find_thermal_camera():
    print("Probing for thermal camera stream...")
    for i in range(10):
        cap = cv2.VideoCapture(i, cv2.CAP_V4L2)
        if cap.isOpened():
            cap.set(cv2.CAP_PROP_CONVERT_RGB, 0.0)
            ret, frame = cap.read()
            cap.release()
            if ret and frame is not None:
                return i
    print("Warning: no working stream found, defaulting to 0.")
    return 0


def synthetic_frame(t=0.0):
    """Fake TC001 frame (image half + thermal half) for --test mode."""
    yy, xx = np.mgrid[0:SENSOR_H, 0:SENSOR_W].astype(np.float32)
    blob = 12.0 * np.exp(-(((xx - 150 - 30 * np.sin(t)) ** 2) +
                           ((yy - 80) ** 2)) / (2 * 22.0 ** 2))
    cold = 6.0 * np.exp(-(((xx - 60) ** 2) + ((yy - 140) ** 2)) / (2 * 16.0 ** 2))
    temps_c = 21.0 + 4.0 * (xx / SENSOR_W) + blob - cold
    raw = ((temps_c + 273.15) * 64.0).astype(np.uint16)
    thdata = np.zeros((SENSOR_H, SENSOR_W, 2), np.uint8)
    thdata[..., 0] = (raw & 0xFF).astype(np.uint8)
    thdata[..., 1] = (raw >> 8).astype(np.uint8)
    # Image half: emulate the camera's onboard AGC'd luma stream
    rng = np.random.default_rng(42)
    luma = cv2.normalize(temps_c, None, 20, 235, cv2.NORM_MINMAX)
    luma = (luma + rng.normal(0, 1.2, luma.shape)).clip(0, 255).astype(np.uint8)
    imdata = np.dstack([luma, np.full_like(luma, 128)])
    return np.vstack([imdata, thdata])


# ----------------------------------------------------------------------
# Main application
# ----------------------------------------------------------------------
class ThermalApp:
    def __init__(self, headless=False):
        self.headless = headless
        self.cmap_i = 3            # INFERNO default
        self.rot_i = 0
        self.blur = 0
        self.alpha = 1.0           # contrast, 0.0 - 3.0 (reference range)
        self.threshold = 2         # floating spot-label threshold, C
        self.hud = True
        self.fullscreen = False
        self.recording = False
        self.writer = None
        self.rec_start = 0.0
        self.snap_flash = 0.0
        self.fps = 0.0
        self._last_t = time.time()
        self._want_snap = False
        self.running = True
        self.save_dir = os.getcwd()

        self.btn_rot_l  = Button("< ROT", self.act_rot_ccw, sub="0 deg")
        self.btn_rot_r  = Button("ROT >", self.act_rot_cw,  sub="0 deg")
        self.btn_pal    = Button("PALETTE", self.act_cmap,
                                 sub=COLORMAPS[self.cmap_i][0])
        self.btn_con_d  = Button("CON -", self.act_con_dn)
        self.btn_con_u  = Button("CON +", self.act_con_up, sub="1.0")
        self.btn_blur_d = Button("BLR -", self.act_blur_dn)
        self.btn_blur_u = Button("BLR +", self.act_blur_up, sub="off")
        self.btn_snap   = Button("SNAPSHOT", self.act_snapshot, sub="save PNG")
        self.btn_rec    = Button("RECORD", self.act_record, sub="idle",
                                 toggle=True)
        self.btn_hud    = Button("HUD", self.act_hud, sub="on", toggle=True)
        self.btn_quit   = Button("QUIT", self.act_quit)
        self.btn_hud.active = True

        self.rows = [
            [self.btn_rot_l, self.btn_rot_r],
            [self.btn_pal],
            [self.btn_con_d, self.btn_con_u],
            [self.btn_blur_d, self.btn_blur_u],
            [self.btn_snap], [self.btn_rec], [self.btn_hud], [self.btn_quit],
        ]
        self.buttons = [b for row in self.rows for b in row]
        self.layout_buttons()

    # ---------------- actions ----------------
    def _rot_subs(self):
        s = ROTATIONS[self.rot_i][0] + " deg"
        self.btn_rot_l.sub = self.btn_rot_r.sub = s

    def act_rot_cw(self):
        self.rot_i = (self.rot_i + 1) % 4
        self._rot_subs()

    def act_rot_ccw(self):
        self.rot_i = (self.rot_i - 1) % 4
        self._rot_subs()

    def act_cmap(self):
        self.cmap_i = (self.cmap_i + 1) % len(COLORMAPS)
        self.btn_pal.sub = COLORMAPS[self.cmap_i][0]

    def act_con_up(self):
        self.alpha = round(min(3.0, self.alpha + 0.1), 1)
        self.btn_con_u.sub = f"{self.alpha:.1f}"

    def act_con_dn(self):
        self.alpha = round(max(0.0, self.alpha - 0.1), 1)
        self.btn_con_u.sub = f"{self.alpha:.1f}"

    def act_blur_up(self):
        self.blur = min(20, self.blur + 1)
        self.btn_blur_u.sub = str(self.blur) if self.blur else "off"

    def act_blur_dn(self):
        self.blur = max(0, self.blur - 1)
        self.btn_blur_u.sub = str(self.blur) if self.blur else "off"

    def act_snapshot(self):
        self._want_snap = True

    def act_record(self):
        if self.recording:
            self.recording = False
            if self.writer:
                self.writer.release()
                self.writer = None
            self.btn_rec.active = False
            self.btn_rec.sub = "idle"
        else:
            name = time.strftime("TC001-%Y%m%d-%H%M%S.avi")
            self.writer = cv2.VideoWriter(
                os.path.join(self.save_dir, name),
                cv2.VideoWriter_fourcc(*"XVID"), 25, (CANVAS_W, CANVAS_H))
            self.recording, self.rec_start = True, time.time()
            self.btn_rec.active = True

    def act_hud(self):
        self.hud = not self.hud
        self.btn_hud.active = self.hud
        self.btn_hud.sub = "on" if self.hud else "off"

    def act_quit(self):
        self.running = False

    # ---------------- layout / input ----------------
    def layout_buttons(self):
        pad = 12
        bh = (CANVAS_H - pad * (len(self.rows) + 1)) // len(self.rows)
        y = pad
        for row in self.rows:
            bw = (RAIL_W - pad * (len(row) + 1)) // len(row)
            x = CANVAS_W - RAIL_W + pad
            for b in row:
                b.rect = (x, y, bw, bh)
                x += bw + pad
            y += bh + pad

    def on_mouse(self, event, x, y, flags, _):
        if event != cv2.EVENT_LBUTTONDOWN:
            return
        # Remap window coordinates -> canvas coordinates so taps land
        # correctly when the window is resized or fullscreen.
        try:
            _, _, ww, wh = cv2.getWindowImageRect(WIN)
            if ww > 0 and wh > 0 and (ww != CANVAS_W or wh != CANVAS_H):
                x = int(x * CANVAS_W / ww)
                y = int(y * CANVAS_H / wh)
        except cv2.error:
            pass
        for b in self.buttons:
            if b.hit(x, y):
                b.flash_until = time.time() + 0.15
                b.action()
                return
        if x < VIEW_W:                      # tap the image = toggle HUD
            self.act_hud()

    # ---------------- frame pipeline ----------------
    def decode(self, frame):
        """Split a raw TC001 frame into the display image and temperatures.

        Display (reference-script pipeline): the camera's onboard-
        processed image stream — YUYV -> BGR, then contrast. The TC001
        denoises/auto-gains this half in hardware, which is why it
        looks much cleaner than anything rebuilt from raw sensor data.

        Measurements: the radiometric half, decoded as full 16-bit
        per-pixel temperatures (kelvin * 64).
        """
        imdata, thdata = np.array_split(frame, 2)
        temps = (thdata[..., 0].astype(np.uint16) +
                 thdata[..., 1].astype(np.uint16) * 256) / 64.0 - 273.15

        bgr = cv2.cvtColor(imdata, cv2.COLOR_YUV2BGR_YUYV)
        bgr = cv2.convertScaleAbs(bgr, alpha=self.alpha)
        return bgr, temps

    def render_heatmap(self, bgr):
        """Reference pipeline: bicubic upscale -> blur -> colormap."""
        big = cv2.resize(bgr, (SENSOR_W * SCALE, SENSOR_H * SCALE),
                         interpolation=cv2.INTER_CUBIC)
        if self.blur:
            big = cv2.blur(big, (self.blur, self.blur))
        _, cmap, invert = COLORMAPS[self.cmap_i]
        heat = cv2.applyColorMap(big, cmap)
        if invert:
            heat = cv2.cvtColor(heat, cv2.COLOR_BGR2RGB)
        return heat

    def compose(self, bgr, temps):
        canvas = np.full((CANVAS_H, CANVAS_W, 3), BG, np.uint8)

        heat = self.render_heatmap(bgr)

        cen_t = round(float(temps[SENSOR_H // 2, SENSOR_W // 2]), 1)
        mn, mx = float(temps.min()), float(temps.max())
        avg = float(temps.mean())
        my, mxx = np.unravel_index(np.argmax(temps), temps.shape)
        ny, nxx = np.unravel_index(np.argmin(temps), temps.shape)

        # center reticle + live center temp (reference style, restyled)
        cx, cy = heat.shape[1] // 2, heat.shape[0] // 2
        cv2.circle(heat, (cx, cy), 12, ACCENT, 1, cv2.LINE_AA)
        for x1, y1, x2, y2 in ((cx - 20, cy, cx - 8, cy), (cx + 8, cy, cx + 20, cy),
                               (cx, cy - 20, cx, cy - 8), (cx, cy + 8, cx, cy + 20)):
            cv2.line(heat, (x1, y1), (x2, y2), ACCENT, 1, cv2.LINE_AA)
        outlined_text(heat, f"{cen_t} C", (cx + 12, cy - 12), 0.45, ACCENT)

        # floating hot/cold spots, only when they stand out from the
        # scene average by more than the threshold (reference behaviour)
        if mx > avg + self.threshold:
            p = (int(mxx) * SCALE, int(my) * SCALE)
            cv2.circle(heat, p, 5, (0, 0, 0), 2, cv2.LINE_AA)
            cv2.circle(heat, p, 5, (0, 0, 255), -1, cv2.LINE_AA)
            outlined_text(heat, f"{mx:.1f} C", (p[0] + 10, p[1] + 5), 0.45)
        if mn < avg - self.threshold:
            p = (int(nxx) * SCALE, int(ny) * SCALE)
            cv2.circle(heat, p, 5, (0, 0, 0), 2, cv2.LINE_AA)
            cv2.circle(heat, p, 5, (255, 0, 0), -1, cv2.LINE_AA)
            outlined_text(heat, f"{mn:.1f} C", (p[0] + 10, p[1] + 5), 0.45)

        rot = ROTATIONS[self.rot_i][1]
        if rot is not None:
            heat = cv2.rotate(heat, rot)

        # fit into view area, leaving room for the scale bar
        SCALE_W = 46
        vh, vw = heat.shape[:2]
        k = min((VIEW_W - 24 - SCALE_W) / vw, (VIEW_H - 24) / vh)
        heat = cv2.resize(heat, (int(vw * k), int(vh * k)),
                          interpolation=cv2.INTER_LINEAR)
        vh, vw = heat.shape[:2]
        ox = (VIEW_W - SCALE_W - vw) // 2 + SCALE_W
        oy = (VIEW_H - vh) // 2
        canvas[oy:oy + vh, ox:ox + vw] = heat
        rounded_rect(canvas, (ox - 1, oy - 1), (ox + vw, oy + vh),
                     (90, 76, 62), r=4, fill=False)

        # corner brackets
        L = 26
        for (bx, by, dx, dy) in ((ox, oy, 1, 1), (ox + vw, oy, -1, 1),
                                 (ox, oy + vh, 1, -1), (ox + vw, oy + vh, -1, -1)):
            cv2.line(canvas, (bx, by), (bx + dx * L, by), ACCENT, 2, cv2.LINE_AA)
            cv2.line(canvas, (bx, by), (bx, by + dy * L), ACCENT, 2, cv2.LINE_AA)

        if self.hud:
            self.draw_scale_bar(canvas, (mn, mx), oy, vh)
            self.draw_status(canvas, cen_t, mn, mx, avg, ox, oy)
            self.draw_histogram(canvas, temps)

        for b in self.buttons:
            b.draw(canvas)

        if time.time() < self.snap_flash:
            cv2.rectangle(canvas, (0, 0), (VIEW_W, CANVAS_H),
                          (255, 255, 255), 6)
        return canvas, cen_t

    def draw_scale_bar(self, canvas, temp_range, oy, vh):
        """Vertical palette ramp labelled with the frame's true min/max."""
        lo, hi = temp_range
        bx, bw = 14, 16
        by, bh = oy + 24, vh - 48
        if bh < 40:
            return
        ramp = np.linspace(255, 0, bh, dtype=np.uint8).reshape(-1, 1)
        _, cmap, invert = COLORMAPS[self.cmap_i]
        bar = cv2.applyColorMap(ramp, cmap)
        if invert:
            bar = cv2.cvtColor(bar, cv2.COLOR_BGR2RGB)
        canvas[by:by + bh, bx:bx + bw] = bar
        rounded_rect(canvas, (bx - 1, by - 1), (bx + bw, by + bh),
                     (90, 76, 62), r=2, fill=False)
        text(canvas, f"{hi:.1f}", (bx - 4, by - 8), 0.42, TEXT)
        text(canvas, f"{lo:.1f}", (bx - 4, by + bh + 16), 0.42, ACCENT2)
        for f in (0.25, 0.5, 0.75):
            ty = int(by + bh * f)
            cv2.line(canvas, (bx + bw, ty), (bx + bw + 4, ty),
                     TEXT_DIM, 1, cv2.LINE_AA)

    def draw_status(self, canvas, cen_t, mn, mx, avg, ox, oy):
        strip = canvas[oy + 6:oy + 34, ox + 8:ox + 8 + 640]
        strip[:] = (strip * 0.35).astype(np.uint8)
        text(canvas, f"CTR {cen_t:5.1f}C", (ox + 16, oy + 26), 0.55, ACCENT, 1)
        text(canvas, f"MAX {mx:5.1f}", (ox + 150, oy + 26), 0.5, (255, 255, 255))
        text(canvas, f"MIN {mn:5.1f}", (ox + 260, oy + 26), 0.5, ACCENT2)
        text(canvas, f"AVG {avg:5.1f}", (ox + 370, oy + 26), 0.5, TEXT_DIM)
        text(canvas, f"THR {self.threshold}C", (ox + 480, oy + 26), 0.5, TEXT_DIM)
        text(canvas, f"{self.fps:4.1f} FPS", (ox + 560, oy + 26), 0.5, TEXT_DIM)

        text(canvas, time.strftime("%H:%M:%S"), (ox + 16, CANVAS_H - 20),
             0.55, TEXT_DIM)
        if self.recording:
            el = time.strftime("%H:%M:%S", time.gmtime(time.time() - self.rec_start))
            if int(time.time() * 2) % 2:
                cv2.circle(canvas, (ox + 120, CANVAS_H - 26), 7, REC_RED, -1,
                           cv2.LINE_AA)
            text(canvas, "REC " + el, (ox + 136, CANVAS_H - 20), 0.55, REC_RED)
            self.btn_rec.sub = el

    def draw_histogram(self, canvas, temps):
        w, h = 250, 120
        x0 = VIEW_W - w - 18
        y0 = CANVAS_H - h - 16
        panel = canvas[y0:y0 + h, x0:x0 + w]
        panel[:] = (panel * 0.25 + np.array(PANEL) * 0.75).astype(np.uint8)
        rounded_rect(canvas, (x0, y0), (x0 + w, y0 + h), (90, 76, 62),
                     r=8, fill=False)
        text(canvas, "THERMAL DISTRIBUTION", (x0 + 10, y0 + 18), 0.4, TEXT_DIM)

        bins = 48
        hist, edges = np.histogram(temps, bins=bins)
        peak = max(int(hist.max()), 1)
        bw = (w - 24) / bins
        _, cmap, invert = COLORMAPS[self.cmap_i]
        ramp = cv2.applyColorMap(
            np.linspace(0, 255, bins, dtype=np.uint8).reshape(1, -1), cmap)
        if invert:
            ramp = cv2.cvtColor(ramp, cv2.COLOR_BGR2RGB)
        ramp = ramp[0]
        base_y = y0 + h - 26
        for i in range(bins):
            bh = int((hist[i] / peak) * (h - 58))
            bx = int(x0 + 12 + i * bw)
            c = tuple(int(v) for v in ramp[i])
            cv2.rectangle(canvas, (bx, base_y - bh),
                          (bx + max(int(bw) - 1, 1), base_y), c, -1)
        text(canvas, f"{edges[0]:.0f}C", (x0 + 10, y0 + h - 8), 0.4, TEXT_DIM)
        text(canvas, f"{edges[-1]:.0f}C", (x0 + w - 48, y0 + h - 8), 0.4, TEXT_DIM)

    # ---------------- run loops ----------------
    def tick_fps(self):
        now = time.time()
        dt = now - self._last_t
        self._last_t = now
        if dt > 0:
            inst = 1.0 / dt
            self.fps = inst if self.fps == 0 else self.fps * 0.9 + inst * 0.1

    def handle_snapshot(self, canvas):
        if self._want_snap:
            self._want_snap = False
            name = time.strftime("TC001-%Y%m%d-%H%M%S.png")
            cv2.imwrite(os.path.join(self.save_dir, name), canvas)
            self.snap_flash = time.time() + 0.25
            self.btn_snap.sub = name[-19:]

    def run_test(self):
        frame = synthetic_frame()
        bgr, temps = self.decode(frame)
        self.tick_fps()
        canvas, _ = self.compose(bgr, temps)
        out = os.path.join(self.save_dir, "test_frame.png")
        cv2.imwrite(out, canvas)
        print("wrote", out)

    def run(self):
        dev = find_thermal_camera()
        print(f"Auto-detected device: /dev/video{dev}")
        cap = cv2.VideoCapture(dev, cv2.CAP_V4L2)
        cap.set(cv2.CAP_PROP_CONVERT_RGB, 0.0)

        cv2.namedWindow(WIN, cv2.WINDOW_GUI_NORMAL)
        cv2.resizeWindow(WIN, CANVAS_W, CANVAS_H)
        cv2.setMouseCallback(WIN, self.on_mouse)

        while self.running and cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                print("Camera disconnected or buffer error")
                break
            bgr, temps = self.decode(frame)
            self.tick_fps()
            canvas, _ = self.compose(bgr, temps)
            self.handle_snapshot(canvas)
            if self.recording and self.writer:
                self.writer.write(canvas)

            cv2.imshow(WIN, canvas)
            key = cv2.waitKey(1) & 0xFF
            if key == ord("q"):
                break
            elif key == ord("r"):
                self.act_rot_cw()
            elif key == ord("e"):
                self.act_rot_ccw()
            elif key == ord("m"):
                self.act_cmap()
            elif key == ord("a"):
                self.act_blur_up()
            elif key == ord("z"):
                self.act_blur_dn()
            elif key == ord("f"):
                self.act_con_up()
            elif key == ord("v"):
                self.act_con_dn()
            elif key == ord("s"):
                self.threshold += 1
            elif key == ord("x"):
                self.threshold = max(0, self.threshold - 1)
            elif key == ord("p"):
                self.act_snapshot()
            elif key == ord("k") and not self.recording:
                self.act_record()
            elif key == ord("t") and self.recording:
                self.act_record()
            elif key == ord("h"):
                self.act_hud()
            elif key == ord("w"):
                self.fullscreen = not self.fullscreen
                cv2.setWindowProperty(
                    WIN, cv2.WND_PROP_FULLSCREEN,
                    cv2.WINDOW_FULLSCREEN if self.fullscreen
                    else cv2.WINDOW_NORMAL)

        if self.writer:
            self.writer.release()
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="TC001 thermal touch console")
    ap.add_argument("--test", action="store_true",
                    help="render one synthetic frame to test_frame.png and exit")
    args = ap.parse_args()

    app = ThermalApp(headless=args.test)
    if args.test:
        app.run_test()
    else:
        app.run()
