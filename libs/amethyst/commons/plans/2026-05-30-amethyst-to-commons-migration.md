# Amethyst → Commons Migration Plan

> **Status:** queued — the keystone Phase A is untouched: no `Account.kt`/`LocalCache.kt` engine in commons, the amethyst monoliths remain (3757 + 4092 LOC), and `commons/model/account` still holds only the pre-existing `AccountInfo/AccountStorage/SignerType` scaffolding the plan's §0 marks as already-present.
> _Audited 2026-06-30._

**Date:** 2026-05-30 · **Owning module:** `commons` · **Status:** plan (no code moved yet)

Goal: move the **shared** parts of the `amethyst` Android app down into `commons`
so Desktop, the headless `cli`, and future iOS reuse them, leaving `amethyst`
as a thin Android-native shell (Activity, navigation, notifications, platform
services). This is the inverse direction of the `commons` package refactor that
preceded it (see `commons/ARCHITECTURE.md`) — that cleaned up *where* shared
code lives; this plan decides *what* still needs to move there.

## Scope surveyed

`amethyst/src/main` ≈ **1817 Kotlin files**. Coupling signals:

| Signal | Files | Meaning |
|---|---|---|
| `androidx.compose.*` | 1075 | UI |
| `R.string` / `amethyst.R` | 559 | localized strings — see §"Strings" |
| `android.content.Context` | 142 | platform handle to abstract |
| Activity / Service / `android.app` | 40 | clearly stays native |
| `com.fasterxml.jackson` | 6 | purity-gate only matters for commonMain/iOS |

Three structured passes (model/, service/, ui/) produced the matrices below.

---

## 0. What is ALREADY in commons (do NOT re-extract)

The agents that surveyed `amethyst` did not all know commons' current state.
These already exist and the amethyst copies are **dedup / re-point** targets,
not new extractions:

- **Account scaffolding:** `model/IAccount`, `model/account/{AccountInfo,
  AccountStorage,SignerType}`, `state/{FollowState,UserMetadataState,
  LoadingState,EventCollectionState}`, `model/cache/*`. **But the concrete
  `Account.kt` (3565 LOC) and `LocalCache.kt` (3917 LOC) are NOT extracted** —
  only their interfaces/scaffolding are. They are the keystone (§Phase A).
- **Relay layer:** `relayClient/*` (assemblers, eoseManagers, composeSubscription
  managers, preload, subscriptions, nip17Dm) and `relays/*`.
- **Formatters / util:** `util/PubKeyFormatter`, `util/NumberFormatters`,
  `util/PlatformNumberFormatter` (expect/actual). amethyst's
  `ui/note/PubKeyFormatter.kt` is a **duplicate**.
- **Services:** `service/lnurl/{LightningAddressResolver,OkHttpLnurlEndpointResolver}`,
  `service/nwc`, `service/upload`. amethyst's `service/lnurl/LightningAddressResolver.kt`
  is a **duplicate**.
- **Strings:** commons localizes through **Compose Multiplatform Resources**
  (`composeResources/values/strings.xml`, `stringResource(Res.string.x)`),
  already 41 strings. ← This is the migration path for `R.string`, NOT a custom
  `StringProvider`.
- **Image loading:** Coil 3 is already KMP — `coil.compose` in `commonMain`,
  `coil.okhttp` in `jvmAndroid`. Shared composables can call `AsyncImage`
  directly; **image loading is largely a non-blocker**.
- **Misc:** `tor/`, `keystorage/` (interface + actuals), `richtext/`,
  `preview/` (OpenGraph/MetaTags parsers), `feeds/`, `ui/feeds` (feed DAL).

---

## 1. Cross-cutting blockers (the real Phase 0)

Ranked by how much extraction they unblock. Note the corrections vs. the raw
survey (Coil and Strings are *less* of a blocker than assumed; account state is
*more*).

