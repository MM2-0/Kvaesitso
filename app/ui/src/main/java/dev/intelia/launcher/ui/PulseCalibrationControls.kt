package dev.intelia.launcher.ui

import android.app.Activity
import android.graphics.Color
import android.widget.*
import java.util.Locale

/** Edits camera geometry and touch padding separately. Only Done persists changes. */
internal class PulseCalibrationControls(private val activity: Activity,
    private val saved: () -> Map<String, PulseCameraProfile>, private val changed: () -> Unit) {
    val view = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    var key = ""; private set
    private var binding = ""
    private var width = 1
    private var height = 1
    private var density = 1f
    private var profile = PulseCameraProfile()
    private var original = profile
    private val widgets = mutableListOf<Pair<TextView, SeekBar>>()
    fun current() = profile
    fun bind(w: Int, h: Int, scale: Float, rotation: Int, displayId: Int, defaultX: Float, defaultY: Float) {
        val next = "$displayId:$rotation:$w:$h:$scale"
        if (binding == next) return
        binding = next; width = w; height = h; density = scale
        key = PulseGeometry.key(displayId, rotation, w, h)
        original = PulseCameraProfile(x = defaultX.coerceIn(0f,1f), y = defaultY.coerceIn(0f,1f))
        profile = saved()[key]?.takeIf { it.valid() } ?: original
        view.removeAllViews(); widgets.clear()
        view.addView(TextView(activity).apply {
            text = activity.getString(de.mm20.launcher2.ui.R.string.intelia_pulse_calibration_help)
            setTextColor(Color.WHITE); textSize = 14f
        })
        repeat(5) { index ->
            val label = TextView(activity).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(0, (12*scale).toInt(),0,0) }
            val slider = SeekBar(activity)
            view.addView(label); view.addView(slider); widgets += label to slider
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    profile = when(index) {
                        0 -> profile.copy(x = value.toFloat()/width)
                        1 -> profile.copy(y = value.toFloat()/height)
                        2 -> profile.copy(widthDp = 8f + value / 10f)
                        3 -> profile.copy(heightDp = 8f + value / 10f)
                        else -> profile.copy(paddingDp = value/10f)
                    }
                    update(); changed()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        val row = LinearLayout(activity)
        listOf("← 1 px" to (0 to -1), "1 px →" to (0 to 1), "↑ 1 px" to (1 to -1), "1 px ↓" to (1 to 1)).forEach { (text, delta) ->
            row.addView(Button(activity).apply {
                this.text = text; isAllCaps = false; textSize = 11f
                setOnClickListener {
                    profile = if (delta.first == 0) profile.copy(x = (profile.x + delta.second.toFloat()/width).coerceIn(0f,1f))
                        else profile.copy(y = (profile.y + delta.second.toFloat()/height).coerceIn(0f,1f))
                    update(); changed()
                }
            }, LinearLayout.LayoutParams(0,-2,1f))
        }
        view.addView(row)
        view.addView(Button(activity).apply {
            text = activity.getString(de.mm20.launcher2.ui.R.string.intelia_pulse_calibration_reset); isAllCaps = false
            setOnClickListener { profile = original; update(); changed() }
        })
        update()
    }
    private fun update() {
        val labels = listOf(de.mm20.launcher2.ui.R.string.intelia_pulse_center_x, de.mm20.launcher2.ui.R.string.intelia_pulse_center_y,
            de.mm20.launcher2.ui.R.string.intelia_pulse_camera_width, de.mm20.launcher2.ui.R.string.intelia_pulse_camera_height,
            de.mm20.launcher2.ui.R.string.intelia_pulse_touch_padding)
        val values = listOf(profile.x*width,profile.y*height,profile.widthDp,profile.heightDp,profile.paddingDp)
        val max = listOf(width,height,880,880,120)
        widgets.forEachIndexed { i,(text,slider) ->
            slider.max = max[i]
            slider.progress = when(i) { 0,1 -> kotlin.math.round(values[i]).toInt(); 2,3 -> ((values[i]-8)*10).toInt(); else -> (values[i]*10).toInt() }
            val name = activity.getString(labels[i]); slider.contentDescription = name
            text.text = "$name: ${String.format(Locale.getDefault(), "%.1f", values[i])} ${if(i<2) "px" else "dp"}"
        }
    }
}
