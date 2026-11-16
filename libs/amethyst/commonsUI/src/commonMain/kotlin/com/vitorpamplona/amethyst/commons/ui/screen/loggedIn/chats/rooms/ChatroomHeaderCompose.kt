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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.concordCommunityHasUnreadFlow
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.relayGroupServerHasUnreadFlow
import com.vitorpamplona.amethyst.commons.chats.rooms.rowHasUnread
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.toMembershipNotice
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chatMessageMarksRoomAsRead
import com.vitorpamplona.amethyst.commons.model.chats.ConcordDirectInviteNote
import com.vitorpamplona.amethyst.commons.model.chats.ConcordServerRoomNote
import com.vitorpamplona.amethyst.commons.model.chats.PinnedChatroomNote
import com.vitorpamplona.amethyst.commons.model.chats.RelayGroupServerRoomNote
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concordChannelLastReadRoute
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatPreview
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatPreviewLabels
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatPreviewText
import com.vitorpamplona.amethyst.commons.model.privateChats.chatPreviewOf
import com.vitorpamplona.amethyst.commons.model.relayGroupChannelLastReadRoute
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteHasEvent
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderByParentFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_to_messages
import com.vitorpamplona.amethyst.commons.resources.channel_created
import com.vitorpamplona.amethyst.commons.resources.channel_image
import com.vitorpamplona.amethyst.commons.resources.channel_information_changed_to
import com.vitorpamplona.amethyst.commons.resources.channel_invite_ignore
import com.vitorpamplona.amethyst.commons.resources.channel_invite_leave
import com.vitorpamplona.amethyst.commons.resources.channel_invite_row_added_you
import com.vitorpamplona.amethyst.commons.resources.channel_invite_row_added_you_by
import com.vitorpamplona.amethyst.commons.resources.chat_preview_decrypting
import com.vitorpamplona.amethyst.commons.resources.chat_preview_note
import com.vitorpamplona.amethyst.commons.resources.chat_preview_photo
import com.vitorpamplona.amethyst.commons.resources.chat_preview_video
import com.vitorpamplona.amethyst.commons.resources.chat_preview_you_prefix
import com.vitorpamplona.amethyst.commons.resources.chat_system_renamed_conversation_to
import com.vitorpamplona.amethyst.commons.resources.chat_system_renamed_conversation_to_you
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_from
import com.vitorpamplona.amethyst.commons.resources.concord_direct_invite_label
import com.vitorpamplona.amethyst.commons.resources.concord_home_title
import com.vitorpamplona.amethyst.commons.resources.concord_server_label
import com.vitorpamplona.amethyst.commons.resources.cordn_group_no_messages_yet
import com.vitorpamplona.amethyst.commons.resources.cordn_group_untitled
import com.vitorpamplona.amethyst.commons.resources.cordn_group_via_coordinator
import com.vitorpamplona.amethyst.commons.resources.cordn_preview_deleted
import com.vitorpamplona.amethyst.commons.resources.cordn_preview_file
import com.vitorpamplona.amethyst.commons.resources.cordn_preview_photo
import com.vitorpamplona.amethyst.commons.resources.cordn_preview_video
import com.vitorpamplona.amethyst.commons.resources.cordn_preview_voice_note
import com.vitorpamplona.amethyst.commons.resources.could_not_decrypt_the_message
import com.vitorpamplona.amethyst.commons.resources.ephemeral_relay_chat
import com.vitorpamplona.amethyst.commons.resources.geohash_chat
import com.vitorpamplona.amethyst.commons.resources.leave
import com.vitorpamplona.amethyst.commons.resources.loading_feed
import com.vitorpamplona.amethyst.commons.resources.marmot_group
import com.vitorpamplona.amethyst.commons.resources.mute_notifications
import com.vitorpamplona.amethyst.commons.resources.muted_chat_content_description
import com.vitorpamplona.amethyst.commons.resources.pin_conversation
import com.vitorpamplona.amethyst.commons.resources.pinned_chat_no_messages_loaded
import com.vitorpamplona.amethyst.commons.resources.pinned_to_top
import com.vitorpamplona.amethyst.commons.resources.public_chat
import com.vitorpamplona.amethyst.commons.resources.referenced_event_not_found
import com.vitorpamplona.amethyst.commons.resources.relay_group_no_messages_yet
import com.vitorpamplona.amethyst.commons.resources.relay_group_server_label
import com.vitorpamplona.amethyst.commons.resources.remove_from_messages
import com.vitorpamplona.amethyst.commons.resources.unmute_notifications
import com.vitorpamplona.amethyst.commons.resources.unpin_conversation
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.layouts.ChatHeaderLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.chatRowTitleWeight
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BlankNote
import com.vitorpamplona.amethyst.commons.ui.note.ChatRoomFaces
import com.vitorpamplona.amethyst.commons.ui.note.LoadDecryptedContentOrNull
import com.vitorpamplona.amethyst.commons.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.ObserveDraftEvent
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.note.elements.ToggleableTimeAgoText
import com.vitorpamplona.amethyst.commons.ui.platform.rememberConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.observeChatPreviewText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.buzzTimelinePreviewSummary
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.loadMarmotRelayIcon
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotGroupPreviewText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotGroupTitle
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.marmotOtherMembers
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.rememberMarmotGroupAvatarUrl
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.RoomNameDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.header.reportWarningContentDescription
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.ConcordLeaveDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.ephemChat.LoadEphemeralChatChannel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn.coordinatorDisplayName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ChatRowAvatarSize
import com.vitorpamplona.amethyst.commons.ui.theme.ChatRowPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.cordn.appEncryptedMedia.CordnMediaTag
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.workspace.isBuzzForum
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.StringResource
import kotlin.math.max
import kotlin.math.min

