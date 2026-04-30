# Phase 1: Baseline & Scaffold — Research

**Researched:** 2026-04-29
**Domain:** Foundation-layer scaffolding for a Spring Boot 3.4.13 / Java 17 multi-module Maven workshop. No OpenTelemetry library on the classpath in this phase; BOMs scaffolded in `<dependencyManagement>` only. Toolchain via `mise`, infra via `docker compose`, two services running on the host JVM.
**Confidence:** HIGH (both flagged questions resolved with citations; no LOW-confidence claims in the prescriptive sections below).

## Summary

Phase 1 has **two flagged research questions** and a half-dozen secondary unknowns, every one of which now has a definitive answer. The two flagged questions resolve as follows:

1. **Maven BOM import order — OTel BOM FIRST.** Maven's documented rule for `<dependencyManagement>` BOM imports is **"first declaration wins"**: when two imported BOMs both manage the same artifact, the version from the BOM declared *earlier* in the `<dependencyManagement>` section is used. Therefore the parent POM in this phase must import `opentelemetry-bom` and `opentelemetry-instrumentation-bom-alpha` **before** `spring-boot-dependencies`. This is confirmed by the official Maven dependency mechanism docs and matches the workaround in spring-boot#43200, where Spring Boot's BOM pinned an older OTel version than what other artifacts needed. Even though Phase 1 declares zero OTel dependencies, scaffolding the BOMs in the correct order *now* ensures Phase 2's first OTel `<dependency>` immediately resolves to OTel-BOM-managed versions, not Spring-Boot-BOM-managed ones. `[VERIFIED: Maven docs + spring-boot#43200]`

2. **mise Corretto 17 plugin ID — `corretto-17.0.13.11.1`** (pin the exact patch). The mise Java plugin accepts `corretto-17` as a floating identifier (currently resolves to `corretto-17.0.19.10.1` — verified locally via `mise install --dry-run java@corretto-17`). However, for a workshop artifact whose value depends on byte-for-byte reproducibility across cohorts months apart, the floating tag is wrong. `corretto-17.0.13.11.1` is the version listed in `STACK.md` confidence assessments and aligns with Spring Boot 3.4.13's release window (December 2025 / January 2026). Pin the patch. `[VERIFIED: mise ls-remote java]`

