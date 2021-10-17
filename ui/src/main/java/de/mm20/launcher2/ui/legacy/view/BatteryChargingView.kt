package de.mm20.launcher2.ui.legacy.view

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.mm20.launcher2.ktx.dp
import java.util.*

class BatteryChargingView : View, DefaultLifecycleObserver {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private var animating = false

    private val activity = context as AppCompatActivity

    init {
        activity.lifecycle.addObserver(this)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            update(intent, true)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        val intent = activity.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        start()
        intent?.let { update(it, true) }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        stop()
        try {
            activity.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
        }
    }

    private fun update(intent: Intent, retryOnZeroCurrent: Boolean = false) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (charging) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (current <= 0) {
                intensity = 5
                start()
                //Workaround for delayed current updates
                if (retryOnZeroCurrent) postDelayed({ update(intent) }, 1000)
                return
            }
            intensity = Math.round(current / 100000f).takeIf { it > 0 } ?: 1
            start()
        } else {
            intensity = 0
        }
    }

    var intensity = 0
        set(value) {
            if (field == 0 && value > 0) start()
            if (value == 0) stop()
            field = when {
                value > 100 -> 100
                value < 0 -> 0
                else -> value
            }

            for (i in field until bubbles.size) {
                bubbles.pop()
            }
            for (i in bubbles.size until field) {
                bubbles.push(FloatArray(6) { 0f })
            }
        }

    fun start() {
        if (animating || intensity == 0) return
        animating = true
        invalidate()
    }

    fun stop() {
        animating = false
    }


    /**
     * 0: Pos X
     * 1: Pos Y
     * 2: Delta X
     * 3: Delta Y
     * 4: Radius
     * 5: Lifetime left
     */
    private var bubbles = ArrayDeque<FloatArray>()

    private val paint = Paint()


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!animating) return
        for (b in bubbles) {
            if (b[5] <= 0f) {
                b[0] = (Math.random() * width).toFloat()
                b[1] = height.toFloat()
                b[2] = ((Math.random() - 0.5) * width / 120f).toFloat() * dp
                b[3] = -(Math.random() * height / 90f).toFloat() * dp
                b[4] = (Math.random() * 2 + 2).toFloat() * dp
                b[5] = (Math.random() * 80 + 40).toInt().toFloat()
            }
            paint.color = Color.argb((b[5] / 120f * 120).toInt(), 255, 255, 255)
            canvas.drawCircle(b[0], b[1], b[4], paint)

            b[0] += b[2]
            b[1] += b[3]
            b[5]--
        }
        postInvalidate()
    }
}