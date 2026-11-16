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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.DraftTagState
import com.vitorpamplona.amethyst.commons.model.composer.IExpiration
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.composer.SplitBuilder
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.latestBuzzEdit
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.mediaServers.buzzWorkspaceServer
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.richtext.UrlParser
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.ReplyMode
import com.vitorpamplona.quartz.buzz.media.BuzzImeta
import com.vitorpamplona.quartz.buzz.stream.BuzzChatMessage
import com.vitorpamplona.quartz.buzz.stream.BuzzEditTagOverlay
import com.vitorpamplona.quartz.buzz.stream.StreamMessageEditEvent
import com.vitorpamplona.quartz.buzz.stream.mentions
import com.vitorpamplona.quartz.buzz.threading.buzzThreadRootForReplyTo
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTags
import com.vitorpamplona.quartz.nip01Core.tags.people.toPTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip28PublicChat.base.notify
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previous
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.notify
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentSet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Stable
open class ChannelNewMessageViewModel :
    ViewModel(),
    ILocationGrabber,
    IExpiration {
    val draftTag = DraftTagState()

    // Strong reference to the live cache note for the current draft tag (derived from the
    // versions flow below), so LocalCache cannot weakly collect it before a deletion needs it.
    var draftNote: AddressableNote? = null
        private set

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

    lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account
    var channel: Channel? = null

    val replyTo = mutableStateOf<Note?>(null)

    // INLINE keeps the reply in the timeline (native reply); MINICHAT sends a kind-1111
    // thread comment that opens as a minichat. Only meaningful while replyTo is set.
    val replyMode = mutableStateOf(ReplyMode.INLINE)

    // When set, the composer is editing an existing Buzz message (kind 9, or a legacy 40002):
    // the next send publishes a kind-40003 edit targeting this note instead of a new
    // message. Only ever set for own messages on a Buzz-dialect relay (see editBuzzMessage).
    val editingBuzzMessage = mutableStateOf<Note?>(null)

    // The custom emoji the message being edited currently renders with. Buzz replaces a message's
    // emoji set with the edit's whenever the edit carries any, so an edit re-sends the ones still
    // used in the text alongside any newly picked ones.
    private var editingBuzzEmojis: List<EmojiUrlTag> = emptyList()

    // Explicit @-mentions resolved by the last createTemplate, used by sendPostSync for the Buzz
    // auto-invite. Kept off the draft path on purpose (see createTemplate / sendPostSync).
    private var pendingBuzzInviteMentions: List<User> = emptyList()

    var uploadState by mutableStateOf<ChatFileUploadState?>(null)

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    val iMetaAttachments = IMetaAttachments()

    val message = TextFieldState()
    var urlPreview by mutableStateOf<String?>(null)
    val isUploadingImage: Boolean get() = uploadState?.isUploadingImage ?: false
    val isUploadingFile: Boolean get() = uploadState?.isUploadingFile ?: false

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    // Invoices
    var canAddInvoice by mutableStateOf(false)
    var wantsInvoice by mutableStateOf(false)

    // Forward Zap to
    var wantsForwardZapTo by mutableStateOf(false)
    var forwardZapTo by mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    val forwardZapToEditting = TextFieldState()

    // NSFW, Sensitive
    var wantsToMarkAsSensitive by mutableStateOf(false)
    var contentWarningDescription by mutableStateOf("")

    // Expiration Date (NIP-40)
    var wantsExpirationDate by mutableStateOf(false)
    override var expirationDate by mutableLongStateOf(TimeUtils.oneDayAhead())

    // GeoHash
    var wantsToAddGeoHash by mutableStateOf(false)
    override var pickedGeoHash by mutableStateOf<String?>(null)
    var location: StateFlow<LocationResult>? = null

    // Geohash location chat (Bitchat interop): messages are signed with an anonymous per-cell
    // identity (unless posting as self) and carry a small NIP-13 PoW + the n/t tags.
    var geohashNickname by mutableStateOf("")
    var geohashTeleported by mutableStateOf(false)
    var geohashPostAsSelf by mutableStateOf(false)

    // ZapRaiser
    var canAddZapRaiser by mutableStateOf(false)
    var wantsZapRaiser by mutableStateOf(false)
    var zapRaiserAmount by mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().lnAddress()

    fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

    fun user(): User = account.userProfile()

    open fun init(accountVM: AccountViewModel) {
        // The channel screens call this straight from their composable body, so it runs on the main
        // thread on every recomposition of that body. Guard against re-running the allocating setup
        // (new UserSuggestionState/EmojiSuggestionState/ChatFileUploadState) when nothing changed:
        // only (re)initialize when the account actually differs. Beyond the wasted allocations, a
        // blind re-init would also reset `uploadState` mid-upload, discarding in-flight progress.
        if (::accountViewModel.isInitialized && this.accountViewModel === accountVM) return

        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions =
            UserSuggestionState(
                accountVM.account,
                accountVM.nip05ClientBuilder(),
                priorityPubkeys = {
                    // Public channels have no membership; recent posters are the
                    // closest thing. The cutoff also bounds the note scan.
                    channel?.participatingAuthors(TimeUtils.oneMonthAgo())?.mapTo(mutableSetOf()) { it.pubkeyHex } ?: emptySet()
                },
            )

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account.emoji)

        this.uploadState = ChatFileUploadState(account.settings.defaultFileServer, account.settings.stripLocationOnUpload)
    }

    open fun load(channel: Channel) {
        this.channel = channel
    }

    open fun reply(replyNote: Note) {
        replyTo.value = replyNote
        replyMode.value = ReplyMode.INLINE
        draftTag.newVersion()
    }

    fun toggleReplyMode() {
        replyMode.value = if (replyMode.value == ReplyMode.INLINE) ReplyMode.MINICHAT else ReplyMode.INLINE
    }

    fun clearReply() {
        replyTo.value = null
        replyMode.value = ReplyMode.INLINE
        draftTag.newVersion()
    }

    /**
     * Enters Buzz edit mode: pre-fills the composer with [note]'s current text (its newest edit,
     * if it has one) and marks the next send as a kind-40003 edit of it. Editing and replying are
     * mutually exclusive, so any pending reply is cleared. The caller gates this to the user's
     * own kind-9 (or legacy 40002) messages on a Buzz relay.
     */
    fun editBuzzMessage(note: Note) {
        replyTo.value = null
        replyMode.value = ReplyMode.INLINE
        editingBuzzMessage.value = note
        val latestEdit = note.latestBuzzEdit()?.event
        message.setTextAndPlaceCursorAtEnd(latestEdit?.content ?: note.event?.content ?: "")
        // Start from what the message shows now: an edit fully replaces its attachments on Buzz,
        // so the ones still referenced in the text must go out again with the edit.
        val currentTags = BuzzEditTagOverlay.apply(note.event?.tags ?: emptyArray(), latestEdit?.tags)
        iMetaAttachments.reset()
        iMetaAttachments.addAll(currentTags.imetas())
        editingBuzzEmojis = currentTags.emojis()
        draftTag.newVersion()
    }

    fun clearBuzzEdit() {
        editingBuzzMessage.value = null
        editingBuzzEmojis = emptyList()
        iMetaAttachments.reset()
        message.setTextAndPlaceCursorAtEnd("")
        draftTag.newVersion()
    }

    open fun editFromDraft(draft: Note) {
        val noteEvent = draft.event
        val noteAuthor = draft.author

        if (noteEvent is DraftWrapEvent && noteAuthor != null) {
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
        }
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return

        val localForwardZapTo = draftEvent.tags.zapSplitSetup()
        val totalWeight = localForwardZapTo.sumOf { it.weight }
        forwardZapTo = SplitBuilder()
        localForwardZapTo.forEach {
            if (it is ZapSplitSetup) {
                val user = LocalCache.getOrCreateUser(it.pubKeyHex)
                forwardZapTo.addItem(user, (it.weight / totalWeight).toFloat())
            }
            // don't support edditing old-style splits.
        }
        forwardZapToEditting.clearText()
        wantsForwardZapTo = localForwardZapTo.isNotEmpty()

        wantsToMarkAsSensitive = draftEvent.isSensitive()
        contentWarningDescription = draftEvent.contentWarningReason() ?: ""

        val draftExpiration = draftEvent.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val geohash = draftEvent.getGeoHash()
        wantsToAddGeoHash = geohash != null
        pickedGeoHash = geohash

        val zapraiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapraiser != null
        zapRaiserAmount = null
        if (zapraiser != null) {
            zapRaiserAmount = zapraiser
        }

        if (forwardZapTo.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        // Both event kinds extend BaseThreadedEvent and share `reply()`. Cast through
        // both candidates so the elvis result lands on the common supertype, then
        // load the addressed reply note in a single block.
        val threadedDraft =
            (draftEvent as? ChannelMessageEvent)
                ?: (draftEvent as? LiveActivitiesChatMessageEvent)
        threadedDraft?.reply()?.eventId?.let { replyId ->
            replyTo.value = LocalCache.checkGetOrCreateNote(replyId)
        }

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreview = findUrlInMessage()
    }

    fun sendPost(onDone: suspend () -> Unit) {
        accountViewModel.launchSigner {
            sendPostSync()
            onDone()
        }
    }

    suspend fun sendPostSync() {
        // Reset before createTemplate populates it, so a prior send/draft can't leak stale mentions.
        pendingBuzzInviteMentions = emptyList()
        val template = createTemplate() ?: return
        val channelRelays = channel?.relays() ?: emptySet()

        // Buzz auto-invite runs ONLY on a real send (never draft auto-save), before the message goes
        // out so the relay accepts the mention. No-op unless this is a Buzz relay group the user
        // moderates; guarded so a failed add never blocks the message.
        channel?.let { autoInviteMentionedBuzzMembers(it, pendingBuzzInviteMentions) }
        channel?.let { joinBuzzChannelBeforePosting(it) }

        // A geohash cell with no resolvable relays has nowhere to publish. Bail before cancel() clears
        // the composer, so the user keeps their text (and draft) to retry rather than losing it silently.
        if (channel is GeohashChatChannel && channelRelays.isEmpty()) return

        // A mined post leaves the phone minutes after this returns — much later if the
        // app is backgrounded and frozen — and the draft is its only user-visible copy
        // until then. The auto-save is debounced and cancel() below drops the pending
        // save, so the last second of typing would never reach it: flush it now.
        // Geohash posts mine inline and drop the draft right away, so only the queued path needs it.
        if (channel !is GeohashChatChannel && accountViewModel.account.powDifficultyFor(template.kind) != null) {
            draftNote = account.getOrCreateDraftNote(draftTag.current)
            sendDraftSync()
        }

        val draftToDelete = draftNote
        onUiThread { cancel() }

        val currentChannel = channel
        if (currentChannel is GeohashChatChannel) {
            // Geohash chat: sign with the anonymous per-cell identity (or the account when posting
            // as self) and mine the fixed 8-bit Bitchat PoW for that pubkey, instead of the account
            // signer + PUBLIC_CHAT PoW category.
            sendGeohashMined(template, currentChannel.geohash, channelRelays.toSet())
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            return
        }

        // Kinds 42/1311 are the PUBLIC_CHAT PoW category: route through the
        // mining gate (a no-op when the category is off). Draft deletion runs
        // inside the publish continuation so a cancelled mining job can't
        // destroy the only copy of the text.
        accountViewModel.account.sendMined(template, PoWReplay.ToRelays(channelRelays.toList())) { readyTemplate ->
            accountViewModel.account.signAndSendPrivatelyOrBroadcast(readyTemplate) {
                channelRelays.toList()
            }
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
        }
    }

    private suspend fun sendGeohashMined(
        template: EventTemplate<out Event>,
        geohash: String,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty()) return
        val account = accountViewModel.account
        val signer: NostrSigner
        val pubKeyHex: String
        if (geohashPostAsSelf) {
            signer = account.signer
            pubKeyHex = account.signer.pubKey
        } else {
            val keyPair = withContext(Dispatchers.IO) { account.geohashIdentity.keyPair(geohash) }
            signer = NostrSignerInternal(keyPair)
            pubKeyHex = keyPair.pubKey.toHexKey()
        }

        // Bitchat mines 8 bits by default; cap the effort so a slow device still sends.
        // PoWMiner.mine moves itself onto its own mining threads.
        val started = TimeSource.Monotonic.markNow()
        val mined =
            runCatching {
                PoWMiner.mine(template, pubKeyHex, 8, account.powMinerWorkers(), isActive = { started.elapsedNow() < 2.seconds })
            }.getOrDefault(template)

        runCatching { account.signWithAndSendPrivately(mined, signer, relays) }
    }

    suspend fun sendDraftSync() {
        if (message.text.toString().isBlank()) {
            account.deleteDraftIgnoreErrors(draftNote)
        } else if (accountViewModel.settings.automaticallyCreateDrafts()) {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template)
        }
    }

    fun pickedMedia(list: ImmutableList<SelectedMedia>) {
        // A Buzz relay only accepts imeta URLs under its own /media/, so a Buzz channel's
        // attachments go to the workspace's media server and nowhere else.
        val channel = channel
        uploadState?.lockServer(
            if (channel is RelayGroupChannel && BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) {
                buzzWorkspaceServer(channel.groupId.relayUrl.url)
            } else {
                null
            },
        )
        uploadState?.load(list)
    }

    fun upload(
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        onceUploaded: suspend () -> Unit,
    ) {
        uploadUnsafe(onError, uploader, onceUploaded)
    }

    fun uploadUnsafe(
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        onceUploaded: suspend () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val uploadState = uploadState ?: return@launch

            val myMultiOrchestrator = uploadState.multiOrchestrator ?: return@launch

            uploadState.mediaUploadTracker.startUpload(myMultiOrchestrator.hasNonMedia())

            val results =
                myMultiOrchestrator.upload(
                    uploadState.caption,
                    uploadState.contentWarningReason,
                    CompressorQuality.fromSlider(uploadState.mediaQualitySlider),
                    uploadState.selectedServer,
                    account,
                    uploader,
                    stripMetadata = uploadState.stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                )

            if (results.allGood) {
                val urls =
                    results.successful.mapNotNull { upload ->
                        val uploaded = upload.result
                        if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                            iMetaAttachments.add(uploaded, uploadState.caption, uploadState.contentWarningReason)

                            uploaded.url
                        } else {
                            null
                        }
                    }

                onUiThread { message.insertUrlAtCursor(urls.joinToString(" ")) }
                urlPreview = findUrlInMessage()

                uploadState.reset()
                onceUploaded()
                draftTag.newVersion()
            } else {
                val errorMessages = results.errors.map { loadStringRes(it.errorResource, *it.params) }.distinct()

                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            uploadState.mediaUploadTracker.finishUpload()
        }
    }

    /**
     * Buzz auto-invite: mentioning someone who isn't yet a member of a Buzz workspace channel adds
     * them (kind-9000 put-user) before the message goes out, so the `@`-mention resolves to a real
     * member — mirroring Buzz's own composer, which is how you pull a bot into a channel by naming it.
     *
     * Only a moderator can issue kind-9000, so this is a no-op for a plain member (the relay would
     * reject it anyway); self and already-present members are skipped. Best-effort — a failed add
     * must never block the message, so each is guarded.
     */
    private suspend fun autoInviteMentionedBuzzMembers(
        channel: Channel,
        mentioned: List<User>?,
    ) {
        if (mentioned.isNullOrEmpty()) return
        if (channel !is RelayGroupChannel || !BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) return
        val me = accountViewModel.account.userProfile().pubkeyHex
        if (!channel.membershipOf(me).canModerate()) return

        mentioned.forEach { user ->
            val pk = user.pubkeyHex
            if (pk != me && channel.membershipOf(pk) == RelayGroupMembership.NONE) {
                try {
                    accountViewModel.account.relayGroups.addRelayGroupUser(channel, pk)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("BuzzAutoInvite", "Failed to add mentioned member ${pk.take(8)}", e)
                }
            }
        }
    }

    // Buzz channels this composer has already tried to join (see [joinBuzzChannelBeforePosting]).
    private val buzzJoinAttempted = ConcurrentSet<GroupId>()

    /**
     * Posting to a Buzz open channel joins it first. The relay takes a non-member's message there,
     * but channel membership is what Buzz lists, counts and offers in its mention picker, and its
     * own client requires joining before it lets anyone post — so a poster who never joined was
     * invisible on the Buzz side. Best-effort: a failed join must never block the message.
     */
    private suspend fun joinBuzzChannelBeforePosting(channel: Channel) {
        if (channel !is RelayGroupChannel || !BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) return
        if (channel.event?.isBuzzDm() == true) return
        if (channel.membershipOf(accountViewModel.account.userProfile().pubkeyHex).isMember()) return
        // Once per channel: the relay's roster (39002) lags the join, and every post before it
        // lands would otherwise re-send the kind-9021 and re-publish the kind-10009 list.
        if (!buzzJoinAttempted.add(channel.groupId)) return
        try {
            accountViewModel.account.relayGroups.joinRelayGroup(channel)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("BuzzAutoJoin", "Failed to join ${channel.groupId.id} before posting", e)
        }
    }

    // `protected open` so a specialized composer (e.g. the Buzz forum reply) can reuse this whole
    // rich EditFieldRow but swap only the event it builds for the composed text.
    protected open suspend fun createTemplate(): EventTemplate<out Event>? {
        val channel = channel ?: return null

        val messageText = message.text.toString()
        val isBuzzChannel = channel is RelayGroupChannel && BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)
        val tagger =
            NewMessageTagger(
                message = messageText,
                pTags = listOfNotNull(replyTo.value?.author),
                eTags = listOfNotNull(replyTo.value),
                dao = accountViewModel,
                userMentionsAsNames = isBuzzChannel,
            )
        tagger.run()

        // Stash the *explicit* @-mentions for a possible auto-invite — but only the send path acts on
        // them (see sendPostSync). Exclude the reply target (seeded into pTags) so replying to a
        // non-member doesn't silently add them; and NEVER invite from here, because createTemplate also
        // runs on every debounced draft auto-save (sendDraftSync) — inviting here would fire kind-9000s
        // while the user is still typing.
        pendingBuzzInviteMentions = tagger.pTags?.filter { it != replyTo.value?.author }.orEmpty()

        val urls = findURLs(messageText)
        val usedAttachments =
            iMetaAttachments.filterIsIn(urls.toSet()).let {
                // Buzz refuses the whole message over any imeta key it doesn't know (e.g. `ox`).
                if (isBuzzChannel) BuzzImeta.sanitize(it) else it
            }
        val emojis = accountViewModel.account.emoji.findEmojiTags(messageText)

        val channelRelays = channel.relays()
        val geoHash = if (wantsToAddGeoHash) (pickedGeoHash ?: (location?.value as? LocationResult.Success)?.geoHash?.toString()) else null

        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null

        // A minichat reply is a kind-1111 thread comment rooted at the parent, independent of the
        // channel type (NIP-29 groups additionally carry the `h` tag). It carries the same mention/
        // hashtag/quote/emoji/attachment enrichment an inline message does — built from tagger.message,
        // not the raw text — so replying in a thread never silently drops any of them.
        // Buzz relays reject unknown kinds outright, and kind 1111 is not in Buzz's
        // registry — a minichat comment sent to a workspace channel would be refused
        // by the relay AFTER the composer already cleared. Buzz replies always go
        // through the Buzz kind-9 branch with its thread markers instead.
        val minichatAllowed = !(channel is RelayGroupChannel && BuzzRelayDialect.isBuzz(channel.groupId.relayUrl))
        val minichatParent = replyTo.value?.takeIf { minichatAllowed && replyMode.value == ReplyMode.MINICHAT }?.event
        if (minichatParent != null) {
            return CommentEvent.replyBuilder(tagger.message, EventHintBundle(minichatParent, channelRelays.firstOrNull())) {
                if (channel is RelayGroupChannel) {
                    hTag(channel.groupId.id)
                    previous(channel.previousEventRefs(account.userProfile().pubkeyHex))
                }
                // `p` mentions for everyone else cited in the body — replyBuilder already tags the
                // parent/root author, so exclude the parent to avoid a duplicate `p`.
                notify(
                    tagger.pTags
                        ?.filter { it.pubkeyHex != minichatParent.pubKey }
                        ?.map { it.toPTag() }
                        .orEmpty(),
                )
                hashtags(findHashtags(tagger.message))
                references(findURLs(tagger.message))
                quotes(findNostrUris(tagger.message))
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }
                geoHash?.let { geohash(it) }
                emojis(emojis)
                imetas(usedAttachments)
            }
        }

        return when {
            channel is PublicChatChannel -> {
                val replyingToEvent = replyTo.value?.toEventHint<ChannelMessageEvent>()
                val channelEvent = channel.event

                if (replyingToEvent != null) {
                    ChannelMessageEvent.reply(tagger.message, replyingToEvent) {
                        notify(replyingToEvent.toPTag())

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        geoHash?.let { geohash(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else if (channelEvent != null) {
                    val hint = EventHintBundle(channelEvent, channelRelays.firstOrNull())
                    ChannelMessageEvent.message(tagger.message, hint) {
                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        geoHash?.let { geohash(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else {
                    ChannelMessageEvent.message(tagger.message, ETag(channel.idHex, channelRelays.firstOrNull())) {
                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        geoHash?.let { geohash(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                }
            }

            channel is LiveActivitiesChannel -> {
                val replyingToEvent = replyTo.value?.toEventHint<LiveActivitiesChatMessageEvent>()
                val activity = channel.info

                if (replyingToEvent != null) {
                    LiveActivitiesChatMessageEvent.reply(tagger.message, replyingToEvent) {
                        notify(replyingToEvent.toPTag())

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else if (activity != null) {
                    val hint = EventHintBundle(activity, channelRelays.firstOrNull())

                    LiveActivitiesChatMessageEvent.message(tagger.message, hint) {
                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else {
                    LiveActivitiesChatMessageEvent.message(tagger.message, channel.toATag()) {
                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                }
            }

            channel is EphemeralChatChannel -> {
                EphemeralChatEvent.build(
                    tagger.message,
                    channel.roomId.relayUrl,
                    channel.roomId.id,
                ) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            }

            channel is GeohashChatChannel -> {
                // Bitchat kind-20000: same mention/hashtag/quote/emoji enrichment as any message,
                // plus the g/n/t tags. Signed + PoW-mined with the per-cell identity in sendPostSync.
                GeohashChatEvent.build(
                    tagger.message,
                    channel.geohash,
                    nickname = geohashNickname.ifBlank { null },
                    teleported = geohashTeleported,
                ) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)

                    replyTo.value?.idHex?.let { add(arrayOf("e", it, "", "reply")) }
                }
            }

            channel is RelayGroupChannel &&
                BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) &&
                editingBuzzMessage.value != null -> {
                // Buzz edit (kind 40003): replaces the text of an existing Buzz message.
                // build() sets the group's `h` tag plus the `e` tag pointing at the edited
                // message; content is the replacement text. Kept minimal to mirror Buzz's own
                // `build_edit` (buzz-sdk builders.rs) — the relay validates edits and the author
                // match. LocalCache overlays the newest edit last-write-wins, so the edited row
                // re-renders with this content.
                // Buzz's clients render an edit with the original's tags overlaid by the edit's
                // (BuzzEditTagOverlay): attachments come ONLY from the edit, and custom emoji from
                // the edit whenever it has any. So the edit carries every attachment and emoji the
                // new text still uses, like Buzz's own `build_message_edit`, or they would vanish.
                val target = editingBuzzMessage.value!!
                val editEmojis =
                    (emojis + editingBuzzEmojis.filter { tagger.message.contains(":${it.code}:") })
                        .distinctBy { it.code }
                StreamMessageEditEvent.build(channel.groupId.id, target.idHex, tagger.message) {
                    // Carry `p` mentions for anyone cited in the edited text, so a mention added
                    // (or kept) by an edit still notifies the member and resolves its `nostr:` ref.
                    mentions(tagger.pTags?.map { it.pubkeyHex }.orEmpty())
                    imetas(usedAttachments)
                    emojis(editEmojis)
                }
            }

            channel is RelayGroupChannel && BuzzRelayDialect.isBuzz(channel.groupId.relayUrl) -> {
                // Buzz workspace message: a kind-9 chat in Buzz's tag shape (`build_message` in
                // buzz-sdk builders.rs), which is what Buzz's own desktop, mobile and CLI clients
                // write. Kind 40002 is only read now, for older messages. Replies thread with NIP-10
                // marked e-tags (["e", root, "", "root"] + ["e", parent, "", "reply"], collapsing to
                // a single "reply" when the parent IS the root) plus a `p` notify to the parent
                // author. The dialect check comes from BuzzRelayDialect (marked off verified Buzz
                // events), not a channel subtype: channel instances are captured by screens for
                // their whole life, so the dialect must be able to flip mid-session without
                // swapping objects.
                // Reply routing (Buzz has no kind-1111): an INLINE reply is flagged `broadcast` so it stays
                // a flat timeline sibling; a MINICHAT reply omits it so the thread markers pull it into the
                // message's minichat (mirrors block/buzz's broadcast-vs-thread split). A non-reply is neither.
                val parent = replyTo.value
                // The parent's resolved root when it is itself a reply (a direct reply's collapsed form
                // carries the root as its "reply" marker), else the parent starts the thread. A parent
                // with only a `root` marker counts as top-level: Buzz's relay validates ancestry that
                // way and rejects a mis-derived root.
                val threadRoot = parent?.let { it.event?.tags?.buzzThreadRootForReplyTo(it.idHex) ?: it.idHex }
                BuzzChatMessage.build(
                    channelId = channel.groupId.id,
                    // Buzz draws an attachment only where the body links it as `![image](url)`.
                    content = BuzzImeta.markdownMediaBody(tagger.message, usedAttachments),
                    threadRoot = threadRoot,
                    replyTo = parent?.idHex,
                    // `p` mentions for everyone cited in the body (plus the reply target, which the
                    // tagger seeds into pTags) so a named member is notified and the Buzz relay can
                    // resolve the `nostr:` reference. Deduplicated, so the reply author isn't doubled.
                    mentions = tagger.pTags?.map { it.pubkeyHex }.orEmpty(),
                    broadcast = parent != null && replyMode.value == ReplyMode.INLINE,
                ) {
                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            }

            channel is RelayGroupChannel -> {
                // NIP-29 group message: a kind-9 chat scoped to the group with an
                // `h` tag. The event is published only to the group's host relay
                // (channel.relays()), where relay29 authorizes and routes it.
                val replyingToEvent = replyTo.value?.toEventHint<ChatEvent>()
                if (replyingToEvent != null) {
                    // A reply quotes its parent via a NIP-18 `q` tag (added by ChatEvent.reply)
                    // + a `p` notify to its author. The group scope (`h`) alone doesn't link a
                    // reply to what it answers — without the `q` tag the quote renders in the
                    // composer but is missing from the signed event.
                    ChatEvent.reply(tagger.message, replyingToEvent) {
                        hTag(channel.groupId.id)
                        previous(channel.previousEventRefs(account.userProfile().pubkeyHex))
                        pTag(replyingToEvent.toPTag())
                        // `p` mentions for everyone else cited in the body (the parent author is
                        // already tagged above with its relay hint) so a named member is notified.
                        pTags(
                            tagger.pTags
                                ?.filter { it.pubkeyHex != replyingToEvent.event.pubKey }
                                ?.map { it.toPTag() }
                                .orEmpty(),
                        )

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                } else {
                    ChatEvent.build(tagger.message) {
                        hTag(channel.groupId.id)
                        previous(channel.previousEventRefs(account.userProfile().pubkeyHex))
                        // `p` mentions for everyone cited in the body so a named member is notified.
                        pTags(tagger.pTags?.map { it.toPTag() }.orEmpty())

                        hashtags(findHashtags(tagger.message))
                        references(findURLs(tagger.message))
                        quotes(findNostrUris(tagger.message))
                        contentWarningReason?.let { contentWarning(it) }
                        localExpirationDate?.let { expiration(it) }

                        emojis(emojis)
                        imetas(usedAttachments)
                    }
                }
            }

            else -> {
                null
            }
        }
    }

    open fun cancel() {
        draftTag.rotate()

        message.setTextAndPlaceCursorAtEnd("")

        replyTo.value = null
        editingBuzzMessage.value = null

        urlPreview = null

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        pickedGeoHash = null

        forwardZapTo = SplitBuilder()
        forwardZapToEditting.clearText()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        uploadState?.reset()

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    open fun findUrlInMessage(): String? = UrlParser().parseValidUrls(message.text.toString()).withScheme.firstOrNull()

    open fun addToMessage(it: String) {
        message.setTextAndPlaceCursorAtEnd(message.text.toString() + " " + it)
        onMessageChanged()
    }

    open fun onMessageChanged() {
        urlPreview = findUrlInMessage()

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

        draftTag.newVersion()
    }

    open fun onForwardZapTextChanged() {
        if (forwardZapToEditting.selection.collapsed) {
            val lastWord = forwardZapToEditting.text.toString()
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    open fun autocompleteWithUser(item: User) {
        userSuggestions?.let {
            if (userSuggestionsMainMessage == UserSuggestionAnchor.MAIN_MESSAGE) {
                val lastWord = message.currentWord()
                it.replaceCurrentWord(message, lastWord, item)
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.addItem(item)
                forwardZapToEditting.clearText()
            }

            userSuggestionsMainMessage = null
            it.reset()
        }

        draftTag.newVersion()
    }

    open fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder)
        }

        message.replaceCurrentWord(wordToInsert)

        emojiSuggestions?.reset()

        urlPreview = findUrlInMessage()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            uploadState?.mediaUploadTracker?.isUploading != true &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount != null) &&
            uploadState?.multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
    }

    override fun locationManager(): DeviceLocation = accountViewModel.host.deviceLocation

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun onCleared() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
    }

    fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.updatePercentage(index, sliderValue)
    }

    fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger = NewMessageTagger(message.text.toString(), emptyList(), emptyList(), accountViewModel)
            tagger.run()
            tagger.pTags?.forEach { taggedUser ->
                if (!forwardZapTo.items.any { it.key == taggedUser }) {
                    forwardZapTo.addItem(taggedUser)
                }
            }
        }
    }

    fun updateZapRaiserAmount(newAmount: Long?) {
        zapRaiserAmount = newAmount
        draftTag.newVersion()
    }

    fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
        draftTag.newVersion()
    }

    fun toggleExpirationDate() {
        wantsExpirationDate = !wantsExpirationDate
        if (wantsExpirationDate) {
            expirationDate = TimeUtils.oneDayAhead()
        }
        draftTag.newVersion()
    }
}
