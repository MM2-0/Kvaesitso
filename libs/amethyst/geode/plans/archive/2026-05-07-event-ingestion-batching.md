# Event ingestion: write batching + pipelined OK

> **Status:** shipped — all three tiers landed: `IEventStore.batchInsert`/`SQLiteEventStore` group commit, the `IngestQueue` pipeline wired through `NostrServer`/`RelayEngine`, and `parallelVerify` with the `VerifyPolicy`/`VerifyAuthOnlyPolicy` split; `publishGroupCommitSingleClient` and `publishPipelinedSingleClient` benchmarks exist.
> _Audited 2026-06-30._

## Problem

EVENT acceptance is the hot path on a busy relay — every published note,
every reaction, every DM lands here. Today the per-event flow is fully
serial:

1. `RelaySession.handleEvent` (`quartz/nip01Core/relay/server/RelaySession.kt:131`)
   awaits `policy.accept(cmd)` (Schnorr verify if `VerifyPolicy` is in
   the stack — ~0.1 ms on JVM).
2. Awaits `store.insert(cmd.event)` — a single SQLite write, guarded by
   the connection-pool writer mutex (`SQLiteConnectionPool`).
3. Sends `OkMessage` back through the writer coroutine.

`LoadBenchmark.publishThroughputSingleClient` measured **~760 EPS**;
the concurrent variant **~2000 EPS** (limited by SQLite writer mutex
contention, not WS throughput).

## Constraints we must keep

- **OK pairs by event id, not by order**: the OK frame carries the
  event id, so clients pair replies to publishes by id. OKs can be
  emitted in any order — including reordered against the EVENT
  stream, and against each other on the same connection. This frees
  us to fan OKs out as soon as the writer has a per-row decision.
- **OK semantics = accepted, not fsynced**: NIP-01 treats `OK true`
  as "accepted by the relay," not "durably on disk." We can reply as
  soon as SQLite returns success for the row (inside the open
  transaction, before commit/fsync). Group commit can batch the
  fsync without holding OKs back.
- **Per-row decision still required**: the OK reason field is
  per-event (duplicate, blocked, invalid sig, pow, etc.), so we
  cannot fan out a single batch-level OK. Each row's OK must reflect
  that row's outcome.

## Sketch

### Tier 1 — SQLite WAL + group commit (cheap win)

WAL is already on (`PRAGMA journal_mode=WAL`). The pool runs with
`PRAGMA synchronous=OFF`, which is one notch more permissive than
the originally-sketched `synchronous=NORMAL` — we keep it as-is
because the project already accepted the OS-crash trade-off there.

Group commit is implemented via a new `IEventStore.batchInsert`:
the SQLite override holds the writer mutex once and wraps N events
in one `BEGIN IMMEDIATE … COMMIT`. Per-row error isolation uses
SAVEPOINTs so one bad event (expired, duplicate id) doesn't roll
back the good ones — just that row reports `Rejected`.

OKs fire as soon as each row's outcome is known inside the writer
batch, not waiting for fsync (per the OK-semantics constraint above).

Implementation lives in `quartz/nip01Core/store/sqlite/SQLiteEventStore.batchInsertEvents`,
exposed through `IEventStore.batchInsert` and consumed by the new
`IngestQueue` (Tier 2 below).

Expected: **~5–10× write throughput** on a fast SSD. SQLite group
commit is well-trodden territory (nostr-rs-relay, strfry both do it).

### Tier 2 — pipelined OK over multiple in-flight EVENTs

`RelaySession.receive` was single-flight: one EVENT in, process, OK
out, next EVENT. With Tier 2 the connection's pump posts to the
shared `IngestQueue` and returns immediately — the WS pump moves
straight to the next frame.

`IngestQueue` (one per `NostrServer`) holds a bounded
`Channel<Submission>` (capacity = 1024 per the `DEFAULT_CAPACITY`
constant) drained by a single writer coroutine. The writer pulls
the first item to start a batch then `tryReceive`-drains everything
else queued (up to 64 — `DEFAULT_MAX_BATCH`), feeds the whole batch
to `IEventStore.batchInsert`, and dispatches each row's
`onComplete` callback as soon as the batch returns. The callback
turns into the `OK` frame at the WS layer.

OKs are not order-preserving (per the constraints above). The
writer coroutine starts lazily on first `submit` so subscription-
only sessions don't pay for it and don't perturb `Dispatchers.Default`
scheduling.

Expected: hides verify+insert latency behind the next EVENT's parse,
gets us closer to network-bound throughput.

### Tier 3 — eager Schnorr verify off the writer thread

`VerifyPolicy` ran synchronously on `receive`, serialising verify
on each connection's pump coroutine. With Tier 3, `IngestQueue`
takes a `verify: ((Event) -> Boolean)?` hook; when set, the writer
fan-outs a `coroutineScope { events.map { async(Default) { verify(it) } }.awaitAll() }`
on each batch before opening the SQLite transaction. Failed
verifies pre-mark `Rejected` and skip the insert.

Wired through `NostrServer(parallelVerify = ...)` and
`geode.RelayEngine(parallelVerify = ...)`, controlled by
`[options].parallel_verify` in the relay config (default `true`)
and `--no-parallel-verify` on the CLI. Internal direct callers of
`NostrServer` (tests, library users) are opt-in: the flag defaults
to `false` to keep existing `VerifyPolicy`-in-chain semantics
unchanged.

`VerifyPolicy` was split into a parameterised
`VerifyEventsAndAuthPolicy(verifyEvents)` with two singletons:

- `VerifyPolicy` (default): verifies both `EVENT` and `AUTH`.
- `VerifyAuthOnlyPolicy`: verifies `AUTH` only, used when the
  `IngestQueue` is doing the EVENT verify.

When `parallelVerify` is on, `composePolicy` swaps `VerifyPolicy`
for `VerifyAuthOnlyPolicy` so EVENTs aren't verified twice while
AUTH commands — which bypass the queue entirely — keep their
signature check. Without this split, removing `VerifyPolicy` from
the chain would let a forged AUTH event mark a pubkey as
authenticated.

Expected: ≈CPU_COUNT× verify-step speed-up on burst publishes
from a single connection, where verify was previously serial on
that pump.

## How to verify

`geode.perf.LoadBenchmark` carries the perf tests:

- `publishGroupCommitSingleClient` — sequential publish-and-confirm
  on one connection (the same shape as the original
  `publishThroughputSingleClient`). Synchronous publishing means
  batch size is always 1, so this case shows per-event SQLite tx
  cost rather than the group-commit win — kept as a 500-EPS floor
  to catch regressions from the rewrite.
- `publishPipelinedSingleClient` — bursts 10 000 EVENTs back-to-
  back without awaiting intermediate OKs; verifies end-to-end
  throughput and that every event id receives exactly one OK (in
  any order). This is where Tier 1 + Tier 2 both light up.

Existing benchmarks stay as the regression floor.

## Risks

- **Group commit windows**: if a single bad event in the batch fails
  validation, we must not roll back the good ones. The batch needs
  per-row commit semantics (row-level errors → row-level OK false).
- **Backpressure on slow disks**: deeper pipelines on slow storage
  amplify out-of-memory pressure. Cap the in-flight queue depth and
  apply existing slow-client backpressure if it fills.
- **Replay protection**: the existing dedupe table needs to see the
  event before commit, not after — keep that check inside the writer
  coroutine.
