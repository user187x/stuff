package xxx.com.web.poster;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.javascript.jscomp.*;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriter;
import com.helger.css.writer.CSSWriterSettings;
import okhttp3.Cookie;
import okhttp3.Headers;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.GanttRenderer;
import org.jfree.data.gantt.Task;
import org.jfree.data.gantt.TaskSeries;
import org.jfree.data.gantt.TaskSeriesCollection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * A panel to display the HTTP response, including status, body, headers, cookies, and a timeline.
 * Now handles large responses, file-backed resources, and pagination.
 */
public class ResponsePanel extends JPanel {

    private final RequestPanel requestPanel;
    private final RequestHandler requestHandler;
    private JLabel statusLabel;
    private JPanel currentImagePanel;
    private RSyntaxTextArea bodyTextArea;
    private JTable headersTable;
    private DefaultTableModel headersTableModel;
    private JTextArea redirectsTextArea;
    private JTable cookiesTable;
    private DefaultTableModel cookiesTableModel;
    private JTabbedPane timelineTabbedPane;
    private JTree resourceTree;
    private DefaultTreeModel treeModel;
    private Map<DefaultMutableTreeNode, Resource> resourceMap;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean isDarkTheme = true;
    private Icon activityIcon;

    private JPanel paginationPanel;
    private JButton nextButton, prevButton, firstButton, lastButton;
    private Map<String, String> paginationLinks;

