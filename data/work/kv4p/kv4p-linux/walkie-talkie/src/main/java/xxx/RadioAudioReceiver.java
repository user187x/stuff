package xxx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.*;
import org.concentus.OpusDecoder;
import xxx.beta.audio.AudioBridge;

/**
 * Receives OPUS-encoded audio packets from the radio via serial port,
 * decodes them, and plays the audio to the speaker.
 *
 * Feeds decoded samples to visualizer via AudioSampleListener.
 */
public class RadioAudioReceiver implements Runnable {
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 1; // Mono
    private static final int FRAME_SIZE_MS = 20;
    private static final int FRAME_SIZE_SAMPLES = SAMPLE_RATE * CHANNELS * (FRAME_SIZE_MS / 1000);

    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>(50);
    private SourceDataLine outputLine;
    private AudioFormat audioFormat;
    private Thread decoderThread;
    private volatile boolean running = false;
    private volatile boolean paused = false;

    // Listener for visualizer - uses AudioBridge.AudioSampleListener interface
    private AudioBridge.AudioSampleListener sampleListener;

    public RadioAudioReceiver(String outputDeviceName) throws LineUnavailableException {
        audioFormat = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
        openOutputLine(outputDeviceName, audioFormat);
    }

    private void openOutputLine(String deviceName, AudioFormat format) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        Mixer mixer = null;
        if (deviceName != null && !deviceName.equals("Default")) {
            for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
                if (mixerInfo.getName().equals(deviceName)) {
                    mixer = AudioSystem.getMixer(mixerInfo);
                    break;
                }
            }
        }

        if (mixer != null) {
            outputLine = (SourceDataLine) mixer.getLine(info);
        } else {
            outputLine = (SourceDataLine) AudioSystem.getLine(info);
        }

        int bufferSize = (int) (SAMPLE_RATE * 0.04); // 40ms buffer
        outputLine.open(format, bufferSize * 2);
    }

    public void setSampleListener(AudioBridge.AudioSampleListener listener) {
        this.sampleListener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        paused = false;
        outputLine.start();
        decoderThread = new Thread(this, "RadioAudioDecoder");
        decoderThread.setDaemon(true);
        decoderThread.start();
    }

    public void stop() {
        running = false;
        if (decoderThread != null) {
            decoderThread.interrupt();
            try {
                decoderThread.join(1000);
            } catch (InterruptedException ignored) {
            }
        }
        if (outputLine != null) {
            try {
                outputLine.drain();
                outputLine.stop();
                outputLine.close();
            } catch (Exception ignored) {
            }
        }
        audioQueue.clear();
    }

    public void pause() {
        paused = true;
        if (outputLine != null) {
            try {
                outputLine.stop();
            } catch (Exception ignored) {
            }
        }
    }

    public void resume() {
        paused = false;
        if (outputLine != null && running && !outputLine.isRunning()) {
            outputLine.start();
        }
    }

    public void queueAudioPacket(byte[] opusData) {
        // Drop if queue is full (audio is getting backed up)
        audioQueue.offer(opusData);
    }

    @Override
    public void run() {
        try {
            OpusDecoder decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);
            short[] pcmBuffer = new short[FRAME_SIZE_SAMPLES];
            byte[] pcmBytes = new byte[FRAME_SIZE_SAMPLES * 2];

            while (running) {
                try {
                    // Wait for next packet (100ms timeout so we can check running flag)
                    byte[] opusPacket = audioQueue.poll();
                    if (opusPacket == null) {
                        Thread.sleep(10);
                        continue;
                    }

                    if (paused) {
                        continue;
                    }

                    try {
                        // Decode OPUS packet to PCM
                        int decodedSamples = decoder.decode(opusPacket, 0, opusPacket.length,
                                pcmBuffer, 0, FRAME_SIZE_SAMPLES, false);

                        if (decodedSamples > 0) {
                            // Convert short[] PCM to byte[] (little-endian)
                            ByteBuffer.wrap(pcmBytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()
                                    .put(pcmBuffer, 0, decodedSamples);

                            int byteCount = decodedSamples * 2;

                            // Send to visualizer (uses AudioBridge.AudioSampleListener interface)
                            if (sampleListener != null) {
                                sampleListener.onAudioSamples(pcmBytes, byteCount, audioFormat);
                            }

                            // Write to output line
                            if (outputLine != null && outputLine.isRunning()) {
                                outputLine.write(pcmBytes, 0, byteCount);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error decoding OPUS packet: " + e.getMessage());
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("RadioAudioReceiver error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getQueueSize() {
        return audioQueue.size();
    }
}