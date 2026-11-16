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
@file:Suppress("DEPRECATION")

package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.privacysandbox.ui.client.view.SandboxedSdkView
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.ui.EmbeddedLoadOverlay
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPill
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillEvent
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillUi
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleSheet
import com.vitorpamplona.amethyst.commons.browser.ui.pill.FindInPagePill
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.napplethost.BrowserWebTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

// How far off-screen a parked (inactive) warm tab is shifted — well past any real screen width.
private val OFFSCREEN_SHIFT = 10_000.dp

// Per-drag-move auto-scroll step (CSS px) when a handle is dragged into the surface's top/bottom edge zone.
private const val AUTOSCROLL_STEP_CSS = 40.0

/**
 * Drives the selection loupe from a handle drag: [active] shows/hides the bubble, [fingerLayerPx] is the
 * drag point in the tab layer's px (where the bubble floats), and ([surfaceX], [surfaceY]) is the same point
 * in surface px (what the provider captures around). Called on drag start/move with `true`, on end with `false`.
 */
private typealias OnMagnify = (active: Boolean, fingerLayerPx: Offset, surfaceX: Float, surfaceY: Float) -> Unit

/** Sends a CSS-px positional IME op (caret move / selection extend) to the page; [edge] is null for a caret move. */
private fun EmbeddedImeBridge.sendFieldOp(
    type: String,
    edge: String?,
    cssX: Float,
    cssY: Float,
) = sendImeOp(
    buildJsonObject {
        put("type", type)
        if (edge != null) put("edge", edge)
        put("x", cssX.toDouble())
        put("y", cssY.toDouble())
    }.toString(),
)

/**
 * The persistent surface layer: a full-window overlay (mounted once in the app shell, below the
 * navigation drawer and dialogs) that renders **every** warm embedded session's [SandboxedSdkView] and
 * keeps it attached. The active session is positioned over the current tab's reserved content area; the
 * rest are parked off-screen but stay attached, so their sessions never detach/close — that's what
 * preserves their state across tab swaps.
 *
 * The surface is z-ordered *below* the client window (privacysandbox.ui locks it there), which still
 * forwards touch input to the provider yet lets Compose draw over it — so the active tab's
 * pill ([com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPill]) is rendered on top of the surface here. Each surface is wrapped in an
 * [EmbeddedSurfaceTouchHolder] so a scroll gesture isn't stolen by a host-side ancestor (the
 * cross-process WebView can't defend its own gesture).
 *
 * [barFavoriteIds] are the favorites currently configured as bottom-bar tabs; warm-keep is scoped to
 * them ([EmbeddedTabHost.retainOnly]), plus whatever is momentarily active.
 */
