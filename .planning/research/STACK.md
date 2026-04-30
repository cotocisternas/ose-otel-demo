# Stack Research

**Domain:** OpenTelemetry manual SDK instrumentation in Spring Boot 3.4.x (workshop teaching artifact)
**Researched:** 2026-04-29
**Confidence:** HIGH (every version below cross-checked against Maven Central, GitHub releases, or official Spring/Grafana sources within the last 30 days)

---

## Executive Recommendation

Pin to a single, immovable triple: **Spring Boot 3.4.13 + OpenTelemetry Java SDK 1.61.0 + OpenTelemetry Java Instrumentation 2.27.0**. Import all three as BOMs so transitive versions stay coherent. The SDK BOM (`io.opentelemetry:opentelemetry-bom`) governs the SDK + API + OTLP exporter + autoconfigure. The Instrumentation alpha BOM (`io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha`) governs the **Logback appender** (the only instrumentation library this demo needs — context propagation across AMQP is hand-rolled in workshop code, which is the entire pedagogical point).

Run apps on the host via `mise` (Corretto 17 + Maven 3.9). Run RabbitMQ 4.3 and `grafana/otel-lgtm:0.26.0` via `docker compose`. Test with JUnit 5 + Testcontainers `rabbitmq` module 1.20.x.

---

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| **Spring Boot** | `3.4.13` | Application framework for both producer and consumer services | User-pinned. Released 2025-12-18; final 3.4.x patch (open-source EOL of 3.4 line). Compatible with Java 17 baseline. Spring AMQP 3.2.8 + Logback 1.5.22 ship in the BOM — both are exactly what the demo needs. |
| **Java (Amazon Corretto)** | `17` | Runtime + compile target | User-pinned. LTS most enterprise teams target. Corretto is vendor-backed (Amazon), free, available via `mise`. Spring Boot 3.4.x supports Java 17/21/24; 17 is the lowest viable baseline. |
| **Maven** | `3.9.x` (latest 3.9.x recommended; pin to e.g. `3.9.11` for reproducibility) | Build tool | User-pinned. Ubiquitous in enterprise Spring Boot codebases. Spring Boot 3.4 requires Maven 3.6.3+; 3.9.x is the current stable line. |
| **Spring AMQP** | `3.2.8` (managed by Spring Boot 3.4.13 BOM — do not override) | RabbitMQ client wrapper used in producer & consumer | Managed transitively by `spring-boot-starter-amqp`. Provides `RabbitTemplate`, `@RabbitListener`, `MessagePostProcessor` — all the touch points where workshop attendees inject/extract trace context. |
| **OpenTelemetry Java API + SDK** | `1.61.0` (BOM) | Trace, metric, log SDKs and OTLP exporters | Released 2026-04-10. Latest stable as of research date. Stabilized `isEnabled()` on Tracer/Logger/instruments — useful teaching surface. The BOM aligns api, sdk, sdk-extension-autoconfigure, exporter-otlp, and the (deprecated) semconv shim to one number. |
| **OpenTelemetry Java Instrumentation** | `2.27.0` (alpha BOM) | Logback bridge appender (`opentelemetry-logback-appender-1.0`) | Released 2026-04-21. Latest as of research date. `2.x` instrumentation tracks `1.x` SDK — version 2.27.0 is built against and tested with SDK 1.61.0. Only one artifact from this BOM is used in the demo: the Logback appender. Pulled via the **alpha** BOM because the appender artifact still carries the `-alpha` suffix. |
| **OpenTelemetry Semantic Conventions (Java)** | `1.40.0` (`io.opentelemetry.semconv:opentelemetry-semconv`) | Attribute key constants (`HTTP_REQUEST_METHOD`, `MESSAGING_SYSTEM`, etc.) | Released 2026-02-19. This is the **new** semconv coordinate (`io.opentelemetry.semconv` group) — a separate repo from the SDK. The legacy `io.opentelemetry:opentelemetry-semconv` shipped in the SDK BOM is **deprecated**; do not use it. Constants matter because messaging attributes (`messaging.system`, `messaging.destination.name`, `messaging.operation`) are exactly the ones attendees will need for the AMQP propagation lesson. |
| **Grafana otel-lgtm** | `grafana/otel-lgtm:0.26.0` | All-in-one OTLP backend (Collector + Tempo + Prometheus + Loki + Grafana + Pyroscope) | Released 2026-04-24. Single container exposing one OTLP endpoint (`:4317` gRPC, `:4318` HTTP) and Grafana on `:3000`. As of v0.24.0 the bundled OTel Collector exposes `otlp_http` (renamed from `otlphttp`) — relevant if attendees inspect collector config. Bundled: Collector v0.150+, Tempo v2.10+, Prometheus v3.11+, Loki v3.7+, Grafana v13. |
| **RabbitMQ** | `rabbitmq:4.3-management` (or pin to `4.3.0-management`) | AMQP 0-9-1 broker | Latest stable management line (4.3.0 released April 2026). The `-management` variant includes the web UI on `:15672`, useful for workshop demos. Supports Spring AMQP 3.2.8. |
| **JUnit Jupiter** | `5.11.x` (managed by Spring Boot 3.4.13) | Test framework | Default in `spring-boot-starter-test`; no override needed. |
| **Testcontainers** | `1.20.4` (or latest 1.20.x as of research date; `2.0.x` line also available) | Integration test infrastructure (RabbitMQ container) | Spring Boot 3.4.13 BOM manages a Testcontainers version. Use the BOM-managed version unless you need a specific feature; `org.testcontainers:rabbitmq` is the module. |

