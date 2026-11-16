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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

/**
 * Magnifier (#4) capture path — see `amethyst/plans/2026-06-25-embed-text-selection-native-parity.md`.
 *
 * Host-side `PixelCopy` of the sandboxed surface returns `ERROR_SOURCE_NO_DATA` (the WebView pixels live in a
 * child SurfaceControl the host never draws into), so the selection loupe's content must be captured INSIDE
 * the keyless `:napplet` provider — where the WebView is a real in-window view with real pixels — and shipped
 * back over the Messenger channel. A controller whose surface can produce such captures implements this;
 * [EmbeddedTabLayer] discovers it by `as?` cast, mirroring how it treats [EmbeddedImeBridge].
 */
interface EmbeddedMagnifierProbe {
    /** A captured loupe frame arrived from the provider. */
    var onMagnifierFrame: ((MagnifierFrame) -> Unit)?

    /**
     * Ask the provider for a zoomed slice of the live page centered on ([surfaceX], [surfaceY]) in surface px
     * (which equal the remote WebView's view px — the SCVH surface is 1:1 with the SandboxedSdkView).
     * [boxWidthPx]×[boxHeightPx] is the source rectangle; the returned image is that, scaled by [zoom].
     */
    fun requestMagnifier(
        surfaceX: Float,
        surfaceY: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
        zoom: Float,
    )
}

/**
 * A loupe frame returned by the provider: a PNG ([bytes]) of [width]×[height], plus timing — [captureMs] is
 * the provider-side draw+encode cost and [requestStampNanos] echoes the client's send stamp so out-of-order
 * frames can be dropped.
 */
data class MagnifierFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val captureMs: Double,
    val requestStampNanos: Long,
) {
    override fun equals(other: Any?) = other is MagnifierFrame && this === other

    override fun hashCode() = System.identityHashCode(this)
}
