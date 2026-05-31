package xxx.com.os.windows;

import com.github.frimtec.libraries.jpse.PowerShellExecutor;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * A utility class to retrieve Graphics Card (GPU) information on a Windows system by executing a
 * PowerShell command using the JPSE library.
 */
public class PowerShellFetcher {

  /**
   * A simple data class to hold the parsed information for a single GPU. (This class remains
   * unchanged)
   */
  public static class GpuInfo {
    private String name;
    private String adapterRAM;
    private String driverVersion;
    private String videoProcessor;

    // Getters and a basic toString for easy printing.
    public String getName() {
      return name;
    }

    public String getAdapterRAM() {
      return adapterRAM;
    }

    public String getDriverVersion() {
      return driverVersion;
    }

    public String getVideoProcessor() {
      return videoProcessor;
    }

    @Override
    public String toString() {
      return "GPUInfo {"
          + "\n  Name='"
          + name
          + '\''
          + ",\n  AdapterRAM='"
          + adapterRAM
          + '\''
          + ",\n  DriverVersion='"
          + driverVersion
          + '\''
          + ",\n  VideoProcessor='"
          + videoProcessor
          + '\''
          + "\n}";
    }
  }

  public static class RamInfo {
    // We will store the most common and useful attributes.
    // More can be added as needed by following the pattern.
    private String manufacturer;
    private String partNumber;
    private String serialNumber;
    private String deviceLocator; // e.g., "Controller0-DIMM0"
    private String bankLabel;     // e.g., "BANK 0"
    private long capacity;        // Stored in bytes
    private int speed;            // in MHz
    private int configuredClockSpeed;
    private int formFactor;

    // Getters for all fields
    public String getManufacturer() { return manufacturer; }
    public String getPartNumber() { return partNumber; }
    public String getSerialNumber() { return serialNumber; }
    public String getDeviceLocator() { return deviceLocator; }
    public String getBankLabel() { return bankLabel; }
    public long getCapacity() { return capacity; }
    public int getSpeed() { return speed; }
    public int getConfiguredClockSpeed() { return configuredClockSpeed; }
    public int getFormFactor() { return formFactor; }

    /**
     * Provides a formatted string for the capacity, converting bytes to Gigabytes (GB).
     * @return Capacity as a formatted string (e.g., "48.00 GB").
     */
    public String getCapacityFormatted() {
      if (capacity <= 0) {
        return "N/A";
      }
      // Using 1024^3 for GB calculation
      return String.format("%.2f GB", capacity / (1024.0 * 1024.0 * 1024.0));
    }

    @Override
    public String toString() {
      return "RamInfo {" +
          "\n  DeviceLocator='" + deviceLocator + '\'' +
          ",\n  Manufacturer='" + manufacturer + '\'' +
          ",\n  PartNumber='" + partNumber + '\'' +
          ",\n  SerialNumber='" + serialNumber + '\'' +
          ",\n  Capacity=" + getCapacityFormatted() +
          ",\n  Speed=" + speed + " MHz" +
          ",\n  ConfiguredClockSpeed=" + configuredClockSpeed + " MHz" +
          ",\n  BankLabel='" + bankLabel + '\'' +
          "\n}";
    }
  }

  /**
   * Executes a PowerShell command to get RAM details and parses the output.
   *
   * @return A List of RamInfo objects, one for each memory module found.
   */
  public static List<RamInfo> getRamInfo() {
    List<RamInfo> ramList = new ArrayList<>();
    // This command gets all available properties for each physical memory module.
    String command = "Get-CimInstance -ClassName Win32_PhysicalMemory | Format-List *";

    PowerShellExecutor executor = PowerShellExecutor.instance();

    String commandOutput = executor.execute(command).getStandardOutput();

    // The parsing logic is the same, but now we use a StringReader
    // to read from the complete output string.
    BufferedReader reader = new BufferedReader(new StringReader(commandOutput));
    String line = null;
    RamInfo currentRamModule = new RamInfo();

    Scanner scanner = new Scanner(commandOutput);

    while (scanner.hasNextLine()) {

      line = scanner.nextLine();

      if (line.trim().isEmpty()) {
        continue;
      }

      if (currentRamModule.deviceLocator != null || currentRamModule.partNumber != null) {
        ramList.add(currentRamModule);
        currentRamModule = new RamInfo();
      }

      String[] parts = line.split(":", 2);
      if (parts.length < 2) {
        continue;
      }

      String key = parts[0].trim();
      String value = parts[1].trim();

      // Skip empty values
      if (value.isEmpty()) {
        continue;
      }

      // Populate the RamInfo object based on the key
      switch (key) {
        case "Manufacturer":
          currentRamModule.manufacturer = value;
          break;
        case "PartNumber":
          currentRamModule.partNumber = value;
          break;
        case "SerialNumber":
          currentRamModule.serialNumber = value;
          break;
        case "DeviceLocator":
          currentRamModule.deviceLocator = value;
          break;
        case "BankLabel":
          currentRamModule.bankLabel = value;
          break;
        case "Capacity":
          currentRamModule.capacity = Long.parseLong(value);
          break;
        case "Speed":
          currentRamModule.speed = Integer.parseInt(value);
          break;
        case "ConfiguredClockSpeed":
          currentRamModule.configuredClockSpeed = Integer.parseInt(value);
          break;
        case "FormFactor":
          currentRamModule.formFactor = Integer.parseInt(value);
          break;
          // Add other cases here to parse more fields if needed.
      }

      // Add the last processed module if it contains data
      if (currentRamModule.deviceLocator != null || currentRamModule.partNumber != null) {
        ramList.add(currentRamModule);
      }
    }

    return ramList;
  }

