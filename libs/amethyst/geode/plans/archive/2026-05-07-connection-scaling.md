# Connection scaling: pushing past 2 000

> **Status:** shipped — Sketches A (`Channel.UNLIMITED` + `outstanding` cap in `WebSocketSessionPump`), B (`connection_group_size`/`worker_group_size`/`call_group_size` CIO knobs in `StaticConfig`), and C (streaming `ManualFilterDeserializer.fromJson(JsonParser)`) are all in code, with the `connectionsHeldOpen10k` / `connectionsHeldOpenWithFanout` benchmarks in `LoadBenchmark`.
> _Audited 2026-06-30._

> **Status (2026-05-07):** Sketches A and B shipped on
> `claude/connection-scaling-plan-YVjc8`. Sketch C landed as a smaller
> slice in Quartz — the streaming-filter cut — once the audit showed
> the rest of the plan's premise was overstated. Verification
> benchmarks (`connectionsHeldOpen10k`, `connectionsHeldOpenWithFanout`)
> are wired up but only run under `-DrunLoadBenchmark=true`. Remaining
> open work, including fan-out de-duplication, is now tracked in
> [`live-broadcast-fanout-index.md`](./2026-05-07-live-broadcast-fanout-index.md).

## Problem

Current measurement (`LoadBenchmark.connectionsHeldOpen`): **~2 000
concurrent connections** before file-descriptor pressure / Ktor CIO
event-loop saturation. Real-world relays (e.g. nostr.wine, nos.lol)
sustain 10–30k. Geode shouldn't be the bottleneck for an Amethyst-
adjacent operator who scales beyond a thousand-user community.

## What's spending memory per connection today

| Cost                    | Per connection                                           | At 5 000 conns |
| ----------------------- | -------------------------------------------------------- | -------------- |
| `outQueue` Channel      | 8 192 string slots × ~8 b ref                            | ~320 MB pinned |
| `RelaySession`          | `LargeCache<String, Job>` for subs (likely 1–10 entries) | ~negligible    |
| `NegSessionRegistry`    | `HashMap<String, NegentropyServerSession>` — usually 0   | ~negligible    |
| Ktor CIO buffers        | TCP read + write buffers                                 | ~10 MB         |
| Per-session writer Job  | one coroutine                                            | ~few KB        |

The `outQueue` reservation is the dominant cost. The 8 192 was sized
for a worst case "thousands of subscriptions, one event matches all" —
but at 5 000 connections we've over-provisioned by ~300 MB just on
the channel array, even though most connections never fan out.

## Sketch

### A — adaptive outQueue capacity ✅ shipped

> Original plan: start at `INITIAL_OUTGOING_BUFFER = 64` and swap to a
> wider channel under a per-session lock when a high-water mark
> trips. **Not how it shipped.**

What actually shipped is the simpler alternative the original Risks
section called out: `Channel.UNLIMITED` plus an `AtomicInteger`
backlog cap. kotlinx.coroutines' `BufferedChannel` allocates segments
lazily, so an unlimited channel pays only the small head-segment cost
on idle connections — there is no preallocated buffer to scale.

```kotlin
private val outQueue = Channel<String>(capacity = Channel.UNLIMITED)
private val outstanding = AtomicInteger(0)

// producer side
val depth = outstanding.incrementAndGet()
if (depth > MAX_OUTGOING_BUFFER) {            // 8192
    outstanding.decrementAndGet()
    droppedForBackpressure = true
    outQueue.close()                          // NIP-01: drop the conn
    return@connect
}
val res = outQueue.trySend(json)
if (!res.isSuccess) outstanding.decrementAndGet()  // closed concurrently

// writer side
for (json in outQueue) {
    ws.outgoing.send(Frame.Text(json))
    outstanding.decrementAndGet()
}
```

Memory characteristic the plan asked for is intact: idle connections
no longer reserve an 8 192-slot fixed buffer; hot fan-out connections
still get bounded at the same 2 MiB cap before the slow-client cutoff
fires. NIP-01 ordering is preserved (no silent drop — connection is
killed at the cap).

Implementation: `geode/.../server/WebSocketSessionPump.kt`. The
channel-swap approach was rejected because
`Channel.UNLIMITED` already gives the lazy-allocation behavior the
swap was simulating, with none of the swap's race surface.

### B — per-relay event-loop pool sizing ✅ shipped

Three optional knobs added to `[network]` in `StaticConfig`:

```toml
[network]
host = "0.0.0.0"
port = 7447
path = "/"
# connection_group_size = 4
# worker_group_size = 16
# call_group_size = 64
```

Default is **`null` (Ktor default)** — no behavior change unless an
operator explicitly tunes them. The values are wired through
`KtorRelay` into the new `embeddedServer(factory = CIO,
rootConfig = serverConfig {…}, configure = {…})` overload (the
short-form `embeddedServer(factory, host, port) {…}` overload doesn't
expose CIO config). The auto-connector that the short form created
now has to be added explicitly via `connector { host = …; port = …}`.

