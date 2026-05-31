package xxx.com.zip;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple Java class demonstrating various compression and archiving formats using Apache Commons Compress.
 * This class now operates entirely on in-memory data for both input and output.
 */
public class CompressionInMemory {

  // Define the name for the entry within archives
  private static final String ARCHIVE_ENTRY_NAME = "sample_data.txt";

  public static void main(String[] args) throws IOException {
    // Generate a long string in memory to be used as the input data
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      sb.append("This is line number ").append(i)
          .append(" in our dynamically generated content. ")
          .append("The purpose is to create a reasonably large block of text ")
          .append("to demonstrate effective compression without writing to a file first.\n");
    }
    byte[] originalData = sb.toString().getBytes(StandardCharsets.UTF_8);

    System.out.println("--- Starting Fully In-Memory Compression and Archiving Demo ---");
    System.out.println("Original data size: " + originalData.length + " bytes\n");

    // Demonstrate ZIP
    System.out.println("--- Running ZIP demo ---");
    byte[] compressedZip = compressZip(ARCHIVE_ENTRY_NAME, originalData, 9);
    System.out.println("ZIP compressed size: " + compressedZip.length + " bytes");
    Map<String, byte[]> extractedZip = extractZip(compressedZip);
    verifyExtractedData(originalData, extractedZip.get(ARCHIVE_ENTRY_NAME));

    // Demonstrate TAR
    System.out.println("\n--- Running TAR demo ---");
    byte[] compressedTar = compressTar(ARCHIVE_ENTRY_NAME, originalData);
    System.out.println("TAR compressed size: " + compressedTar.length + " bytes");
    Map<String, byte[]> extractedTar = extractTar(compressedTar);
    verifyExtractedData(originalData, extractedTar.get(ARCHIVE_ENTRY_NAME));

    // Demonstrate GZIP
    System.out.println("\n--- Running GZIP demo ---");
    byte[] compressedGzip = compressGzip(originalData, 9);
    System.out.println("GZIP compressed size: " + compressedGzip.length + " bytes");
    byte[] decompressedGzip = decompressGzip(compressedGzip);
    verifyExtractedData(originalData, decompressedGzip);

    // Demonstrate BZIP2
    System.out.println("\n--- Running BZIP2 demo ---");
    byte[] compressedBzip2 = compressBzip2(originalData, 9);
    System.out.println("BZIP2 compressed size: " + compressedBzip2.length + " bytes");
    byte[] decompressedBzip2 = decompressBzip2(compressedBzip2);
    verifyExtractedData(originalData, decompressedBzip2);

    // Demonstrate 7Z
    System.out.println("\n--- Running 7z demo ---");
    byte[] compressed7z = compress7z(ARCHIVE_ENTRY_NAME, originalData);
    System.out.println("7z compressed size: " + compressed7z.length + " bytes");
    Map<String, byte[]> extracted7z = extract7z(compressed7z);
    verifyExtractedData(originalData, extracted7z.get(ARCHIVE_ENTRY_NAME));

    // Demonstrate XZ
    System.out.println("\n--- Running XZ demo ---");
    byte[] compressedXz = compressXz(originalData, 9);
    System.out.println("XZ compressed size: " + compressedXz.length + " bytes");
    byte[] decompressedXz = decompressXz(compressedXz);
    verifyExtractedData(originalData, decompressedXz);

    // ... other format demonstrations would follow a similar pattern ...

