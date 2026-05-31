package xxx.com.web.poster;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * A panel for configuring the main HTTP request details like URL, method, auth, and TLS.
 */
public class RequestPanel extends JPanel {

    private JComboBox<String> methodComboBox;
    private JTextField urlTextField;
    private JSpinner timeoutSpinner;
    private JButton sendButton;
    private JCheckBox followRedirectsCheckBox;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextField certPathField;
    private JPasswordField certPasswordField;

    public RequestPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Top Row: Method, URL, Send Button ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        add(createTopActionPanel(), gbc);
        gbc.gridwidth = 1; // Reset gridwidth

        // --- Options Row ---
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        add(createOptionsPanel(), gbc);
        gbc.gridwidth = 1;

        // --- Authentication Panel ---
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        add(createBasicAuthPanel(), gbc);

        // --- TLS Panel ---
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.gridwidth = 2;
        add(createTlsPanel(), gbc);
    }

    private JPanel createTopActionPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));

        methodComboBox = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"});
        urlTextField = new JTextField("https://www.google.com", 60);
        sendButton = new JButton("Send");

        // Add KeyListener to URL field to trigger send on Enter
        urlTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendButton.doClick();
                }
            }
        });

        topPanel.add(methodComboBox, BorderLayout.WEST);
        topPanel.add(urlTextField, BorderLayout.CENTER);
        topPanel.add(sendButton, BorderLayout.EAST);

        return topPanel;
    }

    private JPanel createOptionsPanel() {
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        // Timeout
        optionsPanel.add(new JLabel("Timeout (s):"));
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 600, 1));
        optionsPanel.add(timeoutSpinner);

        // Follow Redirects
        followRedirectsCheckBox = new JCheckBox("Follow Redirects", true);
        optionsPanel.add(followRedirectsCheckBox);

        return optionsPanel;
    }

    private JPanel createBasicAuthPanel() {
        JPanel authPanel = new JPanel(new GridBagLayout());
        authPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Basic Authentication", TitledBorder.LEFT, TitledBorder.TOP));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Username
        gbc.gridx = 0;
        gbc.gridy = 0;
        authPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        usernameField = new JTextField(20);
        authPanel.add(usernameField, gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        authPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        passwordField = new JPasswordField(20);
        authPanel.add(passwordField, gbc);

        return authPanel;
    }

    private JPanel createTlsPanel() {
        JPanel tlsPanel = new JPanel(new GridBagLayout());
        tlsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "TLS Authentication", TitledBorder.LEFT, TitledBorder.TOP));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // Cert Path
        gbc.gridx = 0;
        gbc.gridy = 0;
        tlsPanel.add(new JLabel("P12 File :"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        certPathField = new JTextField(20);
        tlsPanel.add(certPathField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JButton browseButton = new JButton("...");
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int option = fileChooser.showOpenDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                certPathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        tlsPanel.add(browseButton, gbc);

        // Cert Password
        gbc.gridx = 0;
        gbc.gridy = 1;
        tlsPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        certPasswordField = new JPasswordField(20);
        tlsPanel.add(certPasswordField, gbc);

        return tlsPanel;
    }

    // --- Public Getters and Setters ---

    public String getUrl() {
        return urlTextField.getText();
    }

    public void setUrl(String url) {
        urlTextField.setText(url);
    }

    public JButton getSendButton() {
        return sendButton;
    }

    public String getMethod() {
        return Objects.requireNonNull(methodComboBox.getSelectedItem()).toString();
    }

    public int getTimeout() {
        return (Integer) timeoutSpinner.getValue();
    }

    public boolean isFollowRedirects() {
        return followRedirectsCheckBox.isSelected();
    }

    public String getUsername() {
        return usernameField.getText();
    }

    public char[] getPassword() {
        return passwordField.getPassword();
    }

    public String getCertPath() {
        return certPathField.getText();
    }

    public char[] getCertPassword() {
        return certPasswordField.getPassword();
    }

    public void addSendButtonListener(ActionListener listener) {
        sendButton.addActionListener(listener);
    }
}