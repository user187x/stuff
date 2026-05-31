package xxx.com.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A simple SSH client that can connect to a server and execute a command.
 * This class encapsulates the basic functionality of the JSch library.
 */
public class SimpleSSHClient {

  private final String host;
  private final int port;
  private final String username;
  private final String password;
  private Session session;

  /**
   * Constructs a new SimpleSSHClient.
   *
   * @param host     The hostname or IP address of the SSH server.
   * @param port     The port number for the SSH server (usually 22).
   * @param username The username for authentication.
   * @param password The password for authentication.
   */
  public SimpleSSHClient(String host, int port, String username, String password) {
    this.host = host;
    this.port = port;
    this.username = username;
    this.password = password;
  }

  /**
   * Establishes a connection to the SSH server.
   *
   * @throws JSchException if a connection error occurs.
   */
  public void connect() throws JSchException {
    JSch jsch = new JSch();
    session = jsch.getSession(username, host, port);
    session.setPassword(password);

    // This setting avoids the need to manually confirm the host key.
    // In a production environment, you should use a known_hosts file for better security.
    session.setConfig("StrictHostKeyChecking", "no");

    System.out.println("Attempting to connect to " + host + ":" + port + "...");
    session.connect();
    System.out.println("Successfully connected to the SSH server.");
  }

  /**
   * Executes a command on the remote server and returns its output.
   *
   * @param command The command to execute.
   * @return The standard output from the executed command as a String.
   * @throws JSchException if there is a problem with the SSH channel.
   * @throws IOException   if an I/O error occurs while reading the command output.
   */
  public String executeCommand(String command) throws JSchException, IOException {
    if (session == null || !session.isConnected()) {
      throw new IllegalStateException("SSH session is not established. Please call connect() first.");
    }

    ChannelExec channel = null;
    try {
      // Open an "exec" channel
      channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(command);

      // Get input stream to read the command's output
      InputStream in = channel.getInputStream();
      InputStream err = channel.getErrStream();

      System.out.println("Executing command: " + command);
      channel.connect();

      // Read output and error streams
      String output = readStream(in);
      String error = readStream(err);

      if (!error.isEmpty()) {
        System.err.println("Error stream output:\n" + error);
      }

      int exitStatus = channel.getExitStatus();
      System.out.println("Command finished with exit status: " + exitStatus);

      return output;

    } finally {
      if (channel != null) {
        channel.disconnect();
      }
    }
  }

  /**
   * Reads all bytes from an InputStream and converts them to a string.
   *
   * @param inputStream The stream to read from.
   * @return The content of the stream as a UTF-8 string.
   * @throws IOException if an I/O error occurs.
   */
  private String readStream(InputStream inputStream) throws IOException {
    ByteArrayOutputStream result = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024];
    int length;
    while ((length = inputStream.read(buffer)) != -1) {
      result.write(buffer, 0, length);
    }
    return result.toString("UTF-8");
  }

  /**
   * Disconnects the SSH session.
   */
  public void disconnect() {
    if (session != null && session.isConnected()) {
      session.disconnect();
      System.out.println("Disconnected from the SSH server.");
    }
  }

  /**
   * Main method to demonstrate the usage of the SimpleSSHClient.
   */
  public static void main(String[] args) {
    // --- IMPORTANT ---
    // Replace these placeholder values with your actual SSH server details.
    String host = "YOUR_SERVER_IP_OR_HOSTNAME";
    String user = "YOUR_USERNAME";
    String pass = "YOUR_PASSWORD";
    int port = 22;

    // A simple check to ensure placeholders have been changed.
    if ("YOUR_SERVER_IP_OR_HOSTNAME".equals(host)) {
      System.err.println("Error: Please update the host, user, and password in the main method before running.");
      return;
    }

    SimpleSSHClient sshClient = new SimpleSSHClient(host, port, user, pass);
    try {
      sshClient.connect();

      // Example command: list files in the root directory
      String commandToExecute = "ls -la /";
      String commandOutput = sshClient.executeCommand(commandToExecute);

      System.out.println("\n--- Command Output ---");
      System.out.println(commandOutput);
      System.out.println("----------------------\n");

    } catch (JSchException | IOException e) {
      System.err.println("An error occurred during SSH operation:");
      e.printStackTrace();
    } finally {
      sshClient.disconnect();
    }
  }
}

