package xxx.beta.audio;

import com.fazecast.jSerialComm.SerialPort;

import javax.sound.sampled.*;
// Updated package from previous step
import xxx.beta.aprs.APRSPacket;
import xxx.beta.aprs.APRSTypes;
import xxx.beta.aprs.Digipeater;
import xxx.beta.aprs.InformationField;
import xxx.beta.aprs.MessagePacket;
import xxx.beta.aprs.Parser;
import xxx.beta.aprs.Position;
import xxx.beta.aprs.PositionField;
import com.vagell.kv4pht.data.ChannelMemory;
import com.vagell.kv4pht.javAX25.ax25.Afsk1200Modulator;
import com.vagell.kv4pht.javAX25.ax25.Afsk1200MultiDemodulator;
import com.vagell.kv4pht.javAX25.ax25.Arrays;
import com.vagell.kv4pht.javAX25.ax25.Packet;
import com.vagell.kv4pht.javAX25.ax25.PacketDemodulator;
import com.vagell.kv4pht.javAX25.ax25.PacketHandler;
import com.vagell.kv4pht.javAX25.ax25.PacketModulator;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import xxx.beta.usb.SerialInputOutputManager;


import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background controller that manages the connection to the ESP32 (to control the radio), and
 * handles playing back any audio received from the radio on the desktop environment.
 */
public class RadioAudioController implements PacketHandler {

    private static final Logger LOGGER = Logger.getLogger(RadioAudioController.class.getName());
    private static final String FIRMWARE_TAG = "firmware";
    private static final int RUNAWAY_TX_TIMEOUT_SEC = 180;

    // === USB Device Matching ===
    private static final int[] ESP32_VENDOR_IDS = {4292, 6790};
    private static final int[] ESP32_PRODUCT_IDS = {60000, 29987};

    // === Audio Constants ===
    public static final int AUDIO_SAMPLE_RATE = 48000;
    public static final int OPUS_FRAME_SIZE = 1920; // 40ms at 48kHz
    private static final int RX_AUDIO_MIN_BUFFER_SIZE = 8192;

    // === APRS Constants ===
    public static final int APRS_POSITION_EXACT = 0;
    public static final int APRS_POSITION_APPROX = 1;
    public static final int APRS_BEACON_MINS = 5;
    private static final int APRS_MAX_MESSAGE_NUM = 99999;
    private static final int MS_SILENCE_BEFORE_DATA_MS = 1100;
    private static final int MS_SILENCE_AFTER_DATA_MS = 700;
    public static final List<Digipeater> DEFAULT_DIGIPEATERS = List.of(new Digipeater("WIDE1*"), new Digipeater("WIDE2-1"));

    // === Frequency Ranges ===
    private static final float VHF_MIN_FREQ = 134.0f;
    private static final float VHF_MAX_FREQ = 174.0f;
    private static final float UHF_MIN_FREQ = 400.0f;
    private static final float UHF_MAX_FREQ = 480.0f;

    // These will be overwritten by user settings
    @Setter private float min2mTxFreq = 144.0f;
    @Setter private float max2mTxFreq = 148.0f;
    @Setter private float min70cmTxFreq = 420.0f;
    @Setter private float max70cmTxFreq = 450.0f;

    @Getter private float minRadioFreq = VHF_MIN_FREQ;
    @Getter private float maxRadioFreq = VHF_MAX_FREQ;
    @Setter private float minTxFreq = min2mTxFreq;
    @Setter private float maxTxFreq = max2mTxFreq;

    public enum RadioModuleType {UNKNOWN, VHF, UHF}

    // === Audio / Opus Handling ===
    private final float[] pcmFloat = new float[OPUS_FRAME_SIZE];
    private SourceDataLine audioTrack;
    private FloatControl volumeControl;
    private float targetVolume = 0.0f;
    private float currentVolume = 0.0f;

    private final OpusUtils.OpusDecoderWrapper opusDecoder =
            new OpusUtils.OpusDecoderWrapper(AUDIO_SAMPLE_RATE, OPUS_FRAME_SIZE);
    private final OpusUtils.OpusEncoderWrapper opusEncoder =
            new OpusUtils.OpusEncoderWrapper(AUDIO_SAMPLE_RATE, OPUS_FRAME_SIZE);

    // === USB / Serial ===
    @Getter private SerialPort serialPort;
    private SerialInputOutputManager usbIoManager;
    @Getter private Protocol.Sender hostToEsp32;
    private final FrameParser esp32DataStreamParser = new FrameParser(this::handleParsedCommand);
    private int usbConnectAttemptSeq = 0;
    private int activeUsbConnectAttemptId = 0;

