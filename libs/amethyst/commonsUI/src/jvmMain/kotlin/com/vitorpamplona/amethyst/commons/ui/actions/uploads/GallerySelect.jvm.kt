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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.upload_file
import com.vitorpamplona.amethyst.commons.resources.upload_image
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.platform.showModalDialog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.nio.file.Files

// The system's file dialog. It is modal, so it opens through showModalDialog: blocking inside the
// composition's dispatcher crashes the event thread when the window paints in its nested loop.

internal val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "avif", "heic", "bmp")
private val MEDIA_EXTENSIONS = IMAGE_EXTENSIONS + setOf("mp4", "webm", "mov", "mkv", "m4v", "mp3", "m4a", "ogg", "opus", "wav", "flac", "aac")

private fun extensionOf(file: File) = file.name.substringAfterLast('.', "").lowercase()

private fun mimeTypeOf(file: File): String? =
    runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
        ?: when (extensionOf(file)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png", "gif", "webp", "avif", "heic", "bmp" -> "image/${extensionOf(file)}"
            "svg" -> "image/svg+xml"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/aac"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> null
        }

internal suspend fun pickFiles(
    title: String,
    multiple: Boolean,
    extensions: Set<String>,
): List<File> =
    showModalDialog {
        val dialog =
            FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
                isMultipleMode = multiple
                filenameFilter = FilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
            }
        dialog.isVisible = true
        dialog.files?.toList().orEmpty()
    }

internal fun File.toSelectedMedia() = SelectedMedia(StringMediaUri(absolutePath), mimeTypeOf(this))

@Composable
actual fun GallerySelect(onImageUri: (ImmutableList<SelectedMedia>) -> Unit) {
    LaunchedEffect(Unit) {
        val files = pickFiles(loadStringRes(Res.string.upload_file), multiple = true, MEDIA_EXTENSIONS)
        onImageUri(files.map { it.toSelectedMedia() }.toImmutableList())
    }
}

@Composable
actual fun GallerySelectSingle(
    imagesOnly: Boolean,
    onImageUri: (SelectedMedia?) -> Unit,
) {
    LaunchedEffect(Unit) {
        val title = loadStringRes(if (imagesOnly) Res.string.upload_image else Res.string.upload_file)
        val file = pickFiles(title, multiple = false, if (imagesOnly) IMAGE_EXTENSIONS else MEDIA_EXTENSIONS).firstOrNull()
        onImageUri(file?.toSelectedMedia())
    }
}
