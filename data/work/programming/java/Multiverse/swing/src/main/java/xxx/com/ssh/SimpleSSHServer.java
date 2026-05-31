package xxx.com.ssh;

import com.github.lalyos.jfiglet.FigletFont;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.command.CommandFactory;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.InteractiveProcessShellFactory;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;

/** A simple SSH server using Apache SSHD. Connect : ssh user@localhost -p 8222 */
public class SimpleSSHServer {

  public interface DatabaseProvider {

    public boolean userExists(String user);

    public String getPassword(String user);
  }

  private int port = 2222;
  private DatabaseProvider databaseProvider = null;

  public SimpleSSHServer() {}

  public SimpleSSHServer(int port, DatabaseProvider databaseProvider) throws IOException {
    this.databaseProvider = databaseProvider;
    this.port = port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public DatabaseProvider getDatabaseProvider() {
    return databaseProvider;
  }

  public void setDatabaseProvider(DatabaseProvider databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  public void start() throws IOException {

    // Make sure the port is already being used
    if (!isPortAvailable(getPort())) {

      System.err.println("Port is already used " + getPort());
      System.exit(1);
    }

    // 1. Create an SshServer instance
    SshServer sshd = SshServer.setUpDefaultServer();

    // 2. Configure the server
    sshd.setPort(port);

    // 3. Set a host key provider. This is used to identify your server.
    Path serverKey = File.createTempFile("serverKey", ".ser").toPath();
    KeyPairProvider keyPairProvider = new SimpleGeneratorHostKeyProvider(serverKey);

    sshd.setKeyPairProvider(keyPairProvider);

    String welcomeBanner = FigletFont.convertOneLine("SSH SERVER");
    CoreModuleProperties.WELCOME_BANNER.set(sshd, welcomeBanner);

    sshd.setPasswordAuthenticator(
        (username, password, session) -> {
          System.out.println("Authentication Request for : " + username);
          boolean exists = databaseProvider.userExists(username);

          if (!exists) {
            System.out.println("Authentication failure for : " + username + ". No such user");
            return false;
          }

          if (!databaseProvider.getPassword(username).equals(password)) {
            System.out.println("Authentication failure for : " + username + ". Incorrect password");
            return false;
          }

          System.out.println("Authentication Success : " + username);
          return true;
        });

    // Set custom factories that wrap built-in ones for logging
    sshd.setShellFactory(new LoggingShellFactory());
    sshd.setCommandFactory(new LoggingCommandFactory());

    try {

      sshd.start();
      System.out.println("SSH Server started on port " + getPort());

      Thread.sleep(Long.MAX_VALUE);

    } catch (Exception e) {

      System.err.println("Error starting SSH server:" + e.getMessage());
    } finally {
      if (sshd.isStarted()) {

        try {
          sshd.stop();
          System.out.println("SSH Server terminated.");
        } catch (IOException e) {
          System.out.println("Error stopping SSH server:" + e.getMessage());
        }
      }
    }
  }

  /**
   * Custom ShellFactory that logs interactive shell start and wraps the command for stream logging.
   */
  public static class LoggingShellFactory implements org.apache.sshd.server.shell.ShellFactory {
    private final InteractiveProcessShellFactory delegate = new InteractiveProcessShellFactory();

    @Override
    public Command createShell(ChannelSession channel) throws IOException {

      String user = channel.getServerSession().getUsername();
      SocketAddress remoteAddr = channel.getSession().getClientAddress();

      String formattedAddr = formatAddress(remoteAddr);
      String sessionId = user + "@" + formattedAddr;

      System.out.println("User " + user + " started interactive shell from " + formattedAddr);

      // Adjust PTY modes to ensure proper echo and erase handling
      Environment env = channel.getEnvironment();
      Map<PtyMode, Integer> ptyModes = env.getPtyModes();
      if (ptyModes != null) {
        ptyModes.put(PtyMode.ECHO, 0);
        ptyModes.put(PtyMode.VERASE, 127);
      }

      Command innerCommand = delegate.createShell(channel);

      return new LoggingWrapperCommand(innerCommand, sessionId);
    }
  }

  /** Custom CommandFactory that logs exec commands and wraps the command for stream logging. */
  public static class LoggingCommandFactory implements CommandFactory {
    private final ProcessShellCommandFactory delegate = ProcessShellCommandFactory.INSTANCE;

    @Override
    public Command createCommand(ChannelSession channel, String command) throws IOException {

      if (StringUtils.isBlank(command)) command = StringUtils.EMPTY;

      String user = channel.getServerSession().getUsername();
      SocketAddress remoteAddr = channel.getSession().getClientAddress();

      String formattedAddr = formatAddress(remoteAddr);
      String sessionId = user + "@" + formattedAddr;

      System.out.println(user + "@" + formattedAddr + " >> " + command);

      Command innerCommand = delegate.createCommand(channel, command);

      return new LoggingWrapperCommand(innerCommand, sessionId);
    }
  }

  /** Formats SocketAddress to a standard "host:port" string, converting IPv6 loopback to IPv4. */
  private static String formatAddress(SocketAddress addr) {

    if (!(addr instanceof InetSocketAddress isa)) {
      return addr.toString();
    }

    InetAddress ia = isa.getAddress();

    if (ia == null) {
      return isa.getHostString() + ":" + isa.getPort();
    }

    String host = ia.getHostAddress();

    if (ia.isLoopbackAddress() && host.contains(":")) {
      host = "127.0.0.1";
    }

    return host + ":" + isa.getPort();
  }

  /** Wrapper Command that delegates to an inner command but wraps streams for logging. */
  public static class LoggingWrapperCommand implements Command {

    private final Command inner;
    private final String sessionId;

    public LoggingWrapperCommand(Command inner, String sessionId) {
      this.inner = inner;
      this.sessionId = sessionId;
    }

    @Override
    public void setInputStream(InputStream in) {
      InputStream wrappedIn = new LoggingInputStream(in, sessionId + " [CLIENT]");
      inner.setInputStream(wrappedIn);
    }

    @Override
    public void setOutputStream(OutputStream out) {
      OutputStream wrappedOut = new LoggingOutputStream(out, sessionId + " [STDOUT]");
      inner.setOutputStream(wrappedOut);
    }

    @Override
    public void setErrorStream(OutputStream err) {
      // Do not wrap stderr to avoid interfering with terminal behavior
      inner.setErrorStream(err);
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
      inner.setExitCallback(callback);
    }

    @Override
    public void start(ChannelSession channelSession, Environment env) throws IOException {
      inner.start(channelSession, env);
    }

    @Override
    public void destroy(ChannelSession channelSession) throws Exception {
      inner.destroy(channelSession);
    }
  }

  /** Logs read bytes from input streams (e.g., client keystrokes). */
  public static class LoggingInputStream extends FilterInputStream {

    private final String prefix;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public LoggingInputStream(InputStream in, String prefix) {
      super(in);
      this.prefix = prefix;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {

      int bytesRead = super.read(b, off, len);

      if (bytesRead > 0) {
        buffer.write(b, off, bytesRead);
        // Check for newline without creating string
        boolean hasNewline = false;
        for (int i = 0; i < bytesRead; i++) {
          if (b[off + i] == '\n' || b[off + i] == '\r') {
            hasNewline = true;
            break;
          }
        }
        if (hasNewline) {
          String line = buffer.toString(StandardCharsets.UTF_8).trim();
          if (!line.isEmpty()) {
            System.out.println(prefix + ": " + line);
          }
          buffer.reset();
        }
      }
      return bytesRead;
    }

    @Override
    public void close() throws IOException {

      try {

        byte[] remainingBytes = buffer.toByteArray();

        if (remainingBytes.length > 0) {
          String remaining = new String(remainingBytes, StandardCharsets.UTF_8).trim();
          if (!remaining.isEmpty()) {
            System.out.println(prefix + ": " + remaining);
          }
        }
      } finally {
        super.close();
      }
    }
  }

  /** Logs written bytes to output streams (e.g., shell responses). */
  public static class LoggingOutputStream extends FilterOutputStream {

    private final String prefix;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public LoggingOutputStream(OutputStream out, String prefix) {
      super(out);
      this.prefix = prefix;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {

      // Check for newline without creating string
      boolean hasNewline = false;
      for (int i = 0; i < len; i++) {
        if (b[off + i] == '\n' || b[off + i] == '\r') {
          hasNewline = true;
          break;
        }
      }

      if (!hasNewline) {
        // Fast path: no string creation
        buffer.write(b, off, len);
        super.write(b, off, len);
        return;
      }

      // Slow path: has newline, create strings
      String chunkStr = new String(b, off, len, StandardCharsets.UTF_8);
      String fullBufferStr = buffer.toString(StandardCharsets.UTF_8) + chunkStr;
      String[] lines = fullBufferStr.split("[\r\n]+");
      for (int i = 0; i < lines.length - 1; i++) {
        String line = lines[i].trim();
        if (!line.isEmpty()) {
          System.out.println(prefix + ": " + line);
        }
      }
      // Keep last incomplete line in buffer
      if (lines.length > 0) {
        buffer.reset();
        buffer.write(lines[lines.length - 1].getBytes(StandardCharsets.UTF_8));
      }
      super.write(b, off, len);
    }

    @Override
    public void close() throws IOException {

      try {

        byte[] remainingBytes = buffer.toByteArray();

        if (remainingBytes.length > 0) {

          String remaining = new String(remainingBytes, StandardCharsets.UTF_8).trim();
          if (!remaining.isEmpty()) {
            System.out.println(prefix + ": " + remaining);
          }
        }
      } finally {
        super.close();
      }
    }
  }

  public static boolean isPortAvailable(int port) {

    try (ServerSocket socket = new ServerSocket(port)) {

      socket.setReuseAddress(true);
      return true;
    } catch (BindException e) {

      System.err.println("Port " + port + " is already bound.");
      return false;
    } catch (IOException e) {

      System.err.println("Error checking port " + port + ": " + e.getMessage());
      return false;
    }
  }

  public static void main(String[] args) throws IOException {

    DatabaseProvider databaseProvider =
        new DatabaseProvider() {

          @Override
          public boolean userExists(String user) {
            return true;
          }

          @Override
          public String getPassword(String user) {
            return "pass";
          }
        };

    new SimpleSSHServer(8222, databaseProvider).start();
  }
}