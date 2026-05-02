---
quick_id: 260502-8gk
type: research
date: 2026-05-02
---

# Research: Valkey + PostgreSQL Manual OTel Instrumentation (Phase 8)

**Domain:** Jedis 7.x / JdbcTemplate / HikariCP manual OTel SDK 1.61.0 instrumentation
**Confidence:** HIGH — all critical findings verified via Maven Central, GitHub source, or official docs

---

## 1. Library Versions to Pin

| Artifact | Version | Source BOM? | Rationale | Source |
|---|---|---|---|---|
| `redis.clients:jedis` | `7.5.0` | No — pin directly | Latest stable (released 2026-04-27); `JedisPool(host, port)` API unchanged from 5.x; `SetParams.nx().ex(ttl)` replaces legacy `setnx` (correct idiom in 7.x). `6.0.0` appeared to be latest in Maven Central search but GitHub shows 7.5.0 already published to Central. | [VERIFIED: Maven Central + GitHub releases] |
| `org.postgresql:postgresql` | `42.7.8` | Yes — Spring Boot 3.4.13 BOM | pgjdbc driver; BOM property `postgresql.version=42.7.8` | [VERIFIED: spring-boot-dependencies-3.4.13.pom] |
| `com.zaxxer:HikariCP` | `5.1.0` | Yes — Spring Boot 3.4.13 BOM | BOM property `hikaricp.version=5.1.0`; ships with `spring-boot-starter-jdbc` | [VERIFIED: spring-boot-dependencies-3.4.13.pom] |
| `org.testcontainers:postgresql` | BOM-managed `1.20.6` | Yes — Spring Boot 3.4.13 BOM | `testcontainers.version=1.20.6`; `PostgreSQLContainer` implements `JdbcDatabaseContainer` so `@ServiceConnection` works | [VERIFIED: spring-boot-dependencies-3.4.13.pom + testcontainers-bom-1.20.6.pom] |
| `org.testcontainers:testcontainers` | BOM-managed `1.20.6` | Yes | Core for `GenericContainer` (Valkey has no dedicated TC module — see §5) | [VERIFIED: testcontainers-bom-1.20.6.pom] |
| `valkey/valkey` (Docker image) | `8.1-alpine` | n/a | Latest stable 8.x line (8.1.6 on 2026-04-02); exposes port 6379; speaks Redis RESP protocol; Jedis 7.x connects transparently | [VERIFIED: Docker Hub tags] |
| `postgres` (Docker image) | `17-alpine` | n/a | Current PostgreSQL LTS; matches `pg_dump` tool version attendees will have; `-alpine` for small image | [ASSUMED: postgres 17 is current LTS as of 2026] |
| `io.opentelemetry.semconv:opentelemetry-semconv-incubating` | `1.40.0-alpha` | No — already in both service POMs | Required for `DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS` (see §6) | [VERIFIED: semconv-java v1.40.0 source] |

---

## 2. Jedis Manual Instrumentation Skeleton

Pattern: thin `InstrumentedJedisPool` wrapper. Store `host`/`port` at construction time (passed to `JedisPool`). Each operation borrows a `Jedis` from the pool, opens a `CLIENT`-kind span, runs the command, ends the span.

```java
// InstrumentedJedisPool.java — producer-service
public class InstrumentedJedisPool implements Closeable {

    private final JedisPool pool;
    private final Tracer tracer;
    private final String host;
    private final int port;

    public InstrumentedJedisPool(Tracer tracer, String host, int port) {
        this.pool  = new JedisPool(host, port);
        this.tracer = tracer;
        this.host  = host;
        this.port  = port;
    }

    /**
     * SET key value NX EX ttlSeconds. Returns true if key was newly set (cache miss).
     * db.operation.name = "SET" (the Redis command, not the NX semantics — per OTel db semconv).
     */
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        Span span = tracer.spanBuilder("SET " + key)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbIncubatingAttributes.DB_SYSTEM_NAME,
                DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS)
            .setAttribute(DbAttributes.DB_OPERATION_NAME, "SET")
            .setAttribute(ServerAttributes.SERVER_ADDRESS, host)
            .setAttribute(ServerAttributes.SERVER_PORT, (long) port)
            .startSpan();
        try (Scope scope = span.makeCurrent(); Jedis jedis = pool.getResource()) {
            String result = jedis.set(key, value, SetParams.setParams().nx().ex(ttlSeconds));
            span.setAttribute("valkey.set.result", result != null ? result : "NIL");
            return "OK".equals(result);   // "OK" = inserted; null = key existed
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    @Override public void close() { pool.close(); }
}
```

