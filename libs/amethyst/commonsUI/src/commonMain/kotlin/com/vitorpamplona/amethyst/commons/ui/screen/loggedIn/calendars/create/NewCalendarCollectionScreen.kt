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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.calendars.create.NewCalendarCollectionViewModel
import com.vitorpamplona.amethyst.commons.calendars.create.OwnedAppointmentSummary
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_all_day
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_delete
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_delete_confirm_message
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_delete_confirm_title
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_description
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_events_section
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_invalid
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_no_events_yet
import com.vitorpamplona.amethyst.commons.resources.calendar_collection_title
import com.vitorpamplona.amethyst.commons.resources.calendar_untitled
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.create
import com.vitorpamplona.amethyst.commons.resources.edit_calendar_collection
import com.vitorpamplona.amethyst.commons.resources.new_calendar_collection
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCalendarCollectionScreen(
    nav: INav,
    accountViewModel: AccountViewModel,
    editDTag: String? = null,
) {
    val vm: NewCalendarCollectionViewModel = rememberViewModel { NewCalendarCollectionViewModel() }
    vm.init(accountViewModel.account, editDTag)

    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = if (editDTag == null) Res.string.create else Res.string.save,
                titleRes = if (editDTag == null) Res.string.new_calendar_collection else Res.string.edit_calendar_collection,
                isActive = { vm.isValid() && !vm.isPublishing.value },
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
            SpellCheckedOutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(Res.string.calendar_collection_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = !vm.isValid(),
            )

            SpellCheckedOutlinedTextField(
                value = vm.description.value,
                onValueChange = { vm.description.value = it },
                label = { Text(stringRes(Res.string.calendar_collection_description)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            if (!vm.isValid()) {
                Text(
                    text = stringRes(Res.string.calendar_collection_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            AppointmentPickerSection(vm)

            if (vm.isEditing) {
                DeleteCalendarRow(vm = vm, onDeleted = { nav.popBack() }, accountViewModel = accountViewModel)
            }
        }
    }
}

@Composable
private fun DeleteCalendarRow(
    vm: NewCalendarCollectionViewModel,
    onDeleted: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text = stringRes(Res.string.calendar_collection_delete))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringRes(Res.string.calendar_collection_delete_confirm_title)) },
            text = { Text(stringRes(Res.string.calendar_collection_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    accountViewModel.launchSigner {
                        if (vm.deleteLoaded()) onDeleted()
                    }
                }) {
                    Text(
                        text = stringRes(Res.string.calendar_collection_delete),
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

@Composable
private fun AppointmentPickerSection(vm: NewCalendarCollectionViewModel) {
    val available by vm.availableAppointments
    val selectedCount = vm.selectedAddresses.size

    Text(
        text = stringRes(Res.string.calendar_collection_events_section, selectedCount),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )

    if (available.isEmpty()) {
        Text(
            text = stringRes(Res.string.calendar_collection_no_events_yet),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }

    available.forEach { summary ->
        // Snapshot selection state without subscribing to the list itself (we only need to
        // re-render the affected row on toggle).
        val isSelected = vm.selectedAddresses.contains(summary.address)
        AppointmentPickerRow(
            summary = summary,
            isSelected = isSelected,
            onToggle = { vm.toggle(summary.address) },
        )
        HorizontalDivider()
    }
}

@Composable
private fun AppointmentPickerRow(
    summary: OwnedAppointmentSummary,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    // `stringRes` must be called outside `remember` (it's @Composable). The date format is the
    // only piece worth memoising — the localised "All-day" label is a single lookup per row.
    val allDayLabel = stringRes(Res.string.calendar_all_day)
    val whenLabel =
        remember(summary.address, summary.startSeconds, summary.isAllDay, allDayLabel) {
            val startSeconds = summary.startSeconds
            when {
                summary.isAllDay -> allDayLabel
                startSeconds != null -> formatLongDate(startSeconds)
                else -> "—"
            }
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Column(
            modifier = Modifier.padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = summary.title.ifBlank { stringRes(Res.string.calendar_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = whenLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
