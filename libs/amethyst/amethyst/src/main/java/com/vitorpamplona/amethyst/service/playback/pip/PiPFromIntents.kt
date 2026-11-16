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
package com.vitorpamplona.amethyst.service.playback.pip

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.ui.components.getActivity
import kotlinx.coroutines.launch

@Composable
fun rememberVideoDataFromIntents(): MutableState<MediaItemData?> {
    val activity = LocalContext.current.getActivity()

    val videoData =
        remember {
            mutableStateOf(activity.processIntentForPiP(activity.intent))
        }

    val scope = rememberCoroutineScope()

    DisposableEffect(activity) {
        val consumer =
            Consumer<Intent> { intent ->
                scope.launch {
                    videoData.value = activity.processIntentForPiP(intent)
                }
            }

        activity.addOnNewIntentListener(consumer)
        onDispose {
            activity.removeOnNewIntentListener(consumer)
        }
    }

    return videoData
}

fun Activity.processIntentForPiP(intent: Intent): MediaItemData? {
    val videoDataOnCreation = IntentExtras.loadBundle(intent.extras)
    val bounds = IntentExtras.loadBounds(intent.extras)
    val ratio = videoDataOnCreation?.let { MediaAspectRatioCache.videoRatio(it.videoUri, it.aspectRatio) }
    val isPlaying = true
    val isMuted = DEFAULT_MUTED_SETTING.value

    if (!isInPictureInPictureMode) {
        enterPictureInPictureMode(makePipParams(isPlaying, isMuted, ratio, bounds))
    } else {
        setPictureInPictureParams(makePipParams(isPlaying, isMuted, ratio, bounds))
    }

    return videoDataOnCreation
}
