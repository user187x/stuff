package xxx.com.sharedmemory;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

public class SharedMemoryReader {
    public static void main(String[] args) {
        String fileName = "shared_data.bin";
        int bufferSize = 1024;

        System.out.println("[Java] Connecting to shared memory space...");

        // Open the same file created by the Python script
        try (RandomAccessFile file = new RandomAccessFile(fileName, "r");
             FileChannel channel = file.getChannel()) {

            // Map the file into Java's memory as Read-Only
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, bufferSize);

            // Read the bytes from the buffer
            byte[] data = new byte[bufferSize];
            buffer.get(data);

            // Convert bytes to a string. 
            // We use .trim() to strip away the trailing null (\x00) bytes left over from initialization.
            String message = new String(data, StandardCharsets.UTF_8).trim();

            System.out.println("[Java] Data successfully read from shared memory:");
            System.out.println("       -> " + message);

        } catch (Exception e) {
            System.err.println("[Java] Error accessing shared memory: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
