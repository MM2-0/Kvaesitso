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
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.size.Size
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.audio.MediaTracks
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.playableLayout
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_media_to_gallery
import com.vitorpamplona.amethyst.commons.resources.copy_the_note_id_to_the_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_url_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.download_to_phone
import com.vitorpamplona.amethyst.commons.resources.downloading_video_for_sharing
import com.vitorpamplona.amethyst.commons.resources.hash_verification_failed
import com.vitorpamplona.amethyst.commons.resources.hash_verification_info_title
import com.vitorpamplona.amethyst.commons.resources.hash_verification_passed
import com.vitorpamplona.amethyst.commons.resources.media_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.media_added
import com.vitorpamplona.amethyst.commons.resources.media_added_to_profile_gallery
import com.vitorpamplona.amethyst.commons.resources.media_download_has_started_toast
import com.vitorpamplona.amethyst.commons.resources.original
import com.vitorpamplona.amethyst.commons.resources.share_image
import com.vitorpamplona.amethyst.commons.resources.share_video
import com.vitorpamplona.amethyst.commons.resources.unable_to_share_image
import com.vitorpamplona.amethyst.commons.resources.unable_to_share_video
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaPreloadedContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedMediaUrl
import com.vitorpamplona.amethyst.commons.richtext.localJavaFile
import com.vitorpamplona.amethyst.commons.service.image.BlossomFetcher
import com.vitorpamplona.amethyst.commons.ui.cartridges.CartSizeLabel
import com.vitorpamplona.amethyst.commons.ui.cartridges.LocalMediaIsCart
import com.vitorpamplona.amethyst.commons.ui.components.BlurhashBackdrop
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.ContentWarningGate
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.DisplayBlurHash
import com.vitorpamplona.amethyst.commons.ui.components.DisplayUrlWithLoadingSymbol
import com.vitorpamplona.amethyst.commons.ui.components.IconWithSizeLabel
import com.vitorpamplona.amethyst.commons.ui.components.ImageUrlWithDownloadButton
import com.vitorpamplona.amethyst.commons.ui.components.InformationDialog
import com.vitorpamplona.amethyst.commons.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.components.MediaDownloadCard
import com.vitorpamplona.amethyst.commons.ui.components.MediaDownloadRow
import com.vitorpamplona.amethyst.commons.ui.components.MediaKind
import com.vitorpamplona.amethyst.commons.ui.components.ReportCaptureLoading
import com.vitorpamplona.amethyst.commons.ui.components.WaitAndDisplay
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.components.pdf.PdfPreviewCard
import com.vitorpamplona.amethyst.commons.ui.components.rememberMediaSize
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.note.BlankNote
import com.vitorpamplona.amethyst.commons.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.commons.ui.note.types.rememberMediaTracks
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ImageCornerRadius
import com.vitorpamplona.amethyst.commons.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size6dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size75dp
import com.vitorpamplona.amethyst.commons.ui.theme.hashVerifierMark
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.ui.components.pdf.PdfViewerDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.saveMediaToGallery
import com.vitorpamplona.crypto.sha2.sha256StreamWithCount
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.sink
import java.io.File
import java.io.IOException

// Delay before cleaning up shared video temp files.
// Allows time for receiving app to copy the file after user confirms share.
private const val SHARED_VIDEO_CLEANUP_DELAY_MS = 120_000L

// Assumed shape of a video whose dimensions nobody has reported yet — no imeta `dim` and nothing
// cached, which is the norm for a NIP-53 live stream on its first play. Without a ratio the sizing
// modifier leaves height unconstrained, so the player stretches to whatever ceiling encloses it
// (300.dp on the live-stream screen) and letterboxes the real frame inside, leaving black bars top
// and bottom. Guessing the overwhelmingly common video shape puts the first layout in the right
// place; [MediaAspectRatioCache] then corrects anything unusual once the decoder reports its size.
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f

/**
 * Shape to assume for a [MediaUrlVideo] nobody has reported dimensions for — no imeta `dim` and
 * nothing in [MediaAspectRatioCache].
 *
 * Video gets [DEFAULT_VIDEO_ASPECT_RATIO]. Audio gets null, meaning "no ratio, wrap whatever the
 * player asks for": audio has no picture, and the inline player sizes itself as a square (see
 * `AudioPlayerSquare.audioSquare`) so the visualizer and controls get room. A square is taller than
 * 16:9, so caging it in a 16:9 box — which is centre-aligned and unclipped all the way up through
 * NoteComposeLayout — makes the player paint over the note header above it and the reactions row
 * below. Audio also never reaches the cache, since it has no video track to report a size, so the
 * miss is permanent rather than first-play-only.
 */
