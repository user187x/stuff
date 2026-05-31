package xxx.com.web.poster;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * UI Panel containing tabs for Headers, Parameters, Body, and a generated cURL command.
 */
public class RequestDataPanel extends JPanel {

  private final RequestPanel requestPanel; // Reference to get URL, method, etc.
  private JTable headersTable;
  private JTable paramsTable;
  private JTextArea bodyTextArea;
  private JTextArea curlTextArea;
  private DefaultTableModel headersTableModel;
  private DefaultTableModel paramsTableModel;

  public RequestDataPanel(RequestPanel requestPanel) {
    this.requestPanel = requestPanel;
    initComponents();
  }

  private void initComponents() {
    setLayout(new BorderLayout());
    JTabbedPane tabbedPane = new JTabbedPane();

    // Headers Panel
    JPanel headersPanel = createKeyValuePanel("Header Name", "Header Value");
    headersTableModel = (DefaultTableModel) ((JTable) ((JScrollPane) headersPanel.getComponent(0)).getViewport().getView()).getModel();
    tabbedPane.addTab("Headers", headersPanel);

    // Parameters Panel
    JPanel paramsPanel = createKeyValuePanel("Param Name", "Param Value");
    paramsTableModel = (DefaultTableModel) ((JTable) ((JScrollPane) paramsPanel.getComponent(0)).getViewport().getView()).getModel();
    tabbedPane.addTab("Parameters", paramsPanel);

    // Body Panel
    bodyTextArea = new JTextArea();
    bodyTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
    JScrollPane bodyScrollPane = new JScrollPane(bodyTextArea);
    tabbedPane.addTab("Body", bodyScrollPane);

    // cURL Panel
    curlTextArea = new JTextArea();
    curlTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
    curlTextArea.setEditable(false);
    curlTextArea.setLineWrap(true);
    curlTextArea.setWrapStyleWord(true);
    JScrollPane curlScrollPane = new JScrollPane(curlTextArea);
    tabbedPane.addTab("cURL", curlScrollPane);

    // Add a listener to generate the cURL command when the tab is selected
    tabbedPane.addChangeListener(e -> {
      if (tabbedPane.getSelectedComponent() == curlScrollPane) {
        generateCurlCommand();
      }
    });

    add(tabbedPane, BorderLayout.CENTER);
  }


  private JPanel createKeyValuePanel(String keyName, String valueName) {
    JPanel panel = new JPanel(new BorderLayout(5, 5));

    DefaultTableModel tableModel = new DefaultTableModel(new Object[]{keyName, valueName}, 0);
    JTable table = new JTable(tableModel) {
      @Override
      public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        if (!isRowSelected(row)) {
          // Use the UIManager color for alternating rows to respect theme changes
          c.setBackground(row % 2 == 0 ? getBackground() : UIManager.getColor("Table.alternateRowColor"));
        }
        return c;
      }
    };

    panel.add(new JScrollPane(table), BorderLayout.CENTER);

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JTextField keyField = new JTextField(15);
    JTextField valueField = new JTextField(25);
    JButton addButton = new JButton("Add/Change");
    JButton removeButton = new JButton("Remove");

    buttonPanel.add(new JLabel(keyName + ":"));
    buttonPanel.add(keyField);
    buttonPanel.add(new JLabel(valueName + ":"));
    buttonPanel.add(valueField);
    buttonPanel.add(addButton);
    buttonPanel.add(removeButton);

    addButton.addActionListener(e -> {
      tableModel.addRow(new String[]{keyField.getText(), valueField.getText()});
      keyField.setText("");
      valueField.setText("");
    });

    removeButton.addActionListener(e -> {
      int selectedRow = table.getSelectedRow();
      if (selectedRow != -1) {
        tableModel.removeRow(selectedRow);
      }
    });

    panel.add(buttonPanel, BorderLayout.SOUTH);
    return panel;
  }

  private void generateCurlCommand() {
    StringBuilder curl = new StringBuilder("curl");

    // --- URL and Parameters ---
    String url = requestPanel.getUrl();
    Map<String, String> params = getParameters();
    if (!params.isEmpty() && requestPanel.getMethod().equals("GET")) {
      String query = params.entrySet().stream()
          .map(p -> URLEncoder.encode(p.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(p.getValue(), StandardCharsets.UTF_8))
          .collect(Collectors.joining("&"));
      url += (url.contains("?") ? "&" : "?") + query;
    }
    curl.append(" '").append(url).append("'");

    // --- Method ---
    String method = requestPanel.getMethod();
    if (!method.equals("GET")) {
      curl.append(" \\\n  -X ").append(method);
    }

    // --- Follow Redirects ---
    if (requestPanel.isFollowRedirects()) {
      curl.append(" \\\n  -L");
    }

    // --- Headers ---
    for (Map.Entry<String, String> header : getHeaders().entrySet()) {
      curl.append(" \\\n  -H '").append(header.getKey()).append(": ").append(header.getValue()).append("'");
    }

    // --- Basic Auth ---
    String username = requestPanel.getUsername();
    if (username != null && !username.trim().isEmpty()) {
      String password = new String(requestPanel.getPassword());
      curl.append(" \\\n  -u '").append(username).append(":").append(password).append("'");
    }

    // --- TLS Client Certificate ---
    String certPath = requestPanel.getCertPath();
    if (certPath != null && !certPath.trim().isEmpty()) {
      curl.append(" \\\n  --cert '").append(certPath).append(":").append("****").append("'");
    }

    // --- Body ---
    String body = getBody();
    if (body != null && !body.trim().isEmpty()) {
      // Escape single quotes in the body for correct shell command
      String escapedBody = body.replace("'", "'\\''");
      curl.append(" \\\n  --data-raw '").append(escapedBody).append("'");
    }

    curlTextArea.setText(curl.toString());
    curlTextArea.setCaretPosition(0);
  }


  public Map<String, String> getHeaders() {
    return getMapFromTable(headersTableModel);
  }

  public Map<String, String> getParameters() {
    return getMapFromTable(paramsTableModel);
  }

  public String getBody() {
    return bodyTextArea.getText();
  }

  private Map<String, String> getMapFromTable(DefaultTableModel model) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < model.getRowCount(); i++) {
      String key = (String) model.getValueAt(i, 0);
      String value = (String) model.getValueAt(i, 1);
      if (key != null && !key.trim().isEmpty()) {
        map.put(key.trim(), value.trim());
      }
    }
    return map;
  }
}