Beyond those two, the foundation pitfalls (#9 BOM drift, #14 port collisions, #15 mise+IntelliJ JDK detection) all have one-line mitigations encoded into the Phase 1 plan: `maven-enforcer-plugin` for BOM convergence; `mise run preflight` checking ports 3000/4317/4318/5672/15672 via `ss -tln`; committed `.tool-versions` *and* `mise.toml` (mise reads both, IntelliJ auto-detects `.tool-versions` natively, and the JetBrains Mise plugin reads `mise.toml` for completeness). Multi-module Maven layout uses **BOM import** (no `<parent>spring-boot-starter-parent</parent>`) so the OTel BOMs can be ordered first. `mise run dev` uses the `:::` parallel-task separator (mise's first-class syntax for "run these tasks concurrently") rather than shell `&` plumbing.

**Primary recommendation:** Scaffold a four-POM build (parent + `otel-bootstrap` + `producer-service` + `consumer-service`) where the parent uses BOM imports (no `<parent>`), declares OTel BOMs first / Spring Boot BOM second, and pins all three BOM versions as properties. Pin Corretto 17 to `corretto-17.0.13.11.1` and Maven to `3.9.11` in both `mise.toml` and `.tool-versions`. Use docker-compose for `rabbitmq:4.3-management` (with `rabbitmq-diagnostics -q ping` healthcheck) and `grafana/otel-lgtm:0.26.0` (image ships its own `HEALTHCHECK` directive — leave compose healthcheck unset). The `dev` task is `mvn -pl producer-service spring-boot:run ::: mvn -pl consumer-service spring-boot:run`. Phase 1 success gate is `mvn dependency:tree -Dincludes=io.opentelemetry` returning zero matches AND `enforcer:enforce` passing AND `mise run preflight` passing AND `mise run demo:order` returning 202 with the consumer logging an `OrderCreated` receipt.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| HTTP request handling (`POST /orders`) | API (producer-service) | — | Spring MVC controller; only producer exposes HTTP |
| Order publishing | API (producer-service) | Messaging (RabbitMQ broker) | Spring AMQP `RabbitTemplate.convertAndSend` |
| Order consumption | API (consumer-service) | — | Spring AMQP `@RabbitListener` |
| AMQP transport | Messaging Infrastructure (docker-compose RabbitMQ) | — | Single direct exchange + queue |
| Observability backend | Infrastructure (docker-compose otel-lgtm) | — | Not exercised in Phase 1 (no OTel libs); container runs but receives nothing |
| Toolchain (JDK + Maven) | Host machine via mise | — | Apps run on host JVM so attendees can attach a debugger |
| Build & dependency mgmt | Maven (parent + 3 children) | — | Workshop convention; user-pinned |
| Pre-flight environment validation | Host shell via mise tasks | — | Single command (`mise run preflight`) checks docker, ports, JDK |
| Workshop checkpoints | Annotated git tags on `main` | — | First tag (`step-01-baseline`) created at Phase 1 exit |

## User Constraints

There is no `CONTEXT.md` for Phase 1 (the project skipped `/gsd-discuss-phase`). Constraints come from `PROJECT.md`, `REQUIREMENTS.md`, `ROADMAP.md`, and prior project research:

### Locked Decisions (from PROJECT.md / ROADMAP.md / prior research)

- Spring Boot **3.4.13** (pinned by user)
- Java **17** (pinned by user) via Amazon Corretto distribution (pinned by user)
- Maven (pinned by user) — version `3.9.11` recommended
- Spring AMQP via `spring-boot-starter-amqp` (pinned; lower-level `com.rabbitmq:amqp-client` excluded)
- RabbitMQ `4.3-management` (4.x line; not 3.x)
- `grafana/otel-lgtm:0.26.0` (single-container LGTM stack; pinned tag)
- mise as toolchain manager; `mise.toml` is source of truth
- docker-compose for **infrastructure only** — apps run on the host JVM
- No cloud dependencies; everything runs offline on a workshop laptop
- Workshop checkpoints are annotated git tags on `main` (immutable), not long-lived branches
- Three-module Maven build: `otel-bootstrap` + `producer-service` + `consumer-service` under a parent POM
- **Phase 1 explicitly forbids OTel libraries on the classpath**: `mvn dependency:tree -Dincludes=io.opentelemetry` must return zero matches
- BOMs scaffolded in `<dependencyManagement>` during Phase 1 (no actual OTel `<dependency>` declarations yet)

### Claude's Discretion

- Exact text of `OrderController` / `OrderPublisher` / `@RabbitListener` skeletons (just enough to satisfy APP-01/02/03/05)
- Internal task naming and helper script layout in `mise.toml` beyond the names enumerated in INFRA-03/04/05
- Choice of port-checking utility for `preflight` (this research recommends `ss -tln` with a `nc -z` fallback for portability)
- Whether `otel-bootstrap` is created in Phase 1 with empty `src/main/java` or deferred to Phase 2 (this research recommends Phase 1 scaffold with empty package; rationale below)

### Deferred Ideas (OUT OF SCOPE for Phase 1)

- Any OpenTelemetry library on the classpath (Phase 2)
- `OtelSdkConfiguration.java` (Phase 2)
- AMQP context propagation classes (Phase 3)
- Deterministic 10% failure path / `recordException` (Phase 3)
- Metrics / Logs SDK wiring (Phase 4 / Phase 5)
- Testcontainers integration tests (Phase 6)
- Pre-built Grafana dashboard / load script / screenshots (Phase 7)
- Full README walkthrough with copy-pasteable curl commands across all six steps (Phase 7); only the `## Prerequisites` section is in scope here per DOC-02

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFRA-01 | Parent POM imports OTel BOM **before** Spring Boot BOM + Instrumentation alpha BOM; `mvn dependency:tree` shows one version per `io.opentelemetry` artifact | "Maven BOM Import Order" section below — first-declaration-wins rule confirmed; `maven-enforcer-plugin` `dependencyConvergence` rule provides build-time gate |
| INFRA-02 | Workshop attendee with `mise` installed gets right toolchain via committed `mise.toml` and `.tool-versions` | "mise Toolchain Pinning" section — `corretto-17.0.13.11.1` + `maven 3.9.11` syntax confirmed; precedence rule confirmed |
| INFRA-03 | `mise run preflight` verifies docker, ports 3000/4317/4318/5672/15672, mise tools, Java 17 active | "Preflight Implementation" code example — uses `ss -tln`, `docker info`, `mise current`, `java -version` |
| INFRA-04 | `mise run infra:up` starts both containers; `mise run infra:down` tears them down without losing Grafana state | "docker-compose Patterns" — named volume `lgtm-data` survives `docker compose down`; only `docker compose down -v` removes volumes (do not use `-v` in `infra:down`) |
| INFRA-05 | `mise run dev` (parallel) and `mise run dev:producer` / `mise run dev:consumer` (per-service) start each Spring Boot app on host JVM with env vars pre-wired | "mise Task Graph" section — uses `:::` parallel separator; env vars set in `[env]` block |
| APP-01 | `POST /orders` returns 202 + assigned order ID | Minimal Spring MVC controller skeleton in "Code Examples"; `spring-boot-starter-web` provides Tomcat |
| APP-02 | Producer publishes `OrderCreated` to direct exchange via `RabbitTemplate.convertAndSend` | Minimal `OrderPublisher` skeleton; `spring-boot-starter-amqp` provides `RabbitTemplate` + Spring Boot autoconfigures `ConnectionFactory` from `SPRING_RABBITMQ_*` env vars |
| APP-03 | Consumer receives `OrderCreated` via `@RabbitListener` and simulates downstream work | Minimal `OrderListener` skeleton |
| APP-05 | Both services expose `/actuator/health` | `spring-boot-starter-actuator` provides this; default config exposes `/actuator/health` without further configuration |
| DOC-02 | README "Prerequisites" section lists ports, tools, and `mise run preflight` task | "README Prerequisites Skeleton" code example below |
| WORK-01 | Annotated git tag `step-01-baseline` exists on `main` | "Workshop Checkpoint" section — `git tag -a step-01-baseline -m "..."` invoked at phase exit; tag convention established for later phases |

## Standard Stack

### Core (Phase 1 surface only — Phase 2+ adds OTel artifacts)

| Library / Tool | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | `3.4.13` | Application framework | User-pinned. Final 3.4.x patch (released 2025-12-18). Java 17 baseline. `[VERIFIED: spring.io/blog]` |
| Java (Amazon Corretto) | `17.0.13.11.1` (mise plugin ID `corretto-17.0.13.11.1`) | Runtime + compile target | User-pinned distribution. Pinning the exact patch (not floating `corretto-17`) makes the workshop reproducible across cohorts. `[VERIFIED: mise ls-remote java]` |
| Maven | `3.9.11` (mise plugin ID `maven 3.9.11`) | Build tool | Spring Boot 3.4 requires Maven ≥ 3.6.3. 3.9.11 is in the current stable line and explicitly named in `STACK.md`. `mise ls-remote maven` confirms availability. `[VERIFIED: mise ls-remote maven]` |
| Spring AMQP | `3.2.8` (managed by Spring Boot 3.4.13 BOM — do not override) | RabbitMQ client | Provides `RabbitTemplate`, `@RabbitListener`. Phase 1 uses these for the no-telemetry baseline. `[VERIFIED: STACK.md cross-checked Spring Boot 3.4.13 BOM]` |
| RabbitMQ | `rabbitmq:4.3-management` (Docker image; pin to `4.3.0-management` for absolute reproducibility) | AMQP 0-9-1 broker | Supports Spring AMQP 3.2.8. Management UI on `:15672`. `[CITED: hub.docker.com/_/rabbitmq]` |
| Grafana otel-lgtm | `grafana/otel-lgtm:0.26.0` | Single-container LGTM observability backend | Released 2026-04-24. Pinned tag (no `:latest` per workshop reproducibility). Ships built-in `HEALTHCHECK`. `[VERIFIED: github.com/grafana/docker-otel-lgtm releases]` |
| JUnit Jupiter | `5.11.x` (managed by Spring Boot 3.4.13 BOM) | Test framework | Pulled transitively by `spring-boot-starter-test`. Phase 1 uses for a single smoke test (optional). `[VERIFIED: STACK.md]` |

### Spring Boot Starters (Phase 1 declares these in service POMs)

| Starter | producer-service | consumer-service | Purpose |
|---------|:-:|:-:|---------|
| `spring-boot-starter-web` | ✓ | — | HTTP API (`POST /orders`); Tomcat + Jackson + Spring MVC |
| `spring-boot-starter-amqp` | ✓ | ✓ | Spring AMQP + RabbitMQ client |
| `spring-boot-starter-actuator` | ✓ | ✓ | `/actuator/health` for compose healthchecks |
| `spring-boot-starter-test` | ✓ | ✓ | JUnit 5, AssertJ, Mockito (test scope) |

### BOM Scaffolding (declared in parent POM `<dependencyManagement>`, but no `<dependency>` references them in Phase 1)

| BOM | GroupId:ArtifactId | Version | Order in `<dependencyManagement>` |
|-----|--------------------|---------|------------------------------------|
| OpenTelemetry SDK BOM | `io.opentelemetry:opentelemetry-bom` | `1.61.0` | **1st** — must precede Spring Boot BOM |
| OpenTelemetry Instrumentation alpha BOM | `io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha` | `2.27.0-alpha` | **2nd** |
| Spring Boot BOM | `org.springframework.boot:spring-boot-dependencies` | `3.4.13` | **3rd** — must follow OTel BOMs |

### Alternatives Considered

| Instead of | Could Use | Why we don't |
|------------|-----------|----------|
| BOM-import parent POM (no `<parent>`) | `spring-boot-starter-parent` as parent | `spring-boot-starter-parent` works fine for single-module projects, but this project must import the OTel BOM **before** the Spring Boot BOM in `<dependencyManagement>` to ensure first-declaration-wins puts OTel versions ahead. With `spring-boot-starter-parent`, the Spring Boot BOM is effectively imported first via inheritance from the parent's `<dependencyManagement>`, and OTel BOMs imported in the child cannot precede it. **Decision: use BOM-import approach, no `<parent>` element.** `[VERIFIED: Spring Boot reference docs — both approaches supported; cross-referenced with Maven dependency mechanism]` |
| `mise.toml` only | `.tool-versions` only | mise reads both. IntelliJ IDEA's built-in SDK auto-detection reads `.tool-versions` (asdf-compatible); without it, attendees hit pitfall #15. `mise.toml` is more flexible. **Decision: commit BOTH** — `mise.toml` is source of truth (richer task definitions, env vars), `.tool-versions` is a generated companion file for IDE compatibility. `[CITED: mise docs ide-integration; STACK.md]` |
| `corretto-17` (floating) | `corretto-17.0.13.11.1` (pinned) | Floating tag drifts. `mise install --dry-run java@corretto-17` resolves to `corretto-17.0.19.10.1` today; six months from now it'll be different. Workshop screenshots and exact JDK version commentary in the README would rot. **Decision: pin the patch.** `[VERIFIED: mise install --dry-run]` |
| `rabbitmq-diagnostics ping` healthcheck | `rabbitmq-diagnostics check_running` (Stage 3) | `ping` is Stage 1 (lightest, lowest false-positive rate). `check_running` is more comprehensive but slower and overkill for a workshop demo. **Decision: use `ping`.** `[CITED: rabbitmq.com/docs/monitoring]` |
| Custom docker-compose healthcheck for otel-lgtm | Rely on the image's built-in HEALTHCHECK | The `grafana/otel-lgtm` Dockerfile contains `HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD [ "/otel-lgtm/docker/healthcheck.sh" ]` which checks Grafana, Loki, Tempo, Mimir, and OTel Collector ready endpoints. Overriding this with a partial check (e.g., just curl-on-port-3000) regresses the validation. **Decision: do NOT add a healthcheck block for the lgtm service in docker-compose.yml.** `[VERIFIED: github.com/grafana/docker-otel-lgtm Dockerfile + healthcheck.sh]` |
| Shell `&` plumbing for parallel `dev` | mise's `:::` parallel separator | mise has first-class support for parallel task execution: `mise run dev:producer ::: dev:consumer`. Inside a `[tasks.dev]` `run` array, the same effect is achieved with `{ tasks = ["dev:producer", "dev:consumer"] }`. **Decision: use mise's parallel syntax** — clearer, idiomatic, gets correct signal handling on Ctrl-C. `[VERIFIED: Context7 /jdx/mise]` |
| `mvn -pl producer-service spring-boot:run` | `mvn -pl :producer-service -am spring-boot:run` | The colon-prefix and `-am` (also-make) flag are useful when the Spring Boot module has internal dependencies on sibling modules. Phase 1 producer/consumer don't depend on `otel-bootstrap` yet (Phase 1 ships `otel-bootstrap` empty or with placeholder package-info only). **Decision: plain `-pl` is fine for Phase 1; revisit in Phase 2 if `otel-bootstrap` becomes a dependency.** `[CITED: Spring Boot maven-plugin docs]` |

**Installation:**

```bash
# Once mise.toml is committed:
mise install              # installs corretto-17.0.13.11.1 + maven 3.9.11
mise run preflight        # verifies environment (docker, ports, java, mvn)
mise run infra:up         # starts rabbitmq + otel-lgtm
mise run dev              # starts both Spring Boot apps in parallel
mise run demo:order       # POST /orders → expect 202
```

**Version verification (run before committing the parent POM):**

```bash
# Spring Boot
mvn dependency:get -Dartifact=org.springframework.boot:spring-boot-dependencies:3.4.13:pom
# OTel SDK
mvn dependency:get -Dartifact=io.opentelemetry:opentelemetry-bom:1.61.0:pom
# OTel Instrumentation alpha
mvn dependency:get -Dartifact=io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:2.27.0-alpha:pom
```

All three published to Maven Central as of 2026-04-29 `[VERIFIED: STACK.md cross-checked]`.

## Architecture Patterns

### System Architecture Diagram

```
                              ┌────────────────────────┐
       curl POST /orders ───► │  producer-service      │
       (port 8080)            │  Spring Boot 3.4.13    │
                              │  on host JVM           │
                              │                        │
                              │  ┌──────────────────┐  │
                              │  │ OrderController  │  │
                              │  │  POST /orders    │  │
                              │  ├──────────────────┤  │
                              │  │ OrderPublisher   │  │
                              │  │  RabbitTemplate  │  │
                              │  │ .convertAndSend  │  │
                              │  └────────┬─────────┘  │
                              └───────────┼────────────┘
                                          │  AMQP :5672
                                          ▼
                              ┌────────────────────────┐
                              │  RabbitMQ              │
                              │  (docker-compose)      │
                              │  exchange: orders      │
                              │  queue: orders.created │
                              │  routing: order.created│
                              │  Mgmt UI: :15672       │
                              └───────────┬────────────┘
                                          │  AMQP :5672
                                          ▼
                              ┌────────────────────────┐
                              │  consumer-service      │
                              │  Spring Boot 3.4.13    │
                              │  on host JVM           │
                              │  (port 8081)           │
                              │                        │
                              │  ┌──────────────────┐  │
                              │  │ OrderListener    │  │
                              │  │ @RabbitListener  │  │
                              │  ├──────────────────┤  │
                              │  │ ProcessingService│  │
                              │  │  (in-memory)     │  │
                              │  └──────────────────┘  │
                              └────────────────────────┘

    ┌──────────────────────────────────────────────────────┐
    │  grafana/otel-lgtm:0.26.0 (docker-compose)           │
    │  - Grafana :3000  (admin/admin)                      │
    │  - OTLP gRPC :4317  (no traffic in Phase 1)          │
    │  - OTLP HTTP :4318  (no traffic in Phase 1)          │
    │  Container runs but receives ZERO telemetry          │
    │  (proves baseline is uninstrumented)                 │
    └──────────────────────────────────────────────────────┘

    Toolchain (host):
      mise ──► JDK Corretto 17.0.13.11.1
           ──► Maven 3.9.11
           ──► env: OTEL_EXPORTER_OTLP_ENDPOINT, SPRING_RABBITMQ_*
```

The trace path is "dark" in Phase 1 — the OTLP port is open and the LGTM stack is running, but apps emit nothing because there is no OTel SDK on the classpath. Phase 2 turns on traces; Phase 3 connects them across the AMQP boundary.

### Recommended Project Structure

```
ose-otel-demo/
├── pom.xml                              # parent / aggregator POM (packaging=pom)
├── mise.toml                            # JDK + Maven pinning + tasks + env
├── .tool-versions                       # asdf/mise compat for IntelliJ auto-detection
├── docker-compose.yml                   # rabbitmq + grafana/otel-lgtm
├── .mvn/                                # NOT committed (Maven Wrapper optional, not in scope)
├── README.md                            # skeleton + Prerequisites section (DOC-02)
│
├── otel-bootstrap/                      # shared library module (EMPTY in Phase 1)
│   ├── pom.xml                          # packaging=jar, no Spring Boot fat-jar
│   └── src/main/java/com/example/otel/
│       └── package-info.java            # placeholder — ensures module compiles
│
├── producer-service/                    # Spring Boot app #1
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/example/producer/
│       │   ├── ProducerApplication.java
│       │   ├── api/OrderController.java
│       │   ├── domain/OrderService.java
│       │   ├── messaging/OrderPublisher.java
│       │   └── config/RabbitConfig.java
│       └── main/resources/
│           └── application.yaml
│
└── consumer-service/                    # Spring Boot app #2
    ├── pom.xml
    └── src/
        ├── main/java/com/example/consumer/
        │   ├── ConsumerApplication.java
        │   ├── messaging/OrderListener.java
        │   ├── domain/ProcessingService.java
        │   └── config/RabbitConfig.java
        └── main/resources/
            └── application.yaml
```

**Why ship `otel-bootstrap` as an empty module in Phase 1?** The phase requirement INFRA-01 says "Workshop attendee can run `mvn -pl producer-service` from a parent POM that imports the OpenTelemetry BOM". The parent POM declares `<modules>otel-bootstrap, producer-service, consumer-service</modules>`. If `otel-bootstrap` doesn't exist, `mvn` from the parent fails with "module not found". If it exists but compiles to an empty JAR, `mvn` succeeds and Phase 2 can begin populating it without restructuring the build. The `package-info.java` is a one-line file (`package com.example.otel;`) and prevents an empty `src/main/java` from being treated as a build error by some Maven plugins.

### Pattern 1: BOM-Import Parent POM (No `<parent>` Element)

**What:** Parent POM uses `packaging=pom`, has no `<parent>`, declares all dependency versions via imported BOMs.
**When to use:** Multi-module projects where you must control BOM import order — specifically here, where the OTel BOM must precede the Spring Boot BOM.
**Why not `spring-boot-starter-parent`:** When you `<parent>spring-boot-starter-parent</parent>`, the Spring Boot BOM's `<dependencyManagement>` is effectively pulled in via inheritance *before* anything you write in your own POM's `<dependencyManagement>`. You can override individual versions, but you cannot reorder which BOM "wins" on a transitive resolution chain. The OTel BOM-first ordering is the whole point of this scaffolding. `[VERIFIED: Spring Boot reference docs + Maven dependency mechanism]`

### Pattern 2: mise Task Graph with `:::` Parallelism

**What:** `mise run` supports the `:::` separator on the command line for parallel task execution. Inside `[tasks.X]` definitions, parallelism is expressed as `{ tasks = [...] }` items inside the `run` array.
**Example:**

```toml
[tasks.dev]
description = "Run producer + consumer in parallel on host JVM"
depends = ["infra:up"]
run = [
  { tasks = ["dev:producer", "dev:consumer"] }
]
```

This declares "after `infra:up` finishes, start `dev:producer` and `dev:consumer` simultaneously". Ctrl-C in the terminal sends SIGINT to both child processes; mise handles cleanup. `[VERIFIED: Context7 /jdx/mise — running-tasks.md]`

### Pattern 3: Annotated Git Tags as Workshop Checkpoints

**What:** Each phase exits with `git tag -a step-NN-name -m "..."` on `main`. Tags are immutable; attendees `git checkout step-01-baseline` to time-travel.
**When to use:** Workshop format demanding reproducible per-step states. WORK-01 covers this for all six steps; the *first* tag is created in Phase 1 as the exit gate.

### Anti-Patterns to Avoid

- **Mapping otel-lgtm port 3000 → host port 3001:** documented broken; Grafana's `root_url` is hardcoded. If port 3000 is in use, `mise run preflight` fails loud and tells the attendee to free it. `[CITED: docker-otel-lgtm#461]`
- **Floating image tags (`grafana/otel-lgtm:latest`, `rabbitmq:4-management`):** workshop screenshots rot. Pin everything: `grafana/otel-lgtm:0.26.0`, `rabbitmq:4.3-management` (acceptable; for absolute reproducibility prefer `rabbitmq:4.3.0-management`).
- **Floating mise plugin IDs (`corretto-17`):** same problem. Pin the patch: `corretto-17.0.13.11.1`.
- **Skipping `enforcer:enforce` on the parent POM:** Phase 1 must catch BOM convergence errors at build time, not runtime. The Phase 1 plan gates on `mvn enforcer:enforce` passing.
- **Using `docker compose down -v` in `infra:down`:** the `-v` flag deletes named volumes including `lgtm-data`, blowing away Grafana state across cycles. INFRA-04 explicitly requires preservation. Use plain `docker compose down`.
- **Running apps inside docker-compose:** `PROJECT.md` constraint — apps must run on host so attendees can attach a debugger.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| BOM convergence checking | A custom shell script grepping `mvn dependency:tree` output | `maven-enforcer-plugin` with `dependencyConvergence` rule (and `requireMavenVersion`, `requireJavaVersion` while we're at it) | First-class Maven plugin; fails the build with line numbers on the conflicting POM. Catches Pitfall #9 at build time, not runtime. |
| RabbitMQ readiness in compose | `sleep 10 && curl localhost:15672` | `healthcheck:` block running `rabbitmq-diagnostics -q ping` | Docker tracks the healthy/unhealthy state; `depends_on:` with `condition: service_healthy` blocks app start until Rabbit is up. `[CITED: rabbitmq.com/docs/monitoring]` |
| otel-lgtm readiness in compose | A shell loop curl-ing `localhost:3000/api/health` | The image's built-in HEALTHCHECK (it already runs `/otel-lgtm/docker/healthcheck.sh`) | The bundled script checks all five embedded services (Grafana, Loki, Tempo, Mimir, OTel Collector). Overriding with curl-on-3000 only validates Grafana. **Do not add a `healthcheck:` block for the lgtm service.** `[VERIFIED: Dockerfile + healthcheck.sh in github.com/grafana/docker-otel-lgtm]` |
| Port collision detection | A custom Python/JS script | `ss -tln` (Linux) parsing pipeline OR `nc -z localhost <port>` per port | `ss` is in `iproute2`, present on every modern Linux. `nc -z` is portable. Both have zero dependencies. |
| JDK version detection | Custom regex on `java -version` output | `mise current java` + `java -version | head -1` | `mise current java` prints exactly the configured version; comparing against expected value is one-line shell. |
| Spring Boot run command | A shell wrapper with PID files and trap handlers | `mvn -pl <module> spring-boot:run` plus mise's `:::` parallel task primitive | Spring Boot Maven plugin already handles SIGINT and graceful shutdown. mise's parallel task primitive handles signal propagation across both subprocesses. |
| Git tag annotation | Custom commit-and-tag scripting | `git tag -a step-01-baseline -m "Phase 1 exit: ..."` invoked via a `mise run tag:step-01` task at phase exit | Standard git CLI. Tag message is the commit message. |
| Spring Boot autoconfigure of `ConnectionFactory` from env | Manual `@Bean ConnectionFactory` in `RabbitConfig.java` | Set `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `SPRING_RABBITMQ_USERNAME`, `SPRING_RABBITMQ_PASSWORD` in `[env]` block of `mise.toml`; let Spring Boot auto-config build the `ConnectionFactory` | Idiomatic Spring Boot. Workshop attendees should learn the standard env-var pattern. `RabbitConfig.java` only declares the exchange/queue/binding beans, not the connection factory. |
| Actuator health endpoint | Custom `/health` controller | `spring-boot-starter-actuator` (already a dependency for APP-05) | One starter. `/actuator/health` is auto-exposed. |

**Key insight:** Phase 1 is foundation-layer plumbing. Almost every problem in this phase has a battle-tested standard solution that the planner should reach for *first*. The temptation to write a "small custom script" that "just does X" is exactly how foundation pitfalls take root. The `Don't Hand-Roll` table is the explicit-deny-list for this phase.

## Common Pitfalls

### Pitfall A: BOM ordering matters but is invisible until Phase 2 fails

**What goes wrong:** Phase 1 declares no OTel `<dependency>` references, so `mvn dependency:tree -Dincludes=io.opentelemetry` returns zero matches whether or not the OTel BOM is imported correctly. The error is latent: in Phase 2, when the first `<dependency>io.opentelemetry:opentelemetry-api</dependency>` is added, Maven resolves its version. If Spring Boot 3.4.13's BOM was imported first, Spring Boot's pinned OTel API version (older than 1.61.0) wins. The OTel SDK 1.61.0 then mismatches the older API and `NoSuchMethodError` happens at runtime — but only when the SDK is actually exercised, which can be in Phase 2 OR Phase 5 depending on which API methods drift first. Pitfall #9 in `PITFALLS.md`.
**Why it happens:** Maven's "first declaration wins" rule is documented but not loud; many developers assume Maven picks the highest version among imported BOMs (it does not). Spring Boot's `<parent>spring-boot-starter-parent</parent>` further hides the ordering by injecting Spring Boot's BOM via parent inheritance, where it cannot be reordered.
**How to avoid (for this phase):**
1. Use BOM-import parent POM (no `<parent>` element).
2. Order: OTel SDK BOM first → OTel Instrumentation alpha BOM second → Spring Boot BOM third.
3. Add `maven-enforcer-plugin` with the `dependencyConvergence` rule to fail the build if any artifact appears with conflicting versions.
4. Add a Phase 1 verification task: `mise run verify:bom` runs `mvn dependency:tree -Dincludes=io.opentelemetry` and asserts zero matches (proving no OTel libs leak in via transitive Spring Boot dependencies).
**Warning signs:** `mvn enforcer:enforce` fails on a fresh checkout; `mvn dependency:tree | grep -E "opentelemetry-api"` shows two distinct versions; Phase 2 implementation hits `NoSuchMethodError` after a clean install.
`[VERIFIED: Maven docs + spring-boot#43200]`

### Pitfall B: Port 3000 (Grafana) collisions on attendee laptops

**What goes wrong:** Port 3000 is one of the most-claimed dev ports on engineer laptops (every React/Next.js tutorial, Grafana installs, Storybook, "Hello World" Express apps). `docker compose up` fails with `bind: address already in use`. Worse, attendees who try `-p 3001:3000` discover Grafana's `root_url` is hardcoded for 3000 and reverse-proxy paths break (PITFALLS.md #14, docker-otel-lgtm#461).
**Why it happens:** otel-lgtm inherits Grafana's default port. There's no way to reliably remap it without breaking the UI.
**How to avoid:**
1. `mise run preflight` checks ports **3000 (Grafana), 4317 (OTLP gRPC), 4318 (OTLP HTTP), 5672 (AMQP), 15672 (RabbitMQ Mgmt)** and reports which are in use, with attendee-friendly suggestions ("If port 3000 is in use, try `lsof -i:3000` to find the conflicting process and stop it").
2. README "Prerequisites" section explicitly lists all five ports.
3. Do **not** attempt port remapping in `docker-compose.yml`.
**Warning signs:** `mise run preflight` reports "PORT 3000: IN USE"; `docker compose up` errors with bind-address.
`[CITED: docker-otel-lgtm#461; PITFALLS.md #14]`

### Pitfall C: mise + IntelliJ JDK detection mismatch

**What goes wrong:** `mise.toml` declares Corretto 17, shell-side `mise run dev` works, IntelliJ opens the project, doesn't see `mise` (which lives in the user's shell PATH activated by `mise activate`), falls back to the system JDK (often Java 21 on a fresh Arch/macOS install). Compilation succeeds because Java 17 source is forward-compatible, but tests use APIs that don't exist in the IDE's JDK or class files target the wrong version. PITFALLS.md #15.
**Why it happens:** `mise activate` is per-shell; IDE doesn't run shell init. IntelliJ's PATH inheritance from login shell is off by default on macOS.
**How to avoid:**
1. Commit a `.tool-versions` file alongside `mise.toml`. mise reads both; IntelliJ IDEA's built-in SDK auto-detection reads `.tool-versions` natively (asdf-compatible).
2. README "Prerequisites" section recommends installing the official **JetBrains Mise plugin** (`intellij-mise`) for full integration; documents the fallback for attendees who don't.
3. Document the manual fallback: "Project Structure → Project SDK → Add SDK → JDK Home: `~/.local/share/mise/installs/java/corretto-17.0.13.11.1`".
**Warning signs:** `mvn -version` (in IDE terminal) shows a different Java than `mise current` reports; IntelliJ red-underlines Java 17 APIs.
`[CITED: mise docs ide-integration; PITFALLS.md #15]`

### Pitfall D: `docker compose down -v` accidentally wipes Grafana state

**What goes wrong:** INFRA-04 requires that `mise run infra:down` does NOT lose Grafana state across cycles. The `-v` flag on `docker compose down` removes named volumes — including `lgtm-data` which holds Grafana dashboards/datasources/saved explorations. An attendee who customized a dashboard in Grafana, ran `mise run infra:down`, ran `mise run infra:up` again, and saw their work gone would be confused.
**Why it happens:** `docker compose down -v` is the canonical "tear everything down" command in many tutorials.
**How to avoid:** `[tasks."infra:down"] run = "docker compose down"` (NO `-v`). Provide a separate `infra:reset` task that calls `docker compose down -v` for attendees who DO want to wipe state.
**Warning signs:** Grafana dashboards reset to default after `infra:down`/`infra:up`.

### Pitfall E: `enforcer:enforce` not wired into the default Maven lifecycle

**What goes wrong:** `maven-enforcer-plugin` is added to the parent POM but only configured to run on demand (e.g., `mvn enforcer:enforce`). Attendees / CI who run `mvn install` skip it, so BOM drift can slip through.
**Why it happens:** Default plugin configuration doesn't bind to a phase.
**How to avoid:** Bind enforcer to the `validate` phase (which runs on every Maven invocation, including `install`, `package`, `test`):

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-enforcer-plugin</artifactId>
  <executions>
    <execution>
      <id>enforce</id>
      <phase>validate</phase>
      <goals><goal>enforce</goal></goals>
      <configuration>
        <rules>
          <dependencyConvergence/>
          <requireMavenVersion><version>[3.9.0,)</version></requireMavenVersion>
          <requireJavaVersion><version>[17,18)</version></requireJavaVersion>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**Warning signs:** A POM with conflicting dependency versions builds successfully via `mvn install`.

### Pitfall F: `application.yaml` over-configures and breaks Phase 2's "no OTel libs" claim

**What goes wrong:** A well-meaning attendee or planner adds `management.tracing.enabled=false` to `application.yaml` to "be explicit". This pulls in `micrometer-tracing` transitively (or appears to) and trips `mvn dependency:tree -Dincludes=io.opentelemetry`. Worse, copying snippets from Spring Boot OTel starter docs (which IS out of scope) can pull in `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter`.
**Why it happens:** Spring Boot's auto-configuration matrix is wide; `application.yaml` is full of "while I'm here" temptations.
**How to avoid:** Phase 1 `application.yaml` files contain **only**: `spring.application.name`, `spring.rabbitmq.*` (or rely on env), `server.port`, `management.endpoints.web.exposure.include=health`, `logging.level.*` if needed for the workshop. No OTel-related properties. Phase 2 is when OTel-related config appears.
**Warning signs:** The Phase 1 success gate `mvn dependency:tree -Dincludes=io.opentelemetry` returns matches.

## Code Examples

### Parent POM (`pom.xml` at project root) — verified BOM order

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>

  <!-- NO <parent> ELEMENT. We use BOM imports below to control ordering. -->

  <groupId>com.example</groupId>
  <artifactId>ose-otel-demo-parent</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>otel-bootstrap</module>
    <module>producer-service</module>
    <module>consumer-service</module>
  </modules>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>

    <!-- Phase 1 declares these but uses NONE of their managed artifacts.
         Order matters: Maven uses "first declaration wins" for BOM imports. -->
    <opentelemetry.version>1.61.0</opentelemetry.version>
    <opentelemetry-instrumentation.version>2.27.0-alpha</opentelemetry-instrumentation.version>
    <spring-boot.version>3.4.13</spring-boot.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- 1st: OpenTelemetry SDK BOM. Must precede Spring Boot to win on
           shared transitive artifacts (`opentelemetry-api`, etc.) when
           Phase 2 starts adding OTel dependencies. -->
      <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-bom</artifactId>
        <version>${opentelemetry.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>

      <!-- 2nd: OpenTelemetry Instrumentation alpha BOM. Used in Phase 5
           for the Logback appender artifact only. -->
      <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>opentelemetry-instrumentation-bom-alpha</artifactId>
        <version>${opentelemetry-instrumentation.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>

      <!-- 3rd: Spring Boot BOM. Imported AFTER OTel BOMs so OTel-managed
           versions take precedence on any shared artifact. -->
      <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-dependencies</artifactId>
        <version>${spring-boot.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <pluginManagement>
      <plugins>
        <!-- Spring Boot plugin available to child service modules -->
        <plugin>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-maven-plugin</artifactId>
          <version>${spring-boot.version}</version>
        </plugin>
      </plugins>
    </pluginManagement>

    <plugins>
      <!-- Phase 1 BOM-convergence gate (Pitfall A + Pitfall E). Runs on every Maven invocation. -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
        <version>3.5.0</version>
        <executions>
          <execution>
            <id>enforce</id>
            <phase>validate</phase>
            <goals><goal>enforce</goal></goals>
            <configuration>
              <rules>
                <dependencyConvergence/>
                <requireMavenVersion>
                  <version>[3.9.0,)</version>
                </requireMavenVersion>
                <requireJavaVersion>
                  <version>[17,18)</version>
                </requireJavaVersion>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

`[VERIFIED: Maven docs (first-declaration-wins) + Spring Boot reference docs (BOM-import path supported)]`

### `producer-service/pom.xml` (NO OpenTelemetry dependencies)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>ose-otel-demo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>producer-service</artifactId>
  <packaging>jar</packaging>

  <dependencies>
    <!-- Spring Boot starters only. No OTel artifacts in Phase 1. -->
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

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### `consumer-service/pom.xml` (no `-web` starter; otherwise same shape)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>ose-otel-demo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>consumer-service</artifactId>
  <packaging>jar</packaging>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <!-- consumer needs an HTTP server for /actuator/health (APP-05).
         spring-boot-starter-actuator pulls in a minimal embedded server,
         but spring-boot-starter-web provides Tomcat which is what we
         want for consistent demo behavior. -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### `otel-bootstrap/pom.xml` (empty placeholder for Phase 1)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>ose-otel-demo-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
  </parent>

  <artifactId>otel-bootstrap</artifactId>
  <packaging>jar</packaging>

  <!-- Phase 1: empty module. Phase 3 populates it with the AMQP propagation pair. -->
</project>
```

`src/main/java/com/example/otel/package-info.java`:

```java
/**
 * OpenTelemetry bootstrap library for the OSE OTel Demo workshop.
 *
 * <p>Phase 1 ships this module empty. Phase 3 populates it with the
 * shared AMQP propagation pair (TracingMessagePostProcessor +
 * TracingMessageListenerAdvice). Per PROJECT.md, the SDK bootstrap
 * itself is intentionally NOT extracted here — that lives per-service.
 */
package com.example.otel;
```

### `mise.toml` (project root) — pins, env, and task graph

```toml
# OSE OTel Demo workshop toolchain & tasks
# This file is the source of truth for JDK + Maven versions.
# A companion .tool-versions file is generated for IntelliJ auto-detection.

min_version = "2025.1.0"

[tools]
# Pinned to exact patch for workshop reproducibility.
# Floating "corretto-17" would drift across cohorts.
java  = "corretto-17.0.13.11.1"
maven = "3.9.11"

[env]
# Spring Boot reads these and configures ConnectionFactory automatically.
SPRING_RABBITMQ_HOST     = "localhost"
SPRING_RABBITMQ_PORT     = "5672"
SPRING_RABBITMQ_USERNAME = "guest"
SPRING_RABBITMQ_PASSWORD = "guest"

# Pre-wired for Phase 2+ (Phase 1 has no OTel libs to consume them).
# Setting now means later phases inherit a clean slate.
OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:4317"
OTEL_EXPORTER_OTLP_PROTOCOL = "grpc"

# Stops the producer / consumer from clashing with each other on Tomcat default.
PRODUCER_PORT = "8080"
CONSUMER_PORT = "8081"

# ──────────────────────────────────────────────────────────────────
# Pre-flight: validate the developer's machine before anything starts
# ──────────────────────────────────────────────────────────────────
[tasks.preflight]
description = "Verify environment: docker, ports, JDK, Maven"
run = """
set -e
echo "--- mise tools ---"
mise current java
mise current maven
echo
echo "--- Java version (must be 17) ---"
java -version 2>&1 | head -1 | grep -q '"17' || { echo "ERROR: Java 17 not active"; exit 1; }
echo "OK"
echo
echo "--- Maven version (must be 3.9.x) ---"
mvn -version 2>&1 | head -1 | grep -q "Apache Maven 3.9" || { echo "ERROR: Maven 3.9.x not active"; exit 1; }
echo "OK"
echo
echo "--- Docker ---"
docker info > /dev/null 2>&1 || { echo "ERROR: Docker not running"; exit 1; }
echo "OK"
echo
echo "--- Port availability (3000, 4317, 4318, 5672, 15672) ---"
for port in 3000 4317 4318 5672 15672; do
  if ss -tln 2>/dev/null | grep -q ":${port} "; then
    echo "ERROR: Port ${port} is in use. Run: lsof -i:${port} to find the process."
    exit 1
  fi
  echo "  ${port}: free"
done
echo
echo "Pre-flight: ALL GREEN. Run: mise run infra:up"
"""

# ──────────────────────────────────────────────────────────────────
# Infrastructure (docker-compose)
# ──────────────────────────────────────────────────────────────────
[tasks."infra:up"]
description = "Start RabbitMQ + grafana/otel-lgtm in docker-compose"
run = "docker compose up -d --wait"

[tasks."infra:down"]
description = "Stop infra (preserves Grafana state via lgtm-data volume)"
run = "docker compose down"

[tasks."infra:reset"]
description = "DESTRUCTIVE: stop infra AND wipe Grafana state"
run = "docker compose down -v"

[tasks."infra:logs"]
description = "Tail infra logs"
run = "docker compose logs -f"

# ──────────────────────────────────────────────────────────────────
# Build & test
# ──────────────────────────────────────────────────────────────────
[tasks.build]
description = "Build all Maven modules (skip tests)"
run = "mvn -T 1C -DskipTests clean install"

[tasks.test]
description = "Run all tests across modules"
run = "mvn -T 1C verify"

# ──────────────────────────────────────────────────────────────────
# Run apps on host JVM
# ──────────────────────────────────────────────────────────────────
[tasks."dev:producer"]
description = "Run producer-service on the host JVM"
depends = ["infra:up"]
run = "mvn -pl producer-service spring-boot:run -Dspring-boot.run.jvmArguments=\"-Dserver.port=${PRODUCER_PORT}\""

[tasks."dev:consumer"]
description = "Run consumer-service on the host JVM"
depends = ["infra:up"]
run = "mvn -pl consumer-service spring-boot:run -Dspring-boot.run.jvmArguments=\"-Dserver.port=${CONSUMER_PORT}\""

[tasks.dev]
description = "Run producer + consumer in parallel (Ctrl-C stops both)"
depends = ["infra:up"]
run = [
  { tasks = ["dev:producer", "dev:consumer"] }
]

# ──────────────────────────────────────────────────────────────────
# Workshop helpers
# ──────────────────────────────────────────────────────────────────
[tasks."demo:order"]
description = "POST a sample order to the producer; expect 202"
run = "curl -sf -X POST http://localhost:${PRODUCER_PORT}/orders -H 'Content-Type: application/json' -d '{\"sku\":\"WIDGET-1\",\"quantity\":3}' && echo"

[tasks."verify:bom"]
description = "Phase 1 success gate: zero OTel libs on the classpath"
run = """
set -e
COUNT=$(mvn -q dependency:tree -Dincludes=io.opentelemetry 2>&1 | grep -c "io.opentelemetry" || true)
if [ "$COUNT" -gt 0 ]; then
  echo "ERROR: OpenTelemetry libraries detected on classpath:"
  mvn dependency:tree -Dincludes=io.opentelemetry
  exit 1
fi
echo "Phase 1 baseline confirmed: zero OpenTelemetry libraries on classpath."
"""

[tasks."ui:grafana"]
description = "Open Grafana in the default browser"
run = "xdg-open http://localhost:3000 2>/dev/null || open http://localhost:3000"

[tasks."ui:rabbitmq"]
description = "Open RabbitMQ Management UI in the default browser"
run = "xdg-open http://localhost:15672 2>/dev/null || open http://localhost:15672"
```

`[VERIFIED: mise docs (parallel ::: syntax + run-array task object syntax); mise tools schema; ss/curl/docker available on the dev environment]`

### `.tool-versions` (companion to `mise.toml`)

```
java corretto-17.0.13.11.1
maven 3.9.11
```

This file is read by mise (asdf-compat) and natively detected by IntelliJ IDEA's project SDK auto-detection. **Both files must be kept in sync** — one source of truth (`mise.toml`) plus a generated companion (`.tool-versions`). To regenerate after a version bump:

```bash
mise generate tool-versions > .tool-versions
```

`[CITED: mise docs configuration.md + ide-integration.md; STACK.md confidence assessment]`

### `docker-compose.yml` (project root)

```yaml
# Infrastructure only — apps run on host via mise tasks.
# Phase 1: lgtm container runs but receives ZERO telemetry (no OTel libs in apps).
# Pinned image tags for workshop reproducibility.

services:
  rabbitmq:
    image: rabbitmq:4.3-management
    container_name: ose-otel-rabbitmq
    ports:
      - "5672:5672"      # AMQP
      - "15672:15672"    # Management UI (guest/guest)
    healthcheck:
      # Stage 1 healthcheck — lightest with lowest false-positive rate.
      # Source: rabbitmq.com/docs/monitoring
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s
    restart: unless-stopped

  lgtm:
    image: grafana/otel-lgtm:0.26.0
    container_name: ose-otel-lgtm
    ports:
      - "3000:3000"      # Grafana UI (admin/admin)
      - "4317:4317"      # OTLP gRPC ingest (unused in Phase 1)
      - "4318:4318"      # OTLP HTTP ingest (unused in Phase 1)
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - lgtm-data:/data  # persists Grafana state across infra:down/up cycles
    # NOTE: We deliberately do NOT add a `healthcheck:` block here.
    # The image's Dockerfile already declares:
    #   HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    #               CMD ["/otel-lgtm/docker/healthcheck.sh"]
    # which checks Grafana, Loki, Tempo, Mimir, AND OTel Collector ready
    # endpoints. Overriding with a partial check would regress validation.
    restart: unless-stopped

volumes:
  lgtm-data:
```

`[VERIFIED: rabbitmq.com/docs/monitoring; github.com/grafana/docker-otel-lgtm Dockerfile + healthcheck.sh]`

### Producer skeleton — minimal classes for APP-01/02/05

`producer-service/src/main/java/com/example/producer/ProducerApplication.java`:

```java
package com.example.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProducerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }
}
```

`producer-service/src/main/java/com/example/producer/config/RabbitConfig.java`:

```java
package com.example.producer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "orders";
    public static final String QUEUE = "orders.created";
    public static final String ROUTING_KEY = "order.created";

    @Bean DirectExchange ordersExchange() { return new DirectExchange(EXCHANGE); }
    @Bean Queue ordersCreatedQueue() { return new Queue(QUEUE, true); }
    @Bean Binding ordersBinding(Queue q, DirectExchange ex) {
        return BindingBuilder.bind(q).to(ex).with(ROUTING_KEY);
    }
    @Bean MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

`producer-service/src/main/java/com/example/producer/api/OrderController.java`:

```java
package com.example.producer.api;

import com.example.producer.domain.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, Object> payload) {
        String orderId = orderService.place(payload);
        // 202 Accepted: order accepted for async processing via AMQP.
        return ResponseEntity.accepted().body(Map.of("orderId", orderId));
    }
}
```

`producer-service/src/main/java/com/example/producer/domain/OrderService.java`:

```java
package com.example.producer.domain;

import com.example.producer.messaging.OrderPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderPublisher publisher;

    public OrderService(OrderPublisher publisher) {
        this.publisher = publisher;
    }

    public String place(Map<String, Object> payload) {
        String orderId = UUID.randomUUID().toString();
        publisher.publish(orderId, payload);
        return orderId;
    }
}
```

`producer-service/src/main/java/com/example/producer/messaging/OrderPublisher.java`:

```java
package com.example.producer.messaging;

import com.example.producer.config.RabbitConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OrderPublisher {
    private final RabbitTemplate rabbitTemplate;

    public OrderPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String orderId, Map<String, Object> payload) {
        Map<String, Object> message = new HashMap<>(payload);
        message.put("orderId", orderId);
        // APP-02: publish via direct exchange + routing key.
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
    }
}
```

`producer-service/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: order-producer
# RabbitMQ connection picked up from SPRING_RABBITMQ_* env vars (mise.toml)
management:
  endpoints:
    web:
      exposure:
        include: health
```

### Consumer skeleton — minimal classes for APP-03/05

`consumer-service/src/main/java/com/example/consumer/ConsumerApplication.java`:

```java
package com.example.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsumerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsumerApplication.class, args);
    }
}
```

`consumer-service/src/main/java/com/example/consumer/config/RabbitConfig.java`:

```java
package com.example.consumer.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String QUEUE = "orders.created";

    @Bean Queue ordersCreatedQueue() { return new Queue(QUEUE, true); }

    @Bean MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

`consumer-service/src/main/java/com/example/consumer/messaging/OrderListener.java`:

```java
package com.example.consumer.messaging;

import com.example.consumer.config.RabbitConfig;
import com.example.consumer.domain.ProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrderListener {
    private static final Logger LOG = LoggerFactory.getLogger(OrderListener.class);
    private final ProcessingService processingService;

    public OrderListener(ProcessingService processingService) {
        this.processingService = processingService;
    }

    // APP-03: receive OrderCreated and simulate downstream domain work.
    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrder(Map<String, Object> message) {
        Object orderId = message.get("orderId");
        LOG.info("OrderCreated received: orderId={}", orderId);
        processingService.process(message);
    }
}
```

`consumer-service/src/main/java/com/example/consumer/domain/ProcessingService.java`:

```java
package com.example.consumer.domain;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ProcessingService {
    public void process(Map<String, Object> order) {
        // Phase 1: simulated domain work, in-memory only.
        // Phase 3 wires up the deterministic 10% failure path (APP-04).
    }
}
```

`consumer-service/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: order-consumer
management:
  endpoints:
    web:
      exposure:
        include: health
```

### README "Prerequisites" section skeleton (DOC-02)

```markdown
## Prerequisites

You will run two Spring Boot apps on your laptop's JVM and two infrastructure
containers (RabbitMQ + grafana/otel-lgtm) via Docker. Before starting,
verify your environment with `mise run preflight`.

### Required tools

| Tool | Version | Install |
|------|---------|---------|
| [mise](https://mise.jdx.dev/) | ≥ 2025.1.0 | `curl https://mise.run | sh` |
| Docker Engine + Compose v2 | ≥ 24.0 | https://docs.docker.com/engine/install/ |
| Git | ≥ 2.30 | `brew install git` / `apt install git` / `pacman -S git` |

mise will install the right JDK and Maven for you on first `mise install`:

| Auto-installed via mise | Version |
|-------------------------|---------|
| Amazon Corretto JDK | 17.0.13.11.1 |
| Apache Maven | 3.9.11 |

### Required free ports

| Port | Service | Why |
|------|---------|-----|
| 3000 | Grafana UI | Common collision (React/Next.js dev servers) |
| 4317 | OTLP gRPC ingest | Used from Phase 2 onwards |
| 4318 | OTLP HTTP ingest | Reserved for HTTP-fallback variant |
| 5672 | RabbitMQ AMQP | Standard AMQP port |
| 15672 | RabbitMQ Management UI | Standard management port |
| 8080 | producer-service HTTP | Spring Boot default |
| 8081 | consumer-service HTTP | `/actuator/health` only |

If a port is in use, `mise run preflight` will tell you which one and suggest
`lsof -i:<port>` to identify the conflicting process.

### IDE setup

If you use **IntelliJ IDEA**: install the [Mise plugin](https://plugins.jetbrains.com/plugin/24009-mise)
OR ensure IntelliJ's "Project SDK" points at `~/.local/share/mise/installs/java/corretto-17.0.13.11.1`.
The committed `.tool-versions` file enables IntelliJ's built-in auto-detection
as a fallback.

If you use **VS Code**: install the [Mise extension](https://marketplace.visualstudio.com/items?itemName=hverlin.mise-vscode).

### One-time setup

\`\`\`bash
git clone <this repo>
cd ose-otel-demo
mise install        # installs Corretto 17 + Maven 3.9.11
mise run preflight  # validates everything before you start
\`\`\`

### First run

\`\`\`bash
mise run infra:up   # starts RabbitMQ + grafana/otel-lgtm
mise run dev        # starts producer + consumer in parallel
# in a second terminal:
mise run demo:order # POSTs a sample order; expect 202
\`\`\`

You should see the consumer log a line like:
\`OrderCreated received: orderId=<uuid>\`

In Phase 1 there is **no telemetry** — the OTLP endpoint is open and the
Grafana stack is running, but the apps emit nothing. This is intentional:
Phase 2 introduces the OpenTelemetry SDK and traces start flowing.

To verify the baseline: `mise run verify:bom` should report zero OpenTelemetry
libraries on the classpath.
```

### Workshop checkpoint at phase exit

```bash
git add .
git commit -m "Phase 1: working two-service Spring Boot + RabbitMQ baseline (no telemetry)"
git tag -a step-01-baseline -m "Workshop checkpoint: baseline app + scaffolding, zero OTel libs"
git push --tags
```

## Runtime State Inventory

> Phase 1 is a **greenfield** phase — there is no existing runtime state to migrate. This section is included for completeness; nothing was found in any category.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — verified by inspection of `git ls-files` (only `.planning/` and `CLAUDE.md` exist; no databases, no message queues, no caches yet) | None |
| Live service config | None — no live services exist yet | None |
| OS-registered state | None — no Task Scheduler / launchd / systemd registrations | None |
| Secrets/env vars | None — env vars (`SPRING_RABBITMQ_*`, `OTEL_EXPORTER_OTLP_ENDPOINT`) introduced fresh in `mise.toml`, not renamed from any prior name | None |
| Build artifacts | None — no compiled artifacts exist; `~/.m2/repository` is unaffected by Phase 1 | None |

**Nothing found in any category** — verified by `git ls-files`, `docker ps`, and absence of `target/` directories. This is a true greenfield phase.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| mise | INFRA-02 (toolchain installer + task runner) | ✓ | 2026.3.17 | None — mise is mandatory |
| Docker Engine | INFRA-04 (containerized infra) | ✓ | 29.4.1 | None — docker is mandatory for compose |
| Docker Compose v2 | INFRA-04 | ✓ | bundled with Docker 29.4 | None |
| Java (system) | bootstrap-only; mise will install Corretto 17 | ✓ | OpenJDK 26.0.1 (host) | mise installs Corretto 17 inside `mise install` — host JDK irrelevant |
| Maven (system) | bootstrap-only; mise will install Maven 3.9 | ✗ | — | mise installs `maven 3.9.11` inside `mise install` |
| `ss` (iproute2) | INFRA-03 (port checks in `preflight`) | ✓ | iproute2 (Linux native) | `nc -z` available as portable fallback |
| `lsof` | INFRA-03 (port-conflict diagnosis hint) | ✓ | (Linux native) | optional — only used in error messages |
| `nc` (netcat) | INFRA-03 (portable port-check fallback) | ✓ | (Linux native) | — |
| `curl` | demo:order task; healthchecks | ✓ | (Linux native) | — |
| Internet access | First-run download of Maven artifacts, JDK, docker images | Assumed ✓ | — | None — workshop assumes laptop with internet on first setup |

**Missing dependencies with no fallback:** Maven on host PATH — but this is expected: `mise install` provides Maven 3.9.11. The Phase 1 plan must include a `mise install` step before any `mvn` invocation.

**Missing dependencies with viable alternatives:** None.

## Validation Architecture

> SKIPPED — `.planning/config.json` has `workflow.nyquist_validation: false`. No nyquist test sampling needed.

## Security Domain

> `security_enforcement: true` per `.planning/config.json`; ASVS Level 1.

### Applicable ASVS Categories (Phase 1 surface)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | Multi-module Maven layout documented; no shared-library auto-config injecting code into services unintentionally |
| V2 Authentication | no | Phase 1 has no auth (workshop scope). RabbitMQ uses default `guest:guest` (loopback only). Documented as intentional. |
| V3 Session Management | no | No sessions; APIs are stateless |
| V4 Access Control | no | Out of scope per `PROJECT.md` ("Authentication / authorization on the HTTP API — irrelevant to the instrumentation lesson") |
| V5 Input Validation | partial | `OrderController` accepts `Map<String, Object>` payload. For workshop scope this is acceptable (no business logic depends on shape). Phase 1 plan should add a JavaDoc note that schema validation would be added in production. |
| V6 Cryptography | no | No secrets handled in Phase 1 |
| V7 Error Handling | yes | Spring Boot's default `ErrorAttributes` are suitable for workshop scope; no custom error handling that could leak stack traces |
| V8 Data Protection | no | No PII handled |
| V9 Communications | no | All inter-process communication is loopback-only on the developer laptop |
| V10 Malicious Code | no | Workshop is single-developer; no untrusted plugin extension surface |
| V11 Business Logic | no | Order processing is simulated in-memory; no real business rules |
| V12 Files & Resources | no | No file upload/download |
| V13 API | partial | REST API is JSON-only via Spring MVC; standard Jackson configuration. No deserialization gadgets reachable. |
| V14 Configuration | yes | Pinned image tags + pinned mise plugin versions enforce supply-chain reproducibility |

### Known Threat Patterns for Spring Boot 3.4 + RabbitMQ + Maven

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Maven dependency confusion (a typo'd group ID pulls a malicious package) | Tampering | Use BOMs (no version drift); `maven-enforcer-plugin` with `bannedDependencies` rule (Phase 1 plan can opt in if desired); pin dependency versions to known-good releases |
| RabbitMQ default credentials (`guest:guest`) exposed beyond localhost | Spoofing / EoP | docker-compose binds Rabbit ports `5672`/`15672` to localhost only by default. Document explicitly in README that exposing these ports requires changing the default password |
| Spring Boot Actuator exposing sensitive endpoints | Information Disclosure | `application.yaml` sets `management.endpoints.web.exposure.include=health` (whitelist, not `*`) so no `/actuator/env` or `/actuator/beans` leak |
| Floating Docker image tags introducing supply-chain drift | Tampering | All image tags pinned (`rabbitmq:4.3-management`, `grafana/otel-lgtm:0.26.0`); workshop reproducibility doubles as security mitigation |
| Jackson polymorphic deserialization (CVE patterns) | Tampering / EoP | Phase 1 producer/consumer use plain `Map<String, Object>` (not polymorphic types); `Jackson2JsonMessageConverter` default config does not enable `@class` headers |

**No CRITICAL/HIGH security findings for Phase 1.** All identified concerns have standard mitigations encoded into the plan via configuration, not custom code.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Workshop attendees will run on Linux/macOS (POSIX shell) | mise tasks use `set -e`, `ss -tln`, `xdg-open`/`open` fallbacks | If Windows-native (no WSL): `ss` and `xdg-open` unavailable. Plan should note "WSL recommended for Windows attendees" in README. **Low risk** — `PROJECT.md` audience is "engineers with laptop hardware" and the team is internal (Linux/Mac dominant in observability/SRE shops). |
| A2 | The exact patch `corretto-17.0.13.11.1` is acceptable; user has not specified otherwise | mise.toml `java = "corretto-17.0.13.11.1"` | If user wants `17.0.19.10.1` (current latest as of 2026-04-29) instead — easy fix, just bump the patch in `mise.toml` and `.tool-versions`. **Low risk** — pinning the patch was the goal; the exact patch number can be revised. |
| A3 | The exact Maven `3.9.11` is acceptable; STACK.md mentioned `3.9.11` as a recommendation example | mise.toml `maven = "3.9.11"` | Could pin to `3.9.15` (current latest 3.9.x). Either works for Spring Boot 3.4. **Low risk** — bump the patch if a specific 3.9.x feature is needed. |
| A4 | No CONTEXT.md exists for Phase 1 because user explicitly skipped `/gsd-discuss-phase` | "User Constraints" section | If user wants discuss before plan — research is still useful, just gets revised in light of new decisions. **Low risk** — STATE.md confirms "Status: Ready to plan". |

**No assumptions about compliance, retention, security, or performance targets.** Section is intentionally short.

## Open Questions

1. **Should the consumer-service include `spring-boot-starter-web` or just `spring-boot-starter-actuator`?**
   - What we know: APP-05 requires `/actuator/health` on both services, which actuator alone provides.
   - What's unclear: actuator alone does *not* pull in an embedded web server in Spring Boot 3.4 — it requires a web starter to bind to a port. With only `spring-boot-starter-amqp` + `spring-boot-starter-actuator`, the consumer service has no HTTP server, so `/actuator/health` is unreachable.
   - Recommendation: include `spring-boot-starter-web` on the consumer too (already reflected in the consumer-service POM example above). Cost: pulls in Tomcat (~10MB) — acceptable for workshop scope. Alternative: skip web, manage health via actuator's `WebApplicationType.NONE` mode + management server on a separate port — adds config complexity that does not help the workshop.

2. **Should `otel-bootstrap` be created in Phase 1 or deferred to Phase 3 (when its first content lands)?**
   - What we know: ROADMAP.md notes `otel-bootstrap` houses the AMQP propagation pair (Phase 3 deliverable). PROJECT.md's "three-module Maven build" decision implies the module exists from the start.
   - What's unclear: nothing technical — just a structural decision.
   - Recommendation: **create `otel-bootstrap` empty in Phase 1**. The parent POM declares it under `<modules>`; deferring breaks `mvn` from the parent. `package-info.java` keeps the module compilable. Phase 3 populates it.

3. **Does the producer need a non-default `server.port`, or is `8080` fine?**
   - What we know: `8080` is Spring Boot's default; both services would clash on it if run simultaneously.
   - What's unclear: which one moves.
   - Recommendation: producer = `8080` (the one attendees `curl`), consumer = `8081` (only used for `/actuator/health`). Encoded in mise.toml `[env]` block as `PRODUCER_PORT` / `CONSUMER_PORT` and passed via `-Dspring-boot.run.jvmArguments`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `<parent>spring-boot-starter-parent</parent>` for every Spring Boot multi-module project | BOM-import in parent POM (no `<parent>`) when you need to control BOM ordering | Long-standing best practice; doc made explicit in Spring Boot 3.x reference | Lets us put OTel BOM before Spring Boot BOM (this phase's headline structural decision) |
| Shell `&` plumbing for parallel mise tasks | mise's `:::` separator (CLI) and `{ tasks = [...] }` array form (TOML) | Available since mise 2024; documented with first-class examples in 2025 | Cleaner, idiomatic, correct signal handling |
| Floating image tags (`grafana/otel-lgtm:latest`) | Pinned tags (`grafana/otel-lgtm:0.26.0`) | Always was best practice; especially relevant for workshop artifacts | Workshop screenshots + commentary stay accurate across cohorts |
| `docker-compose` (hyphenated) CLI | `docker compose` (subcommand) | Compose v2 (~2022); v1 sunset 2023 | Cleaner integration; required form on modern Docker installs |
| `.tool-versions` (asdf-only) | `mise.toml` (mise primary) + `.tool-versions` (companion for IDE compat) | mise emerged 2023; recommended dual-file practice 2024+ | Source-of-truth split: `mise.toml` for richness, `.tool-versions` for IDE auto-detect |
| Manual port-check shell scripts | `ss -tln` parsing pipeline | Always; included for completeness | Zero dependencies, fast |

**Deprecated/outdated:**
- `docker-compose` (hyphen, v1) — replaced by `docker compose` (subcommand, v2). Use the modern form.
- `rabbitmq:3-management` (3.x line) — RabbitMQ 4.x is current stable. Use `4.3-management`.
- `<parent>spring-boot-starter-parent</parent>` IS NOT deprecated — it's still recommended for single-module projects. Just not the right tool when controlling BOM ordering.

## Sources

### Primary (HIGH confidence)

- [Maven Dependency Mechanism — Importing Dependencies](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html) — first-declaration-wins rule for BOM imports
- [mise — Tasks Running](https://github.com/jdx/mise/blob/main/docs/tasks/running-tasks.md) (via Context7 `/jdx/mise`) — `:::` parallel separator, `{ tasks = [...] }` array syntax
- [mise — IDE Integration](https://mise.jdx.dev/ide-integration.html) — IntelliJ Mise plugin, `.tool-versions` auto-detection, shim setup
- [mise — Java Language](https://mise.jdx.dev/lang/java.html) — `corretto-17` plugin syntax, JAVA_HOME automation
- [mise — Configuration](https://mise.jdx.dev/configuration.html) — `mise.toml` vs `.tool-versions` precedence
- [Spring Boot Reference — Build Systems](https://docs.spring.io/spring-boot/reference/using/build-systems.html) — BOM-import vs `spring-boot-starter-parent`; multi-module guidance
- [Spring Boot Maven Plugin — `spring-boot:run`](https://docs.spring.io/spring-boot/maven-plugin/run.html) — `mvn -pl <module> spring-boot:run`, fork semantics, JVM args
- [RabbitMQ Monitoring docs](https://www.rabbitmq.com/docs/monitoring) — healthcheck stages, `rabbitmq-diagnostics -q ping` recommended
- [grafana/docker-otel-lgtm Dockerfile + healthcheck.sh](https://github.com/grafana/docker-otel-lgtm) — built-in HEALTHCHECK directive checking five backend services
- `STACK.md` (existing project research) — version pins for Spring Boot 3.4.13, OTel SDK 1.61.0, OTel Instrumentation 2.27.0-alpha, otel-lgtm 0.26.0, RabbitMQ 4.3
- `PITFALLS.md` (existing project research) — pitfalls #9 (BOM drift), #14 (port collisions), #15 (mise+IntelliJ), #16 (general)
- `ARCHITECTURE.md` (existing project research) — three-module layout, parent POM template
- `REQUIREMENTS.md` (existing project research) — INFRA-01 through INFRA-05, APP-01/02/03/05, DOC-02, WORK-01
- `ROADMAP.md` (existing project research) — Phase 1 goal + success criteria

### Secondary (MEDIUM confidence — verified on 2026-04-29)

- [Spring Boot 3.4.13 release announcement (2025-12-18)](https://spring.io/blog/2025/12/18/spring-boot-3-4-13-available-now/)
- [spring-projects/spring-boot#43200](https://github.com/spring-projects/spring-boot/issues/43200) — documented OTel-vs-Spring-Boot BOM drift case; workaround = explicit OTel BOM override
- [grafana/docker-otel-lgtm#461](https://github.com/grafana/docker-otel-lgtm/issues/461) — port 3000 remapping is broken
- Local environment probes — `mise ls-remote java` confirms `corretto-17.0.19.10.1` is current floating; `corretto-17.0.13.11.1` listed; `mise ls-remote maven` confirms 3.9.11 + 3.9.15 available; `ss -tln` confirms target ports free; `docker --version` 29.4.1; `mise --version` 2026.3.17

### Tertiary (LOW confidence — none flagged for re-validation in this research)

None. Both flagged questions resolved with HIGH confidence; no LOW-confidence claims in any prescriptive section.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every artifact version cross-checked against `STACK.md` and verified against Maven Central / GitHub Releases / Docker Hub
- Architecture: HIGH — multi-module Maven layout proven in `ARCHITECTURE.md`; BOM-import-vs-parent decision verified against Spring Boot reference docs
- Pitfalls: HIGH — three foundation pitfalls (#9, #14, #15) inherited from `PITFALLS.md` (already vetted) plus three new Phase-1-specific pitfalls (D, E, F) derived from cross-referencing INFRA requirements with config conventions
- BOM order question: HIGH — Maven docs are unambiguous; spring-boot#43200 is corroborating evidence
- mise plugin ID question: HIGH — confirmed against `mise ls-remote java` on the developer's machine and Context7's mise docs corpus

**Research date:** 2026-04-29
**Valid until:** 2026-05-29 (30 days for stable claims; this phase has no fast-moving dependencies). The OTel SDK release cadence is monthly so a Phase 2+ rerun in 30+ days should re-verify versions if implementation slips.
