/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup

import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalDeniedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalGrantedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/*
 * Pure REQ-filter builders for the NIP-29 group-chat data sources. Kept separate from the assemblers so
 * the exact filter each screen puts on the wire (kinds, #d/#h scope, per-relay batching, since/until/limit,
 * authors) can be unit-tested without standing up an Account or relay client.
 *
 * See amethyst/plans/2026-07-18-nip29-group-chat-subscriptions.md and the companion test plan.
 */

/**
 * The relay's **directory** kinds for a group — metadata + admins + members + roles (39000-39003).
 * These four are what NIP-29 relays treat as a group's "metadata" block, and they must be requested
 * **alone**: see [RELAY_GROUP_PIN_KINDS].
 */
val RELAY_GROUP_METADATA_KINDS =
    listOf(
        GroupMetadataEvent.KIND,
        GroupAdminsEvent.KIND,
        GroupMembersEvent.KIND,
        GroupRolesEvent.KIND,
    )

/**
 * The pin list (39005), deliberately kept in its **own** filter rather than merged into
 * [RELAY_GROUP_METADATA_KINDS].
 *
 * NIP-29 relays derived from `relay29`/`khatru29` (0xchat's `groups.0xchat.com` among them) reject a REQ
 * whose filter mixes the 39000-39003 metadata kinds with any other kind, replying
 * `CLOSED … "blocked: it's not allowed to mix metadata kinds with others"`. A single filter asking for
 * 39000-39003 **plus** 39005 is therefore dropped **whole** — the group never resolves its name, roster,
 * roles or the user's own membership, so it renders as a raw id and offers "Join" to somebody the relay
 * already lists as an admin.
 *
 * Splitting into two filter objects fixes it: those relays evaluate the rule per filter, so the
 * metadata filter is served normally and the pins filter is served (or harmlessly ignored) on its own.
 */
val RELAY_GROUP_PIN_KINDS = listOf(GroupPinnedEvent.KIND)

/**
 * A live, channel-scoped subscription to this group's own relay-signed state (39000-39003) on a Buzz
 * relay — the thing that keeps a roster, a name or a visibility flip current without a refetch.
 *
 * Buzz signs these with `d`/`p` tags and **no `h`**, so a `#h` filter looks like it could not match.
 * It does: `filter_match_one` falls back to the stored `channel_id` for an `#h` filter **when the
 * event carries no `h` tag at all**, and these are stored channel-scoped. Scoping the filter by `#h`
 * is also what indexes the subscription under the channel, which is what makes it eligible for the
 * channel fan-out in the first place — a `#d` filter has no channel tag, so on Buzz it registers as a
 * global subscription and by design receives no channel-scoped event, which is why these updates
 * never arrived live.
 *
 * Buzz-only. On a relay29-family relay the same events are addressable with no `channel_id` behind
 * them, so an `#h` filter matches nothing there — those relays keep being served by the `#d`
 * directory filters.
 */
fun buildRelayGroupLiveStateFilter(groupId: GroupId): List<RelayBasedFilter> =
    listOf(
        RelayBasedFilter(
            relay = groupId.relayUrl,
            filter = ExplainedFilter(purpose = SubPurpose.RELAY_GROUPS, entityIds = listOf(groupId.id), kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id))),
        ),
        // Pins stay in their own filter for the same reason the directory filters split them out.
        RelayBasedFilter(
            relay = groupId.relayUrl,
            filter = ExplainedFilter(purpose = SubPurpose.RELAY_GROUPS, entityIds = listOf(groupId.id), kinds = RELAY_GROUP_PIN_KINDS, tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id))),
        ),
    )

/**
 * Every relay-signed group *state* kind: metadata + admins + members + roles + pins. Small replaceable
 * events. **Never put this list on the wire as one filter** — request [RELAY_GROUP_METADATA_KINDS] and
 * [RELAY_GROUP_PIN_KINDS] as separate filters instead (see [RELAY_GROUP_PIN_KINDS]). Kept as the
 * semantic "all state kinds" set for cache/consume-side code.
 */
val RELAY_GROUP_STATE_KINDS = RELAY_GROUP_METADATA_KINDS + RELAY_GROUP_PIN_KINDS

/** Timeline kinds shown in a group's chat — chat messages and polls. */
val RELAY_GROUP_TIMELINE_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND)

