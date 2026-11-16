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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davotoula.lightcompressor.VideoCodec
import com.davotoula.lightcompressor.hls.HlsLadder
import com.davotoula.lightcompressor.utils.CompressorUtils
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.service.upload.HlsPublishState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Compose-facing ViewModel for the "Share HD Video" screen. Holds the form state and a single
 * [HlsPublishOrchestrator] that runs the transcode → upload → publish pipeline. The orchestrator
 * receives closures that capture the account/context so the VM only needs a [load] call from the
 * screen to wire everything together.
 *
 * This class is intentionally thin — all orchestration logic and state-machine tests live in
 * [HlsPublishOrchestrator].
 */
@Stable
open class NewHlsVideoViewModel : ViewModel() {
    var account: Account? = null
        private set
    var pickedUri by mutableStateOf<Uri?>(null)
        private set
    var sourceMetadata by mutableStateOf<HlsSourceMetadata?>(null)
        private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var sensitiveContent by mutableStateOf(false)
    var contentWarningReason by mutableStateOf("")
    var useH265 by mutableStateOf(true)
    var selectedServer by mutableStateOf<ServerName?>(null)

    var selectedRenditionLabels by mutableStateOf(
        HlsLadder
            .default()
            .renditions
            .map { it.resolution.label }
            .toSet(),
    )

    private val _state = MutableStateFlow<HlsPublishState>(HlsPublishState.Idle)
    val state: StateFlow<HlsPublishState> = _state.asStateFlow()

    private val _availableServers = MutableStateFlow<List<ServerName>>(DEFAULT_MEDIA_SERVERS)
    val availableServers: StateFlow<List<ServerName>> = _availableServers.asStateFlow()

    private var orchestrator: HlsPublishOrchestrator? = null
    private var currentJob: Job? = null
    private var serversJob: Job? = null

    fun load(
        account: Account,
        orchestrator: HlsPublishOrchestrator,
    ) {
        this.account = account
        this.orchestrator = orchestrator

        val initialServers = account.blossomServers.hostNameFlow.value
        _availableServers.value = initialServers

        if (selectedServer == null || initialServers.none { it == selectedServer }) {
            selectedServer = account.settings.defaultFileServer
                .takeIf { s -> initialServers.any { it == s } }
                ?: initialServers.firstOrNull()
                ?: DEFAULT_MEDIA_SERVERS.first()
        }

        serversJob?.cancel()
        serversJob =
            viewModelScope.launch {
                account.blossomServers.hostNameFlow.collect { servers ->
                    _availableServers.value = servers
                    if (selectedServer == null || servers.none { it == selectedServer }) {
                        selectedServer = servers.firstOrNull() ?: DEFAULT_MEDIA_SERVERS.first()
                    }
                }
            }
    }

    fun load(
        account: Account,
        context: Context,
    ) = load(
        account,
        createProductionHlsPublishOrchestrator(
            state = _state,
            account = account,
            context = context,
            uriProvider = { pickedUri },
        ),
    )

    fun onVideoPicked(
        uri: Uri,
        metadata: HlsSourceMetadata?,
    ) {
        pickedUri = uri
        sourceMetadata = metadata
    }

    fun clearPickedVideo() {
        pickedUri = null
        sourceMetadata = null
    }

    fun publish(context: Context) {
        val orch = orchestrator ?: return
        val server = selectedServer ?: return
        if (pickedUri == null) return
        if (title.isBlank()) return
        if (selectedRenditionLabels.isEmpty()) return

        val codec = effectiveCodec(useH265)
        val ladder =
            HlsLadder(
                HlsLadder.default().renditions.filter { it.resolution.label in selectedRenditionLabels },
            )
        val request =
            HlsPublishRequest(
                title = title,
                description = description,
                sensitiveContent = sensitiveContent,
                contentWarningReason = contentWarningReason,
                codec = codec,
                server = server,
                ladder = ladder,
                durationSeconds = sourceMetadata?.durationSeconds,
            )

        currentJob =
            viewModelScope.launch(Dispatchers.IO) {
                orch.publish(request)
            }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        orchestrator?.reset()
    }

    fun reset() {
        orchestrator?.reset()
    }

    override fun onCleared() {
        currentJob?.cancel()
    }

    private fun effectiveCodec(wantH265: Boolean): VideoCodec =
        if (wantH265 && CompressorUtils.isHevcEncodingSupported()) {
            VideoCodec.H265
        } else {
            VideoCodec.H264
        }
}

data class HlsSourceMetadata(
    val width: Int,
    val height: Int,
    val durationSeconds: Int,
    val sizeBytes: Long,
)
