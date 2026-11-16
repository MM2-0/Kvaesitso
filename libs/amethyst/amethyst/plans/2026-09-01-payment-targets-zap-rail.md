# NIP-A3 Payment Targets in the zap picker — v1

**Status:** proposal
**Modules:** `quartz`, `commons`, `amethyst`
**Scope:** when a note's author publishes a NIP-A3 payment target, **an
installed app can handle it**, and the note carries no NIP-57 zap split — show
one amount-less chip per such target that hands off to that app.

> **Revised after the first implementation.** This document originally gated the
> chip on *symmetry* — both parties publishing the same protocol — and capped the
> row at two chips. Both are gone: the gate is capability alone (can anything on
> this phone open the URI), there is no cap, and the setting now defaults **on**.
> Sections below that argue for symmetry are kept for the reasoning, but §5 is
> the current rule.

Deliberately excluded from v1: amounts, in-app payment, receipts, fiat
conversion, desktop.

---

## 1. Why v1 has no amounts

Zap presets are **sats**. A `venmo` / `iban` / `upi` chip cannot send 1000
sats, and there is **no FX or bitcoin-price service anywhere in this repo**
(grepped `quartz`, `commons`, `amethyst`). So v1 does not pretend: the chip
carries no number, emits no RFC-8905 `amount=`, and the amount is named in the
external app. The UI has to *say* that rather than leave a suspicious blank —
see §4.

Corollary: **the note's zap counter will not move.** No kind:9735, nothing to
count. In code it is a rail; to the user it must read as *pay*, not *zap*.

---

## 2. The layout decision — and the refactor it deletes

> This is the one place v1 diverges from the sketch, and the reason is that it
> makes the change roughly half the size.

The sketch was "add the icons to the toggle." The toggle is the segmented
control **inside each amount pill** (`UnifiedZapAmountChip`,
`ReactionsRow.kt:2362`). Putting an amount-less rail there has two costs:

1. **It repeats.** With presets of 1000/5000/10000, the identical amount-less
   Venmo segment renders three times and means the same thing each time.
2. **It forces `ZapRail` to become a sealed interface.** The enum
   (`ReactionsRow.kt:2481`) is payload-free, so a segment can't know *which*
   target it opens. Making it data-carrying drags in `present`, `preferred`,
   `selectedRail`, `ZapRailIcon`, `previewPreferredRail`, `previewRailsFor`
   and the settings preview row — and, because `PaymentTarget` has no
   `equals`, breaks the `remember(preferred, present)` key so the user's
   selection resets on recompose.

**Instead: render the chip as a sibling of the amount pills**, appended to the
existing `FlowRow` in `ZapAmountChoiceGrid` (`ReactionsRow.kt:2297`), next to
the `Tune` preset-editor button. It wraps for free, it renders **once**, and
`ZapRail`, `UnifiedZapAmountChip` and every preview stay **completely
untouched**. Same popup, same place the user is already looking.

If an FX service ever lands and the amount becomes expressible, the chip moves
into the toggle then — that is the natural migration, not a reason to pay for
it now.

---

## 3. Intent discovery — the constraint that decides it

`targetSdk = 37`. Under Android 11+ package visibility,
`queryIntentActivities` returns **empty** for any intent not covered by a
`<queries>` declaration — so *without a manifest change this feature silently
shows nothing on every modern device*. The existing `<queries>` block
(`AndroidManifest.xml:4`) covers only `nostrsigner`, TTS, Health Connect and
Tor.

### 3.1 Manifest

Add one `<intent>` per scheme we probe. The important economy: an arbitrary
user-typed type (`iban`, `upi`, `pix`, …) always falls back to
`payto://<type>/<authority>`, so **one `payto` entry covers every generic
type**. Only the ~12 special-cased crypto schemes in `paymentTargetStyleFor`
(`DisplayPaymentTargets.kt:190`) need their own entries.

```xml
<intent>
    <action android:name="android.intent.action.VIEW" />
    <data android:scheme="payto" />
</intent>
<!-- + one each: bitcoin, lightning, liquidnetwork, ethereum, monero, dash,
     zcash, bitcoincash, litecoin, dogecoin, solana, tron -->
```

Use **`<queries>`, never `QUERY_ALL_PACKAGES`** — the latter is a
policy-restricted permission on Play and would need a declaration; specific
`<intent>` filters need nothing. On minSdk 26–29 `<queries>` is ignored and
everything resolves, which is a strict superset of the gated behaviour.

### 3.2 https targets are exempt

`cashapp` / `venmo` / `paypal` map to `https://…`, which a browser always
resolves — discovery would be a tautology. **Skip discovery for https
targets and always show them**: opening `venmo.com/<handle>` in a browser is a
legitimate way to pay, so nothing is broken. For these three types the chip is
therefore gated only on the author having published one.

