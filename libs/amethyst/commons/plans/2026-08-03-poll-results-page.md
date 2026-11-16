---
title: "feat(polls): extended poll results page (counts + who voted for what)"
type: feature
status: proposed
date: 2026-08-03
owner: commons
consumers: amethyst, desktopApp
---

# feat(polls): extended poll results page

> **Status:** proposal. Nothing implemented. This document surveys what exists,
> names four correctness bugs the current tally has, and specifies a shared
> results surface for Android and Desktop. _Authored 2026-08-03._
>
> **Visual mockup:** [`assets/2026-08-03-poll-results-page.html`](assets/2026-08-03-poll-results-page.html)
> — annotated screens for the Android page, the Desktop deck column, the loading /
> closed / empty states, and a side-by-side of the two multi-choice percentage
> readings from §10.1. Open it in a browser; it renders in Amethyst's own theme
> tokens and follows the viewer's light/dark preference.

## 1. Motivation

Today a NIP-88 poll (kind 1068) renders its results **inline in the feed**, and
the results are deliberately tiny: a progress bar, a percentage, and up to four
voter avatars per option (`Poll.kt:571 UserGallery` shows `take(4)` then `+N`).
There is no way to answer the two questions people actually ask about a poll:

1. **How many votes did each answer get?** — the card shows only a percentage,
   never a count, and the percentage denominator is wrong for multi-choice
   polls (§3.1).
2. **Who voted for what?** — Android gives you at most four avatars and no way
   to see the fifth. Desktop already went further (`DesktopPollCard.kt:576
   VoterListPopup`), which proves the demand but leaves the two platforms
   divergent and the logic unshared.

A dedicated results page also unlocks the things a feed card can never host:
filtering the voter list to people you follow, searching it, sorting it, and
being honest about *how complete the tally is* — which today it silently is not
(§3.4).

## 2. What exists today

| Layer | Component | File |
|---|---|---|
| Protocol | `PollEvent` (1068): `options()`, `relays()`, `pollType()`, `endsAt()`, `hasEnded()` | `quartz/…/nip88Polls/poll/PollEvent.kt:63` |
| Protocol | `PollResponseEvent` (1018): `responses()`, `poll()` | `quartz/…/nip88Polls/response/PollResponseEvent.kt:62` |
| Tally | `PollResponsesCache` + `ResponseTally` + `TallyResults` — the single source of truth, hung off `Note.pollState()` | `commons/…/model/nip88Polls/PollResponsesCache.kt` |
| Attach | `Note.poll` / `pollState()` | `commons/…/model/Note.kt:178` |
| Ingest | `consume(PollResponseEvent)` → `getOrCreateNote(pollId).pollState().addResponse(...)` | `amethyst/…/model/LocalCache.kt:2936` |
| Write | `Account.pollRespond(event, responses)` | `amethyst/…/model/Account.kt:2018` |
| UI (Android) | `RenderPoll` / `RenderPollCard` / `RenderResults` / `RenderClosedItem` / `UserGallery`, view-state `PollCard` + `PollItemCard` | `amethyst/…/ui/note/types/Poll.kt` |
| UI (Desktop) | `DesktopPollCard`, `PollResultRow`, `VoterGallery`, `VoterListPopup` | `desktopApp/…/ui/note/DesktopPollCard.kt:95` |
| Feed | `Route.Polls` tab + `PollsFeedFilter` / `OpenPollsFeedFilter` / `ClosedPollsFeedFilter` | `amethyst/…/ui/screen/loggedIn/polls/` |
| Zap polls | `ZapPollEvent` (6969) + `PollNoteViewModel` (amount-weighted, zap-receipt tally) | `quartz/…/experimental/zapPolls/`, `amethyst/…/ui/note/ZapPollNoteViewModel.kt` |

**Reuse verdict:** `PollResponsesCache` is the right foundation and should not
be replaced — but it must become *poll-aware* (§3). The results page needs **no
new event kind, no new cache, and no new feed filter**; it needs a poll-aware
tally, one shared subscription, one shared ViewModel, and two thin platform
shells.

## 3. Correctness gaps to fix first

These are not cosmetic. Three of them make the numbers on the current card
wrong, and the results page would inherit all of them. Each is cheap to fix and
the fix improves the inline card too.

