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

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Low-Latency HLS tags that we remove before media3's playlist parser sees them.
 *
 * `EXT-X-PART` and `EXT-X-PRELOAD-HINT` are the ones that matter: they are the only things in these
 * playlists that produce a **byte-range-bounded** chunk, which is what triggers the crash documented
 * on [LowLatencyStrippingHlsPlaylistParserFactory]. `EXT-X-PART-INF` and `EXT-X-SERVER-CONTROL` go
 * with them — leaving those behind advertises a low-latency contract (PART-TARGET, PART-HOLD-BACK,
 * CAN-BLOCK-RELOAD) that the stripped playlist can no longer honour.
 *
 * Deliberately **not** stripped:
 * - `EXT-X-SKIP` — marks a delta playlist whose segments were legitimately omitted. Removing the tag
 *   while the segments stay missing would corrupt the playlist. Dropping `EXT-X-SERVER-CONTROL`
 *   already stops media3 requesting deltas (`_HLS_skip=YES`), so this should never appear anyway.
 * - `EXT-X-RENDITION-REPORT` — inert once the parts are gone.
 */
private val LOW_LATENCY_TAGS =
    listOf(
        "#EXT-X-PART:",
        "#EXT-X-PART-INF:",
        "#EXT-X-PRELOAD-HINT:",
        "#EXT-X-SERVER-CONTROL:",
    )

/**
 * Removes the Low-Latency HLS tags from a playlist, leaving every other byte untouched.
 *
 * Line separators are preserved exactly: the split/join round-trips `\n`, keeps the `\r` of a CRLF
 * playlist as trailing content, and keeps a trailing newline (which `split` surfaces as a final
 * empty element). Blank lines are never dropped.
 */
internal fun stripLowLatencyTags(playlist: String): String {
    // Cheap pre-check: the overwhelming majority of playlists carry no LL tags at all, and this
    // runs on every playlist reload of every live stream.
    if (LOW_LATENCY_TAGS.none { playlist.contains(it) }) return playlist

    return playlist
        .split("\n")
        .filterNot { line ->
            val trimmed = line.trimStart()
            LOW_LATENCY_TAGS.any { trimmed.startsWith(it) }
        }.joinToString("\n")
}

/**
 * The byte-level form: decode as UTF-8, strip, re-encode.
 *
 * Split out from [LowLatencyStrippingParser] so the charset round-trip is reachable from a plain JVM
 * unit test — `parse` takes an `android.net.Uri`, which stubs to null under
 * `unitTests.isReturnDefaultValues`, so nothing that goes through it is testable without Robolectric.
 *
 * A UTF-8 BOM survives: decoding leaves U+FEFF in the string, `trimStart` does not treat it as
 * whitespace, and re-encoding reproduces the same three bytes. When there is nothing to strip the
 * *original array* is returned, so the common path neither re-encodes nor copies.
 */
internal fun stripLowLatencyTags(playlist: ByteArray): ByteArray {
    val original = playlist.toString(Charsets.UTF_8)
    val stripped = stripLowLatencyTags(original)
    return if (stripped === original) playlist else stripped.toByteArray(Charsets.UTF_8)
}

/**
 * Wraps a media3 playlist parser and strips the Low-Latency tags before delegating.
 *
 * Playlists are a few KB, so reading the stream fully into memory is cheaper than the alternative of
 * a streaming line filter and keeps the transform a pure, testable [stripLowLatencyTags] call.
 */
@UnstableApi
internal class LowLatencyStrippingParser(
    private val delegate: ParsingLoadable.Parser<HlsPlaylist>,
) : ParsingLoadable.Parser<HlsPlaylist> {
    override fun parse(
        uri: Uri,
        inputStream: InputStream,
    ): HlsPlaylist = delegate.parse(uri, ByteArrayInputStream(stripLowLatencyTags(inputStream.readBytes())))
}

/**
 * Serves media3 a de-low-latency-ed view of every HLS playlist.
 *
 * ## Why
 *
 * media3 crashes fatally on a Low-Latency HLS playlist whose parts are byte ranges — which is what
 * zap-stream-core emits (`#EXT-X-PART:URI="…",DURATION=…,BYTERANGE="359712@0"`). Reproduced on
 * media3 1.10.1, Pixel 9a / Android 17, ~0.6s after `ExoPlayerImpl.Init`:
 *
 * ```
 * IllegalArgumentException
 *   at androidx.media3.datasource.DataSpec.<init>          // checkArgument(length > 0 || length == LENGTH_UNSET)
 *   at androidx.media3.datasource.DataSpec.subrange
 *   at androidx.media3.exoplayer.hls.HlsMediaChunk.feedDataToExtractor
 *   at androidx.media3.exoplayer.hls.HlsMediaChunk.loadMedia
 * ```
 *
 * `HlsMediaChunk.feedDataToExtractor` re-enters as `dataSpec.subrange(nextLoadPosition)`. Once the
 * whole bounded range has been fed to the extractor, `nextLoadPosition == length`, so `subrange`
 * asks for a zero-length `DataSpec` and the constructor's `length > 0` precondition throws. The
 * early-return guard in `subrange` only covers `offset == 0`, so a fully-consumed chunk falls
 * straight through. Unbounded chunks are safe — `length == C.LENGTH_UNSET` short-circuits — so this
 * is reachable only via a byte-range part.
 *
 * The failure is unrecoverable rather than merely retried: `Loader` wraps it as
 * `UnexpectedLoaderException`, which `DefaultLoadErrorHandlingPolicy` lists as non-retriable, so it
 * becomes a fatal `ExoPlaybackException: Source error`. Forcing a retry would not help either — the
 * `HlsMediaChunk` instance keeps its `nextLoadPosition`, so it would throw identically forever.
 *
 * Still present verbatim in media3 1.11.0-rc01, so there is no version to upgrade to.
 *
 * **Tracking: https://github.com/androidx/media/issues/3350** — delete this whole file and its test
 * once that is fixed and we are on a media3 release carrying the fix, then drop the explicit
 * `HlsMediaSource.Factory` in [CustomMediaSourceFactory] and let `DefaultMediaSourceFactory` build
 * HLS again. That also restores low latency, and removes the caveat about the wrapping
 * `DefaultMediaSourceFactory` features documented there.
 *
 * ## Trade-off
 *
 * We lose low latency on LL-HLS streams: playback falls back to whole segments, roughly one
 * `TARGETDURATION` further behind the live edge. LL playlists still list their complete segments
 * below the part tags, so they play normally otherwise. Given the alternative is a hard failure
 * within a second, and that media3 offers no per-stream way to decline just the parts, disabling it
 * globally is the conservative trade.
 */
@UnstableApi
internal class LowLatencyStrippingHlsPlaylistParserFactory(
    private val delegate: HlsPlaylistParserFactory = DefaultHlsPlaylistParserFactory(),
) : HlsPlaylistParserFactory {
    override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> = LowLatencyStrippingParser(delegate.createPlaylistParser())

    override fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist,
        previousMediaPlaylist: HlsMediaPlaylist?,
    ): ParsingLoadable.Parser<HlsPlaylist> = LowLatencyStrippingParser(delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist))
}
