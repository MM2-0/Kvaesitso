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
package com.vitorpamplona.amethyst.commons.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.Path

class VoiceAnonymizationController(
    private val scope: CoroutineScope,
    private val logTag: String,
    private val onError: (Throwable) -> Unit,
    /** Re-voices a recording with a preset; the platform's audio pipeline, reached through the host. */
    private val anonymize: suspend (Path, VoicePreset) -> Result<AnonymizedResult>,
) {
    var selectedPreset: VoicePreset by mutableStateOf(VoicePreset.NONE)
        private set
    var processingPreset: VoicePreset? by mutableStateOf(null)
        private set
    var distortedFiles: Map<VoicePreset, AnonymizedResult> by mutableStateOf(emptyMap())
        private set

    private var processingJob: Job? = null

    fun activeFile(originalFile: Path?): Path? =
        if (selectedPreset == VoicePreset.NONE) {
            originalFile
        } else {
            distortedFiles[selectedPreset]?.file
        }

    fun activeWaveform(originalWaveform: List<Float>?): List<Float>? =
        if (selectedPreset == VoicePreset.NONE) {
            originalWaveform
        } else {
            distortedFiles[selectedPreset]?.waveform
        }

    fun selectPreset(
        preset: VoicePreset,
        originalFile: Path?,
    ) {
        Log.d(logTag) { "selectPreset called with: ${preset.name}, pitchFactor: ${preset.pitchFactor}" }
        if (processingPreset != null || preset == selectedPreset) return

        if (preset == VoicePreset.NONE) {
            selectedPreset = preset
            return
        }

        if (distortedFiles.containsKey(preset)) {
            selectedPreset = preset
            return
        }

        val file = originalFile ?: return

        processingJob?.cancel()
        processingPreset = preset
        processingJob =
            scope.launch {
                try {
                    val result = anonymize(file, preset)

                    result
                        .onSuccess { anonymizedResult ->
                            distortedFiles = distortedFiles + (preset to anonymizedResult)
                            selectedPreset = preset
                        }.onFailure { error ->
                            Log.w(logTag, "Failed to anonymize voice", error)
                            onError(error)
                        }
                } finally {
                    processingPreset = null
                    processingJob = null
                }
            }
    }

    fun clear() {
        cancelProcessing()
        deleteDistortedFiles()
        selectedPreset = VoicePreset.NONE
    }

    fun deleteDistortedFiles() {
        distortedFiles.values.forEach { result ->
            try {
                platformFileSystem.delete(result.file, mustExist = false)
                Log.d(logTag) { "Deleted distorted file: ${result.file}" }
            } catch (e: Exception) {
                Log.w(logTag, "Failed to delete distorted file: ${result.file}", e)
            }
        }
        distortedFiles = emptyMap()
    }

    private fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        processingPreset = null
    }
}
