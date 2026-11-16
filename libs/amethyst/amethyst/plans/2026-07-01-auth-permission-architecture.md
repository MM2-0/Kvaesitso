# Contextual AUTH Permissions — Ask *why*, and trust follows

**Date:** 2026-07-01
**Module:** `amethyst` (+ shared bits in `commons`)
**Status:** Implemented — see "As-built" below for where the shipped design diverged from this proposal.

## As-built (final)

The implementation kept this doc's core ideas (purpose derivation, a prompt bus,
per-relay overrides, grant rationale) but the policy model was reshaped during
review:

- **Global mode is `RelayAuthPolicy { ALWAYS, NEVER, CUSTOM }`** — the earlier
  `IF_IN_MY_LIST` / `TRUSTED_FOLLOWS` values were dropped. `CUSTOM` applies a
  `RelayAuthCustomToggles` set of independent switches: **my relays & venues**,
  **read posts from follows**, **message follows**, **message strangers**
  (off by default). New-install default is `CUSTOM` with the first three on.
- **`AuthPurpose` is a `data class` (kind + counterparties + venues)** over an
  `AuthPurposeKind` enum (SEND_DM, NOTIFY_INBOX, READ_OUTBOX, POST_VENUE,
  READ_VENUE, MY_OWN_RELAY, OTHER) — not a sealed interface. Venues (NIP-28
  public chats, NIP-72 communities, NIP-53 live activities) are first-class.
- **Settings screen** uses the app's settings design system (`SettingsSection`
  card + `SettingsSwitchTile`) for the toggles and a grouped, lazily-rendered
  per-relay list (NIP-11 icon, `displayUrl`, tap → relay info).
- **Give-up signal**: quartz's outbox surfaces `onEventGaveUp`, toasted by
  `RelayPublishFailureToast`. `auth-required` NAKs never burn the retry budget
  (they reset it) so a slow AUTH handshake can't drop the event.
- **Known limitation**: an event queued to a relay the user then *denies* stays
  pending in the outbox (auth-required never gives up); evicting it would need a
  quartz "give up on relay for this event" API — deferred.

## Context

Now that Amethyst answers NIP-42 relay AUTH challenges, we need to decide
*when* to reveal the user's identity to a relay — and, crucially, to tell the
user **why** an auth is being requested so they can make an informed choice.

The motivating cases:

- **NIP-17 DM send.** The recipient's DM inbox relays (kind 10050) may require
  auth. If we silently refuse, the message never leaves the device and the user
  has no idea why. We should ask: *"Relay X wants you to log in to deliver your
  private message to Alice — allow?"*
- **Public inbox notifications.** Replying to / mentioning / reacting to someone
  publishes to *their* NIP-65 inbox (kind 10002 read relays), which may require
  auth.
- **Feed download from outboxes.** Reading a followed author's posts may require
  auth to *their* write/outbox relays.

We also want an **automatic mode** for users who trust Amethyst's judgement:
auth (or not) based on a follow-graph heuristic — *if I follow the counterparty
(in any follow list), I trust them enough to reveal my identity to the relay
that serves them.* And regardless of mode, **explicit per-relay overrides** must
be able to force-allow or force-block a single relay. The blocked-relay list
(kind 10006) is a hard block.

## What already exists (reuse — do NOT rebuild)

The NIP-42 plumbing and a first-cut permission gate are already in place:

| Piece | Location |
|---|---|
| AUTH challenge receipt, kind-22242 signing, resend-on-OK | `quartz/.../nip01Core/relay/client/auth/RelayAuthenticator.kt`, `RelayAuthStatus.kt`, `nip42RelayAuth/RelayAuthEvent.kt` |
| Permission gate (per logged-in account) | `amethyst/.../service/relayClient/authCommand/model/AuthCoordinator.kt` |
| Decision engine (per-relay override → global policy) | `.../authCommand/model/RelayAuthPermissionLedger.kt` |
| Policy enum `ALWAYS`/`NEVER`/`IF_IN_MY_LIST`, decision enum `ALLOW`/`DENY` | `commons/.../relayauth/RelayAuthPolicy.kt` |
| Per-relay override persistence interface + DataStore impl | `commons/.../relayauth/RelayAuthPermissionStore.kt`, `amethyst/.../authCommand/model/DataStoreRelayAuthPermissionStore.kt` |
| Settings screen (global policy + per-relay list) | `amethyst/.../ui/screen/loggedIn/relayauth/RelayAuthSettingsScreen.kt` |
| Global policy setting, persisted local-only | `AccountSettings.defaultRelayAuthPolicy`, `LocalPreferences` key `DEFAULT_RELAY_AUTH_POLICY` |
| Blocked-relay list (kind 10006) | `amethyst/.../model/nip51Lists/blockedRelays/BlockedRelayListState.kt` (`.flow`) |
| Follow checks | `Account.isFollowing(...)`, `Account.allFollows.flow.value.authors`, `StarterPacksState.isUserInFollowSets(...)` |
| DM / NIP-65 relay lookups | `DmRelayListState`, `Nip65RelayListState` (+ per-user via `LocalCache`) |