@Composable
fun ChatroomHeaderCompose(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Some Messages rows have no event: a per-relay grouped row (RelayGroupServerRoomNote), or a
    // joined Marmot/NIP-29 group with no messages yet (an event-less placeholder carrying its channel
    // as a gatherer). Render these directly instead of waiting for an event that never arrives, which
    // would blank the row.
    //
    // A cordn room has no event *ever*, not just before its first message: its
    // messages are MLS envelopes that never become Notes (§3.3 of
    // amethyst/plans/2026-09-19-cordn-ui.md), so the row is only ever a carrier
    // for the room in `inGatherers`. Leaving it out of this list sent every
    // cordn room down the branch that waits for an event and drew `BlankNote()`
    // instead — the room was in the feed, correctly, and simply had no pixels.
    val rendersWithoutEvent =
        baseNote is RelayGroupServerRoomNote ||
            baseNote is PinnedChatroomNote ||
            baseNote is ConcordServerRoomNote ||
            baseNote is ConcordDirectInviteNote ||
            (
                baseNote.event == null &&
                    baseNote.inGatherers?.any {
                        it is MarmotGroupChatroom || it is RelayGroupChannel || it is ConcordChannel || it is CordnGroupChatroom
                    } == true
            )

    if (baseNote.event != null || rendersWithoutEvent) {
        ChatroomComposeChannelOrUser(baseNote, accountViewModel, nav)
    } else {
        val hasEvent by observeNoteHasEvent(baseNote, accountViewModel)
        if (hasEvent) {
            ChatroomComposeChannelOrUser(baseNote, accountViewModel, nav)
        } else {
            UserFinderByParentFilterAssemblerSubscription(baseNote, accountViewModel)
            BlankNote()
        }
    }
}

@Composable
fun ChatroomComposeChannelOrUser(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseNoteEvent = baseNote.event
    if (baseNoteEvent is DraftWrapEvent) {
        ObserveDraftEvent(baseNote, accountViewModel) { innerNote ->
            ChatroomEntry(innerNote, accountViewModel, nav, isDraft = true)
        }
    } else {
        ChatroomEntry(baseNote, accountViewModel, nav)
    }
}

@Composable
private fun ChatroomEntry(
    lastMessage: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    isDraft: Boolean = false,
) {
    if (lastMessage is RelayGroupServerRoomNote) {
        RelayGroupServerRoomCompose(lastMessage, accountViewModel, nav)
        return
    }

    if (lastMessage is ConcordServerRoomNote) {
        ConcordServerRoomCompose(lastMessage, accountViewModel, nav)
        return
    }

    // A pinned DM whose messages are all older than the loaded gift wraps: same row, no preview.
    if (lastMessage is PinnedChatroomNote) {
        UserRoomCompose(lastMessage.room, lastMessage, isDraft = false, accountViewModel, nav)
        return
    }

    if (lastMessage is ConcordDirectInviteNote) {
        ConcordDirectInviteRoomCompose(lastMessage, accountViewModel, nav)
        return
    }

    val marmotGroup = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }
    if (marmotGroup != null) {
        MarmotGroupRoomCompose(lastMessage, marmotGroup, accountViewModel, nav)
        return
    }

    val cordnGroup = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? CordnGroupChatroom }
    if (cordnGroup != null) {
        CordnGroupRoomCompose(cordnGroup, accountViewModel, nav)
        return
    }

    val relayGroup = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }
    if (relayGroup != null) {
        RelayGroupRoomCompose(lastMessage, relayGroup, accountViewModel, nav)
        return
    }

    val concordChannel = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concordChannel != null) {
        ConcordRoomCompose(lastMessage, concordChannel, accountViewModel, nav)
        return
    }

    val geohashChannel = lastMessage.inGatherers?.firstNotNullOfOrNull { it as? GeohashChatChannel }
    if (geohashChannel != null) {
        GeohashRoomCompose(lastMessage, geohashChannel, accountViewModel, nav)
        return
    }

    // A relay's "somebody added you" verdict (kind 44100) stands in for the group it invites me to.
    // Matched before the generic group-scoped fallback below, which would otherwise render it as an
    // ordinary joined-group row: with the relay keypair's npub and the raw JSON body as the preview,
    // and a long-press menu offering to leave a group I never agreed to join.
    val inviteEvent = lastMessage.event as? MemberAddedNotificationEvent
    if (inviteEvent != null) {
        ChannelInviteRoomCompose(lastMessage, accountViewModel, nav)
        return
    }

    // A NIP-29 group message whose channel gatherer didn't attach (e.g. loaded before its channel
    // existed, or via a path that skips attach) has no case in the when() below and would blank out.
    // Resolve the group from its `h` tag + provenance relay and render the group row anyway.
    val groupScopedEvent = lastMessage.event?.takeIf { it.isGroupScoped() }
    if (groupScopedEvent != null) {
        val gid = groupScopedEvent.groupId()
        val hostRelay = lastMessage.relays.firstOrNull()
        if (gid != null && hostRelay != null) {
            RelayGroupRoomCompose(
                lastMessage,
                LocalCache.getOrCreateRelayGroupChannel(GroupId(gid, hostRelay)),
                accountViewModel,
                nav,
            )
            return
        }
    }

    val baseNoteEvent = lastMessage.event
    when (baseNoteEvent) {
        // ChannelMessageEvent and ChannelMetadataEvent both expose channelId() via
        // IsInPublicChatChannel and render identically here. Matched explicitly (rather
        // than on the IsInPublicChatChannel interface) so the channel-admin events
        // ChannelHideMessageEvent/ChannelMuteUserEvent keep falling through to `else`.
        is ChannelMessageEvent, is ChannelMetadataEvent -> {
            baseNoteEvent.channelId()?.let {
                LoadPublicChatChannel(it) { channel ->
                    ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
                }
            }
        }

        is ChannelCreateEvent -> {
            LoadPublicChatChannel(baseNoteEvent.id) { channel ->
                ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
            }
        }

        is ChatroomKeyable -> {
            val room = baseNoteEvent.chatroomKey(accountViewModel.userProfile().pubkeyHex)
            UserRoomCompose(room, lastMessage, isDraft, accountViewModel, nav)
        }

        is EphemeralChatEvent -> {
            baseNoteEvent.roomId()?.let {
                LoadEphemeralChatChannel(it) { channel ->
                    ChannelRoomCompose(lastMessage, channel, accountViewModel, nav)
                }
            }
        }

        else -> {
            UserFinderByParentFilterAssemblerSubscription(lastMessage, accountViewModel)
            BlankNote()
        }
    }
}

