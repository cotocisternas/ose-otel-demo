---
id: 1-03-docker-compose
phase: 1-baseline-scaffold
plan: 03
type: execute
wave: 1
depends_on: []
requirements: [INFRA-04]
files_modified:
  - docker-compose.yml
autonomous: true
must_haves:
  truths:
    - "docker-compose.yml declares two services: rabbitmq (image rabbitmq:4.3-management) and lgtm (image grafana/otel-lgtm:0.26.0); both pinned tags, no :latest"
    - "rabbitmq service exposes 5672 (AMQP) + 15672 (Management UI) and has a healthcheck running 'rabbitmq-diagnostics -q ping' (Stage 1 — lightest)"
    - "lgtm service exposes 3000 (Grafana) + 4317 (OTLP gRPC) + 4318 (OTLP HTTP) and has NO custom healthcheck block (the image's built-in HEALTHCHECK directive checks all 5 backends; overriding it would regress validation per RESEARCH Pitfall + Don't Hand-Roll)"
    - "lgtm service mounts a NAMED volume lgtm-data:/data so Grafana state persists across docker compose down/up cycles (Pitfall D / INFRA-04)"
    - "docker compose up -d --wait succeeds: both containers reach healthy state, ports 3000/4317/4318/5672/15672 listening on localhost"
  artifacts:
    - path: "docker-compose.yml"
      provides: "Infrastructure declaration: RabbitMQ broker (4.3-management, 5672/15672) + grafana/otel-lgtm (0.26.0, 3000/4317/4318) with named volume lgtm-data"
      contains: "grafana/otel-lgtm:0.26.0"
  key_links:
    - from: "docker-compose.yml services.rabbitmq"
      to: "host network localhost:5672 + localhost:15672"
      via: "ports: mapping"
      pattern: "5672:5672"
    - from: "docker-compose.yml services.lgtm"
      to: "host network localhost:3000 + 4317 + 4318"
      via: "ports: mapping"
      pattern: "4317:4317"
    - from: "docker-compose.yml services.lgtm"
      to: "named volume lgtm-data"
      via: "volumes: lgtm-data:/data"
      pattern: "lgtm-data:/data"
---

<objective>
Declare the workshop's infrastructure tier (RabbitMQ broker + Grafana otel-lgtm all-in-one observability backend) as a single `docker-compose.yml` with pinned image tags, a Stage-1 RabbitMQ healthcheck, and a named volume for Grafana state persistence. Apps DO NOT run inside compose — only infrastructure (PROJECT.md constraint: apps run on host JVM so attendees can attach a debugger).

