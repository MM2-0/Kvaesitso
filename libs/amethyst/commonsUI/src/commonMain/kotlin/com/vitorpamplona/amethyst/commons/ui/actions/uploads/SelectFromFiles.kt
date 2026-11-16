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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.upload_file
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.collections.immutable.ImmutableList

@Composable
fun SelectFromFiles(
    isUploading: Boolean,
    enabled: Boolean = true,
    tint: Color,
    modifier: Modifier,
    onFilesChosen: (ImmutableList<SelectedMedia>) -> Unit,
) {
    var showFileSelect by remember { mutableStateOf(false) }
    if (showFileSelect) {
        FileSelect(
            onFilesSelected = { files ->
                showFileSelect = false
                if (files.isNotEmpty()) {
                    onFilesChosen(files)
                }
            },
        )
    }

    FileSelectButton(isUploading, enabled, tint, modifier) { showFileSelect = true }
}

@Composable
private fun FileSelectButton(
    isUploading: Boolean,
    enabled: Boolean,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    HoverTooltip(stringRes(id = Res.string.upload_file)) {
        IconButton(
            modifier = modifier,
            enabled = enabled && !isUploading,
            onClick = { onClick() },
        ) {
            if (!isUploading) {
                Icon(
                    symbol = MaterialSymbols.AttachFile,
                    contentDescription = stringRes(id = Res.string.upload_file),
                    modifier = Modifier.height(20.dp),
                    tint = tint,
                )
            } else {
                LoadingAnimation()
            }
        }
    }
}

/** Opens the platform's document picker for audio and PDF files; reports an empty list on cancel. */
@Composable
expect fun FileSelect(onFilesSelected: (ImmutableList<SelectedMedia>) -> Unit = {})

/** Opens the platform's document picker for one file of [mimeTypes]; reports null on cancel. */
@Composable
expect fun DocumentSelectSingle(
    mimeTypes: List<String>,
    onPicked: (SelectedMedia?) -> Unit,
)

/**
 * Opens the platform's document picker for any number of files of [mimeTypes], which may use a
 * subtype wildcard (all images); reports an empty list on cancel.
 */
@Composable
expect fun MultiDocumentSelect(
    mimeTypes: List<String>,
    onPicked: (ImmutableList<SelectedMedia>) -> Unit,
)