    public ResponsePanel(RequestPanel requestPanel, RequestHandler requestHandler) {
        this.requestPanel = requestPanel;
        this.requestHandler = requestHandler;
        resourceMap = new HashMap<>();
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        activityIcon = new ImageIcon(Objects.requireNonNull(getClass().getResource("/gif/progress-bar.gif")));

        // --- Status Panel (Top) ---
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Status: Idle");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusPanel.add(statusLabel, BorderLayout.EAST);
        add(statusPanel, BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- Body Panel is now a JSplitPane with a Resource Tree ---
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Resources");
        treeModel = new DefaultTreeModel(rootNode);
        resourceTree = new JTree(treeModel);

        resourceTree.setCellRenderer(new DefaultTreeCellRenderer() {
            // Cache scaled icons to avoid repeated loading and scaling
            private final ImageIcon scriptsIcon = loadAndScaleIcon("scripts.png");
            private final ImageIcon imagesIcon = loadAndScaleIcon("images.png");
            private final ImageIcon styleSheetsIcon = loadAndScaleIcon("styleSheets.png");
            private final ImageIcon htmlIcon = loadAndScaleIcon("html.png");
            private final ImageIcon otherDocsIcon = loadAndScaleIcon("otherDocs.png");

            private ImageIcon loadAndScaleIcon(String filename) {
                try {
                    // Load from resources directory in classpath
                    java.net.URL iconURL = getClass().getResource("/png/" + filename);
                    if (iconURL == null) {
                        // Fallback: try alternate path without /resources prefix
                        iconURL = getClass().getResource("/" + filename);
                    }

                    if (iconURL != null) {
                        ImageIcon originalIcon = new ImageIcon(iconURL);
                        // Scale the image to 25x25 pixels
                        Image scaledImage = originalIcon.getImage().getScaledInstance(
                                25, 25, Image.SCALE_SMOOTH);
                        return new ImageIcon(scaledImage);
                    }
                } catch (Exception e) {
                    System.err.println("Could not load icon: " + filename);
                }
                return null;
            }

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                          boolean sel, boolean expanded,
                                                          boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

                if (value instanceof DefaultMutableTreeNode node) {
                    Object userObject = node.getUserObject();

                    if (userObject instanceof Resource resource) {
                        setText(resource.toString());

                        // Set icon based on content type
                        String contentType = resource.getContentType();
                        if (contentType != null) {
                            contentType = contentType.toLowerCase();
                            if (contentType.contains("html")) {
                                setIcon(htmlIcon);
                            } else if (contentType.contains("javascript")) {
                                setIcon(scriptsIcon);
                            } else if (contentType.contains("css")) {
                                setIcon(styleSheetsIcon);
                            } else if (contentType.startsWith("image/")) {
                                setIcon(imagesIcon);
                            } else {
                                setIcon(otherDocsIcon);
                            }
                        } else {
                            setIcon(otherDocsIcon);
                        }
                    } else if (userObject instanceof String label) {
                        // Set folder icons for category nodes
                        switch (label) {
                            case "Resources" -> {
                                // Root node - scale the default folder icon
                                Icon defaultIcon = UIManager.getIcon("Tree.closedIcon");
                                if (defaultIcon instanceof ImageIcon) {
                                    Image img = ((ImageIcon) defaultIcon).getImage();
                                    Image scaledImg = img.getScaledInstance(25, 25, Image.SCALE_SMOOTH);
                                    setIcon(new ImageIcon(scaledImg));
                                }
                                else {
                                    setIcon(defaultIcon);
                                }
                            }
                            case "Scripts" -> setIcon(scriptsIcon);
                            case "Stylesheets" -> setIcon(styleSheetsIcon);
                            case "Images" -> setIcon(imagesIcon);
                            case "Other" -> setIcon(otherDocsIcon);
                        }
                    }
                }

                // Set consistent row height for better alignment
                setPreferredSize(new Dimension(getPreferredSize().width, 30));

                return this;
            }
        });

        // ADD THE CONFIGURATION CODE HERE - Right after setting the renderer:
        resourceTree.setRowHeight(32);  // Set row height to accommodate 25x25 icons with padding
        resourceTree.setShowsRootHandles(true);  // Show expand/collapse handles
        resourceTree.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));  // Add padding around tree

        // Optional: Set custom UI properties for better appearance
        resourceTree.putClientProperty("JTree.lineStyle", "Horizontal");  // Show horizontal lines between nodes

        // If you want to adjust the indentation between tree levels:
        // Note: You'll need to import javax.swing.plaf.basic.BasicTreeUI
        BasicTreeUI treeUI = (BasicTreeUI) resourceTree.getUI();
        if (treeUI != null) {
            treeUI.setLeftChildIndent(5);  // Adjust left indent
            treeUI.setRightChildIndent(5);  // Adjust right indent
        }

        resourceTree.addTreeSelectionListener(e -> onResourceSelected());

        // --- Context Menu for Resource Tree ---
        JPopupMenu resourceTreeMenu = new JPopupMenu();
        JMenuItem saveMenuItem = new JMenuItem("Save");
        saveMenuItem.addActionListener(e -> saveSelectedResource());
        resourceTreeMenu.add(saveMenuItem);

        resourceTree.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = resourceTree.getRowForLocation(e.getX(), e.getY());
                    if (row != -1) {
                        resourceTree.setSelectionRow(row);
                        resourceTreeMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });


        JScrollPane treeScrollPane = new JScrollPane(resourceTree);
        treeScrollPane.setMinimumSize(new Dimension(200, 100)); // Ensure tree is visible

        bodyTextArea = new RSyntaxTextArea();
        bodyTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        bodyTextArea.setCodeFoldingEnabled(true);
        bodyTextArea.setEditable(false);
        bodyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        applyDraculaTheme(bodyTextArea);
        RTextScrollPane bodyScrollPane = new RTextScrollPane(bodyTextArea);

        JSplitPane bodySplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScrollPane, bodyScrollPane);
        bodySplitPane.setDividerLocation(250);

        // --- Theme Toggle ---
        JToggleButton themeToggle = new JToggleButton("Light Theme");
        themeToggle.setSelected(false); // Start in dark mode
        themeToggle.addActionListener(e -> {
            isDarkTheme = !themeToggle.isSelected();
            updateTheme();
        });
        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        togglePanel.add(themeToggle);

        JPanel bodyContentPanel = new JPanel(new BorderLayout());
        bodyContentPanel.add(togglePanel, BorderLayout.NORTH);
        bodyContentPanel.add(bodySplitPane, BorderLayout.CENTER);


        tabbedPane.addTab("Body", bodyContentPanel);
        // --- End of Body Panel change ---

        // --- Headers Panel ---
        headersTableModel = new DefaultTableModel(new Object[]{"Name", "Value"}, 0);
        headersTable = new JTable(headersTableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? getBackground() : UIManager.getColor("Table.alternateRowColor"));
                }
                return c;
            }
        };
        headersTable.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JPopupMenu headersMenu = createTablePopupMenu(headersTable);
        headersTable.setComponentPopupMenu(headersMenu);
        tabbedPane.addTab("Headers", new JScrollPane(headersTable));


        redirectsTextArea = new JTextArea();
        redirectsTextArea.setEditable(false);
        redirectsTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabbedPane.addTab("Redirects", new JScrollPane(redirectsTextArea));

        // --- Cookies Panel ---
        cookiesTableModel = new DefaultTableModel(new Object[]{"Name", "Value"}, 0);
        cookiesTable = new JTable(cookiesTableModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? getBackground() : UIManager.getColor("Table.alternateRowColor"));
                }
                return c;
            }
        };
        cookiesTable.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JPopupMenu cookiesMenu = createTablePopupMenu(cookiesTable);
        JMenuItem decodeMenuItem = new JMenuItem("Base64 Decode");
        cookiesMenu.add(decodeMenuItem);

        decodeMenuItem.addActionListener(e -> {
            int selectedRow = cookiesTable.getSelectedRow();
            if (selectedRow != -1) {
                String value = (String) cookiesTableModel.getValueAt(selectedRow, 1);
                try {
                    byte[] decodedBytes = Base64.getDecoder().decode(value);
                    String decodedValue = new String(decodedBytes, StandardCharsets.UTF_8);
                    cookiesTableModel.setValueAt(decodedValue, selectedRow, 1);
                } catch (IllegalArgumentException ex) {
                    JOptionPane.showMessageDialog(this, "Unable to Base64 decode", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        cookiesTable.setComponentPopupMenu(cookiesMenu);
        tabbedPane.addTab("Cookies", new JScrollPane(cookiesTable));

        timelineTabbedPane = new JTabbedPane();
        timelineTabbedPane.addTab("Request Phases", new JLabel("Timeline will be displayed here after a request is made.", SwingConstants.CENTER));
        timelineTabbedPane.addTab("Resource Waterfall", new JLabel("Resource waterfall will be displayed here after a request is made.", SwingConstants.CENTER));

        tabbedPane.addTab("Timeline", timelineTabbedPane);

        add(tabbedPane, BorderLayout.CENTER);

        // --- Pagination Panel ---
        paginationPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        firstButton = new JButton("<< First");
        prevButton = new JButton("< Prev");
        nextButton = new JButton("Next >");
        lastButton = new JButton("Last >>");

        firstButton.addActionListener(e -> navigate("first"));
        prevButton.addActionListener(e -> navigate("prev"));
        nextButton.addActionListener(e -> navigate("next"));
        lastButton.addActionListener(e -> navigate("last"));

        paginationPanel.add(firstButton);
        paginationPanel.add(prevButton);
        paginationPanel.add(nextButton);
        paginationPanel.add(lastButton);
        paginationPanel.setVisible(false);
        add(paginationPanel, BorderLayout.SOUTH);
    }

    private void navigate(String rel) {
        if (paginationLinks != null && paginationLinks.containsKey(rel)) {
            String url = paginationLinks.get(rel);
            requestPanel.setUrl(url);
            requestHandler.executeRequest();
        }
    }

    private void updateTheme() {
        try {
            if (isDarkTheme) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
                applyDraculaTheme(bodyTextArea);
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
                applyLightTheme(bodyTextArea);
            }
            // Update the entire application's UI
            SwingUtilities.updateComponentTreeUI(SwingUtilities.getWindowAncestor(this));
        } catch (UnsupportedLookAndFeelException ex) {
            System.err.println("Failed to set LookAndFeel.");
        }
    }

    private void applyDraculaTheme(RSyntaxTextArea textArea) {
        textArea.setBackground(new Color(0x282a36));
        textArea.setForeground(new Color(0xf8f8f2));
        // Disable highlighting by making selection transparent
        textArea.setSelectionColor(new Color(0, 0, 0, 0));
        textArea.setCurrentLineHighlightColor(new Color(0x44475a)); // Keep this for subtle feedback
        textArea.setUseSelectedTextColor(true);
        textArea.setSelectedTextColor(textArea.getForeground());

        SyntaxScheme scheme = textArea.getSyntaxScheme();
        scheme.getStyle(Token.RESERVED_WORD).foreground = new Color(0xff79c6);
        scheme.getStyle(Token.RESERVED_WORD_2).foreground = new Color(0xff79c6);
        scheme.getStyle(Token.DATA_TYPE).foreground = new Color(0x8be9fd);
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = new Color(0xf1fa8c);
        scheme.getStyle(Token.LITERAL_CHAR).foreground = new Color(0xf1fa8c);
        scheme.getStyle(Token.COMMENT_EOL).foreground = new Color(0x6272a4);
        scheme.getStyle(Token.COMMENT_MULTILINE).foreground = new Color(0x6272a4);
        scheme.getStyle(Token.PREPROCESSOR).foreground = new Color(0x50fa7b);
        scheme.getStyle(Token.VARIABLE).foreground = new Color(0x50fa7b);
        scheme.getStyle(Token.FUNCTION).foreground = new Color(0x50fa7b);
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = new Color(0xbd93f9);
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = new Color(0xbd93f9);
        scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL).foreground = new Color(0xbd93f9);
        scheme.getStyle(Token.OPERATOR).foreground = new Color(0xff79c6);
        scheme.getStyle(Token.SEPARATOR).foreground = new Color(0xf8f8f2);
        scheme.getStyle(Token.WHITESPACE).foreground = new Color(0xf8f8f2);
        scheme.getStyle(Token.IDENTIFIER).foreground = new Color(0xf8f8f2);
        scheme.getStyle(Token.MARKUP_TAG_NAME).foreground = new Color(0xff79c6);
        scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE).foreground = new Color(0x50fa7b);
        scheme.getStyle(Token.MARKUP_TAG_ATTRIBUTE_VALUE).foreground = new Color(0xf1fa8c);
        scheme.getStyle(Token.MARKUP_COMMENT).foreground = new Color(0x6272a4);

        textArea.revalidate();
        textArea.repaint();
    }

    private void applyLightTheme(RSyntaxTextArea textArea) {
        textArea.setBackground(new Color(0xFFFFFF));
        textArea.setForeground(new Color(0x000000));
        // Disable highlighting by making selection transparent
        textArea.setSelectionColor(new Color(0, 0, 0, 0));
        textArea.setCurrentLineHighlightColor(new Color(0xE8E8E8));
        textArea.setUseSelectedTextColor(true);
        textArea.setSelectedTextColor(textArea.getForeground());

        SyntaxScheme scheme = new SyntaxScheme(true);
        textArea.setSyntaxScheme(scheme); // Reset to default light theme scheme

        textArea.revalidate();
        textArea.repaint();
    }


    private void saveSelectedResource() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) resourceTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;

        Resource resource = resourceMap.get(selectedNode);
        if (resource != null) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(resource.toString()));
            int result = fileChooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    if (resource.isFileBacked()) {
                        Files.copy(resource.getBodyFile().toPath(), selectedFile.toPath());
                    } else {
                        try (FileOutputStream fos = new FileOutputStream(selectedFile)) {
                            fos.write(resource.getBody());
                        }
                    }
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private JPopupMenu createTablePopupMenu(JTable table) {
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyMenuItem = new JMenuItem("Copy");
        copyMenuItem.addActionListener(e -> {
            int selectedRow = table.getSelectedRow();
            int selectedColumn = table.getSelectedColumn();
            if (selectedRow != -1 && selectedColumn != -1) {
                Object value = table.getValueAt(selectedRow, selectedColumn);
                if (value != null) {
                    StringSelection stringSelection = new StringSelection(value.toString());
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
                }
            }
        });
        popupMenu.add(copyMenuItem);
        return popupMenu;
    }

    private void onResourceSelected() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) resourceTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;

        Resource resource = resourceMap.get(selectedNode);
        if (resource != null) {
            displayResource(resource);
        }
    }

    private void displayResource(Resource resource) {
        if (resource.isFileBacked()) {
            displayFileBackedResource(resource);
            return;
        }
        String contentType = resource.getContentType() != null ? resource.getContentType().toLowerCase() : "";

        if (contentType.startsWith("image/")) {
            displayImage(resource);
        } else {
            byte[] raw = resource.getBody();
            String bodyString = new String(raw, StandardCharsets.UTF_8);

            if (contentType.contains("html")) {
                setBody(bodyString, contentType);
            } else if (contentType.contains("javascript")) {
                setBody(prettyJavaScript(bodyString), contentType);
            } else if (contentType.contains("css")) {
                CascadingStyleSheet css = CSSReader.readFromString(bodyString);
                if (css == null) {
                    setBody(bodyString, contentType);
                } else {
                    CSSWriterSettings settings = new CSSWriterSettings();
                    settings.setOptimizedOutput(false);
                    CSSWriter writer = new CSSWriter(settings);
                    String prettyCss = writer.getCSSAsString(css);
                    setBody(prettyCss, contentType);
                }
            } else {
                setBody(bodyString, contentType);
            }
        }
    }

    private void displayFileBackedResource(Resource resource) {
        restoreTextArea(); // Ensure text area is visible

        JPanel largeFilePanel = new JPanel(new BorderLayout(5, 5));
        largeFilePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String size = formatFileSize(resource.getBodyFile().length());
        JLabel infoLabel = new JLabel(
                String.format("<html><b>Large Response:</b> %s. The content is too large to display directly and has been saved to a temporary file.</html>", size)
        );
        largeFilePanel.add(infoLabel, BorderLayout.NORTH);

        JTextArea previewArea = new JTextArea();
        previewArea.setEditable(false);
        previewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        previewArea.setText("Loading preview...");

        // Load preview in a background thread
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                StringBuilder preview = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(resource.getBodyFile()))) {
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null && lineCount < 100) {
                        preview.append(line).append("\n");
                        lineCount++;
                    }
                }
                return preview.toString();
            }

            @Override
            protected void done() {
                try {
                    previewArea.setText(get());
                    previewArea.setCaretPosition(0);
                } catch (Exception e) {
                    previewArea.setText("Error loading preview: " + e.getMessage());
                }
            }
        };
        worker.execute();

        largeFilePanel.add(new JScrollPane(previewArea), BorderLayout.CENTER);

        JButton saveButton = new JButton("Save As...");
        saveButton.addActionListener(e -> saveSelectedResource());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveButton);
        largeFilePanel.add(buttonPanel, BorderLayout.SOUTH);

        // Replace the text area scroll pane with this new panel
        Container parent = bodyTextArea.getParent();
        while (parent != null && !(parent instanceof RTextScrollPane)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            Container splitPane = parent.getParent();
            if (splitPane instanceof JSplitPane) {
                ((JSplitPane) splitPane).setRightComponent(largeFilePanel);
            }
        }
    }


    private String prettyJavaScript(String body) {

        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();

        options.setPrettyPrint(true);
        CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);

        try {

            SourceFile input = SourceFile.fromCode("input.js", body);
            Result result = compiler.compile(Collections.<SourceFile>emptyList(), List.of(input), options);

            if (result.success) {
                return compiler.toSource();
            }

        } catch (Exception ignored) {
        }

        return body;
    }

    private void displayImage(Resource resource) {
        try {
            // Create ImageIcon from byte array
            ImageIcon originalIcon = new ImageIcon(resource.getBody());
            Image originalImage = originalIcon.getImage();

            // Get original dimensions
            int originalWidth = originalIcon.getIconWidth();
            int originalHeight = originalIcon.getIconHeight();

            // If the image failed to load
            if (originalWidth <= 0 || originalHeight <= 0) {
                bodyTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
                bodyTextArea.setText("[Failed to load image: " + resource.getContentType() + "]");
                bodyTextArea.setCaretPosition(0);
                return;
            }

            // Calculate scaled dimensions (max 800x600 for display)
            int maxWidth = 800;
            int maxHeight = 600;

            double widthRatio = (double) maxWidth / originalWidth;
            double heightRatio = (double) maxHeight / originalHeight;
            double scalingFactor = Math.min(Math.min(widthRatio, heightRatio), 1.0); // Don't upscale

            int scaledWidth = (int) (originalWidth * scalingFactor);
            int scaledHeight = (int) (originalHeight * scalingFactor);

            // Scale the image
            Image scaledImage = originalImage.getScaledInstance(
                    scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            ImageIcon scaledIcon = new ImageIcon(scaledImage);

            // Create a panel to hold the image
            JPanel imagePanel = new JPanel(new BorderLayout());
            imagePanel.setBackground(Color.WHITE);

            // Create image label with the scaled image
            JLabel imageLabel = new JLabel(scaledIcon);
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // Create info panel
            JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            infoPanel.setBackground(new Color(245, 245, 245));
            infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            // Add image information
            String filename = resource.toString();
            String sizeInfo = String.format("Original: %dx%d px", originalWidth, originalHeight);
            if (scalingFactor < 1.0) {
                sizeInfo += String.format(" | Displayed: %dx%d px (%.0f%%)",
                        scaledWidth, scaledHeight, scalingFactor * 100);
            }
            String fileSize = formatFileSize(resource.getBody().length);

            JLabel infoLabel = new JLabel(String.format(
                    "<html><b>%s</b> - %s - %s - %s</html>",
                    filename,
                    resource.getContentType(),
                    sizeInfo,
                    fileSize
            ));
            infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            infoPanel.add(infoLabel);

            // Add components to image panel
            imagePanel.add(infoPanel, BorderLayout.NORTH);
            imagePanel.add(new JScrollPane(imageLabel), BorderLayout.CENTER);

            // Replace the text area with the image panel
            // Get the parent split pane
            Container parent = bodyTextArea.getParent();
            while (parent != null && !(parent instanceof RTextScrollPane)) {
                parent = parent.getParent();
            }

            if (parent != null) {
                Container splitPane = parent.getParent();
                if (splitPane instanceof JSplitPane) {
                    ((JSplitPane) splitPane).setRightComponent(imagePanel);
                    splitPane.revalidate();
                    splitPane.repaint();
                }
            }

            // Store reference to restore text area later if needed
            currentImagePanel = imagePanel;

        } catch (Exception e) {
            // If image loading fails, show error in text area
            bodyTextArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            bodyTextArea.setText("[Error loading image: " + e.getMessage() + "]");
            bodyTextArea.setCaretPosition(0);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
    }

    public void setResources(Resource mainResource, List<Resource> subResources) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Resources");
        resourceMap.clear();

        DefaultMutableTreeNode mainNode = new DefaultMutableTreeNode(mainResource);
        root.add(mainNode);
        resourceMap.put(mainNode, mainResource);

        // Group sub-resources by type for better organization
        DefaultMutableTreeNode scriptsNode = new DefaultMutableTreeNode("Scripts");
        DefaultMutableTreeNode stylesNode = new DefaultMutableTreeNode("Stylesheets");
        DefaultMutableTreeNode imagesNode = new DefaultMutableTreeNode("Images");
        DefaultMutableTreeNode otherNode = new DefaultMutableTreeNode("Other");

        for (Resource resource : subResources) {
            DefaultMutableTreeNode subNode = new DefaultMutableTreeNode(resource);
            resourceMap.put(subNode, resource);
            String type = resource.getContentType() != null ? resource.getContentType().toLowerCase() : "";
            if (type.contains("javascript")) {
                scriptsNode.add(subNode);
            } else if (type.contains("css")) {
                stylesNode.add(subNode);
            } else if (type.startsWith("image/")) {
                imagesNode.add(subNode);
            } else {
                otherNode.add(subNode);
            }
        }

        if (scriptsNode.getChildCount() > 0) mainNode.add(scriptsNode);
        if (stylesNode.getChildCount() > 0) mainNode.add(stylesNode);
        if (imagesNode.getChildCount() > 0) mainNode.add(imagesNode);
        if (otherNode.getChildCount() > 0) mainNode.add(otherNode);

        treeModel.setRoot(root);
        treeModel.reload();

        resourceTree.expandPath(new TreePath(root.getPath()));
        resourceTree.setSelectionPath(new TreePath(mainNode.getPath()));
    }

    private String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public void setStatus(String status) {
        String defaultColorHex = colorToHex(UIManager.getColor("Label.foreground"));
        String htmlPrefix = "<html><font color='" + defaultColorHex + "'>Status: </font>";
        String htmlSuffix = "</html>";
        String valueColorHex = defaultColorHex; // Default to standard text color

        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("Idle") && !status.equalsIgnoreCase("Error")) {
            try {
                String codeStr = status.split(" ")[0];
                int code = Integer.parseInt(codeStr);
                if (code >= 200 && code < 300) {
                    valueColorHex = "#50fa7b"; // Green
                } else if (code >= 400 && code < 500) {
                    valueColorHex = "#ffb86c"; // Orange
                } else if (code >= 500) {
                    valueColorHex = "#ff5555"; // Red
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                // Not a parsable status code, value color remains default
            }
        }

        statusLabel.setText(htmlPrefix + "<font color='" + valueColorHex + "'>" + status + "</font>" + htmlSuffix);
    }


    public void setBody(String body, String contentType) {
        // If we're currently showing an image, restore the text area
        if (currentImagePanel != null) {
            restoreTextArea();
        }

        String syntaxStyle = SyntaxConstants.SYNTAX_STYLE_NONE;
        String textToSet = body;
        if (contentType != null) {
            contentType = contentType.toLowerCase();
            if (contentType.contains("json")) {
                syntaxStyle = SyntaxConstants.SYNTAX_STYLE_JSON;
                try {
                    Object jsonObject = JsonParser.parseString(body);
                    textToSet = gson.toJson(jsonObject);
                } catch (JsonSyntaxException e) {
                    textToSet = body;
                }
            } else if (contentType.contains("html")) {
                syntaxStyle = SyntaxConstants.SYNTAX_STYLE_HTML;
                try {
                    Document doc = Jsoup.parse(body);
                    doc.outputSettings().indentAmount(4);
                    textToSet = doc.outerHtml();
                } catch (Exception e) {
                    textToSet = body;
                }
            } else if (contentType.contains("xml")) {
                syntaxStyle = SyntaxConstants.SYNTAX_STYLE_XML;
            } else if (contentType.contains("javascript")) {
                syntaxStyle = SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            } else if (contentType.contains("css")) {
                syntaxStyle = SyntaxConstants.SYNTAX_STYLE_CSS;
            }
        }
        bodyTextArea.setSyntaxEditingStyle(syntaxStyle);
        bodyTextArea.setText(textToSet);
        bodyTextArea.setCaretPosition(0);
        if (isDarkTheme) {
            applyDraculaTheme(bodyTextArea);
        } else {
            applyLightTheme(bodyTextArea);
        }
    }

    private void restoreTextArea() {
        // Get the split pane that contains the body content
        Container parent = bodyTextArea.getParent();
        while (parent != null && !(parent instanceof RTextScrollPane)) {
            parent = parent.getParent();
        }
        if (parent != null) {
            Container splitPane = parent.getParent();
            if (splitPane instanceof JSplitPane) {
                // If the right component is not the text area scroll pane, restore it
                if (((JSplitPane) splitPane).getRightComponent() != parent) {
                    ((JSplitPane) splitPane).setRightComponent(parent);
                    splitPane.revalidate();
                    splitPane.repaint();
                }
            }
        }
        currentImagePanel = null;
    }

    public void setHeaders(Headers headers) {
        headersTableModel.setRowCount(0);
        if (headers != null) {
            for (String name : headers.names()) {
                headersTableModel.addRow(new Object[]{name, headers.get(name)});
            }
        }
    }

    public void setRedirects(String redirects) {
        redirectsTextArea.setText(redirects);
        redirectsTextArea.setCaretPosition(0);
    }

    public void setCookies(List<Cookie> cookies) {
        cookiesTableModel.setRowCount(0);
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookiesTableModel.addRow(new Object[]{cookie.name(), cookie.value()});
            }
        }
    }

    public void updateTimeline(TaskSeriesCollection mainTimeline, Map<Resource, TaskSeriesCollection> subResourceTimelines) {
        // --- Update Main Request Phases Tab ---
        if (mainTimeline == null || mainTimeline.getRowCount() == 0) {
            timelineTabbedPane.setComponentAt(0, new JLabel("No timeline data available for the main request.", SwingConstants.CENTER));
        } else {
            final JFreeChart chart = ChartFactory.createGanttChart(
                    "Main Request Timeline", "Phase", "Time (ms)", mainTimeline, true, true, false);
            formatGanttChart(chart);
            timelineTabbedPane.setComponentAt(0, new ChartPanel(chart));
        }

        // --- Update Resource Waterfall Tab ---
        if (subResourceTimelines == null || subResourceTimelines.isEmpty()) {
            timelineTabbedPane.setComponentAt(1, new JLabel("No sub-resource timeline data available.", SwingConstants.CENTER));
        } else {
            TaskSeriesCollection waterfallDataset = new TaskSeriesCollection();
            TaskSeries series = new TaskSeries("Resources");

            for (Map.Entry<Resource, TaskSeriesCollection> entry : subResourceTimelines.entrySet()) {
                Resource resource = entry.getKey();
                TaskSeriesCollection individualTimeline = entry.getValue();
                if (individualTimeline.getRowCount() > 0 && individualTimeline.getColumnCount() > 0) {
                    TaskSeries resourceSeries = individualTimeline.getSeries(0);
                    if (resourceSeries.getItemCount() > 0) {
                        // Get the start of the first task and end of the last task
                        Date minDate = new Date(Long.MAX_VALUE);
                        Date maxDate = new Date(Long.MIN_VALUE);
                        for (Object item : resourceSeries.getTasks()) {
                            Task task = (Task) item;
                            if (task.getDuration().getStart().before(minDate)) {
                                minDate = task.getDuration().getStart();
                            }
                            if (task.getDuration().getEnd().after(maxDate)) {
                                maxDate = task.getDuration().getEnd();
                            }
                        }
                        if (minDate.getTime() <= maxDate.getTime()) {
                            series.add(new Task(resource.toString(), minDate, maxDate));
                        }
                    }
                }
            }
            waterfallDataset.add(series);

            final JFreeChart waterfallChart = ChartFactory.createGanttChart(
                    "Resource Load Waterfall", "Resource", "Time (ms)", waterfallDataset, true, true, false);
            formatGanttChart(waterfallChart);
            timelineTabbedPane.setComponentAt(1, new ChartPanel(waterfallChart));
        }

        timelineTabbedPane.revalidate();
        timelineTabbedPane.repaint();
    }


    private void formatGanttChart(JFreeChart chart) {
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        DateAxis rangeAxis = (DateAxis) plot.getRangeAxis();
        rangeAxis.setDateFormatOverride(new SimpleDateFormat("S'ms'"));
        rangeAxis.setMinimumDate(new Date(0));

        GanttRenderer renderer = (GanttRenderer) plot.getRenderer();
        renderer.setDrawBarOutline(true);
        renderer.setSeriesPaint(0, new Color(75, 125, 185));
        renderer.setShadowVisible(false);
    }

    public void setPagination(Map<String, String> links) {
        this.paginationLinks = links;
        if (links == null || links.isEmpty()) {
            paginationPanel.setVisible(false);
            return;
        }

        paginationPanel.setVisible(true);
        firstButton.setEnabled(links.containsKey("first"));
        prevButton.setEnabled(links.containsKey("prev"));
        nextButton.setEnabled(links.containsKey("next"));
        lastButton.setEnabled(links.containsKey("last"));
    }

    public void reset() {
        setStatus("Idle");
        restoreTextArea(); // Restore text area if showing image
        setBody("", null);
        setHeaders(null);
        setRedirects("");
        setCookies(null);
        setPagination(null);
        timelineTabbedPane.setComponentAt(0, new JLabel("Timeline will be displayed here after a request is made.", SwingConstants.CENTER));
        timelineTabbedPane.setComponentAt(1, new JLabel("Resource waterfall will be displayed here after a request is made.", SwingConstants.CENTER));

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        treeModel.reload();
        resourceMap.clear();
    }
}