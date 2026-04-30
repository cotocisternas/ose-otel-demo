# OSE OTel Demo

A workshop-grade demo that teaches engineers how to instrument a Spring Boot 3.4.13 / Java 17 application using the OpenTelemetry Java SDK directly — manual instrumentation, no `-javaagent` and no Micrometer bridge. The demo covers two of the most common service-to-service shapes (synchronous HTTP and asynchronous RabbitMQ producer/consumer) and emits all three OpenTelemetry signals — traces, metrics, and logs — to Grafana's `otel-lgtm` all-in-one backend.

The workshop progresses through six annotated git tags: `step-01-baseline` → `step-02-traces` → `step-03-context-propagation` → `step-04-metrics` → `step-05-logs` → `step-06-tests`. You can `git checkout` any tag to time-travel through the workshop. The current `main` branch as of `step-01-baseline` shows the **uninstrumented baseline** — both Spring Boot apps run end-to-end with `POST /orders` flowing through RabbitMQ, but with **zero OpenTelemetry libraries on the classpath**. Phase 2 onward adds the SDK.

## Prerequisites

You will run two Spring Boot apps on your laptop's JVM and two infrastructure containers (RabbitMQ + grafana/otel-lgtm) via Docker. Before starting, verify your environment with `mise run preflight`.

### Required tools

| Tool                          | Version       | Install                                                   |
|-------------------------------|---------------|-----------------------------------------------------------|
| mise                          | `≥ 2025.1.0`  | `curl https://mise.run \| sh`                              |
| Docker Engine + Compose v2    | `≥ 24.0`      | https://docs.docker.com/engine/install/                   |
| Git                           | `≥ 2.30`      | `brew install git` / `apt install git` / `pacman -S git`  |

mise will install the right JDK and Maven for you on first `mise install`:

| Auto-installed via mise   | Version                  |
|---------------------------|--------------------------|
| Amazon Corretto JDK       | `corretto-17.0.13.11.1`  |
| Apache Maven              | `3.9.11`                 |

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

If a port is in use, `mise run preflight` will tell you which one and suggest `lsof -i:<port>` to identify the conflicting process.

### IDE setup

If you use **IntelliJ IDEA**: install the [Mise plugin](https://plugins.jetbrains.com/plugin/24009-mise) OR ensure IntelliJ's "Project SDK" points at the mise-installed Corretto JDK (run `mise where java` to print the absolute path). The committed `.tool-versions` file enables IntelliJ's built-in auto-detection as a fallback.

If you use **VS Code**: install the [Mise extension](https://marketplace.visualstudio.com/items?itemName=hverlin.mise-vscode).

### One-time setup

```sh
git clone <this repo>
cd ose-otel-demo
mise install        # installs the Corretto JDK + Maven versions pinned in mise.toml
mise run preflight  # validates everything before you start
```

### First run

```sh
mise run infra:up   # starts RabbitMQ + grafana/otel-lgtm
mise run dev        # starts producer + consumer in parallel
# in a second terminal:
mise run demo:order # POSTs a sample order; expect 202
```

You should see the consumer log a line like: `OrderCreated received: orderId=<uuid>`.

In Phase 1 there is **no telemetry** — the OTLP endpoint is open and the Grafana stack is running, but the apps emit nothing. This is intentional: Phase 2 introduces the OpenTelemetry SDK and traces start flowing. To verify the baseline: `mise run verify:bom` should report zero OpenTelemetry libraries on the classpath.

## Workshop checkpoints

- `step-01-baseline` — Working two-service Spring Boot + RabbitMQ app on host JVM with ZERO telemetry. **Current.**
- `step-02-traces` — (Phase 2) Manual SDK bootstrap; producer and consumer emit DISCONNECTED traces.
- `step-03-context-propagation` — (Phase 3) THE headline lesson: AMQP context propagation joins the two traces.
- `step-04-metrics` — (Phase 4) `SdkMeterProvider` + Counter/Histogram/ObservableGauge.
- `step-05-logs` — (Phase 5) Logs correlation + Loki-to-Tempo click-through.
- `step-06-tests` — (Phase 6) Testcontainers verification.

This section establishes the convention; Phase 7 turns each bullet into a full walkthrough.

## What's NOT here yet

The following are deliberate Phase 1 omissions — the repo isn't incomplete, it's **uninstrumented on purpose** so each later phase has something concrete to add:

- No `OtelSdkConfiguration.java` (Phase 2)
- No `traceparent` header injection on AMQP (Phase 3)
- No metrics or log correlation (Phase 4 / Phase 5)
- No integration tests (Phase 6)
- No pre-built Grafana dashboard or load script (Phase 7)
