# Push Notification Redesign — beautiful, modern, per-kind tray notifications

**Date:** 2026-07-23
**Goal:** Replace the flat, mostly-`BigTextStyle`, single-accent tray
notifications with a per-kind design system: distinct accent colors, status-bar
icons, notification *styles* (MessagingStyle / BigPictureStyle / colorized zap
cards), smart aggregation, and Conversation-section integration — so every kind
of Nostr notification (zap, reaction, reply, mention, DM, repost, nutzap, media,
git, badge, chess…) reads at a glance and looks native to modern Android
(12–15).

This is a **rendering / UX** plan. The delivery/transport layers (FCM,
UnifiedPush, the always-on relay service, gift-wrap unwrap, account matching,
mute/age/self gates) are untouched — everything below happens after
`EventNotificationConsumer.dispatchForAccount()` decides an event is notifiable.

---

## 1. Current state (what we render today)

**Pipeline (unchanged by this plan):**
`PushNotificationReceiverService` (FCM) / `PushMessageReceiver` (UnifiedPush) /
`NotificationRelayService` (always-on) → `PushWrapDecryptor.unwrapAndFeed` →
`LocalCache` → `NotificationDispatcher` (kind + age + `tagsAnEventByUser` gate) →
`EventNotificationConsumer.dispatchForAccount()` → per-kind `notify*()` →
`NotificationUtils` (`NotificationCompat.Builder`).

**What the tray looks like now:**

| Channel | Importance | Style used | Accent | Small icon | Actions |
| --- | --- | --- | --- | --- | --- |
| DMs (`PrivateMessagesID`) | HIGH | `MessagingStyle` ✅ | none | amethyst logo | Reply, Mark-read |
| Zaps (`ZapsID`) | DEFAULT | `BigTextStyle` | none | amethyst logo | — |
| Reactions (`ReactionsID`) | DEFAULT | `BigTextStyle` (+emoji avatar badge) | none | amethyst logo | — |
| Replies (`RepliesID`) | DEFAULT | `BigTextStyle` | none | amethyst logo | Reply (inline) |
| Mentions (`MentionsID`) | DEFAULT | `BigTextStyle` | none | amethyst logo | — |
| Chess (`ChessID`) | DEFAULT | `BigTextStyle` | none | amethyst logo | — |

**Problems this plan fixes:**

1. **No color identity.** Every notification uses the same monochrome amethyst
   status-bar icon and no `setColor()`. A zap, a like, and a git PR are visually
   indistinguishable in the shade until you read the text.
