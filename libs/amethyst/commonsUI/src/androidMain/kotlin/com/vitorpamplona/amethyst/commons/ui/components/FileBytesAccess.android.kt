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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ACTION_CREATE_DOCUMENT with the type and suggested name chosen per request. */
private class CreateDocumentOf : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(
        context: Context,
        input: Pair<String, String>,
    ) = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(input.second)
        .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? = if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private class PendingPicks {
    var save: CompletableDeferred<Uri?>? = null
    var open: CompletableDeferred<Uri?>? = null
}

@Composable
actual fun rememberFileBytesAccess(): FileBytesAccess {
    val context = LocalContext.current
    val pending = remember { PendingPicks() }
    val saver = rememberLauncherForActivityResult(CreateDocumentOf()) { pending.save?.complete(it) }
    // OpenDocument, not GetContent: GetContent is intercepted by the photo picker's shim on recent
    // Android, which hands a non-media file back as a cancel.
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pending.open?.complete(it) }

    return remember(context, saver, opener) {
        object : FileBytesAccess {
            override suspend fun save(
                suggestedName: String,
                mimeType: String,
                content: suspend () -> ByteArray,
            ): Boolean {
                val result = CompletableDeferred<Uri?>().also { pending.save = it }
                saver.launch(suggestedName to mimeType)
                val target = result.await() ?: return false
                val bytes = content()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target)?.use { it.write(bytes) } ?: error("Cannot write to $target")
                }
                return true
            }

            override suspend fun open(mimeTypes: List<String>): ByteArray? {
                val result = CompletableDeferred<Uri?>().also { pending.open = it }
                opener.launch(mimeTypes.toTypedArray())
                val source = result.await() ?: return null
                return withContext(Dispatchers.IO) { context.contentResolver.openInputStream(source)?.use { it.readBytes() } }
            }
        }
    }
}
