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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.edit_music_playlist
import com.vitorpamplona.amethyst.commons.resources.music_playlist_collaborative
import com.vitorpamplona.amethyst.commons.resources.music_playlist_collaborative_hint
import com.vitorpamplona.amethyst.commons.resources.music_playlist_cover_upload_cta
import com.vitorpamplona.amethyst.commons.resources.music_playlist_cover_upload_hint
import com.vitorpamplona.amethyst.commons.resources.music_playlist_delete
import com.vitorpamplona.amethyst.commons.resources.music_playlist_delete_confirm
import com.vitorpamplona.amethyst.commons.resources.music_playlist_description_label
import com.vitorpamplona.amethyst.commons.resources.music_playlist_loading_track
import com.vitorpamplona.amethyst.commons.resources.music_playlist_move_track_down
import com.vitorpamplona.amethyst.commons.resources.music_playlist_move_track_up
import com.vitorpamplona.amethyst.commons.resources.music_playlist_new_title_placeholder
import com.vitorpamplona.amethyst.commons.resources.music_playlist_no_tracks_hint
import com.vitorpamplona.amethyst.commons.resources.music_playlist_notes_label
import com.vitorpamplona.amethyst.commons.resources.music_playlist_private
import com.vitorpamplona.amethyst.commons.resources.music_playlist_private_hint
import com.vitorpamplona.amethyst.commons.resources.music_playlist_remove_track
import com.vitorpamplona.amethyst.commons.resources.music_playlist_title_label
import com.vitorpamplona.amethyst.commons.resources.music_playlist_track_count
import com.vitorpamplona.amethyst.commons.resources.music_playlist_unknown_track
import com.vitorpamplona.amethyst.commons.resources.music_playlist_uploading_banner
import com.vitorpamplona.amethyst.commons.resources.new_music_playlist
import com.vitorpamplona.amethyst.commons.resources.publish
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import kotlinx.collections.immutable.persistentListOf

