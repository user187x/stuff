package xxx.com.messager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import org.apache.commons.lang3.StringUtils;
import xxx.com.encrypt.EncryptAESGCM;
import xxx.com.vetx.websockets.WebSocketClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

import static xxx.com.messager.Messenger.DISCOVERY_PORT;


public class Messenger {

  private JLabel roleActivityLabel;

  private static final int SERVER_PORT = 8999;
  private static final String WEBSOCKET_PATH = "/chat/v1";

  // --- GUI Components ---
  private JFrame frame;
  private JTextArea messageArea, inputArea;
  private JTextField nameField, portField;
  private JButton hostButton, joinButton, sendButton, disconnectButton;
  private static JCheckBox encryptCheck;
  private JLabel passphraseSetLabel;
  private JLabel statusLabel, typingLabel, clientCountLabel, activityIndicatorLabel;
  private JPanel joinPanel, transfersPanel;
  private JList<ReceivedFile> receivedFilesList;
  private DefaultListModel<ReceivedFile> receivedFilesModel;
  private JList<String> peersList;
  private DefaultListModel<String> peersModel;
  private JList<DiscoveredHost> discoveredHostsList;
  private DefaultListModel<DiscoveredHost> discoveredHostsModel;

  // --- Networking & State ---
  private final Vertx vertx = Vertx.vertx();
  private HttpServer server; // host mode
  private final Map<ServerWebSocket, String> userUsernameMap = new ConcurrentHashMap<>();
  private WebSocketClient wsClient; // client mode wrapper
  private String myUsername = "User";
  private Timer typingTimer;
  private final Gson gson = new Gson();
  private final DiscoveryService discoveryService;
  private String hostingIp = null;
  private int hostingPort = -1;
  private volatile boolean isHosting = false;

  private ImageIcon chatWallpaper;

  // --- Security State ---
  private char[] currentPasskey = null;
  private boolean isEncryptionEnabled = false;

  // --- File Transfer State ---
  private final Map<String, FileTransfer> activeTransfers = new ConcurrentHashMap<>();

