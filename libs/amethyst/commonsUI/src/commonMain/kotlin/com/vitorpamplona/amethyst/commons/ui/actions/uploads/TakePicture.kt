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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.record_a_video
import com.vitorpamplona.amethyst.commons.resources.take_a_picture
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.collections.immutable.ImmutableList

/** Whether this platform can capture a photo or video from a camera app. */
expect val canCaptureFromCamera: Boolean

/**
 * Asks for the camera permission if needed, then opens the camera to take a picture. Reports the
 * captured media, or an empty list when the user cancels or no camera app is available.
 */
@Composable
expect fun TakePicture(onPictureTaken: (ImmutableList<SelectedMedia>) -> Unit)

/** Like [TakePicture], but records a video. */
@Composable
expect fun TakeVideo(onVideoTaken: (ImmutableList<SelectedMedia>) -> Unit)

@Composable
fun TakePictureButton(onPictureTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    if (!canCaptureFromCamera) return

    var showCamera by remember { mutableStateOf(false) }
    if (showCamera) {
        TakePicture(
            onPictureTaken = { uri ->
                showCamera = false
                if (uri.isNotEmpty()) {
                    onPictureTaken(uri)
                }
            },
        )
    }

    PictureButton { showCamera = true }
}

@Composable
fun PictureButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
    ) {
        Icon(
            symbol = MaterialSymbols.CameraAlt,
            contentDescription = stringRes(id = Res.string.take_a_picture),
            modifier = Modifier.height(22.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun TakeVideoButton(onVideoTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    if (!canCaptureFromCamera) return

    var showCamera by remember { mutableStateOf(false) }
    if (showCamera) {
        TakeVideo(
            onVideoTaken = { uri ->
                showCamera = false
                if (uri.isNotEmpty()) {
                    onVideoTaken(uri)
                }
            },
        )
    }

    VideoButton { showCamera = true }
}

@Composable
fun VideoButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
    ) {
        Icon(
            symbol = MaterialSymbols.Videocam,
            contentDescription = stringRes(id = Res.string.record_a_video),
            modifier = Modifier.height(22.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}
