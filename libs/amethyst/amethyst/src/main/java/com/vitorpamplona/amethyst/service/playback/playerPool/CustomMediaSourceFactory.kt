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

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.lazilyLoadingSingleTrack
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import androidx.media3.extractor.text.SubtitleParser
import com.vitorpamplona.amethyst.service.playback.PLAYBACK_DIAG_TAG
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemCache
import com.vitorpamplona.amethyst.service.playback.diskCache.HlsLivenessCache
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoCache
import com.vitorpamplona.quartz.utils.Log

/**
 * Whether an HLS item should bypass the disk cache. Pure so the routing is unit-testable.
 *
 * - **Flagged live** (kind:30311 live activity) → always bypass.
 * - **Progressive** (mp4, …) → never bypass; cache normally.
 * - **HLS learned on-demand** → cache. A VOD playlist has `#EXT-X-ENDLIST`, so it is static and its
 *   segments are immutable — safe and worthwhile to cache.
 * - **HLS live or not-yet-classified** → bypass. Caching a live playlist makes ExoPlayer reload a
 *   stale, non-advancing playlist and throw `PlaylistStuckException`, after which playback loops on
 *   the frozen window. Until we have positively learned a URL is on-demand (see [HlsLivenessCache]),
 *   the safe default is to bypass.
 */
internal fun shouldBypassCache(
    isFlaggedLive: Boolean,
    isHls: Boolean,
    isKnownOnDemand: Boolean,
): Boolean =
    when {
        isFlaggedLive -> true
        !isHls -> false
        isKnownOnDemand -> false
        else -> true
    }

/**
 * Whether a [MediaItem] is HLS, via media3's own content-type inference.
 *
 * This is the one "is this HLS?" predicate for playback routing. [CustomMediaSourceFactory] uses it
 * to pick both the cache route and the media-source factory, and [HlsLivenessRecorder] uses it to
 * decide which items are worth learning a liveness verdict for. Those two **must** agree: if the
 * recorder tested more narrowly than the factory, an item the factory treats as HLS would never be
 * classified, [HlsLivenessCache] would answer `isKnownOnDemand = false` for it forever, and
 * [shouldBypassCache] would keep it out of the disk cache permanently.
 *
 * It reads back the mimeType [MediaItemCache] already normalised, which is what makes it correct for
 * BUD-10 blossom URIs — `https://host/<sha256>`, no extension, mime as the only signal. A `.m3u8`
 * substring test on the URL misses those, and separately gives a false positive on a query string
 * over progressive media (`video.mp4?ref=a.m3u8`).
 */
internal fun isHlsMediaItem(mediaItem: MediaItem?): Boolean {
    val config = mediaItem?.localConfiguration ?: return false
    return Util.inferContentTypeForUriAndMimeType(config.uri, config.mimeType) == C.CONTENT_TYPE_HLS
}

/**
 * Decides whether a [MediaItem] plays through the caching data source or bypasses it.
 *
 * The hard constraint is that a **live** HLS stream must never be cached — caching its mutating
 * playlist makes ExoPlayer reload a stale, non-advancing manifest and throw `PlaylistStuckException`
 * (surfaced as `ERROR_CODE_IO_UNSPECIFIED`), after which playback loops replaying the frozen window.
 * But we also *want* to cache immutable on-demand HLS (multi-rendition NIP-71 VOD).
 *
 * A `.m3u8` URL cannot distinguish the two, and the `isLiveStream` metadata flag only marks
 * kind:30311 live activities, so a live `.m3u8` shared in a plain kind:1 note arrives flagged
 * non-live. We therefore learn liveness from ExoPlayer itself: [HlsLivenessRecorder] records the
 * playlist's live/on-demand verdict into [HlsLivenessCache] once loaded, and the *next* play of that
 * URL routes by it. The first (unclassified) play bypasses — the safe default — so a live stream is
 * never cached even once; only a proven on-demand URL is cached, from its second view onward.
 *
 * The `isLiveStream` flag is carried on [MediaItem.mediaMetadata] extras and set by
 * [MediaItemCache] from [com.vitorpamplona.amethyst.service.playback.composable.mediaitem.MediaItemData].
 */
