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
package com.vitorpamplona.amethyst.napplethost

import android.graphics.PointF
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.ui.pill.AccessInfoSheet
import com.vitorpamplona.amethyst.commons.browser.ui.pill.AddressSuggestion
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserChromeTheme
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPill
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillEvent
import com.vitorpamplona.amethyst.commons.browser.ui.pill.BrowserPillUi
import com.vitorpamplona.amethyst.commons.browser.ui.pill.CertificateInfo
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleLine
import com.vitorpamplona.amethyst.commons.browser.ui.pill.ConsoleSheet
import com.vitorpamplona.amethyst.commons.browser.ui.pill.DownloadPromptCard
import com.vitorpamplona.amethyst.commons.browser.ui.pill.FindInPagePill
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageDialogCard
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageDialogType
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PageInfoSheet
import com.vitorpamplona.amethyst.commons.browser.ui.pill.PermissionPromptCard
import kotlin.math.abs

/**
 * The browser chrome of a full-screen `:napplet` window (the web browser and the nsite/napplet host), drawn
 * with the shared Compose components from `commonsUI` — the same pill, find pill, console sheet and page
 * cards the embedded tabs use, so both surfaces are pixel-identical.
 *
 * Owns the chrome's Compose state and two [ComposeView]s laid over the page: the pill at the top (grown to
 * the full window while open, so a tap outside closes it) and find / console at the bottom. Page dialogs,
 * permission prompts and page info open as Compose dialogs. The window only feeds state in ([ui], console
 * lines, find results) and handles what comes out through [Listener].
 */