### 3.1 Multi-choice percentages use the wrong denominator

`ResponseTally.totalVotes()` (`PollResponsesCache.kt:52`) sums the per-option
voter sets, so a voter who picks 3 options is counted 3 times. `filterTo`
divides by that (`:88-95`). On a multi-choice poll the percentages therefore sum
to 100% and read as "share of selections" — but users read a poll bar as "share
of people". Desktop already diverges here, using `tallyState.votes.size`
("distinct voters", `DesktopPollCard.kt:196`) for its footer while its bars use
the selections denominator.

**Proposal:** make the basis explicit and platform-consistent.

```kotlin
fun totalVoters(): Int = votes.size          // distinct pubkeys
fun totalSelections(): Int = tally.entries.sumOf { it.value.size }
```

- single-choice → identical, no visible change;
- multi-choice → percent = `optionVoters / totalVoters`, bars may sum past 100%,
  and each row is captioned **"N of M voters"** so the reading is unambiguous.

### 3.2 Single-choice polls count every `response` tag

NIP-88: *"for singlechoice polls, the first response tag is to be considered the
actual response."* `votesByOption()` (`PollResponsesCache.kt:129-143`) iterates
**all** response tags regardless of poll type, so one malformed (or hostile)
1018 with a `response` tag per option gets counted in every bucket.
`ResponseTally` cannot fix this today because it never sees the `PollEvent`.

### 3.3 Votes outside the poll timeframe still count

NIP-88 keeps the latest response *within the poll timeframe*. `latestByAuthor()`
(`Note.kt:1685`) only compares `createdAt` — a vote stamped after `endsAt` wins
over a legitimate earlier one, and a closed poll's totals keep moving.

### 3.4 Unknown option codes inflate the tally

A `response` tag whose code isn't in `PollEvent.options()` creates a phantom
bucket that still contributes to `totalVotes()`, dragging every real
percentage down. It should be dropped (and counted separately, so the results
page can show "3 responses ignored (invalid)").

### 3.5 The fix: a poll-aware tally

All four collapse into one change — give the tally the poll it belongs to:

```kotlin
// commons/…/model/nip88Polls/PollTallyPolicy.kt  (new)
@Immutable
class PollTallyPolicy(
    val validCodes: Set<String>,
    val type: PollType,
    val createdAt: Long,
    val endsAt: Long?,
) {
    fun isInWindow(createdAt: Long) = createdAt >= this.createdAt && (endsAt == null || createdAt <= endsAt)
    fun accept(codes: List<String>): Set<String> =
        when (type) {
            PollType.SINGLE_CHOICE -> setOfNotNull(codes.firstOrNull { it in validCodes })
            PollType.MULTI_CHOICE -> codes.filterTo(mutableSetOf()) { it in validCodes }
        }
}
```

`PollResponsesCache` gains `var policy: PollTallyPolicy?`, set by
`LocalCache.consume(PollEvent)` (and by `DesktopLocalCache.consume`) when the
poll event itself arrives — responses routinely arrive **before** the poll they
reference, so `ResponseTally` must recompute when the policy lands, and must
degrade to today's permissive behaviour while `policy == null`.
`ResponseTally` then also exposes `rejected: Int` for the "ignored" caption.

Note the ordering hazard is real and already present: `consume(PollResponseEvent)`
calls `getOrCreateNote(pollId).pollState().addResponse(...)`
(`LocalCache.kt:2936`) on a note that may still be an empty placeholder.

## 4. Data completeness (the second reason to fix this now)

**Android never queries a poll's own relays for votes.** Kind 1018 rides along
in the generic engagement filter (`FilterRepliesAndReactionsToNotes.kt:75`),
whose relay set is `Note.relayUrlsForReactions()` = *author inbox relays + relays
the note was seen on* (`Note.kt:339`). NIP-88 votes are published to the relays
listed in the poll's own `relay` tags (`EventBroadcaster.kt:233` does this on the
write side), which the viewer usually isn't subscribed to. Desktop diagnosed and
fixed this per-card (`DesktopPollCard.kt:132-157`); Android tallies are
systematically short.

That same filter also carries `limit = 100` and never pages, so any poll with
more than 100 responses truncates silently.

**Proposal — one shared assembler, used by both platforms and by the results page:**

