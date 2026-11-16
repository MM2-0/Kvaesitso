---
title: "refactor(commons): extract note-type rendering to commons"
type: refactor
status: in-progress
date: 2026-07-16
owner: commons
consumers: amethyst, desktopApp, cli
---

# refactor(commons): extract `ui.note` rendering into commons

> **Status:** in progress. The Tier 0 sweep plus the first clean Tier 1
> renderers have landed on `claude/event-renderers-commons-7pttym` (13 event
> kinds), along with the two enabling infrastructure pieces the plan did not
> originally scope — commons i18n and app-side `Res` access. See
> **§0.1 Progress & findings** for what's built and what the extraction
> actually required in practice. The design below (§1–§8) still holds; §0.1 is
> the amendment. _Authored 2026-07-16; progress appended 2026-07-17._

## 0.1 Progress & findings (2026-07-17)

**Landed (each: `:commons` + `:amethyst` compile, spotless, static analysis,
full pre-push test suite, pushed):**

| Batch | Event kind(s) → commons | Seam mechanic proven |
|-------|-------------------------|----------------------|
| 1 | CodeSnippet, EcashMint | pure package move + shared `NoteBorders` theme |
| 2 | GitStatusPill | Render→Display split (entry reads `GitStatusIndex`) |
| 3 | GitDiffView | plural i18n migration |
| 4 | Birdstar Birdex / detection | commons `ClickableUrl` reuse |
| 5 | PS1 memory-card save | **opaque `@Composable` slot** for an Android-`Bitmap` icon |
| 6 | Podcast badge / link / soundbite atoms | `internal`→public atom extraction + consumer re-point |
| 7 | ActivityCard building blocks | string-free slot atoms + shared `Sizes` theme |
| 8 | Roadstr road-event | **typed slot** (`RoadEventMap`) for the native map |
| 9 | PodcastValueSplits | forced one-string duplication (Android-int toast) |
| 10 | *(infra)* gate #1 | amethyst reads commons `Res` — de-dups shared strings |
| 11 | RelayDiscovery | first shared-string renderer via gate #1 |
| 12 | NIP-52 calendar collection + RSVP | shared string/plural across 3 native call sites |

Shared theme now in commons: `NoteBorders.kt` (`QuoteBorder`, `SmallBorder`,
`StdHorzSpacer`, `StdVertSpacer`, `subtleBorder`, `replyModifier`), `Sizes.kt`
(`Size5dp`, `Size16Modifier`), and `ThemeExtensions.kt` additions (`grayText`,
`allGoodColor`, `warningColor`). These mirror the Android `ui/theme` values;
the Android copies become dead code as the last consumer of each moves.

**Finding A — i18n was an unscoped prerequisite, not a mechanical string swap.**
§3c framed strings as "migrate + swap `R.string`→`Res.string`". In reality
commons had **no translation pipeline** — `crowdin.yml` synced only the Android
app — so a naive migration would drop every translation and remove the key from
future syncs. Fixed once, in batch 2: `crowdin.yml` now lists
`commons/composeResources` as a second Android source with the same
language mapping, and each migrated key carries all 56 locale files across via
`scratchpad/migrate_strings.py` (handles `<string>` and `<plurals>`, deletes the
app copy). No locale regresses. **Do this migration for every string-bearing
renderer; it is the single biggest mechanical cost, exactly as §3c warned, plus
the pipeline wiring §3c missed.**

**Finding B — gate #1: app-side `Res` access is the real unblock for Tier 1.**
Most Tier 1 renderers share strings with a still-native screen
(RelayDiscovery↔RelayInformationScreen, the calendar renderers↔detail/list
screens). Until batch 10, amethyst could not reference commons' generated `Res`
(the compose-resources runtime was not on its classpath, even though commons
already sets `publicResClass = true`). That forced string *duplication*. Batch
10 adds `libs.jetbrains.compose.components.resources` to `amethyst` — now a
native screen switches its `stringResource(R.string.x)` →
`stringResource(Res.string.x)` in the same commit, so the key lives in exactly
one place. **The one exception that still duplicates: a string consumed by an
Android-int API with no Compose form — e.g. `ResourceToastMsg(titleResId: Int)`
in the V4V editor keeps `podcast_value_for_value` app-side.**

