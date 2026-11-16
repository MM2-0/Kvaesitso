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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip99Classifieds

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
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.upload.SuspendableConfirmation
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.ILocationGrabber
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerPostOptions
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawing
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip92IMeta.imetas
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.ProductImageMeta
import com.vitorpamplona.quartz.nip99Classifieds.image
import com.vitorpamplona.quartz.nip99Classifieds.tags.ConditionTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
open class NewProductViewModel :
    ViewModel(),
    ILocationGrabber,
    IMessageField,
    IComposerMediaTools,
    IComposerPostOptions,
    IZapField,
    IZapRaiser,
    IExpiration {
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
                    draftNote = account.getOrCreateDraftNote(draftTag.current)
                    accountViewModel.launchSigner {
                        sendDraftSync()
                    }
                }
            }
        }
    }

    var productImages by mutableStateOf<List<ProductImageMeta>>(emptyList())
    val iMetaDescription = IMetaAttachments()

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

    // Classifieds
    val title = TextFieldState()
    val price = TextFieldState()
    val locationText = TextFieldState()
    val category = TextFieldState()
    var condition by mutableStateOf(ConditionTag.CONDITION.USED_LIKE_NEW)

    // Invoices
    override var canAddInvoice by mutableStateOf(false)
    override var wantsInvoice by mutableStateOf(false)

    override var wantsSecretEmoji by mutableStateOf(false)

    // Forward Zap to
    override var wantsForwardZapTo by mutableStateOf(false)
    override var forwardZapTo = mutableStateOf<SplitBuilder<User>>(SplitBuilder())
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
    override val zapRaiserAmount = mutableStateOf<Long?>(null)

    var relayList by mutableStateOf<ImmutableList<NormalizedRelayUrl>?>(null)

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

    open fun quote(quote: Note) {
        val accountViewModel = accountViewModel

        message.setTextAndPlaceCursorAtEnd(message.text.toString() + "\nnostr:${quote.toNEvent()}")

        quote.author?.let { quotedUser ->
            if (quotedUser.pubkeyHex != accountViewModel.userProfile().pubkeyHex) {
                if (forwardZapTo.value.items.none { it.key.pubkeyHex == quotedUser.pubkeyHex }) {
                    forwardZapTo.value.addItem(quotedUser)
                }
                if (forwardZapTo.value.items.none { it.key.pubkeyHex == accountViewModel.userProfile().pubkeyHex }) {
                    forwardZapTo.value.addItem(accountViewModel.userProfile())
                }

                val pos = forwardZapTo.value.items.indexOfFirst { it.key.pubkeyHex == quotedUser.pubkeyHex }
                forwardZapTo.value.updatePercentage(pos, 0.9f)
            }
        }

        if (!forwardZapTo.value.items.isEmpty()) {
            wantsForwardZapTo = true
        }

        urlPreviews.update(message.text.toString())
    }

    private fun loadFromDraft(draft: Note) {
        val draftEvent = draft.event ?: return
        if (draftEvent !is ClassifiedsEvent) return

        loadFromDraft(draftEvent)
    }

    private fun loadFromDraft(draftEvent: ClassifiedsEvent) {
        val localfowardZapTo = draftEvent.tags.filter { it.size > 1 && it[0] == "zap" }
        forwardZapTo.value = SplitBuilder()
        localfowardZapTo.forEach {
            val user = LocalCache.getOrCreateUser(it[1])
            val value = it.last().toFloatOrNull() ?: 0f
            forwardZapTo.value.addItem(user, value)
        }
        forwardZapToEditting.clearText()
        wantsForwardZapTo = localfowardZapTo.isNotEmpty()

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

        title.setTextAndPlaceCursorAtEnd(draftEvent.title() ?: "")
        price.setTextAndPlaceCursorAtEnd(draftEvent.price()?.amount ?: "")
        category.setTextAndPlaceCursorAtEnd(draftEvent.categories().firstOrNull() ?: "")
        locationText.setTextAndPlaceCursorAtEnd(draftEvent.location() ?: "")
        condition = draftEvent.conditionValid() ?: ConditionTag.CONDITION.USED_LIKE_NEW

        val imageSet = draftEvent.images().toMutableSet()

        draftEvent.imetas().forEach {
            if (it.url in imageSet) {
                productImages = productImages + ProductImageMeta.parse(it)
                imageSet.remove(it.url)
            } else {
                iMetaDescription.add(it)
            }
        }

        imageSet.forEach {
            productImages = productImages + ProductImageMeta(it)
        }

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)

        urlPreviews.update(message.text.toString())
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return

        val draftToDelete = draftNote
        onUiThread { cancel() }

        accountViewModel.account.signAndSendPrivatelyOrBroadcast(template, relayList = { relayList })
        accountViewModel.viewModelScope.launch(Dispatchers.IO) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
        }
    }

    suspend fun sendDraftSync() {
        if (message.text.toString().isBlank()) {
            accountViewModel.account.deleteDraftIgnoreErrors(draftNote)
        } else if (accountViewModel.settings.automaticallyCreateDrafts()) {
            val template = createTemplate() ?: return
            accountViewModel.account.createAndSendDraftIgnoreErrors(draftTag.current, template)
        }
    }

    private suspend fun createTemplate(): EventTemplate<out Event>? {
        val accountViewModel = accountViewModel

        val tagger =
            NewMessageTagger(
                message = message.text.toString(),
                dao = accountViewModel,
            )
        tagger.run()

        val emojis = account.emoji.findEmojiTags(tagger.message)
        val urls = findURLs(tagger.message)
        iMetaDescription.awaitLinkedImages()
        val usedAttachments = iMetaDescription.filterIsIn(urls.toSet()) + productImages.map { it.toIMeta() }

        val geoHash = if (wantsToAddGeoHash) (pickedGeoHash ?: (location?.value as? LocationResult.Success)?.geoHash?.toString()) else null

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null
        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null

        val quotes = findNostrUris(tagger.message)

        val template =
            ClassifiedsEvent.build(
                title.text.toString(),
                PriceTag(price.text.toString(), "SATS", null),
                tagger.message,
                locationText.text.toString().ifBlank { null },
                condition,
            ) {
                productImages.forEach { image(it.url) }

                hashtags(listOfNotNull(category.text.toString().ifBlank { null }) + findHashtags(tagger.message))
                quotes(quotes)

                geoHash?.let { geohash(it) }
                localZapRaiserAmount?.let { zapraiser(it) }
                zapReceiver?.let { zapSplits(it) }
                contentWarningReason?.let { contentWarning(it) }
                localExpirationDate?.let { expiration(it) }

                emojis(emojis)
                imetas(usedAttachments)
                references(urls)
            }

        return template
    }

    /** Uploads a hand drawing ([uploadDrawing]) and adds its GIF to the product's pictures. */
    override fun uploadDrawing(
        drawing: RenderedDrawing,
        alt: String?,
        onError: (title: String, message: String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // A listing's pictures have no `image` preview, so the drawing's still would be an orphan.
            val uploaded = uploadDrawing(drawing, alt, account, accountViewModel.mediaUploader, mediaUploadTracker, uploadStill = false, onError) ?: return@launch
            val header = uploaded.gif.fileHeader
            productImages = productImages +
                ProductImageMeta(
                    url = uploaded.link,
                    mimeType = header.mimeType,
                    blurhash = header.blurHash?.blurhash,
                    dimension = header.dim,
                    alt = alt,
                    hash = header.hash,
                    size = header.size,
                    thumbhash = header.thumbHash?.thumbhash,
                )
        }
    }

    fun upload(
        alt: String?,
        contentWarningReason: String?,
        mediaQuality: Int,
        server: ServerName,
        onError: (title: String, message: String) -> Unit,
        uploader: MediaUploader,
        useH265: Boolean,
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val myAccount = account
            val myMultiOrchestrator = multiOrchestrator ?: return@launch

            mediaUploadTracker.startUpload(myMultiOrchestrator.hasNonMedia())

            val results =
                myMultiOrchestrator.upload(
                    alt,
                    contentWarningReason,
                    CompressorQuality.fromSlider(mediaQuality),
                    server,
                    myAccount,
                    uploader,
                    useH265 = useH265,
                    stripMetadata = stripMetadata,
                    onStrippingFailed = strippingFailureConfirmation::awaitConfirmation,
                    convertGifToMp4 = convertGifToMp4,
                )

            if (results.allGood) {
                val urls =
                    results.successful.mapNotNull { finished ->
                        val uploaded = finished.result
                        if (uploaded is UploadOrchestrator.OrchestratorResult.ServerResult) {
                            if (uploaded.fileHeader.mimeType
                                    ?.startsWith("image") == true
                            ) {
                                productImages = productImages +
                                    ProductImageMeta(
                                        url = uploaded.url,
                                        mimeType = uploaded.fileHeader.mimeType,
                                        blurhash =
                                            uploaded.fileHeader.blurHash
                                                ?.blurhash,
                                        dimension = uploaded.fileHeader.dim,
                                        alt = alt,
                                        hash = uploaded.fileHeader.hash,
                                        size = uploaded.fileHeader.size,
                                        thumbhash =
                                            uploaded.fileHeader.thumbHash
                                                ?.thumbhash,
                                    )
                            } else {
                                iMetaDescription.add(uploaded, alt, contentWarningReason)

                                uploaded.url
                            }
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

    open fun cancel() {
        draftTag.rotate()

        message.setTextAndPlaceCursorAtEnd("")

        multiOrchestrator = null
        mediaUploadTracker.finishUpload()

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        condition = ConditionTag.CONDITION.USED_LIKE_NEW
        locationText.setTextAndPlaceCursorAtEnd("")
        title.setTextAndPlaceCursorAtEnd("")
        category.setTextAndPlaceCursorAtEnd("")
        price.setTextAndPlaceCursorAtEnd("")

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

        productImages = emptyList()
        iMetaDescription.reset()

        emojiSuggestions?.reset()

        reloadRelaySet()
    }

    fun reloadRelaySet() {
        relayList =
            accountViewModel.account.outboxRelays.flow.value
                .toImmutableList()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
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
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.FORWARD_ZAPS) {
                forwardZapTo.value.addItem(item)
                forwardZapToEditting.clearText()
            }

            userSuggestionsMainMessage = null
            userSuggestions.reset()
        }

        draftTag.newVersion()
    }

    open fun autocompleteWithEmoji(item: EmojiPackState.EmojiMedia) {
        emojiSuggestions?.autocompleteInto(message, item)
        urlPreviews.update(message.text.toString())

        draftTag.newVersion()
    }

    open fun autocompleteWithEmojiUrl(item: EmojiPackState.EmojiMedia) {
        val wordToInsert = item.link + " "

        viewModelScope.launch(Dispatchers.IO) {
            iMetaDescription.downloadAndPrepare(item.link, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder)
        }

        message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message.text.toString())

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        message.text.isNotBlank() &&
            !mediaUploadTracker.isUploading &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            title.text.isNotBlank() &&
            price.text.isNotBlank() &&
            category.text.isNotBlank() &&
            multiOrchestrator == null

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
    }

    override fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
    }

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun onCleared() {
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

    fun onTitleChanged() {
        draftTag.newVersion()
    }

    fun onPriceChanged() {
        draftTag.newVersion()
    }

    fun updateCondition(newCondition: ConditionTag.CONDITION) {
        condition = newCondition
        draftTag.newVersion()
    }

    fun updateCategory(text: String) {
        category.setTextAndPlaceCursorAtEnd(text)
        draftTag.newVersion()
    }

    fun onLocationChanged() {
        draftTag.newVersion()
    }

    override fun locationManager(): DeviceLocation = accountViewModel.host.deviceLocation
}