    // === AFSK Modem ===
    private final PacketModulator afskModulator = new Afsk1200Modulator(AUDIO_SAMPLE_RATE);
    private final PacketDemodulator afskDemodulator = new Afsk1200MultiDemodulator(AUDIO_SAMPLE_RATE, this);

    // === APRS State ===
    private boolean aprsBeaconPosition = false;
    @Getter @Setter private int aprsPositionAccuracy = APRS_POSITION_EXACT;
    private ScheduledExecutorService beaconScheduler;
    private ScheduledFuture<?> beaconFuture;
    private int messageNumber = 0;

    // === Radio State ===
    @Getter private @NonNull RadioMode mode = RadioMode.STARTUP;
    @Getter @Setter private boolean hasHighLowPowerSwitch = false;
    @Getter @Setter private boolean hasPhysPttButton = false;
    @Getter private boolean isHighPower = true;
    @Getter private boolean isRssiOn = true;
    @Getter private boolean txAllowed = true;
    @Setter private int squelch = 0;
    @Setter
    private @NonNull String callsign = "";
    @Getter private @NonNull String activeFrequencyStr = "";
    @Getter
    private RadioModuleType radioType = RadioModuleType.UNKNOWN;
    private int activeMemoryId = -1;
    private int consecutiveSilenceBytes = 0;
    private com.vagell.kv4pht.radio.MicGainBoost micGainBoost = com.vagell.kv4pht.radio.MicGainBoost.NONE;
    @Setter private @NonNull String bandwidth = "25kHz";

    // === Desktop Components ===
    private static final RadioAudioServiceCallbacks NO_OP_CALLBACKS = new RadioAudioServiceCallbacks() {};
    @Setter @Getter private @NonNull RadioAudioServiceCallbacks callbacks = NO_OP_CALLBACKS;
    private final ProtocolHandshake protocolHandshake = new ProtocolHandshake(this);

    // Concurrency replacements for Android Handlers
    private final ScheduledExecutorService mainHandler = Executors.newSingleThreadScheduledExecutor();
    private static final long CONNECT_RETRY_PERIOD_MS = 500L;
    private final com.vagell.kv4pht.radio.ConnectionController connectionController =
            new com.vagell.kv4pht.radio.ConnectionController(mainHandler, CONNECT_RETRY_PERIOD_MS, this::isConnectionReady, this::attemptUsbConnect);
    private boolean radioMissingNotified = false;
    private ScheduledFuture<?> txTimeoutFuture;
    private List<ChannelMemory> channelMemories = new ArrayList<>();

    // === Scan Timing ===
    private static final float SEC_BETWEEN_SCANS = 0.5f;

    public interface RadioAudioServiceCallbacks {
        default void radioMissing() {}
        default void radioConnected() {}
        default void radioModuleHandshake() {}
        default void radioModuleNotFound() {}
        default void audioTrackCreated() {}
        default void packetReceived(APRSPacket aprsPacket) {}
        default void scannedToMemory(int memoryId) {}
        default void outdatedFirmware(int firmwareVer) {}
        default void firmwareVersionReceived(int firmwareVer) {}
        default void missingFirmware() {}
        default void txStarted() {}
        default void txEnded() {}
        default void chatError(String text) {}
        default void sMeterUpdate(int value) {}
        default void aprsBeaconing(boolean beaconing, int accuracy) {}
        default void sentAprsBeacon(double latitude, double longitude) {}
        default void unknownLocation() {}
        default void forceTunedToFreq(String newFreqStr) {}
        default void forcedPttStart() {}
        default void forcedPttEnd() {}
        default void setRadioType(RadioModuleType ratioType) {}
        default void showNotification(String title, String message) {}
        // Replaces Google Play Services location fetch
        default void requestLocation(LocationCallback callback) { callback.onLocationReceived(null); }
    }

    public interface LocationCallback {
        void onLocationReceived(Position position);
    }

    public RadioAudioController(String callsign, int squelch, int activeMemoryId, String activeFrequencyStr) {
        this.callsign = callsign != null ? callsign : "";
        this.squelch = squelch;
        this.activeMemoryId = activeMemoryId;
        this.activeFrequencyStr = activeFrequencyStr != null ? activeFrequencyStr : "";

        SecureRandom random = new SecureRandom();
        messageNumber = random.nextInt(APRS_MAX_MESSAGE_NUM);
    }