@Composable
fun NewMusicPlaylistScreen(
    editDTag: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewMusicPlaylistViewModel = rememberViewModel { NewMusicPlaylistViewModel() }

    LaunchedEffect(accountViewModel) {
        vm.init(accountViewModel, editDTag)
    }

    StrippingFailureDialog(vm.strippingFailureConfirmation)

    var wantsToPickCover by remember { mutableStateOf(false) }
    if (wantsToPickCover) {
        GallerySelectSingle(
            onImageUri = { picked ->
                wantsToPickCover = false
                vm.setPickedCover(
                    if (picked != null) persistentListOf(picked) else persistentListOf(),
                )
            },
        )
    }

    val isBusy = vm.isSending.value

    // The send coroutine lives on accountViewModel.viewModelScope so it survives the screen,
    // but the screen still owns the pop-back. Subscribe to the one-shot completion flow: if the
    // user is still here when the upload finishes, we pop back; if they've already left, no one
    // is listening and nothing happens here.
    LaunchedEffect(vm) {
        vm.completionEvents.collect { nav.popBack() }
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = if (vm.isEditing) Res.string.save else Res.string.publish,
                titleRes = if (vm.isEditing) Res.string.edit_music_playlist else Res.string.new_music_playlist,
                onCancel = { nav.popBack() },
                isActive = { vm.isValid() && !isBusy },
                onPost = {
                    if (!vm.isValid() || isBusy) return@ActionTopBar
                    vm.saveAndPublish(
                        accountViewModel = accountViewModel,
                    )
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
            if (isBusy) UploadInProgressBanner(Res.string.music_playlist_uploading_banner)

            CoverImagePicker(
                cover = vm.coverMedia.value,
                existingUrl = vm.coverUrl.value,
                onPick = { wantsToPickCover = true },
                onDelete = { vm.clearPickedCover() },
                accountViewModel = accountViewModel,
                enabled = !isBusy,
                ctaRes = Res.string.music_playlist_cover_upload_cta,
                hintRes = Res.string.music_playlist_cover_upload_hint,
            )

            SpellCheckedOutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(Res.string.music_playlist_title_label)) },
                placeholder = { Text(stringRes(Res.string.music_playlist_new_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = vm.title.value.isBlank(),
            )

            SpellCheckedOutlinedTextField(
                value = vm.description.value,
                onValueChange = { vm.description.value = it },
                label = { Text(stringRes(Res.string.music_playlist_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            SpellCheckedOutlinedTextField(
                value = vm.notes.value,
                onValueChange = { vm.notes.value = it },
                label = { Text(stringRes(Res.string.music_playlist_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            LabeledSwitchRow(
                iconSymbol = if (vm.isPrivate.value) MaterialSymbols.Lock else MaterialSymbols.Public,
                title = stringRes(Res.string.music_playlist_private),
                subtitle = stringRes(Res.string.music_playlist_private_hint),
                checked = vm.isPrivate.value,
                enabled = !isBusy,
                onCheckedChange = { vm.isPrivate.value = it },
            )

            LabeledSwitchRow(
                iconSymbol = MaterialSymbols.Groups,
                title = stringRes(Res.string.music_playlist_collaborative),
                subtitle = stringRes(Res.string.music_playlist_collaborative_hint),
                checked = vm.isCollaborative.value,
                enabled = !isBusy,
                onCheckedChange = { vm.isCollaborative.value = it },
            )

            TrackManagementSection(
                vm = vm,
                accountViewModel = accountViewModel,
                enabled = !isBusy,
            )

            if (vm.isEditing) {
                DeleteMusicPlaylistRow(
                    vm = vm,
                    onDeleted = { nav.popBack() },
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun LabeledSwitchRow(
    iconSymbol: MaterialSymbol,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = iconSymbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * In-playlist track management: lists the working track order with reorder (up/down) and remove
 * controls. The list is empty in create mode (the FAB makes a fresh playlist) and is seeded from
 * the loaded event when editing. Adding *new* tracks happens through the per-song "Add to playlist"
 * sheet, not here.
 */
@Composable
private fun TrackManagementSection(
    vm: NewMusicPlaylistViewModel,
    accountViewModel: AccountViewModel,
    enabled: Boolean,
) {
    val tracks = vm.tracks.value

    HorizontalDivider()

    val count = tracks.size
    Text(
        text = pluralStringRes(Res.plurals.music_playlist_track_count, count, count),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )

    if (tracks.isEmpty()) {
        Text(
            text = stringRes(Res.string.music_playlist_no_tracks_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    tracks.forEachIndexed { index, address ->
        LoadAddressableNote(address) { trackNote ->
            EditableTrackRow(
                position = index + 1,
                trackNote = trackNote,
                isFirst = index == 0,
                isLast = index == tracks.lastIndex,
                enabled = enabled,
                onMoveUp = { vm.moveTrackUp(index) },
                onMoveDown = { vm.moveTrackDown(index) },
                onRemove = { vm.removeTrackAt(index) },
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
private fun EditableTrackRow(
    position: Int,
    trackNote: AddressableNote?,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    // The note may still be resolving (only the address is known). Keep the reorder/remove controls
    // live in that case — they act on the list position, which is valid regardless of whether the
    // track metadata has loaded — and show a "Loading track…" placeholder for the title.
    if (trackNote == null) {
        TrackRow(
            position = position,
            title = stringRes(Res.string.music_playlist_loading_track),
            artist = null,
            cover = null,
            isFirst = isFirst,
            isLast = isLast,
            enabled = enabled,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onRemove = onRemove,
            accountViewModel = accountViewModel,
        )
        return
    }

    // Observe so the row fills in (and recomposes) when the track event arrives from a relay or a
    // newer revision replaces the cached one.
    val trackEvent by observeNoteEvent<MusicTrackEvent>(trackNote, accountViewModel)
    TrackRow(
        position = position,
        title = trackEvent?.title() ?: stringRes(Res.string.music_playlist_unknown_track),
        artist = trackEvent?.artist(),
        cover = trackEvent?.image(),
        isFirst = isFirst,
        isLast = isLast,
        enabled = enabled,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onRemove = onRemove,
        accountViewModel = accountViewModel,
    )
}

@Composable
private fun TrackRow(
    position: Int,
    title: String,
    artist: String?,
    cover: String?,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        if (cover != null) {
            MyAsyncImage(
                imageUrl = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                loadedImageModifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                accountViewModel = accountViewModel,
                onLoadingBackground = { TrackArtworkPlaceholder() },
                onError = { TrackArtworkPlaceholder() },
            )
        } else {
            TrackArtworkPlaceholder()
        }

        Spacer(Modifier.size(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            artist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onMoveUp, enabled = enabled && !isFirst) {
            Icon(
                symbol = MaterialSymbols.ArrowUpward,
                contentDescription = stringRes(Res.string.music_playlist_move_track_up),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onMoveDown, enabled = enabled && !isLast) {
            Icon(
                symbol = MaterialSymbols.ArrowDownward,
                contentDescription = stringRes(Res.string.music_playlist_move_track_down),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(
                symbol = MaterialSymbols.Close,
                contentDescription = stringRes(Res.string.music_playlist_remove_track),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun TrackArtworkPlaceholder() {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DeleteMusicPlaylistRow(
    vm: NewMusicPlaylistViewModel,
    onDeleted: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text = stringRes(Res.string.music_playlist_delete))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringRes(Res.string.music_playlist_delete)) },
            text = { Text(stringRes(Res.string.music_playlist_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    accountViewModel.launchSigner {
                        if (vm.deleteLoaded()) onDeleted()
                    }
                }) {
                    Text(
                        text = stringRes(Res.string.music_playlist_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }
}

@Preview
@Composable
private fun NewMusicPlaylistScreenPreview() {
    ThemeComparisonColumn {
        NewMusicPlaylistScreen(
            editDTag = null,
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
