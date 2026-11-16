# iOS Support for Amethyst

> **Superseded 2026-10-09** by [commons/plans/2026-10-09-ios-app.md](../../commons/plans/2026-10-09-ios-app.md), which
> plans iOS as a third shim of the One-UI app at feature parity with Android. Kept for its history
> (Phase 1 and the Phase 2 audit).

> **Status:** in-progress — `iosArm64`/`iosSimulatorArm64` targets are configured in `quartz` and `commons` (Phase 1), but no `iosApp` module exists yet — later phases not built.
> _Audited 2026-06-30._

**Date:** 2026-05-24
**Status:** Phase 1 complete; Phase 2 in flight
**Owner:** TBD

Plan to incrementally bring Amethyst to iOS by extending the existing
KMP layers from the bottom up. Each phase is independently shippable —
we can pause between any two phases without leaving the tree in a
broken state.

## Why this is tractable today

The structural work that usually dooms a KMP-to-iOS effort is already
done:

- `quartz/` has `iosArm64` + `iosSimulatorArm64` targets configured,
  a working `Platform.ios.kt` actual, and 6 iOS test files that pass.
- Jackson and OkHttp — the two big JVM-only dependencies — are *already
  isolated to `jvmAndroid`* in quartz (`quartz/src/jvmAndroid/.../jackson/`,
  `quartz/src/jvmAndroid/.../okhttp/`). `commonMain` is JVM-free except
  for the obvious `kotlinx.*` stack.
- `commons/commonMain` has exactly **one** Jackson reference
  (`FeedDefinitionSerializer.kt`) and zero OkHttp references. The rest
  of the JVM stickiness lives in `jvmAndroid` / `jvmMain` / `androidMain`,
  which is where it belongs.
- Compose Multiplatform 1.10.3 is in use, which supports iOS officially.
- `secp256k1-kmp` ships iOS targets. `androidx.collection` (LruCache) and
  `androidx.lifecycle.viewmodel.compose` are KMP since 2.8.

What this means: we are not embarking on a months-long "purify
commonMain" migration before any iOS code can compile. Phase 1 is
mostly **add iOS to CI** and **patch the last few leaks**.

## Module-by-module dep matrix

