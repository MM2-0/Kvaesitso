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
package com.vitorpamplona.amethyst.ui.note.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.UrlCachedPreviewer
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.urlPreview
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodeControl
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodePlayhead
import com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.VideoViewInner
import com.vitorpamplona.amethyst.service.playback.diskCache.VideoPrefetcher
import com.vitorpamplona.amethyst.ui.note.creators.location.DEFAULT_MAP_ZOOM
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioWaveformPlayer
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import kotlinx.collections.immutable.ImmutableList
import okio.Path
import com.vitorpamplona.amethyst.commons.ui.components.LoadUrlPreview as AppLoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.components.rememberUrlPreviewState as AppRememberUrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitIssueEvent as AppRenderGitIssueEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPatchEvent as AppRenderGitPatchEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPullRequestEvent as AppRenderGitPullRequestEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPullRequestUpdateEvent as AppRenderGitPullRequestUpdateEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitRepositoryEvent as AppRenderGitRepositoryEvent
import com.vitorpamplona.amethyst.service.playback.composable.VideoView as AppVideoView
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordAudioBox as AppRecordAudioBox
import com.vitorpamplona.amethyst.ui.actions.uploads.RecordVoiceButton as AppRecordVoiceButton
import com.vitorpamplona.amethyst.ui.actions.uploads.VoiceMessagePreview as AppVoiceMessagePreview
import com.vitorpamplona.amethyst.ui.components.GifVideoView as AppGifVideoView
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView as AppZoomableContentView
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog as AppZoomableImageDialog
import com.vitorpamplona.amethyst.ui.components.pdf.PdfViewerDialog as AppPdfViewerDialog
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName as AppLoadCityName
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationPreviewMap as AppLocationPreviewMap
import com.vitorpamplona.amethyst.ui.note.types.EpisodeAudioPlayer as AppEpisodeAudioPlayer
import com.vitorpamplona.amethyst.ui.note.types.MusicTrackAudioPlayer as AppMusicTrackAudioPlayer
import com.vitorpamplona.amethyst.ui.note.types.NoteMediaView as AppNoteMediaView
import com.vitorpamplona.amethyst.ui.note.types.PodcastEpisodeAudioPlayer as AppPodcastEpisodeAudioPlayer
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioWithWaveform as AppRenderAudioWithWaveform