class BrowserChromeHost(
    private val activity: ComponentActivity,
    private val dark: Boolean,
    initial: BrowserPillUi,
    private val listener: Listener,
    private val showClose: Boolean = true,
    private val suggestionsFor: (String) -> List<AddressSuggestion> = { emptyList() },
) {
    /** What the chrome asks its window to do. */
    interface Listener {
        /** Everything from the pill except find and the console, which the host runs itself. */
        fun onPillEvent(event: BrowserPillEvent)

        fun onFind(query: String) {}

        fun onFindNext(forward: Boolean) {}

        fun onFindClosed() {}

        fun onPermissionChange(
            permission: BrowserSitePermission,
            decision: BrowserSitePermission.Decision,
        ) {}

        fun onClearSiteData() {}

        fun onClearAllSiteData() {}

        /** The pill, find or the console opened or closed (the window may need to route back). */
        fun onPanelsChanged() {}
    }

    /** What a sandboxed app was launched with, for [showAccessInfo]. */
    class AccessInfo(
        val title: String,
        val isWebsite: Boolean,
        val capabilities: List<String>,
        val torOn: Boolean?,
        val onManagePermissions: (() -> Unit)?,
    )

    /** A page's JS dialog waiting for an answer. */
    class PendingDialog(
        val type: PageDialogType,
        val host: String?,
        val security: BrowserChrome.Security,
        val message: String,
        val defaultValue: String,
        val offerBlock: Boolean,
        val answer: (confirmed: Boolean, text: String?, block: Boolean) -> Unit,
    )

    /** A site permission request waiting for an answer: allow or not, and whether to remember the choice. */
    class PendingPermission(
        val host: String,
        val security: BrowserChrome.Security,
        val permissions: Set<BrowserSitePermission>,
        val answer: (allow: Boolean, remember: Boolean) -> Unit,
    )

    /**
     * A download the page started, waiting for consent before anything is fetched or written into the
     * shared Downloads collection. [fileName]/[sizeBytes] (-1 when unknown) describe exactly what would
     * be saved; nothing is saved until [answer] runs with true.
     */
    class PendingDownload(
        val host: String?,
        val security: BrowserChrome.Security,
        val fileName: String,
        val sizeBytes: Long,
        val sourceHost: String?,
        val risky: Boolean,
        val answer: (allow: Boolean) -> Unit,
    )

    var ui by mutableStateOf(initial)
    var expanded by mutableStateOf(false)
        private set

    var findOpen by mutableStateOf(false)
        private set
    private var findQuery by mutableStateOf("")
    private var findActive by mutableIntStateOf(0)
    private var findTotal by mutableStateOf<Int?>(null)

    var consoleShowing by mutableStateOf(false)
        private set
    val console = mutableStateListOf<ConsoleLine>()

    var dialog by mutableStateOf<PendingDialog?>(null)
    var permissionPrompt by mutableStateOf<PendingPermission?>(null)
    var downloadPrompt by mutableStateOf<PendingDownload?>(null)
    private var pageInfoOpen by mutableStateOf(false)
    private var accessInfo by mutableStateOf<AccessInfo?>(null)
    private var certificate by mutableStateOf<CertificateInfo?>(null)

    private var topView: ComposeView? = null

    // The collapsed handle's place in the window, for telling a swipe on it from a touch on the page.
    private var handleBounds: RectF? = null
    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop

    // A touch that started on the handle: still undecided, or taken over to open the pill.
    private var handleDown: PointF? = null
    private var swipeTaken = false

    /** Lays the chrome over [root], above the page. */
    fun attach(root: FrameLayout) {
        val top =
            ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { BrowserChromeTheme(dark) { TopChrome() } }
            }
        topView = top
        root.addView(top, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        val bottom =
            ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { BrowserChromeTheme(dark) { BottomChrome() } }
            }
        root.addView(bottom, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    fun showPill(open: Boolean) {
        if (expanded == open) return
        expanded = open
        // Open, the pill's view spans the window so a tap anywhere outside the sheet closes it; closed, it
        // shrinks back to the grabber so every other touch reaches the page.
        topView?.let { view ->
            view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply { height = if (open) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT }
        }
        listener.onPanelsChanged()
    }

    fun openFind() {
        consoleShowing = false
        findOpen = true
        listener.onPanelsChanged()
    }

    fun closeFind() {
        if (!findOpen) return
        findOpen = false
        findQuery = ""
        findTotal = null
        listener.onFindClosed()
        listener.onPanelsChanged()
    }

    fun showConsole(show: Boolean) {
        if (show) closeFind()
        consoleShowing = show
        listener.onPanelsChanged()
    }

    fun setFindResult(
        active: Int,
        total: Int,
    ) {
        findActive = active
        findTotal = total
    }

    fun appendConsole(line: ConsoleLine) {
        if (console.size >= MAX_CONSOLE_LINES) console.removeAt(0)
        console.add(line)
    }

    /** "What it can access" for a sandboxed nSite or nApplet. */
    fun showAccessInfo(info: AccessInfo) {
        accessInfo = info
    }

    /** Page info for the page on screen, with its certificate when it has one. */
    fun showPageInfo(certificate: CertificateInfo?) {
        this.certificate = certificate
        pageInfoOpen = true
    }

    /**
     * The window's touches, before the page sees them. The handle takes no touches itself, so a tap on
     * it lands on whatever the page draws beneath (a game's buttons can sit right there); only a
     * downward swipe that starts on it opens the pill, and the page then gets that touch cancelled.
     * Call from `Activity.dispatchTouchEvent`, with [page] being the activity's own dispatch.
     */
    fun dispatchTouchEvent(
        event: MotionEvent,
        page: (MotionEvent) -> Boolean,
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeTaken = false
                handleDown = if (!expanded && handleBounds?.contains(event.x, event.y) == true) PointF(event.x, event.y) else null
            }
            MotionEvent.ACTION_MOVE -> {
                val down = handleDown
                if (down != null && !swipeTaken) {
                    val dx = event.x - down.x
                    val dy = event.y - down.y
                    if (dy > touchSlop && dy > abs(dx)) {
                        swipeTaken = true
                        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                        page(cancel)
                        cancel.recycle()
                        showPill(true)
                    } else if (abs(dx) > touchSlop || dy < -touchSlop) {
                        handleDown = null
                    }
                }
            }
        }
        if (swipeTaken) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                swipeTaken = false
                handleDown = null
            }
            return true
        }
        return page(event)
    }

    /** Back closes, in order: the open pill, find, then nothing (the page's own history). */
    fun handleBack(): Boolean =
        when {
            expanded -> {
                showPill(false)
                true
            }
            findOpen -> {
                closeFind()
                true
            }
            else -> false
        }

    val wantsBack: Boolean get() = expanded || findOpen

    private fun uiWithConsole(): BrowserPillUi = ui.copy(consoleShowing = consoleShowing, consoleErrors = console.count { it.level == ConsoleLine.Level.ERROR })

    @Composable
    private fun TopChrome() {
        Box(Modifier.fillMaxSize()) {
            if (expanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showPill(false) },
                )
            }
            BrowserPill(
                ui = uiWithConsole(),
                expanded = expanded,
                onExpandedChange = ::showPill,
                onEvent = { event ->
                    when {
                        event is BrowserPillEvent.Action && event.action == BrowserChrome.Action.FIND_IN_PAGE -> openFind()
                        event is BrowserPillEvent.Action && event.action == BrowserChrome.Action.CONSOLE -> showConsole(!consoleShowing)
                        else -> listener.onPillEvent(event)
                    }
                },
                showClose = showClose,
                suggestionsFor = suggestionsFor,
                onPasteAndGo = if (clipboardHasText()) ({ pasteAndGo() }) else null,
                modifier = Modifier.align(Alignment.TopCenter),
                // Swipe-only: see dispatchTouchEvent.
                handleTapToOpen = false,
                handleModifier = Modifier.onGloballyPositioned { handleBounds = it.boundsInWindow().toAndroidRectF() },
            )
        }
        PageDialogs()
    }

    @Composable
    private fun BottomChrome() {
        when {
            findOpen ->
                FindInPagePill(
                    query = findQuery,
                    onQueryChange = {
                        findQuery = it
                        if (it.isEmpty()) findTotal = null
                        listener.onFind(it)
                    },
                    active = findActive,
                    total = findTotal,
                    onNext = listener::onFindNext,
                    onClose = ::closeFind,
                )
            consoleShowing ->
                ConsoleSheet(
                    lines = console,
                    onCopy = { lines -> copy(lines.joinToString("\n") { formatLine(it) }) },
                    onClear = { console.clear() },
                    onCopyLine = { copy(formatLine(it)) },
                    onClose = { showConsole(false) },
                )
        }
    }

    @Composable
    private fun PageDialogs() {
        dialog?.let { pending ->
            Dialog(onDismissRequest = {
                dialog = null
                pending.answer(false, null, false)
            }) {
                PageDialogCard(
                    type = pending.type,
                    host = pending.host,
                    security = pending.security,
                    message = pending.message,
                    defaultValue = pending.defaultValue,
                    offerBlock = pending.offerBlock,
                    onResult = { confirmed, text, block ->
                        dialog = null
                        pending.answer(confirmed, text, block)
                    },
                )
            }
        }
        permissionPrompt?.let { pending ->
            Dialog(onDismissRequest = {
                permissionPrompt = null
                pending.answer(false, false)
            }) {
                PermissionPromptCard(
                    host = pending.host,
                    security = pending.security,
                    permissions = pending.permissions,
                    onAllow = {
                        permissionPrompt = null
                        pending.answer(true, true)
                    },
                    onAllowOnce = {
                        permissionPrompt = null
                        pending.answer(true, false)
                    },
                    onDeny = {
                        permissionPrompt = null
                        pending.answer(false, true)
                    },
                )
            }
        }
        downloadPrompt?.let { pending ->
            Dialog(onDismissRequest = {
                downloadPrompt = null
                pending.answer(false)
            }) {
                DownloadPromptCard(
                    host = pending.host,
                    security = pending.security,
                    fileName = pending.fileName,
                    sizeBytes = pending.sizeBytes,
                    sourceHost = pending.sourceHost,
                    risky = pending.risky,
                    onAllow = {
                        downloadPrompt = null
                        pending.answer(true)
                    },
                    onDeny = {
                        downloadPrompt = null
                        pending.answer(false)
                    },
                )
            }
        }
        accessInfo?.let { info ->
            Dialog(onDismissRequest = { accessInfo = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    AccessInfoSheet(
                        title = info.title,
                        isWebsite = info.isWebsite,
                        capabilities = info.capabilities,
                        torOn = info.torOn,
                        onManagePermissions =
                            info.onManagePermissions?.let { manage ->
                                {
                                    accessInfo = null
                                    manage()
                                }
                            },
                        onDone = { accessInfo = null },
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                }
            }
        }
        if (pageInfoOpen) {
            Dialog(onDismissRequest = { pageInfoOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    PageInfoSheet(
                        ui = ui,
                        certificate = certificate,
                        onPermissionChange = { permission, decision ->
                            ui = ui.copy(sitePermissions = ui.sitePermissions + (permission to decision))
                            listener.onPermissionChange(permission, decision)
                        },
                        onClearSiteData = {
                            pageInfoOpen = false
                            listener.onClearSiteData()
                        },
                        onClearAllSiteData = {
                            pageInfoOpen = false
                            listener.onClearAllSiteData()
                        },
                        modifier = Modifier.widthIn(max = 560.dp),
                    )
                }
            }
        }
    }

    private fun clipboardHasText(): Boolean = BrowserWebTools.clipboardHasText(activity)

    private fun pasteAndGo() {
        val text = BrowserWebTools.clipboardText(activity)
        showPill(false)
        if (text != null) listener.onPillEvent(BrowserPillEvent.Navigate(text))
    }

    private fun copy(text: String) = BrowserWebTools.copyText(activity, "console", text)

    private fun formatLine(line: ConsoleLine): String =
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

    companion object {
        private const val MAX_CONSOLE_LINES = 500

        /** Maps WebView's console level onto the chrome's. */
        fun levelOf(level: ConsoleMessage.MessageLevel): ConsoleLine.Level =
            when (level) {
                ConsoleMessage.MessageLevel.ERROR -> ConsoleLine.Level.ERROR
                ConsoleMessage.MessageLevel.WARNING -> ConsoleLine.Level.WARNING
                ConsoleMessage.MessageLevel.DEBUG -> ConsoleLine.Level.DEBUG
                ConsoleMessage.MessageLevel.TIP -> ConsoleLine.Level.INFO
                else -> ConsoleLine.Level.LOG
            }
    }
}
