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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.Manifest
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.record_a_message_description
import com.vitorpamplona.amethyst.commons.ui.components.ToggleableBox
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordAudioBox(
    modifier: Modifier,
    onRecordTaken: (RecordingResult) -> Unit,
    maxDurationSeconds: Int? = null,
    content: @Composable (Boolean, Int, () -> Unit) -> Unit,
) {
    val mediaRecorder = remember { mutableStateOf<VoiceMessageRecorder?>(null) }
    val context = LocalContext.current
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var pendingPermissionStart by remember { mutableStateOf(false) }

    // Must be called at Composable scope, not in callback
    val recordPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val scope = rememberCoroutineScope()

    val isRecording = mediaRecorder.value != null

    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder.value?.stop()
            mediaRecorder.value = null
        }
    }

    // Read in composition: both helpers run from onClick, where neither
    // string accessor can be called.
    val recordDescription = stringRes(Res.string.record_a_message_description)

    fun startRecording() {
        if (mediaRecorder.value == null) {
            elapsedSeconds = 0
            mediaRecorder.value = VoiceMessageRecorder()
            mediaRecorder.value?.start(context, scope)
        }
    }

    fun stopRecording() {
        val recorder = mediaRecorder.value ?: return
        val result = recorder.stop()
        mediaRecorder.value = null
        if (result != null) {
            onRecordTaken(result)
        } else {
            Toast
                .makeText(
                    context,
                    recordDescription,
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    // Start recording after permission is granted
    LaunchedEffect(recordPermissionState.status.isGranted) {
        if (recordPermissionState.status.isGranted && pendingPermissionStart) {
            pendingPermissionStart = false
            startRecording()
        }
    }

    // Track elapsed time while recording
    LaunchedEffect(mediaRecorder.value) {
        // Capture the current recorder state to avoid repeated reads of volatile state
        val currentRecorder = mediaRecorder.value
        if (currentRecorder != null) {
            // Loop while coroutine is active - LaunchedEffect will cancel when mediaRecorder.value changes
            while (isActive) {
                delay(1000)
                elapsedSeconds++
                if (maxDurationSeconds != null && elapsedSeconds >= maxDurationSeconds) {
                    stopRecording()
                    break
                }
            }
        } else {
            // Reset elapsed time when not recording
            elapsedSeconds = 0
        }
    }

    ToggleableBox(
        modifier = modifier,
        isActive = isRecording,
        onClick = {
            if (isRecording) {
                stopRecording()
            } else {
                if (!recordPermissionState.status.isGranted) {
                    pendingPermissionStart = true
                    recordPermissionState.launchPermissionRequest()
                } else {
                    startRecording()
                }
            }
        },
        content = { active ->
            content(active, elapsedSeconds) {
                stopRecording()
            }
        },
    )
}
