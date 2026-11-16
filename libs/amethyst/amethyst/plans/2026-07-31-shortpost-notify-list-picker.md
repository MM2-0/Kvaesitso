# Adding a whole people-list to a post's Notify / "Visible to" audience

**Status: shipped** (P1 + P2, plus the visual direction below). Landed as
`AudienceSelection.kt` / `AudienceFlap.kt` / `AudienceSheet.kt` in
`amethyst/…/ui/note/creators/notify/`, with bulk mutators on
`ShortNotePostViewModel` and 13 JVM tests in `AudienceSelectionTest`.

Still open, in rough priority order:

- **P3 — "Last private note" entry** in the sheet, reusing the previous send's
  audience. The most-requested shape ("same people as last time") and the
  cheapest remaining win.
- **P3 — the other composers.** `Notifying()` is untouched, so the comment
  composer (`GenericCommentPostScreen`) and the group DM composer's To row
  (`SendDirectMessageTo`) still use the old flat row. They can adopt
  `AudienceFlap` unchanged.
- **Provenance is compose-session-only** — it resets on draft load, so a
  bulk-added group chip does not survive a draft round trip (open question 2
  below, answered "session-only" for now). The audience itself round-trips
  fine; only the chip's undo affordance is lost.
- **Kind-3 follows are not offered** as a catalog entry. The sheet's search
  finds individuals, but "everyone I follow" is deliberately absent given the
  caps.

## Goal

In the short-note composer (`ShortNotePostScreen`), the **Notify** row already lets
you p-tag individual users one at a time. When the lock chip
(`AddPrivateNoteButton`) is on, that same row is relabeled **"Visible to"** and
*becomes the audience* of the gift-wrapped note.

Today the only way to fill it is `Add` → search → pick, one user per round trip.
This proposes an interface to add **every member of one of the user's people
lists / follow packs** in a single gesture, with a review step, and without
turning a 40-person list into an unusable wall of chips or a 40× signer prompt
storm.

## What exists today (reuse — do NOT rebuild)

| Need | Reuse |
|---|---|
| The chip row + "Add" chip | `ui/note/creators/notify/Notifying.kt` (`Notifying`, `NotifyUserChip`, `AddUserChip`) |
| Audience state | `ShortNotePostViewModel.pTags: List<User>?`, `mutedNotifies: Set<HexKey>`, `activeNotifies()`, `addToReplyList(user)` |
| My NIP-51 people lists (kind 30000, public **and** decrypted private members) | `account.peopleLists.uiListFlow: StateFlow<List<PeopleList>>` (`model/nip51Lists/followSets/FollowSetsState.kt`) |
| My follow packs (kind 39089, public members) | `account.followLists.uiListFlow` (`model/nip51Lists/followSets/StarterPacksState.kt`) |
| `PeopleList` UI model (`title`, `image`, `publicMembers`, `privateMembers` as `Set<User>`) | `model/nip51Lists/followSets/PeopleList.kt` |
| Two-column list catalog rendering | `ui/screen/loggedIn/lists/memberEdit/FollowListAndPackAndUserView.kt` — same "Follow sets" + "Discover follows" sectioning |
| Multi-select member review (count header, select-all checkbox, per-user row, confirm button) | `ui/screen/loggedIn/newUser/ImportFollowListPickFollowsScreen.kt` (`PreviewList` / `FollowEntryRow`) |
| Bottom-sheet picker shell w/ search field | `ui/screen/loggedIn/chats/publicChannels/relayGroup/RelayGroupParentPicker.kt` |
| Per-user DM deliverability | `User.dmInboxRelayList()` (used by `Account.computeRelayListToBroadcast`) |
| Relay hints on the outgoing `p` tags | already done at build time in `createTemplate()` via `LocalCache.relayHints` — the picker adds nothing |
| Icons | `MaterialSymbols.Groups`, `.GroupAdd`, `.Checklist` already in `MaterialSymbols.kt` → **no font subset regeneration needed** |

