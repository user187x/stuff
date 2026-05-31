package xxx.com.swing.animator;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.font.*;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;

/**
 * EdgeGlowAnimator — a general-purpose, zero-layout-change edge animation decorator for any Swing
 * component. This version corrects the default shape detection for standard components.
 *
 * @version 2.4
 */
public final class EdgeGlowAnimator {

  private EdgeGlowAnimator() {}

  public enum Style {
    AUTO,
    RECT,
    ROUNDED,
    PILL,
    OVAL
  }

  public static Handle decorate(JComponent comp) {
    Objects.requireNonNull(comp, "component");
    return Manager.INSTANCE.register(comp);
  }

  public static void setGlowingEffect(JComponent comp, boolean on) {
    decorate(comp).setGlowingEffect(on);
  }

  public static void remove(JComponent comp) {
    Manager.INSTANCE.unregister(comp);
  }

  public static void setGlowingText(JComponent comp, boolean on) {
    Handle h = decorate(comp);
    h.setTextMode(true);
    h.setGlowingEffect(on);
  }

  public static void setGlowingIcon(JComponent comp, boolean on) {
    Handle h = decorate(comp);
    h.setIconMode(true);
    h.setGlowingEffect(on);
  }

  // -------------------------- Public Handle ---------------------------

  public static final class Handle {
    private final WeakReference<JComponent> compRef;
    private final UUID id;

    private Handle(JComponent c, UUID id) {
      this.compRef = new WeakReference<>(c);
      this.id = id;
    }

    private Config cfg() {
      return Manager.INSTANCE.getConfig(id);
    }

    public void setGlowingEffect(boolean on) {
      Config c = cfg();
      if (c != null) {
        c.glowEnabled = on;
        Manager.INSTANCE.requestRepaint(compRef.get());
      }
    }

    public boolean isGlowing() {
      Config c = cfg();
      return c != null && c.glowEnabled;
    }

    public void setColor(Color color) {
      Config c = cfg();
      if (c != null) c.color = color != null ? color : c.color;
    }

    public void setThickness(int px) {
      Config c = cfg();
      if (c != null) c.thickness = Math.max(1, px);
    }

    public void setPadding(int px) {
      Config c = cfg();
      if (c != null) c.padding = Math.max(0, px);
    }

    public void setRoundness(int px) {
      Config c = cfg();
      if (c != null) c.roundness = Math.max(0, px);
    }

    public void setStyle(Style style) {
      Config c = cfg();
      if (c != null) c.style = style != null ? style : c.style;
    }

    public void setSpeed(float speed) {
      Config c = cfg();
      if (c != null) c.speed = Math.max(0.05f, Math.min(5f, speed));
    }

    public void setIntensity(float intensity) {
      Config c = cfg();
      if (c != null) c.intensity = Math.max(0f, Math.min(1f, intensity));
    }

    public void setTextMode(boolean on) {
      Config c = cfg();
      if (c != null) c.textMode = on;
    }

    public void setText(String text) {
      Config c = cfg();
      if (c != null) c.textOverride = text;
    }

    public void setFont(Font font) {
      Config c = cfg();
      if (c != null) c.fontOverride = font;
    }

    public void setIconMode(boolean on) {
      Config c = cfg();
      if (c != null) c.iconMode = on;
    }

    public void setIconUseAlpha(boolean on) {
      Config c = cfg();
      if (c != null) c.iconUseAlpha = on;
    }

    public void dispose() {
      Manager.INSTANCE.unregister(compRef.get());
    }
  }

  // -------------------------- Manager/Overlay ---------------------------

  private static final class Manager {
    static final Manager INSTANCE = new Manager();
    private final Map<UUID, Config> configs = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<JRootPane, Overlay> overlays = new WeakHashMap<>();
    private final Map<JComponent, UUID> ids = new WeakHashMap<>();

    Handle register(JComponent comp) {
      UUID id =
          ids.computeIfAbsent(
              comp,
              k -> {
                UUID newId = UUID.randomUUID();
                configs.put(newId, new Config(comp, newId));
                attachListeners(comp);
                return newId;
              });
      ensureOverlay(comp);
      return new Handle(comp, id);
    }

    void unregister(JComponent comp) {
      if (comp == null) return;
      UUID id = ids.remove(comp);
      if (id != null) {
        Config cfg = configs.remove(id);
        if (cfg != null) {
          cfg.dispose();
          requestRepaint(comp);
        }
      }
    }

