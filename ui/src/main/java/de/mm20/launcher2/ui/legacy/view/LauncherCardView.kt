package de.mm20.launcher2.ui.legacy.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleOwner
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.roundToInt

/**
 * A card view implementation that solves the following issues of MaterialCardView:
 *  (1) Content clipping in transitions
 *  (2) Elevation overlay color
 */
open class LauncherCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr), KoinComponent {

    var backgroundOpacity: Int = (currentCardStyle.opacity * 255).roundToInt()
        set(value) {
            setCardBackgroundColor(cardBackgroundColor.defaultColor.let {
                ColorStateList.valueOf((it and 0xFFFFFF) or (value shl 24))
            })
            field = value
        }

    private var strokeOpacity: Int = if (currentCardStyle.borderWidth > 0) 0xFF else 0
        set(value) {
            setStrokeColor(strokeColorStateList?.defaultColor?.let {
                ColorStateList.valueOf((it and 0xFFFFFF) or (value shl 24))
            })
            field = value
        }

    private var overrideBackgroundOpacity = false

    init {
        strokeColor = cardBackgroundColor.defaultColor
        strokeWidth = (currentCardStyle.borderWidth * dp).roundToInt()
        radius = currentCardStyle.radius * dp

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.LauncherCardView,
            0, 0
        ).apply {

            try {
                if (hasValue(R.styleable.LauncherCardView_backgroundOpacity)) {
                    overrideBackgroundOpacity = true
                    backgroundOpacity = getInt(R.styleable.LauncherCardView_backgroundOpacity, 255)
                }
            } finally {
                recycle()
            }
        }
        strokeOpacity = if (backgroundOpacity == 0) 0 else 0xFF
        elevation = if (backgroundOpacity == 255) elevation else 0f
        cardElevation = elevation
    }

    private val dataStore: LauncherDataStore by inject()

    private var job: Job? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        job?.cancel()
        job = lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                dataStore.data.map { it.cards }.distinctUntilChanged().collectLatest {
                    currentCardStyle = it
                    if (!overrideBackgroundOpacity) {
                        backgroundOpacity = (it.opacity * 255).roundToInt()
                        strokeOpacity = if (backgroundOpacity == 0) 0 else 0xFF
                        elevation = if (backgroundOpacity == 255) elevation else 0f
                        cardElevation = elevation
                    }
                    strokeWidth = (it.borderWidth * dp).roundToInt()
                    radius = it.radius * dp
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job?.cancel()
    }

    companion object {
        internal var currentCardStyle = Settings.CardSettings.getDefaultInstance()
    }
}