@OptIn(ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedTabLayer(barFavoriteIds: List<String>) {
    val activeId = EmbeddedTabHost.activeId

    // Tell the warm tabs when the app leaves the screen and when it comes back. The host stops them acting for
    // the user right away and pauses their pages on the same schedule the relays wind down on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> EmbeddedTabHost.onAppStopped()
                    Lifecycle.Event.ON_START -> EmbeddedTabHost.onAppStarted()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep only bottom-row apps warm (plus the active tab, even mid-removal). A favorite removed from
    // the bar drops its warm session here.
    LaunchedEffect(barFavoriteIds, activeId) {
        EmbeddedTabHost.retainOnly(barFavoriteIds.toSet() + setOfNotNull(activeId))
    }

    val bounds = EmbeddedTabHost.contentBounds
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // The keyboard's *snapped* target inset (not the animated one). Only the insets object is taken here;
    // its value is read inside the effect below, so a keyboard showing or hiding doesn't recompose this whole
    // layer (every surface, the pill, the selection overlay).
    val imeTarget = WindowInsets.imeAnimationTarget

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                layerOrigin = it.positionInWindow()
                layerSize = it.size
            },
    ) {
        EmbeddedTabHost.sessions.forEach { session ->
            // Key on the controller too: a theme rebuild replaces the controller under the same id, and
            // the new one needs a fresh SandboxedSdkView (the factory below attaches the surface once).
            key(session.id, session.controller) {
                val active = session.id == activeId

                LaunchedEffect(active) {
                    if (active) session.controller.onShown() else session.controller.onHidden()
                }

                val placement =
                    if (bounds.width > 0f && bounds.height > 0f) {
                        with(density) {
                            // Parked tabs keep the SAME size as the active one and are only shoved
                            // off-screen, so bringing one back is a pure translation — no resize, no
                            // surface re-render, no black flash between tabs. (Parking at 1dp forced a
                            // resize + re-render on every switch, which flashed black for ~1s.)
                            val left = (bounds.left - layerOrigin.x).toDp() + (if (active) 0.dp else OFFSCREEN_SHIFT)
                            // Do NOT shrink the surface for the keyboard: resizing reconfigures the cross-process
                            // SurfaceControlViewHost surface, and the first frame presented after that reconfigure
                            // stalls ~1s (the per-focus "freeze"). Keep the surface full-size and let the page bring
                            // the focused field above the keyboard via the shim's scrollIntoView on focus.
                            Modifier
                                .absoluteOffset(left, (bounds.top - layerOrigin.y).toDp())
                                .size(bounds.width.toDp(), bounds.height.toDp())
                        }
                    } else {
                        // No content bounds reported yet: park tiny off-screen until a tab is shown.
                        Modifier
                            .absoluteOffset(x = (-10000).dp)
                            .size(1.dp)
                    }

                AndroidView(
                    // Wrap the surface so it claims scroll gestures from host-side ancestors (the
                    // cross-process WebView can't request-disallow-intercept itself).
                    factory = { ctx ->
                        EmbeddedSurfaceTouchHolder(ctx).apply {
                            val surface = SandboxedSdkView(ctx)
                            addView(surface, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            session.controller.attachView(surface)
                        }
                    },
                    modifier = placement,
                    onRelease = { holder ->
                        (holder.getChildAt(0) as? SandboxedSdkView)?.let { session.controller.detachView(it) }
                    },
                )
            }
        }

        // Loading / error overlay for the active tab, drawn AFTER the surfaces so it covers the active
        // one's (opaque, pre-first-frame) surface — which itself sits above the nav screens, so the overlay
        // can't live in the screen. A spinner until a real page paints, or an error+retry when the load
        // failed or stalled, so a slow / blank / failed load isn't a bare black/white void.
        //
        // Gated on the active *id*, not on a live controller: right after [EmbeddedTabHost.rebuildAll] (an
        // account switch or a theme flip) the active tab has no session at all for a frame or more, and a
        // SandboxedSdkView with no adapter paints nothing but its background — a black rectangle that reads
        // as broken. Treat "no session yet" as "still loading" so the spinner covers that window too.
        val activeController = EmbeddedTabHost.sessions.firstOrNull { it.id == activeId }?.controller
        if (activeId != null && bounds.width > 0f && bounds.height > 0f) {
            var loadStatus by remember(activeId) { mutableStateOf(activeController?.loadStatus ?: EmbeddedLoadStatus()) }
            var timedOut by remember(activeId) { mutableStateOf(false) }
            DisposableEffect(activeId, activeController) {
                // No session yet — the tab is mid-rebuild after an account switch, or being warmed for the
                // first time. Fall back to the "still loading" state so the spinner covers the surface
                // instead of leaving a bare black rectangle where a dead/absent adapter paints nothing.
                loadStatus = activeController?.loadStatus ?: EmbeddedLoadStatus()
                activeController?.onLoadStatusChanged = { loadStatus = it }
                onDispose { activeController?.onLoadStatusChanged = null }
            }
            // Safety net: nothing painted after a grace period → offer a retry. A load still in flight (a slow
            // site, one over Tor) gets a longer budget before it's called stuck, instead of flipping to an error
            // while it's still progressing.
            LaunchedEffect(activeId, loadStatus) {
                timedOut = false
                if (!loadStatus.hasLoadedReal && !loadStatus.failed) {
                    delay(if (loadStatus.isLoading) 45_000 else 12_000)
                    timedOut = true
                }
            }
            if (!loadStatus.hasLoadedReal) {
                with(density) {
                    Box(
                        Modifier
                            .absoluteOffset(
                                (bounds.left - layerOrigin.x).toDp(),
                                (bounds.top - layerOrigin.y).toDp(),
                            ).size(bounds.width.toDp(), bounds.height.toDp()),
                    ) {
                        EmbeddedLoadOverlay(
                            // A tab with no session yet can't have failed — it hasn't started. Keep it on
                            // the spinner so a re-arming tab never flashes an error the user can't act on.
                            failed = activeController != null && (loadStatus.failed || timedOut),
                            onRetry = {
                                timedOut = false
                                activeController?.retry()
                            },
                        )
                    }
                }
            }
        }

        // The active tab's top pull-down pill, drawn AFTER the surfaces so it sits on top of the (z-below)
        // surface, anchored to the top of the active tab's reserved bounds. Its expanded state is owned here
        // (reset per tab) so we can draw a full-area dismiss scrim behind the open pill — while collapsed,
        // only the small grabber is interactive and page taps pass through.
        val chrome = EmbeddedTabHost.activeChrome
        val consoleBridge = activeController as? ConsoleBridge
        val findBridge = activeController as? FindBridge

        if (chrome != null && bounds.width > 0f && bounds.height > 0f) {
            var pillExpanded by remember(activeId) { mutableStateOf(false) }
            var consoleShowing by remember(activeId) { mutableStateOf(false) }
            var findShowing by remember(activeId) { mutableStateOf(false) }
            var findQuery by remember(activeId) { mutableStateOf("") }
            val context = LocalContext.current

            fun closeFind() {
                if (findShowing) findBridge?.find("")
                findShowing = false
                findQuery = ""
            }

            // Switching tabs drops the find bar (its state is per tab), but the page it searched keeps its
            // highlights until told otherwise — clear them on the tab being left.
            DisposableEffect(findBridge) {
                onDispose { if (findShowing) findBridge?.find("") }
            }

            val tabModifier =
                with(density) {
                    Modifier
                        .absoluteOffset(
                            (bounds.left - layerOrigin.x).toDp(),
                            (bounds.top - layerOrigin.y).toDp(),
                        ).size(bounds.width.toDp(), bounds.height.toDp())
                }

            if (pillExpanded) {
                PlatformBackHandler { pillExpanded = false }
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { pillExpanded = false },
                )
            }

            val consoleLogs = consoleBridge?.consoleLogs
            val baseUi =
                chrome.ui.copy(
                    chrome = chrome.ui.chrome.copy(hasFind = chrome.ui.chrome.hasFind && findBridge != null),
                    consoleShowing = consoleShowing,
                )

            Box(tabModifier) {
                WithConsoleErrors(baseUi, consoleBridge?.consoleErrorCount) { ui ->
                    BrowserPill(
                        ui = ui,
                        expanded = pillExpanded,
                        onExpandedChange = { pillExpanded = it },
                        onEvent = { event ->
                            val action = (event as? BrowserPillEvent.Action)?.action
                            when {
                                action == BrowserChrome.Action.FIND_IN_PAGE && findBridge != null -> {
                                    // One bottom panel at a time: find replaces the console.
                                    consoleShowing = false
                                    findShowing = true
                                }
                                action == BrowserChrome.Action.CONSOLE && consoleBridge != null -> {
                                    if (!consoleShowing) closeFind()
                                    consoleShowing = !consoleShowing
                                }
                                else -> chrome.onEvent(event)
                            }
                        },
                        showClose = false,
                        suggestionsFor = chrome.suggestionsFor,
                        // A clipboard query is a binder call: only make it while the pill is open to use it.
                        onPasteAndGo =
                            if (pillExpanded && BrowserWebTools.clipboardHasText(context)) {
                                {
                                    pillExpanded = false
                                    BrowserWebTools.clipboardText(context)?.let { chrome.onEvent(BrowserPillEvent.Navigate(it)) }
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                // Find in page: opened from the pill's Find tile.
                if (findShowing && findBridge != null) {
                    PlatformBackHandler { closeFind() }
                    val result by findBridge.findResult
                    FindInPagePill(
                        query = findQuery,
                        onQueryChange = {
                            findQuery = it
                            findBridge.find(it)
                        },
                        active = result?.active ?: 0,
                        total = result?.total,
                        onNext = findBridge::findNext,
                        onClose = ::closeFind,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                // The developer console: opened from the pill's console row.
                if (consoleShowing && consoleLogs != null) {
                    ConsoleSheet(
                        lines = consoleLogs,
                        onCopy = { lines -> BrowserWebTools.copyText(context, "console", lines.joinToString("\n", transform = ::formatConsoleLine)) },
                        onClear = { consoleBridge.clearConsoleLogs() },
                        onCopyLine = { BrowserWebTools.copyText(context, "console", formatConsoleLine(it)) },
                        onClose = { consoleShowing = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        // The embedded surface can't host the soft keyboard, so an invisible main-window EditText takes
        // it whenever a field in the ACTIVE tab focuses, relaying edits across [EmbeddedImeBridge]. Lives
        // here, in the main app window, so it can actually receive the IME.
        val context = LocalContext.current
        val imeBridge = EmbeddedTabHost.sessions.firstOrNull { it.id == activeId }?.controller as? EmbeddedImeBridge
        val imeView = remember { RemoteImeView(context) }
        // The embedded WebView can't show Chrome's copy/paste toolbar or selection handles in its cross-process
        // surface, so we draw our own over the page and route actions through the hidden EditText (which mirrors
        // the selection). All the show/hide state lives in one [SelectionUiState] so the rules — toolbar hides
        // while dragging or scrolling, handles hide while scrolling — are expressed in one place.
        val sel = remember { SelectionUiState() }
        // The keyboard collapsing while this view still mirrors the page field means the user put it away on a
        // field they are still on (BACK, or the IME's own hide button) — record it so returning to this tab
        // doesn't pop the keyboard back over the page. A tab switch also collapses the insets but does NOT
        // look like this: measured on device, the switch takes focus off the view in the same frame, so
        // isMirroringPageField() is already false there and the mark this tab was owed survives.
        LaunchedEffect(activeId) {
            snapshotFlow { imeTarget.getBottom(density) }.collect { imeBottomPx ->
                if (imeBottomPx == 0 && imeView.isMirroringPageField()) imeView.noteKeyboardDismissed()
            }
        }
        DisposableEffect(imeBridge) {
            val boundId = activeId
            // A warm tab keeps its page focus while parked, so returning to it fires no focus event: ask the
            // page to re-announce whatever field is still focused. Restores the mirror (so a tap can raise the
            // keyboard) and, when the user left mid-typing, brings the keyboard straight back.
            // Consumed only when there is a bridge to ask (the mark is one-shot, so spending it on a bind that
            // can't send the resync would drop the restore this tab was owed).
            var pendingRestore = imeBridge != null && boundId != null && EmbeddedTabHost.takeKeyboardRestore(boundId)
            imeView.bind(imeBridge)
            imeView.onRangeSelectionChanged = { sel.onFieldRangeToggle(it) }
            imeView.onEdited = { sel.onEdited() }
            imeBridge?.onImeEvent = { event ->
                when (event) {
                    is ImeEvent.Focus -> {
                        imeView.onPageFocus(event)
                        // A field took focus → the browser dropped any page-text selection; clear its overlay so
                        // the stale page handles/Copy bar don't sit above (and steal drags from) the field UI.
                        sel.onPageSelection(null)
                        // Cancel any in-flight scroll-hide from the page phase: otherwise the field's own
                        // selection-reveal scrolls keep it armed and the new field handles/toolbar never appear.
                        sel.scrolling = false
                        sel.onFieldReadOnly(event.readOnly)
                        sel.onFieldGeometry(event.geometry, event.text.isNotEmpty())
                    }
                    ImeEvent.WantKeyboard -> {
                        // Still mirroring this field (the keyboard was merely dismissed) → just put it back.
                        // Otherwise the mirror was released by a tab switch and holds another tab's buffer:
                        // ask for the state first and raise once it lands.
                        if (imeView.isMirroringPageField()) {
                            imeView.raiseKeyboard()
                        } else {
                            pendingRestore = true
                            imeBridge.requestImeResync()
                        }
                    }
                    is ImeEvent.ReFocus -> {
                        // Raise only if something asked for it — a tap that rang the doorbell above, or a tab
                        // left with the keyboard up. A plain tab return re-takes the field silently.
                        imeView.onPageReFocus(event.focus, pendingRestore)
                        pendingRestore = false
                        // Re-arm "the field has text" so a tap can show the insertion handle again (a tab
                        // switch resets it). Geometry stays null here, so this can't pop a handle up on its
                        // own — native shows nothing until the user touches the field.
                        sel.onFieldReadOnly(event.focus.readOnly)
                        sel.onFieldGeometry(null, event.focus.text.isNotEmpty())
                    }
                    ImeEvent.Blur -> {
                        imeView.onPageBlur()
                        sel.onBlur()
                    }
                    is ImeEvent.State -> {
                        imeView.onPageState(event)
                        sel.onFieldGeometry(event.geometry, event.text.isNotEmpty())
                    }
                    is ImeEvent.PageSelection -> sel.onPageSelection(event.takeIf { it.active })
                    is ImeEvent.Scroll -> sel.scrolling = event.active
                    is ImeEvent.CaretTap -> sel.onCaretTap(event.geometry)
                }
            }
            // Posted, not sent inline: the session's own `active` effect resumes a paused (parked) WebView in
            // this same effect pass, and a message delivered to a still-paused page can be lost. One turn of
            // the main looper later, the resume is already on its way over the same (FIFO) channel.
            imeView.post { imeBridge?.requestImeResync() }
            onDispose {
                imeBridge?.onImeEvent = null
                imeView.onRangeSelectionChanged = null
                imeView.onEdited = null
                sel.reset()
                // Remember whether the keyboard was up BEFORE onPageBlur takes it down, so returning to this
                // tab resumes exactly the state it was left in. The page keeps its focus (and caret) either
                // way: blurring it here would fire the page's own blur handlers — validation, autocomplete
                // dismissal, submit-on-blur — for a switch the user never made inside the page.
                //
                // Ask the mirror what it *intends* rather than sampling the window: by the time this dispose
                // runs, the nav transition has already snapped `WindowInsets.imeAnimationTarget` to 0 and
                // taken focus off the view, so both would report "no keyboard" for every tab the user left
                // mid-typing — which is exactly the case this restore exists for. [wantsKeyboardForPageField]
                // also answers the other half: only a keyboard THIS mirror holds counts, so typing in the
                // browser's own address bar never arms a restore for a page field.
                // Only for the session that is still warm under this id: after a rebuild (theme, account) or an
                // eviction this disposal runs late, and a mark recorded now would be restored onto a fresh page.
                if (boundId != null && EmbeddedTabHost.isWarm(boundId, imeBridge)) {
                    EmbeddedTabHost.noteKeyboardOnLeave(boundId, imeView.wantsKeyboardForPageField())
                }
                imeView.onPageBlur()
                imeView.bind(null)
            }
        }
        with(density) {
            AndroidView(
                factory = { imeView },
                modifier =
                    Modifier
                        .absoluteOffset(
                            (bounds.left - layerOrigin.x).toDp().coerceAtLeast(0.dp),
                            (bounds.top - layerOrigin.y).toDp().coerceAtLeast(0.dp),
                        ).size(1.dp),
            )
        }

        // Native-style auto-hide: the insertion handle disappears after a few seconds of inactivity; the next
        // caret tap re-emits geometry and re-shows it. A drag keeps it alive (dragging gate) so it never
        // vanishes mid-drag; the key re-arms whenever the caret moves.
        val caretForTimeout = sel.insertionHandle
        LaunchedEffect(caretForTimeout, sel.dragging) {
            if (caretForTimeout != null && !sel.dragging) {
                delay(4_000)
                sel.hideCaret()
            }
        }

        // Selection loupe (magnifier #4): while a caret/selection handle is dragged, show a magnified live
        // slice of the page. Host-side PixelCopy can't read the sandbox surface (ERROR_SOURCE_NO_DATA), so the
        // pixels are captured in the `:napplet` provider and shipped back here (see [EmbeddedMagnifierProbe]).
        // The handles report the drag point via [onMagnify]; we track it for the bubble position and ask the
        // provider for frames, throttled to one in flight (with a 100 ms timeout) so a fast drag can't flood
        // the IPC channel. The bubble follows the finger every move; the bitmap refreshes as frames land.
        val magProbe = imeBridge as? EmbeddedMagnifierProbe
        val magnifier = remember { MagnifierUiState() }
        val magBubble = DpSize(132.dp, 74.dp)
        val magZoom = 1.5f
        // Source rect (surface px) = bubble px / zoom, so the provider-scaled frame lands ≈ bubble-sized.
        val magSrcW = with(density) { (magBubble.width.toPx() / magZoom).roundToInt() }
        val magSrcH = with(density) { (magBubble.height.toPx() / magZoom).roundToInt() }
        val magScope = rememberCoroutineScope()
        DisposableEffect(magProbe) {
            magProbe?.onMagnifierFrame = { frame ->
                if (magnifier.visible) {
                    magnifier.awaitingFrame = false
                    // Decode off the main thread: this runs for every frame of a handle drag.
                    magScope.launch {
                        val image = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(frame.bytes, 0, frame.bytes.size)?.asImageBitmap() }
                        if (image != null && magnifier.visible && frame.requestStampNanos > magnifier.shownFrameStamp) {
                            magnifier.shownFrameStamp = frame.requestStampNanos
                            magnifier.image = image
                        }
                    }
                }
            }
            onDispose {
                magProbe?.onMagnifierFrame = null
                magnifier.hide()
            }
        }
        // Auto-scroll (#9): while dragging a handle near the surface's top/bottom edge, nudge the embedded
        // content so the selection can keep extending past the viewport — like Android. We scroll on each
        // drag-move that's in the edge zone (the finger is usually still micro-moving); the shim keeps the
        // overlays up during this programmatic scroll and re-reports geometry so they track.
        val edgeZonePx = with(density) { 56.dp.toPx() }
        val onMagnify: OnMagnify = { active, fingerPx, surfaceX, surfaceY ->
            // Drives the loupe, the toolbar-hide-while-dragging rule (and the dragged handle hides itself
            // locally), edge auto-scroll, and suspending the nav drawer's edge swipe — one drag lifecycle.
            sel.dragging = active
            EmbeddedSelectionDrag.dragging = active
            if (active && bounds.height > 0f) {
                val oy = bounds.top - layerOrigin.y
                val dy =
                    when {
                        fingerPx.y < oy + edgeZonePx -> -AUTOSCROLL_STEP_CSS
                        fingerPx.y > oy + bounds.height - edgeZonePx -> AUTOSCROLL_STEP_CSS
                        else -> 0.0
                    }
                if (dy != 0.0) {
                    imeBridge?.sendImeOp(
                        buildJsonObject {
                            put("type", "ime.autoscroll")
                            put("dy", dy)
                        }.toString(),
                    )
                }
            }
            if (!active || magProbe == null) {
                magnifier.hide()
            } else {
                magnifier.visible = true
                magnifier.anchorPx = fingerPx
                val nowMs = SystemClock.uptimeMillis()
                if (!magnifier.awaitingFrame || nowMs - magnifier.lastRequestUptimeMs > 100L) {
                    magnifier.awaitingFrame = true
                    magnifier.lastRequestUptimeMs = nowMs
                    magProbe.requestMagnifier(surfaceX, surfaceY, magSrcW, magSrcH, magZoom)
                }
            }
        }

        val originX = bounds.left - layerOrigin.x
        val originY = bounds.top - layerOrigin.y
        val haveBounds = bounds.width > 0f && bounds.height > 0f

        // In-field (<input>/<textarea>) range selection: cut/copy/paste/select-all routed to the hidden
        // EditText, plus draggable start/end handles (drag → `ime.fieldextend`). The toolbar hides while a
        // handle is dragged or the page scrolls; the handles hide only while scrolling.
        // A readonly field offers only the non-destructive half: its text can be selected and copied, but Cut
        // and Paste would silently do nothing (the page rejects the edit), so native never offers them there.
        // Remembered, not rebuilt per composition: this layer recomposes on every IME inset change, bounds
        // report and console line, while the toolbar it feeds is shown only during a selection. A fresh list
        // of fresh lambdas each time would allocate for nothing and, having a new identity every pass, stop
        // the overlay below from ever skipping.
        val fieldItems =
            remember(sel.fieldReadOnly, imeView) {
                listOfNotNull(
                    if (sel.fieldReadOnly) {
                        null
                    } else {
                        "Cut" to {
                            imeView.cutSelection()
                            Unit
                        }
                    },
                    "Copy" to {
                        imeView.copySelection()
                        Unit
                    },
                    if (sel.fieldReadOnly) {
                        null
                    } else {
                        "Paste" to {
                            imeView.pasteClipboard()
                            Unit
                        }
                    },
                    "Select all" to {
                        imeView.selectAllText()
                        Unit
                    },
                )
            }
        val fieldHandles = sel.fieldHandles
        if (haveBounds && fieldHandles != null) {
            RangeSelectionOverlay(
                geometry = fieldHandles,
                surfaceOriginX = originX,
                surfaceOriginY = originY,
                scale = bounds.width / fieldHandles.viewportWidth,
                showToolbar = sel.fieldToolbar != null,
                toolbarItems = fieldItems,
                onMagnify = onMagnify,
                onExtend = { edge, cssX, cssY -> imeBridge?.sendFieldOp("ime.fieldextend", edge, cssX, cssY) },
            )
        } else if (haveBounds && sel.fieldToolbar != null) {
            // Authoritative range but no geometry yet → a centered fallback toolbar (no handles).
            EmbeddedSelectionToolbar(items = fieldItems, centerXpx = originX + bounds.width / 2f, topYpx = originY + with(density) { 8.dp.toPx() })
        }

        // Bare caret in a field (no range): a draggable insertion handle under the cursor, like Android's.
        val caretGeom = sel.insertionHandle
        if (haveBounds && caretGeom?.caretX != null && caretGeom.caretBottom != null) {
            val scale = bounds.width / caretGeom.viewportWidth
            // Half the caret's line height (layer px), so the loupe capture centers on the text line.
            val caretLineHalf =
                (((caretGeom.caretTop?.let { caretGeom.caretBottom - it }) ?: 0f) * scale / 2f)
                    .coerceIn(0f, with(density) { 28.dp.toPx() })
            InsertionHandle(
                tipPx = Offset(originX + caretGeom.caretX * scale, originY + caretGeom.caretBottom * scale),
                surfaceOriginX = originX,
                surfaceOriginY = originY,
                scale = scale,
                lineHalfPx = caretLineHalf,
                onMagnify = onMagnify,
                onTap = { sel.toggleInsertionPopup() },
                onDragTo = { cssX, cssY -> imeBridge?.sendFieldOp("ime.caretmove", null, cssX, cssY) },
            )
        }

        // Tapping the bare insertion handle opens a small Paste / Select-all popup above the caret (native).
        val popupCaret = sel.insertionPopupAt
        if (haveBounds && popupCaret?.caretX != null && popupCaret.caretTop != null) {
            val scale = bounds.width / popupCaret.viewportWidth
            val gap = with(density) { 8.dp.toPx() }
            val toolbarH = with(density) { 44.dp.toPx() }
            val caretTopPx = originY + popupCaret.caretTop * scale
            val caretBottomPx = originY + (popupCaret.caretBottom ?: popupCaret.caretTop) * scale
            val topY = if (caretTopPx - toolbarH - gap > originY) caretTopPx - toolbarH - gap else caretBottomPx + gap
            EmbeddedSelectionToolbar(
                items =
                    listOf(
                        "Paste" to {
                            imeView.pasteClipboard()
                            sel.hideInsertionPopup()
                        },
                        "Select all" to {
                            imeView.selectAllText()
                            sel.hideInsertionPopup()
                        },
                    ),
                centerXpx = originX + popupCaret.caretX * scale,
                topYpx = topY,
            )
        }

        // Plain page-text selection: a Copy bar over the selection + a drag handle at each end (same rules).
        val pageHandles = sel.pageHandles
        val pageSel = sel.pageSelection
        if (haveBounds && pageHandles != null && pageSel != null) {
            RangeSelectionOverlay(
                geometry = pageHandles,
                surfaceOriginX = originX,
                surfaceOriginY = originY,
                scale = bounds.width / pageHandles.viewportWidth,
                showToolbar = sel.pageToolbar != null,
                toolbarItems =
                    listOf(
                        "Copy" to {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("selection", pageSel.text))
                        },
                    ),
                onMagnify = onMagnify,
                onExtend = { edge, cssX, cssY -> imeBridge?.sendFieldOp("ime.pageextend", edge, cssX, cssY) },
            )
        }

        // The loupe sits above everything else, following the active handle/caret drag.
        Magnifier(magnifier, layerSize, magBubble)
    }
}

/**
 * Host-drawn selection overlay shared by in-field (`<input>`/`<textarea>`) and plain page-text selections:
 * a floating toolbar above the selection (only when [showToolbar] — it's hidden mid-drag) plus a draggable
 * handle at each end. [geometry] is in the page's CSS px; [scale] and the surface origin map those to this
 * layer's px. Dragging a handle reports the new CSS-px target via [onExtend], which the shim turns into a
 * selection extension; the resulting geometry update repositions everything.
 */
@Composable
private fun RangeSelectionOverlay(
    geometry: SelectionGeometry,
    surfaceOriginX: Float,
    surfaceOriginY: Float,
    scale: Float,
    showToolbar: Boolean,
    toolbarItems: List<Pair<String, () -> Unit>>,
    onMagnify: OnMagnify?,
    onExtend: (edge: String, cssX: Float, cssY: Float) -> Unit,
) {
    val density = LocalDensity.current

    fun mapX(css: Float) = surfaceOriginX + css * scale

    fun mapY(css: Float) = surfaceOriginY + css * scale

    if (showToolbar) {
        val toolbarH = with(density) { 44.dp.toPx() }
        val gap = with(density) { 8.dp.toPx() }
        val topPx = mapY(geometry.top)
        val toolbarY = if (topPx - toolbarH - gap > surfaceOriginY) topPx - toolbarH - gap else mapY(geometry.bottom) + gap
        EmbeddedSelectionToolbar(
            items = toolbarItems,
            centerXpx = mapX((geometry.left + geometry.right) / 2f),
            topYpx = toolbarY,
        )
    }

    // Half the selection's line height (layer px), clamped — the bounding box spans every selected line, so
    // cap it at a sane single-line height so the loupe centers on the dragged endpoint's line, not the middle
    // of a tall multi-line box.
    val lineHalfPx = ((geometry.bottom - geometry.top) * scale / 2f).coerceAtMost(with(density) { 24.dp.toPx() })
    SelectionHandle(Offset(mapX(geometry.startX), mapY(geometry.startBottom)), isStart = true, surfaceOriginX, surfaceOriginY, scale, lineHalfPx, onMagnify) { x, y -> onExtend("start", x, y) }
    SelectionHandle(Offset(mapX(geometry.endX), mapY(geometry.endBottom)), isStart = false, surfaceOriginX, surfaceOriginY, scale, lineHalfPx, onMagnify) { x, y -> onExtend("end", x, y) }
}

/**
 * A draggable selection-endpoint handle drawn as Android's teardrop: a disc with one squared corner that
 * points to the caret. The start handle points up-right (hangs to the left of the selection start); the end
 * handle points up-left. [tipPx] is the caret foot (this layer's px) where the point sits; a drag reports
 * the new tip position in the page's CSS px.
 */
@Composable
private fun SelectionHandle(
    tipPx: Offset,
    isStart: Boolean,
    surfaceOriginX: Float,
    surfaceOriginY: Float,
    scale: Float,
    lineHalfPx: Float,
    onMagnify: OnMagnify?,
    onDragTo: (cssX: Float, cssY: Float) -> Unit,
) {
    val sizeDp = 22.dp
    val sizePx = with(LocalDensity.current) { sizeDp.toPx() }
    val color = MaterialTheme.colorScheme.primary
    val currentTip by rememberUpdatedState(tipPx)
    val currentMagnify by rememberUpdatedState(onMagnify)
    // The drag gesture is installed once (pointerInput(Unit)) and outlives recompositions, so everything it
    // reads that can change mid-life (a rotation or resize moves the origin, the page's zoom changes the
    // scale, the drag target captures the current bridge) is read through updated state.
    val currentOriginX by rememberUpdatedState(surfaceOriginX)
    val currentOriginY by rememberUpdatedState(surfaceOriginY)
    val currentScale by rememberUpdatedState(scale)
    val currentLineHalf by rememberUpdatedState(lineHalfPx)
    val currentDragTo by rememberUpdatedState(onDragTo)
    var dragTip by remember { mutableStateOf<Offset?>(null) }
    val tip = dragTip ?: tipPx
    EndDragOnDispose(isDragging = { dragTip != null }, onMagnify = { currentMagnify })

    // Loupe capture: X follows the finger, Y is locked to the authoritative endpoint's line ([currentTip] is
    // the foot, so lift by half the line height) — so the bubble shows the line being edited, not wherever the
    // finger drifts vertically.
    fun magnify(
        fingerPx: Offset,
        active: Boolean = true,
    ) = currentMagnify?.invoke(active, fingerPx, fingerPx.x - currentOriginX, currentTip.y - currentOriginY - currentLineHalf)
    // Place the box so its pointed corner lands on the tip: start = top-right corner, end = top-left corner.
    val boxLeft = if (isStart) tip.x - sizePx else tip.x
    Box(
        Modifier
            .absoluteOffset { IntOffset(boxLeft.roundToInt(), tip.y.roundToInt()) }
            .size(sizeDp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragTip = currentTip
                        magnify(currentTip)
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        val np = (dragTip ?: currentTip) + delta
                        dragTip = np
                        currentDragTo((np.x - currentOriginX) / currentScale, (np.y - currentOriginY) / currentScale)
                        magnify(np)
                    },
                    onDragEnd = {
                        dragTip = null
                        magnify(Offset.Zero, active = false)
                    },
                    onDragCancel = {
                        dragTip = null
                        magnify(Offset.Zero, active = false)
                    },
                )
            },
    ) {
        // Hide the teardrop while THIS handle is being dragged — the loupe stands in for it, like Android.
        if (dragTip == null) {
            Canvas(Modifier.fillMaxSize()) {
                val d = size.minDimension
                val path =
                    Path().apply {
                        if (isStart) {
                            moveTo(d, 0f)
                            lineTo(d, d / 2f)
                            arcTo(Rect(0f, 0f, d, d), 0f, 270f, false)
                            close()
                        } else {
                            moveTo(0f, 0f)
                            lineTo(0f, d / 2f)
                            arcTo(Rect(0f, 0f, d, d), 180f, -270f, false)
                            close()
                        }
                    }
                drawPath(path, color)
            }
        }
    }
}

/**
 * A draggable insertion (cursor) handle drawn as Android's upward teardrop, hanging below a bare caret with
 * its tip on the caret. [tipPx] is the caret foot (this layer's px); a drag reports the new caret position in
 * the page's CSS px, which the shim maps back to a character offset.
 */
@Composable
private fun InsertionHandle(
    tipPx: Offset,
    surfaceOriginX: Float,
    surfaceOriginY: Float,
    scale: Float,
    lineHalfPx: Float,
    onMagnify: OnMagnify?,
    onTap: () -> Unit,
    onDragTo: (cssX: Float, cssY: Float) -> Unit,
) {
    val density = LocalDensity.current
    val wPx = with(density) { 20.dp.toPx() }
    val hPx = wPx * 1.4f
    val color = MaterialTheme.colorScheme.primary
    val currentTip by rememberUpdatedState(tipPx)
    val currentMagnify by rememberUpdatedState(onMagnify)
    val currentTap by rememberUpdatedState(onTap)
    // Read through updated state for the same reason as SelectionHandle: the gesture outlives recompositions.
    val currentOriginX by rememberUpdatedState(surfaceOriginX)
    val currentOriginY by rememberUpdatedState(surfaceOriginY)
    val currentScale by rememberUpdatedState(scale)
    val currentLineHalf by rememberUpdatedState(lineHalfPx)
    val currentDragTo by rememberUpdatedState(onDragTo)

    // Loupe capture: X follows the finger, Y is locked to the authoritative caret's line ([currentTip] is the
    // caret foot, so lift by half the line height) — so the bubble shows the edited line, not wherever the
    // finger drifts vertically (e.g. a diagonal drag in a textarea).
    fun magnify(
        fingerPx: Offset,
        active: Boolean = true,
    ) = currentMagnify?.invoke(active, fingerPx, fingerPx.x - currentOriginX, currentTip.y - currentOriginY - currentLineHalf)
    // Track the finger separately from what we draw: the handle renders at the AUTHORITATIVE caret (tipPx,
    // which snaps to a character position as the move round-trips through the shim), while the finger drives
    // the move. So the handle stays glued to the line/text and clamps to the field instead of trailing off.
    var fingerPx by remember { mutableStateOf<Offset?>(null) }
    EndDragOnDispose(isDragging = { fingerPx != null }, onMagnify = { currentMagnify })
    Box(
        Modifier
            .absoluteOffset { IntOffset((tipPx.x - wPx / 2f).roundToInt(), tipPx.y.roundToInt()) }
            .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
            // One unified gesture so a TAP (toggle the Paste/Select-All popup) and a DRAG (move the caret)
            // don't fight each other. We consume the down so the tap never bleeds to the surface (which would
            // place a caret there and dismiss the popup we're opening).
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var dragging = false
                    var fp = currentTip
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (!dragging) {
                                currentTap()
                            } else {
                                fingerPx = null
                                magnify(Offset.Zero, active = false)
                            }
                            break
                        }
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                            fp = currentTip
                            fingerPx = fp
                            magnify(currentTip)
                        }
                        if (dragging) {
                            // Read the delta BEFORE consuming: positionChange() returns Offset.Zero once the
                            // change isConsumed, so consuming first (or the sandbox surface consuming the move in
                            // an earlier pass) would freeze `fp` at the caret — the handle drags but the caret
                            // never moves. positionChangeIgnoreConsumed() is immune to both.
                            fp += change.positionChangeIgnoreConsumed()
                            change.consume()
                            fingerPx = fp
                            currentDragTo((fp.x - currentOriginX) / currentScale, (fp.y - currentOriginY) / currentScale)
                            magnify(fp)
                        }
                    }
                }
            },
    ) {
        // Hide the teardrop while dragging — the loupe stands in for it, like Android.
        if (fingerPx == null) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.width / 2f
                val cx = size.width / 2f
                val cy = size.height - r
                val path =
                    Path().apply {
                        moveTo(cx, 0f)
                        lineTo(cx + r, cy)
                        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), 0f, 180f, false)
                        lineTo(cx, 0f)
                        close()
                    }
                drawPath(path, color)
            }
        }
    }
}

