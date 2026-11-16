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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.drawing.ui.DrawingButton
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCardShape
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitShape
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitMetaStyle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitPalette
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitRepositoryActivity
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_add_label
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_body_hint
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_create
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_labels
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_labels_hint
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_nothing_to_preview
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_preview
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_subject
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_title
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_write
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

@Composable
fun GitNewIssueScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let { GitNewIssueForm(it, accountViewModel, nav) }
    }
}

/**
 * Full-screen "New issue" composer, laid out as a forge's: the title on its own, the description in
 * a bordered box with Write / Preview tabs and the attach buttons in its header, and labels picked
 * from the ones the repository already uses (or typed). On submit it builds a NIP-34 issue
 * addressed to [repoNote], signs it with the account signer and broadcasts it, then returns to
 * the issues list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GitNewIssueForm(
    repoNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: GitNewIssueViewModel = rememberViewModel { GitNewIssueViewModel() }
    viewModel.init(accountViewModel.account)
    val repoEvent by observeNoteEvent<GitRepositoryEvent>(repoNote, accountViewModel)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = repoEvent?.name() ?: repoNote.dTag(),
                            color = MaterialTheme.colorScheme.grayText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(text = " / ", color = MaterialTheme.colorScheme.grayText, maxLines = 1)
                        Text(text = stringRes(Res.string.git_new_issue_title), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                },
                navigationIcon = {
                    Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                        if (nav.canPop()) {
                            IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                        }
                    }
                },
                actions = {
                    Button(
                        enabled = viewModel.canSend(),
                        onClick = {
                            accountViewModel.launchSigner { viewModel.send(repoNote) }
                            nav.popBack()
                        },
                        shape = GitShape,
                        colors = ButtonDefaults.buttonColors(containerColor = gitPalette().open, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 10.dp).height(34.dp),
                    ) {
                        Text(stringRes(Res.string.git_new_issue_create), style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(LocalDisappearingScaffoldPadding.current)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SpellCheckedOutlinedTextField(
                value = viewModel.subject,
                onValueChange = { viewModel.subject = it },
                placeholder = { Text(stringRes(Res.string.git_new_issue_subject), style = MaterialTheme.typography.titleMedium) },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                singleLine = true,
                shape = GitCardShape,
                colors = gitFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            DescriptionBox(viewModel, accountViewModel, nav)

            viewModel.multiOrchestrator?.let {
                ImageVideoDescription(
                    it,
                    accountViewModel.account.settings.defaultFileServer,
                    isUploading = viewModel.mediaUploadTracker.isUploading,
                    onAdd = { alt, server, sensitiveContent, mediaQuality, useH265, stripMetadata, convertGifToMp4 ->
                        viewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, accountViewModel.mediaUploader, useH265, stripMetadata, convertGifToMp4)
                        accountViewModel.account.settings.changeDefaultFileServer(server)
                    },
                    onDelete = viewModel::deleteMediaToUpload,
                    onCancel = { viewModel.multiOrchestrator = null },
                    accountViewModel = accountViewModel,
                )
            }

            LabelPicker(repoNote, viewModel)
        }
    }

    StrippingFailureDialog(viewModel.strippingFailureConfirmation)
}

/** The description in a bordered box: Write / Preview tabs, the attach buttons, then the text or its rendering. */
@Composable
private fun DescriptionBox(
    viewModel: GitNewIssueViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var preview by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().gitCard()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    .padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorTab(stringRes(Res.string.git_new_issue_write), selected = !preview) { preview = false }
            EditorTab(stringRes(Res.string.git_new_issue_preview), selected = preview) { preview = true }
            Spacer(Modifier.weight(1f))
            // Pictures and videos of the problem: uploaded, linked in the body, and described by
            // an imeta tag on the issue, as in a comment.
            SelectFromGallery(
                isUploading = viewModel.mediaUploadTracker.isUploadingImage,
                enabled = !viewModel.mediaUploadTracker.isUploadingFile,
                tint = MaterialTheme.colorScheme.grayText,
                modifier = Modifier,
            ) {
                viewModel.selectMedia(it)
            }
            DrawingButton(accountViewModel, enabled = !viewModel.mediaUploadTracker.isUploading) { drawing, alt ->
                viewModel.uploadDrawing(drawing, alt, accountViewModel.toastManager::toast, accountViewModel.mediaUploader)
            }
        }
        HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.subtleBorder)

        if (preview) {
            Box(Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(14.dp)) {
                if (viewModel.body.isBlank()) {
                    Text(stringRes(Res.string.git_new_issue_nothing_to_preview), style = gitMetaStyle())
                } else {
                    val background = MaterialTheme.colorScheme.background
                    val backgroundColor = remember { mutableStateOf(background) }
                    RichTextViewer(
                        content = viewModel.body,
                        canPreview = true,
                        quotesLeft = 1,
                        modifier = Modifier.fillMaxWidth(),
                        tags = EmptyTagList,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        } else {
            SpellCheckedOutlinedTextField(
                value = viewModel.body,
                onValueChange = { viewModel.body = it },
                placeholder = { Text(stringRes(Res.string.git_new_issue_body_hint)) },
                minLines = 8,
                colors = gitFieldColors(borderless = true),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditorTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clip(GitShape).clickable(onClick = onClick).padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.grayText,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Box(
            Modifier
                .height(2.dp)
                .width(28.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

/**
 * Labels as tags to tick: the ones the repository's issues already carry, most used first, then
 * any typed in the field below. Picking from the repository's own set keeps "bug" from arriving
 * as "Bug", "bugs" and "type:bug".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelPicker(
    repoNote: AddressableNote,
    viewModel: GitNewIssueViewModel,
) {
    // Recomputed as the repository's items arrive, so a cold open still fills in.
    val activity = observeGitRepositoryActivity(repoNote.dTag())
    val known =
        remember(repoNote, activity) {
            val counts = HashMap<String, Int>()
            LocalCache.getGitRepositoryIfExists(repoNote.dTag())?.items?.filterIntoSet { _, note -> note.event is GitIssueEvent }?.forEach { note ->
                GitItemTitles.labelsOf(note).forEach { counts[it] = (counts[it] ?: 0) + 1 }
            }
            counts.entries
                .sortedByDescending { it.value }
                .take(16)
                .map { it.key }
        }
    val selected = parseLabels(viewModel.labels)
    var typed by remember { mutableStateOf("") }

    fun setLabels(labels: List<String>) {
        viewModel.labels = labels.joinToString(", ")
    }

    fun addTyped() {
        val more = parseLabels(typed)
        if (more.isNotEmpty()) setLabels((selected + more).distinct())
        typed = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringRes(Res.string.git_new_issue_labels), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        val shown = (known + selected).distinct()
        if (shown.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { label ->
                    val on = label in selected
                    LabelToggle(label, on) { setLabels(if (on) selected - label else selected + label) }
                }
            }
        }
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            placeholder = { Text(stringRes(Res.string.git_new_issue_labels_hint), style = MaterialTheme.typography.bodyMedium) },
            textStyle = MaterialTheme.typography.bodyMedium,
            singleLine = true,
            shape = GitCardShape,
            colors = gitFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addTyped() }),
            trailingIcon = {
                if (typed.isNotBlank()) {
                    IconButton(onClick = { addTyped() }) {
                        Icon(MaterialSymbols.Add, contentDescription = stringRes(Res.string.git_new_issue_add_label), modifier = Modifier.size(18.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LabelToggle(
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .clip(GitShape)
                .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.subtleBorder, GitShape)
                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (on) Icon(MaterialSymbols.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}

/** Outlined fields in the git screens' quiet border; [borderless] for one inside a bordered box. */
@Composable
private fun gitFieldColors(borderless: Boolean = false) =
    OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = if (borderless) Color.Transparent else MaterialTheme.colorScheme.subtleBorder,
        focusedBorderColor = if (borderless) Color.Transparent else MaterialTheme.colorScheme.primary,
    )