**Imports:**
- `DbIncubatingAttributes` — `io.opentelemetry.semconv.incubating.DbIncubatingAttributes` (already in producer pom)
- `DbAttributes` — `io.opentelemetry.semconv.DbAttributes`
- `ServerAttributes` — `io.opentelemetry.semconv.ServerAttributes`
- `SetParams` — `redis.clients.jedis.params.SetParams`

---

## 3. JdbcTemplate Manual Instrumentation Skeleton

Pattern: a `withSpan` helper method inside the `OrderRepository` class (not a separate decorator, to maximize readability of the SDK calls for attendees). The auto-configured `JdbcTemplate` is injected as-is; spans wrap individual `jdbcTemplate.update(...)` calls.

```java
// OrderRepository.java — consumer-service (annotated Spring @Repository)
@Repository
public class OrderRepository {

    private final JdbcTemplate jdbc;
    private final Tracer tracer;

    public OrderRepository(JdbcTemplate jdbc, Tracer tracer) {
        this.jdbc = jdbc;
        this.tracer = tracer;
    }

    public void insertProcessedOrder(String orderId, String traceId, String payloadJson) {
        String sql = "INSERT INTO processed_orders"
            + "(order_id, processed_at, consumer_trace_id, payload)"
            + " VALUES (?, NOW(), ?, ?::jsonb)"
            + " ON CONFLICT (order_id) DO NOTHING";

        withSpan("INSERT", "processed_orders", sql,
            () -> jdbc.update(sql, orderId, traceId, payloadJson));
    }

    // ---- helper ----

    private void withSpan(String operation, String table, String sql, Runnable action) {
        Span span = tracer.spanBuilder(operation + " " + table)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute(DbAttributes.DB_SYSTEM_NAME,
                DbAttributes.DbSystemNameValues.POSTGRESQL)
            .setAttribute(DbAttributes.DB_OPERATION_NAME, operation)
            .setAttribute(DbAttributes.DB_COLLECTION_NAME, table)
            .setAttribute(DbAttributes.DB_QUERY_TEXT, sql)       // parameterized — safe to log
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            action.run();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**Key attributes:**
- `DB_SYSTEM_NAME = "postgresql"` — from **stable** `DbAttributes.DbSystemNameValues.POSTGRESQL`
- `DB_COLLECTION_NAME` — maps to the table name (`processed_orders`)
- `DB_QUERY_TEXT` — safe because query is parameterized (placeholders `?`); no user data inlined

---

## 4. HikariCP ObservableGauge Registration Skeleton

**Gotcha:** HikariCP initializes the pool lazily on first `getConnection()`. At `@Bean` factory time the `HikariPoolMXBean` may be null. Solution: register the callback lazily (the SDK calls the callback on each metric collection cycle — if the pool isn't up yet, record 0 and return).

```java
// Inside OtelSdkConfiguration.java @Bean openTelemetry(...) factory body, after SDK is built:

HikariDataSource ds = (HikariDataSource) dataSource; // inject DataSource, cast after auto-config

Meter meter = openTelemetry.getMeter("com.example.consumer");

meter.gaugeBuilder("db.client.connection.count")
    .setDescription("HikariCP active connections")
    .setUnit("{connections}")
    .buildWithCallback(measurement -> {
        HikariPoolMXBean mxBean = ds.getHikariPoolMXBean();
        if (mxBean == null) return;          // pool not yet initialized — skip cycle
        measurement.record(mxBean.getActiveConnections(),
            Attributes.of(AttributeKey.stringKey("state"), "used"));
        measurement.record(mxBean.getIdleConnections(),
            Attributes.of(AttributeKey.stringKey("state"), "idle"));
        measurement.record(mxBean.getThreadsAwaitingConnection(),
            Attributes.of(AttributeKey.stringKey("state"), "pending"));
    });
```

**Lifecycle:** `buildWithCallback` returns an `ObservableLongGauge` (or `ObservableDoubleGauge`) handle. Hold a reference in the `@Configuration` class; the SDK keeps it registered until closed. Closing the `OpenTelemetrySdk` cleans up all instruments automatically.

**Metric name:** `db.client.connection.count` + `state` attribute follows the OTel database client metrics semconv (incubating) for connection pool state. [ASSUMED: exact semconv metric name — the incubating spec defines `db.client.connection.count` with `state` in `used|idle|pending` per the 2025 experimental spec; verify against semconv spec if required]

---

## 5. Testcontainers Integration: Postgres + Valkey

### PostgreSQL — @ServiceConnection (works out of the box)

`PostgreSQLContainer` implements `JdbcDatabaseContainer` which triggers `JdbcContainerConnectionDetailsFactory` in Spring Boot 3.4.13. `@ServiceConnection` auto-wires `spring.datasource.*` properties.

```java
@Container
@ServiceConnection   // wires datasource URL/user/pass automatically
static PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:17-alpine");
```

Maven dependency (BOM-managed, no version tag needed):
```xml
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