Genuinely new: the picker sheet, a bulk mutator on the ViewModel, chip-row
overflow, and the safety rails below.

## Constraints that shape the design

1. **A private note costs one seal + one wrap per recipient.**
   `Account.sendPrivateNote` → `NIP17Factory.createSeals(...)` builds an
   `AddressedSeal` per recipient, each needing a `nip44Encrypt` **and** a `sign`.
   With a local key that's cheap; with a **NIP-46 bunker** it is 2 RPCs per
   recipient throttled to `BUNKER_PARALLELISM = 4`, and with a **NIP-55 external
   signer** it is 2 IPC round trips per recipient. "Add my 200-follow pack" to a
   private note is not a neutral action — it must be capped and confirmed.
2. **The audience is not secret from the audience.** The inner rumor carries a
   `p` tag per recipient, so every recipient learns the full recipient list.
   Adding the **private** members of a kind-30000 list therefore de-privatizes
   them to everyone else on the note. That needs an explicit, visible opt-in —
   never a silent bulk add.
3. **Members without a NIP-17 DM inbox relay may not receive the wrap.**
   `computeRelayListToBroadcast` falls back to the recipient's linked relays,
   which is best-effort. The picker should surface this *before* sending, not as
   a silent partial delivery.
4. **`Notifying` is a `FlowRow` of full-width name chips.** 30 chips push the
   message field off screen. Bulk add forces a collapsed representation.
5. **Public posts are not exempt.** With the lock **off**, the same row is
   "Notify" — bulk-p-tagging 50 people is a notification-spam vector. The same
   cap applies, with different copy.
6. **Drafts round-trip the audience.** `pTags`/`mutedNotifies` are already
   serialized into and restored from the draft (`ShortNotePostViewModel` load
   path); a bulk add must go through the same state so drafts keep working.

## Proposed interface

### 1. Entry point — a second chip in the Notify row

`Notifying(...)` gains an optional `onAddList: (() -> Unit)? = null` slot,
rendered as an `AssistChip` immediately after the existing `Add` chip:

```
Visible to  [🔔 alice ✕] [🔔 bob ✕]  [＋ Add]  [👥 Add list]
```

Nothing changes when `onAddList` is null, so the composables that reuse
`Notifying` (DM composer, comment composer) are untouched.

`ShortNotePostScreen` passes
`onAddList = { postViewModel.wantsToPickNotifyList = true }`.

### 2. The picker — `NotifyListPickerSheet` (ModalBottomSheet, two steps)

**Step 1 — catalog.** Mirrors `FollowListAndPackAndUserView`'s sectioning, with a
search field like `RelayGroupParentPicker`:

```
┌ Add people from a list ───────────────────────┐
│ 🔍 Search lists                               │
│                                               │
│ FOLLOW SETS                                   │
│  👥 Close friends            12 · 🔒 3        │
│  👥 Work                     8                │
│  👥 Nostrdevs                41  ⚠ over limit │
│                                               │
│ FOLLOW PACKS                                  │
│  👥 Bitcoin builders         27               │
│                                               │
│ OTHER                                         │
│  👤 People I follow          312 ⚠ over limit │
│  ⏱ Last private note (5)                      │
└───────────────────────────────────────────────┘
```

- Counts are `publicMembers.size` (+ a lock badge with `privateMembers.size`).
- Kind-3 follows are listed but always land on the review step with **nothing
  pre-selected** — it exists so you can search within your follows, not to bulk
  add 300 people.
- "Last private note" (phase 3) reuses the previous send's audience — the most
  common real-world request ("same people as last time").

**Step 2 — member review.** Reuses the `PreviewList` pattern verbatim
(`accounts_found` / `num_selected` header, select-all checkbox, `LazyColumn` of
rows, confirm button):

