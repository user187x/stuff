package com.kv4p.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.function.Consumer;

import static com.kv4p.desktop.protocol.Kv4pProtocol.AUDIO_WIRE_SAMPLE_RATE;

/**
 * Captures the desktop microphone at 16 kHz mono, encodes 249-sample frames
 * to 128-byte IMA ADPCM blocks, and hands them to the sender (COMMAND_HOST_TX_AUDIO).
 * Replaces the Android AudioRecord path 1:1.
 */
public final class TxAudioCapture implements AutoCloseable {

    private static final AudioFormat FORMAT =
            new AudioFormat(AUDIO_WIRE_SAMPLE_RATE, 16, 1, true, false);

    private final Consumer<byte[]> frameSink;
    private TargetDataLine line;
    private Thread worker;
    private volatile boolean running;

    public TxAudioCapture(Consumer<byte[]> adpcmFrameSink) {
        this.frameSink = adpcmFrameSink;
    }

    public synchronized void start() throws LineUnavailableException {
        if (running) return;
        line = AudioSystem.getTargetDataLine(FORMAT);
        line.open(FORMAT, AUDIO_WIRE_SAMPLE_RATE / 2);
        line.start();
        running = true;
        worker = Thread.ofVirtual().name("kv4p-tx-audio").start(this::captureLoop);
    }

    private void captureLoop() {
        var encoder = new ImaAdpcm.Encoder();
        byte[] raw = new byte[ImaAdpcm.BLOCK_SAMPLES * 2];
        short[] pcm = new short[ImaAdpcm.BLOCK_SAMPLES];
        while (running) {
            int filled = 0;
            while (running && filled < raw.length) {
                int n = line.read(raw, filled, raw.length - filled);
                if (n <= 0) break;
                filled += n;
            }
            if (filled < raw.length) break;
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short) ((raw[i * 2] & 0xFF) | (raw[i * 2 + 1] << 8));
            }
            frameSink.accept(encoder.encodeBlock(pcm, 0));
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (worker != null) {
            try {
                worker.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }
}
