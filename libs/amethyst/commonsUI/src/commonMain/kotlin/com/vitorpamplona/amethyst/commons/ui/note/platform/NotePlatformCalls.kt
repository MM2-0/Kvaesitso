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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.cartridges.CartDetector
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.ui.cartridges.CartPlayOverlay
import com.vitorpamplona.amethyst.commons.ui.cartridges.LocalMediaIsCart
import com.vitorpamplona.amethyst.commons.ui.cartridges.rememberConfirmedCartUrl
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import okio.Path

// Call-site shims for [NotePlatform]: the same names and defaults the app's own composables have,
// so shared note code reads as it did and only its imports point here.

/**
 * The platform's media view, with a Play button on top when the image is a game cartridge.
 *
 * Encrypted chat attachments (NIP-17 files, Marmot and Cordn media) qualify too: their chat view
 * puts the key in the account's `EncryptionKeyCache`, and the image HTTP client the player
 * downloads with decrypts in flight, so the cart arrives as plaintext with its plaintext hash. A
 * `blossom:` URI works too: the cache finds the key again by the blob's sha256 once the URI
 * resolves to a server URL.
 */
@Composable
fun ZoomableContentView(
    content: BaseMediaContent,
    images: ImmutableList<BaseMediaContent> = remember(content) { persistentListOf(content) },
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    val platform = LocalNotePlatform.current
    if (content is MediaUrlImage && CartDetector.mightBeCart(content.url, content.mimeType, content.dim)) {
        val cartUrl = rememberConfirmedCartUrl(content, accountViewModel)
        // Tells a held-back placeholder to leave the middle to the Play button.
        CompositionLocalProvider(LocalMediaIsCart provides (cartUrl != null)) {
            Box {
                platform.ZoomableContentView(content, images, roundedCorner, contentScale, accountViewModel)
                if (cartUrl != null) CartPlayOverlay(content, cartUrl, accountViewModel, Modifier.matchParentSize())
            }
        }
    } else {
        platform.ZoomableContentView(content, images, roundedCorner, contentScale, accountViewModel)
    }
}

