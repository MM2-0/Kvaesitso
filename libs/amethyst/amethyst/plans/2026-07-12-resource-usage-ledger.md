# Resource Usage Ledger — battery/data accounting, user-visible + NIP-17 reportable

**Date:** 2026-07-12
**Goal:** Let users (and developers) see how much network, connection time, and
background activity the app consumes, per subsystem — and let a user send that
data to the developers over NIP-17, reusing the crash-report consent pattern.
When consumption crosses "something is wrong" thresholds, proactively ask the
user (rate-limited, opt-out-able) whether they'd like to send a report.

Background: the 2026-07-12 ping-interval study (see
`2026-07-12-relay-ping-interval-study.md`) showed the dominant energy proxy is
connection-time (relays server-ping every 30–70s while connected) and that
battery bugs are production-only phenomena — so the ledger ships in release,
collects passively, and never transmits anything without an explicit user
action.

## Survey (existing components reused)

- **Send path** — the crash-report pipeline: `DisplayCrashMessages` prefills
  the NIP-17 DM composer via `routeToMessage(user = <dev pubkey>, draftMessage,
  expiresDays = 30)`; the user taps Send; `Account.sendNip17PrivateMessage`
  gift-wraps to the recipient's kind-10050 DM relays. Reused as-is — the
  ledger only builds a different draft string.
- **Persistence idiom** — `ScheduledPostStore` (Jackson + Mutex + tmp-rename +
  version envelope + StateFlow). Cloned as `ResourceUsageStore`.
- **Relay traffic** — counted by a new `RelayConnectionListener`
  (same hook `RelayStats` uses), NOT by modifying quartz.
- **Connection time** — integrated from `INostrClient.connectedRelaysFlow()`
  (exact between emissions; no timers).
- **Network class** — `ConnectivityManager.isMobileOrFalse` StateFlow.
- **Foreground** — new tiny `ForegroundTracker` (ActivityLifecycleCallbacks →
  StateFlow<Boolean>), registered next to `AppForegroundRecycleHook`;
  `MainActivity.isResumed` is not observable and slightly stricter than
  process-foreground.
- **HTTP subsystems** — `RoleBasedHttpClientBuilder` already funnels every
  role (image/video/uploads/money/nip05/preview/push) through two shared
  clients; a cached per-role `newBuilder().addInterceptor(counting)` wrapper
  gives per-subsystem byte attribution without touching the shared clients.
- **UI idioms** — `NotificationSettingsScreen` structure (`Scaffold` +
  `TopBarWithBackButton` + `SettingsSection` cards), route in `Routes.kt`,
  `composableFromEnd` registration, catalog entry via
  `SettingsCatalogBuilder.symEntry` (icon: existing `MaterialSymbols.Bolt` —
  no font regen).
- **App-open dialog** — `DisplayCrashMessages` pattern, mounted in the same
  `AppNavigation` block.

## Design

### Counters
Flat `Map<String, Long>` per UTC epoch-day, retained ~30 days. Key grammar:
`<area>...<mobile|wifi>.<fg|bg>[.<rx|tx>]`, e.g.:

- `net.image.mobile.bg.rx` — bytes downloaded by the image subsystem on
  cellular while backgrounded (same for video/uploads/money/nip05/preview/push)
- `relay.msg.wifi.fg.rx|tx` — approx relay websocket payload bytes
- `relay.connms.mobile.bg` — relay-connection-milliseconds (Σ relays × time)
- `wakelock.notif.ms` / `wakelock.notif.count`
- `worker.scheduledPost.runs` / `worker.calendarReminder.runs` /
  `worker.notificationCatchUp.runs`
- `app.starts` — process starts (detects WorkManager cold-start churn)
- `relay.connects.<net>.<vis>` / `relay.connfails.<net>.<vis>` — completed
  (re)connections and failed dials; each connect paid a TCP+TLS handshake,
  so high daily counts are the reconnect-churn signature
- `cpu.ms` — whole-process CPU time deltas ([android.os.Process
  .getElapsedCpuTime] sampled at flush): the honest aggregate of parsing,
  crypto, coroutines, and UI without per-subsystem guesswork
- `app.fgms` — time with UI visible; display power is proportional to it and
  it's the denominator for every per-day comparison
- `crypto.verify.count` / `crypto.verify.us` — event signature verifications
  (LocalCache.justVerify hook), settling "does Schnorr verify cost matter"
  with data
- `net.<role>.<net>.<vis>.reqs` / `.activems` — HTTP request counts and
  active-transfer time per subsystem; counting lives on the shared base
  client (OkHttpClientFactory) with tag-based role attribution, so untagged
  callers land in `other` instead of escaping the ledger
- `net.bursts.<net>.<vis>` — estimated radio wake-ups from HTTP burst
  patterns (new activity after >10s of HTTP silence): the battery-relevant
  measure that bytes alone can't capture, since scattered small requests
  each pay the radio ramp+tail
- `media.playms` — actual media playback time (ExoPlayer isPlaying
  segments): decoder + screen + streaming at once, the denominator for
  video bytes

- `pow.ms` / `pow.sessions` — NIP-13 mining time (any job mining in the
  PoW queue): full-core CPU, the largest attributable CPU consumer
