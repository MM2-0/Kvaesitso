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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.BooleanType
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.AudienceSelection
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.audience_send_privately
import com.vitorpamplona.amethyst.commons.resources.disable_poll
import com.vitorpamplona.amethyst.commons.resources.disable_private_note
import com.vitorpamplona.amethyst.commons.resources.geohash_exclusive
import com.vitorpamplona.amethyst.commons.resources.geohash_exclusive_explainer
import com.vitorpamplona.amethyst.commons.resources.lightning_create_and_add_invoice
import com.vitorpamplona.amethyst.commons.resources.lightning_invoice
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_question
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_subject
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_subject_caption
import com.vitorpamplona.amethyst.commons.resources.poll
import com.vitorpamplona.amethyst.commons.resources.post
import com.vitorpamplona.amethyst.commons.resources.post_anonymously
import com.vitorpamplona.amethyst.commons.resources.private_note
import com.vitorpamplona.amethyst.commons.resources.private_note_locked
import com.vitorpamplona.amethyst.commons.resources.relay_group_thread_title_label
import com.vitorpamplona.amethyst.commons.resources.schedule_post_always_on_prompt_continue
import com.vitorpamplona.amethyst.commons.resources.schedule_post_always_on_prompt_message
import com.vitorpamplona.amethyst.commons.resources.schedule_post_always_on_prompt_open_settings
import com.vitorpamplona.amethyst.commons.resources.schedule_post_always_on_prompt_title
import com.vitorpamplona.amethyst.commons.resources.what_s_on_your_mind
import com.vitorpamplona.amethyst.commons.resources.zapraiser
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.mediaServers.FileServerSelectionRow
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.OnIncomingShare
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.UploadProgressIndicator
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.VoiceAnonymizationSection
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.rememberSharedMediaResolver
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.components.submitOnCtrlEnter
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.creators.aihelp.AiWritingHelpPanel
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDateButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.expiration.ExpirationDatePicker
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.AddLnInvoiceButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.InvoiceRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.AddGeoHashButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.location.GeoHashPostSection
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.AudienceFlap
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.AudienceSheet
import com.vitorpamplona.amethyst.commons.ui.note.creators.notify.rememberAudienceLists
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollComposer
import com.vitorpamplona.amethyst.commons.ui.note.creators.pow.PowOverrideButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.previews.DisplayPreviews
import com.vitorpamplona.amethyst.commons.ui.note.creators.scheduling.ScheduleAtButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.scheduling.ScheduleAtPicker
import com.vitorpamplona.amethyst.commons.ui.note.creators.scheduling.roundUpToNextQuarterHour
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.AddSecretEmojiButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.secretEmoji.SecretEmojiRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerMediaTools
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolbar
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolsStartPadding
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.composerBody
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.commons.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.MAX_VOICE_RECORD_SECONDS
import com.vitorpamplona.amethyst.commons.ui.note.platform.RecordVoiceButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.VoiceMessagePreview
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.ObserveInboxRelayListAndDisplayIfNotFound
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.ShortNotePostViewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.onUiThread
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Font14SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPickerButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ShortNotePostScreen(
    message: String? = null,
    attachment: String? = null,
    baseReplyToId: HexKey? = null,
    quoteId: HexKey? = null,
    forkId: HexKey? = null,
    versionId: HexKey? = null,
    draftId: HexKey? = null,
    groupThreadId: HexKey? = null,
    groupThreadRelayUrl: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: ShortNotePostViewModel = rememberViewModel { ShortNotePostViewModel() }
    postViewModel.init(accountViewModel)
    postViewModel.setGroupThread(groupThreadId, groupThreadRelayUrl)

    val mediaResolver = rememberSharedMediaResolver()

    val proposeAiImprovements by
        accountViewModel.settings.uiSettingsFlow.automaticallyProposeAiImprovements
            .collectAsStateWithLifecycle()

    LaunchedEffect(proposeAiImprovements) {
        if (proposeAiImprovements == BooleanType.ALWAYS) {
            postViewModel.initWritingAssistant()
        }
    }

    LaunchedEffect(postViewModel, accountViewModel) {
        val baseReplyTo = baseReplyToId?.let { LocalCache.getNoteIfExists(it) }
        val quote = quoteId?.let { LocalCache.getNoteIfExists(it) }
        val fork = forkId?.let { LocalCache.getNoteIfExists(it) }
        val version = versionId?.let { LocalCache.getNoteIfExists(it) }
        val draft = draftId?.let { LocalCache.getNoteIfExists(it) }
        postViewModel.load(baseReplyTo, quote, fork, version, draft)
        message?.ifBlank { null }?.let {
            postViewModel.message.setTextAndPlaceCursorAtEnd(it)
            postViewModel.onMessageChanged()
        }
        mediaResolver.resolve(attachment)?.let {
            postViewModel.selectImage(persistentListOf(it))
        }
        if (draftId == null && forkId == null) {
            postViewModel.applySignature()
        }
    }

    // Microsoft's swift key sends Gifs as new actions
    OnIncomingShare(
        onText = postViewModel::addToMessage,
        onMedia = { postViewModel.selectImage(persistentListOf(it)) },
    )

    NewPostScreenInner(postViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewPostScreenInner(
    postViewModel: ShortNotePostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchAndLoadMyEmojiList(accountViewModel)

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
                postRes = if (postViewModel.wantsPrivateNote) Res.string.audience_send_privately else Res.string.post,
                onPost = onPost,
                onCancel = {
                    // uses the accountViewModel scope to avoid cancelling this
                    // function when the postViewModel is released
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
            NewPostScreenBody(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
private fun NewPostScreenBody(
    postViewModel: ShortNotePostViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()
    val audienceLists = rememberAudienceLists(accountViewModel)
    // Both conversions are remembered on the ViewModel's own state. Unremembered,
    // each recomposition minted a fresh PersistentList/Set, which invalidated the
    // groupChips remember below every single time and handed AudienceFlap new
    // parameter identities so it could never skip.
    val audience = remember(postViewModel.pTags) { postViewModel.pTags?.toImmutableList() ?: persistentListOf() }
    val mutedNotifies = remember(postViewModel.mutedNotifies) { postViewModel.mutedNotifies.toImmutableSet() }
    val groupChips =
        remember(postViewModel.notifyProvenance, audience, mutedNotifies, audienceLists) {
            AudienceSelection
                .activeGroupChips(
                    provenance = postViewModel.notifyProvenance,
                    // Muted people are in pTags but will not be p-tagged, so
                    // counting them would have the chip over-report its batch.
                    audience = audience.mapNotNullTo(mutableSetOf()) { it.pubkeyHex.takeIf { hex -> hex !in mutedNotifies } },
                    lists = audienceLists,
                ).toImmutableList()
        }

    Column(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        ObserveInboxRelayListAndDisplayIfNotFound(accountViewModel, nav)

        // Hosted outside the scrolling content: the sheet is its own window, and
        // keeping it here means it survives wherever the composer scrolls to.
        if (postViewModel.wantsToManageAudience) {
            AudienceSheet(
                audience = audience,
                mutedNotifies = mutedNotifies,
                isPrivate = postViewModel.wantsPrivateNote,
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

        // Where the tools wrap, the text area takes only the height it needs, so the toolbar below
        // sits under the text rather than at the bottom of the window.
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
                postViewModel.originalNote?.let {
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

                AudienceFlap(
                    audience = audience,
                    isPrivate = postViewModel.wantsPrivateNote,
                    accountViewModel = accountViewModel,
                    mutedNotifies = mutedNotifies,
                    groupChips = groupChips,
                    onManage = { postViewModel.wantsToManageAudience = true },
                    onRemoveGroup = { postViewModel.removeListFromAudience(it) },
                    onToggleNotify = { postViewModel.toggleNotify(it) },
                )

                if (postViewModel.wantsSubject || postViewModel.groupThreadTarget != null) {
                    // Styled like the "To"/"Subject" rows in the new-DM composer: an inline label
                    // with a borderless field and a hairline divider, rather than a boxed input.
                    Column(Modifier.fillMaxWidth().padding(vertical = Size10dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = CenterVertically) {
                            Text(
                                text =
                                    stringRes(
                                        if (postViewModel.groupThreadTarget != null) {
                                            Res.string.relay_group_thread_title_label
                                        } else {
                                            Res.string.messages_new_message_subject
                                        },
                                    ),
                                fontSize = Font14SP,
                                fontWeight = FontWeight.W500,
                            )
                            ThinPaddingTextField(
                                state = postViewModel.subject,
                                spellCheck = true,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = stringRes(Res.string.messages_new_message_subject_caption),
                                        color = MaterialTheme.colorScheme.placeholderText,
                                    )
                                },
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                    ),
                            )
                        }
                        HorizontalDivider(thickness = DividerThickness)
                    }
                }

                // Only show text input if no voice message is being posted
                if (postViewModel.voiceMetadata == null && postViewModel.voiceRecording == null) {
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
                                        // Private notes are wrapped with the real key — the
                                        // recipients must know who is talking to them.
                                        if (!postViewModel.wantsPrivateNote) {
                                            postViewModel.wantsAnonymousPost = true
                                        }
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
                            if (postViewModel.wantsPoll || postViewModel.wantsZapPoll) Res.string.marmot_poll_question else Res.string.what_s_on_your_mind,
                            postViewModel,
                            onFocused = postViewModel::onMessageFocused,
                            onContentReceived = { uri, mimeType ->
                                postViewModel.selectImage(
                                    persistentListOf(
                                        SelectedMedia(uri, mimeType),
                                    ),
                                )
                            },
                        )
                    }
                }

                if (postViewModel.wantsPoll || postViewModel.wantsZapPoll) {
                    // Lined up with the text above and the tools below: the post's text is the poll's question.
                    PollComposer(
                        postViewModel,
                        showImages = accountViewModel.settings.showImages(),
                        modifier =
                            Modifier.padding(start = ComposerToolsStartPadding, end = Size10dp, top = Size5dp, bottom = Size10dp),
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

                val alwaysOnEnabled by accountViewModel.account.settings.alwaysOnNotificationService
                    .collectAsStateWithLifecycle()
                val savedAccounts by accountViewModel.host.savedAccounts
                    .collectAsStateWithLifecycle(emptySet())
                val hasMultipleAccounts = savedAccounts.size > 1
                postViewModel.scheduledForSec?.let { current ->
                    Row(
                        verticalAlignment = CenterVertically,
                        modifier = Modifier.padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        ScheduleAtPicker(
                            scheduledForSec = current,
                            onChanged = { postViewModel.scheduledForSec = it },
                            alwaysOnEnabled = alwaysOnEnabled,
                            hasMultipleAccounts = hasMultipleAccounts,
                        )
                    }
                }

                if (postViewModel.wantsToAddGeoHash) {
                    GeoHashPostSection(postViewModel) {
                        SettingsRow(
                            Res.string.geohash_exclusive,
                            Res.string.geohash_exclusive_explainer,
                        ) {
                            Switch(postViewModel.wantsExclusiveGeoPost, onCheckedChange = { postViewModel.wantsExclusiveGeoPost = it })
                        }
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

                // Show preview for both uploaded messages (voiceMetadata) and pending recordings
                (postViewModel.voiceMetadata ?: postViewModel.getVoicePreviewMetadata())?.let { metadata ->
                    val fileServersState =
                        accountViewModel.account.blossomServers.hostNameFlow
                            .collectAsState()
                    val fileServers = fileServersState.value

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Size10dp, horizontal = Size10dp),
                    ) {
                        // Display voice preview or uploading progress
                        postViewModel.voiceOrchestrator?.let { orchestrator ->
                            UploadProgressIndicator(orchestrator)
                        } ?: run {
                            val displayMetadata =
                                metadata.copy(
                                    waveform = postViewModel.activeWaveform ?: metadata.waveform,
                                )
                            VoiceMessagePreview(
                                voiceMetadata = displayMetadata,
                                localFile = postViewModel.activeFile,
                                onReRecord = { recording -> postViewModel.selectVoiceRecording(recording) },
                                isUploading = postViewModel.isUploadingVoice,
                                onRemove = { postViewModel.removeVoiceMessage() },
                            )

                            // Voice anonymization section (only show when not uploading and voice is pending)
                            if (postViewModel.voiceRecording != null) {
                                VoiceAnonymizationSection(
                                    selectedPreset = postViewModel.selectedPreset,
                                    processingPreset = postViewModel.processingPreset,
                                    onPresetSelected = { postViewModel.selectPreset(it) },
                                )
                            }
                        }

                        FileServerSelectionRow(
                            fileServers = fileServers,
                            selectedServer = postViewModel.voiceSelectedServer ?: accountViewModel.account.settings.defaultFileServer,
                            onSelect = { postViewModel.voiceSelectedServer = it },
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

        // Not while the audience sheet is up: it renders its own list off the same
        // UserSuggestionState, and this copy would sit behind the scrim
        // re-subscribing every suggested user's metadata for nobody to see.
        if (!postViewModel.wantsToManageAudience) {
            postViewModel.userSuggestions?.let {
                ShowUserSuggestionList(
                    it,
                    postViewModel::autocompleteWithUser,
                    accountViewModel,
                    modifier = SuggestionListDefaultHeightPage,
                )
            }
        }

        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        val proposeAiImprovements by
            accountViewModel.settings.uiSettingsFlow.automaticallyProposeAiImprovements
                .collectAsStateWithLifecycle()

        AiWritingHelpPanel(
            isVisible = proposeAiImprovements == BooleanType.ALWAYS && postViewModel.showAiPanel,
            readyResults = postViewModel.aiResults,
            selectedResult = postViewModel.aiSelectedResult,
            onToneSelected = postViewModel::selectAiResult,
            onApply = postViewModel::applyAiResult,
            onDismiss = postViewModel::dismissAiResult,
        )

        val alwaysOnEnabled by accountViewModel.account.settings.alwaysOnNotificationService
            .collectAsStateWithLifecycle()
        var showAlwaysOnPrompt by remember { mutableStateOf(false) }

        BottomRowActions(
            postViewModel = postViewModel,
            onScheduleClicked = {
                if (postViewModel.scheduledForSec != null) {
                    postViewModel.scheduledForSec = null
                } else if (!alwaysOnEnabled) {
                    showAlwaysOnPrompt = true
                } else {
                    postViewModel.scheduledForSec =
                        roundUpToNextQuarterHour(TimeUtils.now() + 60 * 60)
                }
            },
        )

        if (showAlwaysOnPrompt) {
            AlertDialog(
                onDismissRequest = { showAlwaysOnPrompt = false },
                title = { Text(stringRes(Res.string.schedule_post_always_on_prompt_title)) },
                text = { Text(stringRes(Res.string.schedule_post_always_on_prompt_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showAlwaysOnPrompt = false
                        nav.nav(Route.NotificationSettings)
                    }) {
                        Text(stringRes(Res.string.schedule_post_always_on_prompt_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAlwaysOnPrompt = false
                        postViewModel.scheduledForSec =
                            roundUpToNextQuarterHour(TimeUtils.now() + 60 * 60)
                    }) {
                        Text(stringRes(Res.string.schedule_post_always_on_prompt_continue))
                    }
                },
            )
        }
    }
}

@Composable
private fun BottomRowActions(
    postViewModel: ShortNotePostViewModel,
    onScheduleClicked: () -> Unit = {
        postViewModel.scheduledForSec =
            if (postViewModel.scheduledForSec != null) {
                null
            } else {
                roundUpToNextQuarterHour(TimeUtils.now() + 60 * 60)
            }
    },
) {
    ComposerToolbar { wrapped ->
        ComposerMediaTools(postViewModel)

        // The recorder fills its parent. The strip bounds it to 50dp tall and lets it take only
        // the width it needs; wrapped, a strip of its own does the same instead of letting it
        // spread over the whole toolbar. One Row in both layouts, only its modifier changing,
        // so a recording survives the switch.
        val recorderScroll = rememberScrollState()
        Row(if (wrapped) Modifier.height(50.dp).horizontalScroll(recorderScroll) else Modifier) {
            RecordVoiceButton(
                onVoiceTaken = { recording ->
                    postViewModel.selectVoiceRecording(recording)
                },
                maxDurationSeconds = MAX_VOICE_RECORD_SECONDS,
            )
        }

        EmojiPickerButton(postViewModel.emojiSuggestions?.emojiPacks, postViewModel.emojiTarget)

        // Polls publish kinds that can't travel inside a private wrap, so the
        // two toggles are mutually exclusive. Neither a private wrap nor a poll makes sense for a
        // NIP-29 group thread (it publishes plainly to the host relay), so hide both there.
        if (!postViewModel.wantsPoll && !postViewModel.wantsZapPoll && postViewModel.groupThreadTarget == null) {
            val haptic = LocalHapticFeedback.current
            AddPrivateNoteButton(
                isActive = postViewModel.wantsPrivateNote,
                isLocked = postViewModel.privateNoteLocked,
            ) {
                val nowPrivate = !postViewModel.wantsPrivateNote
                postViewModel.togglePrivateNote()
                // Sealing a note changes what Send is about to do, so the change
                // is confirmed in the hand as well as on screen — and in the
                // direction it actually moved.
                haptic.performHapticFeedback(
                    if (nowPrivate) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
            }
        }

        if ((postViewModel.canUsePoll || postViewModel.canUseZapPoll) &&
            !postViewModel.wantsPrivateNote &&
            postViewModel.groupThreadTarget == null
        ) {
            AddPollButton(postViewModel.wantsPoll || postViewModel.wantsZapPoll) {
                val isActive = postViewModel.wantsPoll || postViewModel.wantsZapPoll
                if (isActive) {
                    postViewModel.disablePoll()
                } else {
                    postViewModel.wantsPoll = true
                }
            }
        }

        ForwardZapToButton(postViewModel.wantsForwardZapTo) {
            postViewModel.wantsForwardZapTo = !postViewModel.wantsForwardZapTo
        }

        if (postViewModel.canAddZapRaiser) {
            AddZapraiserButton(postViewModel.wantsZapRaiser) {
                postViewModel.wantsZapRaiser = !postViewModel.wantsZapRaiser
            }
        }

        PowOverrideButton(
            effectiveDifficulty = postViewModel.effectivePowDifficulty(),
            defaultDifficulty = postViewModel.defaultPowDifficulty(),
            isOverridden = postViewModel.powOverride != null,
            onSelect = { postViewModel.powOverride = it },
        )

        // A group thread's title is required, so the field is always shown for it — no toggle.
        if (postViewModel.groupThreadTarget == null) {
            AddSubjectButton(postViewModel.wantsSubject) {
                postViewModel.toggleSubject()
            }
        }

        MarkAsSensitiveButton(postViewModel.wantsToMarkAsSensitive) {
            postViewModel.toggleMarkAsSensitive()
        }

        ExpirationDateButton(postViewModel.wantsExpirationDate) {
            postViewModel.toggleExpirationDate()
        }

        // Private wraps are built and sent immediately; scheduling them would
        // require wrapping at publish time, so the option is hidden for now. Scheduling also
        // bypasses the host-relay pin, so it's hidden for NIP-29 group threads too.
        if (!postViewModel.wantsPrivateNote && postViewModel.groupThreadTarget == null) {
            ScheduleAtButton(postViewModel.scheduledForSec != null, onScheduleClicked)
        }

        AddGeoHashButton(postViewModel.wantsToAddGeoHash) {
            postViewModel.wantsToAddGeoHash = !postViewModel.wantsToAddGeoHash
        }

        AddSecretEmojiButton(postViewModel.wantsSecretEmoji) {
            postViewModel.wantsSecretEmoji = !postViewModel.wantsSecretEmoji
        }

        if (postViewModel.canAddInvoice && postViewModel.hasLnAddress()) {
            AddLnInvoiceButton(postViewModel.wantsInvoice) {
                postViewModel.wantsInvoice = !postViewModel.wantsInvoice
            }
        }
    }
}

@Suppress("ViewModelConstructorInComposable")
@Preview
@Composable
private fun BottomRowActionsPreview() {
    val model = ShortNotePostViewModel()
    model.canUsePoll = true
    ThemeComparisonColumn {
        BottomRowActions(model)
    }
}

/**
 * The private-note toggle. Unlike its neighbours in the strip it takes a filled
 * pill when it is on: this is the one control that changes what Send does, so
 * "tinted glyph among eleven identical siblings" is not enough of a signal.
 */
@Composable
private fun AddPrivateNoteButton(
    isActive: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(280),
        label = "privateNoteContainer",
    )
    val content by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(280),
        label = "privateNoteContent",
    )

    val label =
        stringRes(
            when {
                isLocked -> Res.string.private_note_locked
                isActive -> Res.string.disable_private_note
                else -> Res.string.private_note
            },
        )

    HoverTooltip(label) {
        IconButton(
            onClick = { onClick() },
            enabled = !isLocked,
            // A reply to an unsealed rumor is locked private. The button is disabled
            // there, and M3's default disabled colours would erase the filled pill in
            // exactly the case where the note is most definitely private — so the
            // disabled colours mirror the enabled ones, dimmed.
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = container,
                    contentColor = content,
                    disabledContainerColor = container.copy(alpha = container.alpha * 0.6f),
                    disabledContentColor = content.copy(alpha = 0.8f),
                ),
        ) {
            Icon(
                symbol = if (isActive) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
                contentDescription = label,
                modifier = Modifier.height(22.dp),
                tint = content,
            )
        }
    }
}

@Composable
private fun AddSubjectButton(
    isActive: Boolean,
    onClick: () -> Unit,
) {
    HoverTooltip(stringRes(Res.string.messages_new_message_subject)) {
        IconButton(onClick = onClick) {
            Icon(
                symbol = MaterialSymbols.Topic,
                contentDescription = stringRes(Res.string.messages_new_message_subject),
                modifier = Modifier.height(22.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun AddPollButton(
    isPollActive: Boolean,
    onClick: () -> Unit,
) {
    HoverTooltip(stringRes(if (isPollActive) Res.string.disable_poll else Res.string.poll)) {
        IconButton(
            onClick = { onClick() },
        ) {
            if (!isPollActive) {
                Icon(
                    symbol = MaterialSymbols.Poll,
                    contentDescription = stringRes(id = Res.string.poll),
                    modifier = Modifier.height(22.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            } else {
                Icon(
                    symbol = MaterialSymbols.Poll,
                    contentDescription = stringRes(id = Res.string.disable_poll),
                    modifier = Modifier.height(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
