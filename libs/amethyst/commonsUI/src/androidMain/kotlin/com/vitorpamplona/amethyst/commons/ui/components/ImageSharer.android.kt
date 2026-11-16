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
package com.vitorpamplona.amethyst.commons.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberImageSharer(): ImageSharer {
    val context = LocalContext.current
    return remember(context) { AndroidImageSharer(context) }
}

private class AndroidImageSharer(
    val context: Context,
) : ImageSharer {
    override val canShare = true

    override val opensShareSheet = true

    override suspend fun writePng(image: ImageBitmap): MediaUri = withContext(Dispatchers.IO) { saveToCache(image) }

    override suspend fun share(
        image: ImageBitmap,
        chooserTitle: String,
    ): Boolean {
        val uri = writePng(image)
        val sendIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = PNG_MIME
                putExtra(Intent.EXTRA_STREAM, uri)
                // Attaching the URI as ClipData (not just EXTRA_STREAM) lets the system share sheet
                // itself read the file so it can render the image thumbnail preview at the top.
                clipData = ClipData.newUri(context.contentResolver, "Image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
        return true
    }

    /** The file lives in `cacheDir`, covered by the `<cache-path>` entry in `file_paths.xml`. */
    private fun saveToCache(image: ImageBitmap): Uri {
        // The receiving app reads the file after we hand it over, so it can't be deleted then;
        // the previous shares' files can, once any receiver has long finished with them.
        val staleBefore = System.currentTimeMillis() - STALE_SHARE_MS
        context.cacheDir
            .listFiles { f -> f.name.startsWith(SHARE_FILE_PREFIX) && f.lastModified() < staleBefore }
            ?.forEach { it.delete() }

        val file = File(context.cacheDir, "$SHARE_FILE_PREFIX${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
}

private const val PNG_MIME = "image/png"

private const val SHARE_FILE_PREFIX = "amethyst_share_"

private const val STALE_SHARE_MS = 60 * 60 * 1000L
