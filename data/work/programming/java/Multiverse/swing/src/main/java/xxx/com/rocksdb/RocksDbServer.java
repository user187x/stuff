package xxx.com.rocksdb;

import io.javalin.Javalin;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A simple Javalin application that uses RocksDB for data storage.
 *
 * This class provides two REST endpoints:
 * - POST /db/{key}: Stores a key-value pair. The key is from the path, the value is from the request body.
 * - GET /db/{key}: Retrieves a value for a given key.
 */
public class RocksDbServer {

  // The RocksDB instance. This is a static field so it can be accessed
  // in the shutdown hook and Javalin handlers.
  private static RocksDB rocksDB;

  public static void main(String[] args) {
    // Must load the RocksDB library first.
    RocksDB.loadLibrary();

    // The path to the database directory.
    String dbPath = "rocksdb-data";

    // Create the directory if it doesn't exist.
    try {
      Files.createDirectories(Paths.get(dbPath));
    } catch (IOException e) {
      System.err.println("Error creating RocksDB data directory: " + e.getMessage());
      return; // Exit if the directory can't be created
    }

    // Initialize RocksDB
    try (final Options options = new Options().setCreateIfMissing(true)) {
      // Open the database
      rocksDB = RocksDB.open(options, dbPath);
      System.out.println("RocksDB initialized successfully.");
    } catch (RocksDBException e) {
      System.err.println("Error initializing RocksDB: " + e.getMessage());
      return; // Exit if RocksDB can't be opened
    }

    // Add a shutdown hook to close the database when the application terminates
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Closing RocksDB...");
      if (rocksDB != null) {
        rocksDB.close();
      }
      System.out.println("RocksDB closed.");
    }));


    // Create and start the Javalin application
    Javalin app = Javalin.create().start(7070);
    System.out.println("Javalin server started on port 7070.");

    // Add an interceptor to log every request
    // The `before` handler is executed before the main endpoint handler.
    app.before(ctx -> {
      System.out.printf("Received request: %s %s%n", ctx.method(), ctx.path());
    });

    // POST endpoint to store data
    app.post("/db/{key}", ctx -> {
      try {
        String key = ctx.pathParam("key");
        String value = ctx.body();
        rocksDB.put(key.getBytes(), value.getBytes());
        ctx.status(201).result("Stored key: '" + key + "'");
      } catch (RocksDBException e) {
        ctx.status(500).result("Error storing data: " + e.getMessage());
        System.err.println("RocksDB Error: " + e.getMessage());
      }
    });

    // GET endpoint to retrieve data
    app.get("/db/{key}", ctx -> {
      try {
        String key = ctx.pathParam("key");
        byte[] valueBytes = rocksDB.get(key.getBytes());
        if (valueBytes != null) {
          ctx.result(new String(valueBytes));
        } else {
          ctx.status(404).result("Key not found.");
        }
      } catch (RocksDBException e) {
        ctx.status(500).result("Error retrieving data: " + e.getMessage());
        System.err.println("RocksDB Error: " + e.getMessage());
      }
    });

    // Add a simple root endpoint for a welcome message
    app.get("/", ctx -> ctx.result("Javalin and RocksDB server is running!"));
  }
}
