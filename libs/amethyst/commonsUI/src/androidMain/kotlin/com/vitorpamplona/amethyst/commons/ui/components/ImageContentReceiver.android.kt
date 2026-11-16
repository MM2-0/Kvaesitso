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

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.extensionFromMimeType
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class)
actual fun Modifier.imageContentReceiver(onImage: (MediaUri, String?) -> Unit): Modifier =
    composed {
        val appContext = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val currentOnImage by rememberUpdatedState(onImage)
        val listener =
            remember(appContext, scope) {
                object : ReceiveContentListener {
                    override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
                        if (!transferableContent.hasMediaType(MediaType.Image)) {
                            return transferableContent
                        }
                        val remaining = transferableContent.consume { it.uri != null }
                        // A keyboard's read grant is revoked once the InputContentInfo that Compose
                        // keeps in transferableContent's extras is garbage collected, which can be
                        // long before the user presses upload. Reading the items from
                        // transferableContent inside the coroutine keeps it reachable until the
                        // copies are done, so the upload reads our own copy.
                        scope.launch {
                            val clip = transferableContent.clipEntry.clipData
                            val mimeType = clip.description.getMimeType(0)
                            val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                            val copies =
                                withContext(Dispatchers.IO) {
                                    copyToCache(appContext, uris, mimeType).also { files ->
                                        // The composer closed mid-copy: nobody will upload these.
                                        if (!isActive) files.forEach { it?.delete() }
                                    }
                                }
                            uris.zip(copies).forEach { (uri, copy) -> currentOnImage(copy?.toUri() ?: uri, mimeType) }
                        }
                        return remaining
                    }
                }
            }
        contentReceiver(listener)
    }

private const val RECEIVED_DIR = "received_content"

/** Copies each of [uris] into the app cache; a null entry falls back to the original address. */
private fun copyToCache(
    context: Context,
    uris: List<Uri>,
    mimeType: String?,
): List<File?> {
    val dir = File(context.cacheDir, RECEIVED_DIR).apply { mkdirs() }
    deleteStaleFiles(dir)
    val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) ?: extensionFromMimeType(it) } ?: "bin"
    return uris.map { uri ->
        var file: File? = null
        try {
            file = File.createTempFile("received-", ".$extension", dir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
                file
            }
        } catch (e: Exception) {
            Log.w("ImageContentReceiver", "Could not copy received content $uri", e)
            null
        } ?: run {
            file?.delete()
            null
        }
    }
}

/** Received files are only needed until the upload finishes; drop those left from earlier days. */
private fun deleteStaleFiles(dir: File) {
    val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
    dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
}