@Composable
private fun ChannelRoomCompose(
    lastMessage: Note,
    channel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val authorName by observeUserName(lastMessage.author!!, accountViewModel)
    val channelState by observeChannel(channel, accountViewModel)

    val channel = channelState?.channel as? PublicChatChannel ?: return

    val channelPicture = channel.profilePicture()
    val channelName = channel.toBestDisplayName()

    val noteEvent = lastMessage.event

    val description =
        if (noteEvent is ChannelCreateEvent) {
            stringRes(Res.string.channel_created)
        } else if (noteEvent is ChannelMetadataEvent) {
            "${stringRes(Res.string.channel_information_changed_to)} "
        } else {
            noteEvent?.content?.take(200)
        }

    // One predicate for the row dot and the bottom-bar badge — see rowHasUnread.
    // `emptyFlow()` is a shared singleton, so a row that can never be unread allocates nothing.
    // The seed matters: collection only starts after the first composition, so without it every
    // row would paint dotless for a frame and then correct itself while scrolling.
    val unread = remember(lastMessage) { rowHasUnread(lastMessage, accountViewModel.account) }
    val hasNewMessages by (unread?.flow ?: emptyFlow()).collectAsStateWithLifecycle(unread?.initial ?: false)

    var menuOpen by remember { mutableStateOf(false) }
    // Kept as a State (no `by`) so `.value` is read only inside the title and menu-text
    // slots, confining mute-toggle invalidations to those scopes.
    val mutedChats = accountViewModel.mutedPublicChatsFlow().collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = channel.idHex,
        channelPicture = channelPicture,
        channelTitle = { modifier ->
            val isMuted = channel.idHex in mutedChats.value
            ChannelTitleWithLabelInfo(
                channelName,
                if (isMuted) MaterialSymbols.NotificationsOff else MaterialSymbols.Public,
                Res.string.public_chat,
                modifier,
                labelContentDescription = if (isMuted) stringRes(Res.string.muted_chat_content_description) else null,
            )
        },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = "$authorName: $description",
        hasNewMessages = hasNewMessages,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.Public,
        onClick = { nav.nav(routeFor(channel)) },
        onLongClick = { menuOpen = true },
    )

    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    stringRes(
                        if (channel.idHex in mutedChats.value) {
                            Res.string.unmute_notifications
                        } else {
                            Res.string.mute_notifications
                        },
                    ),
                )
            },
            onClick = {
                accountViewModel.toggleMutedPublicChat(channel.idHex)
                menuOpen = false
            },
        )
    }
}

