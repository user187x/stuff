#!/usr/bin/env python3
"""
TC001 THERMAL // touch console  (v4) - Responsive UI Update
------------------------------------
Modernized touchscreen viewer for the Topdon TC001 (256x192 YUYV
thermal camera).

Updates:
  - Dynamic UI resizing
  - Upright text regardless of stream rotation
  - Unsharp Mask "Sharpen" component added
"""

import argparse
import io
import os
import time

import cv2
import numpy as np

# ----------------------------------------------------------------------
# Theme & Defaults
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

# Initial window scale, will dynamically resize
CANVAS_W, CANVAS_H = 1024, 600
RAIL_W             = 216

# 256x192 General settings
width  = 256
height = 192
scale  = 3
newWidth, newHeight = width * scale, height * scale

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
    ("INV RAINBOW", cv2.COLORMAP_RAINBOW, True),
]

ROTATIONS = [
    ("0",   None),
    ("90",  cv2.ROTATE_90_CLOCKWISE),
    ("180", cv2.ROTATE_180),
    ("270", cv2.ROTATE_90_COUNTERCLOCKWISE),
]

WIN = "Thermal"


def is_raspberrypi():
    try:
        with io.open('/sys/firmware/devicetree/base/model', 'r') as m:
            if 'raspberry pi' in m.read().lower():
                return True
    except Exception:
        pass
    return False

isPi = is_raspberrypi()


def text(img, s, org, scale=0.45, color=TEXT, thick=1, font=FONT):
    cv2.putText(img, s, org, font, scale, color, thick, cv2.LINE_AA)

def outlined_text(img, s, org, scale=0.45, color=(0, 255, 255)):
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
        if w <= 0 or h <= 0: return
        hot = time.time() < self.flash_until
        base = PANEL_HI if (hot or (self.toggle and self.active)) else PANEL
        rounded_rect(img, (x, y), (x + w, y + h), base, r=12)
        edge = ACCENT if (self.toggle and self.active) else (90, 76, 62)
        rounded_rect(img, (x, y), (x + w, y + h), edge, r=12, fill=False, thick=1)
        text(img, self.label, (x + 14, y + 27), 0.55,
             ACCENT if (self.toggle and self.active) else TEXT, 1)
        if self.sub:
            text(img, self.sub, (x + 14, y + h - 12), 0.42, TEXT_DIM, 1)


