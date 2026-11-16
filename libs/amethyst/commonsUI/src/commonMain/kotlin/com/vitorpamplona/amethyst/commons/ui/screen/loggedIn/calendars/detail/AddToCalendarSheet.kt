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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_add_to_calendar_none
import com.vitorpamplona.amethyst.commons.resources.calendar_add_to_calendar_title
import com.vitorpamplona.amethyst.commons.resources.calendar_untitled
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTags
import com.vitorpamplona.quartz.nip52Calendar.calendar.CalendarCollectionEvent

/**
 * Bottom sheet that lists the current user's own kind-31924 calendars with a checkbox per
 * calendar. Tapping a row toggles membership of [targetAddress] in that calendar and re-signs
 * the calendar with the updated `a` tag list. The sheet stays open while edits flow so users
 * can toggle multiple calendars without dismissing.
 *
 * Reactive: collects [LocalCache.live.newEventBundles] so newly-broadcast calendars (or our own
 * just-published edits) appear / disappear without dismissing and reopening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToCalendarSheet(
    targetAddress: Address,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val myPubKey = accountViewModel.userProfile().pubkeyHex

    val ownCalendars by produceState<List<CalendarCollectionEvent>>(initialValue = ownCalendars(myPubKey), myPubKey) {
        LocalCache.live.newEventBundles.collect {
            value = ownCalendars(myPubKey)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringRes(Res.string.calendar_add_to_calendar_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (ownCalendars.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringRes(Res.string.calendar_add_to_calendar_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(ownCalendars, key = { it.dTag() }) { calendar ->
                    val isMember = calendar.calendarEventAddresses().contains(targetAddress)
                    CalendarPickerRow(
                        title = calendar.title() ?: stringRes(Res.string.calendar_untitled),
                        isMember = isMember,
                        onToggle = {
                            toggleMembership(
                                accountViewModel = accountViewModel,
                                calendar = calendar,
                                targetAddress = targetAddress,
                                isCurrentlyMember = isMember,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPickerRow(
    title: String,
    isMember: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isMember, onCheckedChange = { onToggle() })
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ownCalendars(myPubKey: String): List<CalendarCollectionEvent> =
    LocalCache.addressables
        .filterIntoSet { _, note ->
            val e = note.event
            e is CalendarCollectionEvent && e.pubKey == myPubKey
        }.mapNotNull { it.event as? CalendarCollectionEvent }
        .sortedBy { it.title()?.lowercase() ?: "" }

private fun toggleMembership(
    accountViewModel: AccountViewModel,
    calendar: CalendarCollectionEvent,
    targetAddress: Address,
    isCurrentlyMember: Boolean,
) {
    // Existing `a` tags minus the target if removing, plus the target if adding. Preserves
    // the calendar's d-tag so the broadcast replaces the addressable rather than minting a
    // new one — same pattern as the edit-collection flow.
    val newAddresses =
        if (isCurrentlyMember) {
            calendar.calendarEventAddresses().filterNot { it == targetAddress }
        } else {
            calendar.calendarEventAddresses() + targetAddress
        }
    accountViewModel.launchSigner {
        accountViewModel.account.signAndComputeBroadcast(
            CalendarCollectionEvent.build(
                title = calendar.title().orEmpty(),
                content = calendar.content,
                dTag = calendar.dTag(),
            ) {
                if (newAddresses.isNotEmpty()) aTags(newAddresses.map { ATag(it) })
            },
        )
    }
}