| Blocker | Reality | Resolution |
|---|---|---|
| **Account state** (`Account.kt`, `LocalCache.kt`, `AccountViewModel`) | The dominant blocker. ~53 UI files read it; nearly every ViewModel depends on it. commons has only the interfaces. | Extract `Account`/`LocalCache` business logic to `commons/model/account` + `model/cache`, behind the existing `IAccount`/cache interfaces. Keep Android-only bits (LocationState wiring, `@Stable`) in a thin adapter. **Keystone — §Phase A.** |
| **`R.string`** (559) | Solved pattern already in use. | Migrate needed strings into `commons/.../composeResources/values/strings.xml`; replace `R.string.x` → `Res.string.x`. No new abstraction. Crowdin config already exists at repo root. |
| **`Context`** (142) | Mostly file pick / clipboard / intents / cacheDir. | Small `expect`/`actual` platform-service interfaces (file picker, clipboard, URI open). Most are confined to `ui/actions/uploads` and a few service packages. |
| **`INav`** (nav callbacks) | Already an interface; low friction. | Shared composables take `onNavigate: (route) -> Unit` lambdas, not `INav` directly. |
| **Image loading (Coil)** | Already KMP in commons. | Use commons Coil directly; only the Android `ImageLoaderSetup`/fetchers stay native (or move to `jvmAndroid`). |
| **OkHttp / Jackson** | Allowed in `jvmAndroid`; banned only in `commonMain`/iOS. | HTTP/JSON code → `jvmAndroid`. Only promote to `commonMain` (for iOS) by swapping to Ktor + kotlinx.serialization, and only when iOS needs it. |

---

## 2. model/ — migration matrix

Most of `amethyst/model` is CLI-safe Nostr state and extracts cleanly. Verdicts:
**Extract** (pure) · **Abstract** (needs storage/HTTP/keystore seam) ·
**Keystone** · **Native**.

| Area | Verdict | Destination | Note |
|---|---|---|---|
| `Account.kt`, `LocalCache.kt`, `accountsCache/` | **Keystone** | `model/account`, `model/cache` | 3565+3917 LOC; behind `IAccount`/cache ifaces; LocationState → adapter |
| `AccountSettings`, `AccountSyncedSettings*` | Extract | `model/account` | pure data + event (de)serialization |
| `UiSettings`, `UiSettingsFlow` | Abstract | `model/` | drop `R.string` (use string keys / `Res`) |
| `nip01UserMetadata`, `nip02FollowLists`, `nip17Dms`, `nip30CustomEmojis`, `nip47WalletConnect`, `nip51Lists`, `nip65RelayList`, `nip72Communities`, `nip78AppSpecific`, `nip86RelayManagement`, `nipA3PaymentTargets`, `nipB7Blossom`, `nipBCOnchainZaps`, `trustedAssertions`, `zap` | Extract | `model/nipNN<slug>` (mirror quartz) | pure event-state wrappers; bulk move |
| `topNavFeeds/`, `algoFeeds/`, `serverList/` | Extract | `feeds/`, `model/relayLists` | feed query orchestration is platform-agnostic |
| `edits/` | Extract | `model/nip51Lists` or `model/nip37Drafts` | encrypted list/draft state |
| `preferences/` (DataStore, KeyStoreEncryption, *SharedPreferences) | Abstract | `keystorage/`, `model/preferences` | schemas → commons; persistence actuals stay Android |
| `marmot/` (Android*Store) | Abstract | `keystorage/marmot` + commons logic | KeyStore-backed stores → expect/actual |
| `nip03Timestamp`, `nip11RelayInfo`, `privacyOptions` (OkHttp), `localRelays` | Abstract | `jvmAndroid` (HTTP) + commonMain (logic) | OkHttp → jvmAndroid; ContentResolver stays native |
| `torState/` | Extract | `tor/`, `model/torState` | relay-eval logic is pure |
| `EncryptedStorage`, `LocalPreferences` | Abstract | `keystorage/` | core logic + iface; SharedPrefs impl stays Android |
| `Amethyst.kt` (Application), `AppModules.kt` (DI) | **Native** | — | Android lifecycle / Context assembly |

**Highest value, lowest friction:** the ~16 `nipNN` state packages + `topNavFeeds`
(bulk pure moves), then `AccountSettings`/`AccountSyncedSettings`.

---

## 3. service/ — migration matrix

