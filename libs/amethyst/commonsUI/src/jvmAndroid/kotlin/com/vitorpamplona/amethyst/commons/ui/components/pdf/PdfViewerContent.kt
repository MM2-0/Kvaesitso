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
package com.vitorpamplona.amethyst.commons.ui.components.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.disk.DiskCache
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pdf_next_page
import com.vitorpamplona.amethyst.commons.resources.pdf_previous_page
import com.vitorpamplona.amethyst.commons.resources.pdf_unable_to_open
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.service.pdf.PdfFetcher
import com.vitorpamplona.amethyst.commons.service.pdf.PdfPageRenderer
import com.vitorpamplona.amethyst.commons.ui.components.ViewerBackButton
import com.vitorpamplona.amethyst.commons.ui.components.ViewerControlsRow
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewerControlsVisibility
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.toggleScale
import net.engawapg.lib.zoomable.zoomable
import java.util.LinkedHashMap

// Hard ceiling on each base-rendered page bitmap, in pixels. Higher = sharper when
// the user pinch-zooms inside the dialog, but each page costs ~maxDim^2 * 4 bytes
// of RAM (32-bit pixels). 3072 gives ~26 MB per A4-shaped page.
private const val VIEWER_MAX_DIM_PX = 3072

// Hard ceiling for the per-page zoom-aware detail render. When the user zooms in
// past HI_RES_ZOOM_THRESHOLD we re-render the current page at
// (VIEWER_MAX_DIM_PX * scale) capped at this value. 4096 keeps the bitmap within
// common GPU texture limits (so drawing stays hardware-accelerated) and caps
// memory at ~48 MB for an A4-shaped page. Above 4096 most mid-range GPUs fall
// back to software rendering, which is what caused the pan/zoom jitter.
private const val HI_RES_MAX_DIM_PX = 4096
private const val HI_RES_ZOOM_THRESHOLD = 1.5f
private const val HI_RES_DEBOUNCE_MS = 200L

// Zoom level the viewer animates to when the user double-taps. Matches the
// threshold region where we swap in the hi-res bitmap.
private const val DOUBLE_TAP_ZOOM_SCALE = 2.5f

// How long the page counter stays up on its own after a page turn, once the reader has hidden the
// chrome. Long enough to read "7 / 24" without putting the controls back on top of the page.
private const val PAGE_INDICATOR_FLASH_MS = 1500L

// How many recently-rendered pages to keep around. Pager already pre-composes the
// current page plus one neighbor; this just speeds up small back/forward swipes.
// At VIEWER_MAX_DIM_PX = 3072 this caps memory at ~80 MB worth of page bitmaps.
private const val PAGE_CACHE_SIZE = 3

private class PageBitmapCache(
    private val maxSize: Int,
) {
    private val cache =
        object : LinkedHashMap<Int, ImageBitmap>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, ImageBitmap>): Boolean = size > maxSize
        }

    @Synchronized fun get(key: Int): ImageBitmap? = cache[key]

    @Synchronized fun put(
        key: Int,
        value: ImageBitmap,
    ) {
        cache[key] = value
    }
}

private class PdfDocumentHandle(
    val snapshot: DiskCache.Snapshot,
    val renderer: PdfPageRenderer,
) {
    // Read up front: Compose's saveable PagerState reads pageCount during teardown, which can run
    // *after* close().
    val pageCount: Int = renderer.pageCount
    val mutex: Mutex = Mutex()

    @Volatile var closed: Boolean = false
        private set

    fun close() {
        closed = true
        runCatching { renderer.close() }.onFailure { Log.w("PdfViewerDialog", "renderer close failed", it) }
        runCatching { snapshot.close() }.onFailure { Log.w("PdfViewerDialog", "snapshot close failed", it) }
    }
}

/** Closes documents off the main thread after their viewer has gone. */
private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The full-screen PDF reader: pages side by side, pinch and double-tap zoom, a back button and a
 * page counter that hide on a tap. The platform puts it in its own window or dialog and adds its
 * buttons through [actions] (Android: share and save to gallery). While [holdControlsOpen] is
 * true, as with a share sheet open, the controls stay up.
 *
 * The arrow and page keys turn pages, for a keyboard on any platform. A mouse can't drag the pager,
 * so a platform without touch passes [showPageButtons] for previous/next buttons beside the counter.
 */
