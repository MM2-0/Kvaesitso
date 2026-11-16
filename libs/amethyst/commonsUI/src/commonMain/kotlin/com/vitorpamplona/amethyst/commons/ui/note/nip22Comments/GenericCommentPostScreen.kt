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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.AudienceSelection
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCodeLocationBanner
import com.vitorpamplona.amethyst.commons.nip72ModCommunities.ui.CommunityRulesViolationBanner
import com.vitorpamplona.amethyst.commons.relayClient.communities.CommunityFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geo_post_change_place
import com.vitorpamplona.amethyst.commons.resources.geo_post_posting_to
import com.vitorpamplona.amethyst.commons.resources.lightning_create_and_add_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_invoice
import com.vitorpamplona.amethyst.commons.resources.post_anonymously
import com.vitorpamplona.amethyst.commons.resources.what_s_on_your_mind
import com.vitorpamplona.amethyst.commons.resources.zapraiser
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.OnIncomingShare
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberSharedMediaResolver
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.submitOnCtrlEnter
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDatePicker
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeoHashPostSection
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.AudienceFlap
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.AudienceSheet
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.rememberAudienceLists
import com.vitorpamplona.amethyst.commons.ui.note.creators.pow.PowOverrideButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.previews.DisplayPreviews
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerPostOptionTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolbar
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.composerBody
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.DisplayExternalId
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadCityName
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.platform.GeohashLocationPickerDialog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPickerButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.review.GitCodeLocation
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@Composable
fun ReplyCommentPostScreen(
    replyId: HexKey? = null,
    message: String? = null,
    attachment: String? = null,
    quoteId: HexKey? = null,
    draftId: HexKey? = null,
    reviewLocation: GitCodeLocation? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: CommentPostViewModel = rememberViewModel { CommentPostViewModel() }
    postViewModel.init(accountViewModel)

    val mediaResolver = rememberSharedMediaResolver()

    LaunchedEffect(postViewModel, accountViewModel) {
        replyId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.reply(it)
        }
        reviewLocation?.let { postViewModel.reviewLocation = it }
        draftId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.editFromDraft(it)
        }
        quoteId?.let { LocalCache.getNoteIfExists(it) }?.let {
            postViewModel.quote(it)
        }
        message?.ifBlank { null }?.let {
            postViewModel.message.setTextAndPlaceCursorAtEnd(it)
            postViewModel.onMessageChanged()
        }
        mediaResolver.resolve(attachment)?.let {
            postViewModel.selectImage(persistentListOf(it))
        }
        if (draftId == null) {
            postViewModel.applySignature()
        }
    }

    GenericCommentPostScreen(postViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericCommentPostScreen(
    postViewModel: CommentPostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAndLoadMyEmojiList(accountViewModel)

    // Microsoft's swift key sends Gifs as new actions
    OnIncomingShare(
        onText = postViewModel::addToMessage,
        onMedia = { postViewModel.selectImage(persistentListOf(it)) },
    )

    // NIP-9B: when replying into a NIP-72 community, mount the community feed
    // subscription so the latest kind:34551 rules document is fetched and
    // observed by the ViewModel for composer-side validation. No-op for
    // hashtag/geohash composers (replyingTo is null or not addressable there).
    (postViewModel.replyingTo as? AddressableNote)?.let { addressable ->
        if (addressable.event is com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent) {
            CommunityFilterAssemblerSubscription(addressable, accountViewModel.dataSources().community)
        }
    }

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    PlatformBackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            onUiThread { postViewModel.cancel() }
        }
        nav.popBack()
    }

    val onPost: () -> Unit = {
        // uses the accountViewModel scope to avoid cancelling this
        // function when the postViewModel is released
        accountViewModel.launchSigner {
            postViewModel.sendPostSync()
            nav.popBack()
        }
    }

    Scaffold(
        modifier = Modifier.submitOnCtrlEnter(isActive = postViewModel::canPost, onSubmit = onPost),
        topBar = {
            PostingTopBar(
                isActive = postViewModel::canPost,
                onCancel = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
                    accountViewModel.launchSigner {
                        postViewModel.sendDraftSync()
                        onUiThread { postViewModel.cancel() }
                    }
                    nav.popBack()
                },
                onPost = onPost,
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            GenericCommentPostBody(
                postViewModel,
                accountViewModel,
                nav,
            )
        }
    }
}

