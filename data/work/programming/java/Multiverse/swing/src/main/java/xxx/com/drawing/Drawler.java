package xxx.com.drawing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A NiFi-like mini canvas:
 * - Drag processors (nodes)
 * - Connect from any port on any side to another port
 * - See live snapping + hover highlights
 * - Pan with right mouse drag
 *
 * Fixes severe UI issues in the original:
 * - Connections are anchored to ports/edges (not centers)
 * - Robust Graphics2D transform handling (no transform corruption)
 * - Clear visual affordances (ports, hover states, cursors)
 */
public class Drawler {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("NiFi‑like Drag & Connect");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1000, 700);
            frame.setLocationRelativeTo(null);
            frame.add(new DrawingCanvas());
            frame.setVisible(true);
        });
    }
}

/** The custom JPanel that acts as the drawing canvas. */
class DrawingCanvas extends JPanel {

    // ======= Model =======
    private enum Side { LEFT, RIGHT, TOP, BOTTOM }

    private static class Node {
        Rectangle bounds;
        String label;
        final List<Port> ports = new ArrayList<>();

        Node(int x, int y, int w, int h, String label) {
            this.bounds = new Rectangle(x, y, w, h);
            this.label = label;
            float[] relPositions = {0.25f, 0.5f, 0.75f};
            for (float rp : relPositions) {
                ports.add(new Port(this, Side.LEFT, rp));
                ports.add(new Port(this, Side.RIGHT, rp));
                ports.add(new Port(this, Side.TOP, rp));
                ports.add(new Port(this, Side.BOTTOM, rp));
            }
        }
    }

    private static class Port {
        final Node node;
        final Side side;
        final float relPos;
        static final int RADIUS = 7; // visual radius
        static final int HIT_PAD = 3; // extra hit padding

        Port(Node node, Side side, float relPos) {
            this.node = node;
            this.side = side;
            this.relPos = relPos;
        }

        Point center() {
            int cx, cy;
            switch (side) {
                case LEFT:
                    cx = node.bounds.x;
                    cy = node.bounds.y + Math.round(node.bounds.height * relPos);
                    break;
                case RIGHT:
                    cx = node.bounds.x + node.bounds.width;
                    cy = node.bounds.y + Math.round(node.bounds.height * relPos);
                    break;
                case TOP:
                    cx = node.bounds.x + Math.round(node.bounds.width * relPos);
                    cy = node.bounds.y;
                    break;
                case BOTTOM:
                    cx = node.bounds.x + Math.round(node.bounds.width * relPos);
                    cy = node.bounds.y + node.bounds.height;
                    break;
                default: // Should not happen
                    cx = node.bounds.x;
                    cy = node.bounds.y;
                    break;
            }
            return new Point(cx, cy);
        }

        Shape shape() {
            Point c = center();
            int r = RADIUS;
            return new Ellipse2D.Double(c.x - r, c.y - r, r * 2, r * 2);
        }

        boolean hit(Point p) {
            int r = RADIUS + HIT_PAD;
            Point c = center();
            int dx = p.x - c.x, dy = p.y - c.y;
            return dx * dx + dy * dy <= r * r;
        }
    }

    private static class Connection {
        final Port from;
        final Port to;
        Connection(Port from, Port to) { this.from = from; this.to = to; }
    }

    // ======= State =======
    private final List<Node> nodes = new ArrayList<>();
    private final List<Connection> connections = new ArrayList<>();

    // Pan state
    private Point panStartScreen = null;
    private final Point viewOffset = new Point(0, 0);

    // Dragging node state
    private Node dragNode = null;
    private Point dragNodeOffset = null;

    // Connecting state
    private Port connectFixed = null;
    private Port connectHover = null;
    private Point connectCurrent = null;

    // Hover state
    private Port hoverPort = null;

