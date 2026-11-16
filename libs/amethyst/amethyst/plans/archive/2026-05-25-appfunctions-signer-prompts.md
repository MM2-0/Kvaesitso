# AppFunctions signer prompts — design

> **Status:** shipped — `AmethystAppFunctions.kt` ships 46 `@AppFunction` verbs including write verbs that acquire signatures across signer types.
> _Audited 2026-06-30._

**Date:** 2026-05-25
**Status:** Draft — no code yet

How write verbs invoked from background Gemini context (via
`androidx.appfunctions` 1.0.0-alpha09 → `PlatformAppFunctionService`)
acquire a signature from each of Amethyst's three signer types. This
is the gating concern that has us only exposing read-only verbs so far
(`searchProfiles`, `searchNotes`, `getFollowing`).

## The three signer types and what each needs

| Signer | Where the private key lives | Sign call latency | Needs user interaction? |
|---|---|---|---|
| **`NostrSignerInternal`** | In-process keypair, loaded at login | Synchronous, microseconds | No |
| **`NostrSignerRemote`** (NIP-46 bunker) | Remote process — a wallet app, browser tab, separate device | Network round-trip via relays, seconds | Yes — the bunker app pops a confirmation on the user's other device |
| **`NostrSignerExternal`** (NIP-55, e.g. Amber) | Another Android app on the same device | Bound-service IPC + activity bounce | Yes — Amber shows an activity in the foreground asking the user to approve |

Each signer surfaces the same `suspend fun sign(...)` API. The difference is
**what happens to the foreground UI** while the sign is in flight.

## What App Functions gives us to work with

From the alpha09 artifact (`androidx.appfunctions:appfunctions-service`):

- **Suspending dispatch.** `executeFunction` is a suspend function — a slow
  signer (NIP-46 round-trip) doesn't block the system shell.
- **Typed exceptions.** `AppFunctionPermissionRequiredException`,
  `AppFunctionDeniedException`, `AppFunctionCancelledException`,
  `AppFunctionAppException`. The non-default constructors take a `Bundle` —
  the system shell can interpret known keys (e.g. a `PendingIntent` to launch
  an in-app confirmation). Concrete bundle contract is undocumented in
  alpha09; needs a sample-app check or an experiment.
- **`PendingIntent`** is listed as a supported parameter type, which strongly
  implies a returned `PendingIntent` can prompt the system to launch the
  app's UI for follow-up.
- **No streaming.** Functions return one value or throw. There's no native
  "in progress" / "user is approving" signal back to Gemini.

## Per-signer approach

### NostrSignerInternal — just works

Verb runs end-to-end inside the dispatch coroutine. `signer.sign(...)` is
synchronous. Publish via `client.publish(...)`. Return success.

**Verbs this covers immediately:** post, follow/unfollow, search-relay
list updates, kind:10002 changes — anything where the signed event is
sent and forgotten.

**Edge case — background `app.client`.** When the app process is
foreground-bound but the user is in Gemini, the client should be
connected. When the user has killed Amethyst recently, the service
process might be cold-started and the client not yet connected to any
relay. The verb needs to either:
- Wait for `client.connect()` (~hundreds of ms once the WebSocket is
  established) — acceptable inside the 5-10s window.
- Use `INostrClient.publish(...)` which queues the publish for when the
  connection comes up. Quartz needs to confirm this is the actual
  behavior; might require `withTimeout` around the publish.

### NostrSignerRemote — the cleanest async case

The bunker sends a NIP-46 request to a relay, the bunker app sees it on
the user's other device, the user approves, the signed event comes back.
`signer.sign(...)` suspends until the response arrives or its internal
timeout fires (default 30s).

**Approach:** call `signer.sign(...)` from within the verb, with a
`withTimeout` budget aligned to App Functions UX expectations (Gemini
typically waits ~30s before showing the user "no response"). On
timeout, throw `AppFunctionCancelledException`. On success, publish and
return.

**Open question — concurrent foreground signing.** If the user is also
trying to send a post from the foreground UI at the same moment, the
bunker app gets two simultaneous requests. NIP-46 handles this — each
request has a unique id — but Amber-like bunker apps may queue both
prompts confusingly. Worth a manual test.

### NostrSignerExternal — the hard case

NIP-55 bounces to a separate Android app's activity. From a background
`PlatformAppFunctionService`, we can't directly `startActivity(...)` —
there's no foreground intent stack to attach to.

**Two viable approaches:**

#### Option A — throw a typed exception with a PendingIntent

```kotlin
@AppFunction
suspend fun postNote(ctx: AppFunctionContext, text: String): PostResult {
    val account = activeAccount() ?: throw AppFunctionDeniedException("not signed in")
    if (account.signer is NostrSignerExternal) {
        // Build a PendingIntent that opens Amethyst at a "approve this
        // post" screen, with the draft text passed through extras.
        val approvalIntent = buildApprovePostPendingIntent(account, text)
        throw AppFunctionPermissionRequiredException(
            message = "Amethyst needs to launch the external signer to approve this post.",
            extras = bundleOf("pending_intent" to approvalIntent),
        )
    }
    // … happy path for the in-process signer
}
```

The system shell renders "Open Amethyst to continue", user taps,
Amethyst opens, user approves through Amber's activity, post lands. The
Gemini conversation doesn't see the final result — the user has to come
back to Gemini and re-confirm.

