package xxx.com.zip;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateCompressorOutputStream;
import org.apache.commons.compress.compressors.deflate.DeflateParameters;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorOutputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream;
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.apache.commons.compress.compressors.z.ZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.tukaani.xz.LZMA2Options;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;

public class CompressionDemo {

  // Example input and output paths (replace with actual paths)
  private static final String INPUT_FILE = "input.txt";
  private static final String OUTPUT_DIR = "output/";

  public static void main(String[] args) throws IOException {
    Files.createDirectories(Paths.get(OUTPUT_DIR));

    // Demonstrate ZIP
    compressZip(INPUT_FILE, OUTPUT_DIR + "output.zip", 9, true, null, null);
    extractZip(OUTPUT_DIR + "output.zip", OUTPUT_DIR + "zip_extract/");

    // Demonstrate TAR
    compressTar(INPUT_FILE, OUTPUT_DIR + "output.tar", "POSIX");
    extractTar(OUTPUT_DIR + "output.tar", OUTPUT_DIR + "tar_extract/");

    // Demonstrate GZIP
    compressGzip(INPUT_FILE, OUTPUT_DIR + "output.gz", 9);
    decompressGzip(OUTPUT_DIR + "output.gz", OUTPUT_DIR + "gzip_decompressed.txt");

    // Demonstrate BZIP2
    compressBzip2(INPUT_FILE, OUTPUT_DIR + "output.bz2", 9);
    decompressBzip2(OUTPUT_DIR + "output.bz2", OUTPUT_DIR + "bzip2_decompressed.txt");

    // Demonstrate 7Z
    compress7z(INPUT_FILE, OUTPUT_DIR + "output.7z", 9, null, null);
    extract7z(OUTPUT_DIR + "output.7z", OUTPUT_DIR + "7z_extract/");

    // Demonstrate XZ
    compressXz(INPUT_FILE, OUTPUT_DIR + "output.xz", 9);
    decompressXz(OUTPUT_DIR + "output.xz", OUTPUT_DIR + "xz_decompressed.txt");

    // Demonstrate Deflate
    compressDeflate(INPUT_FILE, OUTPUT_DIR + "output.deflate", 9);

    // Demonstrate LZMA
    compressLzma(INPUT_FILE, OUTPUT_DIR + "output.lzma");

    // Demonstrate LZ4 (framed)
    compressLz4Framed(INPUT_FILE, OUTPUT_DIR + "output.lz4");

    // Demonstrate Snappy (framed)
    compressSnappyFramed(INPUT_FILE, OUTPUT_DIR + "output.sz");

    // Demonstrate Zstandard
    compressZstandard(INPUT_FILE, OUTPUT_DIR + "output.zst", 22);

    // Demonstrate Pack200 (deprecated, but included)
    compressPack200(INPUT_FILE, OUTPUT_DIR + "output.pack");

    // Demonstrate Z (Unix compress, decompress example)
    // decompressZ("compressed.z", OUTPUT_DIR + "z_decompressed.txt"); // Assume a .Z file exists
  }

