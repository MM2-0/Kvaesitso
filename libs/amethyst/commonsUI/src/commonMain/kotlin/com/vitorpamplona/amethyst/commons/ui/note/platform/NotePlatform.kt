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
package com.vitorpamplona.amethyst.commons.ui.note.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import kotlinx.collections.immutable.ImmutableList
import okio.Path

/**
 * The platform halves of the shared note renderer: what a note card needs that only the front end
 * can draw or do. Media players and the zoomable viewer, maps and reverse geocoding, the note types
 * built on a platform engine (audio players, chess, the git browser, meeting rooms, napplets), the
 * reactions/zap row with its wallet hand-offs, and the post editor.
 *
 * The shared note code calls these through [LocalNotePlatform]; a front end installs its own at the
 * composition root (Android does in its theme). Every member defaults to drawing nothing, so a
 * preview, or a front end still wiring a piece, gets an empty slot rather than a crash.
 */
@Stable
interface NotePlatform {
    // Media

    @Composable
    fun ZoomableContentView(
        content: BaseMediaContent,
        images: ImmutableList<BaseMediaContent>,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
    ) {}

    @Composable
    fun ZoomableImageDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        source: ZoomSourceAnchor?,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) {}

    /**
     * The full-screen PDF reader, opened from a card that shows its own cover instead of the
     * inline first-page preview [ZoomableContentView] draws. With no reader, opens the file in
     * the system's handler.
     */
    @Composable
    fun PdfViewerDialog(
        content: MediaUrlPdf,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) {
        val uriHandler = LocalUriHandler.current
        LaunchedEffect(content.url) {
            // The handler throws when no app takes the link; the dialog closes either way.
            runCatching { uriHandler.openUri(content.url) }
            onDismiss()
        }
    }

    @Composable
    fun GifVideoView(
        videoUri: String,
        contentDescription: String?,
        dimensions: DimensionTag?,
        blurhash: String?,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        onDialog: (() -> Unit)?,
        accountViewModel: AccountViewModel,
        thumbhash: String?,
    ) {}

    /**
     * An animated image (a GIF) drawn the way a still is: filling [modifier], looping, silent, with
     * no player controls. [onError] runs when it cannot be drawn, so the caller can move to a
     * fallback URL.
     */
    @Composable
    fun AnimatedImage(
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        onError: () -> Unit,
    ) {}

    @Composable
    fun VideoView(
        videoUri: String,
        mimeType: String?,
        title: String?,
        artworkUri: String?,
        authorName: String?,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        nostrUriCallback: String?,
        isLiveStream: Boolean,
        accountViewModel: AccountViewModel,
    ) {}

    /**
     * A video filling a full-screen viewer, playing at once. [controllerVisible] shows and hides
     * its playback controls, so the viewer can toggle them with its own chrome.
     */
    @Composable
    fun FullscreenVideoView(
        videoUri: String,
        mimeType: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        controllerVisible: MutableState<Boolean>,
        accountViewModel: AccountViewModel,
    ) {}

    /** The link card for [url]: OpenGraph preview, inline media, or a plain link. */
    @Composable
    fun UrlPreview(
        url: String,
        urlText: String,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav?,
    ) {}

    /**
     * Fetches the OpenGraph preview of [url] off the main thread and hands it to [onResult]; for
     * forms that pre-fill from a link rather than render a card. Nothing where the platform has no fetcher.
     */
    fun loadUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
        onResult: suspend (UrlPreviewState) -> Unit,
    ) {}

    /** The OpenGraph preview of [url], starting from the platform's cache. */
    @Composable
    fun rememberUrlPreviewState(
        url: String,
        accountViewModel: AccountViewModel,
    ): UrlPreviewState = UrlPreviewState.Loading

    /**
     * Starts loading the OpenGraph preview of [url] into the platform's cache ahead of display,
     * unless it is already there. Not composable: feed prefetchers call it from a coroutine.
     */
    fun warmUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
    ) {}

    /**
     * The videos a feed is about to show, most wanted first: on screen or just below it. A platform
     * whose player only opens a video once it is the one playing can open these ahead, so they start
     * at once. [owner] is the feed asking (each keeps its own list); an empty list releases its
     * videos. Not composable: feed prefetchers call it from a coroutine. Android needs none of this:
     * every video card on its feed already prepares a player, and [prefetchVideos] covers what comes next.
     */
    fun warmVideos(
        owner: Any,
        urls: List<String>,
        accountViewModel: AccountViewModel,
    ) {}

    /**
     * The videos below the viewport that no card shows yet, nearest first, for a platform whose
     * players open a video as soon as its card is composed: it can download their first bytes ahead,
     * so the player reads the start from disk. The card right below the screen is left out: the lazy
     * list has already composed it, so its player is loading it. HLS never comes here, nor do grid
     * feeds, whose cells show a still frame and open no player. [owner] is the feed asking; an empty
     * list withdraws its videos. Not composable: feed prefetchers call it from a coroutine.
     */
    fun prefetchVideos(
        owner: Any,
        urls: List<String>,
        accountViewModel: AccountViewModel,
    ) {}

    // Places

    @Composable
    fun LocationPreviewMap(
        latitude: Double,
        longitude: Double,
        aspectRatio: Float,
        pinColor: Color?,
        pinEmoji: String?,
        pinAlpha: Float,
        modifier: Modifier,
        zoom: Double?,
    ) {}

    /**
     * Resolves [geohashStr] to a place name for [content], showing [onLoading] meanwhile. Without a
     * geocoder, the place name the app already knows, or else the geohash itself.
     */
    @Composable
    fun LoadCityName(
        geohashStr: String,
        onLoading: (@Composable () -> Unit)?,
        content: @Composable (String) -> Unit,
    ) {
        content(LocalAppServices.current.cachedPlaceName(geohashStr) ?: geohashStr)
    }

    // Audio

    /**
     * A note's own media through the full player: a video, or an audio file that plays in the
     * video frame. [coverUri] shows as the thumb until it plays; [waveform] draws the bars of an
     * audio file that has them. The player links back to [note]. [isAudio] is what the event says
     * the media is (true for an audio kind, false for a music video, null when it doesn't say), for
     * a platform that draws audio and video differently and can't tell from the URL.
     */
    @Composable
    fun NoteMediaView(
        mediaUri: String,
        isAudio: Boolean?,
        title: String?,
        coverUri: String?,
        waveform: WaveformData?,
        contentScale: ContentScale,
        note: Note,
        accountViewModel: AccountViewModel,
    ) {}

    /**
     * The inline audio strip of a music track (kind 36787), drawn right under its cover:
     * [audioUrl] plays here, and [borderModifier] shapes the strip so it reads as one unit
     * with the cover above it.
     */
    @Composable
    fun MusicTrackAudioPlayer(
        audioUrl: String,
        mimeType: String?,
        title: String?,
        note: Note,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) {}

    @Composable
    fun PodcastEpisodeAudioPlayer(
        audio: PodcastAudio,
        note: Note,
        title: String?,
        image: String?,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) {}

    /**
     * An episode's audio that remembers where the listener is: [audioUrl] plays from
     * [startAtSeconds] when given, at [speed], and the player reports its playhead to [onPlayhead]
     * about once a second while it is shown. It obeys [control]'s seeks, plays and pauses. The
     * player links back to [note], the episode.
     */
    @Composable
    fun EpisodeAudioPlayer(
        audioUrl: String,
        mimeType: String?,
        title: String?,
        image: String?,
        note: Note,
        startAtSeconds: Long?,
        speed: Float,
        control: EpisodeControl,
        onPlayhead: (EpisodePlayhead) -> Unit,
        accountViewModel: AccountViewModel,
    ) {}

    @Composable
    fun RenderAudioWithWaveform(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /**
     * A playable audio clip that belongs to no [Note], such as a voice message in an MLS group
     * whose messages never enter the cache: [mediaUrl] plays with [waveform]'s bars when given.
     */
    @Composable
    fun RenderAudioPlayer(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        accountViewModel: AccountViewModel,
    ) {}

    // Git

    @Composable
    fun RenderGitRepositoryEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitPatchEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitIssueEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitPullRequestEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitPullRequestUpdateEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    // Reactions, zaps and editing

    /**
     * The microphone button: records a voice message (up to [maxDurationSeconds]) and hands the
     * finished recording to [onVoiceTaken].
     */
    @Composable
    fun RecordVoiceButton(
        onVoiceTaken: (RecordingResult) -> Unit,
        maxDurationSeconds: Int?,
    ) {}

    /** Whether [RecordAudioBox] can record here; callers offer a text alternative when it cannot. */
    val canRecordAudio: Boolean get() = false

    /**
     * Records a voice message behind a caller-drawn trigger: [content] gets whether it is
     * recording, the elapsed seconds and a stop action, and the finished recording goes to
     * [onRecordTaken]. Platforms without a recorder draw nothing, so no dead button shows.
     */
    @Composable
    fun RecordAudioBox(
        modifier: Modifier,
        onRecordTaken: (RecordingResult) -> Unit,
        maxDurationSeconds: Int?,
        content: @Composable (isRecording: Boolean, elapsedSeconds: Int, onStop: () -> Unit) -> Unit,
    ) {}

    /**
     * Plays back a voice message before it is sent: [localFile] while it is only on the device,
     * else [voiceMetadata]'s URL. [onReRecord] replaces it with a new recording.
     */
    @Composable
    fun VoiceMessagePreview(
        voiceMetadata: AudioMeta,
        localFile: Path?,
        onRemove: () -> Unit,
        onReRecord: ((RecordingResult) -> Unit)?,
        isUploading: Boolean,
        modifier: Modifier,
    ) {}

    /** Draws nothing: previews, and front ends still wiring their pieces. */
    object None : NotePlatform
}

/** The front end's [NotePlatform]. Static: it is set once at the root and never changes. */
val LocalNotePlatform = staticCompositionLocalOf<NotePlatform> { NotePlatform.None }
