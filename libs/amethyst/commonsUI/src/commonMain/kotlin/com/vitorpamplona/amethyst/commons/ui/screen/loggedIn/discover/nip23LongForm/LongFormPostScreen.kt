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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip23LongForm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_a_tag
import com.vitorpamplona.amethyst.commons.resources.add_header_image
import com.vitorpamplona.amethyst.commons.resources.article_description_placeholder
import com.vitorpamplona.amethyst.commons.resources.article_summary
import com.vitorpamplona.amethyst.commons.resources.edit_article
import com.vitorpamplona.amethyst.commons.resources.geohash_exclusive
import com.vitorpamplona.amethyst.commons.resources.geohash_exclusive_explainer
import com.vitorpamplona.amethyst.commons.resources.lightning_create_and_add_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_invoice
import com.vitorpamplona.amethyst.commons.resources.long_form_post_add_tag
import com.vitorpamplona.amethyst.commons.resources.long_form_post_remove_header_image
import com.vitorpamplona.amethyst.commons.resources.long_form_post_remove_tag
import com.vitorpamplona.amethyst.commons.resources.markdown_edit
import com.vitorpamplona.amethyst.commons.resources.markdown_preview
import com.vitorpamplona.amethyst.commons.resources.new_long_form_post
import com.vitorpamplona.amethyst.commons.resources.publish
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.resources.start_writing_article
import com.vitorpamplona.amethyst.commons.resources.tags_label
import com.vitorpamplona.amethyst.commons.resources.url_slug
import com.vitorpamplona.amethyst.commons.resources.url_slug_placeholder
import com.vitorpamplona.amethyst.commons.resources.your_article_title
import com.vitorpamplona.amethyst.commons.resources.zapraiser
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDatePicker
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeoHashPostSection
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerPostOptionTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolbar
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolsFormStartPadding
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.composerBody
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.ObserveInboxRelayListAndDisplayIfNotFound
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongFormPostScreen(
    draftId: HexKey? = null,
    versionId: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: LongFormPostViewModel = rememberViewModel { LongFormPostViewModel() }
    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, accountViewModel) {
        val draft = draftId?.let { LocalCache.getNoteIfExists(it) }
        val version = versionId?.let { LocalCache.getNoteIfExists(it) }
        postViewModel.load(draft, version)
        if (draftId == null && versionId == null) {
            postViewModel.applySignature()
        }
    }

    WatchAndLoadMyEmojiList(accountViewModel)

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    PlatformBackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            onUiThread { postViewModel.cancel() }
        }
        nav.popBack()
    }

    Scaffold(
        topBar = {
            // A new version of an article already out is an edit: Save, as every other editor says.
            ActionTopBar(
                postRes = if (versionId != null) Res.string.save else Res.string.publish,
                titleRes = if (versionId != null) Res.string.edit_article else Res.string.new_long_form_post,
                isActive = postViewModel::canPost,
                onPost = {
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSync()
                        nav.popBack()
                    }
                },
                onCancel = {
                    accountViewModel.launchSigner {
                        postViewModel.sendDraftSync()
                        onUiThread { postViewModel.cancel() }
                    }
                    nav.popBack()
                },
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
            MarkdownPostScreenBody(postViewModel, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownPostScreenBody(
    postViewModel: LongFormPostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ObserveInboxRelayListAndDisplayIfNotFound(accountViewModel, nav)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Size10dp,
                        end = Size10dp,
                    ).then(composerBody()),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState, reverseScrolling = true),
            ) {
                // Banner image area
                BannerImageArea(
                    coverImageUrl = postViewModel.coverImageUrl,
                    isUploading = postViewModel.isUploadingCoverImage,
                    accountViewModel = accountViewModel,
                    onImageSelected = {
                        postViewModel.uploadCoverImage(it, accountViewModel.mediaUploader, onError = accountViewModel.toastManager::toast)
                    },
                    onClear = {
                        postViewModel.coverImageUrl = ""
                        postViewModel.draftTag.newVersion()
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Title field — large, borderless
                SpellCheckedOutlinedTextField(
                    value = postViewModel.title,
                    onValueChange = {
                        postViewModel.title = it
                        postViewModel.draftTag.newVersion()
                    },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.your_article_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Size10dp),
                    textStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            textDirection = TextDirection.Content,
                        ),
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Words,
                        ),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                )

                // Summary section
                Text(
                    text = stringRes(Res.string.article_summary),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = Size10dp, top = 8.dp),
                )

                SpellCheckedOutlinedTextField(
                    value = postViewModel.summary,
                    onValueChange = {
                        postViewModel.summary = it
                        postViewModel.draftTag.newVersion()
                    },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.article_description_placeholder),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Size10dp, vertical = Size5dp),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    maxLines = 3,
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                )

                // URL Slug field
                Text(
                    text = stringRes(Res.string.url_slug),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = Size10dp, top = 8.dp),
                )

                OutlinedTextField(
                    value = postViewModel.slug,
                    onValueChange = {
                        postViewModel.slug = it
                        postViewModel.draftTag.newVersion()
                    },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.url_slug_placeholder),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Size10dp, vertical = Size5dp),
                    singleLine = true,
                    enabled = !postViewModel.isEditing,
                )

                // Tags section
                Text(
                    text = stringRes(Res.string.tags_label),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = Size10dp, top = 8.dp),
                )

                TagInputSection(
                    tags = postViewModel.tags,
                    onTagsChange = {
                        postViewModel.tags = it
                        postViewModel.draftTag.newVersion()
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = Size5dp))

                // Edit / Preview tabs
                SecondaryTabRow(
                    selectedTabIndex = if (postViewModel.showPreview) 1 else 0,
                ) {
                    Tab(
                        selected = !postViewModel.showPreview,
                        onClick = { postViewModel.showPreview = false },
                        text = { Text(stringRes(Res.string.markdown_edit)) },
                    )
                    Tab(
                        selected = postViewModel.showPreview,
                        onClick = { postViewModel.showPreview = true },
                        text = { Text(stringRes(Res.string.markdown_preview)) },
                    )
                }

                if (postViewModel.showPreview) {
                    // Markdown preview
                    val backgroundColor = remember { mutableStateOf(Color.Transparent) }
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(Size10dp),
                    ) {
                        LocalRichTextPlatform.current.Markdown(
                            content = postViewModel.message.text.toString(),
                            tags = EmptyTagList,
                            canPreview = true,
                            quotesLeft = 1,
                            backgroundColor = backgroundColor,
                            callbackUri = null,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                } else {
                    // Markdown editor
                    ThinPaddingTextField(
                        state = postViewModel.message,
                        onTextChanged = postViewModel::onMessageChanged,
                        onContentReceived = { uri, mimeType ->
                            postViewModel.selectImage(persistentListOf(SelectedMedia(uri, mimeType)))
                        },
                        inputTransformation = MentionPreservingInputTransformation,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Size10dp, vertical = Size5dp),
                        placeholder = {
                            Text(
                                text = stringRes(Res.string.start_writing_article),
                                color = MaterialTheme.colorScheme.placeholderText,
                            )
                        },
                        textStyle =
                            LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                textDirection = TextDirection.Content,
                            ),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                        spellCheck = true,
                        outputTransformation = rememberUrlUserTagOutputTransformation(),
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                    )
                }

                // Content warning section
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
                        GeoHashPostSection(postViewModel) {
                            SettingsRow(
                                Res.string.geohash_exclusive,
                                Res.string.geohash_exclusive_explainer,
                            ) {
                                Switch(postViewModel.wantsExclusiveGeoPost, onCheckedChange = { postViewModel.wantsExclusiveGeoPost = it })
                            }
                        }
                    }
                }

                // Forward zap section
                if (postViewModel.wantsForwardZapTo) {
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ForwardZapTo(postViewModel, accountViewModel)
                    }
                }

                // Image upload section
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

                // Zap raiser section
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

        // User suggestions
        postViewModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                postViewModel::autocompleteWithUser,
                accountViewModel,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        // Emoji suggestions
        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        // Bottom action bar
        MarkdownBottomRowActions(postViewModel)
    }
}