@Composable
private fun ChannelRoomCompose(
    lastMessage: Note,
    channel: EphemeralChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val authorName by observeUserName(lastMessage.author!!, accountViewModel)
    val channelState by observeChannel(channel, accountViewModel)

    val channel = channelState?.channel as? EphemeralChatChannel ?: return

    val relayInfo by loadRelayInfo(channel.roomId.relayUrl)

    val noteEvent = lastMessage.event
    val description = noteEvent?.content?.take(200)

    val lastReadTime by accountViewModel.account.loadLastReadFlow("Channel/${channel.roomId.toKey()}").collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = channel.roomId.toKey(),
        channelPicture = relayInfo.icon,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(channel.toBestDisplayName(), MaterialSymbols.Timer, Res.string.ephemeral_relay_chat, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = "$authorName: $description",
        hasNewMessages = (noteEvent?.createdAt ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.Timer,
        onClick = { nav.nav(routeFor(channel)) },
    )
}

@Composable
private fun GeohashRoomCompose(
    lastMessage: Note,
    channel: GeohashChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(channel, accountViewModel)
    val geohashChannel = channelState?.channel as? GeohashChatChannel ?: channel

    // Anonymous cells have no author profile; the sender's display name lives in the message's `n` tag.
    val noteEvent = lastMessage.event as? GeohashChatEvent
    val nick = noteEvent?.nickname()?.takeIf { it.isNotBlank() } ?: lastMessage.author?.pubkeyHex?.take(8)
    val description = noteEvent?.content?.take(200)
    val lastContent = if (noteEvent != null) "$nick: $description" else ""

    val lastReadTime by accountViewModel.account.loadLastReadFlow("Geohash/${geohashChannel.geohash}").collectAsStateWithLifecycle()

    ChannelName(
        channelIdHex = "Geohash/${geohashChannel.geohash}",
        channelPicture = null,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(geohashChannel.toBestDisplayName(), MaterialSymbols.LocationOn, Res.string.geohash_chat, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = (noteEvent?.createdAt ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.LocationOn,
        onClick = { nav.nav(Route.GeohashChat(geohashChannel.geohash)) },
    )
}

@Composable
private fun MarmotGroupRoomCompose(
    lastMessage: Note,
    chatroom: MarmotGroupChatroom,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val image by chatroom.image.collectAsStateWithLifecycle()
    val avatarUrl by chatroom.avatarUrl.collectAsStateWithLifecycle()
    val relays by chatroom.relays.collectAsStateWithLifecycle()
    val adminPubkeys by chatroom.adminPubkeys.collectAsStateWithLifecycle()

    val members by chatroom.members.collectAsStateWithLifecycle()
    val otherMembers = remember(members) { marmotOtherMembers(members, accountViewModel.account.signer.pubKey) }
    val groupName = marmotGroupTitle(displayName, otherMembers, chatroom.nostrGroupId, accountViewModel)

    // The row is handed the group's placeholder note when it has no messages.
    val lastContent = marmotGroupPreviewText(lastMessage.takeIf { it.event != null }, accountViewModel)

    val lastReadTime by accountViewModel.account.loadLastReadFlow(marmotGroupLastReadRoute(chatroom.nostrGroupId)).collectAsStateWithLifecycle()

    val hasGroupFace = avatarUrl != null || image != null || !displayName.isNullOrBlank()
    if (!hasGroupFace && otherMembers.isNotEmpty()) {
        // An unnamed group without an avatar (White Noise's 1:1 chats) is about its people:
        // show them, as a NIP-17 room does, instead of a relay icon.
        ChannelName(
            channelPicture = { ChatRoomFaces(userHexList = otherMembers, size = ChatRowAvatarSize, accountViewModel = accountViewModel) },
            channelTitle = { modifier -> ChannelTitleWithLabelInfo(groupName, MaterialSymbols.Lock, Res.string.marmot_group, modifier) },
            channelLastTime = lastMessage.createdAt(),
            channelLastContent = lastContent,
            hasNewMessages = (lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime,
            onClick = { nav.nav(Route.MarmotGroupChat(chatroom.nostrGroupId)) },
        )
        return
    }

    // Prefer the group's own avatar — the plain https link first, then the
    // encrypted Blossom blob; when it has neither, fall back to the NIP-11 icon
    // of one of the group's relays (fetched on a cache miss). Resolved only here: a row that
    // shows its members' faces above never draws it, and the relay icon is a NIP-11 fetch.
    val channelPicture =
        if (avatarUrl != null || image != null) {
            rememberMarmotGroupAvatarUrl(avatarUrl, image, accountViewModel, adminPubkeys)
        } else {
            loadMarmotRelayIcon(relays)
        }

    ChannelName(
        channelIdHex = chatroom.nostrGroupId,
        channelPicture = channelPicture,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(groupName, MaterialSymbols.Lock, Res.string.marmot_group, modifier) },
        channelLastTime = lastMessage.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = (lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.Lock,
        onClick = { nav.nav(Route.MarmotGroupChat(chatroom.nostrGroupId)) },
    )
}

/**
 * What to call a message whose whole content is an attachment.
 *
 * The first attachment decides, which is what the eye does too: a row is one
 * line and a message with a photo and a file in it is, to a reader skimming the
 * inbox, a message with a photo in it.
 */
private fun attachmentLabelFor(tags: TagArray): StringResource {
    val first = CordnMediaTag.parseAll(tags).firstOrNull() ?: return Res.string.cordn_preview_file
    return when {
        first.isAudio -> Res.string.cordn_preview_voice_note
        first.isImage -> Res.string.cordn_preview_photo
        first.mimeType.startsWith("video/") -> Res.string.cordn_preview_video
        else -> Res.string.cordn_preview_file
    }
}

/**
 * One cordn room in the Messages list.
 *
 * Takes the room rather than the Note, unlike every other row here. The Note is
 * only a carrier: cordn messages are MLS envelopes that never reach LocalCache,
 * so there is no `lastMessage.event` to read a preview from and the live data
 * is on the room. Reading the Note would render an empty row forever.
 */
@Composable
internal fun CordnGroupRoomCompose(
    chatroom: CordnGroupChatroom,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val name by chatroom.name.collectAsStateWithLifecycle()
    val newest by chatroom.newest.collectAsStateWithLifecycle()
    val annotations by chatroom.annotations.collectAsStateWithLifecycle()
    val unread by chatroom.unreadCount.collectAsStateWithLifecycle()

    val groupName = name?.takeIf { it.isNotBlank() } ?: stringRes(Res.string.cordn_group_untitled, chatroom.gid.take(8))

    // Who carries this conversation. "cordn" was the same word on every cordn
    // row and told a reader nothing they could act on; the coordinator is the
    // one server that sees every message in the group, so naming it is the
    // thing worth the space.
    val coordinators by (
        accountViewModel.account.cordnRuntime?.coordinators
            ?: remember { MutableStateFlow(emptyList<CoordinatorConfig>()) }
    ).collectAsStateWithLifecycle()
    val coordinatorName =
        coordinatorDisplayName(
            pubKey = chatroom.coordinatorPubKey,
            label = coordinators.firstOrNull { it.pubKey == chatroom.coordinatorPubKey }?.label,
            accountViewModel = accountViewModel,
        )

    val lastContent =
        newest?.let { message ->
            val authorName by observeUserName(LocalCache.getOrCreateUser(message.envelope.pubKey), accountViewModel)
            // The edited text when there is one: showing the original in the
            // inbox while the room shows the edit is the kind of mismatch that
            // reads as a sync bug.
            val text = annotations.contentOf(message.envelope.id) ?: message.envelope.content
            // A voice note or a photo carries no text, so the row used to read
            // "Someone: " and trail off — an attachment looked like an empty
            // message. Name what was sent instead, the way the bubble does.
            val preview =
                text.takeIf { it.isNotBlank() }
                    ?: if (annotations.isDeleted(message.envelope.id)) {
                        stringRes(Res.string.cordn_preview_deleted)
                    } else {
                        stringRes(attachmentLabelFor(message.envelope.tags))
                    }
            "$authorName: ${preview.take(200)}"
        } ?: stringRes(Res.string.cordn_group_no_messages_yet)

    ChannelName(
        channelIdHex = chatroom.gid,
        channelPicture = null,
        channelTitle = { modifier ->
            ChannelTitleWithLabelInfo(
                channelName = groupName,
                labelIcon = MaterialSymbols.Dns,
                labelText = coordinatorName,
                modifier = modifier,
                // The pill no longer says "cordn", so the kind of room has to
                // reach a screen reader some other way.
                labelContentDescription = stringRes(Res.string.cordn_group_via_coordinator, coordinatorName),
            )
        },
        channelLastTime = newest?.envelope?.createdAt,
        channelLastContent = lastContent,
        // Counted against the read position the room persists, on the
        // coordinator's cursor rather than the sender's clock -- see
        // CordnGroupChatroom.unreadCount for why a clock cannot be trusted
        // with this. Annotations and this account's own echoes do not count.
        hasNewMessages = unread > 0,
        loadProfilePicture = accountViewModel.settings.showProfilePictures(),
        loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.Groups,
        onClick = { nav.nav(Route.CordnGroupChat(chatroom.coordinatorPubKey, chatroom.gid)) },
    )
}

@Composable
private fun RelayGroupRoomCompose(
    lastMessage: Note,
    baseChannel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val author = lastMessage.author
    val noteEvent = lastMessage.event
    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            // A Buzz timeline row (system line, huddle/job activity, diff) carries JSON/diff in its
            // content, so show its human-readable summary — the same text the in-chat row renders —
            // rather than "author: {json}". Plain chat messages fall through to the usual framing,
            // showing the latest edit with mentions as names.
            buzzTimelinePreviewSummary(noteEvent, accountViewModel) ?: "$authorName: ${observeChatPreviewText(lastMessage)}"
        } else {
            // Event-less placeholder row. Until the channel's `limit = 1` preview REQ settles we cannot
            // tell an empty channel from one whose newest message simply hasn't arrived, and claiming
            // "No messages yet" for a busy channel reads as a bug — so say "Loading" while the joined
            // fleet is still fetching, and commit to the empty wording only once it has.
            val stillFetching by accountViewModel
                .dataSources()
                .relayGroupJoinedChatTail.tail.loadingMore
                .collectAsStateWithLifecycle()
            if (stillFetching) {
                stringRes(Res.string.loading_feed)
            } else {
                stringRes(Res.string.relay_group_no_messages_yet)
            }
        }

    RelayGroupRow(
        baseChannel = baseChannel,
        lastContent = lastContent,
        lastTime = lastMessage.createdAt(),
        accountViewModel = accountViewModel,
        nav = nav,
    ) { channel, dismiss ->
        // Long-press brings the group's membership actions to the Messages row itself, mirroring the
        // group top bar so "Remove from Messages" (drop from my list, stay a member) and "Leave"
        // (kind-9022) are reachable without opening the group first.
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.remove_from_messages)) },
            onClick = {
                dismiss()
                accountViewModel.removeRelayGroupFromMessages(channel)
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.leave), color = MaterialTheme.colorScheme.error) },
            onClick = {
                dismiss()
                accountViewModel.leaveRelayGroup(channel)
            },
        )
    }
}

