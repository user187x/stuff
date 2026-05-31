package xxx.com.python.runtime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class PythonDetector {

  /**
   * Detects the Python version and installation path on a Windows system.
   *
   * @return A String array where the first element is the Python version and the second is the
   *     installation path, or null if Python is not found or an error occurs.
   */
  public static String[] detectPythonInfo() throws IOException, InterruptedException {

    String path = getPythonPath();
    String version = getPythonVersion();
    boolean isSetup = checkPythonEnvironment(path);

    return new String[] {path, version, Boolean.toString(isSetup)};
  }

  public static String getPythonVersion() throws IOException, InterruptedException {

    String pythonVersion = null;

    ProcessBuilder versionPb = new ProcessBuilder("cmd.exe", "/c", "python --version");
    Process versionProcess = versionPb.start();
    BufferedReader versionReader =
        new BufferedReader(new InputStreamReader(versionProcess.getInputStream()));
    String line;
    while ((line = versionReader.readLine()) != null) {
      if (line.startsWith("Python ")) {
        pythonVersion = line.substring("Python ".length()).trim();
        break;
      }
    }
    versionProcess.waitFor();

    return pythonVersion;
  }

  public static String getPythonPath() throws IOException, InterruptedException {

    String pythonPath = null;

    ProcessBuilder pathPb = new ProcessBuilder("cmd.exe", "/c", "where python");
    Process pathProcess = pathPb.start();
    BufferedReader pathReader =
        new BufferedReader(new InputStreamReader(pathProcess.getInputStream()));
    String line;
    while ((line = pathReader.readLine()) != null) {
      // The 'where' command often returns multiple paths if multiple Python installations exist.
      // We'll take the first one found as a common case.
      if (line.toLowerCase().endsWith("python.exe")) {
        pythonPath = line.trim();
        break;
      }
    }
    pathProcess.waitFor(); // Wait for the process to complete

    return pythonPath;
  }

  public static boolean checkPythonEnvironment(String path) throws IOException {

    boolean environmentSetup = false;

    // Check if Python is in PATH (basic environment setup check)
    ProcessBuilder pathPb = new ProcessBuilder("cmd.exe", "/c", "echo %PATH%");
    Process pathProcess = pathPb.start();

    BufferedReader readerPath =
        new BufferedReader(new InputStreamReader(pathProcess.getInputStream()));

    String pathVariable = readerPath.readLine();
    if (pathVariable != null
        && path != null
        && pathVariable.contains(path.substring(0, path.lastIndexOf("\\")))) {
      environmentSetup = true;
    }
    readerPath.close();

    return environmentSetup;
  }

  public static void main(String[] args) throws Exception {

    String[] pythonInfo = detectPythonInfo();
    System.out.println("Python Version: " + pythonInfo[0]);
    System.out.println("Python Path: " + pythonInfo[1]);
    System.out.println("Python SetUp: " + pythonInfo[2]);
  }
}
