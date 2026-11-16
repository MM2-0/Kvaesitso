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
package com.vitorpamplona.amethyst.commons.ui.note.share

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amethyst_logo
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.failed_to_upload_media_no_details
import com.vitorpamplona.amethyst.commons.resources.file_server
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.server_did_not_provide_a_url_after_uploading
import com.vitorpamplona.amethyst.commons.resources.share_as_image
import com.vitorpamplona.amethyst.commons.resources.share_as_image_failed
import com.vitorpamplona.amethyst.commons.resources.share_as_image_generating
import com.vitorpamplona.amethyst.commons.resources.share_as_image_url
import com.vitorpamplona.amethyst.commons.resources.share_as_image_watermark
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.UploadProgressIndicator
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.MediaCaptureState
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.components.rememberImageSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.uploads.errorResource
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import kotlin.coroutines.cancellation.CancellationException

/**
 * Full-screen flow that turns [id]'s note into a shareable image: it renders the post into a
 * framed card, captures it to a bitmap, uploads the PNG to one of the user's Blossom media
 * servers, and hands the resulting URL to the platform's share sheet (the clipboard on desktop).
 */
@Composable
fun ShareNoteAsImageScreen(
    id: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(id) { note ->
        if (note != null) {
            ShareNoteAsImageScreen(note, accountViewModel, nav)
        }
    }
}

/**
 * Renders [id]'s note into the same framed card as [ShareNoteAsImageScreen], captures it to a
 * bitmap and hands it straight to the platform as a PNG (the share sheet on Android, the
 * clipboard on desktop) — no preview, no upload. The screen only shows a brief progress
 * indicator while the card is being captured.
 */
