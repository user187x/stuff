package fullstack.react.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JsonMapper;
import org.jetbrains.annotations.NotNull;
import org.jooq.*;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.jooq.impl.DSL.*;
import static org.jooq.impl.SQLDataType.VARCHAR;

public class App {

  private static final Table<Record> USERS = table(name("users"));
  private static final Field<String> USERNAME = field(name("username"), VARCHAR(255));
  private static final Field<String> PASSWORD = field(name("password"), VARCHAR(255));
  private static final Field<String> IMAGEURL = field(name("imageUrl"), VARCHAR(255));


  public static void main(String[] args) {
    // --- Database Setup ---
    String jdbcUrl = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";

    try (Connection connection = DriverManager.getConnection(jdbcUrl)) {

      DSLContext create = DSL.using(connection, SQLDialect.H2);

      create.createTable(USERS)
          .column(USERNAME)
          .column(PASSWORD)
          .column(IMAGEURL)
          .constraint(constraint("PK_USERS").primaryKey(USERNAME))
          .execute();

      create.insertInto(USERS, USERNAME, PASSWORD, IMAGEURL)
          .values("tester", "password123", "https://placehold.co/400x400/EFEFEFF/grey?text=Profile+Pic")
          .execute();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    // --- 2. Javalin Server Setup ---
    Javalin app = Javalin.create(config -> {
      config.staticFiles.add("/public", Location.CLASSPATH);
      config.spaRoot.addFile("/", "/public/index.html", Location.CLASSPATH);

      Gson gson = new GsonBuilder().setPrettyPrinting().create();

      // 3. Define the JsonMapper inline using an anonymous inner class
      config.jsonMapper(new JsonMapper() {
        @NotNull
        @Override
        public String toJsonString(@NotNull Object obj, @NotNull Type type) {
          return gson.toJson(obj, type);
        }

        @NotNull
        @Override
        public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
          return gson.fromJson(json, targetType);
        }

        @NotNull
        @Override
        public InputStream toJsonStream(@NotNull Object obj, @NotNull Type type) {
          return new ByteArrayInputStream(toJsonString(obj, type).getBytes(StandardCharsets.UTF_8));
        }

        @NotNull
        @Override
        public <T> T fromJsonStream(@NotNull InputStream json, @NotNull Type targetType) {
          return gson.fromJson(new InputStreamReader(json), targetType);
        }

        @Override
        public void writeToOutputStream(@NotNull Stream<?> stream, @NotNull OutputStream outputStream) {
          try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(outputStream))) {
            writer.beginArray();
            stream.forEach(element -> gson.toJson(element, element.getClass(), writer));
            writer.endArray();
          } catch (IOException e) {
            throw new UncheckedIOException("Failed to write JSON stream", e);
          }
        }
      });
    }).start(7070);

    System.out.println("Server started on http://localhost:7070");

    // --- API Routes ---
    // Login endpoint
    app.post("/api/login", ctx -> {
      LoginRequest loginRequest = ctx.bodyAsClass(LoginRequest.class);

      try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
        DSLContext create = DSL.using(connection, SQLDialect.H2);
        Result<Record> result = create.select()
            .from(USERS)
            .where(USERNAME.eq(loginRequest.username).and(PASSWORD.eq(loginRequest.password)))
            .fetch();

        if (result.isNotEmpty()) {
          Map<String, String> response = new HashMap<>();
          response.put("message", "Login successful");
          response.put("username", result.get(0).getValue(USERNAME));
          ctx.json(response);
        } else {
          ctx.status(401).json(Map.of("message", "Invalid username or password"));
        }
      } catch (Exception e) {
        ctx.status(500).json(Map.of("message", "Database error"));
        e.printStackTrace();
      }
    });

    // Endpoint to get user data
    app.get("/api/user/{username}", ctx -> {
      String usernameParam = ctx.pathParam("username");
      try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
        DSLContext create = DSL.using(connection, SQLDialect.H2);

        // Select data using the defined DSL objects
        Result<Record2<String, String>> result = create.select(USERNAME, IMAGEURL)
            .from(USERS)
            .where(USERNAME.eq(usernameParam))
            .fetch();

        if (result.isNotEmpty()) {
          Map<String, String> user = new HashMap<>();
          user.put("username", result.get(0).getValue(USERNAME));
          user.put("imageUrl", result.get(0).getValue(IMAGEURL));
          ctx.json(user);
        } else {
          ctx.status(404).json(Map.of("message", "User not found"));
        }
      } catch (Exception e) {
        ctx.status(500).json(Map.of("message", "Database error"));
        e.printStackTrace();
      }
    });
  }

  // Simple static class to map the JSON login request body
  static class LoginRequest {
    public String username;
    public String password;
  }
}