@Composable
private fun GenericCommentPostBody(
    postViewModel: CommentPostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()
    val audienceLists = rememberAudienceLists(accountViewModel)

    Column(Modifier.fillMaxSize()) {
        // Hosted outside the scrolling content: the sheet is its own window, so
        // it survives wherever the composer scrolls to.
        if (postViewModel.wantsToManageAudience) {
            AudienceSheet(
                audience = postViewModel.notifying?.toImmutableList() ?: persistentListOf(),
                mutedNotifies = postViewModel.mutedNotifies.toImmutableSet(),
                isPrivate = false,
                searchState = postViewModel.audienceSearchText,
                onSearchChanged = postViewModel::onAudienceSearchTextChanged,
                userSuggestions = postViewModel.userSuggestions,
                accountViewModel = accountViewModel,
                onAddUser = {
                    postViewModel.addAllToAudience(listOf(it))
                    postViewModel.audienceSearchText.clearText()
                    postViewModel.userSuggestions?.reset()
                },
                onAddList = { list, users -> postViewModel.addAllToAudience(users, list.id) },
                onDismiss = {
                    postViewModel.wantsToManageAudience = false
                    postViewModel.audienceSearchText.clearText()
                    postViewModel.userSuggestions?.reset()
                },
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Size10dp,
                        end = Size10dp,
                    ).then(composerBody()),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(scrollState, reverseScrolling = true)) {
                postViewModel.externalIdentity?.let {
                    if (it is GeohashId) {
                        // Geo-post: the interactive location channel (with retarget) replaces
                        // the static external-id marker so there's a single location control.
                        GeoPostLocationChannel(postViewModel)
                        Spacer(modifier = StdVertSpacer)
                    } else {
                        Row {
                            DisplayExternalId(it, accountViewModel, nav)
                            Spacer(modifier = StdVertSpacer)
                        }
                    }
                }

                postViewModel.reviewLocation?.let {
                    GitCodeLocationBanner(it, Modifier.padding(bottom = 8.dp))
                }

                postViewModel.replyingTo?.let {
                    Row {
                        NoteCompose(
                            baseNote = it,
                            modifier = MaterialTheme.colorScheme.replyModifier,
                            isQuotedNote = true,
                            unPackReply = ReplyRenderType.NONE,
                            makeItShort = true,
                            quotesLeft = 1,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                        Spacer(modifier = StdVertSpacer)
                    }
                }

                val audience = remember(postViewModel.notifying) { postViewModel.notifying?.toImmutableList() ?: persistentListOf() }
                val mutedNotifies = remember(postViewModel.mutedNotifies) { postViewModel.mutedNotifies.toImmutableSet() }
                val groupChips =
                    remember(postViewModel.notifyProvenance, audience, mutedNotifies, audienceLists) {
                        AudienceSelection
                            .activeGroupChips(
                                provenance = postViewModel.notifyProvenance,
                                audience =
                                    audience.mapNotNullTo(mutableSetOf()) {
                                        it.pubkeyHex.takeIf { hex -> hex !in mutedNotifies }
                                    },
                                lists = audienceLists,
                            ).toImmutableList()
                    }

                AudienceFlap(
                    audience = audience,
                    // A comment is never gift-wrapped, so the flap stays in its
                    // quiet notify form here.
                    isPrivate = false,
                    accountViewModel = accountViewModel,
                    mutedNotifies = mutedNotifies,
                    groupChips = groupChips,
                    onManage = { postViewModel.wantsToManageAudience = true },
                    onRemoveGroup = { postViewModel.removeListFromAudience(it) },
                    onToggleNotify = { postViewModel.toggleNotify(it) },
                )

                Row(
                    modifier = Modifier.padding(vertical = Size10dp),
                ) {
                    if (postViewModel.wantsAnonymousPost) {
                        IconButton(
                            modifier = Size35Modifier,
                            onClick = { postViewModel.wantsAnonymousPost = false },
                        ) {
                            Icon(
                                symbol = MaterialSymbols.NoAccounts,
                                contentDescription = stringRes(Res.string.post_anonymously),
                                modifier = Size30Modifier,
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier.clickable {
                                    postViewModel.wantsAnonymousPost = true
                                },
                        ) {
                            BaseUserPicture(
                                accountViewModel.userProfile(),
                                Size35dp,
                                accountViewModel = accountViewModel,
                            )
                        }
                    }
                    MessageField(
                        Res.string.what_s_on_your_mind,
                        postViewModel,
                        onContentReceived = { uri, mimeType ->
                            postViewModel.selectImage(
                                persistentListOf(
                                    SelectedMedia(uri, mimeType),
                                ),
                            )
                        },
                    )
                }

                DisplayPreviews(postViewModel.urlPreviews, postViewModel.iMetaAttachments, accountViewModel, nav)

                if (postViewModel.wantsToMarkAsSensitive) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ContentSensitivityExplainer(
                            description = postViewModel.contentWarningDescription,
                            onDescriptionChange = { postViewModel.contentWarningDescription = it },
                        )
                    }
                }

                if (postViewModel.wantsExpirationDate) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ExpirationDatePicker(postViewModel)
                    }
                }

                if (postViewModel.wantsToAddGeoHash) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        GeoHashPostSection(postViewModel)
                    }
                }

                if (postViewModel.wantsForwardZapTo) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ForwardZapTo(postViewModel, accountViewModel)
                    }
                }

                postViewModel.multiOrchestrator?.let {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ImageVideoDescription(
                            it,
                            accountViewModel.account.settings.defaultFileServer,
                            isUploading = postViewModel.mediaUploadTracker.isUploading,
                            onAdd = { alt, server, sensitiveContent, mediaQuality, useH265, stripMetadata, convertGifToMp4 ->
                                postViewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, accountViewModel.mediaUploader, useH265, stripMetadata, convertGifToMp4)
                                accountViewModel.account.settings.changeDefaultFileServer(server)
                            },
                            onDelete = postViewModel::deleteMediaToUpload,
                            onCancel = { postViewModel.multiOrchestrator = null },
                            accountViewModel = accountViewModel,
                        )
                    }
                }

                if (postViewModel.wantsInvoice) {
                    postViewModel.lnAddress()?.let { lud16 ->
                        Row(
                            verticalAlignment = CenterVertically,
                            modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                        ) {
                            InvoiceRequest(
                                lud16,
                                accountViewModel.account.userProfile(),
                                accountViewModel,
                                stringRes(id = Res.string.lightning_invoice),
                                stringRes(id = Res.string.lightning_create_and_add_invoice),
                                onNewInvoice = {
                                    postViewModel.insertAtCursor(it)
                                    postViewModel.wantsInvoice = false
                                },
                                onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                            )
                        }
                    }
                }

                if (postViewModel.wantsSecretEmoji) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            SecretEmojiRequest {
                                postViewModel.insertAtCursor(it)
                                postViewModel.wantsSecretEmoji = false
                            }
                        }
                    }
                }

                if (postViewModel.wantsZapRaiser && postViewModel.hasLnAddress()) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ZapRaiserRequest(
                            stringRes(id = Res.string.zapraiser),
                            postViewModel,
                        )
                    }
                }
            }
        }

        postViewModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                postViewModel::autocompleteWithUser,
                accountViewModel,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        // NIP-9B: surface the first community-rules violation found in the current draft.
        postViewModel.validationResult?.let { CommunityRulesViolationBanner(it) }

        BottomRowActions(postViewModel)
    }
}

