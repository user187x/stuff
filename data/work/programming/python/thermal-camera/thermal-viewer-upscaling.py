#!/usr/bin/env python3
"""
TC001 THERMAL // touch console (v6) - Native 1080p & Lanczos4
------------------------------------
Modernized touchscreen viewer for the Topdon TC001 (256x192 YUYV
thermal camera).

Updates:
  - Fixed white gap by rendering the entire UI natively at 1920x1080.
  - Implements direct-to-viewport cv2.INTER_LANCZOS4 upscaling.
  - Upright text regardless of stream rotation.
  - Unsharp Mask "Sharpen" component.
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
BG        = (24, 18, 14)      
PANEL     = (40, 32, 26)
PANEL_HI  = (58, 46, 36)
ACCENT    = (255, 214, 0)     
ACCENT2   = (60, 160, 255)    
REC_RED   = (70, 70, 235)
TEXT      = (235, 230, 220)
TEXT_DIM  = (150, 140, 128)
FONT      = cv2.FONT_HERSHEY_SIMPLEX

# Native 1080p Canvas - Eliminates white padding on 16:9 monitors
CANVAS_W, CANVAS_H = 1920, 1080
RAIL_W             = 380
VIEW_W, VIEW_H     = CANVAS_W - RAIL_W, CANVAS_H

# 256x192 General settings
width  = 256
height = 192

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

def outlined_text(img, s, org, scale=0.45, color=(0, 255, 255), thick=2):
    cv2.putText(img, s, org, FONT, scale, (0, 0, 0), thick + 1, cv2.LINE_AA)
    cv2.putText(img, s, org, FONT, scale, color, thick, cv2.LINE_AA)

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
        
        rounded_rect(img, (x, y), (x + w, y + h), base, r=20)
        edge = ACCENT if (self.toggle and self.active) else (90, 76, 62)
        rounded_rect(img, (x, y), (x + w, y + h), edge, r=20, fill=False, thick=2)
        
        text(img, self.label, (x + 25, y + 55), 1.0,
             ACCENT if (self.toggle and self.active) else TEXT, 2)
        if self.sub:
            text(img, self.sub, (x + 25, y + h - 20), 0.75, TEXT_DIM, 2)


class Slider:
    PAD = 32

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
        ty = y + h - 40
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
                     PANEL_HI if self.dragging else PANEL, r=20)
        rounded_rect(img, (x, y), (x + w, y + h), (90, 76, 62),
                     r=20, fill=False, thick=2)

        val = self.get()
        text(img, self.label, (x + 25, y + 45), 0.9, TEXT, 2)
        vtext = str(val) if val else self.off_label
        (tw_px, _), _ = cv2.getTextSize(vtext, FONT, 0.9, 2)
        text(img, vtext, (x + w - 25 - tw_px, y + 45), 0.9,
             ACCENT if val else TEXT_DIM, 2)

        tx, ty, tw = self._track()
        span = max(self.vmax - self.vmin, 1)
        cv2.line(img, (tx, ty), (tx + tw, ty), (90, 76, 62), 6, cv2.LINE_AA)
        
        step = max(1, span // 10)
        for i in range(self.vmin, self.vmax + 1, step):
            fx = int(tx + (i - self.vmin) / span * tw)
            cv2.line(img, (fx, ty - 8), (fx, ty + 8), TEXT_DIM, 2, cv2.LINE_AA)
        
        frac = (val - self.vmin) / span
        kx = int(tx + frac * tw)
        cv2.line(img, (tx, ty), (kx, ty), ACCENT, 6, cv2.LINE_AA)
        cv2.circle(img, (kx, ty), 16, ACCENT, -1, cv2.LINE_AA)
        cv2.circle(img, (kx, ty), 16, BG, 2, cv2.LINE_AA)


# ----------------------------------------------------------------------
# Camera helpers
# ----------------------------------------------------------------------
def open_capture(dev):
    cap = cv2.VideoCapture('/dev/video' + str(dev), cv2.CAP_V4L)
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


# ----------------------------------------------------------------------
# Main application
# ----------------------------------------------------------------------
class ThermalApp:
    def __init__(self, device=None, headless=False):
        self.device = device
        self.headless = headless

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
        pad = 20
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
        # With the native 1080p canvas, OpenCV automatically translates 
        # mouse clicks mapped to the 1920x1080 grid regardless of window size.
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

        if x < VIEW_W:
            self.act_hud()

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
        
    def _map_thermal_pt(self, col, row, raw_w, raw_h, target_w, target_h):
        if self.rot_i == 1:    # 90 CW
            rx, ry = raw_h - 1 - row, col
            rw, rh = raw_h, raw_w
        elif self.rot_i == 2:  # 180
            rx, ry = raw_w - 1 - col, raw_h - 1 - row
            rw, rh = raw_w, raw_h
        elif self.rot_i == 3:  # 270 CCW
            rx, ry = row, raw_w - 1 - col
            rw, rh = raw_h, raw_w
        else:                  # 0
            rx, ry = col, row
            rw, rh = raw_w, raw_h
            
        fx = int(rx * (target_w / max(1, rw)))
        fy = int(ry * (target_h / max(1, rh)))
        return fx, fy

    def render_heatmap(self, imdata):
        bgr = cv2.cvtColor(imdata, cv2.COLOR_YUV2BGR_YUYV)
        bgr = cv2.convertScaleAbs(bgr, alpha=self.alpha)
        
        # 1. Rotate BEFORE upscaling (keeps coordinates upright)
        rot_flag = ROTATIONS[self.rot_i][1]
        if rot_flag is not None:
            bgr = cv2.rotate(bgr, rot_flag)
            
        # 2. Lanczos4 Direct Upscale logic for 1080p Canvas Space
        raw_h, raw_w = bgr.shape[:2]
        SCALE_W = 60
        pad = 40
        max_w = VIEW_W - pad - SCALE_W
        max_h = VIEW_H - pad
        
        k = min(max_w / raw_w, max_h / raw_h)
        target_w = int(raw_w * k)
        target_h = int(raw_h * k)
        
        # Super-smooth scaling straight to target size
        bgr = cv2.resize(bgr, (target_w, target_h), interpolation=cv2.INTER_LANCZOS4)

        # 3. Unsharp Mask 
        if self.sharpen_val > 0:
            amount = self.sharpen_val / 2.0 
            blurred = cv2.GaussianBlur(bgr, (0, 0), 2.0)
            bgr = cv2.addWeighted(bgr, 1.0 + amount, blurred, -amount, 0)

        # 4. Blur
        if self.rad > 0:
            bgr = cv2.blur(bgr, (self.rad, self.rad))

        # 5. Colormap Apply
        _, cmap, invert = COLORMAPS[self.cmap_i]
        heatmap = cv2.applyColorMap(bgr, cmap)
        if invert:
            heatmap = cv2.cvtColor(heatmap, cv2.COLOR_BGR2RGB)
            
        return heatmap, target_w, target_h

    def compose(self, imdata, thdata):
        # Native 1080p canvas controls the window and eliminates whitespace padding entirely.
        canvas = np.full((CANVAS_H, CANVAS_W, 3), BG, np.uint8)

        heat, target_w, target_h = self.render_heatmap(imdata)
        m = self.measure(thdata)
        
        temps = (thdata[..., 0].astype(np.uint16) +
                 thdata[..., 1].astype(np.uint16) * 256) / 64.0 - 273.15

        cx, cy = self._map_thermal_pt(128, 96, width, height, target_w, target_h)
        
        cv2.circle(heat, (cx, cy), 18, ACCENT, 2, cv2.LINE_AA)
        for x1, y1, x2, y2 in ((cx - 30, cy, cx - 14, cy), (cx + 14, cy, cx + 30, cy),
                               (cx, cy - 30, cx, cy - 14), (cx, cy + 14, cx, cy + 30)):
            cv2.line(heat, (x1, y1), (x2, y2), ACCENT, 2, cv2.LINE_AA)
        outlined_text(heat, f"{self.tstr(m['temp'])} {self.unit}", (cx + 25, cy - 25), 0.85, ACCENT, thick=2)

        if m["maxtemp"] > m["avgtemp"] + self.threshold:
            px, py = self._map_thermal_pt(m["mcol"], m["mrow"], width, height, target_w, target_h)
            cv2.circle(heat, (px, py), 10, (0, 0, 0), 3)
            cv2.circle(heat, (px, py), 10, (0, 0, 255), -1)
            outlined_text(heat, f"{self.tstr(m['maxtemp'])} {self.unit}", (px + 18, py + 10), 0.85, thick=2)

        if m["mintemp"] < m["avgtemp"] - self.threshold:
            px, py = self._map_thermal_pt(m["lcol"], m["lrow"], width, height, target_w, target_h)
            cv2.circle(heat, (px, py), 10, (0, 0, 0), 3)
            cv2.circle(heat, (px, py), 10, (255, 0, 0), -1)
            outlined_text(heat, f"{self.tstr(m['mintemp'])} {self.unit}", (px + 18, py + 10), 0.85, thick=2)

        SCALE_W = 60
        vh, vw = heat.shape[:2]
        ox = (VIEW_W - SCALE_W - vw) // 2 + SCALE_W
        oy = (VIEW_H - vh) // 2
        
        canvas[oy:oy + vh, ox:ox + vw] = heat
        rounded_rect(canvas, (ox - 2, oy - 2), (ox + vw + 2, oy + vh + 2), (90, 76, 62), r=8, fill=False, thick=3)

        L = 50
        for (bx, by, dx, dy) in ((ox, oy, 1, 1), (ox + vw, oy, -1, 1),
                                 (ox, oy + vh, 1, -1), (ox + vw, oy + vh, -1, -1)):
            cv2.line(canvas, (bx, by), (bx + dx * L, by), ACCENT, 4, cv2.LINE_AA)
            cv2.line(canvas, (bx, by), (bx, by + dy * L), ACCENT, 4, cv2.LINE_AA)

        if self.hud:
            self.draw_scale_bar(canvas, (m["mintemp"], m["maxtemp"]), oy, vh)
            self.draw_status(canvas, m, temps, ox, oy)
            self.draw_histogram(canvas, temps, m["mintemp"], m["maxtemp"])

        for c in self.controls:
            c.draw(canvas)

        if time.time() < self.snap_flash:
            cv2.rectangle(canvas, (0, 0), (VIEW_W, CANVAS_H), (255, 255, 255), 10)
        return canvas

    def draw_scale_bar(self, canvas, temp_range, oy, vh):
        lo, hi = temp_range
        bx, bw = 30, 30
        by, bh = oy + 45, vh - 90
        
        ramp = np.linspace(255, 0, bh, dtype=np.uint8).reshape(-1, 1)
        _, cmap, invert = COLORMAPS[self.cmap_i]
        bar = cv2.applyColorMap(ramp, cmap)
        if invert:
            bar = cv2.cvtColor(bar, cv2.COLOR_BGR2RGB)
            
        canvas[by:by + bh, bx:bx + bw] = bar
        rounded_rect(canvas, (bx - 2, by - 2), (bx + bw + 2, by + bh + 2), (90, 76, 62), r=4, fill=False, thick=2)
        text(canvas, f"{self.tstr(hi, 0)}{self.unit}", (bx - 10, by - 15), 0.75, TEXT, thick=2)
        text(canvas, f"{self.tstr(lo, 0)}{self.unit}", (bx - 10, by + bh + 30), 0.75, ACCENT2, thick=2)
        
        for f in (0.25, 0.5, 0.75):
            ty = int(by + bh * f)
            cv2.line(canvas, (bx + bw, ty), (bx + bw + 8, ty), TEXT_DIM, 2, cv2.LINE_AA)

    def draw_status(self, canvas, m, temps, ox, oy):
        strip_y2 = min(oy + 60, CANVAS_H)
        strip_x2 = min(ox + 1200, CANVAS_W)
        if strip_y2 > oy + 12 and strip_x2 > ox + 15:
            strip = canvas[oy + 12:strip_y2, ox + 15:strip_x2]
            strip[:] = (strip * 0.35).astype(np.uint8)
            
        u = self.unit
        text(canvas, f"CTR {self.tstr(m['temp']):>5}{u}", (ox + 30, oy + 45), 0.9, ACCENT, 2)
        text(canvas, f"MAX {self.tstr(m['maxtemp']):>5}", (ox + 270, oy + 45), 0.85, (255, 255, 255), 2)
        text(canvas, f"MIN {self.tstr(m['mintemp']):>5}", (ox + 470, oy + 45), 0.85, ACCENT2, 2)
        text(canvas, f"AVG {self.tstr(m['avgtemp']):>5}", (ox + 670, oy + 45), 0.85, TEXT_DIM, 2)
        text(canvas, f"THR {self.threshold}C", (ox + 870, oy + 45), 0.85, TEXT_DIM, 2)
        text(canvas, f"{self.fps:4.1f} FPS", (ox + 1070, oy + 45), 0.85, TEXT_DIM, 2)

        text(canvas, time.strftime("%H:%M:%S"), (ox + 30, CANVAS_H - 35), 1.0, TEXT_DIM, 2)
        if self.recording:
            el = time.strftime("%H:%M:%S", time.gmtime(time.time() - self.rec_start))
            if int(time.time() * 2) % 2:
                cv2.circle(canvas, (ox + 220, CANVAS_H - 45), 12, REC_RED, -1, cv2.LINE_AA)
            text(canvas, "REC " + el, (ox + 245, CANVAS_H - 35), 1.0, REC_RED, 2)
            self.btn_rec.sub = el

    def draw_histogram(self, canvas, temps, lo=None, hi=None):
        w, h = 450, 220
        x0 = max(20, VIEW_W - w - 40)
        y0 = max(20, CANVAS_H - h - 40)
        
        panel = canvas[y0:y0 + h, x0:x0 + w]
        panel[:] = (panel * 0.25 + np.array(PANEL) * 0.75).astype(np.uint8)
        rounded_rect(canvas, (x0, y0), (x0 + w, y0 + h), (90, 76, 62), r=15, fill=False, thick=2)
        text(canvas, "THERMAL DISTRIBUTION", (x0 + 20, y0 + 35), 0.7, TEXT_DIM, 2)

        bins = 48
        rng = (lo, hi) if (lo is not None and hi is not None and hi > lo) else None
        hist, edges = np.histogram(temps, bins=bins, range=rng)
        peak = max(int(hist.max()), 1)
        bw = (w - 40) / bins
        
        _, cmap, invert = COLORMAPS[self.cmap_i]
        ramp = cv2.applyColorMap(
            np.linspace(0, 255, bins, dtype=np.uint8).reshape(1, -1), cmap)
        if invert:
            ramp = cv2.cvtColor(ramp, cv2.COLOR_BGR2RGB)
        ramp = ramp[0]
        
        base_y = y0 + h - 45
        for i in range(bins):
            bh = int((hist[i] / peak) * (h - 100))
            bx = int(x0 + 20 + i * bw)
            c = tuple(int(v) for v in ramp[i])
            cv2.rectangle(canvas, (bx, base_y - bh),
                          (bx + max(int(bw) - 1, 1), base_y), c, -1)
                          
        text(canvas, f"{self.tstr(edges[0], 0)}{self.unit}",
             (x0 + 20, y0 + h - 15), 0.7, TEXT_DIM, 2)
        text(canvas, f"{self.tstr(edges[-1], 0)}{self.unit}",
             (x0 + w - 90, y0 + h - 15), 0.7, TEXT_DIM, 2)

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

    def run(self):
        dev = self.device if self.device is not None else find_thermal_camera()
        print(f"Using device: /dev/video{dev}")
        cap = open_capture(dev)

        cv2.namedWindow(WIN, cv2.WINDOW_NORMAL)
        cv2.setMouseCallback(WIN, self.on_mouse)

        while self.running and cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                print("Camera disconnected or buffer error")
                break

            imdata, thdata = np.array_split(frame, 2)
            self.tick_fps()
            
            canvas = self.compose(imdata, thdata)
            self.handle_snapshot(canvas)
            
            if self.recording and self.writer:
                self.writer.write(canvas)

            cv2.imshow(WIN, canvas)
            
            key = cv2.waitKey(1) & 0xFF
            if key == ord("q"): break
            elif key == ord("r"): self.act_rot_cw()
            elif key == ord("e"): self.act_rot_ccw()
            elif key == ord("m"): self.act_cmap()
            elif key == ord("u"): self.act_unit()
            elif key == ord("a"): self.set_blur(self.rad + 1)
            elif key == ord("z"): self.set_blur(self.rad - 1)
            elif key == ord("d"): self.set_sharpen(self.sharpen_val + 1)
            elif key == ord("c"): self.set_sharpen(self.sharpen_val - 1)
            elif key == ord("f"): self.act_con_up()
            elif key == ord("v"): self.act_con_dn()
            elif key == ord("s"): self.threshold += 1
            elif key == ord("x"): self.threshold = max(0, self.threshold - 1)
            elif key == ord("p"): self.act_snapshot()
            elif key == ord("k") and not self.recording: self.act_record()
            elif key == ord("t") and self.recording: self.act_record()
            elif key == ord("h"): self.act_hud()
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
                    help="Video Device number e.g. 0")
    args = ap.parse_args()

    app = ThermalApp(device=args.device)
    app.run()