**Finding C — "unused seam" is the cleanest Tier 1 signal.** Several renderers
*declare* `accountViewModel`/`nav` for dispatcher uniformity but never call
them (NIP90Status, RelayDiscovery, the calendar pair, Thread, …). These are
pure value-in: move the body to a commons `XxxCard(event)`, keep the entry with
its original signature. Grep for renderers that declare the seam but never
dot-call it — they need no callback design at all.

**Finding D — commonMain bans Jackson.** A renderer backed by a Jackson model
(MedicalData → `MiniFhir.kt`) cannot move to `commonMain` as-is; the build
rule routes JVM-only Jackson away from common code. Either rewrite the model on
kotlinx.serialization first or leave the renderer app-side. Not attempted.

**Finding E — platform leaves become slots, and it works cleanly.** The plan's
slot idea (§3e/§4) held up for real Android-only leaves: PS1's animated
`Bitmap` icon (opaque `(@Composable () -> Unit)?` slot) and Roadstr's tile map
(a *typed* `RoadEventMap` slot carrying lat/lon/color). The commons card owns
all layout + logic; the entry supplies the one native composable.

**Still ahead (unchanged from §5–§6):** the remaining Tier 1 / Tier 2 renderers
are gated on **Phase 0 (§3f), the rich-text restructure** — the load-bearing
PR that lets `Display*` bodies call the rich-text core directly instead of
routing through the native `TranslatableRichTextViewer`. Tier 3 (signer/dialog
flows) stays app-side per §7. The two trivial leftovers (NIP90Status = one
`Text`; Thread) are not worth their own batch.

> **Original study header (2026-07-16):** review / proposal, no code moved yet;
> the per-event generalization for pulling the *rendering* half of
> `com.vitorpamplona.amethyst.ui.note` into `commons`, leaving the
> nav/AccountViewModel-bound *entry* composables in the Android app.

## 0. Goal (as requested)

Leave the **entry composable** — the one that takes `AccountViewModel` + `INav`
and decodes the `Note`/`Event` — in the mobile app. Move the **actual
rendering** (the Compose layout, specific functions, and their local UI state)
into `commons`, where it takes plain values, immutable state objects, and
**lambda callbacks** instead of `AccountViewModel`/`INav`. The Android package
then shrinks to a thin adapter that wires the lambdas.

This is deliberately narrower than the two standing plans and complementary to
both:

- `commons/plans/2026-04-21-event-renderer.md` — a UI-agnostic
  `RenderedEvent` data model. Ambitious, still "proposed", nothing built.
- `commons/plans/2026-05-30-amethyst-to-commons-migration.md` — the full app
  migration; its §4 puts `ui/note` (184 files, ~50–70% shareable) behind the
  **Account keystone (Phase A)**, which is untouched.

The key insight of *this* plan: **we do not need the Account keystone to start.**
The `Render → Display` split lets each event's layout move now, because the
`Display` half never touches `AccountViewModel` — the `Render` half (staying in
`amethyst`) does the account/relay work and hands down primitives + callbacks.

## 1. What the package looks like today

`amethyst/ui/note`: **214 files, ~48.7k LOC**.
- `types/` — **89 files, ~21.9k LOC**: one `RenderXxx` composable per Nostr
  event kind (the dispatch targets).
- top level — 39 files, ~14.7k LOC: `NoteCompose.kt` (2180 LOC, the central
  dispatcher), avatars, reaction/zap rows, dialogs, formatters.
- `creators/` (post editor), `elements/`, `buttons/`, `nip22Comments/`,
  `share/` — mostly interactive, largely stays native (see §7).

### The dispatcher stays native

`NoteCompose.kt` → `RenderNoteRow()` is a giant `when (baseNote.event)` that
routes each kind to its `RenderXxx(...)`. This is the natural home of the
**entry** layer and should *not* move — it is inherently coupled to
`AccountViewModel`, `INav`, feed state, the quick-action menu, drag/swipe, and
report/block gating.

### A two-tier pattern is already emerging

Several types already split `RenderXxx(note, …)` (decodes the event) from a
`DisplayXxx(…decomposed params…)` (renders). `Highlight.kt` is the clearest:
`RenderHighlight(note, …)` → `DisplayHighlight(comment, highlight, context,
authorHex, url, postAddress, …)`. **But `DisplayHighlight` still takes
`accountViewModel` + `nav`.** The work of this plan is to push that boundary the
last mile so the `Display*` layer takes callbacks/state instead — then relocate
it to `commons`.

### The target already has a reference implementation