@Composable
fun PdfViewerDialog(
    content: MediaUrlPdf,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.PdfViewerDialog(content, onDismiss, accountViewModel)

@Composable
fun ZoomableImageDialog(
    imageUrl: BaseMediaContent,
    allImages: ImmutableList<BaseMediaContent> = remember(imageUrl) { persistentListOf(imageUrl) },
    source: ZoomSourceAnchor? = null,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.ZoomableImageDialog(
    imageUrl = imageUrl,
    allImages = allImages,
    source = source,
    onDismiss = onDismiss,
    accountViewModel = accountViewModel,
)

@Composable
fun AnimatedImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier,
    onError: () -> Unit,
) = LocalNotePlatform.current.AnimatedImage(
    url = url,
    contentDescription = contentDescription,
    contentScale = contentScale,
    modifier = modifier,
    onError = onError,
)

@Composable
fun GifVideoView(
    videoUri: String,
    contentDescription: String?,
    dimensions: DimensionTag?,
    blurhash: String?,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    onDialog: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
    thumbhash: String? = null,
) = LocalNotePlatform.current.GifVideoView(
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

@Composable
fun FullscreenVideoView(
    videoUri: String,
    mimeType: String?,
    contentScale: ContentScale,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) = LocalNotePlatform.current.FullscreenVideoView(videoUri, mimeType, contentScale, modifier, controllerVisible, accountViewModel)

@Composable
fun VideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    nostrUriCallback: String? = null,
    isLiveStream: Boolean = false,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.VideoView(
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
fun LoadUrlPreview(
    url: String,
    urlText: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) = LocalNotePlatform.current.UrlPreview(
    url = url,
    urlText = urlText,
    callbackUri = callbackUri,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun LocationPreviewMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double? = null,
    aspectRatio: Float = 1f,
    pinColor: Color? = null,
    pinEmoji: String? = null,
    pinAlpha: Float = 1f,
) = LocalNotePlatform.current.LocationPreviewMap(
    latitude = latitude,
    longitude = longitude,
    aspectRatio = aspectRatio,
    pinColor = pinColor,
    pinEmoji = pinEmoji,
    pinAlpha = pinAlpha,
    modifier = modifier,
    zoom = zoom,
)

@Composable
fun LoadCityName(
    geohashStr: String,
    onLoading: (@Composable () -> Unit)? = null,
    content: @Composable (String) -> Unit,
) = LocalNotePlatform.current.LoadCityName(
    geohashStr = geohashStr,
    onLoading = onLoading,
    content = content,
)

@Composable
fun MusicTrackAudioPlayer(
    audioUrl: String,
    mimeType: String?,
    title: String?,
    note: Note,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.MusicTrackAudioPlayer(
    audioUrl = audioUrl,
    mimeType = mimeType,
    title = title,
    note = note,
    borderModifier = borderModifier,
    accountViewModel = accountViewModel,
)

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
) = LocalNotePlatform.current.NoteMediaView(
    mediaUri = mediaUri,
    isAudio = isAudio,
    title = title,
    coverUri = coverUri,
    waveform = waveform,
    contentScale = contentScale,
    note = note,
    accountViewModel = accountViewModel,
)

@Composable
fun PodcastEpisodeAudioPlayer(
    audio: PodcastAudio,
    note: Note,
    title: String?,
    image: String?,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.PodcastEpisodeAudioPlayer(
    audio = audio,
    note = note,
    title = title,
    image = image,
    borderModifier = borderModifier,
    accountViewModel = accountViewModel,
)

@Composable
fun RenderAudioPlayer(
    mediaUrl: String,
    title: String?,
    mimeType: String?,
    waveform: WaveformData?,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.RenderAudioPlayer(mediaUrl, title, mimeType, waveform, accountViewModel)

@Composable
fun RenderAudioWithWaveform(
    mediaUrl: String,
    title: String?,
    mimeType: String?,
    waveform: WaveformData?,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderAudioWithWaveform(
    mediaUrl = mediaUrl,
    title = title,
    mimeType = mimeType,
    waveform = waveform,
    note = note,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderGitRepositoryEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitRepositoryEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

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
) = LocalNotePlatform.current.RenderGitPatchEvent(
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
fun RenderGitIssueEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitIssueEvent(
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
fun RenderGitPullRequestEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitPullRequestEvent(
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
fun RenderGitPullRequestUpdateEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitPullRequestUpdateEvent(
    baseNote = baseNote,
    makeItShort = makeItShort,
    canPreview = canPreview,
    quotesLeft = quotesLeft,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

/** The longest voice message the recorder allows, in seconds. */
const val MAX_VOICE_RECORD_SECONDS = 600

@Composable
fun RecordVoiceButton(
    onVoiceTaken: (RecordingResult) -> Unit,
    maxDurationSeconds: Int? = null,
) = LocalNotePlatform.current.RecordVoiceButton(onVoiceTaken, maxDurationSeconds)

@Composable
fun RecordAudioBox(
    modifier: Modifier = Modifier,
    onRecordTaken: (RecordingResult) -> Unit,
    maxDurationSeconds: Int? = null,
    content: @Composable (isRecording: Boolean, elapsedSeconds: Int, onStop: () -> Unit) -> Unit,
) = LocalNotePlatform.current.RecordAudioBox(modifier, onRecordTaken, maxDurationSeconds, content)

@Composable
fun VoiceMessagePreview(
    voiceMetadata: AudioMeta,
    localFile: Path? = null,
    onRemove: () -> Unit,
    onReRecord: ((RecordingResult) -> Unit)? = null,
    isUploading: Boolean = false,
    modifier: Modifier = Modifier,
) = LocalNotePlatform.current.VoiceMessagePreview(voiceMetadata, localFile, onRemove, onReRecord, isUploading, modifier)

@Composable
fun rememberUrlPreviewState(
    url: String,
    accountViewModel: AccountViewModel,
): UrlPreviewState = LocalNotePlatform.current.rememberUrlPreviewState(url, accountViewModel)
