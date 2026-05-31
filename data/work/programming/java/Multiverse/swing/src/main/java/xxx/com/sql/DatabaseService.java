// src/main/java/xxx/com/sql/DatabaseService.java
package xxx.com.sql;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.h2.tools.Server;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.slf4j.LoggerFactory;

public class DatabaseService implements AutoCloseable {

  static {

    //Disable Jooq Logo
    System.setProperty("org.jooq.no-logo", "true");
    System.setProperty("org.jooq.no-tips", "true");
  }

  private static final Logger log = Logger.getLogger(DatabaseService.class.getName());

  /**
   * Defines the storage mode for the database.
   */
  public enum Mode {
    IN_MEMORY,
    FILE
  }

  // --- Database Connection Details ---
  private static final String DB_PROTOCOL = "jdbc:h2";
  private static final String DB_CLOSE_DELAY_PARAM = "DB_CLOSE_DELAY=-1";
  private static final String DB_USER = "sa";
  private static final String DB_PASSWORD = "";

  // Service state
  private Connection connection;
  private DSLContext dsl;
  private Server webServer;
  private final Mode mode;
  private final String dbFilePath;
  private final String dbUrl;

  /**
   * Initializes the service with a specific mode and file path.
   * @param mode The database mode (IN_MEMORY or FILE).
   * @param dbFilePath The path to the database file (only used in FILE mode).
   * @throws SQLException if a database access error occurs.
   */
  public DatabaseService(Mode mode, String dbFilePath) throws SQLException {
    this.mode = mode;
    this.dbFilePath = dbFilePath;

    String dbLocation;

    switch (this.mode) {
      case FILE:
        String path = (this.dbFilePath != null && !this.dbFilePath.trim().isEmpty()) ? this.dbFilePath : "./data/default_db";
        dbLocation = "file:" + path;
        break;
      case IN_MEMORY:
      default:
        dbLocation = "mem:default_db";
        break;
    }
    this.dbUrl = String.join(":", DB_PROTOCOL, dbLocation) + ";" + DB_CLOSE_DELAY_PARAM;

    initializeConnection();
  }

  /**
   * Initializes the service using the default IN_MEMORY mode.
   * @throws SQLException if a database access error occurs.
   */
  public DatabaseService() throws SQLException {
    this(Mode.IN_MEMORY, null);
  }

  /**
   * Establishes or re-establishes the database connection based on the configured mode.
   * @throws SQLException if a database access error occurs.
   */
  private void initializeConnection() throws SQLException {
    log.info("Attempting to connect to: {}" + this.dbUrl);
    this.connection = DriverManager.getConnection(this.dbUrl, DB_USER, DB_PASSWORD);
    this.dsl = DSL.using(this.connection, SQLDialect.H2);
    log.info("DatabaseService (re)initialized and connected to the database.");
  }

  /**
   * Ensures a database connection is active. If the connection was previously closed
   * (e.g., by dropDatabase), it re-initializes it seamlessly.
   * @throws SQLException if re-initialization fails.
   */
  public void createDatabase() throws SQLException {
    if (connection == null || connection.isClosed()) {
      log.info("Connection is closed. Re-initializing database connection.");
      initializeConnection();
    } else {
      log.info("Database connection is already active. No action needed.");
    }
  }

  /**
   * Drops the database by closing the current connection. The service can be
   * re-activated by calling createDatabase().
   * @throws SQLException if closing the connection fails.
   */
  public void dropDatabase() throws SQLException {
    log.info("Dropping database by closing the connection.");
    close();
  }

  /**
   * Inserts a new record into a table and returns the generated ID.
   * @param tableName The name of the table.
   * @param idField The jOOQ Field representing the ID column.
   * @param data A map of column names (excluding the ID column) to their values.
   * @param <T> The type of the ID.
   * @return The generated ID of the new record.
   */
  public <T> T insertRecord(String tableName, Field<T> idField, Map<String, Object> data) {
    Record result = dsl.insertInto(DSL.table(tableName))
        .set(data)
        .returning(idField)
        .fetchOne();
    if (result == null) {
      throw new IllegalStateException("Could not fetch generated ID after insert.");
    }
    return result.getValue(idField);
  }

  public Record findUnique(String tableName, String searchColumn, String searchValue) {
    return dsl.select()
        .from(DSL.table(DSL.name(tableName)))
        .where(DSL.field(DSL.name(searchColumn)).eq(searchValue))
        .fetchOne();
  }

  public boolean exists(String tableName, String searchColumn, String searchValue) {
    return dsl.fetchExists(
        dsl.selectOne()
            .from(DSL.table(DSL.name(tableName)))
            .where(DSL.field(DSL.name(searchColumn)).eq(searchValue))
    );
  }

