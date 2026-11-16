# Entity Ratings (kind 34259) — parse, ingest, render, feed

**Status:** implemented — see §11 for what shipped and where this document was wrong
**Modules:** `quartz`, `commons`, `amethyst`
**Scope:** parse kind-34259 entity ratings, ingest them into `LocalCache`, render
them as a star + review card, and make them appear in the Home feed behind a
Settings › Home toggle.

Deliberately excluded from v1: composing/publishing a rating from Amethyst,
aggregate ("3.8★ from 12 raters") rollups on the rated object, and the kind-30040
publication *reader*. §8 says why and what each would take.

---

## 1. What we are implementing

Kind 34259 is **not a merged NIP**. The upstream spec is
[`XYZ.md` in `abh3po/nostr-polls`](https://github.com/abh3po/nostr-polls/blob/main/XYZ.md)
(Pollerama) — a deliberately generic "rate anything" addressable kind. It defines
exactly three tags:

| tag | spec meaning |
| --- | --- |
| `d` | id of the rated entity; prefixed with the `m` value when the bare id isn't unique (`hashtag:books`) |
| `m` | mark — entity type: `event`, `profile`, `relay`, `hashtag`, `books`, `movies`…; empty ⇒ a nostr event |
| `rating` | "Number less than 1 and greater than 0" |

Everything else in the wild sample below is an **extension by
`silberengel/jumble`** (the web client that signs `client: imwald`), built in
`src/lib/draft-event.ts:1626` for rating a kind-30040 NKBIP-01 publication:

```json
{ "kind": 34259,
  "tags": [
    ["d", "books:30040:5736…edc:wuthering-heights"],
    ["m", "books"],
    ["rating", "1.000"],
    ["s", "5"],
    ["a", "30040:5736…edc:wuthering-heights"],
    ["A", "30040:5736…edc:wuthering-heights"],
    ["e", "aff2…287", "", "5736…edc"],
    ["k", "30040"],
    ["p", "5736…edc"],
    ["c", "true"]
  ],
  "content": "This is my very favorite book. …" }
```

`s` = raw 1–5 stars, `a`/`A` = the rated coordinate, `e` = the index event id,
`k` = rated kind, `p` = rated author, `c` = "has a written comment".

**Design consequence:** we implement the *generic* kind with typed accessors, and
treat `m` as the dispatch key for presentation. We do not hardcode books into the
event class. Books is simply the first mark we render richly.

---

## 2. The two parsing traps

These are the whole reason this needs a plan rather than a 40-line event class.

### 2.1 `rating` is ambiguous at exactly 1

The spec says 0 < rating < 1. jumble publishes `"1.000"` for five stars —
outside the spec's own stated range. Other clients publish a plain `1`–`5`.
So the string `"1"` means **either** 1.0 (⇒ 5 stars) **or** 1 raw star, and the
`rating` tag alone cannot tell you which.

jumble resolves it one way (`event-metadata.ts:1364`: `raw <= 1` ⇒ fraction ⇒
×5), which silently turns a genuine 1-star review from a raw-scale client into a
5-star one. We should not copy that blindly.

**Rule for our parser** (`EntityRatingEvent.stars()`):

1. If an `s` tag is present and parses to 1..5 → that is the star count. Authoritative.
2. Else if `rating` parses to a value in `0.0..1.0` **inclusive** → `rating × 5`.
3. Else if `rating` parses to `1.0 < x <= 5.0` → raw stars.
4. Else → `null` (render the comment, no stars) — never 0, never a guess.

Step 1 is what makes `1` unambiguous for every event jumble emits, and step 2
accepts the boundary value the spec's prose excludes. Pin all four branches with
tests, including the `"1"`-without-`s` ambiguity resolving to 5 (documented as
the interop choice, with the reasoning in a comment).

### 2.2 `d` carries the mark prefix

`d` is `books:30040:<pubkey>:<identifier>` — the coordinate with an `m:` prefix.
The parser must strip a leading `<mark>:` before treating the remainder as an
`Address`. And because only Pollerama-derived clients apply the prefix, any REQ
by `#d` must ask for **both** the prefixed and bare forms
(jumble's `publicationRatingDTagsForQuery` does exactly this).

---

## 3. Quartz — `experimental/ratings/`

Not a merged NIP ⇒ `experimental/`, alongside `agora`, `birdstar`, `nipsOnNostr`.

```
quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/experimental/ratings/
├── EntityRatingEvent.kt      # kind 34259, AddressableEvent
├── RatingMark.kt             # the `m` vocabulary + prefix strip/apply
├── TagArrayBuilderExt.kt     # builder DSL (rating/mark/target/stars)
└── tags/
    ├── RatingTag.kt          # ["rating", "0.000".."1.000"]
    ├── StarsTag.kt           # ["s", "1".."5"]  (jumble ext)
    └── HasCommentTag.kt      # ["c", "true"]    (jumble ext)
```

`EntityRatingEvent` surface:

```kotlin
class EntityRatingEvent(...) : Event(...), AddressableEvent {
    fun mark(): String?                 // `m`, null ⇒ "event" per spec
    fun ratingFraction(): Double?       // `rating` clamped to 0.0..1.0, null if unparseable
    fun stars(): Double?                // §2.1 resolution ladder
    fun targetAddress(): Address?       // `a`/`A` first, then the de-prefixed `d`
    fun targetEventId(): HexKey?        // `e`
    fun targetKind(): Int?              // `k`
    fun targetAuthor(): HexKey?         // `p`
    fun hasComment(): Boolean           // `c` == "true", OR content.isNotBlank()
    companion object { const val KIND = 34259 }
}
```

`targetAddress()` prefers `a`/`A` because they are unambiguous; the de-prefixed
`d` is the fallback for clients that only publish `d` (which the spec permits —
`a` is not in the spec at all).

Also implement `SearchableEvent.indexableContent()` returning `content` — a
review is prose and belongs in NIP-50 search. Cheap, and the `searchable-events`
skill documents the diff surface external engines mirror, so note it there.

**Registration checklist** (each is a real edit, all found by grep):

| File | Edit |
| --- | --- |
| `quartz/…/utils/EventFactory.kt` | branch in the `when` (before the `else` at :845) |
| `quartz/…/kinds/KindNames.kt` | English name "Entity rating" |
| `commons/…/connectedApps/signers/NostrSignerPermissionLedger.kt:244` | addressable re-sign list |
| `amethyst/…/relays/KindDisplayName.kt` | localized name + `strings.xml` |
| `quartz/…/utils/EventFactoryIsKnownKindTest.kt` | assert `isKnownKind(34259)` |

---

## 4. `LocalCache` ingest

Today the event is **dropped**: unknown kind ⇒ bare `Event` ⇒ the `else` at
`LocalCache.kt:3886` logs `"Event Not Supported"` and returns `false`. It never
becomes a `Note`, so no amount of UI work would show it.

Add `is EntityRatingEvent,` to the addressable group that dispatches to
`consumeBaseReplaceable` at `LocalCache.kt:3799`. One line. `consumeBaseReplaceable`
already handles addressable supersession, so the "one rating per author per
target" replaceable semantics come for free.

### Do **not** add a `computeReplyTo` branch in v1

`computeReplyTo` (`LocalCache.kt:1268`) falls through to `emptyList()` for
unknown types, so `Note.replyTo` stays empty and `Note.isNewThread()`
(`commons/…/model/Note.kt:1364`) returns **true**. That is what we want: the
rating renders as a top-level card in the New Threads tab.

This is the `HighlightEvent` precedent — a highlight also carries `a`/`e` to its
source and also has no `computeReplyTo` branch; it renders the source inline
instead.

The trade-off is explicit: without a `computeReplyTo` branch the rating does
**not** appear in the rated publication's thread view or reply count. Adding one
later moves it *out* of New Threads (because `isNewThread()` flips false) unless
`HomeNewThreadFeedFilter` is taught an exception. Decide once; v1 picks feed
visibility, because that is the stated goal.

---

## 5. Feed visibility — three gates, all must open

This is the part that is easy to half-do. An event only reaches the Home feed if
**all three** of these pass. Verified by reading each file.

### Gate 1 — the REQ must ask for the kind

The kind lists are duplicated across five top-nav strategies. Add
`EntityRatingEvent.KIND` to `HomePostsNewThreadKinds2` in:

- `commons/…/relayClient/home/nip65Follows/FilterHomePostsByAuthors.kt:83`
- `commons/…/relayClient/home/nip01Core/FilterHomePostsByHashtags.kt`
- `commons/…/relayClient/home/nip01Core/FilterHomePostsByGeohashes.kt`
- `commons/…/relayClient/home/nip01Core/FilterHomePostsByGlobal.kt`
- `commons/…/relayClient/home/nip72Communities/FilterHomePostsFromCommunities.kt`

(`Kinds2` rather than `Kinds1` — `Kinds1` is already at 16 kinds and the two
lists exist to keep each REQ's kind array bounded.)

### Gate 2 — a `HomeFeedType` group

`commons/…/model/HomeFeedType.kt` is the Settings › Home toggle registry; its
`kinds` drive both the REQ strip (`HomeOutboxEventsEoseManager.removeDisabledHomeKinds`)
and the DAL filter. Add:

```kotlin
RATINGS("ratings", listOf(EntityRatingEvent.KIND)),
```

`code` is the on-disk identifier — never rename it. `HomeFeedTypeTest.kindsAreDisjointAcrossTypes`
enforces that no two groups claim a kind, so 34259 must appear exactly once. Add
a string for the toggle label in `HomeTabsSettingsScreen`.

### Gate 3 — the DAL must accept it

`amethyst/…/home/dal/HomeNewThreadFeedFilter.kt`, two edits:

1. **`ADDRESSABLE_KINDS`** (:108) — required. `feed()` scans `LocalCache.notes`
   only for `kind < 10000` ("Avoids processing addressables twice"), so an
   addressable kind that is not in this list is invisible no matter what else is
   configured.
2. **`acceptableEvent`** (:141) — add `noteEvent is EntityRatingEvent` to the
   type disjunction.

Mirror both in `HomeConversationsFeedFilter.kt` only if we later add the
`computeReplyTo` branch; not in v1.

Desktop has its own copy in `desktopApp/…/feeds/DesktopFeedFilters.kt` — out of
scope here, but note it so the two don't silently diverge.

### Anti-spam gate

jumble drops kind-34259 events with no `d` tag on ingest. Ours is weaker-risk
(addressable ⇒ a `d`-less event occupies exactly one slot per author), but a
rating with no resolvable target is unrenderable. Reject in `acceptableEvent`:
`targetAddress() != null || targetEventId() != null`.

---

## 6. Rendering

New `amethyst/src/main/java/…/ui/note/types/EntityRating.kt`:

```kotlin
@Composable
fun RenderEntityRating(note: Note, accountViewModel: AccountViewModel, nav: INav)
```

Layout, modelled on `Classifieds.kt` (compact addressable card) + `Highlight.kt`
(inline source resolution):

```
┌──────────────────────────────────────────────┐
│ ★★★★★                            [books]     │   stars + mark chip
│ ┌──────────────────────────────────────────┐ │
│ │ 📕 Wuthering Heights                     │ │   target card, clickable
│ │    by @emilybronte                       │ │   ← LoadAddressableNote
│ └──────────────────────────────────────────┘ │
│ This is my very favorite book. The fast-     │   content, TranslatableRichTextViewer
│ paced and mysterious plot …                  │
└──────────────────────────────────────────────┘
```

- Target resolution: `LoadAddressableNote(targetAddress(), …)` — same helper
  `RenderPostApproval.kt:61` and `Attestation.kt` use.
- **Title fallback while (or if) the target never loads:** derive a label from the
  coordinate's `d` (`wuthering-heights` → "Wuthering Heights"), as jumble's
  `publicationTitleHintFromRatingEvent` does. Without this the card is a row of
  stars attached to nothing — see §7.
- Unknown/absent `m`: render stars + comment + a generic `nostr:` link to the
  target. The kind is generic; the card must degrade, not blank.
- Dispatch: add `is EntityRatingEvent -> RenderEntityRating(...)` to
  `NoteCompose.kt`'s render `when`, **before** the `else` at :1658 (which
  currently routes unknown kinds to `RenderTextEvent`).

### The star icons need a font change

`MaterialSymbols.kt:233-234` defines **both** `Star` and `StarBorder` as
`\uF09A` — the same codepoint. Filled and outline stars are currently
indistinguishable (which also means `FavoriteAlgoFeedToggle.kt:90/97` and
`RelayGroupDiscoveryScreen.kt:400` are drawing the same glyph for on and off
today).

A 3.5-of-5 star row needs three distinct glyphs. So:

1. Give `StarBorder` its real outline codepoint and add `StarHalf`.
2. Regenerate the subset — **mandatory**, per `.claude/CLAUDE.md`:
   `./tools/material-symbols-subset/subset.sh`
3. Commit the regenerated `material_symbols_outlined.ttf` with the
   `MaterialSymbols.kt` change, or the new glyphs render as tofu.

Fixing the duplicate is a small pre-existing-bug fix that rides along; call it
out in the PR body since it changes two unrelated toggles' appearance.

---

## 7. The kind-30040 dependency

The rated object here is a **kind-30040 NKBIP-01 publication index**. Amethyst
has no class for 30040 either, so `LocalCache` drops it by the same `else` at
:3886. `LoadAddressableNote` will therefore resolve to a permanently empty
`Note`, and the target card in §6 will never show a real title.

Two ways to close this, and they are separable:

**(a) v1 — slug fallback only.** Ship §6's derived-title fallback and no 30040
class. The card reads "Wuthering Heights" from the coordinate. Cheap, honest,
and correct for the common case where the `d` is a slug. Fails softly (shows the
raw identifier) when the `d` is a hash or an opaque id.

**(b) Phase 2 — minimal `PublicationIndexEvent` (30040).** A `title`/`author`/`d`
parser plus `consumeBaseReplaceable` wiring, so the target card shows the real
title and links somewhere. **Explicitly not** the publication *reader* — 30040 is
an index over kind-30041 sections, and rendering a book is a separate feature an
order of magnitude larger than this one.

Recommend shipping (a) in v1 and (b) as an immediate follow-up. Do **not** let
(b)'s scope pull the reader in.

---

## 8. Out of scope for v1, and what each would cost

| Deferred | Why | Rough shape |
| --- | --- | --- |
| **Publishing a rating** | Needs a compose surface (star picker + comment) and a "what am I rating" entry point, which does not exist until 30040 objects are browsable. The quartz builder DSL lands in v1 anyway, so this is UI-only later. | `NewPostScreen` variant + a rate action on the target card |
| **Aggregate rollups** | Needs a per-target index in `LocalCache` (like the zap/reaction indices) plus a REQ by `#a`/`#d`. Meaningful only once there is a target screen to put the average on. | New index + `FeedMetadataCoordinator` assembler |
| **Rating relays / profiles / hashtags** | The kind is generic and these marks are in the spec, but each needs its own target card and entry point. The event class supports them from day one. | Per-mark `RenderEntityRating` branch |
| **Relay reviews (kind 31987)** | Same `rating` tag convention, also unsupported in Amethyst today. Sharing `stars()` between the two is the natural next step. | Reuse `tags/RatingTag.kt` |

---

## 9. Order of work

1. **quartz** — `EntityRatingEvent` + tags + builder + `RatingMark`, with unit
   tests covering all four `stars()` branches, the `d`-prefix strip, `a`-over-`d`
   preference, and a round-trip of the real imwald event above as a fixture.
2. **quartz registries** — `EventFactory`, `KindNames`, `isKnownKind` test.
3. **LocalCache** — one line at :3799; test that the fixture becomes an
   addressable `Note` and that a newer rating from the same author supersedes it.
4. **Feed plumbing** — the three gates in §5, plus the `HomeFeedType` test update.
5. **Icons** — codepoint fix + `subset.sh` + committed `.ttf`.
6. **Rendering** — `EntityRating.kt`, `NoteCompose` branch, `@Preview` in
   `ThemeComparisonColumn` (the convention every `ui/note/types/*.kt` follows).
7. **Verify** — `./gradlew test`, then drive the real event end-to-end with
   `amy` against `wss://pipe.imwald.eu/` (already a known-good relay in this
   repo: `quartz/plans/2026-07-16-relay-limits.md`) to confirm ingest and
   feed acceptance, not just unit-test green.
8. `./gradlew spotlessApply`.

Steps 1–3 are independently useful (they stop the drop and make the kind
inspectable) and can merge before the UI lands.

---

## 10. Open questions for the maintainer

1. **`m` vocabulary policy.** Render only known marks richly and fall back to a
   generic card for the rest (proposed), or refuse to render unknown marks at
   all? The generic fallback risks showing stars for a mark whose target we
   cannot resolve.
2. **Default-on or default-off.** `HomeFeedType.ALL` enables every group by
   default. A brand-new, single-client, non-NIP kind arriving in everyone's Home
   feed on upgrade is a defensible objection — say so and it ships default-off
   with an explicit opt-in, at the cost of nobody discovering it.
3. **Is 30040 worth it here**, or should ratings wait until publications are a
   real feature? §7(a) makes v1 standalone, but a feed of star-ratings pointing
   at books Amethyst cannot open is arguably not worth shipping alone.

---

## 11. What shipped, and where this plan was wrong

Implemented on `claude/event-kind-34259-parsers-jocu9w`. Three things came out
differently once the code was written; the sections above are left as they were
so the reasoning is still legible, and this section is what is true.

### 11.1 The star-icon claim in §6 was wrong

§6 says `MaterialSymbols.Star` and `StarBorder` sharing `\uF09A` is a bug. **It
is not.** Material Symbols expresses fill through the **FILL variable axis**, not
through separate codepoints, so the upstream codepoint table maps `star`,
`star_border`, `star_outline` *and* `grade` all to `f09a`. The duplicate in
`MaterialSymbols.kt` is correct, and the toggles in `FavoriteAlgoFeedToggle.kt`
and `RelayGroupDiscoveryScreen.kt` distinguish their states by tint, not glyph —
also correct. Nothing there needed fixing.

What is true: `star_half` **is** its own glyph (`e839`), and it was not in the
subset. So the font change that actually shipped is one added symbol:

```kotlin
val StarHalf = MaterialSymbol("\uE839")
```

plus the regenerated `material_symbols_outlined.ttf` (241 codepoints, was 240).
Filled vs empty stars are the same glyph at two tints — `primary` and
`onSurfaceVariant`.

**Corrected after review.** `MaterialSymbolPainter` originally drew the glyph as
tinted text with no variable-axis control, so the first pass tinted an outline
star and called it "filled" — five hollow outlines that read as an empty row.
`87a44b97` added FILL-axis support to the painter and `aa54508e` switched the row
to `filled = isOn`, which is the correct fix: an earned star is FILL=1, not a
tint. Tint now only distinguishes earned from unearned, which is what it is
good for.

### 11.2 Gate 1 is one list, not five

§5 says to add the kind to five parallel REQ kind lists. Only one was right:
`HomePostsNewThreadKinds2`, which the Follows, Global and Relay top-nav
strategies all share.

The hashtag, geohash and community lists select by `t` / `g` / community-`a`
tags. A rating carries none of those, so adding the kind there would widen every
such REQ for zero possible matches. If ratings ever start carrying topics it is a
one-line addition to each.

### 11.3 §7 shipped as option (b)

`PublicationIndexEvent` (kind 30040, NKBIP-01) is implemented — title, author,
summary, image, type, version, topics and the ordered `a`-tag table of contents —
so the rated publication resolves to a real title instead of a slug. The reader
is **not** implemented, and kind 30041 sections are still unparsed, exactly as
§7 said they should be.

`titleOrIdentifier()` keeps the slug fallback from option (a) anyway, because the
spec's mandatory `title` tag is missing from events in the wild.

### 11.4 Everything else went as planned

| Area | Where |
| --- | --- |
| Event classes | `quartz/…/experimental/ratings/`, `quartz/…/experimental/publications/` |
| Registries | `EventFactory`, `KindNames`, `NostrSignerPermissionLedger` |
| Ingest | `LocalCache.kt` addressable group (no `computeReplyTo` branch, per §4) |
| Feed gates | `HomePostsNewThreadKinds2`, `HomeFeedType.RATINGS`, `HomeNewThreadFeedFilter` |
| Settings | `home_content_type_ratings`, `HomeTabsSettingsScreen` |
| Rendering | `amethyst/…/ui/note/types/EntityRating.kt` + `NoteCompose` branch |

Tests: 44 new (27 in `quartz`, 7 ingest + 10 existing-suite in `amethyst`),
covering all four branches of the `stars()` ladder, the `"1"` ambiguity, the
`d`-prefix strip, `a`-over-`d` preference, addressable supersession in both
directions, and the `isNewThread()` tripwire that guards §4's decision. Full
suites green: quartz 4491, commons 1788, amethyst 1415.

`stars()` gained one branch the plan did not anticipate: an `s` tag outside
1..5 falls **through** to `rating` rather than winning, so a bogus `s` cannot
shadow a usable score.

### 11.5 Still open

§10's three questions are unanswered and the code takes the defaults:

1. Unknown marks render generically (stars + review + target link).
2. `RATINGS` is **default-on**, because `HomeFeedType.ALL` enables everything and
   opting one entry out of that would be a special case. One line in
   `HomeFeedType.ALL` changes it.
3. Publishing, aggregate rollups, and the 30041 reader remain unbuilt. The
   `EntityRatingEvent.build()` DSL exists and is tested, so publishing is UI-only
   work whenever a "rate this" entry point exists.