Purpose: INFRA-04 — `mise run infra:up` starts both containers; `mise run infra:down` stops them WITHOUT losing Grafana state. The lgtm container's built-in HEALTHCHECK (in the image's Dockerfile) validates Grafana + Loki + Tempo + Mimir + OTel Collector — we deliberately do NOT add a `healthcheck:` block to the lgtm service in compose because overriding the bundled one regresses validation (Don't-Hand-Roll #3).
Output: 1 file (docker-compose.yml). After `docker compose up -d --wait`, both containers are healthy and the LGTM stack is reachable at http://localhost:3000 (admin/admin).
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/REQUIREMENTS.md
@.planning/phases/1-baseline-scaffold/1-RESEARCH.md
@CLAUDE.md
</context>

<tasks>

<task id="1-03-T1" type="auto">
  <name>Task 1: Write docker-compose.yml with RabbitMQ + grafana/otel-lgtm + named lgtm-data volume</name>
  <files>docker-compose.yml</files>
  <read_first>
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 804-852 — verified docker-compose.yml template)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 124-128 — alternatives table: rabbitmq-diagnostics ping (Stage 1) > check_running (Stage 3); image-built-in HEALTHCHECK > custom curl override)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 297-309 — Don't Hand-Roll table: rabbitmq-diagnostics + image's built-in HEALTHCHECK)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 287-294 — Anti-Patterns: floating image tags, port-3000 remapping, infra:down -v)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 347-352 — Pitfall D: lgtm-data named volume must persist across infra:down)
  </read_first>
  <action>
    Create `docker-compose.yml` at the repo root EXACTLY matching RESEARCH.md lines 806-850. Concrete required content:

    1. **NO `version:` declaration at the top** — Compose v2 ignores it and warns; modern compose files omit it.

    2. **Top-level header comment** explaining the two facts that drive the file: "Infrastructure only — apps run on host via mise tasks" + "Phase 1: lgtm container runs but receives ZERO telemetry (no OTel libs in apps)".

    3. **`services.rabbitmq`** block:
       - `image: rabbitmq:4.3-management` (pinned tag — for absolute reproducibility could pin `4.3.0-management`, but `4.3-management` is acceptable per RESEARCH alternatives table)
       - `container_name: ose-otel-rabbitmq`
       - `ports`: `"5672:5672"` (AMQP) and `"15672:15672"` (Management UI; default credentials guest/guest)
       - `healthcheck`:
         - `test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]` — Stage 1, lightest with lowest false-positive rate (RESEARCH alternatives table line 125)
         - `interval: 10s`
         - `timeout: 5s`
         - `retries: 10`
         - `start_period: 30s`
       - `restart: unless-stopped`

    4. **`services.lgtm`** block:
       - `image: grafana/otel-lgtm:0.26.0` (pinned exact tag — `:latest` is forbidden per RESEARCH Anti-Patterns)
       - `container_name: ose-otel-lgtm`
       - `ports`: `"3000:3000"` (Grafana UI; admin/admin), `"4317:4317"` (OTLP gRPC ingest — silent in Phase 1), `"4318:4318"` (OTLP HTTP ingest — reserved fallback)
       - `environment`: `GF_SECURITY_ADMIN_USER=admin` and `GF_SECURITY_ADMIN_PASSWORD=admin`
       - `volumes`: `lgtm-data:/data` (named volume — survives `docker compose down`; only `docker compose down -v` deletes it, hence Pitfall D)
       - **NO `healthcheck:` block** — the image's Dockerfile already declares `HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD ["/otel-lgtm/docker/healthcheck.sh"]` which validates all five embedded services (Grafana, Loki, Tempo, Mimir, OTel Collector). Overriding with `curl localhost:3000/api/health` would only check Grafana — a regression. Add an inline YAML comment above this `image:` line: `# Image's built-in HEALTHCHECK checks all 5 backends — do NOT override.` so a future reader (or workshop attendee) doesn't "fix" it.
       - **NO port-3000 remapping** (e.g., `3001:3000` is broken — Grafana's `root_url` is hardcoded for 3000; documented in docker-otel-lgtm#461).
       - `restart: unless-stopped`

    5. **Top-level `volumes:`** block declaring the named volume:
       ```yaml
       volumes:
         lgtm-data:
       ```
       (No driver options needed; Docker default local driver suffices.)

    DO NOT add: a `version:` field; a third service for the apps (apps run on host); a `networks:` block (default bridge network is fine for two services on the same compose file); any pre-provisioned dashboards (Phase 7 — WORK-02); any custom `volumes:` for `rabbitmq` (its data does not need to persist across reset cycles for the workshop demo); the `:latest` tag on either image; a custom healthcheck on the lgtm service.
  </action>
  <acceptance_criteria>
    - `test -f docker-compose.yml` exits 0
    - `grep -c 'image: rabbitmq:4.3-management' docker-compose.yml` returns 1
    - `grep -c 'image: grafana/otel-lgtm:0.26.0' docker-compose.yml` returns 1
    - `! grep -E ':latest' docker-compose.yml` exits 0 (no floating tags)
    - `grep -c 'rabbitmq-diagnostics' docker-compose.yml` returns 1 (RabbitMQ healthcheck declared)
    - `grep -c '"-q", "ping"' docker-compose.yml` returns 1 (Stage 1 healthcheck — exact form from RESEARCH)
    - `grep -c '"5672:5672"' docker-compose.yml` returns 1
    - `grep -c '"15672:15672"' docker-compose.yml` returns 1
    - `grep -c '"3000:3000"' docker-compose.yml` returns 1
    - `grep -c '"4317:4317"' docker-compose.yml` returns 1
    - `grep -c '"4318:4318"' docker-compose.yml` returns 1
    - `grep -c 'lgtm-data:/data' docker-compose.yml` returns 1 (named volume mount)
    - `grep -c '^volumes:$' docker-compose.yml` returns 1 (top-level volumes block)
    - `grep -c '^  lgtm-data:$' docker-compose.yml` returns 1 (named volume declaration)
    - The lgtm service has NO healthcheck block: `awk '/^  lgtm:/,/^  [a-z]/' docker-compose.yml | grep -c '^    healthcheck:'` returns 0 (we deliberately don't override the image's built-in)
    - `docker compose config` exits 0 (validates the YAML syntactically and resolves the schema)
    - File is well-formed YAML: `python3 -c "import yaml; yaml.safe_load(open('docker-compose.yml'))"` exits 0 (requires PyYAML; if absent, fall back to `docker compose config`)
  </acceptance_criteria>
  <verify>
    <automated>docker compose config &amp;&amp; grep -q 'image: rabbitmq:4.3-management' docker-compose.yml &amp;&amp; grep -q 'image: grafana/otel-lgtm:0.26.0' docker-compose.yml &amp;&amp; grep -q 'lgtm-data:/data' docker-compose.yml &amp;&amp; ! grep -E ':latest' docker-compose.yml</automated>
  </verify>
  <done>docker-compose.yml exists, valid YAML, declares both services with pinned image tags, RabbitMQ has Stage-1 healthcheck, lgtm has no custom healthcheck (relies on image built-in), lgtm-data named volume mounted at /data, no port-3000 remap, no `:latest` tags.</done>
</task>

<task id="1-03-T2" type="auto">
  <name>Task 2: Verify infra:up green-path (containers reach healthy + persistence across down/up)</name>
  <files>(none — verification only)</files>
  <read_first>
    - docker-compose.yml (just created)
    - mise.toml (created in plan 02 — has `infra:up` / `infra:down` / `infra:reset` tasks)
    - .planning/phases/1-baseline-scaffold/1-RESEARCH.md (lines 347-352 — Pitfall D: lgtm-data must survive infra:down)
  </read_first>
  <action>
    Run a four-step verification proving the docker-compose declaration works AND the persistence behavior demanded by INFRA-04 / Pitfall D actually holds.

    **Step 1 — Bring up cleanly:**
    `docker compose up -d --wait` (or `mise run infra:up`). The `--wait` flag blocks until all healthchecks pass. RabbitMQ should reach healthy within ~30-60s (its `start_period: 30s` + ping retries). lgtm should reach healthy via the image's built-in `HEALTHCHECK` (~30-60s). Total wait should be under 90s on a workshop laptop.

    **Step 2 — Confirm container health:**
    - `docker compose ps --format json` (or `docker compose ps`) shows both containers in `running (healthy)` state.
    - `curl -sf http://localhost:15672 -o /dev/null` exits 0 (RabbitMQ Management UI reachable).
    - `curl -sf http://localhost:3000/api/health -o /dev/null` exits 0 (Grafana health endpoint reachable).

    **Step 3 — Persistence check (Pitfall D):**
    Create a state-bearing artifact in the lgtm-data volume and prove it survives a down/up cycle:
    - `docker compose exec lgtm sh -c 'echo "phase-1-persistence-marker" > /data/persistence-test.txt'` (writes a sentinel file inside the volume mount).
    - `docker compose exec lgtm cat /data/persistence-test.txt` prints `phase-1-persistence-marker`.
    - `docker compose down` (NO `-v` — this is the production `infra:down` semantic).
    - `docker compose up -d --wait` (bring up again).
    - `docker compose exec lgtm cat /data/persistence-test.txt` prints `phase-1-persistence-marker` AGAIN (volume survived).

    **Step 4 — Reset behavior (Pitfall D — destructive path is opt-in):**
    - `docker compose down -v` (this IS the destructive path — what `mise run infra:reset` runs).
    - `docker compose up -d --wait`.
    - `docker compose exec lgtm cat /data/persistence-test.txt` exits NON-zero (file gone — volume was wiped, as expected for reset).

    Cleanup at end: leave containers UP after step 4's last `up -d --wait` so subsequent plans can use them; OR run `docker compose down` (no -v) to leave the system in a clean state for the next executor — RESEARCH does not mandate either; the executor may choose. If a port collision interrupts step 1 or step 3's second `up`, free the port and retry once before failing the task.

    Common failure modes:
    - `--wait` times out (default 30s; healthchecks can take longer on cold start) → re-run with `--wait --wait-timeout 120`.
    - `docker compose exec` fails immediately after `up -d --wait` returns → the container's PID 1 entrypoint may need a brief warm-up; sleep 2 and retry.
  </action>
  <acceptance_criteria>
    - `docker compose up -d --wait --wait-timeout 120` exits 0 on a clean machine
    - `docker compose ps --filter "status=running" --format '{{.Name}} {{.Status}}' | grep -c '(healthy)'` returns 2 (both containers healthy)
    - `curl -sf http://localhost:15672` exits 0 (RabbitMQ Mgmt UI)
    - `curl -sf http://localhost:3000/api/health` exits 0 (Grafana health endpoint inside lgtm)
    - Persistence: write sentinel file via `docker compose exec lgtm sh -c 'echo persist > /data/p.txt'` exits 0; `docker compose down &amp;&amp; docker compose up -d --wait --wait-timeout 120 &amp;&amp; docker compose exec lgtm cat /data/p.txt` outputs `persist`
    - Reset: `docker compose down -v &amp;&amp; docker compose up -d --wait --wait-timeout 120 &amp;&amp; ! docker compose exec lgtm test -f /data/p.txt` exits 0 (file gone after reset)
    - All 5 infrastructure ports listening: `for p in 3000 4317 4318 5672 15672; do ss -tln | grep -q ":${p} " || exit 1; done` exits 0
  </acceptance_criteria>
  <verify>
    <automated>docker compose up -d --wait --wait-timeout 120 &amp;&amp; docker compose ps --format '{{.Status}}' | grep -c '(healthy)' | grep -q '^2$' &amp;&amp; curl -sf http://localhost:15672 -o /dev/null &amp;&amp; curl -sf http://localhost:3000/api/health -o /dev/null</automated>
  </verify>
  <done>Both containers reach healthy via `docker compose up -d --wait`; ports 3000/4317/4318/5672/15672 listen on localhost; sentinel file in /data survives `docker compose down` + `up`; `docker compose down -v` correctly wipes it. Grafana UI reachable at http://localhost:3000, RabbitMQ Mgmt UI at http://localhost:15672.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| Docker Hub → local image cache | First-run pulls of `rabbitmq:4.3-management` and `grafana/otel-lgtm:0.26.0` |