  // --- Constants ---
  public static final int DISCOVERY_PORT = 8888;
  private static final int FILE_CHUNK_SIZE = 8192; // 8 KB chunks

  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(Messenger::new);
  }

  public Messenger() {

    initializeComponents();
    setupUI();
    discoveryService = new DiscoveryService(this::addDiscoveredHost, this::removeExpiredHosts);
    discoveryService.startListening();
  }

  private void initializeComponents() {
    URL backgroundUrl = Messenger.class.getResource("/jpg/wallpaper.jpg");
    if (backgroundUrl != null) {
      chatWallpaper = new ImageIcon(backgroundUrl);
    } else {
      System.out.println("Chat Wall Paper not found");
    }
  }

  // =================================================================================
  // Secure Payload Handling (Replaces the old 'Wrapper' class)
  // =================================================================================

  /**
   * A stateless utility class for creating and parsing secure message envelopes.
   * Payloads can be either plaintext ("raw") or encrypted ("enc").
   */
  private static class SecurePayload {

    /**
     * Wraps a plaintext JSON payload into a secure envelope. If a passkey is provided,
     * the payload is encrypted. Otherwise, it's sent as raw text.
     *
     * @param passKey The passkey to use for encryption. Can be null for plaintext.
     * @param plaintextJson The raw JSON string payload to wrap.
     * @return A JSON string representing the secure envelope.
     */
    public static String wrap(char[] passKey, String plaintextJson) {
      JsonObject envelope = new JsonObject();
      if (passKey != null && passKey.length > 0) {
        try {
          String encryptedData = EncryptAESGCM.encrypt(passKey, plaintextJson);
          envelope.addProperty("type", "enc");
          envelope.addProperty("data", encryptedData);
        } catch (Exception e) {
          // Fallback to raw if encryption fails, logging the error.
          System.err.println("Encryption failed! Sending message as plaintext. Error: " + e.getMessage());
          envelope.addProperty("type", "raw");
          envelope.addProperty("data", plaintextJson);
        }
      } else {
        // No passkey, send as raw.
        envelope.addProperty("type", "raw");
        envelope.addProperty("data", plaintextJson);
      }
      return new Gson().toJson(envelope);
    }

    /**
     * Unwraps a secure envelope, decrypting it if necessary.
     *
     * @param passKey The passkey for decryption. Must not be null if the payload is encrypted.
     * @param envelope The incoming JsonObject representing the envelope.
     * @return The plaintext JSON string from the envelope's data field.
     * @throws Exception if the payload is encrypted and decryption fails.
     */
    public static String unwrap(char[] passKey, JsonObject envelope) throws Exception {
      if (!isEnvelope(envelope)) {
        // If it's not a valid envelope, return the raw object as a string.
        // This handles legacy or non-enveloped messages.
        return envelope.toString();
      }

      String type = envelope.get("type").getAsString();
      String data = envelope.get("data").getAsString();

      if ("enc".equals(type)) {
        if (passKey == null || passKey.length == 0) {
          throw new IllegalStateException("Cannot decrypt message: Passkey is not set.");
        }
        return EncryptAESGCM.decrypt(passKey, data);
      } else {
        return data;
      }
    }

    public static boolean isEnvelope(JsonObject obj) {
      return obj != null && obj.has("type") && obj.has("data");
    }

    public static boolean isEncrypted(JsonObject obj) {
      return isEnvelope(obj) && "enc".equals(obj.get("type").getAsString());
    }
  }


  // =================================================================================
  // Security & Encryption Flow
  // =================================================================================

  /**
   * Centralized method to enable or disable encryption. This is the single point of control
   * for the application's encryption state and UI.
   *
   * @param enable True to attempt to enable encryption, false to disable it.
   */
  private void setEncryptionEnabled(boolean enable) {
    if (enable) {
      // Attempting to turn ON. This triggers the full prompt/validate/retry flow.
      boolean success = promptForPasskey();
      if (!success) {
        // If the process was cancelled or failed, ensure the UI reflects the OFF state.
        SwingUtilities.invokeLater(() -> encryptCheck.setSelected(false));
      }
    } else {
      // Turning OFF. Securely clear the passkey and update the UI.
      if (this.currentPasskey != null) {
        Arrays.fill(this.currentPasskey, '\0'); // Zero out the character array
        this.currentPasskey = null;
      }
      this.isEncryptionEnabled = false;
      SwingUtilities.invokeLater(() -> {
        encryptCheck.setSelected(false);
        passphraseSetLabel.setText(" ");
        logMessage("System", "Encryption disabled.");
        updateStatusLabel();
      });
    }
  }

  /**
   * Prompts the user for a passphrase and validates it. This method handles the entire UI flow,
   * including retries and the option to disconnect on failure. It securely manages the password
   * as a char array.
   *
   * @return true if a valid passphrase was set, false if the user cancelled or chose to disconnect.
   */
  private boolean promptForPasskey() {
    while (true) { // Loop until a valid key is entered or the user cancels.
      JPasswordField pf = new JPasswordField();
      int option = JOptionPane.showConfirmDialog(
          frame, pf, "Enter Shared Passphrase",
          JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
      );

      if (option != JOptionPane.OK_OPTION) {
        return false; // User cancelled
      }

      char[] password = pf.getPassword();
      try {
        if (password == null || password.length == 0) {
          JOptionPane.showMessageDialog(frame, "Passphrase cannot be empty.", "Input Error", JOptionPane.WARNING_MESSAGE);
          continue; // Re-prompt
        }

        if (EncryptAESGCM.validatepassKey(password)) {
          // SUCCESS
          // Securely clear any old key before setting the new one.
          if (this.currentPasskey != null) {
            Arrays.fill(this.currentPasskey, '\0');
          }
          this.currentPasskey = password; // Keep the char[]
          this.isEncryptionEnabled = true;

          SwingUtilities.invokeLater(() -> {
            encryptCheck.setSelected(true);
            passphraseSetLabel.setText("(🔒)");
            logMessage("System", "Encryption enabled with new passkey.");
            updateStatusLabel();
          });

          // Notify host of successful passcode so it can announce the actual join
          if (wsClient != null) {
            JsonObject okMsg = new JsonObject();
            okMsg.addProperty("type", "passcode_ok");
            okMsg.addProperty("user", myUsername);
            sendPlaintextRawMessage(gson.toJson(okMsg));
          }

          // **FIX**: Exit the loop and signal success.
          return true;

        } else {
          // FAILURE
          // Alert the host if we are a client
          if (wsClient != null) {
            JsonObject failMsg = new JsonObject();
            failMsg.addProperty("type", "passcode_fail");
            failMsg.addProperty("user", myUsername);
            // Send this specific alert unencrypted so the host always receives it.
            sendPlaintextRawMessage(gson.toJson(failMsg));
          }

          int choice = JOptionPane.showOptionDialog(
              frame, "Incorrect Passcode. What would you like to do?", "Passcode Error",
              JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null,
              new String[]{"Re-enter", "Disconnect"}, "Re-enter"
          );

          if (choice == JOptionPane.NO_OPTION || choice == JOptionPane.CLOSED_OPTION) {
            SwingUtilities.invokeLater(this::disconnect);
            return false; // User chose to disconnect
          }
          // If they chose "Re-enter", the loop continues.
        }
      } finally {
        // IMPORTANT: Always clear the password array from the JPasswordField
        // unless it's the one we are keeping.
        if (password != this.currentPasskey) {
          Arrays.fill(password, '\0');
        }
      }
    }
  }

  /**
   * Handles the server's reply to an encryption status query (client-side).
   * @param json The JSON object from the server.
   */
  private void handleEncryptionStatus(JsonObject json) {
    boolean hostEncryptionEnabled = json.has("enabled") && json.get("enabled").getAsBoolean();
    SwingUtilities.invokeLater(() -> {
      if (hostEncryptionEnabled) {
        logMessage("System", "Host has encryption ENABLED. Passphrase required.");
        if (!isEncryptionEnabled) { setEncryptionEnabled(true); } // Only prompt if not already enabled
      } else {
        logMessage("System", "Host has encryption DISABLED.");
        setEncryptionEnabled(false);
      }
    });
  }

  // =================================================================================
  // UI Setup Methods (No changes here, kept for context)
  // =================================================================================

  private void setupUI() {

    frame = new JFrame("P2P Messenger");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setLayout(new BorderLayout(10, 10));
    frame.setSize(800, 700);

    frame.add(createControlPanel(), BorderLayout.NORTH);
    frame.add(createCenterPanel(), BorderLayout.CENTER);
    frame.add(createRightPanel(), BorderLayout.EAST);

    frame.addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosing(java.awt.event.WindowEvent e) {
            disconnect();
            vertx.close();
          }
        });

    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }

  private JPanel createControlPanel() {

    JPanel controlPanel = new JPanel(new BorderLayout());
    controlPanel.setBorder(new TitledBorder("Controls"));

    JPanel topControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
    nameField = new JTextField("User" + (int) (Math.random() * 1000), 10);

    nameField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        if (frame != null) {
          frame.setTitle("Messenger - " + nameField.getText());
        }
      }
    });

    nameField.addActionListener(e -> updateTitle());
    portField = new JTextField("" + SERVER_PORT, 5);
    hostButton = new JButton("Host");
    joinButton = new JButton("Join");
    disconnectButton = new JButton("Disconnect");
    disconnectButton.setEnabled(false);
    encryptCheck = new JCheckBox("Encrypt");

    encryptCheck.addActionListener(e -> setEncryptionEnabled(encryptCheck.isSelected()));
    passphraseSetLabel = new JLabel(" ");

    topControls.add(new JLabel("Your Name:"));
    topControls.add(nameField);
    topControls.add(new JLabel("Port:"));
    topControls.add(portField);
    topControls.add(hostButton);
    topControls.add(joinButton);
    topControls.add(disconnectButton);
    topControls.add(encryptCheck);
    topControls.add(passphraseSetLabel);

    JPanel topWrapper = new JPanel(new BorderLayout());
    topWrapper.add(topControls, BorderLayout.WEST);
    activityIndicatorLabel = new JLabel();

    try {

      URL gifUrl = Messenger.class.getResource("/gif/small-gears.gif");

      if (gifUrl != null)
        activityIndicatorLabel.setIcon(new ImageIcon(gifUrl));
      else
        activityIndicatorLabel.setText(" (Active) ");
    }
    catch (Exception e) {
      activityIndicatorLabel.setText(" (Active) ");
    }

    activityIndicatorLabel.setVisible(false);
    topWrapper.add(activityIndicatorLabel, BorderLayout.EAST);

    statusLabel = new JLabel("Status: Idle");
    clientCountLabel = new JLabel("Connected Clients: 0");
    clientCountLabel.setVisible(false);
    typingLabel = new JLabel(" ");
    typingLabel.setForeground(Color.GRAY);

    roleActivityLabel = new JLabel();
    roleActivityLabel.setText("Inactive");
    roleActivityLabel.setForeground(Color.LIGHT_GRAY);
    roleActivityLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    roleActivityLabel.setVisible(false);

    JPanel statusPanel = new JPanel();
    statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
    statusPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
    statusPanel.add(statusLabel);
    statusPanel.add(clientCountLabel);
    statusPanel.add(typingLabel);
    statusPanel.add(Box.createVerticalGlue());
    statusPanel.add(roleActivityLabel);

    joinPanel = createJoinPanel();
    joinPanel.setVisible(false);

    controlPanel.add(topWrapper, BorderLayout.NORTH);
    controlPanel.add(statusPanel, BorderLayout.CENTER);
    controlPanel.add(joinPanel, BorderLayout.SOUTH);

    hostButton.addActionListener(e -> startHosting());
    joinButton.addActionListener(e -> toggleJoinPanel());
    disconnectButton.addActionListener(e -> disconnect());



    updateTitle();
    return controlPanel;
  }

  private JPanel createJoinPanel() {
    JPanel panel = new JPanel(new BorderLayout(10, 10));
    panel.setBorder(new TitledBorder("Join a Host"));

    discoveredHostsModel = new DefaultListModel<>();
    discoveredHostsList = new JList<>(discoveredHostsModel);
    discoveredHostsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    discoveredHostsList.addMouseListener(
        new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
              DiscoveredHost selected = discoveredHostsList.getSelectedValue();
              if (selected != null) connectToHost(selected.getIp(), selected.getPort());
            }
          }
        });
    JScrollPane discoveredScrollPane = new JScrollPane(discoveredHostsList);
    discoveredScrollPane.setBorder(new TitledBorder("Discovered Hosts (Double-click to join)"));
    panel.add(discoveredScrollPane, BorderLayout.CENTER);

    JPanel manualPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JTextField ipField = new JTextField("127.0.0.1", 15);
    JTextField manualPortField = new JTextField("" + SERVER_PORT, 5);
    JButton connectButton = new JButton("Connect Manually");
    connectButton.addActionListener(
        e -> connectToHost(ipField.getText(), Integer.parseInt(manualPortField.getText())));
    manualPanel.add(new JLabel("IP:"));
    manualPanel.add(ipField);
    manualPanel.add(new JLabel("Port:"));
    manualPanel.add(manualPortField);
    manualPanel.add(connectButton);
    panel.add(manualPanel, BorderLayout.SOUTH);

    return panel;
  }

  private JPanel createCenterPanel() {
    JPanel centerPanel = new JPanel(new BorderLayout(10, 10));

    messageArea = new JTextArea();
    messageArea.setEditable(false);
    messageArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
    messageArea.setLineWrap(true);
    messageArea.setWrapStyleWord(true);
    messageArea.setOpaque(false); // Make text area transparent

    // Create a panel that will hold the text area and have the background
    JPanel backgroundPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (chatWallpaper != null) {
          // Draw the image scaled to the component size
          g.drawImage(chatWallpaper.getImage(), 0, 0, getWidth(), getHeight(), this);
        }
      }
    };
    backgroundPanel.add(messageArea);

    JScrollPane messageScrollPane = new JScrollPane(backgroundPanel); // Use the new panel
    messageScrollPane.getViewport().setOpaque(false); // Make viewport see-through
    messageScrollPane.setBorder(new TitledBorder("Chat"));

    JPanel bottomWrapper = new JPanel(new BorderLayout(0, 5));
    bottomWrapper.add(createInputPanel(), BorderLayout.SOUTH);

    transfersPanel = new JPanel();
    transfersPanel.setLayout(new BoxLayout(transfersPanel, BoxLayout.Y_AXIS));

    JScrollPane transfersScrollPane = new JScrollPane(transfersPanel);
    transfersScrollPane.setBorder(new TitledBorder("File Transfers"));
    transfersScrollPane.setPreferredSize(new Dimension(400, 100));

    bottomWrapper.add(transfersScrollPane, BorderLayout.CENTER);

    centerPanel.add(messageScrollPane, BorderLayout.CENTER);
    centerPanel.add(bottomWrapper, BorderLayout.SOUTH);
    return centerPanel;
  }

  private JPanel createInputPanel() {
    JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
    inputArea = new JTextArea(3, 20);
    inputArea.setLineWrap(true);
    inputArea.setWrapStyleWord(true);
    inputArea.setBorder(new TitledBorder("Type message or drop file here"));

    setupInputAreaListeners();

    sendButton = new JButton("Send");
    sendButton.addActionListener(e -> sendMessage(inputArea.getText()));

    inputPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
    inputPanel.add(sendButton, BorderLayout.EAST);
    return inputPanel;
  }

  private Component createRightPanel() {
    JSplitPane splitPane =
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, createReceivedFilesPanel(), createPeersPanel());
    splitPane.setResizeWeight(0.5);
    splitPane.setPreferredSize(new Dimension(200, 0));
    return splitPane;
  }

  private JPanel createReceivedFilesPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(new TitledBorder("Received Files"));

    receivedFilesModel = new DefaultListModel<>();
    receivedFilesList = new JList<>(receivedFilesModel);
    receivedFilesList.setCellRenderer(new ReceivedFileRenderer());

    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem openItem = new JMenuItem("Open"); // open with OS-associated app
    JMenuItem saveItem = new JMenuItem("Save As...");
    JMenuItem deleteItem = new JMenuItem("Delete");

    openItem.addActionListener(e -> openSelectedFile());
    saveItem.addActionListener(e -> saveSelectedFile());
    deleteItem.addActionListener(e -> deleteSelectedFile());

    popupMenu.add(openItem);
    popupMenu.add(saveItem);
    popupMenu.add(deleteItem);

    receivedFilesList.addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
              int index = receivedFilesList.locationToIndex(e.getPoint());
              if (index != -1) {
                receivedFilesList.setSelectedIndex(index);
                popupMenu.show(receivedFilesList, e.getX(), e.getY());
              }
            }
          }
        });

    panel.add(new JScrollPane(receivedFilesList), BorderLayout.CENTER);
    return panel;
  }

  private JPanel createPeersPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(new TitledBorder("Peers"));

    peersModel = new DefaultListModel<>();
    peersList = new JList<>(peersModel);

    JPopupMenu popupMenu = new JPopupMenu();
    JMenuItem dmItem = new JMenuItem("Send Direct Message");
    dmItem.addActionListener(e -> sendDirectMessage());
    popupMenu.add(dmItem);

    peersList.addMouseListener(
        new MouseAdapter() {
          public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isRightMouseButton(e)) {
              int index = peersList.locationToIndex(e.getPoint());
              if (index != -1) {
                peersList.setSelectedIndex(index);
                popupMenu.show(peersList, e.getX(), e.getY());
              }
            }
          }
        });

    panel.add(new JScrollPane(peersList), BorderLayout.CENTER);
    return panel;
  }

  private void setupInputAreaListeners() {
    inputArea.addKeyListener(
        new KeyAdapter() {
          public void keyTyped(KeyEvent e) {
            if (typingTimer != null) typingTimer.cancel();
            String text = inputArea.getText().trim();
            if (text.isEmpty()) {
              sendTypingIndicator(false); // stop immediately if blank
            } else {
              sendTypingIndicator(true);
              typingTimer = new Timer();
              typingTimer.schedule(
                  new TimerTask() {
                    public void run() {
                      sendTypingIndicator(false);
                    }
                  },
                  500);
            }
          }

          public void keyReleased(KeyEvent e) {
            String text = inputArea.getText().trim();
            if (text.isEmpty()) {
              if (typingTimer != null) typingTimer.cancel();
              sendTypingIndicator(false);
            }
          }

          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
              e.consume();
              sendButton.doClick();
            }
          }
        });

    inputArea.setDropTarget(
        new DropTarget() {
          public synchronized void drop(DropTargetDropEvent dtde) {
            try {
              dtde.acceptDrop(DnDConstants.ACTION_COPY);
              @SuppressWarnings("unchecked")
              List<File> droppedFiles =
                  (List<File>)
                      dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
              inputArea.setText("");
              for (File file : droppedFiles) {
                inputArea.append(file.getAbsolutePath() + "\n");
              }
            } catch (Exception ex) {
              System.out.println(ex.getMessage());
              System.exit(1);
            }
          }
        });
  }

  // =================================================================================
  // Networking & Connection Management
  // =================================================================================

  private void startHosting() {

    if (roleActivityLabel != null) {

      roleActivityLabel.setText("Hosting Active");
      roleActivityLabel.setForeground(Color.GREEN);
      roleActivityLabel.setVisible(true);
    }

    hostButton.setEnabled(false);
    joinButton.setEnabled(false);
    statusLabel.setText("Status: Starting...");
    myUsername = nameField.getText();

    updateTitle();

    try {
      final int port = Integer.parseInt(portField.getText());

      HttpServerOptions opts =
          new HttpServerOptions()
              .setIdleTimeout(0)
              .setIdleTimeoutUnit(java.util.concurrent.TimeUnit.SECONDS)
              .setTcpKeepAlive(true);

      server = vertx.createHttpServer(opts);
      server.webSocketHandler(
          ws -> {
            if (!WEBSOCKET_PATH.equals(ws.path())) {
              ws.writeFrame(
                  WebSocketFrame.textFrame("WebSocket Server Path : " + WEBSOCKET_PATH, true));
              ws.close();
              return;
            }

            String username =
                Optional.ofNullable(ws.query()).filter(q -> !q.isBlank()).stream()
                    .flatMap(q -> Arrays.stream(q.split("&")))
                    .map(p -> p.split("=", 2))
                    .filter(kv -> kv.length == 2 && "username".equals(kv[0]))
                    .map(kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8))
                    .filter(s -> !s.isBlank())
                    .findFirst()
                    .orElseGet(() -> "Unknown-" + new Random().nextInt(1000));

            userUsernameMap.put(ws, username);

            SwingUtilities.invokeLater(
                () -> {
                  updateClientCount();
                  updatePeerListsOnConnect(ws, username);
                });

            long pingTimerId =
                vertx.setPeriodic(
                    20_000,
                    id -> ws.writeFrame(WebSocketFrame.pingFrame(Buffer.buffer("keepalive"))));
            ws.pongHandler(buf -> System.out.println("Pong -> " + ws.remoteAddress()));

            ws.handler(
                buffer -> {
                  String message = buffer.toString();
                  // Intercept encryption status request and respond only to requester
                  try {
                    JsonObject j = gson.fromJson(message, JsonObject.class);
                    if (j != null
                        && j.has("type")
                        && "encryption_status_request".equals(j.get("type").getAsString())) {
                      JsonObject reply = new JsonObject();
                      reply.addProperty("type", "encryption_status");
                      reply.addProperty("enabled", this.isEncryptionEnabled); // Respond with current state
                      ws.writeTextMessage(new Gson().toJson(reply));
                      return; // do not broadcast
                    }
                  } catch (Exception ignore) {
                  }
                  handleIncomingMessage(message);
                  broadcastRawMessage(message, ws);
                });

            ws.closeHandler(
                v -> {
                  vertx.cancelTimer(pingTimerId);
                  String closedUsername = userUsernameMap.remove(ws);
                  if (closedUsername != null) {
                    SwingUtilities.invokeLater(
                        () -> {
                          updateClientCount();
                          updatePeerListsOnDisconnect(closedUsername);
                        });
                  }
                });
            ws.exceptionHandler(
                err -> System.err.println("WebSocket Server Error : " + err.getMessage()));
          });

      server
          .listen(port)
          .onSuccess(
              serverSocket ->
                  SwingUtilities.invokeLater(
                      () -> {
                        try {
                          hostingIp = InetAddress.getLocalHost().getHostAddress();
                        } catch (UnknownHostException e) {
                          hostingIp = "127.0.0.1";
                        }
                        isHosting = true;
                        hostingPort = serverSocket.actualPort();
                        setConnectionState(true, "Hosting on: " + hostingIp + ":" + hostingPort);
                        clientCountLabel.setVisible(true);
                        updateClientCount();
                        discoveryService.startBroadcasting(myUsername, hostingIp, hostingPort);
                        logMessage("System", "Server started successfully.");
                      }))
          .onFailure(
              err ->
                  SwingUtilities.invokeLater(
                      () -> {
                        JOptionPane.showMessageDialog(
                            frame,
                            "Could not start host: " + err.getMessage(),
                            "Hosting Error",
                            JOptionPane.ERROR_MESSAGE);
                        setConnectionState(false, "Status: Idle");
                      }));

    } catch (NumberFormatException e) {
      JOptionPane.showMessageDialog(
          frame, "Invalid port number.", "Error", JOptionPane.ERROR_MESSAGE);
      setConnectionState(false, "Status: Idle");
    }
  }

  private void connectToHost(String ip, int port) {
    hostButton.setEnabled(false);
    joinButton.setEnabled(false);
    myUsername = nameField.getText();
    updateTitle();

    String encodedUsername = URLEncoder.encode(myUsername, StandardCharsets.UTF_8);

    WebSocketClient.Config cfg =
        new WebSocketClient.Config()
            .setHost(ip)
            .setPort(port)
            .setSsl(false)
            .setPath(WEBSOCKET_PATH + "?username=" + encodedUsername)
            .setPingIntervalMillis(20_000)
            .setAutoReconnect(true)
            .setReconnectBaseDelayMillis(500)
            .setReconnectMaxDelayMillis(10_000);

    wsClient =new WebSocketClient(vertx, cfg).onOpen(() ->
            SwingUtilities.invokeLater(() -> {

              isHosting = false;

              setConnectionState(true, "Connected to: " + ip + ":" + port);

              if (roleActivityLabel != null){

                roleActivityLabel.setVisible(true);
                roleActivityLabel.setForeground(Color.BLUE);
                roleActivityLabel.setText("Session Joined");
              }

              logMessage("System", "Successfully connected to host.");

              // Immediately query host encryption status (plaintext)
              try {
                JsonObject req = new JsonObject();
                req.addProperty("type", "encryption_status_request");
                // Send directly without the secure envelope
                if (wsClient != null)
                  wsClient.sendText(new Gson().toJson(req));
              }
              catch (Exception ignored) {}
            }))
        .onText(this::handleIncomingMessage)
        .onClose(
            () ->
                SwingUtilities.invokeLater(
                    () -> {
                      logMessage("System", "Disconnected from host.");
                      setConnectionState(false, "Status: Idle");
                    }))
        .onError(
            err ->
                SwingUtilities.invokeLater(
                    () -> {
                      logMessage("Error", "WebSocket error: " + err.getMessage());
                      // Keep UI usable even if initial connect fails
                      hostButton.setEnabled(true);
                      joinButton.setEnabled(true);
                      disconnectButton.setEnabled(false);
                      statusLabel.setText("Status: Idle (connect failed)");
                    }));

    wsClient
        .connect()
        .onFailure(
            err ->
                SwingUtilities.invokeLater(
                    () -> {
                      logMessage("Error", "Could not connect: " + err.getMessage());
                      setConnectionState(false, "Status: Idle");
                    }));
  }

  private void disconnect() {

    if (roleActivityLabel != null){

      roleActivityLabel.setForeground(Color.LIGHT_GRAY);
      roleActivityLabel.setText("Session Disconnected");
      roleActivityLabel.setVisible(false);
    }

    discoveryService.stopBroadcasting();

    if (server != null) {
      server.close();
      server = null;
    }
    if (wsClient != null) {
      try {
        wsClient.close();
      } catch (Exception ignored) {
      }
      wsClient = null;
    }

    this.hostingIp = null;
    this.hostingPort = -1;
    this.isHosting = false;

    activeTransfers.values().forEach(FileTransfer::cancel);
    activeTransfers.clear();

    SwingUtilities.invokeLater(
        () -> {
          transfersPanel.removeAll();
          transfersPanel.revalidate();
          transfersPanel.repaint();
          logMessage("System", "You have disconnected.");
          setConnectionState(false, "Status: Idle");
        });
  }

  // =================================================================================
  // Messaging & Protocol Handling
  // =================================================================================

  private void sendMessage(String text) {

    if (StringUtils.isBlank(text))
      return;

    String[] lines = text.trim().split("\n");
    boolean fileSent = false;

    for (String line : lines) {

      File file = new File(line.trim());

      if (file.exists() && file.isFile()) {
        offerFile(file);
        fileSent = true;
      }
    }
    if (!fileSent) {
      broadcastMessage(myUsername, text);
    }

    inputArea.setText("");
    sendTypingIndicator(false); // ensure indicator off after sending
  }

  private enum MessageType {
    CHAT {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.logMessage(json.get("user").getAsString(), json.get("message").getAsString());
      }
    },
    DM {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.logMessage(
            "(DM from " + json.get("fromUser").getAsString() + ")",
            json.get("message").getAsString());
      }
    },
    TYPING {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.typingLabel.setText(json.get("user").getAsString() + " is typing...");
      }
    },
    STOPPED_TYPING {
      @Override
      void handle(Messenger app, JsonObject json) {
        String user = json.get("user").getAsString();
        if (app.typingLabel.getText().startsWith(user)) {
          app.typingLabel.setText(" ");
        }
      }
    },
    PEER_LIST {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handlePeerList(json.get("peers").getAsJsonArray());
      }
    },
    PEER_JOIN {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.peersModel.addElement(json.get("user").getAsString());
      }
    },
    PEER_LEAVE {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.peersModel.removeElement(json.get("user").getAsString());
      }
    },
    FILE_OFFER {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handleFileOffer(json);
      }
    },
    FILE_ACCEPT {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handleFileAccept(json);
      }
    },
    FILE_DENY {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handleFileDeny(json);
      }
    },
    FILE_CHUNK {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handleFileChunk(json);
      }
    },
    FILE_END {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handleFileEnd(json);
      }
    },
    FILE_CANCEL {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handleFileCancel(json);
      }
    },

    PASSCODE_OK {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handlePasscodeOk(json);
      }
    },
    ENCRYPTION_STATUS {
      @Override
      void handle(Messenger app, JsonObject json) {
        app.handleEncryptionStatus(json);
      }
    },
    ENCRYPTION_STATUS_REQUEST {
      @Override
      void handle(Messenger app, JsonObject json) {
        /* client-only; server responds */
      }
    },
    PASSCODE_FAIL {
      @Override
      void handle(Messenger app, JsonObject json) {
        // This is only handled by the host
        if (app.isHosting) {
          app.logMessage("System", "ALERT: " + json.get("user").getAsString() + " entered an incorrect passcode.");
        }
      }
    },
    UNKNOWN {
      @Override
      void handle(Messenger app, JsonObject json) {
        String t = (json.has("type") ? json.get("type").getAsString() : "<missing>");
        app.logMessage("System", "Unknown message type: " + t);
      }
    };

    abstract void handle(Messenger app, JsonObject json);

    static MessageType from(String s) {
      if (s == null) return UNKNOWN;
      String norm = s.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
      try {
        return MessageType.valueOf(norm);
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }

  /**
   * Handles an incoming raw JSON message from the WebSocket.
   * This method is called on the Vert.x event loop and MUST NOT BLOCK.
   * It immediately schedules the actual processing to run on the Swing Event Dispatch Thread (EDT).
   *
   * @param rawJson The raw JSON string received.
   */
  private void handleIncomingMessage(String rawJson) {
    SwingUtilities.invokeLater(() -> {
      processMessageOnEdt(rawJson);
    });
  }

  /**
   * Processes the message on the Swing EDT to avoid blocking the Vert.x event loop.
   * This method contains all the logic for parsing, decrypting, and handling the message payload.
   *
   * @param rawJson The raw JSON string to process.
   */
  private void processMessageOnEdt(String rawJson) {
    String unwrappedJson;
    try {
      JsonObject envelope = gson.fromJson(rawJson, JsonObject.class);

      // If it's an encrypted envelope and we don't have a key, we must prompt for one.
      // This is now safe because we are on the EDT.
      if (SecurePayload.isEncrypted(envelope) && this.currentPasskey == null) {
        logMessage("System", "Encrypted message received. Please enter the passphrase.");
        if (!promptForPasskey()) {
          logMessage("Error", "Passphrase not provided. Cannot decrypt message.");
          return; // Stop processing if the user cancelled.
        }
      }

      // Now, unwrap the payload. This will decrypt if necessary or just extract the data.
      unwrappedJson = SecurePayload.unwrap(this.currentPasskey, envelope);

    } catch (Exception e) {
      logMessage("Error", "Failed to decrypt message. Passphrase may be incorrect.");
      // Turn off encryption as the key is clearly wrong.
      setEncryptionEnabled(false);
      return;
    }

    try {
      final JsonObject payload = JsonParser.parseString(unwrappedJson).getAsJsonObject();
      final String typeStr = payload.has("type") ? payload.get("type").getAsString() : "UNKNOWN";
      MessageType.from(typeStr).handle(this, payload);
    } catch (Exception e) {
      logMessage("Error", "Could not parse or handle message payload: " + unwrappedJson);
      e.printStackTrace();
    }
  }

  private void broadcastMessage(String user, String message) {
    JsonObject json = new JsonObject();
    json.addProperty("type", "chat");
    json.addProperty("user", user);
    json.addProperty("message", message);
    sendRawMessage(gson.toJson(json));
    logMessage(user, message);
  }

  private void sendDirectMessage() {
    String recipient = peersList.getSelectedValue();
    if (recipient == null
        || recipient.equals(myUsername)
        || recipient.endsWith(" (Host)")
        || recipient.endsWith(" (You)")) return;
    String message =
        JOptionPane.showInputDialog(
            frame,
            "Enter message for " + recipient + ":",
            "Direct Message",
            JOptionPane.PLAIN_MESSAGE);
    if (message != null && !message.trim().isEmpty()) {
      JsonObject json = new JsonObject();
      json.addProperty("type", "dm");
      json.addProperty("fromUser", myUsername);
      json.addProperty("toUser", recipient);
      json.addProperty("message", message);
      sendRawMessage(gson.toJson(json));
      logMessage("(DM to " + recipient + ")", message);
    }
  }

  private void sendTypingIndicator(boolean isTyping) {
    JsonObject json = new JsonObject();
    json.addProperty("type", isTyping ? "typing" : "stopped_typing");
    json.addProperty("user", myUsername);
    sendRawMessage(gson.toJson(json));
  }

  /**
   * Sends a raw JSON string message, bypassing the encryption wrapper.
   * Used for system-level messages that must be readable by the recipient
   * regardless of encryption state (e.g., passcode failure alerts).
   *
   * @param rawMessage The plaintext JSON string to send.
   */
  private void sendPlaintextRawMessage(String rawMessage) {
    if (wsClient != null) {
      try {
        wsClient.sendText(rawMessage);
      } catch (Exception ignored) {
      }
    }
    // This is primarily a client-to-host mechanism, so no host-side broadcast needed.
  }


  private void sendRawMessage(String rawJsonPayload) {
    // The SecurePayload.wrap method handles whether to encrypt based on the current state.
    final String messageToSend = SecurePayload.wrap(
        isEncryptionEnabled ? currentPasskey : null,
        rawJsonPayload
    );

    if (isHosting) {
      // For DMs, send only to the target user.
      JsonObject payload = gson.fromJson(rawJsonPayload, JsonObject.class);
      if ("dm".equals(payload.get("type").getAsString())) {
        String toUser = payload.get("toUser").getAsString();
        userUsernameMap.entrySet().stream()
            .filter(entry -> entry.getValue().equals(toUser))
            .findFirst()
            .ifPresent(entry -> entry.getKey().writeTextMessage(messageToSend));
      } else {
        // Broadcast to all other clients.
        broadcastRawMessage(messageToSend, null);
      }
    } else if (wsClient != null) {
      // Client sends the message to the host.
      try {
        wsClient.sendText(messageToSend);
      } catch (Exception ignored) {}
    }
  }

  private void broadcastRawMessage(String rawMessage, ServerWebSocket fromSocket) {
    userUsernameMap.keySet().stream()
        .filter(ws -> !ws.isClosed() && !Objects.equals(ws, fromSocket))
        .forEach(ws -> ws.writeTextMessage(rawMessage));
  }

  // =================================================================================
  // Peer & Discovery Management
  // =================================================================================

  private void updateClientCount() {
    clientCountLabel.setText("Connected Clients: " + userUsernameMap.size());
  }

  private void updatePeerListsOnConnect(ServerWebSocket newUserSocket, String newUsername) {
    peersModel.addElement(newUsername);
    List<String> allPeers = new ArrayList<>(userUsernameMap.values());
    allPeers.add(myUsername);
    JsonObject peerListJson = new JsonObject();
    peerListJson.addProperty("type", "peer_list");
    peerListJson.add("peers", gson.toJsonTree(allPeers));

    // Send the peer list wrapped securely to the new user
    String securePeerList = SecurePayload.wrap(isEncryptionEnabled ? currentPasskey : null, gson.toJson(peerListJson));
    newUserSocket.writeTextMessage(securePeerList);

    if (isEncryptionEnabled) {
      // Encrypted room: treat as a join REQUEST until passkey verified.
      logMessage("System", "User " + newUsername + " is requesting to join");
      // Do NOT broadcast PEER_JOIN yet. We'll do it when we receive PASSCODE_OK.
    } else {
      // Unencrypted: announce join immediately.
      JsonObject joinJson = new JsonObject();
      joinJson.addProperty("type", "peer_join");
      joinJson.addProperty("user", newUsername);
      broadcastRawMessage(SecurePayload.wrap(null, gson.toJson(joinJson)), newUserSocket);
      logMessage("System", "User " + newUsername + " joined the chat");
    }
  }

  private void handlePasscodeOk(JsonObject json) {
    String username = json.has("user") ? json.get("user").getAsString() : "Unknown";
    // Now that the client has entered the correct passkey, announce the real join.
    JsonObject joinJson = new JsonObject();
    joinJson.addProperty("type", "peer_join");
    joinJson.addProperty("user", username);
    // Broadcast securely since room is encrypted
    broadcastRawMessage(SecurePayload.wrap(isEncryptionEnabled ? currentPasskey : null, gson.toJson(joinJson)), null);
    logMessage("System", "User " + username + " joined the chat");
  }

  private void updatePeerListsOnDisconnect(String username) {
    peersModel.removeElement(username);
    JsonObject leaveJson = new JsonObject();
    leaveJson.addProperty("type", "peer_leave");
    leaveJson.addProperty("user", username);
    broadcastRawMessage(SecurePayload.wrap(isEncryptionEnabled ? currentPasskey : null, gson.toJson(leaveJson)), null);
    logMessage("System", "User " + username + " left the chat");
  }

  private void handlePeerList(JsonArray peersArray) {
    Type listType = new TypeToken<ArrayList<String>>() {}.getType();
    List<String> peers = gson.fromJson(peersArray, listType);
    peersModel.clear();
    peers.forEach(p -> peersModel.addElement(p.equals(myUsername) ? p + " (You)" : p));
  }

  private void addDiscoveredHost(DiscoveredHost host) {
    if (host.getIp().equals(hostingIp) && host.getPort() == hostingPort) return;
    SwingUtilities.invokeLater(
        () -> {
          for (int i = 0; i < discoveredHostsModel.size(); i++) {
            if (discoveredHostsModel.get(i).getIp().equals(host.getIp())
                && discoveredHostsModel.get(i).getPort() == host.getPort()) {
              discoveredHostsModel.set(i, host);
              return;
            }
          }
          discoveredHostsModel.addElement(host);
        });
  }

  private void removeExpiredHosts() {
    SwingUtilities.invokeLater(
        () -> {
          for (int i = discoveredHostsModel.size() - 1; i >= 0; i--) {
            if (discoveredHostsModel.get(i).isExpired()) {
              discoveredHostsModel.remove(i);
            }
          }
        });
  }

  // =================================================================================
  // File Transfer Logic
  // =================================================================================

  private void offerFile(File file) {
    String transferId = UUID.randomUUID().toString();
    FileTransfer transfer =
        new FileTransfer(
            transferId,
            file,
            this::updateTransferProgress,
            this::removeTransfer,
            this::sendCancelMessage);
    activeTransfers.put(transferId, transfer);
    transfersPanel.add(transfer.getProgressPanel());
    transfersPanel.revalidate();
    transfersPanel.repaint();
    JsonObject json = new JsonObject();
    json.addProperty("type", "file_offer");
    json.addProperty("user", myUsername);
    json.addProperty("transferId", transferId);
    json.addProperty("fileName", file.getName());
    json.addProperty("fileSize", file.length());
    sendRawMessage(gson.toJson(json));
    logMessage("System", "Offering file: " + file.getName());
  }

  private void handleFileOffer(JsonObject offerJson) {
    String user = offerJson.get("user").getAsString();
    String fileName = offerJson.get("fileName").getAsString();
    long fileSize = offerJson.get("fileSize").getAsLong();
    String transferId = offerJson.get("transferId").getAsString();
    int choice =
        JOptionPane.showConfirmDialog(
            frame,
            user
                + " wants to send you the file:\n"
                + fileName
                + " ("
                + fileSize / 1024
                + " KB)\nDo you want to accept?",
            "Incoming File Transfer",
            JOptionPane.YES_NO_OPTION);
    JsonObject response = new JsonObject();
    response.addProperty("user", myUsername);
    response.addProperty("transferId", transferId);
    if (choice == JOptionPane.YES_OPTION) {
      response.addProperty("type", "file_accept");
      FileTransfer transfer =
          new FileTransfer(
              transferId,
              fileName,
              fileSize,
              this::updateTransferProgress,
              this::removeTransfer,
              this::sendCancelMessage);
      activeTransfers.put(transferId, transfer);
      transfersPanel.add(transfer.getProgressPanel());
      updateTransferProgress(transfer);
      transfersPanel.revalidate();
      transfersPanel.repaint();
      logMessage("System", "Accepting file: " + fileName);
    } else {
      response.addProperty("type", "file_deny");
      logMessage("System", "Denied file: " + fileName);
    }
    sendRawMessage(gson.toJson(response));
  }

  private void handleFileAccept(JsonObject acceptJson) {
    String transferId = acceptJson.get("transferId").getAsString();
    FileTransfer transfer = activeTransfers.get(transferId);
    if (transfer != null) {
      logMessage("System", acceptJson.get("user").getAsString() + " accepted the file transfer.");
      new Thread(
          () -> {
            try (FileInputStream fis = new FileInputStream(transfer.getFile())) {
              byte[] buffer = new byte[FILE_CHUNK_SIZE];
              int bytesRead;
              while ((bytesRead = fis.read(buffer)) != -1) {
                if (transfer.isCancelled()) {
                  logMessage("System", "File send cancelled by user.");
                  break;
                }
                JsonObject chunkJson = new JsonObject();
                chunkJson.addProperty("type", "file_chunk");
                chunkJson.addProperty("transferId", transferId);
                byte[] actualChunk =
                    (bytesRead == FILE_CHUNK_SIZE) ? buffer : Arrays.copyOf(buffer, bytesRead);
                chunkJson.addProperty("data", Base64.getEncoder().encodeToString(actualChunk));
                sendRawMessage(gson.toJson(chunkJson));
                transfer.addProgress(bytesRead);
              }
              if (!transfer.isCancelled()) {
                JsonObject endJson = new JsonObject();
                endJson.addProperty("type", "file_end");
                endJson.addProperty("transferId", transferId);
                sendRawMessage(gson.toJson(endJson));
              }
            } catch (IOException e) {
              logMessage("Error", "Failed to read file: " + e.getMessage());
              sendCancelMessage(transferId);
            }
          },
          "FileSender")
          .start();
    }
  }

  private void handleFileDeny(JsonObject denyJson) {
    String transferId = denyJson.get("transferId").getAsString();
    FileTransfer transfer = activeTransfers.remove(transferId);
    if (transfer != null) {
      logMessage("System", denyJson.get("user").getAsString() + " denied the file transfer.");
      transfer.cancel();
    }
  }

  private void handleFileChunk(JsonObject chunkJson) {
    String transferId = chunkJson.get("transferId").getAsString();
    FileTransfer transfer = activeTransfers.get(transferId);
    if (transfer != null && !transfer.isCancelled()) {
      byte[] chunkData = Base64.getDecoder().decode(chunkJson.get("data").getAsString());
      transfer.appendData(chunkData);
      transfer.addProgress(chunkData.length);
    }
  }

  private void handleFileEnd(JsonObject endJson) {
    String transferId = endJson.get("transferId").getAsString();
    FileTransfer transfer = activeTransfers.remove(transferId);
    if (transfer != null) {
      ReceivedFile receivedFile = transfer.complete();
      receivedFilesModel.addElement(receivedFile);
      logMessage("System", "File transfer complete: " + receivedFile.getFileName());
    }
  }

  private void handleFileCancel(JsonObject cancelJson) {
    String transferId = cancelJson.get("transferId").getAsString();
    FileTransfer transfer = activeTransfers.remove(transferId);
    if (transfer != null) {
      logMessage(
          "System",
          (cancelJson.has("user") ? cancelJson.get("user").getAsString() : "A user")
              + " cancelled the file transfer.");
      transfer.cancel();
    }
  }

  private void sendCancelMessage(String transferId) {
    FileTransfer transfer = activeTransfers.get(transferId);
    if (transfer != null && !transfer.isCancelled()) {
      transfer.cancel();
      JsonObject json = new JsonObject();
      json.addProperty("type", "file_cancel");
      json.addProperty("user", myUsername);
      json.addProperty("transferId", transferId);
      sendRawMessage(gson.toJson(json));
    }
  }

  private void updateTransferProgress(FileTransfer transfer) {
    SwingUtilities.invokeLater(
        () -> {
          transfer.getProgressPanel().repaint();
          transfer.getProgressPanel().revalidate();
        });
  }

  private void removeTransfer(FileTransfer transfer) {
    SwingUtilities.invokeLater(
        () -> {
          transfersPanel.remove(transfer.getProgressPanel());
          transfersPanel.repaint();
          transfersPanel.revalidate();
        });
  }

  // =================================================================================
  // UI Helpers & File Management
  // =================================================================================

  private void setConnectionState(boolean isConnected, String statusText) {
    myUsername = nameField.getText();
    updateTitle();
    nameField.setEditable(!isConnected);
    portField.setEditable(!isConnected);
    hostButton.setEnabled(!isConnected);
    joinButton.setEnabled(!isConnected);
    disconnectButton.setEnabled(isConnected);
    activityIndicatorLabel.setVisible(isConnected);
    if (!isConnected) {
      setEncryptionEnabled(false); // Use the centralized method to ensure clean state
    }
    joinPanel.setVisible(false);
    updateStatusLabel(statusText);
    clientCountLabel.setVisible(isConnected && isHosting);
    peersModel.clear();

    if (isConnected && isHosting) {
      peersModel.addElement(myUsername + " (Host)");
    }
    inputArea.setEnabled(isConnected);
    if (isConnected) inputArea.requestFocusInWindow();
    if (!isConnected) isHosting = false;
  }

  private void updateStatusLabel() {
    // Overload to update based on existing state
    String currentStatus = statusLabel.getText().split("  \\[")[0];
    updateStatusLabel(currentStatus);
  }

  private void updateStatusLabel(String baseStatus) {
    statusLabel.setText(baseStatus + (isEncryptionEnabled ? "  [Encrypted]" : ""));
  }

  private void updateTitle() {
    myUsername = nameField.getText();
    frame.setTitle("P2P Messenger - " + myUsername);
  }

  private void toggleJoinPanel() {
    joinPanel.setVisible(!joinPanel.isVisible());
    frame.pack();
    frame.setSize(800, 700);
  }

  private void saveSelectedFile() {
    ReceivedFile selectedFile = receivedFilesList.getSelectedValue();
    if (selectedFile == null) return;
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setSelectedFile(new File(selectedFile.getFileName()));
    if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
      File fileToSave = fileChooser.getSelectedFile();
      try {
        Files.write(fileToSave.toPath(), selectedFile.getData());
        logMessage("System", "File saved to " + fileToSave.getAbsolutePath());
      } catch (IOException ex) {
        logMessage("Error", "Could not save file: " + ex.getMessage());
        JOptionPane.showMessageDialog(
            frame, "Error saving file.", "Save Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  private void openSelectedFile() {
    ReceivedFile selectedFile = receivedFilesList.getSelectedValue();
    if (selectedFile == null) return;

    try {
      String name = selectedFile.getFileName();
      String base = name;
      String ext = "";
      int dot = name.lastIndexOf('.');
      if (dot > 0 && dot < name.length() - 1) {
        base = name.substring(0, dot);
        ext = name.substring(dot);
      }
      Path tempFile = java.nio.file.Files.createTempFile("p2p_", "_" + base + ext);
      java.nio.file.Files.write(tempFile, selectedFile.getData());
      tempFile.toFile().deleteOnExit();

      if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(tempFile.toFile());
        logMessage("System", "Opening " + name);
      } else {
        JOptionPane.showMessageDialog(
            frame,
            "Desktop operations are not supported on this platform.",
            "Open Error",
            JOptionPane.ERROR_MESSAGE);
      }
    } catch (IOException ex) {
      logMessage("Error", "Could not open file: " + ex.getMessage());
      JOptionPane.showMessageDialog(
          frame, "Error opening file.", "Open Error", JOptionPane.ERROR_MESSAGE);
    }
  }

  private void deleteSelectedFile() {
    ReceivedFile selectedFile = receivedFilesList.getSelectedValue();
    if (selectedFile != null) {
      receivedFilesModel.removeElement(selectedFile);
      logMessage("System", "Removed file from list: " + selectedFile.getFileName());
    }
  }

  private void logMessage(String user, String message) {
    SwingUtilities.invokeLater(
        () -> {
          String ts =
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
          messageArea.append(String.format("[%s] [%s]: %s\n", ts, user, message));
          messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
  }
}

// =================================================================================
// Supporting Classes (Unchanged)
// =================================================================================

class FileTransfer {
  private final String transferId;
  private final String fileName;
  private final long fileSize;
  private final File file;
  private final ByteArrayOutputStream receivedData;
  private long progress = 0;
  private volatile boolean cancelled = false;
  private final JPanel progressPanel;
  private JProgressBar progressBar;
  private JLabel progressLabel;
  private final java.util.function.Consumer<FileTransfer> onUpdate;
  private final java.util.function.Consumer<FileTransfer> onRemove;
  private final java.util.function.Consumer<String> onCancel;

  // Constructor for SENDER
  public FileTransfer(
      String transferId,
      File file,
      java.util.function.Consumer<FileTransfer> onUpdate,
      java.util.function.Consumer<FileTransfer> onRemove,
      java.util.function.Consumer<String> onCancel) {
    this.transferId = transferId;
    this.fileName = file.getName();
    this.fileSize = file.length();
    this.file = file;
    this.receivedData = null;
    this.onUpdate = onUpdate;
    this.onRemove = onRemove;
    this.onCancel = onCancel;
    this.progressPanel = createProgressPanel();
  }

  // Constructor for RECEIVER
  public FileTransfer(
      String transferId,
      String fileName,
      long fileSize,
      java.util.function.Consumer<FileTransfer> onUpdate,
      java.util.function.Consumer<FileTransfer> onRemove,
      java.util.function.Consumer<String> onCancel) {
    this.transferId = transferId;
    this.fileName = fileName;
    this.fileSize = fileSize;
    this.file = null;
    this.receivedData = new ByteArrayOutputStream();
    this.onUpdate = onUpdate;
    this.onRemove = onRemove;
    this.onCancel = onCancel;
    this.progressPanel = createProgressPanel();
  }

  private JPanel createProgressPanel() {
    JPanel panel = new JPanel(new BorderLayout(5, 0));
    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true); // show percentage text
    progressLabel = new JLabel();
    updateLabel();
    JButton cancelButton = new JButton("X");
    cancelButton.setMargin(new Insets(0, 2, 0, 2));
    cancelButton.addActionListener(e -> onCancel.accept(transferId));
    panel.add(progressLabel, BorderLayout.WEST);
    panel.add(progressBar, BorderLayout.CENTER);
    panel.add(cancelButton, BorderLayout.EAST);
    return panel;
  }

  public String getTransferId() {
    return transferId;
  }

  public File getFile() {
    return file;
  }

  public JPanel getProgressPanel() {
    return progressPanel;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public long getProgressBytes() {
    return progress;
  }

  public long getTotalBytes() {
    return fileSize;
  }

  public void addProgress(long bytes) {
    this.progress += bytes;
    if (fileSize > 0) {
      int pct = (int) Math.min(100, Math.round((double) progress * 100.0 / (double) fileSize));
      progressBar.setIndeterminate(false);
      progressBar.setValue(pct);
      progressBar.setString(pct + "%");
    } else {
      // Unknown size — switch to indeterminate
      progressBar.setIndeterminate(true);
      progressBar.setString("Receiving...");
    }
    updateLabel();
    onUpdate.accept(this);
  }

  public void appendData(byte[] data) {
    try {
      if (receivedData != null) receivedData.write(data);
    } catch (IOException e) {
      System.out.println(e.getMessage());
    }
  }

  public ReceivedFile complete() {
    onRemove.accept(this);
    return new ReceivedFile(fileName, receivedData.toByteArray());
  }

  public void cancel() {
    this.cancelled = true;
    onRemove.accept(this);
  }

  private static String human(long bytes) {
    if (bytes < 1024) return bytes + " B";
    int exp = (int) (Math.log(bytes) / Math.log(1024));
    String pre = "KMGTPE".charAt(exp - 1) + "";
    return String.format(Locale.ROOT, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
  }

  private void updateLabel() {
    if (fileSize > 0) {
      progressLabel.setText(
          String.format("%s — %s / %s", fileName, human(progress), human(fileSize)));
    } else {
      progressLabel.setText(String.format("%s — %s", fileName, human(progress)));
    }
  }
}

class ReceivedFile {
  private final String fileName;
  private final byte[] data;

  public ReceivedFile(String fileName, byte[] data) {
    this.fileName = fileName;
    this.data = data;
  }

  public String getFileName() {
    return fileName;
  }

  public byte[] getData() {
    return data;
  }

  @Override
  public String toString() {
    return fileName + " (" + data.length / 1024 + " KB)";
  }
}

class ReceivedFileRenderer extends DefaultListCellRenderer {
  @Override
  public Component getListCellRendererComponent(
      JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (value instanceof ReceivedFile) {
      setText(((ReceivedFile) value).getFileName());
      setIcon(UIManager.getIcon("FileView.fileIcon"));
    }
    return c;
  }
}

class DiscoveredHost {
  private final String name;
  private final String ip;
  private final int port;
  private long lastSeen;

  public DiscoveredHost(String name, String ip, int port) {
    this.name = name;
    this.ip = ip;
    this.port = port;
    this.lastSeen = System.currentTimeMillis();
  }

  public String getName() {
    return name;
  }

  public String getIp() {
    return ip;
  }

  public int getPort() {
    return port;
  }

  public void updateLastSeen() {
    this.lastSeen = System.currentTimeMillis();
  }

  public boolean isExpired() {
    return System.currentTimeMillis() - lastSeen > 10000;
  }

  @Override
  public String toString() {
    return name + " (" + ip + ":" + port + ")";
  }
}

class DiscoveryService {
  private final java.util.function.Consumer<DiscoveredHost> onHostDiscovered;
  private final Runnable onCheckExpired;
  private volatile boolean running = true;
  private Timer broadcastTimer;
  private final Gson gson = new Gson();
  private final Map<String, DiscoveredHost> discoveredHosts = new ConcurrentHashMap<>();

  public DiscoveryService(
      java.util.function.Consumer<DiscoveredHost> onHostDiscovered, Runnable onCheckExpired) {
    this.onHostDiscovered = onHostDiscovered;
    this.onCheckExpired = onCheckExpired;
  }

  public void startListening() {
    new Thread(
        () -> {
          try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            socket.setBroadcast(true);
            byte[] buffer = new byte[1024];
            while (running) {
              try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                JsonObject json = gson.fromJson(message, JsonObject.class);
                if (json.has("type") && "discovery".equals(json.get("type").getAsString())) {
                  String name = json.get("name").getAsString();
                  String ip = json.get("ip").getAsString();
                  int port = json.get("port").getAsInt();
                  String hostId = ip + ":" + port;
                  DiscoveredHost host =
                      discoveredHosts.computeIfAbsent(
                          hostId, k -> new DiscoveredHost(name, ip, port));
                  host.updateLastSeen();
                  onHostDiscovered.accept(host);
                }
              } catch (IOException e) {
                if (!running) break;
                System.err.println("Discovery listener error: " + e.getMessage());
              }
            }
          } catch (Exception e) {
            if (running) {
              JOptionPane.showMessageDialog(
                  null,
                  "Discovery service failed to start on port "
                      + DISCOVERY_PORT
                      + ".\nAnother app might be using it.",
                  "Discovery Error",
                  JOptionPane.ERROR_MESSAGE);
              System.out.println("Failure listening " + e.getMessage());
            }
          }
        },
        "Discovery-Listener-Thread")
        .start();

    new Timer("Discovery-Expired-Checker", true)
        .schedule(
            new TimerTask() {
              @Override
              public void run() {
                discoveredHosts.values().removeIf(DiscoveredHost::isExpired);
                onCheckExpired.run();
              }
            },
            5000,
            5000);
  }

  public void startBroadcasting(String name, String ip, int port) {
    stopBroadcasting();
    broadcastTimer = new Timer("Discovery-Broadcaster", true);
    broadcastTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            try (DatagramSocket socket = new DatagramSocket()) {
              socket.setBroadcast(true);
              JsonObject json = new JsonObject();
              json.addProperty("type", "discovery");
              json.addProperty("name", name);
              json.addProperty("ip", ip);
              json.addProperty("port", port);
              byte[] buffer = gson.toJson(json).getBytes();
              try {
                DatagramPacket packet =
                    new DatagramPacket(
                        buffer,
                        buffer.length,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT);
                socket.send(packet);
              } catch (Exception e) {
                /* ignore */
              }
            } catch (Exception e) {
              System.err.println("Discovery broadcast error: " + e.getMessage());
            }
          }
        },
        0,
        3000);
  }

  public void stopBroadcasting() {
    if (broadcastTimer != null) {
      broadcastTimer.cancel();
      broadcastTimer = null;
    }
  }

  public void stop() {
    running = false;
    stopBroadcasting();
  }
}
