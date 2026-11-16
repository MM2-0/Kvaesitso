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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.utils

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.service.upload.MediaUploadTracker
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMediaProcessing
import kotlinx.collections.immutable.ImmutableList

@Stable
class ChatFileUploadState(
    val defaultServer: ServerName,
    defaultStripMetadata: Boolean = true,
) {
    val mediaUploadTracker = MediaUploadTracker()
    val isUploadingImage: Boolean get() = mediaUploadTracker.isUploadingImage
    val isUploadingFile: Boolean get() = mediaUploadTracker.isUploadingFile

    var selectedServer by mutableStateOf(defaultServer)

    /**
     * A server this chat must upload to, e.g. a Buzz workspace's own media server, whose relay
     * refuses any other image host. While set, the picker is hidden and the choice is not saved
     * as the user's default.
     */
    var lockedServer by mutableStateOf<ServerName?>(null)
        private set

    fun lockServer(server: ServerName?) {
        lockedServer = server
        selectedServer = server ?: defaultServer
    }

    var caption by mutableStateOf("")

    var contentWarning by mutableStateOf(false)
        private set

    var contentWarningReason by mutableStateOf<String?>(null)

    // Images and Videos
    var multiOrchestrator by mutableStateOf<MultiOrchestrator?>(null)

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by mutableIntStateOf(CompressorQuality.defaultSliderPosition)

    var stripMetadata by mutableStateOf(defaultStripMetadata)
    var encryptFiles by mutableStateOf(true)

    fun load(uris: ImmutableList<SelectedMedia>) {
        reset()
        this.multiOrchestrator = MultiOrchestrator(uris)
    }

    fun isImage(
        url: String,
        mimeType: String?,
    ): Boolean = mimeType?.startsWith("image/") == true || RichTextParser.isImageUrl(url)

    fun reset() {
        multiOrchestrator = null
        mediaUploadTracker.finishUpload()
        caption = ""
        selectedServer = lockedServer ?: defaultServer
        encryptFiles = true
    }

    fun deleteMediaToUpload(selected: SelectedMediaProcessing) {
        val orchestrator = multiOrchestrator ?: return
        orchestrator.remove(selected)
        // An empty orchestrator still renders the gallery, which reads its first item.
        if (orchestrator.size() == 0) multiOrchestrator = null
    }

    /**
     * Deleting the last picked item leaves an *empty* orchestrator, not a null one, so
     * a non-null check alone kept Send live with nothing to send. Callers that loop
     * over the items then post nothing; one that indexes item 0 crashes.
     */
    fun canPost(): Boolean = !mediaUploadTracker.isUploading && (multiOrchestrator?.size() ?: 0) > 0

    fun hasPickedMedia() = multiOrchestrator != null

    fun updateContentWarning(value: Boolean) {
        contentWarning = value
        if (value) {
            contentWarningReason = ""
        } else {
            contentWarningReason = null
        }
    }
}