/**
 * One NIP-29 group as a Messages row: its picture, name, host-relay chip, and a caller-supplied
 * "last message" line and long-press menu.
 *
 * Everything except those two is fixed here, because every list that shows a group has to agree on it —
 * the picture fallback, the unread rule, and where a tap goes. A pending invite is a row in the same
 * sense a joined group is (see `ChannelInvitesSection`); it differs only in what its newest line says
 * and what you can do to it, which is exactly the two slots.
 */
@Composable
fun RelayGroupRow(
    baseChannel: RelayGroupChannel,
    lastContent: String?,
    lastTime: Long?,
    accountViewModel: AccountViewModel,
    nav: INav,
    menuContent: @Composable ColumnScope.(channel: RelayGroupChannel, dismiss: () -> Unit) -> Unit,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val groupPicture = channel.profilePicture()?.ifBlank { null }
    val channelPicture =
        if (groupPicture != null) {
            groupPicture
        } else {
            // Missing/blank group picture: fall back to the host relay's NIP-11 icon
            // (loadRelayInfo fetches the doc on a cache miss).
            val relayInfo by loadRelayInfo(channel.groupId.relayUrl)
            relayInfo.icon?.ifBlank { null }
        }

    // Unread dot: the newest chat is newer than the last time I opened this group. Same last-read
    // route the open group's feed advances (relayGroupChannelLastReadRoute), so opening clears it.
    // A placeholder row (no messages yet) has a null createdAt and never lights the dot.
    val lastReadTime by accountViewModel.account.loadLastReadFlow(relayGroupChannelLastReadRoute(channel.groupId)).collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }

    Box {
        ChannelName(
            channelIdHex = channel.groupId.id,
            channelPicture = channelPicture,
            channelTitle = { modifier ->
                // A group id is only unique within its host relay, so the relay is part of the
                // room's identity and is spelled out; tapping it opens that relay's groups.
                ChannelTitleWithLabelInfo(
                    channelName = channel.toBestDisplayName(),
                    labelIcon = MaterialSymbols.Dns,
                    labelText = channel.groupId.relayUrl.displayUrl(),
                    modifier = modifier,
                    onLabelClick = { nav.nav(Route.RelayGroupServer(channel.groupId.relayUrl.url)) },
                )
            },
            channelLastTime = lastTime,
            channelLastContent = lastContent,
            hasNewMessages = (lastTime ?: Long.MIN_VALUE) > lastReadTime,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
            onClick = {
                // A Buzz forum's posts are its threads; open it there, as the workspace list does.
                if (channel.event?.isBuzzForum() == true) {
                    nav.nav(Route.RelayGroupThreads(channel.groupId.id, channel.groupId.relayUrl.url))
                } else {
                    nav.nav(Route.RelayGroup(channel.groupId.id, channel.groupId.relayUrl.url))
                }
            },
            fallbackSymbol = MaterialSymbols.Groups,
            onLongClick = { menuOpen = true },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menuContent(channel) { menuOpen = false }
        }
    }
}

/**
 * A pending channel invite as a Messages row: the group it invites me to, with the invitation itself
 * as the row's newest line.
 *
 * New Requests is a list of rooms awaiting a decision and this is one, so it sorts in among the
 * unaccepted DMs by when the invite landed rather than being pinned above them. Deciding happens the
 * same way it does for a joined group: tap opens the channel so it can be read first (its top bar
 * offers "Add to Messages"), long-press brings the three answers to the row.
 */
@Composable
private fun ChannelInviteRoomCompose(
    inviteNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Collected rather than read as .value, for the same reason as RenderChannelInvite: the workspace
    // set lands asynchronously, and a snapshot read never recomposes, so a row drawn before its
    // workspace was known would never appear.
    val workspaces by accountViewModel.account.buzzWorkspaces.flow
        .collectAsStateWithLifecycle()
    val notice = remember(inviteNote, workspaces) { inviteNote.toMembershipNotice(workspaces) } ?: return
    val baseChannel =
        remember(notice) { LocalCache.getOrCreateRelayGroupChannel(GroupId(notice.channelId, notice.relay)) }

    // The actor, not the signer: a kind-44100 is signed by the relay keypair reporting the membership
    // change it made, so naming its author here would put an npub on every invite.
    val actorName = observeUserNameByHex(notice.actor, accountViewModel)
    val lastContent =
        if (notice.actor != null) {
            stringRes(Res.string.channel_invite_row_added_you_by, actorName)
        } else {
            stringRes(Res.string.channel_invite_row_added_you)
        }

    RelayGroupRow(
        baseChannel = baseChannel,
        lastContent = lastContent,
        lastTime = notice.createdAt,
        accountViewModel = accountViewModel,
        nav = nav,
    ) { channel, dismiss ->
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.add_to_messages)) },
            onClick = {
                dismiss()
                accountViewModel.acceptChannelInvite(channel)
            },
        )
        // Ignore is a local display choice that leaves me in the roster; Leave is the kind-9022 that
        // actually removes me from the channel. Keeping both means "get this off my list" never has
        // to mean "announce to the relay that I left".
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.channel_invite_ignore)) },
            onClick = {
                dismiss()
                accountViewModel.dismissChannelInvite(channel.groupId.id)
            },
        )
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.channel_invite_leave), color = MaterialTheme.colorScheme.error) },
            onClick = {
                dismiss()
                accountViewModel.leaveChannelInvite(channel)
            },
        )
    }
}