/**
 * Extra timeline kinds a `block/buzz` workspace relay serves in the same `h`-scoped
 * channels, requested UNCONDITIONALLY alongside the NIP-29 set — on a vanilla relay the
 * kinds simply match nothing. A dialect-gated version was tried and reverted: gating
 * creates a bootstrap hole (nothing asks for a Buzz kind until one is consumed) and a
 * worse one — history pages fetched before the mark advance their cursors past ranges
 * queried WITHOUT Buzz kinds, permanently skipping older workspace messages.
 *
 * All are `h`-scoped (`GroupIdTag`), so the same `#h` group REQ returns them:
 * - stream messages v2 (40002), edits (40003), diffs (40008), system rows (40099), canvas (40100)
 * - forum posts/votes/comments (45001-45003)
 * - agent jobs (43001-43006)
 * - workflow trigger + run/step lifecycle + approval gate (46020, 46001-46007, 46010-46012);
 *   note the client-signed grant/deny (46030/46031) carry only a `d` tag (no `h`), so they're
 *   NOT here — surfaces that need them fetch by author (see the workflow board VM / CLI)
 * - huddle lifecycle (48100-48103)
 *
 * Consumption for every one of these already exists in `LocalCache` (see
 * `consumeBuzzTimelineEvent`); requesting them here is what lets them actually arrive for
 * a group feed instead of only appearing if another subscription happened to fetch them.
 */
val BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS =
    listOf(
        StreamMessageV2Event.KIND,
        StreamMessageEditEvent.KIND,
        StreamMessageDiffEvent.KIND,
        SystemMessageEvent.KIND,
        CanvasEvent.KIND,
        ForumPostEvent.KIND,
        ForumVoteEvent.KIND,
        ForumCommentEvent.KIND,
        JobRequestEvent.KIND,
        JobAcceptedEvent.KIND,
        JobProgressEvent.KIND,
        JobResultEvent.KIND,
        JobCancelEvent.KIND,
        JobErrorEvent.KIND,
        WorkflowTriggerEvent.KIND,
        WorkflowTriggeredEvent.KIND,
        WorkflowStepStartedEvent.KIND,
        WorkflowStepCompletedEvent.KIND,
        WorkflowStepFailedEvent.KIND,
        WorkflowCompletedEvent.KIND,
        WorkflowFailedEvent.KIND,
        WorkflowCancelledEvent.KIND,
        WorkflowApprovalRequestedEvent.KIND,
        WorkflowApprovalGrantedEvent.KIND,
        WorkflowApprovalDeniedEvent.KIND,
        HuddleStartedEvent.KIND,
        HuddleParticipantJoinedEvent.KIND,
        HuddleParticipantLeftEvent.KIND,
        HuddleEndedEvent.KIND,
    )

/** The timeline kinds requested for every relay-group REQ (NIP-29 + Buzz; see above). */
val RELAY_GROUP_ALL_TIMELINE_KINDS = RELAY_GROUP_TIMELINE_KINDS + BUZZ_RELAY_GROUP_TIMELINE_EXTRA_KINDS

/**
 * Channel **aux** kinds — overlays that modify an existing row rather than being a row: reactions (7),
 * NIP-09 deletions (5) and the Buzz-native NIP-29 delete (9005).
 *
 * Requested `#h`-scoped on the channel subscription, which is how the Buzz reference client does it
 * (`channelEventKinds` in its Flutter client bundles deletion + reaction + the message kinds into the
 * one `#h` channel REQ). Amethyst otherwise only learns about reactions through the shared `#e`
 * EventFinder query keyed on the ids of notes currently on screen — and that query carries no `#h`, so
 * on a relay that scopes live delivery per channel it is registered as a *global* subscription and can
 * never receive them live (a reaction inherits its target's channel, so it IS channel-scoped). The
 * result was reaction chips that only ever appeared on a re-query, never as they happened.
 *
 * Kept OUT of [RELAY_GROUP_ALL_TIMELINE_KINDS] on purpose: that set feeds the backward history pager,
 * whose `until` cursor walks `created_at`. Letting reactions consume a page's `limit` would advance the
 * cursor past chat messages that were never delivered — the same class of cursor-skip bug that the
 * unconditional-Buzz-kinds note above records.
 */
