package xxx.com.fullscreen;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.sql.*;
import java.util.*;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

public class TaskOrganizer extends JFrame {

  // --- Full Screen Fields ---
  private boolean isInFullScreenMode = false;
  private GraphicsDevice activeFullScreenDevice;
  private Dimension lastWindowSize;
  private Point lastWindowLocation;

  // --- UI Components ---
  private final JPanel taskListPanelContainer;
  private final JPanel taskListPanel;
  private final JScrollPane scrollPane;
  private final JButton addButton;
  private final JLayeredPane layeredPane;

  // --- Task Data and Colors ---
  private final List<Task> actualTasks = new ArrayList<>();
  private final List<Color> taskItemColors = new ArrayList<>();
  private int currentColorIndex = 0;

  // --- Constants ---
  private static final Color COMPLETED_TASK_TEXT_COLOR = Color.GRAY;
  private static final Color DEFAULT_TASK_TEXT_COLOR = Color.WHITE;
  private static final Color APP_BACKGROUND_COLOR = new Color(230, 230, 230);
  private static final int TASK_ITEM_GAP = 8;
  private static final int INDENT_SIZE = 25;

  // --- Drag and Drop State ---
  private Task taskBeingDragged = null;
  private int originalDragIndex = -1;
  private int currentDropLineIndex = -1;
  private final MouseAdapter viewportDragHandler;

  // --- Database ---
  private final DatabaseManager databaseManager;

  // --- Hover Highlighting ---
  private Task currentHighlightRoot = null;
  private final List<Task> tasksToHighlight = new ArrayList<>();
  private static final Color HIGHLIGHT_BORDER_COLOR = Color.RED;
  private static final int HIGHLIGHT_BORDER_THICKNESS = 2;

  public static class Task {
    private long id;
    String description;
    boolean isComplete;
    Color backgroundColor;
    List<Task> subTasks;
    Task parentTask;
    int indentLevel;
    int displayOrder;

    public Task(
        String description, Color backgroundColor, Task parent, int indentLevel, int displayOrder) {
      this.id = -1;
      this.description = description;
      this.isComplete = false;
      this.backgroundColor = backgroundColor;
      this.subTasks = new ArrayList<>();
      this.parentTask = parent;
      this.indentLevel = indentLevel;
      this.displayOrder = displayOrder;
    }

    public Task(
        long id,
        String description,
        Color backgroundColor,
        Task parent,
        int indentLevel,
        int displayOrder) {
      this(description, backgroundColor, parent, indentLevel, displayOrder);
      this.id = id;
    }

    public long getId() {
      return id;
    }

    public void setId(long id) {
      this.id = id;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public boolean isComplete() {
      return isComplete;
    }

    public void setComplete(boolean complete) {
      this.isComplete = complete;
    }

    public Color getBackgroundColor() {
      return backgroundColor;
    }

    public void setBackgroundColor(Color color) {
      this.backgroundColor = color;
    }

    public List<Task> getSubTasks() {
      return subTasks;
    }

    public Task getParentTask() {
      return parentTask;
    }

    public void setParentTask(Task parentTask) {
      this.parentTask = parentTask;
    }

    public int getIndentLevel() {
      return indentLevel;
    }

    public boolean hasSubTasks() {
      return !subTasks.isEmpty();
    }

    public void addSubTask(Task subTask) {
      this.subTasks.add(subTask);
      this.subTasks.sort(Comparator.comparingInt(Task::getDisplayOrder));
    }

    public void removeSubTask(Task subTask) {
      this.subTasks.remove(subTask);
    }

    public int getDisplayOrder() {
      return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
      this.displayOrder = displayOrder;
    }

    @Override
    public String toString() {
      return "Task{id="
          + id
          + ", desc='"
          + description
          + '\''
          + ", parentId="
          + (parentTask != null ? parentTask.getId() : "null")
          + ", order="
          + displayOrder
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Task task = (Task) o;
      return id == task.id && id != -1;
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }
  }