### Supporting Libraries

These are the **exact OTel artifacts** to import (versions managed by the two BOMs above — never pin them individually):

| Library | GroupId:ArtifactId | Source BOM | Purpose | When to Use |
|---------|-------------------|------------|---------|-------------|
| OTel API | `io.opentelemetry:opentelemetry-api` | `opentelemetry-bom` | `Tracer`, `Meter`, `Span`, `Context` | Always — every line of instrumentation code touches the API |
| OTel SDK | `io.opentelemetry:opentelemetry-sdk` | `opentelemetry-bom` | `OpenTelemetrySdk`, `SdkTracerProvider`, `SdkMeterProvider`, `SdkLoggerProvider` | At startup wiring (one `@Configuration` per service) |
| OTLP Exporter | `io.opentelemetry:opentelemetry-exporter-otlp` | `opentelemetry-bom` | gRPC OTLP exporters for traces, metrics, and logs (single artifact covers all three signals) | Always for this demo (otel-lgtm speaks OTLP) |
| Autoconfigure | `io.opentelemetry:opentelemetry-sdk-extension-autoconfigure` | `opentelemetry-bom` | Reads `OTEL_*` env vars / system properties to configure resource, exporters, samplers | Strongly recommended — lets attendees toggle endpoints with `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317` rather than recompiling. Combine with `AutoConfiguredOpenTelemetrySdk.builder()` for programmatic customization where needed |
| Logback Appender | `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` | `opentelemetry-instrumentation-bom-alpha` | Bridges Logback `ILoggingEvent` → OTLP log records, stamping `trace_id`/`span_id` from active context | The ONLY instrumentation-bom artifact used. Configured in `logback-spring.xml` via class `io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender`. Wired to the SDK programmatically with `OpenTelemetryAppender.install(openTelemetrySdk)` after SDK construction. |
| Semconv | `io.opentelemetry.semconv:opentelemetry-semconv` | (none — pin directly to `1.40.0`) | Stable attribute constants (`HttpAttributes.HTTP_REQUEST_METHOD`, `MessagingAttributes.MESSAGING_SYSTEM`, etc.) | Always — using string literals defeats the teaching point about the spec |
| Semconv Incubating | `io.opentelemetry.semconv:opentelemetry-semconv-incubating` | (none — pin directly to `1.40.0-alpha`) | Experimental constants not yet promoted to stable | Optional — only if attendees want to demo attributes still in RC (e.g., some Kubernetes attributes promoted to RC in 2026) |

