package dev.intelia.launcher.ui

import android.app.KeyguardManager
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.*
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import de.mm20.launcher2.searchactions.SearchActionRepository
import de.mm20.launcher2.searchactions.builders.FlowAliasActionBuilder
import de.mm20.launcher2.searchactions.flow.*
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** System-bound, opt-in media control. Never reads notification payloads or app screens. */
class InteliaIslandService : NotificationListenerService(), KoinComponent {
    companion object { @Volatile internal var bridgeConnected: Boolean = false; private set }
    private val preferences: InteliaUiPreferences by inject()
    private val runtime: FlowAliasRuntime by inject()
    private val aliases: SearchActionRepository by inject()
    private val icons: InteliaIconEngine by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val sessions by lazy { getSystemService(MediaSessionManager::class.java) }
    private var panel: LinearLayout? = null
    private var state = InteliaUiState()
    private var targets = emptyList<FlowAliasTarget>()
    private var commands = emptyList<FlowAlias>()
    private var controller: MediaController? = null
    private var expanded = false
    private var hidden = false
    private var connected = false
    private var observing = false
    private var collection: Job? = null
    private var working = false
    private var lastRender: List<Any?>? = null
    private val iconJobs = mutableListOf<Job>()
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = render()
        override fun onPlaybackStateChanged(state: PlaybackState?) = render()
        override fun onSessionDestroyed() { selectSession(emptyList()); refreshSessions() }
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { selectSession(it.orEmpty()) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) hidden = false
            expanded = false
            updateObservation()
        }
    }
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT); addAction("dev.intelia.launcher.REFRESH_PULSE") },
            ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    override fun onListenerConnected() {
        connected = true; bridgeConnected = true
        collection?.cancel()
        collection = scope.launch {
            preferences.state.collectLatest { ui ->
                state = ui
                if (!ui.island) { hidden = false; targets = emptyList(); commands = emptyList(); updateObservation() }
                else combine(runtime.targets(), aliases.getSearchActionBuilders(), icons.revision) { apps, actions, _ ->
                    apps to actions.filterIsInstance<FlowAliasActionBuilder>().map { it.alias }
                }.collect { (apps, flows) -> targets = apps; commands = flows; updateObservation() }
            }
        }
    }
    private fun allowed() = connected && showInteliaIsland(state.island, Settings.canDrawOverlays(this),
        getSystemService(PowerManager::class.java).isInteractive, getSystemService(KeyguardManager::class.java).isKeyguardLocked, hidden)

    private fun updateObservation() {
        if (!allowed()) {
            if (observing) runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
            observing = false; selectSession(emptyList()); removePanel(); return
        }
        if (!observing) {
            try {
                sessions.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, javaClass), handler)
                observing = true
            } catch (_: SecurityException) { removePanel(); return }
        }
        refreshSessions()
    }
    private fun refreshSessions() {
        if (!allowed()) return
        try { selectSession(sessions.getActiveSessions(ComponentName(this, javaClass))) }
        catch (_: SecurityException) { removePanel() }
    }
    private fun selectSession(list: List<MediaController>) {
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { !it.metadata?.getText(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() }
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(mediaCallback)
            controller = next
            next?.registerCallback(mediaCallback, handler)
        }
        render()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun background() = GradientDrawable().apply {
        setColor(Color.argb(247, 22, 23, 25)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.argb(80, 220, 224, 232))
    }
    private fun label(value: CharSequence, size: Float = 14f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private fun action(text: String, description: String = text, run: () -> Unit) = Button(this).apply {
        this.text = text; contentDescription = description; isAllCaps = false; textSize = 13f
        setTextColor(Color.WHITE); backgroundTintList = null
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
            GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(10).toFloat() }, null)
        setPadding(dp(8), dp(3), dp(8), dp(3)); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        minHeight = dp(48); minimumHeight = dp(48); setOnClickListener { if (!working) run() }
    }
    private fun appAction(app: FlowAliasTarget): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(68)
        contentDescription = app.label; isClickable = true; isFocusable = true
        setOnClickListener { if (!working) execute(FlowAlias("pulse", "pulse", app.key)) }
        val parts = app.key.removePrefix("app://").split(':')
        val image = ImageView(this@InteliaIslandService).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val appearance = state
        parts.takeIf { it.size == 2 }?.let { iconJobs += scope.launch {
            try { image.setImageBitmap(icons.load(ComponentName(it[0], it[1]), app.key, appearance)) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { image.setImageDrawable(packageManager.defaultActivityIcon) }
        } }
        addView(image,
            LinearLayout.LayoutParams(dp(32), dp(32)))
        addView(label(app.label, 10f).apply { maxLines = 1; gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)))
    }
    private fun transport(action: Long, run: (MediaController.TransportControls) -> Unit) {
        val current = controller ?: return
        if ((current.playbackState?.actions ?: 0L) and action == 0L) return
        runCatching { run(current.transportControls) }
    }
    private fun render() {
        if (!allowed()) { removePanel(); return }
        val old = panel
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = background(); elevation = dp(8).toFloat()
            setPadding(dp(12), dp(6), dp(12), dp(6)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        val metadata = controller?.metadata
        val title = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.take(160)
            ?: controller?.let { player -> runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(player.packageName, 0)) }.getOrNull() }
        val artist = metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.take(160)
        val playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        val renderKey = listOf(expanded, title?.toString(), artist?.toString(), playing, controller?.playbackState?.actions,
            state.arc, state.icons, state.iconPack, state.iconOverrides, state.theme, resources.configuration.uiMode, icons.revision.value, targets, commands)
        if (old != null && lastRender == renderKey) return
        iconJobs.forEach { it.cancel() }; iconJobs.clear()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48) }
        header.addView(PulseGlyph(this), LinearLayout.LayoutParams(dp(28), dp(28)))
        if (title != null || expanded) header.addView(label(title ?: getString(R.string.intelia_island), 13f),
            LinearLayout.LayoutParams(if (expanded) 0 else dp(154), ViewGroup.LayoutParams.WRAP_CONTENT, if (expanded) 1f else 0f))
        header.contentDescription = getString(R.string.intelia_island) + if (title != null) ": $title" else ""
        header.setOnClickListener { expanded = !expanded; render() }
        header.setOnLongClickListener { expanded = true; render(); true }
        if (expanded) header.addView(action("×", getString(R.string.intelia_close)) { expanded = false; render() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        if (expanded) {
            artist?.let { root.addView(label(it, 12f)) }
            if (controller != null) {
                val row = LinearLayout(this)
                fun control(text: String, description: Int, mask: Long, run: () -> Unit) {
                    val button = action(text, getString(description), run)
                    button.isEnabled = (controller?.playbackState?.actions ?: 0L) and mask != 0L
                    button.alpha = if (button.isEnabled) 1f else .35f
                    row.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
                }
                control("‹", R.string.intelia_previous_track, PlaybackState.ACTION_SKIP_TO_PREVIOUS) { transport(PlaybackState.ACTION_SKIP_TO_PREVIOUS) { it.skipToPrevious() } }
                control(if (playing) "Ⅱ" else "▷", R.string.intelia_play_pause,
                    (if (playing) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY) or PlaybackState.ACTION_PLAY_PAUSE) {
                    if (playing) transport(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) { it.pause() }
                    else transport(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) { it.play() }
                }
                control("›", R.string.intelia_next_track, PlaybackState.ACTION_SKIP_TO_NEXT) { transport(PlaybackState.ACTION_SKIP_TO_NEXT) { it.skipToNext() } }
                root.addView(row)
            }
            val eligible = targets.associateBy { it.key }
            val arc = state.arc.mapNotNull { eligible[it] }
            if (arc.isNotEmpty()) {
                root.addView(label("ARC", 11f).apply { setTextColor(Color.LTGRAY); setPadding(0, dp(12), 0, 0) })
                val row = LinearLayout(this)
                arc.forEach { app -> row.addView(appAction(app), LinearLayout.LayoutParams(0, dp(68), 1f)) }
                root.addView(row)
            }
            val available = commands.filter { alias -> alias.targetKeys.all { it in eligible } }.take(6)
            if (available.isNotEmpty()) {
                root.addView(label("FLOW", 11f).apply { setTextColor(Color.LTGRAY); setPadding(0, dp(8), 0, 0) })
                val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                available.forEach { alias ->
                    content.addView(action(alias.alias + "\n" + alias.targetKeys.joinToString(" → ") { eligible.getValue(it).label }) { execute(alias) },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
                }
                val scroll = ScrollView(this).apply { addView(content); isVerticalScrollBarEnabled = false }
                root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(minOf(available.size * 56, 112))))
            }
            root.addView(action(getString(R.string.intelia_island_hide)) { hidden = true; expanded = false; updateObservation() })
        }
        val width = if (expanded) minOf(resources.displayMetrics.widthPixels - dp(32), dp(344)) else ViewGroup.LayoutParams.WRAP_CONTENT
        val params = WindowManager.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(8); setTitle("Intelia Pulse") }
        try {
            if (old != null) windows.removeView(old)
            panel = null
            windows.addView(root, params); panel = root; lastRender = renderKey
        } catch (_: RuntimeException) { panel = null }
    }
    private fun execute(alias: FlowAlias) {
        working = true
        scope.launch {
            try {
                val result = runtime.activate(this@InteliaIslandService, alias)
                if (result == FlowAliasExecutor.Result.Launched) { expanded = false; render() }
                else Toast.makeText(this@InteliaIslandService, R.string.intelia_flow_partial, Toast.LENGTH_LONG).show()
            } finally { working = false }
        }
    }
    private fun removePanel() {
        iconJobs.forEach { it.cancel() }; iconJobs.clear()
        panel?.let { runCatching { windows.removeView(it) } }; panel = null; lastRender = null
    }
    override fun onListenerDisconnected() {
        connected = false; bridgeConnected = false; collection?.cancel(); updateObservation(); super.onListenerDisconnected()
    }
    override fun onDestroy() {
        connected = false; bridgeConnected = false; updateObservation(); scope.cancel(); unregisterReceiver(screenReceiver); super.onDestroy()
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig); expanded = false; render()
    }
    override fun dump(fd: java.io.FileDescriptor, writer: java.io.PrintWriter, args: Array<out String>?) {
        // System DUMP diagnostics intentionally contain no track/app/message/user content.
        writer.println("Pulse enabled=${state.island} connected=$connected hidden=$hidden observing=$observing window=${panel != null}")
    }
}

private class PulseGlyph(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(canvas: Canvas) {
        val unit = width / 28f; paint.strokeWidth = 2.5f * unit
        canvas.drawLine(9f * unit, 5f * unit, 9f * unit, 20f * unit, paint)
        paint.alpha = 160
        canvas.drawLine(17f * unit, 10f * unit, 17f * unit, 23f * unit, paint); paint.alpha = 255
    }
}
