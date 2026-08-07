package xxx.beta.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.*;
import org.concentus.OpusDecoder;

public class OpusCodec {

    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 2; // 1 for mono, 2 for stereo
    private static final int FRAME_SIZE_MS = 20;

    public void receiveAndPlayOpus(byte[] opusData, int dataLength) throws Exception {
        // 1. Initialize the decoder
        OpusDecoder decoder = new OpusDecoder(SAMPLE_RATE, CHANNELS);

        // Pre-allocate buffer for the decompressed PCM data (16-bit)
        // Formula: SampleRate * Channels * FrameDuration (in seconds) * 2 bytes (for 16-bit)
        int frameSize = SAMPLE_RATE * CHANNELS * (FRAME_SIZE_MS / 1000);
        short[] pcmOut = new short[frameSize];

        // 2. Decode the OPUS packet
        int decodedSamples = decoder.decode(opusData, 0, dataLength, pcmOut, 0, frameSize, false);

        // 3. Convert short[] PCM to byte[] (required for Java Sound API)
        byte[] pcmBytes = new byte[decodedSamples * 2];
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmOut, 0, decodedSamples);

        // 4. Play the audio using Java Sound API
        playPcmAudio(pcmBytes);
    }

    private void playPcmAudio(byte[] pcmData) throws LineUnavailableException {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(format);
            line.start();
            line.write(pcmData, 0, pcmData.length);
            line.drain();
        }
    }
}