**Spring Boot starters** (Spring Boot BOM manages all transitive versions):

| Starter | Purpose |
|---------|---------|
| `spring-boot-starter-web` | HTTP API in producer service (`POST /orders`); brings Tomcat + Jackson + Spring MVC |
| `spring-boot-starter-amqp` | Spring AMQP + `amqp-client`; both producer and consumer |
| `spring-boot-starter-actuator` | `/actuator/health` for compose healthchecks; observability surface attendees know |
| `spring-boot-starter-test` | JUnit 5, AssertJ, Mockito, Spring TestContext — used in unit tests |
| `spring-boot-testcontainers` | `@ServiceConnection` integration with Testcontainers (Spring Boot 3.1+) — wires `RabbitMQContainer` into Spring properties automatically |

**Test-time artifacts:**

| Artifact | Purpose |
|----------|---------|
| `org.testcontainers:junit-jupiter` | `@Testcontainers`/`@Container` JUnit 5 integration |
| `org.testcontainers:rabbitmq` | `RabbitMQContainer` — spins up `rabbitmq:4.3-management-alpine` |
| `io.opentelemetry:opentelemetry-sdk-testing` | `InMemorySpanExporter`, `InMemoryMetricReader`, `InMemoryLogRecordExporter` — let tests assert exported telemetry without network |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| **mise** (formerly rtx) | JDK + Maven version pinning per project | `mise.toml` is source of truth. Activates JDK + Maven on `cd` into project. Sets `JAVA_HOME` automatically. |
| **Docker Compose v2** | Infra (`docker compose up`) | Modern subcommand form (no hyphen). Spring Boot 3.4 + `docker-compose.yml` autodetect works, but this demo runs apps on the host so the autodetect is not used. |
| **curl / httpie** | Hit `POST /orders` from terminal | Workshop README will provide one-liners |
| **Grafana UI** | Inspect traces (Tempo), metrics (Prometheus/Mimir), logs (Loki), correlation across signals | Pre-wired datasources in otel-lgtm; access at `http://localhost:3000`, default `admin/admin` |
| **RabbitMQ Management UI** | Inspect queues, connections, message headers (`traceparent` injection!) | `http://localhost:15672`, default `guest/guest` (loopback-only by default; map port appropriately) |

---

## Concrete Configuration Files

### `mise.toml` (project root)

```toml
[tools]
java = "corretto-17"
maven = "3.9"

[env]
# Optional: set once for both producer and consumer
JAVA_OPTS = "-Xms256m -Xmx512m"

[tasks.dev]
description = "Run producer + consumer locally on host"
run = [
  "mvn -pl producer -am spring-boot:run &",
  "mvn -pl consumer -am spring-boot:run"
]

[tasks."infra:up"]
description = "Start RabbitMQ + otel-lgtm"
run = "docker compose up -d"

[tasks."infra:down"]
description = "Stop infra"
run = "docker compose down -v"

[tasks.test]
description = "Run all tests including Testcontainers"
run = "mvn verify"
```

Verify the Corretto plugin name with `mise ls-remote java | grep corretto-17`. The exact ID may be `corretto-17`, `corretto-17.0.13.11.1`, etc. — pinning to a precise patch (e.g. `corretto-17.0.13.11.1`) is more reproducible than bare `corretto-17`.

### `docker-compose.yml` (project root)

```yaml
services:
  rabbitmq:
    image: rabbitmq:4.3-management
    container_name: ose-otel-rabbitmq
    ports:
      - "5672:5672"     # AMQP
      - "15672:15672"   # Management UI
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 5s
      timeout: 5s
      retries: 10

  lgtm:
    image: grafana/otel-lgtm:0.26.0
    container_name: ose-otel-lgtm
    ports:
      - "3000:3000"     # Grafana UI
      - "4317:4317"     # OTLP gRPC
      - "4318:4318"     # OTLP HTTP
    environment:
      # Default admin/admin in v0.26.0
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
```

