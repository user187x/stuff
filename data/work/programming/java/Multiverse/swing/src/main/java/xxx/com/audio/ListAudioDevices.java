import javax.sound.sampled.*;

public class ListAudioDevices {

  public static void main(String[] args) {
    Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
    System.out.println("--- Available Audio Output Devices ---");

    for (Mixer.Info info : mixerInfos) {
      Mixer mixer = AudioSystem.getMixer(info);

      Line.Info[] targetLineInfos = mixer.getSourceLineInfo();

      if (targetLineInfos.length > 0) {
        System.out.println("---------------------------------------");
        System.out.println("Device Name: " + info.getName());
        System.out.println("Description: " + info.getDescription());
        System.out.println("Vendor:      " + info.getVendor());
        System.out.println("---------------------------------------");
      }
    }
  }
}