**But §4.2 still needs the control probe here.** To tell a real app handler
from a browser, resolve a control `https://<nonexistent-host>/` and treat the
target as app-backed only if its resolver set contains a package outside that
control set. It never gates the chip — it decides whether the chip wears the
app's icon or the brand-colour glyph, and a Chrome icon on a Venmo chip is
worse than no icon at all.

### 3.3 The cache — keyed by scheme+host, warmed from the open picker

The naive cache is per-post and lazy. With symmetry gone the probe set is the
author's target list, so:

> **Probe the targets of the one author whose picker is open** — typically 1–5
> entries — and **merge** the answers into the cache. Merging matters: replacing
> would evict what was learned about every other author the moment a second
> picker opened. Feed rendering still never triggers a probe.

- **Key:** `"<scheme>://<host>"`, e.g. `payto://iban`, `bitcoin://`. Scheme
  alone is too coarse — an app may declare `android:scheme="payto"
  android:host="iban"`, so a scheme-only hit would wrongly claim `payto://upi`
  is handled.
- **Warm:** a `LaunchedEffect` keyed on the author's observed kind:10133 probes
  that handful of keys off the main thread when the picker opens.
- **Read:** synchronous map lookup — required, because
  `RailCapabilityResolver.peek` is called from inside `remember {}`.
- **Recomposition:** the map must be a `MutableStateFlow<Map<String, Boolean>>`,
  not a bare `ConcurrentHashMap`. A plain map write is invisible to Compose and
  the chip would not appear until something else recomposed.
- **Invalidation:** clear on app foreground (`ProcessLifecycleOwner`
  `ON_START`) and re-warm — this is exactly the "user left, installed Venmo,
  came back" flow. A `PACKAGE_ADDED`/`REMOVED` receiver is more precise but is
  more moving parts than v1 needs.

Home: `amethyst/…/service/payments/PayToAppAvailability.kt` (Android-only;
`PackageManager` has no KMP equivalent). The scheme mapping it needs moves out
of the UI file into `commons` (§6.0).

---

## 4. The chip's face

### 4.1 Saying "the app decides the amount"

An amount-less chip beside pills that all show numbers reads as a bug unless
it is visibly a *different kind of thing*. Three cues, no extra layout:

1. **No number.** Icon + protocol label only (`VENMO`).
2. **A different terminal glyph.** `MaterialSymbols.OpenInNew` instead of the
   `ArrowForward` every amount segment uses — "this leaves the app."
3. **A string that says it outright**, e.g. *"Amount set in %1$s"*, shown as
   the chip's `contentDescription` and as a toast on long-press.

**Both glyphs are already in `MaterialSymbols.kt`** (`OpenInNew:280`,
`AccountBalanceWallet:27`) — **no `tools/material-symbols-subset/subset.sh`
run is needed**, and §4.2 adds no new glyphs either.

Long-press must **not** inherit `onChangeAmount` (the sat-preset editor is
meaningless here); it copies the authority, matching `PaymentTargetChip`'s
long-press on the profile.

### 4.2 Which icon it wears — the installed app's, not a bundled logo

**This already works in this codebase.** `ExternalSignerButton.kt:118` renders
installed NIP-55 signers with `it.loadLabel(pm)` / `it.loadIcon(pm)` →
`toBitmap()` → Coil's `rememberAsyncImagePainter`, off the back of
`getExternalSignersInstalled` (`quartz/…/IsExternalSignerInstalled.kt`), which
is `queryIntentActivities(ACTION_VIEW, "nostrsigner:")` — **the same call
§3 already makes for discovery.** The `ResolveInfo` we keep to answer "can
anything open this?" also carries the icon and the app's own name. The icon is
therefore very close to free; what it costs is care.

**Do not bundle brand logos.** Three reasons, in order of weight:

1. **Trademark, not licence.** `CLAUDE.md`'s dependency gate covers *code*
   licences; a Venmo or PayPal mark shipped inside an MIT APK is a separate
   trademark question. Referential use is usually permitted, redistribution of
   the mark often is not. That is a maintainer's call, not a silent one.
2. **The type space is unbounded.** `PaymentTargetsViewModel.addTarget` accepts
   any `type.trim().lowercase()`, so a bundled set can never be complete —
   `pix`, `upi`, `swish`, `interac` and the next one all miss.
3. **The codebase already decided this.** `paymentTargetStyleFor` pairs brand
   *colours* (`VENMO_BLUE #008CFF`, `PAYPAL_DEEP_BLUE #003087`,
   `CASHAPP_LIME #00E64D`) with the generic `AccountBalanceWallet` glyph.
   Brand colour + generic glyph is the established pattern; keep it as the
   fallback. Brand marks are also absent from Material Symbols, so each would
   be a hand-authored `ImageVector` like `CustomHashTagIcons.Cashu`.