    // Visuals
    private static final int GRID = 20;
    private static final Stroke STROKE_CONN = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke STROKE_CONN_DASH = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{8f, 8f}, 0);

    public DrawingCanvas() {
        setBackground(new Color(245, 247, 250));

        // Seed some nodes
        nodes.add(new Node(120, 140, 140, 90, "Processor A"));
        nodes.add(new Node(520, 260, 140, 90, "Processor B"));
        nodes.add(new Node(320, 420, 140, 90, "Processor C"));

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                Point world = toWorld(e.getPoint());
                if (SwingUtilities.isRightMouseButton(e)) {
                    panStartScreen = e.getPoint();
                    clearConnectState();
                    dragNode = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }

                Port p = findPortAt(world);
                if (p != null) {
                    connectFixed = p;
                    connectCurrent = world;
                    connectHover = null;
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                    repaint();
                    return;
                }

                Node n = findNodeAt(world);
                if (n != null) {
                    dragNode = n;
                    Point loc = n.bounds.getLocation();
                    dragNodeOffset = new Point(world.x - loc.x, world.y - loc.y);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    clearConnectState();
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (panStartScreen != null) {
                    int dx = e.getX() - panStartScreen.x;
                    int dy = e.getY() - panStartScreen.y;
                    viewOffset.translate(dx, dy);
                    panStartScreen = e.getPoint();
                    repaint();
                    return;
                }

                Point world = toWorld(e.getPoint());
                if (connectFixed != null) {
                    connectCurrent = world;
                    Port hovered = findPortAt(world);
                    if (hovered != null && hovered.node != connectFixed.node) {
                        connectHover = hovered;
                    } else {
                        connectHover = null;
                    }
                    repaint();
                    return;
                }

                if (dragNode != null && dragNodeOffset != null) {
                    int nx = world.x - dragNodeOffset.x;
                    int ny = world.y - dragNodeOffset.y;
                    nx = snap(nx, GRID);
                    ny = snap(ny, GRID);
                    dragNode.bounds.setLocation(nx, ny);
                    repaint();
                }
            }

            @Override public void mouseMoved(MouseEvent e) {
                Point world = toWorld(e.getPoint());
                hoverPort = findPortAt(world);
                if (hoverPort != null || findNodeAt(world) != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                }
                repaint();
            }

            @Override public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    panStartScreen = null;
                    setCursor(Cursor.getDefaultCursor());
                    return;
                }

                if (connectFixed != null) {
                    if (connectHover != null) {
                        connections.add(new Connection(connectFixed, connectHover));
                    }
                    clearConnectState();
                    repaint();
                    return;
                }

                if (dragNode != null) {
                    dragNode = null;
                    dragNodeOffset = null;
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };

        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        setFocusable(true);
        registerKeyboardAction(e -> { clearConnectState(); repaint(); },
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_FOCUSED);
    }

    // ======= Helpers =======
    private void clearConnectState() {
        connectFixed = null;
        connectHover = null;
        connectCurrent = null;
        setCursor(Cursor.getDefaultCursor());
    }

    private int snap(int v, int grid) { return Math.round(v / (float) grid) * grid; }

    private Point toWorld(Point screen) {
        return new Point(screen.x - viewOffset.x, screen.y - viewOffset.y);
    }

    private Node findNodeAt(Point world) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (n.bounds.contains(world)) return n;
        }
        return null;
    }

    private Port findPortAt(Point world) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            for (Port p : n.ports) {
                if (p.hit(world)) return p;
            }
        }
        return null;
    }

    // ======= Painting =======
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(viewOffset.x, viewOffset.y);

        paintGrid(g2);

        for (Connection c : connections) {
            paintElbowConnection(g2, c.from, c.to, false);
        }

        if (connectFixed != null && connectCurrent != null) {
            paintElbowConnection(g2, connectFixed, connectHover, connectCurrent, true);
        }

        for (Node n : nodes) {
            boolean dragging = (n == dragNode);
            boolean validDropTarget = (connectHover != null && connectHover.node == n);
            paintNode(g2, n, dragging, validDropTarget, connectFixed, connectHover);
        }

        g2.dispose();
    }

    private void paintGrid(Graphics2D g2) {
        g2.setColor(new Color(230, 235, 240));
        int w = getWidth();
        int h = getHeight();
        int startX = -viewOffset.x % GRID;
        int startY = -viewOffset.y % GRID;
        for (int x = startX; x < w; x += GRID) g2.drawLine(x, 0, x, h);
        for (int y = startY; y < h; y += GRID) g2.drawLine(0, y, w, y);
    }

    private void paintNode(Graphics2D g2, Node n, boolean dragging, boolean validDropTarget, Port sourcePort, Port targetPort) {
        int arc = 16;
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(210, 225, 255));
        g2.fillRoundRect(n.bounds.x, n.bounds.y, n.bounds.width, n.bounds.height, arc, arc);

        if (validDropTarget) {
            g2.setColor(new Color(0, 160, 90));
        } else if (dragging) {
            g2.setColor(new Color(70, 120, 220));
        } else {
            g2.setColor(new Color(110, 130, 200));
        }
        g2.drawRoundRect(n.bounds.x, n.bounds.y, n.bounds.width, n.bounds.height, arc, arc);

        g2.setColor(Color.DARK_GRAY);
        FontMetrics fm = g2.getFontMetrics();
        int labelW = fm.stringWidth(n.label);
        int lx = n.bounds.x + (n.bounds.width - labelW) / 2;
        int ly = n.bounds.y + (n.bounds.height + fm.getAscent()) / 2 - 6;
        g2.drawString(n.label, lx, ly);

        for (Port p : n.ports) {
            boolean isSource = (p == sourcePort);
            boolean isTarget = (p == targetPort);
            boolean isHover = (p == hoverPort);
            paintPort(g2, p, isSource, isTarget, isHover);
        }
    }

    private void paintPort(Graphics2D g2, Port p, boolean isSource, boolean isTarget, boolean isHover) {
        Shape s = p.shape();
        g2.setColor(Color.WHITE);
        g2.fill(s);

        if (isSource) {
            g2.setColor(new Color(70, 120, 220)); // Blue for source
            g2.setStroke(new BasicStroke(2.5f));
        } else if (isTarget) {
            g2.setColor(new Color(0, 160, 90)); // Green for target
            g2.setStroke(new BasicStroke(2.5f));
        } else if (isHover) {
            g2.setColor(new Color(100, 100, 100)); // Gray for simple hover
            g2.setStroke(new BasicStroke(2.5f));
        } else {
            g2.setColor(new Color(80, 100, 160));
            g2.setStroke(new BasicStroke(2f));
        }
        g2.draw(s);

        Point c = p.center();
        g2.setColor(new Color(80, 100, 160));
        g2.setStroke(new BasicStroke(1.5f));
        switch (p.side) {
            case LEFT:   g2.drawLine(c.x - 8, c.y, c.x - 2, c.y); break;
            case RIGHT:  g2.drawLine(c.x + 2, c.y, c.x + 8, c.y); break;
            case TOP:    g2.drawLine(c.x, c.y - 8, c.x, c.y - 2); break;
            case BOTTOM: g2.drawLine(c.x, c.y + 2, c.x, c.y + 8); break;
        }
    }

    // Convenience overload for existing, finalized connections.
    private void paintElbowConnection(Graphics2D g2, Port from, Port to, boolean preview) {
        paintElbowConnection(g2, from, to, to.center(), preview);
    }

    // Overload for previews, where the target port might not exist yet.
    private void paintElbowConnection(Graphics2D g2, Port fromPort, Port toPort, Point toPoint, boolean preview) {
        Point from = fromPort.center();
        Path2D path = new Path2D.Double();
        path.moveTo(from.x, from.y);
        Point arrowheadBase;

        boolean fromIsHorizontal = (fromPort.side == Side.LEFT || fromPort.side == Side.RIGHT);

        if (toPort == null) {
            // Fallback for previews not snapping to a port.
            int midX = (from.x + toPoint.x) / 2;
            Point p2 = new Point(midX, from.y);
            arrowheadBase = new Point(midX, toPoint.y);
            path.lineTo(p2.x, p2.y);
            path.lineTo(arrowheadBase.x, arrowheadBase.y);
        } else {
            Point to = toPoint;
            boolean toIsHorizontal = (toPort.side == Side.LEFT || toPort.side == Side.RIGHT);

            if (fromIsHorizontal != toIsHorizontal) {
                // Orthogonal connection (e.g., RIGHT to TOP): use a single-bend L-shape.
                if (fromIsHorizontal) {
                    arrowheadBase = new Point(to.x, from.y);
                } else {
                    arrowheadBase = new Point(from.x, to.y);
                }
                path.lineTo(arrowheadBase.x, arrowheadBase.y);
            } else {
                // Parallel connection (e.g., RIGHT to LEFT or RIGHT to RIGHT).
                if (fromPort.side == toPort.side) {
                    // NEW: Same-side connection (e.g., RIGHT to RIGHT).
                    // This requires a C-shaped bend to go around the node bodies.
                    int stub = GRID * 2;
                    if (fromIsHorizontal) {
                        int lineX;
                        if (fromPort.side == Side.RIGHT) {
                            lineX = Math.max(from.x, to.x) + stub;
                        } else { // LEFT
                            lineX = Math.min(from.x, to.x) - stub;
                        }
                        Point p2 = new Point(lineX, from.y);
                        arrowheadBase = new Point(lineX, to.y);
                        path.lineTo(p2.x, p2.y);
                        path.lineTo(arrowheadBase.x, arrowheadBase.y);
                    } else { // Vertical
                        int lineY;
                        if (fromPort.side == Side.BOTTOM) {
                            lineY = Math.max(from.y, to.y) + stub;
                        } else { // TOP
                            lineY = Math.min(from.y, to.y) - stub;
                        }
                        Point p2 = new Point(from.x, lineY);
                        arrowheadBase = new Point(to.x, lineY);
                        path.lineTo(p2.x, p2.y);
                        path.lineTo(arrowheadBase.x, arrowheadBase.y);
                    }
                } else {
                    // Opposing-side connection (e.g. RIGHT to LEFT): use a double-bend S-shape.
                    if (fromIsHorizontal) {
                        int midX = (from.x + to.x) / 2;
                        Point p2 = new Point(midX, from.y);
                        arrowheadBase = new Point(midX, to.y);
                        path.lineTo(p2.x, p2.y);
                        path.lineTo(arrowheadBase.x, arrowheadBase.y);
                    } else { // Vertical-to-Vertical
                        int midY = (from.y + to.y) / 2;
                        Point p2 = new Point(from.x, midY);
                        arrowheadBase = new Point(to.x, midY);
                        path.lineTo(p2.x, p2.y);
                        path.lineTo(arrowheadBase.x, arrowheadBase.y);
                    }
                }
            }
        }

        path.lineTo(toPoint.x, toPoint.y);

        g2.setStroke(preview ? STROKE_CONN_DASH : STROKE_CONN);
        g2.setColor(preview ? new Color(60, 110, 230) : new Color(70, 70, 80));
        g2.draw(path);

        drawArrowHead(g2, arrowheadBase, toPoint);
    }


    private void drawArrowHead(Graphics2D g2, Point a, Point b) {
        if (a.equals(b)) return;

        double angle = Math.atan2(b.y - a.y, b.x - a.x);
        int len = 10;
        int w = 6;

        Graphics2D g = (Graphics2D) g2.create();
        g.translate(b.x, b.y);
        g.rotate(angle);
        Polygon head = new Polygon();
        head.addPoint(0, 0);
        head.addPoint(-len, -w);
        head.addPoint(-len, w);
        g.fillPolygon(head);
        g.dispose();
    }
}