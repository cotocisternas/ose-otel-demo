# Phase 6: Verification Tests - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 6-verification-tests
**Areas discussed:** Test module structure, @TestConfiguration shape, Cross-service flow mechanism, Test signal coverage

---

## Test module structure

### Q1 — Where should the cross-service integration test (TEST-03/04/05) live?

| Option | Description | Selected |
|--------|-------------|----------|
| New integration-tests module | Add a 4th top-level Maven module that depends on producer-service + consumer-service + Testcontainers + opentelemetry-sdk-testing. Matches ARCHITECTURE.md's optional sketch. | ✓ |
| Per-service @SpringBootTests, no e2e module | Tests live in producer-service/src/test + consumer-service/src/test. Each service tests its OWN side. | |
| Tests in otel-bootstrap | Cross-service test lives alongside MessagePropertiesRoundTripTest. (Circular dependency risk.) | |

**User's choice:** New integration-tests module
**Notes:** Closes ARCHITECTURE.md's optionality flag in v1. Maven layout is now 4 modules: otel-bootstrap, producer-service, consumer-service, integration-tests.

### Q2 — Should we also add per-service @SpringBootTest smoke tests alongside the integration-tests module?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — per-service tests + integration-tests | 3 test classes total: producer smoke + consumer smoke + e2e IT. | |
| No — only the integration-tests module | One e2e test class is sufficient per ROADMAP SC #3 phrasing. | ✓ |

**User's choice:** No — only the integration-tests module
**Notes:** Pedagogically tighter; smaller diff for the workshop attendee to read.

### Q3 — Maven test-phase convention for the integration-tests module?

| Option | Description | Selected |
|--------|-------------|----------|
| Failsafe, *IT.java naming | maven-failsafe-plugin in integration-test phase. `mvn verify` runs it. | ✓ |
| Surefire, *Test.java naming | maven-surefire-plugin in test phase. Simpler but blurs unit-vs-integration. | |

**User's choice:** Failsafe, *IT.java naming
**Notes:** `mise run test` is already `mvn -T 1C verify` — works without mise.toml change. Keeps existing MessagePropertiesRoundTripTest (Surefire) coexistence intact.

### Q4 — Should the integration-tests module's pom.xml depend on the producer-service and consumer-service Spring Boot fat jars, or on the unrepackaged classes jars?

| Option | Description | Selected |
|--------|-------------|----------|
| Classes jar via spring-boot-maven-plugin classifier | Producer + consumer POMs add `<classifier>` config so executable jar gets a classifier and plain classes jar is the default artifact. | ✓ |
| Plain Maven module dependencies (no classifier) | Default Spring Boot 3.4.x repackaging would shadow the classes jar — fails. | |
| You decide | Defer to research/planner. | |

**User's choice:** Classes jar via spring-boot-maven-plugin classifier
**Notes:** Required so SpringApplicationBuilder(ProducerApplication.class) works in the test. Exact classifier value (e.g., `<classifier>exec</classifier>`) deferred to research/planner per Spring Boot 3.4.13 docs.

---

## @TestConfiguration shape

### Q1 — What shape should the test SDK configuration take?

| Option | Description | Selected |
|--------|-------------|----------|
| Parallel @TestConfiguration that builds full SDK | Mirrors production OtelSdkConfiguration line-by-line; in-memory exporters substituted. | ✓ |
| @Bean @Primary override of just the SpanProcessor | Smallest delta. Requires Phase 2 production refactor (violates Phase 2 lock). | |
| OpenTelemetryExtension JUnit5 extension | Bypasses Spring DI; would force a second SDK instance. | |

**User's choice:** Parallel @TestConfiguration that builds full SDK
**Notes:** Attendees can diff TestOtelConfiguration vs OtelSdkConfiguration side-by-side.

### Q2 — How should TestOtelConfiguration replace the production OtelSdkConfiguration?

