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
package com.vitorpamplona.amethyst.commons.notifications.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.FilterByListParams
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.isMutedPublicChatMessage
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.wot.curatedHidesByTrust
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.threading.buzzThreadReply
import com.vitorpamplona.quartz.buzz.threading.buzzThreadRoot
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workspace.buzzParticipants
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.BaseNoteEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import kotlinx.coroutines.flow.StateFlow

class NotificationFeedFilter(
    val account: Account,
    val modeOverride: TopFilter? = null,
) : AdditiveFeedFilter<Note>() {
    // Pin to modeOverride for split-tab mode; otherwise follow the spinner.
    // Lazy so the eagerly-collected topNavFilter pipeline is only built when
    // the split UI actually opens this filter.
    private val overrideFollowLists: StateFlow<IFeedTopNavFilter>? by lazy {
        modeOverride?.let { account.pinnedNotificationFollowLists(it) }
    }

    companion object {
        val ADDRESSABLE_KINDS =
            listOf(
                AudioTrackEvent.KIND,
                MusicTrackEvent.KIND,
                MusicPlaylistEvent.KIND,
                PodcastEpisodeEvent.KIND,
                PodcastMetadataEvent.KIND,
                CalendarTimeSlotEvent.KIND,
                CalendarDateSlotEvent.KIND,
                CalendarRSVPEvent.KIND,
                ClassifiedsEvent.KIND,
                LiveActivitiesEvent.KIND,
                LiveChessGameAcceptEvent.KIND,
                LiveChessMoveEvent.KIND,
                LongFormContentEvent.KIND,
                NipTextEvent.KIND,
                AddressableShortVideoEvent.KIND,
                AddressableNormalVideoEvent.KIND,
                WikiArticleEvent.KIND,
                AttestationRequestEvent.KIND,
            )

        // GitReplyEvent (kind 1622) is deprecated in favour of NIP-22 comments, but
        // events already on relays still arrive and still have to be routed.
        @Suppress("DEPRECATION")
        val NOTIFICATION_KINDS =
            // Kinds that RENDER as a row on the Notifications tab. This is a
            // display gate over whatever is already in LocalCache — it plays no
            // part in relay subscriptions (those live in
            // FilterNotificationsToPubkey, the chat datasources, and the wallet
            // assembler). It deliberately does NOT share Desktop's
            // `NotificationKinds.SUBSCRIPTION_KINDS`: that list answers "what to
            // ask relays for / toast on" and includes envelope kinds like
            // GiftWrap (1059), whose created_at is randomized up to 2 days back
            // (NIP-59). Envelopes never render here — the unwrapped inner event
            // (kind 14/15/…) is the feed row, same rule NotificationDispatcher
            // applies to push. NotificationKindsContractTest pins the
            // relationship between the two lists.
            setOf(
                BadgeAwardEvent.KIND,
                ChannelMessageEvent.KIND,
                // kind-9 chat message, shared by two features:
                //  • NIP-29 group chat: a reply to my group message is a kind-9 that p-tags me
                //    (see ChannelNewMessageViewModel), fetched at startup by
                //    filterGroupNotificationsToPubkey.
                //  • NIP-C7 / Concord: an inline reply (Concord's default reply mode) or an @-mention
                //    p-tags me; a minichat reply is a kind-1111 CommentEvent below.
                //  • Buzz channels: a THREAD reply carries no `p` tag at all (Buzz only p-tags
                //    @mentions), so it qualifies through [isBuzzThreadReplyToMyEvent] instead, which
                //    resolves the author of its `root`/`reply` marked `e` tags.
                // A plain channel message tags no one and matches none of those, so it never reaches
                // here. Without kind 9 the acceptableEvent kind gate would drop these replies before
                // any of the checks, so they'd never render on the Notifications tab.
                ChatEvent.KIND,
                // Legacy Buzz thread replies (nothing writes 40002 any more, but they exist in the
                // wild). Same no-`p`-tag shape as kind 9 above.
                StreamMessageV2Event.KIND,
                ChatMessageEvent.KIND,
                ChatMessageEncryptedFileHeaderEvent.KIND,
                CommentEvent.KIND,
                GenericRepostEvent.KIND,
                GitIssueEvent.KIND,
                GitPatchEvent.KIND,
                GitPullRequestEvent.KIND,
                GitPullRequestUpdateEvent.KIND,
                // NIP-34 threaded activity: legacy comment (1622, deprecated by
                // NIP-22 but still in the wild) and the four status transitions
                // (open/applied/closed/draft, kinds 1630-1633). Included so
                // "someone merged/closed my PR" and "someone commented on my
                // patch" surface on the Notifications tab — without this the
                // status kinds arrive via the p-tag subscription and sit in
                // LocalCache invisible.
                GitReplyEvent.KIND,
                GitStatusOpenEvent.KIND,
                GitStatusAppliedEvent.KIND,
                GitStatusClosedEvent.KIND,
                GitStatusDraftEvent.KIND,
                GitReviewEvent.KIND,
                HighlightEvent.KIND,
                TextNoteEvent.KIND,
                ReactionEvent.KIND,
                RepostEvent.KIND,
                ZapReceiptEvent.KIND,
                NutzapEvent.KIND,
                OnchainZapEvent.KIND,
                Bolt12ZapEvent.KIND,
                LiveActivitiesChatMessageEvent.KIND,
                PictureEvent.KIND,
                PollEvent.KIND,
                ZapPollEvent.KIND,
                EncryptedDmEvent.KIND,
                PublicMessageEvent.KIND,
                VideoNormalEvent.KIND,
                VideoShortEvent.KIND,
                VoiceEvent.KIND,
                VoiceReplyEvent.KIND,
                // A Buzz workflow approval gate (46010) addressed to me — I need to grant/deny it.
                // NOTE: this list does NOT gate push. NotificationDispatcher declares its own, separate
                // NOTIFICATION_KINDS; adding a kind here changes only what renders on the tab.
                WorkflowApprovalRequestedEvent.KIND,
                // "Somebody added you to a channel" (44100). Like the approval gate above, this is a
                // question addressed to me rather than a dated event — it renders as a ChannelInviteCard
                // with Leave / Ignore / Add to Messages, and the DAL sorts pending ones to the top so an
                // old invite can't sink into history. `acceptableEvent` narrows this to the ones still
                // unanswered; a self-join, a dismissal or an accept drops it.
                MemberAddedNotificationEvent.KIND,
            ) + ADDRESSABLE_KINDS

        // How deep to walk a public chat reply chain looking for one of the
        // user's own messages. Bounds the cost on very long threads; the
        // visited-set guards against malformed cyclic replyTo links.
        private const val PUBLIC_CHAT_ANCESTOR_SCAN_LIMIT = 30

        /**
         * Public chats (NIP-28, kind 42) routinely reply to a user without
         * adding a `p` tag, so the normal mention gate ([Event.isTaggedUser])
         * misses them. Treats a channel message as "for me" when one of my own
         * messages appears in its reply chain — a direct reply to my message
         * (the common case: "the previous message was mine") or a later message
         * in a thread I'm already part of (an "active thread"). A kind-42
         * `replyTo` holds only the immediate parent, so the chain is walked
         * hop-by-hop through each cached ancestor.
         *
         * Cache-only (reads [Note.replyTo] + author, never the account), so the
         * push dispatcher and the in-app feed can both relax their p-tag gate
         * with it without loading the account or decrypting anything.
         */
        fun isNotifiablePublicChatReply(
            note: Note,
            authorHex: HexKey,
        ): Boolean {
            if (note.event !is ChannelMessageEvent) return false
            // Top-level channel posts have no parent to reply to — bail before
            // allocating the walk's scratch structures (the common case).
            val parents = note.replyTo
            if (parents.isNullOrEmpty()) return false

            var scanned = 0
            val seen = HashSet<HexKey>()
            val toVisit = ArrayDeque(parents)

            while (toVisit.isNotEmpty() && scanned < PUBLIC_CHAT_ANCESTOR_SCAN_LIMIT) {
                val ancestor = toVisit.removeFirst()
                if (!seen.add(ancestor.idHex)) continue
                scanned++

                if (ancestor.author?.pubkeyHex == authorHex) return true
                ancestor.replyTo?.let { toVisit.addAll(it) }
            }

            return false
        }

        // Shared with EventNotificationConsumer so push notifications and the
        // in-app feed apply the same per-kind "is this event for me" rule.
        // GitReplyEvent (kind 1622) is deprecated in favour of NIP-22 comments, but
        // events already on relays still arrive and still have to be routed.
        @Suppress("DEPRECATION")
        fun tagsAnEventByUser(
            note: Note,
            authorHex: HexKey,
        ): Boolean {
            val event = note.event

            // Public chat replies into my messages, even without a p-tag.
            if (isNotifiablePublicChatReply(note, authorHex)) {
                return true
            }

            if (event is GitStatusEvent || event is GitPullRequestUpdateEvent) {
                // Anyone can publish a status or a PR update that p-tags the user. Only one by the
                // item's author or a confirmed repository member changes the item, so only those
                // notify (gitworkshop drops the rest from its notifications the same way).
                // While the item or its repository's announcements haven't loaded (a cold start, a
                // push) there is nothing to judge by, and dropping it would lose a maintainer's
                // change for good, since nothing re-checks it: it notifies.
                val item = note.replyTo?.lastOrNull()?.event ?: return true
                val authority = LocalCache.gitAuthority
                return !authority.canJudge(item) || authority.isAuthorisedChange(event, item)
            }

            if (event is GitIssueEvent || event is GitPatchEvent ||
                event is GitPullRequestEvent || event is GitReplyEvent || event is GitReviewEvent
            ) {
                // NIP-34 events reach the notifications tab only via the p-tag
                // relay filter, which already selected them because the current
                // user is on the participant list. Any further check would
                // require walking a chain of prior status/reply events that
                // aren't guaranteed to be in cache; short-circuit to trust the
                // p-tag — same policy applied to issues/patches/PRs above.
                return true
            }

            if (event is HighlightEvent) {
                return true
            }

            if (event is BaseNoteEvent) {
                if (note.replyTo?.any { it.author?.pubkeyHex == authorHex } == true) {
                    return true
                }

                if (event is CommentEvent) {
                    // NIP-22 comments carry their root/parent authors as tags, so a
                    // reply to the user's reaction stays detectable even when the
                    // reaction event is not in the local cache (the replyTo-author
                    // check above needs it loaded).
                    //
                    // This also covers replies to the user's zaps: the receipt is
                    // signed by the recipient's lightning provider, so its `P`/`p` name
                    // the provider, and the reply's extra lowercase `p` on the zapper
                    // is what marks it as theirs.
                    if (event.rootAuthorKeys().contains(authorHex) || event.replyAuthorKeys().contains(authorHex)) {
                        return true
                    }
                }

                if ((event is TextNoteEvent || event is CommentEvent)) {
                    val community =
                        event
                            .communityAddress()
                            ?.let {
                                LocalCache.getAddressableNoteIfExists(it)
                            }?.event as? CommunityDefinitionEvent
                    if (community != null) {
                        val moderators = community.moderatorKeys().toSet()
                        val isModerator = moderators.contains(authorHex)

                        if (isModerator && event.pubKey !in moderators) {
                            return true
                        }
                    }
                }

                val isAuthoredPostCited = event.findCitations().any { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex }

                if (isAuthoredPostCited) return true

                val isAuthorDirectlyCited = event.citedUsers().contains(authorHex)

                if (isAuthorDirectlyCited) return true

                return if (event is IForkableEvent && event.isAFork()) {
                    val address = event.forkFromAddress()
                    val version = event.forkFromVersion()

                    // Displays notifications about forks
                    address?.pubKeyHex == authorHex ||
                        (version?.let { LocalCache.getNoteIfExists(it)?.author?.pubkeyHex == authorHex } == true)
                } else {
                    false
                }
            }

            if (event is ReactionEvent) {
                return note.replyTo
                    ?.lastOrNull()
                    ?.author
                    ?.pubkeyHex == authorHex
            }

            if (event is RepostEvent || event is GenericRepostEvent) {
                return note.replyTo
                    ?.lastOrNull()
                    ?.author
                    ?.pubkeyHex == authorHex
            }

            return true
        }
    }

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + followList().code + "-" + account.settings.showMessagesInNotifications.value

    fun followList(): TopFilter = modeOverride ?: account.settings.defaultNotificationFollowList.value

    fun TopFilter.isMuteList() = this is TopFilter.MuteList

    fun TopFilter.isBlockList() = this is TopFilter.PeopleList && this.address == account.blockPeopleList.getBlockListAddress()

    fun TopFilter.wantsToSeeNegativeStuff() = isMuteList() || isBlockList()

    override fun showHiddenKey(): Boolean = followList().wantsToSeeNegativeStuff()

    fun buildFilterParams(account: Account): FilterByListParams =
        FilterByListParams.create(
            followLists = overrideFollowLists?.value ?: account.liveNotificationFollowLists.value,
            hiddenUsers = account.hiddenUsers.flow.value,
        )

    override fun feed(): List<Note> {
        val filterParams = buildFilterParams(account)

        val notifications =
            LocalCache.notes.filterIntoSet { _, note ->
                note.event !is AddressableEvent && acceptableEvent(note, filterParams)
            } +
                LocalCache.addressables.filterIntoSet(ADDRESSABLE_KINDS) { _, note ->
                    acceptableEvent(note, filterParams)
                }

        // Include marmot group messages as notifications (like DMs)
        val loggedInUserHex = account.userProfile().pubkeyHex
        val marmotMessages =
            account.marmotGroupList.rooms.mapFlatten { _, chatroom ->
                chatroom.messages.filter { it.author?.pubkeyHex != loggedInUserHex }
            }

        // Concord Direct Invites waiting on an answer. Like a "you were added to a channel" (44100) they
        // are a standing question, not a chat message, so they ignore the Messages toggle. They are not
        // events in the cache (see ConcordDirectInviteNote), so they are appended here and rebuilt by
        // AccountFeedContentStates when the pending set moves.
        val concordDirectInvites = account.concord.pendingConcordDirectInviteNotes.value

        return sort(notifications + marmotMessages + concordDirectInvites)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = innerApplyFilter(newItems)

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        val filterParams = buildFilterParams(account)

        return collection.filterTo(HashSet()) { acceptableEvent(it, filterParams) }
    }

    /**
     * The Buzz DM message [note] targets me: a kind-40002 in a `t=dm` channel whose 39000 participants
     * include [me]. DM messages carry no `p` tag, so being a participant of the DM channel is the
     * relevance signal — like a Marmot/Concord message. False for non-DM channels, for metadata we
     * haven't loaded yet, or when I'm not a participant. (Own-message and toggle checks live at the call site.)
     */
    private fun isBuzzDmForMe(
        note: Note,
        me: HexKey,
    ): Boolean {
        val md = LocalCache.getRelayGroupChannelForContent(note)?.event ?: return false
        return md.isBuzzDm() && md.buzzParticipants().contains(me)
    }

    /**
     * A reaction/repost that carries NO `p` tag — so neither the follow filter nor the p-tag gate can
     * route it — but whose reacted target (the LAST `e` tag, already loaded) is MY note. Buzz likes are a
     * bare `["e", <id>]` with no author `p` tag (unlike NIP-25, which p-tags the reacted author), so being
     * the author of the liked post is the only relevance signal. Restricted to the no-`p`-tag case so a
     * well-formed reaction keeps its normal p-tag routing, and to an already-loaded target so it can't
     * accept blindly.
     */
    private fun isReactionToMyEvent(
        note: Note,
        me: HexKey,
    ): Boolean {
        val namesAuthor =
            when (val event = note.event) {
                is ReactionEvent -> event.originalAuthor().isNotEmpty()
                is RepostEvent -> event.originalAuthorKeys().isNotEmpty()
                is GenericRepostEvent -> event.originalAuthorKeys().isNotEmpty()
                else -> return false
            }
        if (namesAuthor) return false
        return note.replyTo
            ?.lastOrNull()
            ?.author
            ?.pubkeyHex == me
    }

    /**
     * A Buzz workspace message that `p`-tags me — Buzz only `p`-tags @mentions. A workspace is a
     * closed, membership-gated space, so a coworker's @mention is relevant whether or not I follow
     * them (like Concord), and it should not depend on the per-kind reply heuristics either: Buzz
     * mentions name no event of mine.
     */
    private fun isBuzzMentionOfMe(
        note: Note,
        me: HexKey,
    ): Boolean {
        val mentionsMe =
            when (val event = note.event) {
                is ChatEvent -> me in event.mentionKeys()
                is StreamMessageV2Event -> me in event.mentions()
                else -> return false
            }
        if (!mentionsMe) return false
        val group = LocalCache.getRelayGroupChannelForContent(note) ?: return false
        return BuzzRelayDialect.isBuzz(group.groupId.relayUrl)
    }

    /**
     * A Buzz chat **thread reply** into one of my messages, when the reply carries no `p` tag.
     *
     * Buzz's clients thread with `["e", <id>, "", "reply"]` (nested: `root` + `reply`) and only ever
     * `p`-tag @mentions, so a reply to my message names me nowhere. That is the same shape as a Buzz
     * reaction, which [isReactionToMyEvent] already rescues by resolving the target's author instead of
     * trusting a tag — this does the same for replies, looking at the author of the `root`/`reply`
     * targets specifically (never a bare `e`, which is WhiteNoise/Marmot's in-chat reply, not a thread).
     *
     * Without it a reply to my message in a Buzz channel notifies nothing — and since a thread reply is
     * deliberately kept out of the channel timeline and its unread dot
     * ([com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.isRelayGroupTimelineMessage]),
     * it would be invisible on every surface.
     *
     * Only consulted when there is no `p` tag: a reply that does name me already passes the p-tag gate.
     */
    private fun isBuzzThreadReplyToMyEvent(
        note: Note,
        me: HexKey,
    ): Boolean {
        val event = note.event ?: return false
        val mentionsAnyone =
            when (event) {
                is ChatEvent -> event.mentionKeys().isNotEmpty()
                is StreamMessageV2Event -> event.mentions().isNotEmpty()
                else -> return false
            }
        if (mentionsAnyone) return false

        val threadTargets = setOfNotNull(event.tags.buzzThreadReply(), event.tags.buzzThreadRoot())
        if (threadTargets.isEmpty()) return false

        return note.replyTo?.any { it.idHex in threadTargets && it.author?.pubkeyHex == me } == true
    }

    fun acceptableEvent(
        it: Note,
        filterParams: FilterByListParams,
    ): Boolean {
        val loggedInUserHex = account.userProfile().pubkeyHex

        // When the user opts out of seeing Messages on the Notification tab, drop
        // direct/group message events (DMs and Marmot group chats) entirely.
        val showMessages = account.settings.showMessagesInNotifications.value

        // Marmot group messages are only acceptable if the gathering chatroom is
        // actually in the current account's group list. Notes are stored in the
        // global LocalCache and accumulate a gatherer reference from every
        // account's chatroom that has ever touched them, so seeing any
        // MarmotGroupChatroom is not enough — it could belong to a prior account
        // or a group the user has since left. Verify the chatroom is the same
        // instance held by this account's list.
        val marmotGatherers = it.inGatherers?.filterIsInstance<MarmotGroupChatroom>()
        if (!marmotGatherers.isNullOrEmpty()) {
            if (!showMessages) return false
            val inCurrentAccount =
                marmotGatherers.any { room ->
                    account.marmotGroupList.rooms.get(room.nostrGroupId) === room
                }
            if (!inCurrentAccount) return false
            return it.author?.pubkeyHex != loggedInUserHex
        }

        val noteEvent = it.event

        // Muted public chats contribute nothing to Notifications.
        //
        // NOTE: this filter is one-way. NotificationFeedFilter is an AdditiveFeedFilter, so
        // applyFilter only ever runs over newly-arriving items — nothing re-scans LocalCache
        // when the mute set changes. Entries suppressed while muted therefore do NOT come
        // back on unmute until the tab is refreshed. Device-confirmed; see the design doc.
        if (isMutedPublicChatMessage(noteEvent, account.settings.mutedPublicChats.value)) return false

        // "Somebody added you to a channel" (kind 44100). The relay keypair authors it, so none of the
        // follow/relevance heuristics below can say anything useful about it — its relevance is that it
        // is addressed to me and still unanswered. Every rule that decides "unanswered" (self-join,
        // dismissal, already on my kind-10009, DM vs named channel, superseded by a kind-44101) lives in
        // the account's projection, so this is a map lookup, and answering the prompt drops the row on
        // the next invalidation. This is a standing decision, not a chat message: it ignores the
        // Messages toggle.
        //
        // Ordered after the mute check only for readability — a blanket "never show this" reads before a
        // kind-specific accept. The two can't actually interact: the mute rule matches public-chat
        // messages, which a kind-44100 is not.
        if (noteEvent is MemberAddedNotificationEvent) return account.channelInvites.isPending(it.idHex)

        // Buzz DM: a group chat message in a `t=dm` channel whose 39000 participants include me. A Buzz
        // relay carries DM messages as either kind-9 (NIP-29 chat) or kind-40002 (stream message v2), and
        // neither `p`-tags the recipient, so being a participant of the DM channel is the relevance signal
        // — like a Marmot/Concord message above. Honors the same "Messages in notifications" toggle, and
        // never notifies for my own message.
        if ((noteEvent is StreamMessageV2Event || noteEvent is ChatEvent) && isBuzzDmForMe(it, loggedInUserHex)) {
            if (!showMessages) return false
            return it.author?.pubkeyHex != loggedInUserHex
        }

        // A finished or failed agent job I filed: the workspace bot addresses the outcome to me via a
        // `p` tag = the requester. Notify me directly — I don't "follow" the bot and the job kinds aren't
        // in the generic relevance path, so mirror the Buzz-DM early return above rather than the p-tag
        // heuristic. This is activity (not a chat message), so it ignores the Messages toggle.
        if (noteEvent is JobResultEvent || noteEvent is JobErrorEvent) {
            val requester = (noteEvent as? JobResultEvent)?.requester() ?: (noteEvent as JobErrorEvent).requester()
            return requester == loggedInUserHex && it.author?.pubkeyHex != loggedInUserHex
        }

        // A Buzz workflow paused on an approval gate (46010) addressed to me as the approver: I must
        // grant or deny before the run ships. Same early-return shape — it's activity, not a message.
        if (noteEvent is WorkflowApprovalRequestedEvent) {
            return noteEvent.approver() == loggedInUserHex && it.author?.pubkeyHex != loggedInUserHex
        }

        if (!showMessages &&
            (
                noteEvent is ChatMessageEvent ||
                    noteEvent is ChatMessageEncryptedFileHeaderEvent ||
                    noteEvent is EncryptedDmEvent
            )
        ) {
            return false
        }
        // A DM protocol turned off in Settings › Messages doesn't notify either.
        if (noteEvent is ChatroomKeyable && !account.chatFeedToggles.isEnabled(noteEvent)) {
            return false
        }
        val notifAuthor =
            if (noteEvent is ZapReceiptEvent) {
                val zapRequest = noteEvent.zapRequest
                if (zapRequest != null) {
                    if (noteEvent.zapRequest?.isPrivateZap() == true) {
                        account.privateZapsDecryptionCache.cachedPrivateZap(zapRequest)?.pubKey ?: zapRequest.pubKey
                    } else {
                        zapRequest.pubKey
                    }
                } else {
                    noteEvent.pubKey
                }
            } else if (noteEvent is Bolt12ZapEvent) {
                // The sender is the `P` payer tag; anonymous zaps sign with an ephemeral key.
                noteEvent.payer() ?: noteEvent.pubKey
            } else {
                if (it is AddressableNote) {
                    it.address.pubKeyHex
                } else {
                    it.author?.pubkeyHex
                }
            }

        // Reactions/zaps/reposts target a note via `replyTo`, not via thread-root tags,
        // so isNotInMutedThread on the wrapper event misses them.
        if (noteEvent is ReactionEvent || noteEvent is ZapReceiptEvent || noteEvent is Bolt12ZapEvent ||
            noteEvent is RepostEvent || noteEvent is GenericRepostEvent
        ) {
            val target = it.replyTo?.lastOrNull()
            if (target != null &&
                (
                    account.isThreadMuted(account.resolveThreadRoot(target)) ||
                        isMutedPublicChatMessage(target.event, account.settings.mutedPublicChats.value)
                )
            ) {
                return false
            }
        }

        // Chess events bypass the follow filter — opponents may not be followed
        val isChessEvent = noteEvent is LiveChessGameAcceptEvent || noteEvent is LiveChessMoveEvent

        // Concord community messages bypass the follow filter too: a reply/reaction that p-tags me
        // in a community I've joined is relevant whether or not I follow that member (fellow members
        // usually aren't follows). The p-tag gate below still applies, so only genuine replies /
        // reactions / mentions notify — general channel chatter that doesn't tag me never does.
        //
        // A ConcordChannel gatherer alone isn't enough: notes live in the global LocalCache and keep a
        // gatherer reference from every account/community that ever touched them, so require the
        // community to be one THIS account has currently joined (mirrors the Marmot check above) —
        // otherwise a note from a prior account or a left community would leak onto Notifications.
        // Concord membership (see Account.isConcordActivity): a message in a community this
        // account joined, or a reaction/repost to one.
        val isConcordMessage = account.isInJoinedConcordCommunity(it)
        val isConcord = account.isConcordActivity(it)

        // A bare reaction/repost (no p-tag) to my own already-loaded note — e.g. a Buzz like, which is
        // just `["e", <id>]` — is relevant to me: bypass the follow filter and satisfy the p-tag gate,
        // exactly like a Concord reaction, since being the author of the liked post is the only signal.
        val isReactionToMe = isReactionToMyEvent(it, loggedInUserHex)

        // Same no-`p`-tag rescue for Buzz thread replies into my messages.
        val isThreadReplyToMe = isBuzzThreadReplyToMyEvent(it, loggedInUserHex) || isBuzzMentionOfMe(it, loggedInUserHex)

        // Concord CHAT (a message/reply) honors the "Messages in notifications" toggle that silences DMs
        // and Marmot groups above. A reaction isn't a message — regular reactions ignore that toggle, so
        // Concord reactions do too (only isConcordMessage is gated).
        if (isConcordMessage && !showMessages) return false

        // Global keeps every event that p-tags the user; Selected (and the
        // follow/list modes) also applies the per-kind relevance heuristics.
        val isRawGlobal = followList() is TopFilter.Global

        // The p-tag gate is OR'd with isNotifiablePublicChatReply so channel
        // replies into my messages still notify without a p-tag. Kept inline
        // (not a pre-computed val) so the cheap kind check short-circuits ahead
        // of the tag scan + reply walk, which is also why the cheaper tag scan
        // is ordered first within the OR. In Global mode this is the only
        // relevance check (tagsAnEventByUser is skipped below); it still scopes
        // to genuine replies, so unrelated channel chatter never leaks through.
        return noteEvent?.kind in NOTIFICATION_KINDS &&
            (noteEvent is ZapReceiptEvent || noteEvent is Bolt12ZapEvent || notifAuthor != loggedInUserHex) &&
            (isChessEvent || isConcord || isReactionToMe || isThreadReplyToMe || filterParams.isGlobal() || notifAuthor == null || filterParams.isAuthorInFollows(notifAuthor)) &&
            (noteEvent?.isTaggedUser(loggedInUserHex) == true || isNotifiablePublicChatReply(it, loggedInUserHex) || isReactionToMe || isThreadReplyToMe) &&
            (filterParams.isHiddenList || notifAuthor == null || !account.isHidden(notifAuthor)) &&
            (noteEvent !is EncryptedDmEvent || !account.isDecryptedContentHidden(noteEvent)) &&
            // For a Concord note the explicit p-tag above IS the relevance signal (the reply/reaction/
            // mention targets me directly), so skip the per-kind heuristic — which for a reaction would
            // otherwise need my target message already loaded to resolve replyTo.
            (isRawGlobal || isConcord || isThreadReplyToMe || tagsAnEventByUser(it, loggedInUserHex)) &&
            // Curated also applies the Web of Trust, with the same exemptions as push. Last, so
            // only notes that are notifications at all pay for the lookup.
            !(followList() is TopFilter.Selected && noteEvent != null && notifAuthor != null && account.curatedHidesByTrust(noteEvent, notifAuthor, inJoinedCommunity = isConcord))
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
