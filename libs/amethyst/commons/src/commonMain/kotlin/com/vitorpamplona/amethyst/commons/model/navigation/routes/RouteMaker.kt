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
package com.vitorpamplona.amethyst.commons.model.navigation.routes

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.limitToRouteTextArg
import com.vitorpamplona.amethyst.commons.model.navigation.minichatRouteFor
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.nip64Chess.gameId
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupChatContent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip59Giftwrap.HasInnerEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip73ExternalIds.location.isGeohashedScoped
import com.vitorpamplona.quartz.nip73ExternalIds.topics.isHashtagScoped
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent

/**
 * Kinds whose destination is `Route.Note(id)` — the generic thread view — no matter what the
 * event body turns out to say. Mirrors the `else ->` branch of [routeForInner]: every kind here
 * is a plain note that carries nothing (no channel id, no `a` tag, no chatroom key) that could
 * send it somewhere more specific.
 *
 * **An addressable kind can never be listed here**, however plain it looks. [routeForInner]
 * sends those to `Route.Note(addressTag())` through its `is AddressableEvent` branch, and the
 * difference is not cosmetic: relays do not serve a replaceable by the id of one of its
 * versions, so routing by id is a dead end. This bit the NIP-71 video kinds — 21/22 are regular
 * but 34235/34236 extend `AddressableVideoEvent` — which is why the pair is split below and why
 * `RouteForPointerTest` walks this list against [routeForInner] itself rather than trusting it.
 *
 * `internal` so that test can iterate the real list: a kind added here is covered by
 * construction, not by someone remembering to add a case.
 */
internal val THREAD_VIEW_KINDS =
    intArrayOf(
        TextNoteEvent.KIND,
        CommentEvent.KIND,
        PollEvent.KIND,
        PictureEvent.KIND,
        // NIP-71 regular video only (21/22). The addressable pair, 34235/34236, is cited by
        // `naddr` and routed by address — see the warning above.
        VideoNormalEvent.KIND,
        VideoShortEvent.KIND,
        HighlightEvent.KIND,
        GitIssueEvent.KIND,
        GitPatchEvent.KIND,
        GitPullRequestEvent.KIND,
        GitPullRequestUpdateEvent.KIND,
    )

/**
 * Route for an event we hold only a NIP-19 pointer to — its id plus, when the pointer carries
 * one, its [kind] — because the body has not reached [LocalCache] yet.
 *
 * Notification deep links are the reason this exists. A push normally wakes a *cold* process,
 * so by the time the user taps the tray the cache is empty and [routeFor] can say nothing
 * better than [Route.EventRedirect] — a bare "looking for event" screen the user sits on until
 * the event is re-fetched. An `nevent` already states the kind, which for an ordinary note is
 * the whole answer, so a reply or a mention can open its thread immediately and let the screen
 * fill itself in.
 *
 * Returns null when the kind is absent or needs the body to place the event, leaving the
 * caller's redirect in charge.
 */
fun routeForPointer(
    kind: Int?,
    id: HexKey,
): Route? {
    if (kind == null) return null
    if (kind !in THREAD_VIEW_KINDS) return null
    return Route.Note(id)
}

