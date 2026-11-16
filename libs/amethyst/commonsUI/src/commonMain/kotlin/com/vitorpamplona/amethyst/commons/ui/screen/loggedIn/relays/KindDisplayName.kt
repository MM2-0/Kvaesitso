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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.attestation
import com.vitorpamplona.amethyst.commons.resources.attestation_request
import com.vitorpamplona.amethyst.commons.resources.attestor_proficiency
import com.vitorpamplona.amethyst.commons.resources.attestor_recommendation
import com.vitorpamplona.amethyst.commons.resources.kind_accepted_badge_set
import com.vitorpamplona.amethyst.commons.resources.kind_adventure_prologue
import com.vitorpamplona.amethyst.commons.resources.kind_adventure_reading
import com.vitorpamplona.amethyst.commons.resources.kind_adventure_scene
import com.vitorpamplona.amethyst.commons.resources.kind_app_recommendations
import com.vitorpamplona.amethyst.commons.resources.kind_appointment
import com.vitorpamplona.amethyst.commons.resources.kind_apps
import com.vitorpamplona.amethyst.commons.resources.kind_appt_rsvp
import com.vitorpamplona.amethyst.commons.resources.kind_audio_header
import com.vitorpamplona.amethyst.commons.resources.kind_audio_track
import com.vitorpamplona.amethyst.commons.resources.kind_authored_podcasts
import com.vitorpamplona.amethyst.commons.resources.kind_badge_awards
import com.vitorpamplona.amethyst.commons.resources.kind_badge_definitions
import com.vitorpamplona.amethyst.commons.resources.kind_blob_data
import com.vitorpamplona.amethyst.commons.resources.kind_blob_headers
import com.vitorpamplona.amethyst.commons.resources.kind_blocked_relays
import com.vitorpamplona.amethyst.commons.resources.kind_blogs
import com.vitorpamplona.amethyst.commons.resources.kind_blossom_auth
import com.vitorpamplona.amethyst.commons.resources.kind_blossom_servers
import com.vitorpamplona.amethyst.commons.resources.kind_bookmark_list
import com.vitorpamplona.amethyst.commons.resources.kind_broadcast_relays
import com.vitorpamplona.amethyst.commons.resources.kind_calendar
import com.vitorpamplona.amethyst.commons.resources.kind_channel_definition
import com.vitorpamplona.amethyst.commons.resources.kind_channel_hide_msg
import com.vitorpamplona.amethyst.commons.resources.kind_channel_list
import com.vitorpamplona.amethyst.commons.resources.kind_channel_message
import com.vitorpamplona.amethyst.commons.resources.kind_channel_metadata
import com.vitorpamplona.amethyst.commons.resources.kind_channel_mute_user
import com.vitorpamplona.amethyst.commons.resources.kind_chess_auth
import com.vitorpamplona.amethyst.commons.resources.kind_chess_challenges
import com.vitorpamplona.amethyst.commons.resources.kind_chess_draw_offer
import com.vitorpamplona.amethyst.commons.resources.kind_chess_game_accept
import com.vitorpamplona.amethyst.commons.resources.kind_chess_game_end
import com.vitorpamplona.amethyst.commons.resources.kind_chess_games
import com.vitorpamplona.amethyst.commons.resources.kind_chess_move
import com.vitorpamplona.amethyst.commons.resources.kind_classifieds
import com.vitorpamplona.amethyst.commons.resources.kind_comments
import com.vitorpamplona.amethyst.commons.resources.kind_community_def
import com.vitorpamplona.amethyst.commons.resources.kind_community_list
import com.vitorpamplona.amethyst.commons.resources.kind_community_post
import com.vitorpamplona.amethyst.commons.resources.kind_contact_card
import com.vitorpamplona.amethyst.commons.resources.kind_day_appointment
import com.vitorpamplona.amethyst.commons.resources.kind_deletions
import com.vitorpamplona.amethyst.commons.resources.kind_dm_file
import com.vitorpamplona.amethyst.commons.resources.kind_dm_message
import com.vitorpamplona.amethyst.commons.resources.kind_dm_relays
import com.vitorpamplona.amethyst.commons.resources.kind_drafts
import com.vitorpamplona.amethyst.commons.resources.kind_dvm_content_req
import com.vitorpamplona.amethyst.commons.resources.kind_dvm_content_resp
import com.vitorpamplona.amethyst.commons.resources.kind_dvm_status
import com.vitorpamplona.amethyst.commons.resources.kind_dvm_user_req
import com.vitorpamplona.amethyst.commons.resources.kind_dvm_user_resp
import com.vitorpamplona.amethyst.commons.resources.kind_ebook
import com.vitorpamplona.amethyst.commons.resources.kind_edits
import com.vitorpamplona.amethyst.commons.resources.kind_emoji_pack_list
import com.vitorpamplona.amethyst.commons.resources.kind_emoji_packs
import com.vitorpamplona.amethyst.commons.resources.kind_ephemeral_chat
import com.vitorpamplona.amethyst.commons.resources.kind_ephemeral_chatrooms
import com.vitorpamplona.amethyst.commons.resources.kind_favorite_podcasts
import com.vitorpamplona.amethyst.commons.resources.kind_favorite_relays
import com.vitorpamplona.amethyst.commons.resources.kind_file_headers
import com.vitorpamplona.amethyst.commons.resources.kind_file_servers
import com.vitorpamplona.amethyst.commons.resources.kind_follow_list
import com.vitorpamplona.amethyst.commons.resources.kind_follow_packs
import com.vitorpamplona.amethyst.commons.resources.kind_geohash_follows
import com.vitorpamplona.amethyst.commons.resources.kind_gift_wraps
import com.vitorpamplona.amethyst.commons.resources.kind_git_issue
import com.vitorpamplona.amethyst.commons.resources.kind_git_patch
import com.vitorpamplona.amethyst.commons.resources.kind_git_pr
import com.vitorpamplona.amethyst.commons.resources.kind_git_pr_update
import com.vitorpamplona.amethyst.commons.resources.kind_git_reply
import com.vitorpamplona.amethyst.commons.resources.kind_git_repo
import com.vitorpamplona.amethyst.commons.resources.kind_git_status_applied
import com.vitorpamplona.amethyst.commons.resources.kind_git_status_closed
import com.vitorpamplona.amethyst.commons.resources.kind_git_status_draft
import com.vitorpamplona.amethyst.commons.resources.kind_git_status_open
import com.vitorpamplona.amethyst.commons.resources.kind_hashtag_follows
import com.vitorpamplona.amethyst.commons.resources.kind_highlights
import com.vitorpamplona.amethyst.commons.resources.kind_http_auth
import com.vitorpamplona.amethyst.commons.resources.kind_index_relay_list
import com.vitorpamplona.amethyst.commons.resources.kind_live_chats
import com.vitorpamplona.amethyst.commons.resources.kind_live_streams
import com.vitorpamplona.amethyst.commons.resources.kind_magazine
import com.vitorpamplona.amethyst.commons.resources.kind_magazine_issue
import com.vitorpamplona.amethyst.commons.resources.kind_medical_data
import com.vitorpamplona.amethyst.commons.resources.kind_meeting_room
import com.vitorpamplona.amethyst.commons.resources.kind_meeting_space
import com.vitorpamplona.amethyst.commons.resources.kind_music_playlist
import com.vitorpamplona.amethyst.commons.resources.kind_music_track
import com.vitorpamplona.amethyst.commons.resources.kind_mute_list
import com.vitorpamplona.amethyst.commons.resources.kind_named_bookmarks
import com.vitorpamplona.amethyst.commons.resources.kind_nests_servers
import com.vitorpamplona.amethyst.commons.resources.kind_nip
import com.vitorpamplona.amethyst.commons.resources.kind_nip04_dms
import com.vitorpamplona.amethyst.commons.resources.kind_nns
import com.vitorpamplona.amethyst.commons.resources.kind_nostr_connect
import com.vitorpamplona.amethyst.commons.resources.kind_notes
import com.vitorpamplona.amethyst.commons.resources.kind_nwc_request
import com.vitorpamplona.amethyst.commons.resources.kind_nwc_response
import com.vitorpamplona.amethyst.commons.resources.kind_old_bookmark_list
import com.vitorpamplona.amethyst.commons.resources.kind_ots
import com.vitorpamplona.amethyst.commons.resources.kind_outbox_relays
import com.vitorpamplona.amethyst.commons.resources.kind_pay_to
import com.vitorpamplona.amethyst.commons.resources.kind_people_lists
import com.vitorpamplona.amethyst.commons.resources.kind_pictures
import com.vitorpamplona.amethyst.commons.resources.kind_pins
import com.vitorpamplona.amethyst.commons.resources.kind_podcast_episode
import com.vitorpamplona.amethyst.commons.resources.kind_podcast_metadata
import com.vitorpamplona.amethyst.commons.resources.kind_poll
import com.vitorpamplona.amethyst.commons.resources.kind_poll_response
import com.vitorpamplona.amethyst.commons.resources.kind_private_relays
import com.vitorpamplona.amethyst.commons.resources.kind_private_zaps
import com.vitorpamplona.amethyst.commons.resources.kind_product_collection
import com.vitorpamplona.amethyst.commons.resources.kind_product_review
import com.vitorpamplona.amethyst.commons.resources.kind_profile
import com.vitorpamplona.amethyst.commons.resources.kind_profile_badges
import com.vitorpamplona.amethyst.commons.resources.kind_profile_gallery
import com.vitorpamplona.amethyst.commons.resources.kind_proxy_relays
import com.vitorpamplona.amethyst.commons.resources.kind_public_message
import com.vitorpamplona.amethyst.commons.resources.kind_push_deregistration
import com.vitorpamplona.amethyst.commons.resources.kind_push_preferences
import com.vitorpamplona.amethyst.commons.resources.kind_push_registration
import com.vitorpamplona.amethyst.commons.resources.kind_reactions
import com.vitorpamplona.amethyst.commons.resources.kind_relay_auth
import com.vitorpamplona.amethyst.commons.resources.kind_relay_discovery
import com.vitorpamplona.amethyst.commons.resources.kind_relay_monitor
import com.vitorpamplona.amethyst.commons.resources.kind_relay_set
import com.vitorpamplona.amethyst.commons.resources.kind_reports
import com.vitorpamplona.amethyst.commons.resources.kind_reposts
import com.vitorpamplona.amethyst.commons.resources.kind_reposts_16
import com.vitorpamplona.amethyst.commons.resources.kind_room_presence
import com.vitorpamplona.amethyst.commons.resources.kind_seals
import com.vitorpamplona.amethyst.commons.resources.kind_search_relays
import com.vitorpamplona.amethyst.commons.resources.kind_shipping_option
import com.vitorpamplona.amethyst.commons.resources.kind_shorts
import com.vitorpamplona.amethyst.commons.resources.kind_shorts_repl
import com.vitorpamplona.amethyst.commons.resources.kind_torrent_comments
import com.vitorpamplona.amethyst.commons.resources.kind_torrents
import com.vitorpamplona.amethyst.commons.resources.kind_trusted_providers
import com.vitorpamplona.amethyst.commons.resources.kind_trusted_relays
import com.vitorpamplona.amethyst.commons.resources.kind_user_delete
import com.vitorpamplona.amethyst.commons.resources.kind_user_settings
import com.vitorpamplona.amethyst.commons.resources.kind_user_status
import com.vitorpamplona.amethyst.commons.resources.kind_video
import com.vitorpamplona.amethyst.commons.resources.kind_video_collaboration
import com.vitorpamplona.amethyst.commons.resources.kind_video_list
import com.vitorpamplona.amethyst.commons.resources.kind_video_repl
import com.vitorpamplona.amethyst.commons.resources.kind_video_subtitles
import com.vitorpamplona.amethyst.commons.resources.kind_video_views
import com.vitorpamplona.amethyst.commons.resources.kind_voice_msg
import com.vitorpamplona.amethyst.commons.resources.kind_voice_reply
import com.vitorpamplona.amethyst.commons.resources.kind_wake
import com.vitorpamplona.amethyst.commons.resources.kind_web_bookmark
import com.vitorpamplona.amethyst.commons.resources.kind_wiki
import com.vitorpamplona.amethyst.commons.resources.kind_workouts
import com.vitorpamplona.amethyst.commons.resources.kind_zap_goals
import com.vitorpamplona.amethyst.commons.resources.kind_zap_poll
import com.vitorpamplona.amethyst.commons.resources.kind_zap_req
import com.vitorpamplona.amethyst.commons.resources.kind_zaps
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryPrologueEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStorySceneEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip62RequestToVanish.RequestToVanishEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.offer.LiveChessGameChallengeEvent
import com.vitorpamplona.quartz.nip64Chess.draw.LiveChessDrawOfferEvent
import com.vitorpamplona.quartz.nip64Chess.end.LiveChessGameEndEvent
import com.vitorpamplona.quartz.nip64Chess.game.ChessGameEvent
import com.vitorpamplona.quartz.nip64Chess.jester.JesterEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.discovery.RelayDiscoveryEvent
import com.vitorpamplona.quartz.nip66RelayMonitor.monitor.RelayMonitorEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent
import com.vitorpamplona.quartz.nip71Video.views.VideoViewEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryRequest.DvmContentDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryRequest.DvmUserDiscoveryRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.userDiscoveryResponse.DvmUserDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.review.ProductReviewEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.deregistration.PushDeregistrationEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.preferences.PushPreferencesEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.registration.PushRegistrationEvent
import org.jetbrains.compose.resources.StringResource

