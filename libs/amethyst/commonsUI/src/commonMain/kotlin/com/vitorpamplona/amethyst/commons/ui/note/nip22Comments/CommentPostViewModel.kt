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
package com.vitorpamplona.amethyst.commons.ui.note.nip22Comments

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
import com.vitorpamplona.amethyst.commons.service.pow.PoWReplay
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
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.IAudience
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.IComposerPostOptions
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.IZapField
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send.IMetaAttachments
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.UserSuggestionAnchor
import com.vitorpamplona.amethyst.commons.ui.text.appendSignature
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.text.insertUrlAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.text.replaceCurrentWord
import com.vitorpamplona.amethyst.commons.ui.text.setTextAndPlaceCursorAtBeginning
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.ui.uploads.uploadDrawingIntoText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.geohash.geohash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.getGeoHash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.hasGeohashes
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip30CustomEmoji.emojis
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip34Git.gitThreadRootId
import com.vitorpamplona.quartz.nip34Git.review.GitCodeLocation
import com.vitorpamplona.quartz.nip34Git.review.gitCodeLocation
import com.vitorpamplona.quartz.nip34Git.review.reviewedCommit
import com.vitorpamplona.quartz.nip34Git.review.reviewedFile
import com.vitorpamplona.quartz.nip34Git.review.reviewedLines
import com.vitorpamplona.quartz.nip34Git.review.tags.ReviewedLinesTag
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarning
import com.vitorpamplona.quartz.nip36SensitiveContent.contentWarningReason
import com.vitorpamplona.quartz.nip36SensitiveContent.isSensitive
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplits
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiser
import com.vitorpamplona.quartz.nip57Zaps.zapraiser.zapraiserAmount
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.rules.CommunityRulesValidator
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.scope
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
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Stable
open class CommentPostViewModel :
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            draftTag.versions.collectLatest {
                // don't save the first
                if (it > 0) {
                    val tag = draftTag.current
                    draftNote = account.getOrCreateDraftNote(tag)
                    // Post rotates the tag and then clears the composer. A save still queued from
                    // before that would see the empty text and delete the draft Post just saved
                    // for the post that is still mining, so it's skipped once the tag has moved on.
                    if (draftTag.current == tag) sendDraftSync()
                }
            }
        }
    }

    override lateinit var accountViewModel: AccountViewModel
    lateinit var account: Account

    var externalIdentity by mutableStateOf<ExternalId?>(null)
    var replyingTo: Note? by mutableStateOf(null)

    /** For an inline code review: the file, commit and lines of the diff this comment is about. */
    var reviewLocation: GitCodeLocation? by mutableStateOf(null)

    /** The geohash channel this comment is scoped to, or null when it isn't a geo-post. */
    val geohashScope: String?
        get() = (externalIdentity as? GeohashId)?.geohash

    /** Retarget a geo-post to a different location channel (from the map picker). */
    fun setGeohashScope(geohash: String) {
        externalIdentity = GeohashId(geohash)
    }

    // The signature pre-filled by applySignature(), so an untouched signature-only
    // message is treated as blank instead of auto-saved as a junk draft.
    private var appliedSignature: String? = null

    val iMetaAttachments = IMetaAttachments()

    var notifying by mutableStateOf<List<User>?>(null)

    // Members of the notifying list whose bell is off: they keep their chip
    // (so they are one tap away from being added back) but are dropped from
    // the extra notification p tags of the outgoing comment.
    override var mutedNotifies by mutableStateOf<Set<HexKey>>(emptySet())

    // IAudience maps onto `notifying`, which the draft loaders and the comment
    // builder already read under that name.
    override var audienceMembers: List<User>?
        get() = notifying
        set(value) {
            notifying = value
        }

    override val audienceSearchText = TextFieldState()
    override var wantsToManageAudience by mutableStateOf(false)
    override var notifyProvenance by mutableStateOf<Map<HexKey, Set<String>>>(emptyMap())

    override fun onAudienceChanged() = draftTag.newVersion()

    fun onAudienceSearchTextChanged() {
        if (audienceSearchText.selection.collapsed) {
            userSuggestionsMainMessage = UserSuggestionAnchor.NOTIFY
            userSuggestions?.processCurrentWord(audienceSearchText.text.toString())
        }
    }

    // NIP-9B: latest community rules document for the community we're posting into.
    // Null when the reply target is not a community, or no rules have been observed yet.
    var communityRules: CommunityRulesEvent? by mutableStateOf(null)
        private set
    private var rulesObserverJob: Job? = null

    // NIP-9B: latest validation outcome for the current draft.
    // Null = no violation (or rules not yet known); non-null = first violation found.
    var validationResult: CommunityRulesValidator.Violation? by mutableStateOf(null)
        private set

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

    var wantsAnonymousPost by mutableStateOf(false)

    // NIP-13 per-post override from the composer chip: null = follow account
    // settings, 0 = don't mine this post, >0 = mine at that difficulty.
    var powOverride by mutableStateOf<Int?>(null)

    fun effectivePowDifficulty(): Int? {
        if (!::accountViewModel.isInitialized) return null
        return accountViewModel.account.powDifficultyFor(CommentEvent.KIND, powOverride)
    }

    fun defaultPowDifficulty(): Int? {
        if (!::accountViewModel.isInitialized) return null
        return accountViewModel.account.powDifficultyFor(CommentEvent.KIND)
    }

    // A single ephemeral signer reused for the whole compose session so that media
    // uploads (Blossom/NIP-96 auth events) and the final anonymous post are all signed
    // by the same throwaway key, instead of leaking the real account's pubkey into the
    // upload authorization (and therefore into the returned media URL).
    private var anonymousSignerCache: NostrSigner? = null

    fun anonymousSigner(): NostrSigner = anonymousSignerCache ?: NostrSignerInternal(KeyPair()).also { anonymousSignerCache = it }

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

    fun newPostFor(externalIdentity: ExternalId) {
        this.externalIdentity = externalIdentity
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

    open fun reply(post: Note) {
        this.replyingTo = post
        this.externalIdentity = (post.event as? CommentEvent)?.scope()
        mutedNotifies = emptySet()
        notifyProvenance = emptyMap()
        (post.event as? ZapReceiptEvent)?.let { zap ->
            notifying = listOfNotNull(zapSenderToNotify(zap))
        }
        observeCommunityRules(post)
    }

    /**
     * Zap receipts (kind 9735) are signed by the recipient's lightning provider, so
     * the reply tags built from the receipt alone would notify the custodian instead
     * of the person who zapped. The actual sender is the author of the embedded zap
     * request. Requests carrying an `anon` tag (anonymous or private zaps) are signed
     * by an ephemeral key: skip those — tagging the throwaway key is useless, and
     * tagging the decrypted sender would publicly expose a private zapper.
     */
    private fun zapSenderToNotify(zapEvent: ZapReceiptEvent): User? {
        val request = zapEvent.zapRequest ?: return null
        if (request.hasAnonTag()) return null
        if (request.pubKey == account.signer.pubKey) return null
        return LocalCache.checkGetOrCreateUser(request.pubKey)
    }

    /**
     * Subscribes to the latest [CommunityRulesEvent] when [target] is a NIP-72
     * community, and clears the rules state otherwise. Re-evaluates the current
     * draft against any rules that arrive (NIP-9B composer-side validation).
     */
    private fun observeCommunityRules(target: Note) {
        rulesObserverJob?.cancel()
        rulesObserverJob = null
        communityRules = null
        validationResult = null

        val targetEvent = target.event as? CommunityDefinitionEvent ?: return
        val communityAddress = targetEvent.addressTag()
        val authors = targetEvent.moderatorKeys()

        val filter =
            Filter(
                kinds = listOf(CommunityRulesEvent.KIND),
                authors = authors.sorted(),
                tags = mapOf("a" to listOf(communityAddress)),
                limit = 5,
            )

        rulesObserverJob =
            viewModelScope.launch(Dispatchers.IO) {
                LocalCache.observeEvents<CommunityRulesEvent>(filter).collectLatest { events ->
                    val latest =
                        events
                            .filter { it.communityAddress() == communityAddress }
                            .maxByOrNull { it.createdAt }
                    communityRules = latest
                    revalidateDraft()
                }
            }
    }

    /**
     * Runs the NIP-9B validator against the current draft and updates
     * [validationResult]. Web-of-trust gates and per-day quota checks are deferred
     * to a follow-up (see NIP-9B track), so [postsTodayByKind] and `wot` are null
     * here; the validator skips those checks cleanly.
     */
    private fun revalidateDraft() {
        val rules = communityRules
        if (rules == null) {
            validationResult = null
            return
        }

        if (!::account.isInitialized) return

        validationResult =
            validateDraft(
                rules = rules,
                author = account.signer.pubKey,
                draftContent = message.text.toString(),
            )
    }

    companion object {
        /**
         * Pure helper that runs the NIP-9B validator for a `kind:1111` reply with
         * [draftContent]. Sizes are estimated from the content bytes; tags add bytes,
         * so this can under-count on the boundary, but relays still enforce the real
         * cap. Good enough for a pre-send preview.
         *
         * Web-of-trust gates and per-day quota lookups are deferred to a follow-up;
         * see the NIP-9B track in the issue tracker.
         */
        internal fun validateDraft(
            rules: CommunityRulesEvent,
            author: String,
            draftContent: String,
        ): CommunityRulesValidator.Violation? =
            CommunityRulesValidator(rules).validate(
                author = author,
                kind = CommentEvent.KIND,
                sizeBytes = draftContent.encodeToByteArray().size,
                postsTodayByKind = null,
                wot = null,
            )
    }

    open fun quote(quote: Note) {
        message.setTextAndPlaceCursorAtBeginning(message.text.toString() + "\nnostr:${quote.toNEvent()}")

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

    /**
     * Pre-fills the signature from Compose Settings at the end of the message. Skipped by
     * callers when loading a draft (the draft content already carries — or deliberately
     * omits — a signature).
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
        if (draftEvent !is CommentEvent) return

        loadFromDraft(draftEvent)
    }

    private fun loadFromDraft(draftEvent: CommentEvent) {
        this.externalIdentity = draftEvent.scope()
        // An inline review comment's draft keeps its place in the diff.
        reviewLocation = draftEvent.gitCodeLocation()

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

        val draftExpiration = draftEvent.expiration()
        wantsExpirationDate = draftExpiration != null
        expirationDate = draftExpiration ?: TimeUtils.oneDayAhead()

        val zapraiser = draftEvent.zapraiserAmount()
        wantsZapRaiser = zapraiser != null
        zapRaiserAmount.value = null
        if (zapraiser != null) {
            zapRaiserAmount.value = zapraiser
        }

        val replyAddress = draftEvent.replyAddress()

        if (replyAddress.isNotEmpty()) {
            replyingTo = LocalCache.getOrCreateAddressableNote(replyAddress.first())
        } else {
            draftEvent.replyingTo()?.let {
                replyingTo = LocalCache.getOrCreateNote(it)
            }
        }
        replyingTo?.let { observeCommunityRules(it) }

        wantsToAddGeoHash = draftEvent.hasGeohashes()
        pickedGeoHash = draftEvent.getGeoHash()

        notifying = draftEvent.rootAuthorKeys().mapNotNull { LocalCache.checkGetOrCreateUser(it) } +
            draftEvent.replyAuthorKeys().mapNotNull { LocalCache.checkGetOrCreateUser(it) }
        mutedNotifies = emptySet()
        notifyProvenance = emptyMap()

        // Replies to zaps notify the zap sender through a plain p tag (the receipt's
        // author keys above are the lightning provider). The sender chip always comes
        // back; a missing p tag in the draft means the user muted their bell.
        (replyingTo?.event as? ZapReceiptEvent)?.let { zap ->
            zapSenderToNotify(zap)?.let { sender ->
                notifying = ((notifying ?: emptyList()) + sender).distinct()
                if (!draftEvent.replyAuthorKeys().contains(sender.pubkeyHex)) {
                    mutedNotifies = mutedNotifies + sender.pubkeyHex
                }
            }
        }

        if (forwardZapTo.value.items.isNotEmpty()) {
            wantsForwardZapTo = true
        }

        message.setTextAndPlaceCursorAtEnd(draftEvent.content)

        iMetaAttachments.addAll(draftEvent.imetas())

        urlPreviews.update(message.text.toString())
    }

    suspend fun sendPostSync() {
        val template = createTemplate() ?: return

        // captured before cancel() resets the chip
        val chosenPow = powOverride

        // A mined post leaves the phone minutes after this returns — much later if the
        // app is backgrounded and frozen — and the draft is its only user-visible copy
        // until then. The auto-save is debounced and cancel() below drops the pending
        // save, so the last second of typing would never reach it: flush it now.
        if (accountViewModel.account.powDifficultyFor(template.kind, chosenPow) != null) {
            draftNote = account.getOrCreateDraftNote(draftTag.current)
            sendDraftSync()
        }

        val draftToDelete = draftNote
        val anonymous = wantsAnonymousPost
        onUiThread { cancel() }

        // Draft deletion lives INSIDE each publish continuation: when the post
        // is mined first, the draft must survive until the mined event is
        // actually signed and dispatched — a cancelled or process-killed
        // mining job would otherwise have destroyed the only copy of the text.

        // A reply within a NIP-29 group is group content: pin it to the group's
        // host relay (the relay the thread was seen on) instead of the author's
        // outbox, so it reaches the group and — for a private/closed group — is
        // never leaked to unrelated relays.
        val replyGroupId = replyingTo?.event?.takeIf { it.isGroupScoped() }?.groupId()
        val groupHostRelays =
            replyGroupId?.let { gid ->
                // Prefer the group's cached channel host (the pinned host relay), then the relays
                // the parent was actually seen on. Never fall through to the author's outbox.
                LocalCache.relayGroupChannels
                    .filter { key, _ -> key.id == gid }
                    .map { it.groupId.relayUrl }
                    .distinct()
                    .takeIf { it.isNotEmpty() }
                    ?: replyingTo?.relays?.takeIf { it.isNotEmpty() }
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
        } else if (replyGroupId != null) {
            // Group content: route to the resolved host. If it couldn't be resolved, publish to the
            // parent's relays (possibly empty) rather than broadcasting to the outbox — better to
            // under-deliver a group reply than to leak group participation to unrelated relays.
            val relays = groupHostRelays ?: replyingTo?.relays.orEmpty()
            accountViewModel.account.sendMined(template, PoWReplay.ToRelays(relays), chosenPow) { readyTemplate ->
                accountViewModel.account.signAndSendPrivatelyOrBroadcast(readyTemplate) { relays }
                accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            }
        } else {
            accountViewModel.account.sendMined(template, PoWReplay.Broadcast(), chosenPow) { readyTemplate ->
                accountViewModel.account.signAndComputeBroadcast(readyTemplate)
                accountViewModel.account.deleteDraftIgnoreErrors(draftToDelete)
            }
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
        val tagger =
            NewMessageTagger(
                message = message.text.toString().trim(),
                dao = accountViewModel,
            )
        tagger.run()

        val geoHash = (if (wantsToAddGeoHash) pickedGeoHash else null) ?: (location?.value as? LocationResult.Success)?.geoHash?.toString()

        val emojis = account.emoji.findEmojiTags(tagger.message)
        val urls = findURLs(tagger.message)
        iMetaAttachments.awaitLinkedImages()
        val usedAttachments = iMetaAttachments.filterIsIn(urls.toSet())

        val zapReceiver = if (wantsForwardZapTo) forwardZapTo.value.toZapSplitSetup() else null
        val localZapRaiserAmount = if (wantsZapRaiser) zapRaiserAmount.value else null
        val contentWarningReason = if (wantsToMarkAsSensitive) contentWarningDescription else null
        val localExpirationDate = if (wantsExpirationDate) expirationDate else null

        val replyingTo = replyingTo
        val replyingToEvent = replyingTo?.event

        val template =
            if (replyingTo != null) {
                val eventHint = replyingTo.toEventHint<Event>() ?: return null

                val replyTags: TagArrayBuilder<CommentEvent>.() -> Unit = {
                    // A reply inside a NIP-29 group thread must carry the group's `h`
                    // tag, or the host relay won't accept it and no other member (or
                    // other NIP-29 client, e.g. Flotilla) will see it. Inherit it from
                    // the event being replied to; a no-op for non-group comments.
                    replyingToEvent?.groupId()?.let { hTag(it) }

                    // An inline review comment, as gitworkshop writes it: the file, the commit
                    // the lines are read at, the lines (`del` for removed ones), and each
                    // maintainer's repository, so it is found under every coordinate.
                    reviewLocation?.let { location ->
                        reviewedFile(location.path)
                        location.commit?.let { reviewedCommit(it) }
                        location.startLine?.let { start -> reviewedLines(ReviewedLinesTag(start, location.endLine ?: start, location.removedSide)) }
                        replyingToEvent?.let { item ->
                            LocalCache.gitAuthority.coordinatesToTag(item).forEach { add(QAddressableTag.assemble(it, null)) }
                        }
                        alt("Inline code review comment on ${location.path}")
                    }

                    val notifyPTags = tagger.pTags?.let { pTagList -> pTagList.map { it.toPTag() } } ?: emptyList()

                    val extraNotificationAuthors =
                        if (replyingToEvent is CommunityDefinitionEvent) {
                            replyingToEvent.moderatorKeys().mapNotNull {
                                if (it != replyingToEvent.pubKey) {
                                    LocalCache.checkGetOrCreateUser(it)?.toPTag()
                                } else {
                                    null
                                }
                            }
                        } else if (replyingToEvent is ZapReceiptEvent) {
                            val sender = zapSenderToNotify(replyingToEvent)
                            // The sender's chip stays in the list; a muted bell means
                            // the user doesn't want to ping them, so don't tag them.
                            if (sender != null && sender.pubkeyHex !in mutedNotifies) {
                                listOf(sender.toPTag())
                            } else {
                                emptyList()
                            }
                        } else {
                            emptyList()
                        }

                    notify((notifyPTags + extraNotificationAuthors).distinctBy { it.pubKey })

                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    localZapRaiserAmount?.let { zapraiser(it) }
                    zapReceiver?.let { zapSplits(it) }
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                    geoHash?.let { geohash(it) }
                }

                // NIP-34: a status, PR update, cover note or legacy reply is part of an issue's or
                // PR's thread, which clients read by the item (`#E`). Root the comment there, still
                // answering the event it replies to; rooted at that event, nobody would find it.
                val gitItem =
                    replyingToEvent
                        ?.gitThreadRootId()
                        ?.takeIf { it != replyingToEvent.id }
                        ?.let { LocalCache.getNoteIfExists(it)?.toEventHint<Event>() }

                if (gitItem != null) {
                    CommentEvent.replyBuilder(tagger.message, root = gitItem, replyingTo = eventHint, initializer = replyTags)
                } else {
                    CommentEvent.replyBuilder(tagger.message, replyingTo = eventHint, initializer = replyTags)
                }
            } else {
                val externalIdentity = externalIdentity ?: return null
                CommentEvent.replyExternalIdentity(
                    msg = tagger.message,
                    extId = externalIdentity,
                ) {
                    tagger.pTags?.let { pTagList -> notify(pTagList.map { it.toPTag() }) }

                    hashtags(findHashtags(tagger.message))
                    references(findURLs(tagger.message))
                    quotes(findNostrUris(tagger.message))

                    localZapRaiserAmount?.let { zapraiser(it) }
                    zapReceiver?.let { zapSplits(it) }
                    contentWarningReason?.let { contentWarning(it) }
                    localExpirationDate?.let { expiration(it) }

                    emojis(emojis)
                    imetas(usedAttachments)
                    geoHash?.let { geohash(it) }
                }
            }

        return template
    }

    /** Uploads a hand drawing ([uploadDrawing]) and puts its GIF in the comment. */
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
        stripMetadata: Boolean,
        convertGifToMp4: Boolean,
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
                    useH265 = useH265,
                    stripMetadata = stripMetadata,
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
                val errorMessages =
                    results.errors
                        .map {
                            loadStringRes(
                                it.errorResource,
                                *it.params,
                            )
                        }.distinct()

                onError(loadStringRes(Res.string.failed_to_upload_media_no_details), errorMessages.joinToString(".\n"))
            }

            mediaUploadTracker.finishUpload()
        }
    }

    open fun cancel() {
        draftTag.rotate()
        reviewLocation = null

        message.setTextAndPlaceCursorAtEnd("")

        rulesObserverJob?.cancel()
        rulesObserverJob = null
        communityRules = null
        validationResult = null

        replyingTo = null
        externalIdentity = null

        multiOrchestrator = null
        mediaUploadTracker.finishUpload()

        notifying = null
        mutedNotifies = emptySet()
        resetAudienceEditor()

        wantsInvoice = false
        wantsZapRaiser = false
        zapRaiserAmount.value = null

        wantsForwardZapTo = false
        wantsToMarkAsSensitive = false
        contentWarningDescription = ""
        wantsToAddGeoHash = false
        pickedGeoHash = null
        wantsSecretEmoji = false
        wantsAnonymousPost = false
        anonymousSignerCache = null
        powOverride = null

        forwardZapTo.value = SplitBuilder()
        forwardZapToEditting.clearText()

        urlPreviews.reset()

        userSuggestions?.reset()
        userSuggestionsMainMessage = null

        iMetaAttachments.reset()

        emojiSuggestions?.reset()
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
    }

    override fun onMessageChanged() {
        urlPreviews.update(message.text.toString())
        revalidateDraft()

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
            } else if (userSuggestionsMainMessage == UserSuggestionAnchor.NOTIFY) {
                addAllToAudience(listOf(item))
                audienceSearchText.clearText()
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
            iMetaAttachments.downloadAndPrepare(item.link, accountViewModel.mediaUploader, accountViewModel.httpClientBuilder)
        }

        message.replaceCurrentWord(wordToInsert)
        urlPreviews.update(message.text.toString())

        emojiSuggestions?.reset()

        draftTag.newVersion()
    }

    fun canPost(): Boolean =
        message.text.toString().isNotBlank() &&
            !mediaUploadTracker.isUploading &&
            !wantsInvoice &&
            (!wantsZapRaiser || zapRaiserAmount.value != null) &&
            multiOrchestrator == null &&
            validationResult == null

    fun insertAtCursor(newElement: String) {
        message.insertUrlAtCursor(newElement)
    }

    override fun selectImage(uris: ImmutableList<SelectedMedia>) {
        multiOrchestrator = MultiOrchestrator(uris)
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

    override fun locationFlow(): StateFlow<LocationResult> {
        if (location == null) {
            location = locationManager().geohashStateFlow
        }

        return location!!
    }

    override fun locationManager(): DeviceLocation = accountViewModel.host.deviceLocation
}
