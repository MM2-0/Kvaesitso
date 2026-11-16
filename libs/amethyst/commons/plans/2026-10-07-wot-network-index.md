# Web of Trust network index (NIP-85 kind 30382, ~300k users)

Status: **implemented 2026-10-07** (phases 1–5 except public chats; see §7).

## 1. Goal

Know, in under a microsecond and without a network call, whether a pubkey is inside the
logged-in user's extended network and how much the user's trust provider trusts them.
The answer gates:

- DMs: the **Known** vs **New Requests** split.
- Notifications: the existing **Curated** filter (`TopFilter.Selected`).
- Replies, and possibly public chat messages (later phases).
- The rank / follower-count badges, which today come from a per-profile relay subscription.

The source is the user's NIP-85 trust provider: one kind 30382 card per member of the
network, all signed by the provider key named in the user's kind 10040. The first provider
we onboard is Brainstorm (GrapeRank), from a new **Web of Trust** settings screen. That
screen also sets the minimum accepted score, default **5**.

## 2. What we measured (2026-10-07)

All numbers come from the live `wss://scores.brainstorm.world`. That is the relay real
Brainstorm 10040s point at, and the one Brainstorm's web app uses
(`VITE_NIP85_RELAY_URL`). Two real per-user provider keys were tested.

| Fact | Value |
|---|---|
| Relay | strfry, `max_limit` **100,000**, NIP-45 COUNT and NIP-77 negentropy supported |
| Older relay `nip85.brainstorm.world` | `max_limit` **500**, negentropy supported. Some 10040s in the wild (fiatjaf's fixture) still point here. |
| Cards per user | **154,755** and **149,609** |
| Card size | ~520 B of JSON. Tags: `d`, `rank`, `followers`, `reporters`, `muters`, `client`, `hops` |
| Cold download | **93 MB / 34 s** (155k cards, 2 REQs of 100k, from a datacenter). gzip shrinks the stream ~3.3×. |
| Shared timestamps | **80,577** cards of one user carry the *same* `created_at`, because the provider publishes in batches. |
| Churn | Provider A: 9,704 cards re-signed in 3 days, 25,779 in 30 days. Provider B: all 155k re-signed today. |
| Deletions | Provider A has **169,317** kind-5 events, each carrying many `a` tags (`30382:<P>:<target>`). |
| Rank (0–100) | ≥ 5: **77,270 / 154,755** and **75,283 / 149,609**, so about half the network passes the default. Provider B also has 749 rank-0 cards. |
| Event-object heap | 300k card-shaped events ≈ **254 MB** (~850 B each), before `Note`/`User`/flows |
| Lookup, 310k keys (JVM) | `HashSet<String>` 0.1–0.25 µs but ~14 MB plus the strings. Sorted `LongArray` with hex parse ~0.4–0.5 µs at 2.5 MB. |
| Index file load (JVM) | 6.3 MB file, read and decoded in **14–40 ms** |
| Signature check | ~75 µs per event on JVM, 1–2k events/s parse+verify on a phone (`quartz/plans/2026-07-02-nostrclient-receiver-perf.md`). That is minutes for 300k. |

### Phase 0 on the JVM: download → verify → build → save (2026-10-07)

`quartz/src/jvmTest/.../prodbench/WotNetworkIndexBenchmark.kt` (opt-in with
`-PprodRelayBench=1`) runs the real Quartz stack: `NostrClient` + `fetchAllPages`, Schnorr
verify, the D5/D6 index layout, and the D11 files. Observer
`460c25e6…065c`, whose 10040 names rank provider `7d7ffd72…9377` on
`wss://scores.brainstorm.world`. Run from a 4-core cloud container.

| Stage | Result |
|---|---|
| Download (3 pages, `DRAINED`) | **5.2 s**, 151,831 cards, ~29k cards/s |
| Heap if events are held | 84.6 MB (556 B per parsed card), which the one-pass pipeline avoids |
| Verify every signature (4 cores) | **3.2 s**, 0 invalid, ~48k verifies/s (83 µs per verify per core) |
| Build index (sort, dedupe, drop rank 0) | **0.39 s**, 151,153 entries; **77,671 with rank ≥ 5** |
| Save index + ids files | **0.23 s**; index **3.3 MB**, ids **6.0 MB** |
| Load + decode index | **34 ms** first, 17 ms best |
| Lookup (hex read + binary search) | ~400 ns |
| **One pass** (verify while downloading, no events held, then build + save) | **8.5 s** total: download done at 7.9 s, last verify at 8.0 s |

Notes:
- Paging by `until` worked here (`DRAINED`), because this relay's `max_limit` is 100k.
- At 300k, expect roughly double: about 15–20 s on this hardware.
- The build stage used a boxed sort; a primitive sort will be faster.
- **Not measured yet: a phone.** Signature checks dominate the CPU there (the perf plan
  puts parse plus verify at 1–2k/s), so expect minutes, not seconds. That run is still
  needed for D9b/D9c.

How Brainstorm publishes (read from `NosFabrica/brainstorm_server`,
`app/message_queue_tasks/upload_nostr_events.py`):

- **Score runs.** Scores are recomputed on a schedule (default `schedule_interval_seconds = 604800`, 7 days) or on demand. In steady state only changed scores are published; an occasional full sync re-asserts every card above the cutoff.
- **Removal.** When someone falls out of the network, Brainstorm first publishes a **rank-0 card with no `hops`**, then a kind-5 deletion by `a` tag. The zero card is there "so that even if the relay rejects kind 5, the score is effectively zeroed out".
- **Provider keys are per user.** `authors=[P]` returns exactly that user's network and nothing else.

## 3. What exists today (survey)

| Piece | Where | Reuse? |
|---|---|---|
| 10040 parsing, provider slots | `commons/.../model/trustedAssertions/TrustProviderListState.kt` (`liveUserRankProvider`, `liveUserFollowerCount`), with local backup in `AccountSettings.backupTrustProviderList` | Reuse as is |
| Per-profile rank/followers | `UserCardsCache.rankFlow` / `followerCountStrFlow`, read at `commonsUI/.../AccountUserObservers.kt:470,485`. Cards are fetched per profile by `UserCardsSubAssembler` and stored as `AddressableNote`s in `LocalCache`. | Migrate (D16) |
| Relay ops | `fetchAllPages` (streams, pages by `until`, reports `UNPAGEABLE`), `negentropyReconcile` + `NegentropyLocalIndex`, `count`, `ParallelEventVerifier`, `AdaptiveRelayLimiter` in `quartz/.../relay/client/accessories/` | Reuse |
| Compact hex keys | `Hex.readLong(hex, offset)`, `Hex.toLong128` in `quartz/utils/Hex.kt`. `SeenIds` uses the same 128-bit-prefix idea for event ids. | Reuse |
| Per-account files | `AccountCacheState.createAccount` makes `filesDir/accounts/<pubkey>/` (`:275`); `deleteAccountFiles` removes it on logoff; okio `platformFileSystem`; `atomicWrite` pattern in `FileCordnStores.kt` | Reuse |
| Synced settings | `AccountSyncedSettings` (NIP-78 kind 30078 `AmethystSettings`) | Add `minTrustScore` |
| Foreground signal | `ForegroundTracker.isForeground` (Android) | Trigger for small updates |
| Background work | WorkManager (`NotificationCatchUpWorker`, `ScheduledPostWorker`, which resolves the account from `accountsCache`) | Weekly full check |
| DM split | `ChatroomListKnownFeedFilter.kt:70-78` / `ChatroomListNewFeedFilter.kt:47-56` (incremental path `:167-173`). Known = a sender is followed, or I sent a message. | Extend |
| Notifications | `NotificationFeedFilter.kt:712` author gate; Curated = `TopFilter.Selected` | Extend |
| Push notifications | Renderers apply only `account.isAcceptable(note)` and never the notification TopFilter | Decide (D18) |
| Replies | `ThreadLevelCalculator.kt:109-114` sorts into tiers (thread author, me, follows, rest) | Extend later |
| Friends-of-friends | `commons/wot/WoTService.kt`: Desktop-only badge, in memory, not persisted | Independent, no change |
| 10040 editing UI | None in the app; only `amy graperank register` | New screen |

There is **no Android event store**. `LocalCache` lives only in memory, and every event received
on the shared client is filed into it by `CacheClientConnector`
(`AppModules.kt:873`: `EventCollector(client) { cache.justConsume(...) }`).

## 4. Decisions

Each has a recommendation (**Rec.**) or a recorded decision. The ones still marked **❓** are
open; §6 logs what was decided.

### D1. Which provider feeds the index
Use the 10040 entry whose service is `30382:rank` (`liveUserRankProvider`): its pubkey `P`
and relay hint `R`. That is one provider, with no multi-provider merge (NIP-85 doesn't
specify one). Brainstorm is only the onboarding path; any NIP-85 provider listed under
`30382:rank` works the same way.

**Rec.** A change of `P` or `R` (new 10040, re-registration) throws the index away and
starts a fresh cold sync. The file header records `(P, R)` so a mismatch is detected on load.

### D2. Membership rule
`accepted(pk) = pk == me || pk ∈ follows || (indexReady && rank(pk) >= minScore)`

- Follows always pass, so a stale provider never demotes someone you follow.
- **No card means outside the network.** Brainstorm only publishes above its cutoff.
- **A rank-0 card counts as removed** (Brainstorm's removal marker), as does a card
  deleted by kind 5.
- Mutes, blocks and spam flags still win over everything (`isHidden` is checked first, as today).
- `hops` is stored but **not** used to gate (D7).

### D3. Before the index is ready
When the index is missing, still downloading, or there is no rank provider, every surface
behaves **exactly as today**. Otherwise the first sync would empty the DM list and
notifications for minutes. "Ready" means a complete snapshot for the current `(P, R)` is loaded.

"Missing" and "not loaded yet" are different states:
- **Missing** (no file, or a `(P, R)` mismatch): let everything through, as today.
- **On disk but not loaded yet** (a process just started, e.g. by a push): **wait for the
  load**, bounded by a timeout, before deciding. Push filtering depends on this (D11, D18);
  otherwise every notification that wakes a cold process would skip the filter.

### D4. Use LocalCache? **No.**
- **Size.** 300k cards ≈ 254 MB for the event objects alone, plus 300k `AddressableNote`s and the `User` objects `about.cards()` creates, against ~6 MB for the index.
- **Nothing persists.** LocalCache is in memory only on Android, so it would have to re-download every start.
- **The shared client files everything.** `CacheClientConnector` puts every event on the shared client into LocalCache, so the sync **must use its own client** (D8).

LocalCache keeps what it has today: the account's **own** contact cards (encrypted
nicknames, signed by the account, `UserAssertionsState`) and any 30382 that arrives
incidentally.

### D5. Key width in the index
| Option | Bytes / entry | Risk |
|---|---|---|
| 64-bit prefix | 8 | Grinding a key whose first 64 bits match *some* member is roughly 2^64 / 300k ≈ 2^46 key generations. Our estimate: about a day of GPU vanity grinding, which buys a spammer a place in Known. |
| **128-bit prefix** | 16 | Not feasible to grind; random collisions are effectively zero |
| Full 256 bits | 32 | No gain over 128 for this purpose |

**Rec.** 128-bit: two longs per entry, sorted, via `Hex.readLong(pk, 0)` / `Hex.readLong(pk, 16)`
(no allocation).

### D6. In-memory structure
An immutable `NetworkSnapshot`:

```
keys      LongArray(2N)   sorted by (hi, lo) — binary search, ~19 steps at 300k
rank      ByteArray(N)    0..100 (clamped; unsigned read)
hops      ByteArray(N)    0..127, -1 = unknown
followers IntArray(N)
provider, relay, syncCursor, lastFullCheck, version
```

- Published as `StateFlow<NetworkSnapshot?>`. Each update builds a new snapshot and swaps
  it in atomically; readers never lock.
- About 6.3 MB at 300k. Alternatives considered:
  - Open-addressing hash: same size, O(1), but needs a rehash on load and gives no sorted merge.
  - `HashSet<String>`: 7–8× the memory.
- **❓ D6a.** Also memoize the two prefix longs on `User` so hot paths skip the hex read?
  **Rec.** Not in v1: `Hex.readLong` is cheap and allocation-free. Revisit if profiling says otherwise.

### D7. Which tag values to keep
Keep `rank` (the gate), `followers` (the badge), and `hops` (cheap, useful for UI such as
"3 hops away").

**Decided: don't store `reporters` / `muters`.** Brainstorm publishes these counts of people
in your network who reported or muted someone; they would cost 2 × 4 B × N ≈ 2.4 MB. The
file format is versioned, so this can be revisited without a migration.

### D8. Relay client for the sync
**A second `NostrClient`** with the same `websocketBuilder`, so Tor routing and the relay
blocklist still apply. It is **not** wrapped by `CacheClientConnector`, is created for a sync
and disconnected afterwards. This also keeps the bulk sync from using up the shared
client's subscription slots. Desktop already does this for its kind-10050 indexer client
(`Main.kt:1039`).

### D9. Cold download
1. Read NIP-11 for `R`: `max_limit`, `max_subscriptions`, negentropy support.
2. **Path A (preferred when `max_limit` ≥ ~100k):** `fetchAllPages(R, {kinds:[30382], authors:[P]})`, streaming into a growing set of arrays. Events are never held.
3. **Path B (when A ends `UNPAGEABLE`):** if one second holds more cards than `max_limit` (likely on `nip85.brainstorm.world`), use `negentropyReconcile` with an empty local side to list the ids, then fetch them in batches of 500 by id. The perf plan measured fetch-by-id at up to 30–40k/s on a 33k-event corpus.
4. Sort, deduplicate by d-tag (newest `created_at` wins), drop rank 0, write the file (D11), swap the snapshot in.
5. Every event must have `kind == 30382`, `pubkey == P`, and a 64-hex d-tag. Anything else is dropped.

**D9a. Signature checks — decided: check every signature.** A relay that inserts forged
cards could otherwise make strangers "known". Use `ParallelEventVerifier` on low-priority
threads, so its backlog limit slows the socket instead of growing memory. This is roughly
2.5–5 min of CPU on a phone for the cold sync, once; small updates and the full check (D12)
verify too. A failed signature drops that event.

**❓ D9b. Resuming an interrupted cold download.** A 70–180 MB download on mobile can be killed.
**Rec.** v1 restarts from scratch but writes a "pages done" checkpoint (the `until` cursor
plus partial arrays to a `.partial` file), so Path A can resume. That is cheap to add because
`fetchAllPages` reports each page boundary (`onNewPage`).

**❓ D9c. Network policy.** The cold sync is 50–180 MB.
**Rec.** Run it on any network when the user starts it from the settings screen (they are
looking at a progress bar). Background cold syncs (e.g. after a provider change on another
device) wait for an unmetered network.

### D10. Small updates
The trigger is the app coming to the foreground (`ForegroundTracker`), plus right after the
user asks Brainstorm to recompute. It runs when the last update is more than **15 minutes**
old. Brainstorm recomputes weekly, but other providers may compute on demand, and with the
pre-check below a check that finds nothing costs two COUNTs (~30 ms each once connected).

1. **Anything new?** `COUNT {30382, authors:[P], since: cursor}` against the cards held at or
   after the cursor (entries + tombstones), plus `COUNT {5, authors:[P], since: cursor+1}`
   against 0. Both match → stop: "nothing new". Counting the cursor's own second catches a
   batch that was still arriving at the last sync (providers sign a whole run with one
   `created_at`).
2. **Otherwise** NIP-77 reconcile the window `since: cursor - 3600` against the ids held in
   that window and fetch only the missing ids; then fetch kind 5 `since: cursor + 1`.
3. Relays without NIP-77 or COUNT: fetch 30382 + 5 strictly newer than the cursor.
4. Merge: upsert cards (newest per d-tag); rank 0 or a kind-5 `a` tag `30382:P:<target>`
   removes the entry.
5. Write the files, swap the snapshot in, and advance the cursor to the newest `created_at`.

*First version (fixed):* a plain `since: cursor - 3600` fetch. Because a batch-publishing
provider puts every card in that hour, each update re-downloaded all 155k cards (~93 MB).

*Revised 2026-10-12: no kind 5s on NIP-77 relays.* A provider's deletions can outweigh its
cards many times over: scores.brainstorm.world held 341,569 kind 5s (380 MB, ~9.4k of them
with ~199 `a` tags each) against 151,831 cards (70 MB), and none of them named a card the
relay still served. The cold download also fetched them all, which on a phone over Tor never
finished. Now:

- The cold download fetches **no** kind 5: the relay serves only the cards it still holds.
- Step 1's deletion COUNT is replaced by `COUNT {30382, authors:[P]}` against every card held
  (`heldTotal` in the header, format version 5): a card the relay stopped serving lowers it.
- Step 2 reconciles **all** cards, not the window, and drops the ids only we hold. Measured on
  that relay: 151k ids reconciled in ~40 KB down, ~26 KB up, ~3 s.
- Kind 5s are fetched only in step 3 (relays without NIP-77), where they are the only signal.
- Under default privacy settings the provider's relay was a "new" relay, so the sync went over
  Tor; PR #4437 counts the relays in the user's 10040 as their own.

### D11. On disk
A single file per account: `filesDir/accounts/<pubkey>/wot/network-v1.bin`. It lives inside the
account directory, so the existing `deleteAccountFiles` cleans it up on logoff.

```
magic "AWOT" | u16 version | 32B provider | relay (u16 len + utf8)
| i64 syncCursor | i64 lastFullCheck | i32 N
| keys  N×16 | rank N×1 | hops N×1 | followers N×4
```

- Written with okio to a temp file and then moved into place (`atomicWrite`), so a crash
  leaves the old file or the new one, never half.
- Read with one `readByteArray` and decoded on `Dispatchers.IO` as soon as the `Account` is
  created, including accounts loaded only for push. 14–40 ms on JVM; phase 0 measures it on a
  phone.
- **Loading must be fast because push filtering waits on it** (D3). Consumers call a
  suspending `awaitLoaded(timeout)`; a cold process that a notification wakes pays the load
  once.
- If phase 0 shows the decode is too slow on low-end phones, the fallback is a jvmAndroid
  `actual` that memory-maps the file and binary-searches the mapped `LongBuffer` directly:
  no decode, the OS pages it in on demand. The format above already allows this (fixed-width
  sections, big-endian).
- A bad magic, version, or `(P, R)` mismatch means the file is ignored and a cold sync is scheduled.

A second file `network-ids-v1.bin` holds `(eventId 32B, createdAt i64)` per entry, about
12 MB, then the **tombstones**: `(subject 16B, eventId 32B, createdAt i64)` for each card the
relay still serves that does not count: a rank-0 or kind-5-deleted card, an older version the
relay kept beside the newest (Brainstorm's relay holds a few), or a `d` tag that is not a
lowercase pubkey (keyed by its event id, so it can never supersede a real card). It is **kept** (decided) and read **only** by syncs, never at
startup. It is written together with the index, so the two always describe the same set.
Tombstones make updates and full checks treat those cards as known instead of fetching them
again (format version 2; a version 1 file triggers a fresh download).

### D12. Weekly full check
Purpose: catch deletions or rank-0 cards we missed (a relay outage, an update window
overflow).

- **Cheap check first.** `COUNT {kinds:[30382], authors:[P]}` against `N + removedSinceLastFull`. If they match, stop.
- **Otherwise** run `negentropyReconcile` against `network-ids-v1.bin` through a
  `NegentropyLocalIndex` adapter: fetch the ids we need, drop the ids only we have.
- On Android this is a WorkManager periodic job (unmetered, battery not low) that resolves
  the account from `accountsCache` like `ScheduledPostWorker`.

Decided: keep the ids file and use negentropy; no periodic full re-download.

### D13. Multiple accounts
Several `Account`s can be live at once: background notifications load every writable
account.
- Every live account with a rank provider **loads** its index (~6 MB each), so push
  filtering works for all of them.
- Only the **active** account runs the foreground update.
- The weekly worker goes through every account with a rank provider.

### D14. Settings model
| Setting | Where | Why |
|---|---|---|
| Provider (P, R) | the 10040 itself (already on relays, backed up locally) | One source of truth, interoperable |
| `minTrustScore` (default 5) | `AccountSyncedSettings` (NIP-78) | A preference that should follow the user across devices |
| Brainstorm session token | memory only | Expires after 60 min (`AUTH_ACCESS_TOKEN_EXPIRE_MINUTES`); re-login just asks for one signature |
| Sync state | the index header | Local by nature |

**D14a. No separate switch — decided.** WoT filtering is on whenever the 10040 has a
`30382:rank` provider. Removing the provider (from the settings screen) turns it off.

Consequence to handle in the release: users who already have a 10040 from elsewhere will see
DM Known/New, Curated notifications and replies change after the update, once their first
sync finishes. The settings screen should show "Filtering by <provider>, minimum score N"
and the sync progress, so the change can be explained and undone.

### D15. Brainstorm onboarding (from `brainstorm_server` and `Brainstorm-UI` sources)
Base URL `https://api.brainstorm.world`; the NIP-85 relay is `wss://scores.brainstorm.world`.

1. `GET /authChallenge/{pubkey}` returns `{data:{challenge}}`.
2. Sign a **kind 22242** with tags `["t","brainstorm_login"]` and `["challenge", c]`, then
   `POST /authChallenge/{pubkey}/verify` with `{signed_event}`, which returns `{data:{token}}`.
   Login also creates the user's per-user service key on the server.
3. Authenticated calls send the header `access_token: <token>`.
4. `POST /user/graperank` triggers a score run. It needs at least one follow, is throttled to
   once per 30 min, and can return 429 when a tier's quota is used up.
5. `GET /user/history` returns `ta_pubkey` (the service key `P`) and
   `last_time_calculated_graperank`.
6. Sign a kind 10040 that **merges** into the existing list (keep other entries, public and
   private) the entries `["30382:rank", P, R]` and `["30382:followers", P, R]`, using
   `TrustProviderListEvent.add`. Publish to the outbox relays.
7. Once `last_time_calculated_graperank` is set (poll `/user/history`, or poll `COUNT` until it
   is above 0), start the cold sync (D9).

**Details of this step:**
- **D15a. Public or private 10040 entries — decided: both.** Reading already handles both
  (`liveTrustProviderList` decrypts the private side). Onboarding offers the choice, with
  **public** as the default: Brainstorm's web app recognises only public entries
  (`declaresTrustProvider`), and other clients can only use public ones. Private hides which
  provider you use, at the cost of that interop. `TrustProviderListEvent.add(..., isPrivate)`
  already supports both.
- **D15b. An existing 10040 points at another rank provider.** **Rec.** Warn and ask before replacing it, as Brainstorm's UI does. Never replace silently.
- **D15c. Signers.** Two signatures (22242, 10040) go through the normal `NostrSigner`, so NIP-55 and NIP-46 signers work. Each call is a prompt for users who approve manually.
- **D15d. Free vs paid tiers.** `brainstorm_server` has billing tiers and manual-run quotas (`enforce_manual_quota`). The UI should surface 403/429 as "recomputed recently / quota reached", not as a failure.
- **D15e. Any NIP-85 provider — decided.** The index, sync and filtering work with any
  provider listed under `30382:rank`. Two ways to set one up:
  - **Manual (any provider):** enter or paste the provider's service pubkey and relay; we
    write the 10040 entries. This needs no knowledge of the provider's API.
  - **Guided sign-up, one adapter per provider:** NIP-85 does not specify how to create an
    account with a provider or obtain its service key, so each provider's sign-up is its own
    integration behind a small interface, e.g. `TrustProviderOnboarding { login(); requestScores(); serviceKey(); relay }`.
    Brainstorm (above) is the first adapter; others are added one by one as their APIs are
    known.
- **D15f. The provider writes the 10040 rows — decided (2026-10-08).** A provider knows which
  key serves which tag, so the rows come from it, not from us. Brainstorm's
  `GET /setup/{pubkey}` (no token) returns exactly the rows its site publishes, e.g.
  `30382:rank|followers|reporters|muters|hops` under the per-user key plus a bare `30392` row
  for Trusted Lists; the guided sign-up publishes those, merged into the user's list (rows of
  the same names and the old score provider replaced, everything else kept). When `/setup`
  fails, it falls back to `rank` + `followers` under `ta_pubkey`, what brainstorm.world has
  always published. A survey of 138 kind 10040s on four big relays found 112 naming
  Brainstorm, nearly all tagged `client: Brainstorm`: users mostly set it up on the site.
  So each provider also names a website page (`setupUrl`, for Brainstorm `/setup/activate`)
  where the user can do it there; Amethyst then reads the list from the relays.
- **D15g. Fallbacks — decided (2026-10-08).** For when the provider's setup goes wrong, or a
  provider has no adapter: **set up by hand** (a row editor: a tag name from a hardcoded list
  `30382:rank|followers|hops|reporters|muters`, a key and a relay per row; a `rank` row is
  required; "keep private" lives here), or **copy someone's view**: copy another user's public
  rows (npub or NIP-05) and see the network as they do, since their provider computes the
  cards for them. A provider can name its own observer (`houseObserver`; Brainstorm's is
  `_@brainstorm.world`), offered as "Brainstorm's default view". Copied rows land in the
  editor for review; nothing is published until saved. "Stop using" removes every row the
  rank provider's key serves.

### D16. Rank and follower count move to the index
- **Rec.** `rankFlow` and `followerCountStrFlow` read the snapshot (`snapshotFlow.map { it.rank(user) }`) when the index is ready **and** `P` is the provider for that slot. Brainstorm registers the same key for `rank` and `followers`.
- Otherwise they fall back to today's per-profile path, so users without WoT see no change.
- When the index serves both slots, `UserCardsSubAssembler` stops asking `R` for provider cards for each profile on screen, and keeps fetching only the account's own cards (nicknames).
- Someone outside the network shows no rank. That matches today, since the provider has no card for them.

### D17. Refreshing feeds when the snapshot or threshold changes
DM Known/New and the notification feed are additive filters; they won't re-check old items on their own.
- **Rec.** Expose `snapshotVersion: StateFlow<Long>` combined with `minTrustScore`.
- Their view models force a full refresh when either changes, debounced about 500 ms.
- The notification filter already rebuilds when `feedKey()` changes (it includes the TopFilter code), so adding `minTrustScore` and a WoT-ready flag to that key may be enough there.

### D18. Consumers, one by one
| Surface | Change | Phase |
|---|---|---|
| DM Known | `senderIntersects(follows) \|\| hasSentMessagesTo \|\| (rankProvider != null && activeSenders.any { accepted })`. New stays its exact opposite, including the incremental path at `:167-173`. Group DMs: **any** accepted sender makes the room Known, matching how follows work today. | 4 |
| Notifications, Curated | Gate the author (`NotificationFeedFilter.kt:712`): `isAuthorInFollows \|\| accepted(author)` when `followList()` is `Selected` and a rank provider exists. Zaps are already handled: `notifAuthor` resolves a `ZapReceiptEvent` to the zap *request* sender, including private zaps (`:615-621`). | 4 |
| Push notifications | **Decided: apply Curated filtering to push too.** Today push applies only `account.isAcceptable`. When the account's notification filter is Curated, the push path (`EventNotificationConsumer` / the renderers) uses the same author check as the in-app feed, after `awaitLoaded` (D3). Ideally both call one shared predicate so they cannot drift. | 4 |
| Replies | **Decided: collapse replies from outside the network** behind "N more replies from outside your network", expandable on tap. Follows, me and the thread author are never collapsed. Sorting tiers stay as they are. | 5 |
| Public chats / live chat | Not decided. Phase 5, revisit after replies ship. | 5 |

### D19. Where code lives
| Piece | Module / package |
|---|---|
| `NetworkSnapshot` (arrays, lookup, merge, binary codec) | `quartz/.../nip85TrustedAssertions/users/index/`. Pure NIP-85 utility, no UI, KMP. |
| Sync (cold, update, full check) as `INostrClient` extensions | `quartz/.../nip85TrustedAssertions/users/index/` beside it |
| `WebOfTrustNetworkState` (load, save, schedule, `StateFlow`, accepted()) | `commons/.../wot/`, owned by `Account` |
| `TrustProviderOnboarding` interface + manual setup | `commons/.../wot/onboarding/` |
| Brainstorm adapter (HTTP client, sign-up flow) | `commons/.../wot/onboarding/brainstorm/` |
| Settings screen, strings | `commonsUI` (strings in `composeResources`, per CLAUDE.md) |
| WorkManager job, foreground trigger | `amethyst/` shim |
| `amy trust sync \| status \| check \| setup` | `cli/`, a thin layer over the above |
| Desktop | wired through `DesktopIAccount` once the one-UI move reaches it; the core is already shared |

## 5. Phases

0. **Measure first.** *(JVM part done, see §2; phone still pending.)* `amy wot sync` against a real ~300k network: wall time, bytes, signature-check time, peak heap. Repeat on a mid-range phone through a debug button, and measure the cold index load there (decides whether D11 needs the memory-mapped fallback, and settles D9b/D9c).
1. **Index + storage + sync** (quartz/commons) with unit tests:
   - file format round trip;
   - merge, rank-0 and kind-5 removal;
   - the shared-timestamp `UNPAGEABLE` → negentropy fallback;
   - `(P, R)` mismatch.
2. **Account wiring + D16**: rank and follower badges served from the index; drop the per-profile provider subscriptions.
3. **Web of Trust settings**: current provider and sync status, the min-score slider with a live "N of M people pass" count, manual provider setup, and the Brainstorm adapter (D14, D15).
4. **Consumers**: DM Known/New, Curated notifications in-app and in push (D18).
5. **Collapse out-of-network replies**; then revisit chats (D18).

## 6. Decisions log

Settled on 2026-10-07:

| # | Decision |
|---|---|
| D9a | Check every signature (cold sync, updates, full check). |
| D11 / D12 | Keep the ids file; the weekly full check uses negentropy against it. |
| D14a | No separate switch: filtering is on whenever a `30382:rank` provider exists. |
| D15a | Support public and private 10040 entries; onboarding offers both, public by default. |
| D7 | Don't store `reporters` / `muters`. |
| D18 push | Apply Curated filtering to push notifications; this is why the index must load fast (D3, D11). |
| D18 replies | Collapse replies from outside the network. |
| D15e | Accept any NIP-85 provider. Manual setup for any; guided sign-up via per-provider adapters, since provider account creation isn't specified. Brainstorm first. |
| D15f | The provider writes the 10040 rows (Brainstorm `GET /setup/{pubkey}`), here or on its site (`setupUrl`). |
| D15g | Hand-written rows and copying another user's rows (or a provider's house observer) are the fallbacks. |

