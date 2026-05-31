package xxx.com.graph;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.*;

public class ConnectionViewer extends JFrame {

  private GraphPanel graphPanel = null;
  private final JComboBox<String> searchCombo;
  private final GraphDatabaseSimulator db;
  private final Preferences prefs;
  private JScrollPane scrollPane;
  private JTextArea messageArea;
  private JPanel messagePanel;
  private JPanel nodeInfoPanel;
  private JLabel nodeNameLabel;
  private JTextArea nodeNotesArea;
  private JList<String> connectivityList;

  private final Deque<GraphData> undoStack = new ArrayDeque<>();
  private final Deque<GraphData> redoStack = new ArrayDeque<>();

  // Preference keys
  private static final String PREF_WINDOW_X = "windowX";
  private static final String PREF_WINDOW_Y = "windowY";
  private static final String PREF_WINDOW_WIDTH = "windowWidth";
  private static final String PREF_WINDOW_HEIGHT = "windowHeight";
  private static final String PREF_NODE_IDS = "nodeIds";
  private static final String PREF_EDGE_COUNT = "edgeCount";

  static class GraphData {
    List<Node> nodes;
    List<EdgeData> edges;
  }

  static class EdgeData {
    String sourceId;
    String targetId;
    String relationship;
    double controlX;
    double controlY;
    boolean isDuplex;
    Integer color;

    EdgeData(Edge edge) {
      this.sourceId = edge.source.id;
      this.targetId = edge.target.id;
      this.relationship = edge.relationship;
      this.controlX = edge.controlX;
      this.controlY = edge.controlY;
      this.isDuplex = edge.isduplex;
      this.color = (edge.color != null) ? edge.color.getRGB() : null;
    }
  }

  public static class ColorTypeAdapter extends TypeAdapter<Color> {
    @Override
    public void write(JsonWriter out, Color value) throws IOException {
      if (value == null) {
        out.nullValue();
        return;
      }
      out.value(value.getRGB());
    }

    @Override
    public Color read(JsonReader in) throws IOException {
      if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return new Color(in.nextInt());
    }
  }

  static class Node {
    String id; // Changed from final to allow renaming
    String label; // Can be changed
    final String type; // "Ingress" or "Egress"
    double x, y;
    String note; // New field to store notes
    Color color = null; // Field for custom color

    Node(String id, String label, String type) {
      this.id = id;
      this.label = label;
      this.type = type;
      this.note = "";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Node node = (Node) o;
      return id.equals(node.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  static class Edge {
    Node source;
    Node target;
    String relationship; // Changed from final to allow editing
    double controlX, controlY; // For bending the line
    boolean isduplex = false; // New field for duplexity
    Color color = null; // Field for custom color

    Edge(Node source, Node target, String relationship) {
      this.source = source;
      this.target = target;
      this.relationship = relationship;
      // Initialize control point to the midpoint
      this.controlX = (source.x + target.x) / 2;
      this.controlY = (source.y + target.y) / 2;
    }
  }

  static class GraphDatabaseSimulator {
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, List<Edge>> adjacencyList = new HashMap<>();
    private final Map<String, List<Edge>> reverseAdjacencyList = new HashMap<>();

    public GraphDatabaseSimulator() {
      // Start with an empty database
    }

    public void buildReverseAdjacencyList() {
      reverseAdjacencyList.clear();
      for (List<Edge> edges : adjacencyList.values()) {
        for (Edge edge : edges) {
          reverseAdjacencyList.computeIfAbsent(edge.target.id, k -> new ArrayList<>()).add(edge);
        }
      }
    }

    public Node addNode(String label, String type) {
      if (nodes.containsKey(label)) {
        return null; // Node already exists
      }
      Node newNode = new Node(label, label, type);
      nodes.put(label, newNode);
      adjacencyList.putIfAbsent(label, new ArrayList<>());
      return newNode;
    }

    public void addEdge(Edge edge) {
      if (edge.source != null && adjacencyList.containsKey(edge.source.id)) {
        adjacencyList.get(edge.source.id).add(edge);
        buildReverseAdjacencyList();
      }
    }

    public void removeEdge(Edge edgeToRemove) {
      if (edgeToRemove == null || edgeToRemove.source == null) return;
      if (adjacencyList.containsKey(edgeToRemove.source.id)) {
        adjacencyList.get(edgeToRemove.source.id).remove(edgeToRemove);
        buildReverseAdjacencyList();
      }
    }

    public void removeNode(Node nodeToRemove) {
      if (nodeToRemove == null) return;
      nodes.remove(nodeToRemove.id);
      adjacencyList.remove(nodeToRemove.id); // Remove outgoing edges
      // Remove incoming edges
      adjacencyList
          .values()
          .forEach(edges -> edges.removeIf(edge -> edge.target.equals(nodeToRemove)));
      buildReverseAdjacencyList();
    }

    public Node findNode(String label) {
      return nodes.get(label);
    }

    public Node findNodeIgnoreCase(String label) {
      String lower = label.toLowerCase();
      for (Node n : nodes.values()) {
        if (n.label.toLowerCase().equals(lower)) {
          return n;
        }
      }
      return null;
    }
  }

  class GraphPanel extends JPanel {
    // This final boolean allows a developer to toggle the animation style.
    // true = all animations are synchronized.
    // false = animations have different, asynchronous starting points.
    private final boolean SYNCHRONOUS_ANIMATION = false;

    private final Map<String, Node> activeNodes = new ConcurrentHashMap<>();
    private final List<Edge> activeEdges = new ArrayList<>();
    private int animationTick = 0;
    private double zoomFactor = 1.0;
    private Point2D.Double viewCenter = new Point2D.Double(425, 290);

    // Double buffering
    private BufferedImage buffer;

    // Interaction states
    private Node selectedNode = null; // Node currently being dragged
    private final Set<Node> selectedNodes = new HashSet<>();
    private Node activeSelection = null; // Node selected for creating a connection
    private Point mousePt = null;
    private boolean isDrawingEdge = false;
    private Node sourceNodeForEdge = null;
    private boolean isRelinkingEdge = false;
    private Edge edgeToRelink = null;
    private boolean relinkingSource = false;

    private Node highlightedNode = null;

    // New states for relinking arrowheads
    private Edge selectedEdgeForRelinking = null;
    private boolean isRelinkingArrow = false;
    private Node originalRelinkTarget = null;

    // New states for bending lines
    private Edge selectedEdgeForBending = null;
    private boolean isBendingEdge = false;

    // Panning states
    private boolean isPanning = false;
    private Point initialMousePos;
    private Point initialViewPos;

    // Multi-drag states
    private boolean isDraggingMultipleNodes = false;
    private Point dragStartPoint;
    private Map<Object, Point2D.Double> initialPositions;

    public GraphPanel() {
      setPreferredSize(new Dimension(850, 580));
      setToolTipText(""); // Enable tooltips to show node notes
      setBackground(new Color(45, 45, 45));

      addComponentListener(
          new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
              // Create a new buffer with the new size
              if (getWidth() > 0 && getHeight() > 0) {
                buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
              }
            }
          });

      Timer animationTimer =
          new Timer(
              50,
              e -> {
                animationTick = (animationTick + 1) % 100;
                repaint();
              });
      animationTimer.start();

      MouseAdapter mouseAdapter =
          new MouseAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {
              if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                Node clickedNode = findNodeAtPoint(e.getPoint());
                Edge clickedEdge = findEdgeAtPoint(e.getPoint());

                if (clickedNode != null) {
                  // Toggle selection: if the same node is clicked, deselect it. Otherwise, select
                  // the new one.
                  activeSelection = (activeSelection == clickedNode) ? null : clickedNode;
                  updateNodeInfoPanel(activeSelection);
                  selectedEdgeForBending = null;
                } else if (clickedEdge == null) {
                  // Clicked on empty space
                  activeSelection = null;
                  updateNodeInfoPanel(null);
                  selectedNodes.clear();
                  selectedEdgeForBending = null;
                }
                repaint();
              } else if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                Edge arrowheadEdge = findArrowheadAtPoint(e.getPoint());
                if (arrowheadEdge != null) {
                  selectedEdgeForRelinking =
                      (selectedEdgeForRelinking == arrowheadEdge) ? null : arrowheadEdge;
                  selectedEdgeForBending = null;
                  repaint();
                } else {
                  Edge clickedEdge = findEdgeAtPoint(e.getPoint());
                  if (clickedEdge != null) {
                    if (selectedEdgeForBending == clickedEdge) {
                      selectedEdgeForBending = null; // Deselect
                    } else {
                      selectedEdgeForBending = clickedEdge; // Select
                      selectedEdgeForRelinking = null;
                    }
                    repaint();
                  }
                }
              }
            }