## The gap

`RelayAuthPermissionLedger.decide(relayUrl)` receives **only a relay URL**. It
resolves ALLOW/DENY **silently and immediately**. Three things are missing:

1. **No purpose/"why".** The decision point can't tell a DM-send from a
   feed-read from a stranger's random challenge, so it can't explain itself or
   attribute the relay to a counterparty.
2. **No interactive ASK.** `RelayAuthDecision` is binary. A DENY silently drops
   the auth (and the send fails with no feedback).
3. **No follow-based trust.** `IF_IN_MY_LIST` only checks *my own* relays, never
   "this relay belongs to someone I follow."

## Recommended architecture

Four changes, smallest surface first.

### 1. Carry the *purpose* to the decision point — `AuthPurpose` + an intent registry

New (in `commons/.../relayauth/`, KMP-safe, no Android deps):

```kotlin
sealed interface AuthPurpose {
    data class SendDM(val recipients: Set<HexKey>) : AuthPurpose          // recipient DM inboxes (10050)
    data class NotifyInbox(val recipients: Set<HexKey>) : AuthPurpose     // recipient NIP-65 read relays
    data class ReadOutbox(val author: HexKey?) : AuthPurpose              // author write/outbox relays
    data object MyOwnRelay : AuthPurpose                                  // relay in my own lists
    data object Unknown : AuthPurpose                                     // bare challenge, no attribution
}
```

The auth path is **reactive** (relay pushes the challenge; the lambda only knows
the URL). Most intent is already recoverable from quartz's per-relay pending
events + active filters (see "Where it lives" below), so the registry below is
**minimal** — only for hints quartz can't infer (e.g. the human recipient behind
an encrypted gift wrap). It lives with the coordinator, since `LocalCache`/
`Account` are main-process only:

```kotlin
// amethyst/.../service/relayClient/authCommand/model/RelayAuthIntentRegistry.kt
class RelayAuthIntentRegistry {
    fun register(relay: NormalizedRelayUrl, purpose: AuthPurpose)   // short TTL entry
    fun purposesFor(relay: NormalizedRelayUrl): List<AuthPurpose>   // read at decision time
}
```

Representative registration sites (each already computes its target relays):
- NIP-17 DM send → `SendDM(recipients)` on each recipient DM-inbox relay.
- Reply/mention/reaction broadcast → `NotifyInbox(recipients)`.
- Outbox feed subscriptions → `ReadOutbox(author)`.

*Race note:* keying by relay URL means concurrent purposes can collide; store a
small time-bounded **set** per relay and let the resolver consider all live
entries (the prompt can say "to send your DM to Alice and 2 others"). Acceptable
for a UX hint + trust check; the persisted decision is what actually gates.

### 1b. Persist *why* each relay was granted (grant rationale)

The decision stays **relay-based**, but each relay's stored record must also
remember **why** it was granted, so the settings screen can show, per relay,
purpose-grouped lines of counterparty users (with avatars):

> **wss://inbox.example.com** — Allowed
> · To send DMs to: (avatars) Alice, Bob, Carol
> · To download posts from: (avatars) Dave, Erin

Extend the persisted per-relay record from a bare `RelayAuthDecision` to
`decision + rationale`, where the rationale is an accumulated map keyed by
purpose kind:

```kotlin
// commons/.../relayauth/RelayAuthGrant.kt (new)
data class RelayAuthGrant(
    val decision: RelayAuthDecision,
    // purpose kind -> counterparty pubkeys seen for this relay under that purpose
    val rationale: Map<AuthPurposeKind, Set<HexKey>> = emptyMap(),
    val lastUsedAt: Long = 0L,
)
enum class AuthPurposeKind { SEND_DM, NOTIFY_INBOX, READ_OUTBOX, MY_OWN_RELAY }
```

