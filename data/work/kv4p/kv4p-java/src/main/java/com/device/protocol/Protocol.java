/*
KV4P-HT desktop host (see http://kv4p.com)
Java mirror of microcontroller-src protocol.h — KV4P KISS transport, protocol v2.2.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
*/
package com.device.protocol;

/** Wire-level constants. Byte-for-byte identical to the firmware's protocol.h. */
public final class Protocol {
    private Protocol() {}

    // ---- KISS framing ----
    public static final int KISS_FEND  = 0xC0;
    public static final int KISS_FESC  = 0xDB;
    public static final int KISS_TFEND = 0xDC;
    public static final int KISS_TFESC = 0xDD;

    public static final int KISS_CMD_DATA        = 0x00;
    public static final int KISS_CMD_SETHARDWARE = 0x06;
    public static final int KISS_PORT_0          = 0x00;

    public static final byte[] KV4P_VENDOR_PREFIX = {'K', 'V', '4', 'P'};
    public static final int KV4P_PROTOCOL_VERSION = 0x01;
    public static final int KV4P_VENDOR_HEADER_LEN = 6; // "KV4P" + version + kv4pCommand

    public static final int PROTO_MTU = 2048;
    public static final int KISS_MAX_FRAME_SIZE = PROTO_MTU + 1 + KV4P_VENDOR_HEADER_LEN;

    // ---- Host -> ESP32 vendor commands (RcvCommand in firmware) ----
    public static final int COMMAND_HOST_TX_AUDIO      = 0x0C;
    public static final int COMMAND_HOST_DESIRED_STATE = 0x0D;

    // ---- ESP32 -> Host vendor commands (SndCommand in firmware) ----
    public static final int COMMAND_DEBUG_INFO    = 0x01;
    public static final int COMMAND_DEBUG_ERROR   = 0x02;
    public static final int COMMAND_DEBUG_WARN    = 0x03;
    public static final int COMMAND_DEBUG_DEBUG   = 0x04;
    public static final int COMMAND_DEBUG_TRACE   = 0x05;
    public static final int COMMAND_HELLO         = 0x06;
    public static final int COMMAND_WINDOW_UPDATE = 0x09;
    public static final int COMMAND_DEVICE_STATE  = 0x0B;
    public static final int COMMAND_RX_AUDIO      = 0x0C;

    // ---- HostDesiredState / DeviceState flags ----
    public static final int HOST_STATE_RADIO_CONFIG_VALID    = 1 << 0;
    public static final int HOST_STATE_PTT_REQUESTED         = 1 << 1;
    public static final int HOST_STATE_RX_AUDIO_OPEN         = 1 << 2;
    public static final int HOST_STATE_HIGH_POWER            = 1 << 3;
    public static final int HOST_STATE_RSSI_ENABLED          = 1 << 4;
    public static final int HOST_STATE_FILTER_PRE            = 1 << 5;
    public static final int HOST_STATE_FILTER_HIGH           = 1 << 6;
    public static final int HOST_STATE_FILTER_LOW            = 1 << 7;
    public static final int HOST_STATE_TX_ALLOWED            = 1 << 11;
    public static final int HOST_STATE_ENABLE_STATUS_REPORTS = 1 << 12;

    // Device-only reported flags
    public static final int DEVICE_STATE_PHYS_PTT_DOWN = 1 << 8;
    public static final int DEVICE_STATE_TX_ACTIVE     = 1 << 9;
    public static final int DEVICE_STATE_SQUELCHED     = 1 << 10;

    // ---- HELLO version.features bitmask ----
    public static final int FEATURE_HAS_HL         = 1 << 0;
    public static final int FEATURE_HAS_PHY_PTT    = 1 << 1;
    public static final int FEATURE_HAS_ESP32_AFSK = 1 << 2;

    // ---- Device mode enum (DeviceState.mode) ----
    public static final int DEVICE_MODE_TX      = 0;
    public static final int DEVICE_MODE_RX      = 1;
    public static final int DEVICE_MODE_STOPPED = 2;

    // ---- DeviceState.lastError ----
    public static final int DEVICE_STATE_ERROR_NONE                = 0;
    public static final int DEVICE_STATE_ERROR_RADIO_CONFIG_FAILED = 1;
    public static final int DEVICE_STATE_ERROR_FILTERS_FAILED      = 2;

    // ---- Radio module status char in HELLO / DeviceState ----
    public static final char RADIO_MODULE_NOT_FOUND = 'x';
    public static final char RADIO_MODULE_FOUND     = 'f';

    // ---- Bandwidth (DRA818) ----
    public static final int BW_12K5 = 0; // DRA818_12K5
    public static final int BW_25K  = 1; // DRA818_25K

    // ---- Audio format on the wire ----
    // Live voice audio is 16 kHz mono 16-bit PCM carried as 4-bit IMA WAV ADPCM.
    public static final int AUDIO_WIRE_SAMPLE_RATE  = 16_000;
    public static final int AUDIO_FRAME_BYTES       = 128; // one ADPCM block
    public static final int AUDIO_FRAME_SAMPLES     = 249; // samples decoded per 128-byte mono block

    // ---- Serial link ----
    public static final int SERIAL_BAUD = 115_200;
}