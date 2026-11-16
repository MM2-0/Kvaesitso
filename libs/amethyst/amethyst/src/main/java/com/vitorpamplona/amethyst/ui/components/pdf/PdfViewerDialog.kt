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
package com.vitorpamplona.amethyst.ui.components.pdf

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import com.vitorpamplona.amethyst.commons.ui.components.pdf.PdfViewerContent
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.components.ImmersiveSystemBarsEffect
import com.vitorpamplona.amethyst.ui.components.ViewerSaveToGalleryButton
import com.vitorpamplona.amethyst.ui.components.ViewerShareButton

/** The shared PDF reader in a full-screen, immersive dialog, with Android's share and save buttons. */
@Composable
fun PdfViewerDialog(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        // Go fully immersive while the viewer is open, exactly like the image/video dialog.
        ImmersiveSystemBarsEffect(getDialogWindow())

        val sharePopupExpanded = remember { mutableStateOf(false) }

        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            PdfViewerContent(
                content = content,
                accountViewModel = accountViewModel,
                onDismiss = onDismiss,
                holdControlsOpen = sharePopupExpanded.value,
            ) {
                ViewerShareButton(content, sharePopupExpanded, accountViewModel)
                ViewerSaveToGalleryButton(content, accountViewModel)
            }
        }
    }
}