The rationale is **updated every time** an auth is granted/re-used for that
relay: merge the current `AuthPurpose` counterparties into the matching kind's
set and refresh `lastUsedAt`. This keeps the "why" current as new
DMs/notifications/feeds route through the relay. Store only pubkeys — names and
avatars are resolved for display from `LocalCache` at render time, so the store
stays privacy-light and small.

### 2. Add an `ASK` outcome and a context-aware resolver

Extend the decision enum and generalize `decide()`:

```kotlin
enum class RelayAuthDecision { ALLOW, DENY, ASK }   // ASK added

class RelayAuthContext(val relayUrl: String, val purposes: List<AuthPurpose>)
```

`RelayAuthPermissionLedger.decide(ctx)` precedence (highest → lowest):

1. **Blocked-relay list** (kind 10006) → `DENY`. Never reveal identity to a
   blocked relay, whatever the policy.
2. **Explicit per-relay override** (`RelayAuthPermissionStore`) → return it.
3. **Global policy**:
   - `NEVER` → `DENY`
   - `ALWAYS` → `ALLOW`
   - `IF_IN_MY_LIST` → `ALLOW` if relay ∈ my relay lists, else fall through
   - `TRUSTED_FOLLOWS` *(new — see idea A below)* → `ALLOW` if relay ∈ my lists
     **or** any counterparty in `ctx.purposes` is followed (`Account.allFollows`
     / `StarterPacksState.isUserInFollowSets`) and the purpose permits it; else
     fall through.