| Package (files) | Verdict | Destination | Note |
|---|---|---|---|
| `relayClient` (81) | **Native** | — (dedup) | Android orchestration wiring over commons `relayClient`; do NOT duplicate. Lift any LocalCache-free EOSE managers into commons. |
| `playback` (60) | Native | — | ExoPlayer/Media3, PIP — Android-only |
| `notifications` (15) | Native | — | foreground service, NotificationManager |
| `okhttp` (17) | Extract | `jvmAndroid` | OkHttp clients/interceptors/WS bridge; already JVM-shaped |
| `uploads` (25) | Abstract | `jvmAndroid` + commonMain iface | compression/codecs stay Android; orchestration → common |
| `images` (10) | Abstract | `jvmAndroid` (Coil setup) + commonMain (hashes already in commons) | |
| `cashu` (10) | Extract | `commonMain` (e.g. `model/nip60Cashu` or `service/cashu`) | parser is pure; split off Compose `GenericLoadable` |
| `call` (10) | Abstract | commonMain (state) + `jvmAndroid` (AudioManager/WebRTC) | WebRTC hard to abstract |
| `location` (6) | Abstract | commonMain (geohash) + `jvmAndroid` (Geocoder) | |
| `connectivity` (3) | Abstract | commonMain (model) + actual | trivial seam |
| `broadcast` (2), `ai` (2), `previews` (2) | Extract | commonMain (`service/`, `preview`) | previews already wraps commons parsers — consolidate |
| `lnurl` (2) | Extract / **dedup** | `jvmAndroid` `service/lnurl` | `LightningAddressResolver` duplicates commons — reconcile |
| `namecoin` (1) | Abstract | `jvmAndroid` | ElectrumX over OkHttp |
| `relays` (1, EOSE) | Extract / dedup | `relayClient`/`relays` | consolidate EOSE state |
| `eventCache` (1) | Abstract | commonMain | eviction logic pure; trim trigger via iface |
| `scheduledposts` (4), `calendar` (4) | Native (model extractable) | — | WorkManager-bound; model → commonMain if needed |
| `crashreports` (4), `logging` (3) | Native (DTO extractable) | — | Choreographer/Build native |
| `tts` (2), `cast` (2), `nests` (2) | Native | — | TTS / Cast / foreground service |
| root utils (`ByteFormatter`, `CountFormatter`, etc.) | Extract | `util/` | some may already exist — check first |

**Highest value, lowest friction:** `okhttp`→`jvmAndroid`, `cashu`, `broadcast`,
`ai`, `previews` consolidation, `lnurl` dedup, EOSE consolidation.

---

## 4. ui/ — migration matrix

commons rule (from `ARCHITECTURE.md`): cross-cutting composables → `ui/<area>`;
feature-specific → `<feature>/ui`. Deciding test: "could an unrelated feature
reuse this as-is?"

| Area (files) | Shareable | Destination | Blockers |
|---|---|---|---|
| `ui/dal` (5) | already re-exports | — | none — delete shims, point at `ui/feeds` |
| `ui/theme` (5) | ~90%, **dup** | merge into `ui/theme` | system-chrome (status/nav bar) stays Android |
| `ui/feeds` (12) | ~95% | `ui/feeds` | a few `R.string`, AccountVM |
| `ui/layouts` (9) | ~85% | `ui/layouts` | 2× `R.string`, 1× `INav` |
| `ui/components` (59) | ~75% | split `ui/components` (atoms) + `<feature>/ui` (galleries) | ~30 atoms clean; Coil/AccountVM/`R.string` on the rest |
| `ui/note` (184) | ~50–70% | formatters→`ui/text`; avatars→`ui/elements`/`profile/ui`; renderers→`<nipNN>/ui` | AccountVM (29), `R.string` (24), Note model coupling |
| `ui/actions` (41) | ~65% | transforms→commonMain; VMs→`viewmodels`; media→stay Android | Context (7), AccountVM (7), `R.string` (8) |
| `ui/screen` (971) | mostly native | — | app screens; harvest reusable atoms opportunistically |
| `ui/navigation`, `ui/call`, `ui/cast`, `ui/broadcast`, `ui/tor` | native | — | platform wiring |

**Lowest-friction UI wins** (after string migration): TimeAgo/date formatters →
`ui/text`; `ClickableUrl/Email/Phone`; `GenericLoadable` → `ui/state`; input
transforms; `DisappearingBar*` → `ui/layouts`; feed-state composables; `ui/dal`
shim deletion; `ui/theme` consolidation. ~42% of note/components/actions
composables (112/266) carry no `R.string` at all.

---

## 5. Reconciliation / dedup backlog (do early — prevents drift)

1. `service/relayClient` (Android) vs `commons/relayClient` — keep Android as
   wiring only; lift cache-independent EOSE managers down.
2. `service/lnurl/LightningAddressResolver` vs `commons/service/lnurl/…` — one
   canonical copy.
3. `ui/note/PubKeyFormatter` vs `commons/util/PubKeyFormatter` — delete the dup.
4. `ui/theme` vs `commons/ui/theme` — single color/type source.
5. `ui/dal` vs `commons/ui/feeds` — delete re-export shims.
6. `service/relays/EOSE*` scattered — one EOSE-state home.

---

## 6. Phased roadmap