### `pom.xml` BOM section (parent or each service POM)

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>3.4.13</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry</groupId>
      <artifactId>opentelemetry-bom</artifactId>
      <version>1.61.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>io.opentelemetry.instrumentation</groupId>
      <artifactId>opentelemetry-instrumentation-bom-alpha</artifactId>
      <version>2.27.0-alpha</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

> NOTE on the alpha BOM coordinate: the BOM itself is published as `2.27.0-alpha` (matching the suffix on its constituent artifacts). The non-alpha `opentelemetry-instrumentation-bom:2.27.0` exists too but does **not** include the Logback appender — that artifact ships only via the alpha BOM. Use the alpha BOM.

### `pom.xml` runtime dependencies (per service)

```xml
<dependencies>
  <!-- Spring Boot -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>

  <!-- OpenTelemetry SDK (versions from BOMs) -->
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-extension-autoconfigure</artifactId>
  </dependency>

  <!-- Semantic conventions (NEW coordinate; pin directly) -->
  <dependency>
    <groupId>io.opentelemetry.semconv</groupId>
    <artifactId>opentelemetry-semconv</artifactId>
    <version>1.40.0</version>
  </dependency>

  <!-- Logback appender (only instrumentation artifact we use) -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
  </dependency>

  <!-- Test -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>rabbitmq</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk-testing</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

### `logback-spring.xml` (per service, in `src/main/resources/`)

```xml
<configuration>
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - trace_id=%X{trace_id} span_id=%X{span_id} %msg%n</pattern>
    </encoder>
  </appender>

  <appender name="OTEL"
            class="io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender">
    <captureExperimentalAttributes>false</captureExperimentalAttributes>
    <captureMdcAttributes>*</captureMdcAttributes>
    <captureCodeAttributes>true</captureCodeAttributes>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="OTEL"/>
  </root>
