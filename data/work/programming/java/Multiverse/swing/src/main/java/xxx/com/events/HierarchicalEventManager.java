package xxx.com.events;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.Window;

/**
 * A simple, single-class Java GUI application for creating and managing hierarchical 'Events'.
 * This application demonstrates dynamic panels, drag-and-drop functionality, and component-based UI construction.
 */
public class HierarchicalEventManager extends JFrame {

  private final JPanel mainEventsPanel;
  private final JLayeredPane layeredPane;
  private final List<Event> rootEvents = new ArrayList<>(); // Root data model

  // A static reference to the panel being dragged. This avoids serializing the component.
  private static EventPanel draggedPanel;

  /**
   * Represents the data for a single event.
   */
  static class Event {
    String description;
    ImageIcon avatar;
    List<Event> subEvents = new ArrayList<>();

    Event(String description) {
      this.description = description;
      // Default avatar
      this.avatar = new ImageIcon(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
    }
  }

  /**
   * Represents the visual panel for a single Event.
   * It contains the UI for editing, deleting, adding sub-events, and setting an image.
   * It also contains a panel for its own sub-events.
   */
  class EventPanel extends JPanel {
    private final Event eventData;
    private final JTextArea descriptionArea;
    private final JLabel avatarLabel;
    private final JPanel subEventsPanel;
    private final Container parentContainer; // The container this panel is in

    private static final Border NORMAL_BORDER = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
        new EmptyBorder(10, 10, 10, 10)
    );
    private static final Border DRAG_OVER_BORDER = BorderFactory.createCompoundBorder(
        BorderFactory.createDashedBorder(Color.BLUE, 3, 2),
        new EmptyBorder(10, 10, 10, 10)
    );

    EventPanel(Event eventData, Container parentContainer) {
      this.eventData = eventData;
      this.parentContainer = parentContainer;

      setLayout(new BorderLayout(10, 10));
      setBorder(NORMAL_BORDER);
      setBackground(Color.WHITE);
      setFocusable(true);

      // --- Drag and Drop Handler ---
      DragSource ds = DragSource.getDefaultDragSource();
      DragGestureListener dgl = dge -> {
        // Set the static reference to this panel when drag starts
        HierarchicalEventManager.draggedPanel = this;
        dge.startDrag(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR), new EventTransferable());
      };
      ds.createDefaultDragGestureRecognizer(this, DnDConstants.ACTION_MOVE, dgl);
      new DropTarget(this, new EventDropTargetListener(this));