2. **Only DMs get a real style.** Replies, mentions, public-chat, group chat all
   fall back to `BigTextStyle` even though they are conversations that
   `MessagingStyle` renders far better (avatar + name + threaded messages, and
   it's what Android promotes into the Conversations section).
3. **Media notifications are text-only.** A `PictureEvent` / `VideoEvent`
   mention says "X mentioned you" with no preview. `BigPictureStyle` should show
   the actual image.
4. **A dozen kinds masquerade as "mentions."** Picture, video (×4), poll, git
   (×4), highlight, long-form, wiki all route to the identical
   `notifyMention()` → "X mentioned you". They deserve distinct titles/icons.
5. **No aggregation.** The in-app feed rolls up "5 people reacted / zapped your
   note" into one `MultiSetCard`; the tray posts N separate notifications. This
   is the biggest source of notification spam.
6. **Kind gaps vs. the in-app feed.** The push `when(event)` in
   `dispatchForAccount` does **not** route `NutzapEvent` (9321), `OnchainZapEvent`
   (8333), `RepostEvent`/`GenericRepostEvent` (6/16), `BadgeAwardEvent` (8),
   `LiveActivitiesChatMessageEvent` (1311), or NIP-C7 `ChatEvent` (9) — all of
   which *do* appear in the in-app Notifications tab. (`NotificationDispatcher`'s
   `NOTIFICATION_KINDS` also has to grow for these to arrive at all.)
7. **Zaps look plain.** A zap — the app's signature interaction — renders as
   grey `BigTextStyle` text. It should be a colorized gold card with the amount
   as the hero.

---

## 2. Design principles ("beautiful & modern" made concrete)

Modern Android notification design (Material 3, API 26–35) gives us six levers.
The redesign uses all six, per kind:

- **P1 — Accent color (`setColor`) + status-bar icon per category.** Each
  category gets a monochrome small icon and a brand-consistent accent so the
  shade is scannable pre-read. Palette anchored on existing tokens
  (`Color.kt`): zaps `BitcoinOrange 0xFFF7931A`, reactions a heart-red, replies
  & mentions amethyst `Purple200/500`, DMs a messaging blue-green, git a slate,
  badges a gold. Reserve `setColorized(true)` (full-surface color) for the two
  highest-signal kinds only — **zaps** and **incoming calls** (calls already
  effectively do this via `CallStyle`).
- **P2 — Right style per kind.** `MessagingStyle` for anything conversational
  (DMs, replies, mentions-that-are-replies, public/group chat); `BigPictureStyle`
  for media; a colorized `BigTextStyle` "amount card" for zaps/nutzaps;
  `InboxStyle` for aggregated roll-ups; plain for the rest.
- **P3 — Aggregation that mirrors the in-app `MultiSetCard`.** Reactions,
  reposts, and zaps on the *same target note* collapse into one updating
  notification: "🤙 Alice, Bob & 3 others liked your post." Reuse the exact
  bucketing rules from `CardFeedContentState.convertToCard` (per-target,
  per-day, chunk-of-30) so tray and feed agree.
- **P4 — Conversations & People (API 30+).** Publish a dynamic
  `ShortcutInfoCompat` per chat partner / thread, tag DM & reply notifications
  with `setShortcutId` + `addPerson`, and attach `BubbleMetadata`. This moves
  chats into the dedicated Conversations section, enables bubbles, and gives
  long-press "priority/silence" controls — the single most "modern" upgrade.
- **P5 — Circular avatars + emoji/kind badges.** Circle-crop the large-icon
  avatar (today it's the raw square bitmap); keep the existing NIP-30
  custom-emoji corner-badge overlay and extend the same overlay helper to stamp
  a small kind glyph (bolt/heart/reply) so the avatar itself signals the kind.
- **P6 — Richer, semantic actions.** Reply (have it), **Mark-read** (have it for
  DMs — extend to all), plus per-kind: **Like back** / **Zap back** on
  reaction & zap notifications, **View thread** / **Mute thread** on replies &
  mentions. All via `SEMANTIC_ACTION_*` so Wear/Auto/Assistant render them
  correctly.

---

## 3. Proposed architecture — a per-kind renderer registry

Today the logic is split between a large `when(event)` in
`EventNotificationConsumer` and six near-duplicate `send*Notification` builders
in `NotificationUtils`. We refactor to a **spec + one builder** shape (the same
idea the desktop path already uses via `notifKindFor` + `buildSpec` in
`DesktopNotificationAutoDispatcher`).

**New: `NotificationCategory` (enum) —** the visual identity, replacing the six
ad-hoc channels. Each carries: channel id/name/importance, accent color,
small-icon drawable, default style, and default group key.

```
enum class NotificationCategory {
    DIRECT_MESSAGE,   // DMs, group chat  — HIGH, blue-green, MessagingStyle, Conversation
    REPLY,            // replies to me    — DEFAULT, purple, MessagingStyle, per-thread group
    MENTION,          // text mention/quote — DEFAULT, purple, plain/MessagingStyle
    ZAP,              // ln + nutzap + onchain — DEFAULT, gold, colorized amount card, aggregated
    REACTION,         // likes/emoji      — LOW, heart-red, aggregated InboxStyle
    REPOST,           // boosts           — LOW, green, aggregated
    MEDIA,            // picture/video mention — DEFAULT, purple, BigPictureStyle
    ARTICLE,          // long-form/wiki/highlight mention — DEFAULT, purple, plain
    CODE,             // git issue/patch/PR — DEFAULT, slate, plain
    BADGE,            // NIP-58 award     — DEFAULT, gold, BigPictureStyle (badge image)
    CHESS,            // game events      — DEFAULT, brown, plain
    // calls keep their own CallNotifier / CallStyle path (already modern)
}
```

**New: `NotificationSpec` (data class) —** the fully-resolved, ready-to-render
description of one notification, produced per event:

```
data class NotificationSpec(
    val category: NotificationCategory,
    val dedupeId: String,          // event id (existing id.hashCode() keying)
    val title: String,
    val body: String,
    val timestamp: Long,
    val author: PersonRef?,        // name + avatar url  → Person / large icon
    val bigPictureUrl: String?,    // media / badge image
    val badgeUrl: String?,         // NIP-30 emoji / kind glyph overlay
    val deepLinkUri: String,       // existing nostr:...?account=...&scrollTo=...
    val groupKey: String,          // aggregation bucket (per-thread / per-target)
    val actions: List<NotifAction>,// Reply / ZapBack / Mute / MarkRead …
    val aggregatable: Boolean,     // reactions/zaps/reposts → roll-up
)
```

**New: `NotificationSpecFactory` —** one `fun specFor(event, account, ctx):
NotificationSpec?` that owns all the per-kind text/style decisions currently
scattered across `notify()/notifyReply()/notifyMention()/notifyZap()`. Returns
`null` when the event shouldn't notify. This is the single place a future kind
gets added.

**Rewritten: `NotificationUtils.render(spec)` —** one builder that reads the
spec, selects the style, applies accent/icon/colorized, attaches actions and
Person/shortcut, and handles aggregation + group summaries. The six
`send*Notification` functions collapse into this.

**Kept as-is:** `EventNotificationConsumer.dispatchForAccount` remains the gate
(mute/age/self/known-room checks) but its tail becomes
`NotificationUtils.render(NotificationSpecFactory.specFor(event, account, ctx))`.

This keeps the *policy* (who gets notified) exactly where it is and isolates the
*presentation* (how it looks) into a testable, table-driven unit.

---

## 4. Per-kind visual specification (the heart of the redesign)

| Kind(s) | Category | Style | Accent / icon | Title → Body | Actions |
| --- | --- | --- | --- | --- | --- |
| NIP-17 `ChatMessageEvent` (14), file (15), NIP-04 `EncryptedDmEvent` (4), Marmot group | DIRECT_MESSAGE | `MessagingStyle` + Conversation shortcut + Bubble | blue-green / `chat` | sender → message | Reply, Mark-read |
| `TextNoteEvent`(1)/`CommentEvent`(1111)/`ChannelMessageEvent`(42) **reply to me** | REPLY | `MessagingStyle` (parent as prior message) | purple / `reply` | "X replied" → excerpt (+ quoted parent) | Reply, View thread, Mute thread |
| Same kinds, **mention/quote only** | MENTION | plain (BigText) | purple / `alternate_email` | "X mentioned you" → excerpt | View, Mute thread |
| `ZapReceiptEvent`(9735) | ZAP | **colorized** amount card (BigText) | gold / `bolt` | "⚡ 2,100 sats from X" → zap comment + zapped-note excerpt | Zap back, View |
| `NutzapEvent`(9321) *(new)* | ZAP | colorized amount card | gold / `bolt` (cashu tint) | "🥜 X nutzapped you 2,100 sats" → … | Zap back, View |
| `OnchainZapEvent`(8333) *(new)* | ZAP | colorized amount card | gold / `bolt` | "⛓ X sent an onchain zap" → … | View |
| `ReactionEvent`(7) | REACTION | aggregated `InboxStyle` | heart-red / `favorite` | "🤙 X & N others liked your post" → note excerpt | Like back, View |
| `RepostEvent`(6)/`GenericRepostEvent`(16) *(new)* | REPOST | aggregated | green / `repeat` | "X & N others reposted you" → excerpt | View |
| `PictureEvent`(20)/`VideoNormal`/`Short`/`Vertical`/`Horizontal` | MEDIA | `BigPictureStyle` | purple / `image` | "X shared a photo/video" → caption, image inline | View |
| `PollEvent`/`ZapPollEvent` | MENTION | plain | purple / `ballot` | "X asked a question" → poll title | Vote, View |
| `HighlightEvent`(9802) | ARTICLE | plain | purple / `format_quote` | "X highlighted your article" → highlighted text | View |
| `LongFormContentEvent`(30023)/`WikiArticleEvent`(30818) | ARTICLE | plain | purple / `article` | "X mentioned you in an article" → title | View |
| `GitIssueEvent`/`GitPatchEvent`/`GitPullRequestEvent`/`…Update` | CODE | plain | slate / `merge` | "X opened an issue/PR" → subject | View |
| `BadgeAwardEvent`(8) *(new)* | BADGE | `BigPictureStyle` (badge art) | gold / `award_star` | "You earned a badge" → badge name, image | View |
| `LiveChessGameAcceptEvent`/`LiveChessMoveEvent` | CHESS | plain | brown / `chess` | "X accepted your challenge" / "your turn" | Open board |
| `CallOfferEvent` | (unchanged — `CallNotifier`/`CallStyle`) | — | — | — | Accept/Reject |

Titles/bodies stay in `strings.xml` (translatable) with plurals for the
aggregate counts (see §7).

---

## 5. Channel restructuring

Channels are a **user-visible, migration-sensitive** surface (users may have
customized importance/sound). Plan:

- **Keep** the six existing channel IDs (DMs, Zaps, Reactions, Replies,
  Mentions, Chess) — never rename an ID, it orphans user settings.
- **Add** channels for the newly-rendered categories that don't map cleanly onto
  an existing one: **Reposts** (`RepostsID`, LOW), **Media** (`MediaID`,
  DEFAULT), **Git/Code** (`CodeID`, DEFAULT), **Badges** (`BadgesID`, DEFAULT).
  Nutzaps/onchain fold into the existing **Zaps** channel; articles/highlights
  fold into **Mentions**.
- **Group channels** with `NotificationChannelGroup` ("Social", "Payments",
  "Messages", "Developer") so the system settings page and our in-app
  `NotificationSettingsScreen` read as organized rather than a flat list of 10.
- Register every new channel in `NotificationChannels.contentChannels` (drives
  the settings UI) with its Material Symbol + name, matching the existing
  `Entry` pattern.
- Reactions/Reposts drop to `IMPORTANCE_LOW` (silent, no heads-up) — they're
  ambient; zaps/DMs/replies stay DEFAULT/HIGH.

---

## 6. Capability upgrades (implementation notes)

- **Accent + icon:** add monochrome vector status-bar icons under
  `amethyst/src/main/res/drawable/` (they must be white-on-transparent per
  Android's small-icon rules — *not* the app logo). `builder.setColor(accent)`;
  `setColorized(true)` only for ZAP + CALL. Store accent per category on the
  enum.
- **MessagingStyle for replies/mentions/chat:** generalize the existing
  `sendDMNotificationStyled` `MessagingStyle` construction into
  `render()` for every conversational category. For replies, add the parent note
  as an earlier `addMessage(...)` from the original author so the thread shows
  context. Reuse the existing `Person` + avatar `IconCompat` code.
- **BigPictureStyle:** in `render()`, when `spec.bigPictureUrl != null`, load it
  via the existing Coil `loadBitmap` helper and
  `NotificationCompat.BigPictureStyle().bigPicture(bmp)` with the avatar as
  large icon; collapse to `BigTextStyle` if the image fails to load.
- **Aggregation:** introduce a small `NotificationAggregator` keyed by
  `groupKey` (per-target-note for reactions/zaps/reposts). On each new event it
  reads `activeNotifications` for the group, recomputes the roll-up
  (participants + count), and re-`notify()`s the same id with an updated
  `InboxStyle`/title — mirroring `CardFeedContentState`'s per-target buckets.
  Falls back to a single notification below the threshold. Keeps the existing
  `sendGroupSummary` behavior for cross-target bundling.
- **Circular avatars:** add a `circleCrop(bitmap)` step in `loadBitmap` (reuse
  the `Canvas` approach already in `overlayBadge`). Kind-glyph overlay reuses
  `overlayBadge`.
- **Conversations/Bubbles:** publish `ShortcutInfoCompat` (long-lived, with the
  partner `Person`) when a DM/reply notification posts; set `shortcutId`,
  `addPerson`, and `BubbleMetadata` pointing at a
  `MainActivity`/`CallActivity`-style chat entry. Guard by API level.
- **Dedup / dismiss-on-read:** unchanged (`isDuplicate`,
  `dismissNotificationForEvent` by `eventId.hashCode()`); aggregation reuses a
  stable per-group id so updates replace rather than stack.

---

## 7. Icons & strings work (required side-tasks)

- **Icons (two kinds):**
  1. **Status-bar small icons** are Android drawables (white glyphs) — new
     vector XML in `res/drawable/`. *Not* Material Symbols font glyphs.
  2. **Settings-list icons** in `NotificationChannels` use `MaterialSymbols`. If
     any new `MaterialSymbol("\uXXXX")` codepoint is introduced (e.g. `Repeat`,
     `Merge`, `AwardStar`, `Ballot`, `Image`) that isn't already referenced,
     **regenerate the subset font**: `./tools/material-symbols-subset/subset.sh`
     and commit `material_symbols_outlined.ttf` — otherwise it renders as tofu.
- **Strings:** new titles per kind (repost, media, badge, poll, highlight, git,
  nutzap, onchain) as translatable resources; aggregate counts become
  `<plurals>` (`"%1$s & %2$d others liked your post"`) with the CLDR-category
  discipline from `res/CLAUDE.md` (Slavic/Baltic/Arabic `few`/`many`/`zero`).
  Use the `pluralStringRes(ctx, …)` helper (non-Compose) in the service path.

---

## 8. Parity gaps to close (behavioral, not just visual)

For these to *render*, two lists must include the kind **and**
`NotificationSpecFactory` must handle it:

- `NotificationDispatcher.NOTIFICATION_KINDS` (Android gate) — add
  `NutzapEvent`, `OnchainZapEvent`, `RepostEvent`, `GenericRepostEvent`,
  `BadgeAwardEvent`, `LiveActivitiesChatMessageEvent`, NIP-C7 `ChatEvent`.
- `commons/.../NotificationKinds.SUBSCRIPTION_KINDS` (relay REQ list) — must
  already carry them so they arrive; verify against
  `NotificationKindsContractTest` and extend if needed.
- Keep the in-app `NotificationFeedFilter.NOTIFICATION_KINDS` and the push path
  in lockstep — the contract test should assert push-rendered ⊆ in-app-shown.

Decision needed (flagged for the maintainer, §11): reposts/reactions are
*aggregate-only* by design — confirm we want them in the tray at all (many users
consider like/repost notifications noise; hence `IMPORTANCE_LOW` + off-by-default
mirroring the desktop `KindToggles` where reaction/repost default off).

---

## 9. Phased implementation

- **Phase 0 — Refactor to spec+builder (no visual change).** Introduce
  `NotificationCategory`, `NotificationSpec`, `NotificationSpecFactory`,
  `NotificationUtils.render(spec)`; port the six existing kinds onto it. Prove
  parity with existing behavior via unit tests. *No user-visible diff.*
- **Phase 1 — Color & icons.** Accent + status-bar icon per category, circular
  avatars, colorized zap card. Highest visual ROI, lowest risk.
- **Phase 2 — Styles.** MessagingStyle for replies/mentions/chat;
  BigPictureStyle for media & badges.
- **Phase 3 — Aggregation.** Roll-up for reactions/reposts/zaps via
  `NotificationAggregator`; new Reposts channel; LOW importance for ambient
  kinds.
- **Phase 4 — Parity kinds.** Route nutzap, onchain zap, repost, badge, live
  chat; grow the dispatcher/subscription kind lists; strings + plurals.
- **Phase 5 — Conversations/Bubbles/People + richer actions.** Shortcuts,
  bubbles, Zap-back / Like-back / Mute-thread actions.
- **Phase 6 — Channel groups + settings polish.** `NotificationChannelGroup`s;
  update `NotificationSettingsScreen`.

Each phase is independently shippable and revertible.

---

## 10. Testing

- **JVM unit tests** for `NotificationSpecFactory.specFor(...)` — table-driven,
  one fixture event per kind asserting category/title/body/style/actions. This
  is the regression net that lets the six-kind refactor land safely.
- **Contract test** extending `NotificationKindsContractTest`: every kind the
  factory produces a spec for is in `NOTIFICATION_KINDS` and `SUBSCRIPTION_KINDS`.
- **Aggregation tests:** feed 1 → 5 reactions on one note, assert a single
  updating notification with correct count/participants (mirror
  `CardFeedContentState` test fixtures if they exist).
- **Manual matrix** on Android 12 / 14 / 15 (and one heavily-skinned OEM):
  screenshot each category light+dark, verify color/icon/heads-up/lockscreen
  (public version) and Conversation-section placement for DMs.
- **`amy`**: no CLI surface for tray rendering, but the spec factory is pure
  JVM and can be exercised headless.

---

## 11. Risks & decisions for the maintainer

- **Notification volume.** Aggregation + LOW-importance reactions/reposts are
  the mitigation; still, **confirm** reactions/reposts belong in the tray
  (default-off recommended, matching desktop).
- **Small-icon assets.** Must be true monochrome white glyphs; the current
  colored amethyst logo is *not* a valid small icon and is the reason the shade
  looks flat today. Needs new drawables.
- **Channel proliferation.** Ten channels risks a cluttered settings page —
  hence `NotificationChannelGroup`s; alternatively keep the six and only vary
  color/icon/style within them (color/style don't require a new channel; only
  independent user importance/sound control does). *Recommend: add only
  Reposts + Media + Badges channels, fold the rest.*
- **Font subset drift.** Any new Material Symbol must trigger `subset.sh` or it
  renders as tofu — easy to forget; call it out in the PR checklist.
- **Marmot/encrypted content.** MessagingStyle/Bubbles must not leak decrypted
  DM/group text to the lockscreen public version — the existing
  `setPublicVersion` "New notification arrived" placeholder must be preserved for
  all private categories.

---

## Survey (existing components reused vs. new)

- **Reused as-is:** the entire transport + gate layer (`PushWrapDecryptor`,
  `NotificationDispatcher` gates, `EventNotificationConsumer` mute/age/self/
  known-room checks), Coil `loadBitmap`, `overlayBadge`, `isDuplicate`,
  `dismissNotificationForEvent`, `NotificationReplyReceiver` (extended for
  new actions), `NotificationChannels`/`NotificationSettingsScreen` structure.
- **Extracted/generalized:** the `MessagingStyle` construction from
  `sendDMNotificationStyled` becomes the shared conversational renderer; the six
  `send*Notification` builders collapse into `render(spec)`.
- **Genuinely new (Android-only, correct module):** `NotificationCategory`,
  `NotificationSpec`, `NotificationSpecFactory`, `NotificationAggregator`,
  status-bar drawables, channel groups, shortcut/bubble publishing.
- **Aggregation logic** is a port of `CardFeedContentState.convertToCard`
  bucketing — same rules, tray output instead of `Card`s. Consider extracting
  the bucketing to `commons` so feed and tray share one implementation.