    Config getConfig(UUID id) {
      return configs.get(id);
    }

    void requestRepaint(Component c) {
      if (c == null || !c.isShowing()) return;
      JRootPane rp = SwingUtilities.getRootPane(c);
      if (rp != null) {
        Overlay ov = overlays.get(rp);
        if (ov != null) {
          UUID id = ids.get(c);
          if (id != null) {
            Config cfg = configs.get(id);
            if (cfg != null) {
              Rectangle dirtyBounds = getDirtyBounds(cfg, (JComponent) c, ov);
              if (dirtyBounds != null) ov.repaint(dirtyBounds);
            }
          }
        }
      }
    }

    Rectangle getDirtyBounds(Config cfg, JComponent c, Component relativeTo) {
      Rectangle compBounds =
          SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), relativeTo);
      Rectangle specificBounds =
          (cfg.textMode)
              ? Painter.getRelativeTextBounds(c, cfg)
              : (cfg.iconMode)
                  ? Painter.getRelativeIconBounds(c, cfg)
                  : new Rectangle(c.getWidth(), c.getHeight());

      if (specificBounds == null) specificBounds = new Rectangle(c.getWidth(), c.getHeight());

      specificBounds.setLocation(compBounds.x + specificBounds.x, compBounds.y + specificBounds.y);
      specificBounds.grow(cfg.padding, cfg.padding);
      return specificBounds;
    }

    private void attachListeners(JComponent comp) {
      comp.addHierarchyListener(
          e -> {
            if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) ensureOverlay(comp);
            requestRepaint(comp);
          });
      comp.addComponentListener(
          new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
              requestRepaint(comp);
            }

            @Override
            public void componentResized(ComponentEvent e) {
              requestRepaint(comp);
            }
          });
    }

    private void ensureOverlay(JComponent comp) {
      JRootPane rp = SwingUtilities.getRootPane(comp);
      if (rp != null) overlays.computeIfAbsent(rp, k -> new Overlay(k, this));
    }
  }

  private static final class Overlay extends JComponent implements ActionListener {
    private final JRootPane root;
    private final Manager manager;
    private final Timer timer;
    private long lastTime = -1L;

    Overlay(JRootPane root, Manager manager) {
      this.root = root;
      this.manager = manager;
      setOpaque(false);
      setFocusable(false);

      JLayeredPane lp = root.getLayeredPane();
      lp.add(this, JLayeredPane.DRAG_LAYER);
      setBounds(lp.getBounds());
      lp.addComponentListener(
          new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
              setBounds(lp.getBounds());
            }
          });

      timer = new Timer(16, this);
      timer.setCoalesce(true);
      timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      long now = System.nanoTime();
      if (lastTime == -1L) lastTime = now;
      float deltaTime = (now - lastTime) / 1_000_000_000.0f;
      lastTime = now;

      Rectangle dirtyRect = null;
      boolean activeFound = false;

      List<Config> currentConfigs;
      synchronized (manager.configs) {
        currentConfigs = new ArrayList<>(manager.configs.values());
      }

      for (Config cfg : currentConfigs) {
        JComponent c = cfg.compRef.get();
        if (c == null || !c.isShowing() || SwingUtilities.getRootPane(c) != root) continue;

        if (cfg.glowEnabled) {
          activeFound = true;
          cfg.update(deltaTime);
          Rectangle dirtyBounds = manager.getDirtyBounds(cfg, c, this);
          if (dirtyBounds != null) {
            if (dirtyRect == null) dirtyRect = dirtyBounds;
            else dirtyRect.add(dirtyBounds);
          }
        }
      }

      if (activeFound && dirtyRect != null) {
        repaint(dirtyRect);
      } else if (!activeFound) {
        timer.stop();
        manager.overlays.remove(root);
        if (getParent() != null) getParent().remove(this);
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      Rectangle clipBounds = g.getClipBounds();

      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        List<Config> currentConfigs;
        synchronized (manager.configs) {
          currentConfigs = new ArrayList<>(manager.configs.values());
        }

        for (Config cfg : currentConfigs) {
          if (!cfg.glowEnabled) continue;
          JComponent c = cfg.compRef.get();
          if (c == null || !c.isShowing() || SwingUtilities.getRootPane(c) != root) continue;

          Rectangle bounds = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this);
          Rectangle repaintBounds = new Rectangle(bounds);
          repaintBounds.grow(cfg.padding, cfg.padding);

          if (!clipBounds.intersects(repaintBounds)) continue;

          if (cfg.textMode) {
            Shape s = Painter.resolveTextOutline(c, bounds, cfg, g2);
            if (s != null) Painter.paintTextGlow(g2, s, cfg);
          } else if (cfg.iconMode) {
            Shape s = Painter.resolveIconOutline(c, bounds, cfg, g2);
            if (s != null) Painter.paintTextGlow(g2, s, cfg);
          } else {
            cfg.ensureBand(bounds);
            Painter.paintEdgeGlow(g2, bounds, cfg);
          }
        }
      } finally {
        g2.dispose();
      }
    }
  }

  // Config and Particle classes remain unchanged
  private static final class Config {
    final WeakReference<JComponent> compRef;
    final UUID id;
    Color color = new Color(30, 144, 255);
    int thickness = 4, padding = 6, roundness = 14;
    Style style = Style.AUTO;
    float speed = 1.0f, intensity = 1.0f;
    boolean glowEnabled = false, textMode = false, iconMode = false, iconUseAlpha = false;
    String textOverride = null, lastTextKey = null, lastIconKey = null;
    Font fontOverride = null;
    float progress = 0f, dashPhase = 0f;
    int direction = 1;
    long lastDirChange = 0;
    final Random rand = new Random();
    Shape textOutline = null, iconOutline = null, cachedBand = null;
    List<Point2D.Double> textPathPts = null;
    double[] textCumLen = null;
    double textPathLen = 0d;
    Rectangle2D lastBounds = null;
    final List<Particle> particles = new ArrayList<>();
    long lastParticleSpawn = 0L;

    Config(JComponent c, UUID id) {
      this.compRef = new WeakReference<>(c);
      this.id = id;
    }

    void update(float deltaTime) {
      long now = System.currentTimeMillis();
      if (now - lastDirChange > 2000 + rand.nextInt(3000)) {
        direction *= -1;
        lastDirChange = now;
      }
      progress = (progress + 0.5f * direction * speed * deltaTime) % 1.0f;
      if (progress < 0) progress += 1.0f;
      dashPhase = (dashPhase + 30f * deltaTime) % 48f;
    }

    void ensureBand(Rectangle2D bounds) {
      if (cachedBand == null || !bounds.equals(lastBounds)) {
        cachedBand =
            Painter.createDistortedBand(bounds, thickness, style, roundness, System.nanoTime());
        lastBounds = (Rectangle2D) bounds.clone();
      }
    }

    void spawnAndRenderParticles(Graphics2D g2, Point2D.Double origin, long now_ns) {
      if (now_ns - lastParticleSpawn > 35_000_000L) {
        lastParticleSpawn = now_ns;
        int spawn = 1 + rand.nextInt(3);
        for (int i = 0; i < spawn && particles.size() < 50; i++) {
          double ang = rand.nextDouble() * Math.PI * 2, s = 0.4 + rand.nextDouble() * 0.9;
          particles.add(
              new Particle(
                  origin.x,
                  origin.y,
                  Math.cos(ang) * s,
                  Math.sin(ang) * s,
                  now_ns,
                  450 + rand.nextInt(400)));
        }
      }
      particles.removeIf(
          p -> {
            float t = (now_ns - p.birth) / (p.lifeMs * 1_000_000f);
            if (t >= 1f) return true;
            double ease = 1 - (1 - t) * (1 - t),
                x = p.x + p.vx * 60 * ease,
                y = p.y + p.vy * 60 * ease - 6 * ease;
            Painter.drawRadialOrb(
                g2,
                new Point2D.Double(x, y),
                3f + 3f * (1 - t),
                Color.WHITE,
                0.6f * (1 - t) * intensity);
            return false;
          });
    }

    void dispose() {
      particles.clear();
      cachedBand = null;
    }
  }

  private record Particle(double x, double y, double vx, double vy, long birth, float lifeMs) {}

  private static final class Painter {

    private static double arcForStyle(Rectangle2D r, Config c) {
      double h = r.getHeight(), w = r.getWidth();
      return switch (c.style) {
        case RECT -> 0;
        case OVAL, PILL -> Math.min(w, h);
        case ROUNDED -> Math.min(c.roundness, Math.min(w, h));
        case AUTO -> 0; // Default to sharp rectangle; rounded shapes must be explicit
      };
    }

    static Rectangle getRelativeIconBounds(JComponent c, Config cfg) {
      Icon icon =
          (c instanceof JLabel)
              ? ((JLabel) c).getIcon()
              : (c instanceof AbstractButton ? ((AbstractButton) c).getIcon() : null);
      if (icon == null) return null;

      Rectangle viewR = new Rectangle(c.getWidth(), c.getHeight());
      Insets ins = c.getInsets();
      viewR.x = ins.left;
      viewR.y = ins.top;
      viewR.width -= ins.left + ins.right;
      viewR.height -= ins.top + ins.bottom;

      Rectangle iconR = new Rectangle();
      SwingUtilities.layoutCompoundLabel(
          c, c.getFontMetrics(c.getFont()), "", icon, 0, 0, 0, 0, viewR, iconR, new Rectangle(), 0);
      return iconR;
    }

    static Rectangle getRelativeTextBounds(JComponent c, Config cfg) {
      String txt =
          (cfg.textOverride != null)
              ? cfg.textOverride
              : ((c instanceof JLabel)
                  ? ((JLabel) c).getText()
                  : (c instanceof AbstractButton ? ((AbstractButton) c).getText() : null));
      if (txt == null || txt.isEmpty()) return null;

      Icon icon =
          (c instanceof JLabel)
              ? ((JLabel) c).getIcon()
              : (c instanceof AbstractButton ? ((AbstractButton) c).getIcon() : null);
      Font font = (cfg.fontOverride != null) ? cfg.fontOverride : c.getFont();
      FontMetrics fm = c.getFontMetrics(font);

      Rectangle viewR = new Rectangle(c.getWidth(), c.getHeight());
      Insets ins = c.getInsets();
      viewR.x = ins.left;
      viewR.y = ins.top;
      viewR.width -= ins.left + ins.right;
      viewR.height -= ins.top + ins.bottom;

      Rectangle textR = new Rectangle();
      SwingUtilities.layoutCompoundLabel(
          c,
          fm,
          txt,
          icon,
          (c instanceof JLabel l)
              ? l.getVerticalAlignment()
              : ((AbstractButton) c).getVerticalAlignment(),
          (c instanceof JLabel l)
              ? l.getHorizontalAlignment()
              : ((AbstractButton) c).getHorizontalAlignment(),
          (c instanceof JLabel l)
              ? l.getVerticalTextPosition()
              : ((AbstractButton) c).getVerticalTextPosition(),
          (c instanceof JLabel l)
              ? l.getHorizontalTextPosition()
              : ((AbstractButton) c).getHorizontalTextPosition(),
          viewR,
          new Rectangle(),
          textR,
          (c instanceof JLabel l) ? l.getIconTextGap() : ((AbstractButton) c).getIconTextGap());
      return textR;
    }

    static Shape resolveIconOutline(JComponent c, Rectangle rOnOverlay, Config cfg, Graphics2D g2) {
      Rectangle iconR = getRelativeIconBounds(c, cfg);
      if (iconR == null || iconR.width <= 0 || iconR.height <= 0) return null;
      Icon icon = ((c instanceof JLabel) ? ((JLabel) c).getIcon() : ((AbstractButton) c).getIcon());

      int ix = rOnOverlay.x + iconR.x, iy = rOnOverlay.y + iconR.y;
      String key = ix + ":" + iy + ":" + iconR.width + ":" + iconR.height + ":" + cfg.iconUseAlpha;
      if (key.equals(cfg.lastIconKey)) return cfg.iconOutline;

      if (cfg.iconUseAlpha && icon instanceof ImageIcon ii) {
        cfg.iconOutline = buildAlphaOutline(ii.getImage(), ix, iy, iconR.width, iconR.height);
      } else {
        double arc =
            Math.min(Math.min(iconR.width, iconR.height) * 0.3, Math.max(8, cfg.roundness));
        cfg.iconOutline = new RoundRectangle2D.Double(ix, iy, iconR.width, iconR.height, arc, arc);
      }
      cfg.lastIconKey = key;
      return cfg.iconOutline;
    }

    static Shape resolveTextOutline(JComponent c, Rectangle rOnOverlay, Config cfg, Graphics2D g2) {
      Rectangle textR = getRelativeTextBounds(c, cfg);
      if (textR == null || textR.width <= 0) return null;

      Font font = (cfg.fontOverride != null) ? cfg.fontOverride : c.getFont();
      FontMetrics fm = c.getFontMetrics(font);
      String txt =
          (cfg.textOverride != null)
              ? cfg.textOverride
              : ((c instanceof JLabel) ? ((JLabel) c).getText() : ((AbstractButton) c).getText());
      if (txt.toLowerCase().startsWith("<html>")) txt = txt.replaceAll("<[^>]+>", "");

      int baseX = rOnOverlay.x + textR.x;
      int baseY = rOnOverlay.y + textR.y + fm.getAscent();
      return font.createGlyphVector(g2.getFontRenderContext(), txt).getOutline(baseX, baseY);
    }

    static void paintEdgeGlow(Graphics2D g2, Rectangle bounds, Config cfg) {
      long now_ms = System.currentTimeMillis(), now_ns = System.nanoTime();
      double arc = arcForStyle(bounds, cfg);
      Shape baseShape =
          new RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, arc, arc);

      g2.setComposite(AlphaComposite.SrcOver.derive(0.35f * cfg.intensity));
      g2.setColor(cfg.color.brighter());
      g2.draw(
          new BasicStroke(
                  Math.max(1f, cfg.thickness * 0.75f),
                  BasicStroke.CAP_ROUND,
                  BasicStroke.JOIN_ROUND,
                  10f,
                  new float[] {12f, 10f, 3f, 10f},
                  cfg.dashPhase)
              .createStrokedShape(baseShape));

      Shape oldClip = g2.getClip();
      if (cfg.cachedBand != null) g2.clip(cfg.cachedBand);

      double pulse = 0.5 * (1 + Math.sin(now_ms * 0.00284));
      Point2D.Double head = getPointOnRoundedPerimeter(bounds, cfg, cfg.progress);

      for (int i = 0; i < 9; i++) {
        double step = i / 9.0, p = (cfg.progress - step * 0.035 - 0.01 * pulse + 1.0) % 1.0;
        drawRadialOrb(
            g2,
            getPointOnRoundedPerimeter(bounds, cfg, p),
            (float) (14 + 8 * (1 - step) + 4 * pulse),
            cfg.color,
            (float) (0.26 * (1 - step) * (0.7 + 0.3 * pulse) * cfg.intensity));
      }

      drawRadialOrb(
          g2, head, (float) (18 + 10 * pulse), cfg.color.brighter(), 0.65f * cfg.intensity);
      double orbitT = (now_ms % 1200L) / 1200.0 * Math.PI * 2;
      drawRadialOrb(
          g2,
          new Point2D.Double(head.x + Math.cos(orbitT) * 10, head.y + Math.sin(orbitT) * 10),
          (float) (6 + 3 * pulse),
          Color.WHITE,
          0.55f * cfg.intensity);

      cfg.spawnAndRenderParticles(g2, head, now_ns);

      g2.setClip(oldClip);
      g2.setComposite(AlphaComposite.SrcOver.derive(0.18f * cfg.intensity));
      g2.setColor(cfg.color);
      g2.draw(
          new BasicStroke(
                  Math.max(2f, cfg.thickness * 2.2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
              .createStrokedShape(baseShape));
    }

    // Other helper methods remain unchanged
    public static Shape buildAlphaOutline(Image img, int x, int y, int w, int h) {
      if (img == null || w < 2 || h < 2) return null;
      BufferedImage bi = new BufferedImage(w, h, 2);
      Graphics g = bi.createGraphics();
      g.drawImage(img, 0, 0, w, h, null);
      g.dispose();
      Area a = new Area();
      int s = Math.max(1, Math.max(w, h) / 64);
      for (int i = 0; i < h; i += s) {
        for (int j = 0; j < w; j += s) {
          if ((bi.getRGB(j, i) >>> 24) > 64) a.add(new Area(new Rectangle(x + j, y + i, s, s)));
        }
      }
      return a.isEmpty() ? null : new BasicStroke(1.5f, 1, 1).createStrokedShape(a);
    }

    private static Point2D.Double getPointOnRoundedPerimeter(Rectangle2D r, Config c, double t) {
      double x = r.getX(),
          y = r.getY(),
          w = r.getWidth(),
          h = r.getHeight(),
          a = Math.min(arcForStyle(r, c) / 2.0, Math.min(w, h) / 2.0),
          sw = Math.max(0, w - 2 * a),
          sh = Math.max(0, h - 2 * a),
          q = Math.PI * a / 2.0,
          p = 2 * (sw + sh) + 4 * q,
          d = ((t % 1.0) + 1.0) % 1.0 * p;
      if (d < sw) return new Point2D.Double(x + a + d, y);
      d -= sw;
      if (d < q) {
        double an = -Math.PI / 2 + (d / q) * (Math.PI / 2);
        return new Point2D.Double(x + w - a + a * Math.cos(an), y + a + a * Math.sin(an));
      }
      d -= q;
      if (d < sh) return new Point2D.Double(x + w, y + a + d);
      d -= sh;
      if (d < q) {
        double an = (d / q) * (Math.PI / 2);
        return new Point2D.Double(x + w - a + a * Math.cos(an), y + h - a + a * Math.sin(an));
      }
      d -= q;
      if (d < sw) return new Point2D.Double(x + w - a - d, y + h);
      d -= sw;
      if (d < q) {
        double an = Math.PI / 2 + (d / q) * (Math.PI / 2);
        return new Point2D.Double(x + a + a * Math.cos(an), y + h - a + a * Math.sin(an));
      }
      d -= q;
      if (d < sh) return new Point2D.Double(x, y + h - a - d);
      d -= sh;
      double an = Math.PI + (d / q) * (Math.PI / 2);
      return new Point2D.Double(x + a + a * Math.cos(an), y + a + a * Math.sin(an));
    }

    static Shape createDistortedBand(Rectangle2D r, int th, Style st, int rn, long seed) {
      Random R = new Random(seed);
      int sam = 72;
      double pad = Math.max(2, th * 0.8), j = 2.5;
      List<Point2D.Double> pts = new ArrayList<>(sam);
      Config d = new Config(null, null);
      d.style = st;
      d.roundness = rn;
      for (int i = 0; i < sam; i++) pts.add(getPointOnRoundedPerimeter(r, d, (double) i / sam));
      GeneralPath p = new GeneralPath();
      Point2D.Double p0o =
          getNormalPoint(
              pts.get(0), pts.get(sam - 1), pts.get(1), pad + (R.nextDouble() * 2 - 1) * j);
      p.moveTo(p0o.x, p0o.y);
      for (int i = 0; i < sam; i++) {
        Point2D.Double po =
            getNormalPoint(
                pts.get(i),
                pts.get((i - 1 + sam) % sam),
                pts.get((i + 1) % sam),
                pad + (R.nextDouble() * 2 - 1) * j);
        p.lineTo(po.x, po.y);
      }
      p.closePath();
      Point2D.Double p0i =
          getNormalPoint(
              pts.get(0), pts.get(sam - 1), pts.get(1), -(pad + (R.nextDouble() * 2 - 1) * j));
      p.moveTo(p0i.x, p0i.y);
      for (int i = 0; i < sam; i++) {
        Point2D.Double pi =
            getNormalPoint(
                pts.get(i),
                pts.get((i - 1 + sam) % sam),
                pts.get((i + 1) % sam),
                -(pad + (R.nextDouble() * 2 - 1) * j));
        p.lineTo(pi.x, pi.y);
      }
      p.closePath();
      return new Area(p);
    }

    private static Point2D.Double getNormalPoint(
        Point2D.Double p, Point2D.Double pp, Point2D.Double pn, double d) {
      double dx = pn.x - pp.x, dy = pn.y - pp.y, l = Math.hypot(dx, dy);
      if (l == 0) l = 1;
      return new Point2D.Double(p.x - (dy / l) * d, p.y + (dx / l) * d);
    }

    static void drawRadialOrb(Graphics2D g, Point2D.Double c, float r, Color b, float a) {
      if (a <= 0.01f) return;
      Color i = new Color(b.getRed(), b.getGreen(), b.getBlue(), (int) (255 * Math.min(1f, a))),
          o = new Color(b.getRed(), b.getGreen(), b.getBlue(), 0);
      Paint old = g.getPaint();
      g.setPaint(new RadialGradientPaint(c, r, new float[] {0f, 1f}, new Color[] {i, o}));
      g.fill(new Ellipse2D.Double(c.x - r, c.y - r, r * 2, r * 2));
      g.setPaint(old);
    }

    static void ensureTextPath(Config c, Shape o) {
      String k = o.getBounds().toString();
      if (k.equals(c.lastTextKey)) return;
      c.lastTextKey = k;
      PathIterator it = o.getPathIterator(null, 1.0);
      List<Point2D.Double> pts = new ArrayList<>();
      double[] cs = new double[6];
      while (!it.isDone()) {
        int t = it.currentSegment(cs);
        if (t == 0 || t == 1) pts.add(new Point2D.Double(cs[0], cs[1]));
        it.next();
      }
      if (pts.size() < 2) {
        c.textPathLen = 0;
        return;
      }
      c.textPathPts = pts;
      c.textCumLen = new double[pts.size()];
      c.textPathLen = 0;
      for (int i = 1; i < pts.size(); i++) {
        c.textPathLen += pts.get(i - 1).distance(pts.get(i));
        c.textCumLen[i] = c.textPathLen;
      }
    }

    static Point2D.Double pointAtProgress(Config c, double t) {
      if (c.textPathLen <= 0) return new Point2D.Double();
      double tar = ((t % 1.0) + 1.0) % 1.0 * c.textPathLen;
      int i = Arrays.binarySearch(c.textCumLen, tar);
      if (i < 0) i = -i - 1;
      if (i <= 0) return c.textPathPts.get(0);
      if (i >= c.textCumLen.length) return c.textPathPts.get(c.textPathPts.size() - 1);
      Point2D.Double a = c.textPathPts.get(i - 1), b = c.textPathPts.get(i);
      double sl = c.textCumLen[i] - c.textCumLen[i - 1],
          l = (sl > 0) ? (tar - c.textCumLen[i - 1]) / sl : 0;
      return new Point2D.Double(a.x + (b.x - a.x) * l, a.y + (b.y - a.y) * l);
    }

    static void paintTextGlow(Graphics2D g, Shape o, Config c) {
      long ms = System.currentTimeMillis(), ns = System.nanoTime();
      ensureTextPath(c, o);
      if (c.textPathLen <= 0) return;
      g.setComposite(AlphaComposite.SrcOver.derive(0.35f * c.intensity));
      g.setColor(c.color.brighter());
      g.draw(
          new BasicStroke(
                  Math.max(1f, c.thickness * 0.65f),
                  1,
                  1,
                  10f,
                  new float[] {12f, 10f, 3f, 10f},
                  c.dashPhase)
              .createStrokedShape(o));
      Shape oc = g.getClip();
      g.clip(new BasicStroke(Math.max(2f, c.thickness * 1.6f), 1, 1).createStrokedShape(o));
      double p = 0.5 * (1 + Math.sin(ms * 0.00284));
      for (int i = 0; i < 9; i++) {
        double s = i / 9.0, pr = (c.progress - s * 0.035 - 0.01 * p + 1.0) % 1.0;
        drawRadialOrb(
            g,
            pointAtProgress(c, pr),
            (float) (14 + 8 * (1 - s) + 4 * p),
            c.color,
            (float) (0.26 * (1 - s) * (0.7 + 0.3 * p) * c.intensity));
      }
      Point2D.Double h = pointAtProgress(c, c.progress);
      drawRadialOrb(g, h, (float) (18 + 10 * p), c.color.brighter(), 0.65f * c.intensity);
      double ot = (ms % 1200L) / 1200.0 * Math.PI * 2;
      drawRadialOrb(
          g,
          new Point2D.Double(h.x + Math.cos(ot) * 10, h.y + Math.sin(ot) * 10),
          (float) (6 + 3 * p),
          Color.WHITE,
          0.55f * c.intensity);
      c.spawnAndRenderParticles(g, h, ns);
      g.setClip(oc);
      g.setComposite(AlphaComposite.SrcOver.derive(0.18f * c.intensity));
      g.setColor(c.color);
      g.draw(new BasicStroke(Math.max(2f, c.thickness * 2.2f), 1, 1).createStrokedShape(o));
    }
  }

  // AnimatedIcon and main method remain unchanged.
  public static final class AnimatedIcon implements Icon {
    private final int width, height;
    private final Config cfg;
    private final Timer timer;
    private JComponent observer;

    public AnimatedIcon(int w, int h, Color c) {
      this.width = w;
      this.height = h;
      this.cfg = new Config(null, UUID.randomUUID());
      this.cfg.color = c != null ? c : new Color(30, 144, 255);
      this.cfg.glowEnabled = true;
      this.cfg.style = Style.ROUNDED;
      this.cfg.roundness = 10;
      this.timer =
          new Timer(
              16,
              e -> {
                cfg.update(0.016f);
                if (observer != null) observer.repaint();
              });
      this.timer.setCoalesce(true);
      this.timer.start();
    }

    public void stop() {
      timer.stop();
    }

    public void setObserver(JComponent c) {
      this.observer = c;
    }

    public void setStyle(Style s) {
      cfg.style = s;
    }

    public void setRoundness(int r) {
      cfg.roundness = r;
    }

    public void setIntensity(float i) {
      cfg.intensity = Math.max(0f, Math.min(1f, i));
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        cfg.ensureBand(new Rectangle(x, y, width, height));
        Painter.paintEdgeGlow(g2, new Rectangle(x, y, width, height), cfg);
      } finally {
        g2.dispose();
      }
    }

    @Override
    public int getIconWidth() {
      return width;
    }

    @Override
    public int getIconHeight() {
      return height;
    }
  }

  public static void main(String[] a) {
    SwingUtilities.invokeLater(
        () -> {
          JFrame f = new JFrame("EdgeGlowAnimator (v2.4 Final)");
          f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          f.setSize(800, 500);
          JPanel r = new JPanel(new GridBagLayout());
          r.setBackground(new Color(20, 22, 28));
          f.setContentPane(r);
          JButton b1 = new JButton("Primary"), b2 = new JButton("Secondary");
          JTextField fld = new JTextField("Type here", 12);
          JPanel card = new JPanel();
          card.setPreferredSize(new Dimension(220, 120));
          card.setBackground(new Color(40, 45, 60));
          GridBagConstraints g = new GridBagConstraints();
          g.insets = new Insets(16, 16, 16, 16);
          r.add(b1, g);
          r.add(b2, g);
          r.add(fld, g);
          r.add(card, g);
          int iw = 64, ih = 64;
          BufferedImage sI = new BufferedImage(iw, ih, 2);
          Graphics2D ig = sI.createGraphics();
          ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          ig.setColor(Color.WHITE);
          Path2D s = new Path2D.Double();
          double cx = iw / 2.0, cy = ih / 2.0, r1 = iw * 0.45, r2 = iw * 0.2;
          for (int i = 0; i < 10; i++) {
            double an = -Math.PI / 2 + i * Math.PI / 5,
                rd = (i % 2 == 0) ? r1 : r2,
                px = cx + Math.cos(an) * rd,
                py = cy + Math.sin(an) * rd;
            if (i == 0) s.moveTo(px, py);
            else s.lineTo(px, py);
          }
          s.closePath();
          ig.fill(s);
          ig.dispose();
          JLabel iL = new JLabel(new ImageIcon(sI));
          r.add(iL, g);
          Handle hi = decorate(iL);
          hi.setIconMode(true);
          hi.setIconUseAlpha(true);
          hi.setIntensity(0.95f);
          hi.setGlowingEffect(true);
          JLabel t = new JLabel("Neon Text");
          t.setFont(t.getFont().deriveFont(Font.BOLD, 68f));
          r.add(t, g);
          Handle ht = decorate(t);
          ht.setTextMode(true);
          ht.setGlowingEffect(true);
          setGlowingEffect(b1, true);
          Handle h2 = decorate(b2);
          h2.setStyle(Style.PILL);
          h2.setColor(new Color(255, 99, 185));
          h2.setIntensity(0.9f);
          h2.setGlowingEffect(true);
          Handle h3 = decorate(fld);
          h3.setStyle(Style.ROUNDED);
          h3.setRoundness(18);
          h3.setGlowingEffect(true);
          Handle h4 = decorate(card);
          h4.setStyle(Style.RECT);
          h4.setGlowingEffect(true);
          JLabel iLbl = new JLabel("Icon");
          AnimatedIcon ai = new AnimatedIcon(72, 36, new Color(80, 200, 255));
          ai.setObserver(iLbl);
          iLbl.setIcon(ai);
          r.add(iLbl, g);
          f.setLocationRelativeTo(null);
          f.setVisible(true);
        });
  }
}
