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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.EmbeddedWebAppController

/**
 * Process-level holder of **warm embedded sessions** — the persistent-surface-layer half of keep-warm.
 * Each warm session's [SandboxedSdkView][androidx.privacysandbox.ui.client.view.SandboxedSdkView] is
 * rendered by [EmbeddedTabLayer] and stays attached to the window the whole time, so its WebView (in
 * the keyless `:napplet` process) keeps its full JS state; the active one is positioned over the
 * current tab's content area, the rest sit off-screen but alive.
 *
 * Warm-keep is scoped to **bottom-row apps**: a session is retained only while its app is a bottom-bar
 * favorite (see [retainOnly], driven by the bottom-bar settings). A favorite opened outside the bottom
 * row restarts when it leaves, and a low-memory trim ([rebuildAll]) drops everything.
 *
 * State is Compose snapshot state so [EmbeddedTabLayer] recomposes as sessions / the active id / the
 * content bounds change. Main-thread only.
 */
@RequiresApi(Build.VERSION_CODES.R)
object EmbeddedTabHost {
    class Warm(
        val id: String,
        val controller: EmbeddedSurfaceController,
    )

    private val warm = mutableStateListOf<Warm>()
    val sessions: List<Warm> get() = warm

    /** Id of the session shown over the current content area, or null when no embedded tab is on top. */
    var activeId by mutableStateOf<String?>(null)
        private set

    /**
     * Bumped whenever every warm session must be rebuilt from scratch (see [rebuildAll]) — a DARK/LIGHT
     * theme flip, or an account switch. Both the theme (`nightThemedContext`) and the per-account storage
     * profile ([com.vitorpamplona.amethyst.napplet.NappletWebViewProfiles]) are locked in at WebView
     * construction and can't be changed on a live WebView, so following either means building a new one.
     * The favorite screens and the preloader key their acquisition on this, so they re-acquire a freshly
     * built session instead of the stale warm one.
     */
    var rebuildEpoch by mutableIntStateOf(0)
        private set

    /** Window-space bounds of the active tab's reserved content area. */
    var contentBounds by mutableStateOf(Rect.Zero)
        private set

    /** The active tab's top-sheet controls, drawn by [EmbeddedTabLayer] over the (z-below) surface. */
    var activeChrome by mutableStateOf<EmbeddedTabChrome?>(null)
        private set

    private var chromeOwner: String? = null

    fun setActiveChrome(
        id: String,
        chrome: EmbeddedTabChrome,
    ) {
        // During a nav cross-fade the outgoing screen can recompose once more; ignore its publish so it
        // can't clobber the incoming (now-active) tab's chrome. [setActive] runs before this screen's
        // first SideEffect, so the active tab's own publish always lands.
        if (activeId != id) return
        // Screens publish a remembered chrome instance, so a no-op recomposition hands us the same
        // object — skip the snapshot write to avoid recomposing the whole tab layer every frame.
        if (chromeOwner == id && activeChrome === chrome) return
        chromeOwner = id
        activeChrome = chrome
    }

    fun clearActiveChrome(id: String) {
        // Same twin race as the active id: on a same-route re-nav the new instance has already published
        // its chrome (same id) before the old one disposes. Only clear when this id is no longer the
        // active tab, so the incoming tab's chrome survives.
        if (chromeOwner == id && activeId != id) {
            chromeOwner = null
            activeChrome = null
        }
    }

    /** Returns the existing warm controller for [id], or creates + registers one via [factory]. */
    fun acquire(
        id: String,
        factory: () -> EmbeddedSurfaceController,
    ): EmbeddedSurfaceController {
        warm.firstOrNull { it.id == id }?.let { return it.controller }
        val controller = factory()
        // A session built while the app is in the background (a rebuild, a preload) starts in that state.
        if (!appVisible) controller.onAppVisibility(false)
        if (backgroundIdle) controller.onBackgroundIdle(true)
        warm.add(Warm(id, controller))
        return controller
    }