val RELAY_GROUP_AUX_KINDS =
    listOf(
        DeletionRequestEvent.KIND,
        ReactionEvent.KIND,
        GroupDeleteEventEvent.KIND,
    )

/** How many aux events a channel replays on subscribe. Bounds the backfill; live delivery is unbounded. */
const val RELAY_GROUP_AUX_LIMIT = 500

/**
 * Kinds requested on the **open channel's live tail only** — the timeline set plus the
 * ephemeral kind-20002 typing indicator. Typing is scoped to the one channel on screen
 * (not the whole joined fleet) because it's a live "someone is typing" signal, never
 * stored (20000-29999) and never a feed row (`LocalCache` records it into `BuzzTypingState`
 * and drops it). It matches nothing on a vanilla relay.
 */
val RELAY_GROUP_OPEN_TAIL_KINDS = RELAY_GROUP_ALL_TIMELINE_KINDS + TypingIndicatorEvent.KIND

/**
 * Forum-thread kinds shown in a group's Threads tab: NIP-29 kind-11 roots + kind-1111 comments, PLUS
 * Buzz forum roots (45001) + comments (45003). Requested together (a vanilla relay matches nothing on
 * the Buzz kinds, a Buzz relay nothing on kind-11), so the same Threads REQ surfaces either dialect.
 */
val RELAY_GROUP_THREAD_KINDS = listOf(ThreadEvent.KIND, CommentEvent.KIND, ForumPostEvent.KIND, ForumCommentEvent.KIND)

/** Content kinds a card warms ahead of a tap (chat + polls + threads + comments). */
val RELAY_GROUP_CARD_WARMUP_KINDS = listOf(ChatEvent.KIND, PollEvent.KIND, ThreadEvent.KIND, CommentEvent.KIND)

/**
 * A relay's whole-directory kinds — metadata + admins + members + roles (39000-39003), **no pins**.
 * Narrower than [RELAY_GROUP_STATE_KINDS] on purpose: the directory lists groups, it doesn't need each
 * group's pin list.
 */
val RELAY_GROUP_DIRECTORY_KINDS = RELAY_GROUP_METADATA_KINDS

/** How many directory entries to pull per relay when browsing its whole group list. */
const val RELAY_GROUP_DIRECTORY_LIMIT = 500

/** `d`-tag key of the relay-signed state events (39xxx are addressable by the group id). */
private const val D_TAG = "d"

private fun byHostRelay(joined: Collection<GroupTag>): Map<NormalizedRelayUrl, List<String>> {
    val out = LinkedHashMap<NormalizedRelayUrl, MutableList<String>>()
    joined.forEach { tag ->
        val relay = RelayUrlNormalizer.normalizeOrNull(tag.relayUrl) ?: return@forEach
        out.getOrPut(relay) { mutableListOf() }.add(tag.groupId)
    }
    return out
}

/**
 * State (39000-39005) for every joined group, **two `#d` filters per host relay** carrying that relay's
 * group ids: the 39000-39003 metadata block and the 39005 pin list, kept apart because relay29-family
 * relays refuse a filter that mixes them (see [RELAY_GROUP_PIN_KINDS]). `since` is per-relay (replaceable
 * events; a reconnect just re-confirms).
 */
fun buildRelayGroupStateFilters(
    joined: Collection<GroupTag>,
    sinceForRelay: (NormalizedRelayUrl) -> Long?,
): List<RelayBasedFilter> =
    byHostRelay(joined).flatMap { (relay, ids) ->
        val scope = mapOf(D_TAG to ids.distinct())
        val since = sinceForRelay(relay)
        listOf(
            RelayBasedFilter(relay = relay, filter = ExplainedFilter(purpose = SubPurpose.RELAY_GROUPS, entityIds = ids.distinct(), kinds = RELAY_GROUP_METADATA_KINDS, tags = scope, since = since)),
            RelayBasedFilter(relay = relay, filter = ExplainedFilter(purpose = SubPurpose.RELAY_GROUPS, entityIds = ids.distinct(), kinds = RELAY_GROUP_PIN_KINDS, tags = scope, since = since)),
        )
    }