@Composable
private fun ConcordRoomCompose(
    lastMessage: Note,
    baseChannel: ConcordChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? ConcordChannel ?: baseChannel

    val author = lastMessage.author
    val noteEvent = lastMessage.event
    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            "$authorName: ${observeChatPreviewText(lastMessage)}"
        } else {
            // Event-less placeholder row for a just-joined channel with no messages yet.
            channel.communityName ?: stringRes(Res.string.relay_group_no_messages_yet)
        }

    // Unread dot: the newest timeline message is newer than the last time I opened this channel.
    // Same last-read route the open channel's feed advances (concordChannelLastReadRoute), so
    // opening clears it. `lastMessage` is already the newest timeline note (or a null-timestamp
    // placeholder), so this stays in step with the badges on the Concord channel-list screen.
    val lastReadTime by accountViewModel.account
        .loadLastReadFlow(concordChannelLastReadRoute(channel.channelId.communityId, channel.channelId.channelId))
        .collectAsStateWithLifecycle()

    // Concord has no server-side membership beyond my own kind-13302 list, so there is no soft
    // "Remove from Messages" distinct from leaving — the only action is "Leave" (drop the community
    // from my list = I'm out). Long-press surfaces it on the row with the same confirm the community
    // screen uses; leaving a channel row leaves the whole community it belongs to (the dialog names it).
    val communityId = channel.channelId.communityId
    val isOwner =
        accountViewModel.account.concordSessions
            .sessionFor(communityId)
            ?.entry
            ?.owner == accountViewModel.account.signer.pubKey
    var menuOpen by remember { mutableStateOf(false) }
    var showLeave by remember { mutableStateOf(false) }

    if (showLeave) {
        ConcordLeaveDialog(
            communityName = channel.communityName ?: channel.toBestDisplayName(),
            isOwner = isOwner,
            onDismiss = { showLeave = false },
            onConfirm = {
                showLeave = false
                accountViewModel.leaveConcordCommunity(communityId)
            },
        )
    }

    Box {
        ChannelName(
            channelIdHex = channel.channelId.channelId,
            channelPicture = rememberConcordImageModel(channel.communityIcon, accountViewModel),
            channelTitle = { modifier ->
                val communityName = channel.communityName
                if (communityName != null) {
                    // The label names the parent community and, when tapped, opens that community's
                    // channel list — the "chip that opens the Concord Channel" entry point.
                    ChannelTitleWithLabelInfo(
                        channelName = channel.toBestDisplayName(),
                        labelIcon = MaterialSymbols.Group,
                        labelText = communityName,
                        modifier = modifier,
                        onLabelClick = { nav.nav(Route.ConcordServer(channel.channelId.communityId)) },
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
                        ChannelTitleText(channel.toBestDisplayName(), Modifier.weight(1f, fill = false))
                    }
                }
            },
            channelLastTime = lastMessage.createdAt(),
            channelLastContent = lastContent,
            hasNewMessages = (lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif =
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value,
            fallbackSymbol = MaterialSymbols.Group,
            onClick = { nav.nav(Route.Concord(channel.channelId.communityId, channel.channelId.channelId)) },
            onLongClick = { menuOpen = true },
        )

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.leave), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    menuOpen = false
                    showLeave = true
                },
            )
        }
    }
}

@Composable
private fun RelayGroupServerRoomCompose(
    row: RelayGroupServerRoomNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val relay = row.relay
    val relayInfo by loadRelayInfo(relay)
    val host = relay.displayUrl()
    val name = relayInfo.name?.takeIf { it.isNotBlank() } ?: host

    val author = row.newestMessage?.author
    val noteEvent = row.newestMessage?.event
    val lastContent =
        if (author != null && noteEvent != null) {
            val authorName by observeUserName(author, accountViewModel)
            // Buzz timeline rows (system/huddle/job/diff) carry JSON/diff content — summarize them
            // like the in-chat row instead of printing raw payload; plain chat falls through.
            buzzTimelinePreviewSummary(noteEvent, accountViewModel) ?: "$authorName: ${row.newestMessage?.let { observeChatPreviewText(it) } ?: ""}"
        } else {
            stringRes(Res.string.relay_group_no_messages_yet)
        }

    // Collapsed relay row: light the dot when ANY joined group on this relay has unread chat.
    val hasNewMessages by remember(relay) {
        relayGroupServerHasUnreadFlow(accountViewModel.account, relay)
    }.collectAsStateWithLifecycle(false)

    ChannelName(
        channelIdHex = relay.url,
        channelPicture = relayInfo.icon,
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(name, MaterialSymbols.Dns, Res.string.relay_group_server_label, modifier) },
        channelLastTime = row.newestMessage?.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = hasNewMessages,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.Dns,
        onClick = { nav.nav(Route.RelayGroupServer(relay.url)) },
    )
}

/**
 * A pending Concord Direct Invite as a New Request: the community's name and image, who sent it, and
 * a tap into the Concord hub, where the invite waits with Accept / Decline.
 */
@Composable
private fun ConcordDirectInviteRoomCompose(
    row: ConcordDirectInviteNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val invite = row.invite
    val senderName = observeUserNameByHex(invite.sender, accountViewModel)

    ChannelName(
        channelIdHex = invite.communityId,
        channelPicture = rememberConcordImageModel(invite.icon, accountViewModel),
        channelTitle = { modifier ->
            ChannelTitleWithLabelInfo(
                invite.name.ifBlank { stringRes(Res.string.concord_home_title) },
                MaterialSymbols.Group,
                Res.string.concord_direct_invite_label,
                modifier,
            )
        },
        channelLastTime = row.createdAt(),
        channelLastContent = stringRes(Res.string.concord_direct_invite_from, senderName),
        hasNewMessages = true,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.Group,
        onClick = { nav.nav(Route.Concords) },
    )
}

@Composable
private fun ConcordServerRoomCompose(
    row: ConcordServerRoomNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Community name/icon from the folded Control Plane (bumped via the session revision).
    val revision by accountViewModel.account.concordSessions.revision
        .collectAsStateWithLifecycle()
    val session =
        remember(row.communityId, revision) {
            accountViewModel.account.concordSessions.sessionFor(row.communityId)
        }
    val metadata = remember(session, revision) { session?.state?.value?.metadata }
    // Before the Control Plane folds there is no metadata; the name the community was joined under
    // still tells the user which one it is.
    val name =
        metadata?.name?.takeIf { it.isNotBlank() }
            ?: session?.entry?.name?.takeIf { it.isNotBlank() }
            ?: stringRes(Res.string.concord_home_title)

    val author = row.newestMessage?.author
    val newestMessage = row.newestMessage
    val lastContent =
        if (author != null && newestMessage?.event != null) {
            val authorName by observeUserName(author, accountViewModel)
            "$authorName: ${observeChatPreviewText(newestMessage)}"
        } else {
            stringRes(Res.string.relay_group_no_messages_yet)
        }

    // Collapsed community row: light the dot when ANY channel in this community has unread messages.
    val hasNewMessages by remember(row.communityId) {
        concordCommunityHasUnreadFlow(accountViewModel.account, row.communityId)
    }.collectAsStateWithLifecycle(false)

    ChannelName(
        channelIdHex = row.communityId,
        channelPicture = rememberConcordImageModel(metadata?.icon, accountViewModel),
        channelTitle = { modifier -> ChannelTitleWithLabelInfo(name, MaterialSymbols.Group, Res.string.concord_server_label, modifier) },
        channelLastTime = row.newestMessage?.createdAt(),
        channelLastContent = lastContent,
        hasNewMessages = hasNewMessages,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
        fallbackSymbol = MaterialSymbols.Group,
        onClick = { nav.nav(Route.ConcordServer(row.communityId)) },
    )
}

