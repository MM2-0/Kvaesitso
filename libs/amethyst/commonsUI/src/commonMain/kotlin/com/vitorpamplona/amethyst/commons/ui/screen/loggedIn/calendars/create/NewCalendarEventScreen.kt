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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.calendars.create.NewCalendarEventViewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_event_all_day
import com.vitorpamplona.amethyst.commons.resources.calendar_event_all_day_locked
import com.vitorpamplona.amethyst.commons.resources.calendar_event_end
import com.vitorpamplona.amethyst.commons.resources.calendar_event_end_before_start
import com.vitorpamplona.amethyst.commons.resources.calendar_event_hashtags
import com.vitorpamplona.amethyst.commons.resources.calendar_event_image
import com.vitorpamplona.amethyst.commons.resources.calendar_event_image_upload_failed
import com.vitorpamplona.amethyst.commons.resources.calendar_event_image_upload_failed_body
import com.vitorpamplona.amethyst.commons.resources.calendar_event_invalid
import com.vitorpamplona.amethyst.commons.resources.calendar_event_location
import com.vitorpamplona.amethyst.commons.resources.calendar_event_participant_input
import com.vitorpamplona.amethyst.commons.resources.calendar_event_participant_remove
import com.vitorpamplona.amethyst.commons.resources.calendar_event_participants_section
import com.vitorpamplona.amethyst.commons.resources.calendar_event_pick_date
import com.vitorpamplona.amethyst.commons.resources.calendar_event_pick_image
import com.vitorpamplona.amethyst.commons.resources.calendar_event_start
import com.vitorpamplona.amethyst.commons.resources.calendar_event_summary
import com.vitorpamplona.amethyst.commons.resources.calendar_event_title
import com.vitorpamplona.amethyst.commons.resources.edit_calendar_event
import com.vitorpamplona.amethyst.commons.resources.new_calendar_event
import com.vitorpamplona.amethyst.commons.resources.publish
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.create.CalendarDateTimePickerButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCalendarEventScreen(
    nav: INav,
    accountViewModel: AccountViewModel,
    editKind: Int? = null,
    editPubKeyHex: String? = null,
    editDTag: String? = null,
) {
    val vm: NewCalendarEventViewModel = rememberViewModel { NewCalendarEventViewModel() }
    vm.init(accountViewModel.account)
    if (editKind != null && editPubKeyHex != null && editDTag != null) {
        // loadForEdit is idempotent across recompositions; safe to call from the composable body.
        vm.loadForEdit(accountViewModel.account, editKind, editPubKeyHex, editDTag)
    }

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = if (vm.isEditing) Res.string.save else Res.string.publish,
                titleRes = if (vm.isEditing) Res.string.edit_calendar_event else Res.string.new_calendar_event,
                // Not while the cover is still uploading (the event would go out without it) or a publish is running.
                isActive = { vm.isValid() && vm.isEndAfterStart() && !vm.isUploadingImage.value && !vm.isPublishing.value },
                onCancel = { nav.popBack() },
                onPost = {
                    accountViewModel.launchSigner {
                        if (vm.publish()) {
                            nav.popBack()
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AllDayToggleRow(vm)

            SpellCheckedOutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(Res.string.calendar_event_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            val isAllDay by vm.isAllDay
            FieldLabel(stringRes(Res.string.calendar_event_start))
            CalendarDateTimePickerButton(
                unixSeconds = vm.startSeconds.value,
                placeholder = stringRes(Res.string.calendar_event_pick_date),
                includeTime = !isAllDay,
                onChange = { vm.startSeconds.value = it },
            )

            FieldLabel(stringRes(Res.string.calendar_event_end))
            CalendarDateTimePickerButton(
                unixSeconds = vm.endSeconds.value,
                placeholder = stringRes(Res.string.calendar_event_pick_date),
                includeTime = !isAllDay,
                onChange = { vm.endSeconds.value = it },
            )

            OutlinedTextField(
                value = vm.location.value,
                onValueChange = { vm.location.value = it },
                label = { Text(stringRes(Res.string.calendar_event_location)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SpellCheckedOutlinedTextField(
                value = vm.summary.value,
                onValueChange = { vm.summary.value = it },
                label = { Text(stringRes(Res.string.calendar_event_summary)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            ImageRow(vm = vm, accountViewModel = accountViewModel)

            OutlinedTextField(
                value = vm.hashtags.value,
                onValueChange = { vm.hashtags.value = it },
                label = { Text(stringRes(Res.string.calendar_event_hashtags)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ParticipantsRow(vm = vm, accountViewModel = accountViewModel)

            if (!vm.isValid()) {
                Text(
                    text = stringRes(Res.string.calendar_event_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (!vm.isEndAfterStart()) {
                Text(
                    text = stringRes(Res.string.calendar_event_end_before_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AllDayToggleRow(vm: NewCalendarEventViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(Res.string.calendar_event_all_day),
                style = MaterialTheme.typography.titleSmall,
            )
            if (vm.isEditing) {
                // Toggling all-day mid-edit would mean a different event kind (31922 vs 31923)
                // and a different addressable, leaving the original event live as a stale copy.
                // The user can delete the appointment and re-create if they want to change kind.
                Text(
                    text = stringRes(Res.string.calendar_event_all_day_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = vm.isAllDay.value,
            onCheckedChange = { vm.isAllDay.value = it },
            enabled = !vm.isEditing,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/**
 * URL field + gallery-picker icon. Tapping the icon launches the system picker; once the user
 * picks an image we hand it to [NewCalendarEventViewModel.uploadAndSetImage], which sends it
 * to the user's configured file server (Blossom / NIP-96) and writes the resulting
 * URL into [vm.imageUrl]. A small inline progress indicator covers the upload window.
 */
@Composable
private fun ImageRow(
    vm: NewCalendarEventViewModel,
    accountViewModel: AccountViewModel,
) {
    val scope = rememberCoroutineScope()
    var pickImage by remember { mutableStateOf(false) }
    if (pickImage) {
        GallerySelectSingle(imagesOnly = true) { media ->
            pickImage = false
            if (media == null) return@GallerySelectSingle
            scope.launch {
                val ok = vm.uploadAndSetImage(media.uri, media.mimeType, accountViewModel.mediaUploader)
                if (!ok) {
                    accountViewModel.toastManager.toast(
                        Res.string.calendar_event_image_upload_failed,
                        Res.string.calendar_event_image_upload_failed_body,
                    )
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = vm.imageUrl.value,
            onValueChange = { vm.imageUrl.value = it },
            label = { Text(stringRes(Res.string.calendar_event_image)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            enabled = !vm.isUploadingImage.value,
        )
        if (vm.isUploadingImage.value) {
            CircularProgressIndicator(
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            IconButton(onClick = { pickImage = true }) {
                Icon(
                    symbol = MaterialSymbols.AddPhotoAlternate,
                    contentDescription = stringRes(Res.string.calendar_event_pick_image),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Inline participant picker. The simplest workable shape: a single-line OutlinedTextField that
 * accepts a 64-hex pubkey or an npub, and an "Add" button that pushes it into the list. The
 * current participants render as a column of [UserRow] entries with an X button each.
 *
 * Search-by-display-name is an obvious follow-up but the field accepting npubs directly is the
 * primary nostr-native flow today; copy-pasting an npub from a profile is how most users add
 * collaborators in other apps.
 */
@Composable
private fun ParticipantsRow(
    vm: NewCalendarEventViewModel,
    accountViewModel: AccountViewModel,
) {
    // Same suggestion machinery the badge-award / DM / new-post screens use: type a name, npub,
    // hex, or nip-05; the LazyColumn below shows live matches from the local cache + relay
    // search; tapping a row adds the user to the participants list.
    val userSuggestions =
        remember { UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder()) }
    DisposableEffect(Unit) { onDispose { userSuggestions.reset() } }

    var searchInput by rememberSaveable { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(stringRes(Res.string.calendar_event_participants_section, vm.participants.size))

        OutlinedTextField(
            value = searchInput,
            onValueChange = {
                searchInput = it
                if (it.length > 2) {
                    userSuggestions.processCurrentWord(it)
                } else {
                    userSuggestions.reset()
                }
            },
            label = { Text(stringRes(Res.string.calendar_event_participant_input)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (searchInput.length > 2) {
            ShowUserSuggestionList(
                userSuggestions = userSuggestions,
                onSelect = { user ->
                    vm.addParticipant(user.pubkeyHex)
                    searchInput = ""
                    userSuggestions.reset()
                },
                accountViewModel = accountViewModel,
                modifier = SuggestionListDefaultHeightChat,
            )
        }

        vm.participants.forEach { pubKey ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClickableUserPicture(
                    baseUserHex = pubKey,
                    size = Size30dp,
                    accountViewModel = accountViewModel,
                )
                LoadUser(baseUserHex = pubKey) { user ->
                    if (user != null) {
                        UsernameDisplay(
                            baseUser = user,
                            weight = Modifier.weight(1f).padding(horizontal = 8.dp),
                            accountViewModel = accountViewModel,
                        )
                    } else {
                        Text(
                            text = pubKey.take(8) + "…" + pubKey.takeLast(8),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.removeParticipant(pubKey) }) {
                    Icon(
                        symbol = MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.calendar_event_participant_remove),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
