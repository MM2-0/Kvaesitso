package dev.intelia.launcher.ui

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.view.WindowCompat
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class InteliaPulseCalibrationActivity : Activity(), KoinComponent {
    private val preferences: InteliaUiPreferences by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controls: PulseCalibrationControls
    private lateinit var status: TextView
    private var overlay: View? = null
    private var host: WindowManager? = null
    private var resumed = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val color = Color.rgb(22,25,24)
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; text = getString(R.string.intelia_camera_calibration) }
        body.addView(status)
        fun button(resource: Int, run: () -> Unit) {
            body.addView(Button(this).apply { text = getString(resource); isAllCaps = false; setOnClickListener { run() } })
        }
        controls = PulseCalibrationControls(this, { preferences.state.value.pulseProfiles }) { showPreview() }
        body.addView(controls.view)
        button(R.string.intelia_pulse_save) {
            val key = controls.key; val profile = controls.current().copy(confirmed = true)
            if (key.isNotBlank() && profile.valid()) scope.launch {
                try {
                    preferences.update { it.copy(pulseProfiles = it.pulseProfiles + (key to profile)) }
                    finish()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { Toast.makeText(this@InteliaPulseCalibrationActivity, R.string.intelia_camera_calibration_unavailable, Toast.LENGTH_LONG).show() }
            }
        }
        button(R.string.intelia_close) { finish() }
        button(R.string.intelia_pulse_activation) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(color); addView(body) }
        setContentView(scroll)
        body.setOnApplyWindowInsetsListener { _, insets ->
            val padding = (16*resources.displayMetrics.density).toInt()
            @Suppress("DEPRECATION")
            body.setPadding(padding+insets.systemWindowInsetLeft,padding+insets.systemWindowInsetTop,
                padding+insets.systemWindowInsetRight,padding+insets.systemWindowInsetBottom)
            bind(); insets
        }
    }
    private fun bind() {
        val windows = getSystemService(WindowManager::class.java)
        val metrics = resources.displayMetrics
        val bounds = if(Build.VERSION.SDK_INT>=30) windows.currentWindowMetrics.bounds else Rect(0,0,metrics.widthPixels,metrics.heightPixels)
        val insets = window.decorView.rootWindowInsets ?: return
        val cutout = if(Build.VERSION.SDK_INT>=28) insets.displayCutout?.boundingRects?.minByOrNull { it.top } else null
        @Suppress("DEPRECATION") val rotation = windows.defaultDisplay.rotation
        @Suppress("DEPRECATION") val id = windows.defaultDisplay.displayId
        controls.bind(bounds.width(),bounds.height(),metrics.density,rotation,id,
            cutout?.exactCenterX()?.div(bounds.width()) ?: .5f,
            cutout?.exactCenterY()?.div(bounds.height()) ?: .03f)
        showPreview()
    }
    private fun calibration(active: Boolean) {
        InteliaIslandService.calibrationActive = active
        sendBroadcast(Intent("dev.intelia.launcher.REFRESH_PULSE").setPackage(packageName))
    }
    override fun onResume() { super.onResume(); resumed = true; calibration(true); window.decorView.post { bind() } }
    private fun showPreview() {
        removePreview()
        if(!resumed || controls.key.isBlank()) return
        val context = InteliaPulseAccessibilityService.instance
        if(context == null) { status.text = getString(R.string.intelia_pulse_calibration_permission); return }
        status.text = getString(R.string.intelia_camera_calibration)
        val manager = context.getSystemService(WindowManager::class.java)
        val metrics = resources.displayMetrics
        val screen = if(Build.VERSION.SDK_INT>=30) manager.currentWindowMetrics.bounds else Rect(0,0,metrics.widthPixels,metrics.heightPixels)
        val profile = controls.current()
        val rect = PulseGeometry.bounds(profile,screen.width(),screen.height(),metrics.density) ?: return
        val preview = object : View(context) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(176,209,192); style = Paint.Style.STROKE; strokeWidth = 2f }
            override fun onDraw(canvas: Canvas) {
                val pad = profile.paddingDp * metrics.density
                paint.alpha = 90; canvas.drawRect(1f,1f,width-1f,height-1f,paint)
                paint.alpha = 255; canvas.drawOval(pad,pad,width-pad,height-pad,paint)
                canvas.drawLine(width/2f-6,height/2f,width/2f+6,height/2f,paint)
                canvas.drawLine(width/2f,height/2f-6,width/2f,height/2f+6,paint)
            }
        }.apply { contentDescription = getString(R.string.intelia_camera_calibration) }
        val params = WindowManager.LayoutParams(rect.width,rect.height,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT; x = rect.left; y = rect.top
            if(Build.VERSION.SDK_INT>=30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if(Build.VERSION.SDK_INT>=28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            setTitle("Intelia camera calibration")
        }
        try { manager.addView(preview,params); overlay = preview; host = manager } catch(_: RuntimeException) { removePreview() }
    }
    private fun removePreview() { overlay?.let { runCatching { host?.removeViewImmediate(it) } }; overlay = null; host = null }
    override fun onPause() { resumed = false; removePreview(); calibration(false); super.onPause() }
    override fun onConfigurationChanged(config: android.content.res.Configuration) { super.onConfigurationChanged(config); removePreview(); window.decorView.post { bind() } }
    override fun onDestroy() { removePreview(); scope.cancel(); super.onDestroy() }
}