| Option | Description | Selected |
|--------|-------------|----------|
| @Import + @SpringBootTest properties exclude | spring.main.allow-bean-definition-overriding=true; bean-name override of OpenTelemetry. | ✓ |
| @SpringBootTest with profile + @Profile("test") | Requires Phase 2 retrofit on existing OtelSdkConfiguration (violates Phase 2 lock). | |
| @TestConfiguration nested static class | Less greppable; harder to reference from README. | |

**User's choice:** @Import + @SpringBootTest properties exclude

### Q3 — Test SDK — single shared instance for both services, or per-service mirror?

| Option | Description | Selected |
|--------|-------------|----------|
| Single shared TestOtelConfiguration | ONE InMemorySpanExporter sees ALL spans from both contexts. Test infra is exempt from production duplication rule. | ✓ |
| Per-service TestOtelConfiguration mirror | Consistent with workshop duplication theme; complicates assertions. | |

**User's choice:** Single shared TestOtelConfiguration
**Notes:** README delta should explicitly call out the contrast (production duplicates; tests share).

### Q4 — What does TestOtelConfiguration expose besides the InMemorySpanExporter?

| Option | Description | Selected |
|--------|-------------|----------|
| Tracer + InMemorySpanExporter @Beans | Match production exposed beans for constructor injection. Log/metric exporters added per Test signal coverage. | ✓ |
| Full mirror of production beans + in-memory exporter | Pre-decides Test signal coverage. | |
| Just OpenTelemetry + InMemorySpanExporter, no Tracer/Meter | Production code constructor-injects Tracer; option not viable. | |

**User's choice:** Tracer + InMemorySpanExporter @Beans (subsequently extended to full triple-signal per signal coverage decision)

---

## Cross-service flow mechanism

### Q1 — How should the test bring up both producer and consumer in one JVM?

| Option | Description | Selected |
|--------|-------------|----------|
| Two SpringApplicationBuilder contexts | Preserves two-service mental model; manual context management. | ✓ |
| One @SpringBootTest with both Application classes | Single fused context; blurs the two-service boundary. | |
| Run producer in-context, consumer as compiled-classes-only | Skips real ConsumerApplication startup; loses CONSUMER + INTERNAL spans wiring fidelity. | |

**User's choice:** Two SpringApplicationBuilder contexts
**Notes:** AMQP boundary is REAL; preserves the workshop's two-service teaching.

### Q2 — How should the RabbitMQContainer be started and exposed to both contexts?

| Option | Description | Selected |
|--------|-------------|----------|
| @Container static field + System.setProperty SPRING_RABBITMQ_* | Idiomatic Testcontainers + JUnit5; properties read by Spring at refresh. | ✓ |
| @DynamicPropertySource with manual context registration | Tied to @SpringBootTest lifecycle; doesn't auto-apply to manual contexts. | |
| Pass connection via SpringApplicationBuilder.properties(...) | Explicit per-context; more lines. | |

**User's choice:** @Container static field + System.setProperty SPRING_RABBITMQ_*
**Notes:** Test class @AfterAll clears properties defensively to avoid JVM-wide leak across test classes.

### Q3 — How does the test trigger POST /orders on the producer side?

| Option | Description | Selected |
|--------|-------------|----------|
| TestRestTemplate against producer's random port | Real HTTP exercises SERVER + INTERNAL + PRODUCER spans. | ✓ |
| Direct OrderService.place(...) call | Skips SERVER span; breaks SpanKind coverage. | |
| WebTestClient | Adds webflux dep; producer is Spring MVC. | |

**User's choice:** TestRestTemplate against producer's random port

### Q4 — After POSTing the order, how does the test wait for the consumer to finish processing?

| Option | Description | Selected |
|--------|-------------|----------|
| Awaitility polling on InMemorySpanExporter span count | Deterministic; no Thread.sleep; PITFALLS #4/#11 mitigation. | ✓ |
| CountDownLatch in a test-only @RabbitListener | Competes with production listener for messages. | |
| Fixed Thread.sleep(2000) | PITFALLS #11 anti-pattern; flaky on CI. | |

**User's choice:** Awaitility polling on InMemorySpanExporter span count + forceFlush
**Notes:** Awaitility added as test-scope dep on integration-tests/pom.xml (BOM-managed by Spring Boot 3.4.13).