    // ---- the app in the background ----

    /** How long the app sits in the background before even the visible tab's page is paused (see there). */
    const val BACKGROUND_PAUSE_MS = NappletHostContract.BACKGROUND_PAUSE_MS

    private var appVisible = true
    private var backgroundIdle = false
    private val backgroundTimer = Handler(Looper.getMainLooper())
    private val goIdle =
        Runnable {
            backgroundIdle = true
            warm.forEach { it.controller.onBackgroundIdle(true) }
        }

    /** The app's UI stopped (went to the background). */
    fun onAppStopped() {
        if (!appVisible) return
        appVisible = false
        warm.forEach { it.controller.onAppVisibility(false) }
        backgroundTimer.postDelayed(goIdle, BACKGROUND_PAUSE_MS)
    }

    /** The app's UI started again. */
    fun onAppStarted() {
        backgroundTimer.removeCallbacks(goIdle)
        if (appVisible) return
        appVisible = true
        warm.forEach { it.controller.onAppVisibility(true) }
        if (backgroundIdle) {
            backgroundIdle = false
            warm.forEach { it.controller.onBackgroundIdle(false) }
        }
    }

    /** True if a warm session already exists for [id] (used by the preloader to skip re-acquiring). */
    fun isWarm(id: String): Boolean = warm.any { it.id == id }

    /** True if [controller] is still the warm session for [id] (not torn down or replaced by a rebuild). */
    fun isWarm(
        id: String,
        controller: Any?,
    ): Boolean = warm.any { it.id == id && it.controller === controller }

    /**
     * The theme (dark or not) the warm sessions were built in. Kept here, next to the sessions, rather than in
     * the watcher's `remember`: an Activity recreated while the process lives re-seeds a remembered value to
     * the CURRENT theme, so a system dark-mode flip that happened meanwhile was never noticed and the warm
     * pages stayed in the old theme.
     */
    private var builtDark: Boolean? = null

    /** Rebuilds every warm session when the resolved theme differs from the one they were built in. */
    fun rebuildIfThemeChanged(dark: Boolean) {
        val previous = builtDark
        builtDark = dark
        // The first report only records what the sessions (built from the same preference) already use.
        if (previous != null && previous != dark) rebuildAll(keepPages = true)
    }

    /**
     * Seeds [contentBounds] with an approximate full-content rect when no tab has reported real bounds
     * yet, so surfaces preloaded before the user visits any tab lay out at a realistic viewport (and so
     * actually download their content) instead of at the 1dp off-screen fallback. A real visit's
     * [reportBounds] overwrites this with the exact area.
     */
    fun seedBoundsIfUnset(bounds: Rect) {
        if (contentBounds == Rect.Zero) contentBounds = bounds
    }

    // Monotonic ownership token. Double-tapping a bottom-bar tab pops and re-adds the SAME route, so the
    // outgoing screen instance disposes *after* the incoming one has already called setActive with the
    // same id. A plain `clearActiveIfMatches(id)` would then null the active id the new instance just set
    // (same id → it "matches"), leaving no active tab and blacking the surface out. Tokening each
    // setActive lets the disposer clear only if nobody claimed active in the meantime.
    private var activeToken = 0L

    /** Marks [id] the active tab and returns the ownership token to hand back to [clearActiveIfOwner]. */
    fun setActive(id: String): Long {
        activeId = id
        activeToken += 1
        return activeToken
    }

    /** Clears the active tab only if [token] is still the latest claim (no newer [setActive] ran). */
    fun clearActiveIfOwner(token: Long) {
        if (activeToken == token) activeId = null
    }

    fun reportBounds(bounds: Rect) {
        contentBounds = bounds
    }