So: **the installed app's icon *is* the brand icon**, sourced from the device
instead of shipped. It is self-limiting in the right direction — the "popular
options" are exactly the ones with an app installed.

**Four things the precedent gets away with and we would not:**

- **Load once, in the warm step.** `ExternalSignerButton` calls `loadIcon()` +
  `toBitmap()` inside a `LazyColumn` item, so it re-runs on recomposition —
  tolerable in a one-shot dialog, not in the zap popup. `loadIcon` reads the
  target APK's resources, so it is I/O: do it in §3.3's off-main warm and
  cache the **`ImageBitmap`**, never the `Drawable`.
- **Size and mask it.** minSdk is 26, so any icon may be an
  `AdaptiveIconDrawable`: a 108×108 canvas whose outer margin the launcher
  masks away. A bare `toBitmap()` drawn at 18dp shows a small logo floating in
  padding. Use `toBitmap(px, px)` at the target size plus
  `Modifier.clip(CircleShape)` — what a launcher does. The precedent renders
  at 48dp and gets away with it.
- **Pick one app, or none.** `payto://` can resolve to several. Ask
  `resolveActivity(intent, MATCH_DEFAULT_ONLY)` for the user's default; when
  Android hands back its `ResolverActivity` (no default set) there is no app
  to name — fall back to the glyph rather than showing the chooser's icon.
- **Accept that it cannot be tinted.** Every other rail is a monochrome glyph
  tinted `BitcoinOrange` / `onSurface`. A full-colour raster can't join that
  scheme — which is arguably the point: it is the visual signal that this
  segment leaves the app. It needs the circular clip and a slightly smaller
  optical size to sit beside 18dp glyphs.

**This promotes the https control-probe from a nicety to v1 work.** §3.2 exempts
`venmo` / `paypal` / `cashapp` from discovery because a browser always resolves
`https://`. That is fine for *gating*, but not for *icons*: with only a browser
installed, `resolveActivity` returns **Chrome**, and a Chrome icon on a Venmo
chip is worse than no icon. So an https target needs the control probe
(resolve `https://<nonexistent-host>/`, treat the target as app-backed only if
its resolver set contains a package outside that control set) to decide
**icon vs brand-colour glyph**, even though it never gates the chip.

---

## 5. Gates (all must hold)

1. Setting `showPayToZapChip` — **default on**. The chip only ever shows a
   target its author chose to publish, to a device that can already open it,
   so the discovery gate is doing the real narrowing (`UiSettings.kt:67` →
   `UiSettingsFlow.kt:59` → `UISharedPreferences.kt:192`).
2. Note has **no** zap split: `zapSplitSetup().isNullOrEmpty()`. payto can't
   fan out and returns no receipt. `RailCapabilityResolver.peek` **already
   computes `splits`** — one-line reuse.
