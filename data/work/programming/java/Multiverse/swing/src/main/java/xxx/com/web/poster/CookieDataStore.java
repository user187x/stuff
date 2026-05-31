package xxx.com.web.poster;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class CookieDataStore implements CookieJar {

  private static final String JDBC_URL = "jdbc:h2:mem:cookies;DB_CLOSE_DELAY=-1";
  private static final String USER = "sa";
  private static final String PASSWORD = "";

  // --- FIX: Use DSL.name() to create identifiers correctly ---
  private static final Table<?> COOKIES_TABLE = table(DSL.name("cookies"));
  private static final Field<String> HOST = field(DSL.name("host"), String.class);
  private static final Field<String> NAME = field(DSL.name("name"), String.class);
  private static final Field<String> VALUE = field(DSL.name("value"), String.class);
  private static final Field<Long> EXPIRES_AT = field(DSL.name("expiresAt"), Long.class);
  private static final Field<String> DOMAIN = field(DSL.name("domain"), String.class);
  private static final Field<String> PATH = field(DSL.name("path"), String.class);
  private static final Field<Boolean> SECURE = field(DSL.name("secure"), Boolean.class);
  private static final Field<Boolean> HTTP_ONLY = field(DSL.name("httpOnly"), Boolean.class);
  private static final Field<Boolean> PERSISTENT = field(DSL.name("persistent"), Boolean.class);
  private static final Field<Boolean> HOST_ONLY = field(DSL.name("hostOnly"), Boolean.class);

  private final DSLContext dslContext;

  public CookieDataStore() {
    try {
      Connection connection = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
      this.dslContext = DSL.using(connection, SQLDialect.H2);
      initDatabase();
    } catch (SQLException e) {
      throw new RuntimeException("Failed to initialize CookieDataStore", e);
    }
  }

  private void initDatabase() {
    dslContext
        .createTableIfNotExists(COOKIES_TABLE)
        .column(HOST)
        .column(NAME)
        .column(VALUE)
        .column(EXPIRES_AT)
        .column(DOMAIN)
        .column(PATH)
        .column(SECURE)
        .column(HTTP_ONLY)
        .column(PERSISTENT)
        .column(HOST_ONLY)
        .primaryKey(HOST, NAME)
        .execute();
  }

  @Override
  public void saveFromResponse(HttpUrl url, @NotNull List<Cookie> cookies) {
    String host = url.host();
    for (Cookie cookie : cookies) {
      dslContext
          .mergeInto(COOKIES_TABLE)
          .usingDual()
          .on(HOST.eq(host).and(NAME.eq(cookie.name())))
          .whenMatchedThenUpdate()
          .set(VALUE, cookie.value())
          .set(EXPIRES_AT, cookie.expiresAt())
          .set(DOMAIN, cookie.domain())
          .set(PATH, cookie.path())
          .set(SECURE, cookie.secure())
          .set(HTTP_ONLY, cookie.httpOnly())
          .set(PERSISTENT, cookie.persistent())
          .set(HOST_ONLY, cookie.hostOnly())
          .whenNotMatchedThenInsert(
              HOST, NAME, VALUE, EXPIRES_AT, DOMAIN, PATH,
              SECURE, HTTP_ONLY, PERSISTENT, HOST_ONLY)
          .values(
              host, cookie.name(), cookie.value(), cookie.expiresAt(),
              cookie.domain(), cookie.path(), cookie.secure(),
              cookie.httpOnly(), cookie.persistent(), cookie.hostOnly())
          .execute();
    }
  }

  @Override
  public @NotNull List<Cookie> loadForRequest(HttpUrl url) {
    String host = url.host();
    List<Cookie> cookies = new ArrayList<>();

    Result<org.jooq.Record> results = dslContext
        .select()
        .from(COOKIES_TABLE)
        .where(HOST.eq(host))
        .fetch();

    for (org.jooq.Record r : results) {
      Cookie.Builder builder = new Cookie.Builder()
          .name(r.get(NAME))
          .value(r.get(VALUE))
          .expiresAt(r.get(EXPIRES_AT))
          .domain(r.get(DOMAIN))
          .path(r.get(PATH));

      if (Boolean.TRUE.equals(r.get(SECURE))) builder.secure();
      if (Boolean.TRUE.equals(r.get(HTTP_ONLY))) builder.httpOnly();
      if (Boolean.TRUE.equals(r.get(HOST_ONLY))) builder.hostOnlyDomain(r.get(DOMAIN));

      cookies.add(builder.build());
    }

    long now = System.currentTimeMillis();
    return cookies.stream()
        .filter(cookie -> cookie.expiresAt() > now)
        .collect(Collectors.toList());
  }
}