Still open: D6a (memoize prefixes on `User`), D9b (resuming a cold sync), D9c (network
policy), D18 public chats. Phase 0 measurements should settle the first three.

## 7. Implementation notes (2026-10-07)

| Piece | Where |
|---|---|
| Index, builder, codec | `quartz/.../nip85TrustedAssertions/users/index/TrustNetwork{Index,Builder,Codec}.kt` (11 unit tests) |
| Sync (cold / update / full check) | `.../users/index/TrustNetworkSync.kt`; live run in `WotNetworkIndexBenchmark.productionSync`: 151k cards in 6.5 s cold, 1.2 s update, 2.9 s full check |
| Per-account state | `commons/.../wot/network/TrustNetworkState.kt`, owned by `Account.trustNetwork`; files in `accounts/<pubkey>/wot/` |
| Verdict | `Account.trustNetworkVerdict` / `isOutsideTrustNetwork` / `isKnownChatroom` |
| Minimum score | `AccountSyncedSettings.security.minTrustScore` (synced, default 5) |
| Consumers | DM Known/New filters + DM push (`isKnownChatroom`); Curated notifications in-app (`NotificationFeedFilter`) and push (`EventNotificationConsumer`); collapsed out-of-network replies (`ThreadFeedView`); rank/follower badges from the index (`UserCardsCache`), which also drops the per-profile provider subscription |
| Onboarding | `commons/.../wot/onboarding/` (`TrustProviderOnboarding`, `BrainstormOnboarding`, OkHttp transport) |
| Settings screen | `commonsUI/.../settings/wot/WebOfTrustScreen.kt` (`Route.WebOfTrust`, in Settings › Account), render-tested in both themes |
| Android | dedicated sync client + metered check (`AppModules`/`AccountCacheState`), foreground trigger, daily `TrustNetworkSyncWorker` |

