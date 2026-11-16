# Extract `NestSubscriptionManager` from `NestViewModel`

> **Status:** in-progress — the stepping-stone `ActiveSubscription.kt` is extracted, but the orchestration class `NestSubscriptionManager` does not exist and `reconcileSubscriptions`/`openSubscription`/`closeSubscription`/`fetchSpeakerCatalog` still live in the 2295-line `NestViewModel`.
> _Audited 2026-06-30._

**Status**: deferred — flagged in the audit pass, not landed yet.

## Why

`NestViewModel.kt` is 2112 lines and growing. ~1000 of those lines are
the per-speaker subscription-lifecycle state machine: open / close /
reconcile / catalog-fetch / decoder-await / level-tap / mute-routing /
hush. The rest is room-level public API (connect, disconnect,
broadcast, presence, reactions, focus / network observers, UI state).

These two concerns are coupled by shared mutable state but do not
share a *responsibility*. The audit-9 finding flagged it as the
single biggest SRP breach in the touched code.

We extracted `ActiveSubscription` into its own file (`ActiveSubscription.kt`)
in commit `<TBD>` as a stepping stone — the deferred extraction
proper is the orchestration class.

## Target shape

```kotlin
internal class NestSubscriptionManager(
    private val viewModelScope: CoroutineScope,
    private val signer: NostrSigner,
    private val decoderFactory: (channelCount: Int, sampleRate: Int) -> OpusDecoder,
    private val playerFactory: (channelCount: Int, sampleRate: Int) -> AudioPlayer,
    private val onActiveSpeakersChanged: (Set<String>) -> Unit,
    private val onSpeakerActivity: (String) -> Unit,
    private val onAudioLevel: (String, Float) -> Unit,
    private val onConnectingSpeakerChanged: (pubkey: String, connecting: Boolean) -> Unit,
    private val effectiveListenMuted: () -> Boolean,
    private val locallyHushed: () -> Set<String>,
) {
    val speakerCatalogs: StateFlow<Map<String, RoomSpeakerCatalog>>
    val audioLevels: StateFlow<Map<String, Float>>

    fun bind(listener: NestsListener)
    fun unbind()  // cancel everything; release native resources
    fun updateSpeakers(requested: Set<String>)
    fun applyEffectiveMute()
    fun setLocalHushed(pubkey: String, hushed: Boolean)
}
```

## State that moves

From `NestViewModel`:
- `activeSubscriptions: MutableMap<String, ActiveSubscription>`
- `catalogJobs: MutableMap<String, Job>`
- `_speakerCatalogs: MutableStateFlow<Map<String, RoomSpeakerCatalog>>`
- `requestedSpeakers: Set<String>`
- `speakingExpiryJobs: MutableMap<String, Job>` (for `onSpeakerActivity` debounce)
- `_audioLevels` if separable

## Methods that move

- `reconcileSubscriptions`
- `openSubscription` (~150 lines)
- `closeSubscription`
- `fetchSpeakerCatalog`
- `awaitAudioPipelineConfig`
- `onSpeakerActivity` debounce timer
- `onAudioLevel` coalescing
- `applyEffectiveListenMute`'s subscription-touching half
- `setLocalHushed`'s player.setVolume half
- `publishActiveSpeakers`

## What stays in `NestViewModel`

- Public lifecycle (`connect`, `disconnect`, `onCleared`)
- Connection state machine (`ConnectionUiState`)
- Broadcast state (`broadcast` / `_uiState.broadcast`)
- Presence aggregation (kind 10312 events)
- Reactions
- Focus / network observers
- The `NestUiState` composition itself

`NestViewModel` calls `manager.bind(listener)` on each fresh
listener and `manager.unbind()` on disconnect / cleanup.
`updateSpeakers` and mute / hush propagate through to the manager.

## Why deferred

- 1000-line code move across ~10 methods + 5 state fields
- Subtle coupling: catalog readiness affects spinner state, mute
  state has effective + per-speaker flavours, expiry jobs need
  parent scope, `closed` flag is currently a single VM-wide flag
- Test surface: `NestViewModelTest` exercises subscription paths
  through the VM today; would need to either keep that surface or
  add a `NestSubscriptionManagerTest`.

The extraction has a clean contract (callbacks for the bits that
remain VM-side) but landing it without behavioural drift wants a
focused review pass plus its own dedicated test rebuild — bigger
than fits in the current audit-pass.

## When to land

After:
- The catalog-driven decoder reconfig (T3) has run in production
  for long enough that the `awaitAudioPipelineConfig` pattern is
  proven against real publishers.
- Any pending subscription-lifecycle bug fixes (e.g. the audit's
  earlier "boundary-rebuild dangling decoder" path) are settled —
  moving them mid-fix risks introducing regressions.

Track as a follow-up issue rather than a near-term must-do.