```
┌ Close friends ─────────────── 12 found · 9 selected ┐
│ ☑ Select all                                        │
│ ─────────────────────────────────────────────────── │
│ ☑ 🖼 alice                                          │
│ ☑ 🖼 bob            already added                    │
│ ☐ 🖼 carol          🔒 private member of this list   │
│ ☑ 🖼 dave           ⚠ no DM inbox relay             │
│ ☐ 🖼 erin           muted                           │
│ ─────────────────────────────────────────────────── │
│ ⚠ Everyone on this note sees the full recipient list │
│                          [ Add 9 people ]           │
└─────────────────────────────────────────────────────┘
```

Row rules:

| Row state | Default | Behavior |
|---|---|---|
| Ordinary public member | selected | — |
| Already in `pTags` | selected, checkbox disabled | shown so the count reads true; adding is a no-op |
| **Private** member of the list | **deselected** | badge + one-line explainer; selecting it is the explicit opt-in required by constraint 2 |
| No DM inbox relay (only shown while the lock is on) | selected | ⚠ badge; a "Deselect N without inbox relays" quick action sits under the header |
| Muted / blocked by me | **deselected** | badge |

The bottom warning line renders **only when the lock is on**, and the confirm
button is disabled at 0 selected.

### 3. Chip-row overflow

Once `pTags.size > CHIP_COLLAPSE_THRESHOLD` (start at 6), `Notifying` renders the
first N chips plus a `[+K more]` `AssistChip` that expands the row in place. Add
a `[Clear all]` chip in the expanded state. This is a change to `Notifying` and
benefits the existing one-by-one flow too.

### 4. Provenance (recommended, small)

Snapshot semantics: selecting a list **expands into individual `pTags`
immediately** — the event tags individual pubkeys, the user must see exactly who
receives it, and per-person removal must keep working. A "live list reference
resolved at send time" is rejected: the audience would silently change between
compose and send.

But keep a display-only provenance map so a bulk add can be undone as a unit:

```kotlin
// pubkey -> the list dTags it arrived from. Display + undo only; never read
// when building the event.
var notifyProvenance by mutableStateOf<Map<HexKey, Set<String>>>(emptyMap())
```

which lets the row show a removable group chip when a whole list is present:

```
Visible to  [👥 Close friends (9) ✕] [🔔 zoe ✕] [＋ Add] [👥 Add list]
```

`✕` removes exactly the pubkeys whose provenance is *only* that list, leaving
individually-added and multi-list people in place. This is the single feature
that makes "add all the users of a list" feel like a list operation rather than
a paste. If it has to be cut for phase 1, the flat chips + overflow still work.

## ViewModel changes (`ShortNotePostViewModel`)

```kotlin
var wantsToPickNotifyList by mutableStateOf(false)

/**
 * Bulk sibling of [addToReplyList]. One state write for the whole batch: N
 * individual writes would trigger N recompositions of the chip row and N
 * draft-version bumps.
 */
fun addAllToReplyList(users: Collection<User>, fromListTag: String? = null) {
    if (users.isEmpty()) return
    val current = pTags ?: emptyList()
    val known = current.mapTo(mutableSetOf()) { it.pubkeyHex }
    pTags = current + users.filter { known.add(it.pubkeyHex) }
    mutedNotifies = mutedNotifies - users.mapTo(mutableSetOf()) { it.pubkeyHex }
    fromListTag?.let { tag ->
        notifyProvenance = notifyProvenance.toMutableMap().apply {
            users.forEach { merge(it.pubkeyHex, setOf(tag)) { a, b -> a + b } }
        }
    }
    draftTag.newVersion()
}

fun removeFromReplyList(users: Collection<User>) { /* mirror, for the group ✕ */ }
```

`cancel()` / `load(draft)` reset `notifyProvenance` alongside `pTags` and
`mutedNotifies`.

## Safety rails

Two constants, both applied to `activeNotifies().size` *after* the add:

- `NOTIFY_SOFT_CAP = 25` — the confirm button in the sheet turns into a
  confirmation: private → *"This note will be encrypted and sent 28 separate
  times, once per person. With an external signer this means 28 approvals."*;
  public → *"28 people will get a notification for this post."*