/** Android's [NotePlatform]: the app's own media, map, audio, chess, git, napplet and zap composables. */
object AndroidNotePlatform : NotePlatform {
    override fun warmUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
    ) {
        if (UrlCachedPreviewer.cache.get(url) == null) accountViewModel.urlPreview(url) {}
    }

    /**
     * Every video card here prepares its own player as soon as it is composed, which is only about
     * one card before it is on screen. The videos further down get their first bytes cached ahead,
     * unless videos load only on a tap.
     */
    override fun prefetchVideos(
        owner: Any,
        urls: List<String>,
        accountViewModel: AccountViewModel,
    ) {
        val videos =
            if (accountViewModel.settings.startVideoPlayback()) {
                urls.map { VideoPrefetcher.Video(it, useProxy = (accountViewModel.httpClientBuilder.proxyPortForVideo(it) ?: 0) > 0) }
            } else {
                emptyList()
            }
        Amethyst.instance.videoPrefetcher.prefetch(owner, videos)
    }

    override fun loadUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
        onResult: suspend (UrlPreviewState) -> Unit,
    ) = accountViewModel.urlPreview(url, onResult)

    @Composable
    override fun rememberUrlPreviewState(
        url: String,
        accountViewModel: AccountViewModel,
    ): UrlPreviewState = AppRememberUrlPreviewState(url, accountViewModel)

    @Composable
    override fun ZoomableContentView(
        content: BaseMediaContent,
        images: ImmutableList<BaseMediaContent>,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
    ) = AppZoomableContentView(
        content = content,
        images = images,
        roundedCorner = roundedCorner,
        contentScale = contentScale,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun PdfViewerDialog(
        content: MediaUrlPdf,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) = AppPdfViewerDialog(content, accountViewModel, onDismiss)

    @Composable
    override fun ZoomableImageDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        source: ZoomSourceAnchor?,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) = AppZoomableImageDialog(
        imageUrl = imageUrl,
        allImages = allImages,
        source = source,
        onDismiss = onDismiss,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun GifVideoView(
        videoUri: String,
        contentDescription: String?,
        dimensions: DimensionTag?,
        blurhash: String?,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        onDialog: (() -> Unit)?,
        accountViewModel: AccountViewModel,
        thumbhash: String?,
    ) = AppGifVideoView(
        videoUri = videoUri,
        contentDescription = contentDescription,
        dimensions = dimensions,
        blurhash = blurhash,
        roundedCorner = roundedCorner,
        contentScale = contentScale,
        onDialog = onDialog,
        accountViewModel = accountViewModel,
        thumbhash = thumbhash,
    )

    // Coil's animated decoder plays GIFs through the plain image loader on Android.
    @Composable
    override fun AnimatedImage(
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        onError: () -> Unit,
    ) = AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        onError = { onError() },
    )

    @Composable
    override fun VideoView(
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
    ) = AppVideoView(
        videoUri = videoUri,
        mimeType = mimeType,
        title = title,
        artworkUri = artworkUri,
        authorName = authorName,
        roundedCorner = roundedCorner,
        contentScale = contentScale,
        nostrUriCallback = nostrUriCallback,
        isLiveStream = isLiveStream,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun UrlPreview(
        url: String,
        urlText: String,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav?,
    ) = AppLoadUrlPreview(
        url = url,
        urlText = urlText,
        callbackUri = callbackUri,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun LocationPreviewMap(
        latitude: Double,
        longitude: Double,
        aspectRatio: Float,
        pinColor: Color?,
        pinEmoji: String?,
        pinAlpha: Float,
        modifier: Modifier,
        zoom: Double?,
    ) = AppLocationPreviewMap(
        latitude = latitude,
        longitude = longitude,
        modifier = modifier,
        zoom = zoom ?: DEFAULT_MAP_ZOOM,
        aspectRatio = aspectRatio,
        pinColor = pinColor,
        pinEmoji = pinEmoji,
        pinAlpha = pinAlpha,
    )

    @Composable
    override fun LoadCityName(
        geohashStr: String,
        onLoading: (@Composable () -> Unit)?,
        content: @Composable (String) -> Unit,
    ) = AppLoadCityName(
        geohashStr = geohashStr,
        onLoading = onLoading,
        content = content,
    )

    @Composable
    override fun NoteMediaView(
        mediaUri: String,
        isAudio: Boolean?,
        title: String?,
        coverUri: String?,
        waveform: WaveformData?,
        contentScale: ContentScale,
        note: Note,
        accountViewModel: AccountViewModel,
    ) = AppNoteMediaView(
        mediaUri = mediaUri,
        title = title,
        coverUri = coverUri,
        waveform = waveform,
        contentScale = contentScale,
        note = note,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun MusicTrackAudioPlayer(
        audioUrl: String,
        mimeType: String?,
        title: String?,
        note: Note,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) = AppMusicTrackAudioPlayer(
        audioUrl = audioUrl,
        mimeType = mimeType,
        title = title,
        note = note,
        borderModifier = borderModifier,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun PodcastEpisodeAudioPlayer(
        audio: PodcastAudio,
        note: Note,
        title: String?,
        image: String?,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) = AppPodcastEpisodeAudioPlayer(
        audio = audio,
        note = note,
        title = title,
        image = image,
        borderModifier = borderModifier,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun EpisodeAudioPlayer(
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
    ) = AppEpisodeAudioPlayer(audioUrl, mimeType, title, image, note, startAtSeconds, speed, control, onPlayhead, accountViewModel)

    @Composable
    override fun RenderAudioPlayer(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        accountViewModel: AccountViewModel,
    ) = RenderAudioWaveformPlayer(
        mediaUrl = mediaUrl,
        title = title,
        mimeType = mimeType,
        waveform = waveform,
        authorName = null,
        callbackUri = null,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun RenderAudioWithWaveform(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderAudioWithWaveform(
        mediaUrl = mediaUrl,
        title = title,
        mimeType = mimeType,
        waveform = waveform,
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitRepositoryEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitRepositoryEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitPatchEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitPatchEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitIssueEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitIssueEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitPullRequestEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitPullRequestEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitPullRequestUpdateEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitPullRequestUpdateEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun FullscreenVideoView(
        videoUri: String,
        mimeType: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        controllerVisible: MutableState<Boolean>,
        accountViewModel: AccountViewModel,
    ) = VideoViewInner(
        videoUri = videoUri,
        mimeType = mimeType,
        contentScale = contentScale,
        borderModifier = modifier,
        automaticallyStartPlayback = true,
        controllerVisible = controllerVisible,
        isFullscreen = true,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun RecordVoiceButton(
        onVoiceTaken: (RecordingResult) -> Unit,
        maxDurationSeconds: Int?,
    ) = AppRecordVoiceButton(onVoiceTaken, maxDurationSeconds)

    override val canRecordAudio: Boolean get() = true

    @Composable
    override fun RecordAudioBox(
        modifier: Modifier,
        onRecordTaken: (RecordingResult) -> Unit,
        maxDurationSeconds: Int?,
        content: @Composable (isRecording: Boolean, elapsedSeconds: Int, onStop: () -> Unit) -> Unit,
    ) = AppRecordAudioBox(modifier, onRecordTaken, maxDurationSeconds, content)

    @Composable
    override fun VoiceMessagePreview(
        voiceMetadata: AudioMeta,
        localFile: Path?,
        onRemove: () -> Unit,
        onReRecord: ((RecordingResult) -> Unit)?,
        isUploading: Boolean,
        modifier: Modifier,
    ) = AppVoiceMessagePreview(voiceMetadata, localFile, onRemove, onReRecord, isUploading, modifier)
}