`config.example.toml` documents the knobs and includes the operator
note that targeting >1k connections needs `ulimit -n 65536` (or
matching `LimitNOFILE=` in a systemd unit).

### C — reduce per-message JSON allocations ✅ partially shipped (in Quartz)

> Original plan claim: "`OptimizedJsonMapper.fromJsonToCommand`
> allocates a `JsonNode` tree per incoming frame." **Overstated.**

Audit of `quartz/.../jackson` showed the Command/Message envelope is
already streaming:

| Path                    | Already streaming? | Tree alloc?                                        |
| ----------------------- | ------------------ | -------------------------------------------------- |
| `MessageDeserializer`   | yes                | only for `COUNT` result (rare)                     |
| `CommandDeserializer`   | yes                | only for **filter sub-objects** in REQ/COUNT/NEG-OPEN |
| `EventDeserializer`     | yes                | none — `currentName().hashCode()` dispatch         |
| `ManualFilterDeserializer` | **no**          | `jp.codec.readTree(jp)` per filter                 |

So the only relay-inbound tree allocation worth chasing was filter
parsing — the bulk of the per-frame allocations on a REQ-heavy
relay.

What shipped: a streaming `ManualFilterDeserializer.fromJson(jp:
JsonParser)` modeled exactly on `EventDeserializer`. Token-loop with
field-name dispatch (`ids` / `authors` / `kinds` / `since` / `until` /
`limit` / `search`, plus dynamic `#x` / `&x` tag keys), and
`readStringArray` / `readIntArray` helpers that drop invalid entries
silently to match the tree path's `mapNotNull { asTextOrNull() }`
tolerance. Wired into all four internal call sites:
`FilterDeserializer.deserialize` and the three `CommandDeserializer`
paths (REQ, COUNT, NEG-OPEN).

The tree-based `fromJson(ObjectNode)` overload is retained for
external/cross-format adapters (Quartz is a published library).

What was NOT done — and why:
- **Streaming Jackson for the Command envelope**: already streaming.
  No allocation to remove.
- **kotlinx-serialization for the inbound path**: not pursued. The
  cross-mapper round-trip tests in `KotlinSerializationMapperTest`
  show the two formats are interchangeable, but the engine swap is a
  much larger lift than the filter cut and there's no evidence the
  KS path is faster on this code shape.
- **Per-session `ObjectMapper`**: Jackson's `ObjectMapper` is
  thread-safe and stateless — sharing one is the recommended pattern.
  Per-session would *increase* allocation, not decrease it.

## How to verify ✅ shipped

Two new benchmarks in `geode.perf.LoadBenchmark`, gated behind
`-DrunLoadBenchmark=true`:

- **`connectionsHeldOpen10k`** — opens 10 000 idle WebSocket
  connections, asserts every one settles to EOSE inside 120 s, and
  measures retained JVM heap (after `System.gc()` + 200 ms settle)
  with a 1 GiB ceiling assertion. Requires `ulimit -n 32768` on
  Linux.
- **`connectionsHeldOpenWithFanout`** — 5 000 subscribers all
  matching `kinds:[1]`, one publisher emitting `targetEps × duration`
  events, prints p50 / p99 last-fanout latency. No assertion on
  latency — just regression-detection via stdout logging.

The original `connectionsHeldOpen` benchmark stays as the **baseline
floor (~2 000 conns)** for before/after comparisons.

Note on heap-vs-RSS: the original plan said "RSS stays under 1 GB"
but the JVM can only measure heap from inside; `Runtime.totalMemory
- freeMemory` is what the benchmark asserts on. RSS will be higher
because of code, native buffers, off-heap (Ktor CIO), etc.

## Risks (post-implementation)

- ~~**Adaptive channel swap is fiddly**~~ — sidestepped by using
  `Channel.UNLIMITED` instead of swapping bounded channels.
- **Bumping CIO group sizes can hurt** — kept the defaults `null`.
  Operators must opt in, and the docstrings explicitly say to
  benchmark before/after.
- **OS-level FD limit** — documented in `config.example.toml` next to
  the CIO knobs. Test prereq is also documented in the benchmark
  KDoc.

## Open work

- **Fan-out de-duplication** — when one EVENT matches N subscribers,
  we currently re-serialize and copy the JSON N times into N
  channels. Caching one pre-serialized payload per event and
  broadcasting a shared reference is a much bigger win than anything
  in this plan; tracked in
  [`live-broadcast-fanout-index.md`](./2026-05-07-live-broadcast-fanout-index.md).
- **Filter-matching index** — same plan. At 10k conns × ~5 filters
  that's 50k evaluations per published EVENT, almost all of which
  could be culled by indexing subscriptions on `kinds` / `authors` /
  `#e` / `#p`.
- **Netty engine evaluation** — Ktor's Netty engine handles many idle
  connections with measurably lower per-connection overhead than
  CIO. Not pursued here because it changes the transport layer
  wholesale; revisit only if the CIO knobs in (B) prove insufficient
  for an operator at 20k+ connections.