- `NOTIFY_HARD_CAP = 100` — selection above this is blocked with an explanatory
  line rather than silently truncated.

Both are tunable; the point is that the cost in constraint 1 is disclosed at the
moment of the bulk action rather than discovered as a hung signer dialog.

Additionally: the catalog marks any list whose member count exceeds the hard cap
with `⚠ over limit` and opens it with nothing pre-selected.

## Where the code goes

```
amethyst/…/ui/note/creators/notify/
├── Notifying.kt                    (edit: onAddList slot, overflow, group chip)
├── NotifyListPickerSheet.kt        (new: catalog + review sheet)
└── NotifyListSelection.kt          (new: pure state holder — filtering,
                                     counts, badge computation, cap checks)
amethyst/…/ui/screen/loggedIn/home/
├── ShortNotePostScreen.kt          (edit: wire onAddList, host the sheet)
└── ShortNotePostViewModel.kt       (edit: bulk mutators + provenance + flag)
```

Kept in `amethyst/` for now because `peopleLists` / `followLists` live on the
Android `Account` and have no `commons` equivalent (verified: no references in
`commons/` or `desktopApp/`). `NotifyListSelection` is deliberately a plain,
Compose-free state holder over `List<PeopleList>` + `Set<HexKey>` so it can move
to `commons/…/viewmodels/` unchanged the day the desktop composer wants the same
picker.

## Strings (new)

`notify_add_from_list`, `notify_list_picker_title`, `notify_list_picker_search`,
`notify_list_section_follow_sets`, `notify_list_section_follow_packs`,
`notify_list_section_other`, `notify_list_all_follows`,
`notify_list_member_private_badge`, `notify_list_member_private_explainer`,
`notify_list_member_no_inbox_relay`, `notify_list_member_already_added`,
`notify_list_member_muted`, `notify_list_deselect_no_inbox`,
`notify_list_audience_is_public_to_recipients`, `notify_list_add_n_people`,
`notify_list_over_limit`, `notify_list_soft_cap_private`,
`notify_list_soft_cap_public`, `notify_list_hard_cap`, `notify_chips_more`,
`notify_chips_clear_all`, `notify_group_chip_remove`.

Reused as-is: `accounts_found`, `num_selected`, `select_all`, `feed_is_empty`,
`follow_sets`, `discover_follows`.

## Phasing

- **P1** — `onAddList` chip, catalog + review sheet over people lists and follow
  packs, `addAllToReplyList`, chip overflow, both caps. This is the whole ask.
- **P2** — provenance group chip + unit removal; "deselect all without inbox
  relay" quick action.
- **P3** — "Last private note" reuse entry; the same picker wired into the group
  DM composer's To row (`SendDirectMessageTo`) and the comment composer, since
  all three share `Notifying`.

## Test plan

JVM unit tests on `NotifyListSelection` (no Compose needed):

- dedupe against existing `pTags`; already-added members don't inflate the count.
- private members start deselected; selecting them is what puts them in the result.
- muted/blocked start deselected.
- hard cap blocks, soft cap flags, neither truncates silently.
- `addAllToReplyList` is idempotent and un-mutes previously muted members.
- draft round-trip: bulk-added audience survives `sendDraftSync()` → `load(draft)`.

Manual: 12-person list into a private note with an external signer — confirm the
approval count matches the disclosed number, and that recipients without a DM
inbox relay were flagged before send.

## Visual direction — the row itself needs a redesign

Bolting a second chip onto today's Notify row makes an already weak surface
worse, so the picker should land together with a redesign of the row. The
governing idea: **when the note is sealed, the composer should look like an
envelope, and the audience should be the flap.** Seven moves, each independently
shippable, each using a component the app already has:

1. **A container, not a row.** Today the bold grey label and the chips are
   siblings in one `FlowRow` — same weight class, no boundary, so it reads as
   loose fragments floating above the message. Wrap them in a tinted rounded
   Surface that sits flush on top of the message body. Public mode leaves it
   untinted and borderless, so ordinary posts gain nothing they didn't ask for.