/**
 * Host-drawn copy/paste bar for an embedded page selection. Chrome can't present its own action-mode in the
 * cross-process surface, so this floats over the active tab; each action runs on the hidden [RemoteImeView]
 * (which mirrors the page's text + selection), so cut/paste relay back to the page through the normal path.
 * Uses pointer-input rather than `clickable` so tapping it doesn't pull focus off the EditText (which would
 * drop the keyboard and the selection).
 */
@Composable
private fun EmbeddedSelectionToolbar(
    items: List<Pair<String, () -> Unit>>,
    centerXpx: Float,
    topYpx: Float,
) {
    // Center in a SINGLE layout pass: measure the bar, then place it at centerX − width/2. (The old
    // measure-then-offset approach rendered it left-edge-at-centerX for one frame, so it visibly slid in
    // from the right each time the bar was (re)composed.)
    Layout(
        content = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    items.forEachIndexed { i, (label, action) ->
                        if (i > 0) {
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant),
                            )
                        }
                        SelectionToolbarItem(label, action)
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val bar = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            bar.place((centerXpx - bar.width / 2f).roundToInt(), topYpx.roundToInt())
        }
    }
}

@Composable
private fun SelectionToolbarItem(
    label: String,
    onClick: () -> Unit,
) {
    // The gesture is keyed on the label only, so read the action through updated state: "Copy" captures the
    // selected text, and a stale first lambda copied the previous selection.
    val currentOnClick by rememberUpdatedState(onClick)
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                // Consume the DOWN (not just the up) so the tap never bleeds through to the embedded surface
                // beneath — otherwise tapping the bar over non-editable page area blurs the field.
                .pointerInput(label) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            currentOnClick()
                        }
                    }
                }.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

