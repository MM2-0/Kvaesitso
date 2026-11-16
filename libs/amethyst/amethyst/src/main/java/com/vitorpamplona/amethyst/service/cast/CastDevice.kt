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
package com.vitorpamplona.amethyst.service.cast

import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource

@Immutable
data class CastDevice(
    val id: String,
    val name: String,
)

@Immutable
data class CastRequest(
    val url: String,
    val mimeType: String? = null,
    val title: String? = null,
    val artworkUri: String? = null,
    /**
     * Whether the receiver should treat this as an endless live stream rather than a seekable
     * recording. Resolve it with [resolveCastLiveness] — never from the URL, which cannot tell a
     * live `.m3u8` from an on-demand one.
     */
    val isLive: Boolean = false,
    /**
     * What the video is — "H.265 (HEVC) 1920x1080" — taken from the local player, which has already
     * decoded it. Reported when a cast fails, because the receiver itself rarely says why. See
     * [summarizeCastFormat].
     */
    val formatSummary: String? = null,
)

/**
 * Decides what to tell the Cast receiver about a stream's liveness.
 *
 * [learned] is ExoPlayer's verdict for this URL (see HlsLivenessCache), recorded once it has parsed
 * the playlist — the only signal that actually distinguishes a live `.m3u8` from an on-demand one.
 * It wins whenever we have it. [metadataFlag] is the kind:30311 live-activity flag, which is right
 * when set but absent for a live stream shared in a plain kind:1 note, so it is only the fallback.
 *
 * Defaulting to non-live when we know nothing keeps the previous behaviour for the common case
 * (progressive MP4), where a live claim would cost the receiver its seek bar and duration.
 */
fun resolveCastLiveness(
    learned: Boolean?,
    metadataFlag: Boolean,
): Boolean = learned ?: metadataFlag

// Critical for HLS: sending an `.m3u8` URL with `video/mp4` makes the default
// Cast receiver try to demux a playlist as MP4 and crash, wiping the TV's Cast
// service from the network in the process. Strip query/fragment first so
// signed URLs like `…/stream.m3u8?token=…` still match.
fun CastRequest.effectiveMimeType(): String {
    mimeType?.takeIf { it.isNotBlank() }?.let { return it }
    val path = url.substringBefore('?').substringBefore('#')
    return when {
        path.endsWith(".m3u8", ignoreCase = true) -> "application/vnd.apple.mpegurl"
        path.endsWith(".mpd", ignoreCase = true) -> "application/dash+xml"
        path.endsWith(".webm", ignoreCase = true) -> "video/webm"
        path.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
        else -> "video/mp4"
    }
}

sealed class CastSessionState {
    object Idle : CastSessionState()

    data class Connecting(
        val device: CastDevice,
    ) : CastSessionState()

    data class Casting(
        val device: CastDevice,
        val request: CastRequest,
    ) : CastSessionState()

    data class Error(
        val device: CastDevice?,
        val message: CastErrorMessage,
    ) : CastSessionState()
}

/**
 * What went wrong, in a form the picker resolves in composition. The caster reports failures from
 * Cast SDK callbacks, where there is no blocking way to read a Compose resource, so it hands over
 * the resource and the UI formats it.
 *
 * [text] names the receiver as `%1$s`: the picker supplies the [CastSessionState.Error] device's
 * name, or a generic noun when the failure happens before or after we know which device it was.
 * A `_detail` resource also takes [detail] — what the video was — as `%2$s`.
 */
@Immutable
data class CastErrorMessage(
    val text: StringResource,
    val detail: String? = null,
)