- **Phase A — Account keystone (largest, gates the rest).** Extract `Account` +
  `LocalCache` business logic into `commons/model/account` + `model/cache`
  behind the existing interfaces; amethyst keeps a thin Android adapter
  (LocationState, Compose `@Stable`). Unblocks ViewModels + interactive UI.
- **Phase B — pure model & service extracts (parallel, low risk).** The ~16
  `nipNN` model packages, `topNavFeeds`, `cashu`, `broadcast`, `ai`,
  `previews`, EOSE/lnurl/PubKeyFormatter dedups, `okhttp`→`jvmAndroid`.
- **Phase C — storage/network seams.** `keystorage` expect/actual for prefs +
  KeyStore + marmot stores; HTTP packages (nip11/nip03/namecoin/privacyOptions)
  → `jvmAndroid`.
- **Phase D — string migration + shared UI atoms.** Migrate the needed strings
  into `composeResources`, then extract the low-friction UI list in §4.
- **Phase E — feature UI.** Note type renderers → `<nipNN>/ui`; read-only
  renderers (Badge, Emoji, Poll display) before interactive ones (reactions,
  composers). Much of `ui/note/creators` and reaction/zap rows may stay native
  unless Desktop/iOS need them.
- **Never moves:** Application/DI, notifications, playback, WorkManager jobs,
  TTS, Cast, foreground services, navigation shells, Activity-bound code.

## 7. Suggested first PR (smallest valuable slice)

Phase B subset — zero account dependency, immediately reusable by Desktop:
the dedups (§5.2–5.5) + a bulk move of 4–6 pure `nipNN` model packages
(`nip17Dms`, `nip30CustomEmojis`, `nip47WalletConnect`, `nip65RelayList`,
`trustedAssertions`, `zap`). Mechanical, compile-verifiable, no new abstractions.
Phase A is its own large PR and should be planned separately.

---

## 8. Architecture end-state (why the tree below is shaped this way)

The ports already exist in commons (`IAccount`, `ICacheProvider`,
`ICacheEventStream`) but **every app reimplements them** — `amethyst`
(`object LocalCache` 3917 LOC + `class Account` 3565 LOC), `desktopApp`
(`class DesktopLocalCache` 739 LOC + `DesktopIAccount` 278 LOC), `cli`
(stateless). Crucially these implementations are **platform-agnostic already**
(`Account.kt` has one android import — `@Stable` — plus a single `LocationState`
coupling; `LocalCache.kt` has two; `DesktopLocalCache` has zero). So this is
**accidental forking, not essential divergence.** The end-state is **one
canonical implementation per port, living in commons**, with apps keeping only
thin platform adapters.

Two consequences shape the package tree:

1. **Decompose the monoliths; don't relocate them.** `Account` (28 `StateFlow`s +
   ~170 `suspend fun`s) splits into *state* (per-user reactive state), *actions*
   (the verbs), and a thin *façade* implementing `IAccount`. `LocalCache`
   becomes the cache *engine* as a `class` (so every platform gets multi-account;
   Desktop already needed it).
2. **Litmus test = CLI-constructibility.** `signer → cache → account state →
   actions → relay client → pure services` must build in a headless `main()`
   with no Compose/Android. Anything that can't is mislayered or missing a port.

Layer is the primary axis; NIP is the secondary axis (`nipNN<slug>` matching
quartz). The raw per-NIP list **state** lives in `model/nipNN<slug>`; the
account's *composed/derived* view of those lists lives in `model/account/*`. The
generic `state/` package (load-a-thing `StateFlow` machines) is **not** where
account-specific state goes.

## 9. Target package hierarchy (the destination tree)

`commonMain/.../commons/` (✚ = new package, ⤺ = merge into existing, ✔ = exists):

