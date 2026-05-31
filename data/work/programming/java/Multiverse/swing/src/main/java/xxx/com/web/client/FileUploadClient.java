package xxx.com.web.client;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * This class demonstrates how to upload a file to a web server using a multipart/form-data POST
 * request in a Java Swing application.
 */
public class FileUploadClient extends JFrame {

  private static final String LINE_FEED = "\r\n";
  private JTextField textFieldHost;
  private JSpinner spinnerPort;
  private JTextField textFieldEndpoint;
  private JTextArea textAreaResponse;
  private JButton buttonSelectFile;
  private JButton buttonUpload;
  private JLabel labelSelectedFile;
  private JFileChooser fileChooser;
  private JProgressBar progressBar;
  private File selectedFile;

  public FileUploadClient() {
    super("Java Swing File Uploader");

    // --- UI Setup ---
    textFieldHost = new JTextField("localhost", 15);
    spinnerPort = new JSpinner(new SpinnerNumberModel(80, 1, 65535, 1));
    textFieldEndpoint = new JTextField("/upload", 15);
    textAreaResponse = new JTextArea(10, 40);
    textAreaResponse.setEditable(false);
    textAreaResponse.setLineWrap(true);
    textAreaResponse.setWrapStyleWord(true);
    buttonSelectFile = new JButton("Select File...");
    buttonSelectFile.setToolTipText("Select a file to upload");
    buttonUpload = new JButton("Upload");
    buttonUpload.setToolTipText("Upload the selected file to the server");
    buttonUpload.setEnabled(false); // Disabled until a file is selected
    labelSelectedFile = new JLabel("No file selected.");
    fileChooser = new JFileChooser();
    progressBar = new JProgressBar(0, 100);
    progressBar.setStringPainted(true);
    progressBar.setVisible(false);

    // --- Layout ---
    JPanel panelServer = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panelServer.add(new JLabel("Host:"));
    panelServer.add(textFieldHost);
    panelServer.add(new JLabel("Port:"));
    panelServer.add(spinnerPort);
    panelServer.add(new JLabel("Endpoint:"));
    panelServer.add(textFieldEndpoint);

    JPanel panelFile = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panelFile.add(buttonSelectFile);
    panelFile.add(labelSelectedFile);

    JPanel panelCenter = new JPanel();
    panelCenter.setLayout(new BoxLayout(panelCenter, BoxLayout.Y_AXIS));
    panelCenter.add(panelFile);
    panelCenter.add(Box.createVerticalStrut(10));
    panelCenter.add(buttonUpload);
    buttonUpload.setAlignmentX(Component.CENTER_ALIGNMENT);
    panelCenter.add(Box.createVerticalStrut(10));

    JPanel panelSouth = new JPanel(new BorderLayout());
    panelSouth.add(progressBar, BorderLayout.NORTH);
    panelSouth.add(new JScrollPane(textAreaResponse), BorderLayout.CENTER);

    Container contentPane = getContentPane();
    ((JComponent) contentPane).setBorder(new EmptyBorder(10, 10, 10, 10));
    contentPane.setLayout(new BorderLayout(10, 10));
    contentPane.add(panelServer, BorderLayout.NORTH);
    contentPane.add(panelCenter, BorderLayout.CENTER);
    contentPane.add(panelSouth, BorderLayout.SOUTH);

    // --- Event Handlers ---
    buttonSelectFile.addActionListener((ActionEvent e) -> handleSelectFile());
    buttonUpload.addActionListener((ActionEvent e) -> handleUpload());

    // --- Finalize Window ---
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null); // Center the window
    setVisible(true);
  }

  private void handleSelectFile() {
    int returnValue = fileChooser.showOpenDialog(this);
    if (returnValue == JFileChooser.APPROVE_OPTION) {
      selectedFile = fileChooser.getSelectedFile();
      labelSelectedFile.setText(selectedFile.getName());
      buttonUpload.setEnabled(true);
    }
  }

  private void handleUpload() {
    String host = textFieldHost.getText().trim();
    String endpoint = textFieldEndpoint.getText().trim();
    int port = (Integer) spinnerPort.getValue();

    if (host.isEmpty() || endpoint.isEmpty()) {
      JOptionPane.showMessageDialog(
          this, "Please enter host and endpoint.", "Error", JOptionPane.ERROR_MESSAGE);
      return;
    }

    String requestURL = "http://" + host + ":" + port + endpoint;

    // Disable button during upload
    buttonUpload.setEnabled(false);
    progressBar.setValue(0);
    progressBar.setVisible(true);
    textAreaResponse.setText("Uploading file...\n");

    // Use SwingWorker to perform the upload in a background thread
    UploadWorker worker = new UploadWorker(requestURL, selectedFile);
    worker.addPropertyChangeListener(evt -> {
      if ("progress".equals(evt.getPropertyName())) {
        progressBar.setValue((Integer) evt.getNewValue());
      }
    });
    worker.execute();
  }

  /** SwingWorker to handle the file upload in a background thread, so the GUI doesn't freeze. */
  private class UploadWorker extends SwingWorker<String, Void> {
    private String requestURL;
    private File uploadFile;

    public UploadWorker(String requestURL, File uploadFile) {
      this.requestURL = requestURL;
      this.uploadFile = uploadFile;
    }

    @Override
    protected String doInBackground() throws Exception {
      HttpURLConnection httpConn = null;
      OutputStream outputStream = null;
      PrintWriter writer = null;
      String charset = "UTF-8";
      String boundary = "===" + System.currentTimeMillis() + "===";

      URL url = new URL(requestURL);
      httpConn = (HttpURLConnection) url.openConnection();
      httpConn.setUseCaches(false);
      httpConn.setDoOutput(true);
      httpConn.setDoInput(true);
      httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
      httpConn.setRequestProperty("User-Agent", "Java Client");

      outputStream = httpConn.getOutputStream();
      writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);

      // Add file part
      String fileName = uploadFile.getName();
      writer.append("--" + boundary).append(LINE_FEED);
      writer
          .append("Content-Disposition: form-data; name=\"uploadedFile\"; filename=\"")
          .append(fileName)
          .append("\"")
          .append(LINE_FEED);
      writer
          .append("Content-Type: ")
          .append(URLConnection.guessContentTypeFromName(fileName))
          .append(LINE_FEED);
      writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
      writer.append(LINE_FEED);
      writer.flush();

      long totalBytes = uploadFile.length();
      long uploaded = 0;
      try (FileInputStream inputStream = new FileInputStream(uploadFile)) {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
          uploaded += bytesRead;
          setProgress((int) (uploaded * 100 / totalBytes));
        }
        outputStream.flush();
      }

      writer.append(LINE_FEED);
      writer.flush();

      // Finish request
      writer.append("--").append(boundary).append("--").append(LINE_FEED);
      writer.close();

      // Get response
      int status = httpConn.getResponseCode();
      if (status == HttpURLConnection.HTTP_OK) {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(httpConn.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            response.append(line);
          }
        }
        httpConn.disconnect();
        return "SERVER RESPONSE: " + response.toString();
      } else {
        throw new IOException("Server returned non-OK status: " + status);
      }
    }

    @Override
    protected void done() {
      progressBar.setVisible(false);
      try {
        String serverResponse = get();
        textAreaResponse.append(serverResponse);
      } catch (Exception e) {
        textAreaResponse.append("\nERROR: " + e.getMessage());
        e.printStackTrace();
      } finally {
        // Re-enable the upload button
        buttonUpload.setEnabled(true);
      }
    }
  }

  /** Main method to run the Swing application. */
  public static void main(String[] args) {
    FlatDarkLaf.setup();
    SwingUtilities.invokeLater(FileUploadClient::new);
  }
}
