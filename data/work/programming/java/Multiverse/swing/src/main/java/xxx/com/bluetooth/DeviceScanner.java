//package xxx.com.bluetooth;
//
//import org.sputnikdev.bluetooth.URL;
//import org.sputnikdev.bluetooth.manager.DeviceGovernor;
//import org.sputnikdev.bluetooth.manager.DiscoveredDevice;
//import org.sputnikdev.bluetooth.manager.impl.BluetoothManagerBuilder;
//
//import java.util.Map;
//import java.util.concurrent.TimeUnit;
//
//public class BluetoothScanner {
//  public static void main(String[] args) throws Exception {
//    BluetoothManagerBuilder builder = new BluetoothManagerBuilder()
//        .withTinyBTransport(true); // Enable TinyB transport for BlueZ compatibility
//
//    builder.build();
//
//    // Add discovery listener to capture devices and their RSSI
//    builder.getDiscoveryGovernor().addListener(new DiscoveryListener() {
//      @Override
//      public void deviceDiscovered(DiscoveredDevice device) {
//        URL url = device.getURL();
//        String name = device.getName() != null ? device.getName() : "Unknown";
//        int rssi = device.getRSSI();
//        System.out.println("Discovered - Name: " + name + ", Address: " + url.getDeviceAddress() + ", RSSI: " + rssi + " dBm");
//      }
//
//      @Override
//      public void deviceUpdated(DiscoveredDevice device) {
//        // Handle updates (e.g., RSSI changes)
//        URL url = device.getURL();
//        String name = device.getName() != null ? device.getName() : "Unknown";
//        int rssi = device.getRSSI();
//        System.out.println("Updated - Name: " + name + ", Address: " + url.getDeviceAddress() + ", RSSI: " + rssi + " dBm");
//      }
//
//      @Override
//      public void deviceLost(DiscoveredDevice device) {
//        System.out.println("Lost - Address: " + device.getURL().getDeviceAddress());
//      }
//    });
//
//    // Start discovery
//    builder.getDiscoveryGovernor().startDiscovery();
//
//    // Add shutdown hook to stop discovery
//    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//      builder.getDiscoveryGovernor().stopDiscovery();
//      System.out.println("Discovery stopped");
//    }));
//
//    System.out.println("Scanning for Bluetooth devices... (Press Ctrl+C to stop)");
//
//    // Continual polling: Print summary of known devices every 5 seconds
//    while (true) {
//      Map<URL, DeviceGovernor> devices = builder.getDeviceGovernors();
//      System.out.println("\nCurrently known devices (" + devices.size() + "):");
//      for (Map.Entry<URL, DeviceGovernor> entry : devices.entrySet()) {
//        URL url = entry.getKey();
//        DeviceGovernor governor = entry.getValue();
//        String name = governor.getName() != null ? governor.getName() : "Unknown";
//        int rssi = governor.getRSSI();
//        System.out.println("Name: " + name + ", Address: " + url.getDeviceAddress() + ", RSSI: " + rssi + " dBm");
//      }
//      System.out.println("-------------------");
//      TimeUnit.SECONDS.sleep(5);
//    }
//  }
//}
