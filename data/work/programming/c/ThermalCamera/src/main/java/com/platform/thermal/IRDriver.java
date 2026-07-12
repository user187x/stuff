package com.platform.thermal;

import com.sun.jna.Library;
import com.sun.jna.Native;

public class IRDriver {

  public interface ThermalNative extends Library {
    ThermalNative INSTANCE = Native.load("ir_bridge", ThermalNative.class);

    int init_thermal_camera(String devicePath);

    float get_center_temperature();

    void shutdown_thermal_camera();
  }

  public static void main(String[] args) {
    System.setProperty("jna.library.path", "./build");

    String devicePath = args.length > 0 ? args[0] : "/dev/video2";
    System.out.println("Initializing V4L2 Thermal Stream on " + devicePath + "...");

    int status = ThermalNative.INSTANCE.init_thermal_camera(devicePath);

    if (status == 1) {
      System.out.println("Camera successfully initialized.");

      // After 0.5 seconds of calibration, proceed to thermometry calculation[cite: 2].
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // We poll a few frames to clear the V4L2 buffer of pre-calibration data
      for (int i = 0; i < 5; i++) {
        ThermalNative.INSTANCE.get_center_temperature();
      }

      float centerTemp = ThermalNative.INSTANCE.get_center_temperature();
      System.out.println(String.format("Center Target Temperature: %.2f °C", centerTemp));

      ThermalNative.INSTANCE.shutdown_thermal_camera();
      System.out.println("Camera released gracefully.");
    } else {
      System.err.println("Camera initialization failed with error code: " + status);
      if (status == -1) {
        System.err.println(
            "ERROR: Permission denied. Have you added your user to the 'video' group?");
      }
    }
  }
}
