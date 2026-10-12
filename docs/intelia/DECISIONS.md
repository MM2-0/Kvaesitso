# Architectural decisions

Record durable decisions here; factual run results belong in STATUS. Revisit a
decision with new evidence, preserving its rationale and superseding entry.

## D001 — Preserve the upstream baseline (accepted, 2026-10-12)

Intelia evolves the existing repository. Preserve Kvaesitso history, attribution,
GPL-3.0-or-later policy and module-specific licenses. Bootstrap is documentation
only, with no dependency, manifest, namespace or behavior change. This protects
recent reliability fixes and makes the first feature's impact reviewable.

## D002 — Shared, compact agent context (accepted, 2026-10-12)

`AGENTS.md` is canonical for both coding agents; `CLAUDE.md` imports it. STATUS
and numbered tasks provide current context. Specialized documents are consulted
on demand. One coordinator is the default, with optional bounded delegation and
one integrator. This reduces repeated audits and competing edits.

## D003 — FLOW starts with explicit local app aliases (accepted, 2026-10-12)

Task 010 delivers exact alias matching, local persistence, visible target preview
and one user-initiated app launch. Reuse search-action storage, settings and result
chips. Do not reinterpret `AppSearchAction` (ACTION_SEARCH) as app launching.
Reuse profile-aware `Application.launch` through a dedicated adapter. First
targets are visible apps in the current personal profile; work/private profiles
and generalized execution are deferred rather than silently retargeted.

No new Room table is planned for this slice: use a new action discriminator and
versioned payload in existing entity fields, with backward-compatible readers
and backup round-trip checks. Recheck that this fits the implementation before
editing; record a reasoned amendment if a schema change becomes necessary.

## D004 — New interaction remains launcher-local (accepted, 2026-10-12)

ORBIT extends scaffold gesture state; MORPH extends existing home components;
POCKET may later be a temporary scaffold component. No new overlay or
Accessibility automation is approved. Existing inherited global actions remain
unchanged. Multi-step workflows require explicit cancellation, return and failure
semantics before implementation.

## D005 — AI is optional authoring assistance (accepted, 2026-10-12)

No AI SDK, external service or credential is introduced now. A later AI feature
may propose workflows, which must be inspectable and independently executable
without AI. Sending user data and introducing an external service require their
own explicit scope and consent model.

## D006 — Measurement precedes performance claims (accepted, 2026-10-12)

Cloud build/unit-test success is a development baseline. Device UX, jank, startup,
heap and battery claims require recorded device evidence. PERFORMANCE budgets
are proposed targets until measured. Fix observed regressions before speculative
optimizations or adding benchmark dependencies.
