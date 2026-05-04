# Phase 16: Head Sampling + W3C Baggage - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-04
**Phase:** 16-head-sampling-w3c-baggage
**Areas discussed:** Baggage lifecycle scoping, SpanProcessor wiring pattern, Listener advice restructuring, README sub-lesson narrative

---

## Baggage Lifecycle Scoping

### Where should the baggage Context be scoped?

| Option | Description | Selected |
|--------|-------------|----------|
| In the controller | OrderController.create() wraps orderService.place() in try(Scope) with baggage Context. Attendees see the Baggage API at the HTTP entry point. | ✓ |
| In a servlet filter | New BaggageFilter reads X-Customer-Tier before controller. Clean separation but hides the Baggage API. | |
| You decide | Let Claude pick. | |

**User's choice:** In the controller (Recommended)

### Baggage key naming

| Option | Description | Selected |
|--------|-------------|----------|
| customer-tier | Baggage key = 'customer-tier'. Span attribute = 'baggage.customer-tier'. Matches BAG-04 exactly. | ✓ |
| order.customer-tier | Namespaced key. Span attribute = 'baggage.order.customer-tier'. | |
| You decide | Let Claude pick. | |

**User's choice:** customer-tier (Recommended)

### Scope extent

| Option | Description | Selected |
|--------|-------------|----------|
| Just place() | try(Scope) wraps only orderService.place(). Baggage active for AMQP + HTTP hops. | ✓ |
| Entire method body | try(Scope) wraps everything after header extraction. Simpler structure. | |
| You decide | Let Claude pick. | |

**User's choice:** Just place() (Recommended)

### Allowlist configurability

| Option | Description | Selected |
|--------|-------------|----------|
| Constructor-configurable | BaggageSpanAttributeProcessor takes Set<String> allowedKeys. Reusable, teaches cardinality-control. | ✓ |
| Hardcoded | Single-key allowlist inside the class. Simpler but less reusable. | |
| You decide | Let Claude pick. | |

**User's choice:** Constructor-configurable (Recommended)

### SERVER span baggage attribute

| Option | Description | Selected |
|--------|-------------|----------|
| Leave it — teaching moment | SERVER span missing attribute demonstrates baggage lifecycle. README calls it out. | ✓ |
| Stamp SERVER span too | Move baggage to a filter or add post-hoc stamp. Consistent attributes. | |

**User's choice:** Leave it — teaching moment (Recommended)

### Default value mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| Annotation defaultValue | @RequestHeader(defaultValue = "standard"). Spring handles the default. | ✓ |
| Null check in code | Ternary in method body. Mirrors X-Idempotency-Key pattern. | |

**User's choice:** Annotation defaultValue (Recommended)

### Load script behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Rotate through gold/silver/standard | Random pick per request. Demonstrates cardinality-control lesson. | ✓ |
| Fixed 'gold' | Always sends gold. Simpler. | |
| No header in load.sh | Manual-only per BAG-04. | |

**User's choice:** Rotate through gold/silver/standard (Recommended)

---

## SpanProcessor Wiring Pattern

### How to wire BaggageSpanAttributeProcessor

| Option | Description | Selected |
|--------|-------------|----------|
| Inline alongside batch | Create inside buildTracerProvider() next to BatchSpanProcessor. Self-contained. | ✓ |
| Pass as parameter | Change method signature to accept SpanProcessor... More flexible. | |
| You decide | Let Claude pick. | |

**User's choice:** Inline alongside batch (Recommended)

### Plan split strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Split into separate plans | 4 plans: 16-01 sampling, 16-02 processor, 16-03 baggage e2e, 16-04 tag. | ✓ |
| Single plan for buildTracerProvider() | Both sampler + processor in one plan. Fewer plans. | |
| You decide | Let planner determine. | |

**User's choice:** Split into separate plans (Recommended)

### Package location

| Option | Description | Selected |
|--------|-------------|----------|
| otel-bootstrap/context/ | New 'context' package. Cross-cutting concern separate from amqp/ and http/. | ✓ |
| otel-bootstrap/ root | Flat in com.example.otel. Simpler. | |
| You decide | Let planner place it. | |

**User's choice:** otel-bootstrap/context/ (Recommended)

---

## Listener Advice Restructuring

### Nesting shape

| Option | Description | Selected |
|--------|-------------|----------|
| Outer extracted + inner span | Two nested try-with-resources. Minimal diff from current code. | ✓ |
| Single scope with Context.with(span) | Combine into one Context. Single try. Less common API. | |
| You decide | Let Claude pick. | |

**User's choice:** Outer extracted + inner span (Recommended)

### Backwards-compat note

| Option | Description | Selected |
|--------|-------------|----------|
| Just describe the change | Commit says the change, no compat note. Code speaks for itself. | ✓ |
| Note backwards compat explicitly | Commit/comment notes the backwards-compat nature. | |
| You decide | Let Claude pick. | |

**User's choice:** Just describe the change

### Keep or remove .setParent(extracted)

| Option | Description | Selected |
|--------|-------------|----------|
| Keep it | Redundant but LOAD-BEARING teaching artifact. Explicit > implicit. | ✓ |
| Remove it | Cleaner, teaches that makeCurrent() sets implicit parent. | |

**User's choice:** Keep it (Recommended)

---

## README Sub-Lesson Narrative

### F2-3 callout placement

| Option | Description | Selected |
|--------|-------------|----------|
| Warning box BEFORE the code change | Prominent callout before sampler swap. Prevents confusion. | ✓ |
| Callout AFTER the demo results | Encourages debugging before revealing the answer. | |
| Dedicated 'Gotchas' section at the end | Separate section covering interactions and edge cases. | |

**User's choice:** Warning box BEFORE the code change (Recommended)

### Sub-section structure

| Option | Description | Selected |
|--------|-------------|----------|
| Two numbered sub-sections | Step 16a and Step 16b as ### sub-sections. Each has own walkthrough. | ✓ |
| Single flat section | One Step 16, no sub-numbering. | |
| Two separate step numbers | Step 16 + Step 17. Conflicts with Phase 17 AMQP. | |

**User's choice:** Two numbered sub-sections (Recommended)

### Head-vs-tail contrast table format

| Option | Description | Selected |
|--------|-------------|----------|
| Side-by-side markdown table | 5-dimension comparison: Where, Sees, Bandwidth, Decides on, Trade-off. | ✓ |
| Prose comparison | Two paragraphs. More narrative, less scannable. | |
| You decide | Let Claude pick. | |

**User's choice:** Side-by-side markdown table (Recommended)

---

## Claude's Discretion

- BaggageSpanAttributeProcessor implementation details (reads `Baggage.fromContext(parentContext)` in `onStart`)
- Exact sampler swap diff (one-line change per service)
- TestOtelConfiguration updates (X-4 mitigation)
- verify:head-sampling and verify:baggage mise task implementations
- Integration test assertions for baggage propagation
- Screenshot deferral to Phase 18 Playwright pipeline
- OtelSdkConfiguration comment updates
- README §16 exact wording and structure
- `orderId` variable scoping across try(Scope) block

## Deferred Ideas

None — discussion stayed within phase scope.
