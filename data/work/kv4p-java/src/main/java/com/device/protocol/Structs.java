package com.device.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Java records mirroring the firmware's packed C structs, encoded/decoded
 * little-endian exactly as {@code [[gnu::packed]]} lays them out on the wire.
 */
public final class Structs {
    private Structs() {}

    private static ByteBuffer le(byte[] data) {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static ByteBuffer le(int capacity) {
        return ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** struct host_desired_state (22 bytes) — host → firmware snapshot. */
    public record HostDesiredState(
            long sequence,      // uint32
            int memoryId,       // int32, -1 = VFO
            int flags,          // uint16 bitmask (HOST_STATE_*)
            int bw,             // uint8, 0 = 12.5 kHz, 1 = 25 kHz
            float freqTx,       // MHz
            float freqRx,       // MHz
            int ctcssTx,        // uint8 tone index, 0 = none
            int squelch,        // uint8 0..8
            int ctcssRx) {      // uint8 tone index, 0 = none

        public static final int SIZE = 22;

        public byte[] toBytes() {
            ByteBuffer b = le(SIZE);
            b.putInt((int) sequence);
            b.putInt(memoryId);
            b.putShort((short) flags);
            b.put((byte) bw);
            b.putFloat(freqTx);
            b.putFloat(freqRx);
            b.put((byte) ctcssTx);
            b.put((byte) squelch);
            b.put((byte) ctcssRx);
            return b.array();
        }

        public HostDesiredState withFlags(int newFlags) {
            return new HostDesiredState(sequence, memoryId, newFlags, bw, freqTx, freqRx, ctcssTx, squelch, ctcssRx);
        }

        public HostDesiredState withSequence(long seq) {
            return new HostDesiredState(seq, memoryId, flags, bw, freqTx, freqRx, ctcssTx, squelch, ctcssRx);
        }

        public boolean flag(int mask) {
            return (flags & mask) != 0;
        }
    }

    /** struct device_state (26 bytes) — firmware → host applied-state snapshot. */
    public record DeviceState(
            long appliedSequence, // uint32
            int memoryId,         // int32
            int flags,            // uint16 (HOST_STATE_* | DEVICE_STATE_*)
            int bw,               // uint8
            float freqTx,
            float freqRx,
            int ctcssTx,
            int squelch,
            int ctcssRx,
            char radioModuleStatus, // 'f' found / 'x' not found
            int mode,               // DEVICE_MODE_*
            int lastError,          // DEVICE_STATE_ERROR_*
            int latestRssi) {       // uint8

        public static final int SIZE = 26;

        public static DeviceState fromBytes(byte[] data, int off) {
            ByteBuffer b = le(data);
            b.position(off);
            long seq = b.getInt() & 0xFFFF_FFFFL;
            int memoryId = b.getInt();
            int flags = b.getShort() & 0xFFFF;
            int bw = b.get() & 0xFF;
            float ftx = b.getFloat();
            float frx = b.getFloat();
            int ctx = b.get() & 0xFF;
            int sq = b.get() & 0xFF;
            int crx = b.get() & 0xFF;
            char status = (char) (b.get() & 0xFF);
            int mode = b.get() & 0xFF;
            int err = b.get() & 0xFF;
            int rssi = b.get() & 0xFF;
            return new DeviceState(seq, memoryId, flags, bw, ftx, frx, ctx, sq, crx, status, mode, err, rssi);
        }

        public boolean flag(int mask) {
            return (flags & mask) != 0;
        }
    }

    /** struct version (17 bytes) — leading part of HELLO. */
    public record Version(
            int ver,              // uint16 firmware version
            char radioModuleStatus,
            long windowSize,      // uint32 initial flow-control window
            int rfModuleType,     // 0 = SA818 VHF, 1 = SA818 UHF
            float minRadioFreq,
            float maxRadioFreq,
            int features) {       // FEATURE_* bitmask

        public static final int SIZE = 17;

        public static Version fromBytes(byte[] data, int off) {
            ByteBuffer b = le(data);
            b.position(off);
            int ver = b.getShort() & 0xFFFF;
            char status = (char) (b.get() & 0xFF);
            long window = b.getInt() & 0xFFFF_FFFFL;
            int rf = b.get() & 0xFF;
            float min = b.getFloat();
            float max = b.getFloat();
            int features = b.get() & 0xFF;
            return new Version(ver, status, window, rf, min, max, features);
        }
    }

    /** struct hello (43 bytes) = Version + initial DeviceState. */
    public record Hello(Version version, DeviceState deviceState) {
        public static final int SIZE = Version.SIZE + DeviceState.SIZE;

        public static Hello fromBytes(byte[] data) {
            if (data.length < SIZE) {
                throw new IllegalArgumentException("HELLO payload too short: " + data.length);
            }
            return new Hello(Version.fromBytes(data, 0), DeviceState.fromBytes(data, Version.SIZE));
        }
    }

    /** struct window_update (4 bytes). */
    public record WindowUpdate(long size) {
        public static final int SIZE = 4;

        public static WindowUpdate fromBytes(byte[] data) {
            return new WindowUpdate(le(data).getInt() & 0xFFFF_FFFFL);
        }
    }
}