  // ZIP Compression with level (0-9) and store option (no compression if true)
  public static void compressZip(String input, String output, int level, boolean store, String password, String strength) throws IOException {
    if (password != null && !password.isEmpty()) {
      throw new IOException("Encryption not supported for ZIP");
    }
    try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(new File(output))) {
      zaos.setLevel(level);
      zaos.setMethod(store ? ZipArchiveOutputStream.STORED : ZipArchiveOutputStream.DEFLATED);
      ZipArchiveEntry entry = new ZipArchiveEntry(new File(input).getName());
      zaos.putArchiveEntry(entry);
      Files.copy(Paths.get(input), zaos);
      zaos.closeArchiveEntry();
    }
  }

  public static void extractZip(String input, String outputDir) throws IOException {
    Files.createDirectories(Paths.get(outputDir));
    try (ZipFile zipFile = new ZipFile(new File(input))) {
      Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
      while (entries.hasMoreElements()) {
        ZipArchiveEntry entry = entries.nextElement();
        File outFile = new File(outputDir, entry.getName());
        try (InputStream is = zipFile.getInputStream(entry)) {
          Files.copy(is, outFile.toPath());
        }
      }
    }
  }

  // TAR Archiving (no built-in compression, but can be combined with compressors)
  public static void compressTar(String input, String output, String longFileMode) throws IOException {
    try (TarArchiveOutputStream taos = new TarArchiveOutputStream(new FileOutputStream(output))) {
      int mode;
      switch (longFileMode) {
        case "GNU":
          mode = TarArchiveOutputStream.LONGFILE_GNU;
          break;
        case "TRUNCATE":
          mode = TarArchiveOutputStream.LONGFILE_TRUNCATE;
          break;
        case "POSIX":
        default:
          mode = TarArchiveOutputStream.LONGFILE_POSIX;
          break;
      }
      taos.setLongFileMode(mode);
      TarArchiveEntry entry = new TarArchiveEntry(new File(input).getName());
      entry.setSize(new File(input).length());
      taos.putArchiveEntry(entry);
      Files.copy(Paths.get(input), taos);
      taos.closeArchiveEntry();
    }
  }

  public static void extractTar(String input, String outputDir) throws IOException {
    Files.createDirectories(Paths.get(outputDir));
    try (TarArchiveInputStream tais = new TarArchiveInputStream(new FileInputStream(input))) {
      TarArchiveEntry entry;
      while ((entry = tais.getNextTarEntry()) != null) {
        File outFile = new File(outputDir, entry.getName());
        if (entry.isDirectory()) {
          outFile.mkdirs();
        } else {
          Files.copy(tais, outFile.toPath());
        }
      }
    }
  }

  // GZIP with compression level (-1 default, 0-9)
  public static void compressGzip(String input, String output, int level) throws IOException {
    GzipParameters params = new GzipParameters();
    params.setCompressionLevel(level);
    try (GzipCompressorOutputStream gcos = new GzipCompressorOutputStream(new FileOutputStream(output), params)) {
      Files.copy(Paths.get(input), gcos);
    }
  }

  public static void decompressGzip(String input, String output) throws IOException {
    try (GzipCompressorInputStream gcis = new GzipCompressorInputStream(new FileInputStream(input))) {
      Files.copy(gcis, Paths.get(output));
    }
  }

  // BZIP2 with block size (1-9, higher = better compression)
  public static void compressBzip2(String input, String output, int blockSize) throws IOException {
    try (BZip2CompressorOutputStream bcos = new BZip2CompressorOutputStream(new FileOutputStream(output), blockSize)) {
      Files.copy(Paths.get(input), bcos);
    }
  }

  public static void decompressBzip2(String input, String output) throws IOException {
    try (BZip2CompressorInputStream bcis = new BZip2CompressorInputStream(new FileInputStream(input))) {
      Files.copy(bcis, Paths.get(output));
    }
  }

  // 7Z with compression level (0-9)
  public static void compress7z(String input, String output, int level, String password, String strength) throws IOException {
    if (password != null && !password.isEmpty()) {
      throw new IOException("Encryption not supported for 7Z");
    }
    try (SevenZOutputFile szof = new SevenZOutputFile(new File(output))) {
      SevenZArchiveEntry entry = szof.createArchiveEntry(new File(input), new File(input).getName());
      szof.putArchiveEntry(entry);
      try (InputStream is = Files.newInputStream(Paths.get(input))) {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
          szof.write(buffer, 0, len);
        }
      }
      szof.closeArchiveEntry();
    }
  }

  public static void extract7z(String input, String outputDir) throws IOException {
    Files.createDirectories(Paths.get(outputDir));
    try (SevenZFile szf = new SevenZFile(new File(input))) {
      SevenZArchiveEntry entry;
      while ((entry = szf.getNextEntry()) != null) {
        File outFile = new File(outputDir, entry.getName());
        try (OutputStream os = new FileOutputStream(outFile)) {
          byte[] buffer = new byte[8192];
          long remaining = entry.getSize();
          while (remaining > 0) {
            int len = (int) Math.min(buffer.length, remaining);
            szf.read(buffer, 0, len);
            os.write(buffer, 0, len);
            remaining -= len;
          }
        }
      }
    }
  }

  // XZ with preset level (0-9)
  public static void compressXz(String input, String output, int preset) throws IOException {
    LZMA2Options options = new LZMA2Options(preset);
    try (XZCompressorOutputStream xcos = new XZCompressorOutputStream(new FileOutputStream(output), preset)) {
      Files.copy(Paths.get(input), xcos);
    }
  }

  public static void decompressXz(String input, String output) throws IOException {
    try (XZCompressorInputStream xcis = new XZCompressorInputStream(new FileInputStream(input))) {
      Files.copy(xcis, Paths.get(output));
    }
  }

  // Deflate with level (0-9)
  public static void compressDeflate(String input, String output, int level) throws IOException {
    DeflateParameters params = new DeflateParameters();
    params.setCompressionLevel(level);
    try (DeflateCompressorOutputStream dcos = new DeflateCompressorOutputStream(new FileOutputStream(output), params)) {
      Files.copy(Paths.get(input), dcos);
    }
  }

  // LZMA (no level, basic)
  public static void compressLzma(String input, String output) throws IOException {
    try (LZMACompressorOutputStream lcos = new LZMACompressorOutputStream(new FileOutputStream(output))) {
      Files.copy(Paths.get(input), lcos);
    }
  }

  // LZ4 Framed (options for block size, etc., but basic here)
  public static void compressLz4Framed(String input, String output) throws IOException {
    try (FramedLZ4CompressorOutputStream lcos = new FramedLZ4CompressorOutputStream(new FileOutputStream(output))) {
      Files.copy(Paths.get(input), lcos);
    }
  }

  // Snappy Framed (no levels)
  public static void compressSnappyFramed(String input, String output) throws IOException {
    try (FramedSnappyCompressorOutputStream scos = new FramedSnappyCompressorOutputStream(new FileOutputStream(output))) {
      Files.copy(Paths.get(input), scos);
    }
  }

  // Zstandard with level (-131072 to 22)
  public static void compressZstandard(String input, String output, int level) throws IOException {
    try (ZstdCompressorOutputStream zcos = new ZstdCompressorOutputStream(new FileOutputStream(output), level)) {
      Files.copy(Paths.get(input), zcos);
    }
  }

  // Pack200 (deprecated, with strategy)
  public static void compressPack200(String input, String output) throws IOException {
    try (Pack200CompressorOutputStream pcos = new Pack200CompressorOutputStream(new FileOutputStream(output))) {
      Files.copy(Paths.get(input), pcos);
    }
  }

  // Z decompress (compression not supported, only decompress)
  public static void decompressZ(String input, String output) throws IOException {
    try (ZCompressorInputStream zcis = new ZCompressorInputStream(new FileInputStream(input))) {
      Files.copy(zcis, Paths.get(output));
    }
  }
}