/**
 * Recent chat of **one** joined group: a single-valued `#h` filter on its host relay, bounded by a shared
 * time floor ([sinceEpoch]) and **no `limit`** — a time floor bounds it, so it stays reconnect-safe (a
 * reconnect re-issues one `since=window` REQ, never a page replay).
 *
 * ### Why one group per filter — and per *subscription*
 *
 * This used to batch every joined group on a relay into ONE filter carrying every group id in `#h`. That
 * shape is valid NIP-01 (a multi-value tag filter is an OR), and relays serve it correctly for **stored**
 * queries — which is exactly what made the bug so confusing: the boot backfill populated every group, so
 * the Messages list looked right until it needed to update.
 *
 * But `block/buzz` (the relay behind `*.communities.buzz.xyz`) indexes each live subscription under a
 * *single* channel uuid, resolved from the filters' `#h`. When two or more distinct ids appear anywhere
 * across a subscription's filters, `extract_channel_id_from_filters` returns `None` and the subscription
 * is registered as **global** — and global subscriptions deliberately never receive channel-scoped events
 * (`fan_out_scoped`: "Global subscriptions do NOT receive channel-scoped events", guarding against leaking
 * private channel content to a subscriber whose membership wasn't checked per channel). Net effect: a
 * batched tail gets full history at EOSE and then goes permanently deaf, so the Messages row froze while
 * the open chat — which always used a single-valued `#h` — stayed live.
 *
 * The resolution scans **all filters of a subscription**, so splitting into one filter per group is not
 * enough: each group needs its **own subscription** (see [RelayGroupJoinedChatTailFilterAssembler], which
 * keys its EOSE manager on [GroupId]). Relays advertise room for this — buzz allows `max_subscriptions:
 * 1024` against `max_filters: 10` — and it sidesteps the filter cap for anyone in more than ten groups.
 */
fun buildRelayGroupJoinedChatTailFilter(
    groupId: GroupId,
    sinceEpoch: Long,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = groupId.relayUrl,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.RELAY_GROUPS,
                entityIds = listOf(groupId.id),
                kinds = RELAY_GROUP_ALL_TIMELINE_KINDS,
                tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                since = sinceEpoch,
            ),
    )

/**
 * The **newest message** of one joined channel, at any age: single-valued `#h`, `limit = 1`, and a
 * `since` that is null until this relay EOSEs.
 *
 * This is what fills the channel's Messages-list row, and it is deliberately count-bounded rather than
 * time-bounded. [buildRelayGroupJoinedChatTailFilter] floors at `now - 7 days` to keep recent chat warm
 * in cache; on its own that means a channel nobody has posted in for eight days returns *zero* events,
 * so `newestChatNote()` stays null and the row is stuck on its "No messages yet" placeholder forever —
 * sorted to the bottom of Messages by `createdAt = 0`, indistinguishable from a channel you just joined.
 *
 * Every other roster-driven protocol on that screen already bounds by count for exactly this reason:
 * NIP-28 asks `limit = 1` per followed channel
 * (the app's `filterLastMessageFollowingPublicChats`),
 * Concord asks `limit = 10` per channel with no floor (`ConcordSubscriptionPlanner.channelPreviewFilters`).
 * They can, because their row set comes from a list event — unlike NIP-17/NIP-04, whose *rooms* are
 * discovered from the messages themselves and therefore need the backward pagers on the Messages list.
 * A NIP-29 channel is roster-driven (kind 10009), so it needs one newest message per row, not a window.
 *
 * `since` comes from the per-relay EOSE map: null on a cold start (newest message whatever its age),
 * then advancing, so a reconnect re-asks for one event rather than replaying a window.
 */
fun buildRelayGroupPreviewFilter(
    groupId: GroupId,
    sinceEpoch: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = groupId.relayUrl,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.RELAY_GROUPS,
                entityIds = listOf(groupId.id),
                kinds = RELAY_GROUP_ALL_TIMELINE_KINDS,
                tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                since = sinceEpoch,
                limit = 1,
            ),
    )

/**
 * Reactions/deletions for **every** message in one channel, `#h`-scoped on its host relay — see
 * [RELAY_GROUP_AUX_KINDS]. Single-valued `#h`, so it can share a channel-scoped subscription with the
 * chat tail without downgrading it.
 */
fun buildRelayGroupAuxFilter(
    groupId: GroupId,
    sinceEpoch: Long,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = groupId.relayUrl,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.RELAY_GROUPS,
                entityIds = listOf(groupId.id),
                kinds = RELAY_GROUP_AUX_KINDS,
                tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                since = sinceEpoch,
                limit = RELAY_GROUP_AUX_LIMIT,
            ),
    )