### Valkey — GenericContainer + @DynamicPropertySource

**Testcontainers 1.20.6 has NO `redis` or `valkey` module** (verified: the BOM lists activemq, azure, cassandra, kafka, postgresql, rabbitmq, mongodb etc. — no redis module). The Spring Boot `@ServiceConnection` for Redis requires `com.redis.testcontainers.RedisContainer` (a third-party library not in the BOM). For the workshop: use `GenericContainer` + `@DynamicPropertySource` — simpler and teaches more.

```java
@Container
static GenericContainer<?> valkey =
    new GenericContainer<>("valkey/valkey:8.1-alpine")
        .withExposedPorts(6379);

@DynamicPropertySource
static void valkeyProperties(DynamicPropertyRegistry registry) {
    registry.add("valkey.host", valkey::getHost);
    registry.add("valkey.port", () -> valkey.getMappedPort(6379));
}
```

In the producer, read `${valkey.host}` / `${valkey.port}` from `application.properties` so the same properties source works in both test and production (production: `valkey.host=localhost`, `valkey.port=6379`).

---

## 6. Semconv 1.40.0 Database Attribute Mapping

| Attribute | Key String | Java Constant | Stable or Incubating | Notes |
|---|---|---|---|---|
| `db.system.name` (PostgreSQL) | `"postgresql"` | `DbAttributes.DbSystemNameValues.POSTGRESQL` | **STABLE** (`opentelemetry-semconv:1.40.0`) | Only 4 values stable: mariadb, microsoft.sql_server, mysql, postgresql |
| `db.system.name` (Redis/Valkey) | `"redis"` | `DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS` | **INCUBATING** (`opentelemetry-semconv-incubating:1.40.0-alpha`) | No "valkey" value exists in 1.40.0; Valkey speaks Redis protocol so "redis" is correct |
| `db.operation.name` | `"INSERT"`, `"SET"`, `"GET"` | `DbAttributes.DB_OPERATION_NAME` | **STABLE** | Use the SQL keyword or Redis command name verbatim |
| `db.collection.name` | table name string | `DbAttributes.DB_COLLECTION_NAME` | **STABLE** | For JDBC: use the table name; not applicable to Redis |
| `db.query.text` | SQL string | `DbAttributes.DB_QUERY_TEXT` | **STABLE** | Log parameterized SQL (placeholders `?`); never inline user values |
| `db.namespace` | database name | `DbAttributes.DB_NAMESPACE` | **STABLE** | Optional for this workshop (Postgres DB name = "orders") |
| `server.address` | host string | `ServerAttributes.SERVER_ADDRESS` | **STABLE** | Use for both JDBC and Redis spans |
| `server.port` | port long | `ServerAttributes.SERVER_PORT` | **STABLE** | Use for both JDBC and Redis spans |
| `network.peer.address` | IP/hostname | `NetworkAttributes.NETWORK_PEER_ADDRESS` | **STABLE** | Alternative to server.address for Redis spans; server.address is sufficient |

**Critical finding:** `db.system.name = "redis"` requires the **incubating** artifact. Both service POMs already declare `opentelemetry-semconv-incubating:1.40.0-alpha` — no new dependency needed.

The legacy `DbIncubatingAttributes.DB_SYSTEM` (key: `"db.system"`) is `@Deprecated` in 1.40.0-alpha — do not use it.

---

## 7. Pitfalls and Gotchas

- **`setnx` is legacy in Jedis 7.x** — use `jedis.set(key, value, SetParams.setParams().nx().ex(ttl))` which is atomic SET-if-not-exists-with-TTL. Legacy `jedis.setnx()` exists but requires a separate `jedis.expire()` call — NOT atomic.

- **Span name for Redis must be the command, not the key** — correct: `"SET idempotency:..."`, better: `"SET"` (fixed low-cardinality name). High-cardinality key strings in span names bloat Tempo's trace index. Put the key in a span attribute if needed, but keep the name as just the command verb.

- **`HikariPoolMXBean` is null until first connection** — see §4. `ds.getHikariPoolMXBean()` returns null before any `getConnection()` call. Guard with null check in the callback.

- **`spring.sql.init.mode` default is `embedded`** — Spring Boot only runs `schema.sql` automatically for embedded DBs (H2/HSQL/Derby) by default. For a real Postgres container, **must** set `spring.sql.init.mode=always` in `application-test.properties` (or unconditionally in `application.properties` on the consumer). Without this, `processed_orders` table is never created and the app silently fails.

- **`@ServiceConnection` does NOT work for Valkey/Redis with standard Testcontainers** — `@ServiceConnection` for Redis requires `com.redis.testcontainers.RedisContainer` (third-party). `GenericContainer("valkey/valkey:8.1-alpine")` is not recognized. Use `@DynamicPropertySource` instead.

