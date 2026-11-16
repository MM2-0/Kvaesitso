# T16 closure roadmap — full coverage with correct behaviours

> **Status:** in-progress — Priorities 1 & 2 closed and the suite passes in-suite, but P3 (CI gating) is deferred and the framesPerGroup rerun + two upstream items remain open, so the roadmap's "fully closed" goal is not reached.
> _Audited 2026-06-30._

**Goal state.** Every spec'd cross-stack scenario green in suite-mode
sweeps, asserting its full design intent (no soft-passes, no vacuous
threshold loosening), with CI gating live and stable.

**Where we are.** The merged `claude/cross-stack-interop-test-XAbYB`
branch ships 22 of 23 spec'd scenarios; each passes individually.
Suite-mode runs hit a residual moq-relay 0.10.x routing race on a
specific subset (~40-60% flake rate). Five scenarios soft-pass the
listener side as a known-flake mitigation. CI is intentionally
unwired pending stability.

This roadmap takes the suite from "passes individually" to "passes
in suite + CI" through three sequential plans. None should be
parallelized — each unblocks the next.

## Priority 1 — `2026-05-07-moq-relay-routing-investigation.md` ✅ CLOSED

> **Closed 2026-05-07** by merging `origin/main` (five `:quic`
> commits: `2a4c07ae`, `d5c854be`, `b622d0c9`, `86a4727e`,
> `31d19258`). The flake was a `:quic` packet-acceptance bug, not
> a moq-relay routing race. Step 1 trace capture in this branch
> first disproved the moq-relay-routing hypothesis (relay
> correctly forwards the upstream SUBSCRIBE); the QUIC team's
> separate work landed in main between the merge base and pickup
> and incidentally closed the speaker-side post-handshake bidi
> drop.

**What landed.**
- A 5-commit merge from `origin/main` carrying ALPN-list
  threading, PTO STREAM retransmits, RFC 9001 §6 1-RTT key
  update, multiconnect/multiplex pacing, and qlog flush.
- Per-test moq-relay TRACE capture instrumentation
  (commit `d7f87971`) — kept in place; useful for follow-up
  regression triage.
- Cross-stack trace artefacts preserved at
  `nestsClient/plans/artefacts/2026-05-07-routing-race-disproven/`
  for post-mortem reference.

**Acceptance bar met.** 5/5 sweep BUILD SUCCESSFUL on
HangInteropTest with their current soft-pass assertions intact.
55/55 tests pass.

## Priority 2 — `2026-05-07-tighten-cross-stack-assertions.md` ✅ CLOSED

> **Closed 2026-05-07** by commits `04be38ad` (initial tightening)
> and `029329af` (recalibration of two floors against empirical
> post-merge steady state). Verification sweep:
> **5/5 BUILD SUCCESSFUL × 22 tests = 110/110 hard-pass** on
> `./gradlew :nestsClient:jvmTest --tests HangInteropTest --tests
> BrowserInteropTest -DnestsHangInterop=true
> -DnestsBrowserInterop=true --rerun-tasks`.

**What landed.**
- 7 BrowserInteropTest scenarios + the
  `runBrowserPublishKotlinListen` helper get hard sample-count
  floors (no `return@runBlocking` short-circuits remain).
- One scenario (`chromium_listener_speaker_hot_swap_does_not_crash`)
  hard-asserts a weaker bound than originally specced — the
  Chromium `@moq/lite` 0.2.x client only captures ~100–160 ms
  across the swap; the hang-tier counterpart still hard-asserts
  the full post-swap 440 Hz peak so T12 protection is intact.
  Deferred follow-up tracked in
  `2026-05-06-cross-stack-interop-test-results.md`.
- Gap matrix updated.

**Acceptance bar met.** 5/5 sweep with hard assertions.

## Priority 3 — `2026-05-07-cross-stack-interop-ci-gating.md` ⏸ DEFERRED (manual run only)

> **Briefly wired then reverted, 2026-05-07.** Commit `21947bc5`
> re-added both jobs to `build.yml`; verification gave 10/10
> sweep × 22 tests = 220/220 hard-pass on the agent rig. A
> subsequent maintainer review judged the cold-cache cost
> (~10 min hang, ~13 min browser) too high for the change-pattern
> (most PRs don't touch audio / MoQ / QUIC) and removed the jobs.
>
> The suites stay green locally and are intended to run before
> merging any change that touches the audio / moq-lite / `:quic`
> stack. Documentation: [`nestsClient/tests/README.md`](../tests/README.md)
> covers when/how/prerequisites/debugging.
>
> The CI-gating plan is kept around in case the wallclock cost
> calculus changes (e.g., a much faster runner, or a smaller
> opt-in subset of the suite that runs in <2 min).

## Independent track — `2026-05-07-framespergroup-production-rerun.md`

This one **doesn't block the closure roadmap**. It can run any
time after Priority 1 is done; it settles whether the test pin
(5) and production default (50) can converge, or whether they
must remain different. Either outcome is shippable.

**What lands.**
- Logcat data from a fresh two-phone field test against current
  nostrnests production at multiple `framesPerGroup` values.
- A data-driven decision on whether to change the production
  default, the test pin, or neither.

**Why it's parallelizable.** Doesn't gate the test suite or CI;
it gates a one-line code change to `NestMoqLiteBroadcaster`'s
default constant.

## After all four close — what remains

Two open items, both genuinely upstream:

1. **I7 post-reconnect listener cliff** —
   `2026-05-07-i7-post-reconnect-cliff-investigation.md`. The I7
   reverse scenario passes its 2.5 s threshold but a regression
   test of "all post-reconnect data arrives" would require the
   moq-relay 0.10.x per-broadcast forward queue fix. Same upstream
   class as the routing race.

2. **I12 GOAWAY** — only re-emerges if an IETF moq-transport
   target lands (currently moq-lite-03 only). Tracked in
   `2026-05-06-cross-stack-interop-test-results.md`.

Beyond those: T16 reaches "full coverage with correct behaviours"
when this roadmap closes.

## Estimated wallclock

- Priority 1: 1–2 days (depends on whether upstream version bump
  fixes it, or we have to file + wait for upstream).
- Priority 2: 0.5 day (mechanical replacement of soft-passes
  with floors, plus rerun verification).
- Priority 3: 0.5 day (re-add CI jobs, run the 10× sweep, merge).
- Independent track (framesPerGroup): 0.5 day (needs prod-rig
  access).

Total: 2.5–3.5 days of focused work to take T16 from "infra
shipped" to "fully closed".

## Plan files

- `2026-05-07-moq-relay-routing-investigation.md`
- `2026-05-07-tighten-cross-stack-assertions.md`
- `2026-05-07-cross-stack-interop-ci-gating.md`
- `2026-05-07-framespergroup-production-rerun.md`
- (this file) `2026-05-07-t16-closure-roadmap.md`