</configuration>
```

The appender is `install()`ed programmatically against the `OpenTelemetrySdk` after construction — workshop attendees will write that line themselves.

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| OTel Java SDK manually wired | OpenTelemetry Java Agent (`-javaagent:opentelemetry-javaagent.jar`) | When the goal is "ship working observability fast" and zero code changes — but defeats the entire workshop premise. Out of scope per `PROJECT.md`. |
| OTel Java SDK manually wired | `micrometer-tracing-bridge-otel` | When the team already invests in Micrometer Observation API and just needs OTLP egress. Hides the SDK behind Micrometer abstractions — wrong for a workshop teaching the SDK. Out of scope per `PROJECT.md`. |
| OTel Java SDK manually wired | `opentelemetry-spring-boot-starter` | When you want auto-instrumentation of Spring MVC + JDBC + Reactor at startup with one dependency. Wraps the agent and starter machinery — opaque to attendees. Out of scope per `PROJECT.md`. |
| Spring AMQP (`spring-boot-starter-amqp`) | `com.rabbitmq:amqp-client` directly | When you need AMQP 1.0 (Spring AMQP is 0-9-1), or maximum control over channel/connection lifecycle. Spring AMQP is what 99% of Spring teams write — out of scope per `PROJECT.md`. |
| Grafana otel-lgtm single container | OTel Collector + Tempo + Mimir + Loki + Grafana as 5 separate compose services | When you want production-realistic compose for staging environments, or to teach the Collector pipeline as a separate lesson. The single-container distro intentionally collapses these for workshop simplicity. |
| Grafana otel-lgtm single container | Jaeger (traces only) + Prometheus (metrics only) | When you only care about one signal. We need all three — otel-lgtm covers all three with one OTLP endpoint. |
| Amazon Corretto 17 | Eclipse Temurin / Oracle / Liberica / GraalVM CE 17 | Temurin is the most popular alternative and is fungible with Corretto for this demo. Corretto chosen by user. |
| Maven 3.9.x | Gradle 8.x | When the team prefers Kotlin/Groovy DSL and incremental compilation speed. Maven chosen by user; both are first-class in Spring Boot 3.4. |
| Testcontainers `rabbitmq` module | Embedded broker (e.g., `qpid-broker-j`) | When you need millisecond startup. Testcontainers RabbitMQ pulls a real broker — slower per test class, but the trace propagation we're testing depends on real AMQP semantics. |
| OTLP gRPC exporter (port 4317) | OTLP HTTP exporter (port 4318) | If a workshop attendee's network blocks gRPC. otel-lgtm exposes both. gRPC is the demo default because it's slightly more efficient and what most teams ship. |

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| `opentelemetry-javaagent` (the `-javaagent:` JAR) | Auto-instruments at bytecode level — hides the SDK calls the workshop is built to expose. Explicitly out of scope per `PROJECT.md`. | Manual SDK setup with `OpenTelemetrySdk.builder()` |
| `io.micrometer:micrometer-tracing-bridge-otel` | Routes spans through Micrometer's Observation API rather than the OTel SDK directly. Out of scope per `PROJECT.md`. | Direct `Tracer`/`Span` usage from `io.opentelemetry.api` |
| `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` | Convenience wrapper that pulls in agent-style auto-instrumentation. Out of scope per `PROJECT.md`. | Hand-rolled `@Configuration` class building `OpenTelemetrySdk` |
| `io.opentelemetry:opentelemetry-semconv` (legacy coordinate, in the SDK BOM) | Deprecated in favor of the standalone `io.opentelemetry.semconv:*` artifacts maintained in `semantic-conventions-java`. The legacy artifact is still published but stale and will eventually be removed. | `io.opentelemetry.semconv:opentelemetry-semconv:1.40.0` (and `-incubating` if needed) |
| `io.opentelemetry.exporter:opentelemetry-exporter-jaeger` (deprecated) | Jaeger thrift exporter; OTel project deprecated and is removing Zipkin/Jaeger exporters (Zipkin's final release scheduled for August 2026, Jaeger already gone). | `opentelemetry-exporter-otlp` — Tempo/Jaeger backends both speak OTLP now. |
| Spring Boot 3.3.x or 3.5.x | User pinned to 3.4.13. 3.3.x is fine technically; 3.5.x would change managed Logback/Spring AMQP versions and require revalidation. | Stay on `3.4.13`. (Note: 3.4.13 is the **last** community release of 3.4.x; if the demo lives past late 2026 plan a 3.5.x bump.) |
| `rabbitmq:3-management` (3.x line) | RabbitMQ 3.x is in maintenance only as of 2026; 4.x is the current stable line and is what new deployments will use. Spring AMQP 3.2.x supports both. | `rabbitmq:4.3-management` |
| `grafana/otel-lgtm:latest` | Floating tag — workshop reproducibility breaks every time Grafana publishes a new image. | Pin to `grafana/otel-lgtm:0.26.0` |
| Embedded H2 / in-memory message brokers in tests | The whole point of the Testcontainers lesson is "test against real infra". | `org.testcontainers:rabbitmq` with a real `rabbitmq:4.3-management-alpine` container |
| Mixing OTLP HTTP/protobuf and gRPC exporters in the same app | Adds a config dimension attendees have to keep straight; only one transport is needed. | gRPC only (`OTEL_EXPORTER_OTLP_PROTOCOL=grpc`, endpoint `http://localhost:4317`) |
| Logging via `logback.xml` | Spring Boot loads `logback-spring.xml` with profile-aware `<springProfile>` support; `logback.xml` bypasses that. | `logback-spring.xml` |
| `opentelemetry-extension-annotations` (deprecated) | Replaced by `opentelemetry-instrumentation-annotations` years ago. | If you want `@WithSpan`, use `opentelemetry-instrumentation-annotations` — but the demo deliberately avoids this in favor of explicit `tracer.spanBuilder()` calls. |

---

## Stack Patterns by Variant

**If a workshop attendee's machine blocks gRPC outbound on `:4317`:**
- Switch the OTLP exporter protocol to HTTP/protobuf
- Set `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf` and `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318`
- otel-lgtm exposes both — no docker-compose change needed
- Because: gRPC is the default but HTTP works identically for this demo's volume

