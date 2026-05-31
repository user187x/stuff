package xxx.com.swing.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class NestedPanelsGUI extends JFrame {

  public enum Indicator {

    EXPANDED(" ▲ ", false), COLLAPSED(" ▼ ", true);

    private String pointer;
    private boolean visible;

    private Indicator(String pointer, boolean visible) {
      this.pointer = pointer;
      this.visible = visible;
    }

    public boolean visible() {
      return visible;
    }

    @Override
    public String toString() {
      return pointer;
    }
  }

  public enum AddLogic {
    CHILD, SIBLING
  }

  private static Map<AddLogic, JPanel> panelMap = new HashMap<>();

  private static final long serialVersionUID = 1L;

  private Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private JPanel contentPanel;
  private JScrollPane scrollPane;

  private Stack<JPanel> panelStack = new Stack<>();

  public NestedPanelsGUI() {

    setTitle("Json Expander");
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    setSize(600, 400);
    setLayout(new BorderLayout());

    contentPanel = new JPanel();
    contentPanel.setName("Base Panel");
    panelStack.push(contentPanel);

    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
    contentPanel.setBackground(Color.LIGHT_GRAY);

    scrollPane = new JScrollPane(contentPanel);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(15);
    // scrollPane.setPreferredSize(new Dimension(50, 30));

    add(scrollPane, BorderLayout.CENTER);

    panelMap.put(AddLogic.SIBLING, contentPanel);

    JButton addChildButton = new JButton("Add Child");
    addChildButton.addActionListener(e -> addPanel(null, AddLogic.CHILD));

    JButton addSiblingButton = new JButton("Add Sibling");
    addSiblingButton.addActionListener(e -> addPanel(null, AddLogic.SIBLING));

    JButton expandAllButton = new JButton("Expand All");
    expandAllButton.addActionListener(e -> {

      expandAll(contentPanel, Indicator.EXPANDED);

      contentPanel.revalidate();
      contentPanel.repaint();

      revalidate();
      repaint();
    });

    JButton collapseAllButton = new JButton("Collapse All");
    collapseAllButton.addActionListener(e -> {

      expandAll(contentPanel, Indicator.COLLAPSED);

      contentPanel.revalidate();
      contentPanel.repaint();

      revalidate();
      repaint();
    });

    JPanel buttonPanel = new JPanel(new FlowLayout());
    buttonPanel.add(addChildButton);
    buttonPanel.add(addSiblingButton);
    buttonPanel.add(expandAllButton);
    buttonPanel.add(collapseAllButton);

    add(buttonPanel, BorderLayout.SOUTH);

    setVisible(true);

    addResizeListener(contentPanel);
  }

  private void expandAll(Component component, Indicator indicator) {

    if (component instanceof JPanel) {

      JPanel jpanel = JPanel.class.cast(component);

      if (jpanel.getBorder() != null) {

        TitledBorder border = TitledBorder.class.cast(jpanel.getBorder());

        String originalTitle = border.getTitle().substring(3);
        border.setTitle(indicator + originalTitle);

        Arrays.asList(jpanel.getComponents()).stream()
            .filter(JTextPane.class::isInstance)
            .map(JTextPane.class::cast)
            .findAny()
            .ifPresent(tp -> tp.setVisible(indicator.visible));

        jpanel.setBorder(border);

        jpanel.revalidate();
        jpanel.repaint();
      }
    }

    if (component instanceof Container) {

      Container container = Container.class.cast(component);

      for (Component childComponent : container.getComponents()) {

        expandAll(childComponent, indicator);
      }
    }
  }

  private void addResizeListener(JPanel mainPanel) {

    if (isVisible() == false)
      setVisible(true);

    mainPanel.addComponentListener(new ComponentAdapter() {

      @Override
      public void componentResized(ComponentEvent e) {

        ListIterator<JPanel> iterator = panelStack.listIterator();

        while (iterator.hasNext()) {

          JPanel childPanel = iterator.next();
          Dimension availableSize = childPanel.getParent().getSize();

          int maxWidth = availableSize.width - 1;
          int maxHeight = availableSize.height - 1;

          childPanel.setPreferredSize(new Dimension(maxWidth, maxHeight));
          childPanel.setMaximumSize(new Dimension(maxWidth, maxHeight));
          childPanel.setSize(new Dimension(maxWidth, maxHeight));

          childPanel.revalidate();
          childPanel.repaint();
        }
      }
    });
  }

  private void addMouseClickListener(JPanel jpanel) {

    jpanel.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseClicked(MouseEvent e) {

        Component clickedComponent = e.getComponent();

        if (clickedComponent instanceof JPanel) {

          JPanel clickedPanel = (JPanel) clickedComponent;
          togglePanel(clickedPanel);
        }
      }
    });
  }

  private void addPanel(String input, AddLogic addLogic) {

    String value = Optional.ofNullable(input).orElse("XXX");

    JPanel newPanel = new JPanel();
    newPanel.setLayout(new BoxLayout(newPanel, BoxLayout.Y_AXIS));
    newPanel.setBorder(BorderFactory.createTitledBorder(Indicator.COLLAPSED + value));
    newPanel.setName(Integer.toString(panelStack.size()));
    newPanel.setBackground(getPanelColor(panelStack.size()));

    JPanel parentPanel = panelMap.getOrDefault(addLogic, panelStack.getLast());

    parentPanel.add(newPanel);

    // Add to the stack
    panelStack.add(newPanel);

    addMouseClickListener(newPanel);

    JTextPane textField = new JTextPane();
    textField.setAlignmentX(Component.LEFT_ALIGNMENT);
    textField.setAlignmentY(Component.TOP_ALIGNMENT);
    textField.setText("{}");
    newPanel.add(textField);

    parentPanel.revalidate();
    parentPanel.repaint();

    // Ensure contentPanel is revalidated and repainted to resize properly
    contentPanel.revalidate();
    contentPanel.repaint();

    revalidateScrollPane();
  }

  private void revalidateScrollPane() {
    SwingUtilities.invokeLater(() -> {

      // 1. Force the content panel to recalculate its preferred size
      // contentPanel.setPreferredSize(contentPanel.getPreferredSize());

      // Dynamically calculate the preferred size of contentPanel
      Dimension contentPreferredSize = contentPanel.getPreferredSize();
      contentPanel.setPreferredSize(
          new Dimension(contentPreferredSize.width, contentPreferredSize.height + 100)); // Increase
                                                                                         // height
                                                                                         // to allow
                                                                                         // scrolling

      // Revalidate the JScrollPane
      scrollPane.revalidate();
      scrollPane.repaint();

      // Scroll to bottom
      JScrollBar vertical = scrollPane.getVerticalScrollBar();
      vertical.setValue(vertical.getMaximum());
    });
  }

  private void togglePanel(JPanel clickedPanel) {

    System.out.println("Clicked a panel : " + clickedPanel.getName());

    JTextPane textPane = Arrays.asList(clickedPanel.getComponents()).stream()
        .filter(component -> component instanceof JTextPane)
        .map(JTextPane.class::cast)
        .findFirst()
        .get();

    TitledBorder border = (TitledBorder) clickedPanel.getBorder();
    String originalTitle = border.getTitle().substring(3);

    if (textPane.isVisible()) {

      textPane.setVisible(false);
      border.setTitle(Indicator.EXPANDED + originalTitle);

      clickedPanel.setBorder(border);
    }
    else {

      textPane.setVisible(true);
      border.setTitle(Indicator.COLLAPSED + originalTitle);

      clickedPanel.setBorder(border);
    }

    clickedPanel.revalidate();
    clickedPanel.repaint();
  }

  private Color getPanelColor(int panelNumber) {

    switch (panelNumber % 4) {
      case 0:
        return new Color(204, 153, 255);
      case 1:
        return Color.LIGHT_GRAY;
      case 2:
        return Color.PINK;
      case 3:
        return Color.GREEN;
      default:
        return Color.YELLOW;
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> new NestedPanelsGUI());
  }
}
