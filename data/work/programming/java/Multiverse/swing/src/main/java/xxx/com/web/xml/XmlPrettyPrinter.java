package xxx.com.web.xml;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

/**
 * A simple, single-file Java program to read an XML file from a given path and pretty-print its
 * content to the console with standard indentation.
 */
public class XmlPrettyPrinter {

  /**
   * The main method that drives the program.
   *
   * @param args Command-line arguments (not used).
   */
  public static void main(String[] args) throws Exception {
    // Use a try-with-resources block to ensure the scanner is closed automatically.

    URL resourceUrl = XmlPrettyPrinter.class.getClassLoader().getResource("xml/sample.xml");
    File xmlFile = new File(Objects.requireNonNull(resourceUrl).getPath());
    if (!xmlFile.exists()) {
      // Handle the case where the file does not exist before attempting to read.
      throw new FileNotFoundException(
          "Error: The file was not found at the specified path: " + xmlFile.getAbsolutePath());
    }

    // Read the entire content of the file into a string.
    String xmlContent = new String(Files.readAllBytes(xmlFile.toPath()));

    System.out.println("\n--- Original XML ---");
    System.out.println(xmlContent);

    System.out.println("\n--- Formatted XML ---");
    // Call the method to perform the pretty-printing.
    String formattedXml = formatXml(xmlContent);
    System.out.println(formattedXml);
  }

  /**
   * Formats a raw XML string with proper indentation.
   *
   * @param inputXml The raw, unformatted XML string.
   * @return A pretty-printed XML string with 4-space indentation.
   * @throws Exception if any transformation error occurs.
   */
  public static String formatXml(String inputXml) throws Exception {
    // A TransformerFactory is used to create Transformer instances.
    TransformerFactory factory = TransformerFactory.newInstance();

    // To prevent certain types of XML attacks, set secure processing features.
    factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);

    // A Transformer can be used to process XML from a variety of sources and write the
    // transformation output.
    Transformer transformer = factory.newTransformer();

    // Set the output properties for the transformation.
    // OutputKeys.INDENT specifies whether the output should be indented.
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    // {http://xml.apache.org/xslt}indent-amount is a specific property to control the number of
    // spaces to indent.
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

    // Create a Source object from the input XML string.
    Source xmlInput = new StreamSource(new StringReader(inputXml));

    // Create a StringWriter to hold the formatted XML output.
    StringWriter stringWriter = new StringWriter();

    // Create a Result object that will write to our StringWriter.
    StreamResult xmlOutput = new StreamResult(stringWriter);

    // Perform the transformation from the source to the result.
    // Since no specific transformation (XSLT) is provided, the transformer performs an identity
    // transformation,
    // which simply copies the source to the result, applying the output properties (like
    // indentation).
    transformer.transform(xmlInput, xmlOutput);

    // Return the formatted XML from the StringWriter.
    return stringWriter.toString();
  }
}