    // Tabs whose soft keyboard was up at the moment the user left them. A warm tab keeps its page focus while
    // it sits off-screen, so coming back is a *resume*: the keyboard returns only for the tabs that had one,
    // the way Android restores a window's IME state. A tab the user deliberately typed on and then dismissed
    // the keyboard for comes back with the caret still in the field but the page unobstructed.
    private val keyboardUpOnLeave = mutableSetOf<String>()

    /** Records whether the soft keyboard was up as [id] stops being the active tab. */
    fun noteKeyboardOnLeave(
        id: String,
        wasUp: Boolean,
    ) {
        if (wasUp) keyboardUpOnLeave.add(id) else keyboardUpOnLeave.remove(id)
    }

    /** Consumes the "restore the keyboard" mark for [id] (a restore happens at most once per leave). */
    fun takeKeyboardRestore(id: String): Boolean = keyboardUpOnLeave.remove(id)

    fun evict(id: String) {
        parked.remove(id)
        val w = warm.firstOrNull { it.id == id } ?: return
        if (activeId == id) activeId = null
        keyboardUpOnLeave.remove(id)
        warm.remove(w)
        w.controller.teardown()
    }

    /**
     * Tears down [id]'s warm session so its screen re-acquires a freshly built one (e.g. an nSite switched
     * between Tor and the open web). Unlike [evict] this keeps [activeId]: the screen stays composed and
     * never re-runs its `setActive`, so clearing it would park the new session off-screen — a blank tab.
     */
    fun rebuild(id: String) {
        val w = warm.firstOrNull { it.id == id } ?: return
        keyboardUpOnLeave.remove(id)
        warm.remove(w)
        w.controller.teardown()
    }

    // How many composed screens currently show each id. Re-navigating to the same route composes the new
    // screen (which acquires the SAME warm controller) before the old one disposes; counting lets the old
    // one's disposal see that the tab is still in use instead of evicting the controller under the new one.
    private val holders = mutableMapOf<String, Int>()

    /** A screen showing [id] entered composition. Pair with [release]. */
    fun hold(id: String) {
        holders[id] = (holders[id] ?: 0) + 1
    }

    // Non-bar tabs whose screen left composition while their back-stack entry lives on — another screen
    // was pushed on top (even the tab's own Site settings). They stay warm until EVERY such entry is
    // destroyed: the same tab can sit in the back stack twice (opened again from inside itself), and popping
    // the top one must not take the session from under the one still waiting below.
    private val parked = mutableMapOf<String, MutableSet<Lifecycle>>()

    private fun isParked(id: String) = !parked[id].isNullOrEmpty()

    /**
     * The last screen showing [id] left composition. A bottom-bar tab ([keepWarm]) stays warm. Any other tab
     * goes once its back-stack entry ([entry]'s lifecycle) is destroyed — right away when it already is (the
     * user left it), or later when merely covered by a pushed screen, so coming back doesn't restart the
     * page.
     */
    fun releaseWhenGone(
        id: String,
        entry: Lifecycle,
        keepWarm: () -> Boolean,
    ) {
        if (keepWarm()) return

        fun gone() {
            parked[id]?.let {
                it.remove(entry)
                if (it.isEmpty()) parked.remove(id)
            }
            // A screen may have come back to this tab meanwhile, another entry may still hold it, or it may
            // have joined the bottom bar.
            if ((holders[id] ?: 0) == 0 && !isParked(id) && !keepWarm()) evict(id)
        }
        if (entry.currentState == Lifecycle.State.DESTROYED) {
            gone()
            return
        }
        // Already watched from an earlier time this entry was covered.
        if (!parked.getOrPut(id) { mutableSetOf() }.add(entry)) return
        entry.addObserver(
            object : LifecycleEventObserver {
                override fun onStateChanged(
                    source: LifecycleOwner,
                    event: Lifecycle.Event,
                ) {
                    if (event != Lifecycle.Event.ON_DESTROY) return
                    entry.removeObserver(this)
                    gone()
                }
            },
        )
    }

