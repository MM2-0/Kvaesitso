package dev.intelia.launcher.ui

import android.app.Activity
import android.graphics.Color
import android.widget.*
import java.util.Locale

/** Debug calibration stored locally per display/aspect/rotation, not in launcher preferences. */
internal class PulseProbeCalibration(private val activity: Activity, private val changed: () -> Unit) {
    val view = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val preferences = activity.getSharedPreferences("intelia-pulse-calibration-debug", Activity.MODE_PRIVATE)
    private var key = ""
    private var binding = ""
    private var display = PulseAnchorGeometry.Bounds(0, 0, 0, 0)
    private var density = 1f
    private var original = emptyList<Float>()
    private var values = emptyList<Float>()
    private val widgets = mutableListOf<Pair<TextView, SeekBar>>()

    fun bind(screen: PulseAnchorGeometry.Bounds, detected: PulseAnchorGeometry.Bounds, scale: Float, rotation: Int, displayId: Int) {
        val nextBinding = "$displayId:$rotation:${screen.width}:${screen.height}:$scale"
        if (nextBinding == binding) return
        binding = nextBinding; display = screen; density = scale
        key = "v1:$displayId:$rotation:${screen.width * 1000L / screen.height}"
        original = listOf((detected.left + detected.width / 2f - screen.left) / screen.width,
            (detected.top + detected.height / 2f - screen.top) / screen.height, detected.width / scale, detected.height / scale)
        val saved = preferences.getString(key, null)?.split(';')?.mapNotNull { it.toFloatOrNull() }
        values = saved?.takeIf { it.size == 4 && resolve(it) != null } ?: original
        view.removeAllViews(); widgets.clear()
        view.addView(TextView(activity).apply {
            text = "Kalibrace kamery\nPosuň křížek na skutečný střed čočky. Posuvníky fungují i tam, kde Android dotyky blokuje. Změny se ukládají automaticky pro tento displej a orientaci."
            setTextColor(Color.WHITE); textSize = 14f
        })
        repeat(4) { index ->
            val label = TextView(activity).apply { setTextColor(Color.WHITE); textSize = 14f }
            val slider = SeekBar(activity)
            view.addView(label); view.addView(slider)
            widgets += label to slider
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val next = values.toMutableList()
                    next[index] = when (index) {
                        0 -> progress.toFloat() / display.width
                        1 -> progress.toFloat() / display.height
                        else -> (progress + minimum()) / density
                    }
                    apply(next)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        val nudge = LinearLayout(activity)
        listOf("← 1 px" to (0 to -1), "1 px →" to (0 to 1), "↑ 1 px" to (1 to -1), "1 px ↓" to (1 to 1)).forEach { (title, delta) ->
            nudge.addView(Button(activity).apply {
                text = title; textSize = 11f; isAllCaps = false
                setOnClickListener {
                    val next = values.toMutableList()
                    next[delta.first] = (next[delta.first] + delta.second.toFloat() /
                        (if (delta.first == 0) display.width else display.height)).coerceIn(0f, 1f)
                    apply(next)
                }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        view.addView(nudge)
        view.addView(Button(activity).apply {
            text = "Obnovit návrh podle Androidu"
            isAllCaps = false
            setOnClickListener { apply(original) }
        })
        updateWidgets()
    }

    fun bounds(): PulseAnchorGeometry.Bounds? = if (values.size == 4) resolve(values) else null
    private fun resolve(v: List<Float>) = PulseAnchorGeometry.calibrated(display, v[0], v[1], v[2], v[3], density)
    private fun minimum() = (48 * density).toInt()

    private fun apply(next: List<Float>) {
        val fitted = resolve(next) ?: return
        values = listOf((fitted.left + fitted.width / 2f - display.left) / display.width,
            (fitted.top + fitted.height / 2f - display.top) / display.height,
            maxOf(48f, fitted.width / density), maxOf(48f, fitted.height / density))
        preferences.edit().putString(key, values.joinToString(";")).apply()
        updateWidgets(); changed()
    }

    private fun updateWidgets() {
        val rect = bounds() ?: return
        val positions = listOf(rect.left + rect.width / 2 - display.left, rect.top + rect.height / 2 - display.top, rect.width, rect.height)
        val limits = listOf(display.width, display.height, display.width - minimum(), display.height / 3 - minimum())
        val names = listOf("Střed X", "Střed Y", "Šířka oblasti", "Výška oblasti")
        widgets.forEachIndexed { index, (label, slider) ->
            slider.max = limits[index].coerceAtLeast(0)
            slider.progress = if (index < 2) positions[index] else positions[index] - minimum()
            label.text = "${names[index]}: ${positions[index]} px · ${String.format(Locale.getDefault(), "%.1f", positions[index] / density)} dp"
            slider.contentDescription = names[index]
        }
    }
}
