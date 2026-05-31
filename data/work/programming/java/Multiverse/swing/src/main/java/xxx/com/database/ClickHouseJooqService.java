package xxx.com.database;

import static org.jooq.impl.DSL.case_;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.countDistinct;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.lag;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.partitionBy;
import static org.jooq.impl.DSL.rowNumber;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.table;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Record5;
import org.jooq.Record6;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.SelectConditionStep;
import org.jooq.Table;
import org.jooq.WindowDefinition;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultExecuteListenerProvider;

/**
 * ClickHouse JOOQ Service for querying ClickHouse database using JOOQ's DSL
 *
 * Dependencies required in pom.xml:
 * <dependencies>
 *   <dependency>
 *     <groupId>org.jooq</groupId>
 *     <artifactId>jooq</artifactId>
 *     <version>3.20.7</version>
 *   </dependency>
 *   <dependency>
 *     <groupId>com.clickhouse</groupId>
 *     <artifactId>clickhouse-jdbc</artifactId>
 *     <version>0.9.2</version>
 *   </dependency>
 *   <dependency>
 *     <groupId>org.apache.httpcomponents.client5</groupId>
 *     <artifactId>httpclient5</artifactId>
 *     <version>5.5</version>
 *   </dependency>
 * </dependencies>
 */
public class ClickHouseJooqService {

  private final DSLContext dslContext;
  private final Connection connection;

  // Example table fields - adjust according to your schema
  private final Table<Record> EVENTS_TABLE = table("events");
  private final Field<String> EVENT_ID = field("event_id", String.class);
  private final Field<String> USER_ID = field("user_id", String.class);
  private final Field<String> EVENT_TYPE = field("event_type", String.class);
  private final Field<LocalDateTime> TIMESTAMP = field("timestamp", LocalDateTime.class);
  private final Field<Integer> VALUE = field("value", Integer.class);

  public ClickHouseJooqService(String host, int port, String database,
      String username, String password) throws SQLException {
    this.connection = createConnection(host, port, database, username, password);
    this.dslContext = createDslContext(connection);
  }

  /**
   * Creates ClickHouse connection with optimized settings
   */
  private Connection createConnection(String host, int port, String database,
      String username, String password) throws SQLException {
    String url = String.format("jdbc:clickhouse://%s:%d/%s", host, port, database);

    Properties props = new Properties();
    props.setProperty("user", username);
    props.setProperty("password", password);

    // ClickHouse specific optimizations
    props.setProperty("compress", "true");
    props.setProperty("decompress", "true");
    props.setProperty("socket_timeout", "30000");
    props.setProperty("connection_timeout", "10000");
    props.setProperty("max_execution_time", "120");

    return DriverManager.getConnection(url, props);
  }

  /**
   * Creates JOOQ DSL context configured for ClickHouse
   */
  private DSLContext createDslContext(Connection connection) {
    Configuration configuration = new DefaultConfiguration()
        .set(connection)
        .set(SQLDialect.CLICKHOUSE)
        .set(new DefaultExecuteListenerProvider(
            new ClickHouseExecuteListener()
        ));

    return DSL.using(configuration);
  }

  /**
   * Simple select query with WHERE clause
   */
  public Result<Record> findEventsByUserId(String userId, int limit) {
    return dslContext
        .select()
        .from(EVENTS_TABLE)
        .where(USER_ID.eq(userId))
        .orderBy(TIMESTAMP.desc())
        .limit(limit)
        .fetch();
  }

  /**
   * Aggregation query with GROUP BY
   */
  public @NotNull Result<Record2<String, Integer>> getEventCountsByType(LocalDateTime startTime,
      LocalDateTime endTime) {
    return dslContext
        .select(EVENT_TYPE, count().as("event_count"))
        .from(EVENTS_TABLE)
        .where(TIMESTAMP.between(startTime, endTime))
        .groupBy(EVENT_TYPE)
        .orderBy(count().desc())
        .fetch();
  }

  /**
   * Complex query with JOIN and subquery
   */
  public Result<Record2<String, Long>> getTopActiveUsers(int topN, LocalDateTime since) {
    // Subquery to get user activity counts
    Field<Long> activityCount = select(count())
        .from(EVENTS_TABLE.as("e2"))
        .where(field("e2.user_id", String.class).eq(USER_ID))
        .and(field("e2.timestamp", LocalDateTime.class).gt(since))
        .asField("activity_count");

    return dslContext
        .select(USER_ID, activityCount)
        .from(EVENTS_TABLE)
        .where(TIMESTAMP.gt(since))
        .groupBy(USER_ID)
        .having(count().gt(10)) // Only users with more than 10 events
        .orderBy(activityCount.desc())
        .limit(topN)
        .fetch();
  }

  /**
   * Insert query using JOOQ DSL
   */
  public int insertEvent(String eventId, String userId, String eventType,
      LocalDateTime timestamp, Integer value) {
    return dslContext
        .insertInto(EVENTS_TABLE)
        .columns(EVENT_ID, USER_ID, EVENT_TYPE, TIMESTAMP, VALUE)
        .values(eventId, userId, eventType, timestamp, value)
        .execute();
  }

