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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// Decrypt-once memo per plaintext hash → the on-disk file:// model. Object URLs never change for a
// given hash (content-addressed), so this is safe to keep for the process lifetime and bounded by the
// number of distinct community images a session touches.
private val resolvedByHash = ConcurrentHashMap<String, String>()

/**
 * Resolve a CORD-02 §6 community [pointer] to a model string for `RobohashFallbackAsyncImage`:
 *
 *  - **null / blank** → null (caller falls back to the robohash).
 *  - **plain URL** (a url-only pointer, e.g. Amethyst's own metadata form) → the URL, loaded directly.
 *  - **encrypted** (`key`/`nonce`/`hash` present) → fetch the ciphertext, AES-256-GCM-decrypt + verify
 *    the plaintext SHA-256 (all in [ImagePointer.decryptOrNull]), cache the plaintext to disk, and
 *    return its `file://` path. Returns null while loading or on any fetch/decrypt/integrity failure,
 *    so a swapped or unreachable blob simply shows the robohash instead of garbage.
 *
 * The plaintext lands in [cacheDir], which must be private to the app (it holds a private
 * community's pictures).
 */
@Composable
fun rememberDecryptedConcordImageModel(
    pointer: ImagePointer?,
    accountViewModel: AccountViewModel,
    cacheDir: File,
): String? {
    if (pointer == null) return null

    // A url-only pointer isn't encrypted media — hand the URL straight to Coil.
    if (!pointer.isResolvable()) return pointer.url.ifBlank { null }

    // produceState keeps its value across key changes, so a new pointer starts from its own memo
    // (or nothing) rather than showing the previous picture until it resolves.
    val model by produceState<String?>(resolvedByHash[pointer.hash], pointer, accountViewModel, cacheDir) {
        value = resolvedByHash[pointer.hash]
        if (value != null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatchingCancellable { resolve(pointer, accountViewModel, cacheDir) }
                    .onFailure { Log.w("ConcordImage", "Failed to resolve community image ${pointer.url}", it) }
                    .getOrNull()
            }
    }
    return model
}

/**
 * Downloads, decrypts and verifies [pointer]'s picture into [cacheDir], unless an earlier run
 * already did, and returns its `file://` model. Null when it cannot be fetched or does not verify.
 */
private suspend fun resolve(
    pointer: ImagePointer,
    accountViewModel: AccountViewModel,
    cacheDir: File,
): String? {
    resolvedByHash[pointer.hash]?.let { return it }

    // The hash names the file, so it must be one: a pointer is attacker-controlled input, and a
    // path in it must not reach a file outside [cacheDir].
    val hash = pointer.hash.lowercase()
    if (!SHA256_HEX.matches(hash)) return null

    val cacheFile = File(cacheDir, "concord-img-$hash")
    if (!cacheFile.exists()) {
        val client = accountViewModel.httpClientBuilder.okHttpClientForImage(pointer.url)
        val ciphertext =
            client.newCall(Request.Builder().url(pointer.url).build()).executeAsync().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body.bytes()
            }

        val plaintext = pointer.decryptOrNull(ciphertext) ?: return null
        cacheDir.mkdirs()
        // Written aside and renamed into place, so neither a second screen resolving the same
        // picture nor a crash mid-write leaves a truncated file under the final name.
        val partial = File.createTempFile("concord-img-$hash", ".part", cacheDir)
        try {
            partial.writeBytes(plaintext)
            if (!partial.renameTo(cacheFile) && !cacheFile.exists()) return null
        } finally {
            partial.delete()
        }
    }
    val uri = "file://${cacheFile.absolutePath}"
    resolvedByHash[pointer.hash] = uri
    return uri
}

private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
