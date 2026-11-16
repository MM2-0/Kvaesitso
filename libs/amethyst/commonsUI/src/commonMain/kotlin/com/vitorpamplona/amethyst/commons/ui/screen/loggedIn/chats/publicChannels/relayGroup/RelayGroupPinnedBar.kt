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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_pinned_content_description
import com.vitorpamplona.amethyst.commons.resources.relay_group_pinned_label
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin

/**
 * Self-hiding NIP-29 pinned-message bar shown under the group's top bar. Renders
 * nothing when the group has no pins (kind-39005 empty), so it never obstructs a
 * normal chat. When there are pins (`e` ids or `a` addresses) it shows a single-line preview of the current
 * one; tapping [onJumpToNote] scrolls the feed to that message and, if the group
 * has more than one pin, advances to the next for the following tap (Telegram-style
 * cycling) rather than piling every pin on screen at once.
 */
@Composable
fun RelayGroupPinnedBar(
    channel: RelayGroupChannel,
    accountViewModel: AccountViewModel,
    onJumpToNote: (Note) -> Unit,
) {
    // The full pin list: `e` pins (regular events) and `a` pins (addressable events) interleaved.
    val pinnedIds = channel.pins
    if (pinnedIds.isEmpty()) return

    // Newest pin (last in the relay's display order) surfaced first; reset when the list changes.
    var index by remember(pinnedIds) { mutableIntStateOf(pinnedIds.lastIndex) }
    val safeIndex = index.coerceIn(0, pinnedIds.lastIndex)
    val currentPin = pinnedIds[safeIndex]

    // An `a` pin resolves to the addressable note, whose latest version loads by address.
    val note =
        remember(currentPin) {
            when (currentPin) {
                is AddressPin -> LocalCache.getOrCreateAddressableNote(currentPin.address)
                is EventPin -> LocalCache.checkGetOrCreateNote(currentPin.eventId)
            }
        } ?: return

    // Fetch + observe the pinned message so its author and content fill in once it loads.
    val noteState by observeNote(note, accountViewModel)
    val liveNote = noteState.note

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onJumpToNote(liveNote)
                            // Cycle to the previous (older) pin so a repeat tap walks the history.
                            if (pinnedIds.size > 1) {
                                index = if (safeIndex == 0) pinnedIds.lastIndex else safeIndex - 1
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.PushPin,
                    contentDescription = stringRes(Res.string.relay_group_pinned_content_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringRes(Res.string.relay_group_pinned_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (pinnedIds.size > 1) {
                            Text(
                                text = "${safeIndex + 1}/${pinnedIds.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = pinnedPreview(liveNote) ?: stringRes(Res.string.relay_group_pinned_content_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
        }
    }
}

/**
 * A one-line "Author: message" preview for the bar, or just the message when the
 * author's name hasn't loaded. Null when the pinned event body isn't loaded yet, so
 * the caller can fall back to a generic label.
 */
private fun pinnedPreview(note: Note): String? {
    val content =
        note.event
            ?.content
            ?.replace('\n', ' ')
            ?.trim()
    val author = note.author?.toBestDisplayName()
    return when {
        content.isNullOrBlank() -> author
        author != null -> "$author: $content"
        else -> content
    }
}