```
model/
├─ cache/
│   ├─ ICacheProvider.kt  ICacheEventStream.kt            ✔ read ports
│   ├─ ILocalCache.kt                                     ✚ write port (from amethyst)
│   ├─ LocalCache.kt          ← unified cache ENGINE as a CLASS   (amethyst object + DesktopLocalCache collapse here)
│   └─ UserMetadataCache.kt                               ✔
├─ account/
│   ├─ Account.kt             ← façade implementing IAccount       (from amethyst Account)
│   ├─ AccountInfo.kt  AccountStorage.kt  SignerType.kt   ✔
│   ├─ settings/   AccountSettings, AccountSyncedSettings(+Internal)          ✚
│   ├─ follows/    derived per-feed live-follow lists + all-follows merge     ✚
│   ├─ relays/     account relay sets + outbox relay cache                    ✚
│   └─ hidden/     LiveHiddenUsers — mute/spam aggregated view               ✚
├─ nip02FollowList/      ⤺ raw Kind-3 follow-list state      (amethyst nip02FollowLists)
├─ nip65RelayList/       ⤺ relay-list state
├─ nip51Lists/           ⤺ mute / bookmark / people lists
├─ nip30CustomEmojis/    ⤺ emoji-pack state
├─ nip01Core/            ⤺ kind-0 user-metadata relay-hint state  (amethyst nip01UserMetadata)
├─ nip17Dm/  nip47WalletConnect/  nip60Cashu/  nip72ModCommunities/
│  nip78AppData/  nip85TrustedAssertions/  nip86RelayManagement/
│  nipA3PaymentTargets/  nipB7Blossom/  nipBCOnchainZaps/      ✚ per-NIP state holders (bulk)
└─ settings/             ✚ UiSettings / UiSettingsFlow (global, NOT account-scoped)

state/                   ✔ generic StateFlow machines (FollowState, …) — unchanged
actions/                 ✔ the account "verbs" — extend, don't duplicate
│   existing: DmActions  FollowActions  SearchActions  ZapActions  ZapSplitResolver
│   add:      PostActions  ReactionActions  BookmarkActions  MuteActions
│             RelayActions  ProfileActions  GroupActions  DeletionActions …
feeds/
├─ custom/               ✔
├─ topNav/               ✚ (amethyst model/topNavFeeds)
└─ algo/                 ✚ (amethyst model/algoFeeds)
relayClient/             ✔ + cache-free EOSE managers lifted from amethyst service/relays
keystorage/
├─ SecureKeyStorage.kt   ✔
├─ preferences/          ✚ LocalPreferences logic + DataStore/SharedPrefs schema (+actuals)
├─ account/              ✚ account persistence (pairs with model/account/AccountStorage)
└─ marmot/               ✚ MLS key-package / message / group-state store ports (+actuals)
service/
├─ upload/  nwc/  lnurl/ ✔   (lnurl: dedup amethyst LightningAddressResolver)
├─ broadcast/  ai/  connectivity/  eventCache/   ✚ commonMain (pure)
└─ okhttp/  nip11RelayInfo/  nip03Timestamp/  namecoin/  privacyOptions/   ✚ jvmAndroid (OkHttp/Jackson)
tor/                     ✔ + torState relay-evaluation logic
```

UI destinations follow `commons/ARCHITECTURE.md` (cross-cutting → `ui/<area>`,
feature-specific → `<feature>/ui`) and are detailed in §4 — out of scope for the
"common objects" core tier above.

## 10. Package-naming decisions (the rules behind §9)

1. **`model/nipNN<slug>` (raw list state) vs `model/account/*` (composed view).**
   `model/nip02FollowList` holds the raw Kind-3 list; `model/account/follows`
   holds the user's *derived* per-feed follow flows that compose it. Same for
   relays (`nip65RelayList` vs `account/relays`) and mutes (`nip51Lists` vs
   `account/hidden`).
2. **NIP slugs match quartz exactly**, normalizing amethyst's drift:
   `nip02FollowLists→nip02FollowList`, `nip01UserMetadata→nip01Core`,
   `nip30CustomEmojis` (keep — existing commons name), `nip72Communities→
   nip72ModCommunities`, `nip78AppSpecific→nip78AppData`,
   `trustedAssertions→nip85TrustedAssertions` (or keep the existing readable
   `model/trustedAssertions` — pick one and converge).
3. **`state/` stays generic.** Account-specific state never goes here; it goes in
   `model/account/*`. `state/` is reusable load-a-thing `StateFlow` machines.
4. **`actions/` = verbs, `model/` = nouns/state, `cache/` = the store.** The 170
   `Account` methods become `*Actions` files grouped by concern, taking
   `signer + cache + relayClient`.
5. **`keystorage/` = persistence ports + actuals; the settings *schema* is
   `model/account/settings`.** Schema (what) and persistence (how/where) are
   separate packages.
6. **`service/` name is source-set-agnostic.** Pure services sit in `commonMain`,
   OkHttp/Jackson ones in `jvmAndroid` — same `service/<x>` path, different
   source set (per `ARCHITECTURE.md` §"Source sets").
7. **Keep the name `LocalCache`** for the engine class (222 amethyst files / 560
   call-sites reference it); amethyst keeps a thin `object LocalCache` delegating
   to one shared `class` instance during transition.