Status legend:
- ✅ iOS-ready (targets configured, no JVM-only deps in shared code)
- 🟡 Partial (intermediate source sets need adding, but no major dep blockers)
- 🔴 Blocked (significant native work required)
- ⛔ Out of scope (won't ship on iOS)

| Module | Today | Phase 1 | Phase 2 | Phase 3 | Phase 4 | Phase 5 |
|---|---|---|---|---|---|---|
| `quartz/` | ✅ | CI + audit | — | — | — | — |
| `commons/` (non-UI) | 🟡 | — | ✅ | — | — | — |
| `commons/` (UI) | 🟡 | — | — | ✅ | — | — |
| `iosApp/` (new) | n/a | — | — | scaffold | feature-complete | — |
| `quic/` | 🔴 | — | — | — | — | iOS actuals |
| `nestsClient/` | 🔴 | — | — | — | — | iOS actuals |
| `amethyst/` (app) | ⛔ | — | — | — | — | — |
| `desktopApp/` | ⛔ | — | — | — | — | — |
| `cli/` | ⛔ | — | — | — | — | — |

## Source-set diagram (target end state)

```
commons/src/
├── commonMain/              ── all targets
│   ├── coreMain/            ── ViewModels, state, DAL (no Compose)
│   │   ├── jvmAndroidCore/  ── Android + Desktop
│   │   │   ├── androidCore/
│   │   │   └── jvmCore/
│   │   └── nativeCore/      ── iOS
│   │       ├── iosArm64Core/
│   │       └── iosSimArm64Core/
│   └── uiMain/              ── Compose UI, icons, resources
│       ├── jvmAndroidUi/
│       │   ├── androidUi/
│       │   └── jvmUi/
│       └── nativeUi/        ── iOS Compose
```

(Names sketched for clarity; in practice we'll fold `coreMain` /
`uiMain` together once *every* file in `uiMain` compiles for iOS —
the split is a transitional scaffold for Phase 2 ↔ Phase 3.)

`quartz/`, `quic/`, `nestsClient/` already use a `jvmAndroid` shared
source set; we'll add a sibling `nativeMain` (or just `iosMain` where
that's simpler) when each module turns on iOS.

---

## Phase 1 — Lock down Quartz on iOS

**Duration estimate:** 1–2 weeks
**Deliverable:** `./gradlew :quartz:iosSimulatorArm64Test` runs in CI on every PR.

### Tasks

1. **Add iOS to CI for `:quartz`.**
   - GitHub Actions macOS runner step: `iosSimulatorArm64Test` +
     `iosArm64SourceSetTest` (compile only).
   - This is the single most valuable change in the entire plan — it
     prevents anyone from accidentally re-adding a JVM-only import to
     `commonMain`.

2. **Audit the `jvmAndroid` boundary.**
   - Confirm everything Jackson/OkHttp-related lives in `jvmAndroid`
     (it does today — keep it that way).
   - Add a checkstyle / detekt rule, or a simple grep gate in CI, that
     fails the build if `com.fasterxml.jackson` or `okhttp3` shows up
     in `commonMain`.

3. **Validate `secp256k1` iOS path.**
   - Make sure `KeyPair`, `SchnorrSigner`, NIP-44 v2 vectors run green
     on `iosSimulatorArm64Test`.
   - The iOS tests already cover NIP-04 / NIP-17 / NIP-19 / NIP-49 — we
     just need to surface them in CI.

4. **Plan the `expect`/`actual` for iOS HTTP.**
   - Phase 1 only sketches the design; the actual `Ktor-darwin` wiring
     lands in Phase 2 when `:commons` needs it.
   - Decide: Ktor everywhere, vs OkHttp on JVM/Android + Ktor on iOS.
     **Recommendation:** keep OkHttp on JVM/Android (we use OkHttp-specific
     features in relay reconnect logic) and add an iOS-only Ktor actual.

### Risks

- None major. The work here is mostly defensive.

---

## Phase 2 — Bring `:commons` to iOS, non-UI first

**Duration estimate:** 2–3 weeks
**Deliverable:** `./gradlew :commons:iosSimulatorArm64Test` compiles every
shared ViewModel and state class.

### Tasks

1. **Add iOS targets to `commons/build.gradle.kts`.**
   - `iosArm64()` + `iosSimulatorArm64()`.
   - Introduce intermediate source sets `coreMain` (all targets) and
     `uiMain` (JVM + Android only, for now).

2. **Migrate `FeedDefinitionSerializer.kt` off Jackson.**
   - Move to `kotlinx.serialization`, OR
   - Push it down into `jvmAndroidCore` and create a `nativeCore` actual.
     **Recommendation:** migrate. It's one file; one-time cost is small;
     reduces split-actual surface area forever.

3. **Add `expect`/`actual` wrappers for JVM-only deps used by ViewModels.**

   | Concern | JVM/Android | iOS actual |
   |---|---|---|
   | HTTP client | OkHttp | Ktor + `Ktor-darwin` |
   | Secure key storage | Android Keystore / java-keyring | Keychain Services |
   | EXIF strip (image upload) | `commons-imaging` | `ImageIO` (`CGImageSourceCopyPropertiesAtIndex`) |
   | File I/O paths | `java.io.File` | `NSFileManager` / `okio` |
   | Logging | `android.util.Log` / SLF4J | `os_log` via cinterop, or plain `println` to start |

4. **Compile-only iOS for `:commons` ViewModels.**
   - At the end of Phase 2 we have ViewModels, account state, LocalCache
     wrappers, filter assemblers, and `ComposeSubscriptionManager` building
     on iOS — but no UI yet.
   - Smoke test: write a small `commonTest` that constructs an `Account`,
     subscribes to a stub relay, and verifies a follow event lands in
     `LocalCache`. Run it on iOS simulator.

### Risks

- **Ktor migration scope creep.** Hold the line: Phase 2 only wraps HTTP
  behind `expect`. Don't refactor the relay pool. That's a separate PR.
- **Coroutines dispatcher differences.** `Dispatchers.IO` does not exist on
  Kotlin/Native by default — code that explicitly references it needs a
  `KmpDispatchers.IO` shim. Audit before Phase 2 starts.

### Phase 2 audit (2026-05-24): commons/commonMain iOS-blocker inventory

Audit of all 335 .kt files in `commons/src/commonMain/`. Better than feared
— most files are already KMP-clean. The actual blockers are 21 files
across ~6 distinct concerns. Each row below is a small mergeable PR.

**By blocker category:**

| Blocker | Files | Fix |
|---|---|---|
| `java.util.Base64` | 1 (`Base64Image.kt`) | `kotlin.io.encoding.Base64` (stdlib since 1.8) |
| `AtomicLong` / `AtomicInteger` | 2 (`ChessLobbyState.kt`, `SigningState.kt`) | `kotlinx.atomicfu.atomic` |
| `ConcurrentHashMap` | 4 (`ChessRelayFetchHelper.kt`, `ChessEventCollector.kt`, `ComposeSubscriptionManager.kt`, `MutableComposeSubscriptionManager.kt`) | `androidx.collection.MutableScatterMap` (KMP) — synchronization most likely already provided by enclosing scope; audit per file |
| `SortedSet` + `ConcurrentSkipListSet` | 2 (`EventListMatchingFilter.kt`, `NoteListMatchingFilter.kt`) | Switch to `mutableListOf` + sort-on-access, or `androidx.collection.MutableScatterSet` with manual order |
| `WeakReference` | 5 (`Channel.kt`, `Chatroom.kt`, `MarmotGroupChatroom.kt`, `UserRelaysCache.kt`, **+1**) | `expect class KmpWeakReference<T>` actuals: JVM `java.lang.ref.WeakReference`; iOS `kotlin.native.ref.WeakReference` |
| `BigDecimal` | 1 (`Note.kt`) | Either KMP bignum lib (`com.ionspin:bignum`) or move the BigDecimal-using helper to `jvmAndroid` and stub on iOS |
| `java.io.File` | 1 (`MediaContentModels.kt`) | Replace with `String` path, or `okio.Path` |
| `java.net.URI` / `MalformedURLException` | 2 (`RichTextParser.kt`, `UrlInfoItem.kt`) | KMP URL lib (`io.ktor:ktor-http`) or stay JVM via expect/actual `parseUrl()` |
| `java.nio.charset.Charset` | 1 (`HtmlCharsetParser.kt`) | `kotlin.text.Charsets` for UTF-8/16; for arbitrary charsets, expect/actual |
| `:nestsClient` project dep | 2 (`NestViewModel.kt`, `ActiveSubscription.kt`) | Move both files to `jvmAndroid` source set (audio rooms are Phase 5 anyway) |
| `com.halilibo.richtext.*` | 1 (`RenderMarkdown.kt`) | Verify iOS artifact; if missing, move to `jvmAndroid` until Phase 3 markdown decision |

**Files that look scary but aren't:**
- 183 files import `androidx.compose.*` — these all map to JetBrains Compose
  Multiplatform's iOS artifacts (identical package paths). No work needed.
- 7 files import `androidx.lifecycle.*` — KMP since 2.8.0. No work needed.
- 0 files import `coil3.network.okhttp` (Coil network is already isolated).
- 0 files import `javax.*` or `android.*` directly from commonMain.

**Recommended PR order** (ascending cost, descending obviousness):

1. ✅ Phase 1 complete (gates + iOS CI for quartz, Jackson migration).
2. **Base64** (1 file, ~2 LOC change). Demonstrates the pattern.
3. **Atomics** (2 files, atomicfu plugin + ~10 LOC).
4. **ConcurrentHashMap** (4 files; needs concurrency audit per file).
5. **WeakReference** (5 files + 1 new expect/actual).
6. **`:nestsClient` files → jvmAndroid** (2 files; pure source-set move).
7. **`RenderMarkdown.kt` → jvmAndroid OR iOS verification** (1 file; depends on lib check).
8. **URL parsing** (2 files; either ktor-http dep or expect/actual).
9. **Charsets, BigDecimal, File** (3 files; small per-file decisions).
10. After ~9 lands: add iOS targets to `:commons`, expect failures to be down
    to ~zero, run `compileKotlinIosSimulatorArm64` to confirm.
11. Then proceed with the original Phase 2 plan items (Ktor for HTTP,
    SecureKeyStore expect/actual, etc.) for the cross-cutting deps.

---

## Phase 3 — Compose Multiplatform UI on iOS

**Duration estimate:** 3–4 weeks
**Deliverable:** A read-only iOS `.ipa` on TestFlight internal that connects
to relays and renders a feed.

### Tasks

1. **Flip `uiMain` to target = all (including iOS).**
   - Compose Multiplatform 1.10.3 supports iOS. The Material Symbols font
     and other Compose Resources already work cross-platform.

2. **Audit UI deps for iOS.**

   | Dep | Status | Action |
   |---|---|---|
   | `jetbrains.compose.*` (1.10.3) | ✅ | None |
   | `androidx.lifecycle.viewmodel.compose` 2.8+ | ✅ KMP | None |
   | `coil3` | ✅ iOS | Swap network fetcher from `coil-okhttp` to `coil-ktor` on iOS via source-set split |
   | `markdown-ui` / `markdown-ui-material3` | ⚠️ Verify | Likely OK on iOS; if not, fall back to commonmark + custom renderer |
   | `kotlinx-collections-immutable` | ✅ | None |
   | Material Symbols font | ✅ | None (already via Compose Resources) |

3. **Create the `iosApp/` module.**
   - SwiftUI `App` + `UIViewControllerRepresentable` hosting
     `ComposeUIViewController { App() }`.
   - Tab bar (UIKit) for top-level navigation, Compose for each tab's
     content area. **Same split philosophy as Desktop**: native shell,
     shared content.
   - Add Xcode project + Gradle Kotlin/Native framework wiring (no
     CocoaPods; use the JetBrains-recommended `embedAndSignAppleFrameworkForXcode`).

4. **Ship a "read-only Nostr browser" first cut.**
   - Profile view, single-feed home, NoteCard rendering, image loading,
     basic navigation.
   - No posting, no DMs, no audio rooms.
   - This validates the *entire* stack — relay client, LocalCache, feed DAL,
     NoteCard composable, Coil 3, Compose Resources, font rendering — without
     touching signing.

### Risks

- **Compose iOS performance on large feeds.** Profile early with a realistic
  `LocalCache` (10k+ notes) before locking screen architecture. If recomposition
  storms appear, lean harder on `compose-stability-diagnostics` and
  `compose-state-deferred-reads` skills.
- **Touch interactions vs Android conventions.** Pull-to-refresh, swipe
  back, long-press menus all differ on iOS. Some screens may need
  platform-specific gesture handling.
- **Markdown rendering library iOS support.** If `markdown-ui-material3`
  doesn't ship iOS artifacts, this is a half-week detour to switch
  renderers. Verify in week 1 of Phase 3.

---

## Phase 4 — Write paths: signing, posting, settings

**Duration estimate:** 2–3 weeks
**Deliverable:** Fully read/write iOS client, minus audio rooms.

### Tasks

1. **Wire `NostrSignerInternal` to Keychain.**
   - The signer is already KMP — only the key storage actual needs
     adding (done in Phase 2's `SecureKeyStore` abstraction).

2. **Make `NostrSignerRemote` (NIP-46 bunker) work on iOS.**
   - Should be KMP-clean once Ktor migration is done. Audit for any
     stray Jackson / OkHttp inside the NIP-46 path.

3. **NIP-55 alternative.**
   - **There is no Amber on iOS.** Plan replacements:
     - Push users toward NIP-46 bunkers (Amber-as-bunker,
       remote nostr-connect URIs).
     - URL-scheme handoff to native iOS signers (`nos2x-fhe`, `Nostore`)
       *if* they expose a sign API. Track separately.
   - Onboarding screen needs an iOS-specific copy variant.

4. **Posting, reactions, zaps.**
   - Mostly free — ViewModels already in `:commons`. Wire UI buttons and
     test end-to-end on TestFlight.

5. **Settings UI.**
   - Share via Compose. iOS-native preference screens are a polish item
     for later.

### Risks

- **Apple App Review on cryptocurrency / zaps.** Lightning zaps via LNURL
  are fine (no in-app crypto purchase). Anything that looks like an
  in-app wallet or onchain send may need legal review and / or feature
  gating per-region. Start review conversations early.
- **Push notifications.** APNs is the only path on iOS. Nostr DM push
  relays don't speak APNs natively. Likely needs a small relay-proxy
  (similar to `notify.damus.io`'s architecture). Design doc in
  `amethyst/plans/` before Phase 4 ends.

---

## Phase 5 — `:quic` + `:nestsClient` for audio rooms (optional)

**Duration estimate:** 4–6 weeks
**Status:** Defer until 1–4 are solid. App is shippable on iOS without
audio rooms.

### Tasks

1. **`:quic` — add iOS actuals.**
   - UDP socket via `Network.framework` (`NWConnection` with `.udp`).
   - AEAD (AES-GCM, ChaCha20-Poly1305) via Apple CryptoKit
     (`AES.GCM.SealedBox`, `ChaChaPoly`).
   - TLS state machine is already pure Kotlin in `commonMain` — no change.

2. **`:nestsClient` — add iOS actuals.**
   - Opus encode/decode: `libopus` via cinterop, or pull `opus.framework`
     from a Swift Package / CocoaPods spec.
   - Mic + speaker: `AVAudioEngine` (input/output nodes) instead of
     `AudioRecord` / `AudioTrack`.

3. **moq-lite listener path first** (the production path per CLAUDE.md),
   then speaker.

### Risks

- **Background audio on iOS.** Audio rooms in the background need a
  proper `AVAudioSession` category + the `audio` background mode in
  `Info.plist`. Apple sometimes rejects apps that abuse this. Worth a
  separate audit before submission.
- **Opus framework distribution.** `libopus` via Swift Package is
  cleanest; CocoaPods is fine but pulls in a build-time dep on Ruby.
  Decide before Phase 5 starts.

---

## Phase 6 — Ship polish (ongoing, post-Phase 4)

- App Store metadata, screenshots, privacy manifest
  (`NSPrivacyAccessedAPI*` declarations — file access, user defaults).
- Localizations carry over automatically via Compose Resources.
- Background fetch limits — iOS is far stricter than Android. Tune
  feed prefetch + relay reconnect for background launch budgets.
- TestFlight beta → public release.

---

## Cross-cutting risks (track from day one)

| Risk | Mitigation | First chance to catch |
|---|---|---|
| `commonMain` regresses with a JVM-only import | Add iOS to CI on every PR | Phase 1, task 1 |
| Coroutines `Dispatchers.IO` ergonomics on iOS | Audit + introduce `KmpDispatchers` shim | Phase 2, task 3 |
| Compose iOS performance on big feeds | Early profiling with realistic `LocalCache` | Phase 3 risk section |
| App Store review (zaps, onchain) | Talk to legal / read App Store guidelines early | Phase 4 risk section |
| No NIP-55 equivalent on iOS | Lean on NIP-46; document in onboarding | Phase 4, task 3 |
| Push notifications via APNs | Relay-proxy design doc | Phase 4, end of phase |
| Background audio policy | `AVAudioSession` audit + `Info.plist` review | Phase 5 risk section |

## Suggested first PR

Smallest useful start: **Phase 1, tasks 1 + 2** — add `:quartz` iOS to
CI and add the import-gate that prevents Jackson / OkHttp regressions
in `commonMain`. That single PR de-risks the rest of the plan without
touching any product code.

## Open questions

- Do we want a `:cli` analogue on iOS (a "headless" Nostr daemon)? Out
  of scope for this plan, but iosArm64 *could* host one if we ever need
  a CLI-on-phone story.
- Mac Catalyst vs native macOS: Desktop is already JVM-Compose. We
  could theoretically also ship Catalyst from the iOS build, but that's
  three "desktop"-ish targets to maintain. Recommendation: punt.
- iPad layout: do we want a separate split-view UI like `desktopApp`,
  or just scale up the iPhone layout? Phase 3 keeps the iPhone layout;
  iPad polish is a Phase 6 item.
