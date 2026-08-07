package xxx.alpha;

import javax.sound.sampled.*;

public class AudioManager {
    private TargetDataLine micLine;
    private SourceDataLine speakerLine;

    // kv4p HT uses 44.1kHz or 22kHz depending on the firmware version.
    // Adjust to match the ESP32 ADC/DAC configuration.
    private final AudioFormat audioFormat = new AudioFormat(44100.0f, 16, 1, true, false);

    public void initAudio() throws LineUnavailableException {
        // Setup Desktop Microphone Input
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
        micLine = (TargetDataLine) AudioSystem.getLine(micInfo);
        micLine.open(audioFormat);
        micLine.start();

        // Setup Desktop Speaker Output
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, audioFormat);
        speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speakerLine.open(audioFormat);
        speakerLine.start();
    }

    public void playAudio(byte[] pcmData) {
        if (speakerLine != null) {
            speakerLine.write(pcmData, 0, pcmData.length);
        }
    }

    public byte[] captureAudio(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        if (micLine != null) {
            micLine.read(buffer, 0, buffer.length);
        }
        return buffer;
    }
}