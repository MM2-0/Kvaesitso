package dev.intelia.launcher.ui

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import androidx.core.content.ContextCompat
import de.mm20.launcher2.searchactions.SearchActionRepository
import de.mm20.launcher2.searchactions.builders.FlowAliasActionBuilder
import de.mm20.launcher2.searchactions.flow.*
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Optional bounded overlay. Never retrieves app content or injects gestures. */
class InteliaPulseAccessibilityService : AccessibilityService(), KoinComponent {
    companion object { @Volatile internal var instance: InteliaPulseAccessibilityService? = null; private set }
    private val preferences: InteliaUiPreferences by inject()
    private val runtime: FlowAliasRuntime by inject()
    private val aliases: SearchActionRepository by inject()
    private val icons: InteliaIconEngine by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val sessions by lazy { getSystemService(MediaSessionManager::class.java) }
    private var state = InteliaUiState()
    private var targets = emptyList<FlowAliasTarget>()
    private var commands = emptyList<FlowAlias>()
    private var controller: MediaController? = null
    private var panel: View? = null
    private var observing = false
    private var collection: Job? = null
    private var restricted = true
    private var hidden = false
    private var detail = false
    private var arc = false
    private var pending: FlowAlias? = null
    private var working = false
    private var presentation = PulsePresentation.RESTRICTED
    private var lastRender: List<Any?>? = null
    private val iconJobs = mutableListOf<Job>()
    private val close = Runnable { detail = false; arc = false; pending = null; render() }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = render()
        override fun onPlaybackStateChanged(playback: PlaybackState?) = render()
        override fun onSessionDestroyed() { select(emptyList()); refreshSessions() }
    }
    private val sessionChanges = MediaSessionManager.OnActiveSessionsChangedListener { select(it.orEmpty()) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) hidden = false
            if (intent?.getBooleanExtra("intelia.home.visible", false) == true) restricted = false
            detail = false; arc = false; pending = null
            observe()
        }
    }
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction("dev.intelia.launcher.REFRESH_PULSE")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onServiceConnected() {
        instance = this
        collection?.cancel()
        collection = scope.launch {
            combine(preferences.state, runtime.targets(), aliases.getSearchActionBuilders(), icons.revision) { ui, apps, builders, _ ->
                Triple(ui, apps, builders.filterIsInstance<FlowAliasActionBuilder>().map { it.alias })
            }.collect { (ui, apps, flows) ->
                state = ui; targets = apps; commands = flows
                if (!ui.island) { hidden = false; detail = false; arc = false; pending = null }
                if (pending != null && pending !in flows) pending = null
                observe()
            }
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Ignore our non-focusable overlay events; only our real activities can clear a restriction.
        if (pkg == packageName && event.className?.toString() !in setOf(InteliaHomeActivity::class.java.name, InteliaPulseCalibrationActivity::class.java.name)) return
        restricted = pkg == "com.android.systemui" || pkg == "com.android.settings" ||
            pkg.endsWith(".permissioncontroller") || pkg.contains("incallui") || pkg == "com.android.server.telecom"
        detail = false; arc = false; pending = null
        observe()
    }
    override fun onInterrupt() { restricted = true; observe() }
    private fun allowed() = state.island && !hidden && !restricted && !InteliaIslandService.calibrationActive &&
        getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun eligible(player: MediaController) = targets.any { it.key.removePrefix("app://").substringBefore(':') == player.packageName }
    private fun observe() {
        val mediaAllowed = allowed() && InteliaIslandService.bridgeConnected
        if (!mediaAllowed) {
            if (observing) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionChanges) }
            observing = false; select(emptyList())
        } else {
            if (!observing) try {
                sessions.addOnActiveSessionsChangedListener(sessionChanges, ComponentName(this, InteliaIslandService::class.java), handler)
                observing = true
            } catch (_: SecurityException) { observing = false }
            refreshSessions()
        }
        render()
    }
    private fun refreshSessions() {
        if (!observing) return
        try { select(sessions.getActiveSessions(ComponentName(this, InteliaIslandService::class.java))) }
        catch (_: SecurityException) { select(emptyList()) }
    }
    private fun select(list: List<MediaController>) {
        val available = list.filter(::eligible)
        val next = available.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: available.firstOrNull { it.sessionToken == controller?.sessionToken }
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback); controller = next; next?.registerCallback(callback, handler)
        }
        render()
    }
    private fun playing() = controller?.playbackState?.state in listOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING)
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun foregroundColor() = if (dark()) Color.rgb(238, 239, 236) else Color.rgb(28, 30, 28)
    private fun dark() = when (state.theme) { 1 -> true; 2 -> false; else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES }
    private fun surface() = GradientDrawable().apply {
        setColor(if (dark()) Color.rgb(22, 25, 24) else Color.rgb(241, 242, 237)); cornerRadius = dp(22).toFloat()
    }
    private fun label(value: CharSequence, size: Float = 14f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(foregroundColor()); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private fun action(value: String, run: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 14f; setTextColor(foregroundColor())
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(if (dark()) 0x22ffffff else 0x22000000), null, GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(12).toFloat() })
        stateListAnimator = null; elevation = 0f
        minimumHeight = dp(48); minHeight = dp(48)
        setOnClickListener { if (!working && allowed()) { renew(); run() } }
    }
    private fun renew() { handler.removeCallbacks(close); handler.postDelayed(close, 12_000) }
    private fun openDetail() { detail = true; arc = false; pending = null; renew(); render() }
    private fun openArc() { arc = true; detail = false; pending = null; renew(); render() }
    private fun navigate(destination: String) {
        detail = false; arc = false; pending = null; render()
        runCatching { startActivity(Intent(this, InteliaHomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("intelia.open", destination)) }
    }
    private fun transport(mask: Long, perform: (MediaController.TransportControls) -> Unit) {
        val player = controller ?: return
        if (eligible(player) && (player.playbackState?.actions ?: 0L) and mask != 0L) runCatching { perform(player.transportControls) }
    }
    private fun render() {
        val metrics = resources.displayMetrics
        val screen = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds else Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        @Suppress("DEPRECATION") val rotation = windows.defaultDisplay.rotation
        @Suppress("DEPRECATION") val displayId = windows.defaultDisplay.displayId
        val profile = state.pulseProfiles[PulseGeometry.key(displayId, rotation, screen.width(), screen.height())]
        val next = pulsePresentation(state.island, allowed() && profile?.confirmed == true, playing(), detail, arc)
        if (next in listOf(PulsePresentation.DISABLED, PulsePresentation.RESTRICTED)) {
            presentation = next; detail = false; arc = false; pending = null; removePanel(); return
        }
        val media = controller?.metadata
        val title = media?.getText(MediaMetadata.METADATA_KEY_TITLE)?.take(160)?.toString().orEmpty()
        val artist = media?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.take(160)?.toString().orEmpty()
        val artwork = (media?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: media?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
            ?.takeIf { !it.isRecycled && it.width <= 1024 && it.height <= 1024 }
        val renderKey = listOf(next, profile, screen, title, artist, controller?.sessionToken, controller?.playbackState?.actions,
            playing(), state.theme, resources.configuration.uiMode, state.arc, state.icons, state.iconPack, state.iconOverrides,
            targets, commands, pending, icons.revision.value, artwork?.generationId)
        if (panel != null && lastRender == renderKey) return
        iconJobs.forEach { it.cancel() }; iconJobs.clear()
        val expanded = next == PulsePresentation.EXPANDED || next == PulsePresentation.ARC
        val anchor = PulseGeometry.bounds(profile!!, screen.width(), screen.height(), metrics.density,
            if (next == PulsePresentation.COMPACT) 40f else profile.paddingDp * 2,
            if (next == PulsePresentation.COMPACT) 6f else profile.paddingDp * 2) ?: run { removePanel(); return }
        val width = if (expanded) minOf(screen.width() - dp(24), dp(344)) else anchor.width
        val safeTop = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top else dp(48)
        val detailTop = maxOf(safeTop, anchor.bottom + dp(6)).coerceAtMost(screen.height() - dp(96))
        val maxHeight = (screen.height() - detailTop - dp(48)).coerceAtLeast(dp(48))
        val root = object : LinearLayout(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, if (expanded) View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST) else h)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            if (next != PulsePresentation.HIDDEN) background = surface()
            if (expanded) { elevation = dp(6).toFloat(); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        }
        if (!expanded) {
            root.contentDescription = getString(R.string.intelia_island) + if (playing() && title.isNotBlank()) ": $title" else ""
            root.isFocusable = true
            root.setOnClickListener { if (playing()) openDetail() else navigate("search") }
            root.setOnLongClickListener { openArc(); true }
            var cancelled = false
            var held = false
            val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent) = true
                override fun onSingleTapUp(e: MotionEvent): Boolean { if (!cancelled) root.performClick(); return true }
                override fun onLongPress(e: MotionEvent) { if (!cancelled) { held = true; root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } }
            })
            root.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) { cancelled = false; held = false }
                if (event.actionMasked == MotionEvent.ACTION_CANCEL || event.pointerCount > 1) cancelled = true
                if (cancelled) {
                    val cancel = MotionEvent.obtain(event); cancel.action = MotionEvent.ACTION_CANCEL
                    detector.onTouchEvent(cancel); cancel.recycle()
                } else detector.onTouchEvent(event)
                // Keep the original window for the entire pointer stream; replacing it while
                // DOWN can hand the remaining gesture to the status bar on some devices.
                if (event.actionMasked == MotionEvent.ACTION_UP && held && !cancelled) root.performLongClick()
                true
            }
            if (next == PulsePresentation.COMPACT) root.addView(PulseAmbientGlyph(this, foregroundColor(), artwork), LinearLayout.LayoutParams(width, anchor.height))
        } else {
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(label(if (arc) "ARC" else title.ifBlank { getString(R.string.intelia_island) }, 15f), LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(action("×") { detail = false; arc = false; pending = null; render() }.apply { contentDescription = getString(R.string.intelia_close) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            root.addView(header)
            val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            if (!arc) {
                if (artist.isNotBlank()) body.addView(label(artist, 12f))
                val row = LinearLayout(this)
                fun control(text: String, description: Int, mask: Long, perform: () -> Unit) {
                    row.addView(action(text, perform).apply {
                        contentDescription = getString(description)
                        isEnabled = (controller?.playbackState?.actions ?: 0L) and mask != 0L
                        alpha = if (isEnabled) 1f else .35f
                    }, LinearLayout.LayoutParams(0, dp(48), 1f))
                }
                control("‹", R.string.intelia_previous_track, PlaybackState.ACTION_SKIP_TO_PREVIOUS) { transport(PlaybackState.ACTION_SKIP_TO_PREVIOUS) { it.skipToPrevious() } }
                control(if (playing()) "Ⅱ" else "▷", R.string.intelia_play_pause, (if (playing()) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY) or PlaybackState.ACTION_PLAY_PAUSE) {
                    if (playing()) transport(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) { it.pause() }
                    else transport(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) { it.play() }
                }
                control("›", R.string.intelia_next_track, PlaybackState.ACTION_SKIP_TO_NEXT) { transport(PlaybackState.ACTION_SKIP_TO_NEXT) { it.skipToNext() } }
                body.addView(row)
                body.addView(action("ARC") { openArc() })
            } else {
                val eligible = targets.associateBy { it.key }
                val preview = pending
                if (preview != null) {
                    body.addView(label(preview.alias, 16f))
                    body.addView(label(preview.targetKeys.joinToString(" → ") { eligible[it]?.label ?: "—" }))
                    body.addView(action(getString(R.string.intelia_pulse_run)) {
                        if (preview in commands && preview.targetKeys.all { it in eligible }) execute(preview)
                    })
                } else {
                    val row = LinearLayout(this)
                    state.arc.forEach { key ->
                        val app = eligible[key]
                        val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(72) }
                        if (app != null) {
                            val image = ImageView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                            item.addView(image, LinearLayout.LayoutParams(dp(32), dp(32)))
                            item.addView(label(app.label, 10f).apply { gravity = Gravity.CENTER; maxLines = 1; setPadding(dp(3), dp(4), dp(3), 0) }, LinearLayout.LayoutParams(-1, -2))
                            item.contentDescription = app.label; item.isFocusable = true
                            item.setOnClickListener { if (!working && allowed()) execute(FlowAlias("pulse", "pulse", app.key)) }
                            val parts = key.removePrefix("app://").split(':')
                            if (parts.size == 2) iconJobs += scope.launch {
                                try { image.setImageBitmap(icons.load(ComponentName(parts[0], parts[1]), key, state)) }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { image.setImageDrawable(packageManager.defaultActivityIcon) }
                            }
                        }
                        row.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                    if (state.arc.isNotEmpty()) body.addView(row)
                    body.addView(action(getString(R.string.intelia_pulse_search)) { navigate("search") })
                    commands.filter { it.targetKeys.all(eligible::containsKey) }.take(6).forEach { flow ->
                        body.addView(action(flow.alias + "  →") { pending = flow; render() })
                    }
                    body.addView(action(getString(R.string.intelia_pulse_settings)) { navigate("settings") })
                }
            }
            body.addView(action(getString(R.string.intelia_island_hide)) { hidden = true; detail = false; arc = false; pending = null; observe() })
            root.addView(ScrollView(this).apply { addView(body); isFillViewport = false }, LinearLayout.LayoutParams(-1, -2))
        }
        root.setOnGenericMotionListener { _, _ -> false }
        val params = WindowManager.LayoutParams(width, if (expanded) -2 else anchor.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (expanded) (profile.x * screen.width() - width / 2).toInt().coerceIn(dp(12), (screen.width() - width - dp(12)).coerceAtLeast(dp(12))) else anchor.left
            y = if (expanded) detailTop else anchor.top
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            setTitle("Intelia Pulse")
        }
        if (expanded) root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) { detail = false; arc = false; pending = null; render() }
            false
        }
        // Keep the compact/hidden gesture listener installed above; only detail needs outside handling.
        try {
            val previous = presentation
            removePanel(); windows.addView(root, params); panel = root; presentation = next; lastRender = renderKey
            if (next == PulsePresentation.COMPACT && previous == PulsePresentation.HIDDEN && !state.reduceMotion && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                root.alpha = 0f; root.animate().alpha(1f).setDuration(160).start()
            }
        } catch (_: RuntimeException) { removePanel(); presentation = PulsePresentation.RESTRICTED }
    }
    private fun execute(alias: FlowAlias) {
        if (working || !allowed()) return
        working = true
        scope.launch {
            try {
                if (runtime.activate(this@InteliaPulseAccessibilityService, alias) != FlowAliasExecutor.Result.Launched)
                    Toast.makeText(this@InteliaPulseAccessibilityService, R.string.intelia_flow_partial, Toast.LENGTH_LONG).show()
                detail = false; arc = false; pending = null; render()
            } finally { working = false }
        }
    }
    private fun removePanel() { panel?.let { runCatching { windows.removeViewImmediate(it) } }; panel = null; lastRender = null }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; restricted = true; observe(); return super.onUnbind(intent) }
    override fun onConfigurationChanged(config: Configuration) { super.onConfigurationChanged(config); detail = false; arc = false; pending = null; lastRender = null; render() }
    override fun onDestroy() {
        instance = null; handler.removeCallbacksAndMessages(null); iconJobs.forEach { it.cancel() }
        if (observing) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionChanges) }
        controller?.unregisterCallback(callback); controller = null; removePanel(); scope.cancel(); unregisterReceiver(receiver); super.onDestroy()
    }
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) {
        writer.println("Pulse presentation=$presentation enabled=${state.island} restricted=$restricted hidden=$hidden observing=$observing window=${panel != null}")
    }
}

private class PulseAmbientGlyph(context: Context, color: Int, private val artwork: Bitmap?) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(canvas: Canvas) {
        val u = resources.displayMetrics.density; paint.strokeWidth = 1.7f * u
        val x = 10 * u; val cy = height / 2f
        canvas.drawLine(x, cy - 5 * u, x, cy + 3 * u, paint)
        canvas.drawLine(x + 5 * u, cy - 2 * u, x + 5 * u, cy + 6 * u, paint)
        if (artwork != null && !artwork.isRecycled) canvas.drawBitmap(artwork, null, RectF(width - 20*u, cy - 8*u, width - 4*u, cy + 8*u), paint)
        else canvas.drawCircle(width - 12 * u, cy, 2 * u, paint)
    }
}