`commons/ui/note/StaticWebsiteCard.kt` is exactly the end-state shape: pure
value params, lambda slots (`onOpen: (() -> Unit)?`, `headerActions:
@Composable (() -> Unit)?`), commons `Res.string`, commons icons, Coil
`AsyncImage` — **zero `AccountViewModel`/`INav`**. Also already extracted into
`commons/ui/note/`: `CollapsedSpamNote`, `HeaderPill`, `QuietMark`,
`ReplyContext`, `ReplyToLabel`. Do not re-propose these — copy their convention.

## 2. The canonical signature (the generalization anchor)

Across `types/`, the entry composable is remarkably uniform. Parameter frequency
(public `@Composable` entry fns):

```
accountViewModel 198   note/baseNote 152   backgroundColor 71
nav 157             quotesLeft 41       makeItShort 38   canPreview 35
```

So the near-universal entry shape is:

```kotlin
@Composable
fun RenderXxx(
    note: Note,
    quotesLeft: Int,               // recursion budget for embedded quotes
    backgroundColor: MutableState<Color>,
    makeItShort: Boolean,          // compact feed vs full thread view
    canPreview: Boolean,           // expand link/media previews inline
    accountViewModel: AccountViewModel,
    nav: INav,
)
```

`backgroundColor`, `quotesLeft`, `makeItShort`, `canPreview` are **pure display
inputs** and travel straight into `commons` unchanged. Only `accountViewModel`
and `nav` need a seam.

## 3. The dependency surface to cross

### 3a. `AccountViewModel` — smaller behavioral surface than it looks

`accountViewModel` appears 198× in `types/`, but most are **pass-through** to
leaf composables, not direct calls. The actual method surface across the whole
`note` package, grouped:

- **Lookups** (resolve a hex/address to a `Note`/`User`): `getNoteIfExists`,
  `getOrCreateAddressableNote`, `getUserIfExists`, `checkGetOrCreateUser`,
  `loadParticipants`, `loadUsers`, `userProfile`. → **read through the commons
  cache port, not a lambda** (see §3d). Async participant/user loads that need
  relay round-trips still resolve on the entry side.
- **Config/settings**: `settings`, `zapAmountChoices`, `showSensitiveContent`,
  `httpClientBuilder`, `nip`. → passed as plain values / a small settings holder.
- **Actions (write + signer)**: `zap`, `follow`/`unfollow`, `delete`, `hide`,
  `reactToOrDelete`, `decrypt`, `launchSigner`, `addPublicBookmark`,
  `muteThread`. → become **callback lambdas** (`onZap: () -> Unit`, …).
- **Predicates**: `isLoggedUser`, `isWriteable`, `isThreadMutedFor`. → plain
  `Boolean` params.
- **Infra**: `toastManager`, `runOnIO`, `tempManualPaymentCache`. → stay native;
  surfaced as callbacks (`onError: (msg) -> Unit`) where they cross the seam.

### 3b. `INav` — trivial

Already an interface; the migration plan §1 prescribes the answer: shared
composables take `onClick*` / `onNavigate` lambdas, not `INav`. Each nav target
in a `Display*` body becomes one lambda (`onClickUser: (User) -> Unit`,
`onClickNote`, `onClickHashtag`, …).

### 3c. `R.string` (52/89 type files) — solved pattern

Migrate the needed strings to `commons/.../composeResources/values/strings.xml`
and swap `stringResource(R.string.x)` → `stringResource(Res.string.x)`. No new
abstraction (see `StaticWebsiteCard`, which already does this). This is the
single biggest *mechanical* cost, but it is rote.

### 3d. Reads cross the seam via the **cache port**, not lambdas

The cache is already commons-shaped — we should read through it rather than
re-passing every lookup as a lambda:

- `LocalCache` is declared `object LocalCache : ILocalCache, ICacheProvider`
  (`amethyst/model/LocalCache.kt:351`). The read ports `ICacheProvider` +
  `ICacheEventStream` already live in `commons/model/cache/`.
- `Account` **already holds the cache as an injected instance**:
  `Account.kt:379` `val cache: LocalCache`. So `account.cache` is a real path
  today — and once `LocalCache` becomes a class (migration §8 end-state) it
  becomes the correct *per-account* instance instead of a global singleton.
- **`IAccount` does not expose it yet** — it only carries
  `privateZapsDecryptionCache`. **Proposed:** add `val cache: ICacheProvider`
  to `IAccount` (commons), implemented by `Account` (it already has the field).