  /**
   * Executes a PowerShell command using the JPSE library to get GPU details and parses the output.
   * This method is designed for Windows systems where PowerShell is available.
   *
   * @return A List of GpuInfo objects, one for each graphics card found. Returns an empty list if
   *     an error occurs or no GPUs are found.
   */
  public static List<GpuInfo> getGpuInfo() {
    List<GpuInfo> gpuList = new ArrayList<>();
    // The PowerShell command to execute (the command itself is unchanged).
    String command =
        "Get-CimInstance -ClassName Win32_VideoController | Format-List Name, AdapterRAM, DriverVersion, VideoProcessor";

    PowerShellExecutor executor = PowerShellExecutor.instance();

    String commandOutput = executor.execute(command).getStandardOutput();

    // The parsing logic is the same, but now we use a StringReader
    // to read from the complete output string.
    BufferedReader reader = new BufferedReader(new StringReader(commandOutput));
    String line;
    GpuInfo currentGpu = new GpuInfo();

    Scanner scanner = new Scanner(commandOutput);

    while (scanner.hasNextLine()) {

      line = scanner.nextLine();

      if (line.trim().isEmpty()) {
        continue;
      }

      String[] parts = line.split(":", 2);
      if (parts.length < 2) {
        continue;
      }

      String key = parts[0].trim();
      String value = parts[1].trim();

      if (key.equalsIgnoreCase("Name") && currentGpu.name != null) {
        gpuList.add(currentGpu);
        currentGpu = new GpuInfo();
      }

      switch (key) {
        case "Name":
          currentGpu.name = value;
          break;
        case "AdapterRAM":
          try {
            long ramBytes = Long.parseLong(value);
            currentGpu.adapterRAM = String.format("%.2f GB", ramBytes / (1024.0 * 1024.0 * 1024.0));
          } catch (NumberFormatException e) {
            currentGpu.adapterRAM = value;
          }
          break;
        case "DriverVersion":
          currentGpu.driverVersion = value;
          break;
        case "VideoProcessor":
          currentGpu.videoProcessor = value;
          break;
      }
    }
    if (currentGpu.name != null) {
      gpuList.add(currentGpu);
    }

    return gpuList;
  }

  /** Main method to demonstrate the usage of the getGpuInfo function. */
  public static void main(String[] args) {

    System.out.println("Fetching Physical Memory (RAM) information...");
    List<RamInfo> ramModules = getRamInfo();

    if (ramModules.isEmpty()) {
      System.out.println("No RAM modules found or an error occurred.");
    } else {
      System.out.println("Found " + ramModules.size() + " RAM module(s):");
      for (int i = 0; i < ramModules.size(); i++) {
        System.out.println("\n--- RAM Module #" + (i + 1) + " ---");
        System.out.println(ramModules.get(i));
      }
    }

    System.out.println("Fetching GPU information using JPSE library...");
    List<GpuInfo> gpus = getGpuInfo();

    if (gpus.isEmpty()) {
      System.out.println("No GPUs found or an error occurred.");
    } else {
      System.out.println("Found " + gpus.size() + " GPU(s):");
      for (int i = 0; i < gpus.size(); i++) {
        System.out.println("\n--- GPU #" + (i + 1) + " ---");
        System.out.println(gpus.get(i));
      }
    }
  }
}
