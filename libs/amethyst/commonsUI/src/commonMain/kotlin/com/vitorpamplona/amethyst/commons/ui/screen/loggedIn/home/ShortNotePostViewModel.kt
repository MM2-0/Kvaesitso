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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.actions.ReplyActions
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.audio.VoiceAnonymizationController
import com.vitorpamplona.amethyst.commons.audio.VoicePreset
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.DraftTagState
import com.vitorpamplona.amethyst.commons.model.composer.IExpiration
import com.vitorpamplona.amethyst.commons.model.composer.IZapRaiser
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.composer.PreviewState
import com.vitorpamplona.amethyst.commons.model.composer.SplitBuilder
import com.vitorpamplona.amethyst.commons.model.composer.messageTags
import com.vitorpamplona.amethyst.commons.model.composer.pTagsWithHints
import com.vitorpamplona.amethyst.commons.model.composer.quoteMessage
import com.vitorpamplona.amethyst.commons.model.composer.toZapSplitSetup
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.upload_error_title
import com.vitorpamplona.amethyst.commons.resources.upload_error_voice_message_exception
import com.vitorpamplona.amethyst.commons.resources.upload_error_voice_message_failed
import com.vitorpamplona.amethyst.commons.resources.voice_anonymization_failed
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPost
import com.vitorpamplona.amethyst.commons.service.ai.WritingAssistant
import com.vitorpamplona.amethyst.commons.service.ai.WritingAssistantStatus
import com.vitorpamplona.amethyst.commons.service.ai.WritingResult
import com.vitorpamplona.amethyst.commons.service.ai.WritingTone
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.IAudience
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerPostOptions
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.text.appendSignature
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.text.setTextAndPlaceCursorAtBeginning
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawingIntoText
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.experimental.zapPolls.closedAt
import com.vitorpamplona.quartz.experimental.zapPolls.consensusThreshold
import com.vitorpamplona.quartz.experimental.zapPolls.maxAmount
import com.vitorpamplona.quartz.experimental.zapPolls.minAmount
import com.vitorpamplona.quartz.experimental.zapPolls.tags.PollOptionTag
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip10Notes.tags.prepareETagsAsReplyTo
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitiveOrNSFW
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.OptionTag
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip92IMeta.IMetaTagBuilder
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.sensitiveContent
import com.vitorpamplona.quartz.nip94FileMetadata.size
import com.vitorpamplona.quartz.nip94FileMetadata.thumbhash
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.nipA0VoiceMessages.BaseVoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceEvent
import com.vitorpamplona.quartz.nipA0VoiceMessages.VoiceReplyEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okio.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class UserSuggestionAnchor {
    MAIN_MESSAGE,
    FORWARD_ZAPS,
    POLL_OPTION,
    TO_USERS,
    NOTIFY,
}

/** Below this many characters a draft is too short for a rewrite to say anything useful. */
private const val AI_MIN_TEXT_LENGTH = 20

/** How long typing must pause before spending the device's model on the draft. */
private const val AI_DEBOUNCE_MS = 1000L

/** Model status crosses a process boundary, so it is only re-read this often. */
private const val AI_STATUS_RECHECK_SECONDS = 30

/** A poll needs two answers to choose between. */
private const val MIN_POLL_OPTIONS = 2

/** A new poll stays open for a day after it is sent. */
private val DEFAULT_POLL_DEADLINE = PollDeadline.After(TimeUtils.ONE_DAY.toLong())

