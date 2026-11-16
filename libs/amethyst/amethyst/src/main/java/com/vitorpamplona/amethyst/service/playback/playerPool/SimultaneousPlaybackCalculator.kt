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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.getSystemService
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

class SimultaneousPlaybackCalculator {
    companion object {
        fun isLowMemory(context: Context): Boolean {
            val activityManager: ActivityManager? = context.getSystemService()
            return activityManager?.isLowRamDevice == true
        }

        /** The device's total RAM, or 0 when the system won't say. */
        fun totalRamBytes(context: Context): Long {
            val activityManager: ActivityManager = context.getSystemService() ?: return 0L
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            return info.totalMem
        }

        @OptIn(UnstableApi::class)
        fun max(appContext: Context): Int {
            val maxInstances =
                try {
                    val info = MediaCodecUtil.getDecoderInfo(MimeTypes.VIDEO_H264, false, false)
                    if (info != null && info.maxSupportedInstances > 0) {
                        info.maxSupportedInstances
                    } else {
                        0
                    }
                } catch (_: MediaCodecUtil.DecoderQueryException) {
                    0
                }

            if (maxInstances > 0) {
                return maxInstances
            }

            return if (isLowMemory(appContext)) {
                5
            } else {
                10
            }
        }
    }
}
