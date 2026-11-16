# WebRTC Call Architecture

A map of how P2P and group voice/video calls work in Amethyst, aimed at future
contributors (human or AI) who need to change call code without spending an
hour grepping.

## 1. Protocol (NIP-AC)

Calls are signaled over Nostr using the NIP-AC event family. Every signaling
event is sealed inside a NIP-59 gift wrap of kind `21059`
(`EphemeralGiftWrapEvent`) addressed to a single recipient per wrap.

| Kind  | Event                    | Purpose                                                    |
|-------|--------------------------|------------------------------------------------------------|
| 25050 | `CallOfferEvent`         | Offer + SDP, starts ringing on the callee                  |
| 25051 | `CallAnswerEvent`        | Answer + SDP, callee accepts                               |
| 25052 | `CallIceCandidateEvent`  | Trickle ICE candidate for a specific peer                  |
| 25053 | `CallHangupEvent`        | End the call / drop a peer                                 |
| 25054 | `CallRejectEvent`        | Decline ringing (only valid while ringing)                 |
| 25055 | `CallRenegotiateEvent`   | Mid-call SDP renegotiation (e.g. voice → video)            |

Specification: `quartz/.../nipACWebRtcCalls/NIP-AC.md`.

Gift-wrap recipient is set via `EphemeralGiftWrapEvent.create(event, recipientPubKey)`.
The wrap's outer `p` tag is the recipient's pubkey; relay subscriptions
filter gift wraps by `p == myPubKey`, so **a device only receives signaling
addressed explicitly to its account**.

## 2. Module layout

```
quartz/commonMain/.../nipACWebRtcCalls/
├── events/                      # Event classes (KIND constants, builders, accessors)
├── tags/                        # CallType, CallIdTag, etc.
└── WebRtcCallFactory.kt         # Sign + gift-wrap helpers (P2P + group variants)

commons/commonMain/.../nipACWebRtcCalls/     # ← this package
├── CallState.kt                 # Sealed state + EndReason enum
├── CallManager.kt               # State machine, signaling dispatch, timeouts
├── PeerSession.kt               # Platform-neutral per-peer signaling interface
└── PeerSessionManager.kt        # Map<pubKey, PeerSession> + ICE buffering

amethyst/.../service/call/       # Android platform layer
├── CallController.kt            # Wires CallManager ↔ WebRTC, audio routing
├── WebRtcCallSession.kt         # org.webrtc.PeerConnection wrapper
├── WebRtcPeerSessionAdapter.kt  # Implements PeerSession over org.webrtc
├── CallAudioManager.kt          # Speakerphone / Bluetooth SCO routing
├── CallMediaManager.kt          # Camera / microphone tracks
├── CallForegroundService.kt     # Android foreground service while in a call
└── CallNotificationReceiver.kt  # Accept / reject from notification actions

amethyst/.../ui/call/            # Android call UI
└── CallScreen.kt                # IncomingCallUI, InCallUI, etc.
```

`quartz` is pure Kotlin/Multiplatform protocol code with **zero** WebRTC or
Android dependencies. `commons/.../nipACWebRtcCalls/` is also platform-neutral — it holds
the state machine and a `PeerSession` abstraction. All `org.webrtc` code lives
in `amethyst/service/call/`.

## 3. State machine

```
                ┌──────────────────────────────────────────────┐
                │                                              │
                ▼                                              │
              Idle ─────initiateCall()─────▶ Offering          │
                │                               │              │
                │                         onCallAnswered       │
                │                               │              │
                │                               ▼              │
     onIncomingCallEvent                    Connecting         │
                │                               │              │
                ▼                         onPeerConnected      │
         IncomingCall ──acceptCall()─────▶     │               │
                │                               ▼              │
           rejectCall()                     Connected          │
                │                               │              │
                │                          hangup() /          │
                │                         PEER_HANGUP          │
                │                               │              │
                └───────────────▶  Ended ◀─────┘              │
                                     │                         │
                                     └─────after 2s────────────┘
```

States (`CallState.kt`):

- **`Idle`** — no call.
- **`Offering`** — we sent the offer, waiting for answers. Carries
  `peerPubKeys` (pending callees).
- **`IncomingCall`** — we received an offer and are ringing. Carries the
  caller, `groupMembers`, and the SDP offer.