    /** A screen showing [id] left composition. Returns true when no other screen still shows it. */
    fun release(id: String): Boolean {
        val left = (holders[id] ?: 1) - 1
        if (left <= 0) holders.remove(id) else holders[id] = left
        return left <= 0
    }

    /** Drops every warm session whose id isn't in [keep] (bottom-row membership + the active tab). */
    fun retainOnly(keep: Set<String>) {
        warm
            .filter { it.id !in keep && !isParked(it.id) }
            .forEach { evict(it.id) }
    }

    /**
     * Something a WebView can only pick up at construction changed (the theme, or the account), or the
     * system asked for memory back: tear down every warm session and bump [rebuildEpoch] so the visible
     * screen and the preloader re-acquire freshly built sessions. This keeps [activeId], so the visible tab
     * re-activates the instant its screen re-acquires — the user just sees the current tab reload, not a
     * blanked-out surface.
     *
     * [keepPages]: each browser tab's rebuilt controller resumes the page it showed, with the user's Tor, zoom
     * and desktop choices ([EmbeddedWebAppController.PageSnapshot]). Android sends the memory trim routinely,
     * about a minute into the background, so without this every pinned site came back on its start URL. Never
     * across an account switch: the next account must not open the previous one's pages.
     */
    fun rebuildAll(keepPages: Boolean) {
        // Every page is about to be rebuilt from scratch, so no field survives to restore a keyboard onto.
        keyboardUpOnLeave.clear()
        val copy = warm.toList()
        warm.clear()
        pageSnapshots.clear()
        if (keepPages) {
            copy.forEach { w -> (w.controller as? EmbeddedWebAppController)?.let { pageSnapshots[w.id] = it.snapshot() } }
        }
        copy.forEach { it.controller.teardown() }
        rebuildEpoch += 1
    }

    // Page state carried from a torn-down browser tab to its rebuilt controller (see [rebuildAll]).
    private val pageSnapshots = mutableMapOf<String, EmbeddedWebAppController.PageSnapshot>()

    /** The page state [rebuildAll] saved for tab [id], handed over once. */
    fun takePageSnapshot(id: String): EmbeddedWebAppController.PageSnapshot? = pageSnapshots.remove(id)

    /**
     * Account the warm sessions were built for, as the opaque WebView storage-profile name (null while
     * logged out). Kept HERE, next to the sessions it describes, rather than in a composable's `remember`:
     * the whole logged-in subtree is rebuilt per account (`key(pubKey)` in `AccountScopedViewModelStore`),
     * so a remembered "last applied" value would be re-seeded to the NEW account on the very first
     * composition after a switch and the change would never be detected.
     */
    private var builtForProfile: String? = null
    private var profileSeeded = false

    /**
     * Rebuilds every warm session when the active account changes, so all embedded apps follow the switch.
     *
     * A WebView's storage profile is fixed at construction (`WebViewCompat.setProfile` throws once it has
     * loaded content), so a live session can't be re-pointed at the new account's jar — it has to be
     * rebuilt. Rebuilding is also what makes the tab work at all after a switch: the account-keyed subtree
     * is recreated, which disposes each session's `SandboxedSdkView` and closes the sandbox-side session,
     * leaving the warm controller holding an already-consumed (and now dead) adapter. Reused as-is, it
     * would hand the fresh view no adapter and the tab would render permanently blank.
     *
     * Covers tabs that aren't on screen too: every warm session is torn down, and the preloader re-warms
     * the pinned ones against the new profile, so none can come back still bound to the old account.
     */
    fun rebuildIfProfileChanged(profileName: String?) {
        if (profileSeeded && builtForProfile == profileName) return
        val isFirstCall = !profileSeeded
        profileSeeded = true
        builtForProfile = profileName
        // Seeding on the first call (app start) must not bump the epoch: nothing is stale yet, and a
        // needless bump would restart the preload sweep that is just getting going.
        if (!isFirstCall) rebuildAll(keepPages = false)
    }
}