Two consequences for the split:

1. **`AccountViewModel`'s lookups currently bypass `account.cache`** and call the
   `object LocalCache` singleton directly (`getNoteIfExists =
   LocalCache.getNoteIfExists(hex)`, and ~30 more). Re-point these at
   `account.cache.…` as part of the extraction — it's the same call, but through
   the instance/port, which is what makes them commons-reachable and
   multi-account-correct.
2. **Commons render code that needs a read takes the port, not a lambda.** A
   `Display*` body that must resolve an embedded note/user depends on
   `ICacheProvider` (or `IAccount`, for hide/mute predicates like
   `isAcceptable`/`isHidden`) — both already commons-side. **Lambdas are then
   reserved for nav and write/signer actions**, which genuinely can't move.

Prefer this over the "loader lambda" framing in §3a wherever the lookup is a
synchronous cache read.

### 3e. The **leaf-composable toolkit** — the real blocker

The render bodies don't just lay out boxes; they recursively call a handful of
heavy shared composables that *themselves* take `accountViewModel`/`nav`. Top
leaf deps in `types/`:

```
TranslatableRichTextViewer 55   observeNoteEvent 23   LoadNote 18
UserPicture 16                  observeNote 15         LoadUser 13
NoteCompose 12                  LoadDecryptedContent 6 ClickableUrl 6
```

Two sub-problems:

1. **`TranslatableRichTextViewer` is flavor-specific** (#1 dependency, 55 uses)
   and cannot be naively moved. But the fix is not a per-call slot — it is a
   one-time restructure of the rich-text stack (**§3f, Phase 0**). After that
   restructure, nested/inline rich text in `Display*` bodies calls the **commons
   renderer directly**; only the single top-level translatable post body still
   routes through the native translation wrapper, supplied by the entry.
2. **`LoadNote`/`LoadUser`/`observe*` are relay-subscription + LocalCache
   bound.** These live in `amethyst/service/relayClient/reqCommand/`. Per the
   migration plan they stay native orchestration. **Resolution:** resolve on the
   entry side and pass the loaded `Note`/`User` (or a small immutable snapshot)
   down; or pass embedded-content as a slot the same way as rich text.

### 3f. Phase 0 — restructure the rich-text stack (gating prerequisite)

Rich text is the #1 dependency and blocks all of Tier 2. It is **not** a wall,
because the stack is already layered — it just needs the core decoupled:

```
TranslatableRichTextViewer   flavor-specific (play=ML-Kit, fdroid=no-op)  ← translation
  └─ ExpandableRichTextViewer   "show more/less" expansion                 ← middle
       └─ RichTextViewer.kt (1031 LOC)   paragraphs, URLs, blossom,        ← RENDER CORE
          custom emoji, bech links, hashtags, note/user embeds
```

Two facts make the core movable:

- **The translation *state* is already in commons** —
  `commons/ui/components/TranslationConfig`. Only the *service*
  (`service/lang/LanguageTranslatorService`, `TranslationsCache`, ML-Kit) is
  Android. So translation was never really entangled with rendering; it wraps it.
- **The 1031-LOC core's real `accountViewModel` surface is only 5 members**
  (despite 87 pass-through refs): `account` (for language settings only),
  `bechLinkCache`, `checkGetOrCreateNote`, `getNoteIfExists`, `toastManager`.
  Three are cache reads → the **`ICacheProvider` port (§3d)**; `toastManager` →
  an `onError`/`onToast` callback; `nav` → `onClick*` callbacks.

**Restructure:**

1. Move `RichTextViewer` + `ExpandableRichTextViewer` to
   `commons/ui/text/` (or `ui/components/`), taking `ICacheProvider` + nav/error
   callbacks instead of `AccountViewModel`/`INav`. Language settings arrive as
   plain values.
2. Leave `TranslatableRichTextViewer` native and thin: it computes the
   `TranslationConfig` via the native service, renders the status bar (drivable
   from the commons `TranslationConfig`), and calls the **commons** renderer with
   the final string. The `src/play`↔`src/fdroid` flavor split stays exactly where
   it is.
3. **The one edge that stays a slot:** `RichTextViewer` embeds *full notes*
   inline (`DisplayFullNote`/`BechLink` → `NoteCompose`). That recurses into the
   native dispatcher (§1), so full-note embeds take a
   `renderEmbeddedNote: @Composable (Note) -> Unit` slot. Inline text, URLs,
   emoji, hashtags, user mentions — everything else — moves cleanly.

**Payoff:** once the core is in commons, Tier 2's rich text is a direct call, not
a slot on every renderer. This is why Phase 0 gates the rest and should land
before scaling Tier 2. It also directly advances migration-plan §4's
"formatters → `ui/text`" line.

**This is the crux of the generalization:** the seam is not "primitives only"
(too limiting for content that embeds other notes/users), it is
**primitives + immutable state objects + `@Composable` slots for recursive
content + lambda callbacks for actions.** `StaticWebsiteCard`'s `headerActions`
slot is the pattern in miniature.

## 4. The proposed split contract

For each event kind, produce two composables:

**Entry (stays in `amethyst/ui/note/types/`), thin adapter:**
```kotlin
@Composable
fun RenderXxx(note, quotesLeft, backgroundColor, makeItShort, canPreview,
              accountViewModel, nav) {
    val event = note.event as? XxxEvent ?: return
    // decode event → values; resolve users/notes via accountViewModel;
    // build callbacks that close over accountViewModel + nav
    XxxCard(                                   // commons
        title = event.title(), …,
        makeItShort = makeItShort,
        richText = { txt -> TranslatableRichTextViewer(txt, …, accountViewModel, nav) },
        onClickAuthor = { nav.nav(routeFor(author)) },
        onZap = { accountViewModel.zap(note, …) },
    )
}
```

**Display (moves to `commons`), pure:**
```kotlin
@Composable
fun XxxCard(
    title: String?, …,                         // decoded values
    makeItShort: Boolean,
    richText: @Composable (String) -> Unit,    // slot for flavor-coupled leaf
    onClickAuthor: () -> Unit,                  // nav callback
    onZap: (() -> Unit)? = null,                // action callback (null = hidden)
) { /* Compose layout only */ }
```

**Seam types to introduce in `commons/ui/note/`:**
- A small `@Immutable` snapshot per family where many primitives travel together
  (e.g. `AuthorLine(name, pubkeyHex, avatarUrl, nip05)`), so signatures stay
  sane. Prefer these over 15-arg functions.
- Reuse existing commons atoms: `UserAvatar`, `Nip05OrPubkeyLine`,
  `ClickableTexts`, `RobohashImage`, `GenericLoadable`, the icon set, `Res`
  strings. Several avatar/user-line needs are **already** in
  `commons/ui/components`.

**Where files land** (per `commons/ARCHITECTURE.md` + migration §4): a
cross-cutting card → `commons/ui/note/`; a NIP-specific renderer → the owning
`commons/.../<nipNN>/ui/` package. Follow whatever the existing
`commons/ui/note/*` files already do.

## 5. Tiered categorization of the 89 `types/`

Classified by the seam each needs (method: `accountViewModel` refs, leaf-dep
grep, `launchSigner`/dialog grep).

### Tier 0 — no `AccountViewModel` at all (13 files, ~2.2k LOC) — **do first**
`ActivityCard, Birdex, CodeSnippet, EcashMint, GitDiffView, GitStatusPill,
ImetaContent, MusicFormatting, PodcastChips, PodcastSoundbites,
PodcastValueSplits, Ps1Save, RoadEvent`.
These are already value-in/Compose-out. Mostly just an `R.string` sweep + a
package move. **Ideal pilots** — several are also self-contained cards (no rich
text, no loaders).
> **Status (2026-07-17):** ✅ done — ActivityCard, Birdex, CodeSnippet,
> EcashMint, GitDiffView, GitStatusPill, PodcastChips, PodcastSoundbites,
> PodcastValueSplits, Ps1Save, RoadEvent. **Left:** `ImetaContent` /
> `MusicFormatting` are pure non-UI util helpers used by Tier-2 parents — they
> ride along when Chat / MusicTrack move, not worth a standalone batch.

### Tier 1 — decode-only, no loaders, no rich text
Read-only renderers that only need `nav` callbacks + `R.string` (e.g. relay/list
cards, badge display, goal/fundraiser headers, calendar/road cards). Split is
mechanical: decode in entry, pass primitives + `onClick*` down. Bulk of the
value.
> **Status (2026-07-17):** started — RelayDiscovery and the NIP-52 calendar
> collection + RSVP cards done via gate #1 (§0.1 Finding B). The cleanest next
> targets are the "unused seam" renderers (§0.1 Finding C). Note in practice
> most Tier-1 strings are **shared with a native screen**, so each move also
> switches that screen to commons `Res` in the same commit.

### Tier 2 — need recursive content slots (rich text and/or embedded note/user)
23 use `TranslatableRichTextViewer`; 23 use `LoadNote/LoadUser/observe*`:
`Text, TextModification, Highlight, PublicMessage, PrivateMessage, Report,
Chat, CommunityHeader, FollowList, Git, InteractiveStory, MusicTrack,
MusicPlaylist, PodcastEpisode, PodcastMetadata, Video, PictureDisplay,
Attestation, NIP90ContentDiscoveryResponse, ZapPoll, AudioTrack, …`.
Movable, but **only via the `@Composable` slot pattern** (§3e/§4). The layout
moves to commons; rich-text/embeds stay app-supplied. `Highlight` is the
recommended Tier-2 pilot (already half-split).

### Tier 3 — interactive: signer / dialogs / write actions — **stay native or last**
5 use `launchSigner`: `AppDefinition, Badge, CalendarRsvpRow, GitStatusActions,
Poll`. Plus zap/reaction dialogs, `Poll`/`ZapPoll` voting, `Chess`. The
**visual card** can still move (as a Tier-2 body with `onVote`/`onSign`
callbacks), but the interaction/signer flow stays in `amethyst`. Don't force
these early.

## 6. Recommended sequencing

1. **Pilot (1 PR, Tier 0):** move 2–3 pure cards (e.g. `RoadEvent`, `EcashMint`,
   `CodeSnippet`) to `commons/ui/note/`, migrate their strings, leave a thin
   `RenderXxx` entry. Proves the string + package + preview-test loop end to end
   and sets the reviewable template. Verify with `./gradlew :commons:build` and
   a Compose `@Preview`.
2. **Bulk Tier 0/1** — mechanical, parallelizable, one small PR per event family.
   These need neither rich text nor the cache port, so they don't wait on Phase 0.
3. **Phase 0 — rich-text restructure (§3f), the gate for Tier 2.** Move
   `RichTextViewer`/`ExpandableRichTextViewer` to commons behind `ICacheProvider`
   + callbacks, with a `renderEmbeddedNote` slot for full-note embeds; leave
   `TranslatableRichTextViewer` native and thin. Depends on adding
   `IAccount.cache: ICacheProvider` (§3d). This is the load-bearing PR — review
   it carefully before scaling.
4. **Tier 2 pilot:** `Highlight` → move `DisplayHighlight` to commons calling the
   new commons renderer directly + `onClickAuthor`/`onClickNote` callbacks.
   Confirms the post-Phase-0 shape.
5. **Tier 2 family by family** (podcast ×13, git ×5, calendar ×4, music ×3,
   chat ×3) once the pilot is settled.
6. **Tier 3** last, and only the visual shell; leave signer flows native.

Each step is small, compile-verifiable, and deletes an app-side render body —
exactly the "manually, event by event" cadence you called for.

## 7. Out of scope (stays native)

- `NoteCompose.kt` dispatcher, feed/thread scaffolding, drag/swipe,
  quick-action menu, report/block gating.
- `creators/` (post editor), `buttons/`, most of `share/` — interactive,
  signer- and Context-bound.
- The flavor-specific `TranslatableRichTextViewer` **translation wrapper** and
  the ML-Kit `service/lang/*` — stay native (the *renderer core* underneath them
  moves; see §3f).
- The `service/relayClient/reqCommand` observers/loaders — resolved on the entry
  side or consumed via slots, not moved.

## 8. Risks

- **Slot proliferation.** If every embed becomes a slot, entries get noisy.
  Mitigate with per-family `@Immutable` snapshot types and by reusing existing
  commons atoms rather than re-passing avatars/user-lines as slots.
- **String drift.** Moving strings piecemeal risks duplicate keys between
  `amethyst` `R.string` and commons `Res.string`. Migrate + delete the app copy
  in the same PR; don't leave both.
- **Preview coverage.** The `types/` files carry `@Preview`s (see
  `Highlight.kt`). Move the previews with the body so commons keeps visual
  regression cover; they also become desktop/cli-testable fixtures.
- **Desktop double-benefit vs. drift.** Desktop currently re-parses some of
  this; extracting to commons is the chance to converge — but only if Desktop is
  pointed at the new card in the same or an immediately following PR.