**If an attendee wants to skip Docker for RabbitMQ (e.g., uses native RabbitMQ via Homebrew/apt):**
- Skip `mise run infra:up` for the RabbitMQ service
- Set `spring.rabbitmq.host=localhost` (default) and ensure the local broker matches version 4.x
- Because: the demo's instrumentation lessons are broker-version-agnostic for AMQP 0-9-1

**If the workshop wants to extend to traces-only (skip metrics+logs lessons):**
- Drop `opentelemetry-logback-appender-1.0` and the Logback configuration
- Skip the `SdkMeterProvider` and `SdkLoggerProvider` wiring
- Because: each signal SDK provider is independent; you can ship a traces-only OTel SDK setup as a stripped-down checkpoint

**If running on Apple Silicon (arm64):**
- All three images (`rabbitmq:4.3-management`, `grafana/otel-lgtm:0.26.0`, Corretto 17) ship native arm64 builds
- No `--platform linux/amd64` workaround needed
- Because: as of 2026 all three projects publish multi-arch manifests by default

---

## Version Compatibility Matrix

| Component A | Compatible With | Notes |
|-------------|-----------------|-------|
| `spring-boot:3.4.13` | `Java 17, 21, 24` | We use Java 17 (LTS, user-pinned) |
| `spring-boot:3.4.13` | `spring-amqp:3.2.8` (managed) | Don't override — Spring Boot tested this combo |
| `spring-boot:3.4.13` | `logback:1.5.22` (managed) | Logback 1.5.x required by the OTel logback appender 2.27.0 (needs Logback ≥ 1.0; 1.5.x works) |
| `opentelemetry-bom:1.61.0` | `opentelemetry-instrumentation-bom-alpha:2.27.0-alpha` | Instrumentation 2.27.0 was released 11 days after SDK 1.61.0 and is built/tested against it |
| `opentelemetry-bom:1.61.0` | `opentelemetry-semconv:1.40.0` | Independent release lines but coordinated; 1.40.0 (Feb 2026) covers all attribute keys SDK 1.61.0 references |
| `opentelemetry-logback-appender-1.0:2.27.0-alpha` | `logback-classic:1.5.x` | Artifact name suffix `-1.0` refers to Logback 1.0+ API compatibility; works with 1.5.x |
| `testcontainers:rabbitmq` (BOM-managed) | `rabbitmq:4.3-management` | Testcontainers' default `RabbitMQContainer` image is overridable via `.withImageName()` if you want to match production |
| `grafana/otel-lgtm:0.26.0` | `OTLP gRPC :4317`, `OTLP HTTP :4318` | Endpoints unchanged across recent versions; safe pin |
| `Java 17 (Corretto)` | `Maven 3.9.x` | Maven 3.9 requires Java 8+; Corretto 17 satisfies easily |
| `Spring AMQP 3.2.8` | `RabbitMQ 4.x` | AMQP 0-9-1 wire compat is unchanged; Spring AMQP 3.2 supports both 3.x and 4.x brokers |

---

## Confidence Assessment

