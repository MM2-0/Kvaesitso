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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip56Reports.ui.ReportDialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.report_dialog_reminder_group
import com.vitorpamplona.amethyst.commons.resources.report_dialog_reminder_public
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun ReportNoteDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    // A report the message's room keeps among its members is for the room's admins to review;
    // it does not also block the author, which would empty the conversation for the reporter.
    val staysInRoom = remember(note) { accountViewModel.reportStaysInRoom(note) }
    ReportDialog(
        onBlock = { note.author?.let { accountViewModel.hide(it) } },
        onReport = { reason, additionalReason ->
            accountViewModel.report(note, reason, additionalReason)
            if (!staysInRoom) note.author?.let { accountViewModel.hide(it) }
        },
        onDismiss = onDismiss,
        reminder = if (staysInRoom) Res.string.report_dialog_reminder_group else Res.string.report_dialog_reminder_public,
    )
}
