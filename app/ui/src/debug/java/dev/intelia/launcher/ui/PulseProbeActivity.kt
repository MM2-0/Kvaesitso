package dev.intelia.launcher.ui

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import java.io.FileDescriptor
import java.io.PrintWriter

/** Debug-only, bounded capability experiment. No app actions or notification data. */
class PulseProbeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var host: WindowManager? = null
    private lateinit var output: TextView
    private lateinit var calibration: PulseProbeCalibration
    private var details = "Waiting for window insets"
    private var downs = 0
    private var moves = 0
    private var ups = 0
    private var cancels = 0
    private var mode = "none"
    private var actual = "none"
    private var resumed = false
    private var diagnosticsVisible = false
    private val expire = Runnable { detach(); refresh() }
    private val refreshBounds = Runnable { updateBounds() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(24, 25, 28))
        }
        output = TextView(this).apply { setTextColor(Color.WHITE); textSize = 11f }
        column.addView(output)
        fun button(title: String, block: () -> Unit) {
            column.addView(Button(this).apply { text = title; isAllCaps = false; setOnClickListener { block() } })
        }
        button("Upravit polohu kamery / zobrazit náhled") { attach(PulseProbeAccessibilityService.connected != null, false) }
        calibration = PulseProbeCalibration(this) { attach(PulseProbeAccessibilityService.connected != null, false) }
        column.addView(calibration.view)
        button("Skrýt náhled") { detach(); refresh() }
        button("Hotovo") { finish() }
        val technical = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        column.addView(CheckBox(this).apply {
            text = "Technické údaje a testy"; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                diagnosticsVisible = checked; technical.visibility = if (checked) View.VISIBLE else View.GONE; refresh()
            }
        })
        column.addView(technical)
        fun diagnosticButton(title: String, block: () -> Unit) {
            technical.addView(Button(this).apply { text = title; isAllCaps = false; setOnClickListener { block() } })
        }
        diagnosticButton("Application: camera anchor") { attach(false, false) }
        diagnosticButton("Application: safe anchor") { attach(false, true) }
        diagnosticButton("Accessibility: camera anchor") { attach(true, false) }
        diagnosticButton("Accessibility permission settings") {
            startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(24, 25, 28)); addView(column) })
        column.setOnApplyWindowInsetsListener { _, insets ->
            val padding = (16 * resources.displayMetrics.density).toInt()
            val cutout = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
            @Suppress("DEPRECATION")
            column.setPadding(padding + maxOf(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0),
                padding + maxOf(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0),
                padding + maxOf(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0),
                padding + maxOf(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0))
            updateBounds(); insets
        }
        updateBounds()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        detach()
        runRequest()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        setCalibrationActive(true)
        handler.post { runRequest() }
    }

    private fun runRequest() {
        if (!resumed) return
        if (window.decorView.rootWindowInsets == null) {
            handler.postDelayed({ runRequest() }, 100)
            return
        }
        // Explicit activity rotation, never a global device setting.
        val rotation = intent.getIntExtra("rotation", -1)
        if (rotation in 0..3) {
            val requested = intArrayOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)[rotation]
            if (requestedOrientation != requested) { requestedOrientation = requested; return }
        }
        updateBounds()
        when (intent.getStringExtra("probe")) {
            "application" -> attach(false, false)
            "safe" -> attach(false, true)
            "accessibility" -> attach(true, false)
        }
        // Subsequent resumes must not silently reattach a hidden test window.
        intent.removeExtra("probe")
    }

    private fun updateBounds() {
        val insets = window.decorView.rootWindowInsets ?: return
        val cutout = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout else null
        @Suppress("DEPRECATION")
        val status = insets.systemWindowInsetTop
        val metrics = resources.displayMetrics
        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        val rect = displayBounds(windowManager)
        val detected = PulseAnchorGeometry.anchor(rect, cutout?.boundingRects?.minByOrNull { it.top }?.let {
            PulseAnchorGeometry.Bounds(it.left, it.top, it.right, it.bottom)
        }, status, (48 * metrics.density).toInt(), false)
        if (detected != null) {
            @Suppress("DEPRECATION")
            calibration.bind(rect, detected, metrics.density, rotation, windowManager.defaultDisplay.displayId)
        }
        details = "rotation=$rotation density=${metrics.density} statusTop=$status cutout=${cutout?.boundingRects.orEmpty()} safe=${cutout?.safeInsetTop ?: 0}"
        refresh()
    }

    private fun attach(accessibility: Boolean, safe: Boolean) {
        detach()
        val context = if (accessibility) PulseProbeAccessibilityService.connected else this
        if (context == null || (!accessibility && !Settings.canDrawOverlays(this))) {
            mode = "restricted: missing ${if (accessibility) "accessibility host" else "overlay grant"}"
            refresh(); return
        }
        val manager = context.getSystemService(WindowManager::class.java)
        val insets = window.decorView.rootWindowInsets ?: return
        val rect = displayBounds(manager)
        val cutout = if (Build.VERSION.SDK_INT >= 28) insets.displayCutout?.boundingRects?.minByOrNull { it.top } else null
        @Suppress("DEPRECATION")
        val status = insets.systemWindowInsetTop
        val target = (48 * resources.displayMetrics.density).toInt()
        val detected = PulseAnchorGeometry.anchor(rect, cutout?.let { PulseAnchorGeometry.Bounds(it.left, it.top, it.right, it.bottom) }, status, target, safe) ?: return
        val bounds = if (safe) detected else calibration.bounds() ?: detected
        val view = object : View(context) {
            // Visible calibration outline; actual hidden mode is a separate acceptance test.
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 2f }
            override fun onDraw(canvas: Canvas) {
                canvas.drawRect(1f, 1f, width - 1f, height - 1f, paint)
                val cx = width / 2f; val cy = height / 2f
                canvas.drawCircle(cx, cy, minOf(width, height) / 6f, paint)
                canvas.drawLine(cx - 10, cy, cx + 10, cy, paint)
                canvas.drawLine(cx, cy - 10, cx, cy + 10, paint)
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> downs++
                    MotionEvent.ACTION_MOVE -> moves++
                    MotionEvent.ACTION_UP -> ups++
                    MotionEvent.ACTION_CANCEL -> cancels++
                }
                refresh()
                return true
            }
        }.apply { contentDescription = "Pulse calibration test area, no action" }
        val params = WindowManager.LayoutParams(bounds.width, bounds.height,
            if (accessibility) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = bounds.left; y = bounds.top
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            setTitle("Intelia Pulse debug calibration")
        }
        downs = 0; moves = 0; ups = 0; cancels = 0
        mode = "${if (accessibility) "accessibility" else "application"}/${if (safe) "safe" else "camera"} requested=$bounds"
        try {
            manager.addView(view, params)
            overlay = view; host = manager
            view.post { updateBounds() }
            handler.postDelayed(expire, 120_000)
        } catch (_: RuntimeException) { mode = "restricted: addView rejected" }
        refresh()
    }

    private fun displayBounds(manager: WindowManager): PulseAnchorGeometry.Bounds {
        val rect = if (Build.VERSION.SDK_INT >= 30) manager.currentWindowMetrics.bounds else {
            val size = Point()
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRealSize(size)
            Rect(0, 0, size.x, size.y)
        }
        return PulseAnchorGeometry.Bounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    private fun refresh() {
        overlay?.let { val xy = IntArray(2); it.getLocationOnScreen(xy); actual = "${xy[0]},${xy[1]},${it.width},${it.height}" }
        if (overlay == null) actual = "none"
        output.text = "INTELIA · KALIBRACE KAMERY\nNáhled se skryje po 2 minutách nebo při opuštění této obrazovky." +
            (if (mode.startsWith("restricted")) "\nPro náhled chybí povolení překryvu nebo kalibrační služby." else "") +
            (if (diagnosticsVisible) "\n${diagnostics()}" else "")
    }

    private fun detach() {
        handler.removeCallbacks(expire)
        overlay?.let { runCatching { host?.removeViewImmediate(it) } }
        overlay = null; host = null
    }

    private fun diagnostics() = "$details\n$mode\nactual=$actual\nDOWN=$downs MOVE=$moves UP=$ups CANCEL=$cancels"

    private fun setCalibrationActive(active: Boolean) {
        InteliaIslandService.calibrationActive = active
        sendBroadcast(android.content.Intent("dev.intelia.launcher.REFRESH_PULSE").setPackage(packageName))
    }

    override fun onPause() { resumed = false; handler.removeCallbacksAndMessages(null); detach(); setCalibrationActive(false); super.onPause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); detach(); super.onDestroy() }
    override fun onConfigurationChanged(config: android.content.res.Configuration) {
        super.onConfigurationChanged(config); detach(); handler.post(refreshBounds); handler.post { runRequest() }
    }
    override fun dump(prefix: String, fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        refresh()
        writer.println(diagnostics())
    }
}

/** No window-content retrieval, global actions, gesture injection or notification access. */
class PulseProbeAccessibilityService : AccessibilityService() {
    companion object { var connected: PulseProbeAccessibilityService? = null; private set }
    override fun onServiceConnected() { connected = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { connected = null; super.onDestroy() }
}