internal fun unknownMediaAspectRatio(
    mimeType: String?,
    url: String,
): Float? = if (RichTextParser.isAudioContent(mimeType, url)) null else DEFAULT_VIDEO_ASPECT_RATIO

@Composable
fun ZoomableContentView(
    content: BaseMediaContent,
    images: ImmutableList<BaseMediaContent> = remember(content) { persistentListOf(content) },
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    var dialogOpen by remember(content) { mutableStateOf(false) }
    // Keyed by the media, so a viewer opened from a sibling thumbnail can close into this one.
    val source = rememberZoomSourceAnchor(content)
    val boundsTrackingModifier = Modifier.zoomSourceAnchor(source)

    when (content) {
        is MediaUrlImage -> {
            val ratio = content.dim?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(content.url)
            ContentWarningGate(
                isSensitive = content.contentWarning != null,
                reasons = setOfNotNull(content.contentWarning),
                preloadUrls = listOf(content.url),
                accountViewModel = accountViewModel,
                modifier = mediaSizingModifier(ratio, contentScale),
                backdrop = (content.thumbhash ?: content.blurhash)?.let { { BlurhashBackdrop(content.blurhash, content.description, content.thumbhash) } },
            ) {
                if (content.isAnimatedMedia()) {
                    // The anchor lets the viewer grow out of, and shrink back into, the gif.
                    Box(modifier = boundsTrackingModifier) {
                        GifVideoView(
                            videoUri = content.url,
                            contentDescription = content.description,
                            dimensions = content.dim,
                            blurhash = content.blurhash,
                            roundedCorner = roundedCorner,
                            contentScale = contentScale,
                            onDialog = { dialogOpen = true },
                            accountViewModel = accountViewModel,
                            thumbhash = content.thumbhash,
                        )
                    }
                } else {
                    TwoSecondController(content) { controllerVisible ->
                        val mainImageModifier =
                            Modifier
                                .fillMaxWidth()
                                .then(boundsTrackingModifier)
                                .clickable { dialogOpen = true }
                        val loadedImageModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier.fillMaxWidth()
                        UrlImageView(content, contentScale, mainImageModifier, loadedImageModifier, controllerVisible, accountViewModel = accountViewModel)
                    }
                }
            }
        }

        is MediaUrlVideo -> {
            // Audio, or media that may be audio, gets an audio card: B (waveform) or C (cover) when the
            // declaration says audio, A while it can't tell. The player's probe, once in, overrides the
            // declaration — undecided files find their real card, mislabelled videos come back here.
            var probedAudioOnly by remember(content.url) { mutableStateOf(PlayableMediaProbeCache.get(content.url)) }
            // An `.mp4` / `video/mp4` that doesn't say whether it holds video has its track list read
            // before playback: Blossom serves AAC uploads that way, and they belong in the audio card.
            val tracks = if (content.isLiveStream) MediaTracks.UNKNOWN else rememberMediaTracks(content.url, content.mimeType, accountViewModel)
            val audioOnly =
                probedAudioOnly ?: when (tracks) {
                    MediaTracks.AUDIO_ONLY -> true
                    MediaTracks.HAS_VIDEO -> false
                    else -> null
                }
            val layout =
                remember(content.url, content.mimeType, content.artworkUri, content.isLiveStream, audioOnly) {
                    // A live stream is the one stream we know is video-first; it keeps the video player.
                    if (content.isLiveStream) {
                        PlayableLayout.VIDEO
                    } else {
                        playableLayout(content.mimeType, content.url, hasArtwork = content.artworkUri != null, probedAudioOnly = audioOnly)
                    }
                }

            if (layout != PlayableLayout.VIDEO) {
                ContentWarningGate(
                    isSensitive = content.contentWarning != null,
                    reasons = setOfNotNull(content.contentWarning),
                    preloadUrls = emptyList(),
                    accountViewModel = accountViewModel,
                ) {
                    PlayableAudioView(
                        content = content,
                        layout = layout,
                        onProbed = { audioOnly ->
                            PlayableMediaProbeCache.put(content.url, audioOnly)
                            probedAudioOnly = audioOnly
                        },
                        accountViewModel = accountViewModel,
                    )
                }
            } else {
                // The fallback classifies the URL string, so compute it once per content — for audio
                // the cache miss is permanent and this branch re-runs on every recomposition.
                val fallbackRatio =
                    remember(content.url, content.mimeType) {
                        unknownMediaAspectRatio(content.mimeType, content.url)
                    }
                // What the player measured beats the event's `dim`, which can be the stored size of
                // an anamorphic video or ignore rotation.
                val ratio =
                    MediaAspectRatioCache.videoRatio(content.url, content.dim?.aspectRatioOrNull())
                        ?: fallbackRatio
                ContentWarningGate(
                    isSensitive = content.contentWarning != null,
                    reasons = setOfNotNull(content.contentWarning),
                    preloadUrls = emptyList(),
                    accountViewModel = accountViewModel,
                    modifier = mediaSizingModifier(ratio, contentScale),
                    backdrop = (content.thumbhash ?: content.blurhash)?.let { { BlurhashBackdrop(content.blurhash, content.description, content.thumbhash) } },
                ) {
                    Box(
                        // The sizing modifier is repeated here because ContentWarningGate only applies
                        // the one it is handed when the content is actually sensitive — the common
                        // non-sensitive path emits content() bare. Without a height constraint of its
                        // own this box stretches to whatever ceiling encloses it and the player
                        // letterboxes the frame inside, which is what put black bars above and below
                        // live streams (their enclosure is StreamingHeaderModifier's 300.dp cap).
                        modifier = mediaSizingModifier(ratio, contentScale).then(boundsTrackingModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        VideoView(
                            videoUri = content.url,
                            mimeType = content.mimeType,
                            title = content.description,
                            artworkUri = content.artworkUri,
                            authorName = content.authorName,
                            dimensions = content.dim,
                            blurhash = content.blurhash,
                            roundedCorner = roundedCorner,
                            contentScale = contentScale,
                            nostrUriCallback = content.uri,
                            onDialog = { dialogOpen = true },
                            accountViewModel = accountViewModel,
                            thumbhash = content.thumbhash,
                            isLiveStream = content.isLiveStream,
                            hash = content.hash,
                            captions = content.captions,
                            sizeBytes = content.sizeBytes,
                        )
                    }
                }
            }
        }

        is MediaLocalImage -> {
            if (content.isAnimatedMedia()) {
                content.localJavaFile?.let {
                    Box(modifier = boundsTrackingModifier) {
                        GifVideoView(
                            videoUri = it.toUri().toString(),
                            contentDescription = content.description,
                            dimensions = content.dim,
                            blurhash = content.blurhash,
                            roundedCorner = roundedCorner,
                            contentScale = contentScale,
                            onDialog = { dialogOpen = true },
                            accountViewModel = accountViewModel,
                            thumbhash = content.thumbhash,
                        )
                    }
                }
            } else {
                TwoSecondController(content) { controllerVisible ->
                    val mainImageModifier =
                        Modifier
                            .fillMaxWidth()
                            .then(boundsTrackingModifier)
                            .clickable { dialogOpen = true }
                    val loadedImageModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier.fillMaxWidth()

                    LocalImageView(content, contentScale, mainImageModifier, loadedImageModifier, controllerVisible, accountViewModel = accountViewModel)
                }
            }
        }

        is MediaLocalVideo -> {
            content.localJavaFile?.let {
                Box(
                    modifier = Modifier.fillMaxWidth().then(boundsTrackingModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    VideoView(
                        videoUri = it.toUri().toString(),
                        mimeType = content.mimeType,
                        title = content.description,
                        artworkUri = content.artworkUri,
                        authorName = content.authorName,
                        roundedCorner = roundedCorner,
                        contentScale = contentScale,
                        nostrUriCallback = content.uri,
                        onDialog = { dialogOpen = true },
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }

        is MediaUrlPdf -> {
            Box(modifier = Modifier.fillMaxWidth().then(boundsTrackingModifier)) {
                val sharePopupExpanded = remember { mutableStateOf(false) }
                ShareMediaAction(
                    accountViewModel = accountViewModel,
                    popupExpanded = sharePopupExpanded,
                    content = content,
                    onDismiss = { sharePopupExpanded.value = false },
                )
                PdfPreviewCard(
                    content = content,
                    accountViewModel = accountViewModel,
                    onOpen = { dialogOpen = true },
                    onLongPress = { sharePopupExpanded.value = true },
                )
            }
        }
    }

    if (dialogOpen) {
        if (content is MediaUrlPdf) {
            PdfViewerDialog(
                content = content,
                accountViewModel = accountViewModel,
                onDismiss = { dialogOpen = false },
            )
        } else {
            ZoomableImageDialog(
                imageUrl = content,
                allImages = images,
                source = source,
                onDismiss = {
                    dialogOpen = false
                },
                accountViewModel = accountViewModel,
                // The thumbnail's corners (imageModifier clips to QuoteBorder): the grow animation
                // squares them off as it opens instead of snapping them square.
                sourceCornerRadius = if (roundedCorner) ImageCornerRadius else 0.dp,
            )
        }
    }
}

@Composable
fun TwoSecondController(
    content: BaseMediaContent,
    inner: @Composable (controllerVisible: MutableState<Boolean>) -> Unit,
) {
    val controllerVisible = remember(content) { mutableStateOf(false) }

    inner(controllerVisible)
}

@Composable
fun LocalImageView(
    content: MediaLocalImage,
    contentScale: ContentScale,
    mainImageModifier: Modifier,
    loadedImageModifier: Modifier,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    alwayShowImage: Boolean = false,
    fullResolution: Boolean = false,
) {
    if (content.localFileExists()) {
        val capture = LocalMediaCapture.current
        val showImage =
            remember {
                mutableStateOf(
                    alwayShowImage || capture != null || accountViewModel.settings.showImages(),
                )
            }

        val ratio = remember(content) { content.dim?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(content.localJavaFile.toString()) }
        val context = LocalContext.current
        val imageModel =
            if (fullResolution) {
                remember(content.localJavaFile, context) {
                    ImageRequest
                        .Builder(context)
                        .data(content.localJavaFile)
                        .size(Size.ORIGINAL)
                        .build()
                }
            } else {
                content.localJavaFile
            }
        CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center) { imageVisible ->
            if (imageVisible) {
                SubcomposeAsyncImage(
                    model = imageModel,
                    contentDescription = content.description,
                    contentScale = contentScale,
                    modifier = mainImageModifier,
                ) {
                    val state by painter.state.collectAsState()
                    when (state) {
                        is AsyncImagePainter.State.Loading,
                        -> {
                            ReportCaptureLoading()
                            if (content.blurhash != null || content.thumbhash != null) {
                                if (ratio != null) {
                                    DisplayBlurHash(
                                        content.blurhash,
                                        content.description,
                                        contentScale,
                                        loadedImageModifier.aspectRatio(ratio),
                                        thumbhash = content.thumbhash,
                                    )
                                } else {
                                    DisplayBlurHash(
                                        content.blurhash,
                                        content.description,
                                        contentScale,
                                        loadedImageModifier,
                                        thumbhash = content.thumbhash,
                                    )
                                }
                            } else {
                                if (ratio != null) {
                                    Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                                        LoadingAnimation(Size40dp, Size6dp)
                                    }
                                } else {
                                    WaitAndDisplay {
                                        DisplayUrlWithLoadingSymbol(content, accountViewModel.toastManager::toast)
                                    }
                                }
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            BlankNote(loadedImageModifier)
                        }

                        is AsyncImagePainter.State.Success -> {
                            SubcomposeAsyncImageContent(loadedImageModifier)

                            val image = (state as AsyncImagePainter.State.Success).result.image
                            RequestHdrFor(image, fullscreen = fullResolution)

                            SideEffect {
                                MediaAspectRatioCache.add(content.localJavaFile.toString(), image.width, image.height)
                            }

                            content.isVerified?.let {
                                AnimatedVisibility(
                                    visible = controllerVisible.value,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    Box(Modifier.align(Alignment.TopEnd), contentAlignment = Alignment.TopEnd) {
                                        HashVerificationSymbol(it)
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            } else {
                if ((content.blurhash != null || content.thumbhash != null) && ratio != null) {
                    DisplayBlurHash(
                        content.blurhash,
                        content.description,
                        ContentScale.Crop,
                        loadedImageModifier
                            .aspectRatio(ratio)
                            .clickable { showImage.value = true },
                        thumbhash = content.thumbhash,
                    )
                    IconButton(
                        modifier = Modifier.size(Size75dp),
                        onClick = { showImage.value = true },
                    ) {
                        DownloadForOfflineIcon(Size75dp, Color.White)
                    }
                } else {
                    ImageUrlWithDownloadButton(content.uri, showImage)
                }
            }
        }
    } else {
        BlankNote(loadedImageModifier)
    }
}

@Composable
fun UrlImageView(
    content: MediaUrlImage,
    contentScale: ContentScale,
    mainImageModifier: Modifier,
    loadedImageModifier: Modifier,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    alwayShowImage: Boolean = false,
    fullResolution: Boolean = false,
) {
    val ratio = content.dim?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(content.url)

    val capture = LocalMediaCapture.current
    val showImage =
        remember {
            mutableStateOf(
                alwayShowImage || capture != null || accountViewModel.settings.showImages(),
            )
        }

    val context = LocalContext.current
    val imageModel =
        if (fullResolution) {
            remember(content.url, context) {
                ImageRequest
                    .Builder(context)
                    .data(content.url)
                    .size(Size.ORIGINAL)
                    .build()
            }
        } else {
            content.url
        }

    CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center) {
        if (it) {
            SubcomposeAsyncImage(
                model = imageModel,
                contentDescription = content.description,
                contentScale = contentScale,
                modifier = mainImageModifier,
            ) {
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Loading,
                    -> {
                        ReportCaptureLoading()
                        if (content.blurhash != null || content.thumbhash != null) {
                            if (ratio != null) {
                                val modifier =
                                    if (contentScale == ContentScale.Crop) {
                                        loadedImageModifier.clickable { showImage.value = true }
                                    } else {
                                        loadedImageModifier.aspectRatio(ratio).clickable { showImage.value = true }
                                    }

                                DisplayBlurHash(
                                    content.blurhash,
                                    content.description,
                                    ContentScale.Crop,
                                    modifier,
                                    thumbhash = content.thumbhash,
                                )
                            } else {
                                DisplayBlurHash(
                                    content.blurhash,
                                    content.description,
                                    ContentScale.Crop,
                                    loadedImageModifier,
                                    thumbhash = content.thumbhash,
                                )
                            }
                        } else {
                            if (ratio != null) {
                                Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                                    LoadingAnimation(Size40dp, Size6dp)
                                }
                            } else {
                                WaitAndDisplay {
                                    DisplayUrlWithLoadingSymbol(content, accountViewModel.toastManager::toast)
                                }
                            }
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        ClickableUrlOrBlossom(urlText = "${content.url} ", url = content.url)
                    }

                    is AsyncImagePainter.State.Success -> {
                        SubcomposeAsyncImageContent(loadedImageModifier)

                        ShowHashAnimated(content, controllerVisible, Modifier.align(Alignment.TopEnd))

                        val image = (state as AsyncImagePainter.State.Success).result.image
                        RequestHdrFor(image, fullscreen = fullResolution)

                        SideEffect {
                            MediaAspectRatioCache.add(content.url, image.width, image.height)
                        }
                    }

                    else -> {}
                }
            }
        } else {
            // Only a held-back image asks for its size: one that loads never sends the request.
            val size = rememberMediaSize(content, accountViewModel)
            // A game cartridge has its Play button in the middle: only the size goes under it.
            val isCart = LocalMediaIsCart.current
            if ((content.blurhash != null || content.thumbhash != null) && ratio != null) {
                val modifier =
                    if (contentScale == ContentScale.Crop) {
                        loadedImageModifier.clickable { showImage.value = true }
                    } else {
                        loadedImageModifier.aspectRatio(ratio).clickable { showImage.value = true }
                    }

                DisplayBlurHash(
                    content.blurhash,
                    content.description,
                    contentScale,
                    modifier,
                    thumbhash = content.thumbhash,
                )
                if (isCart) {
                    CartSizeLabel(size)
                } else {
                    IconWithSizeLabel(MediaKind.IMAGE, size) {
                        IconButton(
                            modifier = Modifier.size(Size75dp),
                            onClick = { showImage.value = true },
                        ) {
                            DownloadForOfflineIcon(Size75dp, Color.White)
                        }
                    }
                }
            } else if (contentScale == ContentScale.Crop || ratio != null) {
                // In a grid (Crop) the cell sizes the tile, so the card fills it; elsewhere it takes the
                // image's own shape.
                val cardRatio = if (contentScale == ContentScale.Crop) null else ratio
                MediaDownloadCard(content.url, MediaKind.IMAGE, size, cardRatio, loadedImageModifier, underCartPlay = isCart) { showImage.value = true }
            } else {
                MediaDownloadRow(content.url, MediaKind.IMAGE, size, Modifier) { showImage.value = true }
            }
        }
    }
}

@Composable
fun ShowHashAnimated(
    content: MediaUrlImage,
    controllerVisible: MutableState<Boolean>,
    modifier: Modifier,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(modifier, contentAlignment = Alignment.TopEnd) {
            ShowHash(content)
        }
    }
}

@Composable
fun ShowHash(content: MediaUrlContent) {
    var verifiedHash by remember(content.url) { mutableStateOf<Boolean?>(null) }

    if (content.hash != null) {
        LaunchedEffect(key1 = content.url) {
            val newVerifiedHash =
                withContext(Dispatchers.IO) {
                    verifyHash(content)
                }
            if (newVerifiedHash != verifiedHash) {
                verifiedHash = newVerifiedHash
            }
        }
    }

    verifiedHash?.let { HashVerificationSymbol(it) }
}

fun BaseMediaContent.isAnimatedMedia(): Boolean =
    if (this is MediaUrlContent) {
        mimeType == "image/gif" ||
            mimeType == "image/avif" ||
            isAnimatedMediaUrl(url)
    } else if (this is MediaPreloadedContent) {
        mimeType == "image/gif" ||
            mimeType == "image/avif" ||
            localFile?.name?.endsWith(".gif", ignoreCase = true) == true ||
            localFile?.name?.endsWith(".avif", ignoreCase = true) == true
    } else {
        false
    }

@Composable
fun ShareMediaAction(
    accountViewModel: AccountViewModel,
    popupExpanded: MutableState<Boolean>,
    content: BaseMediaContent,
    onDismiss: () -> Unit,
) {
    if (content is MediaUrlContent) {
        ShareMediaAction(
            popupExpanded = popupExpanded,
            videoUri = content.url,
            postNostrUri = content.uri,
            blurhash = content.blurhash,
            dim = content.dim,
            hash = content.hash,
            mimeType = content.mimeType,
            onDismiss = onDismiss,
            content = content,
            accountViewModel = accountViewModel,
        )
    } else if (content is MediaPreloadedContent) {
        ShareMediaAction(
            popupExpanded = popupExpanded,
            videoUri = content.localJavaFile?.toUri().toString(),
            postNostrUri = content.uri,
            blurhash = content.blurhash,
            dim = content.dim,
            hash = null,
            mimeType = content.mimeType,
            onDismiss = onDismiss,
            content = content,
            accountViewModel = accountViewModel,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ShareMediaAction(
    popupExpanded: MutableState<Boolean>,
    videoUri: String?,
    postNostrUri: String?,
    blurhash: String?,
    dim: DimensionTag?,
    hash: String?,
    mimeType: String?,
    onDismiss: () -> Unit,
    content: BaseMediaContent? = null,
    accountViewModel: AccountViewModel,
) {
    val mediaDownloadHasStartedToastStr = stringRes(Res.string.media_download_has_started_toast)
    val downloadingVideoForSharingStr = stringRes(Res.string.downloading_video_for_sharing)
    // Track if video is downloading - hoisted here to block menu dismiss during download
    val isDownloadingVideo = remember { mutableStateOf(false) }

    if (popupExpanded.value) {
        M3ActionDialog(
            title = stringRes(Res.string.media_actions_dialog_title),
            onDismiss = { if (!isDownloadingVideo.value) onDismiss() },
        ) {
            val clipboardManager = LocalClipboard.current
            val scope = rememberCoroutineScope()

            // Copy & Gallery section
            if ((videoUri != null && !videoUri.startsWith("file")) || postNostrUri != null) {
                M3ActionSection {
                    if (videoUri != null && !videoUri.startsWith("file")) {
                        M3ActionRow(icon = MaterialSymbols.Link, text = stringRes(Res.string.copy_url_to_clipboard)) {
                            scope.launch {
                                clipboardManager.setText(videoUri)
                            }
                            onDismiss()
                        }
                    }
                    if (content is MediaUrlPdf && videoUri != null && !videoUri.startsWith("file")) {
                        val appContext = LocalContext.current.applicationContext
                        M3ActionRow(icon = MaterialSymbols.Download, text = stringRes(Res.string.download_to_phone)) {
                            accountViewModel.viewModelScope.launch(Dispatchers.IO) {
                                saveMediaToGallery(content, appContext, accountViewModel)
                            }
                            Toast
                                .makeText(
                                    appContext,
                                    mediaDownloadHasStartedToastStr,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            onDismiss()
                        }
                    }
                    postNostrUri?.let {
                        M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(Res.string.copy_the_note_id_to_the_clipboard)) {
                            scope.launch {
                                clipboardManager.setText(it)
                            }
                            onDismiss()
                        }
                        M3ActionRow(icon = MaterialSymbols.Collections, text = stringRes(Res.string.add_media_to_gallery)) {
                            if (videoUri != null) {
                                val n19 = Nip19Parser.uriToRoute(postNostrUri)?.entity as? NEvent
                                if (n19 != null) {
                                    // Forward the imeta poster so HLS gallery entries have a decodable
                                    // still — the .m3u8 URL itself is a text manifest. Only
                                    // MediaUrlVideo carries an artworkUri.
                                    val posterUrl = (content as? MediaUrlVideo)?.artworkUri
                                    val thumbhashFromContent =
                                        (content as? MediaUrlVideo)?.thumbhash
                                            ?: (content as? MediaUrlImage)?.thumbhash
                                    accountViewModel.addMediaToGallery(n19.hex, videoUri, n19.relay.getOrNull(0), blurhash, dim, hash, mimeType, thumbhash = thumbhashFromContent, image = posterUrl)
                                    accountViewModel.toastManager.toast(Res.string.media_added, Res.string.media_added_to_profile_gallery)
                                }
                            }
                            onDismiss()
                        }
                    }
                }
            }

            // Share section
            content?.let {
                val context = LocalContext.current

                M3ActionSection {
                    when (content) {
                        is MediaUrlImage -> {
                            videoUri?.let {
                                if (videoUri.isNotEmpty()) {
                                    M3ActionRow(icon = MaterialSymbols.Share, text = stringRes(Res.string.share_image)) {
                                        accountViewModel.viewModelScope.launch { shareImageFile(context, videoUri, mimeType) }
                                        onDismiss()
                                    }
                                }
                            }
                        }

                        is MediaUrlVideo -> {
                            videoUri?.let {
                                if (videoUri.isNotEmpty()) {
                                    M3ActionRow(
                                        icon = MaterialSymbols.Share,
                                        text = stringRes(Res.string.share_video),
                                        enabled = !isDownloadingVideo.value,
                                    ) {
                                        isDownloadingVideo.value = true
                                        Toast.makeText(context, downloadingVideoForSharingStr, Toast.LENGTH_SHORT).show()
                                        accountViewModel.viewModelScope.launch {
                                            shareVideoFile(
                                                context = context,
                                                videoUrl = videoUri,
                                                mimeType = mimeType,
                                                okHttpClient = { url ->
                                                    accountViewModel.httpClientBuilder.okHttpClientForVideo(url)
                                                },
                                                onComplete = {
                                                    isDownloadingVideo.value = false
                                                    onDismiss()
                                                },
                                                onError = {
                                                    isDownloadingVideo.value = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is MediaLocalVideo -> {
                            content.localJavaFile?.let { localFile ->
                                M3ActionRow(icon = MaterialSymbols.Share, text = stringRes(Res.string.share_video)) {
                                    accountViewModel.viewModelScope.launch { shareLocalVideoFile(context, localFile, mimeType) }
                                    onDismiss()
                                }
                            }
                        }

                        else -> { /* No share option for other types */ }
                    }
                }
            }
        }
    }
}

private suspend fun shareImageFile(
    context: Context,
    videoUri: String,
    mimeType: String?,
) {
    try {
        // Get sharable URI and file extension
        val (uri, fileExtension) = ShareHelper.getSharableUriFromUrl(context, videoUri)

        // Determine mime type, use provided or derive from extension
        val determinedMimeType = ShareHelper.resolveShareMimeType(mimeType, fileExtension)

        // Create share intent
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = determinedMimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        context.startActivity(Intent.createChooser(shareIntent, null))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("ZoomableContentView", "Failed to share image: $videoUri", e)
        Toast.makeText(context, loadStringRes(Res.string.unable_to_share_image), Toast.LENGTH_SHORT).show()
    }
}

private suspend fun shareVideoFile(
    context: Context,
    videoUrl: String,
    mimeType: String?,
    okHttpClient: (String) -> OkHttpClient,
    onComplete: () -> Unit,
    onError: () -> Unit,
) {
    val tempFile = ShareHelper.createTempVideoFile(context)
    var sharedFile: File? = null
    try {
        withContext(Dispatchers.IO) {
            // Download video using streaming
            val client = okHttpClient(videoUrl)
            val request =
                Request
                    .Builder()
                    .get()
                    .url(videoUrl)
                    .build()

            client.newCall(request).executeAsync().use { response ->
                check(response.isSuccessful) { "Download failed: ${response.code}" }
                val responseBody = response.body

                // Stream the response to the temp file
                tempFile.outputStream().use { outputStream ->
                    val bytesCopied = responseBody.source().readAll(outputStream.sink())
                    if (bytesCopied == 0L) {
                        throw IOException("Download failed: empty response body")
                    }
                }
            }

            // Prepare the temp file for sharing (determines extension and creates sharable URI)
            val (uri, extension, sharableFile) = ShareHelper.prepareTempVideoForSharing(context, tempFile)
            sharedFile = sharableFile

            // Determine mime type
            val determinedMimeType = ShareHelper.resolveShareMimeType(mimeType, extension)

            // Create share intent
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = determinedMimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, null))
            }
        }

        // Schedule cleanup to allow the receiving app time to copy the file.
        // The application scope, not this one: cleanup must survive after the share UI is dismissed.
        Amethyst.instance.applicationIOScope.launch {
            delay(SHARED_VIDEO_CLEANUP_DELAY_MS)
            sharedFile?.let { file ->
                if (!file.delete()) {
                    Log.w("ZoomableContentView") { "Failed to delete shared file: ${file.path}" }
                }
            }
        }

        withContext(Dispatchers.Main) {
            onComplete()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("ZoomableContentView", "Failed to share video: $videoUrl", e)

        // Clean up temp file on error
        if (!tempFile.delete()) {
            Log.w("ZoomableContentView") { "Failed to delete temp file: ${tempFile.path}" }
        }
        sharedFile?.let { file ->
            if (!file.delete()) {
                Log.w("ZoomableContentView") { "Failed to delete shared file: ${file.path}" }
            }
        }

        withContext(Dispatchers.Main) {
            Toast
                .makeText(
                    context,
                    loadStringRes(Res.string.unable_to_share_video),
                    Toast.LENGTH_SHORT,
                ).show()
            onError()
        }
    }
}

private suspend fun shareLocalVideoFile(
    context: Context,
    localFile: File,
    mimeType: String?,
) {
    try {
        withContext(Dispatchers.IO) {
            // Get sharable URI for the local file
            val (uri, extension) = ShareHelper.getSharableUriForLocalVideo(context, localFile)

            // Determine mime type
            val determinedMimeType = ShareHelper.resolveShareMimeType(mimeType, extension)

            // Create share intent
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = determinedMimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, null))
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("ZoomableContentView", "Failed to share local video: ${localFile.path}", e)
        Toast
            .makeText(
                context,
                loadStringRes(Res.string.unable_to_share_video),
                Toast.LENGTH_SHORT,
            ).show()
    }
}

private fun verifyHash(content: MediaUrlContent): Boolean? {
    if (content.hash == null) return null

    Amethyst.instance.diskCache.openSnapshot(BlossomFetcher.diskCacheKey(content.url))?.use { snapshot ->
        val (hashBytes, _) = sha256StreamWithCount(snapshot.data.toFile().inputStream())
        return hashBytes.toHexKey() == content.hash
    }

    return null
}

@Composable
private fun HashVerificationSymbol(verifiedHash: Boolean) {
    val hashVerificationPassedStr = stringRes(Res.string.hash_verification_passed)
    val hashVerificationFailedStr = stringRes(Res.string.hash_verification_failed)
    val localContext = LocalContext.current
    val openDialogMsg = remember { mutableStateOf<String?>(null) }

    openDialogMsg.value?.let {
        InformationDialog(
            title = stringRes(Res.string.hash_verification_info_title),
            textContent = it,
        ) {
            openDialogMsg.value = null
        }
    }

    if (verifiedHash) {
        IconButton(
            modifier = hashVerifierMark,
            onClick = {
                openDialogMsg.value = hashVerificationPassedStr
            },
        ) {
            Icon(
                painter = painterRes(Res.drawable.original, 1),
                contentDescription = stringRes(id = Res.string.hash_verification_passed),
                modifier = Size30Modifier,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
        IconButton(
            modifier = hashVerifierMark,
            onClick = {
                openDialogMsg.value = hashVerificationFailedStr
            },
        ) {
            Icon(
                symbol = MaterialSymbols.Report,
                contentDescription = stringRes(id = Res.string.hash_verification_failed),
                modifier = Size30Modifier,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
