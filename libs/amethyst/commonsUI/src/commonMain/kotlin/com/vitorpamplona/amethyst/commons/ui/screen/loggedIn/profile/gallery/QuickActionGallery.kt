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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete_dialog_btn
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_gallery_alert_body_v2
import com.vitorpamplona.amethyst.commons.resources.quick_action_request_deletion_gallery_title
import com.vitorpamplona.amethyst.commons.ui.note.QuickActionAlertDialogOneButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun QuickActionGallery(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable (() -> Unit) -> Unit,
) {
    val popupExpanded = remember { mutableStateOf(false) }

    content { popupExpanded.value = true }

    if (popupExpanded.value && baseNote.author == accountViewModel.account.userProfile()) {
        DeleteFromGalleryDialog(
            note = baseNote,
            onDismiss = { popupExpanded.value = false },
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun DeleteFromGalleryDialog(
    note: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    QuickActionAlertDialogOneButton(
        title = stringRes(Res.string.quick_action_request_deletion_gallery_title),
        textContent = stringRes(Res.string.quick_action_request_deletion_gallery_alert_body_v2),
        buttonIcon = MaterialSymbols.Delete,
        buttonText = stringRes(Res.string.quick_action_delete_dialog_btn),
        onClickDoOnce = {
            accountViewModel.removeFromMediaGallery(note)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}