---

## Test signal coverage

### Q1 — What signals should the integration test assert?

| Option | Description | Selected |
|--------|-------------|----------|
| Traces only — strict TEST-01..06 reading | Smallest test surface; matches TEST phrasing literally. | |
| Traces + logs (skip metrics) | Triple-signal correlation has CI proof on failure path. | |
| Traces + logs + metrics (full triple-signal) | Every Phase 4/5/Phase 3 requirement has a CI gate; ~2-3x test size. | ✓ |

**User's choice:** Traces + logs + metrics (full triple-signal)
**Notes:** TestOtelConfiguration grows to expose InMemoryLogRecordExporter + InMemoryMetricReader beans (D-08 extended).

### Q2 — Test layout for the triple-signal assertions?

| Option | Description | Selected |
|--------|-------------|----------|
| Three @Test methods, one per signal | Each test failure points to a specific signal regression. | ✓ |
| Single @Test with all assertions chained | First failure short-circuits; harder diagnosis. | |
| Three @Test methods + APP-04 failure-path test | Same as Option 1 + a fourth test for the error path. | |

**User's choice:** Three @Test methods, one per signal (extended to four per next question).

### Q3 — Should we add a separate @Test for the APP-04 deterministic 10th-order failure path?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes — add tenthOrderFailureProducesErrorSpan @Test | Triple-signal correlation proof on the workshop's only error path. | ✓ |
| No — happy path only | APP-04 / TRACE-09 / Phase 5 D-16 have no CI guardrail. | |

**User's choice:** Yes — add the failure-path test
**Notes:** This is the workshop's single strongest assertion of "all three signals work together"; D-17 documents this as the Phase 6 highlight.

### Q4 — How should InMemoryMetricReader collect samples in tests?

| Option | Description | Selected |
|--------|-------------|----------|
| Use InMemoryMetricReader directly (no PeriodicMetricReader) — manual collect | Synchronous; deterministic; standard opentelemetry-sdk-testing pattern. | ✓ |
| Wrap InMemoryMetricExporter in PeriodicMetricReader with 100ms interval | Mirrors production shape; still requires Awaitility wait. | |
| Awaitility wait on metric count with 10s interval | Production shape preserved; +10s test runtime; flakiness risk. | |

**User's choice:** Use InMemoryMetricReader directly (no PeriodicMetricReader) — manual collect
**Notes:** Test SDK metric pipeline diverges from production's; documented in TestOtelConfiguration's comment block.

---

## Claude's Discretion

- Exact `<classifier>` value on `spring-boot-maven-plugin` — research/planner picks against Spring Boot 3.4.13 reference.
- Awaitility version pin (BOM-managed by Spring Boot 3.4.13).
- Test class package (`com.example.e2e` recommended; matches ARCHITECTURE.md sketch).
- `@BeforeEach` reset semantics for `InMemoryMetricReader` (filter-by-attribute, per-test SDK rebuild, or `>=` count assertions).
- Exact assertion library + helpers (AssertJ + opentelemetry-sdk-testing's SpanDataAssert / MetricAssert if present in 1.61.0).

## Deferred Ideas

- GitHub Actions / CI workflow YAML — Phase 7 polish if at all.
- Per-service @SpringBootTest smoke tests — v2 candidate if cohort feedback requests.
- `@ServiceConnection` variant — Phase 7 polish callout opportunity ("simpler test shape we didn't pick").
- Test-only logback-test.xml — only if console noise becomes a problem.
- Performance benchmarks / JMH — out of scope per PROJECT.md.
- Native-image compatibility test — out of scope per PROJECT.md.
- Cardinality-stress tests for metrics — fixed cardinality in v1.
- Baggage propagation tests — v2 candidate.
- Span-sampling alternates — SAMP-01 v2 entry.
- TurboFilter MDC injection unit test — not load-bearing.
- integration-tests module dashboards / load scripts — Phase 7 WORK-02 / WORK-03.