class Slider:
    PAD = 18

    def __init__(self, label, get, setter, vmin, vmax, off_label="off"):
        self.label = label
        self.get = get
        self.set = setter
        self.vmin, self.vmax = vmin, vmax
        self.off_label = off_label
        self.rect = (0, 0, 0, 0)
        self.dragging = False

    def _track(self):
        x, y, w, h = self.rect
        tx = x + self.PAD
        tw = w - 2 * self.PAD
        ty = y + h - 20
        return tx, ty, tw

    def hit(self, px, py):
        x, y, w, h = self.rect
        return x <= px <= x + w and y <= py <= y + h

    def value_from_x(self, px):
        tx, _, tw = self._track()
        frac = min(1.0, max(0.0, (px - tx) / max(tw, 1)))
        return int(round(self.vmin + frac * (self.vmax - self.vmin)))

    def set_from_x(self, px):
        self.set(self.value_from_x(px))

    def draw(self, img):
        x, y, w, h = self.rect
        if w <= 0 or h <= 0: return
        rounded_rect(img, (x, y), (x + w, y + h),
                     PANEL_HI if self.dragging else PANEL, r=12)
        rounded_rect(img, (x, y), (x + w, y + h), (90, 76, 62),
                     r=12, fill=False, thick=1)

        val = self.get()
        text(img, self.label, (x + 14, y + 22), 0.5, TEXT, 1)
        vtext = str(val) if val else self.off_label
        (tw_px, _), _ = cv2.getTextSize(vtext, FONT, 0.5, 1)
        text(img, vtext, (x + w - 14 - tw_px, y + 22), 0.5,
             ACCENT if val else TEXT_DIM, 1)

        tx, ty, tw = self._track()
        span = max(self.vmax - self.vmin, 1)
        cv2.line(img, (tx, ty), (tx + tw, ty), (90, 76, 62), 3, cv2.LINE_AA)
        step = max(1, span // 10)
        for i in range(self.vmin, self.vmax + 1, step):
            fx = int(tx + (i - self.vmin) / span * tw)
            cv2.line(img, (fx, ty - 3), (fx, ty + 3), TEXT_DIM, 1, cv2.LINE_AA)
        
        frac = (val - self.vmin) / span
        kx = int(tx + frac * tw)
        cv2.line(img, (tx, ty), (kx, ty), ACCENT, 3, cv2.LINE_AA)
        cv2.circle(img, (kx, ty), 9, ACCENT, -1, cv2.LINE_AA)
        cv2.circle(img, (kx, ty), 9, BG, 1, cv2.LINE_AA)


# ----------------------------------------------------------------------
# Camera helpers
# ----------------------------------------------------------------------
def open_capture(dev):
    cap = cv2.VideoCapture('/dev/video' + str(dev), cv2.CAP_V4L)
    if isPi:
        cap.set(cv2.CAP_PROP_CONVERT_RGB, 0.0)
    else:
        cap.set(cv2.CAP_PROP_CONVERT_RGB, 0.0)
    return cap

def looks_like_tc001(frame):
    return (frame is not None and frame.ndim == 3 and
            frame.shape[0] == height * 2 and frame.shape[1] == width)

def find_thermal_camera():
    print("Probing for thermal camera stream...")
    for i in range(10):
        cap = open_capture(i)
        if cap.isOpened():
            ret, frame = cap.read()
            cap.release()
            if ret and looks_like_tc001(frame):
                return i
    print("Warning: no TC001-shaped stream found, defaulting to 0.")
    return 0

def synthetic_frame(t=0.0):
    yy, xx = np.mgrid[0:height, 0:width].astype(np.float32)
    blob = 12.0 * np.exp(-(((xx - 150 - 30 * np.sin(t)) ** 2) +
                           ((yy - 80) ** 2)) / (2 * 22.0 ** 2))
    cold = 6.0 * np.exp(-(((xx - 60) ** 2) + ((yy - 140) ** 2)) / (2 * 16.0 ** 2))
    temps_c = 21.0 + 4.0 * (xx / width) + blob - cold
    raw = ((temps_c + 273.15) * 64.0).astype(np.uint16)
    thdata = np.zeros((height, width, 2), np.uint8)
    thdata[..., 0] = (raw & 0xFF).astype(np.uint8)
    thdata[..., 1] = (raw >> 8).astype(np.uint8)
    rng = np.random.default_rng(42)
    luma = cv2.normalize(temps_c, None, 20, 235, cv2.NORM_MINMAX)
    luma = (luma + rng.normal(0, 1.2, luma.shape)).clip(0, 255).astype(np.uint8)
    imdata = np.dstack([luma, np.full_like(luma, 128)])
    return np.vstack([imdata, thdata])

# ----------------------------------------------------------------------
# Main application
# ----------------------------------------------------------------------
class ThermalApp:
    def __init__(self, device=None, headless=False):
        self.device = device
        self.headless = headless
        
        # Sizing defaults
        self.cw = CANVAS_W
        self.ch = CANVAS_H
        self.rail_w = RAIL_W
        self.vw = self.cw - self.rail_w
        self.vh = self.ch

        self.cmap_i = 3
        self.rot_i = 0
        self.rad = 0
        self.sharpen_val = 0
        self.alpha = 1.0
        self.threshold = 2
        self.unit = "C"
        self.hud = True
        self.fullscreen = False
        self.recording = False
        self.writer = None
        self.rec_start = 0.0
        self.rec_size = (self.cw, self.ch)
        self.snap_flash = 0.0
        self.fps = 0.0
        self._last_t = time.time()
        self._want_snap = False
        self.running = True
        self.save_dir = os.getcwd()

        self.btn_rot_l  = Button("< ROT", self.act_rot_ccw, sub="0 deg")
        self.btn_rot_r  = Button("ROT >", self.act_rot_cw,  sub="0 deg")
        self.btn_pal    = Button("PALETTE", self.act_cmap, sub=COLORMAPS[self.cmap_i][0])
        self.btn_unit   = Button("UNITS", self.act_unit, sub="deg C")
        self.btn_con_d  = Button("CON -", self.act_con_dn)
        self.btn_con_u  = Button("CON +", self.act_con_up, sub="1.0")
        
        self.sld_blur   = Slider("BLUR", get=lambda: self.rad, setter=self.set_blur, vmin=0, vmax=20)
        self.sld_sharp  = Slider("SHARPEN", get=lambda: self.sharpen_val, setter=self.set_sharpen, vmin=0, vmax=10)
        
        self.btn_snap   = Button("SNAPSHOT", self.act_snapshot, sub="save PNG")
        self.btn_rec    = Button("RECORD", self.act_record, sub="idle", toggle=True)
        self.btn_hud    = Button("HUD", self.act_hud, sub="on", toggle=True)
        self.btn_quit   = Button("QUIT", self.act_quit)
        self.btn_hud.active = True

        self.rows = [
            [self.btn_rot_l, self.btn_rot_r],
            [self.btn_pal, self.btn_unit],
            [self.btn_con_d, self.btn_con_u],
            [self.sld_blur],
            [self.sld_sharp],
            [self.btn_snap, self.btn_rec],
            [self.btn_hud, self.btn_quit],
        ]
        self.controls = [c for row in self.rows for c in row]
        self.buttons = [c for c in self.controls if isinstance(c, Button)]
        self.sliders = [c for c in self.controls if isinstance(c, Slider)]
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

    def act_unit(self):
        self.unit = "F" if self.unit == "C" else "C"
        self.btn_unit.sub = "deg F" if self.unit == "F" else "deg C"

    def set_blur(self, v):
        self.rad = int(min(20, max(0, v)))
        
    def set_sharpen(self, v):
        self.sharpen_val = int(min(10, max(0, v)))

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
            name = time.strftime("%Y%m%d--%H%M%S") + 'output.avi'
            # Lock the recording size to whatever the active dimensions are to avoid cv2 corruption
            self.rec_size = (self.cw, self.ch) 
            self.writer = cv2.VideoWriter(
                os.path.join(self.save_dir, name),
                cv2.VideoWriter_fourcc(*"XVID"), 25, self.rec_size)
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
        bh = (self.ch - pad * (len(self.rows) + 1)) // len(self.rows)
        y = pad
        for row in self.rows:
            bw = (self.rail_w - pad * (len(row) + 1)) // len(row)
            x = self.cw - self.rail_w + pad
            for b in row:
                b.rect = (x, y, bw, bh)
                x += bw + pad
            y += bh + pad
            
    def update_dynamic_layout(self):
        """Pulls resizing details from the OS to adjust internal canvas dimensions natively."""
        try:
            _, _, ww, wh = cv2.getWindowImageRect(WIN)
            if ww > 300 and wh > 200 and (ww != self.cw or wh != self.ch):
                self.cw, self.ch = ww, wh
                self.vw = self.cw - self.rail_w
                self.vh = self.ch
                self.layout_buttons()
        except cv2.error:
            pass

    def _remap(self, x, y):
        try:
            _, _, ww, wh = cv2.getWindowImageRect(WIN)
            if ww > 0 and wh > 0 and (ww != self.cw or wh != self.ch):
                return int(x * self.cw / ww), int(y * self.ch / wh)
        except cv2.error:
            pass
        return x, y

    def on_mouse(self, event, x, y, flags, _):
        x, y = self._remap(x, y)

        if event == cv2.EVENT_LBUTTONUP:
            for s in self.sliders:
                s.dragging = False
            return
        if event == cv2.EVENT_MOUSEMOVE:
            if flags & cv2.EVENT_FLAG_LBUTTON:
                for s in self.sliders:
                    if s.dragging:
                        s.set_from_x(x)
            return
        if event != cv2.EVENT_LBUTTONDOWN:
            return

        for s in self.sliders:
            if s.hit(x, y):
                s.dragging = True
                s.set_from_x(x)
                return

        for b in self.buttons:
            if b.hit(x, y):
                b.flash_until = time.time() + 0.15
                b.action()
                return

        if x < self.vw:
            self.act_hud()
            
    def _map_pt(self, x, y, w, h, rot):
        """Translates an (x,y) location into its new matrix coordinates after OpenCV rotation"""
        if rot == cv2.ROTATE_90_CLOCKWISE:
            return (h - 1 - y, x)
        elif rot == cv2.ROTATE_180:
            return (w - 1 - x, h - 1 - y)
        elif rot == cv2.ROTATE_90_COUNTERCLOCKWISE:
            return (y, w - 1 - x)
        return (x, y)

    # ------------------------------------------------------------------
    # Frame parsing, temperatures, imaging pipeline
    # ------------------------------------------------------------------
    def to_unit(self, c):
        return c * 9.0 / 5.0 + 32.0 if self.unit == "F" else c

    def tstr(self, c, dec=1):
        return f"{self.to_unit(c):.{dec}f}"

    def measure(self, thdata):
        hi = thdata[96][128][0]
        lo = thdata[96][128][1]
        lo = lo.astype(np.uint16) * 256
        rawtemp = hi + lo
        temp = (rawtemp / 64) - 273.15
        temp = round(temp, 2)

        lomax = thdata[..., 1].max()
        posmax = thdata[..., 1].argmax()
        mcol, mrow = divmod(posmax, width)
        himax = thdata[mcol][mrow][0]
        lomax = lomax.astype(np.uint16) * 256
        maxtemp = himax + lomax
        maxtemp = (maxtemp / 64) - 273.15
        maxtemp = round(maxtemp, 2)

        lomin = thdata[..., 1].min()
        posmin = thdata[..., 1].argmin()
        lcol, lrow = divmod(posmin, width)
        himin = thdata[lcol][lrow][0]
        lomin = lomin.astype(np.uint16) * 256
        mintemp = himin + lomin
        mintemp = (mintemp / 64) - 273.15
        mintemp = round(mintemp, 2)

        loavg = thdata[..., 1].mean()
        hiavg = thdata[..., 0].mean()
        loavg = loavg.astype(np.uint16) * 256
        avgtemp = loavg + hiavg
        avgtemp = (avgtemp / 64) - 273.15
        avgtemp = round(avgtemp, 2)

        return {
            "temp": temp,
            "maxtemp": maxtemp, "mrow": int(mrow), "mcol": int(mcol),
            "mintemp": mintemp, "lrow": int(lrow), "lcol": int(lcol),
            "avgtemp": avgtemp,
        }

    def render_heatmap(self, imdata):
        bgr = cv2.cvtColor(imdata, cv2.COLOR_YUV2BGR_YUYV)
        bgr = cv2.convertScaleAbs(bgr, alpha=self.alpha)
        bgr = cv2.resize(bgr, (newWidth, newHeight), interpolation=cv2.INTER_CUBIC)

        # Unsharp Mask algorithm functioning as 'Sharpen' slider (like Snapseed structure)
        if self.sharpen_val > 0:
            amount = self.sharpen_val / 2.0 
            blurred = cv2.GaussianBlur(bgr, (0, 0), 2.0)
            bgr = cv2.addWeighted(bgr, 1.0 + amount, blurred, -amount, 0)

        if self.rad > 0:
            bgr = cv2.blur(bgr, (self.rad, self.rad))

        _, cmap, invert = COLORMAPS[self.cmap_i]
        heatmap = cv2.applyColorMap(bgr, cmap)
        if invert:
            heatmap = cv2.cvtColor(heatmap, cv2.COLOR_BGR2RGB)
        return heatmap

    def compose(self, imdata, thdata):
        self.update_dynamic_layout()
        canvas = np.full((self.ch, self.cw, 3), BG, np.uint8)

        heat = self.render_heatmap(imdata)
        m = self.measure(thdata)

        temps = (thdata[..., 0].astype(np.uint16) +
                 thdata[..., 1].astype(np.uint16) * 256) / 64.0 - 273.15

        orig_h, orig_w = heat.shape[:2]
        
        # Apply stream rotation FIRST
        rot = ROTATIONS[self.rot_i][1]
        if rot is not None:
            heat = cv2.rotate(heat, rot)

        # Compute point transformations onto the rotated array for drawing features upright
        cx, cy = orig_w // 2, orig_h // 2
        cx_r, cy_r = self._map_pt(cx, cy, orig_w, orig_h, rot)
        
        cv2.circle(heat, (cx_r, cy_r), 12, ACCENT, 1, cv2.LINE_AA)
        for x1, y1, x2, y2 in ((cx_r - 20, cy_r, cx_r - 8, cy_r), (cx_r + 8, cy_r, cx_r + 20, cy_r),
                               (cx_r, cy_r - 20, cx_r, cy_r - 8), (cx_r, cy_r + 8, cx_r, cy_r + 20)):
            cv2.line(heat, (x1, y1), (x2, y2), ACCENT, 1, cv2.LINE_AA)
            
        # Write text upright
        outlined_text(heat, f"{self.tstr(m['temp'])} {self.unit}", (cx_r + 12, cy_r - 12), 0.45, ACCENT)

        if m["maxtemp"] > m["avgtemp"] + self.threshold:
            px, py = m["mcol"] * scale, m["mrow"] * scale
            px_r, py_r = self._map_pt(px, py, orig_w, orig_h, rot)
            cv2.circle(heat, (px_r, py_r), 5, (0, 0, 0), 2)
            cv2.circle(heat, (px_r, py_r), 5, (0, 0, 255), -1)
            outlined_text(heat, f"{self.tstr(m['maxtemp'])} {self.unit}", (px_r + 10, py_r + 5), 0.45)

        if m["mintemp"] < m["avgtemp"] - self.threshold:
            px, py = m["lcol"] * scale, m["lrow"] * scale
            px_r, py_r = self._map_pt(px, py, orig_w, orig_h, rot)
            cv2.circle(heat, (px_r, py_r), 5, (0, 0, 0), 2)
            cv2.circle(heat, (px_r, py_r), 5, (255, 0, 0), -1)
            outlined_text(heat, f"{self.tstr(m['mintemp'])} {self.unit}", (px_r + 10, py_r + 5), 0.45)

        # Map to adaptive viewport dimensions
        SCALE_W = 46
        vh, vw = heat.shape[:2]
        k = min((self.vw - 24 - SCALE_W) / max(vw, 1), (self.vh - 24) / max(vh, 1))
        heat = cv2.resize(heat, (int(vw * k), int(vh * k)), interpolation=cv2.INTER_LINEAR)
        vh, vw = heat.shape[:2]
        ox = (self.vw - SCALE_W - vw) // 2 + SCALE_W
        oy = (self.vh - vh) // 2
        canvas[oy:oy + vh, ox:ox + vw] = heat
        rounded_rect(canvas, (ox - 1, oy - 1), (ox + vw, oy + vh), (90, 76, 62), r=4, fill=False)

        L = 26
        for (bx, by, dx, dy) in ((ox, oy, 1, 1), (ox + vw, oy, -1, 1),
                                 (ox, oy + vh, 1, -1), (ox + vw, oy + vh, -1, -1)):
            cv2.line(canvas, (bx, by), (bx + dx * L, by), ACCENT, 2, cv2.LINE_AA)
            cv2.line(canvas, (bx, by), (bx, by + dy * L), ACCENT, 2, cv2.LINE_AA)

        if self.hud:
            self.draw_scale_bar(canvas, (m["mintemp"], m["maxtemp"]), oy, vh)
            self.draw_status(canvas, m, temps, ox, oy)
            self.draw_histogram(canvas, temps, m["mintemp"], m["maxtemp"])

        for c in self.controls:
            c.draw(canvas)

        if time.time() < self.snap_flash:
            cv2.rectangle(canvas, (0, 0), (self.vw, self.ch), (255, 255, 255), 6)
        return canvas

    def draw_scale_bar(self, canvas, temp_range, oy, vh):
        lo, hi = temp_range
        bx, bw = 14, 16
        by, bh = oy + 24, vh - 48
        if bh < 40: return
        ramp = np.linspace(255, 0, bh, dtype=np.uint8).reshape(-1, 1)
        _, cmap, invert = COLORMAPS[self.cmap_i]
        bar = cv2.applyColorMap(ramp, cmap)
        if invert:
            bar = cv2.cvtColor(bar, cv2.COLOR_BGR2RGB)
        canvas[by:by + bh, bx:bx + bw] = bar
        rounded_rect(canvas, (bx - 1, by - 1), (bx + bw, by + bh), (90, 76, 62), r=2, fill=False)
        text(canvas, f"{self.tstr(hi, 0)}{self.unit}", (bx - 4, by - 8), 0.42, TEXT)
        text(canvas, f"{self.tstr(lo, 0)}{self.unit}", (bx - 4, by + bh + 16), 0.42, ACCENT2)
        for f in (0.25, 0.5, 0.75):
            ty = int(by + bh * f)
            cv2.line(canvas, (bx + bw, ty), (bx + bw + 4, ty), TEXT_DIM, 1, cv2.LINE_AA)

    def draw_status(self, canvas, m, temps, ox, oy):
        strip_y2 = min(oy + 34, self.ch)
        strip_x2 = min(ox + 8 + 640, self.cw)
        if strip_y2 > oy + 6 and strip_x2 > ox + 8:
            strip = canvas[oy + 6:strip_y2, ox + 8:strip_x2]
            strip[:] = (strip * 0.35).astype(np.uint8)
        u = self.unit
        text(canvas, f"CTR {self.tstr(m['temp']):>5}{u}", (ox + 16, oy + 26), 0.55, ACCENT, 1)
        text(canvas, f"MAX {self.tstr(m['maxtemp']):>5}", (ox + 150, oy + 26), 0.5, (255, 255, 255))
        text(canvas, f"MIN {self.tstr(m['mintemp']):>5}", (ox + 260, oy + 26), 0.5, ACCENT2)
        text(canvas, f"AVG {self.tstr(m['avgtemp']):>5}", (ox + 370, oy + 26), 0.5, TEXT_DIM)
        text(canvas, f"THR {self.threshold}C", (ox + 480, oy + 26), 0.5, TEXT_DIM)
        text(canvas, f"{self.fps:4.1f} FPS", (ox + 560, oy + 26), 0.5, TEXT_DIM)

        text(canvas, time.strftime("%H:%M:%S"), (ox + 16, self.ch - 20), 0.55, TEXT_DIM)
        if self.recording:
            el = time.strftime("%H:%M:%S", time.gmtime(time.time() - self.rec_start))
            if int(time.time() * 2) % 2:
                cv2.circle(canvas, (ox + 120, self.ch - 26), 7, REC_RED, -1, cv2.LINE_AA)
            text(canvas, "REC " + el, (ox + 136, self.ch - 20), 0.55, REC_RED)
            self.btn_rec.sub = el

    def draw_histogram(self, canvas, temps, lo=None, hi=None):
        w, h = 250, 120
        x0 = max(10, self.vw - w - 18)
        y0 = max(10, self.ch - h - 16)
        
        # Don't draw if heavily squished
        if self.vw < 300 or self.vh < 200: return 
        
        panel = canvas[y0:y0 + h, x0:x0 + w]
        panel[:] = (panel * 0.25 + np.array(PANEL) * 0.75).astype(np.uint8)
        rounded_rect(canvas, (x0, y0), (x0 + w, y0 + h), (90, 76, 62), r=8, fill=False)
        text(canvas, "THERMAL DISTRIBUTION", (x0 + 10, y0 + 18), 0.4, TEXT_DIM)

        bins = 48
        rng = (lo, hi) if (lo is not None and hi is not None and hi > lo) else None
        hist, edges = np.histogram(temps, bins=bins, range=rng)
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
        text(canvas, f"{self.tstr(edges[0], 0)}{self.unit}",
             (x0 + 10, y0 + h - 8), 0.4, TEXT_DIM)
        text(canvas, f"{self.tstr(edges[-1], 0)}{self.unit}",
             (x0 + w - 52, y0 + h - 8), 0.4, TEXT_DIM)

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
            name = "TC001" + time.strftime("%Y%m%d-%H%M%S") + ".png"
            cv2.imwrite(os.path.join(self.save_dir, name), canvas)
            self.snap_flash = time.time() + 0.25
            self.btn_snap.sub = time.strftime("%H:%M:%S")

    def run_test(self):
        frame = synthetic_frame()
        imdata, thdata = np.array_split(frame, 2)
        self.tick_fps()
        canvas = self.compose(imdata, thdata)
        out = os.path.join(self.save_dir, "test_frame.png")
        cv2.imwrite(out, canvas)
        print("wrote", out)

    def run(self):
        dev = self.device if self.device is not None else find_thermal_camera()
        print(f"Using device: /dev/video{dev}")
        cap = open_capture(dev)

        cv2.namedWindow(WIN, cv2.WINDOW_GUI_NORMAL)
        cv2.resizeWindow(WIN, CANVAS_W, CANVAS_H)
        cv2.setMouseCallback(WIN, self.on_mouse)

        while self.running and cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                print("Camera disconnected or buffer error")
                break
            if not looks_like_tc001(frame):
                print("Unexpected frame shape", getattr(frame, "shape", None),
                      "- is this the right /dev/video device?")
                break

            imdata, thdata = np.array_split(frame, 2)
            self.tick_fps()
            canvas = self.compose(imdata, thdata)
            self.handle_snapshot(canvas)
            
            if self.recording and self.writer:
                if (self.cw, self.ch) != self.rec_size:
                    rec_frame = cv2.resize(canvas, self.rec_size)
                else:
                    rec_frame = canvas
                self.writer.write(rec_frame)

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
            elif key == ord("u"):
                self.act_unit()
            elif key == ord("a"):
                self.set_blur(self.rad + 1)
            elif key == ord("z"):
                self.set_blur(self.rad - 1)
            elif key == ord("d"):
                self.set_sharpen(self.sharpen_val + 1)
            elif key == ord("c"):
                self.set_sharpen(self.sharpen_val - 1)
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
    ap.add_argument("--device", type=int, default=None,
                    help="Video Device number e.g. 0, use v4l2-ctl --list-devices")
    ap.add_argument("--test", action="store_true",
                    help="render one synthetic frame to test_frame.png and exit")
    args = ap.parse_args()

    app = ThermalApp(device=args.device, headless=args.test)
    if args.test:
        app.run_test()
    else:
        app.run()
