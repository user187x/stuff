// File: StableDiffusion.java
package xxx.com.python.diffusion;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import xxx.com.jvmargs.JvmUtil;

public class StableDiffusion {

  private static final String VENV_DIR = ".venv";
  private static final String PYTHON_SCRIPT_NAME = "image_generator.py";

  public static void main(String[] args) throws Exception {

    JvmUtil.enforceJvmArgs(
        StableDiffusion.class, StableDiffusion::invokePython, args, HashSet::new);
  }

  public static void invokePython() {

    Path workingDirectory = Paths.get(System.getProperty("user.dir"));
    File venv = new File(workingDirectory.resolve(VENV_DIR).toString());

    Path pythonScriptInSandbox = null;

    try {

      URL resourceUrl =
          StableDiffusion.class.getClassLoader().getResource("python/" + PYTHON_SCRIPT_NAME);
      Path pythonResourceScript = Paths.get(Objects.requireNonNull(resourceUrl).toURI());

      pythonScriptInSandbox = workingDirectory.resolve(PYTHON_SCRIPT_NAME);
      Files.copy(pythonResourceScript, pythonScriptInSandbox, StandardCopyOption.REPLACE_EXISTING);

    } catch (Exception e) {

      System.err.println("Unable to load python script in " + PYTHON_SCRIPT_NAME);
      System.exit(1);
    }

    File pythonScriptFile = pythonScriptInSandbox.toFile();
    pythonScriptFile.deleteOnExit(); // Clean up the script file when JVM exits.

    System.out.println("Checking for virtual environment...");
    if (!venv.exists()) {
      System.out.println("Virtual environment not found. Creating...");
      try {
        runCommand("python3", "-m", "venv", venv.getAbsolutePath());
        System.out.println("Virtual environment created at: " + venv.getAbsolutePath());
      } catch (IOException | InterruptedException e) {
        System.err.println("Failed to create Python virtual environment: " + e.getMessage());
        return;
      }
    } else {
      System.out.println("Virtual environment already exists.");
    }

    String os = System.getProperty("os.name").toLowerCase();
    String pipExecutable;
    String pythonExecutable;

    if (os.contains("win")) {
      pipExecutable =
          venv.getAbsolutePath() + File.separator + "Scripts" + File.separator + "pip.exe";
      pythonExecutable =
          venv.getAbsolutePath() + File.separator + "Scripts" + File.separator + "python.exe";
    } else {
      pipExecutable = venv.getAbsolutePath() + File.separator + "bin" + File.separator + "pip";
      pythonExecutable =
          venv.getAbsolutePath() + File.separator + "bin" + File.separator + "python";
    }

    try {

      System.out.println("Upgrading pip and setuptools...");
      runCommand(pythonExecutable, "-m", "pip", "install", "--upgrade", "pip", "setuptools");
      System.out.println("Installing required packages...");
      runCommand(pipExecutable, "install", "torch", "diffusers", "transformers", "accelerate");
      System.out.println("Dependencies installed successfully.");
    } catch (IOException | InterruptedException e) {

      System.err.println("Failed to install Python dependencies: " + e.getMessage());
      return;
    }

    try (Context pythonContext =
        Context.newBuilder("python")
            .allowAllAccess(true)
            .option("python.Executable", pythonExecutable)
            .option("python.ForceImportSite", "true")
            .currentWorkingDirectory(workingDirectory) // Use the string path of the directory
            .build()) {

      System.out.println("Loading and running the image generation script...");
      pythonContext.eval(Source.newBuilder("python", pythonScriptFile).build());
      Value generateImageFunc = pythonContext.getBindings("python").getMember("generate_image");

      if (generateImageFunc == null || !generateImageFunc.canExecute()) {
        System.err.println("Error: Could not find 'generate_image' function in the script.");
        return;
      }

      String prompt = "A photorealistic painting of a cat wearing a wizard hat";
      String outputImagePath = "generated_image.png";
      System.out.println("Generating image with prompt: '" + prompt + "'");

      Value result = generateImageFunc.execute(prompt, outputImagePath);

      // --- FIX: Check if the result is a string before using it ---
      if (result != null && result.isString()) {
        String imagePath = result.asString();
        System.out.println("----------------------------------------------------");
        System.out.println("Image generation complete!");
        System.out.println("Image saved to: " + new File(imagePath).getAbsolutePath());
        System.out.println("----------------------------------------------------");
      } else {
        System.out.println("----------------------------------------------------");
        System.out.println(
            "Image generation function executed, but did not return the expected path.");
        System.out.println(
            "Image should be saved at: " + new File(outputImagePath).getAbsolutePath());
        System.out.println("----------------------------------------------------");
      }

    } catch (PolyglotException | IOException e) {
      System.err.println("An error occurred while running the Python script: " + e.getMessage());
    }
  }

  private static void runCommand(String... command) throws IOException, InterruptedException {

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.inheritIO();
    Process process = pb.start();

    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new IOException("Command `" + String.join(" ", command) + "` exited with code " + exitCode);
    }
  }
}
