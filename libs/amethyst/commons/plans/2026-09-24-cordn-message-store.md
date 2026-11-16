# cordn message store

*2026-09-24*

## The problem

A cordn room's messages exist in exactly one place on the device: a
`LinkedHashMap` inside `CordnGroupChatroom`. `CordnGroupStore` persists the MLS
group state, the `GroupCursor`, echo state, joined-via-request and
`CordnRoomState(draft, lastReadCursor)`. It does not persist a single message.

That is worse than an uncached history, because of how the cursor works.
`GroupCursor.fetchCursor` advances past every message the stream delivers and is
saved on the way out of `catchUp`/`subscribe`. On the next launch,
`msg_fetch_many(after: fetchCursor)` therefore returns nothing — the client has
already consumed everything. The room opens empty and **never refills**.

Rewinding the cursor would not rescue it either. A cordn payload is sealed twice:
the MLS application message, then `SealedPayload` under
`MLS-Exporter("cordn", "group-payload", 32)`. Both keys are epoch-derived, so
once the group ratchets forward the ciphertext the coordinator still holds is
unreadable to us. **Ingestion is the only moment the message is in the clear.**

Three visible symptoms, one cause:

- rooms are empty after every app restart
- the Messages inbox says "No messages yet" for every cordn room, because
  `CordnGroupChatroom._newest` is only ever set by `add`/`addAll`
- leaving and re-opening a room mid-session is fine; killing the app is not

## What Marmot already does

`MarmotMessageStore` / `AndroidMarmotMessageStore` is the same subsystem, built:
an `EncryptedAppendLog` at `<root>/mls_groups/<gid>/messages`, storing the
**decrypted inner event JSON**, with idempotent appends. Its own KDoc states the
principle we need: *"the ratchet moved past the ciphertext long ago, so this
store is the only copy."*

Two differences shape our design rather than letting us copy it outright:

1. **Marmot has a second recovery path; cordn has none.** Marmot's kind-445
   events sit on relays and it retains epoch secrets, so a replay can
   re-decrypt — which is *why* it needs dedup. cordn has no replay, which makes
   the store more load-bearing here, not less.
2. **Marmot messages are `Note`s in `LocalCache`; cordn's must never be.** The
   isolation rule means our read path hydrates `CordnGroupChatroom` directly.

cordn also has **no message expiry**, so there is no prune-before-read step.

## Design

### What a stored entry is

`CordnDeliveredMessage` = `CordnEnvelope` + `cursor`. The envelope already has
`toJson()`/`encode()`/`decode()`. The cursor has to ride along: the room orders
on it and the unread divider compares against it, and it is not derivable from
the envelope.

Entry format — one JSON object per log entry, versioned:

```json
{"v":1,"c":<cursor>,"e":{…envelope…}}
```

A new `CordnDeliveredMessageCodec` beside `CordnEnvelope` in quartz owns it, so
the format is tested where the envelope's own round-trip test already lives.

### Where it is written

`CordnGroupManager.ingest` produces `Delivery.Message(gid, cursor, …)` but is
not `suspend`. The append belongs in the `suspend` caller — the `onDelivery`
path inside `catchUp`/`subscribe` — so that "the room has it" and "disk has it"
happen together rather than on separate beats.

**Ordering is the correctness property.** Append the message, *then* advance and
persist the cursor. Crash between the two and the message is on disk while the
cursor still points before it: the next `catchUp` re-delivers it and dedup drops
it. Do it in the other order and the message is gone forever, with no way back.
This is the one invariant a reviewer should check first.

### Dedup

Marmot dedups on whole-entry string equality via `EncryptedAppendLog.contains`.
That is not enough here: the same message re-delivered after a crash carries the
same envelope but the entry string is only equal if the cursor matches too, and
`Ingestion` can legitimately hand back a different cursor. Dedup on
`envelope.id`, using an in-memory `Set<HexKey>` per group built at load — cheap,
and the log is already read in full on open.

### Where it is read

`CordnRuntime.restoreRoomState` loads draft + read position when a screen opens
a room, deliberately: its KDoc notes that doing it at login would read state for
rooms nobody opens.

The inbox preview breaks that symmetry — it needs `_newest` for every room
*before* any room is opened. So two reads, not one:

