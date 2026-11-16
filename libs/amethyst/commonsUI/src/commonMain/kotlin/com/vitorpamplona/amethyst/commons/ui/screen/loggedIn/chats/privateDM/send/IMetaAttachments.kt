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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.privateDM.send

import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip92IMeta.imetaTagBuilder
import com.vitorpamplona.quartz.nip94FileMetadata.alt
import com.vitorpamplona.quartz.nip94FileMetadata.blurhash
import com.vitorpamplona.quartz.nip94FileMetadata.dims
import com.vitorpamplona.quartz.nip94FileMetadata.hash
import com.vitorpamplona.quartz.nip94FileMetadata.magnet
import com.vitorpamplona.quartz.nip94FileMetadata.mimeType
import com.vitorpamplona.quartz.nip94FileMetadata.originalHash
import com.vitorpamplona.quartz.nip94FileMetadata.sensitiveContent
import com.vitorpamplona.quartz.nip94FileMetadata.size
import com.vitorpamplona.quartz.nip94FileMetadata.thumbhash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class IMetaAttachments {
    // Uploads land here from background threads while linked images land from the composer's:
    // every change is an atomic update so neither overwrites the other.
    private val attachments = MutableStateFlow<List<IMetaTag>>(emptyList())

    val iMetaAttachments: List<IMetaTag> get() = attachments.value

    // Links prepareLinkedImages already took on, so each one is downloaded once.
    private val linkedImages = MutableStateFlow<Set<String>>(emptySet())

    // Links whose imeta is still being computed; awaitLinkedImages waits for these.
    private val pendingLinks = MutableStateFlow<Set<String>>(emptySet())

    suspend fun downloadAndPrepare(
        url: String,
        uploader: MediaUploader,
        httpClients: IRoleBasedHttpClientBuilder,
    ) {
        val imeta = uploader.remoteFileHeader(url, httpClients)?.toIMeta(url)

        if (imeta != null) {
            attachments.update { it + imeta }
        }
    }

    /**
     * Gives the images linked in a message the same imeta an upload gets: each one is downloaded,
     * hashed and measured on [scope]. A link is an image when its extension says so or, without
     * one, when [mimeTypeOf] (the composer's URL preview) does. Each link is tried once, and an
     * imeta already there (from an upload or a draft) is never replaced.
     */
    fun prepareLinkedImages(
        urls: List<String>,
        scope: CoroutineScope,
        uploader: MediaUploader,
        httpClients: IRoleBasedHttpClientBuilder,
        mimeTypeOf: suspend (url: String) -> String?,
    ) {
        urls.forEach { url ->
            if (!RichTextParser.isValidURL(url) || RichTextParser.isVideoUrl(url) || RichTextParser.isAudioUrl(url)) return@forEach
            if (hasIMeta(url) || !claimLinkedImage(url)) return@forEach

            pendingLinks.update { it + url }
            scope.launch(Dispatchers.IO) {
                var finished = false
                try {
                    val isImage = RichTextParser.isImageUrl(url) || mimeTypeOf(url)?.startsWith("image/") == true
                    // No dimensions means the bytes did not decode as an image: no imeta for them.
                    val header = if (isImage) uploader.remoteFileHeader(url, httpClients)?.takeIf { it.dim != null } else null
                    if (header != null) {
                        val imeta = header.toIMeta(url)
                        attachments.update { current -> if (current.any { it.url == url }) current else current + imeta }
                    }
                    finished = true
                } finally {
                    // Done before the claim is released, so a new claim's pending mark survives.
                    pendingLinks.update { it - url }
                    // Cancelled (the composer left the screen): try again next time it is shown.
                    if (!finished) linkedImages.update { it - url }
                }
            }
        }
    }

    /**
     * Waits, at most [timeoutMs], for the linked images still being measured, so a post sent right
     * after a paste carries their imeta. Returns at once when nothing is pending.
     */
    suspend fun awaitLinkedImages(timeoutMs: Long = LINKED_IMAGE_WAIT_MS) {
        withTimeoutOrNull(timeoutMs) { pendingLinks.first { it.isEmpty() } }
    }

    private fun hasIMeta(url: String) = iMetaAttachments.any { it.url == url }

    private fun claimLinkedImage(url: String): Boolean {
        var claimed = false
        linkedImages.update {
            claimed = url !in it
            it + url
        }
        return claimed
    }

    private fun FileHeader.toIMeta(url: String) =
        imetaTagBuilder(url) {
            hash(this@toIMeta.hash)
            size(this@toIMeta.size)
            this@toIMeta.mimeType?.let { mimeType(it) }
            dim?.let { dims(it) }
            blurHash?.let { blurhash(it.blurhash) }
            thumbHash?.let { thumbhash(it.thumbhash) }
        }

    fun add(imeta: IMetaTag) {
        replace(imeta.url, imeta)
    }

    fun addAll(imetas: List<IMetaTag>) {
        imetas.forEach {
            replace(it.url, it)
        }
    }

    fun remove(url: String) {
        attachments.update { current -> current.filter { it.url != url } }
    }

    fun replace(
        url: String,
        iMeta: IMetaTag,
    ) {
        attachments.update { current -> current.filter { it.url != url } + iMeta }
    }

    fun add(
        result: UploadOrchestrator.OrchestratorResult.ServerResult,
        alt: String?,
        contentWarningReason: String?,
    ) {
        val iMeta =
            imetaTagBuilder(result.url) {
                hash(result.fileHeader.hash)
                size(result.fileHeader.size)
                result.fileHeader.mimeType?.let { mimeType(it) }
                result.fileHeader.dim?.let { dims(it) }
                result.fileHeader.blurHash?.let { blurhash(it.blurhash) }
                result.fileHeader.thumbHash?.let { thumbhash(it.thumbhash) }
                result.magnet?.let { magnet(it) }
                result.uploadedHash?.let { originalHash(it) }

                alt?.let { alt(it) }
                contentWarningReason?.let { sensitiveContent(contentWarningReason) }
            }

        replace(iMeta.url, iMeta)
    }

    fun filterIsIn(urls: Set<String>) = iMetaAttachments.filter { it.url in urls }

    fun reset() {
        attachments.value = emptyList()
        linkedImages.value = emptySet()
    }

    companion object {
        // Most links measure in under a second; a post never waits longer than this for one.
        const val LINKED_IMAGE_WAIT_MS = 3_000L
    }
}