/**
 * For a geo-post (a comment scoped to a geohash channel), shows which place it will post
 * to and lets the user retarget it via the map picker. Hidden for non-geo comments.
 */
@Composable
private fun GeoPostLocationChannel(postViewModel: CommentPostViewModel) {
    val scope = postViewModel.geohashScope ?: return
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(horizontal = Size10dp, vertical = 6.dp),
        verticalAlignment = CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = stringRes(Res.string.geo_post_posting_to),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LoadCityName(geohashStr = scope) { cityName ->
                Text(text = cityName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
        }
        TextButton(onClick = { showPicker = true }) {
            Text(stringRes(Res.string.geo_post_change_place))
        }
    }

    if (showPicker) {
        GeohashLocationPickerDialog(
            initialGeohash = scope,
            onDismiss = { showPicker = false },
            onConfirm = { cell ->
                postViewModel.setGeohashScope(cell)
                showPicker = false
            },
        )
    }
}

@Composable
private fun BottomRowActions(postViewModel: CommentPostViewModel) {
    ComposerToolbar { _ ->
        ComposerMediaTools(postViewModel)

        EmojiPickerButton(postViewModel.emojiSuggestions?.emojiPacks, postViewModel.message)

        ComposerPostOptionTools(postViewModel)

        PowOverrideButton(
            effectiveDifficulty = postViewModel.effectivePowDifficulty(),
            defaultDifficulty = postViewModel.defaultPowDifficulty(),
            isOverridden = postViewModel.powOverride != null,
            onSelect = { postViewModel.powOverride = it },
        )
    }
}
