package xxx.claudewaveshare;

import com.fazecast.jSerialComm.SerialPort;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

/**
 * Command-line front end. Its responsibility is orchestration only: gather a
 * little setup from the user, build a {@link Radio}, then drive it from a menu.
 * All radio behaviour lives in the single-responsibility classes behind the
 * Radio facade.
 *
 * Note: this uses the conventional {@code public static void main(String[])}
 * entry point rather than the original {@code static void main()} (which relied
 * on a preview "implicit main" feature). The conventional form runs on any
 * standard JDK without preview flags.
 */
public class App {

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);

        // 1. Pick and open a port (programmatic, so a bad choice doesn't exit).
        List<String> ports = DeviceManager.listPorts();
        System.out.println("Available ports:");
        if (ports.isEmpty()) {
            System.out.println("  (none found - is the dongle plugged in?)");
        }
        for (String p : ports) System.out.println("  " + p);

        System.out.print("Port name: ");
        String portName = in.nextLine().trim();

        DeviceManager dm = new DeviceManager();
        if (!dm.open(portName)) {
            System.out.println("Could not open '" + portName + "'. Check the name and permissions (dialout group).");
            return;
        }
        SerialPort port = dm.getSerialPort();
        System.out.println("Opened " + portName + " at " + DeviceManager.BAUD + " baud.");

        // 2. Identity / band.
        int myAddr = promptInt(in, "This node address (0-65534, e.g. 1): ", 1) & 0xFFFF;
        System.out.print("Node name (for beacons): ");
        String nodeName = in.nextLine().trim();
        if (nodeName.isEmpty()) nodeName = "node-" + myAddr;

        RadioConfig.Band band = promptBand(in);

        // Received files land in ./received next to where the app is run.
        Path outputDir = Path.of("received");

        // 3. Build the radio. This starts the receive path immediately.
        Radio radio = new Radio(port, myAddr, band, nodeName, outputDir);
        radio.setLog(System.out::println);
        radio.setMessageHandler((src, text) ->
                System.out.printf("%n<< message from 0x%04X: %s%n", src, text));

        System.out.printf("Radio ready as \"%s\" (0x%04X) on %s band.%n", nodeName, myAddr, band);

        // 4. Menu loop.
        boolean run = true;
        while (run) {
            printMenu();
            String choice = in.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> doTune(in, radio);
                    case "2" -> doScan(in, radio);
                    case "3" -> doSendMessage(in, radio);
                    case "4" -> doBeacon(in, radio);
                    case "5" -> doSendFile(in, radio);
                    case "6" -> doJoinNet(in, radio);
                    case "q", "Q" -> run = false;
                    default -> System.out.println("Unknown option.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        radio.shutdown();
        dm.close();
        System.out.println("Bye.");
    }

    // --- menu actions -------------------------------------------------------

    private static void doTune(Scanner in, Radio radio) {
        System.out.print("Tune by (f)requency MHz or (c)hannel? ");
        String mode = in.nextLine().trim().toLowerCase();
        if (mode.startsWith("f")) {
            double mhz = promptDouble(in, "Frequency MHz (e.g. 868 or 915): ", 868);
            radio.setFrequency(mhz);
        } else {
            int ch = promptInt(in, "Channel (0-80): ", 18);
            radio.setChannel(ch);
        }
    }

    private static void doScan(Scanner in, Radio radio) {
        System.out.print("Scan (w)hole band or a (r)ange? ");
        String mode = in.nextLine().trim().toLowerCase();
        long dwell = (long) promptInt(in, "Dwell per channel ms (e.g. 400): ", 400);
        if (mode.startsWith("r")) {
            int lo = promptInt(in, "Start channel (0-80): ", 0);
            int hi = promptInt(in, "End channel (0-80): ", 80);
            int step = promptInt(in, "Step (>=1): ", 1);
            radio.scan(lo, hi, step, dwell);
        } else {
            radio.scanBand(dwell);
        }
    }

    private static void doSendMessage(Scanner in, Radio radio) {
        System.out.print("Destination address (blank = broadcast): ");
        String dst = in.nextLine().trim();
        System.out.print("Message: ");
        String msg = in.nextLine();
        if (dst.isEmpty()) {
            radio.sendBroadcast(msg);
        } else {
            radio.sendMessage(Integer.decode(dst) & 0xFFFF, msg);
        }
    }

    private static void doBeacon(Scanner in, Radio radio) {
        System.out.print("(s)tart, s(t)op, or (l)ist peers? ");
        String op = in.nextLine().trim().toLowerCase();
        if (op.startsWith("s")) {
            radio.startBeacon();
        } else if (op.startsWith("t")) {
            radio.stopBeacon();
        } else {
            List<Beacon.Peer> peers = radio.peers();
            if (peers.isEmpty()) {
                System.out.println("No peers heard yet.");
            } else {
                System.out.println("Peers:");
                long now = System.currentTimeMillis();
                for (Beacon.Peer p : peers) {
                    System.out.printf("  %s  (last seen %ds ago)%n",
                            p, (now - p.lastSeenMs) / 1000);
                }
            }
        }
    }

    private static void doSendFile(Scanner in, Radio radio) throws IOException {
        System.out.print("Destination address (blank = broadcast): ");
        String dst = in.nextLine().trim();
        System.out.print("Path to file: ");
        String path = in.nextLine().trim();
        File f = new File(path);
        if (!f.isFile()) {
            System.out.println("Not a file: " + path);
            return;
        }
        int dstAddr = dst.isEmpty() ? Protocol.BROADCAST_ADDR : (Integer.decode(dst) & 0xFFFF);
        radio.sendFile(dstAddr, f);
    }

    private static void doJoinNet(Scanner in, Radio radio) {
        System.out.println("Set the module's RF address + channel so matching dongles hear each other.");
        int addr = promptInt(in, "Module RF address (0-65535, 65535=listen all): ", 1);
        int ch = promptInt(in, "Channel (0-80): ", 18);
        radio.joinNet(addr, ch);
    }

    // --- prompt helpers -----------------------------------------------------

    private static void printMenu() {
        System.out.println();
        System.out.println("==== LoRa menu ====");
        System.out.println("1) Set frequency / channel");
        System.out.println("2) Scan for signals");
        System.out.println("3) Send a message");
        System.out.println("4) Beacon (start / stop / list peers)");
        System.out.println("5) Send a file");
        System.out.println("6) Join net (set module address + channel)");
        System.out.println("q) Quit");
        System.out.print("> ");
    }

    private static RadioConfig.Band promptBand(Scanner in) {
        System.out.print("Band - (H)F 850-930MHz or (L)F 410-490MHz [H]: ");
        String s = in.nextLine().trim().toLowerCase();
        return s.startsWith("l") ? RadioConfig.Band.LF : RadioConfig.Band.HF;
    }

    private static int promptInt(Scanner in, String prompt, int def) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.isEmpty()) return def;
        try { return Integer.decode(s); }
        catch (NumberFormatException e) { System.out.println("  (using default " + def + ")"); return def; }
    }

    private static double promptDouble(Scanner in, String prompt, double def) {
        System.out.print(prompt);
        String s = in.nextLine().trim();
        if (s.isEmpty()) return def;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { System.out.println("  (using default " + def + ")"); return def; }
    }
}