@Composable
fun ShareNoteAsImageFileScreen(
    id: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(id) { note ->
        if (note != null) {
            ShareNoteAsImageFileScreen(note, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteAsImageFileScreen(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val imageSharer = rememberImageSharer()
    val shareImageTitle = stringRes(Res.string.share_as_image)
    val graphicsLayer = rememberGraphicsLayer()
    val mediaCapture = remember { MediaCaptureState() }

    // Guards against capturing/sharing more than once across recompositions.
    var shared by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.share_as_image), nav) },
    ) { pad ->
        Box(
            modifier =
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // Rendered (but unpainted) only to feed the snapshot; the user never sees this card.
            CaptureSource(
                note = note,
                graphicsLayer = graphicsLayer,
                mediaCapture = mediaCapture,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            GeneratingPreview()

            LaunchedEffect(note) {
                // Wait a couple of frames so the card is measured and drawn, then for its media
                // to finish loading before the final snapshot.
                withFrameNanos {}
                withFrameNanos {}
                mediaCapture.awaitMediaLoaded(MEDIA_LOAD_TIMEOUT_MS)
                withFrameNanos {}
                val bitmap = graphicsLayer.toImageBitmap()

                if (!shared) {
                    shared = true
                    val sent =
                        try {
                            imageSharer.share(bitmap, shareImageTitle)
                        } catch (e: Exception) {
                            // Writing the PNG (a full disk) or reaching the share sheet can fail.
                            if (e is CancellationException) throw e
                            Log.w("ShareNoteAsImage", "Could not share the image", e)
                            false
                        }
                    if (!sent) {
                        accountViewModel.toastManager.toast(Res.string.share_as_image, Res.string.share_as_image_failed)
                    } else if (!imageSharer.opensShareSheet) {
                        accountViewModel.toastManager.toast(Res.string.share_as_image, Res.string.copied_to_clipboard)
                    }
                    nav.popBack()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteAsImageScreen(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val imageSharer = rememberImageSharer()
    val textSharer = rememberTextSharer()
    val scope = rememberCoroutineScope()
    val shareUrlTitle = stringRes(Res.string.share_as_image_url)

    val graphicsLayer = rememberGraphicsLayer()
    val mediaCapture = remember { MediaCaptureState() }
    val orchestrator = remember { UploadOrchestrator() }

    // The captured bitmap that becomes the preview and, on share, the uploaded file.
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    // Once true the off-screen capture source is disposed; [preview] holds the final bitmap.
    var captureComplete by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val fileServers by account.blossomServers.hostNameFlow.collectAsState()

    var selectedServer by remember(fileServers) {
        mutableStateOf(
            fileServers.firstOrNull { it == account.settings.defaultFileServer }
                ?: fileServers.firstOrNull()
                ?: DEFAULT_MEDIA_SERVERS[0],
        )
    }

    val fileServerOptions =
        remember(fileServers) {
            fileServers.map { TitleExplainer(it.name, it.baseUrl) }.toImmutableList()
        }

    fun shareImage() {
        val image = preview ?: return
        if (isProcessing) return
        isProcessing = true
        scope.launch {
            val server = selectedServer
            val finalState =
                try {
                    withContext(Dispatchers.IO) {
                        val uri = imageSharer.writePng(image)
                        orchestrator.upload(
                            uri = uri,
                            mimeType = PNG_MIME,
                            alt = null,
                            contentWarningReason = null,
                            compressionQuality = CompressorQuality.UNCOMPRESSED,
                            server = server,
                            account = account,
                            uploader = accountViewModel.mediaUploader,
                            stripMetadata = false,
                        )
                    }
                } catch (e: Exception) {
                    // Writing the PNG can fail (a full disk); leave the screen usable for a retry.
                    if (e is CancellationException) throw e
                    isProcessing = false
                    accountViewModel.toastManager.toast(Res.string.share_as_image_url, e.message, e)
                    return@launch
                }

            isProcessing = false

            when (finalState) {
                is UploadingState.Finished -> {
                    val result = finalState.result
                    if (result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        account.settings.changeDefaultFileServer(server)
                        try {
                            textSharer.share(result.url, null, shareUrlTitle)
                            if (!imageSharer.opensShareSheet) {
                                accountViewModel.toastManager.toast(Res.string.share_as_image_url, Res.string.copied_to_clipboard)
                            }
                            nav.popBack()
                        } catch (e: Exception) {
                            // Desktop puts the link on a clipboard another app may hold: show it instead.
                            if (e is CancellationException) throw e
                            accountViewModel.toastManager.toast(Res.string.share_as_image_url, result.url, e)
                        }
                    } else {
                        accountViewModel.toastManager.toast(
                            Res.string.failed_to_upload_media_no_details,
                            Res.string.server_did_not_provide_a_url_after_uploading,
                        )
                    }
                }

                is UploadingState.Error -> {
                    accountViewModel.toastManager.toast(
                        Res.string.failed_to_upload_media_no_details,
                        finalState.errorResource,
                        *finalState.params,
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.share_as_image_url), nav) },
        bottomBar = {
            ShareBottomBar(
                serverName = selectedServer.name,
                serverOptions = fileServerOptions,
                onSelectServer = { selectedServer = fileServers[it] },
                shareEnabled = captureComplete && preview != null && !isProcessing,
                onShare = ::shareImage,
            )
        },
    ) { pad ->
        Box(
            modifier =
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // Keep the note rendered (but unpainted) until the snapshot is final, so async
            // media has a chance to load into the layer before the second capture.
            if (!captureComplete) {
                CaptureSource(
                    note = note,
                    graphicsLayer = graphicsLayer,
                    mediaCapture = mediaCapture,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                LaunchedEffect(note) {
                    // Wait a couple of frames so the card is measured and drawn, snapshot a
                    // first preview, then refine once its media has finished loading.
                    withFrameNanos {}
                    withFrameNanos {}
                    preview = graphicsLayer.toImageBitmap()
                    mediaCapture.awaitMediaLoaded(MEDIA_LOAD_TIMEOUT_MS)
                    withFrameNanos {}
                    preview = graphicsLayer.toImageBitmap()
                    captureComplete = true
                }
            }

            val captured = preview
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (captured != null) {
                    Image(
                        bitmap = captured,
                        contentDescription = stringRes(Res.string.share_as_image),
                        contentScale = ContentScale.FillWidth,
                        modifier =
                            Modifier
                                .widthIn(max = 460.dp)
                                .fillMaxWidth()
                                .shadow(18.dp, PreviewShape, clip = false)
                                .clip(PreviewShape),
                    )
                } else {
                    GeneratingPreview()
                }
            }

            if (isProcessing) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = PreviewShape,
                        tonalElevation = 6.dp,
                    ) {
                        UploadProgressIndicator(
                            orchestrator,
                            modifier = Modifier.widthIn(min = 160.dp).padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratingPreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringRes(Res.string.share_as_image_generating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomBar(
    serverName: String,
    serverOptions: ImmutableList<TitleExplainer>,
    onSelectServer: (Int) -> Unit,
    shareEnabled: Boolean,
    onShare: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextSpinner(
                label = stringRes(Res.string.file_server),
                placeholder = serverName,
                options = serverOptions,
                onSelect = onSelectServer,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onShare,
                enabled = shareEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    symbol = MaterialSymbols.Share,
                    contentDescription = null,
                    modifier = Size18Modifier,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.quick_action_share))
            }
        }
    }
}

/**
 * The framed card that is recorded into [graphicsLayer] but not painted to the screen — the
 * visible preview is the captured [ImageBitmap] instead. The capture modifier is first in the
 * chain so the opaque background and border are part of the bitmap.
 *
 * [mediaCapture] makes the note's media load regardless of the data-saving settings, draws
 * videos as still frames, and tracks what is still loading.
 */
@Composable
private fun CaptureSource(
    note: Note,
    graphicsLayer: GraphicsLayer,
    mediaCapture: MediaCaptureState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    CompositionLocalProvider(
        LocalMediaCapture provides mediaCapture,
        LocalDisplaySettings provides LocalDisplaySettings.current.copy(showProfilePictures = true),
    ) {
        Box {
            CaptureCard(note, graphicsLayer, accountViewModel, nav)
            // The card is never seen: keep taps off the links and buttons inside it.
            Box(Modifier.matchParentSize().then(BlockPointerInput))
        }
    }
}

private val BlockPointerInput =
    Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent().changes.forEach { it.consume() }
        }
    }

/**
 * Measures the card at its full height, as a feed item is, so a note taller than the window
 * isn't squeezed to fit and centred (which cut its top and bottom out of the snapshot). A
 * runaway note is cut at [MAX_CAPTURE_PIXELS], keeping the bitmap small enough to hold and for
 * Android's canvas to draw: it refuses bitmaps over 100 MB.
 */
private fun Modifier.capturedAtFullHeight() =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val maxHeight = MAX_CAPTURE_PIXELS / placeable.width.coerceAtLeast(1)
        layout(placeable.width, placeable.height.coerceAtMost(maxHeight)) { placeable.place(0, 0) }
    }

@Composable
private fun CaptureCard(
    note: Note,
    graphicsLayer: GraphicsLayer,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                // Lets the recorded size below exceed the window's height.
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    // Intentionally no drawLayer: this source stays invisible and only feeds the
                    // snapshot; the user sees the resulting Image.
                }.clipToBounds()
                .capturedAtFullHeight()
                .clip(PreviewShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape)
                .padding(14.dp),
    ) {
        NoteCompose(
            baseNote = note,
            isQuotedNote = true,
            quotesLeft = 1,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(Res.drawable.amethyst_logo),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringRes(Res.string.share_as_image_watermark),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PNG_MIME = "image/png"

// Upper bound on waiting for the off-screen card's media before the final snapshot. Video
// frames need the file downloaded first, so this is generous.
private const val MEDIA_LOAD_TIMEOUT_MS = 15_000L

private val PreviewShape = RoundedCornerShape(18.dp)

// Largest snapshot, in pixels (48 MB as ARGB): 1080 px wide allows a card about 11,000 px tall.
private const val MAX_CAPTURE_PIXELS = 12_000_000
