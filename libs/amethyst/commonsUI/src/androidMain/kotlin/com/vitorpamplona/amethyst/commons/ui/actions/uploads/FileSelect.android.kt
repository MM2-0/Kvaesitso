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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
actual fun FileSelect(onFilesSelected: (ImmutableList<SelectedMedia>) -> Unit) {
    // Saveable: a picker open across an activity recreation must not be launched a second time.
    var hasLaunched by rememberSaveable { mutableStateOf(false) }
    val resolver = LocalContext.current.contentResolver

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = { uris: List<Uri> ->
                onFilesSelected(
                    uris
                        .map {
                            SelectedMedia(it, resolver.getType(it))
                        }.toImmutableList(),
                )
                hasLaunched = false
            },
        )

    @Composable
    fun LaunchFilePicker() {
        SideEffect {
            if (!hasLaunched) {
                hasLaunched = true
                launcher.launch(
                    arrayOf(
                        "audio/*",
                        "application/pdf",
                    ),
                )
            }
        }
    }

    LaunchFilePicker()
}

@Composable
actual fun DocumentSelectSingle(
    mimeTypes: List<String>,
    onPicked: (SelectedMedia?) -> Unit,
) {
    var hasLaunched by rememberSaveable { mutableStateOf(false) }
    val resolver = LocalContext.current.contentResolver
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri: Uri? ->
                hasLaunched = false
                onPicked(uri?.let { SelectedMedia(it, resolver.getType(it)) })
            },
        )
    LaunchedEffect(Unit) {
        if (!hasLaunched) {
            hasLaunched = true
            launcher.launch(mimeTypes.toTypedArray())
        }
    }
}

@Composable
actual fun MultiDocumentSelect(
    mimeTypes: List<String>,
    onPicked: (ImmutableList<SelectedMedia>) -> Unit,
) {
    var hasLaunched by rememberSaveable { mutableStateOf(false) }
    val resolver = LocalContext.current.contentResolver
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
            onResult = { uris: List<Uri> ->
                hasLaunched = false
                onPicked(uris.map { SelectedMedia(it, resolver.getType(it)) }.toImmutableList())
            },
        )
    LaunchedEffect(Unit) {
        if (!hasLaunched) {
            hasLaunched = true
            launcher.launch(mimeTypes.toTypedArray())
        }
    }
}
