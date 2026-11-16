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
package com.vitorpamplona.amethyst.commons.ui.note.elements

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ots_info_description
import com.vitorpamplona.amethyst.commons.resources.ots_info_pending_description
import com.vitorpamplona.amethyst.commons.resources.ots_info_title
import com.vitorpamplona.amethyst.commons.resources.timestamp_pending_short
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.HeaderPill
import com.vitorpamplona.amethyst.commons.ui.note.LoadOts
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * Compact pill showing the note's NIP-03 OpenTimestamps proof: how long ago
 * the note is proven to have existed. Tapping it explains the proof and shows
 * the attested date. Sits inline in note headers.
 */
@Composable
fun DisplayOts(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    LoadOts(
        note,
        accountViewModel,
        whenConfirmed = { unixtimestamp ->
            val timeStr = timeAgoNoDot(unixtimestamp)

            HeaderPill(
                symbol = MaterialSymbols.OpenTimestamps,
                text = timeStr,
                contentDescription = stringRes(Res.string.ots_info_title),
                onClick = {
                    accountViewModel.toastManager.toast(
                        Res.string.ots_info_title,
                        Res.string.ots_info_description,
                        formatDateTime(unixtimestamp * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.MEDIUM),
                    )
                },
            )
        },
        whenPending = {
            // The stamp icon plus an ellipsis: attestation requested, not yet
            // anchored. The content description carries the words; tapping
            // explains what is being waited on.
            HeaderPill(
                symbol = MaterialSymbols.OpenTimestamps,
                text = "…",
                contentDescription = stringRes(Res.string.timestamp_pending_short),
                onClick = {
                    accountViewModel.toastManager.toast(
                        Res.string.ots_info_title,
                        Res.string.ots_info_pending_description,
                    )
                },
            )
        },
    )
}