- `commons/…/relayClient/…/polls/PollResponsesFilterAssembler.kt` — a
  compose-scoped `Subscribable` keyed by poll id, filtering
  `kinds = [1018], tags = {"e": [pollId]}` over `PollEvent.relays() + the
  viewer's read relays` (this is desktop's fix, lifted out of the card).
- The results page additionally does a one-shot backfill with **`fetchAllPages`**
  from `quartz/…/nip01Core/relay/client/accessories/` — do **not** hand-roll a
  paging loop; that package already has it.
- Optional but cheap: a NIP-45 **`count`** call (same package) per relay gives
  "relay reports 412 responses, 380 loaded" — the page can then be honest about
  incompleteness instead of presenting a partial tally as final.

## 5. Screen design

### 5.1 Anatomy

```
┌─────────────────────────────────────────────┐
│ ←  Poll results                          ⟳  │   TopBarWithBackButton
├─────────────────────────────────────────────┤
│ [avatar] Alice · 2d ago                     │   author row (UserCompose-style)
│ "Which relay should we default to?"         │   poll question (content)
│ ⬤ Open · closes in 4h   ⬡ Single choice     │   status + type chips
│ 412 voters · 412 selections                 │   totals (§3.1)
├─────────────────────────────────────────────┤
│ ▸ nos.lol                    189  46%  ✓    │   option rows, winner marked,
│   [▓▓▓▓▓▓▓▓▓░░░░░░░░░░]  ◍◍◍◍ +185          │   animated bar + avatar stack
│ ▸ relay.damus.io             142  34%       │
│   [▓▓▓▓▓▓░░░░░░░░░░░░░]  ◍◍◍◍ +138          │
│ ▸ purplepag.es                81  20%       │
├─────────────────────────────────────────────┤
│ You voted: nos.lol            [Change vote] │   only when signed in + voted
├─────────────────────────────────────────────┤
│ [All] [nos.lol] [damus] [purplepag.es]      │   option filter chips
├─────────────────────────────────────────────┤
│ ⬤  Ana Reis              nos.lol     2d ago │   voter rows — UserLine as-is
│    ana ✓ nostrcheck.me                      │   (§6.1): picture / name /
│ ⬤  Bruno Sá              damus       2d ago │   NIP-05, vote passed in as
│    bruno ✓ nostrplebs.com                   │   trailingContent
│ ⬤  npub1q8f…j3xw         damus       22h ago│   (npub = no-NIP-05 fallback)
│ …                                            │
├─────────────────────────────────────────────┤
│ 380 of ~412 loaded from 6 relays · 3 ignored│   completeness footer (§4)
└─────────────────────────────────────────────┘
```

Audience filter, sort and voter search are **deliberately not in the first
version** — see §5.3.

### 5.2 Behaviour decisions

| Question | Decision | Why |
|---|---|---|
| Percent basis | single-choice: % of voters; multi-choice: % of voters, bars may exceed 100% in sum, each row captioned "N of M voters" | §3.1 — matches how people read a poll |
| Voter list default | flat list of **all** voters, each row showing which option(s) they chose | answers "who voted for what" in one screen without drilling |
| Voter row | `UserLine` unmodified, with the vote passed as its existing `trailingContent` (§6.1) | a voter is a user; the app already has this row, and it already has the slot |
| Option chips | filter the voter list to one option; the summary block never filters | keeps totals stable while browsing |
| Muted users | excluded by default via `account.isHidden`, with a footer line "2 hidden by your mute list" | consistent with every other user list in the app |
| Ordering | you first, then follows, then pubkey — today's `filterTo` comparator, no sort control | the ordering already exists and already does the useful thing |
| Live updates | the page is a live view of `pollState().responses`; new votes animate in | it's a StateFlow already; no refresh button needed except for the relay backfill |
| Closed polls | status chip flips to "Ended · 2d ago", "Change vote" disappears, late votes excluded (§3.3) with an "N late votes excluded" footnote | spec compliance, and it explains a number that would otherwise look wrong |
| Empty state | "No votes yet" + the option rows at 0 | |

### 5.3 Deferred to a later pass

Three controls are specified here so the layout leaves room for them, but are
**not** part of the first version:

- **audience filter** (`Everyone` / `Following` / `Following + WoT`),
- **sort control** (`Newest` / `Oldest` on top of the default relevance order),
- **voter search**.

Each is cheap on its own — the follow set and the comparator both already exist —
but none of them is needed to answer "how many votes did each answer get, and who
voted for what". They land once the page is real and it's clear which of them
people actually reach for. Until then the list is ordered you → follows → rest,
and the option chips are the only filter.

## 6. Code structure

Following `commons/ARCHITECTURE.md` (state + ViewModels + shared Compose in
`commons`, screens/nav platform-native):

### 6.1 The voter row is `UserLine`, unmodified

A voter is a user, and the app already has a user row with a trailing slot:
**`UserLine`** (`amethyst/…/ui/note/creators/userSuggestions/ShowUserSuggestionList.kt:185`),
the row the mention autocomplete and the follow-import screens use. It is
`SlimListItem` (`ui/layouts/listItem/SlimListItemLayout.kt:168`) with:

| Slot | Filled with | Renders |
|---|---|---|
| `leadingContent` | `ClickableUserPicture(user, Size55dp, …)` | avatar |
| `headlineContent` | `UsernameDisplay(user, accountViewModel)` | petname → `bestName()` → `pubkeyDisplayHex()`, with custom-emoji support via `CreateTextWithEmoji` |
| `supportingContent` | `WatchAndDisplayNip05Row(user, accountViewModel)` | the NIP-05 identifier — local part, verified symbol, domain — in `colorScheme.nip05` |
| `trailingContent` | **caller-supplied, already `null`-able** | — |

```kotlin
fun UserLine(
    baseUser: User,
    accountViewModel: AccountViewModel,
    trailingContent: (@Composable (User) -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    onClick: () -> Unit,
)
```

So the results screen writes **no row at all** and modifies **no existing
composable** — it calls `UserLine` and passes the option label plus the relative
timestamp as `trailingContent`. Voter rows then inherit for free the things a
hand-rolled row silently loses: petname overrides, emoji in display names, live
NIP-05 verification state, and the npub fallback when nothing else has arrived.

Two consequences worth stating:

- **The second line is the NIP-05 identifier, not the npub.** `WatchAndDisplayNip05Row`
  (`:214`) renders `pubkeyDisplayHex()` only in the `else` branch, when the user
  has no NIP-05 — it is a fallback, not the intended content. Note the app draws
  it as *name · verified symbol · domain* with no `@` (`:236-258`), and the
  `hasLocalPart()` check means a root identifier (`_@domain`) shows the domain
  alone.
- **No bespoke "follows" or "you" chip.** No user row in the app has one, and the
  ordering (you → follows → rest) already carries that information. If a follow
  marker is wanted later it should come from the existing follow-state renderer,
  not a badge invented for this screen.

The one loose end: `UserLine` lives under `note/creators/userSuggestions/`, a
mention-autocomplete package, despite being a general-purpose row with three
callers already. Moving it to `ui/note/` is a tidy-up this change makes
worthwhile but does not require.

### 6.2 New and edited files

**New in `commons` (shared):**

| File | Contents |
|---|---|
| `model/nip88Polls/PollTallyPolicy.kt` | §3.5 |
| `model/nip88Polls/PollResponsesCache.kt` *(edit)* | poll-aware `ResponseTally`, `totalVoters()`, `totalSelections()`, `rejected`, `lateVotes` |
| `viewmodels/nip88Polls/PollResultsViewModel.kt` | holds the poll `Note`; exposes `StateFlow<PollResultsState>` (header + option rows + voter rows + completeness meta); owns the option-chip selection; drives the backfill. Sibling in style to `viewmodels/LiveStreamTopZappersViewModel.kt`, with the usual nested `Factory` |
| `relayClient/…/polls/PollResponsesFilterAssembler.kt` | §4 |
| `ui/note/polls/PollResultsHeader.kt`, `PollOptionBreakdown.kt` | slot-based shared composables for the header and the option bars, per `compose-slot-api-pattern`. **No shared voter-row composable** — each platform's own user row fills that job (§6.1) |

**New in `amethyst` (Android):**

- `ui/screen/loggedIn/polls/results/PollResultsScreen.kt` — `LoadNote(noteId) { … }`
  + `Scaffold(topBar = TopBarWithBackButton(...))` + `LazyColumn`, modelled on
  `ContactListUsersScreen.kt:51`, which is the established "users related to note
  X" screen in this codebase.
- `Route.PollResults(val noteId: String)` in `ui/navigation/routes/Routes.kt`,
  registered with `composableFromEndArgs<Route.PollResults>` in `AppNavigation.kt`
  (the slide-in detail-screen builder).

**New in `desktopApp`:**

- `DeckColumnType.PollResults(noteId)` in `ui/deck/DeckColumnType.kt` (plus the
  `title()` and `typeKey()` branches — those `when`s are exhaustive) and a render
  branch in `DeckColumnContainer.kt`. Keep `VoterListPopup` as the quick peek and
  add a **"See all voters"** footer that opens the column. Its voter rows follow
  the same rule as §6.1 against desktop's own user-row composable rather than the
  bespoke avatar+name pair in `VoterListPopup`.

**Edited (entry points):**

- `Poll.kt` — make the totals line and the `UserGallery` `+N` chip clickable →
  `nav.nav(Route.PollResults(noteId))`; add a "N votes" count next to the
  percentage (currently absent).
- Note dropdown menu — a "Poll results" item when the note is a `PollEvent`.

**Deliberately not built:** no new feed filter, no new event kind, no second
cache, no per-platform tally logic.

## 7. Zap polls (kind 6969) — phase 4

`ZapPollEvent` results are **amount-weighted** (`PollNoteViewModel.refreshTallies()`,
`ZapPollNoteViewModel.kt:102`), not one-person-one-vote, so they share the page
*shell* but not the tally:

- option rows show **sats per option** + zapper count, plus the poll's
  `consensusThreshold` drawn as a line on the bars;
- voter rows are ordered by amount and show the amount;
- private zaps are visible only where the viewer can decrypt them
  (`cachedIsPollOptionZappedBy`, `:230`) — the page must say "some zaps are
  private and not shown" rather than quietly under-count.

Sequencing it last keeps the NIP-88 work from being blocked on reconciling two
very different tally models.

## 8. Testing

- Extend `commons/src/commonTest/…/model/nip88Polls/PollResponsesCacheTest.kt`:
  single-choice response carrying 3 tags counts once (§3.2); vote after `endsAt`
  excluded and counted as late (§3.3); unknown code rejected and excluded from
  the denominator (§3.4); multi-choice `totalVoters` vs `totalSelections` (§3.1);
  policy arriving *after* responses recomputes the tally (§3.5); duplicate
  pubkey latest-wins (existing behaviour, lock it in).
- New `PollResultsViewModelTest` with Turbine: option-chip transitions, mute
  exclusion, live insertion of a new vote.
- Manual: a >100-response poll (paging), a poll whose `relay` tags the viewer
  isn't connected to (completeness), a multi-choice poll (denominators).

## 9. Phasing

| Phase | Scope | Ships value alone? |
|---|---|---|
| 0 | Poll-aware tally (§3) — commons + tests | yes — fixes the inline card's numbers on both platforms |
| 1 | Shared subscription + backfill (§4) | yes — Android tallies stop being short |
| 2 | `PollResultsViewModel` + shared composables + Android screen + route + entry points | yes — the feature |
| 3 | Desktop column | parity |
| 4 | Audience filter, sort, voter search (§5.3) | once it's clear which are actually reached for |
| 5 | Zap polls (§7) | breadth |

Phases 0 and 1 are worth landing even if the page itself is deferred: they are
bug fixes wearing a feature's clothes.

## 10. Open questions

1. **Multi-choice bars summing past 100%** — the honest rendering, but visually
   unusual. Alternative: keep selections as the basis and caption "% of
   selections". Needs a call before §3.1 lands.
2. **Should the results page be reachable for polls you haven't voted on yet?**
   The inline card deliberately hides results until you vote / opt in
   (`Poll.kt:298-336`, `hasViewedPollResults`). Proposal: the page respects the
   same gate — reaching it counts as opting in and calls `markPollResultsViewed`,
   exactly like today's "View results" link.
3. **Move `UserLine` out of `userSuggestions/`?** It is a general-purpose row
   sitting in a mention-autocomplete package (§6.1). The poll screen is its
   fourth caller. Move it to `ui/note/` with this change, or leave it and
   import across packages?
4. **Export/share results** (copy as text, or a rendered image) — desirable, but
   listed as out of scope until the page exists.
