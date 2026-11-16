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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.note.BookmarkToggleButton
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * A bookmark toggle for a podcast (show or episode). "Favoriting"/subscribing to a podcast reuses
 * the existing NIP-51 bookmark list (kind 10003) — a public bookmark matches the soft public
 * recommendation the dedicated favorites list (10054) was meant for, and the note then appears in
 * the standard Bookmarks screen. Works for both regular events (e-tag) and addressable shows/
 * episodes (a-tag) because [AccountViewModel.addPublicBookmark] branches on the note type.
 *
 * Bookmarked state is read from the public bookmark id/address **sets** (not `List<Note>`
 * containment) so it reflects reliably for addressable notes and updates the moment the list
 * changes — the icon flipping filled/outline is the feedback, no toast needed.
 */
@Composable
fun PodcastBookmarkButton(
    note: Note,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    val bookmarkState = accountViewModel.account.bookmarkState

    val publicAddresses by bookmarkState.publicBookmarkAddressIdSet.collectAsStateWithLifecycle()
    val publicEvents by bookmarkState.publicBookmarkEventIdSet.collectAsStateWithLifecycle()

    val isBookmarked =
        remember(note, publicAddresses, publicEvents) {
            if (note is AddressableNote) {
                note.address in publicAddresses
            } else {
                note.idHex in publicEvents
            }
        }

    BookmarkToggleButton(
        isBookmarked = isBookmarked,
        onToggle = {
            if (isBookmarked) {
                accountViewModel.removePublicBookmark(note)
            } else {
                accountViewModel.addPublicBookmark(note)
            }
        },
        modifier = modifier,
        iconSize = iconSize,
    )
}
