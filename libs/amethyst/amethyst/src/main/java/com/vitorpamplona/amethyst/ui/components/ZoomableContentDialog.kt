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
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_image
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_pdf
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_video
import com.vitorpamplona.amethyst.commons.resources.image_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.resources.pdf_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.resources.video_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaPreloadedContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.localJavaFile
import com.vitorpamplona.amethyst.commons.ui.components.LocalZoomSourceGroup
import com.vitorpamplona.amethyst.commons.ui.components.SlidingCarousel
import com.vitorpamplona.amethyst.commons.ui.components.ViewerBackButton
import com.vitorpamplona.amethyst.commons.ui.components.ViewerControlsRow
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.ZoomTransition
import com.vitorpamplona.amethyst.commons.ui.components.getActivityWindow
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewerControlsVisibility
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomTransition
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceFor
import com.vitorpamplona.amethyst.commons.ui.components.zoomTransitionContainer
import com.vitorpamplona.amethyst.commons.ui.components.zoomTransitionLayer
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.VideoViewInner
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
fun ZoomableImageDialog(
    imageUrl: BaseMediaContent,
    allImages: ImmutableList<BaseMediaContent> = listOf(imageUrl).toImmutableList(),
    source: ZoomSourceAnchor? = null,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    sourceCornerRadius: Dp = 0.dp,
) {
    val pagerState: PagerState =
        rememberPagerState(initialPage = allImages.indexOf(imageUrl).coerceAtLeast(0)) { allImages.size }

    // The grow-from-thumbnail transition, shared with the desktop viewer. It closes into the
    // thumbnail of the media on screen, not the one tapped: after paging to another image, that
    // one's own thumbnail, or a fade when the note doesn't show it.
    val group = LocalZoomSourceGroup.current
    val transition =
        rememberZoomTransition(
            source = { zoomSourceFor(allImages.getOrNull(pagerState.currentPage), imageUrl, source, group) },
            sourceCornerRadius = sourceCornerRadius,
            onDismissed = onDismiss,
        )

    // ZoomState of the currently-visible image, hoisted from RenderImageOrVideo so the
    // grow/shrink animation can read live scale/offset and start the exit from the
    // image's actual on-screen bounds when the user has zoomed in.
    var currentZoomState by remember { mutableStateOf<ZoomState?>(null) }

    Dialog(
        onDismissRequest = transition::dismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = true,
                decorFitsSystemWindows = false,
            ),
    ) {
        val orientation = LocalConfiguration.current.orientation
        println("This Log only exists to force orientation listener $orientation")

        val activityWindow = getActivityWindow()
        val dialogWindow = getDialogWindow()

        // Keyed, not inline: each assignment below is a window-manager round trip, so it must run
        // on orientation changes only, not on every recomposition of this content.
        DisposableEffect(orientation, activityWindow, dialogWindow) {
            if (activityWindow != null && dialogWindow != null) {
                // Preserve what the dialog window owns: the brightness override applied by the
                // fullscreen swipe controls, and the HDR mode [RequestHdrFor] manages for the
                // image on screen. Copying the activity's instead would snap brightness back
                // mid-session and cap or force HDR by whatever the feed behind happens to show.
                val current = dialogWindow.attributes
                val attributes = WindowManager.LayoutParams()
                attributes.copyFrom(activityWindow.attributes)
                attributes.type = current.type
                // Disable the system dim so the thumbnail stays visible behind the growing dialog.
                attributes.dimAmount = 0f
                attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                attributes.screenBrightness = current.screenBrightness
                attributes.colorMode = current.colorMode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    attributes.desiredHdrHeadroom = current.desiredHdrHeadroom
                }
                dialogWindow.attributes = attributes
            }
            onDispose {}
        }

        // Go fully immersive while the full-screen media viewer is open. Applies to full-screen
        // images and video alike (shared dialog), and matches the PDF viewer.
        ImmersiveSystemBarsEffect(dialogWindow)

        Box(modifier = Modifier.fillMaxSize()) {
            // Background surface that fades in as the content grows to fullscreen.
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = transition.progress },
            ) {}

            DialogContent(
                allImages = allImages,
                imageUrl = imageUrl,
                pagerState = pagerState,
                transition = transition,
                currentZoomState = { currentZoomState },
                onZoomStateChanged = { currentZoomState = it },
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
private fun DialogContent(
    allImages: ImmutableList<BaseMediaContent>,
    imageUrl: BaseMediaContent,
    pagerState: PagerState,
    transition: ZoomTransition,
    currentZoomState: () -> ZoomState?,
    onZoomStateChanged: (ZoomState) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val onContentPositioned: (LayoutCoordinates, Float?) -> Unit = transition::onMediaPositioned
    val onDismiss: () -> Unit = transition::dismiss

    val sharePopupExpanded = remember { mutableStateOf(false) }
    val controllerVisible = rememberViewerControlsVisibility(holdOpen = sharePopupExpanded.value)

    LaunchedEffect(key1 = pagerState, key2 = imageUrl) {
        val page = allImages.indexOf(imageUrl)
        if (page > -1) {
            pagerState.scrollToPage(page)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                onClick = {
                    if (!sharePopupExpanded.value) {
                        controllerVisible.value = !controllerVisible.value
                    }
                },
            ).zoomTransitionContainer(transition),
        Alignment.TopCenter,
    ) {
        // Transformed image/video container. Only this layer scales & translates so the
        // image aligns with the tapped thumbnail on enter/exit. Controls stay put.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .zoomTransitionLayer(transition, currentZoomState),
        ) {
            if (allImages.size > 1) {
                SlidingCarousel(
                    pagerState = pagerState,
                ) { index ->
                    allImages.getOrNull(index)?.let { pageContent ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val isCurrent = index == pagerState.currentPage
                            RenderImageOrVideo(
                                content = pageContent,
                                roundedCorner = false,
                                isFiniteHeight = true,
                                controllerVisible = controllerVisible,
                                accountViewModel = accountViewModel,
                                onContentPositioned =
                                    if (isCurrent) onContentPositioned else null,
                                onZoomStateChanged =
                                    if (isCurrent) onZoomStateChanged else null,
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RenderImageOrVideo(
                        content = imageUrl,
                        roundedCorner = false,
                        isFiniteHeight = true,
                        controllerVisible = controllerVisible,
                        accountViewModel = accountViewModel,
                        onContentPositioned = onContentPositioned,
                        onZoomStateChanged = onZoomStateChanged,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controllerVisible.value,
            enter = fadeIn(),
            exit = fadeOut(),
            // Also fade with the grow animation so controls appear/disappear alongside it.
            modifier = Modifier.graphicsLayer { alpha = transition.progress.coerceIn(0f, 1f) },
        ) {
            ViewerControlsRow {
                ViewerBackButton(onDismiss)

                Spacer(modifier = Modifier.weight(1f))

                allImages.getOrNull(pagerState.currentPage)?.let { myContent ->
                    if (myContent is MediaUrlImage || myContent is MediaLocalImage) {
                        ViewerShareButton(myContent, sharePopupExpanded, accountViewModel)
                    }

                    val isPdfOrStaticImage = myContent is MediaUrlImage || myContent is MediaLocalImage || myContent is MediaUrlPdf
                    val isNotLiveStream = myContent !is MediaUrlContent || !isHlsMedia(myContent.url, myContent.mimeType)
                    if (isPdfOrStaticImage && isNotLiveStream) {
                        ViewerSaveToGalleryButton(myContent, accountViewModel)
                    }
                }
            }
        }
    }
}

// Takes the resolved text, not the catalog entry: MediaSaverToDisk's onSuccess is a
// plain callback, and the catalog's only non-composable accessor suspends.
private fun showToastOnMain(
    context: Context,
    text: String,
) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}

internal suspend fun saveMediaToGallery(
    content: BaseMediaContent,
    localContext: Context,
    accountViewModel: AccountViewModel,
) {
    val isImage = content is MediaUrlImage || content is MediaLocalImage
    val isPdf = content is MediaUrlPdf

    val success =
        when {
            isImage -> Res.string.image_saved_to_the_gallery
            isPdf -> Res.string.pdf_saved_to_the_gallery
            else -> Res.string.video_saved_to_the_gallery
        }
    val failure =
        when {
            isImage -> Res.string.failed_to_save_the_image
            isPdf -> Res.string.failed_to_save_the_pdf
            else -> Res.string.failed_to_save_the_video
        }
    val successText = loadStringRes(success)

    if (content is MediaUrlContent) {
        MediaSaverToDisk.downloadAndSave(
            content.url,
            mimeType = content.mimeType,
            okHttpClient = {
                if (isImage || isPdf) {
                    accountViewModel.httpClientBuilder.okHttpClientForImage(it)
                } else {
                    accountViewModel.httpClientBuilder.okHttpClientForVideo(it)
                }
            },
            localContext,
            resolveBlossom = {
                Amethyst.instance.blossomResolver
                    .findServers(it)
                    ?.serverUrl
            },
            onSuccess = {
                showToastOnMain(localContext, successText)
            },
            onError = {
                accountViewModel.toastManager.toast(failure, null, it)
            },
        )
    } else if (content is MediaPreloadedContent) {
        content.localJavaFile?.let {
            MediaSaverToDisk.save(
                it,
                content.mimeType,
                localContext,
                onSuccess = {
                    showToastOnMain(localContext, successText)
                },
                onError = { innerIt ->
                    accountViewModel.toastManager.toast(failure, null, innerIt)
                },
            )
        }
    }
}

// A video's measured shape beats its `dim` (see MediaAspectRatioCache.videoRatio); images have none.
private fun BaseMediaContent.aspectRatioOrNull(): Float? =
    when (this) {
        is MediaUrlContent -> MediaAspectRatioCache.videoRatio(url, dim?.aspectRatioOrNull())
        is MediaPreloadedContent -> localJavaFile?.let { MediaAspectRatioCache.videoRatio(it.toUri().toString(), dim?.aspectRatioOrNull()) } ?: dim?.aspectRatioOrNull()
        else -> dim?.aspectRatioOrNull()
    }

@Composable
private fun RenderImageOrVideo(
    content: BaseMediaContent,
    roundedCorner: Boolean,
    isFiniteHeight: Boolean,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    onContentPositioned: ((LayoutCoordinates, aspectRatio: Float?) -> Unit)? = null,
    onZoomStateChanged: ((ZoomState) -> Unit)? = null,
) {
    val contentScale =
        if (isFiniteHeight) {
            ContentScale.Fit
        } else {
            ContentScale.FillWidth
        }

    val rowModifier =
        if (onContentPositioned != null) {
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onContentPositioned(it, content.aspectRatioOrNull()) }
        } else {
            Modifier.fillMaxWidth()
        }

    // Hoist ZoomState so the parent dialog can read live scale/offset and start the
    // exit animation from the image's actual on-screen bounds when zoomed in.
    val zoomState = rememberZoomState()
    LaunchedEffect(zoomState, onZoomStateChanged) {
        onZoomStateChanged?.invoke(zoomState)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = rowModifier) {
        when (content) {
            is MediaUrlImage -> {
                val mainModifier =
                    Modifier
                        .fillMaxWidth()
                        .zoomable(
                            zoomState,
                            onTap = {
                                controllerVisible.value = !controllerVisible.value
                            },
                        )

                UrlImageView(
                    content = content,
                    contentScale = contentScale,
                    mainImageModifier = mainModifier,
                    loadedImageModifier = Modifier.fillMaxWidth(),
                    controllerVisible = controllerVisible,
                    accountViewModel = accountViewModel,
                    alwayShowImage = true,
                    fullResolution = true,
                )
            }

            is MediaUrlVideo -> {
                val borderModifier =
                    if (roundedCorner) {
                        MaterialTheme.colorScheme.imageModifier
                    } else {
                        Modifier.fillMaxWidth()
                    }

                val ratio = MediaAspectRatioCache.videoRatio(content.url, content.dim?.aspectRatioOrNull())
                val modifier =
                    if (ratio != null) {
                        Modifier.aspectRatio(ratio)
                    } else {
                        Modifier
                    }

                Box(modifier, contentAlignment = Alignment.Center) {
                    VideoViewInner(
                        videoUri = content.url,
                        mimeType = content.mimeType,
                        aspectRatio = ratio,
                        title = content.description,
                        artworkUri = content.artworkUri,
                        authorName = content.authorName,
                        borderModifier = borderModifier,
                        contentScale = contentScale,
                        nostrUriCallback = content.uri,
                        automaticallyStartPlayback = true,
                        controllerVisible = controllerVisible,
                        hasBlurhash = content.blurhash != null,
                        isFullscreen = true,
                        captions = content.captions,
                        accountViewModel = accountViewModel,
                    )
                }
            }

            is MediaLocalImage -> {
                val mainModifier =
                    Modifier
                        .fillMaxWidth()
                        .zoomable(
                            zoomState,
                            onTap = {
                                controllerVisible.value = !controllerVisible.value
                            },
                        )

                LocalImageView(
                    content = content,
                    contentScale = contentScale,
                    mainImageModifier = mainModifier,
                    loadedImageModifier = Modifier.fillMaxWidth(),
                    controllerVisible = controllerVisible,
                    accountViewModel = accountViewModel,
                    alwayShowImage = true,
                    fullResolution = true,
                )
            }

            is MediaLocalVideo -> {
                val borderModifier =
                    if (roundedCorner) {
                        MaterialTheme.colorScheme.imageModifier
                    } else {
                        Modifier.fillMaxWidth()
                    }

                content.localJavaFile?.let {
                    val ratio = MediaAspectRatioCache.videoRatio(it.toUri().toString(), content.dim?.aspectRatioOrNull())

                    val modifier =
                        if (ratio != null) {
                            Modifier.aspectRatio(ratio)
                        } else {
                            Modifier
                        }

                    Box(modifier, contentAlignment = Alignment.Center) {
                        VideoViewInner(
                            videoUri = it.toUri().toString(),
                            mimeType = content.mimeType,
                            aspectRatio = ratio,
                            title = content.description,
                            artworkUri = content.artworkUri,
                            authorName = content.authorName,
                            borderModifier = borderModifier,
                            contentScale = contentScale,
                            nostrUriCallback = content.uri,
                            automaticallyStartPlayback = true,
                            controllerVisible = controllerVisible,
                            hasBlurhash = content.blurhash != null,
                            isFullscreen = true,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        }
    }
}