- **`DB_SYSTEM_NAME = "redis"` is INCUBATING, not stable** — the stable `DbAttributes.DbSystemNameValues` in 1.40.0 only contains mariadb, mysql, postgresql, microsoft.sql_server. For Redis/Valkey, import `DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS` from the `-incubating` artifact. The `opentelemetry-semconv-incubating:1.40.0-alpha` dep is already in both service POMs.

- **Do NOT define a custom `DataSource` @Bean** — Spring Boot backs off `DataSourceAutoConfiguration` when a `DataSource` @Bean is present, which suppresses `spring.sql.init.*` processing. Instrument at the `JdbcTemplate` call-site instead (see §3); leave HikariCP auto-config untouched. The `JdbcTemplate` itself can be a custom @Bean that wraps the auto-configured `DataSource`.

- **Jedis 7.x vs 5.x** — the `JedisPool(String host, int port)` constructor and `Jedis.set(String, String, SetParams)` API are identical in both. Migration guide shows breaking changes only for cluster/multi-db failover configs (not used here). `JedisPool` still exists in 7.5.0. Pin `7.5.0` (latest stable).

---

## 8. Sources

- [VERIFIED: Maven Central] `redis.clients:jedis:7.5.0` — `https://repo1.maven.org/maven2/redis/clients/jedis/7.5.0/jedis-7.5.0.pom` (confirms 7.5.0 published)
- [VERIFIED: GitHub releases] Jedis release history — `https://api.github.com/repos/redis/jedis/releases` (7.5.0 released 2026-04-27, not a pre-release)
- [VERIFIED: Maven Central] Spring Boot 3.4.13 BOM — `https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.4.13/spring-boot-dependencies-3.4.13.pom` — confirmed: `postgresql.version=42.7.8`, `hikaricp.version=5.1.0`, `testcontainers.version=1.20.6`
- [VERIFIED: Maven Central] Testcontainers 1.20.6 BOM artifact list — no `redis` or `valkey` module exists; `postgresql` module present
- [VERIFIED: GitHub source] `semantic-conventions-java v1.40.0 DbAttributes.java` — `DbSystemNameValues` contains only `MARIADB, MICROSOFT_SQL_SERVER, MYSQL, POSTGRESQL` — `REDIS` is absent
- [VERIFIED: GitHub source] `semantic-conventions-java v1.40.0 DbIncubatingAttributes.java` — `DbSystemNameIncubatingValues.REDIS = "redis"` confirmed; no VALKEY value
- [VERIFIED: GitHub source] `semantic-conventions-java v1.40.0 ServerAttributes.java` — `SERVER_ADDRESS`, `SERVER_PORT` stable
- [VERIFIED: GitHub source] `semantic-conventions-java v1.40.0 NetworkAttributes.java` — `NETWORK_PEER_ADDRESS`, `NETWORK_PEER_PORT` stable
- [VERIFIED: GitHub source] `semantic-conventions-java v1.40.0 DbAttributes.java` — `DB_OPERATION_NAME`, `DB_COLLECTION_NAME`, `DB_QUERY_TEXT`, `DB_NAMESPACE`, `DB_SYSTEM_NAME` all stable
- [VERIFIED: GitHub source] `spring-boot v3.4.13 JdbcContainerConnectionDetailsFactory.java` — accepts `JdbcDatabaseContainer<?>` → `@ServiceConnection` works for `PostgreSQLContainer`
- [VERIFIED: GitHub source] `spring-boot v3.4.13 RedisContainerConnectionDetailsFactory.java` — requires `com.redis.testcontainers.RedisContainer`, NOT `GenericContainer`
- [VERIFIED: GitHub source] `brettwooldridge/HikariCP HikariCP-5.1.0 HikariPoolMXBean.java` — `getActiveConnections()`, `getIdleConnections()`, `getTotalConnections()`, `getThreadsAwaitingConnection()` confirmed
- [VERIFIED: GitHub source] `jedis v7.5.0 SetParams.java` — `nx()` and `ex(long seconds)` methods confirmed present
- [VERIFIED: GitHub source] `jedis v7.5.0 JedisPool.java` — `JedisPool(String host, int port)` constructor confirmed present
- [VERIFIED: Docker Hub] `valkey/valkey` tags — `8.1-alpine` (8.1.6, updated 2026-04-02) is latest stable 8.x alpine tag
- [VERIFIED: Context7 `/redis/jedis`] Jedis pool try-with-resources pattern
- [VERIFIED: Context7 `/open-telemetry/opentelemetry-java`] `ObservableDoubleGauge.buildWithCallback` pattern