/** Returns the catalog entry for the translated kind name, or null if unknown. */
@Suppress("DEPRECATION")
fun kindDisplayName(kind: Int): StringResource? =
    when (kind) {
        AcceptedBadgeSetEvent.KIND -> Res.string.kind_accepted_badge_set
        AdvertisedRelayListEvent.KIND -> Res.string.kind_outbox_relays
        AppDefinitionEvent.KIND -> Res.string.kind_apps
        AppRecommendationEvent.KIND -> Res.string.kind_app_recommendations
        AppSpecificDataEvent.KIND -> Res.string.kind_user_settings
        AudioHeaderEvent.KIND -> Res.string.kind_audio_header
        AudioTrackEvent.KIND -> Res.string.kind_audio_track
        MusicTrackEvent.KIND -> Res.string.kind_music_track
        EbookEvent.KIND -> Res.string.kind_ebook
        MagazineEvent.KIND -> Res.string.kind_magazine
        MagazineIssueEvent.KIND -> Res.string.kind_magazine_issue
        MusicPlaylistEvent.KIND -> Res.string.kind_music_playlist
        PodcastEpisodeEvent.KIND -> Res.string.kind_podcast_episode
        PodcastMetadataEvent.KIND -> Res.string.kind_podcast_metadata
        AuthoredPodcastsEvent.KIND -> Res.string.kind_authored_podcasts
        FavoritePodcastsListEvent.KIND -> Res.string.kind_favorite_podcasts
        AttestationEvent.KIND -> Res.string.attestation
        AttestationRequestEvent.KIND -> Res.string.attestation_request
        AttestorRecommendationEvent.KIND -> Res.string.attestor_recommendation
        AttestorProficiencyEvent.KIND -> Res.string.attestor_proficiency
        BadgeAwardEvent.KIND -> Res.string.kind_badge_awards
        BadgeDefinitionEvent.KIND -> Res.string.kind_badge_definitions
        BlockedRelayListEvent.KIND -> Res.string.kind_blocked_relays
        BlossomServersEvent.KIND -> Res.string.kind_blossom_servers
        NestsServersEvent.KIND -> Res.string.kind_nests_servers
        BlossomAuthorizationEvent.KIND -> Res.string.kind_blossom_auth
        BroadcastRelayListEvent.KIND -> Res.string.kind_broadcast_relays
        BookmarkListEvent.KIND -> Res.string.kind_bookmark_list
        OldBookmarkListEvent.KIND -> Res.string.kind_old_bookmark_list
        CalendarDateSlotEvent.KIND -> Res.string.kind_day_appointment
        CalendarCollectionEvent.KIND -> Res.string.kind_calendar
        CalendarTimeSlotEvent.KIND -> Res.string.kind_appointment
        CalendarRSVPEvent.KIND -> Res.string.kind_appt_rsvp
        ChessGameEvent.KIND -> Res.string.kind_chess_games
        JesterEvent.KIND -> Res.string.kind_chess_auth
        FavoriteRelayListEvent.KIND -> Res.string.kind_favorite_relays
        LiveChessGameChallengeEvent.KIND -> Res.string.kind_chess_challenges
        LiveChessGameAcceptEvent.KIND -> Res.string.kind_chess_game_accept
        LiveChessMoveEvent.KIND -> Res.string.kind_chess_move
        LiveChessGameEndEvent.KIND -> Res.string.kind_chess_game_end
        LiveChessDrawOfferEvent.KIND -> Res.string.kind_chess_draw_offer
        ChannelCreateEvent.KIND -> Res.string.kind_channel_definition
        ChannelHideMessageEvent.KIND -> Res.string.kind_channel_hide_msg
        PublicChatListEvent.KIND -> Res.string.kind_channel_list
        ChannelMessageEvent.KIND -> Res.string.kind_channel_message
        ChannelMetadataEvent.KIND -> Res.string.kind_channel_metadata
        ChannelMuteUserEvent.KIND -> Res.string.kind_channel_mute_user
        ChatMessageEncryptedFileHeaderEvent.KIND -> Res.string.kind_dm_file
        ChatMessageEvent.KIND -> Res.string.kind_dm_message
        DmRelayListEvent.KIND -> Res.string.kind_dm_relays
        ClassifiedsEvent.KIND -> Res.string.kind_classifieds
        ProductCollectionEvent.KIND -> Res.string.kind_product_collection
        ShippingOptionEvent.KIND -> Res.string.kind_shipping_option
        ProductReviewEvent.KIND -> Res.string.kind_product_review
        CommentEvent.KIND -> Res.string.kind_comments
        CommunityDefinitionEvent.KIND -> Res.string.kind_community_def
        CommunityListEvent.KIND -> Res.string.kind_community_list
        CommunityPostApprovalEvent.KIND -> Res.string.kind_community_post
        ContactListEvent.KIND -> Res.string.kind_follow_list
        DeletionRequestEvent.KIND -> Res.string.kind_deletions
        DraftWrapEvent.KIND -> Res.string.kind_drafts
        EmojiPackEvent.KIND -> Res.string.kind_emoji_packs
        EmojiListEvent.KIND -> Res.string.kind_emoji_pack_list
        EphemeralChatEvent.KIND -> Res.string.kind_ephemeral_chat
        EphemeralChatListEvent.KIND -> Res.string.kind_ephemeral_chatrooms
        FileMetadataEvent.KIND -> Res.string.kind_file_headers
        ProfileGalleryEntryEvent.KIND -> Res.string.kind_profile_gallery
        FileServersEvent.KIND -> Res.string.kind_file_servers
        FileStorageEvent.KIND -> Res.string.kind_blob_data
        FileStorageHeaderEvent.KIND -> Res.string.kind_blob_headers
        FhirResourceEvent.KIND -> Res.string.kind_medical_data
        StarterPackEvent.KIND -> Res.string.kind_follow_packs
        GenericRepostEvent.KIND -> Res.string.kind_reposts_16
        GeohashListEvent.KIND -> Res.string.kind_geohash_follows
        GiftWrapEvent.KIND -> Res.string.kind_gift_wraps
        EphemeralGiftWrapEvent.KIND -> Res.string.kind_gift_wraps
        GitIssueEvent.KIND -> Res.string.kind_git_issue
        GitPatchEvent.KIND -> Res.string.kind_git_patch
        GitPullRequestEvent.KIND -> Res.string.kind_git_pr
        GitPullRequestUpdateEvent.KIND -> Res.string.kind_git_pr_update
        GitRepositoryEvent.KIND -> Res.string.kind_git_repo
        GitReplyEvent.KIND -> Res.string.kind_git_reply
        GitStatusOpenEvent.KIND -> Res.string.kind_git_status_open
        GitStatusAppliedEvent.KIND -> Res.string.kind_git_status_applied
        GitStatusClosedEvent.KIND -> Res.string.kind_git_status_closed
        GitStatusDraftEvent.KIND -> Res.string.kind_git_status_draft
        ZapGoalEvent.KIND -> Res.string.kind_zap_goals
        InterestListEvent.KIND -> Res.string.kind_hashtag_follows
        HighlightEvent.KIND -> Res.string.kind_highlights
        HTTPAuthorizationEvent.KIND -> Res.string.kind_http_auth
        IndexerRelayListEvent.KIND -> Res.string.kind_index_relay_list
        InteractiveStoryPrologueEvent.KIND -> Res.string.kind_adventure_prologue
        InteractiveStorySceneEvent.KIND -> Res.string.kind_adventure_scene
        InteractiveStoryReadingStateEvent.KIND -> Res.string.kind_adventure_reading
        BookmarkSetEvent.KIND -> Res.string.kind_named_bookmarks
        LiveActivitiesChatMessageEvent.KIND -> Res.string.kind_live_chats
        LiveActivitiesEvent.KIND -> Res.string.kind_live_streams
        ZapReceiptEvent.KIND -> Res.string.kind_zaps
        Bolt12ZapEvent.KIND -> Res.string.kind_zaps
        NwcRequestEvent.KIND -> Res.string.kind_nwc_request
        NwcResponseEvent.KIND -> Res.string.kind_nwc_response
        PrivateZapEvent.KIND -> Res.string.kind_private_zaps
        ZapRequestEvent.KIND -> Res.string.kind_zap_req
        LongFormContentEvent.KIND -> Res.string.kind_blogs
        MeetingRoomEvent.KIND -> Res.string.kind_meeting_room
        MeetingRoomPresenceEvent.KIND -> Res.string.kind_room_presence
        MeetingSpaceEvent.KIND -> Res.string.kind_meeting_space
        MetadataEvent.KIND -> Res.string.kind_profile
        MuteListEvent.KIND -> Res.string.kind_mute_list
        NNSEvent.KIND -> Res.string.kind_nns
        NipTextEvent.KIND -> Res.string.kind_nip
        NostrConnectEvent.KIND -> Res.string.kind_nostr_connect
        DvmStatusEvent.KIND -> Res.string.kind_dvm_status
        DvmContentDiscoveryRequestEvent.KIND -> Res.string.kind_dvm_content_req
        DvmContentDiscoveryResponseEvent.KIND -> Res.string.kind_dvm_content_resp
        DvmUserDiscoveryRequestEvent.KIND -> Res.string.kind_dvm_user_req
        DvmUserDiscoveryResponseEvent.KIND -> Res.string.kind_dvm_user_resp
        OtsEvent.KIND -> Res.string.kind_ots
        PaymentTargetsEvent.KIND -> Res.string.kind_pay_to
        FollowSetEvent.KIND -> Res.string.kind_people_lists
        ProfileBadgesEvent.KIND -> Res.string.kind_profile_badges
        PictureEvent.KIND -> Res.string.kind_pictures
        WorkoutRecordEvent.KIND -> Res.string.kind_workouts
        PinListEvent.KIND -> Res.string.kind_pins
        ZapPollEvent.KIND -> Res.string.kind_zap_poll
        PollEvent.KIND -> Res.string.kind_poll
        PollResponseEvent.KIND -> Res.string.kind_poll_response
        EncryptedDmEvent.KIND -> Res.string.kind_nip04_dms
        PrivateOutboxRelayListEvent.KIND -> Res.string.kind_private_relays
        ProxyRelayListEvent.KIND -> Res.string.kind_proxy_relays
        PublicMessageEvent.KIND -> Res.string.kind_public_message
        ReactionEvent.KIND -> Res.string.kind_reactions
        UserAssertionEvent.KIND -> Res.string.kind_contact_card
        RelayAuthEvent.KIND -> Res.string.kind_relay_auth
        RelayDiscoveryEvent.KIND -> Res.string.kind_relay_discovery
        RelayMonitorEvent.KIND -> Res.string.kind_relay_monitor
        RelaySetEvent.KIND -> Res.string.kind_relay_set
        ReportEvent.KIND -> Res.string.kind_reports
        RepostEvent.KIND -> Res.string.kind_reposts
        RequestToVanishEvent.KIND -> Res.string.kind_user_delete
        SealEvent.KIND -> Res.string.kind_seals
        SearchRelayListEvent.KIND -> Res.string.kind_search_relays
        UserStatusEvent.KIND -> Res.string.kind_user_status
        TextNoteEvent.KIND -> Res.string.kind_notes
        TextNoteModificationEvent.KIND -> Res.string.kind_edits
        TorrentEvent.KIND -> Res.string.kind_torrents
        TorrentCommentEvent.KIND -> Res.string.kind_torrent_comments
        TrustedRelayListEvent.KIND -> Res.string.kind_trusted_relays
        TrustProviderListEvent.KIND -> Res.string.kind_trusted_providers
        VideoCurationSetEvent.KIND -> Res.string.kind_video_list
        VideoCollaborationEvent.KIND -> Res.string.kind_video_collaboration
        TextTrackEvent.KIND -> Res.string.kind_video_subtitles
        VideoViewEvent.KIND -> Res.string.kind_video_views
        PushRegistrationEvent.KIND -> Res.string.kind_push_registration
        PushDeregistrationEvent.KIND -> Res.string.kind_push_deregistration
        PushPreferencesEvent.KIND -> Res.string.kind_push_preferences
        AddressableNormalVideoEvent.KIND -> Res.string.kind_video_repl
        AddressableShortVideoEvent.KIND -> Res.string.kind_shorts_repl
        VideoNormalEvent.KIND -> Res.string.kind_video
        VideoShortEvent.KIND -> Res.string.kind_shorts
        VoiceEvent.KIND -> Res.string.kind_voice_msg
        VoiceReplyEvent.KIND -> Res.string.kind_voice_reply
        WakeUpEvent.KIND -> Res.string.kind_wake
        WebBookmarkEvent.KIND -> Res.string.kind_web_bookmark
        WikiArticleEvent.KIND -> Res.string.kind_wiki
        else -> null
    }

/**
 * Returns the translated display name for [kind] from the shared string catalog when available,
 * falling back to the English name from [KindNames], then to "k<number>".
 */
suspend fun kindNameFor(kind: Int): String {
    val res = kindDisplayName(kind)
    return if (res != null) loadStringRes(res) else (KindNames.nameFor(kind) ?: "k$kind")
}

/** Composition-side twin of [kindNameFor], for labels rendered straight into the UI. */
@Composable
fun kindName(kind: Int): String {
    val res = kindDisplayName(kind)
    return if (res != null) stringRes(res) else (KindNames.nameFor(kind) ?: "k$kind")
}