@Composable
fun PdfViewerContent(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    holdControlsOpen: Boolean = false,
    showPageButtons: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val platformContext = LocalPlatformContext.current

    @Suppress("ProduceStateDoesNotAssignValue")
    val handleState by produceState<PdfDocumentHandle?>(initialValue = null, key1 = content.url) {
        // Set as soon as the handle exists. Closing the viewer mid-download cancels this producer,
        // and the blocking download and renderer still finish; withContext then drops the handle
        // it built, so without this record nothing would close its snapshot and renderer.
        var opened: PdfDocumentHandle? = null
        value =
            try {
                PdfFetcher.openSnapshot(
                    content.url,
                    { checkNotNull(SingletonImageLoader.get(platformContext).diskCache) },
                    { url -> accountViewModel.httpClientBuilder.okHttpClientForPreview(url) },
                ) { snapshot ->
                    PdfDocumentHandle(snapshot, PdfPageRenderer(snapshot.data.toFile())).also { opened = it }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    opened?.let { orphan -> closeScope.launch { orphan.mutex.withLock { orphan.close() } } }
                    throw e
                }
                Log.w("PdfViewerDialog", "Failed to open PDF: ${content.url}", e)
                null
            }
    }

    // Capture the handle as a local val so the onDispose lambda closes *this* handle,
    // not whatever the delegated property reads at dispose time. Without this, the
    // DisposableEffect keyed on handleState runs its onDispose when handleState
    // transitions from null -> handle, and `handleState?.close()` reads the new handle
    // and closes it right after it was created.
    val handleForDispose = handleState
    DisposableEffect(handleForDispose) {
        onDispose {
            // Off the main thread: closing the cache snapshot takes Coil's global DiskLruCache
            // lock, which its cleanup pass holds across bursts of unlinks. Under the render mutex,
            // so a page render still in flight finishes before the renderer is closed under it.
            handleForDispose?.let { handle ->
                closeScope.launch {
                    handle.mutex.withLock { handle.close() }
                }
            }
        }
    }

    val handle = handleState

    // A PDF that takes longer than the auto-hide delay to fetch would otherwise reveal its first
    // page with the chrome already gone, and nothing left to re-arm the timer.
    val controlsVisible =
        rememberViewerControlsVisibility(
            holdOpen = holdControlsOpen,
            armed = handle != null,
        )

    val pagerState = rememberPagerState { handle?.pageCount ?: 0 }
    val pageCache = remember(handle) { PageBitmapCache(PAGE_CACHE_SIZE) }

    // The page counter is wayfinding rather than a control, so it outlives the buttons for a
    // moment after every page turn -- a reader who tapped the chrome away still sees where a
    // swipe landed.
    var pageJustChanged by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        pageJustChanged = true
        delay(PAGE_INDICATOR_FLASH_MS)
        pageJustChanged = false
    }

    val toggleControls = { if (!holdControlsOpen) controlsVisible.value = !controlsVisible.value }

    val pageScope = rememberCoroutineScope()
    val turnPage = { delta: Int ->
        val target = (pagerState.currentPage + delta).coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))
        pageScope.launch { pagerState.animateScrollToPage(target) }
        Unit
    }

    // The reader takes focus so the page keys reach it without a click first.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(handle) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.PageUp -> {
                            turnPage(-1)
                            true
                        }

                        Key.DirectionRight, Key.PageDown -> {
                            turnPage(1)
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.focusRequester(focusRequester)
                .clickable(onClick = toggleControls),
    ) {
        if (handle == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (handle.pageCount == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringRes(Res.string.pdf_unable_to_open),
                    color = Color.White,
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                PdfPageView(
                    handle = handle,
                    pageIndex = pageIndex,
                    cache = pageCache,
                    // The zoomable page consumes the tap before the box underneath ever sees it,
                    // so the toggle has to hang off the gesture detector that owns it.
                    onTap = toggleControls,
                )
            }
        }

        // The buttons hold the top edge; the page counter lives along the bottom. Keeping them on
        // one strip meant the counter sat dead centre under the display cutout on punch-hole
        // devices, which is exactly where a front camera is. Down here it is clear of the cutout,
        // stays centred on the screen rather than on whatever space the asymmetric button groups
        // leave behind, and reads as wayfinding rather than as another control.
        ViewerControlsRow(modifier = Modifier.align(Alignment.TopCenter)) {
            AnimatedVisibility(visible = controlsVisible.value, enter = fadeIn(), exit = fadeOut()) {
                ViewerBackButton(onDismiss)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (handle != null) {
                AnimatedVisibility(visible = controlsVisible.value, enter = fadeIn(), exit = fadeOut()) {
                    Row(horizontalArrangement = spacedBy(Size10dp), content = actions)
                }
            }
        }

        if (handle != null && handle.pageCount > 0) {
            ViewerControlsRow(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.Center,
                atBottom = true,
            ) {
                if (showPageButtons) {
                    AnimatedVisibility(visible = controlsVisible.value, enter = fadeIn(), exit = fadeOut()) {
                        PageButton(MaterialSymbols.AutoMirrored.KeyboardArrowLeft, stringRes(Res.string.pdf_previous_page)) { turnPage(-1) }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible.value || pageJustChanged,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${handle.pageCount}",
                        color = Color.White,
                        modifier =
                            Modifier
                                .padding(horizontal = Size10dp)
                                .background(Color.Black.copy(alpha = 0.4f), shape = MaterialTheme.shapes.small)
                                .padding(horizontal = Size10dp, vertical = Size5dp),
                    )
                }

                if (showPageButtons) {
                    AnimatedVisibility(visible = controlsVisible.value, enter = fadeIn(), exit = fadeOut()) {
                        PageButton(MaterialSymbols.AutoMirrored.KeyboardArrowRight, stringRes(Res.string.pdf_next_page)) { turnPage(1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageButton(
    symbol: MaterialSymbol,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Size5dp),
        colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Icon(symbol = symbol, contentDescription = description)
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun PdfPageView(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    cache: PageBitmapCache,
    onTap: () -> Unit,
) {
    val cached = cache.get(pageIndex)

    @Suppress("ProduceStateDoesNotAssignValue")
    val baseBitmap by produceState<ImageBitmap?>(initialValue = cached, key1 = handle, key2 = pageIndex) {
        if (value != null) return@produceState
        val rendered = renderPageCatching(handle, pageIndex, VIEWER_MAX_DIM_PX)
        rendered?.let { cache.put(pageIndex, it) }
        value = rendered
    }

    val zoomState = rememberZoomState()

    // Re-render the page at a higher resolution once the user zooms in and settles,
    // so pinch-zoomed text stays crisp instead of getting GPU-upscaled from the base
    // bitmap. Released when zoom drops back under threshold or the page leaves view.
    var hiResBitmap by remember(handle, pageIndex) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(handle, pageIndex, baseBitmap) {
        if (baseBitmap == null) return@LaunchedEffect
        snapshotFlow { zoomState.scale }
            .debounce(HI_RES_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collectLatest { scale ->
                if (scale < HI_RES_ZOOM_THRESHOLD) {
                    hiResBitmap = null
                } else {
                    val target = (VIEWER_MAX_DIM_PX * scale).toInt().coerceAtMost(HI_RES_MAX_DIM_PX)
                    // Skip if the hi-res render wouldn't beat what we already have.
                    val base = baseBitmap ?: return@collectLatest
                    val baseLongest = maxOf(base.width, base.height)
                    if (target <= baseLongest) {
                        hiResBitmap = null
                    } else {
                        hiResBitmap = renderPageCatching(handle, pageIndex, target)
                    }
                }
            }
    }

    val imageBitmap = hiResBitmap ?: baseBitmap

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Medium = bilinear. High is bicubic/Mitchell and gets recomputed on every
                // frame during pan/zoom, which is the main source of jitter when the
                // source bitmap is several megapixels. Bilinear on a 3072-4096 px source
                // looks effectively identical on-screen.
                filterQuality = FilterQuality.Medium,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zoomable(
                            zoomState = zoomState,
                            onTap = { onTap() },
                            onDoubleTap = { position ->
                                zoomState.toggleScale(targetScale = DOUBLE_TAP_ZOOM_SCALE, position = position)
                            },
                        ),
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

private suspend fun renderPageCatching(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    maxDim: Int,
): ImageBitmap? =
    try {
        handle.mutex.withLock {
            if (handle.closed) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    handle.renderer.renderPage(pageIndex, maxDim).image
                }
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("PdfViewerDialog", "Failed to render page $pageIndex at $maxDim px", e)
        null
    }