| Container network → host network | Port mappings expose 3000/4317/4318/5672/15672 on the developer's machine |
| Host filesystem → named volume | `lgtm-data:/data` — Docker manages the host-side path under `/var/lib/docker/volumes/` |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-1-03-01 | Tampering | Floating Docker image tags introduce supply-chain drift across cohorts | mitigate | Pinned tags `rabbitmq:4.3-management` and `grafana/otel-lgtm:0.26.0`; no `:latest` |
| T-1-03-02 | Spoofing / EoP | RabbitMQ default credentials (guest/guest) reachable beyond loopback | accept | docker-compose port mappings `"5672:5672"` / `"15672:15672"` bind to all host interfaces in default Compose semantics. For workshop on a laptop this is acceptable (host firewall typically blocks external ingress). Workshop README will note that exposing these ports beyond the laptop requires changing the password. |
| T-1-03-03 | Information Disclosure | Grafana admin/admin default credentials | accept | Workshop scope; instance is local-only; auth out of scope per PROJECT.md |
| T-1-03-04 | DoS | RabbitMQ healthcheck false-positive blocks `--wait` indefinitely | mitigate | Stage-1 ping is the lightest healthcheck (RESEARCH alternatives table); `--wait-timeout 120` gives plenty of headroom; `start_period: 30s` accommodates cold-start |
| T-1-03-05 | DoS | Port 3000 collision with attendee's Next.js / Grafana / Storybook | mitigate | preflight task (plan 02) checks port 3000 BEFORE compose runs, with a `lsof -i:3000` actionable hint |
| T-1-03-06 | Tampering | A "helpful" attendee adds a custom healthcheck to the lgtm service, partially regressing validation | mitigate | Inline YAML comment above lgtm's `image:` line documents that the image's built-in HEALTHCHECK is comprehensive and must not be overridden |

