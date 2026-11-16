# NIP-77 negentropy at scale: strfry-interop snapshot path

> **Status:** shipped — sketches A–D landed: `IEventStore.snapshotIdsForNegentropy`/`IdAndTime` id-and-time snapshot, `max_sync_events`/`frame_size_limit` (500_000)/`max_sessions_per_connection` (200) config, and strfry-verbatim NEG-ERR/NOTICE wording, with `SnapshotIdsForNegentropyTest`, `Nip77NegentropyTest`, and a geode-vs-geode sync test. (E prebuilt-tree is an explicit out-of-scope follow-up; the dedicated `LoadBenchmark` negentropy cases were not added.)
> _Audited 2026-06-30._

## Problem

`RelaySession` delegates NEG-OPEN to `NegSessionRegistry.open`
(`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/relay/server/NegSessionRegistry.kt:58-77`),
which calls `store.snapshotQuery(filters)` and feeds the **entire**
result list of full `Event` objects into `NegentropyServerSession`
(`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip77Negentropy/NegentropyServerSession.kt:40-54`).
For a relay holding 5 M events that match a broad NEG-OPEN filter
(`{kinds: [1, 7]}`), this materialises 5 M `Event` objects with
content/tags/sig — call it ~1 KB/event, ~5 GB transient pressure per
concurrent NEG-OPEN — before the first NEG-MSG goes out.

The negentropy library itself is fine. Internally it pivots into a
sealed `StorageVector` of `(uint64 timestamp, byte[32] id)` items —
40 bytes/entry. The waste is purely in the snapshot step.

Two operator-visible symptoms:

1. NEG-OPEN with a broad filter spikes JVM heap; under load, GC pause
   stalls every other handler on the same process.
2. NEG-OPEN latency before the first NEG-MSG response is O(N) — for
   large stores the client waits seconds for what should be a
   millisecond round-trip.

## Reference: strfry

