# NIP-46 Signer — device verification checklist

Everything below is behavior that JVM unit tests **cannot** exercise: interactive
consent dialogs, the foreground service, real relay traffic, deep links, and
cross-app interop. The protocol/authorization logic underneath is covered by
`quartz` (`NostrConnectSignerServiceTest`) and `commons`
(`Nip46PermissionAuthorizerTest`, `Nip46ConsentIntegrationTest`) unit tests; this
list is the manual pass that earns "first-class" on a real device.

Run as the signer on one device/account ("bunker"); use a second app/account as
the client.

## Pairing
- [ ] **Bunker flow**: Settings → Nostr Signer → turn on → scan/copy the
      `bunker://` QR into a client (Coracle, Nostrudel, or a second
      Amethyst via `amy login bunker://…`). Client resolves your npub via
      `get_public_key`.
- [ ] **NostrConnect flow**: client shows a `nostrconnect://` code → "Scan a
      code" on the signer screen pairs it and the signer turns on.
- [ ] **NostrConnect informed consent**: pairing a `nostrconnect://` offer that
      carries `perms=` shows a connect sheet listing the app's requested
      permissions (e.g. "Sign notes (kind 1)", "Decrypt messages") + a trust
      picker BEFORE anything is granted. Approving pre-grants exactly those ops
      (unless Paranoid); Cancel/Block declines and nothing is registered. A
      re-pair of a known app skips the sheet and keeps prior decisions.
      Repro: `amy login --nostrconnect --perms sign_event:1,nip44_encrypt`
      prints an offer that carries exactly those perms (see CLI interop driver).
- [ ] **Global scanner**: scan a `nostrconnect://` from the profile/search
      camera → lands on the signer screen and pairs.
- [ ] **Deep link**: tap a `nostrconnect://` link (web/other app) → Amethyst
      opens the signer screen and pairs (cold start AND already-running).

## Note preview in the sign dialog (device-only)
- [ ] A `sign_event`/publish request renders the unsigned event as a **NoteCompose
      preview** (text + media + mentions, authored by the signing account), with
      the "Show event" JSON toggle still available below it.
- [ ] Works for both a NIP-46 remote app and a napplet Publish/SignEvent.
- [ ] When the main Activity is gone (app fully backgrounded, only the signer
      foreground service alive → `CallSessionBridge.accountViewModel` is null),
      the dialog falls back to the plain content quote + JSON without crashing.
- [ ] **Risk to watch:** NoteCompose is feed UI rendered inside a standalone
      dialog Activity; if it reads a CompositionLocal only provided by the main
      scaffold it could crash at runtime (compiles fine). Verify on device; if it
      misbehaves, the JSON fallback path is one boolean away.

## Entry point + connected-apps management (2026-07-16)
- [ ] **Drawer entry**: the signer opens from the left drawer's "You" section, directly
      under Wallet (moved out of Settings). It's also available as a bottom-bar favorite.
- [ ] **Dedicated apps screen**: "Manage connected apps" on the signer screen opens a
      NIP-46-only list (name, npub, relay count, last-used, trust chip), separate from the
      napplet/nsite/browser Connected Apps screen. NIP-46 apps no longer appear there.
- [ ] **Idle auto-forget**: an app left unused for 7 days is dropped on the next signer
      start (its background relay subscription goes with it); an app still signing is kept.

## Comes-to-front on a request (2026-07-16)
- [ ] **Backgrounded surfacing**: with Amethyst fully backgrounded (Android 12+), a client
      signing/connect request pops the consent dialog — via a full-screen-intent notification
      on the high-importance "Signing requests" channel (the `startActivity` fast path is
      BAL-blocked when backgrounded). On a locked screen it launches straight to the dialog;
      while actively on another app it shows a heads-up prompt to tap.
- [ ] **Foreground**: with Amethyst in the foreground the dialog opens directly (no extra
      notification — `SignerConsentNotifier` no-ops when `foregroundTracker.isForeground`).