      // --- Avatar Panel (West) ---
      avatarLabel = new JLabel(resizeIcon(eventData.avatar, 64, 64));
      avatarLabel.setPreferredSize(new Dimension(64, 64));
      avatarLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));

      // --- Content Panel (Center) ---
      JPanel contentPanel = new JPanel(new BorderLayout(5, 5));
      contentPanel.setOpaque(false);

      descriptionArea = new JTextArea(eventData.description);
      descriptionArea.setWrapStyleWord(true);
      descriptionArea.setLineWrap(true);
      descriptionArea.addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
          eventData.description = descriptionArea.getText();
        }
      });
      contentPanel.add(new JScrollPane(descriptionArea), BorderLayout.CENTER);

      // --- Button Panel (South of Content) ---
      JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      buttonPanel.setOpaque(false);

      JButton addSubEventButton = new JButton("Add Sub-Event");
      addSubEventButton.addActionListener(e -> addSubEvent());

      JButton setPictureButton = new JButton("Set Picture");
      setPictureButton.addActionListener(e -> setPicture());

      JButton deleteButton = new JButton("Delete");
      deleteButton.addActionListener(e -> deleteEvent());

      buttonPanel.add(addSubEventButton);
      buttonPanel.add(setPictureButton);
      buttonPanel.add(deleteButton);
      contentPanel.add(buttonPanel, BorderLayout.SOUTH);

      // --- Sub-Events Panel (South of Main Panel) ---
      subEventsPanel = new JPanel();
      subEventsPanel.setLayout(new BoxLayout(subEventsPanel, BoxLayout.Y_AXIS));
      subEventsPanel.setOpaque(false);
      subEventsPanel.setBorder(new EmptyBorder(10, 20, 0, 0)); // Indent sub-events

      // Re-populate sub-events from data model
      for (Event subEvent : eventData.subEvents) {
        subEventsPanel.add(new EventPanel(subEvent, subEventsPanel));
      }

      add(avatarLabel, BorderLayout.WEST);
      add(contentPanel, BorderLayout.CENTER);
      add(subEventsPanel, BorderLayout.SOUTH);

      // Mouse listener for hover effect
      addMouseListener(new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent e) {
          setBackground(new Color(240, 245, 255));
        }
        @Override
        public void mouseExited(MouseEvent e) {
          setBackground(Color.WHITE);
        }
      });
    }

    private void addSubEvent() {
      Event newSubEvent = new Event("New Sub-Event");
      eventData.subEvents.add(newSubEvent);
      EventPanel newSubEventPanel = new EventPanel(newSubEvent, this.subEventsPanel);
      subEventsPanel.add(newSubEventPanel);
      refreshLayout(this);
    }

    private void setPicture() {
      JFileChooser fileChooser = new JFileChooser();
      int result = fileChooser.showOpenDialog(this);
      if (result == JFileChooser.APPROVE_OPTION) {
        File selectedFile = fileChooser.getSelectedFile();
        try {
          Image img = ImageIO.read(selectedFile);
          eventData.avatar = new ImageIcon(img);
          avatarLabel.setIcon(resizeIcon(eventData.avatar, 64, 64));
          refreshLayout(this);
        } catch (IOException ex) {
          JOptionPane.showMessageDialog(this, "Could not load image.", "Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }

    private void deleteEvent() {
      int confirm = JOptionPane.showConfirmDialog(this,
          "Are you sure you want to delete this event and all its sub-events?",
          "Confirm Deletion", JOptionPane.YES_NO_OPTION);

      if (confirm == JOptionPane.YES_OPTION) {
        // Find the parent EventPanel to remove data from its model
        EventPanel parentEventPanel = findParentEventPanel(this);
        if (parentEventPanel != null) {
          parentEventPanel.eventData.subEvents.remove(this.eventData);
        } else {
          // This is a top-level event, remove from the root list
          rootEvents.remove(this.eventData);
        }

        parentContainer.remove(this);
        refreshLayout(parentContainer);
      }
    }

    public Container getParentContainer() {
      return parentContainer;
    }

    public JPanel getSubEventsPanel() {
      return subEventsPanel;
    }

    public Event getEventData() {
      return eventData;
    }

    private void refreshLayout(Component component) {
      component.revalidate();
      component.repaint();
      Window topLevelWindow = SwingUtilities.getWindowAncestor(component);
      if (topLevelWindow != null) {
        topLevelWindow.revalidate();
        topLevelWindow.repaint();
      }
    }

    private ImageIcon resizeIcon(ImageIcon icon, int width, int height) {
      if (icon == null || icon.getImage() == null) return null;
      Image img = icon.getImage();
      Image resizedImage = img.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH);
      return new ImageIcon(resizedImage);
    }
  }

  /**
   * Handles the data transfer for drag-and-drop operations.
   * This now acts as a simple token, not a container for the component.
   */
  public static class EventTransferable implements Transferable {
    public static final DataFlavor EVENT_FLAVOR = new DataFlavor(Object.class, "application/x-java-eventpanel");
    private static final DataFlavor[] FLAVORS = {EVENT_FLAVOR};

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      return FLAVORS;
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return flavor.equals(EVENT_FLAVOR);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
      if (isDataFlavorSupported(flavor)) {
        return draggedPanel; // Return the statically referenced panel
      } else {
        throw new UnsupportedFlavorException(flavor);
      }
    }
  }

  /**
   * Finds the containing EventPanel for a given component.
   */
  private EventPanel findParentEventPanel(Component c) {
    Container parent = c.getParent();
    while(parent != null) {
      if(parent instanceof EventPanel) {
        return (EventPanel) parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  /**
   * Listens for drop events and handles the logic of re-parenting or reordering EventPanels.
   */
  class EventDropTargetListener extends DropTargetAdapter {
    private final EventPanel targetPanel;

    public EventDropTargetListener(EventPanel targetPanel) {
      this.targetPanel = targetPanel;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
      if (dtde.isDataFlavorSupported(EventTransferable.EVENT_FLAVOR)) {
        targetPanel.setBorder(EventPanel.DRAG_OVER_BORDER);
      }
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
      targetPanel.setBorder(EventPanel.NORMAL_BORDER);
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
      try {
        if (draggedPanel == null) return; // If no panel is being dragged, do nothing.

        // Prevent dropping a panel onto itself or one of its own children
        if (draggedPanel == targetPanel || targetPanel.isAncestorOf(draggedPanel)) {
          dtde.rejectDrop();
          return;
        }

        dtde.acceptDrop(DnDConstants.ACTION_MOVE);

        // --- Data Model Update ---
        // 1. Remove from old parent's data model
        EventPanel oldParentPanel = findParentEventPanel(draggedPanel);
        if (oldParentPanel != null) {
          oldParentPanel.getEventData().subEvents.remove(draggedPanel.getEventData());
        } else {
          rootEvents.remove(draggedPanel.getEventData()); // Was a root event
        }

        // 2. Add to new parent's data model
        targetPanel.getEventData().subEvents.add(draggedPanel.getEventData());

        // --- UI Update ---
        // 1. Remove from old UI container
        Container oldContainer = draggedPanel.getParentContainer();
        oldContainer.remove(draggedPanel);

        // 2. Add to new UI container
        JPanel newParentContainer = targetPanel.getSubEventsPanel();
        newParentContainer.add(draggedPanel);

        // Refresh both old and new containers
        refreshComponent(oldContainer);
        refreshComponent(targetPanel);

        dtde.dropComplete(true);
      } catch (Exception e) {
        e.printStackTrace();
        dtde.rejectDrop();
      } finally {
        targetPanel.setBorder(EventPanel.NORMAL_BORDER);
        draggedPanel = null; // Clean up the static reference
      }
    }
  }


  public HierarchicalEventManager() {
    super("Hierarchical Event Manager");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(600, 800);
    setLocationRelativeTo(null);

    // --- Main Panel for Events ---
    mainEventsPanel = new JPanel();
    mainEventsPanel.setLayout(new BoxLayout(mainEventsPanel, BoxLayout.Y_AXIS));
    mainEventsPanel.setBackground(Color.WHITE);
    mainEventsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
    // Make the main panel a drop target for creating top-level events
    new DropTarget(mainEventsPanel, new DropTargetAdapter() {
      @Override
      public void drop(DropTargetDropEvent dtde) {
        try {
          if (draggedPanel == null) return;

          // Don't do anything if it's already a top-level event
          if (draggedPanel.getParentContainer() == mainEventsPanel) {
            dtde.rejectDrop();
            return;
          }

          dtde.acceptDrop(DnDConstants.ACTION_MOVE);

          // Data model update
          EventPanel oldParent = findParentEventPanel(draggedPanel);
          if(oldParent != null) {
            oldParent.getEventData().subEvents.remove(draggedPanel.getEventData());
          }
          rootEvents.add(draggedPanel.getEventData());

          // UI Update
          Container oldContainer = draggedPanel.getParentContainer();
          oldContainer.remove(draggedPanel);
          mainEventsPanel.add(draggedPanel);

          refreshComponent(oldContainer);
          refreshComponent(mainEventsPanel);

          dtde.dropComplete(true);
        } catch (Exception e) {
          e.printStackTrace();
          dtde.rejectDrop();
        } finally {
          draggedPanel = null; // Clean up the static reference
        }
      }
    });


    // --- Scroll Pane ---
    JScrollPane scrollPane = new JScrollPane(mainEventsPanel);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(null);
    scrollPane.getVerticalScrollBar().setUnitIncrement(16);


    // --- Floating Add Button ---
    JButton addEventButton = new JButton("+");
    addEventButton.setFont(new Font("Arial", Font.BOLD, 24));
    addEventButton.setFocusPainted(false);
    addEventButton.setBackground(new Color(30, 144, 255));
    addEventButton.setForeground(Color.WHITE);
    addEventButton.addActionListener(e -> addNewEvent());
    addEventButton.setMargin(new Insets(0,0,0,0));
    addEventButton.setBorder(BorderFactory.createEmptyBorder());

    // --- Layered Pane to hold scroll pane and button ---
    layeredPane = new JLayeredPane();
    layeredPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        scrollPane.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        int buttonSize = 60;
        int margin = 20;
        addEventButton.setBounds(layeredPane.getWidth() - buttonSize - margin,
            layeredPane.getHeight() - buttonSize - margin,
            buttonSize, buttonSize);
      }
    });

    layeredPane.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
    layeredPane.add(addEventButton, JLayeredPane.PALETTE_LAYER);

    setContentPane(layeredPane);

    // Add some initial data
    addNewEvent();
  }

  private void refreshComponent(Component c) {
    if (c == null) return;
    c.revalidate();
    c.repaint();
    Window topLevelWindow = SwingUtilities.getWindowAncestor(c);
    if (topLevelWindow != null) {
      topLevelWindow.revalidate();
      topLevelWindow.repaint();
    }
  }

  private void addNewEvent() {
    Event newEvent = new Event("New Top-Level Event");
    rootEvents.add(newEvent); // Add to the root data model
    EventPanel newEventPanel = new EventPanel(newEvent, mainEventsPanel);
    mainEventsPanel.add(newEventPanel);
    refreshComponent(mainEventsPanel);
  }

  public static void main(String[] args) {
    // Run the GUI on the Event Dispatch Thread (EDT)
    SwingUtilities.invokeLater(() -> {
      try {
        // Use a more modern look and feel
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception e) {
        e.printStackTrace();
      }
      new HierarchicalEventManager().setVisible(true);
    });
  }
}
