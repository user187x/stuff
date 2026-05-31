// src/main/java/xxx/com/sql/App.java
package xxx.com.sql;

import org.jooq.Record;
import org.jooq.impl.DSL;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A demonstration class to initialize and use the DatabaseService.
 */
public class App {

  public static void main(String[] args) {
    System.out.println("--- Starting DatabaseService Demonstration ---");

    // Use try-with-resources to ensure the service is closed automatically
    try (DatabaseService dbService = new DatabaseService()) {

      // --- Database Info ---
      System.out.println("\n--- 1. Calling databaseInfo() ---");
      Map<String, String> info = dbService.databaseInfo();
      info.forEach((key, value) -> System.out.println("  " + key + ": " + value));

      // --- Create Table with Auto-Increment ID ---
      System.out.println("\n--- 2. Calling createTable() with auto-increment ---");
      String tableName = "APP_CONFIG";
      String idColumnName = "ID";
      Map<String, Class<?>> tableColumns = new LinkedHashMap<>();
      tableColumns.put(idColumnName, Integer.class); // This will be our auto-incrementing PK
      tableColumns.put("CONFIG_KEY", String.class);
      tableColumns.put("CONFIG_VALUE", String.class);
      dbService.createTable(tableName, tableColumns, idColumnName, true); // Set PK and auto-increment

      // --- Update Table ---
      System.out.println("\n--- 3. Calling updateTable() to add a column ---");
      dbService.updateTable(tableName, "DESCRIPTION", String.class);

      // --- Create Records and get generated IDs ---
      System.out.println("\n--- 4. Calling insertRecord() ---");
      Map<String, Object> record1Data = new HashMap<>();
      record1Data.put("CONFIG_KEY", "app.version");
      record1Data.put("CONFIG_VALUE", "1.0.3");
      // Corrected call to use the new insertRecord method
      Integer id1 = dbService.insertRecord(tableName, DSL.field(idColumnName, Integer.class), record1Data);
      System.out.println("  Created record with generated ID: " + id1);

      Map<String, Object> record2Data = new HashMap<>();
      record2Data.put("CONFIG_KEY", "app.theme");
      record2Data.put("CONFIG_VALUE", "dark");
      // Corrected call to use the new insertRecord method
      Integer id2 = dbService.insertRecord(tableName, DSL.field(idColumnName, Integer.class), record2Data);
      System.out.println("  Created record with generated ID: " + id2);

      // --- Count ---
      System.out.println("\n--- 5. Calling count() ---");
      int recordCount = dbService.count(tableName);
      System.out.println("  Number of records in " + tableName + ": " + recordCount);

      // --- Find Unique Record ---
      System.out.println("\n--- 6. Calling findUnique() ---");
      Record themeRecord = dbService.findUnique(tableName, "CONFIG_KEY", "app.theme");
      if (themeRecord != null) {
        System.out.println("  Found record with value: " + themeRecord.getValue("CONFIG_VALUE"));
      }

      // --- Check if Record Exists ---
      System.out.println("\n--- 7. Calling exists() ---");
      boolean versionExists = dbService.exists(tableName, "CONFIG_KEY", "app.version");
      System.out.println("  Does a record with key 'app.version' exist? " + versionExists);
      boolean fakeExists = dbService.exists(tableName, "CONFIG_KEY", "app.admin");
      System.out.println("  Does a record with key 'app.admin' exist? " + fakeExists);

      // --- Database Size ---
      System.out.println("\n--- 8. Calling databaseSize() ---");
      long dbSize = dbService.databaseSize();
      System.out.println("  Estimated database size (bytes): " + dbSize);

      // --- Drop Table ---
      System.out.println("\n--- 9. Calling dropTable() ---");
      dbService.dropTable(tableName);

      // --- Placeholder Methods ---
      System.out.println("\n--- 10. Calling placeholder methods ---");
      dbService.createDatabase();
      dbService.dropDatabase();

      // --- Show GUI ---
      System.out.println("\n--- 11. Calling showGUI() ---");
      dbService.showGUI();
      System.out.println("  The H2 GUI should now be available in your web browser.");
      System.out.println("  The application will remain active for 60 seconds to allow you to explore the GUI.");

      TimeUnit.SECONDS.sleep(60);

    } catch (SQLException e) {
      System.err.println("A database error occurred: " + e.getMessage());
      e.printStackTrace();
    } catch (InterruptedException e) {
      System.err.println("Application was interrupted.");
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      System.err.println("An unexpected error occurred: " + e.getMessage());
      e.printStackTrace();
    }

    System.out.println("\n--- DatabaseService Demonstration Finished ---");
  }
}
