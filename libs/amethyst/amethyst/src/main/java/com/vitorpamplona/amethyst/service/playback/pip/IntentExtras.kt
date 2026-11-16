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

import android.graphics.Rect
import android.os.Bundle
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData
import com.vitorpamplona.quartz.utils.ensure

class IntentExtras {
    companion object {
        fun loadBounds(intent: Bundle?): Rect? {
            ensure(intent != null) { return null }

            val left = intent.getInt("boundLeft")
            val right = intent.getInt("boundRight")
            val top = intent.getInt("boundTop")
            val bottom = intent.getInt("boundBottom")

            return if (left > 0 && right > 0 && top > 0 && bottom > 0) {
                Rect(left, top, right, bottom)
            } else {
                null
            }
        }

        fun loadBundle(intent: Bundle?): MediaItemData? {
            ensure(intent != null) { return null }
            val uri = intent.getString("videoUri") ?: return null

            val ratio = intent.getFloat("aspectRatio")
            val port = intent.getInt("proxyPort")

            return MediaItemData(
                videoUri = uri,
                authorName = intent.getString("authorName"),
                title = intent.getString("title"),
                artworkUri = intent.getString("artworkUri"),
                callbackUri = intent.getString("callbackUri"),
                mimeType = intent.getString("mimeType"),
                aspectRatio = if (ratio > 0) ratio else null,
                proxyPort = if (port > 0) port else null,
                keepPlaying = intent.getBoolean("keepPlaying", true),
                waveformData = intent.getFloatArray("wavefrontData")?.toList()?.let { WaveformData(it) },
                isLiveStream = intent.getBoolean("isLiveStream", false),
                hash = intent.getString("hash"),
                isAudio = intent.getBoolean("isAudio", false),
            )
        }

        fun createBundle(
            data: MediaItemData,
            bounds: Rect?,
        ): Bundle =
            Bundle().apply {
                putString("videoUri", data.videoUri)
                data.authorName?.let { putString("authorName", it) }
                data.title?.let { putString("title", it) }
                data.artworkUri?.let { putString("artworkUri", it) }
                data.callbackUri?.let { putString("callbackUri", it) }
                data.mimeType?.let { putString("mimeType", it) }
                data.aspectRatio?.let { putFloat("aspectRatio", it) }
                data.proxyPort?.let { putInt("proxyPort", it) }
                putBoolean("keepPlaying", data.keepPlaying)
                data.waveformData?.let { putFloatArray("wavefrontData", it.wave.toFloatArray()) }
                putBoolean("isLiveStream", data.isLiveStream)
                data.hash?.let { putString("hash", it) }
                putBoolean("isAudio", data.isAudio)

                bounds?.let { putInt("boundLeft", it.left) }
                bounds?.let { putInt("boundRight", it.right) }
                bounds?.let { putInt("boundTop", it.top) }
                bounds?.let { putInt("boundBottom", it.bottom) }
            }
    }
}
