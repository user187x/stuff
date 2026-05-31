package xxx.com.gpu;

import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdevice_attribute;
import jcuda.driver.JCudaDriver;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import oshi.SystemInfo;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

import static jcuda.driver.JCudaDriver.*;

public class GpuInfo {

  public static void main(String[] args) {
    // Enable exceptions for JCuda to provide more detailed error information
    JCudaDriver.setExceptionsEnabled(true);

    // Initialize the JCuda driver
    cuInit(0);

    // Get the number of CUDA-enabled devices
    int[] deviceCount = new int[1];
    cuDeviceGetCount(deviceCount);

    if (deviceCount[0] == 0) {
      System.out.println("No CUDA-enabled devices found.");
      return;
    }

    // Display the integrated video card specs
    getIntegratedGpu();

    // We will get the info for the first device (device 0)
    CUdevice device = new CUdevice();
    cuDeviceGet(device, 0);

    // Create a context for the device. We declare it final to use it in the shutdown hook.
    final CUcontext gpuContext = new CUcontext();
    cuCtxCreate(gpuContext, 0, device);

    // --- Print Static GPU Information (once at the start) ---
    System.out.println("------------------ GPU Specifications ------------------");

    // Get and print the device name
    byte[] deviceName = new byte[1024];
    cuDeviceGetName(deviceName, deviceName.length, device);
    System.out.println("Device Name: " + new String(deviceName).trim());

    // Get compute capability
    int[] major = new int[1];
    int[] minor = new int[1];
    cuDeviceGetAttribute(major, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR, device);
    cuDeviceGetAttribute(minor, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MINOR, device);
    System.out.println("Compute Capability: " + major[0] + "." + minor[0]);

    // Get and print total memory
    long[] totalMemoryArr = new long[1];
    cuMemGetInfo(new long[1], totalMemoryArr);
    // Declare as final to use it within the lambda
    final long totalMemoryMB = totalMemoryArr[0] / 1024 / 1024;
    System.out.println("Total Global Memory: " + totalMemoryMB + " MB");

    System.out.println("Total Memory Utilization: " + getMemoryInfo().utilizedMB());
    System.out.println("Total Memory Free: " + getMemoryInfo().freeMB());

    // Get clock rates
    int[] clockRate = new int[1];
    cuDeviceGetAttribute(clockRate, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_CLOCK_RATE, device);
    System.out.println("GPU Clock Rate: " + (clockRate[0] / 1000.0) + " MHz");

    int[] memClockRate = new int[1];
    cuDeviceGetAttribute(memClockRate, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_MEMORY_CLOCK_RATE, device);
    System.out.println("Memory Clock Rate: " + (memClockRate[0] / 1000.0) + " MHz");

    // Get core and architecture details
    int[] multiprocessorCount = new int[1];
    cuDeviceGetAttribute(multiprocessorCount, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT, device);
    System.out.println("Multiprocessor Count: " + multiprocessorCount[0]);

    int coresPerMP = getCoresPerMultiprocessor(major[0]);
    int totalCores = multiprocessorCount[0] * coresPerMP;
    System.out.println("CUDA Cores per MP: " + coresPerMP);
    System.out.println("Total CUDA Cores: " + totalCores);

    int[] l2CacheSize = new int[1];
    cuDeviceGetAttribute(l2CacheSize, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_L2_CACHE_SIZE, device);
    System.out.println("L2 Cache Size: " + (l2CacheSize[0] / 1024) + " KB");

    int[] warpSize = new int[1];
    cuDeviceGetAttribute(warpSize, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_WARP_SIZE, device);
    System.out.println("Warp Size: " + warpSize[0] + " Threads");

    int[] maxThreadsPerBlock = new int[1];
    cuDeviceGetAttribute(maxThreadsPerBlock, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_MAX_THREADS_PER_BLOCK, device);
    System.out.println("Max Threads per Block: " + maxThreadsPerBlock[0]);

    System.out.println("------------------------------------------------------");
    System.out.println("\n--- Live Memory Monitor (Press Ctrl+C to stop) ---\n");

    // --- Live Updating with an ExecutorService ---

    // Create a single-threaded scheduled executor
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Define the monitoring task as a Runnable
    Runnable memoryMonitorTask = () -> {
      // Get current memory info
      long[] currentFreeMemory = new long[1];
      long[] currentTotalMemory = new long[1];
      cuMemGetInfo(currentFreeMemory, currentTotalMemory);

      // Calculate utilized memory in MB
      long utilizedMemoryMB = (currentTotalMemory[0] - currentFreeMemory[0]) / 1024 / 1024;
      long freeMemoryMB = currentFreeMemory[0] / 1024 / 1024;

      // Format the output string. Padding ensures the previous line is fully overwritten.
      String output = String.format(
          "Utilized: %5d MB / %5d MB   |   Free: %5d MB                  ",
          utilizedMemoryMB,
          totalMemoryMB,
          freeMemoryMB
      );

      // Print with a carriage return to move the cursor to the start of the line
      System.out.print(output + "\r");
      System.out.flush();
    };

    // Schedule the task to run every 500ms with no initial delay
    executor.scheduleAtFixedRate(memoryMonitorTask, 0, 500, TimeUnit.MILLISECONDS);

    // Add a shutdown hook to gracefully clean up resources when the JVM terminates
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("\nShutting down executor and cleaning up resources...");
      executor.shutdownNow(); // Immediately stop the scheduled task
      cuCtxDestroy(gpuContext);
      System.out.println("GPU context destroyed. Exiting.");
    }));
  }

  public static void getIntegratedGpu(){

    SystemInfo si = new SystemInfo();
    HardwareAbstractionLayer hal = si.getHardware();
    List<GraphicsCard> graphicsCards = hal.getGraphicsCards();

    for (GraphicsCard card : graphicsCards) {
      // You can use conditional logic here to identify integrated GPUs
      // For example, by checking if the name contains "Intel HD Graphics"
      // or by checking the vendor information.
      if (card.getName().contains("Intel HD Graphics") || card.getVendor().contains("Intel")) {
        System.out.println("Integrated GPU Details:");
        System.out.println("  Name: " + card.getName());
        System.out.println("  Vendor: " + card.getVendor());
        System.out.println("  Device ID: " + card.getDeviceId());
        System.out.println("  Driver Version: " + card.getVersionInfo());
        System.out.println("  VRAM: " + card.getVRam()); // Video RAM
      }
    }
  }

  /**
   * Helper function to determine the number of CUDA cores per multiprocessor
   * based on the major compute capability of the GPU.
   *
   * @param major The major compute capability version.
   * @return The number of cores per multiprocessor.
   */
  private static int getCoresPerMultiprocessor(int major) {
    return switch (major) {
      case 1 -> 8;    // Tesla
      case 2 -> 48;   // Fermi
      case 3 -> 192;  // Kepler
      case 5 -> 128;  // Maxwell
      case 6 -> 64;   // Pascal
      case 7 -> 64;   // Volta / Turing
      case 8 -> 128;  // Ampere
      case 9 -> 128;  // Hopper
      default -> 0;   // Unknown or future architecture
    };
  }

  /**
   * A data-carrier record to hold a snapshot of GPU memory information.
   *
   * @param totalMB Total memory in megabytes.
   * @param freeMB Free memory in megabytes.
   * @param utilizedMB Utilized memory in megabytes.
   */
  public record GpuMemoryInfo(long totalMB, long freeMB, long utilizedMB) {}

  /**
   * Gets a snapshot of the current GPU memory usage.
   *
   * @return A GpuMemoryInfo object with total, free, and utilized memory in MB.
   */
  private static GpuMemoryInfo getMemoryInfo() {
    long[] freeMemory = new long[1];
    long[] totalMemory = new long[1];
    cuMemGetInfo(freeMemory, totalMemory);

    long totalMB = totalMemory[0] / 1024 / 1024;
    long freeMB = freeMemory[0] / 1024 / 1024;
    long utilizedMB = totalMB - freeMB;

    return new GpuMemoryInfo(totalMB, freeMB, utilizedMB);
  }

  /**
   * Calculates the total number of CUDA cores on the GPU.
   *
   * @param device The CUdevice to query.
   * @return The total number of CUDA cores.
   */
  private static int getTotalCores(CUdevice device) {
    int[] major = new int[1];
    cuDeviceGetAttribute(major, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_COMPUTE_CAPABILITY_MAJOR, device);

    int[] multiprocessorCount = new int[1];
    cuDeviceGetAttribute(multiprocessorCount, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_MULTIPROCESSOR_COUNT, device);

    int coresPerMP = getCoresPerMultiprocessor(major[0]);
    return multiprocessorCount[0] * coresPerMP;
  }

  /**
   * A helper method to get the GPU device name as a clean String.
   *
   * @param device The CUdevice to query.
   * @return The name of the device.
   */
  private static String getGpuName(CUdevice device) {
    byte[] deviceNameBytes = new byte[1024]; // A buffer large enough for any device name
    cuDeviceGetName(deviceNameBytes, deviceNameBytes.length, device);

    // C-style strings are null-terminated. We find the first null byte (0)
    // to determine the actual length of the name.
    int len = 0;
    while (len < deviceNameBytes.length && deviceNameBytes[len] != 0) {
      len++;
    }

    // Create a String using only the bytes that are part of the name.
    return new String(deviceNameBytes, 0, len);
  }

  /**
   * Gets the total global memory available on the GPU.
   *
   * @return Total memory in megabytes (MB).
   */
  private static long getTotalMemoryMB() {
    long[] totalMemory = new long[1];
    // cuMemGetInfo returns free and total memory in bytes. We only need total here.
    cuMemGetInfo(new long[1], totalMemory);
    return totalMemory[0] / 1024 / 1024;
  }


  /**
   * Gets the core clock rate of the GPU.
   *
   * @param device The CUdevice to query.
   * @return The core clock rate in megahertz (MHz).
   */
  private static int getClockRateMHz(CUdevice device) {
    int[] clockRate = new int[1];
    cuDeviceGetAttribute(clockRate, CUdevice_attribute.CU_DEVICE_ATTRIBUTE_CLOCK_RATE, device);
    // The attribute is returned in kilohertz, so we convert to megahertz.
    return clockRate[0] / 1000;
  }
}