- [ ] **Android 14+ caveat**: `USE_FULL_SCREEN_INTENT` is restricted for non-calling apps,
      so the FSI may degrade to a heads-up rather than auto-launch — verify the prompt still
      arrives and is tappable. Requires notification permission (already needed for the
      always-on service).

## Consent (Tier 1)
- [ ] **First-connect trust picker**: a bunker-flow connect with a valid secret
      shows the trust-level dialog (Full trust / Reasonable / Paranoid) BEFORE any
      signing; choosing a level records it in Connected Apps.
- [ ] **Cancel/Block**: dismissing the connect dialog rejects the connection (no
      silent grant).
- [ ] **Per-op ASK**: with a REASONABLE app, ask the client to sign a
      **kind 0 / kind 3 / delete (5)** or **decrypt a DM** → the per-op dialog
      appears (these are excluded from the auto-allowed set).
- [ ] **Remember variants**: "allow for this op" stops re-prompting; "session"
      stops until the signer restarts; "24h/30d" expire; "deny for op" sticks.
- [ ] **PARANOID app** prompts on every request; **FULL_TRUST** never prompts.
- [ ] **Timeout**: ignore a per-op dialog for 2 minutes → the request fails
      closed (deny) and the signer keeps serving later requests (not wedged).

## Anti-spam rotation (already shipped)
- [ ] "New address" → confirm dialog → old `bunker://` goes dark, connected apps
      drop, QR updates; re-pairing a legit app keeps its trust level.

## Visibility (Tier 2)
- [ ] Signer screen shows "Signing as npub1…", a live "Recent activity" feed
      (signed kind N / encrypted / decrypted / shared pubkey, green/red dot,
      relative time), and per-app history on the Connected-App detail screen.
- [ ] The Connected-App detail screen for a remote client shows its name/url,
      not a raw `nip46:` coordinate.

## Reliability (Tier 3)
- [ ] **Relay health**: kill connectivity → status shows "X of N relays
      connected"; restore → "all connected".
- [ ] **Boot restart**: enable the signer, reboot the device → the foreground
      service comes back and the signer answers a request without reopening the
      app. (Same for an app update via `MY_PACKAGE_REPLACED`.)
- [ ] **Doze/background**: after ~30 min idle in Doze, a request still gets
      serviced (may lag by a relay reconnect).

## Interop matrix
Pair + sign + nip44 encrypt/decrypt + logout against each:
- [ ] Coracle
- [ ] Nostrudel
- [ ] snort / other NIP-46 client

## CLI interop driver (`amy`) — added 2026-07-17
The `cli` module (`amy`) drives the same quartz/commons code, so it plays either
side of every NIP-46 flow for reproducible interop tests without a second phone.
All of it reuses `Nip46PermissionAuthorizer.parsePerms` / `toSignerOp` — no
protocol logic in `cli`. See `amy --help` (the `Remote signing (NIP-46)` block).

Amy as the **client** (Amethyst is the signer):
- `amy login bunker://…` — pair against Amethyst's advertised `bunker://`, then
  every `amy` signing verb routes through it. Surfaces `auth_url` challenges to
  stderr, so it completes even when Amethyst defers consent.
- `amy login --nostrconnect [--perms sign_event:1,nip44_encrypt,…]` — mint an
  offer for Amethyst to scan. `--perms` is what exercises the app's
  **informed-consent** sheet (the offer carries the declared ops).

Amy as the **signer** (Amethyst, or any client, is the client):
- `amy bunker` — headless auto-approve signer for the operator's own key.
- `amy bunker --perms sign_event:1,nip44_encrypt` — restricted signer: allows
  only the listed ops, **rejects** the rest. Use to test how the app-as-client
  handles a signer that says no.
- `amy bunker --interactive` — keeps listening and prompts `y/N` per request on
  the terminal (TTY-only, default-deny, prompts serialized). Composes with
  `--perms` (auto-allow the listed ops, prompt for the rest = the "Reasonable"
  policy on the CLI).

## Audit findings — known limitations (2026-07-16)

An adversarial review of the signer logic surfaced these. The head-of-line
issues below share one root cause: `authorize()`/`onConnect()` run **inline** in
`NostrConnectSignerService`'s single-consumer loop, and relay-set changes restart
that loop via `collectLatest`.