We want byte-for-byte interop and comparable throughput against
[hoytech/strfry](https://github.com/hoytech/strfry). The relevant
defaults from `strfry/src/apps/relay/RelayNegentropy.cpp` and
`golpe.yaml`:

| Knob                                  | strfry default        | Notes |
|---------------------------------------|-----------------------|-------|
| `frameSizeLimit` (NEG-MSG payload)    | **500_000 bytes**     | Hard-coded `Negentropy ne(storage, 500'000)` |
| `relay__negentropy__maxSyncEvents`    | **1_000_000**         | Hard cap on items in the snapshot |
| `relay__maxSubsPerConnection`         | **200**               | Shared between REQ and NEG sessions |
| `idSize` on the wire                  | **32 bytes**          | NIP-77 v1 (`PROTOCOL_VERSION = 0x61`) |
| Default `since` window                | **none**              | Filter is honored as-is |
| Filter parser                         | same as REQ           | Honors `limit`, `kinds`, `authors`, `#tags` |
| Snapshot data                         | `(created_at, id)` only | LMDB scan inserts into `negentropy::storage::Vector` |

Two-phase materialisation in strfry: `QueryScheduler` scans LMDB
asynchronously, batching matched level-ids into a `vector<uint64_t>`;
on completion the worker pulls each event header, calls
`storageVector.insert(packed.created_at(), packed.id())`, then
`seal()`s. The session response is sent only after seal.

Our equivalent must (a) match the snapshot footprint of ~40 bytes per
event, and (b) match the wire-level frame-cap so a single
reconciliation round-trip exchanges the same payload size.

## Sketch

### A — id-and-time-only snapshot path (memory parity)

Negentropy only needs `(createdAt, id)` pairs. Add a streaming
`IEventStore.snapshotIdsForNegentropy(filter)` that returns
`Sequence<IdAndTime>` (`data class IdAndTime(val createdAt: Long, val id: ByteArray)`)
— no content/tags/sig, no `Event` allocation. The SQLite path is a
plain `SELECT id, created_at FROM event_headers WHERE …` against the
existing `query_by_created_at_id` index
(`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip01Core/store/sqlite/EventIndexesModule.kt:79`).

```kotlin
// IEventStore
suspend fun snapshotIdsForNegentropy(filters: List<Filter>): List<IdAndTime>

// LiveEventStore — already deduplicates union of multi-filter results
suspend fun snapshotIdsForNegentropy(filters: List<Filter>): List<IdAndTime>
```

`NegentropyServerSession` is rewritten to take that list (or a
two-phase `pendingIds → seal` builder) and feed it directly into
`StorageVector`. Memory drops from O(N × ~1 KB) to O(N × 40 B) — for
1 M events, ~40 MB instead of ~1 GB; matches strfry's per-session
footprint.

**id encoding:** strfry stores 32-byte raw ids (NIP-77 v1
`ID_SIZE = 32`); the 16-byte truncation is `FINGERPRINT_SIZE`, only used
internally for SHA-256 accumulator output. The current Kotlin code
already passes the hex `event.id` string to `storage.insert(..)`
(`NegentropyServerSession.kt:50`); the kmp-negentropy library decodes
that to 32 raw bytes. Confirm this is preserved when we switch to a
`ByteArray` id input — do not pre-truncate.

### B — bounded snapshot size, NOT a default `since` window

The previous draft of this plan suggested defaulting `since` to a 90-day
horizon. **Drop that.** strfry doesn't do it — the filter is honored
as-is — and silently bounding `since` would break interop with strfry's
sync clients (e.g. `strfry sync`, nostr-sdk's negentropy reconciler):
they ask for "everything" and rely on getting it.

Instead match strfry's protection: a hard cap on the number of items
that go into a single snapshot.

```toml
[negentropy]
max_sync_events = 1_000_000   # matches strfry's relay__negentropy__maxSyncEvents
```

`NegSessionRegistry.open` checks `count >= max_sync_events` after the
SQLite count or during scan, and on overflow sends:

```
["NEG-ERR", "<subId>", "blocked: too many query results"]
```

Exact wording matches strfry so client error-handling that string-matches
behaves identically. (Spec doesn't normatively define error text; this is
de-facto interop.)

### C — frame-size cap on NEG-MSG: 500_000 bytes (strfry parity)

`NegentropyServerSession` is constructed with `frameSizeLimit = 0`
today. **Set `frameSizeLimit = 500_000`** (matching strfry) so a single
NEG-MSG round-trip carries the same payload as strfry's. 64 KB — what
the previous draft suggested — would force 8× more round-trips for
large reconciliations and noticeably slow sync against strfry-style
clients that expect ~1 MB hex-encoded NEG-MSGs.

The kmp-negentropy library enforces `frameSizeLimit >= 4096` (or `0`
for unlimited). 500_000 is well above the floor. Pure config change in
`NegSessionRegistry.open`; expose via `[negentropy].frame_size_limit`
for operators who tune it down to fit smaller WS frame budgets.

Note: Ktor's WebSocket layer does not impose a default frame cap, so a
~1 MB hex-encoded negentropy payload is delivered intact. The
`[limits].max_ws_frame_bytes` operator knob was removed once Ktor's
unbounded default was confirmed to be sufficient — see the
`refactor(geode): drop [limits] section` commit. If a reverse proxy
is in front of the relay, the proxy's frame cap is the only thing
that can still truncate.

### D — concurrent NEG-OPEN cap, shared with REQ

A NEG-OPEN holds session state until NEG-CLOSE (or connection close).
Today `NegSessionRegistry.sessions` is unbounded. strfry caps at 200
sessions per connection **shared with REQ** — both count against
`relay__maxSubsPerConnection`.

Implement as:
- Reuse the existing per-connection REQ subscription cap (or introduce
  one if absent) and let NEG sessions consume the same budget.
- Default cap: **200** to match strfry. Configurable via
  `[limits].max_subs_per_connection`.
- On overflow strfry sends a NOTICE, not NEG-ERR:
  `["NOTICE", "too many concurrent NEG requests"]`. We should match
  this — `NEG-ERR` is reserved for per-session protocol errors in
  strfry's model.

### E — pre-built fingerprint tree (follow-up, not in scope here)

strfry's real production-scale advantage is the **`negentropy::storage::BTreeLMDB`** backend: a persistent, incrementally-maintained B-tree
of `(timestamp, id)` keys with per-node fingerprint accumulators.
When NEG-OPEN's filter string matches a pre-registered
`NegentropyFilter` tree, strfry skips the materialise-and-seal step
entirely and reconciles directly off the LMDB B-tree in O(log n)
fingerprint computations. See `env.foreach_NegentropyFilter` and
`addStatelessView` in `RelayNegentropy.cpp`.

Equivalent for geode: a `quartz/.../nip77Negentropy/PrebuiltStorage`
backed by a SQLite-side incremental fingerprint index, registered per
canonical filter. This is a substantial piece of work and **out of
scope for this plan** — call it out for a follow-up
(`geode/plans/2026-05-08-negentropy-prebuilt-tree.md`). The A+B+C+D
combination is enough to match strfry's `MemoryView` path, which is
what 99% of ad-hoc NEG-OPENs hit.

## Concrete error-string interop table

Match strfry verbatim — clients in the wild string-match on these:

| Condition                             | strfry frame                                              |
|---------------------------------------|-----------------------------------------------------------|
| Snapshot exceeds `max_sync_events`    | `["NEG-ERR", "<subId>", "blocked: too many query results"]` |
| NEG-MSG for unknown subId             | `["NEG-ERR", "<subId>", "closed: unknown subscription handle"]` |
| Library `reconcile()` parse failure   | `["NEG-ERR", "<subId>", "PROTOCOL-ERROR"]`                |
| Per-connection sub cap exceeded       | `["NOTICE", "too many concurrent NEG requests"]`          |
| NEG-MSG before NEG-OPEN seal complete | `["NOTICE", "negentropy error: got NEG-MSG before NEG-OPEN complete"]` |

The current Kotlin code in `NegSessionRegistry.kt:82` sends
`"error: no negentropy session for <subId>"` for the unknown-subId
case. Update to strfry's wording.

## Wire-level conformance checks

Before merging, verify against strfry as ground truth:

1. **Round-trip with `strfry sync`**: stand up a small geode instance,
   point `strfry sync ws://geode-host` at it, confirm sync completes
   and converges in ≤ comparable round-trips for an N=100k corpus.
2. **idSize**: assert kmp-negentropy round-trips full 32-byte ids;
   the 16-byte fingerprint stays internal to the library.
3. **Protocol version byte**: NIP-77 v1 = `0x61`. Confirm
   `NegentropyServerSession.processMessage` neither emits nor accepts
   a different version byte. Negotiation happens inside the library;
   surface the version on `NIP-11.limitation.negentropy = 1` so clients
   know the v1 path is supported.

## How to verify

Add to `geode/src/test/kotlin/com/vitorpamplona/geode/perf/LoadBenchmark.kt`:

- `negentropyOpenLatencyLargeCorpus` — preload 1 M events, measure
  NEG-OPEN → first NEG-MSG latency. Target **<200 ms** (strfry's
  C++ `MemoryView` path on equivalent hardware does ~80–150 ms;
  we expect a 1.5–2× JVM tax).
- `negentropyMemoryPressure` — open 10 concurrent NEG-OPENs on the
  same large corpus; measure RSS delta. Target **<500 MB**
  (10 × ~40 MB session footprint + scan overhead).
- `negentropyStrfryInterop` — programmatic round-trip against a
  containerised `hoytech/strfry`: same fixture corpus loaded in both,
  cross-sync, assert byte-identical id-set convergence in equal
  round-trips ±1.

Add to `quartz/.../nip77Negentropy/`:

- `NegentropyServerSessionTest.processMessage_atFrameLimit_splitsAcrossRounds`
  — large symmetric difference, assert each NEG-MSG payload is
  ≤ 500_000 bytes and the session completes in N rounds (not 1).

## Risks

- **Cursor lifetime**: holding a SQLite cursor open across the full
  sync is fragile if the client stalls. Materialise to a smaller
  in-memory `List<IdAndTime>` (40 bytes/entry) once at NEG-OPEN time,
  reuse for the lifetime of the session. Bounded by `max_sync_events`.
- **Frame-size 500_000 vs WS frame budget**: hex-encoded payload is
  ~1 MB. If `LimitsSection.max_ws_frame_bytes` is set lower than
  ~1.5 MB the response gets truncated/rejected by the WS layer. Lift
  the WS default OR cap `frame_size_limit` to
  `max_ws_frame_bytes / 2` at startup; fail-fast log a warning if the
  operator's config makes negentropy unusable.
- **No default `since` (intentional, but worth flagging)**: a hostile
  client doing `NEG-OPEN {kinds:[1]}` against a large corpus will hit
  `max_sync_events` and get NEG-ERR. The cap is the protection; do
  not also silently bound `since`.
- **kmp-negentropy library version**: pinned to `v1.0.2`
  (`gradle/libs.versions.toml:9`). Confirm v1.0.2 enforces
  `frameSizeLimit >= 4096`, supports protocol byte `0x61`, and
  internal `ID_SIZE = 32`. If any of those don't match strfry, this
  plan needs an upstream fix on kmp-negentropy first.
