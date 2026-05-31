package xxx.com.barcode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

public class App {

  public static void main(String[] args) throws Exception {

    String input = "xxxx-123";
    
    generatePDF417(input);
    //generateQRCode(input);
  }

  public static void generatePDF417(String message) throws Exception {

    Map<EncodeHintType, ErrorCorrectionLevel> hashMap = new HashMap<EncodeHintType, ErrorCorrectionLevel>();
    hashMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);

    BitMatrix bitMatrix = new MultiFormatWriter().encode(new String(message.getBytes(StandardCharsets.UTF_8)),
    BarcodeFormat.PDF_417, 400, 400);

    String path = System.getProperty("user.home") + "/Desktop/barcode.png";
    String format = path.substring(path.lastIndexOf('.') + 1);
    
    MatrixToImageWriter.writeToPath(bitMatrix, format, Paths.get(path));
    System.out.println("Generated PDF at: " + path);
  }

  public static void generateQRCode(String text) throws Exception {

    QRCodeWriter qrcodeWriter = new QRCodeWriter();
    BitMatrix bitMatrix = qrcodeWriter.encode(text, BarcodeFormat.QR_CODE, 200, 200);

    String path = System.getProperty("user.home") + "/Desktop/qrcode.png";
    String format = path.substring(path.lastIndexOf('.') + 1);

    MatrixToImageWriter.writeToPath(bitMatrix, format, Paths.get(path));
  }
}