  public void createTable(String tableName, Map<String, Class<?>> columns, String primaryKeyColumn, boolean autoIncrementPk) {
    var createTableStep = dsl.createTable(tableName);

    for (Map.Entry<String, Class<?>> entry : columns.entrySet()) {
      DataType<?> dataType = getDataType(entry.getValue());

      if (entry.getKey().equals(primaryKeyColumn) && autoIncrementPk) {
        if (!dataType.isNumeric()) {
          throw new IllegalArgumentException("Auto-increment primary key must be a numeric type.");
        }
        dataType = dataType.identity(true);
      }
      createTableStep = createTableStep.column(entry.getKey(), dataType);
    }

    createTableStep.constraint(DSL.constraint("PK_" + tableName).primaryKey(primaryKeyColumn)).execute();
    log.info("Successfully created table "+ tableName +" with primary key "+primaryKeyColumn+" Auto-increment: "+ autoIncrementPk);
  }

  public void dropTable(String tableName) {
    dsl.dropTableIfExists(tableName).execute();
    log.info("Successfully dropped table" +  tableName);
  }

  public void updateTable(String tableName, String newColumnName, Class<?> newColumnType) {
    DataType<?> dataType = getDataType(newColumnType);
    dsl.alterTable(tableName).addColumn(newColumnName, dataType).execute();
    log.info("Successfully updated table " + tableName + " by adding column " + newColumnName + " of type " + dataType.getTypeName());
  }

  public int count(String tableName) {
    return dsl.selectCount()
        .from(DSL.table(DSL.name(tableName)))
        .fetchOne(0, int.class);
  }

  public long databaseSize() {
    if (this.mode == Mode.FILE) {
      try {
        // This query is specific to H2 file-based databases.
        String sql = "SELECT VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE NAME = 'info.FILE_SIZE'";
        String sizeStr = dsl.resultQuery(sql).fetchOne(0, String.class);
        return sizeStr != null ? Long.parseLong(sizeStr) : 0L;
      } catch (Exception e) {
        // Changed to log.info to prevent red text in console.
        log.info("Could not determine database file size. This may happen if the database is new or empty.");
        return 0L;
      }
    } else {
      // Changed to log.info to prevent red text in console.
      // A more robust solution is to configure the logger via a properties file.
      log.info("The databaseSize() method is not applicable for IN_MEMORY mode and will return 0.");
      return 0L;
    }
  }

  public Map<String, String> databaseInfo() throws SQLException {
    Map<String, String> info = new HashMap<>();
    DatabaseMetaData metaData = connection.getMetaData();
    info.put("Database Product Name", metaData.getDatabaseProductName());
    info.put("Database Product Version", metaData.getDatabaseProductVersion());
    info.put("Driver Name", metaData.getDriverName());
    info.put("Driver Version", metaData.getDriverVersion());
    info.put("URL", metaData.getURL());
    info.put("User Name", metaData.getUserName());
    return info;
  }

  public void showGUI() throws SQLException {
    if (webServer == null || !webServer.isRunning(false)) {
      webServer = Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082");
      webServer.start();
      log.info("H2 GUI started at "+ webServer.getURL());

      // --- Console Instructions for User ---
      String jdbcUrlForConsole = this.dbUrl.replace(";" + DB_CLOSE_DELAY_PARAM, "");

      System.out.println("\n--- H2 Console Connection Details ---");
      System.out.println("To connect to the database, use the following details in the H2 Console login form:");
      System.out.println("1. Driver Class:  org.h2.Driver (should be the default)");
      System.out.println("2. JDBC URL:      " + jdbcUrlForConsole);
      System.out.println("3. User Name:     " + DB_USER);
      System.out.println("4. Password:      (leave this field empty)");
      System.out.println("-------------------------------------\n");

    } else {
      // Changed to log.info to prevent red text in console.
      log.info("H2 GUI is already running at " +  webServer.getURL());
    }
  }

  @Override
  public void close() throws SQLException {
    if (webServer != null && webServer.isRunning(true)) {
      webServer.stop();
      log.info("H2 GUI server stopped.");
    }
    if (connection != null && !connection.isClosed()) {
      connection.close();
      log.info("Database connection closed.");
    }
    // Set to null to indicate the service is in a closed state
    this.connection = null;
    this.dsl = null;
  }

  private DataType<?> getDataType(Class<?> clazz) {
    if (clazz == String.class) {
      return SQLDataType.VARCHAR(255);
    } else if (clazz == Integer.class) {
      return SQLDataType.INTEGER;
    } else if (clazz == Long.class) {
      return SQLDataType.BIGINT;
    } else if (clazz == Boolean.class) {
      return SQLDataType.BOOLEAN;
    } else if (clazz == java.sql.Date.class) {
      return SQLDataType.DATE;
    } else if (clazz == java.sql.Timestamp.class) {
      return SQLDataType.TIMESTAMP;
    } else if (clazz == Double.class) {
      return SQLDataType.DOUBLE;
    }
    throw new IllegalArgumentException("Unsupported class type for SQL mapping: " + clazz.getName());
  }
}