| Recommendation | Confidence | Source |
|----------------|------------|--------|
| Spring Boot 3.4.13 final 3.4.x patch, EOL announced | HIGH | Official Spring blog 2025-12-18 |
| Spring AMQP 3.2.8 + Logback 1.5.22 + Netty 4.1.130.Final pinned in 3.4.13 BOM | HIGH | Direct read of `spring-boot-dependencies/build.gradle@v3.4.13` via GitHub API |
| OTel Java SDK 1.61.0 latest stable | HIGH | GitHub Releases API, published 2026-04-10 |
| OTel Instrumentation 2.27.0 latest stable | HIGH | GitHub Releases API, published 2026-04-21 |
| Logback appender ships in `-alpha` BOM only | HIGH | Maven Central artifact listing + OTel instrumentation README |
| Semconv `io.opentelemetry.semconv:1.40.0` is current | HIGH | GitHub Releases API, published 2026-02-19 |
| Legacy `io.opentelemetry:opentelemetry-semconv` deprecated | HIGH | OTel docs + repo split into `semantic-conventions-java` |
| `grafana/otel-lgtm:0.26.0` latest | HIGH | GitHub Releases API, published 2026-04-24 |
| RabbitMQ 4.3.0 latest stable | MEDIUM-HIGH | Docker Hub tags + RabbitMQ release notes (cross-checked) |
| Testcontainers `rabbitmq` module pulled via Spring Boot BOM | HIGH | Spring Boot 3.4 BOM manages `org.testcontainers:*` versions |
| `mise.toml` syntax for `corretto-17` + `maven` | MEDIUM | Verified via mise official docs; exact patch ID may need `mise ls-remote java` to resolve |
| RabbitMQ image tag `rabbitmq:4.3-management` not floating-broken across patch updates | MEDIUM | Tag stability convention — pin to `4.3.0-management` for absolute reproducibility |

---

## Sources

- [Spring Boot 3.4.13 release announcement (2025-12-18)](https://spring.io/blog/2025/12/18/spring-boot-3-4-13-available-now/) — release date, EOL of 3.4.x line
- [Spring Boot 3.4.13 BOM (`spring-boot-dependencies/build.gradle`)](https://github.com/spring-projects/spring-boot/blob/v3.4.13/spring-boot-project/spring-boot-dependencies/build.gradle) — Spring AMQP 3.2.8, Logback 1.5.22, Netty 4.1.130.Final, Micrometer 1.14.14
- [opentelemetry-java releases (GitHub API)](https://github.com/open-telemetry/opentelemetry-java/releases) — SDK 1.61.0 (2026-04-10) latest
- [opentelemetry-java-instrumentation releases (GitHub API)](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases) — Instrumentation 2.27.0 (2026-04-21) latest
- [semantic-conventions-java releases (GitHub API)](https://github.com/open-telemetry/semantic-conventions-java/releases) — semconv 1.40.0 (2026-02-19) latest
- [opentelemetry-bom on Maven Central](https://central.sonatype.com/artifact/io.opentelemetry/opentelemetry-bom) — confirmed 1.61.0
- [opentelemetry-instrumentation-bom-alpha on Maven Central](https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/opentelemetry-instrumentation-bom-alpha) — confirmed alpha BOM publication
- [opentelemetry-logback-appender-1.0 README](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/logback/logback-appender-1.0/library/README.md) — appender configuration and `OpenTelemetryAppender.install()` usage
- [Context7 `/open-telemetry/opentelemetry-java`](https://context7.com/open-telemetry/opentelemetry-java) — `AutoConfiguredOpenTelemetrySdk` builder API; manual `SdkTracerProvider` + `BatchSpanProcessor` + `OtlpGrpcSpanExporter` setup
- [grafana/docker-otel-lgtm releases (GitHub API)](https://github.com/grafana/docker-otel-lgtm/releases) — v0.26.0 (2026-04-24) latest; bundled component versions
- [Grafana otel-lgtm documentation](https://grafana.com/docs/opentelemetry/docker-lgtm/) — image usage and exposed ports
- [RabbitMQ Docker Hub tags](https://hub.docker.com/_/rabbitmq/tags) — `4.3.0-management` and `4.3-management` available
- [mise Java language docs](https://mise.jdx.dev/lang/java.html) — `corretto-17` plugin usage, `mise.toml` syntax
- [Testcontainers RabbitMQ module](https://java.testcontainers.org/modules/rabbitmq/) — `RabbitMQContainer` API, `org.testcontainers:rabbitmq` coordinate
- `PROJECT.md` (this repo) — constraints driving stack selection (no agent, no Micrometer bridge, Spring AMQP, Corretto 17, Maven, mise)

---
*Stack research for: OpenTelemetry manual SDK instrumentation in Spring Boot 3.4.13 (workshop demo)*
*Researched: 2026-04-29*
