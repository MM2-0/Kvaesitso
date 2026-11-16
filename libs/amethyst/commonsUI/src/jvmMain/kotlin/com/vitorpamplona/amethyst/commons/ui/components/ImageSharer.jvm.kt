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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.mediaUriOfFile
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.awt.HeadlessException
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/** Desktop has no share sheet: the image goes to the clipboard, ready to paste anywhere. */
private object ClipboardImageSharer : ImageSharer {
    override val canShare = true

    override val opensShareSheet = false

    override suspend fun writePng(image: ImageBitmap): MediaUri =
        withContext(Dispatchers.IO) {
            // The per-user (0700) temp dir, whose leftovers are swept at startup after a crash.
            val file = AmethystTempDir.createTempFile("amethyst_share_", ".png")
            file.deleteOnExit()
            file.writeBytes(image.encodePng())
            mediaUriOfFile(file.toOkioPath())
        }

    override suspend fun share(
        image: ImageBitmap,
        chooserTitle: String,
    ): Boolean {
        // A per-pixel copy, tens of MB for a long card: keep it off the UI thread.
        val awtImage = withContext(Dispatchers.Default) { image.toAwtImage() }
        return try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(awtImage), null)
            true
        } catch (e: IllegalStateException) {
            // Another app holds the clipboard open (Windows).
            Log.w("ImageSharer", "Clipboard unavailable", e)
            false
        } catch (e: HeadlessException) {
            Log.w("ImageSharer", "No clipboard in a headless JVM", e)
            false
        }
    }
}

private class ImageTransferable(
    val image: Image,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }
}

@Composable
actual fun rememberImageSharer(): ImageSharer = ClipboardImageSharer