/**
 * Renders a Messages row title as the channel name followed by the room-kind glyph (globe for a
 * Public Chat, lock for a Marmot Group, ...). The kind's word is the glyph's content description:
 * spelled out in a chip on every row it competed with the room name for attention. The title takes
 * its weight from [ChatHeaderLayout] (bold only when unread), so none is set here.
 */
@Composable
internal fun ChannelTitleWithLabelInfo(
    channelName: String,
    labelIcon: MaterialSymbol,
    label: StringResource,
    modifier: Modifier,
    labelContentDescription: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        ChannelTitleText(channelName, Modifier.weight(1f, fill = false))
        Spacer(TitleLabelGap)
        ChatRowTypeIcon(labelIcon, labelContentDescription ?: stringRes(id = label))
    }
}

private val TitleLabelGapWidth = 6.dp
private val TitleLabelGap = Modifier.width(TitleLabelGapWidth)

@Composable
private fun ChannelTitleText(
    channelName: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = channelName,
        style = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * As above, for a label that is a name rather than a fixed word, so it is spelled out as a muted
 * [ChatRowLabel]: the relay a NIP-29 group lives on, the Concord community a channel belongs to.
 *
 * A cordn room's label carries its coordinator, which is a value and not a
 * string resource: the coordinator is the one server that carries every message
 * in that group, so which one it is tells a reader more than being told twice
 * that this is a cordn chat.
 */
@Composable
internal fun ChannelTitleWithLabelInfo(
    channelName: String,
    labelIcon: MaterialSymbol,
    labelText: String,
    modifier: Modifier,
    labelContentDescription: String? = null,
    onLabelClick: (() -> Unit)? = null,
) {
    Layout(
        content = {
            ChannelTitleText(channelName)
            ChatRowLabel(
                symbol = labelIcon,
                text = labelText,
                contentDescription = labelContentDescription,
                onClick = onLabelClick,
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val (title, label) = measurables
        val gap = TitleLabelGapWidth.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE / 2
        // The name is what the row is for, so it is sized first: up to [NAME_MIN_SHARE] of the
        // row is held for it before the label gets any. Measured the other way round, a label at
        // its full cap (an npub, a long relay URL) left a narrow pane's name no width at all.
        val nameNeeds = title.maxIntrinsicWidth(Constraints.Infinity)
        val nameReserve = min(nameNeeds, (width * NAME_MIN_SHARE).toInt())
        val labelPlaceable = label.measure(Constraints(maxWidth = (width - nameReserve - gap).coerceAtLeast(0)))
        val labelSpace = if (labelPlaceable.width > 0) labelPlaceable.width + gap else 0
        val titlePlaceable = title.measure(Constraints(maxWidth = (width - labelSpace).coerceAtLeast(0)))

        val height = max(titlePlaceable.height, labelPlaceable.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout((titlePlaceable.width + labelSpace).coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            titlePlaceable.placeRelative(0, (height - titlePlaceable.height) / 2)
            labelPlaceable.placeRelative(titlePlaceable.width + gap, (height - labelPlaceable.height) / 2)
        }
    }
}

/** The share of a title row held for the room name before a text label is sized. */
private const val NAME_MIN_SHARE = 0.6f

/**
 * A warning glyph on the row of a 1:1 room whose counterpart has been reported by someone the user
 * follows. This is the surface where the user decides whether to open an unsolicited DM at all, so it
 * warns before engagement rather than after. Group rooms render nothing.
 */
@Composable
private fun RoomReportWarningIcon(
    preloadedUser: User?,
    accountViewModel: AccountViewModel,
) {
    val user = preloadedUser ?: return
    val flow = remember(user) { accountViewModel.createUserReportWarningFlow(user) }
    val state by flow.collectAsStateWithLifecycle()

    if (state.shouldWarn) {
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = reportWarningContentDescription(state),
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = StdHorzSpacer)
    }
}

@Composable
private fun UserRoomCompose(
    room: ChatroomKey,
    lastMessage: Note,
    isDraft: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var popupExpanded by remember { mutableStateOf(false) }
    // Kept as a State (no `by`) so `.value` is only read inside the firstRow and
    // menu-text slots, confining pin-toggle invalidations to those scopes.
    val pinnedRooms = accountViewModel.pinnedChatroomsFlow().collectAsStateWithLifecycle()

    // A sent message I authored counts as read (#1286, #1287); an unsent draft still needs my attention.
    val newestEvent = lastMessage.event
    val countsAsRead =
        !isDraft &&
            newestEvent != null &&
            chatMessageMarksRoomAsRead(newestEvent, room, accountViewModel.account.signer.pubKey)

    val lastReadTime by accountViewModel.account.loadLastReadFlow(privateChatLastReadRoute(room)).collectAsStateWithLifecycle()
    val hasNewMessages = !countsAsRead && (lastMessage.createdAt() ?: Long.MIN_VALUE) > lastReadTime

    ChatHeaderLayout(
        channelPicture = {
            ChatRoomFaces(
                userHexList = remember(room) { room.users.toList() },
                size = ChatRowAvatarSize,
                accountViewModel = accountViewModel,
            )
        },
        firstRow = {
            val titleWeight = chatRowTitleWeight(hasNewMessages)
            val counterpartHex = room.users.singleOrNull()
            if (counterpartHex != null) {
                // 1:1 room: resolve the counterpart once and share it between the name and the
                // report-warning icon below, instead of each doing its own LoadUser.
                LoadUser(baseUserHex = counterpartHex) { counterpart ->
                    RoomNameDisplay(room, Modifier.weight(1f), accountViewModel, preloadedUser = counterpart, fontWeight = titleWeight)
                    RoomReportWarningIcon(counterpart, accountViewModel)
                }
            } else {
                RoomNameDisplay(room, Modifier.weight(1f), accountViewModel, preloadedUser = null, fontWeight = titleWeight)
            }
            if (room in pinnedRooms.value) {
                Icon(
                    symbol = MaterialSymbols.PushPin,
                    contentDescription = stringRes(Res.string.pinned_to_top),
                    modifier = Size15Modifier,
                    tint = MaterialTheme.colorScheme.placeholderText,
                )
            }
            TimeAgo(lastMessage.createdAt(), hasNewMessages)
        },
        secondRow = {
            if (lastMessage is PinnedChatroomNote) {
                PreviewLine(stringRes(Res.string.pinned_chat_no_messages_loaded))
            } else {
                LastMessagePreview(lastMessage, accountViewModel)
            }
        },
        onClick = { nav.nav(Route.Room(room)) },
        onLongClick = { popupExpanded = true },
        hasNewMessages = hasNewMessages,
    )

    DropdownMenu(
        expanded = popupExpanded,
        onDismissRequest = { popupExpanded = false },
    ) {
        DropdownMenuItem(
            text = {
                Text(stringRes(if (room in pinnedRooms.value) Res.string.unpin_conversation else Res.string.pin_conversation))
            },
            onClick = {
                accountViewModel.toggleChatroomPin(room)
                popupExpanded = false
            },
        )
    }
}

/**
 * The one-line preview of the room's newest message.
 *
 * NIP-04 rooms carry ciphertext in `event.content`, so the preview may only ever come from the
 * decryption cache. [chatPreviewOf] keeps the three not-a-body outcomes apart — still decrypting,
 * never decryptable, and no event at all — so a message that simply hasn't been opened yet isn't
 * mislabelled as unreadable. The pending state resolves on its own: [LoadDecryptedContentOrNull]
 * pushes the plaintext into its state as soon as the signer answers.
 */
@Composable
private fun RowScope.LastMessagePreview(
    lastMessage: Note,
    accountViewModel: AccountViewModel,
) {
    LoadDecryptedContentOrNull(lastMessage, accountViewModel) { content ->
        // Keyed so a scrolling list doesn't re-scan the DM's `p` tags on every recomposition.
        val preview =
            remember(lastMessage.event, content) {
                chatPreviewOf(
                    event = lastMessage.event,
                    decrypted = content,
                    myPubKey = accountViewModel.account.signer.pubKey,
                    canDecrypt = accountViewModel.account.isWriteable(),
                )
            }

        val text =
            when (preview) {
                is ChatPreview.Body -> {
                    // Mark my own newest message with a "You:" prefix (like other messengers) so a
                    // 1:1 room's preview shows who spoke last instead of reading like the counterpart.
                    val sentByMe = lastMessage.author?.pubkeyHex == accountViewModel.account.signer.pubKey
                    if (sentByMe) stringRes(Res.string.chat_preview_you_prefix, preview.text) else preview.text
                }
                is ChatPreview.SubjectChange -> {
                    // A rename with no text has no body to quote; say what it did instead of "You: ".
                    val authorHex = lastMessage.event?.pubKey
                    if (authorHex == accountViewModel.account.signer.pubKey) {
                        stringRes(Res.string.chat_system_renamed_conversation_to_you, preview.subject)
                    } else {
                        stringRes(Res.string.chat_system_renamed_conversation_to, observeUserNameByHex(authorHex, accountViewModel), preview.subject)
                    }
                }
                ChatPreview.Decrypting -> stringRes(Res.string.chat_preview_decrypting)
                ChatPreview.Undecryptable -> stringRes(Res.string.could_not_decrypt_the_message)
                ChatPreview.Missing -> stringRes(Res.string.referenced_event_not_found)
            }

        PreviewLine(rememberTidyPreview(text))
    }
}

@Composable
fun ChannelName(
    channelIdHex: String,
    channelPicture: String?,
    channelTitle: @Composable (Modifier) -> Unit,
    channelLastTime: Long?,
    channelLastContent: String?,
    hasNewMessages: Boolean,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    autoPlayGif: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    // A room with no picture of its own shows this glyph on a tonal disc instead of a robohash.
    // Null keeps the robohash (search results, where a robot face is the established look).
    fallbackSymbol: MaterialSymbol? = null,
) {
    ChannelName(
        channelPicture = {
            if (fallbackSymbol != null && channelPicture.isNullOrBlank()) {
                ChatRowTonalAvatar(fallbackSymbol)
            } else {
                RobohashFallbackAsyncImage(
                    robot = channelIdHex,
                    model = channelPicture,
                    contentDescription = stringRes(Res.string.channel_image),
                    modifier = ChatRowPictureModifier,
                    loadProfilePicture = loadProfilePicture,
                    loadRobohash = loadRobohash,
                    autoPlayGif = autoPlayGif,
                )
            }
        },
        channelTitle,
        channelLastTime,
        channelLastContent,
        hasNewMessages,
        onClick,
        onLongClick,
    )
}

@Composable
fun ChannelName(
    channelPicture: @Composable () -> Unit,
    channelTitle: @Composable (Modifier) -> Unit,
    channelLastTime: Long?,
    channelLastContent: String?,
    hasNewMessages: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    ChatHeaderLayout(
        channelPicture = channelPicture,
        firstRow = {
            channelTitle(Modifier.weight(1f))
            TimeAgo(channelLastTime, hasNewMessages)
        },
        secondRow = {
            PreviewLine(channelLastContent?.let { rememberTidyPreview(it) } ?: stringRes(Res.string.referenced_event_not_found))
        },
        onClick = onClick,
        onLongClick = onLongClick,
        hasNewMessages = hasNewMessages,
    )
}

/** The preview line of a row. Its color comes from [ChatHeaderLayout], which lifts it when unread. */
@Composable
private fun RowScope.PreviewLine(text: String) {
    Text(
        text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
        modifier = Modifier.weight(1f),
    )
}

/**
 * [text] as a preview line should say it: see [ChatPreviewText.tidy]. Keyed on the text, so a row
 * re-tidies only when its newest message changes.
 */
@Composable
private fun rememberTidyPreview(text: String): String {
    val labels =
        ChatPreviewLabels(
            photo = stringRes(Res.string.chat_preview_photo),
            video = stringRes(Res.string.chat_preview_video),
            note = stringRes(Res.string.chat_preview_note),
        )
    return remember(text, labels) {
        ChatPreviewText.tidy(text, labels) { hex -> LocalCache.getUserIfExists(hex)?.toBestDisplayName() }
    }
}

@Composable
private fun TimeAgo(
    channelLastTime: Long?,
    hasNewMessages: Boolean,
) {
    if (channelLastTime == null) return
    // The compact "• 10h" form. Not TimeAgoStyle.Short: on Android that goes through the platform's
    // relative-span formatter ("12 hr. ago", "4 days ago"), which is several times wider and eats
    // into the title. Tight variant because TimeAgoGap already provides the space before the dot.
    // It takes the accent only when the row is unread, beside the dot below it.
    ToggleableTimeAgoText(
        timestamp = channelLastTime,
        style = TimeAgoStyle.DottedTight,
        color = if (hasNewMessages) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        modifier = TimeAgoGap,
    )
}

private val TimeAgoGap = Modifier.padding(start = 8.dp)
