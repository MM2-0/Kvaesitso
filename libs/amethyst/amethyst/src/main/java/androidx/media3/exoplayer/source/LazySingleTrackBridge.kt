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
package androidx.media3.exoplayer.source

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi

/**
 * Reaches media3's package-private [ProgressiveMediaSource.Factory.enableLazyLoadingWithSingleTrack],
 * which is how [DefaultMediaSourceFactory] side-loads a subtitle file: the source reports its one
 * track up front and fetches nothing until that track is selected. Without it, a progressive
 * source must open the file to discover its tracks, so every caption would be downloaded before
 * the video could start, and an unreachable one would fail preparation of the whole merged item.
 *
 * Lives in media3's package only for that access. Amethyst's HLS path builds its own sources
 * (see CustomMediaSourceFactory) and so has to rebuild the subtitle wrap DefaultMediaSourceFactory
 * would otherwise apply.
 */
@UnstableApi
internal fun ProgressiveMediaSource.Factory.lazilyLoadingSingleTrack(
    trackId: Int,
    format: Format,
): ProgressiveMediaSource.Factory = enableLazyLoadingWithSingleTrack(trackId, format)
