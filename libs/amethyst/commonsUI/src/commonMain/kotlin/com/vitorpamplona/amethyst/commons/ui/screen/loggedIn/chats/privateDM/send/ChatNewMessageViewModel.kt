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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send

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
import com.vitorpamplona.amethyst.commons.drawing.RenderedDrawing
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.DraftTagState
import com.vitorpamplona.amethyst.commons.model.composer.IExpiration
import com.vitorpamplona.amethyst.commons.model.composer.IZapRaiser
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.commons.model.composer.PreviewState
import com.vitorpamplona.amethyst.commons.model.composer.SplitBuilder
import com.vitorpamplona.amethyst.commons.model.composer.toZapSplitSetup
import com.vitorpamplona.amethyst.commons.model.location.DeviceLocation
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.relays.dialableRelays
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_encrypted_media_message
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_encrypted_media_title
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerPostOptions
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.ChatFileSender
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.ChatFileUploader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.upload.SuccessfulUploads
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils.ChatFileUploadState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawing
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.crypto.aes.AESGCM
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrEventUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.NIP17Group
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class ChatNewMessageViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IComposerPostOptions,
    IZapField,
    IZapRaiser,
    IExpiration {
    val draftTag = DraftTagState()

    // Strong reference to the live cache note for the current draft tag (derived from the
    // versions flow below), so LocalCache cannot weakly collect it before a deletion needs it.
    var draftNote: AddressableNote? = null
        private set

    lateinit var accountViewModel: AccountViewModel
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

    val room = MutableStateFlow<ChatroomKey?>(null)

    val roomUsers: StateFlow<List<User>> =
        room
            .mapNotNull {
                it?.users?.mapNotNull { userHex -> LocalCache.checkGetOrCreateUser(userHex) } ?: emptyList()
            }.flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                room.value?.users?.mapNotNull { userHex -> LocalCache.checkGetOrCreateUser(userHex) } ?: emptyList(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val recipientsMissingDmRelays: StateFlow<ImmutableList<User>> =
        roomUsers
            .transformLatest {
                val dmRelayListNoteFlows =
                    it.map { user ->
                        user.dmRelayListNote
                            .flow()
                            .metadata.stateFlow
                    }

                if (dmRelayListNoteFlows.isEmpty()) {
                    emitAll(MutableStateFlow(persistentListOf()))
                } else {
                    val flow =
                        combine(dmRelayListNoteFlows) { dmRelayListNotes ->
                            dmRelayListNotes
                                .mapNotNull { noteState ->
                                    val noteEvent = noteState.note.event as? DmRelayListEvent
                                    if (noteEvent == null || noteEvent.dialableRelays().isEmpty()) {
                                        noteState.note.author
                                    } else {
                                        null
                                    }
                                }.toImmutableList()
                        }
                    emitAll(flow)
                }
            }.onStart {
            }.onCompletion {
            }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                roomUsers.value
                    .filter { user ->
                        user.dmInboxRelays().isNullOrEmpty()
                    }.toImmutableList(),
            )

    val replyTo = mutableStateOf<Note?>(null)

    var uploadState by mutableStateOf<ChatFileUploadState?>(null)

    // Stripping failure dialog
    val strippingFailureConfirmation = SuspendableConfirmation()

    val iMetaAttachments = IMetaAttachments()

    var uploadsWaitingToBeSent by mutableStateOf<List<SuccessfulUploads>>(emptyList())

    override val message = TextFieldState()

    val urlPreviews = PreviewState()

    val isUploadingImage: Boolean get() = uploadState?.isUploadingImage ?: false
    val isUploadingFile: Boolean get() = uploadState?.isUploadingFile ?: false

    var userSuggestions: UserSuggestionState? = null
    var userSuggestionsMainMessage: UserSuggestionAnchor? = null

    var emojiSuggestions: EmojiSuggestionState? = null

    val toUsers = TextFieldState()
    val subject = TextFieldState()

    // Invoices
    override var canAddInvoice by mutableStateOf(false)
    override var wantsInvoice by mutableStateOf(false)

    override var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    override var wantsForwardZapTo by mutableStateOf(false)
    override val forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
    override val forwardZapToEditting = TextFieldState()

    // NSFW, Sensitive
    override var wantsToMarkAsSensitive by mutableStateOf(false)
    var contentWarningDescription by mutableStateOf("")

    // Expiration Date (NIP-40)
    override var wantsExpirationDate by mutableStateOf(false)
    override var expirationDate by mutableLongStateOf(TimeUtils.oneDayAhead())

    // GeoHash
    override var wantsToAddGeoHash by mutableStateOf(false)
    override var pickedGeoHash by mutableStateOf<String?>(null)
    var location: StateFlow<LocationResult>? = null

    // ZapRaiser
    override var canAddZapRaiser by mutableStateOf(false)
    override var wantsZapRaiser by mutableStateOf(false)
    override var zapRaiserAmount = mutableStateOf<Long?>(null)

    fun lnAddress(): String? = account.userProfile().lnAddress()

    override fun hasLnAddress(): Boolean = account.userProfile().lnAddress() != null

    fun user(): User = account.userProfile()

    fun init(accountVM: AccountViewModel) {
        this.accountViewModel = accountVM
        this.account = accountVM.account
        this.canAddInvoice = hasLnAddress()
        this.canAddZapRaiser = hasLnAddress()

        this.userSuggestions?.reset()
        this.userSuggestions =
            UserSuggestionState(
                accountVM.account,
                accountVM.nip05ClientBuilder(),
                priorityPubkeys = { room.value?.users ?: emptySet() },
            )

        this.emojiSuggestions?.reset()
        this.emojiSuggestions = EmojiSuggestionState(accountVM.account.emoji)

        this.uploadState =
            ChatFileUploadState(
                account.settings.defaultFileServer,
                account.settings.stripLocationOnUpload,
            )
    }

    fun load(room: ChatroomKey) {
        this.room.tryEmit(room)
        this.toUsers.setTextAndPlaceCursorAtEnd(
            room.users.mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
        )
    }

    fun reply(replyNote: Note) {
        replyTo.value = replyNote
        draftTag.newVersion()
    }

    fun clearReply() {
        replyTo.value = null
        draftTag.newVersion()
    }

    fun quote(quote: Note) {
        message.setTextAndPlaceCursorAtEnd(message.text.toString() + "\nnostr:${quote.toNEvent()}")
        urlPreviews.update(message.text.toString())

        // creates a split with that author.
        val quotedAuthor = quote.author ?: return

        if (quotedAuthor.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
            if (forwardZapTo.value.items.none { it.key.pubkeyHex == quotedAuthor.pubkeyHex }) {
                forwardZapTo.value.addItem(quotedAuthor)
            }
            if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) {
                forwardZapTo.value.addItem(accountViewModel.userProfile())
            }

            val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == quotedAuthor.pubkeyHex }
            forwardZapTo.value.updatePercentage(pos, 0.9f)

            wantsForwardZapTo = true
        }
    }

    fun editFromDraft(draft: Note) {
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

    fun loadExpiration(expirationDays: Int) {
        this.wantsExpirationDate = true
        this.expirationDate = TimeUtils.now() + expirationDays * 86400L
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return

        val localForwardZapTo = draftEvent.tags.zapSplitSetup()
        val totalWeight = localForwardZapTo.sumOf { it.weight }
        forwardZapTo.value = SplitBuilder()
        localForwardZapTo.forEach {
            if (it is ZapSplitSetup) {
                val user = LocalCache.getOrCreateUser(it.pubKeyHex)
                forwardZapTo.value.addItem(user, (it.weight / totalWeight).toFloat())
            }
            // don't support editing old-style splits.
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
        zapRaiserAmount.value = null
        if (zapraiser != null) {
            zapRaiserAmount.value = zapraiser
        }

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        draftEvent.subject()?.let {
            subject.setTextAndPlaceCursorAtEnd("")
        }

        if (draftEvent is NIP17Group) {
            toUsers.setTextAndPlaceCursorAtEnd(
                draftEvent.groupMembers().mapNotNull { runCatching { Hex.decode(it).toNpub() }.getOrNull() }.joinToString(", ") { "@$it" },
            )

            val replyId =
                when (draftEvent) {
                    is ChatMessageEvent -> draftEvent.replyTo().lastOrNull()
                    is ChatMessageEncryptedFileHeaderEvent -> draftEvent.replyTo().lastOrNull()
                    else -> null
                }

            if (replyId != null) {
                replyTo.value = LocalCache.checkGetOrCreateNote(replyId)
            }
        } else if (draftEvent is EncryptedDmEvent) {
            val recipientNPub = draftEvent.verifiedRecipientPubKey()?.let { Hex.decode(it).toNpub() }
            toUsers.setTextAndPlaceCursorAtEnd("@$recipientNPub")

            val replyId = draftEvent.replyTo()
            if (replyId != null) {
                replyTo.value = LocalCache.checkGetOrCreateNote(replyId)
            }
        }

        val draftText =
            if (draftEvent is EncryptedDmEvent) {
                accountViewModel.account.privateDMDecryptionCache.cachedDM(draftEvent) ?: ""
            } else {
                draftEvent.content
            }
        message.setTextAndPlaceCursorAtEnd(draftText)
        urlPreviews.update(message.text.toString())

        iMetaAttachments.addAll(draftEvent.imetas())
    }

    suspend fun sendPostSync() {
        // With PoW on, the gift wraps are mined in the background and leave the phone
        // minutes later; until then the draft is the only copy the user can see. The
        // auto-save is debounced and cancel() drops the pending save, so flush it now
        // and delete it only once the wraps are actually sent.
        if (accountViewModel.account.powDifficultyFor(GiftWrapEvent.KIND) != null) {
            draftNote = account.getOrCreateDraftNote(draftTag.current)
            sendDraftSync()
        }

        val draftToDelete = draftNote
        val account = accountViewModel.account
        innerSendPost(null) {
            // off the caller: signing the deletion must not hold up clearing the composer.
            account.scope.launch(Dispatchers.IO) { account.deleteDraftIgnoreErrors(draftToDelete) }
        }
        onUiThread { cancel() }
    }

    suspend fun sendDraftSync() {
        if (message.text.toString().isBlank()) {
            account.deleteDraftIgnoreErrors(draftNote)
        } else if (accountViewModel.settings.automaticallyCreateDrafts()) {
            innerSendPost(draftTag.current)
        }
    }

    fun pickedMedia(list: ImmutableList<SelectedMedia>) {
        uploadState?.load(list)
    }

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    /**
     * Uploads a hand drawing ([uploadDrawing]), encrypted unless the chat's uploads were switched
     * to plain ones, and holds it to go out as a file message with the next send. When the server
     * refuses the encrypted GIF, the drawing is kept and the user is offered the same retry without
     * encryption as a picked file ([retryWithoutEncryption]). A held file message has no `imeta`
     * to hang a still on, so only the GIF goes up.
     */
    fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    ) {
        val uploadState = uploadState
        viewModelScope.launch(Dispatchers.IO) {
            if (uploadState == null) {
                drawing.delete()
                return@launch
            }
            val cipher = if (uploadState.encryptFiles) AESGCM() else null
            // Resolved here: the upload reports its failure from a plain callback.
            val retryTitle = loadStringRes(Res.string.failed_to_upload_encrypted_media_title)
            val retryExplainer = loadStringRes(Res.string.failed_to_upload_encrypted_media_message)
            var offeredRetry = false
            val uploaded =
                uploadDrawing(
                    drawing,
                    alt,
                    account,
                    accountViewModel.mediaUploader,
                    uploadState.mediaUploadTracker,
                    uploadStill = false,
                    onError,
                    server = uploadState.selectedServer,
                    encrypt = cipher,
                    keepFilesOnFailure = cipher != null,
                    onUploadError = { title, message ->
                        if (cipher == null) {
                            onError(title, message)
                        } else {
                            offeredRetry = true
                            pendingRetryDrawings.update { it + PendingDrawing(drawing, alt, onError) }
                            encryptedUploadErrorTitle = retryTitle
                            encryptedUploadErrorMessage = retryExplainer + "\n\n" + message
                        }
                    },
                )
            if (uploaded == null) {
                // Kept only for the retry; any other failure leaves nothing to retry.
                if (cipher != null && !offeredRetry) drawing.delete()
                return@launch
            }
            uploadsWaitingToBeSent += SuccessfulUploads(uploaded.gif, alt, null, cipher)
            draftTag.newVersion()
        }
    }

    /** A drawing whose encrypted upload failed, kept until the user retries without encryption or dismisses. */
    class PendingDrawing(
        val drawing: RenderedDrawing,
        val alt: String?,
        val onError: (title: String, message: String) -> Unit,
    )

    // Written by the upload's IO callback and taken on the main thread, so it is swapped atomically.
    private val pendingRetryDrawings = MutableStateFlow<List<PendingDrawing>>(emptyList())

    /**
     * Deletes the files of the drawings kept for a retry that will not happen. On the account's
     * scope, which outlives this one: it also runs from [onCleared].
     */
    private fun discardPendingDrawings() {
        val pending = pendingRetryDrawings.getAndUpdate { emptyList() }
        if (pending.isEmpty()) return
        accountViewModel.viewModelScope.launch(Dispatchers.IO) { pending.forEach { it.drawing.delete() } }
    }

    fun uploadAndHold(
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        onceUploaded: () -> Unit,
    ) {
        val uploadState = uploadState ?: return

        accountViewModel.launchSigner {
            ChatFileUploader(account).justUploadNIP17(
                uploadState,
                onError,
                onEncryptedUploadError = { title, message ->
                    encryptedUploadErrorTitle = title
                    encryptedUploadErrorMessage = message
                    pendingRetryMode = RetryMode.HOLD
                    pendingRetryOnError = onError
                    pendingRetryUploader = uploader
                    pendingRetryOnceUploaded = onceUploaded
                },
                uploader,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
            ) {
                uploadsWaitingToBeSent += it
                draftTag.newVersion()
                onceUploaded()
            }
        }
    }

    fun uploadAndSend(
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        onceUploaded: () -> Unit,
    ) {
        val room = room.value ?: return
        val uploadState = uploadState ?: return

        accountViewModel.launchSigner {
            ChatFileUploader(account).justUploadNIP17(
                uploadState,
                onError,
                onEncryptedUploadError = { title, message ->
                    encryptedUploadErrorTitle = title
                    encryptedUploadErrorMessage = message
                    pendingRetryMode = RetryMode.SEND
                    pendingRetryOnError = onError
                    pendingRetryUploader = uploader
                    pendingRetryOnceUploaded = onceUploaded
                },
                uploader,
                onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
            ) {
                ChatFileSender(room, account).sendNIP17(it)
                draftTag.newVersion()
                onceUploaded()
            }
        }
    }

    // Encrypted upload error state for retry dialog
    var encryptedUploadErrorTitle by mutableStateOf<String?>(null)
    var encryptedUploadErrorMessage by mutableStateOf<String?>(null)
    var pendingRetryMode by mutableStateOf<RetryMode?>(null)
    var pendingRetryOnError by mutableStateOf<((String, String) -> Unit)?>(null)
    var pendingRetryUploader by mutableStateOf<MediaUploader?>(null)
    var pendingRetryOnceUploaded by mutableStateOf<(() -> Unit)?>(null)

    enum class RetryMode { HOLD, SEND }

    fun dismissEncryptedUploadError() {
        discardPendingDrawings()
        encryptedUploadErrorTitle = null
        encryptedUploadErrorMessage = null
        pendingRetryMode = null
        pendingRetryOnError = null
        pendingRetryUploader = null
        pendingRetryOnceUploaded = null
    }

    fun retryWithoutEncryption() {
        // Taken before the dialog is dismissed, which would delete them. The dialog may stand for
        // both kept drawings and picked files: each kind is retried.
        val drawings = pendingRetryDrawings.getAndUpdate { emptyList() }
        val mode = pendingRetryMode
        val onError = pendingRetryOnError
        val uploader = pendingRetryUploader
        val onceUploaded = pendingRetryOnceUploaded
        val room = room.value
        val uploadState = uploadState

        dismissEncryptedUploadError()

        if (drawings.isNotEmpty()) {
            uploadState?.encryptFiles = false
            drawings.forEach { uploadDrawing(it.drawing, it.alt, it.onError) }
        }

        if (mode == null) return

        // HOLD only uploads; SEND also needs the room to send into.
        if (uploadState == null || uploader == null) return
        if (mode == RetryMode.SEND && room == null) return

        uploadState.encryptFiles = false

        accountViewModel.launchSigner {
            when (mode) {
                RetryMode.HOLD -> {
                    ChatFileUploader(account).justUploadNIP17Unencrypted(
                        uploadState,
                        onError ?: accountViewModel.toastManager::toast,
                        uploader,
                        onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                    ) {
                        uploadsWaitingToBeSent += it
                        draftTag.newVersion()
                        onceUploaded?.invoke()
                    }
                }

                RetryMode.SEND -> {
                    val sendRoom = room ?: return@launchSigner
                    ChatFileUploader(account).justUploadNIP17Unencrypted(
                        uploadState,
                        onError ?: accountViewModel.toastManager::toast,
                        uploader,
                        onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                    ) {
                        ChatFileSender(sendRoom, account).sendNIP17(it)
                        draftTag.newVersion()
                        onceUploaded?.invoke()
                    }
                }
            }
        }
    }

    private suspend fun innerSendPost(
        draftTag: String?,
        onSent: suspend () -> Unit = {},
    ) {
        val room = room.value ?: return

        if (draftTag == null && message.text.isBlank()) {
            // Only held files (a drawing, say): they go out as file messages, with no empty chat
            // message beside them.
            ChatFileSender(room, accountViewModel.account).sendNIP17(uploadsWaitingToBeSent)
            onSent()
            return
        }

        // Rewrites typed `@npub…`/`note1…` references into `nostr:` URIs. Only the rewritten text is
        // used: the tagger's pTags must NOT reach the event, since a NIP-17 room is defined by its
        // p-tags — adding a mentioned user would send the message to a different conversation.
        val tagger = NewMessageTagger(message.text.toString(), null, null, accountViewModel)
        tagger.run()

        val messageText = tagger.message
        val urls = findURLs(messageText)
        iMetaAttachments.awaitLinkedImages()
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())
        val emojis = accountViewModel.account.emoji.findEmojiTags(messageText)
        val geoHash = if (wantsToAddGeoHash) (pickedGeoHash ?: (location?.value as? LocationResult.Success)?.geoHash?.toString()) else null
        val message = messageText

        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null
        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null

        val replyHint = replyTo.value?.toEventHint<BaseDMGroupEvent>()

        val template =
            if (replyHint == null) {
                ChatMessageEvent.build(message, room.users.map { LocalCache.getOrCreateUser(it).toPTag() }) {
                    hashtags(findHashtags(message))
                    references(findURLs(message))
                    quotes(findNostrEventUris(message))

                    geoHash?.let { geohash(it) }
                    localZapRaiserAmount?.let { zapraiser(it) }
                    zapReceiver?.let { zapSplits(it) }
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            } else {
                ChatMessageEvent.reply(message, replyHint) {
                    hashtags(findHashtags(message))
                    references(findURLs(message))
                    quotes(findNostrEventUris(message))

                    geoHash?.let { geohash(it) }
                    localZapRaiserAmount?.let { zapraiser(it) }
                    zapReceiver?.let { zapSplits(it) }
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                }
            }

        if (draftTag != null) {
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag, template)
        } else {
            accountViewModel.account.sendNip17PrivateMessage(template, onSent)
        }

        if (draftTag == null) {
            ChatFileSender(room, accountViewModel.account).sendNIP17(uploadsWaitingToBeSent)
        }
    }

    fun cancel() {
        draftTag.rotate()

        message.setTextAndPlaceCursorAtEnd("")
        subject.setTextAndPlaceCursorAtEnd("")

        replyTo.value = null

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        pickedGeoHash = null
        wantsSecretEmoji = false

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.clearText()

        urlPreviews.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        uploadsWaitingToBeSent = emptyList()
        uploadState?.reset()
        discardPendingDrawings()

        dismissEncryptedUploadError()

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    override fun onMessageChanged() {
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

        draftTag.newVersion()
    }

    fun onToUsersChanged() {
        if (toUsers.selection.collapsed) {
            val lastWord = toUsers.currentWord()
            userSuggestionsMainMessage = UserSuggestionAnchor.TO_USERS
            userSuggestions?.processCurrentWord(lastWord)
        }

        // updateRoomFromUsersInput()

        draftTag.newVersion()
    }

    fun updateRoomFromUsersInput() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            val toUsersTagger = NewMessageTagger(toUsers.text.toString(), null, null, accountViewModel)
            toUsersTagger.run()

            val users = toUsersTagger.pTags?.mapTo(mutableSetOf()) { it.pubkeyHex }
            if (users.isNullOrEmpty()) {
                room.emit(null)
            } else {
                if (users != room.value?.users) {
                    room.emit(ChatroomKey(users))
                }
            }
        }
    }

    fun onSubjectChanged() {
        draftTag.newVersion()
    }

    override fun onForwardZapTextChanged() {
        if (forwardZapToEditting.selection.collapsed) {
            val lastWord = forwardZapToEditting.text.toString()
            userSuggestionsMainMessage = UserSuggestionAnchor.FORWARD_ZAPS
            userSuggestions?.processCurrentWord(lastWord)
        }
    }

    fun autocompleteWithUser(item: User) {
        userSuggestions?.let { userSuggestions ->
            when (userSuggestionsMainMessage) {
                UserSuggestionAnchor.MAIN_MESSAGE -> {
                    val lastWord = message.currentWord()
                    userSuggestions.replaceCurrentWord(message, lastWord, item)
                    urlPreviews.update(message.text.toString())
                }

                UserSuggestionAnchor.FORWARD_ZAPS -> {
                    forwardZapTo.value.addItem(item)
                    forwardZapToEditting.clearText()
                }

                UserSuggestionAnchor.TO_USERS -> {
                    val lastWord = toUsers.currentWord()
                    userSuggestions.replaceCurrentWord(toUsers, lastWord, item)
                    updateRoomFromUsersInput()
                }

                else -> {}
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)
        urlPreviews.update(message.text.toString())

        draftTag.newVersion()
    }

    fun autocompleteWithEmojiUrl(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaAttachments.downloadAndPrepare(item.link, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder)
        }

        message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message.text.toString())

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        (message.text.isNotBlank() || uploadsWaitingToBeSent.isNotEmpty()) &&
            uploadState?.mediaUploadTracker?.isUploading != true &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            (toUsers.text.isNotBlank()) &&
            uploadState?.multiOrchestrator == null &&
            recipientsMissingDmRelays.value.isEmpty()

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
        urlPreviews.update(message.text.toString())
    }

    override fun onCleared() {
        Log.d("Init") { "OnCleared: ${this::class.simpleName}" }
        discardPendingDrawings()
    }

    // NIP-04 sending is deprecated. NIP-17 is always used.

    override fun updateZapPercentage(
        index: Int,
        sliderValue: Float,
    ) {
        forwardZapTo.value.updatePercentage(index, sliderValue)

        draftTag.newVersion()
    }

    override fun updateZapFromText() {
        viewModelScope.launch(Dispatchers.IO) {
            val tagger = NewMessageTagger(message.text.toString(), emptyList(), emptyList(), accountViewModel)
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

    override fun toggleMarkAsSensitive() {
        wantsToMarkAsSensitive = !wantsToMarkAsSensitive
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
