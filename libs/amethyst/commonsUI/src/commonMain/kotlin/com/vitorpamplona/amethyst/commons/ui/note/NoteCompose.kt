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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.audio.hasPlayableAudio
import com.vitorpamplona.amethyst.commons.birdstar.ui.RenderBirdDetection
import com.vitorpamplona.amethyst.commons.birdstar.ui.RenderBirdex
import com.vitorpamplona.amethyst.commons.concord.ui.ConcordCommunityPill
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.kanban.ui.RenderKanbanBoard
import com.vitorpamplona.amethyst.commons.kanban.ui.RenderKanbanCard
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.isCommunityDefinition
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeEditDraftTo
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.replyingDirectlyTo
import com.vitorpamplona.amethyst.commons.model.textNoteModifications
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.RenderAuction
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.RenderBid
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.RenderBidConfirmation
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.RenderMarketplace
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.RenderProduct
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.RenderStall
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitSummaryEdits
import com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents.ui.RenderMostroInfo
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.RenderProductCollection
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.RenderProductReview
import com.vitorpamplona.amethyst.commons.nip99Classifieds.ui.RenderShippingOption
import com.vitorpamplona.amethyst.commons.nipC0CodeSnippets.ui.RenderCodeSnippetEvent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeCommunityApprovalNeedStatus
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteModifications
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannelPicture
import com.vitorpamplona.amethyst.commons.rendering.unknown.isUnknownKind
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.approve
import com.vitorpamplona.amethyst.commons.resources.draft
import com.vitorpamplona.amethyst.commons.resources.expiration_date_label
import com.vitorpamplona.amethyst.commons.resources.expiration_info_description
import com.vitorpamplona.amethyst.commons.resources.group_picture
import com.vitorpamplona.amethyst.commons.resources.jump_to_parent_reply
import com.vitorpamplona.amethyst.commons.resources.one_year_plus
import com.vitorpamplona.amethyst.commons.resources.pinned_notes
import com.vitorpamplona.amethyst.commons.resources.private_rumor_info_description
import com.vitorpamplona.amethyst.commons.resources.private_rumor_info_title
import com.vitorpamplona.amethyst.commons.resources.private_rumor_mark
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.ExpandedTextGutter
import com.vitorpamplona.amethyst.commons.ui.components.ExpandedTextGutterState
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.LocalExpandedTextGutter
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.layouts.GenericRepostLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NoteComposeLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NoteHorizontalPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.HeaderPill
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.QuietMark
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.RenderCashuMint
import com.vitorpamplona.amethyst.commons.ui.note.RenderFedimint
import com.vitorpamplona.amethyst.commons.ui.note.RenderMintRecommendation
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.DisplayZapSplits
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayEditStatus
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayFollowingCommunityInPost
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayFollowingHashtagsInPost
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayLocation
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayOts
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayPoW
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayReward
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.elements.Reward
import com.vitorpamplona.amethyst.commons.ui.note.elements.ShowForkInformation
import com.vitorpamplona.amethyst.commons.ui.note.elements.StaleRelayHint
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgoStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderGitIssueEvent
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderGitPatchEvent
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderGitPullRequestEvent
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderGitPullRequestUpdateEvent
import com.vitorpamplona.amethyst.commons.ui.note.platform.RenderGitRepositoryEvent
import com.vitorpamplona.amethyst.commons.ui.note.timeAheadNoDot
import com.vitorpamplona.amethyst.commons.ui.note.types.BadgeDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.DisplayContactList
import com.vitorpamplona.amethyst.commons.ui.note.types.DisplayPeopleList
import com.vitorpamplona.amethyst.commons.ui.note.types.DisplayRelayList
import com.vitorpamplona.amethyst.commons.ui.note.types.DisplayStarterPack
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.note.types.EmptyState
import com.vitorpamplona.amethyst.commons.ui.note.types.FileMetadataDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.FileStorageHeaderDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.PictureDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.ProvideKindCardRow
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAcceptedBadgeSet
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderActiveProfileTheme
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentArtifact
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentArtifactRemoval
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentAuditEntry
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentPairing
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentProfile
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentProject
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentTeam
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentTeamCatalog
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAgentTurnMetric
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAppData
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAppDefinition
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAppRecommendation
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAssetBundle
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAttestation
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAttestationRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAttestorProficiency
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAttestorRecommendation
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAudioHeader
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAudioTrack
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderAuthoredPodcasts
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBadgeAward
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBallot
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBlossomAuth
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBlossomPieceIndex
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBlossomServers
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBolt12OfferList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBolt12Zap
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBolt12ZapIntent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBookshelfDirectory
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzArchiveRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzArchivedIdentitiesList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzCanvas
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzChannelSummary
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzDiffCard
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzDmAddMember
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzDmCreated
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzDmHide
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzDmOpen
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzDmVisibility
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzForumComment
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzForumPost
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzForumVoteCard
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzHuddleEnded
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzHuddleGuidelines
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzHuddleJoined
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzHuddleLeft
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzHuddleLiveness
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzHuddleReaction
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzHuddleStarted
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzIdentityArchived
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzIdentityUnarchived
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzJobAccepted
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzJobCancel
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzJobError
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzJobProgress
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzJobRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzJobResult
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzMemberRemoved
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzMessageBookmarked
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzMessageEdit
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzMessagePinned
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzMessageScheduled
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzModerationBan
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzModerationResolveReport
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzModerationTimeout
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzModerationUntimeout
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzPresenceSnapshot
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzPresenceUpdate
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzProductFeedback
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzRelayAdminAddMember
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzRelayAdminChangeRole
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzRelayAdminRemoveMember
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzReminder
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzSetWorkspaceProfile
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzSystemMessageCard
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzThreadSummary
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzTyping
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderBuzzUnarchiveRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCalendarCollectionEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCalendarDateSlotEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCalendarRSVPEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCalendarTimeSlotEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCapabilityDiscovery
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCashuMintQuote
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCashuSpendingHistory
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChannelHideMessage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChannelInvite
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChannelMessage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChannelMuteUser
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChat
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChatMessage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChatMessageEncryptedFile
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderChessGame
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiCoordinatorAdvertisement
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiJobResult
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiManualTrigger
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiRepositoryStatus
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiRequestReadinessList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiServiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiServiceStop
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiWorkflowProgress
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCiWorkflowResult
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCitation
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderClassifieds
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCommunity
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCommunityList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCommunityRules
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderConcordChatEdit
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderConcordControlEdition
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderConcordInviteBundle
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCreateGroup
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCvmServerAnnouncement
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCvmToolsList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderCyberspaceBag
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDeleteGroup
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDeletionRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssEventList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssFeedList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssListFolder
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssListenProgress
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssProvenance
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssProvenanceFolder
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssReadWriteLists
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDrssShow
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDvmContentDiscoveryResponse
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDvmHeartbeat
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDvmRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDvmResult
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderDvmStatus
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEbook
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEmojiPack
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEncryptionKeyList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEncryptionKeyRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEngram
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEntityRating
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEphemeralChat
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEphemeralChatList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderEventReminder
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderExtensionListing
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderExtensionRelease
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderExternalIdentities
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderExternalReaction
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFavoriteFollowSets
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFavoritePodcasts
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFeedDefinition
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFhirResource
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFileServers
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFileStorage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderFundraiser
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeocache
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeocacheCurationList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeocacheFoundLog
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeocacheVerification
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeohashChat
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGeohashPresence
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitCoverNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitRepositoryState
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitReviewEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitStatusEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGoal
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupAdmins
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupCreateInvite
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupDeleteEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupEditMetadata
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupJoinRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupLeaveRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupMembers
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupMetadata
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupParticipants
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupPinned
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupPutUser
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupRemoveUser
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupRoles
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGroupUpdatePinList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderHighlight
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderHttpAuth
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderIdentityProof
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderInteractiveStory
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderInteractiveStoryReadingState
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderJesterChess
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLabel
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLearningResource
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveActivityChatMessage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveActivityEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveActivityRaid
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveChessAccept
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveChessChallenge
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveChessDrawOffer
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveChessGameEnd
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLiveChessMove
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLongFormContent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderLongFormDraft
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMagazine
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMagazineIssue
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderManagedAgent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMarmotKeyPackage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMeetingRoomEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMeetingRoomPresence
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMeetingSpaceEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMostroDevFee
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMostroDispute
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMostroUserRating
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMusicPlaylist
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMusicTrack
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNNS
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNappletSnapshot
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNestsAdminCommand
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNestsServers
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNipContent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNutzap
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNutzapInfo
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderNwcInfo
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderObserverFrame
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderOnchainZap
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderOts
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderP2POrder
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPaymentTargets
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPersona
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPinListEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPodcastEpisode
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPodcastMetadata
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPoll
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPollResponse
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPostApproval
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPostingStreak
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPredictionMarket
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPrivateKindCard
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPrivateMessage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderProfileBadges
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderProfileCard
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderProfileGalleryEntry
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPs1Save
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPublicChatList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPublicMessage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPublicationIndex
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPublicationSection
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderPushLease
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderReaction
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayAddMember
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayAuth
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayDiscovery
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayGroupMessage
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayInviteRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayJoinRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayLeaveRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayMembershipList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayMonitor
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayRemoveMember
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayReview
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRelayRole
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderReleaseArtifactSet
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderReport
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRequestToVanish
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRoadEventConfirmation
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRoadEventReport
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRoboSatsCoordinatorRating
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderRoutstrNode
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSiteSnapshot
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSnoAvatar
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSnoObject
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSnoShard
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSoftwareApplication
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSoftwareAsset
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderSoftwareRelease
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderStickerPack
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTextEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTextModificationEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTextTrack
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderThemeDefinition
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderThread
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTopEight
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTorrent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderTorrentComment
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderUnknownKind
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderUserGraspList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderUserStatus
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderVideoCollaboration
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderVideoCurationSet
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderVideoView
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderVoiceTrack
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWebBookmark
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWikiContent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWikiMergeAcceptance
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWikiMergeRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWikiRedirect
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWorkflowApprovalDecision
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWorkflowDefinition
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWorkflowLifecycle
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWorkflowTrigger
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderWorkoutTemplate
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderZapPoll
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderZapReceipt
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderZapRequest
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.note.types.VideoDisplay
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.RenderDListHeader
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.RenderDListItem
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderAppCurationSet
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderArticleCurationSet
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderBookmarkList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderBookmarkSet
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderFavoriteAlgoFeedsList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderGeohashList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderGitAuthorList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderGitRepositoryList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderGoodWikiAuthorList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderInterestList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderInterestSet
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderKindMuteSet
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderMediaFollowList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderMediaStarterPack
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderMuteList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderOldBookmarkList
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderPictureCurationSet
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.RenderSimpleGroupList
import com.vitorpamplona.amethyst.commons.ui.note.types.observeZapSender
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.RenderTrustProviderList
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.RenderTrustedAssertion
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.RenderTrustedList
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.fullWidthHeaderPictureSize
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderChatClip
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.RenderConcordTimerNotice
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.RenderPublicChatChannelHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.PodcastTrailerListItem
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.ExerciseTemplateDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.workouts.WorkoutDisplay
import com.vitorpamplona.amethyst.commons.ui.state.produceCachedStateAsync
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.HalfDoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Height4dpModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.UserNameMaxRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.UserNameRowHeight
import com.vitorpamplona.amethyst.commons.ui.theme.channelNotePictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.newItemBackgroundColor
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.walletScrutiny.ui.RenderBuildVerification
import com.vitorpamplona.quartz.buzz.aeEngrams.EngramEvent
import com.vitorpamplona.quartz.buzz.agentProfiles.AgentProfileEvent
import com.vitorpamplona.quartz.buzz.amTurnMetrics.AgentTurnMetricEvent
import com.vitorpamplona.quartz.buzz.aoObserver.ObserverFrameEvent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactEvent
import com.vitorpamplona.quartz.buzz.arArtifacts.ArtifactRemovalEvent
import com.vitorpamplona.quartz.buzz.audit.AuditEntryEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadSummaryEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.ThreadWindowBoundsEvent
import com.vitorpamplona.quartz.buzz.cwChannelWindow.WindowBoundsEvent
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmCreatedEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.erReminders.EventReminderEvent
import com.vitorpamplona.quartz.buzz.forum.ForumCommentEvent
import com.vitorpamplona.quartz.buzz.forum.ForumPostEvent
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleGuidelinesEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleLivenessEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleReactionEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentitiesListEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.ArchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchiveRequestEvent
import com.vitorpamplona.quartz.buzz.iaIdentityArchival.UnarchivedIdentityEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.managedAgents.ManagedAgentEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationBanEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationResolveReportEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationTimeoutEvent
import com.vitorpamplona.quartz.buzz.moderation.ModerationUntimeoutEvent
import com.vitorpamplona.quartz.buzz.moderation.ProductFeedbackEvent
import com.vitorpamplona.quartz.buzz.mpProjects.ProjectEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberRemovedNotificationEvent
import com.vitorpamplona.quartz.buzz.pairing.PairingEvent
import com.vitorpamplona.quartz.buzz.plPushLease.PushLeaseEvent
import com.vitorpamplona.quartz.buzz.presence.PresenceUpdateEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminAddMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminChangeRoleEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminRemoveMemberEvent
import com.vitorpamplona.quartz.buzz.stream.CanvasEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageBookmarkedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessagePinnedEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageScheduledEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.StreamReminderEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.ChannelSummaryEvent
import com.vitorpamplona.quartz.buzz.stream.sidecars.PresenceSnapshotEvent
import com.vitorpamplona.quartz.buzz.teamCatalog.TeamCatalogEvent
import com.vitorpamplona.quartz.buzz.teams.TeamEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalDeniedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalGrantedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowApprovalRequestedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCancelledEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepCompletedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepFailedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowStepStartedEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggeredEvent
import com.vitorpamplona.quartz.buzz.wpWorkspaceProfile.SetWorkspaceProfileEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListFragmentEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChatEditEvent
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.concord.cord04Roles.control.ControlEditionEvent
import com.vitorpamplona.quartz.concord.cord05Invites.ConcordInviteListEvent
import com.vitorpamplona.quartz.concord.cord05Invites.bundle.ConcordInviteBundleEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmServerAnnouncementEvent
import com.vitorpamplona.quartz.contextvm.cep06Announcements.CvmToolsListEvent
import com.vitorpamplona.quartz.cyberspace.CyberspaceBagEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoAvatarEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoObjectEvent
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoShardEvent
import com.vitorpamplona.quartz.drss.eventList.DrssEventListEvent
import com.vitorpamplona.quartz.drss.eventList.UnrecognizedKind30102Event
import com.vitorpamplona.quartz.drss.feedList.DrssFeedListEvent
import com.vitorpamplona.quartz.drss.feedList.UnrecognizedKind30100Event
import com.vitorpamplona.quartz.drss.listFolder.DrssListFolderEvent
import com.vitorpamplona.quartz.drss.listFolder.UnrecognizedKind30101Event
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressAppDataEvent
import com.vitorpamplona.quartz.drss.listenProgress.DrssListenProgressEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceEvent
import com.vitorpamplona.quartz.drss.provenance.DrssProvenanceFolderEvent
import com.vitorpamplona.quartz.drss.provenance.UnrecognizedKind30104Event
import com.vitorpamplona.quartz.drss.provenance.UnrecognizedKind31985Event
import com.vitorpamplona.quartz.drss.readWriteLists.DrssReadWriteListsEvent
import com.vitorpamplona.quartz.drss.show.DrssShowEvent
import com.vitorpamplona.quartz.experimental.agora.FundraiserEvent
import com.vitorpamplona.quartz.experimental.attestations.attestation.AttestationEvent
import com.vitorpamplona.quartz.experimental.attestations.proficiency.AttestorProficiencyEvent
import com.vitorpamplona.quartz.experimental.attestations.recommendation.AttestorRecommendationEvent
import com.vitorpamplona.quartz.experimental.attestations.request.AttestationRequestEvent
import com.vitorpamplona.quartz.experimental.audio.header.AudioHeaderEvent
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import com.vitorpamplona.quartz.experimental.ballots.BallotEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashPresenceEvent
import com.vitorpamplona.quartz.experimental.bounties.bountyBaseReward
import com.vitorpamplona.quartz.experimental.citations.CitationEvent
import com.vitorpamplona.quartz.experimental.clink.debits.DebitEvent
import com.vitorpamplona.quartz.experimental.clink.manage.ManageEvent
import com.vitorpamplona.quartz.experimental.clink.offers.OfferEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.AddressableListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.ListHeaderEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.AddressableListItemEvent
import com.vitorpamplona.quartz.experimental.decentralizedLists.item.ListItemEvent
import com.vitorpamplona.quartz.experimental.decoupling.setup.EncryptionKeyListEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.request.EncryptionKeyRequestEvent
import com.vitorpamplona.quartz.experimental.decoupling.transfer.response.EncryptionKeyTransferEvent
import com.vitorpamplona.quartz.experimental.edits.TextNoteModificationEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.ExerciseTemplateEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutRecordEvent
import com.vitorpamplona.quartz.experimental.fitness.workout.WorkoutTemplateEvent
import com.vitorpamplona.quartz.experimental.forks.IForkableEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryReadingStateEvent
import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.board.UnrecognizedKind30301Event
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.kanban.card.UnrecognizedKind30302Event
import com.vitorpamplona.quartz.experimental.library.BlossomPieceIndexEvent
import com.vitorpamplona.quartz.experimental.library.BookshelfDirectoryEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import com.vitorpamplona.quartz.experimental.medical.FhirResourceEvent
import com.vitorpamplona.quartz.experimental.music.playlist.MusicPlaylistEvent
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.experimental.nip95.data.FileStorageEvent
import com.vitorpamplona.quartz.experimental.nip95.header.FileStorageHeaderEvent
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.experimental.nns.NNSEvent
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.experimental.postingStreak.PostingStreakEvent
import com.vitorpamplona.quartz.experimental.predictionMarkets.PredictionMarketEvent
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.experimental.profileTheme.active.ActiveProfileThemeEvent
import com.vitorpamplona.quartz.experimental.profileTheme.definition.ThemeDefinitionEvent
import com.vitorpamplona.quartz.experimental.ps1saves.Ps1SaveEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.ratings.RelayReviewEvent
import com.vitorpamplona.quartz.experimental.roadstr.confirmation.RoadEventConfirmationEvent
import com.vitorpamplona.quartz.experimental.roadstr.report.RoadEventReportEvent
import com.vitorpamplona.quartz.experimental.topEight.TopEightEvent
import com.vitorpamplona.quartz.experimental.trustedLists.TrustedListEvent
import com.vitorpamplona.quartz.experimental.videoCollaboration.VideoCollaborationEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.assetBundle.AssetBundleEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.BuildVerificationEvent
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.experimental.zapstore.identityProof.IdentityProofEvent
import com.vitorpamplona.quartz.feedDefinition.FeedDefinitionEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.PublishedKeyPackage
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.NotificationRequestEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenListEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRemovalEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geoHashOrScope
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.strongPoWOrNull
import com.vitorpamplona.quartz.nip15Marketplace.auction.AuctionEvent
import com.vitorpamplona.quartz.nip15Marketplace.bid.BidEvent
import com.vitorpamplona.quartz.nip15Marketplace.bidConfirmation.BidConfirmationEvent
import com.vitorpamplona.quartz.nip15Marketplace.marketplace.MarketplaceEvent
import com.vitorpamplona.quartz.nip15Marketplace.product.ProductEvent
import com.vitorpamplona.quartz.nip15Marketplace.stall.StallEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip23LongContent.draft.LongFormDraftEvent
import com.vitorpamplona.quartz.nip25Reactions.ExternalReactionEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelHideMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMuteUserEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.PublicChatListEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupParticipantsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupRolesEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupCreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupDeleteEventEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupJoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupLeaveRequestEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiListEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.stickers.StickerPackEvent
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.jobResult.CiJobResultEvent
import com.vitorpamplona.quartz.nip34Git.ci.manualTrigger.CiManualTriggerEvent
import com.vitorpamplona.quartz.nip34Git.ci.repositoryStatus.CiRepositoryStatusEvent
import com.vitorpamplona.quartz.nip34Git.ci.requestReadiness.CiRequestReadinessListEvent
import com.vitorpamplona.quartz.nip34Git.ci.secretUpdate.CiSecretUpdateEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceRequest.CiServiceRequestEvent
import com.vitorpamplona.quartz.nip34Git.ci.serviceStop.CiServiceStopEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowProgress.CiWorkflowProgressEvent
import com.vitorpamplona.quartz.nip34Git.ci.workflowResult.CiWorkflowResultEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.grasp.UserGraspListEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip38UserStatus.UserStatusEvent
import com.vitorpamplona.quartz.nip39ExtIdentities.ExternalIdentitiesEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip43RelayMembers.addMember.RelayAddMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.inviteRequest.RelayInviteRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.joinRequest.RelayJoinRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.leaveRequest.RelayLeaveRequestEvent
import com.vitorpamplona.quartz.nip43RelayMembers.list.RelayMembershipListEvent
import com.vitorpamplona.quartz.nip43RelayMembers.removeMember.RelayRemoveMemberEvent
import com.vitorpamplona.quartz.nip43RelayMembers.roles.RelayRoleEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcNotificationEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.PinListEvent
import com.vitorpamplona.quartz.nip51Lists.appCurationSet.AppCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.gitAuthorList.GitAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.gitRepositoryList.GitRepositoryListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiAuthorList.GoodWikiAuthorListEvent
import com.vitorpamplona.quartz.nip51Lists.goodWikiRelayList.GoodWikiRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.interestList.InterestListEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.kindMuteSet.KindMuteSetEvent
import com.vitorpamplona.quartz.nip51Lists.mediaFollowList.MediaFollowListEvent
import com.vitorpamplona.quartz.nip51Lists.mediaStarterPack.MediaStarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.ProxyRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relaySets.RelaySetEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.videoCurationSet.VideoCurationSetEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent
import com.vitorpamplona.quartz.nip52Calendar.rsvp.CalendarRSVPEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiMergeAcceptanceEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiMergeRequestEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.hasZapSplitSetupBesidesAuthor
import com.vitorpamplona.quartz.nip58Badges.accepted.AcceptedBadgeSetEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip58Badges.definition.BadgeDefinitionEvent
import com.vitorpamplona.quartz.nip58Badges.profile.ProfileBadgesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.SiteSnapshotEvent
import com.vitorpamplona.quartz.nip5dNapplets.named.NamedNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.root.RootNappletEvent
import com.vitorpamplona.quartz.nip5dNapplets.snapshot.NappletSnapshotEvent
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
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
import com.vitorpamplona.quartz.nip69P2pOrderEvents.P2POrderEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDevFee.MostroDevFeePaymentEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute.MostroDisputeEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroDispute.UnrecognizedKind38386Event
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.UnrecognizedKind38385Event
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.MostroUserRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroRating.UnrecognizedKind38384Event
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.RoboSatsCoordinatorRatingEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.robosatsRating.UnrecognizedKind31986Event
import com.vitorpamplona.quartz.nip71Video.VideoEvent
import com.vitorpamplona.quartz.nip71Video.textTrack.TextTrackEvent
import com.vitorpamplona.quartz.nip71Video.views.VideoViewEvent
import com.vitorpamplona.quartz.nip72ModCommunities.approval.CommunityPostApprovalEvent
import com.vitorpamplona.quartz.nip72ModCommunities.communityAddress
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.isACommunityPost
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip75ZapGoals.ZapGoalEvent
import com.vitorpamplona.quartz.nip78AppData.AppDataEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.addressables.AddressableAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.events.EventAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.externalIds.ExternalIdAssertionEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.UserAssertionEvent
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.UnrecognizedKind38000Event
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import com.vitorpamplona.quartz.nip90Dvms.DvmRequestEvent
import com.vitorpamplona.quartz.nip90Dvms.DvmResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.DvmContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.dvmHeartbeat.DvmHeartbeatEvent
import com.vitorpamplona.quartz.nip90Dvms.status.DvmStatusEvent
import com.vitorpamplona.quartz.nip94FileMetadata.FileMetadataEvent
import com.vitorpamplona.quartz.nip96FileStorage.config.FileServersEvent
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.ProductCollectionEvent
import com.vitorpamplona.quartz.nip99Classifieds.collection.UnrecognizedKind30405Event
import com.vitorpamplona.quartz.nip99Classifieds.review.ProductReviewEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.UnrecognizedKind30406Event
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA3PaymentTargets.PaymentTargetsEvent
import com.vitorpamplona.quartz.nipA4PublicMessages.PublicMessageEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallAnswerEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallHangupEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallIceCandidateEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRejectEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallRenegotiateEvent
import com.vitorpamplona.quartz.nipB0WebBookmarks.WebBookmarkEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.intent.Bolt12ZapIntentEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServersEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.nipF4Podcasts.authored.AuthoredPodcastsEvent
import com.vitorpamplona.quartz.nipF4Podcasts.episode.PodcastEpisodeEvent
import com.vitorpamplona.quartz.nipF4Podcasts.favorites.FavoritePodcastsListEvent
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.Podcasting20EpisodeEvent
import com.vitorpamplona.quartz.nipXXPodcasting20.episode.UnrecognizedKind30054Event
import com.vitorpamplona.quartz.nipXXPodcasting20.metadata.Podcasting20PodcastMetadata
import com.vitorpamplona.quartz.nipXXPodcasting20.trailer.Podcasting20TrailerEvent
import com.vitorpamplona.quartz.nipXXPrivateNoteStorage.PnsEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.deregistration.PushDeregistrationEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.preferences.PushPreferencesEvent
import com.vitorpamplona.quartz.nipXXPushNotifications.registration.PushRegistrationEvent
import com.vitorpamplona.quartz.openagents.nipCapCapabilities.discovery.CapabilityDiscoveryEvent
import com.vitorpamplona.quartz.openagents.nipExtExtensions.listing.ExtensionListingEvent
import com.vitorpamplona.quartz.openagents.nipExtExtensions.release.ExtensionReleaseEvent
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun NoteCompose(
    baseNote: Note,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: ReplyRenderType = ReplyRenderType.FULL,
    makeItShort: Boolean = false,
    isHiddenFeed: Boolean = false,
    isPinned: Boolean = false,
    quotesLeft: Int,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: (() -> Unit)? = null,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    // A note never draws with its parent's bleed: a quote or boost that renders as a card (a
    // community, a channel, a hidden-note warning) keeps its media inside. A boosted note's own
    // body gets its bleed from LocalBoostedNoteFrame instead.
    ProvideMediaBleed(0.dp) {
        WatchNoteEvent(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            nav,
            modifier,
        ) {
            CheckHiddenFeedWatchBlockAndReport(
                note = baseNote,
                modifier = modifier,
                ignoreAllBlocksAndReports = isHiddenFeed,
                showHiddenWarning = isQuotedNote || isBoostedNote,
                accountViewModel = accountViewModel,
                nav = nav,
            ) { canPreview ->
                AcceptableNote(
                    baseNote = baseNote,
                    modifier = modifier,
                    routeForLastRead = routeForLastRead,
                    isBoostedNote = isBoostedNote,
                    isQuotedNote = isQuotedNote,
                    unPackReply = unPackReply,
                    makeItShort = makeItShort,
                    canPreview = canPreview,
                    isPinned = isPinned,
                    quotesLeft = quotesLeft,
                    parentBackgroundColor = parentBackgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onClick = onClick,
                    moreOptions = moreOptions,
                )
            }
        }
    }
}

