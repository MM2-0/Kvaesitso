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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_filter_all
import com.vitorpamplona.amethyst.commons.resources.calendar_filter_no_calendars
import com.vitorpamplona.amethyst.commons.resources.calendar_filter_sheet_title
import com.vitorpamplona.amethyst.commons.resources.calendar_untitled
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Top-bar affordance that scopes the appointments feed to a single kind-31924 calendar's
 * member set. Selecting "All" clears the filter.
 *
 * Filter state lives on [model] (passed in as [selectedDTag] / [onSelect]) so it survives a
 * configuration change and a trip into an appointment, but doesn't persist across launches —
 * keeping a filter sticky between sessions would surprise a user who set it once and forgot.
 * The filter is applied client-side after the feed loads, so changing it doesn't trigger a
 * relay refetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarFilterChip(
    selectedDTag: String?,
    onSelect: (String?) -> Unit,
    model: CalendarsViewModel,
) {
    // Fed by a kind-31924 cache observer on the model, so it wakes for this account's calendars
    // and not for every event the app ingests.
    val ownCalendars by model.ownCalendars.collectAsStateWithLifecycle()
    val selected = ownCalendars.firstOrNull { it.dTag() == selectedDTag }
    val label =
        selected?.title()?.takeIf { it.isNotBlank() }
            ?: stringRes(Res.string.calendar_filter_all)

    var showSheet by remember { mutableStateOf(false) }

    AssistChip(
        onClick = { showSheet = true },
        label = {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
    )

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringRes(Res.string.calendar_filter_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FilterChoiceRow(
                    title = stringRes(Res.string.calendar_filter_all),
                    isSelected = selectedDTag == null,
                    onClick = {
                        onSelect(null)
                        showSheet = false
                    },
                )
                if (ownCalendars.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringRes(Res.string.calendar_filter_no_calendars),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(ownCalendars, key = { it.dTag() }) { calendar ->
                            FilterChoiceRow(
                                title = calendar.title() ?: stringRes(Res.string.calendar_untitled),
                                isSelected = selectedDTag == calendar.dTag(),
                                onClick = {
                                    onSelect(calendar.dTag())
                                    showSheet = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChoiceRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
