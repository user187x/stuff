package com.kv4p.desktop.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import static com.kv4p.desktop.protocol.Kv4pProtocol.AUDIO_WIRE_SAMPLE_RATE;

/**
 * Plays decoded RX audio (16 kHz mono 16-bit LE PCM) through the default
 * desktop output device. Replaces the Android AudioTrack path 1:1.
 */
public final class RxAudioPlayer implements AutoCloseable {

    private static final AudioFormat FORMAT =
            new AudioFormat(AUDIO_WIRE_SAMPLE_RATE, 16, 1, true, false); // 16 kHz, mono, signed, little-endian

    private SourceDataLine line;
    private volatile boolean open;

    public synchronized void start() throws LineUnavailableException {
        if (open) return;
        line = AudioSystem.getSourceDataLine(FORMAT);
        line.open(FORMAT, AUDIO_WIRE_SAMPLE_RATE); // ~0.5 s buffer
        line.start();
        open = true;
    }

    /** Decode one COMMAND_RX_AUDIO ADPCM payload and queue it for playback. */
    public void playAdpcm(byte[] adpcmPayload) {
        if (!open || adpcmPayload.length < ImaAdpcm.BLOCK_BYTES) return;
        short[] pcm = ImaAdpcm.decodePayload(adpcmPayload);
        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            bytes[i * 2] = (byte) (pcm[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        line.write(bytes, 0, bytes.length);
    }

    @Override
    public synchronized void close() {
        open = false;
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }
}