- **FIXED — unbounded first-connect prompt.** `Nip46ConsentBridge.requestConnect`
  now has the same 120s `withTimeoutOrNull` as `requestOp`, so an ignored
  first-connect dialog can no longer wedge the loop forever.
- **FIXED — consent no longer blocks other clients (needs on-device
  validation).** The service now fans each request out into a child coroutine
  under a `Semaphore(maxConcurrentHandles)` (now 1024); dedup/staleness stay on
  the single consumer, only `handle()` runs concurrently. So a request awaiting a
  prompt no longer stalls auto-allowed traffic, and several prompts can be pending
  at once. The identity signer's crypto is NOT serialized (the old
  `BunkerRequestProcessor.cryptoLock` was removed 2026-10-06): local keys are pure
  crypto and NIP-55 signers like Amber accept overlapping calls, so a high-throughput
  client's ops run in parallel. First-connect consent is still serialized by
  `Nip46PermissionAuthorizer.connectLock` so two connects can't stack dialogs. Per-op prompts batch: the shared `SignerConsentCoordinator.pending`
  flow drives one dialog (1 pending) or a checkbox list (>1). Covered by
  `BunkerRequestProcessorConcurrencyTest`, but the on-device paths below still need
  a real run:
  - [ ] **Burst batching:** a client fires several dangerous-kind requests at once
        → one batched sheet with checkboxes + select-all, Allow/Deny selected,
        "Remember" toggle. Approving a subset leaves the rest pending.
  - [ ] **Auto-allowed keeps flowing:** while a prompt sits open, a REASONABLE
        auto-allowed request from another app still gets signed and answered.
  - [ ] **Concurrent external-signer ops:** with a NIP-55 external signer (Amber),
        a burst of approved requests drives overlapping calls and every one is answered.
  - [ ] **Fail-closed on dismiss:** backing out of the batched sheet denies every
        still-open request (not just the selected ones).
- **Relay-set change cancels in-flight work.** A `logout` (or a new nostrconnect
  pairing) mutates the listen set → `collectLatest` restarts the service →
  cancels the in-flight `handle()`. Practical impact is low (a logout ACK is lost
  but the client is leaving; a pairing-time cancel makes other clients retry).
  Proper fix: manage subscriptions incrementally (diff add/remove) instead of a
  full restart. Deferred (same reason).
- **Per-author rate limiter removed (2026-10-06).** A single app must be able to
  drive ~10k requests/s, so the 40-per-10s fixed window (and its `rate limited`
  error reply) is gone. The remaining flood bounds were resized for that rate:
  `maxQueue` 256 → 10,000, `seenCap` 4096 → 10,000, `maxConcurrentHandles`
  16 → 1024. Covered by `NostrConnectSignerServiceTest.burstFromOneClientIsFullyServiced`.
- **Unpaired clients are still limited.** Paired clients are exempt, but a key
  that isn't paired gets 10 requests per 60 s, and all unpaired keys together
  get 100 per 60 s, checked before decrypting; over-limit requests are dropped
  with no reply. The per-key check runs before the pairing lookup (a DataStore
  read), and only keys found unpaired spend the shared budget, so a flood can't
  lock out a paired app. Known paired keys are cached in memory for the run.
  Reads of the per-app permission store check the file exists first, so a
  pairing lookup for an unknown key opens no DataStore.
- **Per-request bookkeeping batched (2026-10-06).** The activity log and the
  persisted seen-id set each take a drop-oldest channel send per request; one
  coroutine publishes the log at most every 250 ms and saves the ids every 2 s.
  Stale-request warnings are summarized once per second.
- **Low-severity, left as-is:** first-time transport-key/secret mint
  is unsynchronized (practically serialized on the UI thread).

## Deliberately NOT changed
The always-on foreground **notification** was left as-is: it is shared with the
relay/DM always-on service, so retitling it "Signing for N apps" or deep-linking
it to the signer screen would be wrong when the service is up for another reason.
Interactive consent uses its own dedicated dialog Activity, so it needs no
notification actions.