**Phase scope:** Workshop scaffold — no Internet exposure (laptop-local), no persistence beyond Grafana state in a Docker named volume, no real secrets (defaults documented as workshop-only). Out of scope: TLS termination, authn beyond defaults, network policies, image signature verification.
</threat_model>

<verification>
- `docker compose config` validates the YAML schema.
- `docker compose up -d --wait --wait-timeout 120` brings both services to healthy state.
- All 5 infrastructure ports (3000, 4317, 4318, 5672, 15672) listen on localhost.
- `lgtm-data` named volume persists across plain `docker compose down` + `up`.
- `docker compose down -v` wipes `lgtm-data` (destructive path is opt-in).
- The `lgtm` service has no `healthcheck:` block (relies on the image's built-in `HEALTHCHECK`).
- Both image tags are pinned (no `:latest`).
</verification>

<success_criteria>
- INFRA-04 satisfied: `mise run infra:up` (which calls `docker compose up -d --wait` per plan 02) starts both containers healthy; `mise run infra:down` (which calls plain `docker compose down`) preserves Grafana state via the `lgtm-data` named volume across cycles.
- Pitfall D neutralised: persistence verified via the sentinel-file test in T2; `down`/`up` keeps state, `down -v`/`up` (i.e., `mise run infra:reset`) wipes it.
- Don't-Hand-Roll honored: no custom healthcheck for lgtm (image's built-in checks all 5 backends); no shell sleep loops (`--wait` blocks until healthy).
</success_criteria>

<output>
After completion, create `.planning/phases/1-baseline-scaffold/1-03-SUMMARY.md` documenting:
- Final docker-compose.yml service shape (one paragraph: rabbitmq with Stage-1 healthcheck + lgtm with image-builtin healthcheck only + named lgtm-data volume)
- Confirmed `docker compose ps` showing both healthy
- Confirmed persistence-test result (sentinel file survives down/up; wiped on down -v / up)
- Files created: 1
</output>