@Composable
fun AcceptableNote(
    baseNote: Note,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: ReplyRenderType = ReplyRenderType.FULL,
    makeItShort: Boolean = false,
    canPreview: Boolean = true,
    isPinned: Boolean = false,
    quotesLeft: Int,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
    onClick: (() -> Unit)? = null,
    moreOptions: (@Composable () -> Unit)?,
) {
    if (isQuotedNote || isBoostedNote) {
        val noteEvent = baseNote.event
        when (noteEvent) {
            is ChannelCreateEvent -> {
                RenderPublicChatChannelHeader(
                    channelId = noteEvent.id,
                    sendToChannel = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            is ChannelMetadataEvent -> {
                noteEvent.channelId()?.let {
                    RenderPublicChatChannelHeader(
                        channelId = it,
                        sendToChannel = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            is CommunityDefinitionEvent -> {
                (baseNote as? AddressableNote)?.let {
                    RenderCommunity(
                        baseNote = it,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            else -> {
                LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel, nav) { showPopup ->
                    CheckNewAndRenderNote(
                        baseNote = baseNote,
                        modifier = modifier,
                        routeForLastRead = routeForLastRead,
                        isBoostedNote = isBoostedNote,
                        isQuotedNote = isQuotedNote,
                        unPackReply = unPackReply,
                        makeItShort = makeItShort,
                        canPreview = canPreview,
                        quotesLeft = quotesLeft,
                        isPinned = isPinned,
                        parentBackgroundColor = parentBackgroundColor,
                        accountViewModel = accountViewModel,
                        showPopup = showPopup,
                        nav = nav,
                        onClick = onClick,
                        moreOptions = moreOptions,
                    )
                }
            }
        }
    } else {
        when (val noteEvent = baseNote.event) {
            is ChannelCreateEvent -> {
                RenderPublicChatChannelHeader(
                    channelId = noteEvent.id,
                    sendToChannel = true,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            is ChannelMetadataEvent -> {
                noteEvent.channelId()?.let {
                    RenderPublicChatChannelHeader(
                        channelId = it,
                        sendToChannel = true,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            is CommunityDefinitionEvent -> {
                (baseNote as? AddressableNote)?.let {
                    RenderCommunity(
                        baseNote = it,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            else -> {
                LongPressToQuickAction(baseNote, accountViewModel, nav) { showPopup ->
                    CheckNewAndRenderNote(
                        baseNote = baseNote,
                        modifier = modifier,
                        routeForLastRead = routeForLastRead,
                        isBoostedNote = isBoostedNote,
                        isQuotedNote = isQuotedNote,
                        unPackReply = unPackReply,
                        makeItShort = makeItShort,
                        canPreview = canPreview,
                        isPinned = isPinned,
                        quotesLeft = quotesLeft,
                        parentBackgroundColor = parentBackgroundColor,
                        accountViewModel = accountViewModel,
                        showPopup = showPopup,
                        nav = nav,
                        onClick = onClick,
                        moreOptions = moreOptions,
                    )
                }
            }
        }
    }
}

@Composable
fun calculateBackgroundColor(
    createdAt: Long?,
    routeForLastRead: String? = null,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    dismissNotificationId: HexKey? = null,
): MutableState<Color> {
    val defaultBackgroundColor = MaterialTheme.colorScheme.background
    val newItemColor = MaterialTheme.colorScheme.newItemBackgroundColor

    // Inner notes (reposts) pass routeForLastRead = null with the parent's bgColor state;
    // share it directly so the inner highlight fades in lockstep with the outer.
    if (routeForLastRead == null && parentBackgroundColor != null) {
        return parentBackgroundColor
    }

    val isNew =
        remember(createdAt, routeForLastRead) {
            routeForLastRead != null && accountViewModel.loadAndMarkAsRead(routeForLastRead, createdAt, dismissNotificationId)
        }

    val bgColor =
        remember(createdAt) {
            mutableStateOf(
                if (isNew) {
                    newItemColor.compositeOver(parentBackgroundColor?.value ?: defaultBackgroundColor)
                } else {
                    parentBackgroundColor?.value ?: Color.Transparent
                },
            )
        }

    if (isNew) {
        LaunchedEffect(createdAt) {
            delay(5000)
            bgColor.value = parentBackgroundColor?.value ?: Color.Transparent
        }
    }

    return bgColor
}

@Composable
private fun CheckNewAndRenderNote(
    baseNote: Note,
    modifier: Modifier = Modifier,
    routeForLastRead: String? = null,
    isBoostedNote: Boolean = false,
    isQuotedNote: Boolean = false,
    unPackReply: ReplyRenderType = ReplyRenderType.FULL,
    makeItShort: Boolean = false,
    canPreview: Boolean = true,
    isPinned: Boolean = false,
    quotesLeft: Int,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: INav,
    onClick: (() -> Unit)? = null,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    val backgroundColor =
        calculateBackgroundColor(
            baseNote.createdAt(),
            routeForLastRead,
            parentBackgroundColor,
            accountViewModel,
            dismissNotificationId = baseNote.idHex,
        )

    InnerNoteWithReactions(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        clickModifier = clickableNoteModifier(baseNote, modifier, accountViewModel, showPopup, nav, onClick),
        isBoostedNote = isBoostedNote,
        isQuotedNote = isQuotedNote,
        unPackReply = unPackReply,
        makeItShort = makeItShort,
        canPreview = canPreview,
        isPinned = isPinned,
        quotesLeft = quotesLeft,
        accountViewModel = accountViewModel,
        nav = nav,
        moreOptions = moreOptions,
    )
}

/**
 * Creates a [Modifier] with [combinedClickable] for note navigation (click to open,
 * long-press for quick action popup). Remembered by [baseNote] and [modifier] to avoid
 * recreating the click handler on every recomposition.
 *
 * Used directly on [NoteComposeLayout]'s modifier in [InnerNoteWithReactions] to avoid
 * a wrapping Column node. For callers that need a wrapping container, use [ClickableNote].
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun clickableNoteModifier(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: INav,
    onClick: (() -> Unit)? = null,
): Modifier =
    remember(baseNote, modifier, onClick) {
        modifier
            .combinedClickable(
                onClick = {
                    if (onClick != null) {
                        onClick()
                    } else {
                        val redirectToNote =
                            if (baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent) {
                                baseNote.replyTo?.lastOrNull() ?: baseNote
                            } else {
                                baseNote
                            }

                        nav.nav {
                            if (redirectToNote.event is DraftWrapEvent) {
                                withContext(Dispatchers.IO) {
                                    routeEditDraftTo(redirectToNote, accountViewModel.account)
                                }
                            } else {
                                routeFor(redirectToNote, accountViewModel.account)
                            }
                        }
                    }
                },
                onLongClick = showPopup,
            )
    }

/**
 * Wraps [content] in a Column with click handling and animated background.
 *
 * Kept for callers outside NoteCompose (e.g. ChannelCardCompose) that need a
 * container for non-NoteComposeLayout content. For NoteCompose itself,
 * [InnerNoteWithReactions] applies [clickableNoteModifier] directly on the
 * [NoteComposeLayout] modifier to eliminate this Column wrapper node.
 */
@Composable
fun ClickableNote(
    baseNote: Note,
    modifier: Modifier,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: INav,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            clickableNoteModifier(baseNote, modifier, accountViewModel, showPopup, nav)
                .background(backgroundColor.value),
    ) {
        content()
    }
}

/**
 * What a repost hands the note it boosts: the bleed for that note's body, and whether the repost's
 * header already shows that note's author row ([FullWidthRepostHeader]).
 */
@Immutable
private data class BoostedNoteFrame(
    val mediaBleed: Dp = 0.dp,
    val headerShown: Boolean = false,
)

private val NoBoostedNoteFrame = BoostedNoteFrame()

/**
 * The [BoostedNoteFrame] a reposted note takes from its repost. Only that note's
 * [InnerNoteWithReactions] reads it, so the rest of what a boosted note draws (its hidden-note and
 * community cards, reactions) never sees one.
 */
private val LocalBoostedNoteFrame = compositionLocalOf { NoBoostedNoteFrame }

/** Provides [LocalMediaBleed] and [LocalBoostedNoteFrame], skipping whichever already holds its value. */
@Composable
private fun ProvideNoteBleeds(
    media: Dp,
    boostedNote: BoostedNoteFrame,
    content: @Composable () -> Unit,
) {
    if (LocalBoostedNoteFrame.current == boostedNote) {
        ProvideMediaBleed(media, content)
    } else {
        CompositionLocalProvider(LocalBoostedNoteFrame provides boostedNote) {
            ProvideMediaBleed(media, content)
        }
    }
}

/**
 * Hands [frame] to a repost's boosted note. Always through a provider, unlike [ProvideNoteBleeds]:
 * the frame changes when the reposted note loads or is found hidden, and a branch between providing
 * and not would rebuild the boosted note, restarting its images and players.
 */
@Composable
private fun ProvideRepostFrame(
    frame: BoostedNoteFrame,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBoostedNoteFrame provides frame) {
        ProvideMediaBleed(0.dp, content)
    }
}

/** The note a repost boosts, as [RenderRepost] draws it. */
private fun Note.repostedNote(): Note? = replyTo?.lastOrNull { !it.isCommunityDefinition() }

/**
 * The reposted note whose author row a full-width repost's header can show: one that has loaded and
 * is not hidden. A note still loading, or hidden by a block, mute or report, keeps the one-row
 * header, and the body shows its placeholder or warning without naming the hidden author.
 */
@Composable
private fun headedRepostedNote(
    repost: Note,
    accountViewModel: AccountViewModel,
): Note? {
    val reposted = repost.repostedNote() ?: return null
    val event by observeNoteEvent<Event>(reposted, accountViewModel)
    val hidden by remember(reposted) { accountViewModel.createIsHiddenFlow(reposted) }.collectAsStateWithLifecycle()
    return if (event != null && !hidden.isPostHidden && hidden.isAcceptable) reposted else null
}

@Composable
fun InnerNoteWithReactions(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    clickModifier: Modifier = Modifier,
    isBoostedNote: Boolean,
    isQuotedNote: Boolean,
    unPackReply: ReplyRenderType,
    makeItShort: Boolean,
    canPreview: Boolean,
    isPinned: Boolean,
    quotesLeft: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    val notBoostedNorQuote = !isBoostedNote && !isQuotedNote
    val editState = observeEdits(baseNote = baseNote, accountViewModel = accountViewModel)
    val isNotRepost = baseNote.event !is RepostEvent && baseNote.event !is GenericRepostEvent
    val showSecondRow = isNotRepost && notBoostedNorQuote && accountViewModel.settings.isCompleteUIMode()

    // The full-width style heads the note with the author and lets the content use the whole width.
    val fullWidth = notBoostedNorQuote && LocalDisplaySettings.current.fullWidthNotes

    // A note with an author column takes "Show Less" for its expanded text there. A boosted note
    // has none and uses its booster's; a quote is its own box, and a full-width note has no column,
    // so their text keeps the button inline.
    val gutter = if (notBoostedNorQuote && !fullWidth) remember { ExpandedTextGutterState() } else null
    val providedGutter = if (isQuotedNote || fullWidth) null else gutter ?: LocalExpandedTextGutter.current

    // An edge-to-edge note's media runs out to the screen edges, over its side padding. A boosted
    // note sits in its booster's column and follows it; a quote is a box its media must stay in.
    val boostedFrame = if (isBoostedNote && !isQuotedNote) LocalBoostedNoteFrame.current else NoBoostedNoteFrame
    val mediaBleed =
        when {
            fullWidth -> if (LocalDisplaySettings.current.edgeToEdgeMedia) NoteHorizontalPadding else 0.dp
            else -> boostedFrame.mediaBleed
        }

    // A full-width repost heads itself with both authors' rows, so the note it boosts skips its own.
    val repostedNote = if (fullWidth && !isNotRepost) headedRepostedNote(baseNote, accountViewModel) else null

    CompositionLocalProvider(LocalExpandedTextGutter provides providedGutter) {
        NoteComposeLayout(
            modifier =
                clickModifier
                    .drawBehind { drawRect(backgroundColor.value) }
                    .fillMaxWidth(),
            addPadding = !isBoostedNote,
            showAuthorColumn = notBoostedNorQuote,
            fullWidth = fullWidth,
            showSecondRow = showSecondRow,
            // Under a repost's header the boosted body takes the header's spacer, not one of its own.
            showContentSpacer = (isNotRepost && !boostedFrame.headerShown) || repostedNote != null,
            authorPicture = {
                if (repostedNote != null) {
                    // FullWidthRepostHeader draws both authors' pictures beside their rows.
                } else if (fullWidth) {
                    // Sized to the header rows beside it: 35dp next to two rows, 30dp next to one.
                    val pictureSize = fullWidthHeaderPictureSize(twoRows = showSecondRow)
                    Box(modifier = remember(pictureSize) { Modifier.size(pictureSize) }, contentAlignment = Alignment.BottomEnd) {
                        RenderAuthorImages(baseNote, nav, accountViewModel, pictureSize)
                    }
                } else if (notBoostedNorQuote) {
                    Box(modifier = Size55Modifier, contentAlignment = Alignment.BottomEnd) {
                        RenderAuthorImages(baseNote, nav, accountViewModel)
                    }
                }
            },
            firstRow = {
                if (repostedNote != null) {
                    FullWidthRepostHeader(baseNote, repostedNote, isPinned, editState, accountViewModel, nav, moreOptions)
                } else if (!boostedFrame.headerShown) {
                    FirstUserInfoRow(
                        baseNote = baseNote,
                        showAuthorPicture = isQuotedNote,
                        isPinned = isPinned,
                        editState = editState,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        moreOptions = moreOptions,
                    )
                }
            },
            secondRow = {
                if (showSecondRow) {
                    SecondUserInfoRow(
                        baseNote,
                        accountViewModel,
                        nav,
                    )
                }
            },
            noteContent = {
                // Only the kinds RenderNoteRow hands mediaBleed to let their media run past the column.
                ProvideNoteBleeds(0.dp, NoBoostedNoteFrame) {
                    RenderNoteRow(
                        baseNote = baseNote,
                        backgroundColor = backgroundColor,
                        makeItShort = makeItShort,
                        canPreview = canPreview,
                        editState = editState,
                        quotesLeft = quotesLeft,
                        unPackReply = unPackReply,
                        accountViewModel = accountViewModel,
                        nav = nav,
                        isBoostedNote = isBoostedNote,
                        mediaBleed = mediaBleed,
                        boostedHeaderShown = repostedNote != null,
                    )
                }

                if (!makeItShort) {
                    val noteEvent = baseNote.event
                    val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetupBesidesAuthor() ?: false }
                    if (zapSplits && noteEvent != null) {
                        Spacer(modifier = HalfDoubleVertSpacer)
                        DisplayZapSplits(noteEvent, false, accountViewModel, nav)
                    }
                }

                RenderApprovalIfNeeded(baseNote, accountViewModel, nav)
            },
            reactionsRow = {
                if (isNotRepost && baseNote.event !is DraftWrapEvent) {
                    if (makeItShort) {
                        Spacer(modifier = DoubleVertSpacer)
                    } else {
                        ReactionsRow(
                            baseNote = baseNote,
                            showReactionDetail = notBoostedNorQuote,
                            addPadding = !isBoostedNote,
                            editState = editState,
                            accountViewModel = accountViewModel,
                            nav = nav,
                            alignToText = fullWidth,
                        )
                    }
                } else if (baseNote.event is DraftWrapEvent) {
                    Spacer(modifier = DoubleVertSpacer)
                }
            },
            authorGutter = {
                if (gutter != null) ExpandedTextGutter(gutter)
            },
        )
    }
}

@Composable
private fun RenderApprovalIfNeeded(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote.isNewThread()) {
        val communityAddress =
            remember(baseNote) {
                baseNote.event?.communityAddress()
            }
        communityAddress?.let {
            LoadAddressableNote(it) { community ->
                if (community != null) {
                    val showApproveButton by observeCommunityApprovalNeedStatus(baseNote, community, accountViewModel)
                    if (showApproveButton == true) {
                        RenderApproveButton(baseNote, community, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun RenderApproveButton(
    post: Note,
    community: AddressableNote,
    accountViewModel: AccountViewModel,
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minWidth = Size10dp, minHeight = Size30dp),
        contentPadding = PaddingValues(0.dp),
        onClick = {
            accountViewModel.approveCommunityPost(post, community)
        },
    ) {
        Text(stringRes(Res.string.approve))
    }
}

@Composable
fun NoteBody(
    baseNote: Note,
    showAuthorPicture: Boolean = false,
    unPackReply: ReplyRenderType = ReplyRenderType.FULL,
    makeItShort: Boolean = false,
    canPreview: Boolean = true,
    showSecondRow: Boolean,
    isPinned: Boolean = false,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    FirstUserInfoRow(
        baseNote = baseNote,
        showAuthorPicture = showAuthorPicture,
        isPinned = isPinned,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
        moreOptions = moreOptions,
    )

    if (showSecondRow) {
        SecondUserInfoRow(
            baseNote,
            accountViewModel,
            nav,
        )
    }

    if (baseNote.event !is RepostEvent && baseNote.event !is GenericRepostEvent) {
        Spacer(modifier = Height4dpModifier)
    }

    RenderNoteRow(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        makeItShort = makeItShort,
        canPreview = canPreview,
        editState = editState,
        quotesLeft = quotesLeft,
        unPackReply = unPackReply,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    if (!makeItShort) {
        val noteEvent = baseNote.event
        val zapSplits = remember(noteEvent) { noteEvent?.hasZapSplitSetupBesidesAuthor() ?: false }
        if (zapSplits && noteEvent != null) {
            Spacer(modifier = HalfDoubleVertSpacer)
            DisplayZapSplits(noteEvent, false, accountViewModel, nav)
        }
        StaleRelayHint(baseNote)
    }
}

/**
 * The per-kind body of a note: the one place that maps an event class to the composable that draws
 * it. The feed cards call it, and so does the thread view's master note (ThreadFeedView) for every
 * kind that has no thread-specific header, so a kind added here renders in both without a second
 * branch to keep in sync.
 *
 * [mediaBleed] reaches only the kinds whose body is the note's own text or media (text notes,
 * pictures, videos, files, reposts); every other kind draws cards and must keep its media inside
 * them, so callers render this with no bleed of their own in effect. [boostedHeaderShown] tells a
 * repost's boosted note that the repost's header already shows its author row.
 */
@Composable
internal fun RenderNoteRow(
    baseNote: Note,
    backgroundColor: MutableState<Color>,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    unPackReply: ReplyRenderType,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    isBoostedNote: Boolean = false,
    mediaBleed: Dp = 0.dp,
    boostedHeaderShown: Boolean = false,
) {
    // The kind cards below read the row's quote budget and preview permission from here, so a card
    // quoted inside another (or inside itself) never quotes deeper than the row allows.
    ProvideKindCardRow(quotesLeft, canPreview && !makeItShort) {
        when (val noteEvent = baseNote.event) {
            is MetadataEvent -> {
                RenderProfileCard(baseNote, backgroundColor, accountViewModel, nav)
            }

            is AppDefinitionEvent -> {
                RenderAppDefinition(baseNote, accountViewModel, nav)
            }

            is AppRecommendationEvent -> {
                RenderAppRecommendation(baseNote, accountViewModel, nav)
            }

            is SoftwareApplicationEvent -> {
                RenderSoftwareApplication(baseNote, accountViewModel, nav, backgroundColor)
            }

            is SoftwareAssetEvent -> {
                RenderSoftwareAsset(baseNote, accountViewModel, nav)
            }

            is ReleaseArtifactSetEvent -> {
                // kind 30063 is used by both NIP-51 (ReleaseArtifactSet) and NIP-82 (SoftwareRelease).
                // The EventFactory always materializes the NIP-51 class; dispatch to NIP-82 when the
                // tag signature matches.
                if (noteEvent.isNip82SoftwareRelease()) {
                    RenderSoftwareRelease(baseNote, backgroundColor, accountViewModel, nav)
                } else {
                    RenderReleaseArtifactSet(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

            is AttestationEvent -> {
                RenderAttestation(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is AttestationRequestEvent -> {
                RenderAttestationRequest(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is AttestorRecommendationEvent -> {
                RenderAttestorRecommendation(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is AttestorProficiencyEvent -> {
                RenderAttestorProficiency(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            // Kind 31337 is borrowed (AntennaPod's ad-skip data, game state, sync indexes): only an
            // event with an audio file to play is a track. The rest fall through to the text card.
            is AudioTrackEvent if noteEvent.hasPlayableAudio() -> {
                RenderAudioTrack(baseNote, ContentScale.FillWidth, accountViewModel, nav)
            }

            is AudioHeaderEvent -> {
                RenderAudioHeader(baseNote, ContentScale.FillWidth, accountViewModel, nav)
            }

            is MusicTrackEvent -> {
                RenderMusicTrack(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
            }

            is MusicPlaylistEvent -> {
                RenderMusicPlaylist(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
            }

            is PodcastEpisodeEvent -> {
                RenderPodcastEpisode(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
            }

            is Podcasting20EpisodeEvent -> {
                RenderPodcastEpisode(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
            }

            is Podcasting20TrailerEvent -> {
                PodcastTrailerListItem(baseNote, accountViewModel, nav)
            }

            is PodcastMetadataEvent -> {
                RenderPodcastMetadata(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
            }

            is AppSpecificDataEvent -> {
                // kind:30078 is overloaded; only the Podcasting-2.0 show-metadata variant renders as a
                // podcast card. Anything else (e.g. a client's own settings) keeps the text fallback.
                if (noteEvent.dTag() == Podcasting20PodcastMetadata.PODCAST_METADATA_D_TAG) {
                    RenderPodcastMetadata(baseNote, makeItShort, canPreview, backgroundColor, accountViewModel, nav)
                } else {
                    RenderTextEvent(
                        baseNote,
                        makeItShort,
                        canPreview,
                        quotesLeft,
                        unPackReply,
                        backgroundColor,
                        editState,
                        accountViewModel,
                        nav,
                        isBoostedNote = isBoostedNote,
                    )
                }
            }

            is DraftWrapEvent -> {
                RenderDraft(baseNote, quotesLeft, unPackReply, backgroundColor, accountViewModel, nav)
            }

            is ReactionEvent -> {
                RenderReaction(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is RepostEvent -> {
                ProvideRepostFrame(BoostedNoteFrame(mediaBleed, boostedHeaderShown)) {
                    RenderRepost(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

            is GenericRepostEvent -> {
                ProvideRepostFrame(BoostedNoteFrame(mediaBleed, boostedHeaderShown)) {
                    RenderRepost(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

            is ReportEvent -> {
                RenderReport(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is LongFormContentEvent -> {
                RenderLongFormContent(baseNote, accountViewModel, nav)
            }

            is PublicationIndexEvent -> {
                RenderPublicationIndex(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is ThreadEvent -> {
                RenderThread(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    unPackReply,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is CodeSnippetEvent -> {
                RenderCodeSnippetEvent(baseNote)
            }

            is WikiArticleEvent -> {
                RenderWikiContent(baseNote, accountViewModel, nav)
            }

            is NipTextEvent -> {
                RenderNipContent(baseNote, accountViewModel, nav)
            }

            is BadgeAwardEvent -> {
                RenderBadgeAward(baseNote, backgroundColor, accountViewModel, nav)
            }

            is MemberAddedNotificationEvent -> {
                RenderChannelInvite(baseNote, accountViewModel, nav)
            }

            is BadgeDefinitionEvent -> {
                BadgeDisplay(baseNote = baseNote, accountViewModel = accountViewModel, nav = nav)
            }

            is ZapReceiptEvent -> {
                RenderZapReceipt(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is NutzapEvent -> {
                RenderNutzap(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is OnchainZapEvent -> {
                RenderOnchainZap(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is Bolt12ZapEvent -> {
                RenderBolt12Zap(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is LiveActivitiesClipEvent -> {
                RenderChatClip(baseNote, accountViewModel, nav)
            }

            is FhirResourceEvent -> {
                RenderFhirResource(baseNote, accountViewModel, nav)
            }

            is FollowSetEvent -> {
                DisplayPeopleList(baseNote, backgroundColor, accountViewModel, nav)
            }

            is StarterPackEvent -> {
                DisplayStarterPack(baseNote, true, accountViewModel, nav)
            }

            is ContactListEvent -> {
                DisplayContactList(baseNote, accountViewModel, nav)
            }

            is RelaySetEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is DmRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is AdvertisedRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is SearchRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is BlockedRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is TrustedRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is FavoriteRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is IndexerRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is ProxyRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is BroadcastRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            is RelayDiscoveryEvent -> {
                RenderRelayDiscovery(baseNote, accountViewModel, nav)
            }

            is RelayMembershipListEvent -> {
                RenderRelayMembershipList(baseNote, accountViewModel, nav)
            }

            is RelayAddMemberEvent -> {
                RenderRelayAddMember(baseNote, accountViewModel, nav)
            }

            is RelayRemoveMemberEvent -> {
                RenderRelayRemoveMember(baseNote, accountViewModel, nav)
            }

            is RelayJoinRequestEvent -> {
                RenderRelayJoinRequest(baseNote, accountViewModel, nav)
            }

            is RelayLeaveRequestEvent -> {
                RenderRelayLeaveRequest(baseNote, accountViewModel, nav)
            }

            is RelayAuthEvent -> {
                RenderRelayAuth(baseNote, accountViewModel, nav)
            }

            is HTTPAuthorizationEvent -> {
                RenderHttpAuth(baseNote)
            }

            is BlossomAuthorizationEvent -> {
                RenderBlossomAuth(baseNote)
            }

            is PinListEvent -> {
                RenderPinListEvent(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is EmojiPackEvent -> {
                RenderEmojiPack(baseNote, true, backgroundColor, accountViewModel)
            }

            is LiveActivitiesEvent -> {
                RenderLiveActivityEvent(baseNote, accountViewModel, nav)
            }

            is MeetingSpaceEvent -> {
                RenderMeetingSpaceEvent(baseNote, accountViewModel, nav)
            }

            is MeetingRoomEvent -> {
                RenderMeetingRoomEvent(baseNote, accountViewModel, nav)
            }

            is MeetingRoomPresenceEvent -> {
                RenderMeetingRoomPresence(baseNote, accountViewModel, nav)
            }

            is GitRepositoryEvent -> {
                RenderGitRepositoryEvent(baseNote, accountViewModel, nav)
            }

            is RootSiteEvent -> {
                StaticWebsiteManifestCard(noteEvent, nav)
            }

            is NamedSiteEvent -> {
                StaticWebsiteManifestCard(noteEvent, nav)
            }

            // Other apps publish their own formats under these kinds: only a valid NIP-5D manifest
            // gets the napplet card, the rest get the fallback card that shows what they carry.
            is RootNappletEvent -> {
                if (noteEvent.isValid()) {
                    StaticWebsiteManifestCard(noteEvent, nav)
                } else {
                    RenderUnknownKind(baseNote, makeItShort, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

            is NamedNappletEvent -> {
                if (noteEvent.isValid()) {
                    StaticWebsiteManifestCard(noteEvent, nav)
                } else {
                    RenderUnknownKind(baseNote, makeItShort, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

            is GitPatchEvent -> {
                RenderGitPatchEvent(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is GitIssueEvent -> {
                RenderGitIssueEvent(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is GitPullRequestEvent -> {
                RenderGitPullRequestEvent(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is GitPullRequestUpdateEvent -> {
                RenderGitPullRequestUpdateEvent(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is GitStatusEvent -> {
                RenderGitStatusEvent(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is GitReviewEvent -> {
                RenderGitReviewEvent(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is GitCoverNoteEvent -> {
                RenderGitCoverNoteEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is EncryptedDmEvent -> {
                RenderPrivateMessage(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is ChatMessageEvent -> {
                RenderChatMessage(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is ChatMessageEncryptedFileHeaderEvent -> {
                RenderChatMessageEncryptedFile(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is SnoObjectEvent -> {
                RenderSnoObject(baseNote, accountViewModel)
            }

            is SnoAvatarEvent -> {
                RenderSnoAvatar(baseNote, accountViewModel)
            }

            is SnoShardEvent -> {
                RenderSnoShard(baseNote, accountViewModel)
            }

            is CyberspaceBagEvent -> {
                RenderCyberspaceBag(baseNote, accountViewModel)
            }

            is ChessGameEvent -> {
                RenderChessGame(
                    baseNote,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is LiveChessGameChallengeEvent -> {
                RenderLiveChessChallenge(
                    baseNote,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is LiveChessGameEndEvent -> {
                RenderLiveChessGameEnd(
                    baseNote,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is CashuMintEvent -> {
                RenderCashuMint(noteEvent)
            }

            is FedimintEvent -> {
                RenderFedimint(noteEvent)
            }

            is MintRecommendationEvent -> {
                RenderMintRecommendation(noteEvent)
            }

            is MostroInfoEvent -> {
                RenderMostroInfo(baseNote)
            }

            // Kind 30301 is shared: the board and the WalletScrutiny verdict each have a card, and
            // the class EventFactory picked from the tags is what decides between them.
            is KanbanBoardEvent -> {
                RenderKanbanBoard(baseNote, makeItShort, accountViewModel)
            }

            is BuildVerificationEvent -> {
                RenderBuildVerification(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is KanbanCardEvent -> {
                RenderKanbanCard(baseNote, makeItShort, accountViewModel, nav)
            }

            // Other apps' formats on those shared kinds (an encrypted planner's tasks, Fieldbook
            // memberships, Paygress revocations...). EventCache never stores them, so this only
            // guards the fall-through below, which would print their raw content.
            is UnrecognizedKind30301Event,
            is UnrecognizedKind30302Event,
            is UnrecognizedKind38385Event,
            // Shopstr's encrypted carts (30405) and escrow records (30406): ciphertext, nothing to draw.
            is UnrecognizedKind30405Event,
            is UnrecognizedKind30406Event,
            // drss.io's reader: listening progress, generated shows, list folders, lists and
            // provenance notes, drawn the way drss draws them (types/Drss.kt).
            is DrssListenProgressAppDataEvent,
            is DrssListenProgressEvent,
            -> RenderDrssListenProgress(baseNote, accountViewModel, nav)
            is DrssShowEvent -> RenderDrssShow(baseNote, accountViewModel, nav)
            is DrssListFolderEvent -> RenderDrssListFolder(baseNote, accountViewModel, nav)
            is DrssFeedListEvent -> RenderDrssFeedList(baseNote, accountViewModel, nav)
            is DrssEventListEvent -> RenderDrssEventList(baseNote, accountViewModel, nav)
            is DrssProvenanceEvent -> RenderDrssProvenance(baseNote, backgroundColor, accountViewModel, nav)
            is DrssProvenanceFolderEvent -> RenderDrssProvenanceFolder(baseNote, accountViewModel, nav)
            is DrssReadWriteListsEvent -> RenderDrssReadWriteLists(baseNote, accountViewModel, nav)

            // Other apps' events on the kinds drss.io shares: the unknown-kind card, as before they
            // were typed.
            is UnrecognizedKind30054Event,
            is UnrecognizedKind30100Event,
            is UnrecognizedKind30101Event,
            is UnrecognizedKind30102Event,
            is UnrecognizedKind30104Event,
            is UnrecognizedKind31985Event,
            -> RenderUnknownKind(baseNote, makeItShort, quotesLeft, backgroundColor, accountViewModel, nav)

            is PredictionMarketEvent -> {
                RenderPredictionMarket(baseNote, makeItShort, accountViewModel)
            }

            is BallotEvent -> {
                RenderBallot(baseNote, accountViewModel)
            }

            is ClassifiedsEvent -> {
                RenderClassifieds(
                    noteEvent,
                    baseNote,
                    accountViewModel,
                    nav,
                )
            }

            // Open Markets, around the NIP-99 listings.
            is ProductCollectionEvent -> {
                RenderProductCollection(baseNote, makeItShort, accountViewModel, nav)
            }

            is ShippingOptionEvent -> {
                RenderShippingOption(baseNote, makeItShort, accountViewModel, nav)
            }

            is ProductReviewEvent -> {
                RenderProductReview(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            // NIP-15. Other apps' records on 30017-30020 (no name, no merchants) fall through to the
            // text card below, as they always did.
            is StallEvent if noteEvent.isWellFormed() -> {
                RenderStall(baseNote, makeItShort, accountViewModel, nav)
            }

            is ProductEvent if noteEvent.isWellFormed() -> {
                RenderProduct(baseNote, makeItShort, accountViewModel, nav)
            }

            is MarketplaceEvent if noteEvent.isWellFormed() -> {
                RenderMarketplace(baseNote, makeItShort, accountViewModel, nav)
            }

            is AuctionEvent if noteEvent.isWellFormed() -> {
                RenderAuction(baseNote, makeItShort, accountViewModel, nav)
            }

            is BidEvent if noteEvent.isWellFormed() -> {
                RenderBid(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is BidConfirmationEvent if noteEvent.isWellFormed() -> {
                RenderBidConfirmation(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is CalendarTimeSlotEvent -> {
                RenderCalendarTimeSlotEvent(baseNote, accountViewModel, nav)
            }

            is CalendarDateSlotEvent -> {
                RenderCalendarDateSlotEvent(baseNote, accountViewModel, nav)
            }

            is CalendarCollectionEvent -> {
                RenderCalendarCollectionEvent(baseNote, accountViewModel, nav)
            }

            is CalendarRSVPEvent -> {
                RenderCalendarRSVPEvent(baseNote, accountViewModel, nav)
            }

            is ZapGoalEvent -> {
                RenderGoal(baseNote, accountViewModel, nav)
            }

            is FundraiserEvent -> {
                RenderFundraiser(baseNote, makeItShort, accountViewModel, nav)
            }

            is EbookEvent -> {
                RenderEbook(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is MagazineEvent -> {
                RenderMagazine(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is MagazineIssueEvent -> {
                RenderMagazineIssue(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is BirdexEvent -> {
                RenderBirdex(baseNote)
            }

            is BirdDetectionEvent -> {
                RenderBirdDetection(baseNote)
            }

            is Ps1SaveEvent -> {
                RenderPs1Save(baseNote)
            }

            is GeocacheListingEvent -> {
                RenderGeocache(baseNote, accountViewModel)
            }

            is GeocacheFoundLogEvent -> {
                RenderGeocacheFoundLog(baseNote, accountViewModel)
            }

            is RoadEventReportEvent -> {
                RenderRoadEventReport(baseNote)
            }

            is RoadEventConfirmationEvent -> {
                RenderRoadEventConfirmation(baseNote)
            }

            is HighlightEvent -> {
                RenderHighlight(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is DvmContentDiscoveryResponseEvent -> {
                RenderDvmContentDiscoveryResponse(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is DvmStatusEvent -> {
                RenderDvmStatus(
                    baseNote,
                    accountViewModel,
                    nav,
                )
            }

            is ZapPollEvent -> {
                RenderZapPoll(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    unPackReply,
                    backgroundColor,
                    accountViewModel,
                    nav,
                    isBoostedNote = isBoostedNote,
                )
            }

            is ChatEvent -> {
                // A kind-9 that belongs to a Buzz/NIP-29 relay group (its channel is recorded as a gatherer
                // on consume) renders as a conversation row with a group/DM header — so a Buzz DM on the
                // Notifications tab reads like a message, not a post. A non-group kind-9 keeps the plain chat body.
                if (baseNote.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel } != null) {
                    RenderRelayGroupMessage(
                        baseNote,
                        makeItShort,
                        canPreview,
                        quotesLeft,
                        backgroundColor,
                        editState,
                        accountViewModel,
                        nav,
                    )
                } else {
                    RenderChat(
                        baseNote,
                        makeItShort,
                        canPreview,
                        quotesLeft,
                        unPackReply,
                        backgroundColor,
                        accountViewModel,
                        nav,
                        isBoostedNote = isBoostedNote,
                    )
                }
            }

            is StreamMessageV2Event -> {
                RenderRelayGroupMessage(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is PollEvent -> {
                RenderPoll(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                    isBoostedNote = isBoostedNote,
                )
            }

            is FileMetadataEvent -> {
                ProvideMediaBleed(mediaBleed) {
                    FileMetadataDisplay(baseNote, true, ContentScale.FillWidth, accountViewModel)
                }
            }

            // Covers every NIP-71 video kind (21, 22, 34235, 34236) via the shared interface,
            // so new video subtypes render automatically instead of falling through to text.
            is VideoEvent -> {
                ProvideMediaBleed(mediaBleed) {
                    VideoDisplay(baseNote, makeItShort, canPreview, backgroundColor, ContentScale.FillWidth, accountViewModel, nav)
                }
            }

            is VideoCollaborationEvent -> {
                RenderVideoCollaboration(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is VideoCurationSetEvent -> {
                RenderVideoCurationSet(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is PictureEvent -> {
                ProvideMediaBleed(mediaBleed) {
                    PictureDisplay(baseNote, true, ContentScale.FillWidth, PaddingValues(vertical = 5.dp), backgroundColor, accountViewModel, nav)
                }
            }

            is WorkoutRecordEvent -> {
                WorkoutDisplay(baseNote, backgroundColor, canPreview, quotesLeft, accountViewModel, nav)
            }

            is ExerciseTemplateEvent -> {
                ExerciseTemplateDisplay(baseNote, backgroundColor, canPreview, quotesLeft, accountViewModel, nav)
            }

            is BaseVoiceEvent -> {
                RenderVoiceTrack(baseNote, accountViewModel, nav)
            }

            is FileStorageHeaderEvent -> {
                ProvideMediaBleed(mediaBleed) {
                    FileStorageHeaderDisplay(baseNote, true, ContentScale.FillWidth, accountViewModel)
                }
            }

            is CommunityPostApprovalEvent -> {
                RenderPostApproval(
                    baseNote,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is TextNoteModificationEvent -> {
                RenderTextModificationEvent(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is ChannelMessageEvent -> {
                RenderChannelMessage(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is LiveActivitiesChatMessageEvent -> {
                RenderLiveActivityChatMessage(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is TorrentEvent -> {
                RenderTorrent(
                    baseNote,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is TorrentCommentEvent -> {
                RenderTorrentComment(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    unPackReply,
                    backgroundColor,
                    editState,
                    accountViewModel,
                    nav,
                )
            }

            is InteractiveStoryBaseEvent -> {
                RenderInteractiveStory(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            // One branch for kinds 31/32/33: they are one idea aimed at three sorts of source.
            is CitationEvent -> {
                RenderCitation(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is LearningResourceEvent -> {
                RenderLearningResource(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is BookshelfDirectoryEvent -> {
                RenderBookshelfDirectory(baseNote, accountViewModel, nav)
            }

            is BlossomPieceIndexEvent -> {
                RenderBlossomPieceIndex(baseNote, accountViewModel)
            }

            is ExternalReactionEvent -> {
                RenderExternalReaction(baseNote, backgroundColor, accountViewModel, nav)
            }

            is WikiMergeRequestEvent -> {
                RenderWikiMergeRequest(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is WikiMergeAcceptanceEvent -> {
                RenderWikiMergeAcceptance(baseNote, accountViewModel, nav)
            }

            is WikiRedirectEvent -> {
                RenderWikiRedirect(baseNote, accountViewModel, nav)
            }

            is PublicationContentEvent -> {
                RenderPublicationSection(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is RelayReviewEvent -> {
                RenderRelayReview(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is EntityRatingEvent -> {
                RenderEntityRating(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                )
            }

            is PublicMessageEvent -> {
                RenderPublicMessage(
                    baseNote,
                    makeItShort,
                    canPreview,
                    quotesLeft,
                    backgroundColor,
                    accountViewModel,
                    nav,
                    isBoostedNote = isBoostedNote,
                )
            }

            is MuteListEvent -> {
                RenderMuteList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is BookmarkListEvent -> {
                RenderBookmarkList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is OldBookmarkListEvent -> {
                RenderOldBookmarkList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is BookmarkSetEvent -> {
                RenderBookmarkSet(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is ArticleCurationSetEvent -> {
                RenderArticleCurationSet(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is PictureCurationSetEvent -> {
                RenderPictureCurationSet(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is AppCurationSetEvent -> {
                RenderAppCurationSet(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is GitRepositoryListEvent -> {
                RenderGitRepositoryList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is FavoriteAlgoFeedsListEvent -> {
                RenderFavoriteAlgoFeedsList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is GitAuthorListEvent -> {
                RenderGitAuthorList(baseNote, backgroundColor, accountViewModel, nav)
            }

            is MediaFollowListEvent -> {
                RenderMediaFollowList(baseNote, backgroundColor, accountViewModel, nav)
            }

            is GoodWikiAuthorListEvent -> {
                RenderGoodWikiAuthorList(baseNote, backgroundColor, accountViewModel, nav)
            }

            is KindMuteSetEvent -> {
                RenderKindMuteSet(baseNote, backgroundColor, accountViewModel, nav)
            }

            is MediaStarterPackEvent -> {
                RenderMediaStarterPack(baseNote, backgroundColor, accountViewModel, nav)
            }

            is InterestListEvent -> {
                RenderInterestList(baseNote, backgroundColor, accountViewModel, nav)
            }

            is InterestSetEvent -> {
                RenderInterestSet(baseNote, backgroundColor, accountViewModel, nav)
            }

            is GeohashListEvent -> {
                RenderGeohashList(baseNote, backgroundColor, accountViewModel)
            }

            is SimpleGroupListEvent -> {
                RenderSimpleGroupList(baseNote, backgroundColor, accountViewModel)
            }

            is GoodWikiRelayListEvent -> {
                DisplayRelayList(baseNote, accountViewModel, nav)
            }

            // NIP-85: one card for the four assertion kinds, which differ only in what they score.
            is UserAssertionEvent,
            is EventAssertionEvent,
            is AddressableAssertionEvent,
            is ExternalIdAssertionEvent,
            -> {
                RenderTrustedAssertion(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            is TrustProviderListEvent -> {
                RenderTrustProviderList(baseNote, accountViewModel, nav)
            }

            // Trusted Lists, kinds 30392-30395.
            is TrustedListEvent -> {
                RenderTrustedList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            }

            // Decentralized Lists. An item that declares a list (the spec's "nonstandard method") is a header.
            is ListHeaderEvent,
            is AddressableListHeaderEvent,
            -> {
                RenderDListHeader(baseNote, accountViewModel, nav)
            }

            is ListItemEvent -> {
                if (noteEvent.declaresList()) {
                    RenderDListHeader(baseNote, accountViewModel, nav)
                } else {
                    RenderDListItem(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

            is AddressableListItemEvent -> {
                if (noteEvent.declaresList()) {
                    RenderDListHeader(baseNote, accountViewModel, nav)
                } else {
                    RenderDListItem(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
                }
            }

            is GroupMetadataEvent -> {
                RenderGroupMetadata(baseNote, accountViewModel, nav)
            }

            // Profile and settings lists.
            is EmojiListEvent -> RenderEmojiList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is StickerPackEvent -> RenderStickerPack(baseNote, backgroundColor, accountViewModel, nav)
            is ProfileBadgesEvent -> RenderProfileBadges(baseNote, accountViewModel, nav)
            is AcceptedBadgeSetEvent -> RenderAcceptedBadgeSet(baseNote, backgroundColor, accountViewModel, nav)
            is BlossomServersEvent -> RenderBlossomServers(baseNote, backgroundColor)
            is FileServersEvent -> RenderFileServers(baseNote, backgroundColor)
            is NestsServersEvent -> RenderNestsServers(baseNote, backgroundColor)
            is PrivateOutboxRelayListEvent -> DisplayRelayList(baseNote, accountViewModel, nav)
            is NutzapInfoEvent -> RenderNutzapInfo(baseNote)
            is PaymentTargetsEvent -> RenderPaymentTargets(baseNote, accountViewModel, nav)
            is Bolt12OfferListEvent -> RenderBolt12OfferList(baseNote, accountViewModel)
            is FavoritePodcastsListEvent -> RenderFavoritePodcasts(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is AuthoredPodcastsEvent -> RenderAuthoredPodcasts(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is FavoriteFollowSetsListEvent -> RenderFavoriteFollowSets(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CommunityListEvent -> RenderCommunityList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is TopEightEvent -> RenderTopEight(baseNote, backgroundColor, accountViewModel, nav)
            is ExternalIdentitiesEvent -> RenderExternalIdentities(baseNote)
            is UserStatusEvent -> RenderUserStatus(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is PostingStreakEvent -> RenderPostingStreak(baseNote)
            is ActiveProfileThemeEvent -> RenderActiveProfileTheme(baseNote, backgroundColor, accountViewModel, nav)
            is ThemeDefinitionEvent -> RenderThemeDefinition(baseNote, backgroundColor, accountViewModel, nav)
            is ProfileGalleryEntryEvent -> RenderProfileGalleryEntry(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is WebBookmarkEvent -> RenderWebBookmark(baseNote, backgroundColor, accountViewModel, nav)
            is GeocacheCurationListEvent -> RenderGeocacheCurationList(baseNote, accountViewModel, nav)

            // Deletions, labels, timestamps, chess, P2P orders and other public records.
            is DeletionRequestEvent -> RenderDeletionRequest(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is RequestToVanishEvent -> RenderRequestToVanish(baseNote, backgroundColor, accountViewModel, nav)
            is LabelEvent -> RenderLabel(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is OtsEvent -> RenderOts(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is FileStorageEvent -> RenderFileStorage(baseNote)
            is GeocacheVerificationEvent -> RenderGeocacheVerification(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is InteractiveStoryReadingStateEvent -> RenderInteractiveStoryReadingState(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is LongFormDraftEvent -> RenderLongFormDraft(baseNote, accountViewModel)
            is JesterEvent -> RenderJesterChess(baseNote, accountViewModel, nav)
            is LiveChessGameAcceptEvent -> RenderLiveChessAccept(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is LiveChessMoveEvent -> RenderLiveChessMove(baseNote, backgroundColor, accountViewModel, nav)
            is LiveChessDrawOfferEvent -> RenderLiveChessDrawOffer(baseNote, backgroundColor, accountViewModel, nav)
            is P2POrderEvent -> RenderP2POrder(baseNote)
            is MostroUserRatingEvent -> RenderMostroUserRating(baseNote, accountViewModel, nav)
            is MostroDisputeEvent -> RenderMostroDispute(baseNote)
            is MostroDevFeePaymentEvent -> RenderMostroDevFee(baseNote)
            is RoboSatsCoordinatorRatingEvent -> RenderRoboSatsCoordinatorRating(baseNote, accountViewModel, nav)
            is PollResponseEvent -> RenderPollResponse(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is RelayMonitorEvent -> RenderRelayMonitor(baseNote)
            is TextTrackEvent -> RenderTextTrack(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is VideoViewEvent -> RenderVideoView(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is WorkoutTemplateEvent -> RenderWorkoutTemplate(baseNote, backgroundColor, accountViewModel, nav)
            // Other apps' formats on the RoboSats (31986), mint-recommendation (38000) and Mostro
            // rating/dispute (38384/38386) kinds: nothing to draw, and their raw content must not
            // reach the text path (EventCache keeps 38000 junk only so it supersedes older versions).
            is UnrecognizedKind31986Event,
            is UnrecognizedKind38000Event,
            is UnrecognizedKind38384Event,
            is UnrecognizedKind38386Event,
            -> {}

            // Buzz chat, huddle, job, forum, moderation and archival events.
            is ArchiveRequestEvent -> RenderBuzzArchiveRequest(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is UnarchiveRequestEvent -> RenderBuzzUnarchiveRequest(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ArchivedIdentityEvent -> RenderBuzzIdentityArchived(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is UnarchivedIdentityEvent -> RenderBuzzIdentityUnarchived(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ArchivedIdentitiesListEvent -> RenderBuzzArchivedIdentitiesList(baseNote, accountViewModel, nav)
            is SystemMessageEvent -> RenderBuzzSystemMessageCard(baseNote, accountViewModel, nav)
            is CanvasEvent -> RenderBuzzCanvas(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is StreamMessageEditEvent -> RenderBuzzMessageEdit(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is StreamMessagePinnedEvent -> RenderBuzzMessagePinned(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is StreamMessageBookmarkedEvent -> RenderBuzzMessageBookmarked(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is StreamMessageScheduledEvent -> RenderBuzzMessageScheduled(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is StreamReminderEvent -> RenderBuzzReminder(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is StreamMessageDiffEvent -> RenderBuzzDiffCard(baseNote, backgroundColor, accountViewModel, nav)
            is ChannelSummaryEvent -> RenderBuzzChannelSummary(baseNote, backgroundColor, accountViewModel, nav)
            is PresenceSnapshotEvent -> RenderBuzzPresenceSnapshot(baseNote, accountViewModel, nav)
            is ThreadSummaryEvent -> RenderBuzzThreadSummary(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ForumPostEvent -> RenderBuzzForumPost(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ForumCommentEvent -> RenderBuzzForumComment(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ForumVoteEvent -> RenderBuzzForumVoteCard(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is HuddleStartedEvent -> RenderBuzzHuddleStarted(baseNote, accountViewModel, nav)
            is HuddleParticipantJoinedEvent -> RenderBuzzHuddleJoined(baseNote, accountViewModel, nav)
            is HuddleParticipantLeftEvent -> RenderBuzzHuddleLeft(baseNote, accountViewModel, nav)
            is HuddleEndedEvent -> RenderBuzzHuddleEnded(baseNote, accountViewModel, nav)
            is HuddleLivenessEvent -> RenderBuzzHuddleLiveness(baseNote, accountViewModel, nav)
            is HuddleGuidelinesEvent -> RenderBuzzHuddleGuidelines(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is HuddleReactionEvent -> RenderBuzzHuddleReaction(baseNote)
            is JobRequestEvent -> RenderBuzzJobRequest(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is JobAcceptedEvent -> RenderBuzzJobAccepted(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is JobProgressEvent -> RenderBuzzJobProgress(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is JobResultEvent -> RenderBuzzJobResult(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is JobCancelEvent -> RenderBuzzJobCancel(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is JobErrorEvent -> RenderBuzzJobError(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is DmOpenEvent -> RenderBuzzDmOpen(baseNote, accountViewModel, nav)
            is DmCreatedEvent -> RenderBuzzDmCreated(baseNote, accountViewModel, nav)
            is DmAddMemberEvent -> RenderBuzzDmAddMember(baseNote, accountViewModel, nav)
            is DmHideEvent -> RenderBuzzDmHide(baseNote, accountViewModel, nav)
            is DmVisibilityEvent -> RenderBuzzDmVisibility(baseNote, accountViewModel, nav)
            is PresenceUpdateEvent -> RenderBuzzPresenceUpdate(baseNote, accountViewModel, nav)
            is TypingIndicatorEvent -> RenderBuzzTyping(baseNote, accountViewModel, nav)
            is MemberRemovedNotificationEvent -> RenderBuzzMemberRemoved(baseNote, accountViewModel, nav)
            is ModerationBanEvent -> RenderBuzzModerationBan(baseNote, backgroundColor, accountViewModel, nav)
            is ModerationTimeoutEvent -> RenderBuzzModerationTimeout(baseNote, backgroundColor, accountViewModel, nav)
            is ModerationUntimeoutEvent -> RenderBuzzModerationUntimeout(baseNote, accountViewModel, nav)
            is ModerationResolveReportEvent -> RenderBuzzModerationResolveReport(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ProductFeedbackEvent -> RenderBuzzProductFeedback(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is RelayAdminAddMemberEvent -> RenderBuzzRelayAdminAddMember(baseNote, accountViewModel, nav)
            is RelayAdminRemoveMemberEvent -> RenderBuzzRelayAdminRemoveMember(baseNote, accountViewModel, nav)
            is RelayAdminChangeRoleEvent -> RenderBuzzRelayAdminChangeRole(baseNote, accountViewModel, nav)
            is SetWorkspaceProfileEvent -> RenderBuzzSetWorkspaceProfile(baseNote, accountViewModel)
            is ThreadWindowBoundsEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Sync)
            is WindowBoundsEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Sync)

            // NIP-29 group moderation and metadata, NIP-28 channel moderation, Concord, location chats.
            is GroupPutUserEvent -> RenderGroupPutUser(baseNote, backgroundColor, accountViewModel, nav)
            is GroupRemoveUserEvent -> RenderGroupRemoveUser(baseNote, backgroundColor, accountViewModel, nav)
            is GroupEditMetadataEvent -> RenderGroupEditMetadata(baseNote, backgroundColor, accountViewModel, nav)
            is GroupDeleteEventEvent -> RenderGroupDeleteEvent(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CreateGroupEvent -> RenderCreateGroup(baseNote, backgroundColor, accountViewModel, nav)
            is DeleteGroupEvent -> RenderDeleteGroup(baseNote, backgroundColor, accountViewModel, nav)
            is GroupCreateInviteEvent -> RenderGroupCreateInvite(baseNote, accountViewModel, nav)
            is GroupUpdatePinListEvent -> RenderGroupUpdatePinList(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is GroupJoinRequestEvent -> RenderGroupJoinRequest(baseNote, backgroundColor, accountViewModel, nav)
            is GroupLeaveRequestEvent -> RenderGroupLeaveRequest(baseNote, backgroundColor, accountViewModel, nav)
            is GroupAdminsEvent -> RenderGroupAdmins(baseNote, accountViewModel, nav)
            is GroupMembersEvent -> RenderGroupMembers(baseNote, accountViewModel, nav)
            is GroupRolesEvent -> RenderGroupRoles(baseNote, backgroundColor, accountViewModel, nav)
            is GroupParticipantsEvent -> RenderGroupParticipants(baseNote, accountViewModel, nav)
            is GroupPinnedEvent -> RenderGroupPinned(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ChannelHideMessageEvent -> RenderChannelHideMessage(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ChannelMuteUserEvent -> RenderChannelMuteUser(baseNote, backgroundColor, accountViewModel, nav)
            is PublicChatListEvent -> RenderPublicChatList(baseNote, backgroundColor, accountViewModel, nav)
            is EphemeralChatListEvent -> RenderEphemeralChatList(baseNote, backgroundColor, accountViewModel, nav)
            is EphemeralChatEvent -> RenderEphemeralChat(baseNote, backgroundColor, accountViewModel, nav)
            is GeohashChatEvent -> RenderGeohashChat(baseNote, backgroundColor, accountViewModel, nav)
            is GeohashPresenceEvent -> RenderGeohashPresence(baseNote, nav)
            is AdminCommandEvent -> RenderNestsAdminCommand(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CommunityRulesEvent -> RenderCommunityRules(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is LiveActivitiesRaidEvent -> RenderLiveActivityRaid(baseNote, backgroundColor, accountViewModel, nav)
            is ConcordChatEditEvent -> RenderConcordChatEdit(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ConcordTimerNoticeEvent -> RenderConcordTimerNotice(baseNote, accountViewModel, nav)
            is ControlEditionEvent -> RenderConcordControlEdition(baseNote)
            is ConcordInviteBundleEvent -> RenderConcordInviteBundle(baseNote)
            is ConcordCommunityListEvent -> RenderPrivateKindCard(baseNote)
            is ConcordCommunityListFragmentEvent -> RenderPrivateKindCard(baseNote)
            is ConcordInviteListEvent -> RenderPrivateKindCard(baseNote)
            is RelayInviteRequestEvent -> RenderRelayInviteRequest(baseNote)
            is RelayRoleEvent -> RenderRelayRole(baseNote, backgroundColor, accountViewModel, nav)

            // Encrypted and protocol plumbing (MLS, gift wraps, wallet connect, calls, push): a card
            // that names the kind, never the ciphertext; zap requests and key packages show their public fields.
            // Zaps and payment requests
            is ZapRequestEvent -> RenderZapRequest(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is PrivateZapEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Bolt)
            is Bolt12ZapIntentEvent -> RenderBolt12ZapIntent(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            // NIP-47 wallet connect
            is NwcInfoEvent -> RenderNwcInfo(baseNote)
            is NwcRequestEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Payment)
            is NwcResponseEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Paid)
            is NwcNotificationEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)
            // CLINK
            is OfferEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Storefront)
            is DebitEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Payment)
            is ManageEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Settings)
            // NIP-60 Cashu wallet
            is CashuWalletEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.AccountBalanceWallet)
            // AI agent keys (kind 12000): encrypted to the author, never drawn
            is AgentKeyEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Key)
            // NIP-FB scheduled tasks (kind 34817): encrypted to the author, never drawn
            is ScheduledTaskEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Schedule)
            is CashuTokenEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Paid)
            is CashuSpendingHistoryEvent -> RenderCashuSpendingHistory(baseNote, accountViewModel, nav)
            is CashuMintQuoteEvent -> RenderCashuMintQuote(baseNote)
            // Marmot MLS (KeyPackageEvent 30443 and LegacyKeyPackageEvent 443 share PublishedKeyPackage)
            is PublishedKeyPackage -> RenderMarmotKeyPackage(baseNote)
            is KeyPackageRelayListEvent -> DisplayRelayList(baseNote, accountViewModel, nav)
            is WelcomeEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.GroupAdd)
            is GroupEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Groups)
            is NotificationRequestEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)
            is TokenRequestEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)
            is TokenListEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)
            is TokenRemovalEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.NotificationsOff)
            // NIP-59 gift wraps and seals (EphemeralGiftWrapEvent extends GiftWrapEvent)
            is GiftWrapEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Mail)
            is SealEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Lock)
            is PnsEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Lock)
            // WebRTC calls
            is CallOfferEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Call)
            is CallAnswerEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Call)
            is CallIceCandidateEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.SettingsInputAntenna)
            is CallRenegotiateEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Sync)
            is CallHangupEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.CallEnd)
            is CallRejectEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.CallEnd)
            // Remote signing, push, wake-up
            is NostrConnectEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.PhonelinkLock)
            is PushRegistrationEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)
            is PushDeregistrationEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.NotificationsOff)
            is PushPreferencesEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Tune)
            is WakeUpEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Notifications)
            // Key decoupling
            is EncryptionKeyListEvent -> RenderEncryptionKeyList(baseNote)
            is EncryptionKeyRequestEvent -> RenderEncryptionKeyRequest(baseNote)
            is EncryptionKeyTransferEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Key)

            // NIP-90 job requests and results. After the DVM kinds above that keep their own cards.
            // Base-class branches: they must stay AFTER the existing `is DvmContentDiscoveryResponseEvent` and
            // `is DvmStatusEvent` branches (both DvmResponseEvent subclasses), which keep their own renderers.
            is DvmRequestEvent -> RenderDvmRequest(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is DvmResponseEvent -> RenderDvmResult(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is DvmHeartbeatEvent -> RenderDvmHeartbeat(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)

            // Git CI, repository state and developer records.
            is CiWorkflowProgressEvent -> RenderCiWorkflowProgress(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CiWorkflowResultEvent -> RenderCiWorkflowResult(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CiJobResultEvent -> RenderCiJobResult(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CiManualTriggerEvent -> RenderCiManualTrigger(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CiServiceRequestEvent -> RenderCiServiceRequest(baseNote, accountViewModel, nav)
            is CiServiceStopEvent -> RenderCiServiceStop(baseNote, accountViewModel, nav)
            is CiCoordinatorAdvertisementEvent -> RenderCiCoordinatorAdvertisement(baseNote)
            is CiRequestReadinessListEvent -> RenderCiRequestReadinessList(baseNote, accountViewModel, nav)
            is CiRepositoryStatusEvent -> RenderCiRepositoryStatus(baseNote)
            is CiSecretUpdateEvent -> RenderPrivateKindCard(baseNote, MaterialSymbols.Key)
            is GitRepositoryStateEvent -> RenderGitRepositoryState(baseNote)
            is UserGraspListEvent -> RenderUserGraspList(baseNote)
            is IdentityProofEvent -> RenderIdentityProof(baseNote, backgroundColor, accountViewModel, nav)
            is AssetBundleEvent -> RenderAssetBundle(baseNote, backgroundColor, accountViewModel, nav)
            is SiteSnapshotEvent -> RenderSiteSnapshot(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is NappletSnapshotEvent -> RenderNappletSnapshot(baseNote, backgroundColor, accountViewModel, nav)
            is FeedDefinitionEvent -> RenderFeedDefinition(baseNote, accountViewModel, nav)
            is AppDataEvent -> RenderAppData(baseNote)
            is NNSEvent -> RenderNNS(baseNote)

            // Buzz agents, workflows, teams and projects; OpenAgents capabilities; ContextVM.
            is AgentProfileEvent -> RenderAgentProfile(baseNote)
            is ManagedAgentEvent -> RenderManagedAgent(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is PersonaEvent -> RenderPersona(baseNote, backgroundColor, accountViewModel, nav)
            is TeamEvent -> RenderAgentTeam(baseNote, backgroundColor, accountViewModel, nav)
            is TeamCatalogEvent -> RenderAgentTeamCatalog(baseNote, backgroundColor, accountViewModel, nav)
            is ProjectEvent -> RenderAgentProject(baseNote, backgroundColor, accountViewModel, nav)
            is ArtifactEvent -> RenderAgentArtifact(baseNote, backgroundColor, accountViewModel, nav)
            is ArtifactRemovalEvent -> RenderAgentArtifactRemoval(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is AuditEntryEvent -> RenderAgentAuditEntry(baseNote, accountViewModel, nav)
            is WorkflowDefEvent -> RenderWorkflowDefinition(baseNote)
            is WorkflowTriggerEvent -> RenderWorkflowTrigger(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is WorkflowTriggeredEvent, is WorkflowStepStartedEvent, is WorkflowStepCompletedEvent, is WorkflowStepFailedEvent, is WorkflowCompletedEvent, is WorkflowFailedEvent, is WorkflowCancelledEvent, is WorkflowApprovalRequestedEvent, is WorkflowApprovalGrantedEvent, is WorkflowApprovalDeniedEvent -> RenderWorkflowLifecycle(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ApprovalGrantEvent, is ApprovalDenyEvent -> RenderWorkflowApprovalDecision(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is CapabilityDiscoveryEvent -> RenderCapabilityDiscovery(baseNote, backgroundColor, accountViewModel, nav)
            is ExtensionListingEvent -> RenderExtensionListing(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
            is ExtensionReleaseEvent -> RenderExtensionRelease(baseNote)
            is CvmServerAnnouncementEvent -> RenderCvmServerAnnouncement(baseNote, backgroundColor, accountViewModel, nav)
            is RoutstrNodeEvent -> RenderRoutstrNode(baseNote, nav)
            is CvmToolsListEvent -> RenderCvmToolsList(baseNote, accountViewModel)
            is EventReminderEvent -> RenderEventReminder(baseNote)
            is AgentTurnMetricEvent -> RenderAgentTurnMetric(baseNote)
            is EngramEvent -> RenderEngram(baseNote)
            is ObserverFrameEvent -> RenderObserverFrame(baseNote)
            is PairingEvent -> RenderAgentPairing(baseNote)
            is PushLeaseEvent -> RenderPushLease(baseNote)

            else -> {
                if (noteEvent != null && isUnknownKind(noteEvent)) {
                    // A kind quartz has no class for: its content may be JSON or ciphertext, and its
                    // tags carry what it is about, so it gets the fallback card, not the text path.
                    RenderUnknownKind(baseNote, makeItShort, quotesLeft, backgroundColor, accountViewModel, nav)
                } else {
                    ProvideMediaBleed(mediaBleed) {
                        RenderTextEvent(
                            baseNote,
                            makeItShort,
                            canPreview,
                            quotesLeft,
                            unPackReply,
                            backgroundColor,
                            editState,
                            accountViewModel,
                            nav,
                            isBoostedNote = isBoostedNote,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ObserveDraftEvent(
    note: Note,
    accountViewModel: AccountViewModel,
    render: @Composable (Note) -> Unit,
) {
    val noteEvent by observeNoteEvent<DraftWrapEvent>(note, accountViewModel)

    noteEvent?.let {
        val innerNote by produceCachedStateAsync(cache = accountViewModel.draftNoteCache, key = it)

        innerNote?.let {
            render(it)
        }
    }
}

@Composable
fun RenderDraft(
    note: Note,
    quotesLeft: Int,
    unPackReply: ReplyRenderType,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ObserveDraftEvent(note, accountViewModel) {
        val edits = remember { mutableStateOf(GenericLoadable.Empty<EditState>()) }

        RenderNoteRow(
            baseNote = it,
            backgroundColor = backgroundColor,
            makeItShort = false,
            canPreview = true,
            editState = edits,
            quotesLeft = quotesLeft,
            unPackReply = unPackReply,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        val zapSplits = remember(it.event) { it.event?.hasZapSplitSetupBesidesAuthor() }
        if (zapSplits == true) {
            Spacer(modifier = HalfDoubleVertSpacer)
            DisplayZapSplits(it.event!!, false, accountViewModel, nav)
        }
    }
}

@Composable
fun RenderRepost(
    note: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    note.repostedNote()?.let {
        NoteCompose(
            it,
            modifier = Modifier,
            isBoostedNote = true,
            unPackReply = ReplyRenderType.NONE,
            quotesLeft = quotesLeft - 1,
            parentBackgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun getGradient(backgroundColor: MutableState<Color>): Brush {
    val solid =
        if (backgroundColor.value.alpha == 0f) {
            MaterialTheme.colorScheme.background
        } else {
            backgroundColor.value.copy(alpha = 1f)
        }
    return Brush.verticalGradient(
        colors =
            listOf(
                solid.copy(alpha = 0f),
                solid,
            ),
    )
}

@Composable
fun ReplyNoteComposition(
    replyingDirectlyTo: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    NoteCompose(
        baseNote = replyingDirectlyTo,
        modifier = MaterialTheme.colorScheme.replyModifier,
        isQuotedNote = true,
        unPackReply = ReplyRenderType.NONE,
        makeItShort = true,
        quotesLeft = 0,
        parentBackgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Composable
fun SecondUserInfoRow(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    val noteAuthor = note.author ?: return

    Row(
        verticalAlignment = CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
        modifier = UserNameMaxRowHeight,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (noteEvent is IForkableEvent && noteEvent.isAFork()) {
                ShowForkInformation(noteEvent, Modifier, accountViewModel, nav)
            } else {
                ObserveDisplayNip05Status(noteAuthor, accountViewModel, nav)
            }
        }

        val baseReward = remember(noteEvent) { noteEvent.bountyBaseReward()?.let { Reward(it) } }
        if (baseReward != null) {
            DisplayReward(baseReward, note, accountViewModel)
        }
    }
}

@Composable
fun DisplayExpiration(
    expirationDate: Long,
    accountViewModel: AccountViewModel,
) {
    // Beyond a year timeAheadNoDot switches to a full date, which is too wide
    // for the pill: clamp it.
    val text =
        if (expirationDate - TimeUtils.now() > TimeUtils.ONE_YEAR) {
            stringRes(Res.string.one_year_plus)
        } else {
            timeAheadNoDot(expirationDate)
        }
    HeaderPill(
        symbol = MaterialSymbols.Timer,
        text = text,
        contentDescription = stringRes(Res.string.expiration_date_label),
        onClick = {
            accountViewModel.toastManager.toast(
                Res.string.expiration_date_label,
                Res.string.expiration_info_description,
                formatDateTime(expirationDate * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.MEDIUM),
            )
        },
    )
}

@Composable
fun DisplayOtsIfInOriginal(
    note: Note,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
) {
    val editNote = (editState.value as? GenericLoadable.Loaded<EditState>)?.loaded?.modificationToShow?.value

    if (editNote == null) {
        DisplayOts(note = note, accountViewModel = accountViewModel)
    } else {
        DisplayOts(note = editNote, accountViewModel = accountViewModel)
    }
}

@Composable
fun DisplayDraft() {
    QuietMark(text = stringRes(Res.string.draft))
}

@Composable
fun DisplayDraftChat() {
    QuietMark(text = stringRes(Res.string.draft))
}

@Composable
fun FirstUserInfoRow(
    baseNote: Note,
    showAuthorPicture: Boolean,
    isPinned: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
        modifier = UserNameRowHeight,
    ) {
        val isRepost = baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent
        val isDraft = baseNote.isDraft()
        val textColor = if (isRepost) MaterialTheme.colorScheme.grayText else Color.Unspecified

        // Zap receipts are signed by the recipient's lightning provider; show the
        // sender from the embedded zap request instead of the service key.
        val zapSender =
            if (baseNote.event is ZapReceiptEvent) {
                observeZapSender(baseNote, accountViewModel).value
            } else {
                null
            }

        if (zapSender != null) {
            if (showAuthorPicture) {
                UserPicture(zapSender, Size25dp, accountViewModel = accountViewModel, nav = nav)
            }
            UsernameDisplay(zapSender, Modifier.weight(1f), textColor = textColor, accountViewModel = accountViewModel)
        } else if (showAuthorPicture) {
            NoteAuthorPicture(baseNote, Size25dp, accountViewModel = accountViewModel, nav = nav)
            NoteUsernameDisplay(baseNote, Modifier.weight(1f), textColor = textColor, accountViewModel = accountViewModel, nav = nav)
        } else {
            NoteUsernameDisplay(baseNote, Modifier.weight(1f), textColor = textColor, accountViewModel = accountViewModel, nav = nav)
        }

        CheckAndDisplayEditStatus(editState)

        if (isDraft) {
            DisplayDraft()
        }

        if (isDraft) {
            ObserveDraftEvent(baseNote, accountViewModel) { draftNote ->
                val isCommunityPost =
                    remember(draftNote) {
                        draftNote.event?.isACommunityPost() == true
                    }

                if (isCommunityPost) {
                    DisplayFollowingCommunityInPost(draftNote, accountViewModel, nav)
                } else {
                    DisplayFollowingHashtagsInPost(draftNote, accountViewModel, nav)
                }
            }
        } else {
            val isCommunityPost =
                remember(baseNote) {
                    baseNote.event?.isACommunityPost() == true
                }

            if (isCommunityPost) {
                DisplayFollowingCommunityInPost(baseNote, accountViewModel, nav)
            } else {
                DisplayFollowingHashtagsInPost(baseNote, accountViewModel, nav)
            }
        }

        val noteEvent = baseNote.event

        val geo = remember(noteEvent) { noteEvent?.geoHashOrScope() }
        if (geo != null) {
            DisplayLocation(geo, accountViewModel, nav)
        }

        DisplayOtsIfInOriginal(baseNote, editState, accountViewModel)

        val pow = remember(noteEvent) { noteEvent?.strongPoWOrNull() }
        if (pow != null) {
            DisplayPoW(pow, accountViewModel)
        }

        Expiration(baseNote, accountViewModel)

        if (baseNote.isPrivateRumor()) {
            PrivateRumorMark(accountViewModel)
        }

        // A Concord message (e.g. surfaced in Notifications) names its parent community with the same
        // highlighted chip the Messages row uses, tapping through to that community's channel list.
        val concordChannel = remember(baseNote) { baseNote.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel } }
        concordChannel?.communityName?.let { communityName ->
            ConcordCommunityPill(
                communityName = communityName,
                onClick = { nav.nav(Route.ConcordServer(concordChannel.channelId.communityId)) },
            )
        }

        if (isPinned) {
            PinnedMark()
        }

        JumpToParentReplyButton(baseNote, accountViewModel, nav)

        // The dotted TimeAgo carries its own " • " separator and the options
        // button has slack around its icon, so this pair stays unspaced.
        Row(verticalAlignment = CenterVertically) {
            TimeAgo(baseNote, style = TimeAgoStyle.DottedTight)

            if (moreOptions == null) {
                MoreOptionsButton(baseNote, editState, accountViewModel, nav)
            } else {
                moreOptions()
            }
        }
    }
}

@Composable
fun PinnedMark() {
    QuietMark(
        symbol = MaterialSymbols.PushPin,
        contentDescription = stringRes(Res.string.pinned_notes),
    )
}

@Composable
fun PrivateRumorMark(accountViewModel: AccountViewModel) {
    QuietMark(
        symbol = MaterialSymbols.Lock,
        contentDescription = stringRes(Res.string.private_rumor_mark),
        onClick = {
            accountViewModel.toastManager.toast(
                Res.string.private_rumor_info_title,
                Res.string.private_rumor_info_description,
            )
        },
    )
}

@Composable
fun JumpToParentReplyButton(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (!accountViewModel.settings.isCompleteUIMode()) return
    if (baseNote.event !is BaseThreadedEvent) return

    val parentNote =
        remember(baseNote) {
            replyingDirectlyTo(baseNote, LocalCache)?.takeIf { LocalCache.getAnyChannel(it) == null }
        } ?: return

    ClickableBox(
        modifier = Modifier.size(20.dp),
        onClick = {
            nav.nav { routeFor(parentNote, accountViewModel.account) }
        },
    ) {
        Icon(
            symbol = MaterialSymbols.KeyboardArrowUp,
            contentDescription = stringRes(Res.string.jump_to_parent_reply),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
fun Expiration(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event
    if (event != null) {
        val expires = remember(event) { event.expiration() }
        if (expires != null) {
            DisplayExpiration(expires, accountViewModel)
        }
    }
}

@Composable
fun CheckAndDisplayEditStatus(editState: State<GenericLoadable<EditState>>) {
    if (editState.value is GenericLoadable.Loaded) {
        (editState.value as? GenericLoadable.Loaded<EditState>)?.loaded?.let {
            DisplayEditStatus(it)
        }
    }
}

@Composable
fun observeEdits(
    baseNote: Note,
    accountViewModel: AccountViewModel,
): State<GenericLoadable<EditState>> {
    val event = baseNote.event
    // A git issue, patch or PR is edited by cover notes, presented as kind-1 edits are.
    if (event is GitIssueEvent || event is GitPullRequestEvent || event is GitPatchEvent) {
        return observeGitSummaryEdits(baseNote)
    }
    if (event !is TextNoteEvent) {
        return EmptyState
    }

    val editState =
        remember(baseNote.idHex) {
            // Edits are anchored on the note (Note.edits), so the current set is readable synchronously
            // (no cache scan) — start Empty or Loaded, never Loading.
            val cached = baseNote.textNoteModifications()
            mutableStateOf(
                if (cached.isEmpty()) {
                    GenericLoadable.Empty()
                } else {
                    val state = EditState()
                    state.updateModifications(cached)
                    GenericLoadable.Loaded(state)
                },
            )
        }

    // Upstream resolves on IO and `distinctUntilChanged`s, so this LaunchedEffect only
    // fires when the actual modification list changes — no more recomputing + reassigning
    // editState on every unrelated emission of the edits flow.
    val modifications by observeNoteModifications(baseNote, accountViewModel)

    LaunchedEffect(modifications) {
        val mods = modifications ?: return@LaunchedEffect
        if (mods.isEmpty()) {
            if (editState.value !is GenericLoadable.Empty) {
                editState.value = GenericLoadable.Empty()
            }
        } else {
            val current = editState.value
            if (current is GenericLoadable.Loaded) {
                current.loaded.updateModifications(mods)
            } else {
                val state = EditState()
                state.updateModifications(mods)
                editState.value = GenericLoadable.Loaded(state)
            }
        }
    }

    return editState
}

@Composable
fun RenderAuthorImages(
    baseNote: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
    size: Dp = Size55dp,
) {
    val noteEvent = baseNote.event
    if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) {
        val baseRepost = baseNote.replyTo?.lastOrNull()
        if (baseRepost != null) {
            RepostNoteAuthorPicture(baseNote, baseRepost, size, accountViewModel, nav)
        } else {
            NoteAuthorPicture(baseNote, size, accountViewModel = accountViewModel, nav = nav)
        }
    } else if (noteEvent is ZapReceiptEvent) {
        // Zap receipts are signed by the recipient's lightning provider; show the
        // sender from the embedded zap request instead of the service key, matching
        // how the thread's master note resolves the author.
        val zapSender = observeZapSender(baseNote, accountViewModel).value
        if (zapSender != null) {
            UserPicture(zapSender, size, accountViewModel = accountViewModel, nav = nav)
        } else {
            NoteAuthorPicture(baseNote, size, accountViewModel = accountViewModel, nav = nav)
        }
    } else {
        NoteAuthorPicture(baseNote, size, accountViewModel = accountViewModel, nav = nav)
    }

    if (noteEvent is ChannelMessageEvent) {
        val baseChannelHex = noteEvent.channelId()
        if (baseChannelHex != null) {
            LoadPublicChatChannel(baseChannelHex) { channel ->
                ChannelNotePicture(
                    channel,
                    // The badge is 20dp on the 55dp picture; it keeps that share on a smaller one.
                    if (size == Size55dp) Modifier else Modifier.size(size * 20 / 55),
                    accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun ChannelNotePicture(
    baseChannel: PublicChatChannel,
    sizeModifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    val model by observeChannelPicture(baseChannel, accountViewModel)

    RobohashFallbackAsyncImage(
        robot = baseChannel.idHex,
        model = model,
        contentDescription = stringRes(Res.string.group_picture),
        modifier = sizeModifier.then(MaterialTheme.colorScheme.channelNotePictureModifier),
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
    )
}

@Composable
internal fun RepostNoteAuthorPicture(
    baseNote: Note,
    baseRepost: Note,
    size: Dp,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Each picture fills its 35/55 corner of the layout, less a 1dp sliver: 34dp at 55dp.
    val pictureSize = size * 35 / 55 - 1.dp
    GenericRepostLayout(
        baseAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseNote,
                size = pictureSize,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        repostAuthorPicture = {
            NoteAuthorPicture(
                baseNote = baseRepost,
                size = pictureSize,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        size = size,
    )
}
