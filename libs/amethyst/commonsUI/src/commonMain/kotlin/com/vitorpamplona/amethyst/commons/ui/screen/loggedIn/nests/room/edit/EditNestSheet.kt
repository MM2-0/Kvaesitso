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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nest_close_action
import com.vitorpamplona.amethyst.commons.resources.nest_close_room_confirm_action
import com.vitorpamplona.amethyst.commons.resources.nest_close_room_confirm_body
import com.vitorpamplona.amethyst.commons.resources.nest_close_room_confirm_title
import com.vitorpamplona.amethyst.commons.resources.nest_create_cancel
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_endpoint
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_image
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_room
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_service
import com.vitorpamplona.amethyst.commons.resources.nest_create_field_summary
import com.vitorpamplona.amethyst.commons.resources.nest_edit_save
import com.vitorpamplona.amethyst.commons.resources.nest_edit_title
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import kotlinx.coroutines.launch

/**
 * Bottom sheet that lets the host edit the current room — pre-fills
 * from the existing kind-30312 event, republishes with the same
 * `d`-tag on save (so the relay treats it as a replacement of the
 * original), or closes the room with a destructive button.
 *
 * Visibility gating (host-only) is the caller's job;
 * [EditNestViewModel] preserves every promoted participant
 * verbatim, so a save MUST NOT silently demote anyone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNestSheet(
    accountViewModel: AccountViewModel,
    event: MeetingSpaceEvent,
    onDismiss: () -> Unit,
) {
    val key = remember(event) { "EditNest-${event.dTag()}" }
    val viewModel: EditNestViewModel = rememberViewModel(key = key) { EditNestViewModel() }
    LaunchedEffect(viewModel, event) { viewModel.bind(accountViewModel, event) }

    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var confirmCloseOpen by remember { mutableStateOf(false) }
    if (confirmCloseOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmCloseOpen = false },
            title = { Text(stringRes(Res.string.nest_close_room_confirm_title)) },
            text = { Text(stringRes(Res.string.nest_close_room_confirm_body)) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        confirmCloseOpen = false
                        scope.launch {
                            if (viewModel.closeRoom()) onDismiss()
                        }
                    },
                ) {
                    Text(stringRes(Res.string.nest_close_room_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCloseOpen = false }) {
                    Text(stringRes(Res.string.nest_create_cancel))
                }
            },
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Two-region layout so the keyboard never squashes the action
        // row: the form scrolls inside its own viewport, the bottom
        // buttons stay anchored. `imePadding()` on the outer Column
        // lifts the whole sheet above the keyboard, so the anchored
        // row sits just above the IME instead of behind it.
        Column(
            modifier = Modifier.fillMaxWidth().imePaddingSafe(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringRes(Res.string.nest_edit_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                SpellCheckedOutlinedTextField(
                    value = state.roomName,
                    onValueChange = viewModel::setRoomName,
                    label = { Text(stringRes(Res.string.nest_create_field_room)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SpellCheckedOutlinedTextField(
                    value = state.summary,
                    onValueChange = viewModel::setSummary,
                    label = { Text(stringRes(Res.string.nest_create_field_summary)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.serviceUrl,
                    onValueChange = viewModel::setServiceUrl,
                    label = { Text(stringRes(Res.string.nest_create_field_service)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.endpointUrl,
                    onValueChange = viewModel::setEndpointUrl,
                    label = { Text(stringRes(Res.string.nest_create_field_endpoint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.imageUrl,
                    onValueChange = viewModel::setImageUrl,
                    label = { Text(stringRes(Res.string.nest_create_field_image)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(4.dp))
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Destructive — flips status to CLOSED with the same d-tag
                // so subscribers see the room go dark. Confirm prompt
                // gates the actual publish so a misclick on the
                // edit-sheet's bottom row doesn't kill the room.
                TextButton(
                    enabled = !state.isPublishing,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = { confirmCloseOpen = true },
                ) {
                    Text(stringRes(Res.string.nest_close_action))
                }
                Row {
                    TextButton(onClick = onDismiss, enabled = !state.isPublishing) {
                        Text(stringRes(Res.string.nest_create_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = state.canSubmit,
                        onClick = {
                            scope.launch {
                                if (viewModel.save()) onDismiss()
                            }
                        },
                    ) {
                        if (state.isPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringRes(Res.string.nest_edit_save))
                        }
                    }
                }
            }
        }
    }
}