2. **Faces at rest, chips only while editing.** A chip is avatar + display name +
   bell ≈ 180dp; three people wrap the row and twelve bury the message field.
   At rest show a **facepile** — overlapping 24dp avatars plus "Alice, Bruno & 7
   others" — identical in height at 3 people or 90. `Poll.kt`'s `UserGallery`
   (`take(4)`, `spacedBy((-10).dp)`, "+N" bubble) already does exactly this.
3. **Muted must not look broken.** `Modifier.alpha(0.4f)` is Android's universal
   *disabled* signal; using it for a deliberate, reversible state makes a working
   feature look like a rendering bug. Use an unfilled chip with a struck bell and
   full-contrast text — "switched off", not "greyed out".
4. **One way in, not two competing chips.** `Add` is an `AssistChip` of the same
   weight as the people beside it, so the action competes with the data — and the
   list feature would add a second one. Collapse both into a single `＋` on the
   flap that opens the one sheet (search + lists + per-person switches).
   `Notifying(onManage: () -> Unit)` replaces `onAddUser`/`onAddList`.
5. **The empty state is an invitation, not a paragraph.**
   `R.string.private_note_no_receivers` is two lines of grey body copy where a
   button belongs. Replace with one accent-coloured tappable line sitting exactly
   where the faces will appear: *"Nobody yet — choose who can see this."*
6. **Make the mode change a moment.** Going from "broadcast to the network" to
   "encrypted to nine people" currently tints one 22dp icon among eleven
   identical siblings. Choreograph it once, ~300ms: flap expands and tints, lock
   glyph closes, the strip's lock takes a filled pill, the send button relabels to
   **Send privately**, one haptic tick. `AnimatedVisibility` +
   `animateColorAsState` + `LocalHapticFeedback` — all already used in the app.
7. **A whole list arriving should feel like an arrival.** Twelve chips appearing
   at once feels like a paste; twelve faces landing 40ms apart feels like a guest
   list filling up. Pair it with the removable group chip from the provenance
   section so the whole add is one tap to undo.

No new icons: `Lock`, `LockOpen`, `Groups`, `PersonAdd`, `NotificationsOff` and
`Check` are all already referenced in `MaterialSymbols.kt`, so the bundled subset
font does not need regenerating.

Ship order: **01–03** stand alone and fix the ugliness without any new feature;
**04–05** land with the picker; **06–07** are the polish pass.

All seven shipped together, with two deviations worth recording:

- **Move 05 kept a fact the short copy would have dropped.** The old paragraph
  said "only you will be able to see this note" — true, since `canPost()` does
  not gate a private note on having recipients, so a sealed note with an empty
  audience really does go only to its author. The invitation therefore reads
  *"Only you — choose who else can see this"* rather than *"Nobody yet"*, and
  the same line now covers the everyone-muted case, which is the same situation
  by a different route.
- **Move 07's stagger is not implemented.** The group chip and the one-tap undo
  shipped; the sequenced arrival of the faces did not, because the facepile is
  a plain `Row` rather than a lazy list, so there is no `animateItem` to hang it
  on. It needs a keyed `AnimatedVisibility` per face — worth doing, but it is
  decoration, and the rest of the move (provenance, undo) is the part that
  carries meaning.

Interactive mockups (before/after, live private-mode transition):
<https://claude.ai/code/artifact/b4f8941f-b787-4355-9c12-c1b238538fe9>

## Open questions

1. Should the private-member opt-in be per-user (as proposed) or a single
   list-level "include 3 private members" toggle? Per-user is safer; list-level
   is fewer taps.
2. Do we want the group chip to survive a draft round trip (provenance
   serialized into the draft), or is it a compose-session-only affordance?
3. Is 25 the right soft cap for a *public* post's Notify row, or should public
   notify be capped lower given it is pure notification spam?