/** The recent-chat live tail for a single open group, `#h`-scoped on its host relay. */
fun buildRelayGroupOpenChatTailFilter(
    groupId: GroupId,
    sinceEpoch: Long,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = groupId.relayUrl,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.RELAY_GROUPS,
                entityIds = listOf(groupId.id),
                kinds = RELAY_GROUP_OPEN_TAIL_KINDS,
                tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                since = sinceEpoch,
            ),
    )

/**
 * Backward-history page(s) for a single open group: one `#h` filter per **armed** relay at its own
 * `until`, capped by [limit], **all authors** (so it also re-materializes the user's own history). A
 * relay with no requested `until` contributes nothing (it is parked).
 */
fun buildRelayGroupHistoryFilters(
    groupId: GroupId,
    armedRelays: Collection<NormalizedRelayUrl>,
    untilForRelay: (NormalizedRelayUrl) -> Long?,
    limit: Int,
): List<RelayBasedFilter> =
    armedRelays.mapNotNull { relay ->
        val until = untilForRelay(relay) ?: return@mapNotNull null
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.RELAY_GROUPS,
                    entityIds = listOf(groupId.id),
                    kinds = RELAY_GROUP_ALL_TIMELINE_KINDS,
                    tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                    until = until,
                    limit = limit,
                ),
        )
    }

/**
 * The whole group directory a single [relay] hosts: kinds 39000-39003, unscoped by `d`/`h` (every group
 * the relay signs), capped at [RELAY_GROUP_DIRECTORY_LIMIT]. Backs the "browse a relay's channels" screen.
 */
fun buildRelayGroupDirectoryFilter(
    relay: NormalizedRelayUrl,
    sinceEpoch: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = relay,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.RELAY_GROUPS,
                kinds = RELAY_GROUP_DIRECTORY_KINDS,
                limit = RELAY_GROUP_DIRECTORY_LIMIT,
                since = sinceEpoch,
            ),
    )

/**
 * Backward-history page(s) for a group's **Threads** tab: one `#h` filter per **armed** relay at its own
 * `until`, capped by [limit], over the thread kinds (11/1111). The forum analog of
 * [buildRelayGroupHistoryFilters]; a parked relay (no requested `until`) contributes nothing.
 */
fun buildRelayGroupThreadsHistoryFilters(
    groupId: GroupId,
    armedRelays: Collection<NormalizedRelayUrl>,
    untilForRelay: (NormalizedRelayUrl) -> Long?,
    limit: Int,
): List<RelayBasedFilter> =
    armedRelays.mapNotNull { relay ->
        val until = untilForRelay(relay) ?: return@mapNotNull null
        RelayBasedFilter(
            relay = relay,
            filter =
                ExplainedFilter(
                    purpose = SubPurpose.RELAY_GROUPS,
                    entityIds = listOf(groupId.id),
                    kinds = RELAY_GROUP_THREAD_KINDS,
                    tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                    until = until,
                    limit = limit,
                ),
        )
    }

/** The Threads-tab feed for a single open group: kind-11/1111 `#h`-scoped on the host relay. */
fun buildRelayGroupThreadsFilter(
    groupId: GroupId,
    sinceEpoch: Long?,
): RelayBasedFilter =
    RelayBasedFilter(
        relay = groupId.relayUrl,
        filter =
            ExplainedFilter(
                purpose = SubPurpose.RELAY_GROUPS,
                entityIds = listOf(groupId.id),
                kinds = RELAY_GROUP_THREAD_KINDS,
                tags = mapOf(GroupIdTag.TAG_NAME to listOf(groupId.id)),
                since = sinceEpoch,
            ),
    )

/**
 * Whether [groupId] is in the user's joined set — a joined group is kept warm app-wide by the always-on
 * state + chat-tail subs, so the on-screen [RelayGroupCardWarmupFilterAssembler] must skip it.
 */
fun isRelayGroupJoined(
    joined: Collection<GroupTag>,
    groupId: GroupId,
): Boolean =
    joined.any {
        it.groupId == groupId.id && RelayUrlNormalizer.normalizeOrNull(it.relayUrl) == groupId.relayUrl
    }
