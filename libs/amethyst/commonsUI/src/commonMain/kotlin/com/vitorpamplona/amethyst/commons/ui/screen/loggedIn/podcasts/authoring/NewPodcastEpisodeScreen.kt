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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.podcast_cover_upload_cta
import com.vitorpamplona.amethyst.commons.resources.podcast_cover_upload_hint
import com.vitorpamplona.amethyst.commons.resources.podcast_edit_episode
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_audio_picked
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_audio_upload_cta
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_audio_upload_hint
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_audio_url_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_audio_url_placeholder
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_chapters_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_delete
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_delete_confirm
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_duration_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_more_details
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_number_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_season_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_summary_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_title_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_title_placeholder
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_topics_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_topics_placeholder
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_transcript_label
import com.vitorpamplona.amethyst.commons.resources.podcast_episode_video_label
import com.vitorpamplona.amethyst.commons.resources.podcast_new_episode
import com.vitorpamplona.amethyst.commons.resources.podcast_publishing_banner
import com.vitorpamplona.amethyst.commons.resources.publish
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.DocumentSelectSingle
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.CoverImagePicker
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.UploadInProgressBanner
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music.UploadPlaceholder
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.podcasts.authoring.V4VSplitEditor
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource

/**
 * Composer for a Podcasting-2.0 episode. Cover art + audio file upload at the top (or paste URLs),
 * the core fields (title, summary, duration), and a collapsible "More details" section for the
 * Podcasting-2.0 extras (season/number, video, transcript, chapters, topics). Create + edit + delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPodcastEpisodeScreen(
    editDTag: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewPodcastEpisodeViewModel = rememberViewModel { NewPodcastEpisodeViewModel() }

    LaunchedEffect(accountViewModel) { vm.init(accountViewModel, editDTag) }

    StrippingFailureDialog(vm.strippingFailureConfirmation)

    var wantsToPickCover by remember { mutableStateOf(false) }
    if (wantsToPickCover) {
        GallerySelectSingle(
            onImageUri = { picked ->
                wantsToPickCover = false
                vm.setPickedCover(if (picked != null) persistentListOf(picked) else persistentListOf())
            },
        )
    }

    var wantsToPickAudio by remember { mutableStateOf(false) }
    if (wantsToPickAudio) {
        AudioFileSelect { picked ->
            wantsToPickAudio = false
            vm.setPickedAudio(picked) { accountViewModel.host.probeAudioFile(it) }
        }
    }

    val isBusy = vm.isSending.value

    LaunchedEffect(vm) { vm.completionEvents.collect { nav.popBack() } }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = if (vm.isEditing) Res.string.save else Res.string.publish,
                titleRes = if (vm.isEditing) Res.string.podcast_edit_episode else Res.string.podcast_new_episode,
                onCancel = { nav.popBack() },
                isActive = { vm.isValid() && !isBusy },
                onPost = {
                    if (!vm.isValid() || isBusy) return@ActionTopBar
                    vm.saveAndPublish(accountViewModel)
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isBusy) UploadInProgressBanner(Res.string.podcast_publishing_banner)

            CoverImagePicker(
                cover = vm.coverMedia.value,
                existingUrl = vm.coverUrl.value,
                onPick = { wantsToPickCover = true },
                onDelete = { vm.clearPickedCover() },
                accountViewModel = accountViewModel,
                enabled = !isBusy,
                ctaRes = Res.string.podcast_cover_upload_cta,
                hintRes = Res.string.podcast_cover_upload_hint,
            )

            AudioFilePickerRow(
                pickedName = vm.pickedAudioName.value,
                onPick = { wantsToPickAudio = true },
                onClear = { vm.clearPickedAudio() },
                enabled = !isBusy,
            )

            SpellCheckedOutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(Res.string.podcast_episode_title_label)) },
                placeholder = { Text(stringRes(Res.string.podcast_episode_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = vm.title.value.isBlank(),
            )

            SpellCheckedOutlinedTextField(
                value = vm.description.value,
                onValueChange = { vm.description.value = it },
                label = { Text(stringRes(Res.string.podcast_episode_summary_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            OutlinedTextField(
                value = vm.durationSeconds.value,
                onValueChange = { input -> vm.durationSeconds.value = input.filter { it.isDigit() } },
                label = { Text(stringRes(Res.string.podcast_episode_duration_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            var showMore by rememberSaveable { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showMore = !showMore }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(Res.string.podcast_episode_more_details),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    symbol = if (showMore) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }

            if (showMore) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = vm.season.value,
                        onValueChange = { input -> vm.season.value = input.filter { it.isDigit() } },
                        label = { Text(stringRes(Res.string.podcast_episode_season_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = vm.episodeNumber.value,
                        onValueChange = { input -> vm.episodeNumber.value = input.filter { it.isDigit() } },
                        label = { Text(stringRes(Res.string.podcast_episode_number_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                UrlField(vm.videoUrl, Res.string.podcast_episode_video_label)
                UrlField(vm.transcriptUrl, Res.string.podcast_episode_transcript_label)
                UrlField(vm.chaptersUrl, Res.string.podcast_episode_chapters_label)

                OutlinedTextField(
                    value = vm.topics.value,
                    onValueChange = { vm.topics.value = it },
                    label = { Text(stringRes(Res.string.podcast_episode_topics_label)) },
                    placeholder = { Text(stringRes(Res.string.podcast_episode_topics_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = vm.audioUrl.value,
                    onValueChange = { vm.audioUrl.value = it },
                    label = { Text(stringRes(Res.string.podcast_episode_audio_url_label)) },
                    placeholder = { Text(stringRes(Res.string.podcast_episode_audio_url_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                // Episode-level V4V override; leave empty to inherit the show's split.
                V4VSplitEditor(vm.splitEditor, accountViewModel)
            }

            if (vm.isEditing) {
                HorizontalDivider()
                DeleteEpisodeRow(vm = vm, onDeleted = { nav.popBack() }, accountViewModel = accountViewModel)
            }
        }
    }
}

@Composable
private fun UrlField(
    state: androidx.compose.runtime.MutableState<String>,
    labelRes: StringResource,
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = { Text(stringRes(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
}

@Composable
private fun AudioFilePickerRow(
    pickedName: String?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    enabled: Boolean,
) {
    if (pickedName != null) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .let { if (enabled) it.clickable(onClick = onPick) else it }
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text = pickedName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = stringRes(Res.string.podcast_episode_audio_picked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                TextButton(onClick = onClear) { Text(stringRes(Res.string.cancel)) }
            }
        }
    } else {
        UploadPlaceholder(
            iconSymbol = MaterialSymbols.MusicNote,
            ctaRes = Res.string.podcast_episode_audio_upload_cta,
            hintRes = Res.string.podcast_episode_audio_upload_hint,
            onClick = onPick,
            aspectRatio = null,
            enabled = enabled,
        )
    }
}

// Single audio file via OpenDocument restricted to audio MIME types.
@Composable
private fun AudioFileSelect(onAudioPicked: (SelectedMedia?) -> Unit) = DocumentSelectSingle(listOf("audio/*"), onAudioPicked)

@Composable
private fun DeleteEpisodeRow(
    vm: NewPodcastEpisodeViewModel,
    onDeleted: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text = stringRes(Res.string.podcast_episode_delete))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringRes(Res.string.podcast_episode_delete)) },
            text = { Text(stringRes(Res.string.podcast_episode_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    accountViewModel.launchSigner { if (vm.deleteLoaded()) onDeleted() }
                }) {
                    Text(text = stringRes(Res.string.podcast_episode_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}