**UX gap.** No way to communicate the eventual outcome back to Gemini's
chat. Acceptable for v1.

#### Option B — refuse write verbs when the signer is NIP-55

Throw `AppFunctionNotSupportedException` immediately. User configures a
different signer (local or NIP-46) to enable Gemini-driven writes.
Simpler, cleaner, but limits the audience — many Amethyst users on
Amber would lose the feature.

**Recommendation:** start with Option B, ship Internal + Remote support,
then add Option A behind a feature flag in a follow-up. Option B
unblocks the feature for ~70% of users today; Option A is more work
and has the unresolved "result doesn't get back to Gemini" wrinkle.

## Per-write-verb concerns

### postNote(text)
- Internal: sign → publish to outbox. Done.
- Remote: sign (suspends) → publish. Done.
- External: throw NotSupported, or PendingIntent dance.
- Side concern: should this go into the user's drafts vs immediately
  publish? Gemini-issued posts feel like they should publish (the
  user asked for it), but a "review before post" screen via PendingIntent
  is a nice safety net even for the local-signer path.

### follow(npub) / unfollow(npub)
- Same signer paths as postNote, simpler payload.
- Reads the current kind:3, modifies, signs, publishes — `FollowActions`
  is ready.
- **No** "preview" step needed — follow/unfollow is reversible.

### sendDm(recipient, text)
- Same signer paths.
- `DmActions.buildTextDm` is ready, plus `resolveDmRelays`.
- **Concern**: strict mode (default) refuses to send when recipient has
  no kind:10050. Should Gemini's `sendDm` default to strict or
  permissive? Argument for strict: it's NIP-17 spec behavior. Argument
  for permissive: Gemini users won't know what kind:10050 is and will
  see confusing failures. **Lean: permissive by default**, surface the
  source in the result.

### zapUser(npub, sats, comment?)
- Same signer paths for the kind:9734 zap request.
- But there's a *second* signing-like step: an LN payment via NWC
  (if configured). NWC has its own permission model and can also fail.
- For v1: build the zap request, fetch the BOLT11 invoice, return the
  invoice in the result. User pays via their wallet. Skip NWC
  auto-payment.

### zapEvent(eventId, sats, comment?)
- Same as zapUser but uses `ZapActions.buildEventZapRequestsForSplits`
  so multi-party notes route correctly.
- May return multiple invoices (one per split recipient).

## Account selection

All verbs read `Amethyst.instance.sessionManager.loggedInAccount()` once
at entry. **Multi-account question**: should Gemini be able to specify
*which* account to act as? Two answers:

- v1: no — always act as the currently-active account. Matches what the
  user sees in the foreground UI. Simpler.
- Later: add an optional `accountNpub: String?` parameter to each write
  verb. Defaults to the active account.

Start with v1.

## Permissions surfaced to Gemini

The App Functions schema XML (auto-generated by KSP) lists each verb
plus its parameters. Gemini's tool picker shows these to the user. We
should add a `description` (via `isDescribedByKDoc = true`, which we
already do) that makes write verbs sound consequential — "Publishes a
note to your Nostr followers", not "Calls postNote".

## Open questions for an experiment day

1. What concrete `Bundle` keys does the system shell respect on
   `AppFunctionPermissionRequiredException`? Run a tiny test app, throw
   the exception with various bundle contents, observe what Gemini
   surfaces.
2. Can the user approve a Gemini-issued write from within the Gemini
   chat (inline confirmation) or only by opening Amethyst? Affects
   Option A's UX.
3. Does `INostrClient.publish(...)` actually queue when the relay
   pool is disconnected, or does it return immediately with no
   delivery? Determines whether the verb needs an explicit
   "wait for at least one OK" gate.
4. NWC and Gemini: if the user has a NWC wallet configured, should
   `zapUser` auto-pay? Adds another consent layer.

## Minimum viable first write verb

Pick **`postNote(text)`** as the pilot.

Why:
- Simplest: one signed event, one publish, one ack.
- Read-back is straightforward: return the event id + the relays it
  landed on. Gemini can compose "Posted! Here's the link: nostr:nevent…".
- Failure modes are well-bounded (signer error, no outbox relays, all
  relays rejected).
- No multi-party complexity (zap splits, DM strict mode).

Scope of the pilot:
- `NostrSignerInternal` only (Option B for NIP-55, Remote in a
  follow-up). Document the cutoff in kdoc.
- Returns `PostNoteResult(eventId, publishedTo, rejectedBy)` — an
  `@AppFunctionSerializable`.
- Builds on the existing `commons/.../quartz/.../TextNoteEvent.build`
  and `client.publish` — no new actions needed.
- ~50 lines of new code in `AmethystAppFunctions.kt`, plus the result
  class.

Once shipped, follow-ups in order:
1. `follow(npub)` / `unfollow(npub)` — same signer caveat.
2. NIP-46 (Remote) signer support — change the gate from "signer is
   internal" to "signer can sign in-process".
3. `sendDm(recipient, text)` — first write verb that uses encryption.
4. `zapUser` / `zapEvent` — non-trivial because of LN flow.
5. NIP-55 support via PendingIntent (Option A) once the system-shell
   contract is understood.

No write-verb code lands until question (1) above is answered —
otherwise the NIP-55 path is undefined and we ship something that
"sort of works" for half our users.