            @Override
            public void mousePressed(MouseEvent e) {
              mousePt = e.getPoint();
              Node pressedNode = findNodeAtPoint(e.getPoint());

              if (SwingUtilities.isLeftMouseButton(e)) {
                if (selectedEdgeForBending != null
                    && findEdgeAtPoint(e.getPoint()) == selectedEdgeForBending) {
                  isBendingEdge = true;
                  return;
                }

                if (selectedEdgeForRelinking != null
                    && findArrowheadAtPoint(e.getPoint()) == selectedEdgeForRelinking) {
                  isRelinkingArrow = true;
                  originalRelinkTarget = selectedEdgeForRelinking.target;
                  selectedNode = null; // Prevent node dragging
                  return;
                }

                if (pressedNode != null) {
                  // --- MODIFICATION: SET GRABBING CURSOR ---
                  setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                  if (selectedNodes.contains(pressedNode)) {
                    isDraggingMultipleNodes = true;
                    dragStartPoint = e.getPoint();
                    initialPositions = new HashMap<>();
                    for (Node node : selectedNodes) {
                      initialPositions.put(node, new Point2D.Double(node.x, node.y));
                    }
                    // Store initial positions of control points for relevant edges
                    for (Edge edge : activeEdges) {
                      if (selectedNodes.contains(edge.source)
                          && selectedNodes.contains(edge.target)) {
                        initialPositions.put(
                            edge, new Point2D.Double(edge.controlX, edge.controlY));
                      }
                    }
                    return; // Prevent single node drag logic
                  }
                  // If the pressed node is the actively selected one, start drawing a connection.
                  if (pressedNode == activeSelection) {
                    isDrawingEdge = true;
                    sourceNodeForEdge = pressedNode;
                  }
                  // In either case, set the node as selected for a potential drag-to-move
                  // operation.
                  selectedNode = pressedNode;

                } else if (findEdgeAtPoint(e.getPoint()) == null) {
                  // --- MODIFICATION: SET GRABBING CURSOR ---
                  setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                  isDraggingMultipleNodes = true; // Use the existing multi-drag flag
                  dragStartPoint = e.getPoint();
                  initialPositions = new HashMap<>();

                  // Select ALL active nodes to be dragged.
                  selectedNodes.clear();
                  selectedNodes.addAll(activeNodes.values());

                  // Store initial positions for ALL nodes.
                  for (Node node : activeNodes.values()) {
                    initialPositions.put(node, new Point2D.Double(node.x, node.y));
                  }

                  // Store initial positions of control points for ALL edges.
                  for (Edge edge : activeEdges) {
                    initialPositions.put(edge, new Point2D.Double(edge.controlX, edge.controlY));
                  }
                  repaint(); // Repaint to show the new selection.
                }

              } else if (SwingUtilities.isRightMouseButton(e)) {
                Object clickedObject = findObjectAtPoint(e.getPoint());
                if (clickedObject instanceof Edge) {
                  showEdgeContextMenu(e, (Edge) clickedObject);
                } else if (clickedObject instanceof Node) {
                  showNodeContextMenu(e, (Node) clickedObject);
                } else {
                  showCanvasContextMenu(e);
                }
              }

              if (SwingUtilities.isMiddleMouseButton(e)) {
                // --- MODIFICATION: SET MOVE CURSOR FOR PANNING ---
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                isPanning = true;
                initialMousePos = e.getPoint();
                initialViewPos = ConnectionViewer.this.scrollPane.getViewport().getViewPosition();
              }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              // --- MODIFICATION: RESET CURSOR ON RELEASE ---
              setCursor(Cursor.getDefaultCursor());

              if (isBendingEdge) {
                isBendingEdge = false;
                captureAndSaveState();
                return;
              }

              if (isRelinkingArrow) {
                Node targetNode = findNodeAtPoint(e.getPoint());
                if (targetNode != null && !targetNode.equals(selectedEdgeForRelinking.source)) {
                  selectedEdgeForRelinking.target = targetNode;
                } else {
                  // Return to original if no valid target
                  selectedEdgeForRelinking.target = originalRelinkTarget;
                }
                isRelinkingArrow = false;
                selectedEdgeForRelinking = null;
                originalRelinkTarget = null;
                captureAndSaveState();
                repaint();
                return;
              }

              boolean wasDragging =
                  (selectedNode != null && !isDrawingEdge) || isDraggingMultipleNodes;

              if (isDrawingEdge && sourceNodeForEdge != null) {
                Node targetNode = findNodeAtPoint(e.getPoint());
                if (targetNode != null) {
                  Edge newEdge = new Edge(sourceNodeForEdge, targetNode, "");
                  if (sourceNodeForEdge.equals(targetNode)) {
                    // Self-loop
                    newEdge.controlX = sourceNodeForEdge.x;
                    newEdge.controlY = sourceNodeForEdge.y - 60; // Initial loop shape
                  }
                  addEdge(newEdge);
                }
              }
              // Reset interaction states. activeSelection is handled by mouseClicked.
              isDrawingEdge = false;
              sourceNodeForEdge = null;
              selectedNode = null;
              isPanning = false;
              isDraggingMultipleNodes = false;
              dragStartPoint = null;
              initialPositions = null;

              if (wasDragging) {
                captureAndSaveState();
              }
              repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
              Point2D.Double worldPoint = toWorld(e.getPoint());
              mousePt = e.getPoint();

              if (isBendingEdge) {
                if (selectedEdgeForBending.source.equals(selectedEdgeForBending.target)) {
                  // Self-loop bending logic
                  double dx = worldPoint.x - selectedEdgeForBending.source.x;
                  double dy = worldPoint.y - selectedEdgeForBending.source.y;
                  double dist = Math.sqrt(dx * dx + dy * dy);
                  if (dist < 30) { // Enforce minimum loop size
                    dx = (dx / dist) * 30;
                    dy = (dy / dist) * 30;
                  }
                  selectedEdgeForBending.controlX = selectedEdgeForBending.source.x + dx;
                  selectedEdgeForBending.controlY = selectedEdgeForBending.source.y + dy;
                } else {
                  selectedEdgeForBending.controlX = worldPoint.x;
                  selectedEdgeForBending.controlY = worldPoint.y;
                }
                repaint();
                return;
              }

              if (isDraggingMultipleNodes) {
                double dx = (e.getX() - dragStartPoint.x) / zoomFactor;
                double dy = (e.getY() - dragStartPoint.y) / zoomFactor;
                for (Node node : selectedNodes) {
                  Point2D.Double initialPos = initialPositions.get(node);
                  node.x = initialPos.x + dx;
                  node.y = initialPos.y + dy;
                }
                for (Edge edge : activeEdges) {
                  if (selectedNodes.contains(edge.source) && selectedNodes.contains(edge.target)) {
                    Point2D.Double initialPos = initialPositions.get(edge);
                    edge.controlX = initialPos.x + dx;
                    edge.controlY = initialPos.y + dy;
                  } else if (selectedNodes.contains(edge.source)
                      || selectedNodes.contains(edge.target)) {
                    // For edges connected to the group, just reset to midpoint
                    edge.controlX = (edge.source.x + edge.target.x) / 2;
                    edge.controlY = (edge.source.y + edge.target.y) / 2;
                  }
                }
                repaint();
                return;
              }

              if (isRelinkingArrow) {
                repaint();
                return;
              }
              // If a node is selected and if we are NOT in drawing mode, then move the node.
              if (selectedNode != null && !isDrawingEdge) {
                double dx = worldPoint.x - selectedNode.x;
                double dy = worldPoint.y - selectedNode.y;
                selectedNode.x = worldPoint.x;
                selectedNode.y = worldPoint.y;

                // Update control points of connected edges that aren't being bent
                for (Edge edge : activeEdges) {
                  if ((edge.source == selectedNode || edge.target == selectedNode)
                      && edge != selectedEdgeForBending) {
                    if (edge.source.equals(edge.target)) {
                      // Adjust self-loop control point
                      edge.controlX += dx;
                      edge.controlY += dy;
                    } else {
                      edge.controlX = (edge.source.x + edge.target.x) / 2;
                      edge.controlY = (edge.source.y + edge.target.y) / 2;
                    }
                  }
                }
                repaint();
              } else if (isDrawingEdge) {
                repaint(); // Repaint to show the line being drawn.
              } else if (isPanning) {
                Point current = e.getPoint();
                int dx = initialMousePos.x - current.x;
                int dy = initialMousePos.y - current.y;
                Point newPos = new Point(initialViewPos.x + dx, initialViewPos.y + dy);
                JViewport vp = ConnectionViewer.this.scrollPane.getViewport();
                Dimension viewSize = vp.getView().getPreferredSize();
                Dimension extent = vp.getExtentSize();
                newPos.x = Math.max(0, Math.min(newPos.x, viewSize.width - extent.width));
                newPos.y = Math.max(0, Math.min(newPos.y, viewSize.height - extent.height));
                vp.setViewPosition(newPos);
              }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
              // This can be empty now as mouseDragged handles the line drawing preview
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
              Point2D.Double beforeZoom = toWorld(e.getPoint());
              double scale = Math.pow(1.1, -e.getWheelRotation());
              zoomFactor *= scale;
              Point2D.Double afterZoom = toWorld(e.getPoint());
              viewCenter.x += beforeZoom.x - afterZoom.x;
              viewCenter.y += beforeZoom.y - afterZoom.y;
              revalidate();
              repaint();
            }
          };
      addMouseListener(mouseAdapter);
      addMouseMotionListener(mouseAdapter);
      addMouseWheelListener(mouseAdapter);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      Node node = findNodeAtPoint(event.getPoint());
      if (node != null && node.note != null && !node.note.trim().isEmpty()) {
        return "<html><p width=\"250\">"
            + node.note.trim().replaceAll("\n", "<br>")
            + "</p></html>";
      }
      return super.getToolTipText(event);
    }

    private void editEdgeRelationship(Edge edge) {
      String newRelationship =
          JOptionPane.showInputDialog(GraphPanel.this, "Add Label", edge.relationship);
      if (newRelationship != null) { // Allow empty relationship
        edge.relationship = newRelationship.trim();
        repaint();
        captureAndSaveState();
      }
    }

    private void deleteEdge(Edge edge) {
      activeEdges.remove(edge);
      db.removeEdge(edge);
      repaint();
      captureAndSaveState();
    }

    private void showEdgeContextMenu(MouseEvent e, final Edge edge) {
      JPopupMenu contextMenu = new JPopupMenu();

      JMenuItem deleteItem = new JMenuItem("Delete");
      deleteItem.addActionListener(actionEvent -> deleteEdge(edge));
      contextMenu.add(deleteItem);

      contextMenu.addSeparator();

      JMenuItem editNameItem = new JMenuItem("Set Label");
      editNameItem.addActionListener(actionEvent -> editEdgeRelationship(edge));
      contextMenu.add(editNameItem);

      JMenuItem colorItem = new JMenuItem("Set Color");
      colorItem.addActionListener(actionEvent -> showColorPickerDialog(edge));
      contextMenu.add(colorItem);

      JMenuItem reverseItem = new JMenuItem("Reverse Flow");
      reverseItem.addActionListener(
          actionEvent -> {
            db.removeEdge(edge);
            Node temp = edge.source;
            edge.source = edge.target;
            edge.target = temp;
            db.addEdge(edge);
            repaint();
            captureAndSaveState();
          });
      contextMenu.add(reverseItem);

      if (edge.isduplex) {
        JMenu simplexMenu = new JMenu("Set Simplex");

        JMenuItem keepThisDirection =
            new JMenuItem("Keep: " + edge.source.label + " -> " + edge.target.label);
        keepThisDirection.addActionListener(
            actionEvent -> {
              edge.isduplex = false;
              repaint();
              captureAndSaveState();
            });
        simplexMenu.add(keepThisDirection);

        JMenuItem keepReverseDirection =
            new JMenuItem("Keep: " + edge.target.label + " -> " + edge.source.label);
        keepReverseDirection.addActionListener(
            actionEvent -> {
              edge.isduplex = false;
              Node temp = edge.source;
              edge.source = edge.target;
              edge.target = temp;
              repaint();
              captureAndSaveState();
            });
        simplexMenu.add(keepReverseDirection);
        contextMenu.add(simplexMenu);

      } else {
        JMenuItem duplexItem = new JMenuItem("Set Duplex");
        duplexItem.addActionListener(
            actionEvent -> {
              edge.isduplex = true;
              repaint();
              captureAndSaveState();
            });
        contextMenu.add(duplexItem);
      }

      contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void showCanvasContextMenu(MouseEvent e) {
      JPopupMenu contextMenu = new JPopupMenu();

      JMenuItem createItem = new JMenuItem("Create Node");
      createItem.addActionListener(actionEvent -> createNewNodeAtPoint(e.getPoint()));
      contextMenu.add(createItem);

      contextMenu.addSeparator();

      JMenuItem undoItem = new JMenuItem("Undo");
      undoItem.addActionListener(actionEvent -> undo());
      undoItem.setEnabled(undoStack.size() > 1);
      contextMenu.add(undoItem);

      JMenuItem redoItem = new JMenuItem("Redo");
      redoItem.addActionListener(actionEvent -> redo());
      redoItem.setEnabled(!redoStack.isEmpty());
      contextMenu.add(redoItem);

      contextMenu.addSeparator();

      JMenuItem selectAllItem = new JMenuItem("Select All");
      selectAllItem.addActionListener(actionEvent -> selectAllNodes());
      contextMenu.add(selectAllItem);

      JMenuItem centerItem = new JMenuItem("Center");
      centerItem.addActionListener(actionEvent -> centerAllNodes());
      contextMenu.add(centerItem);

      contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void selectAllNodes() {
      selectedNodes.clear();
      selectedNodes.addAll(activeNodes.values());
      repaint();
    }

    private Object findObjectAtPoint(Point p) {
      Node node = findNodeAtPoint(p);
      if (node != null) return node;
      return findEdgeAtPoint(p);
    }

    private Node findNodeAtPoint(Point p) {
      Point2D.Double worldPoint = toWorld(p);
      for (Node node : activeNodes.values()) {
        if (worldPoint.distance(node.x, node.y) <= 20) {
          return node;
        }
      }
      return null;
    }

    private Edge findArrowheadAtPoint(Point p) {
      Point2D.Double worldPoint = toWorld(p);
      for (Edge edge : activeEdges) {
        // Approximate the end of the curve for arrowhead detection
        double t = 0.95; // A point very close to the end of the curve
        double mt = 1 - t;
        double endX = mt * mt * edge.source.x + 2 * mt * t * edge.controlX + t * t * edge.target.x;
        double endY = mt * mt * edge.source.y + 2 * mt * t * edge.controlY + t * t * edge.target.y;

        if (worldPoint.distance(endX, endY) <= 12) {
          return edge;
        }
      }
      return null;
    }

    private Edge findEdgeAtPoint(Point p) {
      final double TOLERANCE = 5.0 / zoomFactor; // Make tolerance aware of zoom
      Point2D.Double worldPoint = toWorld(p);
      for (Edge edge : activeEdges) {
        Shape curve;
        if (edge.source.equals(edge.target)) {
          double radiusX = Math.abs(edge.controlX - edge.source.x);
          double radiusY = Math.abs(edge.controlY - edge.source.y);
          curve =
              new Ellipse2D.Double(
                  edge.source.x - radiusX, edge.source.y - radiusY, radiusX * 2, radiusY * 2);

        } else {
          curve =
              new QuadCurve2D.Double(
                  edge.source.x,
                  edge.source.y,
                  edge.controlX,
                  edge.controlY,
                  edge.target.x,
                  edge.target.y);
        }

        if (curve
            .getBounds2D()
            .intersects(
                worldPoint.x - TOLERANCE, worldPoint.y - TOLERANCE, TOLERANCE * 2, TOLERANCE * 2)) {
          return edge;
        }
      }
      return null;
    }

    private void createNewNodeAtPoint(Point p) {
      JTextField nameField = new JTextField(10);
      JRadioButton producerRadio = new JRadioButton("Producer", true);
      JRadioButton consumerRadio = new JRadioButton("Consumer");
      ButtonGroup group = new ButtonGroup();
      group.add(producerRadio);
      group.add(consumerRadio);
      JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
      panel.add(new JLabel("Node Name:"));
      panel.add(nameField);
      panel.add(new JSeparator());
      panel.add(new JLabel("Node Type:"));
      panel.add(producerRadio);
      panel.add(consumerRadio);
      int result =
          JOptionPane.showConfirmDialog(
              this, panel, "Create Node", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (result == JOptionPane.OK_OPTION) {
        String name = nameField.getText().trim();
        if (name.isEmpty() || db.findNode(name) != null) {
          JOptionPane.showMessageDialog(
              this, "Node name must be unique and not empty.", "Error", JOptionPane.ERROR_MESSAGE);
          return;
        }
        String type = producerRadio.isSelected() ? "Ingress" : "Egress";
        Node newNode = db.addNode(name, type);
        Point2D.Double worldPoint = toWorld(p);
        newNode.x = worldPoint.x;
        newNode.y = worldPoint.y;
        activeNodes.put(newNode.id, newNode);
        repaint();
        captureAndSaveState();
      }
    }

    private void showNodeContextMenu(MouseEvent e, final Node node) {
      JPopupMenu contextMenu = new JPopupMenu();

      JMenuItem deleteItem = new JMenuItem("Delete");
      deleteItem.addActionListener(actionEvent -> deleteNode(node));
      contextMenu.add(deleteItem);

      JMenuItem renameItem = new JMenuItem("Rename");
      renameItem.addActionListener(actionEvent -> renameNode(node));
      contextMenu.add(renameItem);

      contextMenu.addSeparator();

      JMenuItem selfConnectItem = new JMenuItem("Self Connected");
      selfConnectItem.addActionListener(
          actionEvent -> {
            Edge newEdge = new Edge(node, node, "");
            newEdge.controlX = node.x;
            newEdge.controlY = node.y - 60; // Initial loop shape
            addEdge(newEdge);
          });
      contextMenu.add(selfConnectItem);

      contextMenu.addSeparator();

      JMenuItem colorItem = new JMenuItem("Pick Color");
      colorItem.addActionListener(actionEvent -> showColorPickerDialog(node));
      contextMenu.add(colorItem);

      JMenuItem noteItem = new JMenuItem("Edit Note");
      noteItem.addActionListener(actionEvent -> showNoteDialog(node));
      contextMenu.add(noteItem);

      JMenuItem emitMessageItem = new JMenuItem("Trace");
      emitMessageItem.addActionListener(actionEvent -> emitMessage(node));
      contextMenu.add(emitMessageItem);

      JMenuItem centerItem = new JMenuItem("Center View");
      centerItem.addActionListener(actionEvent -> centerNode(node));
      contextMenu.add(centerItem);

      contextMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void renameNode(Node nodeToRename) {
      String newName =
          JOptionPane.showInputDialog(
              GraphPanel.this, "Enter new name for node:", nodeToRename.label);

      if (newName == null || newName.trim().isEmpty()) {
        return; // User cancelled or entered empty name
      }
      newName = newName.trim();

      if (newName.equals(nodeToRename.label)) {
        return; // Name is unchanged
      }

      if (db.findNode(newName) != null) {
        JOptionPane.showMessageDialog(
            this, "A node with this name already exists.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }

      // Update database, graph panel's active nodes, and the node object
      String oldId = nodeToRename.id;

      // 1. Update the 'activeNodes' map in GraphPanel
      activeNodes.remove(oldId);

      // 2. Update the 'nodes' map key in the database
      db.nodes.remove(oldId);

      // 3. Update the 'adjacencyList' map key for outgoing edges
      if (db.adjacencyList.containsKey(oldId)) {
        List<Edge> outgoingEdges = db.adjacencyList.remove(oldId);
        db.adjacencyList.put(newName, outgoingEdges);
      }

      // 4. Update node object itself
      nodeToRename.id = newName;
      nodeToRename.label = newName;

      // 5. Re-add to maps with new key
      activeNodes.put(newName, nodeToRename);
      db.nodes.put(newName, nodeToRename);

      repaint();
      captureAndSaveState();
    }

    private void showColorPickerDialog(Object graphObject) {
      JDialog dialog =
          new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Pick a Color", true);
      JPanel panel = new JPanel(new GridLayout(2, 6, 5, 5));
      panel.setBorder(new EmptyBorder(5, 5, 5, 5));

      Color[] colors = {
          new Color(220, 20, 60), // Crimson Red
          new Color(255, 160, 122), // Light Salmon
          new Color(255, 140, 0), // Dark Orange
          new Color(255, 215, 0), // Gold
          new Color(60, 179, 113), // Medium Sea Green
          new Color(34, 139, 34), // Forest Green
          new Color(30, 144, 255), // Dodger Blue
          new Color(100, 149, 237), // Cornflower Blue
          new Color(186, 85, 211), // Medium Orchid
          new Color(148, 0, 211), // Dark Violet
          new Color(128, 128, 128), // Gray
          null // Default color option
      };

      for (Color color : colors) {
        JButton button = new JButton();
        button.setFocusable(false);
        if (color != null) {
          button.setBackground(color);
          button.setPreferredSize(new Dimension(30, 30));
        } else {
          button.setText("D");
          button.setToolTipText("Reset to default color");
        }
        button.addActionListener(
            e -> {
              if (graphObject instanceof Node) {
                ((Node) graphObject).color = color;
              } else if (graphObject instanceof Edge) {
                ((Edge) graphObject).color = color;
              }
              dialog.dispose();
              repaint();
              captureAndSaveState();
            });
        panel.add(button);
      }

      dialog.setContentPane(panel);
      dialog.pack();
      dialog.setLocationRelativeTo(this);
      dialog.setVisible(true);
    }

    private void emitMessage(Node startNode) {
      messageArea.append("\n--- Message Emission Started from '" + startNode.label + "' ---\n");
      propagateMessage(startNode, new LinkedHashSet<>());
      messageArea.append("--- Message Emission Finished ---\n\n");
      messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    private void propagateMessage(Node currentNode, Set<Node> visitedNodes) {
      if (!visitedNodes.add(currentNode)) {
        // Node has been visited in this emission path, stop to prevent cycles
        return;
      }

      // Find outgoing edges and propagate the message
      db.adjacencyList
          .getOrDefault(currentNode.id, new ArrayList<>())
          .forEach(
              edge -> {
                Node targetNode = edge.target;
                List<String> pathLabels =
                    new ArrayList<>(visitedNodes)
                        .stream().map(node -> node.label).collect(Collectors.toList());
                String breadcrumb = String.join(" > ", pathLabels) + " > " + targetNode.label;
                messageArea.append(
                    breadcrumb + ": Message received by '" + targetNode.label + "'\n");
                propagateMessage(
                    targetNode,
                    new LinkedHashSet<>(visitedNodes)); // Pass a copy of the visited set
              });
    }

    private void showNoteDialog(Node node) {
      JTextArea textArea = new JTextArea(10, 30);
      textArea.setText(node.note);
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      JScrollPane scrollPane = new JScrollPane(textArea);
      int result =
          JOptionPane.showConfirmDialog(
              this,
              scrollPane,
              "Note for " + node.label,
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.PLAIN_MESSAGE);
      if (result == JOptionPane.OK_OPTION) {
        node.note = textArea.getText();
        repaint();
        captureAndSaveState();
      }
    }

    private void deleteNode(Node node) {
      activeEdges.removeIf(edge -> edge.source.equals(node) || edge.target.equals(node));
      db.removeNode(node);
      activeNodes.remove(node.id);
      repaint();
      captureAndSaveState();
    }

    public void addNode(Node node) {
      if (node == null || activeNodes.containsKey(node.id)) return;

      if (node.x == 0 && node.y == 0) { // Only randomize if not loaded from prefs
        int centerX = getWidth() > 0 ? getWidth() / 2 : 400;
        int centerY = getHeight() > 0 ? getHeight() / 2 : 300;
        node.x = centerX + (Math.random() - 0.5) * 200;
        node.y = centerY + (Math.random() - 0.5) * 200;
      }
      activeNodes.put(node.id, node);
      repaint();
      captureAndSaveState();
    }

    public void addEdge(Edge edge) {
      boolean exists =
          activeEdges.stream()
              .anyMatch(e -> e.source.equals(edge.source) && e.target.equals(edge.target));
      if (!exists) {
        activeEdges.add(edge);
        db.addEdge(edge);
      }
      repaint();
      captureAndSaveState();
    }

    public void clear() {
      activeNodes.clear();
      activeEdges.clear();
      db.nodes.clear();
      db.adjacencyList.clear();
      repaint();
      captureAndSaveState();
    }

    private void centerAllNodes() {
      if (activeNodes.isEmpty()) return;
      double sumX = 0, sumY = 0;
      for (Node n : activeNodes.values()) {
        sumX += n.x;
        sumY += n.y;
      }
      double avgX = sumX / activeNodes.size();
      double avgY = sumY / activeNodes.size();
      centerAt(avgX, avgY);
    }

    private void centerNode(Node node) {
      centerAt(node.x, node.y);
    }

    private void centerAt(double cx, double cy) {
      viewCenter = new Point2D.Double(cx, cy);
      repaint();
    }

    private Point2D.Double toWorld(Point p) {
      return new Point2D.Double(
          (p.x - getWidth() / 2.0) / zoomFactor + viewCenter.x,
          (p.y - getHeight() / 2.0) / zoomFactor + viewCenter.y);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      if (buffer == null && getWidth() > 0 && getHeight() > 0) {
        buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
      }

      if (buffer != null) {
        Graphics2D g2d = buffer.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(getBackground());
        g2d.fillRect(0, 0, getWidth(), getHeight());

        AffineTransform oldTransform = g2d.getTransform();
        AffineTransform tx = new AffineTransform();
        tx.translate(getWidth() / 2.0, getHeight() / 2.0);
        tx.scale(zoomFactor, zoomFactor);
        tx.translate(-viewCenter.x, -viewCenter.y);
        g2d.setTransform(tx);

        drawGrid(g2d);

        boolean isDark = FlatLaf.isLafDark();

        for (Edge edge : activeEdges) {
          drawEdge(g2d, edge, isDark);
        }
        if (isDrawingEdge && sourceNodeForEdge != null && mousePt != null) {
          Point2D.Double worldPoint = toWorld(mousePt);
          Edge tempEdge =
              new Edge(
                  sourceNodeForEdge,
                  new Node(null, null, null) {
                    {
                      x = worldPoint.x;
                      y = worldPoint.y;
                    }
                  },
                  null);
          drawEdge(g2d, tempEdge, isDark);
        } else if (isRelinkingEdge && edgeToRelink != null && mousePt != null) {
          String rel = edgeToRelink.relationship;
          Point2D.Double worldPoint = toWorld(mousePt);
          if (relinkingSource) {
            Edge tempEdge =
                new Edge(
                    new Node(null, null, null) {
                      {
                        x = worldPoint.x;
                        y = worldPoint.y;
                      }
                    },
                    edgeToRelink.target,
                    rel);
            drawEdge(g2d, tempEdge, isDark);
          } else {
            Edge tempEdge =
                new Edge(
                    edgeToRelink.source,
                    new Node(null, null, null) {
                      {
                        x = worldPoint.x;
                        y = worldPoint.y;
                      }
                    },
                    rel);
            drawEdge(g2d, tempEdge, isDark);
          }
        } else if (isRelinkingArrow && selectedEdgeForRelinking != null && mousePt != null) {
          Point2D.Double worldPoint = toWorld(mousePt);
          Edge tempEdge =
              new Edge(
                  selectedEdgeForRelinking.source,
                  new Node(null, null, null) {
                    {
                      x = worldPoint.x;
                      y = worldPoint.y;
                    }
                  },
                  selectedEdgeForRelinking.relationship);
          drawEdge(g2d, tempEdge, isDark);
        }
        for (Node node : activeNodes.values()) {
          drawNode(g2d, node, isDark);
        }

        g2d.setTransform(oldTransform);
        g2d.dispose();
        g.drawImage(buffer, 0, 0, this);
      }
    }

    private void drawGrid(Graphics2D g2d) {
      int gridSize = 20;
      g2d.setColor(new Color(60, 60, 60));
      g2d.setStroke(new BasicStroke(1.0f / (float) zoomFactor));

      Rectangle viewRect = getVisibleRect();
      Point2D.Double origin = toWorld(viewRect.getLocation());
      Point2D.Double extent =
          toWorld(new Point(viewRect.x + viewRect.width, viewRect.y + viewRect.height));

      for (double x = Math.floor(origin.x / gridSize) * gridSize; x < extent.x; x += gridSize) {
        g2d.draw(new Line2D.Double(x, origin.y, x, extent.y));
      }

      for (double y = Math.floor(origin.y / gridSize) * gridSize; y < extent.y; y += gridSize) {
        g2d.draw(new Line2D.Double(origin.x, y, extent.x, y));
      }
    }

    private void drawArrowhead(Graphics2D g2d, Point2D from, Point2D to) {
      AffineTransform oldTx = g2d.getTransform();

      // Position and rotate the arrowhead
      g2d.translate(to.getX(), to.getY());
      g2d.rotate(Math.atan2(to.getY() - from.getY(), to.getX() - from.getX()) - Math.PI / 2);

      // Counter-scale to keep the arrowhead size constant on screen
      g2d.scale(1 / zoomFactor, 1 / zoomFactor);

      // Define the arrowhead shape
      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(0, 0);
      arrowHead.addPoint(-6, -12);
      arrowHead.addPoint(6, -12);
      g2d.fill(arrowHead);

      // Restore the original transform
      g2d.setTransform(oldTx);
    }

    private void drawEdge(Graphics2D g2d, Edge edge, boolean isDark) {
      Node source = edge.source;
      Node target = edge.target;
      String relationship = edge.relationship;

      Shape curve;
      if (source.equals(target)) {
        double dx = edge.controlX - source.x;
        double dy = edge.controlY - source.y;
        double radius = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.atan2(dy, dx);

        double startAngle = -Math.toDegrees(angle) - 120;
        double extentAngle = -300;
        curve =
            new Arc2D.Double(
                edge.controlX - radius,
                edge.controlY - radius,
                radius * 2,
                radius * 2,
                startAngle,
                extentAngle,
                Arc2D.OPEN);
      } else {
        curve =
            new QuadCurve2D.Double(
                source.x, source.y, edge.controlX, edge.controlY, target.x, target.y);
      }

      // Set edge color, giving precedence to interaction states
      Color edgeColor;
      if (edge.color != null) {
        edgeColor = edge.color;
      } else {
        edgeColor = isDark ? Color.LIGHT_GRAY : Color.GRAY;
      }

      if (edge == selectedEdgeForBending) {
        g2d.setColor(Color.BLUE);
      } else {
        g2d.setColor(edgeColor);
      }

      g2d.setStroke(new BasicStroke(1.5f / (float) zoomFactor));
      g2d.draw(curve);

      // --- Arrowhead Drawing ---
      Color originalColor = g2d.getColor();
      if (edge == selectedEdgeForRelinking) {
        g2d.setColor(Color.RED); // Relinking highlight
      } else {
        g2d.setColor(edgeColor); // Use edge color for arrowhead
      }

      Point2D controlPt = new Point2D.Double(edge.controlX, edge.controlY);

      // Arrowhead at target
      Point2D targetPt = new Point2D.Double(target.x, target.y);
      if (source.equals(target)) {
        double radius = new Point2D.Double(source.x, source.y).distance(controlPt);
        double angle = Math.atan2(controlPt.getY() - source.y, controlPt.getX() - source.x);
        double arrowAngle = -Math.toRadians(Math.toDegrees(angle) + 150);
        Point2D arrowPoint =
            new Point2D.Double(
                controlPt.getX() + radius * Math.cos(arrowAngle),
                controlPt.getY() - radius * Math.sin(arrowAngle));
        Point2D fromPoint =
            new Point2D.Double(
                controlPt.getX() + radius * Math.cos(arrowAngle + 0.1),
                controlPt.getY() - radius * Math.sin(arrowAngle + 0.1));
        drawArrowhead(g2d, fromPoint, arrowPoint);
      } else {
        double dx = targetPt.getX() - controlPt.getX();
        double dy = targetPt.getY() - controlPt.getY();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 1e-6) {
          double boundaryX = targetPt.getX() - (dx / dist) * 20;
          double boundaryY = targetPt.getY() - (dy / dist) * 20;
          Point2D boundaryPt = new Point2D.Double(boundaryX, boundaryY);
          drawArrowhead(g2d, controlPt, boundaryPt);
        }
      }

      // Arrowhead at source if duplex
      if (edge.isduplex) {
        Point2D sourcePt = new Point2D.Double(source.x, source.y);
        double dx = sourcePt.getX() - controlPt.getX();
        double dy = sourcePt.getY() - controlPt.getY();
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 1e-6) {
          double boundaryX = sourcePt.getX() - (dx / dist) * 20;
          double boundaryY = sourcePt.getY() - (dy / dist) * 20;
          Point2D boundaryPt = new Point2D.Double(boundaryX, boundaryY);
          drawArrowhead(g2d, controlPt, boundaryPt);
        }
      }
      g2d.setColor(originalColor);

      // --- Animation Dot ---
      // The calculation for 't' (time) now includes an offset if asynchronous
      // animation is enabled. The offset is derived from the edge's hash code
      // to give each edge a unique but consistent animation start time.
      double offset = SYNCHRONOUS_ANIMATION ? 0 : (double)(edge.hashCode() % 100) / 100.0;
      double t = ((animationTick % 100) / 100.0 + offset) % 1.0;

      double mt = 1.0 - t;
      double dotX, dotY;
      if (source.equals(target)) {
        double radius = new Point2D.Double(source.x, source.y).distance(controlPt);
        double angle = Math.atan2(controlPt.getY() - source.y, controlPt.getX() - source.x);
        double startAngleRad = -Math.toRadians(Math.toDegrees(angle) + 120);
        double sweepAngleRad = -Math.toRadians(300);
        dotX = controlPt.getX() + radius * Math.cos(startAngleRad + t * sweepAngleRad);
        dotY = controlPt.getY() - radius * Math.sin(startAngleRad + t * sweepAngleRad);
      } else {
        dotX = mt * mt * source.x + 2 * mt * t * edge.controlX + t * t * target.x;
        dotY = mt * mt * source.y + 2 * mt * t * edge.controlY + t * t * target.y;
      }

      AffineTransform oldTx = g2d.getTransform();
      g2d.translate(dotX, dotY);
      g2d.scale(1 / zoomFactor, 1 / zoomFactor);
      g2d.setColor(Color.WHITE);
      g2d.fillOval(-3, -3, 6, 6);
      g2d.setTransform(oldTx);

      // Second dot for duplex
      if (edge.isduplex) {
        double t2 = 1.0 - t;
        double mt2 = 1.0 - t2;
        double dotX2, dotY2;

        if (source.equals(target)) {
          double radius = new Point2D.Double(source.x, source.y).distance(controlPt);
          double angle = Math.atan2(controlPt.getY() - source.y, controlPt.getX() - source.x);
          double startAngleRad = -Math.toRadians(Math.toDegrees(angle) + 120);
          double sweepAngleRad = -Math.toRadians(300);
          dotX2 = controlPt.getX() + radius * Math.cos(startAngleRad + t2 * sweepAngleRad);
          dotY2 = controlPt.getY() - radius * Math.sin(startAngleRad + t2 * sweepAngleRad);
        } else {
          dotX2 = mt2 * mt2 * source.x + 2 * mt2 * t2 * edge.controlX + t2 * t2 * target.x;
          dotY2 = mt2 * mt2 * source.y + 2 * mt2 * t2 * edge.controlY + t2 * t2 * target.y;
        }

        oldTx = g2d.getTransform();
        g2d.translate(dotX2, dotY2);
        g2d.scale(1 / zoomFactor, 1 / zoomFactor);
        g2d.setColor(Color.WHITE);
        g2d.fillOval(-3, -3, 6, 6);
        g2d.setTransform(oldTx);
      }

      // --- Edge Label ---
      if (relationship != null && !relationship.trim().isEmpty()) {
        oldTx = g2d.getTransform();
        g2d.setFont(new Font("SansSerif", Font.ITALIC, 12));
        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(relationship);
        int textHeight = fm.getHeight();

        double midX = edge.controlX;
        double midY = edge.controlY;

        g2d.translate(midX, midY);
        g2d.scale(1 / zoomFactor, 1 / zoomFactor);

        int padding = 5;
        int arc = 15;
        int rectWidth = textWidth + padding * 2;
        int rectHeight = textHeight;
        int rectX = -rectWidth / 2;
        int rectY = -fm.getAscent();

        Color bgColor = isDark ? new Color(30, 30, 30, 200) : new Color(255, 255, 255, 200);
        g2d.setColor(bgColor);
        g2d.fillRoundRect(rectX, rectY, rectWidth, rectHeight, arc, arc);

        g2d.setColor(new Color(255, 255, 255, 100));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(rectX, rectY, rectWidth, rectHeight, arc, arc);

        Color textColor = isDark ? Color.WHITE : Color.DARK_GRAY;
        g2d.setColor(textColor);
        g2d.drawString(relationship, -textWidth / 2, 0);

        g2d.setTransform(oldTx);
      }
    }

    private void drawNode(Graphics2D g2d, Node node, boolean isDark) {
      Color nodeColor;
      if (node.color != null) {
        nodeColor = node.color;
      } else {
        nodeColor = "Ingress".equals(node.type) ? new Color(66, 135, 245) : new Color(245, 120, 66);
      }
      g2d.setColor(nodeColor);
      g2d.fillOval((int) node.x - 20, (int) node.y - 20, 40, 40);

      // Render borders and highlights based on state
      if (node == highlightedNode) { // Search highlight takes precedence
        float pulse = (float) Math.abs(Math.sin(animationTick * 0.3)) * 3f + 2f;
        g2d.setStroke(new BasicStroke(pulse / (float) zoomFactor));
        g2d.setColor(Color.YELLOW);
        g2d.drawOval((int) node.x - 20, (int) node.y - 20, 40, 40);
      } else if (node == activeSelection) { // Visual cue for node selected to start a connection
        g2d.setStroke(new BasicStroke(2.5f / (float) zoomFactor));
        g2d.setColor(Color.CYAN);
        g2d.drawOval((int) node.x - 20, (int) node.y - 20, 40, 40);
      } else if (selectedNodes.contains(node)) {
        g2d.setStroke(new BasicStroke(2.5f / (float) zoomFactor));
        g2d.setColor(Color.WHITE);
        g2d.drawOval((int) node.x - 20, (int) node.y - 20, 40, 40);
      } else { // Default border
        Color borderColor = isDark ? Color.LIGHT_GRAY : Color.BLACK;
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke(2f / (float) zoomFactor));
        g2d.drawOval((int) node.x - 20, (int) node.y - 20, 40, 40);
      }

      // --- Node Label ---
      AffineTransform oldTx = g2d.getTransform();
      g2d.translate(node.x, node.y);
      g2d.scale(1 / zoomFactor, 1 / zoomFactor);
      g2d.setColor(Color.WHITE);
      FontMetrics fm = g2d.getFontMetrics();
      int textWidth = fm.stringWidth(node.label);
      g2d.drawString(node.label, -textWidth / 2, fm.getAscent() / 2);
      g2d.setTransform(oldTx);

      // --- Note Icon ---
      if (node.note != null && !node.note.trim().isEmpty()) {
        oldTx = g2d.getTransform();
        g2d.translate(node.x + 10, node.y - 18);
        g2d.scale(1 / zoomFactor, 1 / zoomFactor);

        g2d.setColor(new Color(255, 255, 153));
        g2d.fillRect(0, 0, 8, 8);
        g2d.setColor(Color.GRAY);
        g2d.drawRect(0, 0, 8, 8);
        g2d.setTransform(oldTx);
      }
    }
  }

  public ConnectionViewer() {
    setTitle("Connection Viewer");
    prefs = Preferences.userNodeForPackage(ConnectionViewer.class);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            saveState();
            e.getWindow().dispose();
          }
        });

    setLayout(new BorderLayout());
    db = new GraphDatabaseSimulator();

    JPanel controlPanel = new JPanel(new BorderLayout(0, 5)); // Added vertical gap
    controlPanel.setBorder(new EmptyBorder(10, 10, 0, 10));

    // --- MODIFICATION 1: SEARCH PANEL ---
    // Use BorderLayout to allow the search combo box to fill the width.
    JPanel searchPanel = new JPanel(new BorderLayout());
    // The "Search:" JLabel is removed.

    searchCombo = new JComboBox<>();
    searchCombo.setEditable(true);
    // The setPreferredSize() call is removed to allow resizing.
    searchPanel.add(searchCombo, BorderLayout.CENTER); // Add to CENTER to fill space.
    controlPanel.add(searchPanel, BorderLayout.NORTH);

    // --- MODIFICATION 2: IMPORT/EXPORT PANEL ---
    // Use GridLayout to make buttons share space equally.
    JPanel importExportPanel = new JPanel(new GridLayout(1, 2, 5, 0)); // 1 row, 2 cols, 5px h-gap
    JButton importButton = new JButton("Import");
    importButton.addActionListener(e -> importData());
    JButton exportButton = new JButton("Export");
    exportButton.addActionListener(e -> exportData());
    importExportPanel.add(importButton);
    importExportPanel.add(exportButton);
    controlPanel.add(importExportPanel, BorderLayout.CENTER);


    JButton clearButton = new JButton("Reset Canvas");
    clearButton.addActionListener(
        e -> {
          int confirm =
              JOptionPane.showConfirmDialog(
                  this,
                  "Are you sure you want to reset the canvas? This cannot be undone.",
                  "Confirm Reset",
                  JOptionPane.YES_NO_OPTION,
                  JOptionPane.WARNING_MESSAGE);
          if (confirm == JOptionPane.YES_OPTION) {
            graphPanel.clear();
          }
        });
    graphPanel = new GraphPanel();

    messageArea = new JTextArea();
    messageArea.setEditable(false);

    messagePanel = new JPanel(new BorderLayout(5, 5));
    messagePanel.setBorder(BorderFactory.createTitledBorder("Node Console"));
    messagePanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);

    // Node Info Panel
    nodeInfoPanel = new JPanel(new BorderLayout(5, 5));
    nodeInfoPanel.setBorder(BorderFactory.createTitledBorder("Node Info"));
    nodeNameLabel = new JLabel("No node selected");
    nodeNotesArea = new JTextArea(5, 20);
    nodeNotesArea.setEditable(false);
    nodeNotesArea.setLineWrap(true);
    nodeNotesArea.setWrapStyleWord(true);
    connectivityList = new JList<>();
    JScrollPane notesScrollPane = new JScrollPane(nodeNotesArea);
    JScrollPane connectivityScrollPane = new JScrollPane(connectivityList);
    TitledBorder connectivityBorder = BorderFactory.createTitledBorder("Node Connectivity");
    connectivityScrollPane.setBorder(connectivityBorder);
    nodeInfoPanel.add(nodeNameLabel, BorderLayout.NORTH);
    nodeInfoPanel.add(notesScrollPane, BorderLayout.CENTER);
    nodeInfoPanel.add(connectivityScrollPane, BorderLayout.SOUTH);

    JSplitPane messageNodeSplit =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, messagePanel, nodeInfoPanel);
    messageNodeSplit.setResizeWeight(0.5);

    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    bottomPanel.add(clearButton);

    JPanel sidePanel = new JPanel(new BorderLayout());
    sidePanel.add(controlPanel, BorderLayout.NORTH);
    sidePanel.add(messageNodeSplit, BorderLayout.CENTER);
    sidePanel.add(bottomPanel, BorderLayout.SOUTH);

    scrollPane = new JScrollPane(graphPanel);
    scrollPane.setWheelScrollingEnabled(false);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidePanel, scrollPane);
    splitPane.setOneTouchExpandable(true);
    splitPane.setDividerLocation(250);

    add(splitPane, BorderLayout.CENTER);

    loadState(); // Load window size and location before packing
    captureState(); // Save the initial state for undo

    pack();
    setLocationRelativeTo(null); // Fallback if no prefs

    final JTextComponent editor = (JTextComponent) searchCombo.getEditor().getEditorComponent();

    // --- MODIFICATION 3: SEARCH PLACEHOLDER TEXT ---
    // Add a FocusListener to show placeholder text when the search box is empty.
    final String placeholder = "Search...";
    final Color placeholderColor = UIManager.getColor("textInactiveText");
    final Color defaultColor = editor.getForeground();

    editor.setText(placeholder);
    editor.setForeground(placeholderColor);

    editor.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        if (editor.getText().equals(placeholder)) {
          editor.setText("");
          editor.setForeground(defaultColor);
        }
      }
      @Override
      public void focusLost(FocusEvent e) {
        if (editor.getText().isEmpty()) {
          editor.setText(placeholder);
          editor.setForeground(placeholderColor);
        }
      }
    });


    final DocumentListener docListener =
        new DocumentListener() {
          @Override
          public void insertUpdate(DocumentEvent e) {
            updateAutoComplete();
          }

          @Override
          public void removeUpdate(DocumentEvent e) {
            updateAutoComplete();
          }

          @Override
          public void changedUpdate(DocumentEvent e) {
            updateAutoComplete();
          }

          private void updateAutoComplete() {
            SwingUtilities.invokeLater(
                () -> {
                  String currentText = editor.getText();

                  // Don't run autocompletion on the placeholder text
                  if (currentText.equals(placeholder)) {
                    return;
                  }

                  List<String> matches = new ArrayList<>();
                  if (!currentText.isEmpty()) {
                    matches =
                        db.nodes.values().stream()
                            .map(node -> node.label)
                            .filter(
                                label -> label.toLowerCase().startsWith(currentText.toLowerCase()))
                            .sorted()
                            .limit(5)
                            .toList();
                  }

                  editor.getDocument().removeDocumentListener(this);

                  searchCombo.removeAllItems();
                  matches.forEach(searchCombo::addItem);
                  editor.setText(currentText);

                  if (!matches.isEmpty() && editor.isFocusOwner()) {
                    searchCombo.showPopup();
                  } else {
                    searchCombo.hidePopup();
                  }

                  editor.getDocument().addDocumentListener(this);
                });
          }
        };

    searchCombo.addActionListener(
        e -> {
          Object selectedItem = searchCombo.getSelectedItem();
          if (selectedItem == null || selectedItem.toString().equals(placeholder)) { // Ignore placeholder
            graphPanel.highlightedNode = null;
            graphPanel.repaint();
            return;
          }

          String nodeLabel = selectedItem.toString();
          Node node = db.findNode(nodeLabel);

          if (node != null) {
            graphPanel.highlightedNode = node;
            graphPanel.centerNode(node);
          } else {
            graphPanel.highlightedNode = null;
          }
          graphPanel.repaint();
        });

    editor.getDocument().addDocumentListener(docListener);
  }

  private void updateNodeInfoPanel(Node node) {
    if (node != null) {
      nodeNameLabel.setText("Node: " + node.label);
      nodeNotesArea.setText(node.note);
      DefaultListModel<String> model = new DefaultListModel<>();

      List<String> path = new ArrayList<>();
      if (hasCycle(node, path)) {
        model.addElement("CYCLE DETECTED!");
        model.addElement(String.join(" -> ", path));
      }

      Set<String> reachableNodes = new TreeSet<>();
      collectReachableNodes(node, new HashSet<>(), reachableNodes);
      reachableNodes.remove(node.label); // Ensure the node doesn't list itself

      for (String reachableNode : reachableNodes) {
        model.addElement(reachableNode);
      }
      connectivityList.setModel(model);

    } else {
      nodeNameLabel.setText("No node selected");
      nodeNotesArea.setText("");
      connectivityList.setModel(new DefaultListModel<>());
    }
  }

  private boolean hasCycle(Node startNode, List<String> cyclePath) {
    Map<Node, Node> parentMap = new HashMap<>();
    Queue<Node> queue = new LinkedList<>();
    Set<Node> visited = new HashSet<>();

    queue.add(startNode);
    visited.add(startNode);

    while (!queue.isEmpty()) {
      Node current = queue.poll();

      // Check neighbors
      for (Edge edge : db.adjacencyList.getOrDefault(current.id, Collections.emptyList())) {
        Node neighbor = edge.target;
        if (neighbor.equals(startNode)) {
          // Found a cycle
          Node backTrack = current;
          cyclePath.add(startNode.label);
          while (backTrack != null && !backTrack.equals(startNode)) {
            cyclePath.addFirst(backTrack.label);
            backTrack = parentMap.get(backTrack);
          }
          cyclePath.addFirst(startNode.label);
          return true;
        }
        if (!visited.contains(neighbor)) {
          visited.add(neighbor);
          parentMap.put(neighbor, current);
          queue.add(neighbor);
        }
      }
      // Check duplex neighbors
      for (Edge edge : db.reverseAdjacencyList.getOrDefault(current.id, Collections.emptyList())) {
        if (edge.isduplex) {
          Node neighbor = edge.source;
          if (neighbor.equals(startNode)) {
            // Found a cycle
            Node backTrack = current;
            cyclePath.add(startNode.label);
            while (backTrack != null && !backTrack.equals(startNode)) {
              cyclePath.addFirst(backTrack.label);
              backTrack = parentMap.get(backTrack);
            }
            cyclePath.addFirst(startNode.label);
            return true;
          }
          if (!visited.contains(neighbor)) {
            visited.add(neighbor);
            parentMap.put(neighbor, current);
            queue.add(neighbor);
          }
        }
      }
    }
    return false;
  }

  private void collectReachableNodes(Node currentNode, Set<Node> visited, Set<String> reachable) {
    if (currentNode == null || !visited.add(currentNode)) {
      return;
    }

    // Direct connections
    db.adjacencyList
        .getOrDefault(currentNode.id, Collections.emptyList())
        .forEach(
            edge -> {
              if (reachable.add(edge.target.label)) {
                collectReachableNodes(edge.target, new HashSet<>(visited), reachable);
              }
            });

    // Duplex connections (reverse)
    db.reverseAdjacencyList.getOrDefault(currentNode.id, Collections.emptyList()).stream()
        .filter(edge -> edge.isduplex)
        .forEach(
            edge -> {
              if (reachable.add(edge.source.label)) {
                collectReachableNodes(edge.source, new HashSet<>(visited), reachable);
              }
            });
  }

  private void importData() {
    JFileChooser fileChooser = new JFileChooser();
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      File file = fileChooser.getSelectedFile();
      try (FileReader reader = new FileReader(file)) {
        Gson gson =
            new GsonBuilder().registerTypeAdapter(Color.class, new ColorTypeAdapter()).create();
        restoreState(gson.fromJson(reader, GraphData.class));
        captureState(); // Set the imported state as the new baseline
      } catch (IOException e) {
        JOptionPane.showMessageDialog(
            this, "Error importing file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void exportData() {
    GraphData graphData = new GraphData();
    graphData.nodes = new ArrayList<>(graphPanel.activeNodes.values());
    graphData.edges =
        graphPanel.activeEdges.stream().map(EdgeData::new).collect(Collectors.toList());

    JFileChooser fileChooser = new JFileChooser();
    if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
      File file = new File(fileChooser.getSelectedFile() + ".json");
      try (FileWriter writer = new FileWriter(file)) {
        Gson gson =
            new GsonBuilder()
                .registerTypeAdapter(Color.class, new ColorTypeAdapter())
                .setPrettyPrinting()
                .create();
        gson.toJson(graphData, writer);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(
            this, "Error exporting file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void saveState() {
    // Window geometry
    prefs.putInt(PREF_WINDOW_X, getX());
    prefs.putInt(PREF_WINDOW_Y, getY());
    prefs.putInt(PREF_WINDOW_WIDTH, getWidth());
    prefs.putInt(PREF_WINDOW_HEIGHT, getHeight());

    // Nodes
    String nodeIds = String.join(",", graphPanel.activeNodes.keySet());
    prefs.put(PREF_NODE_IDS, nodeIds);

    for (Node node : graphPanel.activeNodes.values()) {
      String prefix = "node." + node.id + ".";
      prefs.put(prefix + "label", node.label);
      prefs.put(prefix + "type", node.type);
      prefs.putDouble(prefix + "x", node.x);
      prefs.putDouble(prefix + "y", node.y);
      prefs.put(prefix + "note", node.note);
      if (node.color != null) {
        prefs.putInt(prefix + "color", node.color.getRGB());
      } else {
        prefs.remove(prefix + "color");
      }
    }

    // Edges
    prefs.putInt(PREF_EDGE_COUNT, graphPanel.activeEdges.size());
    for (int i = 0; i < graphPanel.activeEdges.size(); i++) {
      Edge edge = graphPanel.activeEdges.get(i);
      String prefix = "edge." + i + ".";
      prefs.put(prefix + "source", edge.source.id);
      prefs.put(prefix + "target", edge.target.id);
      prefs.put(prefix + "relationship", edge.relationship);
      prefs.putDouble(prefix + "controlX", edge.controlX);
      prefs.putDouble(prefix + "controlY", edge.controlY);
      prefs.putBoolean(prefix + "isduplex", edge.isduplex);
      if (edge.color != null) {
        prefs.putInt(prefix + "color", edge.color.getRGB());
      } else {
        prefs.remove(prefix + "color");
      }
    }
  }

  private void loadState() {
    // Window geometry
    int x = prefs.getInt(PREF_WINDOW_X, -1);
    int y = prefs.getInt(PREF_WINDOW_Y, -1);
    int width = prefs.getInt(PREF_WINDOW_WIDTH, 1200);
    int height = prefs.getInt(PREF_WINDOW_HEIGHT, 800);

    setSize(width, height);
    if (x != -1 && y != -1) {
      setLocation(x, y);
    } else {
      setLocationRelativeTo(null);
    }

    GraphData data = new GraphData();
    data.nodes = new ArrayList<>();
    data.edges = new ArrayList<>();

    // Nodes
    String nodeIdsString = prefs.get(PREF_NODE_IDS, "");
    if (!nodeIdsString.isEmpty()) {
      String[] nodeIds = nodeIdsString.split(",");
      for (String id : nodeIds) {
        String prefix = "node." + id + ".";
        String label = prefs.get(prefix + "label", id);
        String type = prefs.get(prefix + "type", "Ingress");
        double nodeX = prefs.getDouble(prefix + "x", 0);
        double nodeY = prefs.getDouble(prefix + "y", 0);
        String note = prefs.get(prefix + "note", "");

        Node node = new Node(id, label, type);
        node.x = nodeX;
        node.y = nodeY;
        node.note = note;
        int rgb = prefs.getInt(prefix + "color", Integer.MAX_VALUE);
        if (rgb != Integer.MAX_VALUE) {
          node.color = new Color(rgb);
        }
        data.nodes.add(node);
      }
    }

    // Edges
    int edgeCount = prefs.getInt(PREF_EDGE_COUNT, 0);
    for (int i = 0; i < edgeCount; i++) {
      String prefix = "edge." + i + ".";
      String sourceId = prefs.get(prefix + "source", null);
      String targetId = prefs.get(prefix + "target", null);
      if (sourceId != null && targetId != null) {
        EdgeData edgeData =
            new EdgeData(new Edge(new Node(sourceId, "", ""), new Node(targetId, "", ""), ""));
        edgeData.relationship = prefs.get(prefix + "relationship", "");
        edgeData.controlX = prefs.getDouble(prefix + "controlX", 0);
        edgeData.controlY = prefs.getDouble(prefix + "controlY", 0);
        edgeData.isDuplex = prefs.getBoolean(prefix + "isduplex", false);
        int rgb = prefs.getInt(prefix + "color", Integer.MAX_VALUE);
        if (rgb != Integer.MAX_VALUE) {
          edgeData.color = rgb;
        }
        data.edges.add(edgeData);
      }
    }
    restoreState(data);
  }

  private void captureState() {
    GraphData state = new GraphData();
    state.nodes = new ArrayList<>();
    for (Node node : graphPanel.activeNodes.values()) {
      Node copy = new Node(node.id, node.label, node.type);
      copy.x = node.x;
      copy.y = node.y;
      copy.note = node.note;
      copy.color = node.color;
      state.nodes.add(copy);
    }
    state.edges = graphPanel.activeEdges.stream().map(EdgeData::new).collect(Collectors.toList());
    undoStack.push(state);
    redoStack.clear();
  }

  private void captureAndSaveState() {
    captureState();
    saveState();
  }

  private void restoreState(GraphData state) {
    if (state == null) return;
    graphPanel.activeNodes.clear();
    graphPanel.activeEdges.clear();
    db.nodes.clear();
    db.adjacencyList.clear();

    for (Node node : state.nodes) {
      db.nodes.put(node.id, node);
      graphPanel.activeNodes.put(node.id, node);
      db.adjacencyList.put(node.id, new ArrayList<>());
    }

    for (EdgeData edgeData : state.edges) {
      Node source = db.findNode(edgeData.sourceId);
      Node target = db.findNode(edgeData.targetId);
      if (source != null && target != null) {
        Edge edge = new Edge(source, target, edgeData.relationship);
        edge.controlX = edgeData.controlX;
        edge.controlY = edgeData.controlY;
        edge.isduplex = edgeData.isDuplex;
        if (edgeData.color != null) {
          edge.color = new Color(edgeData.color);
        }
        graphPanel.activeEdges.add(edge);
        db.addEdge(edge);
      }
    }
    db.buildReverseAdjacencyList();
    graphPanel.repaint();
  }

  private void undo() {
    if (undoStack.size() > 1) {
      redoStack.push(undoStack.pop());
      restoreState(undoStack.peek());
    }
  }

  private void redo() {
    if (!redoStack.isEmpty()) {
      GraphData state = redoStack.pop();
      undoStack.push(state);
      restoreState(state);
    }
  }

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(
        () -> {
          new ConnectionViewer().setVisible(true);
        });
  }
}