Behaviours added during implementation:
- A cold download that returns **zero cards** does not activate filtering (a provider still
  computing a new user's scores has published nothing yet); the screen says so and retries.
- The index on disk is used **before** the 10040 resolves at startup (5 s grace), so a push that
  wakes the process is filtered.
- The provider's rank-0 cards are kept as tombstones in the ids file (D11), so neither the
  update nor the full check fetches them again (678 per check for the test account before).
- Update cost, live (Brainstorm, 154,755 cards): "nothing new" in 0.8–0.9 s for a whole
  `amy trust sync` including JVM start and connect; the two COUNTs take ~30 ms after a
  130–430 ms connect. The full check after a re-download needed 11 cards. Covered by
  `TrustNetworkSyncTest` against a local geode relay (batch arriving mid-sync, re-rank,
  rank-0 removal, kind-5 deletion).

Added later: `amy trust sync | status | check | setup` (named `trust` because `amy wot` is the
deprecated alias of `fof`), a thin layer over `TrustNetworkState.syncNow` / `explain` and the
extracted `withScoreProvider`. Live: 151k cards in 10.9 s, update 1.2 s, full check 2.6 s.

Added 2026-10-08:
- **Cards seen between syncs** (`TrustNetworkState.offer`): profiles on screen ask the provider
  for their cards again, and a card newer than the sync cursor wins over the index until the next
  sync downloads it. Lookups read it first; feeds re-run only when it moves someone in or out
  (`verdictRevision`).
- **Public chats** (NIP-28, live-stream chat, ephemeral chats, NIP-29 groups): consecutive
  messages from outside the network collapse into one quiet row, "3 messages from outside your
  network · Show", with no names, pictures, text or times (`OutsideNetworkChat.kt`, opt-in via
  `LocalChatCollapseOutsideNetwork`). Not applied to private chats (Known / New Requests),
  Concord (members-only) or geohash chats (throwaway keys would collapse nearly everything).