- **per-group summary** (newest envelope + count), its own small whole-blob key,
  read at login. Fixes "No messages yet" without touching the logs.
- **full log**, read on room open, hydrating via `addAll` so the existing
  `recompute`/annotation fold runs exactly as it does for live delivery.

The summary is written on the same beat as the append.

### `EncryptedAppendLog` placement — needs a decision

`CordnIndependenceTest` forbids cordn importing anything whose import line
contains "marmot". The log currently lives in
`commons/.../commons/marmot/EncryptedAppendLog.kt`, so cordn cannot use it where
it is.

It is, however, entirely protocol-neutral: it takes `encrypt`/`decrypt` lambdas
and stores opaque strings. Nothing in it knows what Marmot is.

- **A — move it** to a neutral package (`commons/.../store/`), update Marmot's
  import. One implementation, one file format, one migration path. Touches a
  Marmot file (import line only, no behaviour change) and *improves*
  independent-deletability: neither feature owns the primitive any more.
- **B — duplicate it** into a cordn package. ~400 lines of subtle framing,
  folding and legacy-format migration, copied, free to drift.
- **C — whole-blob rewrites** with cordn's existing `atomicWrite` pattern. O(n)
  bytes per send. This is the cost the append log exists to avoid.

**Recommend A.** The guard's own KDoc draws the line at "marmot-named code" —
the coupling it exists to prevent — and a shared neutral primitive is not that.
Worth Vitor's sign-off before it happens, since it edits a Marmot file.

### Backup and migration — needs a decision

`CordnBackup.Archive.Group` carries coordinator, gid, state, cursor and
joined-via-request. No messages.

- **Device migration** (`CordnMigrationStores`) is "this device becomes that
  device". Arriving with no history would be the surprising outcome. **Include.**
  *Done* — as `amethystMessages` on the group document, alongside the other
  additive `amethyst*` fields, written before the cursor on import for the same
  reason the live path writes it that way. Bounded per group by
  `MESSAGE_BUDGET_BYTES`, newest first: these blobs go to hosts whose limits we
  do not know, and a handoff that fails because one group is chatty is worse
  than one that carries a deep but bounded history.
- **Backup** is a recovery artifact whose size the user sees. History could
  multiply it by a large factor. **Exclude for now**, and say so in the backup
  screen's copy rather than letting someone discover it at restore time. *Still
  open.*

Both are reversible later; the format is versioned.

### Deletion

`deleteGroup(gid)` must remove the log and the summary. Leaving a group today
removes the MLS state; leaving plaintext history behind would be a quiet
regression in exactly the property this feature sells. Explicit test.

## Test plan

The failure modes here are lifecycle, not logic, and this branch has already
shipped two bugs of that shape. Tests that matter:

- **crash between append and cursor save** → message survives, re-delivery
  dedups on id. The invariant above, tested directly.
- **round-trip** an envelope with tags, an edit, emoji, and empty content.
- **rehydrate equals live** — the annotation fold built from a loaded log
  matches the one built by ingesting the same messages. The fold is what the
  user sees; equality of the raw list is not enough.
- **`_newest` after load** → the inbox preview shows the last message.
- **`deleteGroup` removes the log** and the summary.
- **cancellation** — a load or append cancelled mid-flight leaves no partial
  entry.

One trap, learned the hard way on this branch: `InMemoryCordnGroupStore`'s
`suspend` methods never reach a suspension point, so cancellation is
*unobservable* in any test using it. The fake for the message store must
actually suspend, or the cancellation test above proves nothing.

## Non-goals

No expiry (cordn has none), no search, no paging — the log loads whole. A room
with tens of thousands of messages would want paging; note it, do not build it
until a room gets there.

## Files

- `quartz/.../cordn/spec02Envelopes/CordnDeliveredMessageCodec.kt` — new
- `commons/.../cordn/CordnGroupStore.kt` — append/load/summary/delete
- `commons/.../cordn/FileCordnStores.kt` — the log-backed implementation
- `commons/.../cordn/CordnGroupManager.kt` — append at ingest, ordered before
  the cursor
- `commons/.../store/EncryptedAppendLog.kt` — moved (decision A)
- `commons/.../marmot/…` — import update only
- `amethyst/.../model/cordn/CordnRuntime.kt` — summary at login, log on open
- `commons/.../cordn/CordnMigrationStores.kt` — carry messages
