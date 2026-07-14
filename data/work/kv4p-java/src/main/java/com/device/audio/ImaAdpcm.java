package com.device.audio;

/**
 * IMA WAV ADPCM codec (mono), compatible with the firmware's
 * {@code ADPCMEncoder/ADPCMDecoder(AV_CODEC_ID_ADPCM_IMA_WAV, 128)}.
 *
 * Block layout (mono, 128-byte block):
 *   bytes 0-1  predictor (int16 LE) — also the first decoded sample
 *   byte  2    step index (0..88)
 *   byte  3    reserved (0)
 *   bytes 4-127  124 data bytes = 248 nibbles, low nibble first
 *
 * One block therefore decodes to 1 + 248 = 249 samples, matching the
 * firmware's AUDIO_FRAME_SAMPLES_WIRE.
 */
public final class ImaAdpcm {

    public static final int BLOCK_BYTES = 128;
    public static final int BLOCK_SAMPLES = 249;

    private static final int[] INDEX_TABLE = {
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8
    };

    private static final int[] STEP_TABLE = {
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
        19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
        50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
        130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
        337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
        876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
        2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
        5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
        15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
    };

    /** Persistent encoder state (predictor carried across blocks like the streaming codec). */
    public static final class Encoder {
        private int predictor = 0;
        private int stepIndex = 0;

        /**
         * Encode exactly {@link #BLOCK_SAMPLES} 16-bit PCM samples into one
         * {@link #BLOCK_BYTES}-byte IMA WAV block.
         */
        public byte[] encodeBlock(short[] pcm, int off) {
            byte[] block = new byte[BLOCK_BYTES];
            // Block header: first sample is transmitted verbatim as the predictor.
            predictor = pcm[off];
            block[0] = (byte) (predictor & 0xFF);
            block[1] = (byte) ((predictor >> 8) & 0xFF);
            block[2] = (byte) stepIndex;
            block[3] = 0;

            int outIdx = 4;
            int nibbleCount = 0;
            int packed = 0;
            for (int i = 1; i < BLOCK_SAMPLES; i++) {
                int nibble = encodeSample(pcm[off + i]);
                if ((nibbleCount & 1) == 0) {
                    packed = nibble & 0x0F;            // low nibble first
                } else {
                    packed |= (nibble & 0x0F) << 4;
                    block[outIdx++] = (byte) packed;
                }
                nibbleCount++;
            }
            return block;
        }

        private int encodeSample(int sample) {
            int step = STEP_TABLE[stepIndex];
            int diff = sample - predictor;
            int nibble = 0;
            if (diff < 0) {
                nibble = 8;
                diff = -diff;
            }
            int delta = step >> 3;
            if (diff >= step) { nibble |= 4; diff -= step; delta += step; }
            step >>= 1;
            if (diff >= step) { nibble |= 2; diff -= step; delta += step; }
            step >>= 1;
            if (diff >= step) { nibble |= 1; delta += step; }

            predictor = clamp16((nibble & 8) != 0 ? predictor - delta : predictor + delta);
            stepIndex = clampIndex(stepIndex + INDEX_TABLE[nibble]);
            return nibble;
        }

        public void reset() {
            predictor = 0;
            stepIndex = 0;
        }
    }

    /** Decoder is stateless per block: predictor and step index arrive in the block header. */
    public static short[] decodeBlock(byte[] block, int off) {
        short[] pcm = new short[BLOCK_SAMPLES];
        int predictor = (short) ((block[off] & 0xFF) | (block[off + 1] << 8));
        int stepIndex = clampIndex(block[off + 2] & 0xFF);
        pcm[0] = (short) predictor;

        int sampleIdx = 1;
        for (int i = 4; i < BLOCK_BYTES && sampleIdx < BLOCK_SAMPLES; i++) {
            int b = block[off + i] & 0xFF;
            for (int half = 0; half < 2 && sampleIdx < BLOCK_SAMPLES; half++) {
                int nibble = (half == 0) ? (b & 0x0F) : (b >> 4);
                int step = STEP_TABLE[stepIndex];
                int delta = step >> 3;
                if ((nibble & 4) != 0) delta += step;
                if ((nibble & 2) != 0) delta += step >> 1;
                if ((nibble & 1) != 0) delta += step >> 2;
                predictor = clamp16((nibble & 8) != 0 ? predictor - delta : predictor + delta);
                stepIndex = clampIndex(stepIndex + INDEX_TABLE[nibble]);
                pcm[sampleIdx++] = (short) predictor;
            }
        }
        return pcm;
    }

    /** Decode a payload made of one or more consecutive 128-byte blocks. */
    public static short[] decodePayload(byte[] payload) {
        int blocks = payload.length / BLOCK_BYTES;
        short[] pcm = new short[blocks * BLOCK_SAMPLES];
        for (int i = 0; i < blocks; i++) {
            short[] chunk = decodeBlock(payload, i * BLOCK_BYTES);
            System.arraycopy(chunk, 0, pcm, i * BLOCK_SAMPLES, BLOCK_SAMPLES);
        }
        return pcm;
    }

    private static int clamp16(int v) {
        return Math.max(-32768, Math.min(32767, v));
    }

    private static int clampIndex(int v) {
        return Math.max(0, Math.min(88, v));
    }

    private ImaAdpcm() {}
}
