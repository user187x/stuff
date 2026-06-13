//package xxx;
//
//// Removed Android & AndroidX imports
//// import android.content.Context;
//// import androidx.test.core.app.ApplicationProvider;
//// import androidx.test.platform.app.InstrumentationRegistry;
//// import androidx.test.runner.AndroidJUnit4;
//// import android.util.Log;
//// import android.os.Process;
//
//import java.util.logging.Logger;
//import java.util.logging.Level;
//
//// Note: You will need to substitute these with your desktop USB abstraction
//// (e.g., usb4java, jSerialComm) or custom stubs if porting the hoho library.
//import android.hardware.usb.UsbDevice;
//import android.hardware.usb.UsbDeviceConnection;
//import android.hardware.usb.UsbManager;
//
//import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
//import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
//import com.hoho.android.usbserial.driver.ChromeCcdSerialDriver;
//import com.hoho.android.usbserial.driver.CommonUsbSerialPort;
//import com.hoho.android.usbserial.driver.CommonUsbSerialPortWrapper;
//import com.hoho.android.usbserial.driver.Cp21xxSerialDriver;
//import com.hoho.android.usbserial.driver.FtdiSerialDriver;
//import com.hoho.android.usbserial.driver.GsmModemSerialDriver;
//import com.hoho.android.usbserial.driver.ProbeTable;
//import com.hoho.android.usbserial.driver.ProlificSerialDriver;
//import com.hoho.android.usbserial.driver.ProlificSerialPortWrapper;
//import com.hoho.android.usbserial.driver.SerialTimeoutException;
//import com.hoho.android.usbserial.driver.UsbSerialDriver;
//import com.hoho.android.usbserial.driver.UsbSerialPort;
//import com.hoho.android.usbserial.driver.UsbSerialProber;
//import com.hoho.android.usbserial.util.SerialInputOutputManager;
//import com.hoho.android.usbserial.util.TelnetWrapper;
//import com.hoho.android.usbserial.util.TestBuffer;
//import com.hoho.android.usbserial.util.UsbWrapper;
//import com.hoho.android.usbserial.driver.UsbSerialPort.ControlLine;
//
//import org.junit.After;
//import org.junit.AfterClass;
//import org.junit.Assert;
//import org.junit.Assume;
//import org.junit.Before;
//import org.junit.BeforeClass;
//import org.junit.Rule;
//import org.junit.Test;
//import org.junit.rules.TestRule;
//import org.junit.rules.TestWatcher;
//import org.junit.runner.Description;
//
//import java.io.IOException;
//import java.nio.BufferOverflowException;
//import java.util.Arrays;
//import java.util.EnumSet;
//import java.util.List;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.ScheduledFuture;
//import java.util.concurrent.TimeUnit;
//
//import static org.hamcrest.CoreMatchers.anyOf;
//import static org.hamcrest.CoreMatchers.equalTo;
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertFalse;
//import static org.junit.Assert.assertNotEquals;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertNull;
//import static org.junit.Assert.assertThat;
//import static org.junit.Assert.assertThrows;
//import static org.junit.Assert.assertTrue;
//import static org.junit.Assert.fail;
//
//public class DeviceTest {
//    private final static Logger logger = Logger.getLogger(DeviceTest.class.getName());
//
//    // Configuration via System properties instead of Android InstrumentationRegistry
//    private static String  rfc2217_server_host;
//    private static int     rfc2217_server_port = 2217;
//    private static boolean rfc2217_server_nonstandard_baudrates;
//    private static String  test_device_driver;
//    private static int     test_device_port;
//
//    private UsbManager usbManager;
//    UsbWrapper usb;
//    static TelnetWrapper telnet;
//
//    @Rule
//    public TestRule watcher = new TestWatcher() {
//        protected void starting(Description description) {
//            logger.info("===== starting test: " + description.getMethodName() + " =====");
//        }
//    };
//
//    @BeforeClass
//    public static void setUpFixture() throws Exception {
//        // Read properties from JVM arguments: -Drfc2217_server_host=...
//        rfc2217_server_host                  = System.getProperty("rfc2217_server_host");
//        rfc2217_server_nonstandard_baudrates = Boolean.parseBoolean(System.getProperty("rfc2217_server_nonstandard_baudrates", "false"));
//        test_device_driver                   = System.getProperty("test_device_driver");
//        test_device_port                     = Integer.parseInt(System.getProperty("test_device_port", "0"));
//
//        telnet = new TelnetWrapper(rfc2217_server_host, rfc2217_server_port);
//    }
//
//    @Before
//    public void setUp() throws Exception {
//        telnet.setUp();
//
//        // NOTE: Replace with your desktop USB provider abstraction
//        // context = ApplicationProvider.getApplicationContext();
//        // usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
//        usbManager = DesktopUsbContext.getUsbManager(); // Placeholder for desktop USB manager
//
//        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
//        if(availableDrivers.isEmpty()) {
//            ProbeTable customTable = new ProbeTable();
//            customTable.addProduct(0x2342, 0x8036, CdcAcmSerialDriver.class);
//            availableDrivers = new UsbSerialProber(customTable).findAllDrivers(usbManager);
//        }
//        assertEquals("no USB device found", 1, availableDrivers.size());
//        UsbSerialDriver usbSerialDriver = availableDrivers.get(0);
//
//        if(test_device_driver != null) {
//            String driverName = usbSerialDriver.getClass().getSimpleName();
//            assertEquals(test_device_driver + "SerialDriver", driverName);
//        }
//
//        assertTrue(usbSerialDriver.getPorts().size() > test_device_port);
//
//        // Context is removed from UsbWrapper construction for pure Java
//        usb = new UsbWrapper(usbSerialDriver, test_device_port);
//        usb.setUp();
//
//        logger.info("Using USB device " + usb.serialPort.toString() + " driver=" + usb.serialDriver.getClass().getSimpleName());
//        telnet.read(-1);
//    }
//
//    @After
//    public void tearDown() throws IOException {
//        if(usb != null)
//            usb.tearDown();
//        telnet.tearDown();
//    }
//
//    @AfterClass
//    public static void tearDownFixture() throws Exception {
//        telnet.tearDownFixture();
//    }
//
//    private static int indexOfDifference(final CharSequence cs1, final CharSequence cs2) {
//        return indexOfDifference(cs1, cs2, 0, 0);
//    }
//
//    private static int indexOfDifference(final CharSequence cs1, final CharSequence cs2, int cs1startpos, int cs2startpos) {
//        if (cs1 == cs2) return -1;
//        if (cs1 == null || cs2 == null) return 0;
//        if (cs1startpos < 0 || cs2startpos < 0) return -1;
//
//        int i, j;
//        for (i = cs1startpos, j = cs2startpos; i < cs1.length() && j < cs2.length(); ++i, ++j) {
//            if (cs1.charAt(i) != cs2.charAt(j)) {
//                break;
//            }
//        }
//        if (j < cs2.length() || i < cs1.length()) {
//            return i;
//        }
//        return -1;
//    }
//
//    private int findDifference(final StringBuilder data, final StringBuilder expected) {
//        int length = 0;
//        int datapos = indexOfDifference(data, expected);
//        int expectedpos = datapos;
//        while(datapos != -1) {
//            int nextexpectedpos = -1;
//            int nextdatapos = datapos + 2;
//            int len = -1;
//            if(nextdatapos + 10 < data.length()) {
//                String nextsub = data.substring(nextdatapos, nextdatapos + 10);
//                nextexpectedpos = expected.indexOf(nextsub, expectedpos);
//                if(nextexpectedpos >= 0) {
//                    len = nextexpectedpos - expectedpos - 2;
//                }
//            }
//            logger.info("difference at " + datapos + " len " + len);
//            logger.fine("        got " + data.substring(Math.max(datapos - 20, 0), Math.min(datapos + 20, data.length())));
//            logger.fine("   expected " + expected.substring(Math.max(expectedpos - 20, 0), Math.min(expectedpos + 20, expected.length())));
//            datapos = indexOfDifference(data, expected, nextdatapos, nextexpectedpos);
//            expectedpos = nextexpectedpos + (datapos  - nextdatapos);
//            if(len == -1) length = -1;
//            else length += len;
//        }
//        return length;
//    }
//
//    private void doReadWrite(String reason) throws Exception {
//        doReadWrite(reason, -1);
//    }
//
//    private void doReadWrite(String reason, int readWait) throws Exception {
//        byte[] buf1 = new byte[]{ 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x55, 0x55};
//        byte[] buf2 = new byte[]{ 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x55, 0x55};
//        byte[] data;
//
//        telnet.write(buf1);
//        data = usb.read(buf1.length, -1, readWait);
//        assertThat(reason, data, equalTo(buf1));
//
//        usb.write(buf2);
//        data = telnet.read(buf2.length, readWait);
//        assertThat(reason, data, equalTo(buf2));
//    }
//
//    private void purgeWriteBuffer(int timeout) throws Exception {
//        try {
//            logger.fine(" purge begin");
//            usb.serialPort.purgeHwBuffers(true, false);
//        } catch(UnsupportedOperationException ignored) {}
//
//        byte[] data = telnet.read(-1, timeout);
//        int len = 0;
//        while(data.length != 0) {
//            len += data.length;
//            logger.fine(" purge read " + data.length);
//            data = telnet.read(-1, timeout);
//        }
//        logger.fine(" purge end " + len);
//    }
//
//    @Test
//    public void openClose() throws Exception {
//        usb.open();
//        telnet.setParameters(19200, 8, 1, UsbSerialPort.PARITY_NONE);
//        usb.setParameters(19200, 8, 1, UsbSerialPort.PARITY_NONE);
//        doReadWrite("");
//
//        try {
//            usb.serialPort.open(usb.deviceConnection);
//            fail("already open expected");
//        } catch (IOException ignored) {}
//
//        doReadWrite("");
//        usb.close();
//
//        try {
//            usb.serialPort.close();
//            fail("already closed expected");
//        } catch (IOException ignored) {}
//
//        try {
//            usb.write(new byte[]{0x00});
//            fail("write closed expected");
//        } catch(IOException ex) {
//            assertEquals("Connection closed", ex.getMessage());
//        }
//
//        try {
//            usb.read(1);
//            fail("read closed expected");
//        } catch(IOException ex) {
//            assertEquals("Connection closed", ex.getMessage());
//        }
//
//        try {
//            usb.setParameters(9600, 8, 1, UsbSerialPort.PARITY_NONE);
//            fail("error expected");
//        } catch (IOException | NullPointerException ignored) {}
//
//        usb.open();
//        telnet.setParameters(9600, 8, 1, UsbSerialPort.PARITY_NONE);
//        usb.setParameters(9600, 8, 1, UsbSerialPort.PARITY_NONE);
//        doReadWrite("");
//
//        assertEquals(SerialInputOutputManager.State.RUNNING, usb.ioManager.getState());
//        usb.serialPort.close();
//
//        for (int i = 0; i < 1000; i++) {
//            if (usb.ioManager.getState() == SerialInputOutputManager.State.STOPPED)
//                break;
//            Thread.sleep(1);
//        }
//
//        try {
//            usb.read();
//            fail("closed expected");
//        } catch (IOException ex) {
//            assertEquals("java.io.IOException: Connection closed", ex.getMessage());
//        }
//
//        if(SerialInputOutputManager.State.STOPPED != usb.ioManager.getState())
//            usb.ioManager = null;
//        usb.close();
//
//        class CloseRunnable implements Runnable {
//            boolean wait;
//            public void run() {
//                try {
//                    while(wait) Thread.sleep(1);
//                    Thread.sleep(5);
//                } catch (InterruptedException ignored) {}
//                logger.fine("close");
//                usb.close();
//            }
//        }
//
//        usb.open(EnumSet.of(UsbWrapper.OpenCloseFlags.NO_IOMANAGER_THREAD));
//        CloseRunnable closer = new CloseRunnable();
//        closer.wait = true;
//        Thread th = new Thread(closer);
//        th.start();
//
//        try {
//            closer.wait = false;
//            usb.serialPort.read(new byte[256], 2000);
//            fail("closed expected");
//        } catch(IOException ex) {
//            assertEquals("Connection closed", ex.getMessage());
//        }
//        th.join();
//
//        closer.wait = true;
//        th = new Thread(closer);
//        th.start();
//
//        try {
//            closer.wait = false;
//            usb.serialPort.read(new byte[256], 0);
//            fail("closed expected");
//        } catch(IOException ex) {
//            assertEquals("Connection closed", ex.getMessage());
//        }
//        th.join();
//    }
//
//    // ... [Truncated for brevity, standard string/buffer tests carry over identically] ...
//    // Note: Ensure when porting to desktop you copy over prolificBaudRate, ftdiBaudRate,
//    // Ch34xBaudRate, dataBits, parity, stopBits, etc. directly. They rely strictly on
//    // hoho library objects which should translate 1:1 if you have desktop mocks.
//
//    @Test
//    public void IoManager() throws Exception {
//        SerialInputOutputManager.DEBUG = true;
//        usb.open(EnumSet.of(UsbWrapper.OpenCloseFlags.NO_IOMANAGER_THREAD));
//        assertNull(usb.ioManager);
//        usb.ioManager = new SerialInputOutputManager(usb.serialPort);
//        assertNull(usb.ioManager.getListener());
//        usb.ioManager.setListener(usb);
//        assertEquals(usb, usb.ioManager.getListener());
//        usb.ioManager = new SerialInputOutputManager(usb.serialPort, usb);
//        assertEquals(usb, usb.ioManager.getListener());
//
//        assertEquals(0, usb.ioManager.getWriteTimeout());
//        usb.ioManager.setWriteTimeout(11);
//        assertEquals(11, usb.ioManager.getWriteTimeout());
//
//        assertEquals(usb.serialPort.getReadEndpoint().getMaxPacketSize(), usb.ioManager.getReadBufferSize());
//        usb.ioManager.setReadBufferSize(12);
//        assertEquals(12, usb.ioManager.getReadBufferSize());
//        assertEquals(4096, usb.ioManager.getWriteBufferSize());
//        usb.ioManager.setWriteBufferSize(13);
//        assertEquals(13, usb.ioManager.getWriteBufferSize());
//
//        usb.ioManager.setReadBufferSize(usb.ioManager.getReadBufferSize());
//        usb.ioManager.setWriteBufferSize(usb.ioManager.getWriteBufferSize());
//        usb.ioManager.setWriteTimeout(usb.ioManager.getWriteTimeout());
//        usb.close();
//
//        usb.open(EnumSet.of(UsbWrapper.OpenCloseFlags.NO_IOMANAGER_START));
//        usb.setParameters(19200, 8, 1, UsbSerialPort.PARITY_NONE);
//        telnet.setParameters(19200, 8, 1, UsbSerialPort.PARITY_NONE);
//
//        // Converted from Process.THREAD_PRIORITY_DEFAULT to Standard Thread Priority
//        usb.ioManager.setThreadPriority(Thread.NORM_PRIORITY);
//        usb.ioManager.start();
//        usb.waitForIoManagerStarted();
//        assertTrue("iomanager thread", usb.hasIoManagerThread());
//
//        try {
//            usb.ioManager.start();
//            fail("already running error expected");
//        } catch (IllegalStateException ignored) {}
//
//        try {
//            // Converted from Process.THREAD_PRIORITY_LOWEST
//            usb.ioManager.setThreadPriority(Thread.MIN_PRIORITY);
//            fail("setThreadPriority IllegalStateException expected");
//        } catch (IllegalStateException ignored) {}
//
//        usb.ioManager.setWriteTimeout(21);
//        assertEquals(21, usb.ioManager.getWriteTimeout());
//        usb.ioManager.setReadBufferSize(22);
//        assertEquals(22, usb.ioManager.getReadBufferSize());
//        usb.ioManager.setWriteBufferSize(23);
//        assertEquals(23, usb.ioManager.getWriteBufferSize());
//
//        // readbuffer resize
//        telnet.write(new byte[1]);
//        usb.ioManager.setReadBufferSize(64);
//        logger.fine("setReadBufferSize(64)");
//        telnet.write(new byte[1]);
//        telnet.write(new byte[1]);
//        usb.read(3);
//
//        // writebuffer resize
//        try {
//            usb.ioManager.writeAsync(new byte[8192]);
//            fail("expected BufferOverflowException");
//        } catch (BufferOverflowException ignored) {}
//
//        usb.ioManager.setWriteBufferSize(16);
//        usb.ioManager.writeAsync("1234567890AB".getBytes());
//        try {
//            usb.ioManager.setWriteBufferSize(8);
//            fail("expected BufferOverflowException");
//        } catch (BufferOverflowException ignored) {}
//
//        usb.ioManager.setWriteBufferSize(24);
//        telnet.write("a".getBytes());
//        assertThat(usb.read(1), equalTo("a".getBytes()));
//        assertThat(telnet.read(12), equalTo("1234567890AB".getBytes()));
//
//        usb.ioManager.setReadBufferSize(8);
//        logger.fine("setReadBufferSize(8)");
//        telnet.write("b".getBytes());
//        assertThat(usb.read(1), equalTo("b".getBytes()));
//        telnet.write("c".getBytes());
//        assertThat(usb.read(1), equalTo("c".getBytes()));
//        telnet.write("d".getBytes());
//        assertThat(usb.read(1), equalTo("d".getBytes()));
//
//        usb.close();
//        for (int i = 0; i < 100 && usb.hasIoManagerThread(); i++) {
//            Thread.sleep(1);
//        }
//        assertFalse("iomanager thread", usb.hasIoManagerThread());
//        SerialInputOutputManager.DEBUG = false;
//
//        // legacy start
//        usb.open(EnumSet.of(UsbWrapper.OpenCloseFlags.NO_IOMANAGER_START));
//        usb.setParameters(19200, 8, 1, UsbSerialPort.PARITY_NONE);
//        telnet.setParameters(19200, 8, 1, UsbSerialPort.PARITY_NONE);
//        usb.ioManager.setThreadPriority(Thread.NORM_PRIORITY); // Thread.NORM_PRIORITY
//        usb.waitForIoManagerStarted();
//        try {
//            usb.ioManager.start();
//            fail("already running error expected");
//        } catch (IllegalStateException ignored) {}
//    }
//}