  public TaskOrganizer() {
    setTitle("Task List (F11 Full Screen, ESC Exit)");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(800, 600);
    setLocationRelativeTo(null);

    databaseManager = new DatabaseManager();
    initDatabase();
    initializeTaskColors();

    viewportDragHandler =
        new MouseAdapter() {
          @Override
          public void mouseDragged(MouseEvent e) {
            handleDragMotion(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            handleDragRelease(e);
          }
        };

    layeredPane = new JLayeredPane();
    layeredPane.setBackground(APP_BACKGROUND_COLOR);
    layeredPane.setOpaque(true);
    setContentPane(layeredPane);

    taskListPanelContainer = new JPanel(new BorderLayout());
    taskListPanelContainer.setBackground(APP_BACKGROUND_COLOR);
    taskListPanelContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

    taskListPanel =
        new JPanel() {
          @Override
          protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (taskBeingDragged != null
                && currentDropLineIndex != -1
                && taskBeingDragged.getParentTask() == null) {
              Graphics2D g2d = (Graphics2D) g.create();
              g2d.setColor(Color.BLACK);
              g2d.setStroke(new BasicStroke(2));
              int yLine = 0;
              if (getComponentCount() > 0) {
                int visualTaskCount = 0;
                boolean lineDrawn = false;
                for (Component wrapperComp : getComponents()) {
                  if (wrapperComp instanceof JPanel
                      && ((JPanel) wrapperComp).getComponentCount() > 0
                      && ((JPanel) wrapperComp).getComponent(0) instanceof TaskEntryPanel panel) {
                    if (panel.getTask().getParentTask() == null) {
                      if (visualTaskCount == currentDropLineIndex) {
                        yLine = wrapperComp.getY() - TASK_ITEM_GAP / 2;
                        lineDrawn = true;
                        break;
                      }
                      visualTaskCount++;
                    }
                  }
                }
                if (!lineDrawn) {
                  if (actualTasks.isEmpty() && currentDropLineIndex == 0) {
                    yLine = TASK_ITEM_GAP / 2;
                  } else if (currentDropLineIndex == countTopLevelTasks()
                      && getComponentCount() > 0) {
                    Component lastVisibleTopLevelWrapper = null;
                    for (int i = getComponentCount() - 1; i >= 0; i--) {
                      Component comp = getComponent(i);
                      if (comp instanceof JPanel
                          && ((JPanel) comp).getComponentCount() > 0
                          && ((JPanel) comp).getComponent(0) instanceof TaskEntryPanel panel) {
                        if (panel.getTask().getParentTask() == null) {
                          lastVisibleTopLevelWrapper = comp;
                          break;
                        }
                      }
                    }
                    if (lastVisibleTopLevelWrapper != null) {
                      yLine =
                          lastVisibleTopLevelWrapper.getY()
                              + lastVisibleTopLevelWrapper.getHeight()
                              + TASK_ITEM_GAP / 2;
                    } else {
                      yLine = TASK_ITEM_GAP / 2;
                    }
                  } else {
                    yLine = TASK_ITEM_GAP / 2;
                  }
                }
              } else {
                yLine = TASK_ITEM_GAP / 2;
              }
              g2d.drawLine(5, yLine, getWidth() - 5, yLine);
              g2d.dispose();
            }
          }
        };
    taskListPanel.setLayout(new BoxLayout(taskListPanel, BoxLayout.Y_AXIS));
    taskListPanel.setBackground(APP_BACKGROUND_COLOR);
    taskListPanelContainer.add(taskListPanel, BorderLayout.NORTH);

    taskListPanel.addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            Component compOver = taskListPanel.getComponentAt(e.getPoint());
            Task taskToSetAsRoot = null;
            Component current = compOver;
            while (current != null
                && current != taskListPanel
                && current != taskListPanelContainer
                && current != scrollPane) {
              if (current instanceof TaskEntryPanel) {
                taskToSetAsRoot = ((TaskEntryPanel) current).getTask();
                break;
              }
              if (current instanceof JPanel
                  && ((JPanel) current).getLayout() instanceof BorderLayout) {
                Component centerComp =
                    ((BorderLayout) ((JPanel) current).getLayout())
                        .getLayoutComponent(BorderLayout.CENTER);
                if (centerComp instanceof TaskEntryPanel) {
                  taskToSetAsRoot = ((TaskEntryPanel) centerComp).getTask();
                  break;
                }
              }
              current = current.getParent();
            }
            setHighlightRoot(taskToSetAsRoot);
          }
        });

    taskListPanel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseExited(MouseEvent e) {
            if (!taskListPanel.getBounds().contains(e.getPoint())) {
              setHighlightRoot(null);
            }
          }
        });

    scrollPane = new JScrollPane(taskListPanelContainer);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    scrollPane.getViewport().setBackground(APP_BACKGROUND_COLOR);
    layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);

    addButton = new JButton("+");
    addButton.setFont(new Font("Arial", Font.BOLD, 28));
    addButton.setFocusPainted(false);
    addButton.setBackground(new Color(0, 122, 204));
    addButton.setForeground(Color.WHITE);
    addButton.setToolTipText("Add a new top-level task");
    layeredPane.add(addButton, JLayeredPane.PALETTE_LAYER);

    addButton.addActionListener(
        e -> {
          String defaultTaskName = "Task " + (countAllTasksRecursively(actualTasks) + 1);
          addNewTaskToList(defaultTaskName, null, 0, actualTasks.size());
          rebuildTaskListUI();
          scrollToBottom();
          requestFocusInWindow();
        });

    addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_F11) toggleFullScreen();
            else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && isInFullScreenMode)
              exitFullScreenMode();
          }
        });

    layeredPane.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            scrollPane.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            positionAddButton();
            layeredPane.revalidate();
            layeredPane.repaint();
          }
        });

    setFocusable(true);
    loadTasksFromDB();
    rebuildTaskListUI();

    Runtime.getRuntime()
        .addShutdownHook(new Thread(() -> System.out.println("Application shutting down...")));
  }

  private void setHighlightRoot(Task rootTask) {
    if (Objects.equals(currentHighlightRoot, rootTask)) {
      return;
    }
    currentHighlightRoot = rootTask;
    tasksToHighlight.clear();
    if (currentHighlightRoot != null) {
      collectTasksToHighlight(currentHighlightRoot, tasksToHighlight);
    }
    updateTaskHighlightsInUI();
  }

  private void collectTasksToHighlight(Task task, List<Task> list) {
    list.add(task);
    for (Task subTask : task.getSubTasks()) {
      collectTasksToHighlight(subTask, list);
    }
  }

  private void updateTaskHighlightsInUI() {
    for (Component wrapperComp : taskListPanel.getComponents()) {
      if (wrapperComp instanceof JPanel wrapper
          && ((JPanel) wrapperComp).getLayout() instanceof BorderLayout) {
        Component centerComp =
            ((BorderLayout) wrapper.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (centerComp instanceof TaskEntryPanel entryPanel) {
          Task task = entryPanel.getTask();

          Border outerIndentBorder = new EmptyBorder(0, task.getIndentLevel() * INDENT_SIZE, 0, 0);
          Border newWrapperBorder;

          if (tasksToHighlight.contains(task)) {
            Border highlightBorder =
                BorderFactory.createLineBorder(HIGHLIGHT_BORDER_COLOR, HIGHLIGHT_BORDER_THICKNESS);
            newWrapperBorder =
                BorderFactory.createCompoundBorder(outerIndentBorder, highlightBorder);
          } else {
            newWrapperBorder = outerIndentBorder;
          }
          wrapper.setBorder(newWrapperBorder);
        }
      }
    }
    taskListPanel.revalidate();
    taskListPanel.repaint();
  }

  private void initDatabase() {
    try {
      databaseManager.createTableIfNotExists();
    } catch (RuntimeException e) {
      JOptionPane.showMessageDialog(
          this,
          "Error initializing database: "
              + e.getMessage()
              + "\nApplication might not work correctly.",
          "Database Error",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void loadTasksFromDB() {
    try {
      List<Task> loadedTasks = databaseManager.getAllTasks(this);
      actualTasks.clear();
      actualTasks.addAll(loadedTasks);
      if (actualTasks.isEmpty()) {
        addInitialTasksAndSave();
      }
    } catch (RuntimeException e) {
      JOptionPane.showMessageDialog(
          this,
          "Error loading tasks from database: "
              + e.getMessage()
              + "\nApplication might not work correctly.",
          "Database Load Error",
          JOptionPane.ERROR_MESSAGE);
      actualTasks.clear();
      addInitialTasksAndSave();
    }
  }

  private int countTopLevelTasks() {
    return actualTasks.size();
  }

  private int countAllTasksRecursively(List<Task> tasks) {
    int count = 0;
    for (Task task : tasks) {
      count++;
      count += countAllTasksRecursively(task.getSubTasks());
    }
    return count;
  }

  private void initializeTaskColors() {
    taskItemColors.add(new Color(70, 179, 157));
    taskItemColors.add(new Color(144, 190, 109));
    taskItemColors.add(new Color(243, 156, 18));
    taskItemColors.add(new Color(192, 57, 43));
    taskItemColors.add(new Color(84, 110, 122));
    taskItemColors.add(new Color(22, 122, 101));
  }

  private void positionAddButton() {
    if (addButton == null || layeredPane == null) return;
    int buttonSize = 60;
    int margin = 20;
    addButton.setBounds(
        layeredPane.getWidth() - buttonSize - margin,
        layeredPane.getHeight() - buttonSize - margin,
        buttonSize,
        buttonSize);
  }

  private void addInitialTasksAndSave() {
    Task parentTask = addNewTaskToList("Parent Task (Edit me!)", null, 0, actualTasks.size());
    if (parentTask != null) {
      addNewTaskToList("Sub-Task 1.1", parentTask, 1, parentTask.getSubTasks().size());
      Task subParent =
          addNewTaskToList(
              "Sub-Task 1.2 (has children)", parentTask, 1, parentTask.getSubTasks().size());
      if (subParent != null) {
        addNewTaskToList("Sub-Sub-Task 1.2.1", subParent, 2, subParent.getSubTasks().size());
        addNewTaskToList("Sub-Sub-Task 1.2.2", subParent, 2, subParent.getSubTasks().size());
      }
      addNewTaskToList("Sub-Task 1.3", parentTask, 1, parentTask.getSubTasks().size());
    }
    addNewTaskToList("Another Top-Level Task", null, 0, actualTasks.size());
  }

  private Task addNewTaskToList(
      String description, Task parent, int indentLevel, int displayOrder) {
    Color taskColor = taskItemColors.get(currentColorIndex);
    currentColorIndex = (currentColorIndex + 1) % taskItemColors.size();
    Task newTask = new Task(description, taskColor, parent, indentLevel, displayOrder);
    if (parent == null) {
      actualTasks.add(newTask);
      actualTasks.sort(Comparator.comparingInt(Task::getDisplayOrder));
    } else {
      parent.addSubTask(newTask);
    }
    try {
      databaseManager.insertTask(newTask);
    } catch (RuntimeException e) {
      JOptionPane.showMessageDialog(
          this,
          "Error saving new task: " + e.getMessage(),
          "DB Save Error",
          JOptionPane.ERROR_MESSAGE);
      if (parent == null) actualTasks.remove(newTask);
      else parent.removeSubTask(newTask);
      return null;
    }
    return newTask;
  }

  private void rebuildTaskListUI() {
    taskListPanel.removeAll();
    addTasksToDisplay(actualTasks);
    updateTaskHighlightsInUI();
    taskListPanel.revalidate();
    taskListPanel.repaint();
  }

  private void scrollToBottom() {
    SwingUtilities.invokeLater(
        () -> {
          JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
          if (verticalScrollBar != null) verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        });
  }

  private void addTasksToDisplay(List<Task> tasks) {
    for (Task task : tasks) {
      TaskEntryPanel actualTaskEntry = new TaskEntryPanel(task);
      JPanel wrapperPanel = new JPanel(new BorderLayout());
      wrapperPanel.setBackground(APP_BACKGROUND_COLOR);
      wrapperPanel.setOpaque(true);

      Border initialBorder = new EmptyBorder(0, task.getIndentLevel() * INDENT_SIZE, 0, 0);
      wrapperPanel.setBorder(initialBorder);

      wrapperPanel.add(actualTaskEntry, BorderLayout.CENTER);
      Dimension fixedSize = actualTaskEntry.getPreferredSize();
      wrapperPanel.setPreferredSize(new Dimension(0, fixedSize.height));
      wrapperPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedSize.height));

      taskListPanel.add(wrapperPanel);
      taskListPanel.add(Box.createRigidArea(new Dimension(0, TASK_ITEM_GAP)));
      if (task.hasSubTasks()) {
        addTasksToDisplay(task.getSubTasks());
      }
    }
  }

  public void startTaskDrag(Task task, MouseEvent e) {
    if (SwingUtilities.isLeftMouseButton(e)) {
      if (task.getParentTask() != null) {
        taskBeingDragged = null;
        return;
      }
      taskBeingDragged = task;
      originalDragIndex = actualTasks.indexOf(task);
      if (originalDragIndex == -1) {
        taskBeingDragged = null;
        return;
      }
      scrollPane.getViewport().addMouseMotionListener(viewportDragHandler);
      scrollPane.getViewport().addMouseListener(viewportDragHandler);
    }
  }

  private void handleDragMotion(MouseEvent e) {
    if (taskBeingDragged == null || taskBeingDragged.getParentTask() != null) return;
    Point pInTaskList =
        SwingUtilities.convertPoint(scrollPane.getViewport(), e.getPoint(), taskListPanel);
    int newDropLineIndex = 0;
    int topLevelTasksEncountered = 0;
    for (Component wrapperComp : taskListPanel.getComponents()) {
      if (wrapperComp instanceof JPanel
          && ((JPanel) wrapperComp).getComponentCount() > 0
          && ((JPanel) wrapperComp).getComponent(0) instanceof TaskEntryPanel panel) {
        if (panel.getTask().getParentTask() == null) {
          if (pInTaskList.y < wrapperComp.getY() + wrapperComp.getHeight() / 2) {
            newDropLineIndex = topLevelTasksEncountered;
            break;
          }
          topLevelTasksEncountered++;
          newDropLineIndex = topLevelTasksEncountered;
        }
      }
    }
    if (newDropLineIndex != currentDropLineIndex) {
      currentDropLineIndex = newDropLineIndex;
      taskListPanel.repaint();
    }
  }

  private void handleDragRelease(MouseEvent e) {
    if (taskBeingDragged != null) {
      scrollPane.getViewport().removeMouseMotionListener(viewportDragHandler);
      scrollPane.getViewport().removeMouseListener(viewportDragHandler);
    }
    if (taskBeingDragged == null || originalDragIndex == -1 || currentDropLineIndex == -1) {
      taskBeingDragged = null;
      originalDragIndex = -1;
      currentDropLineIndex = -1;
      taskListPanel.repaint();
      requestFocusInWindow();
      return;
    }

    Task taskToMove = taskBeingDragged;

    // Remove from old location
    if (taskToMove.getParentTask() != null) {
      taskToMove.getParentTask().removeSubTask(taskToMove);
      updateSiblingOrderInDB(taskToMove.getParentTask().getSubTasks());
    } else {
      actualTasks.remove(taskToMove);
      updateSiblingOrderInDB(actualTasks);
    }

    // Determine drop target
    Task newParent = null;
    int rootTaskIndex = 0;
    for (Component wrapperComp : taskListPanel.getComponents()) {
      if (wrapperComp instanceof JPanel
          && ((JPanel) wrapperComp).getComponentCount() > 0
          && ((JPanel) wrapperComp).getComponent(0) instanceof TaskEntryPanel panel) {

        Task candidateTask = panel.getTask();

        if (candidateTask == taskToMove) continue;

        Rectangle bounds = wrapperComp.getBounds();
        Point localPt =
            SwingUtilities.convertPoint(scrollPane.getViewport(), e.getPoint(), taskListPanel);

        if (bounds.contains(localPt)) {
          if (localPt.y < bounds.getCenterY()) {
            // Insert above this task
            if (candidateTask.getParentTask() == null) {
              actualTasks.add(rootTaskIndex, taskToMove);
              taskToMove.setParentTask(null);
              taskToMove.indentLevel = 0;
            } else {
              newParent = candidateTask.getParentTask();
              newParent.getSubTasks().add(taskToMove);
              taskToMove.setParentTask(newParent);
              taskToMove.indentLevel = newParent.indentLevel + 1;
            }
            break;
          } else {
            // Insert below or nest under
            newParent = candidateTask;
            newParent.getSubTasks().add(0, taskToMove);
            taskToMove.setParentTask(newParent);
            taskToMove.indentLevel = newParent.indentLevel + 1;
            break;
          }
        }

        if (candidateTask.getParentTask() == null) {
          rootTaskIndex++;
        }
      }
    }

    // If no insertion point was found, treat as root
    if (taskToMove.getParentTask() == null && !actualTasks.contains(taskToMove)) {
      int insertionPoint = Math.max(0, Math.min(currentDropLineIndex, actualTasks.size()));
      actualTasks.add(insertionPoint, taskToMove);
      taskToMove.indentLevel = 0;
    }

    // Recalculate display order
    updateSiblingOrderInDB(
        taskToMove.getParentTask() == null
            ? actualTasks
            : taskToMove.getParentTask().getSubTasks());

    // Update DB
    try {
      databaseManager.updateTask(taskToMove);
    } catch (RuntimeException ex) {
      JOptionPane.showMessageDialog(
          this,
          "Error updating task in database: " + ex.getMessage(),
          "DB Error",
          JOptionPane.ERROR_MESSAGE);
    }

    rebuildTaskListUI();

    taskBeingDragged = null;
    originalDragIndex = -1;
    currentDropLineIndex = -1;
    requestFocusInWindow();
  }

  private void triggerInlineEdit(Task task) {
    for (Component wrapperComp : taskListPanel.getComponents()) {
      if (wrapperComp instanceof JPanel
          && ((JPanel) wrapperComp).getComponentCount() > 0
          && ((JPanel) wrapperComp).getComponent(0) instanceof TaskEntryPanel panel) {
        if (panel.getTask() == task) {
          panel.switchToEditMode();
          break;
        }
      }
    }
  }

  private void propagateCompletionToSubtasks(Task parent, boolean complete) {
    if (parent == null) return;
    List<Task> tasksToUpdateInDB = new ArrayList<>();
    _recursivePropagateCompletion(parent, complete, tasksToUpdateInDB);
    if (!tasksToUpdateInDB.isEmpty()) {
      for (Task t : tasksToUpdateInDB) {
        try {
          databaseManager.updateTask(t);
        } catch (RuntimeException ex) {
          System.err.println(
              "Failed to update completion for task " + t.getId() + ": " + ex.getMessage());
        }
      }
    }
  }

  private void _recursivePropagateCompletion(
      Task currentTask, boolean newCompleteState, List<Task> tasksToUpdateInDB) {
    if (currentTask.isComplete() != newCompleteState) {
      currentTask.setComplete(newCompleteState);
      tasksToUpdateInDB.add(currentTask);
    }
    if (currentTask.hasSubTasks()) {
      for (Task subTask : currentTask.getSubTasks()) {
        _recursivePropagateCompletion(subTask, newCompleteState, tasksToUpdateInDB);
      }
    }
  }

  private void propagateIncompletionToParents(Task child) {
    Task currentParent = child.getParentTask();
    List<Task> tasksToUpdateInDB = new ArrayList<>();
    while (currentParent != null) {
      if (currentParent.isComplete()) {
        currentParent.setComplete(false);
        tasksToUpdateInDB.add(currentParent);
        currentParent = currentParent.getParentTask();
      } else break;
    }
    if (!tasksToUpdateInDB.isEmpty()) {
      for (Task t : tasksToUpdateInDB) {
        try {
          databaseManager.updateTask(t);
        } catch (RuntimeException ex) {
          System.err.println(
              "Failed to update parent incompletion for task "
                  + t.getId()
                  + ": "
                  + ex.getMessage());
        }
      }
    }
  }

  private void checkAndSetParentCompletion(Task parent) {
    if (parent == null) return;
    boolean originalParentCompletion = parent.isComplete();
    boolean shouldBeComplete = true;
    if (parent.hasSubTasks()) {
      for (Task subTask : parent.getSubTasks()) {
        if (!subTask.isComplete()) {
          shouldBeComplete = false;
          break;
        }
      }
    } else return;
    if (originalParentCompletion != shouldBeComplete) {
      parent.setComplete(shouldBeComplete);
      try {
        databaseManager.updateTask(parent);
      } catch (RuntimeException ex) {
        System.err.println(
            "Failed to update parent completion for task "
                + parent.getId()
                + ": "
                + ex.getMessage());
        parent.setComplete(originalParentCompletion);
        return;
      }
      if (shouldBeComplete) checkAndSetParentCompletion(parent.getParentTask());
      else propagateIncompletionToParents(parent);
    }
  }

  private class CircularDeleteButton extends JButton {
    private static final int DIAMETER = 20;

    public CircularDeleteButton() {
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setPreferredSize(new Dimension(DIAMETER, DIAMETER));
      setMinimumSize(new Dimension(DIAMETER, DIAMETER));
      setMaximumSize(new Dimension(DIAMETER, DIAMETER));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(getModel().isArmed() ? Color.RED.darker() : Color.RED);
      g2.fill(new Ellipse2D.Double(0, 0, getWidth() - 1, getHeight() - 1));
      g2.setColor(Color.WHITE);
      g2.setStroke(new BasicStroke(2));
      int offset = getWidth() / 4;
      g2.drawLine(offset, offset, getWidth() - offset - 1, getHeight() - offset - 1);
      g2.drawLine(offset, getHeight() - offset - 1, getWidth() - offset - 1, offset);
      g2.dispose();
    }
  }

  private class TaskEntryPanel extends JPanel {
    private final Task task;
    private final JLabel textLabel;
    private final JCheckBox checkBox;
    private final JTextField editTextField;
    private boolean isInEditMode = false;
    private final JButton quickDeleteButton;
    private final JLabel subTaskCountLabel;

    public TaskEntryPanel(Task task) {
      this.task = task;
      setLayout(new BorderLayout(10, 0));
      setBackground(task.getBackgroundColor());
      setBorder(new EmptyBorder(10, 15, 10, 15));

      JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      leftPanel.setOpaque(false);
      checkBox = new JCheckBox();
      checkBox.setSelected(task.isComplete());
      checkBox.setOpaque(false);
      checkBox.addActionListener(
          actionEvent -> {
            final boolean isChecked = checkBox.isSelected();
            task.setComplete(isChecked);
            try {
              databaseManager.updateTask(task);
            } catch (RuntimeException dbEx) {
              JOptionPane.showMessageDialog(
                  TaskOrganizer.this,
                  "Error updating task completion: " + dbEx.getMessage(),
                  "DB Error",
                  JOptionPane.ERROR_MESSAGE);
              task.setComplete(!isChecked);
              checkBox.setSelected(!isChecked);
              return;
            }
            SwingUtilities.invokeLater(
                () -> {
                  if (isChecked) {
                    propagateCompletionToSubtasks(task, true);
                    checkAndSetParentCompletion(task.getParentTask());
                  } else {
                    propagateCompletionToSubtasks(task, false);
                    propagateIncompletionToParents(task);
                  }
                  TaskOrganizer.this.rebuildTaskListUI();
                  TaskOrganizer.this.requestFocusInWindow();
                });
          });
      leftPanel.add(checkBox);
      add(leftPanel, BorderLayout.WEST);

      JPanel centerContentPanel = new JPanel(new CardLayout());
      centerContentPanel.setOpaque(false);
      textLabel = new JLabel(task.getDescription());
      textLabel.setFont(new Font("Arial", Font.PLAIN, 16));
      centerContentPanel.add(textLabel, "DISPLAY_LABEL");

      editTextField = new JTextField();
      editTextField.setFont(new Font("Arial", Font.PLAIN, 16));
      editTextField.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
      editTextField.addActionListener(ae -> switchToDisplayMode(true));
      editTextField.addFocusListener(
          new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent fe) {
              if (isInEditMode) switchToDisplayMode(true);
            }
          });
      editTextField.addKeyListener(
          new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent ke) {
              if (ke.getKeyCode() == KeyEvent.VK_ESCAPE) switchToDisplayMode(false);
            }
          });
      centerContentPanel.add(editTextField, "EDIT_FIELD");
      add(centerContentPanel, BorderLayout.CENTER);

      JPanel rightPanel = new JPanel();
      rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
      rightPanel.setOpaque(false);
      rightPanel.add(Box.createVerticalGlue());
      quickDeleteButton = new CircularDeleteButton();
      quickDeleteButton.setToolTipText("Delete task immediately");
      quickDeleteButton.addActionListener(e -> deleteTaskImmediately(task));
      quickDeleteButton.setAlignmentX(Component.CENTER_ALIGNMENT);
      subTaskCountLabel = new JLabel();
      subTaskCountLabel.setFont(new Font("Arial", Font.PLAIN, 9));
      subTaskCountLabel.setForeground(Color.DARK_GRAY);
      subTaskCountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
      rightPanel.add(quickDeleteButton);
      rightPanel.add(Box.createRigidArea(new Dimension(0, 2)));
      rightPanel.add(subTaskCountLabel);
      rightPanel.add(Box.createVerticalGlue());
      add(rightPanel, BorderLayout.EAST);

      updateAppearance();
      int fixedHeight = 60;
      setPreferredSize(new Dimension(0, fixedHeight));
      setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedHeight));

      MouseAdapter panelMouseListener =
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              Component source = (Component) e.getSource();
              if (source == quickDeleteButton
                  || SwingUtilities.isDescendingFrom(source, quickDeleteButton)) return;
              if (e.isPopupTrigger())
                TaskOrganizer.this.createAndShowContextMenu(
                    TaskEntryPanel.this, task, e.getPoint());
              else if (SwingUtilities.isLeftMouseButton(e)
                  && e.getClickCount() == 2
                  && !isInEditMode) switchToEditMode();
            }

            @Override
            public void mousePressed(MouseEvent e) {
              Component source = (Component) e.getSource();
              if (source == quickDeleteButton
                  || SwingUtilities.isDescendingFrom(source, quickDeleteButton)) return;
              if (e.isPopupTrigger())
                TaskOrganizer.this.createAndShowContextMenu(
                    TaskEntryPanel.this, task, e.getPoint());
              else if (SwingUtilities.isLeftMouseButton(e)
                  && e.getClickCount() == 1
                  && !isInEditMode
                  && task.getParentTask() == null) {
                TaskOrganizer.this.startTaskDrag(TaskEntryPanel.this.task, e);
              }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              Component source = (Component) e.getSource();
              if (source == quickDeleteButton
                  || SwingUtilities.isDescendingFrom(source, quickDeleteButton)) return;
              if (e.isPopupTrigger())
                TaskOrganizer.this.createAndShowContextMenu(
                    TaskEntryPanel.this, task, e.getPoint());
            }
          };
      addMouseListener(panelMouseListener);
      centerContentPanel.addMouseListener(panelMouseListener);
      textLabel.addMouseListener(panelMouseListener);
    }

    public Task getTask() {
      return this.task;
    }

    public void switchToEditMode() {
      if (isInEditMode) return;
      isInEditMode = true;
      CardLayout cl = (CardLayout) ((JPanel) this.getComponent(1)).getLayout();
      cl.show((JPanel) this.getComponent(1), "EDIT_FIELD");
      editTextField.setText(task.getDescription());
      editTextField.selectAll();
      SwingUtilities.invokeLater(() -> editTextField.requestFocusInWindow());
      revalidate();
      repaint();
    }

    private void switchToDisplayMode(boolean saveChanges) {
      if (!isInEditMode) return;
      isInEditMode = false;
      String originalText = task.getDescription();
      if (saveChanges) {
        String newText = editTextField.getText().trim();
        if (!newText.isEmpty()) {
          if (!newText.equals(originalText)) {
            task.setDescription(newText);
            try {
              databaseManager.updateTask(task);
            } catch (RuntimeException ex) {
              JOptionPane.showMessageDialog(
                  TaskOrganizer.this,
                  "Error saving task description: " + ex.getMessage(),
                  "DB Save Error",
                  JOptionPane.ERROR_MESSAGE);
              task.setDescription(originalText);
            }
          }
          textLabel.setText(task.getDescription());
        } else {
          task.setDescription(originalText);
          textLabel.setText(originalText);
        }
      } else {
        textLabel.setText(originalText);
      }
      CardLayout cl = (CardLayout) ((JPanel) this.getComponent(1)).getLayout();
      cl.show((JPanel) this.getComponent(1), "DISPLAY_LABEL");
      revalidate();
      repaint();
      TaskOrganizer.this.requestFocusInWindow();
    }

    public void updateAppearance() {
      textLabel.setText(task.getDescription());
      textLabel.setForeground(
          task.isComplete() ? COMPLETED_TASK_TEXT_COLOR : DEFAULT_TASK_TEXT_COLOR);
      checkBox.setSelected(task.isComplete());
      setBackground(task.getBackgroundColor());
      int numSubTasks = task.getSubTasks().size();
      if (numSubTasks > 0) {
        long completedSubTasks = task.getSubTasks().stream().filter(Task::isComplete).count();
        subTaskCountLabel.setText("(" + completedSubTasks + " of " + numSubTasks + ")");
        subTaskCountLabel.setVisible(true);
      } else {
        subTaskCountLabel.setText("");
        subTaskCountLabel.setVisible(false);
      }
    }
  }

  private void createAndShowContextMenu(Component invoker, Task task, Point point) {
    JPopupMenu contextMenu = new JPopupMenu();
    JMenuItem editItem = new JMenuItem("Edit");
    editItem.addActionListener(e -> triggerInlineEdit(task));
    contextMenu.add(editItem);
    JMenuItem addSubTaskItem = new JMenuItem("Add Sub-Task");
    addSubTaskItem.addActionListener(
        e -> {
          String subTaskName = "Sub-Task " + (task.getSubTasks().size() + 1);
          addNewTaskToList(subTaskName, task, task.getIndentLevel() + 1, task.getSubTasks().size());
          rebuildTaskListUI();
          requestFocusInWindow();
        });
    contextMenu.add(addSubTaskItem);
    JMenuItem deleteItem = new JMenuItem("Delete");
    deleteItem.addActionListener(e -> deleteTaskWithPrompt(task));
    contextMenu.add(deleteItem);
    contextMenu.addSeparator();
    JMenuItem moveUpItem = new JMenuItem("Move Up");
    List<Task> siblingList =
        task.getParentTask() == null ? actualTasks : task.getParentTask().getSubTasks();
    int taskIndexInSiblings = siblingList.indexOf(task);
    moveUpItem.setEnabled(taskIndexInSiblings > 0);
    moveUpItem.addActionListener(e -> moveTaskWithButton(task, -1, siblingList));
    contextMenu.add(moveUpItem);
    JMenuItem moveDownItem = new JMenuItem("Move Down");
    moveDownItem.setEnabled(
        taskIndexInSiblings >= 0 && taskIndexInSiblings < siblingList.size() - 1);
    moveDownItem.addActionListener(e -> moveTaskWithButton(task, 1, siblingList));
    contextMenu.add(moveDownItem);
    contextMenu.show(invoker, point.x, point.y);
    requestFocusInWindow();
  }

  private void deleteTaskImmediately(Task task) {
    try {
      databaseManager.deleteTask(task.getId());
    } catch (RuntimeException ex) {
      JOptionPane.showMessageDialog(
          this,
          "Error deleting task from database: " + ex.getMessage(),
          "DB Delete Error",
          JOptionPane.ERROR_MESSAGE);
      return;
    }
    Task parent = task.getParentTask();
    if (parent != null) {
      parent.removeSubTask(task);
      updateSiblingOrderInDB(parent.getSubTasks());
      checkAndSetParentCompletion(parent);
    } else {
      actualTasks.remove(task);
      updateSiblingOrderInDB(actualTasks);
    }
    rebuildTaskListUI();
    requestFocusInWindow();
  }

  private void deleteTaskWithPrompt(Task task) {
    int response =
        JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete this task and all its sub-tasks?\n\""
                + task.getDescription()
                + "\"",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (response == JOptionPane.YES_OPTION) deleteTaskImmediately(task);
    requestFocusInWindow();
  }

  private void moveTaskWithButton(Task task, int direction, List<Task> listToReorderIn) {
    int currentIndex = listToReorderIn.indexOf(task);
    if (currentIndex == -1) return;
    int newIndex = currentIndex + direction;
    if (newIndex >= 0 && newIndex < listToReorderIn.size()) {
      Collections.swap(listToReorderIn, currentIndex, newIndex);
      updateSiblingOrderInDB(listToReorderIn);
      rebuildTaskListUI();
    }
    requestFocusInWindow();
  }

  private void updateSiblingOrderInDB(List<Task> siblings) {
    if (siblings == null || siblings.isEmpty()) return;
    List<Task> tasksToUpdate = new ArrayList<>();
    for (int i = 0; i < siblings.size(); i++) {
      Task sibling = siblings.get(i);
      if (sibling.getDisplayOrder() != i) {
        sibling.setDisplayOrder(i);
        tasksToUpdate.add(sibling);
      }
    }
    if (!tasksToUpdate.isEmpty()) {
      try {
        databaseManager.updateTaskOrder(tasksToUpdate);
      } catch (RuntimeException ex) {
        JOptionPane.showMessageDialog(
            this,
            "Error updating task order: " + ex.getMessage(),
            "DB Error",
            JOptionPane.ERROR_MESSAGE);
        loadTasksFromDB();
        rebuildTaskListUI();
      }
    }
  }

  private void toggleFullScreen() {
    if (isInFullScreenMode) exitFullScreenMode();
    else enterFullScreenMode();
  }

  private void enterFullScreenMode() {
    lastWindowSize = getSize();
    lastWindowLocation = getLocation();
    GraphicsConfiguration currentGraphicsConfig = getGraphicsConfiguration();
    GraphicsDevice deviceToUse = currentGraphicsConfig.getDevice();
    if (!deviceToUse.isFullScreenSupported()) {
      JOptionPane.showMessageDialog(
          this,
          "True full-screen mode is not supported on this display.\nMaximizing window instead.",
          "Full Screen Not Supported",
          JOptionPane.INFORMATION_MESSAGE);
      setExtendedState(JFrame.MAXIMIZED_BOTH);
      this.activeFullScreenDevice = null;
    } else {
      this.activeFullScreenDevice = deviceToUse;
      setVisible(false);
      dispose();
      setUndecorated(true);
      setResizable(false);
      this.activeFullScreenDevice.setFullScreenWindow(this);
    }
    isInFullScreenMode = true;
    requestFocusInWindow();
  }

  private void exitFullScreenMode() {
    boolean wasInTrueFullScreenMode =
        (this.activeFullScreenDevice != null
            && this.activeFullScreenDevice.getFullScreenWindow() == this);
    if (wasInTrueFullScreenMode) this.activeFullScreenDevice.setFullScreenWindow(null);
    if (isUndecorated()) {
      setVisible(false);
      dispose();
      setUndecorated(false);
    }
    setResizable(true);
    if (lastWindowSize != null) setSize(lastWindowSize);
    else setSize(800, 600);
    if (lastWindowLocation != null) setLocation(lastWindowLocation);
    else setLocationRelativeTo(null);
    setExtendedState(JFrame.NORMAL);
    if (!isVisible()) setVisible(true);
    isInFullScreenMode = false;
    this.activeFullScreenDevice = null;
    requestFocusInWindow();
  }

  /// ////////////////////////////////////////////
  /// Database Class
  /// ///////////////////////////////////////////

  public class DatabaseManager {

    private static final String JDBC_URL =
        "jdbc:h2:./tasklist_db;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"; // Store DB in current dir
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public DatabaseManager() {
      try {
        Class.forName("org.h2.Driver");
      } catch (ClassNotFoundException e) {
        System.err.println("H2 Driver not found!");
        e.printStackTrace();
        // Consider a more graceful exit or user notification
      }
    }

    public Connection getConnection() throws SQLException {
      return DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
    }

    public void closeConnection(Connection conn) {
      if (conn != null) {
        try {
          conn.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    }

    public void createTableIfNotExists() {
      String sql =
          "CREATE TABLE IF NOT EXISTS tasks ("
              + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
              + "description VARCHAR(255) NOT NULL,"
              + "is_complete BOOLEAN NOT NULL,"
              + "background_color_rgb VARCHAR(20) NOT NULL,"
              + "parent_id BIGINT,"
              + "indent_level INT NOT NULL,"
              + "display_order INT NOT NULL,"
              + "FOREIGN KEY (parent_id) REFERENCES tasks(id) ON DELETE CASCADE"
              + ")";
      try (Connection conn = getConnection();
          Statement stmt = conn.createStatement()) {
        stmt.execute(sql);
      } catch (SQLException e) {
        e.printStackTrace();
        throw new RuntimeException("Failed to create tasks table", e);
      }
    }

    public TaskOrganizer.Task insertTask(TaskOrganizer.Task task) {
      String sql =
          "INSERT INTO tasks (description, is_complete, background_color_rgb, parent_id, indent_level, display_order) "
              + "VALUES (?, ?, ?, ?, ?, ?)";
      try (Connection conn = getConnection();
          PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        pstmt.setString(1, task.getDescription());
        pstmt.setBoolean(2, task.isComplete());
        pstmt.setString(3, colorToString(task.getBackgroundColor()));
        if (task.getParentTask() != null) {
          pstmt.setLong(4, task.getParentTask().getId());
        } else {
          pstmt.setNull(4, java.sql.Types.BIGINT);
        }
        pstmt.setInt(5, task.getIndentLevel());
        pstmt.setInt(6, task.getDisplayOrder());

        int affectedRows = pstmt.executeUpdate();
        if (affectedRows > 0) {
          try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
              task.setId(generatedKeys.getLong(1));
            } else {
              throw new SQLException("Creating task failed, no ID obtained.");
            }
          }
        }
        return task;
      } catch (SQLException e) {
        e.printStackTrace();
        throw new RuntimeException("Failed to insert task: " + task.getDescription(), e);
      }
    }

    public void updateTask(TaskOrganizer.Task task) {
      String sql =
          "UPDATE tasks SET description = ?, is_complete = ?, background_color_rgb = ?, "
              + "parent_id = ?, indent_level = ?, display_order = ? WHERE id = ?";
      try (Connection conn = getConnection();
          PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, task.getDescription());
        pstmt.setBoolean(2, task.isComplete());
        pstmt.setString(3, colorToString(task.getBackgroundColor()));
        if (task.getParentTask() != null) {
          pstmt.setLong(4, task.getParentTask().getId());
        } else {
          pstmt.setNull(4, java.sql.Types.BIGINT);
        }
        pstmt.setInt(5, task.getIndentLevel());
        pstmt.setInt(6, task.getDisplayOrder());
        pstmt.setLong(7, task.getId());
        pstmt.executeUpdate();
      } catch (SQLException e) {
        e.printStackTrace();
        throw new RuntimeException("Failed to update task: " + task.getDescription(), e);
      }
    }

    public void deleteTask(long taskId) {
      String sql = "DELETE FROM tasks WHERE id = ?";
      try (Connection conn = getConnection();
          PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setLong(1, taskId);
        pstmt.executeUpdate();
      } catch (SQLException e) {
        e.printStackTrace();
        throw new RuntimeException("Failed to delete task with id: " + taskId, e);
      }
    }

    public List<TaskOrganizer.Task> getAllTasks(TaskOrganizer app) {
      Map<Long, Task> taskMap = new HashMap<>();
      List<TaskOrganizer.Task> topLevelTasks = new ArrayList<>();
      String sql =
          "SELECT id, description, is_complete, background_color_rgb, parent_id, indent_level, display_order FROM tasks ORDER BY parent_id ASC, display_order ASC";

      try (Connection conn = getConnection();
          Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql)) {

        List<RawTaskData> rawTasks = new ArrayList<>();
        while (rs.next()) {
          rawTasks.add(
              new RawTaskData(
                  rs.getLong("id"),
                  rs.getString("description"),
                  rs.getBoolean("is_complete"),
                  stringToColor(rs.getString("background_color_rgb")),
                  rs.getLong("parent_id"), // Store parent_id, check for null later
                  rs.wasNull(), // Check if parent_id was SQL NULL
                  rs.getInt("indent_level"),
                  rs.getInt("display_order")));
        }

        // First pass: create all task objects and put them in a map
        for (RawTaskData raw : rawTasks) {
          TaskOrganizer.Task task =
              new Task(
                  raw.id,
                  raw.description,
                  raw.backgroundColor,
                  null,
                  raw.indentLevel,
                  raw.displayOrder);
          task.setComplete(raw.isComplete);
          taskMap.put(raw.id, task);
        }

        // Second pass: link parents and children
        for (RawTaskData raw : rawTasks) {
          TaskOrganizer.Task currentTask = taskMap.get(raw.id);
          if (!raw.isParentIdNull) {
            TaskOrganizer.Task parentTask = taskMap.get(raw.parentId);
            if (parentTask != null) {
              currentTask.setParentTask(parentTask); // Set parent reference
              parentTask.addSubTask(currentTask); // Add to parent's subtask list
            } else {
              System.err.println(
                  "Orphan task found: "
                      + currentTask.getDescription()
                      + " with parent_id: "
                      + raw.parentId);
            }
          } else {
            topLevelTasks.add(currentTask);
          }
        }

        // Ensure subtasks are sorted by display order within their parents
        for (TaskOrganizer.Task task : taskMap.values()) {
          if (task.hasSubTasks()) {
            task.getSubTasks()
                .sort(Comparator.comparingInt(TaskOrganizer.Task::getDisplayOrder));
          }
        }

      } catch (SQLException e) {
        e.printStackTrace();
        throw new RuntimeException("Failed to load tasks from database", e);
      }
      topLevelTasks.sort(Comparator.comparingInt(TaskOrganizer.Task::getDisplayOrder));
      return topLevelTasks;
    }

    // Helper class for loading
    private static class RawTaskData {
      long id;
      String description;
      boolean isComplete;
      Color backgroundColor;
      long parentId;
      boolean isParentIdNull;
      int indentLevel;
      int displayOrder;

      RawTaskData(
          long id,
          String description,
          boolean isComplete,
          Color backgroundColor,
          long parentId,
          boolean isParentIdNull,
          int indentLevel,
          int displayOrder) {
        this.id = id;
        this.description = description;
        this.isComplete = isComplete;
        this.backgroundColor = backgroundColor;
        this.parentId = parentId;
        this.isParentIdNull = isParentIdNull;
        this.indentLevel = indentLevel;
        this.displayOrder = displayOrder;
      }
    }

    public void updateTaskOrder(List<TaskOrganizer.Task> tasksToUpdate) {
      String sql = "UPDATE tasks SET display_order = ?, parent_id = ? WHERE id = ?";
      try (Connection conn = getConnection();
          PreparedStatement pstmt = conn.prepareStatement(sql)) {
        for (TaskOrganizer.Task task : tasksToUpdate) {
          pstmt.setInt(1, task.getDisplayOrder());
          if (task.getParentTask() != null) {
            pstmt.setLong(2, task.getParentTask().getId());
          } else {
            pstmt.setNull(2, java.sql.Types.BIGINT);
          }
          pstmt.setLong(3, task.getId());
          pstmt.addBatch();
        }
        pstmt.executeBatch();
      } catch (SQLException e) {
        e.printStackTrace();
        throw new RuntimeException("Failed to batch update task order", e);
      }
    }

    private String colorToString(Color color) {
      if (color == null) return "0,0,0";
      return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
    }

    private Color stringToColor(String rgbString) {
      if (rgbString == null || rgbString.isEmpty()) return Color.LIGHT_GRAY;
      String[] parts = rgbString.split(",");
      try {
        return new Color(
            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
      } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
        System.err.println("Error parsing color string: " + rgbString + ". Defaulting to gray.");
        return Color.GRAY;
      }
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(
        () -> {
          TaskOrganizer app = new TaskOrganizer();
          app.setVisible(true);
          app.positionAddButton();
          app.requestFocusInWindow();
        });
  }
}