    public void start() {
        initAudioTrack();
        connectionController.start();
    }

    public void stop() {
        tryToStopRadioModule();
        connectionController.stop();
        protocolHandshake.onDestroy();

        if (this.beaconScheduler != null && !beaconScheduler.isShutdown()) {
            beaconScheduler.shutdownNow();
        }
        mainHandler.shutdownNow();

        if (usbIoManager != null) {
            usbIoManager.stop();
            usbIoManager = null;
        }
        if (serialPort != null) {
            serialPort.closePort();
            serialPort = null;
        }
        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.close();
            audioTrack = null;
        }
    }

    public void setChannelMemories(List<ChannelMemory> memories) {
        this.channelMemories = memories != null ? memories : new ArrayList<>();
    }

    public void setFilters(boolean emphasis, boolean highpass, boolean lowpass) {
        hostToEsp32.filters(Filters.builder().high(highpass).low(lowpass).pre(emphasis).build());
    }

    public void setMicGainBoost(String micGainBoost) {
        this.micGainBoost = MicGainBoost.parse(micGainBoost);
    }

    public void setMinRadioFreq(float newMinFreq) {
        minRadioFreq = newMinFreq;
        if (mode != RadioMode.STARTUP && Float.parseFloat(activeFrequencyStr) < minRadioFreq) {
            tuneToFreq(String.format(java.util.Locale.US, "%.4f", min70cmTxFreq), squelch, true);
            callbacks.forceTunedToFreq(activeFrequencyStr);
        }
    }

    public void setMaxRadioFreq(float newMaxFreq) {
        maxRadioFreq = newMaxFreq;
        if (mode != RadioMode.STARTUP && Float.parseFloat(activeFrequencyStr) > maxRadioFreq) {
            tuneToFreq(String.format(java.util.Locale.US, "%.4f", min2mTxFreq), squelch, true);
            callbacks.forceTunedToFreq(activeFrequencyStr);
        }
    }

    public void setAprsBeaconPosition(boolean enabled) {
        if (this.aprsBeaconPosition != enabled) {
            this.aprsBeaconPosition = enabled;
            if (enabled) {
                startBeaconScheduler();
            } else if (beaconFuture != null) {
                stopBeaconScheduler();
            }
        }
    }

    public boolean getAprsBeaconPosition() { return this.aprsBeaconPosition; }

    private void startBeaconScheduler() {
        if (beaconScheduler == null || beaconScheduler.isShutdown()) {
            beaconScheduler = Executors.newSingleThreadScheduledExecutor();
        }
        if (beaconFuture != null) beaconFuture.cancel(false);

        beaconFuture = beaconScheduler.scheduleAtFixedRate(() -> {
            try {
                if (aprsBeaconPosition) sendPositionBeacon();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Beacon task error", t);
            }
        }, 0, APRS_BEACON_MINS, TimeUnit.MINUTES);
    }

    private void stopBeaconScheduler() {
        if (beaconFuture != null) {
            beaconFuture.cancel(false);
            beaconFuture = null;
        }
        if (beaconScheduler != null) {
            beaconScheduler.shutdownNow();
            beaconScheduler = null;
        }
    }

    public void setMode(RadioMode mode) {
        if (mode == RadioMode.FLASHING) {
            hostToEsp32.stop();
            audioTrack.stop();
            usbIoManager.stop();
            try {
                serialPort.setDTR();
                serialPort.clearRTS();
                Thread.sleep(100);
                serialPort.clearDTR();
                serialPort.setRTS();
                Thread.sleep(50);
                serialPort.setDTR();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        this.mode = mode;
    }

    public void setActiveMemoryId(int activeMemoryId) {
        this.activeMemoryId = activeMemoryId;
        if (activeMemoryId > -1) {
            tuneToMemory(activeMemoryId, squelch, false);
        } else {
            tuneToFreq(activeFrequencyStr, squelch, false);
        }
    }

    private void tryToStopRadioModule() {
        if (isConnectionReady() && (mode == RadioMode.RX || mode == RadioMode.TX || mode == RadioMode.SCAN)) {
            try {
                LOGGER.info("Sending stop to ESP32...");
                hostToEsp32.stop();
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isTxAllowed(float freq) {
        final float halfBandwidth = (bandwidth.equals("25kHz") ? 0.025f : 0.0125f) / 2;
        return (freq >= (minTxFreq + halfBandwidth)) && (freq <= (maxTxFreq - halfBandwidth));
    }

    public void tuneToFreq(String frequencyStr, int squelchLevel, boolean forceTune) {
        if (mode == RadioMode.STARTUP) return;
        setMode(RadioMode.RX);
        if (!forceTune && activeFrequencyStr.equals(frequencyStr) && squelch == squelchLevel) return;

        float freq;
        try {
            freq = Float.parseFloat(makeSafeHamFreq(frequencyStr));
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid frequency string: " + frequencyStr, e);
            return;
        }
        activeFrequencyStr = frequencyStr;
        activeMemoryId = -1;
        squelch = squelchLevel;

        if (isRadioConnected()) {
            hostToEsp32.group(Group.builder()
                    .freqTx(freq).freqRx(freq)
                    .bw((bandwidth.equals("25kHz") ? DRA818_25K : DRA818_12K5))
                    .squelch((byte) squelchLevel).build());
        }
        txAllowed = isTxAllowed(freq);
    }

    public String makeSafeHamFreq(String strFreq) {
        try {
            float freq = Float.parseFloat(strFreq);
            while (freq > 500.0f) freq /= 10;
            return formatFreq(Math.max(minRadioFreq, Math.min(freq, maxRadioFreq)));
        } catch (NumberFormatException e) {
            return formatFreq(minTxFreq);
        }
    }

    private static String formatFreq(float freq) {
        return String.format(java.util.Locale.US, "%.4f", freq);
    }

    public String validateFrequency(String tempFrequency) {
        return makeSafeHamFreq(tempFrequency);
    }

    public void tuneToMemory(int memoryId, int squelchLevel, boolean forceTune) {
        if (forceTune || activeMemoryId != memoryId || squelch != squelchLevel) {
            channelMemories.stream()
                    .filter(channelMemory -> channelMemory.memoryId == memoryId)
                    .findFirst()
                    .ifPresent(channelMemory -> tuneToMemory(channelMemory, squelchLevel, forceTune));
        }
    }

    public void tuneToMemory(ChannelMemory memory, int squelchLevel, boolean forceTune) {
        if (memory == null || (!forceTune && activeMemoryId == memory.memoryId && squelch == squelchLevel) || mode == RadioMode.STARTUP) return;

        activeFrequencyStr = validateFrequency(memory.frequency);
        activeMemoryId = memory.memoryId;
        final float txFreq = Float.parseFloat(getTxFreq(memory.frequency, memory.offset, memory.offsetKhz));

        if (isRadioConnected()) {
            hostToEsp32.group(Group.builder()
                    .freqTx(txFreq).freqRx(Float.parseFloat(makeSafeHamFreq(activeFrequencyStr)))
                    .bw(bandwidth.equals("25kHz") ? DRA818_25K : DRA818_12K5)
                    .squelch((byte) squelchLevel)
                    .ctcssRx((byte) Math.max(0, ToneHelper.getToneIndex(memory.rxTone)))
                    .ctcssTx((byte) Math.max(0, ToneHelper.getToneIndex(memory.txTone)))
                    .build());
        }
        txAllowed = isTxAllowed(txFreq);
    }

    private String getTxFreq(String txFreq, int offset, int khz) {
        if (offset == ChannelMemory.OFFSET_NONE) return txFreq;
        float freqFloat = Float.parseFloat(txFreq);
        if (offset == ChannelMemory.OFFSET_UP) freqFloat += (khz / 1000f);
        else if (offset == ChannelMemory.OFFSET_DOWN) freqFloat -= (khz / 1000f);
        return makeSafeHamFreq(Float.toString(freqFloat));
    }

    private void checkScanDueToSilence() {
        if (consecutiveSilenceBytes >= (AUDIO_SAMPLE_RATE * SEC_BETWEEN_SCANS)) {
            consecutiveSilenceBytes = 0;
            nextScan();
        }
    }

    private void initAudioTrack() {
        if (audioTrack != null) {
            audioTrack.close();
            audioTrack = null;
        }

        try {
            AudioFormat format = new AudioFormat(AUDIO_SAMPLE_RATE, 16, 1, true, false); // 48kHz, 16bit, mono, signed, little-endian
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            audioTrack = (SourceDataLine) AudioSystem.getLine(info);
            audioTrack.open(format, RX_AUDIO_MIN_BUFFER_SIZE);
            audioTrack.start();

            if (audioTrack.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                volumeControl = (FloatControl) audioTrack.getControl(FloatControl.Type.MASTER_GAIN);
                targetVolume = 0.0f;
                setVolumeReal(0.0f);
            }
            callbacks.audioTrackCreated();
        } catch (LineUnavailableException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize desktop Java Sound line.", e);
        }
    }

    private void setVolumeReal(float linearVolume) {
        if (volumeControl == null) return;
        // Convert linear (0.0 to 1.0) to Decibels
        float dB = (float) (Math.log10(Math.max(0.0001, linearVolume)) * 20.0);
        dB = Math.max(volumeControl.getMinimum(), Math.min(dB, volumeControl.getMaximum()));
        volumeControl.setValue(dB);
    }

    private void setTxRunAwayTimer() {
        if (txTimeoutFuture != null) txTimeoutFuture.cancel(false);
        txTimeoutFuture = mainHandler.schedule(() -> {
            if (RadioMode.TX.equals(getMode())) {
                LOGGER.info("Warning: runaway TX timeout reached, PTT stopped.");
                endPtt();
            }
        }, RUNAWAY_TX_TIMEOUT_SEC, TimeUnit.SECONDS);
    }

    public void startPtt() {
        if (hostToEsp32 == null) {
            LOGGER.severe("Attempted to start PTT but hostToEsp32 is null. USB connection likely failed.");
            radioMissing();
            return;
        }
        if (mode == RadioMode.RX && txAllowed) {
            setMode(RadioMode.TX);
            callbacks.sMeterUpdate(0);
            setTxRunAwayTimer();
            hostToEsp32.pttDown();
            targetVolume = 0.0f;
            setVolumeReal(0.0f);
            callbacks.txStarted();
        } else {
            LOGGER.warning("Attempted to start PTT when not allowed");
        }
    }

    public void endPtt() {
        if (mode == RadioMode.TX) {
            setMode(RadioMode.RX);
            targetVolume = 0.0f;
            setVolumeReal(0.0f);
            hostToEsp32.pttUp();
            callbacks.txEnded();
        }
    }

    public void reconnectViaUSB() {
        LOGGER.info(connectLog("reconnectViaUSB(): clearing pending state for next attempt"));
        radioMissingNotified = false;
        connectionController.markAttemptFinished();
    }

    public void renegotiateAfterFlashing() {
        LOGGER.info(connectLog("renegotiateAfterFlashing(): closing port and resetting state before renegotiation"));
        closePortAndReset();
        reconnectViaUSB();
    }

    private boolean isConnectionReady() {
        return hostToEsp32 != null && serialPort != null && usbIoManager != null;
    }

    private void closePortAndReset() {
        hostToEsp32 = null;
        if (usbIoManager != null) {
            try { usbIoManager.stop(); } catch (Exception ignored) {}
            usbIoManager = null;
        }
        if (serialPort != null) {
            try { serialPort.closePort(); } catch (Exception ignored) {}
            serialPort = null;
        }
    }

    private void attemptUsbConnect() {
        activeUsbConnectAttemptId = ++usbConnectAttemptSeq;
        if (isConnectionReady()) {
            connectionController.markAttemptFinished();
            return;
        }
        setMode(RadioMode.STARTUP);
        setRadioType(RadioModuleType.UNKNOWN);

        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            if (isESP32Device(port)) {
                setupSerialConnection(port);
                return;
            }
        }
        radioMissing();
    }

    private boolean isESP32Device(SerialPort port) {
        // jSerialComm maps VID/PID if available. On some platforms it might be hidden in getDescriptivePortName()
        int vendorId = port.getVendorID();
        int productId = port.getProductID();
        for (int i = 0; i < ESP32_VENDOR_IDS.length; i++) {
            if (vendorId == ESP32_VENDOR_IDS[i] && productId == ESP32_PRODUCT_IDS[i]) return true;
        }
        return false;
    }

    public void setupSerialConnection(SerialPort port) {
        serialPort = port;
        serialPort.setComPortParameters(115200, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);

        if (!serialPort.openPort()) {
            closePortAndReset();
            radioMissing();
            return;
        }

        serialPort.clearRTS(); // Equivalent to setRTS(true) in Android lib depending on inversion
        serialPort.clearDTR();

        usbIoManager = new SerialInputOutputManager(serialPort, new SerialInputOutputManager.Listener() {
            @Override public void onNewData(byte[] data) { esp32DataStreamParser.processBytes(data); }
            @Override public void onRunError(Exception e) {
                if (audioTrack != null) audioTrack.stop();
                closePortAndReset();
                radioMissing();
            }
        });

        usbIoManager.setWriteBufferSize(90000);
        usbIoManager.setReadBufferSize(1024);
        usbIoManager.start();

        hostToEsp32 = new Protocol.Sender(usbIoManager);
        protocolHandshake.start();
    }

    public void radioConnected() {
        connectionController.markAttemptFinished();
        radioMissingNotified = false;
        callbacks.radioConnected();
    }

    private void radioMissing() {
        connectionController.markAttemptFinished();
        closePortAndReset();
        if (!radioMissingNotified) {
            radioMissingNotified = true;
            callbacks.radioMissing();
        }
    }

    void onHandshakeCompleted() { connectionController.markAttemptFinished(); }
    int getActiveUsbConnectAttemptId() { return activeUsbConnectAttemptId; }
    private String connectLog(String message) { return "connect#" + activeUsbConnectAttemptId + " " + message; }

    public void setRadioType(RadioModuleType radioType) {
        callbacks.setRadioType(radioType);
        if (!Objects.equals(this.radioType, radioType)) {
            this.radioType = radioType;
            updateFrequencyLimitsForBand();
        }
    }

    public void updateFrequencyLimitsForBand() {
        if (RadioModuleType.VHF.equals(getRadioType())) {
            setMinRadioFreq(VHF_MIN_FREQ);
            setMinTxFreq(min2mTxFreq);
            setMaxTxFreq(max2mTxFreq);
            setMaxRadioFreq(VHF_MAX_FREQ);
        } else if (RadioModuleType.UHF.equals(getRadioType())) {
            setMinRadioFreq(UHF_MIN_FREQ);
            setMinTxFreq(min70cmTxFreq);
            setMaxTxFreq(max70cmTxFreq);
            setMaxRadioFreq(UHF_MAX_FREQ);
        }
        txAllowed = isTxAllowed(Float.parseFloat(activeFrequencyStr));
    }

    public void setScanning(boolean scanning, boolean goToRxMode) {
        if (!scanning && mode != RadioMode.SCAN) return;

        if (!scanning) {
            if (squelch == 0) tuneToMemory(activeMemoryId, squelch, true);
            if (goToRxMode) setMode(RadioMode.RX);
        } else {
            setMode(RadioMode.SCAN);
            nextScan();
        }
    }

    public void setScanning(boolean scanning) { setScanning(scanning, true); }

    public void nextScan() {
        if (getMode() != RadioMode.SCAN || channelMemories.isEmpty()) return;

        int currentIndex = -1;
        for (int i = 0; i < channelMemories.size(); i++) {
            if (channelMemories.get(i).memoryId == activeMemoryId) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % channelMemories.size();
        int firstTriedIndex = nextIndex;

        do {
            ChannelMemory candidate = channelMemories.get(nextIndex);
            float memoryFreqFloat = 0.0f;
            try { memoryFreqFloat = Float.parseFloat(candidate.frequency); } catch (Exception ignored) {}

            if (!candidate.skipDuringScan && memoryFreqFloat >= minRadioFreq && memoryFreqFloat <= maxRadioFreq) {
                consecutiveSilenceBytes = 0;
                tuneToMemory(candidate, squelch > 0 ? squelch : 1, true);
                callbacks.scannedToMemory(candidate.memoryId);
                return;
            }
            nextIndex = (nextIndex + 1) % channelMemories.size();
        } while (nextIndex != firstTriedIndex);
    }

    private float[] applyMicGain(float[] audioBuffer) {
        if (micGainBoost == MicGainBoost.NONE) return audioBuffer;
        float[] newAudioBuffer = new float[audioBuffer.length];
        for (int i = 0; i < audioBuffer.length; i++) newAudioBuffer[i] = audioBuffer[i] * micGainBoost.getGain();
        return newAudioBuffer;
    }

    public void sendAudioToESP32(float[] samples, boolean dataMode) {
        if (hostToEsp32 == null) return;
        if (!dataMode) samples = applyMicGain(samples);

        byte[] audioFrame = new byte[Protocol.PROTO_MTU];
        int encodedLength = opusEncoder.encode(samples, audioFrame);
        hostToEsp32.txAudio(java.util.Arrays.copyOfRange(audioFrame, 0, encodedLength));
    }

    public boolean isRadioConnected() { return isConnectionReady() && mode != RadioMode.STARTUP; }

    private void handleParsedCommand(final RcvCommand cmd, final byte[] param, final Integer len) {
        switch (cmd) {
            case COMMAND_SMETER_REPORT:
                Protocol.Rssi.from(param, len)
                        .map(Protocol.Rssi::getSMeter9Value)
                        .filter(i -> getMode() == RadioMode.RX || getMode() == RadioMode.SCAN)
                        .ifPresent(callbacks::sMeterUpdate);
                break;
            case COMMAND_PHYS_PTT_DOWN: handlePhysicalPttDown(); break;
            case COMMAND_PHYS_PTT_UP: handlePhysicalPttUp(); break;
            case COMMAND_DEBUG_INFO: LOGGER.info(new String(Arrays.copyOf(param, len))); break;
            case COMMAND_DEBUG_DEBUG: LOGGER.fine(new String(Arrays.copyOf(param, len))); break;
            case COMMAND_DEBUG_ERROR: LOGGER.severe(new String(Arrays.copyOf(param, len))); break;
            case COMMAND_DEBUG_WARN: LOGGER.warning(new String(Arrays.copyOf(param, len))); break;
            case COMMAND_HELLO: protocolHandshake.onHelloReceived(); break;
            case COMMAND_RX_AUDIO: handleRxAudio(param, len); break;
            case COMMAND_VERSION: protocolHandshake.onVersionReceived(Protocol.FirmwareVersion.from(param, len)); break;
            case COMMAND_WINDOW_UPDATE:
                WindowUpdate.from(param, len).ifPresent(windowAck ->
                        hostToEsp32.enlargeFlowControlWindow(windowAck.getSize()));
                break;
            default: break;
        }
    }

    private void handlePhysicalPttUp() {
        if (getMode() == RadioMode.TX) {
            endPtt();
            callbacks.forcedPttEnd();
        }
    }

    private void handlePhysicalPttDown() {
        if (getMode() == RadioMode.RX && txAllowed) {
            startPtt();
            callbacks.forcedPttStart();
        }
    }

    private void handleRxAudio(final byte[] param, final Integer len) {
        int decoded = opusDecoder.decode(param, len, pcmFloat);

        if (getMode() == RadioMode.RX || getMode() == RadioMode.SCAN) {
            afskDemodulator.addSamples(pcmFloat, decoded);

            if (audioTrack != null && audioTrack.isOpen()) {
                // Convert float[] to 16-bit little-endian byte array for Java Sound API
                byte[] pcmBytes = new byte[decoded * 2];
                for (int i = 0; i < decoded; i++) {
                    short val = (short) Math.max(-32768, Math.min(32767, pcmFloat[i] * 32768.0f));
                    pcmBytes[i * 2] = (byte) (val & 0xFF);
                    pcmBytes[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
                }
                audioTrack.write(pcmBytes, 0, pcmBytes.length);
                ensureAudioPlaying();
            }
        }

        if (getMode() == RadioMode.SCAN) {
            for (int i = 0; i < decoded; i++) {
                if (Math.abs(pcmFloat[i]) > 0.001) consecutiveSilenceBytes = 0;
                else {
                    consecutiveSilenceBytes++;
                    checkScanDueToSilence();
                }
            }
        }
    }

    private void ensureAudioPlaying() {
        if (!audioTrack.isActive()) {
            targetVolume = 0;
            currentVolume = 0;
            setVolumeReal(0.0f);
            audioTrack.start();
        }
        float alpha = 0.05f;
        currentVolume = alpha + (1.0f - alpha) * currentVolume;
        targetVolume = currentVolume > 0.7f ? currentVolume : 0.0f;
        setVolumeReal(targetVolume);
    }

    @Override
    public void handlePacket(byte[] packet) {
        try {
            APRSPacket aprsPacket = Parser.parseAX25(packet);
            InformationField info = aprsPacket.getPayload();
            if (info.getDataTypeIdentifier() == ':') {
                MessagePacket msg = new MessagePacket(info.getRawBytes(), aprsPacket.getDestinationCall());
                String target = msg.getTargetCallsign().trim().toUpperCase();
                if (!msg.isAck() && target.equals(callsign.toUpperCase())) {
                    callbacks.showNotification("APRS Message", aprsPacket.getSourceCall() + " messaged you: " + msg.getMessageBody());
                    mainHandler.schedule(() -> sendAckMessage(aprsPacket.getSourceCall().toUpperCase(), msg.getMessageNumber()), 1000, TimeUnit.MILLISECONDS);
                }
            }
            callbacks.packetReceived(aprsPacket);
        } catch (Exception e) {
            LOGGER.fine("Unable to parse an APRS packet, skipping.");
        }
    }

    public void sendPositionBeacon() {
        if (!txAllowed || getMode() != RadioMode.RX) return;

        // Request GPS coordinates from desktop UI/Hardware
        callbacks.requestLocation(position -> {
            if (position != null) {
                sendPositionBeacon(position.getLatitude(), position.getLongitude());
            } else {
                callbacks.unknownLocation();
            }
        });
    }

    private void sendPositionBeacon(final double latitude, final double longitude) {
        if (getMode() != RadioMode.RX) return;

        final boolean isApprox = aprsPositionAccuracy == APRS_POSITION_APPROX;
        final Position myPos = new Position(
                isApprox ? Math.round(latitude * 100.0) / 100.0 : latitude,
                isApprox ? Math.round(longitude * 100.0) / 100.0 : longitude
        );
        try {
            final PositionField posField = new PositionField(("=" + myPos.toCompressedString()).getBytes(), "", 1);
            final APRSPacket aprsPacket = new APRSPacket(callsign, "BEACON", DEFAULT_DIGIPEATERS, posField.getRawBytes());
            aprsPacket.getPayload().addAprsData(APRSTypes.T_POSITION, posField);
            txAX25Packet(new Packet(aprsPacket.toAX25Frame()));
            callbacks.sentAprsBeacon(myPos.getLatitude(), myPos.getLongitude());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception while trying to beacon APRS location.", e);
        }
    }

    public void sendAckMessage(String to, String remoteMessageNum) {
        MessagePacket msgPacket = new MessagePacket(to, "ack" + remoteMessageNum, remoteMessageNum);
        APRSPacket aprsPacket = new APRSPacket(callsign, to, DEFAULT_DIGIPEATERS, msgPacket.getRawBytes());
        txAX25Packet(new Packet(aprsPacket.toAX25Frame()));
    }

    public int sendChatMessage(String to, String text) {
        final String outText = text.replace('|', ' ').replace('~', ' ').replace('{', ' ');
        final String targetCallsign = (to == null || to.trim().isEmpty()) ? "CQ" : to;
        if (callsign.trim().isEmpty()) return -1;

        MessagePacket msgPacket = new MessagePacket(targetCallsign, outText, String.valueOf(messageNumber++));
        if (messageNumber > APRS_MAX_MESSAGE_NUM) messageNumber = 0;

        try {
            APRSPacket aprsPacket = new APRSPacket(callsign, targetCallsign, DEFAULT_DIGIPEATERS, msgPacket.getRawBytes());
            txAX25Packet(new Packet(aprsPacket.toAX25Frame()));
        } catch (IllegalArgumentException e) {
            callbacks.chatError(e.getMessage());
            return -1;
        }
        return messageNumber - 1;
    }

    private void sendSilentFrames(int durationMs) {
        float[] opusFrame = new float[OPUS_FRAME_SIZE];
        java.util.Arrays.fill(opusFrame, 0.0f);
        for (int i = 0; i < (durationMs / 40); i++) sendAudioToESP32(opusFrame, true);
    }

    private void txAX25Packet(Packet ax25Packet) {
        if (!txAllowed) return;

        startPtt();
        float[] opusFrame = new float[OPUS_FRAME_SIZE];
        sendSilentFrames(MS_SILENCE_BEFORE_DATA_MS);

        int opusFrameIndex = 0;
        java.util.Arrays.fill(opusFrame, 0.0f);
        afskModulator.prepareToTransmit(ax25Packet);
        float[] buffer = afskModulator.getTxSamplesBuffer();

        int n;
        while ((n = afskModulator.getSamples()) > 0) {
            for (int i = 0; i < n; i++) {
                opusFrame[opusFrameIndex++] = buffer[i];
                if (opusFrameIndex == OPUS_FRAME_SIZE) {
                    sendAudioToESP32(opusFrame, true);
                    java.util.Arrays.fill(opusFrame, 0.0f);
                    opusFrameIndex = 0;
                }
            }
        }
        sendAudioToESP32(opusFrame, true);
        sendSilentFrames(MS_SILENCE_AFTER_DATA_MS);
        endPtt();
    }

    public void setHighPower(boolean highPower) {
        if (isHighPower != highPower) {
            isHighPower = highPower;
            if (isRadioConnected()) hostToEsp32.setHighPower(HlState.builder().isHighPower(highPower).build());
        }
    }

    public void setRssi(boolean on) {
        if (isRssiOn != on) {
            isRssiOn = on;
            if (isRadioConnected()) hostToEsp32.setRssi(Protocol.RSSIState.builder().on(isRssiOn).build());
        }
    }
}