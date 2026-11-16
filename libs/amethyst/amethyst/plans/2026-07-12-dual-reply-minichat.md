# Dual-mode replies: inline + "minichat" threads across all chats

## Goal

Give every Amethyst chat two ways to reply, chosen at send time:

- **Inline reply** — a normal chat message that references its parent and stays in
  the main timeline (today's behavior). On the wire this is the chat protocol's
  native reply: NIP-C7 kind-9 with a `q` quote (Concord), kind-42 reply (NIP-28),
  kind-9 `+h` reply (NIP-29), kind-14 reply (NIP-17 DM).
- **Minichat reply** — a **kind-1111 NIP-22 `CommentEvent`** rooted at the parent
  message. It is pulled *out* of the main timeline and shown in a separate
  **minichat** ("chat within a chat") opened from the parent. This matches Soapbox
  Armada exactly (kind-9 `q` = inline quote, kind-1111 = thread).

The rule is uniform and protocol-agnostic: **any kind-1111 whose root is a chat
message opens as that message's minichat.** So the same treatment automatically
covers Concord kind-9, NIP-28 kind-42, NIP-29 kind-9, and (later) NIP-17 kind-14 —
wherever a 1111 lands on a chat message.

## Reuse survey (what already exists — do NOT rebuild)

| Need | Reuse |
|---|---|
| kind-1111 reply builder (NIP-22 `K/E/P`+`k/e/p`) | `quartz/.../nip22Comments/CommentEvent.replyBuilder`; Concord's `ChannelChat.reply` already uses it |
| 1111 → parent wiring | `LocalCache.computeReplyTo` (CommentEvent branch) → `parentNote.replies`; minichat content = `note.replies.filter { it.event is CommentEvent }` |
| "N replies" chip | `observeNoteReplyCount(note, avm)` (EventObservers.kt) — already used by `RelayGroupThreadsScreen` |
| Shared per-row action strip | `ChatMessageCompose.NormalChatNote` `detailRow` `Row` — one place, every chat type |
| Thread rendering | `threadview/ThreadFeedView` + `ThreadAssembler.findThreadFor`; NIP-29 `RelayGroupThreadsScreen` as the chat-adjacent precedent |
| Per-message 1111 REQ (public chats) | `FilterRepliesAndReactionsToNotes` (kinds incl 1111, `#e`) via `EventFinder`; `RelayGroupThreadFeedFilterAssembler` (compose-scoped `#h`+1111 sub) |
| Composer reply state + "replying-to" preview | `*NewMessageViewModel.replyTo` + `chats/utils/DisplayReplyingToNote` |
| NIP-22 comment composer | `note/nip22Comments/CommentPostViewModel` (full-featured) |

Concord already delivers kind-1111 replies through the existing channel-plane
subscription (they're wrapped like every other rumor), so **no new subscription is
needed for Concord** — only the timeline split, the chip, the minichat screen, and
the composer picker.

## Design

### 1. Wire model (settled — matches Armada)
- Inline reply → native chat reply event, native reply tags, stays in timeline.
- Minichat reply → kind-1111 `CommentEvent`: uppercase `K/E/P` at the immutable
  thread root (the chat message), lowercase `k/e/p` at the immediate parent, plus
  whatever binding the plane requires (Concord: `channel`/`epoch`). One level:
  replying inside a minichat roots the new 1111 at the **same** root message
  (parent = the message being answered, root = the minichat root), rendered flat —
  so minichat messages don't spawn sub-threads. (The wire still permits nesting;
  we render flat.)

### 2. Timeline vs minichat split (rendering)
- **Main feed** excludes kind-1111 comments whose root is a chat message — they
  live in the minichat, not as flat siblings. Implemented in the shared
  `ChannelFeedFilter` / `ChatroomFeedFilter` by dropping `CommentEvent`s that root
  onto a message already in the feed (keep everything else).
- Each root message row shows an **"N replies" chip** (from `observeNoteReplyCount`
  restricted to CommentEvent replies) in the `detailRow` strip; tap → minichat route.

### 3. Minichat screen
- A thread screen keyed by the **root message id** (+ the channel/room key needed to
  re-derive the plane / re-subscribe). Renders the root message pinned at top, then
  its kind-1111 replies as a flat mini-timeline (reuse `ChatroomMessageCompose`), with
  its own composer that always sends kind-1111 rooted at this message.
- Back it with `ThreadFeedView`/`ThreadAssembler` where possible; for Concord, feed
  it from `rootNote.replies` (already populated) + a lifecycle sub that keeps the
  plane live.

### 4. Composer mode picker
- Add `replyMode: ReplyMode {INLINE, MINICHAT}` next to `replyTo` in each
  `*NewMessageViewModel` (Concord `ConcordNewMessageViewModel`, DM
  `ChatNewMessageViewModel`, channels `ChannelNewMessageViewModel`).
- Render a small toggle beside `DisplayReplyingToNote` ("Reply in chat" ⇄ "Reply in
  thread"). Default = INLINE (least surprise; user opts into pulling it aside).
- Send branch: `MINICHAT` routes to the kind-1111 builder
  (`CommentEvent.replyBuilder` / Concord `buildChannelReply`), `INLINE` keeps the
  native reply builder.

### 5. Subscriptions
- **Concord**: none new (1111 arrives via the channel plane). Just ensure the
  timeline filter and minichat read `rootNote.replies`.
- **NIP-28 / NIP-29 (phase 2)**: add a compose-scoped assembler (clone
  `RelayGroupThreadFeedFilterAssembler`) that REQs `{kinds:[1111], "#e":[<visible
  message ids>]}` (and `#E`) off the feed's current message-id set (from
  `FeedContentState`). Reuse the same minichat screen/row.
- **NIP-17 DM (phase 3, later)**: kind-1111 replies must be gift-wrapped like the
  kind-14s; deferred — needs an encrypted-comment path, more design.

## Phasing

1. **Phase 1 — Concord, full UX + all shared pieces.** ReplyMode enum + composer
   toggle; timeline split (drop chat-rooted 1111s); "N replies" chip in the shared
   `detailRow`; minichat route + screen; Concord send branch. Delivers the complete
   dual-mode experience for Concord and builds every shared component.
2. **Phase 2 — public chats.** Per-message 1111 subscription for NIP-28 + NIP-29;
   reuse the Phase-1 chip/screen/composer. NIP-29 already has a thread screen to
   reconcile with.
3. **Phase 3 — DMs.** Gift-wrapped kind-1111 minichat for NIP-17. Deferred.

## Decisions (settled)
- **Default mode** when tapping reply: **INLINE**. User opts into MINICHAT via the toggle.
- **Minichat depth**: **flat, one level**. Replying inside a minichat roots at the
  same message; no sub-threads.
- **Scope now**: **Phase 1 + 2 together** — Concord AND public chats (NIP-28/NIP-29).
  DMs (phase 3) still deferred.
- **Screen styling**: **chat-styled bubbles** (reuse `ChatroomMessageCompose`) so the
  minichat reads as "a chat within a chat".

## Verification
- quartz/commons unit tests for the reply-mode builders + the timeline-filter split
  (a chat-rooted 1111 is excluded from the feed but present in `rootNote.replies`).
- On-device: in Concord, reply inline (stays in timeline) and reply-in-thread (opens
  minichat); confirm Armada shows our minichat replies as a thread and its threads
  open as our minichat; confirm the "N replies" chip count.