@Stable
open class ShortNotePostViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IComposerMediaTools,
    IComposerPostOptions,
    IZapField,
    IZapRaiser,
    IExpiration,
    IAudience {
    val draftTag = DraftTagState()

    // Strong reference to the live cache note for the current draft tag (derived from the
    // versions flow below), so LocalCache cannot weakly collect it before a deletion needs it.
    var draftNote: AddressableNote? = null
        private set

    override lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftTag.versions.collectLatest {
                // don't save the first
                if (it > 0) {
                    val tag = draftTag.current
                    draftNote = account.getOrCreateDraftNote(tag)
                    accountViewModel.launchSigner {
                        // Post rotates the tag and then clears the composer. A save still queued from
                        // before that would see the empty text and delete the draft Post just saved
                        // for the post that is still mining, so it's skipped once the tag has moved on.
                        if (draftTag.current == tag) sendDraftSync()
                    }
                }
            }
        }
    }

    var originalNote: Note? by mutableStateOf(null)
    var forkedFromNote: Note? by mutableStateOf(null)

    // The signature pre-filled by applySignature(), so an untouched signature-only
    // message is treated as blank instead of auto-saved as a junk draft.
    private var appliedSignature: String? = null

    var pTags by mutableStateOf<List<User>?>(null)
    var eTags by mutableStateOf<List<Note>?>(null)

    // IAudience maps onto pTags rather than replacing it: the field is read all
    // over createTemplate and the draft loaders under that name.
    override var audienceMembers: List<User>?
        get() = pTags
        set(value) {
            pTags = value
        }

    override fun onAudienceChanged() = draftTag.newVersion()

    val iMetaAttachments = IMetaAttachments()

    override val message = TextFieldState()

    val urlPreviews = PreviewState()

    val mediaUploadTracker = MediaUploadTracker()
    override val isUploadingImage: Boolean get() = mediaUploadTracker.isUploadingImage
    override val isUploadingFile: Boolean get() = mediaUploadTracker.isUploadingFile

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    // Voice Messages
    var voiceRecording by mutableStateOf<RecordingResult?>(null)
    var voiceLocalFile by mutableStateOf<Path?>(null)
    var isUploadingVoice by mutableStateOf(false)
    var voiceMetadata by mutableStateOf<AudioMeta?>(null)
    var voiceSelectedServer by mutableStateOf<ServerName?>(null)
    var voiceOrchestrator by mutableStateOf<UploadOrchestrator?>(null)

    // Voice Anonymization
    private val voiceAnonymization =
        VoiceAnonymizationController(
            scope = viewModelScope,
            logTag = "ShortNotePostViewModel",
            onError = { error ->
                val message = error.message
                if (message != null) {
                    accountViewModel.toastManager.toast(Res.string.error, message, error)
                } else {
                    accountViewModel.toastManager.toast(Res.string.error, Res.string.voice_anonymization_failed, error)
                }
            },
            anonymize = { file, preset -> accountViewModel.host.anonymizeVoice(file, preset.name) },
        )

    val activeFile: Path?
        get() = voiceAnonymization.activeFile(voiceLocalFile)

    val activeWaveform: List<Float>?
        get() = voiceAnonymization.activeWaveform(voiceRecording?.amplitudes)

    val selectedPreset: VoicePreset
        get() = voiceAnonymization.selectedPreset

    val processingPreset: VoicePreset?
        get() = voiceAnonymization.processingPreset

    // Polls
    // Both regular polls and zap polls share the same `pollOptions` text fields so that switching
    // between the two poll types never hides or discards the text the user already typed.
    var canUsePoll by mutableStateOf(false)
    var wantsPoll by mutableStateOf(false)
    var pollOptions: SnapshotStateList<PollOptionDraft> = newPollOptions()

    // The option last typed in or focused: the `@` and `:` suggestions and the emoji button write
    // into it. Null once the message field takes focus back.
    var activePollOption by mutableStateOf<PollOptionDraft?>(null)
    var pollType by mutableStateOf(PollType.SINGLE_CHOICE)

    // When voting closes, for both kinds of poll.
    var pollDeadline by mutableStateOf<PollDeadline>(DEFAULT_POLL_DEADLINE)

    // ZapPolls
    var canUseZapPoll by mutableStateOf(false)
    var wantsZapPoll by mutableStateOf(false)
    var zapPollValueMaximum by mutableStateOf<Long?>(null)
    var zapPollValueMinimum by mutableStateOf<Long?>(null)
    var zapPollConsensusThreshold: Int? = null

    var isValidValueMaximum = mutableStateOf(true)
    var isValidValueMinimum = mutableStateOf(true)
    var isValidConsensusThreshold = mutableStateOf(true)
    var isValidClosedAt = mutableStateOf(true)

    // Invoices
    override var canAddInvoice by mutableStateOf(false)
    override var wantsInvoice by mutableStateOf(false)

    override var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    override var wantsForwardZapTo by mutableStateOf(false)
    override var forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    override val forwardZapToEditting = TextFieldState()

    // Optional subject line: a NIP-14 `subject` tag on a kind-1 note, or the `title` of a NIP-29
    // kind-11 group thread. Toggled from the bottom row; auto-on for group threads.
    var wantsSubject by mutableStateOf(false)
    val subject = TextFieldState()

    // NSFW, Sensitive
    override var wantsToMarkAsSensitive by mutableStateOf(false)
    var contentWarningDescription by mutableStateOf("")

    // Expiration Date (NIP-40)
    override var wantsExpirationDate by mutableStateOf(false)
    override var expirationDate by mutableLongStateOf(TimeUtils.oneDayAhead())

    // GeoHash
    override var wantsToAddGeoHash by mutableStateOf(false)
    var location: StateFlow<LocationResult>? = null

    override var pickedGeoHash by mutableStateOf<String?>(null)
    var wantsExclusiveGeoPost by mutableStateOf(false)

    // ZapRaiser
    override var canAddZapRaiser by mutableStateOf(false)
    override var wantsZapRaiser by mutableStateOf(false)
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    // Anonymous Reply
    var wantsAnonymousPost by mutableStateOf(false)

    // Private (gift-wrapped) note: instead of publishing, the kind-1 is
    // wrapped to every p-tagged user plus a self-copy and sent to their DM
    // relays. Locked ON when replying to an unsealed rumor — a public reply
    // would e-tag the parent's private id onto public relays.
    var wantsPrivateNote by mutableStateOf(false)
    var privateNoteLocked by mutableStateOf(false)

    fun togglePrivateNote() {
        if (privateNoteLocked) return
        wantsPrivateNote = !wantsPrivateNote
    }

    // Notify / Visible-to editor: lets the user p-tag people who aren't
    // cited in the message. For private notes the Notify list IS the
    // audience, so this is how receivers are picked.
    override val audienceSearchText = TextFieldState()
    override var wantsToManageAudience by mutableStateOf(false)
    override var notifyProvenance by mutableStateOf<Map<HexKey, Set<String>>>(emptyMap())

    fun onAudienceSearchTextChanged() {
        if (audienceSearchText.selection.collapsed) {
            val lastWord = audienceSearchText.text.toString()
            userSuggestionsMainMessage = UserSuggestionAnchor.NOTIFY
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    // Members of pTags whose bell is off: they keep their chip in the Notify
    // list (so they are one tap away from being added back) but are dropped
    // from the outgoing event's p tags.
    override var mutedNotifies by mutableStateOf<Set<HexKey>>(emptySet())

    // A single ephemeral signer reused for the whole compose session so that media
    // uploads (Blossom/NIP-96 auth events) and the final anonymous post are all signed
    // by the same throwaway key, instead of leaking the real account's pubkey into the
    // upload authorization (and therefore into the returned media URL).
    private var anonymousSignerCache: NostrSigner? = null

    fun anonymousSigner(): NostrSigner = anonymousSignerCache ?: NostrSignerInternal(KeyPair()).also { anonymousSignerCache = it }

    // Scheduled posting: epoch seconds (UTC) when the post should be published.
    // Null = post immediately on Send (existing behavior).
    var scheduledForSec by mutableStateOf<Long?>(null)

    // NIP-13 per-post override from the composer chip: null = follow account
    // settings, 0 = don't mine this post, >0 = mine at that difficulty.
    var powOverride by mutableStateOf<Int?>(null)

    // Best guess of the kind createTemplate() will produce, for the PoW chip.
    // A private note is gift-wrapped, so what gets mined (and what the
    // settings gate on) is the kind-1059 wrap, not the inner kind-1.
    private fun anticipatedPowKind(): Int =
        when {
            wantsPrivateNote -> GiftWrapEvent.KIND
            wantsPoll -> PollEvent.KIND
            wantsZapPoll -> ZapPollEvent.KIND
            voiceRecording != null -> VoiceEvent.KIND
            else -> TextNoteEvent.KIND
        }

    fun effectivePowDifficulty(): Int? {
        if (!::accountViewModel.isInitialized) return null
        return accountViewModel.account.powDifficultyFor(anticipatedPowKind(), powOverride)
    }

    fun defaultPowDifficulty(): Int? {
        if (!::accountViewModel.isInitialized) return null
        return accountViewModel.account.powDifficultyFor(anticipatedPowKind())
    }

    // --- AI Writing Help -----------------------------------------------------------------

    var aiResults by mutableStateOf<ImmutableMap<WritingTone, WritingResult>>(persistentMapOf())
    var aiSelectedResult by mutableStateOf<WritingResult?>(null)
    var aiStatus by mutableStateOf<WritingAssistantStatus>(WritingAssistantStatus.Unavailable)
    private var writingAssistant: WritingAssistant? = null
    private var aiComputeJob: Job? = null
    private var aiStatusJob: Job? = null
    private var lastComputedText: String = ""
    private var lastStatusCheckAt: Long = 0

    /** True once a batch is past its debounce and its inferences have reached the model. */
    private var aiInferenceRunning = false

    /** Newest draft text seen while a batch was running, picked up when that batch ends. */
    private var aiPendingText: String? = null

    /**
     * Whether there is anything to show. The user's preference is watched by the screen so
     * that turning the setting off hides the panel right away.
     */
    val showAiPanel: Boolean
        get() = aiStatus is WritingAssistantStatus.Available && aiResults.isNotEmpty()

    private fun isAiEnabledInSettings(): Boolean =
        accountViewModel.settings.uiSettingsFlow.automaticallyProposeAiImprovements.value ==
            BooleanType.ALWAYS

    fun initWritingAssistant() {
        if (writingAssistant != null) return
        writingAssistant = accountViewModel.host.createWritingAssistant() ?: return
        refreshAiStatus()
    }

    /**
     * Re-reads the model status, and asks for the download when the device can run the model
     * but has not fetched it yet. Runs outside [aiComputeJob] so a keystroke cannot cancel a
     * download halfway, and is throttled because it crosses into another process.
     */
    private fun refreshAiStatus() {
        val assistant = writingAssistant ?: return
        if (aiStatusJob?.isActive == true) return

        val now = TimeUtils.now()
        if (lastStatusCheckAt > 0 && now - lastStatusCheckAt < AI_STATUS_RECHECK_SECONDS) return
        lastStatusCheckAt = now

        // Stays on the ViewModel's main dispatcher so every AI field is written from one
        // thread; the assistant moves its own blocking work to IO.
        aiStatusJob =
            viewModelScope.launch {
                val status = assistant.checkAvailability()
                aiStatus =
                    if (status is WritingAssistantStatus.Downloadable && isAiEnabledInSettings()) {
                        assistant.requestDownload()
                    } else {
                        status
                    }

                // The model may have become usable while the draft sat untouched; the next
                // keystroke should not be what finally starts the proposals.
                if (aiStatus is WritingAssistantStatus.Available) precomputeAiResults()
            }
    }

    fun precomputeAiResults() {
        if (!::accountViewModel.isInitialized) return

        val text = message.text.toString().trim()
        if (text.length < AI_MIN_TEXT_LENGTH) {
            // Covers the composer being emptied or reset: the old proposals no longer
            // describe what is in the field, so they must not stay on screen.
            resetAiState()
            return
        }
        if (text == lastComputedText) return

        if (writingAssistant == null) return
        if (!isAiEnabledInSettings()) return

        if (aiStatus !is WritingAssistantStatus.Available) {
            refreshAiStatus()
            return
        }

        // An inference that has reached the model cannot be recalled — cancelling its future
        // is what used to crash the app, so the bridge detaches instead (see GenAiFutures).
        // Abandoning a running batch would therefore leave seven rewrites burning on-device
        // compute for text the user has already moved past, and every later keystroke would
        // stack seven more on top. So only the debounce window is cancellable: once a batch
        // is under way it is left to finish, and the newest text is picked up when it ends.
        if (aiInferenceRunning) {
            aiPendingText = text
            return
        }

        aiComputeJob?.cancel()
        aiResults = persistentMapOf()
        aiSelectedResult = null

        aiComputeJob =
            viewModelScope.launch {
                delay(AI_DEBOUNCE_MS)

                aiInferenceRunning = true
                try {
                    runAiBatch(text)
                } finally {
                    aiInferenceRunning = false
                }

                // The draft moved on while this batch ran: start the next one now.
                val pending = aiPendingText
                aiPendingText = null
                if (pending != null && pending != text) precomputeAiResults()
            }
    }

    private suspend fun runAiBatch(text: String) {
        val assistant = writingAssistant ?: return

        val results =
            coroutineScope {
                WritingTone.entries
                    .map { tone ->
                        async {
                            try {
                                assistant.transform(text, tone)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w("ShortNotePostViewModel", "Could not run the $tone rewrite", e)
                                null
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
                    // A model that hands back the input unchanged has nothing to offer.
                    .filter { it.transformedText.isNotBlank() && it.transformedText != it.originalText }
                    .associateBy { it.tone }
                    .toImmutableMap()
            }

        aiResults = results
        // Only now: a run that was cancelled must not mark this text as done, or
        // coming back to it would show nothing.
        lastComputedText = text
    }

    fun selectAiResult(tone: WritingTone) {
        aiSelectedResult = aiResults[tone]
    }

    fun applyAiResult() {
        aiSelectedResult?.let {
            val applied = it.transformedText
            message.setTextAndPlaceCursorAtEnd(applied)
            aiSelectedResult = null
            aiResults = persistentMapOf()
            // The edit above re-enters onMessageChanged. Remember the applied text so it
            // does not immediately trigger a fresh batch of inferences over it.
            lastComputedText = applied.trim()
            draftTag.newVersion()
        }
    }

    fun dismissAiResult() {
        aiSelectedResult = null
    }

    private fun resetAiState() {
        aiComputeJob?.cancel()
        aiComputeJob = null
        aiPendingText = null
        aiResults = persistentMapOf()
        aiSelectedResult = null
        lastComputedText = ""
    }

    fun lnAddress(): String? = account.userProfile().lnAddress()

    override fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

    fun user(): User = account.userProfile()

    open fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions = UserSuggestionState(accountVM.account, accountVM.nip05ClientBuilder())

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account.emoji)
    }

    /**
     * When set, this composer produces a NIP-29 kind-11 group thread instead of a kind-1 note:
     * the title is the first line, the rest is the body, and it publishes ONLY to the group's host
     * [relays] (relay29 authorizes writes by the `h` tag). Toggles that don't apply to a group thread
     * (poll, private note, scheduling) are hidden while this is set.
     */
    class GroupThreadTarget(
        val groupId: HexKey,
        val relays: List<NormalizedRelayUrl>,
    )

    var groupThreadTarget: GroupThreadTarget? = null
        private set

    fun setGroupThread(
        groupId: HexKey?,
        relayUrl: String?,
    ) {
        val relay = relayUrl?.let { RelayUrlNormalizer.normalizeOrNull(it) }
        groupThreadTarget = if (groupId != null && relay != null) GroupThreadTarget(groupId, listOf(relay)) else null
        // A thread wants a title, so surface the field by default when composing one.
        if (groupThreadTarget != null) wantsSubject = true
    }

    open fun load(
        replyingTo: Note?,
        quote: Note?,
        fork: Note?,
        version: Note?,
        draft: Note?,
    ) {
        val noteEvent = draft?.event
        val noteAuthor = draft?.author

        if (draft != null && noteEvent is DraftWrapEvent && noteAuthor != null) {
            viewModelScope.launch(Dispatchers.IO) {
                accountViewModel.createTempDraftNote(noteEvent)?.let { innerNote ->
                    val oldTag = (draft.event as? AddressableEvent)?.dTag()
                    if (oldTag != null) {
                        draftTag.set(oldTag)
                        draftNote = account.getOrCreateDraftNote(oldTag)
                    }
                    onUiThread { loadFromDraft(innerNote) }
                }
            }
        } else {
            originalNote = replyingTo
            privateNoteLocked = replyingTo?.isPrivateRumor() == true
            wantsPrivateNote = privateNoteLocked
            mutedNotifies = emptySet()
            notifyProvenance = emptyMap()
            replyingTo?.let { replyNote ->
                if (replyNote.event is BaseThreadedEvent) {
                    this.eTags = (replyNote.replyTo ?: emptyList()).plus(replyNote)
                } else {
                    this.eTags = listOf(replyNote)
                }

                if (replyNote.event !is CommunityDefinitionEvent) {
                    this.pTags = threadMembers(replyNote, this.eTags)
                }
            }
                ?: run {
                    eTags = null
                    pTags = null
                }

            val user = account.userProfile()

            canAddInvoice = user.lnAddress() != null
            canAddZapRaiser = user.lnAddress() != null
            canUsePoll = originalNote == null
            canUseZapPoll = originalNote == null
            multiOrchestrator = null

            quote?.let { quotedNote ->
                message.setTextAndPlaceCursorAtBeginning(quoteMessage(message.text.toString(), quotedNote))

                quotedNote.author?.let { quotedUser ->
                    if (quotedUser.pubkeyHex != user.pubkeyHex) {
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == quotedUser.pubkeyHex }) {
                            forwardZapTo.value.addItem(quotedUser)
                        }
                        if (forwardZapTo.value.items.none { it.key.pubkeyHex == user.pubkeyHex }) {
                            forwardZapTo.value.addItem(user)
                        }

                        val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == quotedUser.pubkeyHex }
                        forwardZapTo.value.updatePercentage(pos, 0.9f)
                    }
                }
            }

            fork?.let { forkedNoted ->
                message.setTextAndPlaceCursorAtEnd(version?.event?.content ?: forkedNoted.event?.content ?: "")

                forkedNoted.event?.isSensitiveOrNSFW()?.let {
                    if (it) wantsToMarkAsSensitive = true
                }

                forkedNoted.event?.zapraiserAmount()?.let {
                    zapRaiserAmount.value = it
                }

                forkedNoted.event?.zapSplitSetup()?.let { setup ->
                    val totalWeight = setup.sumOf { if (it is ZapSplitSetupLnAddress) 0.0 else it.weight }

                    setup.forEach {
                        if (it is ZapSplitSetup) {
                            forwardZapTo.value.addItem(LocalCache.getOrCreateUser(it.pubKeyHex), (it.weight / totalWeight).toFloat())
                        }
                    }
                }

                // Only adds if it is not already set up.
                if (forwardZapTo.value.items.isEmpty()) {
                    forkedNoted.author?.let { forkedAuthor ->
                        if (forkedAuthor.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == forkedAuthor.pubkeyHex }) forwardZapTo.value.addItem(forkedAuthor)
                            if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) forwardZapTo.value.addItem(accountViewModel.userProfile())

                            val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == forkedAuthor.pubkeyHex }
                            forwardZapTo.value.updatePercentage(pos, 0.8f)
                        }
                    }
                }

                forkedNoted.author?.let {
                    if (this.pTags == null) {
                        this.pTags = listOf(it)
                    } else if (this.pTags?.contains(it) != true) {
                        this.pTags = listOf(it) + (this.pTags ?: emptyList())
                    }
                }

                forkedFromNote = forkedNoted
            } ?: run {
                forkedFromNote = null
            }

            if (!forwardZapTo.value.items.isEmpty()) {
                wantsForwardZapTo = true
            }
        }

        urlPreviews.update(message.text.toString())
    }

    /**
     * Pre-fills the signature from Compose Settings at the end of the message. Skipped by
     * callers when loading a draft or forking (the existing content already carries — or
     * deliberately omits — a signature).
     */
    fun applySignature() {
        val signature = accountViewModel.composeSignature()
        if (signature.isEmpty()) return

        appliedSignature = signature
        message.appendSignature(signature)
        urlPreviews.update(message.text.toString())
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return
        if (draftEvent is TextNoteEvent) {
            loadFromDraft(draftEvent)
        }

        if (draftEvent is PollEvent) {
            loadFromDraft(draftEvent)
        }

        if (draftEvent is ZapPollEvent) {
            loadFromDraft(draftEvent)
        }
    }

    // Everyone taking part in the thread gets a Notify chip: whoever the parent
    // already p-tags, plus the author of every note in the reply chain. Members
    // the user doesn't want to ping are muted via their chip's bell, not removed.
    private fun threadMembers(
        replyNote: Note,
        threadNotes: List<Note>?,
    ): List<User> {
        val mentions =
            (replyNote.event as? TextNoteEvent)
                ?.mentions()
                ?.map { LocalCache.getOrCreateUser(it.pubKey) }
                ?: emptyList()
        val authors = threadNotes?.mapNotNull { it.author } ?: emptyList()
        return (mentions + authors + listOfNotNull(replyNote.author)).distinct()
    }

    private fun loadFromDraft(draftEvent: TextNoteEvent) {
        canAddInvoice = accountViewModel.userProfile().lnAddress() != null
        canAddZapRaiser = accountViewModel.userProfile().lnAddress() != null
        multiOrchestrator = null

        val localForwardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.clearText()
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()
        contentWarningDescription = draftEvent.contentWarningReason() ?: ""

        val draftExpiration = draftEvent.tags.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        pickedGeoHash = geohash
        if (geohash != null) {
            wantsExclusiveGeoPost = draftEvent.kind == CommentEvent.KIND
        }

        val zapRaiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapRaiser != null
        zapRaiserAmount.value = null
        if (zapRaiser != null) {
            zapRaiserAmount.value = zapRaiser
        }

        eTags = draftEvent.threadReferenceIds().mapNotNull { LocalCache.checkGetOrCreateNote(it) }

        pTags =
            draftEvent
                .mentions()
                .mapNotNull { LocalCache.checkGetOrCreateUser(it.pubKey) }
                // A built event can legitimately repeat a p tag (the voice-reply
                // branch notifies the parent author on top of the notify list), so
                // the audience it round-trips through a draft has to be deduped.
                .distinct()

        // Same precedence as ShowForkInformation: a forked address wins over a forked version.
        val forkedAddress = draftEvent.forkFromAddress()
        val forkedVersion = draftEvent.forkFromVersion()
        if (forkedAddress != null) {
            forkedFromNote = LocalCache.getOrCreateAddressableNote(forkedAddress)
        } else if (forkedVersion != null) {
            forkedFromNote = LocalCache.checkGetOrCreateNote(forkedVersion)
        }

        // The reply-marked e/a tag, else the root-marked one (a direct reply to a root carries
        // only the root marker). Markers only: an unmarked `a` (a community, a mention) must not
        // turn a top-level draft into a reply, so replyingToAddressOrEvent()'s positional
        // fallback is not used here.
        originalNote =
            draftEvent.tags
                .firstOrNull { it.size > 3 && (it[0] == "e" || it[0] == "a") && it[3] == "reply" }
                ?.let { LocalCache.checkGetOrCreateNote(it[1]) }
                ?: draftEvent.tags
                    .firstOrNull { it.size > 3 && (it[0] == "e" || it[0] == "a") && it[3] == "root" }
                    ?.let { LocalCache.checkGetOrCreateNote(it[1]) }

        canUsePoll = originalNote == null
        canUseZapPoll = originalNote == null

        // A drafted private reply must come back locked private: the parent
        // rumor's id is inside the draft's e-tags, and posting it publicly
        // would leak that id.
        privateNoteLocked = originalNote?.isPrivateRumor() == true
        wantsPrivateNote = privateNoteLocked

        // A muted thread member is simply absent from the draft's p tags, so
        // rebuild the full chip list and mark whoever the draft dropped as muted.
        val draftNotifies = pTags.orEmpty().map { it.pubkeyHex }.toSet()
        val members =
            originalNote
                ?.takeIf { it.event !is CommunityDefinitionEvent }
                ?.let { threadMembers(it, eTags) }
                .orEmpty()
        mutedNotifies = members.mapNotNullTo(mutableSetOf()) { member -> member.pubkeyHex.takeIf { it !in draftNotifies } }
        if (members.isNotEmpty()) {
            pTags = (pTags.orEmpty() + members).distinct()
        }

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        wantsPoll = false
        wantsZapPoll = false

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message.text.toString())
    }

    private fun loadFromDraft(draftEvent: PollEvent) {
        canAddInvoice = accountViewModel.userProfile().lnAddress() != null
        canAddZapRaiser = accountViewModel.userProfile().lnAddress() != null
        multiOrchestrator = null

        val localForwardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.clearText()
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()
        contentWarningDescription = draftEvent.contentWarningReason() ?: ""

        val draftExpiration = draftEvent.tags.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        pickedGeoHash = geohash
        if (geohash != null) {
            wantsExclusiveGeoPost = draftEvent.kind == CommentEvent.KIND
        }

        val zapRaiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapRaiser != null
        zapRaiserAmount.value = null
        if (zapRaiser != null) {
            zapRaiserAmount.value = zapRaiser
        }

        eTags =
            draftEvent.tags.filter { it.size > 1 && (it[0] == "e" || it[0] == "a") && it.getOrNull(3) != "fork" }.mapNotNull {
                val note = LocalCache.checkGetOrCreateNote(it[1])
                note
            }

        pTags =
            draftEvent
                .mentionKeys()
                .mapNotNull { LocalCache.checkGetOrCreateUser(it) }
                // A built event can legitimately repeat a p tag (the voice-reply
                // branch notifies the parent author on top of the notify list), so
                // the audience it round-trips through a draft has to be deduped.
                .distinct()
        mutedNotifies = emptySet()
        notifyProvenance = emptyMap()

        canUsePoll = originalNote == null
        canUseZapPoll = originalNote == null

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        wantsZapPoll = false

        val polls = draftEvent.options()
        wantsPoll = polls.isNotEmpty()

        pollOptions.clear()
        polls.mapTo(pollOptions) { PollOptionDraft(it.code, it.label) }
        while (pollOptions.size < MIN_POLL_OPTIONS) pollOptions.add(PollOptionDraft())
        keepTrailingPollOption()

        pollType = draftEvent.pollType()
        pollDeadline = draftEvent.endsAt()?.let { PollDeadline.At(it) } ?: PollDeadline.Never

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message.text.toString())
    }

    private fun loadFromDraft(draftEvent: ZapPollEvent) {
        canAddInvoice = accountViewModel.userProfile().lnAddress() != null
        canAddZapRaiser = accountViewModel.userProfile().lnAddress() != null
        multiOrchestrator = null

        val localForwardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.clearText()
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()
        contentWarningDescription = draftEvent.contentWarningReason() ?: ""

        val draftExpiration = draftEvent.tags.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        pickedGeoHash = geohash
        if (geohash != null) {
            wantsExclusiveGeoPost = draftEvent.kind == CommentEvent.KIND
        }

        val zapRaiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapRaiser != null
        zapRaiserAmount.value = null
        if (zapRaiser != null) {
            zapRaiserAmount.value = zapRaiser
        }

        eTags = draftEvent.threadReferenceIds().mapNotNull { LocalCache.checkGetOrCreateNote(it) }

        pTags =
            draftEvent
                .mentions()
                .mapNotNull { LocalCache.checkGetOrCreateUser(it.pubKey) }
                // A built event can legitimately repeat a p tag (the voice-reply
                // branch notifies the parent author on top of the notify list), so
                // the audience it round-trips through a draft has to be deduped.
                .distinct()
        mutedNotifies = emptySet()
        notifyProvenance = emptyMap()

        canUsePoll = originalNote == null
        canUseZapPoll = originalNote == null

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        wantsPoll = false

        val polls = draftEvent.pollOptionsArray()
        wantsZapPoll = polls.isNotEmpty()

        pollOptions.clear()
        polls.sortedBy { it.index }.mapTo(pollOptions) { PollOptionDraft(text = it.descriptor) }
        while (pollOptions.size < MIN_POLL_OPTIONS) pollOptions.add(PollOptionDraft())
        keepTrailingPollOption()

        zapPollValueMinimum = draftEvent.minAmount()
        zapPollValueMaximum = draftEvent.maxAmount()
        zapPollConsensusThreshold = draftEvent.consensusThreshold()?.let { (it * 100).toInt() }
        pollDeadline = draftEvent.closedAt()?.let { PollDeadline.At(it) } ?: PollDeadline.Never

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message.text.toString())
    }

    suspend fun sendPostSync() {
        // Upload voice message first if it hasn't been uploaded yet
        if (voiceRecording != null && voiceMetadata == null) {
            val serverToUse = voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer
            uploadVoiceMessageSync(
                serverToUse,
            ) { _, _ -> } // Error handling is done by checking voiceMetadata below

            // Abort if upload failed - don't post without voice data
            if (voiceMetadata == null) {
                Log.w("ShortNotePostViewModel", "Voice upload failed, aborting post")
                deleteVoiceLocalFile()
                voiceAnonymization.deleteDistortedFiles()
                return
            }
            // Update default server if voice message was successfully uploaded
            voiceSelectedServer?.let {
                account.settings.changeDefaultFileServer(it)
            }
        }

        val template = createTemplate() ?: return

        // captured before cancel() resets the chip
        val chosenPow = powOverride

        // A mined post leaves the phone minutes after this returns — much later if the
        // app is backgrounded and frozen — and the draft is its only user-visible copy
        // until then. The auto-save is debounced and cancel() below drops the pending
        // save, so the last second of typing would never reach it: flush it now.
        // A private note mines its gift wraps, not the kind-1 itself.
        val minedKind = if (wantsPrivateNote && template.kind == TextNoteEvent.KIND) GiftWrapEvent.KIND else template.kind
        if (accountViewModel.settings.automaticallyCreateDrafts() && accountViewModel.account.powDifficultyFor(minedKind, chosenPow) != null) {
            draftNote = account.getOrCreateDraftNote(draftTag.current)
            // the same template sendDraftSync() would rebuild; reuse it.
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template)
        }

        val draftToDelete = draftNote
        val anonymous = wantsAnonymousPost
        val scheduledFor = scheduledForSec
        val privately = wantsPrivateNote
        val threadTarget = groupThreadTarget
        onUiThread { cancel() }

        // Draft deletion lives INSIDE each publish continuation: when the post
        // is mined first, the draft must survive until the mined event is
        // actually signed and dispatched — a cancelled or process-killed
        // mining job would otherwise have destroyed the only copy of the text.

        if (threadTarget != null) {
            // NIP-29 group thread: publish only to the group's host relay, never the account's
            // outbox — bypass the private/scheduled/anonymous paths entirely.
            accountViewModel.account.sendMined(template, PoWReplay.ToRelays(threadTarget.relays), chosenPow) { readyTemplate ->
                accountViewModel.account.signAndSendPrivatelyOrBroadcast(readyTemplate) { threadTarget.relays }
                accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            }
            return
        }

        if (privately && template.kind == TextNoteEvent.KIND) {
            // Gift-wrap to the p-tagged users instead of publishing. Private
            // wins over the anonymous and scheduled modes: a locked private
            // reply must never fall through to a public publish path (the UI
            // hides those toggles while private mode is on). The inner note and
            // seals are signed inline; only wrap mining is queued — the content
            // is committed (and checkpointed) by the time this returns. The draft
            // goes only once the wraps are sent, like the public paths.
            @Suppress("UNCHECKED_CAST")
            accountViewModel.account.sendPrivateNote(template as EventTemplate<TextNoteEvent>, chosenPow) {
                accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            }
            return
        }

        if (scheduledFor != null && !anonymous) {
            // Re-stamp the template with created_at = scheduled time so the post,
            // when published later, shows up at its scheduled moment in feeds
            // rather than as N minutes/hours old (= compose time). Mining
            // commits the future created_at into the hashed id, and the worker
            // publishes the stored signed JSON verbatim, so the nonce is still
            // valid at publish time.
            val rescheduledTemplate =
                EventTemplate<Event>(
                    createdAt = scheduledFor,
                    kind = template.kind,
                    tags = template.tags,
                    content = template.content,
                )

            accountViewModel.account.sendMined(
                rescheduledTemplate,
                PoWReplay.Schedule(scheduledFor),
                chosenPow,
            ) { readyTemplate ->
                storeScheduledPost(readyTemplate, scheduledFor)
                accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            }
            return
        }

        if (anonymous) {
            // The anonymous key signs without a client tag, so the template is
            // mined as-is against the throwaway pubkey — and never checkpointed
            // to disk, so the key and content can't outlive the process.
            val anonSigner = anonymousSigner()
            val powDifficulty = accountViewModel.account.powDifficultyFor(template.kind, chosenPow)
            val enqueued =
                powDifficulty != null &&
                    accountViewModel.account.mineInBackground(template.kind, powDifficulty) { isActive ->
                        // the clock keeps created_at current for the whole run
                        // (NIP-13 recommendation), starting from the moment a worker
                        // picks the job up rather than when it was queued.
                        val mined = PoWMiner.mine(template, anonSigner.pubKey, powDifficulty, accountViewModel.account.powMinerWorkers(), isActive, TimeUtils::now)
                        accountViewModel.account.signAnonymouslyAndBroadcast(mined, anonymousSigner = anonSigner)
                        accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
                    }
            if (!enqueued) {
                accountViewModel.account.signAnonymouslyAndBroadcast(template, anonymousSigner = anonSigner)
                accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            }
            return
        }

        accountViewModel.account.sendMined(template, PoWReplay.Broadcast(), chosenPow) { readyTemplate ->
            broadcastPublicPost(readyTemplate)
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
        }
    }

    /**
     * Signs the (possibly mined) re-stamped template and parks it in the
     * scheduled-post store for the worker to publish at its created_at time.
     * Runs on the mining queue's scope when PoW is on, so it must not touch
     * viewModelScope.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun storeScheduledPost(
        template: EventTemplate<out Event>,
        publishAtSec: Long,
    ) {
        val (event, relays, extras) = accountViewModel.account.createPostEvent(template)
        val store = accountViewModel.host.scheduledPostStore ?: return
        store.add(
            ScheduledPost(
                id = Uuid.random().toString(),
                accountPubkey = event.pubKey,
                signedEventJson = event.toJson(),
                relayUrls = relays.map { it.url },
                extraEventsJson = extras.map { it.toJson() },
                publishAtSec = publishAtSec,
                createdAtSec = TimeUtils.now(),
            ),
        )
    }

    /**
     * The publish step shared by the direct and post-mining paths: signs the
     * template and dispatches the broadcast. Tracked broadcasting is launched
     * fire-and-forget on the account scope — the composer must not wait for
     * relay acks before navigating away, and a mined job must not hold its
     * queue entry while acks trickle in (the event is signed and dispatched;
     * only progress reporting remains). Runs on the mining queue's scope when
     * PoW is on, so it must not touch viewModelScope.
     */
    private suspend fun broadcastPublicPost(template: EventTemplate<out Event>) {
        if (accountViewModel.settings.useTrackedBroadcasts()) {
            val (event, relays, extras) = accountViewModel.account.createPostEvent(template)
            accountViewModel.account.scope.launch {
                accountViewModel.broadcastTracker.trackBroadcast(
                    event = event,
                    relays = relays,
                    client = accountViewModel.account.client,
                )
                accountViewModel.account.consumePostEvent(event, relays, extras)
            }
        } else {
            accountViewModel.account.signAndComputeBroadcast(template)
        }
    }

    suspend fun sendDraftSync() {
        val text = message.text.toString()
        if (text.isBlank() || text.trim() == appliedSignature) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftNote)
        } else if (accountViewModel.settings.automaticallyCreateDrafts()) {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template)
        }
    }

    private suspend fun createTemplate(): EventTemplate<out Event>? {
        // Check if this is a voice message
        voiceMetadata?.let { audioMeta ->
            // Only create voice reply if original note is also a voice message
            val originalVoiceHint = originalNote?.toEventHint<BaseVoiceEvent>()
            if (originalVoiceHint != null) {
                // Create voice reply event (KIND 1244)
                return VoiceReplyEvent.build(
                    voiceMessage = audioMeta,
                    replyingTo = originalVoiceHint,
                )
            }
            // If no original note, create a standalone voice event (KIND 1222)
            if (originalNote == null) {
                return VoiceEvent.build(
                    voiceMessage = audioMeta,
                )
            }
            // Otherwise, original note exists but is not a voice message
            // Create a TextNoteEvent (KIND 1) with audio as IMeta attachment
            return TextNoteEvent.build(audioMeta.url) {
                val replyingTo = originalNote?.toEventHint<TextNoteEvent>()
                if (replyingTo != null) {
                    val tags = prepareETagsAsReplyTo(replyingTo, null)
                    accountViewModel.fixReplyTagHints(tags)
                    markedETags(tags)
                    if (replyingTo.event.pubKey !in mutedNotifies) {
                        notify(replyingTo.toPTag())
                    }
                }
                activeAudience()?.let { userList ->
                    val tags =
                        userList.map {
                            val tag = it.toPTag()
                            if (tag.relayHint == null) {
                                tag.copy(relayHint = LocalCache.relayHints.hintsForKey(it.pubkeyHex).firstOrNull())
                            } else {
                                tag
                            }
                        }
                    notify(tags)
                }
                // Add audio as IMeta attachment
                add(audioMeta.toIMetaArray())
            }
        }

        val tagger =
            NewMessageTagger(
                message.text.toString().trim(),
                activeAudience(),
                eTags,
                accountViewModel,
            )
        tagger.run()

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val geoHash =
            if (wantsToAddGeoHash) {
                // A map-picked geohash wins over the live GPS fix.
                pickedGeoHash ?: (location?.value as? LocationResult.Success)?.geoHash?.toString()
            } else {
                null
            }
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null

        val emojis = account.emoji.findEmojiTags(tagger.message)
        val urls = findURLs(tagger.message)
        iMetaAttachments.awaitLinkedImages()
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null
        val subjectValue = subject.text.toString().trim()

        val threadTarget = groupThreadTarget
        return if (threadTarget != null) {
            // NIP-29 kind-11 group thread. The title is the required subject field (no first-line
            // fallback); the body is the full message. Scoped to the group by `h`; the host relay
            // authorizes the write.
            ThreadEvent.build(tagger.message, subjectValue) {
                hTag(threadTarget.groupId)
                // `p` mentions for everyone cited in the body, so a named member is notified and the
                // `nostr:` reference resolves — matching the Poll/ZapPoll branches below.
                pTags(tagger.directMentionsUsers.map { it.toPTag() })

                messageTags(tagger.message)

                geoHash?.let { geohash(it) }
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }

                emojis(emojis)
                imetas(usedAttachments)
            }
        } else if (wantsPoll) {
            val options = taggedPollOptions()
            if (options.isEmpty()) return null

            val quotes = pollQuotes(tagger, options)
            val relays =
                accountViewModel.account.nip65RelayList.outboxFlow.value
                    .toList()

            PollEvent.build(tagger.message, options.map { OptionTag(it.code, it.tagger.message) }, pollClosesAt(), relays, pollType) {
                pTags(pollMentions(tagger, options).map { it.toPTag() })
                quotes(quotes)
                hashtags(findHashtags(tagger.message))

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }

                emojis(pollEmojis(emojis, options))
                imetas(usedAttachments)
            }
        } else if (wantsZapPoll) {
            val options = taggedPollOptions()
            if (options.isEmpty()) return null

            ZapPollEvent.build(tagger.message, options.mapIndexed { index, option -> PollOptionTag(index, option.tagger.message) }) {
                pollClosesAt()?.let { closedAt(it) }
                zapPollValueMinimum?.let { minAmount(it) }
                zapPollValueMaximum?.let { maxAmount(it) }
                zapPollConsensusThreshold?.let { consensusThreshold(it / 100.0) }

                pTags(pollMentions(tagger, options).map { it.toPTag() })
                quotes(pollQuotes(tagger, options))
                hashtags(findHashtags(tagger.message))

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }

                emojis(pollEmojis(emojis, options))
                imetas(usedAttachments)
            }
        } else if (shouldReplyAsComment()) {
            // NIP-22: replies to a brand-new Amethyst kind-1 thread root are sent as
            // kind 1111 Comments instead of kind 1 replies.
            val eventHint = originalNote?.toEventHint<Event>() ?: return null

            CommentEvent.replyBuilder(tagger.message, eventHint) {
                tagger.pTagsWithHints(LocalCache.relayHints)?.let { notify(it) }

                messageTags(tagger.message)

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }

                emojis(emojis)
                imetas(usedAttachments)
            }
        } else {
            TextNoteEvent.build(tagger.message) {
                val replyingTo = originalNote?.toEventHint<TextNoteEvent>()
                val forkingFrom = forkedFromNote?.toEventHint<TextNoteEvent>()

                if (replyingTo != null || forkingFrom != null) {
                    val tags = prepareETagsAsReplyTo(replyingTo, forkingFrom)
                    // fixes wrong tags from previous clients
                    tags.forEach {
                        val note = LocalCache.getNoteIfExists(it.eventId)
                        val ourAuthor = note?.author?.pubkeyHex
                        val ourHint = note?.relayHintUrl()
                        if (it.author == null || it.author?.isBlank() == true) {
                            it.author = ourAuthor
                        } else {
                            if (ourAuthor != null && it.author != ourAuthor) {
                                it.author = ourAuthor
                            }
                        }
                        if (it.relay == null) {
                            it.relay = ourHint
                        } else {
                            if (ourHint != null && it.relay != ourHint) {
                                it.relay = ourHint
                            }
                        }
                    }
                    markedETags(tags)
                }

                tagger.pTagsWithHints(LocalCache.relayHints)?.let { notify(it) }

                messageTags(tagger.message)

                if (wantsSubject && subjectValue.isNotBlank()) subject(subjectValue)

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }

                emojis(emojis)
                imetas(usedAttachments)
            }
        }
    }

    /**
     * NIP-22: a reply should be a kind 1111 Comment (instead of a kind 1 reply) when
     * the note being replied to is a kind 1 [TextNoteEvent], is the root of a new
     * thread, and was itself posted from Amethyst ([ReplyActions.repliesAsComment],
     * shared with amy). Forks keep using kind 1.
     */
    private fun shouldReplyAsComment(): Boolean {
        if (forkedFromNote != null) return false
        val replyingToEvent = originalNote?.event ?: return false
        return ReplyActions.repliesAsComment(replyingToEvent)
    }

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        useH265: Boolean,
        stripMetadata: Boolean = true,
        convertGifToMp4: Boolean = false,
    ) {
        uploadUnsafe(alt, contentWarningReason, mediaQuality, server, onError, uploader, useH265, stripMetadata, convertGifToMp4)
    }

    fun uploadUnsafe(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        useH265: Boolean,
        stripMetadata: Boolean = true,
        convertGifToMp4: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            mediaUploadTracker.startUpload(myMultiOrchestrator.hasNonMedia())

            val results =
                myMultiOrchestrator.upload(
                    alt,
                    contentWarningReason,
                    CompressorQuality.fromSlider(mediaQuality),
                    server,
                    account,
                    uploader,
                    useH265,
                    stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                    convertGifToMp4 = convertGifToMp4,
                    forcedSigner = if (wantsAnonymousPost) anonymousSigner() else null,
                )

            if (results.allGood) {
                val urls =
                    results.successful.mapNotNull { state ->
                        val uploaded = state.result
                        if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                            val iMeta =
                                IMetaTagBuilder(uploaded.url)
                                    .apply {
                                        hash(uploaded.fileHeader.hash)
                                        size(uploaded.fileHeader.size)
                                        uploaded.fileHeader.mimeType
                                            ?.let { mimeType(it) }
                                        uploaded.fileHeader.dim
                                            ?.let { dims(it) }
                                        uploaded.fileHeader.blurHash
                                            ?.let { blurhash(it.blurhash) }
                                        uploaded.fileHeader.thumbHash
                                            ?.let { thumbhash(it.thumbhash) }
                                        uploaded.magnet?.let { magnet(it) }
                                        uploaded.uploadedHash?.let { originalHash(it) }

                                        alt?.let { alt(it) }
                                        contentWarningReason?.let { sensitiveContent(contentWarningReason) }
                                    }.build()

                            iMetaAttachments.replace(iMeta.url, iMeta)

                            uploaded.url
                        } else {
                            null
                        }
                    }

                onUiThread { message.insertUrlAtCursor(urls.joinToString(" ")) }
                urlPreviews.update(message.text.toString())

                multiOrchestrator = null
            } else {
                val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()
                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            mediaUploadTracker.finishUpload()
        }
    }

    /** Uploads a hand drawing ([uploadDrawing]) and puts its GIF in the note. */
    override fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val signer = if (wantsAnonymousPost) anonymousSigner() else null
            uploadDrawingIntoText(drawing, alt, account, accountViewModel.mediaUploader, mediaUploadTracker, onError, iMetaAttachments, message, urlPreviews, signer)
        }
    }

    open fun cancel() {
        draftTag.rotate()

        message.setTextAndPlaceCursorAtEnd("")

        forkedFromNote = null

        multiOrchestrator = null
        mediaUploadTracker.finishUpload()
        // cancel() is UI-thread confined -- the TextFieldState writes below require it -- and
        // this cleanup deletes files, so the disk work goes to IO. The path is captured here
        // because voiceLocalFile is cleared on the next lines, before the coroutine runs.
        val staleVoiceFile = voiceLocalFile
        viewModelScope.launch(Dispatchers.IO) {
            voiceAnonymization.clear()
            deleteVoiceLocalFile(staleVoiceFile)
        }
        voiceRecording = null
        voiceLocalFile = null
        isUploadingVoice = false
        voiceMetadata = null
        voiceSelectedServer = null
        voiceOrchestrator = null
        pTags = null
        mutedNotifies = emptySet()
        notifyProvenance = emptyMap()

        wantsPoll = false
        pollOptions = newPollOptions()
        activePollOption = null
        pollType = PollType.SINGLE_CHOICE
        pollDeadline = DEFAULT_POLL_DEADLINE

        wantsZapPoll = false
        zapPollValueMaximum = null
        zapPollValueMinimum = null
        zapPollConsensusThreshold = null

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsSubject = false
        subject.clearText()
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        pickedGeoHash = null
        wantsExclusiveGeoPost = false
        wantsSecretEmoji = false
        wantsAnonymousPost = false
        anonymousSignerCache = null
        scheduledForSec = null
        powOverride = null
        wantsPrivateNote = false
        privateNoteLocked = false
        resetAudienceEditor()

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.clearText()

        urlPreviews.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        iMetaAttachments.reset()

        emojiSuggestions?.reset()

        resetAiState()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
    }

    override fun onMessageChanged() {
        activePollOption = null
        urlPreviews.update(message.text.toString())

        if (message.selection.collapsed) {
            val lastWord = message.currentWord()
            if (lastWord.startsWith("@")) {
                userSuggestionsMainMessage = UserSuggestionAnchor.MAIN_MESSAGE
                userSuggestions?.processCurrentWord(lastWord)
            } else {
                userSuggestionsMainMessage = null
                userSuggestions?.reset()
            }

            emojiSuggestions?.processCurrentWord(lastWord)
        }

        precomputeAiResults()
        draftTag.newVersion()
    }

    override fun onForwardZapTextChanged() {
        if (forwardZapToEditting.selection.collapsed) {
            val lastWord = forwardZapToEditting.text.toString()
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                userSuggestions.replaceCurrentWord(message, lastWord, item)
                urlPreviews.update(message.text.toString())
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.POLL_OPTION) {
                activePollOption?.let { userSuggestions.replaceCurrentWord(it.state, it.state.currentWord(), item) }
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.value.addItem(item)
                forwardZapToEditting.clearText()
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.NOTIFY) {
                addAllToAudience(listOf(item))
                audienceSearchText.clearText()
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    open fun autocompleteWithEmoji(item: EmojiMedia) {
        emojiSuggestions?.autocompleteInto(emojiTarget, item)
        urlPreviews.update(message.text.toString())

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder)
        }

        emojiTarget.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message.text.toString())

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    /** The field an emoji goes into: the poll option being written, else the message. */
    val emojiTarget: TextFieldState
        get() = activePollOption?.takeIf { wantsPoll || wantsZapPoll }?.state ?: message

    private fun newPollOptions(): SnapshotStateList<PollOptionDraft> = mutableStateListOf(PollOptionDraft(), PollOptionDraft())

    fun canPost(): Boolean {
        // Voice messages can be posted without text (with either uploaded or pending recording)
        if (voiceMetadata != null || voiceRecording != null) {
            return !isUploadingVoice && !mediaUploadTracker.isUploading && processingPreset == null
        }

        // A NIP-29 group thread requires a title — it has no first-line fallback.
        if (groupThreadTarget != null && subject.text.isBlank()) return false

        // Regular text/media posts require text
        return message.text.toString().isNotBlank() &&
            !mediaUploadTracker.isUploading &&
            !isUploadingVoice &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            (!(wantsPoll || wantsZapPoll) || isPollReady()) &&
            (!wantsZapPoll || (isValidValueMinimum.value && isValidValueMaximum.value)) &&
            multiOrchestrator == null
    }

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
    }

    override fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    fun selectVoiceRecording(recording: RecordingResult) {
        // Cancel any ongoing processing and delete existing files
        voiceAnonymization.clear()
        deleteVoiceLocalFile()
        voiceRecording = recording
        voiceLocalFile = recording.file
        voiceMetadata = null
    }

    fun getVoicePreviewMetadata(): AudioMeta? =
        voiceRecording?.let { recording ->
            AudioMeta(
                url = "", // Empty URL for preview (local file will be used)
                mimeType = recording.mimeType,
                duration = recording.duration,
                waveform = recording.amplitudes,
            )
        }

    fun selectPreset(preset: VoicePreset) {
        voiceAnonymization.selectPreset(preset, voiceLocalFile)
    }

    fun removeVoiceMessage() {
        voiceAnonymization.clear()
        deleteVoiceLocalFile()
        voiceRecording = null
        voiceLocalFile = null
        voiceMetadata = null
        voiceSelectedServer = null
        isUploadingVoice = false
        voiceOrchestrator = null
    }

    private fun deleteVoiceLocalFile(toDelete: Path? = voiceLocalFile) {
        toDelete?.let { file ->
            try {
                platformFileSystem.delete(file, mustExist = false)
                Log.d("ShortNotePostViewModel") { "Deleted voice file: $file" }
            } catch (e: Exception) {
                Log.w("ShortNotePostViewModel", "Failed to delete voice file: $file", e)
            }
        }
    }

    suspend fun uploadVoiceMessageSync(
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
    ) {
        val recording = voiceRecording ?: return
        val fileToUpload = activeFile ?: recording.file
        val waveform = activeWaveform ?: recording.amplitudes
        val uploadErrorTitle = loadStringRes(Res.string.upload_error_title)
        val uploadVoiceFailed = loadStringRes(Res.string.upload_error_voice_message_failed)

        isUploadingVoice = true

        try {
            val uri = mediaUriOfFile(fileToUpload)
            val orchestrator = UploadOrchestrator()
            voiceOrchestrator = orchestrator

            val result =
                orchestrator.upload(
                    uri = uri,
                    mimeType = recording.mimeType,
                    alt = null,
                    contentWarningReason = null,
                    compressionQuality = CompressorQuality.UNCOMPRESSED,
                    server = server,
                    account = account,
                    uploader = accountViewModel.mediaUploader,
                    useH265 = false,
                    forcedSigner = if (wantsAnonymousPost) anonymousSigner() else null,
                )

            when (result) {
                is UploadingState.Finished -> {
                    when (val orchestratorResult = result.result) {
                        is UploadOrchestrator.OrchestratorResult.ServerResult -> {
                            voiceMetadata =
                                AudioMeta(
                                    url = orchestratorResult.url,
                                    mimeType = recording.mimeType,
                                    hash = orchestratorResult.fileHeader.hash,
                                    duration = recording.duration,
                                    waveform = waveform,
                                )
                            // Delete the local file after successful upload
                            deleteVoiceLocalFile()
                            voiceAnonymization.deleteDistortedFiles()
                            voiceLocalFile = null
                            voiceRecording = null
                        }
                    }
                }

                is UploadingState.Error -> {
                    onError(uploadErrorTitle, uploadVoiceFailed)
                    voiceRecording = null
                }
            }
        } catch (e: Exception) {
            onError(
                uploadErrorTitle,
                loadStringRes(Res.string.upload_error_voice_message_exception, e.message ?: e::class.simpleName.orEmpty()),
            )
            voiceRecording = null
        } finally {
            isUploadingVoice = false
            voiceOrchestrator = null
        }
    }

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun onCleared() {
        writingAssistant?.close()
        writingAssistant = null
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
    }

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger =
                NewMessageTagger(message.text.toString(), emptyList(), emptyList(), accountViewModel)
            tagger.run()
            tagger.pTags?.forEach { taggedUser ->
                if (!forwardZapTo.value.items.any { it.key == taggedUser }) {
                    forwardZapTo.value.addItem(taggedUser)
                }
            }
        }
    }

    override fun updateZapRaiserAmount(newAmount: Long?) {
        zapRaiserAmount.value = newAmount
        draftTag.newVersion()
    }

    fun removePollOption(option: PollOptionDraft) {
        pollOptions.remove(option)
        if (activePollOption === option) activePollOption = null
        while (pollOptions.size < MIN_POLL_OPTIONS) pollOptions.add(PollOptionDraft())
        keepTrailingPollOption()
        draftTag.newVersion()
    }

    /**
     * The cursor in [option] moved, or (when [textChanged]) its text did: offers `@` and `:`
     * completions and keeps the row to type the next option into. Only a text change is a
     * new draft version: a draft save signs an event, which on a remote or external signer is a
     * round trip, so tapping between fields must not cause one.
     */
    fun onPollOptionChanged(
        option: PollOptionDraft,
        textChanged: Boolean,
    ) {
        activePollOption = option
        if (textChanged) keepTrailingPollOption()

        if (option.state.selection.collapsed) {
            val lastWord = option.state.currentWord()
            if (lastWord.startsWith("@")) {
                userSuggestionsMainMessage = UserSuggestionAnchor.POLL_OPTION
                userSuggestions?.processCurrentWord(lastWord)
            } else {
                userSuggestionsMainMessage = null
                userSuggestions?.reset()
            }
            emojiSuggestions?.processCurrentWord(lastWord)
        }

        if (textChanged) draftTag.newVersion()
    }

    /** The message field took focus: the suggestions open for an option would complete into the wrong field. */
    fun onMessageFocused() {
        if (activePollOption == null) return
        activePollOption = null
        closePollOptionSuggestions()
    }

    /** Takes the poll off the post. Its options stay, so turning it back on finds them. */
    fun disablePoll() {
        wantsPoll = false
        wantsZapPoll = false
        activePollOption = null
        closePollOptionSuggestions()
    }

    private fun closePollOptionSuggestions() {
        if (userSuggestionsMainMessage == UserSuggestionAnchor.POLL_OPTION) {
            userSuggestionsMainMessage = null
            userSuggestions?.reset()
        }
        emojiSuggestions?.reset()
    }

    /** There is always one empty option at the end to type the next one into, so adding one is just typing. */
    private fun keepTrailingPollOption() {
        if (pollOptions.lastOrNull()?.isEmpty != true) pollOptions.add(PollOptionDraft())
    }

    /** The empty row at the end is where the next option goes, not an option to remove; one emptied mid-list is. */
    fun isTrailingPollOption(option: PollOptionDraft) = option === pollOptions.lastOrNull() && option.isEmpty && pollOptions.size > MIN_POLL_OPTIONS

    fun canRemovePollOption(option: PollOptionDraft) = pollOptions.size > MIN_POLL_OPTIONS && !isTrailingPollOption(option)

    /** The options that say the same thing as another, ignoring case: they split the vote for nothing. */
    fun duplicatePollOptions(): Set<PollOptionDraft> =
        filledPollOptions()
            .groupBy { it.label.lowercase() }
            .values
            .filter { it.size > 1 }
            .flatMapTo(mutableSetOf()) { it }

    /**
     * When the poll closes. A length counts from when the poll goes out: the scheduled time for
     * a scheduled post, which is re-stamped to that moment and published then.
     */
    private fun pollClosesAt(): Long? = pollDeadline.closesAt(scheduledForSec ?: TimeUtils.now())

    /** A deadline the poll would reach before or as it goes out: voting would be closed from the start. */
    fun isPollDeadlinePassed(): Boolean {
        val closesAt = pollClosesAt() ?: return false
        return closesAt < (scheduledForSec ?: TimeUtils.now()) + TimeUtils.ONE_MINUTE
    }

    /** The options as they are sent: in order, trimmed, without the empty ones. */
    private fun filledPollOptions(): List<PollOptionDraft> = pollOptions.filter { it.label.isNotEmpty() }

    /**
     * A draft keeps the poll even before any option is written, so it reopens as one; a post
     * only goes out once [isPollReady].
     */
    private fun pollOptionsToSend(): List<PollOptionDraft> = filledPollOptions().ifEmpty { pollOptions }

    /** An option and its text run through the same tagger as the message: `@` mentions become `nostr:` references. */
    private class TaggedPollOption(
        val code: String,
        val tagger: NewMessageTagger,
    )

    private suspend fun taggedPollOptions(): List<TaggedPollOption> =
        pollOptionsToSend().map { option ->
            TaggedPollOption(option.code, NewMessageTagger(option.label, dao = accountViewModel).also { it.run() })
        }

    /** Everyone the question or an option mentions, so the poll notifies them and renders their names. */
    private fun pollMentions(
        tagger: NewMessageTagger,
        options: List<TaggedPollOption>,
    ): Set<User> = tagger.directMentionsUsers + options.flatMap { it.tagger.directMentionsUsers }

    /** The notes the question or an option cites, so readers can fetch and show them. */
    private fun pollQuotes(
        tagger: NewMessageTagger,
        options: List<TaggedPollOption>,
    ) = (findNostrUris(tagger.message) + options.flatMap { findNostrUris(it.tagger.message) }).distinct()

    /** The custom emojis of the question and of every option: the card draws an option's `:code:` from these tags. */
    private fun pollEmojis(
        messageEmojis: List<EmojiUrlTag>,
        options: List<TaggedPollOption>,
    ): List<EmojiUrlTag> = (messageEmojis + options.flatMap { accountViewModel.account.emoji.findEmojiTags(it.tagger.message) }).distinctBy { it.code }

    private fun isPollReady(): Boolean =
        filledPollOptions().size >= MIN_POLL_OPTIONS &&
            duplicatePollOptions().isEmpty() &&
            !isPollDeadlinePassed()

    // ---
    // Zap Polls
    // ---

    fun updateMinZapAmountForPoll(textMin: String) {
        zapPollValueMinimum = textMin.toLongOrNull()?.takeIf { it > 0 }
        checkMinMax()
        draftTag.newVersion()
    }

    fun updateMaxZapAmountForPoll(textMax: String) {
        zapPollValueMaximum = textMax.toLongOrNull()?.takeIf { it > 0 }
        checkMinMax()
        draftTag.newVersion()
    }

    fun checkMinMax() {
        if ((zapPollValueMinimum ?: 0) > (zapPollValueMaximum ?: Long.MAX_VALUE)) {
            isValidValueMinimum.value = false
            isValidValueMaximum.value = false
        } else {
            isValidValueMinimum.value = true
            isValidValueMaximum.value = true
        }
    }

    override fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        draftTag.newVersion()
    }

    fun toggleSubject() {
        wantsSubject = !wantsSubject
        if (!wantsSubject) subject.clearText()
        draftTag.newVersion()
    }

    override fun toggleExpirationDate() {
        wantsExpirationDate = !wantsExpirationDate
        if (wantsExpirationDate) {
            expirationDate = TimeUtils.oneDayAhead()
        }
        draftTag.newVersion()
    }

    override fun locationManager(): DeviceLocation = accountViewModel.host.deviceLocation
}