4. **Fall-through**: `ASK` if the purpose is attributable (we can show a reason);
   otherwise `DENY` silently (don't prompt for anonymous stranger challenges).

Keep the current relay-only `decide(url)` as a thin overload calling
`decide(RelayAuthContext(url, registry.purposesFor(url)))` so existing callers
compile.

Whenever the resolver yields `ALLOW` and an auth is actually sent — regardless
of *how* it was allowed (auto policy, stored override, or a just-approved ASK) —
call `store.recordUse(relayUrl, purpose)` for each attributed purpose so the
grant rationale (§1b) stays current.

### 3. Surface the ASK prompt to the UI and await the answer

The `signWithAllLoggedInUsers` lambda in `AuthCoordinator` is **already a
`suspend` context**, so the resolver can suspend and await a user decision — no
restructuring of the auth send path.

- Add an event stream on the coordinator (or account):
  `SharedFlow<RelayAuthRequest>` where
  `RelayAuthRequest(relay, purposes, reply: CompletableDeferred<UserAuthChoice>)`.
  (Follows the repo's one-shot-event flow pattern — see `kotlin-flow-state-event-modeling`.)
- A composable observer (registered in the logged-in scaffold) collects the flow
  and shows a dialog: *"{relay} requires you to log in to {reason}."* with
  actions **Allow once / Always allow this relay / Block this relay**. The last
  two write through `RelayAuthPermissionLedger.setDecision(...)`.
- The lambda `await`s the deferred (bounded by a timeout consistent with
  `RelayAuthStatus`), then proceeds to sign or returns `emptyList()`.

Reason strings are derived from `AuthPurpose` via a small mapper (resolve
recipient pubkeys → display names through `LocalCache`).

### 4. New policy mode + settings

- Add `TRUSTED_FOLLOWS` to `RelayAuthPolicy` (recommended — idea A).
- `RelayAuthSettingsScreen`: add the new mode with an explanatory blurb; the
  per-relay override list already supports force-allow/force-block (now
  three-state incl. "ask"). No storage-format change if we keep decisions
  per-relay (idea B, recommended default).

## Where it lives: quartz (generic mechanism) vs amethyst (policy + UI)

Goal (per the brief): if the auth+resend mechanism can be made **robust and
generic**, it belongs in **quartz**; only the *semantics* (why / follow-trust /
prompt copy / rationale UI) stay in **amethyst**.

### The resend queue already exists in quartz — and is the "intent registry"

`PoolEventOutbox` / `PoolEventOutboxState` already persist outgoing events
per-relay across reconnects, and `NostrClient.syncFilters(relay)` — called on
connect **and after an auth OK** (`RelayAuthenticator.checkAuthResults`) —
already re-sends pending EVENTs, not just REQ subscriptions. So the park-and-
flush half of idea C is largely built; we just need to make it correct.

It also means we mostly **don't need a separate `RelayAuthIntentRegistry`**:
quartz already knows, per relay, the *pending outgoing events*
(`PoolEventOutbox`) and the *active subscription filters* (`activeRequests`).
That set IS the intent. At AUTH time quartz can hand the injected decision
callback this context; amethyst derives purpose from it (a pending kind-1059
gift wrap → `SendDM`; a REQ whose `authors` are followed → `ReadOutbox`). Keep a
tiny registry only for hints quartz can't infer (e.g. the human recipient behind
a gift wrap, which is encrypted) — but drive the common cases off quartz state.

### Generic fixes to land in quartz (`nip01Core/relay/client/`)

1. **Treat `auth-required` as a first-class deferred state, not a burned retry.**
   *(Landed — commit 2.)* The resend-after-auth path already works:
   `syncFilters` on the auth `OK` re-sends every still-pending EVENT, so the
   common single-round case (send → `auth-required` → auth → resend → accepted)
   already delivered. The narrow bug: `PoolEventOutboxState.newResponse` sent
   `auth-required` down the generic-failure path, so each NAK consumed the
   per-relay retry budget (`isDone() = responses.size > 2 || tries.size > 3`).
   Budget exhaustion isn't checked on the NAK itself but on the **next
   `newTry`** — i.e. the resend `syncFilters` issues after the auth `OK`. So
   across *repeated* rounds (slow external NIP-55 signer, reconnect churn, or a
   relay that re-challenges) the saved event could be **evicted right as it was
   about to be redelivered**. Fix: `auth-required` records no failure and leaves
   `relaysRemaining` untouched (mirrors `StandaloneRelayClient`'s
   `!msg.message.startsWith("auth-required")`), so the existing resend can
   redeliver no matter how many auth rounds elapse first.
2. **Real retry policy instead of a hard count.** Replace the `>2 / >3` cliff
   with bounded retries + backoff, and a **terminal "gave up" notification**
   (via `RelayConnectionListener` / a publish-result callback) so events are
   never *silently* dropped. `NostrClientPublishExt.publishAndConfirmDetailed`
   and `pendingPublishRelaysFor` already give higher layers a confirmation
   surface to build on.
3. **Enrich the injected auth-decision callback with pending context.** The
   `signWithAllLoggedInUsers = (relayUrl, authTemplate) -> …` hook in
   `RelayAuthenticator` currently gets only the URL. Pass a generic
   `RelayAuthChallengeContext` carrying the relay's pending events + active
   filters, and let it return not just "sign or not" but an outcome that can
   **suspend for a host decision**. The `AuthPurpose`/`RelayAuthContext` types
   move to a quartz-neutral shape (opaque to quartz); amethyst supplies the
   resolver.
4. **Expose an "event is blocked on auth for relay X" signal** so a host UI can
   show the prompt and reflect "queued, not lost." A `SharedFlow`/listener on the
   client, host-agnostic.

### What stays in amethyst

The *policy and meaning*: blocked-list + follow-graph resolver, purpose/
counterparty derivation (needs `LocalCache`/`Account`, main-process only), the
`TRUSTED_FOLLOWS` mode, the ASK prompt UI, and the per-relay **grant rationale**
persistence + settings rows (§1b). These depend on identity/UI and cannot live
in quartz.

## A few ideas / open decisions

These are the knobs where more than one answer is defensible. Recommendation
first.

- **A. Follow-based trust shape.** *(Recommended: new `TRUSTED_FOLLOWS` policy
  mode.)* Cleanest extension of the existing enum + settings radio group.
  Alternatives: a separate independent "trust relays of people I follow" toggle
  that layers on any base mode (more flexible, more UI); or never-automatic —
  follow-status only pre-selects the "remember" button in the ASK dialog (most
  conservative).

- **B. Decision memory granularity.** *(Decided: per-relay — the decision gate
  is one ALLOW/DENY per relay.)* We keep the gate relay-based but enrich the
  stored record with the grant rationale (§1b) so the settings screen can
  explain each relay. Rejected alternative: making the *gate itself*
  per-purpose × per-relay (allow relay X for DMs but keep asking for feed reads)
  — richer but more confusing; the rationale display gives the transparency
  without splitting the gate.

- **C. In-flight send when auth isn't yet granted.** *(Recommended: fix
  quartz's existing outbox so park-and-flush is the default.)* The queue already
  exists (`PoolEventOutbox` + `syncFilters`-after-auth); the work is making
  `auth-required` a deferred state (not a burned retry) and adding backoff + a
  terminal give-up signal — see the quartz section above. This is strictly
  better than the amethyst-only best-effort/retry fallback and is generic, so it
  belongs in quartz. Best-effort remains the trivial fallback only if we choose
  not to touch quartz.

- **D. Which purposes auto-trust covers.** DMs and public inbox notifications are
  clear yes. Outbox/feed reads ("maybe" in the brief) could be a sub-toggle
  under `TRUSTED_FOLLOWS` so reading is treated more liberally than writing.

## Files to touch

- `commons/.../relayauth/RelayAuthPolicy.kt` — add `TRUSTED_FOLLOWS`, add `ASK`.
- `commons/.../relayauth/AuthPurpose.kt` — **new** sealed hierarchy + `RelayAuthContext` + `AuthPurposeKind`.
- `commons/.../relayauth/RelayAuthGrant.kt` — **new** per-relay record (decision + rationale, §1b).
- `commons/.../relayauth/RelayAuthPermissionStore.kt` + `amethyst/.../DataStoreRelayAuthPermissionStore.kt`
  — store/load `RelayAuthGrant` (decision + rationale) instead of a bare decision; add a
  `recordUse(relayUrl, purpose)` merge that updates the rationale + `lastUsedAt`.
- `amethyst/.../authCommand/model/RelayAuthPermissionLedger.kt` — context-aware
  `decide(ctx)`, blocked-list + follow-trust inputs, `ASK` fall-through.
- `amethyst/.../authCommand/model/RelayAuthIntentRegistry.kt` — **new, minimal**:
  only for hints quartz can't infer (e.g. the recipient behind an encrypted gift
  wrap). Common purposes are derived from quartz's pending events + active
  filters instead.