3. Recipient (note author) publishes ≥1 handoff-class target.
4. §3 says an app can handle it (or it's https). **This is the substantive
   gate**; everything else is a precondition.

No cap: every openable target is offered. Discovery is what bounds the row —
a target with nothing to open it never reaches the picker.

**Handoff-class** excludes the wallet-covered types — `lightning`/`ln`/`lnurl`
and `bitcoin`/`btc`/`onchain` *are* the existing LIGHTNING and ONCHAIN rails.
Without this exclusion the picker grows a second Bolt icon beside the first.

---

## 6. Implementation

### 6.0 Prep — no behaviour change
- `quartz`: `PaymentTarget` → `data class` (it has no `equals` today; needed
  for list keys and dedupe, and it fixes the hand-rolled field-by-field
  compare in `PaymentTargetsViewModel.addTarget`).
- `commons/…/model/payments/PaymentTargetTypes.kt` (package exists, holds
  `PaymentSourceResolver`): `canonical(raw)`, `isWalletCovered(canonical)`,
  `schemeFor(canonical)`. Move `LIGHTNING_TARGET_TYPES` /
  `BITCOIN_TARGET_TYPES` (`DisplayPaymentTargets.kt:67,70`) and the scheme half
  of `paymentTargetStyleFor` here — today they are duplicated twice inside one
  Android UI file, and discovery needs them too.
- `commons/…/model/User.kt`: `paymentTargetsNote` + `paymentTargets()`,
  mirroring `nutzapInfoNote` (`User.kt:79`).

**No new relay subscription:** kind 10133 already rides in
`UserMetadataForKeyKinds` beside kind:0 and kind:10019
(`FilterUserMetadataForKey.kt:50`), so the recipient's targets are in cache by
the time the note renders — same as the nutzap rail.

### 6.1 Matcher — pure, headless
`commons/…/model/payments/PayToRailMatcher.kt`: canonicalize both sides, drop
wallet-covered types, intersect on type, dedupe by type. No Android, no
Compose.

### 6.2 Discovery
`amethyst/…/service/payments/PayToAppAvailability.kt` per §3.3 + the manifest
`<queries>` entries per §3.1. Each cache entry holds what §4.2 needs as well as
the yes/no: `{ resolves: Boolean, label: String?, icon: ImageBitmap? }` —
decoded once in the warm step at the 18dp target size, never per composition.
Icon and label are null for the no-default (`ResolverActivity`) and
browser-only cases, and the chip falls back to the brand-colour glyph.

### 6.3 Capability
- `RailCapability` += `payToTargets: List<PaymentTarget> = emptyList()` —
  defaulted, so `RailCapabilityCashuStatusTest` and every existing call site
  compile untouched.
- `peek(..., senderTargets = emptyList(), payToEnabled = false, available = emptyMap())`
  — **defaulted, because `zapClick` also calls `peek`**
  (`ReactionsRow.kt:1464`) for the one-tap fast path, which must stay
  Lightning-only. Returns empty when splits exist.
- `observeZapRailCapability` (`ReactionsRow.kt:2098`) adds four inputs, each
  both a subscription trigger and a `remember` key — the contract spelled out
  in the "do NOT delete these as unused" comment at `ReactionsRow.kt:2105`:
  `paymentTargetsState.flow`, the author's `paymentTargetsNote`,
  `uiSettingsFlow.showPayToZapRail`, and the availability `StateFlow`.

### 6.4 UI
One new `PayToHandoffChip` composable appended to `ZapAmountChoiceGrid`'s
`FlowRow`. Action: `uriHandler.openUri(...)`; keep the existing try/catch →
`no_payment_app_found_for_type` toast (string exists) as a belt-and-braces
fallback for the race where the app is uninstalled between warm and tap. It
must not touch `zappingProgress`, `zapStartingTime` or `accountViewModel.zap`.

### 6.5 Settings + strings
`showPayToZapRail` through the `showOnchainWallet` chain + `SettingsCatalogBuilder`;
new strings; changelog.

---

## 7. Tests

| Level | Test | Asserts |
|---|---|---|
| `commons/commonTest` | `PaymentTargetTypesTest` | alias collapse, case/whitespace, wallet-covered set, scheme mapping |
| `commons/commonTest` | `PayToRailMatcherTest` | empty sender → empty; no overlap → empty; `ln` vs `lightning` → empty (wallet-covered); `Venmo` vs `venmo` → match; dedupe by type |
| `amethyst/test` | sibling of `RailCapabilityCashuStatusTest` | split present → empty; setting off → empty; no author → empty; unavailable scheme → empty; https target → shown without probe; **existing rails unaffected** |
| `amethyst/test` | `PayToAppAvailabilityTest` | key is scheme+host, not scheme; probe count == sender's target count, independent of post count; `ResolverActivity` default → null icon; browser-only https → null icon (control probe) |
| Manual | | chip appears once (not per pill); tap opens the app; **counter does not move**; split note shows no chip; install app → background → foreground → chip appears; adaptive icon is masked round, not floating in padding; https target with no app shows the glyph, not Chrome |

---

## 8. Open decisions

1. **Chip placement** — sibling vs inside the toggle (§2). Recommend sibling:
   renders once and deletes the whole `ZapRail` refactor. Flagged because it
   diverges from the original sketch.
2. **Default for `showPayToZapRail`** — recommend **off**, matching how
   `ReactionRowAction.Pay` already ships disabled.
3. **Private rumors** — on-chain is suppressed there (it would e-tag the
   rumor). A payto handoff publishes nothing, so it is arguably safe.
   Recommend **allow**, noting the divergence from the on-chain precedent.
4. **`ReactionRowAction.Pay` overlap** — recommend keeping both, `Pay`
   disabled by default: `Pay` browses *all* of a recipient's targets, this
   chip is the *matched, installed, splitless* shortcut.
5. **Colour icon beside monochrome glyphs** (§4.2). The app icon can't be
   tinted, so the chip will be the one full-colour thing in the popup.
   Recommend **accepting** it as the "this leaves the app" signal — but it is a
   visible break from the rail iconography and worth an explicit yes.
6. ~~**Symmetry heuristic**~~ — *removed; see the note at the top.* It was
   right for closed loops (Venmo, Cash App, UPI),
   arguably too strict for open ones (Monero: a sender needs a wallet, not a
   published address). Ship strict; relaxing later is additive. Note that
   intent discovery already covers much of what symmetry was proxying for, so
   dropping symmetry for scheme-based types is a live option.