fun routeFor(
    note: Note,
    loggedIn: Account,
): Route? {
    // A minichat reply opens the message's thread, not the whole channel it belongs to. Must run
    // before the channel-gatherer shortcuts below, which would otherwise swallow it into the channel.
    minichatRouteFor(note)?.let { return it }

    // Marmot group messages should navigate to the group chat
    val marmotGroup = note.inGatherers?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
    if (marmotGroup != null) {
        return Route.MarmotGroupChat(marmotGroup.nostrGroupId)
    }

    // NIP-29 relay-group content (kind 9 chat, 11 thread, 1111 comment, polls) should open the
    // group chat screen — like every other chat NIP — not the generic thread view it would fall
    // through to. A consumed group message is attached to its RelayGroupChannel (which carries the
    // host relay), so route via that; fall back to the note's `h` tag + provenance relay otherwise.
    val relayGroup = note.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }
    if (relayGroup != null) {
        return routeFor(relayGroup)
    }

    // A channel invite (kind-44100) has two destinations and gives each its own target: the room block
    // inside the card carries its own click and opens the room, so the rest of the row — the header, the
    // body around the block, the space below the author — opens the reply page instead. Replying is the
    // one thing you can do with an invite that the card itself doesn't already offer a button for, and
    // the generic thread view has nothing to show for a relay-signed notification nobody replied to yet.
    if (note.event is MemberAddedNotificationEvent) {
        return Route.GenericCommentPost(replyTo = note.idHex)
    }

    // Concord channel content (kind 9 chat, 1111 reply, 7 reaction) lands in LocalCache as a real
    // Note attached to its ConcordChannel gatherer. Route to the Concord chat instead of the generic
    // thread view it would otherwise fall through to: a minichat reply (kind-1111) opens its thread
    // ([minichatRouteFor]); a top-level message / reaction opens the channel.
    val concordChannel = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concordChannel != null) {
        return minichatRouteFor(note) ?: routeFor(concordChannel)
    }

    val noteEvent = note.event ?: return Route.EventRedirect(note.idHex)

    // Only group *content* opens its group from the `h` tag alone (the kinds the comment above
    // names). Other kinds carry `h` for their own reasons — Zapstore tags every app it publishes
    // with one — and routing those by it opened an empty group chat instead of the app.
    if (noteEvent.isGroupChatContent()) {
        val groupId = noteEvent.groupId()
        val hostRelay = note.relays.firstOrNull()
        if (groupId != null && hostRelay != null) {
            return routeFor(GroupId(groupId, hostRelay))
        }
    }

    return routeFor(noteEvent, loggedIn)
}

fun routeFor(
    noteEvent: Event,
    loggedIn: Account,
): Route? =
    if (noteEvent is DraftWrapEvent) {
        val innerEvent = loggedIn.draftsDecryptionCache.preCachedDraft(noteEvent)

        if (innerEvent != null) {
            routeForInner(innerEvent, loggedIn)
        } else {
            Route.Note(noteEvent.id)
        }
    } else {
        routeForInner(noteEvent, loggedIn)
    }

