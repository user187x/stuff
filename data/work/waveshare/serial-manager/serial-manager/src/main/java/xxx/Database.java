package xxx;

import org.h2.tools.Server;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.sql.Connection;
import java.sql.DriverManager;

// Statically import DSL functions
import static org.jooq.impl.DSL.*;

public class Database {

 private static final String DB_URL = "jdbc:h2:mem:authdb;DB_CLOSE_DELAY=-1";
 private static final String DB_USER = "sa";
 private static final String DB_PASS = "";

 // 1. Define Table and Fields explicitly as constants in UPPERCASE
 private static final Table<Record> USERS_TABLE = table(name("USERS"));
 private static final Field<String> USERNAME_FIELD = field(name("USERNAME"), SQLDataType.VARCHAR(50));
 private static final Field<String> PASSWORD_FIELD = field(name("PASSWORD"), SQLDataType.VARCHAR(50));

 public static void init() {
  try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

   // Boot the H2 web console
   //Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start();
   Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092").start();
   System.out.println("H2 Web Console running on port 8082.");

   DSLContext dsl = DSL.using(conn);

   // 2. DDL: Create schema using the defined constants (No string literals here)
   dsl.createTableIfNotExists(USERS_TABLE)
       .column(USERNAME_FIELD, SQLDataType.VARCHAR(50).nullable(false))
       .column(PASSWORD_FIELD, SQLDataType.VARCHAR(50).nullable(false))
       .constraint(primaryKey(USERNAME_FIELD))
       .execute();

   // 3. DML: Check for existing records
   int count = dsl.fetchCount(USERS_TABLE);

   if (count == 0) {
    dsl.insertInto(USERS_TABLE, USERNAME_FIELD, PASSWORD_FIELD)
        .values("admin", "secret")
        .execute();
    System.out.println("Seeded database with default admin account via jOOQ.");
   }
  } catch (Exception e) {
   throw new RuntimeException("Failed to initialize database", e);
  }
 }

 public static boolean authenticate(String username, String password) {
  try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
   DSLContext dsl = DSL.using(conn);

   // 4. DQL: Validate login using strongly-typed field constants
   return dsl.fetchExists(
       dsl.selectOne()
           .from(USERS_TABLE)
           .where(USERNAME_FIELD.eq(username))
           .and(PASSWORD_FIELD.eq(password))
   );
  } catch (Exception e) {
   e.printStackTrace();
   return false;
  }
 }
}