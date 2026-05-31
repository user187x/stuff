package xxx.com.captcha;

import com.formdev.flatlaf.FlatDarkLaf;
import com.wf.captcha.GifCaptcha;
import com.wf.captcha.base.Captcha;
import java.awt.BorderLayout;
import java.io.ByteArrayOutputStream;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

/** A simple Java program to generate a CAPTCHA image and save it to a file. */
public class CaptchaGenerator {

  public static void main(String[] args) throws Exception {
    CaptchaGenerator.generate();
  }

  public static void generate() throws Exception {

    GifCaptcha captcha = new GifCaptcha(130, 48);
    captcha.setFont(Captcha.FONT_8);
    captcha.setWidth(300);
    captcha.setHeight(200);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    captcha.out(baos);
    ImageIcon imageIcon = new ImageIcon(baos.toByteArray());

    // Simply displaying it on a JFrame
    FlatDarkLaf.setup();
    JLabel imageLabel = new JLabel(imageIcon);

    JFrame frame = new JFrame();
    frame.setTitle("Captcha Generator [" + captcha.text() + "]");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(captcha.getWidth(), captcha.getHeight());
    frame.setResizable(false);
    frame.setLocationRelativeTo(null);
    frame.add(imageLabel, BorderLayout.CENTER);

    frame.pack();
    frame.setVisible(true);
  }
}