fun routeForInner(
    noteEvent: Event,
    loggedIn: Account,
): Route? =
    when (noteEvent) {
        is JesterEvent -> {
            Route.ChessGame(noteEvent.gameId() ?: noteEvent.id)
        }

        is AppDefinitionEvent -> {
            if (noteEvent.includeKind(5300)) {
                Route.ContentDiscovery(noteEvent.id)
            } else {
                // By address, not version id: the per-id note may have been
                // evicted and relays don't serve replaceables by old ids.
                Route.Note(noteEvent.addressTag())
            }
        }

        is IsInPublicChatChannel -> {
            noteEvent.channelId()?.let {
                Route.PublicChatChannel(it)
            }
        }

        is ChannelCreateEvent -> {
            Route.PublicChatChannel(noteEvent.id)
        }

        is LiveActivitiesEvent -> {
            noteEvent.address().let {
                Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
            }
        }

        is LiveActivitiesChatMessageEvent -> {
            noteEvent.activityAddress()?.let {
                Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag)
            }
        }

        is EphemeralChatEvent -> {
            noteEvent.roomId()?.let {
                Route.EphemeralChat(it.id, it.relayUrl.url)
            }
        }

        is StarterPackEvent -> {
            Route.FollowPack(noteEvent.address())
        }

        is ChatroomKeyable -> {
            val room = noteEvent.chatroomKey(loggedIn.userProfile().pubkeyHex)
            loggedIn.chatroomList.getOrCreatePrivateChatroom(room)
            Route.Room(room)
        }

        is CommunityDefinitionEvent -> {
            Route.Community(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is GitRepositoryEvent -> {
            Route.GitRepository(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is SoftwareApplicationEvent -> {
            Route.SoftwareAppDetail(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        // Calendar appointments route to their dedicated detail screen rather than the generic
        // Route.Note that AddressableEvent would fall through to — without this the notification
        // tap and `nostr:naddr…` deep links land on the bare note view instead of the calendar
        // detail with RSVPs, participants, and the "in calendars" list.
        is com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent -> {
            Route.CalendarEventDetail(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent -> {
            Route.CalendarEventDetail(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        // Geocaches route to their own screens for the same reason calendars do above: this one
        // function is what a feed tap, a notification tap and a `nostr:naddr…` deep link all go
        // through. Without these branches a cache opened from another NIP-CC client lands on the
        // bare note view -- which is the interop path that matters most, since a treasures.to
        // link is how most people will first meet a cache in Amethyst.
        is GeocacheListingEvent -> {
            Route.GeocacheDetail(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is GeocacheCurationListEvent -> {
            Route.GeocacheHunt(noteEvent.kind, noteEvent.pubKey, noteEvent.dTag())
        }

        is GiftWrapEvent, is SealEvent -> {
            val wrap = noteEvent as HasInnerEvent
            wrap.innerEventId?.let {
                routeFor(LocalCache.getOrCreateNote(it), loggedIn)
            }
        }

        // A kind-0 IS the person: tapping one anywhere (feed card, quote, `nostr:naddr`
        // deep link) opens their profile instead of the bare note view AddressableEvent
        // below would otherwise fall through to.
        is MetadataEvent -> {
            Route.Profile(noteEvent.pubKey)
        }

        is AddressableEvent -> {
            Route.Note(noteEvent.addressTag())
        }

        else -> {
            Route.Note(noteEvent.id)
        }
    }

fun routeToMessage(
    user: HexKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: BaseAccountViewModel,
): Route =
    routeToMessage(
        setOf(user),
        draftMessage,
        replyId,
        draftId,
        expiresDays,
        accountViewModel,
    )

fun routeToMessage(
    users: Set<HexKey>,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: BaseAccountViewModel,
) = routeToMessage(
    ChatroomKey(users),
    draftMessage,
    replyId,
    draftId,
    expiresDays,
    accountViewModel,
)

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: BaseAccountViewModel,
): Route = routeToMessage(room, draftMessage, replyId, draftId, expiresDays, accountViewModel.account)

fun routeToMessage(
    room: ChatroomKey,
    draftMessage: String? = null,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    account: Account,
): Route {
    account.chatroomList.getOrCreatePrivateChatroom(room)

    // Every prefilled chat draft funnels through here, including the unbounded ones: crash and
    // resource-usage reports, error toasts, shared payloads. A draft too big to fit in the route
    // string makes the destination unmatchable — see [limitToRouteTextArg].
    return Route.Room(
        room,
        message = draftMessage?.limitToRouteTextArg(),
        replyId = replyId,
        draftId = draftId,
        expiresDays = expiresDays,
    )
}

fun routeToMessage(
    user: User,
    draftMessage: String?,
    replyId: HexKey? = null,
    draftId: HexKey? = null,
    expiresDays: Int? = null,
    accountViewModel: BaseAccountViewModel,
): Route = routeToMessage(user.pubkeyHex, draftMessage, replyId, draftId, expiresDays, accountViewModel)

fun routeReplyTo(
    note: Note,
    account: Account,
): Route? {
    // Marmot group messages must reply inside the encrypted group, not as a
    // public kind:1111 comment. The inner kind:9 event has no group hint of
    // its own — we detect the group via the gathering MarmotGroupChatroom,
    // mirroring routeFor() above.
    val marmotGroup = note.inGatherers?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
    if (marmotGroup != null) {
        return Route.MarmotGroupChat(marmotGroup.nostrGroupId, replyId = note.idHex)
    }

    // Concord messages are end-to-end encrypted: a reply must go through the channel plane (a sealed
    // kind-1111), never a public kind-1111 — which would leak the private rumor id onto public relays
    // and wouldn't bind to the channel. Route the reply into the message's minichat, whose composer
    // sends the wrapped reply. For a thread reply (kind-1111) reply into the same flat thread (its
    // root); for a top-level message (kind-9) the message itself is the thread root.
    val concord = note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concord != null) {
        val rootId = (note.event as? CommentEvent)?.rootEventIds()?.firstOrNull() ?: note.idHex
        return Route.ChatMinichat(rootId, concord.channelId.communityId, concord.channelId.channelId)
    }

    // A Buzz/NIP-29 relay-group message (kind-9 or kind-40002, e.g. a Buzz DM shown on the Notifications
    // tab) must reply INSIDE the group, not as a public kind:1111 comment. The group has no hint on the
    // inner event — detect it via the gathering RelayGroupChannel (recorded on consume), mirroring the
    // Marmot/Concord cases above — and open the conversation, where the composer sends the Buzz-dialect
    // reply. Without this it falls through to `else` = Route.GenericCommentPost (kind:1111).
    val relayGroup = note.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }
    if (relayGroup != null) {
        return routeFor(relayGroup)
    }

    val noteEvent = note.event
    return when (noteEvent) {
        is ChannelMessageEvent -> {
            noteEvent.channelId()?.let { channelId ->
                Route.PublicChatChannel(channelId, replyTo = note.idHex)
            }
        }

        is LiveActivitiesChatMessageEvent -> {
            noteEvent.activityAddress()?.let {
                Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag, replyTo = note.idHex)
            }
        }

        is EphemeralChatEvent -> {
            noteEvent.roomId()?.let {
                Route.EphemeralChat(it.id, it.relayUrl.url, replyTo = note.idHex)
            }
        }

        is PublicMessageEvent -> {
            Route.NewPublicMessage(
                users = noteEvent.groupKeySet() - account.userProfile().pubkeyHex,
                parentId = noteEvent.id,
            )
        }

        is TextNoteEvent -> {
            Route.NewShortNote(baseReplyTo = note.idHex)
        }

        is ChatroomKeyable -> {
            // Covers EncryptedDmEvent (NIP-04) and BaseDMGroupEvent (NIP-17) — both
            // implement ChatroomKeyable with identical routing semantics here.
            routeToMessage(
                room = noteEvent.chatroomKey(account.userProfile().pubkeyHex),
                draftMessage = null,
                replyId = noteEvent.id,
                draftId = null,
                account = account,
            )
        }

        is CommentEvent -> {
            if (noteEvent.isGeohashedScoped()) {
                Route.GeoPost(replyTo = note.idHex)
            } else if (noteEvent.isHashtagScoped()) {
                Route.HashtagPost(replyTo = note.idHex)
            } else {
                Route.GenericCommentPost(replyTo = note.idHex)
            }
        }

        is ZapReceiptEvent -> {
            // A public reply can't tag a private zapper without exposing them.
            // When we hold the decrypted sender (we are the zap recipient), reply
            // in their DM room instead of the public comment composer.
            val request = noteEvent.zapRequest
            val privateSender =
                if (request?.isPrivateZap() == true) {
                    account.privateZapsDecryptionCache.cachedPrivateZap(request)?.pubKey
                } else {
                    null
                }

            if (privateSender != null) {
                routeToMessage(ChatroomKey(setOf(privateSender)), null, account = account)
            } else {
                Route.GenericCommentPost(replyTo = note.idHex)
            }
        }

        else -> {
            Route.GenericCommentPost(replyTo = note.idHex)
        }
    }
}

suspend fun routeEditDraftTo(
    note: Note,
    account: Account,
): Route? {
    val noteEvent = note.event as DraftWrapEvent
    val draft = account.draftsDecryptionCache.cachedDraft(noteEvent)

    return when (draft) {
        is ChannelMessageEvent -> {
            draft.channelId()?.let { Route.PublicChatChannel(it, draftId = note.idHex) }
        }

        is LiveActivitiesChatMessageEvent -> {
            draft.activityAddress()?.let { Route.LiveActivityChannel(it.kind, it.pubKeyHex, it.dTag, draftId = note.idHex) }
        }

        is EphemeralChatEvent -> {
            draft.roomId()?.let { Route.EphemeralChat(it.id, it.relayUrl.url, draftId = note.idHex) }
        }

        is ChatroomKeyable -> {
            val room = draft.chatroomKey(account.userProfile().pubkeyHex)
            account.chatroomList.getOrCreatePrivateChatroom(room)
            Route.Room(room, draftId = note.idHex)
        }

        is TextNoteEvent -> {
            Route.NewShortNote(draft = note.idHex)
        }

        is LongFormContentEvent -> {
            Route.NewLongFormPost(draft = note.idHex)
        }

        is ClassifiedsEvent -> {
            Route.NewProduct(draft = note.idHex)
        }

        is PublicMessageEvent -> {
            Route.NewPublicMessage(
                users = draft.groupKeySet() - account.userProfile().pubkeyHex,
                parentId = noteEvent.id,
                draftId = note.idHex,
            )
        }

        is PollEvent -> {
            Route.NewPoll(draft = note.idHex)
        }

        is ZapPollEvent -> {
            Route.NewPoll(draft = note.idHex)
        }

        is CommentEvent -> {
            if (draft.isGeohashedScoped()) {
                Route.GeoPost(draft = note.idHex)
            } else if (draft.isHashtagScoped()) {
                Route.HashtagPost(draft = note.idHex)
            } else {
                Route.GenericCommentPost(draft = note.idHex)
            }
        }

        else -> {
            null
        }
    }
}