- `tor.ms` / `tor.starts` — in-app (Arti) Tor uptime and bootstraps, from the
  raw TorService status (NOT TorManager.status, whose WhileSubscribed
  upstream calls service.start() when collected). External Tor (Orbot) is
  deliberately untracked — its battery belongs to Orbot
- `service.alwayson.ms` — NotificationRelayService uptime: the mode context
  that explains a device's relay connection-time
- `call.ms`/`call.sessions`, `nests.ms`/`nests.sessions` — calls and NIP-53
  audio rooms (mic + Opus + live media connection), from the foreground
  services' lifecycles
- `location.ms` — time actively listening for GPS updates (geohash tagging);
  mostly a tripwire for a leaked location subscription
- `crypto.decrypt.count/us`, `crypto.encrypt.count/us` — NIP-04/44 work via a
  MeteringNostrSigner decorator wrapped inside NostrSignerWithClientTag at
  account load; durations metered only for local-key signers (external/
  remote waits are IPC/network, not CPU)
- `sign.local|nip46|nip55.count` — signatures by signer kind: NIP-46 is a
  relay round-trip and NIP-55 an Amber IPC wake, so the kind is the
  battery-relevant dimension (this supersedes "signing is negligible", which
  is only true for local keys)
- `battery.drain.fg|bg` — measured battery percent while discharging, sampled
  at flush from BatteryManager: NOT app-isolated, but the ground truth that
  report corpora can correlate the other counters against

- `screen.<Name>.ms` — foreground time per screen, added after the original
  privacy review: only the route's base NAME is recorded (screenNameOf strips
  every navigation argument before the value leaves the nav layer), so the
  ledger can say "Profile" but never whose profile

Deliberately not tracked (v1): per-coroutine or per-dispatcher CPU (needs a
thread registry; `cpu.ms` answers whether CPU matters at all first).
(Two earlier v1 exclusions were later revisited: per-screen time ships with
names-only privacy as above, and signing is now counted per signer kind
because NIP-46/NIP-55 signatures are network/IPC round-trips, not local CPU.)

Flat keys keep the store schema-free: new counters need no migration.

### Components (`amethyst/.../service/resourceusage/`)
- `UsageKeys` — key constants/builders + dimension helpers.
- `ResourceUsageStore` — daily buckets on disk (`resource_usage.json`),
  `mergeInto(day, deltas)`, `allDays()`, prune, plus alert state
  (lastAlertAtSec, optOut).
- `ResourceUsageAccountant` — in-memory `ConcurrentHashMap<String, LongAdder>`
  hot path (`add()` is called per relay frame), debounced flush (30s) into the
  store, day-rollover handling, merged read API for UI/report.
- `ForegroundTracker` — startedActivities>0 as StateFlow.
- `RelayUsageListener` — `RelayConnectionListener` counting sent/received
  frame sizes with current network/visibility dims.
- `RelayConnectionTimeIntegrator` — combines connectedRelays × isMobile ×
  isForeground; closes an accounting segment on every change and on
  `closeOpenSegment()` (called from accountant flush and reads, so multi-hour
  stable background sessions still account without any timer).
- `UsageCountingInterceptor` + counting response body — per-role HTTP bytes;
  wrapped clients cached per (role, base client identity).
- `ResourceUsageReportAssembler` — Markdown: device/app header (crash-report
  style), human summary (today + 7 days), fenced per-day counter dump.
- `ResourceUsageAlerts` — pure threshold logic (see below) + rate limiting.
- `DisplayResourceUsageAlert` — consent dialog (view details / send / not
  now / don't ask again).
- UI: `ResourceUsageScreen` under `ui/screen/loggedIn/settings/`.

### Wiring (AppModules / Amethyst / hooks)
- store + accountant + integrator constructed in `AppModules`; listener added
  via `client.addConnectionListener`.
- `ForegroundTracker` registered in `Amethyst.onCreate` (main process only).
- `RoleBasedHttpClientBuilder` gains an optional usage meter.
- `EventNotificationConsumer.withWakeLock` gains an optional held-duration
  callback (threaded through `NotificationDispatcher`).
- Workers increment their run counters via `Amethyst.instance` (guarded).
- `AppModules.trim()` flushes the accountant (backgrounding = natural flush).

### Alert thresholds (v1, deliberately conservative — tune with real reports)
Evaluated on the last *complete* day, OR today once exceeded:
- background cellular traffic > 50 MB/day
- relay connection time > 12 relay-hours/day while backgrounded on cellular
- notification wakelock held > 30 min/day
- process starts > 75/day
Rate limit: at most one prompt per 7 days; "don't ask again" persisted.
Never auto-sends: every path goes through the DM composer where the user sees
exactly what will be sent and must tap Send.

### Privacy
Counters are sizes, durations, and counts — no URLs, no relay names, no event
content. The report includes device model fields identical to the crash
report. Everything stays on-device until the user explicitly sends the DM
(NIP-40 30-day expiration, same as crash reports).

### Explicitly out of scope (v1)
- Layer 1 (Perfetto/ODPM macrobenchmarks) and Layer 2 (`TrafficStats` socket
  tags) — add only if the ledger proves blind somewhere (e.g. WS bytes are
  payload-approximate; TrafficStats would give exact on-wire bytes).
- Per-relay attribution in the ledger (RelayStats screens already exist).
- Desktop: accountant/store are Android-module for now; extraction to commons
  is mechanical if desktop wants it.