- **`Connecting`** — the handshake is in progress. Carries `peerPubKeys`
  (peers we're handshaking with) and `pendingPeerPubKeys` (known group
  members we haven't heard from yet).
- **`Connected`** — at least one peer session is live. Same fields as
  `Connecting` plus `startedAtEpoch`.
- **`Ended(reason)`** — terminal transition. After `ENDED_DISPLAY_MS` (2 s)
  the state automatically resets to `Idle`.

`EndReason`: `HANGUP`, `REJECTED`, `TIMEOUT`, `ERROR`, `PEER_HANGUP`,
`PEER_REJECTED`, `ANSWERED_ELSEWHERE`.

## 4. `CallManager` responsibilities

`CallManager` (in this package) is the single owner of call state. It:

1. Runs the state machine. All mutations go through `stateMutex` — signaling
   events arrive on multiple relay coroutines concurrently.
2. Dispatches signaling events (`onSignalingEvent`) to per-kind handlers
   (`onIncomingCallEvent`, `onCallAnswered`, `onCallRejected`, `onPeerHangup`,
   `onIceCandidate`, `onRenegotiate`).
3. Publishes outbound signaling via `factory: WebRtcCallFactory` and the
   injected `publishEvent: (EphemeralGiftWrapEvent) -> Unit` lambda.
4. Forwards SDP/ICE payloads to the platform WebRTC layer via callback
   properties (`onAnswerReceived`, `onIceCandidateReceived`,
   `onRenegotiationOfferReceived`, `onNewPeerInGroupCall`,
   `onMidCallOfferReceived`, `onPeerLeft`). **These callbacks are the only
   `CallManager` → `CallController` contact surface.**
5. Manages two kinds of timers:
   - **Global timeout** (`CALL_TIMEOUT_MS = 60 s`) — fires while in `Offering`
     or `IncomingCall`. Transitions to `Ended(TIMEOUT)` and, if `Offering`,
     publishes a group hangup so callees stop ringing immediately.
   - **Per-peer invite timeout** (`PEER_INVITE_TIMEOUT_MS = 30 s`) — one timer
     per pending peer. See §7 for the invited-vs-watchdog distinction.
6. Deduplicates incoming events (`processedEventIds`) and drops events older
   than 20 s or from before `initTimestamp` (`isEventTooOld`). Remembers
   already-terminated call-ids (`completedCallIds`) so relay replays after an
   app restart don't re-ring for a dead call.

`CallManager` is created by `CallController`, which injects the current
account's `NostrSigner`, a coroutine scope tied to the account's lifetime,
`isFollowing` to gate incoming offers, the `publishEvent` lambda that goes
through the relay client, and `isCallsEnabled` (settings toggle).

## 5. Incoming call pipeline

```
relay → GiftWrap subscription on p=myPubKey
      → DecryptAndIndexProcessor decrypts & unwraps
      → routes kind-25050..25055 events to CallManager.onSignalingEvent
      → stateMutex.withLock { … dedup, age-gate, dispatch … }
      → per-kind handler mutates _state and/or forwards to CallController
      → CallController drives org.webrtc.PeerConnection via WebRtcCallSession
```

Relay subscription: `amethyst/.../service/relayClient/.../FilterGiftWrapsToPubkey.kt`
(filter: `kind ∈ {1059, 21059}, #p = [myPubKey]`).

Decryption + dispatch: `DecryptAndIndexProcessor.kt` routes every
`CallOfferEvent | CallAnswerEvent | CallIceCandidateEvent | CallHangupEvent |
CallRejectEvent | CallRenegotiateEvent` to `callManager?.onSignalingEvent(event)`.

## 6. Outgoing call pipeline

- **P2P**: `CallController.initiateCall(peer, type)` → generates SDP →
  `CallManager.initiateCall(peer, type, callId, sdpOffer)` → transitions to
  `Offering`, publishes one wrap to the callee.
- **Group**: `CallController.beginGroupCall(peers, type)` →
  `CallManager.beginOffering(callId, peers, type)` transitions to `Offering`
  and schedules per-peer timers, then CallController creates a separate SDP
  per peer and calls `CallManager.publishOfferToPeer` once for each.

`WebRtcCallFactory.createGroupCallOffer` / `createGroupCallAnswer` /
`createGroupHangup` / `createGroupReject` each sign a **single** inner event
(with `p` tags listing all members) and produce one `EphemeralGiftWrapEvent`
per recipient — the inner event is shared, only the outer wrap envelope
differs.

## 7. Group calls: invited vs. watchdog timers

Two sources drop a pending peer from the state after 30 s, but they publish
different signals:

- **Invited by us** (we sent the offer): added to `peersInvitedByUs`.
  On timeout we publish a `CallHangup` addressed to them so their device
  stops ringing.
- **Watched by us** (we accepted a group call and are waiting on siblings
  we never invited): we only run a local watchdog. Terminating their ringing
  is the *original caller's* responsibility; we must not publish anything on
  their behalf.

Distinguishing these two is the job of `peersInvitedByUs` and the
`wasInvitedByUs` flag in `handlePeerTimeout`. Do not collapse them.

Callee-to-callee mesh (group calls): when `acceptCall` runs, any answers we
already observed during `IncomingCall` (`discoveredCalleePeers`) trigger
`onNewPeerInGroupCall` callbacks so the CallController opens additional peer
connections. Mid-call offers from other callees arrive via `onMidCallOfferReceived`.

## 8. Multi-device ("answered elsewhere") handling

A user can be logged in on multiple devices. All of them are subscribed to
gift wraps for the same pubkey, so all of them receive the caller's offer and
ring simultaneously. The rules:

1. **`acceptCall` and `rejectCall`** publish two sets of wraps:
   - The "real" wraps for every other group member (carrying SDP where
     relevant).
   - **An extra wrap of the same signed event addressed to `signer.pubKey`**
     so sibling devices on the same account observe it.
2. **Sibling device receives a self-answer in `IncomingCall`** →
   `onCallAnswered` transitions to `Ended(ANSWERED_ELSEWHERE)`. Self-reject
   in `IncomingCall` → `Ended(REJECTED)`. Both are purely local state
   changes; neither publishes any signaling. **This is why the device that
   picked up is never disturbed.**
3. **The device that published the self-wrap also receives its own echo**.
   It is ignored: the self-pubkey guard at the top of both `onCallAnswered`
   and `onCallRejected` returns early in any state other than `IncomingCall`.
   Without the self-reject guard, an echo arriving while in
   `Connecting`/`Connected` would fire `onPeerLeft(signer.pubKey)` and the
   CallController would dispose its *own* PeerSession, killing the live
   audio — do not remove that guard.
4. **Self-ICE and self-hangup** are unconditionally filtered at the top of
   `onSignalingEvent` (they are never useful). Self-answers and self-rejects
   are **not** filtered there — they are the "answered/rejected elsewhere"
   signal and must reach the handler.
5. **Stale offer replays**: `completedCallIds` is populated by hangup, reject,
   **and self-answer** events. If a relay replays events out of order so a
   self-answer arrives before the original offer, the later offer is dropped
   by the `callId in completedCallIds` check in `onIncomingCallEvent`.

See `CallManagerTest.kt`'s "Multi-Device" section for the full scenario
coverage.

## 9. Invariants and pitfalls

- **All state mutations must happen under `stateMutex`.** The lock also
  guards `processedEventIds`, `completedCallIds`, `peersInvitedByUs`, and
  `perPeerTimeoutJobs`. Methods that are called from outside
  `onSignalingEvent` (e.g. `acceptCall`, `hangup`, `onPeerConnected`) take
  the lock themselves.
- **Publish outside the lock.** The lock protects state; the signing + gift
  wrap is slow and must not block other signaling. Pattern: read/mutate state
  inside the lock, collect the bytes to publish, then `publishEvent` after
  releasing.
- **`transitionToEnded` is local-only.** It never publishes anything.
  Publishers (`hangup`, `rejectCall`, timeout handlers) publish *around* it.
- **`onPeerLeft(pubKey)` must never be called with `signer.pubKey`.** It
  tells CallController to dispose a peer session; passing self tears down
  the local call. The self-pubkey early-returns in `onCallAnswered` and
  `onCallRejected` enforce this.
- **`ENDED_DISPLAY_MS` auto-reset.** After ending, state lingers in `Ended`
  for 2 s so the UI can show "Call ended" before resetting to `Idle`.
  `resetJob` is cancelled on any new transition.
- **`isEventTooOld` rejects events older than 20 s** (`MAX_EVENT_AGE_SECONDS`)
  or created before `CallManager` was constructed. This prevents relays from
  ringing the phone with a day-old offer on app startup.
- **`isFollowing` gates incoming offers only.** Once a call is in flight,
  all mid-call signaling is accepted regardless of the follow relationship.
- **Do NOT add event kinds to `onSignalingEvent`'s self-filter without
  thinking.** Self-answers and self-rejects are load-bearing for
  multi-device.

## 10. Testing

`commons/commonTest/.../nipACWebRtcCalls/CallManagerTest.kt` is the canonical reference
for expected state transitions. It uses real `NostrSignerInternal` keys so
the factory-sign-wrap pipeline is exercised end-to-end, with `publishEvent`
captured into a list for assertions. Construct events directly with the
`makeOffer` / `makeAnswer` / `makeHangup` / `makeReject` / `makeIceCandidate`
helpers — they mirror real event shapes but skip signing.

The "Multi-Device: Second Logged-In Device Must Stop Ringing" section at the
bottom of the test file shows how to simulate two phones with the same
signer. Prefer extending it over creating new test files.

Run just the CallManager tests:

```bash
./gradlew :commons:commonTest --tests "com.vitorpamplona.amethyst.commons.call.CallManagerTest"
```