@UnstableApi
class CustomMediaSourceFactory(
    videoCache: VideoCache,
    dataSourceFactory: DataSource.Factory,
) : MediaSource.Factory {
    private val cachingDataSource: DataSource.Factory = videoCache.get(dataSourceFactory)
    private val plainDataSource: DataSource.Factory = dataSourceFactory

    private val cachingFactory: MediaSource.Factory =
        DefaultMediaSourceFactory(cachingDataSource)
    private val nonCachingFactory: MediaSource.Factory =
        DefaultMediaSourceFactory(dataSourceFactory)

    // Stateless, so one instance serves both HLS factories.
    private val playlistParserFactory = LowLatencyStrippingHlsPlaylistParserFactory()

    // The same parser set DefaultMediaSourceFactory side-loads subtitles with (WebVTT, SubRip,
    // TTML, SSA, ...). Stateless; each track gets its own parser from create().
    private val subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()

    // HLS is built explicitly rather than through DefaultMediaSourceFactory, which exposes no hook
    // for a playlist parser factory. See LowLatencyStrippingHlsPlaylistParserFactory for why we need
    // one. Everything else still routes through the Default factories above.
    //
    // The cost of bypassing it: HLS items skip what DefaultMediaSourceFactory wraps around the
    // source — side-loaded subtitleConfigurations (MergingMediaSource), clipping, ad insertion, and
    // live target-offset defaults. Subtitles ARE reachable now (MediaItemCache side-loads NIP-71
    // `text-track` captions), so createMediaSource below mirrors that wrap for the HLS path;
    // clipping and ad insertion remain unused, and the live setters aren't on the
    // MediaSource.Factory interface. Anything else added later must be mirrored here too.
    private val cachingHlsFactory: MediaSource.Factory = hlsFactory(cachingDataSource)
    private val nonCachingHlsFactory: MediaSource.Factory = hlsFactory(dataSourceFactory)

    private fun hlsFactory(dataSource: DataSource.Factory): MediaSource.Factory =
        HlsMediaSource
            .Factory(dataSource)
            .setPlaylistParserFactory(playlistParserFactory)

    private val allFactories = listOf(cachingFactory, nonCachingFactory, cachingHlsFactory, nonCachingHlsFactory)

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        allFactories.forEach { it.setDrmSessionManagerProvider(drmSessionManagerProvider) }
        return this
    }

    // Retained as well as forwarded: the subtitle sources in withSideLoadedSubtitles are built per
    // call rather than living in allFactories, so this is the only way they can be given the same
    // retry/backoff behaviour the player configured for everything else.
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        this.loadErrorHandlingPolicy = loadErrorHandlingPolicy
        allFactories.forEach { it.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }
        return this
    }

    override fun getSupportedTypes(): IntArray = nonCachingFactory.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val id = mediaItem.mediaId
        val flaggedLive = isFlaggedLive(mediaItem)

        // One predicate governs both the cache routing below and which factory builds the source, so
        // the two can never disagree. See isHlsMediaItem.
        val hls = isHlsMediaItem(mediaItem)

        val knownOnDemand = HlsLivenessCache.isKnownOnDemand(id)
        val bypassCache = shouldBypassCache(flaggedLive, hls, knownOnDemand)

        val factory =
            if (hls) {
                if (bypassCache) nonCachingHlsFactory else cachingHlsFactory
            } else {
                if (bypassCache) nonCachingFactory else cachingFactory
            }
        // DefaultMediaSourceFactory is what merges side-loaded subtitle tracks into the playback
        // source. The explicit HLS factory above does not, so on that path a caption track would
        // load into nothing; mirror the wrap here. Progressive items already got it from the
        // Default factory, so they must NOT be wrapped again.
        val source =
            if (hls) {
                withSideLoadedSubtitles(factory.createMediaSource(mediaItem), mediaItem, bypassCache)
            } else {
                factory.createMediaSource(mediaItem)
            }

        // Logs the routing inputs directly rather than a re-derived label, so it can't drift from
        // shouldBypassCache.
        Log.d(PLAYBACK_DIAG_TAG) {
            "SOURCE ${if (bypassCache) "BYPASS" else "CACHE"} flaggedLive=$flaggedLive hls=$hls knownOnDemand=$knownOnDemand " +
                "mime=${mediaItem.localConfiguration?.mimeType} -> ${source::class.java.simpleName} id=$id"
        }
        return source
    }

    /**
     * Rebuilds what [DefaultMediaSourceFactory] does for `subtitleConfigurations`: each side-loaded
     * track becomes its own source, merged alongside the video. Like media3's default, the file is
     * parsed into cues while it loads ([SubtitleExtractor]) — the player's TextRenderer only takes
     * cues, its legacy in-renderer decoding of raw `text/vtt` samples is off — and a track whose
     * format no parser understands is left out rather than offered as one that cannot play.
     *
     * Subtitle files are small, immutable documents, so they follow the video's cache decision —
     * a track fetched beside a cached VOD playlist is worth keeping too, and one beside a live
     * stream is not worth a cache entry for a stream that is never replayed.
     */
    private fun withSideLoadedSubtitles(
        source: MediaSource,
        mediaItem: MediaItem,
        bypassCache: Boolean,
    ): MediaSource {
        val subtitles = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
        if (subtitles.isEmpty()) return source

        val dataSource = if (bypassCache) plainDataSource else cachingDataSource
        val subtitleSources = subtitles.mapNotNull { subtitleSource(it, dataSource) }
        if (subtitleSources.isEmpty()) return source

        return MergingMediaSource(source, *subtitleSources.toTypedArray())
    }

    private fun subtitleSource(
        subtitle: MediaItem.SubtitleConfiguration,
        dataSource: DataSource.Factory,
    ): MediaSource? {
        val format =
            Format
                .Builder()
                .setSampleMimeType(subtitle.mimeType)
                .setLanguage(subtitle.language)
                .setSelectionFlags(subtitle.selectionFlags)
                .setRoleFlags(subtitle.roleFlags)
                .setLabel(subtitle.label)
                .setId(subtitle.id)
                .build()
        if (!subtitleParserFactory.supportsFormat(format)) return null

        // What the extractor will emit, declared up front so nothing is fetched before selection.
        val cuesFormat =
            format
                .buildUpon()
                .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
                .setCodecs(format.sampleMimeType)
                .setCueReplacementBehavior(subtitleParserFactory.getCueReplacementBehavior(format))
                .build()
        val extractors = ExtractorsFactory { arrayOf(SubtitleExtractor(subtitleParserFactory.create(format), format)) }

        return ProgressiveMediaSource
            .Factory(dataSource, extractors)
            .lazilyLoadingSingleTrack(0, cuesFormat)
            .apply { loadErrorHandlingPolicy?.let { setLoadErrorHandlingPolicy(it) } }
            .createMediaSource(MediaItem.fromUri(subtitle.uri))
    }

    // Only the explicit event-kind flag (kind:30311 live activities). Returns false when the flag
    // is absent or false; the HLS URL check in createMediaSource covers everything else.
    private fun isFlaggedLive(mediaItem: MediaItem): Boolean {
        val extras = mediaItem.mediaMetadata.extras
        return extras != null &&
            extras.containsKey(MediaItemCache.EXTRA_IS_LIVE_STREAM) &&
            extras.getBoolean(MediaItemCache.EXTRA_IS_LIVE_STREAM, false)
    }
}
