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
package com.vitorpamplona.amethyst.service.playback.playerPool.aspectRatio

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.vitorpamplona.amethyst.commons.model.MutableMediaAspectRatioCache
import kotlin.math.roundToInt

class AspectRatioCacher(
    val cache: MutableMediaAspectRatioCache,
) : Player.Listener {
    var currentUrl: String? = null

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        currentUrl = mediaItem?.mediaId
    }

    // width/height are the decoded frame, not the shape it is shown at: an anamorphic video
    // (stored 1080x720 with 4:9 pixels, displayed 480x720) needs the pixel ratio applied, or its
    // box comes out landscape and ContentFrame, which does apply it, crops the portrait picture.
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        currentUrl?.let {
            val displayWidth = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt()
            cache.addMeasured(it, displayWidth, videoSize.height)
        }
    }
}
