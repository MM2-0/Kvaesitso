package de.mm20.launcher2.ui.legacy.widget

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import android.widget.TextClock
import androidx.core.content.ContextCompat
import androidx.core.view.postDelayed
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewDateTimeBinding
import de.mm20.launcher2.ui.legacy.view.LauncherCardView
import java.util.*

class SmartWidget : LauncherCardView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    private val binding = ViewDateTimeBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        clipToPadding = false
        clipChildren = false
        layoutTransition = LayoutTransition()

        binding.dateTimeTimeView.format12Hour = "hh:mm"
        binding.dateTimeTimeView.format24Hour = "HH:mm"


        binding.dateTimeTimeView.setOnClickListener {
            try {
                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                ActivityStarter.start(context, this, intent = intent)
            } catch (_: ActivityNotFoundException) {
                // Ignore
            }
        }
        postDelayed(1) {
            translucent = true
        }
    }

    private val translucentDisableRunnable = Runnable@{
        if (translucent) return@Runnable
        binding.dateTimeTimeView.setShadowLayer(0f, 0f, 0f, 0)
        val textColor = ContextCompat.getColorStateList(context, R.color.text_color_primary)
        val dividerColor = ContextCompat.getColor(context, R.color.color_divider)
        binding.dateTimeTimeView.setTextColor(textColor)
        binding.bottomPadding.setBackgroundColor(dividerColor)
        binding.bottomPadding.elevation = 0f
        compactView?.setTranslucent(false)
    }

    private val translucentEnableRunnable = Runnable@{
        if (!translucent) return@Runnable
        val textColor = Color.argb(255, 255, 255, 255)
        val shadowY = resources.getDimension(R.dimen.elevation_shadow_1dp_y)
        val shadowR = resources.getDimension(R.dimen.elevation_shadow_1dp_radius)
        val shadowC = Color.argb(66, 0, 0, 0)
        binding.dateTimeTimeView.setTextColor(textColor)
        binding.dateTimeTimeView.setShadowLayer(shadowR, 0f, shadowY, shadowC)
        binding.bottomPadding.setBackgroundColor(textColor)
        binding.bottomPadding.elevation = 1f
        compactView?.setTranslucent(true)
    }

    var translucent: Boolean = false
        set(value) {
            if (value == field) return
            if (value) {
                removeCallbacks(translucentDisableRunnable)
                postDelayed(translucentEnableRunnable, 100)
                AnimatorSet().apply {
                    duration = 200
                    playTogether(
                        ObjectAnimator.ofInt(this@SmartWidget, "backgroundOpacity", 0).apply {
                            interpolator = AccelerateInterpolator(3f)
                        },
                        ObjectAnimator.ofFloat(this@SmartWidget, "translationZ", -elevation).apply {
                            interpolator = DecelerateInterpolator(3f)
                        }
                    )
                }.start()

            } else {
                removeCallbacks(translucentEnableRunnable)
                postDelayed(translucentDisableRunnable, 70)
                AnimatorSet().apply {
                    duration = 200
                    playTogether(
                        ObjectAnimator.ofFloat(this@SmartWidget, "translationZ", 0f).apply {
                            interpolator = AccelerateInterpolator(3f)
                        },
                        ObjectAnimator.ofInt(
                            this@SmartWidget,
                            "backgroundOpacity",
                            LauncherPreferences.instance.cardOpacity
                        ).apply {
                            interpolator = DecelerateInterpolator(3f)
                        }
                    )
                }.start()

            }
            field = value
        }


    var compactView: CompactView? = getDefaultCompactView()
        set(value) {
            binding.smartWidgetContainer.removeView(field as? View)
            if (value == null) {
                field = getDefaultCompactView()
            } else {
                field = value
            }
            (field as? View)?.let {
                it.layoutParams = getCompactViewLayoutParams()
                binding.smartWidgetContainer.addView(it)
            }
            field?.setTranslucent(translucent)
        }

    private fun getDefaultCompactView(): CompactView {
        return DateCompactView(context)
    }

    private fun getCompactViewLayoutParams(): RelativeLayout.LayoutParams {
        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE)
        params.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
        params.addRule(RelativeLayout.START_OF, R.id.smartWidgetDivider)
        params.marginStart = (16 * dp).toInt()
        params.marginEnd = (16 * dp).toInt()
        return params
    }
}

class DateCompactView : TextClock, CompactView {
    override fun setTranslucent(translucent: Boolean) {
        if (translucent) {
            val textColor = Color.argb(255, 255, 255, 255)
            val shadowY = resources.getDimension(R.dimen.elevation_shadow_1dp_y)
            val shadowR = resources.getDimension(R.dimen.elevation_shadow_1dp_radius)
            val shadowC = Color.argb(66, 0, 0, 0)
            setShadowLayer(shadowR, 0f, shadowY, shadowC)
            setTextColor(textColor)
        } else {
            val textColor = ContextCompat.getColorStateList(context, R.color.text_color_primary)
            setShadowLayer(0f, 0f, 0f, 0)
            setTextColor(textColor)
        }
    }

    override var goToParent: (() -> Unit)? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    init {
        isClickable = true
        elevation = 2 * dp
        isFocusable = true
        setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
        textSize = 20f
        setTextColor(ContextCompat.getColorStateList(context, R.color.text_color_primary))
        setOnClickListener {
            val startMillis = System.currentTimeMillis()
            val builder = CalendarContract.CONTENT_URI.buildUpon()
            builder.appendPath("time")
            ContentUris.appendId(builder, startMillis)
            val intent = Intent(Intent.ACTION_VIEW)
                .setData(builder.build())
            ActivityStarter.start(context, this, intent = intent)
        }
        val dayFormat = DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMdyyyy")
        val dayOfWeekFormat = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE")
        val dateFormat =
            context.getString(R.string.date_format_clock_widget, dayOfWeekFormat, dayFormat)

        format12Hour = dateFormat
        format24Hour = dateFormat

        val outValue = TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            outValue,
            true
        )
        foreground = context.getDrawable(outValue.resourceId)
    }
}