  /**
   * Batch insert using JOOQ
   */
  public int[] batchInsertEvents(List<EventRecord> events) {
    // Create individual insert statements and execute as batch
    List<Query> insertStatements = new ArrayList<>();

    for (EventRecord event : events) {
      insertStatements.add(dslContext
          .insertInto(EVENTS_TABLE)
          .columns(EVENT_ID, USER_ID, EVENT_TYPE, TIMESTAMP, VALUE)
          .values(event.eventId(), event.userId(),
              event.eventType(), event.timestamp(), event.value()));
    }

    return dslContext.batch(insertStatements).execute();
  }

  /**
   * Advanced query with window functions (if supported by your ClickHouse version)
   */
  public @NotNull Result<Record6<String, String, LocalDateTime, Integer, Integer, Integer>> getUserEventRanking(String userId) {
    WindowDefinition userWindow = name("user_window").as(
        partitionBy(USER_ID)
            .orderBy(TIMESTAMP.desc())
    );

    return dslContext
        .select(
            EVENT_ID,
            EVENT_TYPE,
            TIMESTAMP,
            VALUE,
            rowNumber().over(userWindow).as("row_number"),
            lag(VALUE, 1).over(userWindow).as("previous_value")
        )
        .from(EVENTS_TABLE)
        .where(USER_ID.eq(userId))
        .orderBy(TIMESTAMP.desc())
        .fetch();
  }

  /**
   * Query with ClickHouse-specific functions using raw SQL
   */
  public @NotNull Result<Record3<LocalDateTime, Integer, Integer>> getHourlyEventCounts(LocalDate date) {
    // Using ClickHouse's toStartOfHour function with raw SQL for better compatibility
    Field<LocalDateTime> hourField = field("toStartOfHour(timestamp)", LocalDateTime.class);
    return dslContext
        .select(
            hourField.as("hour"),
            count().as("event_count"),
            countDistinct(USER_ID).as("unique_users")
        )
        .from(EVENTS_TABLE)
        .where(field("toDate(timestamp) = {0}", Boolean.class, date))
        .groupBy(hourField)
        .orderBy(hourField)
        .fetch();
  }

  /**
   * Conditional aggregation using CASE WHEN
   */
  public @NotNull Result<Record5<String, BigDecimal, BigDecimal, BigDecimal, Integer>> getEventTypeDistribution(String userId) {
    return dslContext
        .select(
            USER_ID,
            sum(case_().when(EVENT_TYPE.eq("click"), 1).else_(0)).as("clicks"),
            sum(case_().when(EVENT_TYPE.eq("view"), 1).else_(0)).as("views"),
            sum(case_().when(EVENT_TYPE.eq("purchase"), 1).else_(0)).as("purchases"),
            count().as("total_events")
        )
        .from(EVENTS_TABLE)
        .where(USER_ID.eq(userId))
        .groupBy(USER_ID)
        .fetch();
  }

  /**
   * Raw SQL execution for ClickHouse-specific operations
   */
  public Result<Record> executeRawQuery(String sql, Object... bindings) {
    return dslContext
        .fetch(sql, bindings);
  }

  /**
   * Parameterized query with dynamic WHERE conditions
   */
  public Result<Record> findEventsWithDynamicFilters(String userId,
      List<String> eventTypes,
      LocalDateTime startTime,
      LocalDateTime endTime,
      Integer minValue) {
    SelectConditionStep<Record> query = dslContext
        .select()
        .from(EVENTS_TABLE)
        .where(noCondition()); // Start with no conditions

    if (userId != null) {
      query = query.and(USER_ID.eq(userId));
    }

    if (eventTypes != null && !eventTypes.isEmpty()) {
      query = query.and(EVENT_TYPE.in(eventTypes));
    }

    if (startTime != null) {
      query = query.and(TIMESTAMP.ge(startTime));
    }

    if (endTime != null) {
      query = query.and(TIMESTAMP.le(endTime));
    }

    if (minValue != null) {
      query = query.and(VALUE.ge(minValue));
    }

    return query
        .orderBy(TIMESTAMP.desc())
        .limit(1000)
        .fetch();
  }

  /**
   * Close the connection
   */
  public void close() throws SQLException {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  /**
   * Record class for batch inserts
   */
  public record EventRecord(
      String eventId,
      String userId,
      String eventType,
      LocalDateTime timestamp,
      Integer value
  ) {}

  /**
   * Custom execute listener for ClickHouse-specific optimizations
   */
  private static class ClickHouseExecuteListener implements ExecuteListener {
    @Override
    public void executeStart(ExecuteContext ctx) {
      // Add ClickHouse-specific query hints or logging
      System.out.println("Executing ClickHouse query: " + ctx.sql());
    }

    @Override
    public void exception(ExecuteContext ctx) {
      // Handle ClickHouse-specific exceptions
      System.err.println("ClickHouse query failed: " + ctx.sqlException().getMessage());
    }
  }

  /**
   * Example usage
   */
  public static void main(String[] args) {
    try {
      ClickHouseJooqService service = new ClickHouseJooqService(
          "localhost", 8123, "default", "default", ""
      );

      // Example queries
      Result<Record> userEvents = service.findEventsByUserId("user123", 10);
      System.out.println("Found " + userEvents.size() + " events for user123");

      @NotNull Result<Record2<String, Integer>> eventCounts = service.getEventCountsByType(
          LocalDateTime.now().minusDays(7),
          LocalDateTime.now()
      );
      System.out.println("Event type counts: " + eventCounts.size() + " types");

      service.close();

    } catch (SQLException e) {
      System.err.println("Database connection failed: " + e.getMessage());
    }
  }
}