/**
 * Ends a handle drag that is still in progress when the handle leaves composition — the selection collapsed
 * or the page blurred under the finger. The gesture's own end/cancel callbacks never run then (its coroutine
 * is just cancelled), which left the loupe on screen, the toolbar hidden and the drawer's edge swipe disabled
 * app-wide ([EmbeddedSelectionDrag]).
 */
@Composable
private fun EndDragOnDispose(
    isDragging: () -> Boolean,
    onMagnify: () -> OnMagnify?,
) {
    DisposableEffect(Unit) {
        onDispose {
            if (isDragging()) onMagnify()?.invoke(false, Offset.Zero, 0f, 0f)
        }
    }
}

/** One console line as plain text, for copying. */
private fun formatConsoleLine(line: ConsoleLine): String =
    buildString {
        append(line.level.name).append(": ").append(line.message)
        if (line.source.isNotBlank()) {
            append(" (")
                .append(line.source)
                .append(':')
                .append(line.line)
                .append(')')
        }
    }

/**
 * Reads the page's console error count in a scope of its own: a page that keeps logging errors then
 * recomposes just the pill, not the whole tab layer around it.
 */
@Composable
private fun WithConsoleErrors(
    ui: BrowserPillUi,
    errors: IntState?,
    content: @Composable (BrowserPillUi) -> Unit,
) {
    content(ui.copy(consoleErrors = errors?.intValue ?: 0))
}