    System.out.println("\n--- Demo Finished ---");
  }

  private static void verifyExtractedData(byte[] original, byte[] extracted) {
    if (extracted == null) {
      System.out.println("Verification FAILED: Extracted data is null.");
      return;
    }
    boolean match = Arrays.equals(original, extracted);
    System.out.println("Verification successful: " + match + " (Original size: " + original.length + ", Extracted size: " + extracted.length + ")");
  }

  // Helper method to compress data from a byte array into an output stream
  private static void writeBytesToStream(byte[] data, OutputStream cos) throws IOException {
    try (InputStream is = new ByteArrayInputStream(data)) {
      IOUtils.copy(is, cos);
    }
  }

  // ZIP Compression with level (0-9)
  public static byte[] compressZip(String entryName, byte[] data, int level) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream zaos = new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(baos)) {
      zaos.setLevel(level);
      ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
      entry.setSize(data.length);
      zaos.putArchiveEntry(entry);
      writeBytesToStream(data, zaos);
      zaos.closeArchiveEntry();
      zaos.finish(); // Ensure all data is written
      return baos.toByteArray();
    }
  }

  // TAR Archiving
  public static byte[] compressTar(String entryName, byte[] data) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TarArchiveOutputStream taos = new TarArchiveOutputStream(baos)) {
      taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
      TarArchiveEntry entry = new TarArchiveEntry(entryName);
      entry.setSize(data.length);
      taos.putArchiveEntry(entry);
      writeBytesToStream(data, taos);
      taos.closeArchiveEntry();
      taos.finish();
      return baos.toByteArray();
    }
  }

  // 7Z Archiving
  public static byte[] compress7z(String entryName, byte[] data) throws IOException {
    File tempFile = File.createTempFile("temp7z", ".7z");
    try (SevenZOutputFile szof = new SevenZOutputFile(tempFile)) {
      SevenZArchiveEntry entry = szof.createArchiveEntry(new File(entryName), entryName);
      szof.putArchiveEntry(entry);
      szof.write(data);
      szof.closeArchiveEntry();
    }
    return Files.readAllBytes(tempFile.toPath());
  }


  // GZIP with compression level (-1 default, 0-9)
  public static byte[] compressGzip(byte[] data, int level) throws IOException {
    GzipParameters params = new GzipParameters();
    params.setCompressionLevel(level);
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GzipCompressorOutputStream gcos = new GzipCompressorOutputStream(baos, params)) {
      writeBytesToStream(data, gcos);
      gcos.finish();
      return baos.toByteArray();
    }
  }

  // BZIP2 with block size (1-9)
  public static byte[] compressBzip2(byte[] data, int blockSize) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BZip2CompressorOutputStream bcos = new BZip2CompressorOutputStream(baos, blockSize)) {
      writeBytesToStream(data, bcos);
      bcos.finish();
      return baos.toByteArray();
    }
  }

  // XZ with preset level (0-9)
  public static byte[] compressXz(byte[] data, int preset) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XZCompressorOutputStream xcos = new XZCompressorOutputStream(baos, preset)) {
      writeBytesToStream(data, xcos);
      xcos.finish();
      return baos.toByteArray();
    }
  }

  // --- EXTRACTION AND DECOMPRESSION METHODS ---

  public static Map<String, byte[]> extractZip(byte[] compressedData) throws IOException {
    Map<String, byte[]> results = new HashMap<>();
    try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        ZipArchiveInputStream zais = new ZipArchiveInputStream(bais)) {
      ZipArchiveEntry entry;
      while ((entry = zais.getNextZipEntry()) != null) {
        if (!entry.isDirectory()) {
          results.put(entry.getName(), IOUtils.toByteArray(zais));
        }
      }
    }
    return results;
  }

  public static Map<String, byte[]> extractTar(byte[] compressedData) throws IOException {
    Map<String, byte[]> results = new HashMap<>();
    try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        TarArchiveInputStream tais = new TarArchiveInputStream(bais)) {
      TarArchiveEntry entry;
      while ((entry = tais.getNextTarEntry()) != null) {
        if (!entry.isDirectory()) {
          results.put(entry.getName(), IOUtils.toByteArray(tais));
        }
      }
    }
    return results;
  }

  public static byte[] decompressGzip(byte[] compressedData) throws IOException {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        GzipCompressorInputStream gcis = new GzipCompressorInputStream(bais)) {
      return IOUtils.toByteArray(gcis);
    }
  }

  public static byte[] decompressBzip2(byte[] compressedData) throws IOException {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        BZip2CompressorInputStream bcis = new BZip2CompressorInputStream(bais)) {
      return IOUtils.toByteArray(bcis);
    }
  }

  public static Map<String, byte[]> extract7z(byte[] compressedData) throws IOException {
    Map<String, byte[]> results = new HashMap<>();
    // 7z extraction requires random access, so we must write to a temporary file.
    Path tempFile = Files.createTempFile("temp7z-extract", ".7z");
    try {
      Files.write(tempFile, compressedData);
      try (SevenZFile szf = new SevenZFile(tempFile.toFile())) {
        SevenZArchiveEntry entry;
        while ((entry = szf.getNextEntry()) != null) {
          if (!entry.isDirectory()) {
            byte[] content = new byte[(int) entry.getSize()];
            szf.read(content, 0, content.length);
            results.put(entry.getName(), content);
          }
        }
      }
    } finally {
      // Ensure the temporary file is deleted
      Files.deleteIfExists(tempFile);
    }
    return results;
  }

  public static byte[] decompressXz(byte[] compressedData) throws IOException {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
        XZCompressorInputStream xcis = new XZCompressorInputStream(bais)) {
      return IOUtils.toByteArray(xcis);
    }
  }
}

