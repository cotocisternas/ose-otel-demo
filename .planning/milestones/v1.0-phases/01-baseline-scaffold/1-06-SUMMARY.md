---
plan: 1-06-readme-and-exit-gate
phase: 01-baseline-scaffold
status: complete
tag: step-01-baseline
tag_commit: 6aa3a924e3893493883775e5ae5c4384c4c1f530
tag_pushed: false
---

# Plan 1-06 — README + Phase 1 Exit Gate

**Tasks completed:** T1 (README + .gitignore) ✅ · T2 (5-criteria runtime verification) ✅ · T3 (annotated tag) **awaiting user**

## Outputs

| File | Lines | Purpose |
|------|-------|---------|
| `README.md` | 76 | Workshop attendee front door — DOC-02 Prerequisites section |
| `.gitignore` | 19 | Maven `target/`, IDE state, mise runtime state, OS junk |

`README.md` structure: H1 + intro → `## Prerequisites` (required tools, mise auto-installs, port table, IDE setup, one-time setup, first run, baseline-zero-OTel reminder) → `## Workshop checkpoints` (6 step-tags, only step-01 exists today) → `## What's NOT here yet` (deliberate Phase 1 omissions). All 24 T1 grep gates pass.

## T2 — five Phase 1 success criteria, simultaneously green

| Criterion | Command | Result |
|-----------|---------|--------|
| 1. Preflight green | `mise run preflight` | exit 0, last line `Pre-flight: ALL GREEN` |
| 2. Apps work end-to-end | `mise run infra:up && mise run dev` then `mise run demo:order` | `POST /orders` → 202 + `{"orderId":"1c2b627a-…"}`; consumer logs `OrderCreated received: orderId=1c2b627a-…` (same UUID — proves AMQP round-trip); both `/actuator/health` return `{"status":"UP"}` |
| 3. Zero OTel libs | `mise run verify:bom` | exit 0, `Phase 1 baseline confirmed: zero OpenTelemetry libraries on classpath.` |
| 4. Working tree clean (pre-tag) | `git status --porcelain` | empty |
| 5. README self-diagnosis | grep checks for mise/Docker/Git/preflight/Corretto/.tool-versions/ports | all 24 patterns matched |

Runtime detail (T2 criterion 2):
- `docker compose pull`: 23.8 s (one-time, ~1.4 GB)
- `mise run infra:up` with `--wait`: 32.8 s (RabbitMQ healthcheck `rabbitmq-diagnostics -q ping` + lgtm built-in HEALTHCHECK both `Healthy`)
- Both Spring Boot apps started in <1 s: `Started ProducerApplication in 0.754s`, `Started ConsumerApplication in 0.808s`
- AMQP round-trip latency: ~11 s from `mise run dev` start to consumer-logged receipt (dominated by Maven cold starts; Spring Boot itself is sub-second)
- `mise run infra:down` (no `-v`): 4 s, lgtm-data volume preserved

## Deviations from plan

1. **Acceptance-criterion vs plan-body conflict resolved by trimming README**. Plan body required Corretto + Maven version strings to appear in BOTH the auto-installed table AND the IDE setup paragraph / first-run code-block comment, but T1 acceptance specifies `grep -c == 1` for each. Resolved by keeping the strings in the canonical table only, replacing the IDE-section path with `mise where java` invocation, and dropping the version comment from the one-time-setup code block. README intent preserved (workshop attendee can still resolve the path); `grep -c == 1` constraint satisfied.
2. **Container names in docker-compose.yml are `ose-otel-rabbitmq` and `ose-otel-lgtm`** (per plan 1-03's executor), not `ose-otel-demo-rabbitmq` / `ose-otel-demo-lgtm` as my orchestrator-prompt sketch suggested. The shorter names work and are pinned in the file; no behavioral difference.
3. **T3 tag creation deferred to user**, per phase-execution decision: orchestrator drives runtime verification (T2), user runs `git tag -a step-01-baseline` to apply the immutable workshop checkpoint themselves.

## T3 — tag command (user runs)

The tag target commit is `6aa3a92` (current `main` HEAD). Run from repo root:

```sh
git tag -a step-01-baseline -m "Workshop checkpoint: Phase 1 — baseline app + scaffolding, zero OTel libs.

Two-service Spring Boot 3.4.13 / Java 17 demo on host JVM via mise; RabbitMQ + grafana/otel-lgtm in docker-compose. POST /orders → publish → consume works end-to-end. Maven dependency:tree -Dincludes=io.opentelemetry returns zero matches — baseline truly uninstrumented. Phase 2 introduces the OpenTelemetry SDK."
```

Verify:
```sh
git tag --list step-01-baseline                                        # outputs: step-01-baseline
git for-each-ref --format='%(objecttype) %(refname)' refs/tags/step-01-baseline   # outputs: tag refs/tags/step-01-baseline (NOT 'commit' — proves annotated)
git show step-01-baseline | head -5                                    # shows the tag message
```

`git push origin step-01-baseline` (or `git push --tags`) is your follow-up — the tag is local-only until you explicitly push.

## Phase 1 complete on tag

After the tag lands, all 11 Phase 1 REQ-IDs are satisfied:
- INFRA-01 (BOM ordering — plan 1-01) ✅
- INFRA-02, INFRA-03, INFRA-05 (mise toolchain — plan 1-02) ✅
- INFRA-04 (docker-compose — plan 1-03) ✅
- APP-01, APP-02, APP-05 (producer-service — plan 1-04) ✅
- APP-03, APP-05 (consumer-service — plan 1-05) ✅
- DOC-02 (README Prerequisites — this plan T1) ✅
- WORK-01 (annotated tag — this plan T3, user-applied) ⏳