**Quartz (generic mechanism — see the quartz section):**
- `quartz/.../nip01Core/relay/client/pool/PoolEventOutboxState.kt` +
  `PoolEventOutbox.kt` — `auth-required` as a pending-auth state excluded from
  `isDone()`; bounded retry + backoff; terminal give-up notification.
- `quartz/.../nip01Core/relay/client/auth/RelayAuthenticator.kt` — pass a
  `RelayAuthChallengeContext` (pending events + active filters) to the injected
  decision hook; allow the hook to suspend for a host decision.
- `quartz/.../nip01Core/relay/client/listeners/RelayConnectionListener.kt` (or a
  new client `SharedFlow`) — "event blocked on auth for relay X" + "gave up"
  signals. Fold the good `StandaloneRelayClient` auth-retry logic into the
  production path.
- `amethyst/.../authCommand/model/AuthCoordinator.kt` — build `RelayAuthContext`
  from the registry, emit `RelayAuthRequest` on `ASK`, await the reply.
- Wire the ledger's new inputs where it's constructed (blocked-list flow,
  follow-check, relay-ownership lookups from `DmRelayListState`/`Nip65RelayListState`).
- Registration calls at the DM sender, reply/reaction broadcaster, and outbox
  feed subscription.
- `amethyst/.../ui/screen/loggedIn/relayauth/RelayAuthSettingsScreen.kt` — new
  mode; three-state per-relay overrides; **per-relay rationale rows** grouped by
  purpose ("To send DMs to: …", "To download posts from: …") rendering
  counterparty avatars + names resolved from `LocalCache`.
- New composable dialog + observer for `RelayAuthRequest`, hosted in the
  logged-in scaffold.

## Verification

- **Unit (commons/amethyst JVM):** table-test `decide(ctx)` across the
  precedence ladder — blocked beats override beats policy; `TRUSTED_FOLLOWS`
  allows a followed-counterparty relay and falls to `ASK` for a stranger;
  `Unknown` purpose → silent `DENY`.
- **Quartz outbox (JVM unit tests):** an `auth-required` NAK does **not** advance
  `isDone()` and the event survives; after a simulated auth OK, `syncFilters`
  re-sends it; a non-auth terminal error still discards; retries honor backoff
  and emit a give-up signal instead of a silent drop. Include a race test:
  repeated `auth-required` NAKs before auth completes must not drop the event.
- **Intent registry:** register/expire, multi-purpose merge on one relay.
- **Grant rationale:** `recordUse` merges new counterparties into the right
  purpose kind, dedupes, refreshes `lastUsedAt`; `allDecisions()`/settings query
  returns the grouped rationale for rendering.
- **`amy` interop:** drive a NIP-17 send to a recipient whose 10050 relay
  requires auth against a local auth-required relay (`amy serve` / geode) and
  confirm the AUTH round-trip + delivery once allowed. (Enforces the
  verify-don't-guess rule.)
- **Manual:** send a DM to a followed vs non-followed npub on an auth-required
  inbox under each policy mode; confirm the prompt copy names the right reason
  and that Always/Block persist.
- `./gradlew :commons:test :amethyst:testDebugUnitTest` and `./gradlew spotlessApply`.
