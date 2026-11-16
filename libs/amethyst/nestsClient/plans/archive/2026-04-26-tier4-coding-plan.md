# Tier 4 — coding plan (token refresh, moq-lite reconnect backoff)

> **Status:** shipped — `ReconnectingNestsListener`/`ReconnectingNestsSpeaker` + `NestsReconnectPolicy` implement reconnect-with-backoff and JWT re-mint on reconnect.
> _Audited 2026-06-30._

Tier 4 of `2026-04-26-nostrnests-integration-audit.md`. Pure
infrastructure hardening — no user-visible UX changes. Covers the
items the audit flagged as "MAYBE GAP" or "worth confirming".

## Step 1 — Token re-mint before expiry on long sessions (#16)

moq-auth tokens live 600 s and there's no refresh endpoint. A
listener / speaker that stays in a room past the 10-minute mark
needs to mint a fresh JWT and reopen the WT session — otherwise the
moq-rs relay starts rejecting per-track auth checks.

### Audit first

- Walk `MoqLiteSession` + `MoqLiteNestsListener` /
  `MoqLiteNestsSpeaker` to confirm the JWT is only consulted at
  WT CONNECT time (it is — moq-rs's auth check is per-session, not
  per-OBJECT). That means the **only thing that needs re-minting
  is when the WT session itself dies and we reconnect** — see Step 2.
- If the user stays in a room for hours without a transport blip,
  the relay never re-checks the JWT, so we don't strictly need a
  pro-active re-mint. **Confirm against moq-rs**: does the relay
  drop the session when its claims `exp` passes? If yes → step 2's
  reconnect path naturally re-mints.

### If a pro-active re-mint IS needed

Implement in `MoqLiteNestsListener` / `MoqLiteNestsSpeaker`:

- Parse the JWT's `exp` claim from the response — extend
  `OkHttpNestsClient.mintToken` to return `(token, expiresAt: Long)`
  instead of just `token`.
- Schedule a coroutine in `connectNests*` that fires
  `(expiresAt - now - 60s)` before expiry and calls a private
  `reauthorize()` that mints a fresh token and...
    - either silently passes it to a (yet-to-exist) moq-lite
      "AUTH_REFRESH" message — moq-lite **doesn't have one** today,
    - **or** quietly tears down the current WT session + reconnects
      (matching the reconnect path in Step 2).

### Open question

Does moq-rs honour a re-handshake on the same session? Almost
certainly not — there's no in-band auth message. So the pro-active
path collapses into "trigger a reconnect ~60 s before `exp`".

### Recommendation

Treat this step as **subsumed by Step 2** — once the reconnect
backoff handles transport drops, an `exp`-driven self-disconnect
is just another trigger. Schedule:

```kotlin
val refreshAt = expiresAt - now - REFRESH_LEAD_MS  // 60_000
scope.launch {
    delay(refreshAt.coerceAtLeast(0))
    triggerReconnect(reason = "JWT expiry")
}
```

in `connectNestsListener` / `connectNestsSpeaker`, where
`triggerReconnect(...)` is the same entry point Step 2 builds.

---

## Step 2 — moq-lite Connection.Reload-equivalent reconnect with backoff (#17)

The JS reference uses
```
delay: { initial: 1000, multiplier: 2, max: 30000 }
```
when the WT session drops. Confirm Amethyst's `MoqLiteSession`
has equivalent backoff before adding it.

### Audit

- `nestsClient/.../moq/lite/MoqLiteSession.kt` — does any flow on
  it actually reconnect on transport failure? Or does the session
  surface `Closed` and leave it to the caller (the
  `NestsListener` / `NestsSpeaker` connectors) to retry?
- `nestsClient/.../NestsConnect.kt` —
  `connectNestsListener` / `connectNestsSpeaker` walk the
  HTTP→WT→moq-lite handshake once and return a Listener/Speaker.
  No retry today.

The audit will almost certainly conclude: **no auto-reconnect
exists**. Add it.

### Design

Add at the orchestration layer (`connectNestsListener` /
`connectNestsSpeaker`), not inside `MoqLiteSession`. The session is
"this connection"; reconnect is "open another connection".

- `nestsClient/.../NestsReconnectPolicy.kt` (NEW) — pure data:
  ```kotlin
  data class NestsReconnectPolicy(
      val initialDelayMs: Long = 1_000,
      val multiplier: Double = 2.0,
      val maxDelayMs: Long = 30_000,
      val maxAttempts: Int = Int.MAX_VALUE,
  )
  ```
- `nestsClient/.../NestsListener.kt` — extend the state machine:
    - new `NestsListenerState.Reconnecting(attempt: Int, delayMs: Long)`
    - on the underlying session emitting `Closed`, walk the reconnect
      flow (mint fresh JWT → open WT → wrap in MoqLiteSession →
      re-issue every active SubscribeHandle).
- `nestsClient/.../NestsSpeaker.kt` — equivalent on the speaker
  side. Re-publish the broadcast suffix automatically; transparently
  resume capture after the new session opens.

### Test

- New `nestsClient/src/jvmTest/.../moq/lite/MoqLiteReconnectTest.kt`
  — drive a `FakeWebTransport` that closes after the first frame,
  then assert the listener auto-reconnects within
  `initialDelayMs + jitter`.
- Update one of the interop tests (round-trip) to deliberately kill
  the WT session mid-broadcast and verify the listener resumes
  receiving frames.

### Risk

- Re-issuing every active SubscribeHandle has to preserve the
  consumer-side `Flow<MoqObject>` so app code doesn't notice.
  Achievable by buffering the handle's flow upstream of the
  per-session adapter — likely a `MutableSharedFlow` per handle
  that the per-session pump emits into.

---

## Suggested commit order

1. **Audit + notes**: open a small text note documenting whether
   the relay drops sessions on JWT `exp` (Step 1 dependency) — a
   ~2-paragraph file in `nestsClient/plans/`.
2. **`NestsReconnectPolicy` + state machine extension** (Step 2,
   listener side first).
3. **Auto-reconnect tests** against `FakeWebTransport`.
4. **Speaker-side reconnect** (Step 2 second half).
5. **JWT-expiry-driven reconnect** (Step 1, plumbing only — uses
   the Step 2 entry point).

Each step is independently merge-safe; the listener-only path is
useful even if the speaker reconnect ships later.

## Out of scope for Tier 4

- WebSocket fallback (`useWebSocket: true`) — only matters for
  browser clients without WebTransport. Android has WT via QUIC, so
  not a gap.
- Multi-track / video / fetch — confirmed not used by nostrnests.
- Bitrate probes — not used by nostrnests.
- Per-IP rate-limit smoothing for `/auth` — already handled at the
  user-action level (one mint per join). No need for client-side
  throttling.