@Composable
private fun BannerImageArea(
    coverImageUrl: String,
    isUploading: Boolean,
    accountViewModel: AccountViewModel,
    onImageSelected: (SelectedMedia) -> Unit,
    onClear: () -> Unit,
) {
    var showGallerySelect by remember { mutableStateOf(false) }
    if (showGallerySelect) {
        GallerySelectSingle(
            onImageUri = { media ->
                showGallerySelect = false
                if (media != null) {
                    onImageSelected(media)
                }
            },
        )
    }

    if (coverImageUrl.isNotBlank()) {
        // Show the uploaded banner image
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = Size10dp, vertical = Size5dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showGallerySelect = true },
        ) {
            MyAsyncImage(
                imageUrl = coverImageUrl,
                contentDescription = stringRes(Res.string.add_header_image),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = Modifier.fillMaxSize(),
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = null,
            )
            // Close button to remove the image
            IconButton(
                onClick = onClear,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
            ) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.long_form_post_remove_header_image),
                    tint = Color.White,
                )
            }
        }
    } else {
        // Dashed border placeholder — clickable to open gallery
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = Size10dp, vertical = Size5dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        RoundedCornerShape(12.dp),
                    ).clickable(enabled = !isUploading) { showGallerySelect = true },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    MaterialSymbols.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringRes(Res.string.add_header_image),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagInputSection(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
) {
    var tagInput by remember { mutableStateOf("") }

    Row(
        verticalAlignment = CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Size10dp, vertical = Size5dp),
    ) {
        OutlinedTextField(
            value = tagInput,
            onValueChange = { tagInput = it },
            placeholder = {
                Text(
                    text = stringRes(Res.string.add_a_tag),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        val newTag = tagInput.trim().lowercase()
                        if (newTag.isNotBlank() && newTag !in tags) {
                            onTagsChange(tags + newTag)
                        }
                        tagInput = ""
                    },
                ),
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = {
                val newTag = tagInput.trim().lowercase()
                if (newTag.isNotBlank() && newTag !in tags) {
                    onTagsChange(tags + newTag)
                }
                tagInput = ""
            },
        ) {
            Icon(
                MaterialSymbols.Add,
                contentDescription = stringRes(Res.string.long_form_post_add_tag),
            )
        }
    }

    if (tags.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Size10dp, vertical = 4.dp),
        ) {
            tags.forEach { tag ->
                AssistChip(
                    onClick = { onTagsChange(tags - tag) },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            MaterialSymbols.Close,
                            contentDescription = stringRes(Res.string.long_form_post_remove_tag, tag),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun MarkdownBottomRowActions(postViewModel: LongFormPostViewModel) {
    ComposerToolbar(startPadding = ComposerToolsFormStartPadding) { _ ->
        ComposerMediaTools(postViewModel)
        ComposerPostOptionTools(postViewModel)
    }
}