Structure after the 2026-10-08 cleanup (`commons/.../wot/`):

| Piece | Job |
|---|---|
| `TrustVerdicts` | One frozen snapshot of everything a decision depends on (network, cards seen since the sync, minimum score, user, follows) and the single rule, `explain`. `Account.trustVerdicts` publishes a new one only when an answer can change; feeds and screens key on it. `Account.currentTrustVerdicts()` is the live one for one-off checks (push). |
| `TrustNetworkStore` | The two files (`network-v2.bin`, `network-ids-v2.bin`); deletes the v1 names. |
| `TrustNetworkSource` | The relay (`RelayTrustNetworkSource`), or a fake in `TrustNetworkSchedulingTest`. |
| `TrustNetworkState` | Loads, matches the provider, schedules syncs (on one confined dispatcher), keeps the between-syncs cards. Outcomes are typed (`TrustNetworkOutcome`, `TrustNetworkProblem`). |
| `curatedHidesByTrust` | The one list of Curated exemptions, used in-app and by push. |
| `TrustProviderListState.resolvedRankProvider` | The provider once the 10040 is read and decrypted; replaces the 5 s grace timer. |
| `WebOfTrustViewModel` | Guided sign-up and manual provider entry, out of the composable. |

Not done: Nests lobby and minichat (own message lists), Desktop wiring (its `DesktopIAccount`
does not use the commons `Account` yet), and the phone measurements for phase 0.

