package xxx.com.dragndrop;

import com.formdev.flatlaf.FlatDarculaLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * TaskOrganizerApp
 *
 * A single-file Java Swing app for organizing tasks and arbitrarily deep subtasks.
 * - Drag & drop to reorder or move tasks (including whole subtrees) under other tasks.
 * - Simple, material-inspired cards for each task.
 * - Right-click (or use buttons) to add task, add subtask, rename, delete, expand/collapse.
 *
 * Compile & run (Java 11+):
 *   javac TaskOrganizerApp.java && java TaskOrganizerApp
 */
public class TaskOrganizerApp extends JFrame {
  private final TaskTree tree;
  private final DefaultTreeModel model;

  public static void main(String[] args) {
    FlatDarculaLaf.setup();

    SwingUtilities.invokeLater(() -> {
     //setSystemLookAndFeel();
      new TaskOrganizerApp().setVisible(true);
    });
  }

  public TaskOrganizerApp() {
    super("Task Organizer");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(900, 640);
    setLocationRelativeTo(null);

    // Root is a hidden container node
    TaskNode root = new TaskNode("ROOT");
    model = new DefaultTreeModel(root);
    tree = new TaskTree(model);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setRowHeight(48);
    tree.setToggleClickCount(1);

    // Seed example data from the prompt
    seedExampleData(root);
    model.reload();
    tree.expandAll();

    // Toolbar
    JToolBar toolbar = buildToolbar();

    // Context menu
    JPopupMenu menu = buildContextMenu();
    tree.setComponentPopupMenu(menu);
    tree.addMouseListener(new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) { maybeSelectOnRightClick(e); }
      @Override public void mouseReleased(MouseEvent e) { maybeSelectOnRightClick(e); }
      private void maybeSelectOnRightClick(MouseEvent e) {
        if (e.isPopupTrigger()) {
          TreePath path = tree.getPathForLocation(e.getX(), e.getY());
          if (path != null) tree.setSelectionPath(path);
        }
      }
    });

    // Layout
    JPanel rootPanel = new JPanel(new BorderLayout());
    rootPanel.setBorder(new EmptyBorder(12, 12, 12, 12));
    rootPanel.add(toolbar, BorderLayout.NORTH);
    rootPanel.add(new JScrollPane(tree), BorderLayout.CENTER);
    setContentPane(rootPanel);
  }

  private static void setSystemLookAndFeel() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      // Subtle material-inspired tweaks
      UIManager.put("Tree.paintLines", Boolean.FALSE);
      UIManager.put("Tree.leftChildIndent", 8);
      UIManager.put("Tree.rightChildIndent", 18);
      UIManager.put("Tree.rowHeight", 48);
      UIManager.put("ScrollBar.width", 12);
    } catch (Exception ignored) {}
  }

  private JToolBar buildToolbar() {
    JToolBar tb = new JToolBar();
    tb.setFloatable(false);
    tb.setBorder(new EmptyBorder(6, 6, 6, 6));
    tb.add(makeButton("+ Task", e -> addTaskAtRoot()));
    tb.add(makeButton("+ Subtask", e -> addSubtask()));
    tb.add(makeButton("Rename", e -> renameSelected()));
    tb.add(makeButton("Delete", e -> deleteSelected()));
    tb.addSeparator();
    tb.add(makeButton("Expand All", e -> tree.expandAll()));
    tb.add(makeButton("Collapse All", e -> tree.collapseAll()));
    tb.addSeparator();
    JLabel hint = new JLabel("Drag a task to reorder or drop onto another task to re-parent");
    hint.setBorder(new EmptyBorder(0, 12, 0, 0));
    hint.setForeground(new Color(0x555555));
    tb.add(hint);
    return tb;
  }

  private JButton makeButton(String label, ActionListener al) {
    JButton b = new JButton(label);
    b.addActionListener(al);
    b.putClientProperty("JButton.buttonType", "roundRect");
    b.setFocusPainted(false);
    return b;
  }

  private JPopupMenu buildContextMenu() {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem addTask = new JMenuItem("Add Task at Root");
    JMenuItem addSub = new JMenuItem("Add Subtask");
    JMenuItem rename = new JMenuItem("Rename");
    JMenuItem del = new JMenuItem("Delete");
    addTask.addActionListener(e -> addTaskAtRoot());
    addSub.addActionListener(e -> addSubtask());
    rename.addActionListener(e -> renameSelected());
    del.addActionListener(e -> deleteSelected());
    menu.add(addTask);
    menu.add(addSub);
    menu.add(rename);
    menu.add(del);
    return menu;
  }

  private void addTaskAtRoot() {
    String name = prompt("New task name:", "New Task");
    if (name == null || name.isBlank()) return;
    TaskNode node = new TaskNode(name.trim());
    TaskNode root = (TaskNode) model.getRoot();
    model.insertNodeInto(node, root, root.getChildCount());
    tree.scrollPathToVisible(new TreePath(node.getPath()));
  }

  private void addSubtask() {
    TaskNode selected = tree.getSelectedTaskNode();
    if (selected == null) return;
    String name = prompt("New subtask name:", "New Subtask");
    if (name == null || name.isBlank()) return;
    TaskNode node = new TaskNode(name.trim());
    model.insertNodeInto(node, selected, selected.getChildCount());
    tree.expandPath(new TreePath(selected.getPath()));
    tree.scrollPathToVisible(new TreePath(node.getPath()));
  }

  private void renameSelected() {
    TaskNode selected = tree.getSelectedTaskNode();
    if (selected == null) return;
    String name = prompt("Rename task:", selected.getName());
    if (name == null || name.isBlank()) return;
    selected.setUserObject(name.trim());
    model.nodeChanged(selected);
  }

  private void deleteSelected() {
    TaskNode selected = tree.getSelectedTaskNode();
    if (selected == null) return;
    if (JOptionPane.showConfirmDialog(this, "Delete '" + selected.getName() + "'?", "Delete",
        JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
      MutableTreeNode parent = (MutableTreeNode) selected.getParent();
      if (parent != null) {
        model.removeNodeFromParent(selected);
      }
    }
  }

  private static String prompt(String msg, String initial) {
    return (String) JOptionPane.showInputDialog(null, msg, "Task Organizer",
        JOptionPane.PLAIN_MESSAGE, null, null, initial);
  }

  private void seedExampleData(TaskNode root) {
    TaskNode pack = new TaskNode("Pack for trip to Europe");
    pack.add(new TaskNode("Shorts"));
    pack.add(new TaskNode("Shoes"));
    TaskNode camping = new TaskNode("Camping gear");
    camping.add(new TaskNode("Bear mace"));
    camping.add(new TaskNode("Tent"));
    camping.add(new TaskNode("Soap"));
    pack.add(camping);

    TaskNode clean = new TaskNode("Clean House");
    clean.add(new TaskNode("clean fridge"));
    clean.add(new TaskNode("sweep kitchen"));
    clean.add(new TaskNode("take out trash"));

    root.add(pack);
    root.add(clean);
  }

  // ===== Task Node =====
  static class TaskNode extends DefaultMutableTreeNode {
    public TaskNode(String name) { super(name); }
    public String getName() { return Objects.toString(getUserObject(), ""); }
    @Override public String toString() { return getName(); }
  }

  // ===== Tree with custom renderer & DnD =====
  static class TaskTree extends JTree {
    TaskTree(DefaultTreeModel model) {
      super(model);
      setCellRenderer(new CardRenderer());
      setBorder(new EmptyBorder(8, 8, 8, 8));
      putClientProperty("JTree.lineStyle", "None");
      setDragEnabled(true);
      setDropMode(DropMode.ON_OR_INSERT);
      setTransferHandler(new TreeTransferHandler());

      // Keyboard: delete/rename/new
      getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
      getActionMap().put("delete", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          Container top = SwingUtilities.getWindowAncestor(TaskTree.this);
          if (top instanceof TaskOrganizerApp) {
            ((TaskOrganizerApp) top).deleteSelected();
          }
        }
      });
      getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "rename");
      getActionMap().put("rename", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) {
          Container top = SwingUtilities.getWindowAncestor(TaskTree.this);
          if (top instanceof TaskOrganizerApp) {
            ((TaskOrganizerApp) top).renameSelected();
          }
        }
      });
    }

    public TaskNode getSelectedTaskNode() {
      TreePath p = getSelectionPath();
      if (p == null) return null;
      Object n = p.getLastPathComponent();
      return (n instanceof TaskNode) ? (TaskNode) n : null;
    }

    public void expandAll() {
      for (int i = 0; i < getRowCount(); i++) expandRow(i);
    }
    public void collapseAll() {
      for (int i = getRowCount() - 1; i >= 0; i--) collapseRow(i);
    }
  }

  // ===== Pretty card renderer =====
  static class CardRenderer extends JPanel implements TreeCellRenderer {
    private final JLabel label = new JLabel();
    private boolean selected;
    private int depth;

    CardRenderer() {
      setOpaque(false);
      setLayout(new BorderLayout());
      label.setBorder(new EmptyBorder(12, 16, 12, 16));
      label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
      add(label, BorderLayout.CENTER);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
        boolean expanded, boolean leaf, int row, boolean hasFocus) {
      selected = sel;
      if (value instanceof DefaultMutableTreeNode) {
        Object uo = ((DefaultMutableTreeNode) value).getUserObject();
        label.setText(String.valueOf(uo));
        depth = tree.getPathForRow(row) != null ? tree.getPathForRow(row).getPathCount() - 1 : 0;
      } else {
        label.setText(String.valueOf(value));
        depth = 0;
      }
      return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      // Left indent per depth (visual \"tick\")
      int indent = Math.max(0, (depth - 1)) * 20; // root is hidden; depth 1 = top level
      int x = indent;
      int y = 6;
      int w = getWidth() - indent - 6;
      int h = getHeight() - 12;
      int arc = 16;

      // Shadow (subtle elevation)
      g2.setColor(new Color(0, 0, 0, 26));
      g2.fillRoundRect(x + 2, y + 3, w, h, arc, arc);

      // Card background
      Color base = selected ? new Color(0xE3F2FD) : new Color(0xFFFFFF);
      g2.setColor(base);
      g2.fillRoundRect(x, y, w, h, arc, arc);

      // Border
      g2.setColor(new Color(0xE0E0E0));
      g2.drawRoundRect(x, y, w, h, arc, arc);

      // Leading color bar for hierarchy level (material-ish accent)
      int barW = 6;
      Color[] accents = {
          new Color(0x2962FF), // level 1
          new Color(0x00C853), // level 2
          new Color(0xAA00FF), // level 3
          new Color(0xFF6D00), // level 4+
      };
      Color bar = accents[Math.min(depth - 1, accents.length - 1) < 0 ? 0 : Math.min(depth - 1, accents.length - 1)];
      g2.setColor(bar);
      g2.fillRoundRect(x, y, Math.min(barW, w), h, arc, arc);

      g2.dispose();
      super.paintComponent(g);
    }
  }

  // ===== Drag & Drop TransferHandler =====
  static class TreeTransferHandler extends TransferHandler {
    private final DataFlavor nodesFlavor;
    private TaskNode[] nodesToRemove;

    TreeTransferHandler() {
      try {
        String mimeType = DataFlavor.javaJVMLocalObjectMimeType + ";class=\"" + TaskNode[].class.getName() + "\"";
        nodesFlavor = new DataFlavor(mimeType);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean canImport(TransferSupport support) {
      if (!support.isDrop()) return false;
      support.setShowDropLocation(true);
      if (!support.isDataFlavorSupported(nodesFlavor)) return false;

      // Disallow dropping a node onto itself or its own subtree
      JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
      JTree tree = (JTree) support.getComponent();
      TreePath dest = dl.getPath();
      try {
        TaskNode[] nodes = (TaskNode[]) support.getTransferable().getTransferData(nodesFlavor);
        if (dest == null || nodes == null) return false;
        TaskNode target = (TaskNode) dest.getLastPathComponent();
        for (TaskNode n : nodes) {
          if (n == target) return false;
          if (isNodeDescendant(target, n)) return false;
        }
      } catch (Exception ignored) {}
      return true;
    }

    private boolean isNodeDescendant(TreeNode node, TreeNode potentialAncestor) {
      if (node == potentialAncestor) return true;
      TreeNode parent = node.getParent();
      while (parent != null) {
        if (parent == potentialAncestor) return true;
        parent = parent.getParent();
      }
      return false;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
      JTree tree = (JTree) c;
      TreePath[] paths = tree.getSelectionPaths();
      if (paths == null) return null;

      List<TaskNode> copies = new ArrayList<>();
      List<TaskNode> toRemove = new ArrayList<>();
      for (TreePath p : paths) {
        TaskNode node = (TaskNode) p.getLastPathComponent();
        copies.add(copy(node)); // copy entire subtree
        toRemove.add(node);
      }
      TaskNode[] nodes = copies.toArray(new TaskNode[0]);
      nodesToRemove = toRemove.toArray(new TaskNode[0]);
      return new NodesTransferable(nodes, nodesFlavor);
    }

    private TaskNode copy(TaskNode node) {
      TaskNode clone = new TaskNode(node.getName());
      @SuppressWarnings("unchecked") Enumeration<TreeNode> e = node.children();
      while (e.hasMoreElements()) {
        clone.add(copy((TaskNode) e.nextElement()));
      }
      return clone;
    }

    @Override
    public int getSourceActions(JComponent c) { return MOVE; }

    @Override
    public boolean importData(TransferSupport support) {
      if (!canImport(support)) return false;
      JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
      JTree tree = (JTree) support.getComponent();
      DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
      TreePath dest = dl.getPath();
      int index = dl.getChildIndex();
      TaskNode parent = (TaskNode) dest.getLastPathComponent();

      try {
        TaskNode[] nodes = (TaskNode[]) support.getTransferable().getTransferData(nodesFlavor);
        if (index == -1) { // dropped ON a node => append as last child
          index = parent.getChildCount();
        }
        for (TaskNode n : nodes) {
          model.insertNodeInto(n, parent, Math.min(index, parent.getChildCount()));
          index++;
        }
        // Remove originals if move within same component
        if (support.getSourceDropActions() == MOVE && nodesToRemove != null) {
          for (TaskNode n : nodesToRemove) {
            MutableTreeNode p = (MutableTreeNode) n.getParent();
            if (p != null) model.removeNodeFromParent(n);
          }
        }
        nodesToRemove = null;
        TreePath path = new TreePath(parent.getPath());
        tree.expandPath(path);
        tree.scrollPathToVisible(path);
        return true;
      } catch (Exception ex) {
        ex.printStackTrace();
      }
      return false;
    }

    // Wrap for DnD
    static class NodesTransferable implements Transferable {
      private final TaskNode[] nodes;
      private final DataFlavor flavor;
      NodesTransferable(TaskNode[] nodes, DataFlavor flavor) {
        this.nodes = nodes; this.flavor = flavor; }
      @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{flavor}; }
      @Override public boolean isDataFlavorSupported(DataFlavor f) { return flavor.equals(f); }
      @Override public Object getTransferData(DataFlavor f) { return nodes; }
    }
  }
}
