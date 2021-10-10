package de.mm20.launcher2.ui.legacy.view

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.ui.R
import org.koin.androidx.viewmodel.ext.android.viewModel
import kotlin.math.abs

class SwipeCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : MaterialCardView(context, attrs, defStyleAttr) {

    private val backdrop = FrameLayout(context)
    private val icon = ImageView(context)
    private val content = MaterialCardView(context)
    private val iconColor = ContextCompat.getColor(context, R.color.swipe_card_icon_color)
    private val iconColorActive =
        ContextCompat.getColor(context, R.color.swipe_card_icon_color_active)

    init {
        super.addView(backdrop)
        super.addView(icon, LayoutParams((40 * dp).toInt(), (24 * dp).toInt()))
        icon.setColorFilter(iconColor)
        super.addView(content)
        content.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        content.radius = radius
        content.transitionName = "SwipeCardView/content"
        radius = LauncherPreferences.instance.cardRadius * dp
        //content.setCardBackgroundColor(cardBackgroundColor)
        super.setCardBackgroundColor(
            ContextCompat.getColor(
                context,
                R.color.swipe_cardview_background
            )
        )
        content.layoutTransition = ChangingLayoutTransition()
        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        content.foreground = ta.getDrawable(0)
        ta.recycle()
    }

    var leftAction: SwipeAction? = null
    var rightAction: SwipeAction? = null


    private var leftThreshold = false
        set(value) {
            if (value == field) return
            if (value) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                leftAction?.color?.let { backdrop.setBackgroundColor(it) }
                AnimatorSet().also {
                    it.playTogether(
                        ViewAnimationUtils.createCircularReveal(
                            backdrop,
                            (28 * dp).toInt(),
                            (height * 0.5).toInt(),
                            0f,
                            width.toFloat()
                        )
                            .setDuration(300),
                        ObjectAnimator.ofArgb(icon, "colorFilter", iconColor, iconColorActive)
                            .apply {
                                duration = 150
                                startDelay = 100
                            },
                        ObjectAnimator.ofFloat(icon, "scaleX", 1.2f).apply {
                            duration = 200
                        },
                        ObjectAnimator.ofFloat(icon, "scaleY", 1.2f).apply {
                            duration = 200
                        }
                    )
                }.start()
            } else {
                AnimatorSet().also {
                    it.playTogether(
                        ViewAnimationUtils.createCircularReveal(
                            backdrop,
                            (28 * dp).toInt(),
                            (height * 0.5).toInt(),
                            width.toFloat(),
                            0f
                        ).apply {
                            doOnEnd {
                                if (!rightThreshold && !leftThreshold) backdrop.setBackgroundColor(0)
                            }
                            duration = 300
                        },
                        ObjectAnimator.ofArgb(icon, "colorFilter", iconColorActive, iconColor)
                            .apply {
                                duration = 150
                                startDelay = 100
                            },
                        ObjectAnimator.ofFloat(icon, "scaleX", 1f).apply {
                            duration = 200
                        },
                        ObjectAnimator.ofFloat(icon, "scaleY", 1f).apply {
                            duration = 200
                        }
                    )
                }.start()

            }
            field = value
        }

    private var rightThreshold = false
        set(value) {
            if (value == field) return
            if (value) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                rightAction?.color?.let { backdrop.setBackgroundColor(it) }
                AnimatorSet().also {
                    it.playTogether(
                        ViewAnimationUtils.createCircularReveal(
                            backdrop,
                            (width - 28 * dp).toInt(),
                            (height * 0.5).toInt(),
                            0f,
                            width.toFloat()
                        )
                            .setDuration(300),
                        ObjectAnimator.ofArgb(icon, "colorFilter", iconColor, iconColorActive)
                            .apply {
                                duration = 150
                                startDelay = 100
                            },
                        ObjectAnimator.ofFloat(icon, "scaleX", 1.2f).apply {
                            duration = 200
                        },
                        ObjectAnimator.ofFloat(icon, "scaleY", 1.2f).apply {
                            duration = 200
                        }
                    )
                }.start()
            } else {
                AnimatorSet().also {
                    it.playTogether(
                        ViewAnimationUtils.createCircularReveal(
                            backdrop,
                            (width - 28 * dp).toInt(),
                            (height * 0.5).toInt(),
                            width.toFloat(),
                            0f
                        ).apply {
                            doOnEnd {
                                if (!rightThreshold && !leftThreshold) backdrop.setBackgroundColor(0)
                            }
                            duration = 300
                        },
                        ObjectAnimator.ofArgb(icon, "colorFilter", iconColorActive, iconColor)
                            .apply {
                                duration = 150
                                startDelay = 100
                            },
                        ObjectAnimator.ofFloat(icon, "scaleX", 1f).apply {
                            duration = 200
                        },
                        ObjectAnimator.ofFloat(icon, "scaleY", 1f).apply {
                            duration = 200
                        }
                    )
                }.start()

            }
            field = value
        }

    private var swipeDirectionLeft: Boolean? = null
        set(value) {
            if (field == value) return
            backdrop.setBackgroundColor(0)
            if (value == true) {
                leftAction?.icon?.let { icon.setImageResource(it) }
                icon.setPadding((16 * dp).toInt(), 0, 0, 0)
                icon.layoutParams = (icon.layoutParams as LayoutParams).also {
                    it.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }
                icon.pivotX = 28 * dp
            } else if (value == false) {
                rightAction?.icon?.let { icon.setImageResource(it) }
                icon.setPadding(0, 0, (16 * dp).toInt(), 0)
                icon.layoutParams = (icon.layoutParams as LayoutParams).also {
                    it.gravity = Gravity.CENTER_VERTICAL or Gravity.END
                }
                icon.pivotX = 12 * dp
            }
            field = value
        }

    override fun setCardBackgroundColor(color: Int) {
        content.setCardBackgroundColor(color)
    }

    override fun setCardBackgroundColor(color: ColorStateList?) {
        content.setCardBackgroundColor(color)
    }

    override fun addView(child: View?) {
        content.addView(child)
    }

    override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
        content.addView(child, params)
    }

    override fun addView(child: View?, width: Int, height: Int) {
        content.addView(child, width, height)
    }

    override fun setRadius(radius: Float) {
        super.setRadius(radius)
        content?.radius = radius
    }

    override fun removeAllViews() {
        content.removeAllViews()
    }

    override fun removeAllViewsInLayout() {
        content.removeAllViewsInLayout()
    }

    override fun removeView(view: View?) {
        content.removeView(view)
    }

    override fun removeViewAt(index: Int) {
        content.removeViewAt(index)
    }

    override fun removeViewInLayout(view: View?) {
        content.removeViewInLayout(view)
    }

    override fun removeViews(start: Int, count: Int) {
        content.removeViews(start, count)
    }

    override fun removeViewsInLayout(start: Int, count: Int) {
        content.removeViewsInLayout(start, count)
    }


    private var downX = 0f
    private var downY = 0f
    private var isClick = false
    private var isLongClick = false
    private val longClickRunnable = Runnable {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        performLongClick()
        isClick = false
        isLongClick = true
        content.foreground?.state = intArrayOf(android.R.attr.state_enabled)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isClick = true
                isLongClick = false
                handler?.postDelayed(
                    longClickRunnable,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )
                content.foreground?.setHotspot(event.x, event.y)
                content.foreground?.state =
                    intArrayOf(android.R.attr.state_pressed, android.R.attr.state_enabled)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > abs(event.y - downY)) parent.requestDisallowInterceptTouchEvent(
                    true
                )
                if (isLongClick) return false
                swipeDirectionLeft = event.x - downX > 0
                if (isClick && abs(event.x - downX) < 4 * dp) {
                    return true
                }
                isClick = false
                handler?.removeCallbacks(longClickRunnable)
                content.translationX = event.x - downX
                leftThreshold = content.translationX > 0.5f * width
                rightThreshold = content.translationX < -0.5f * width
                content.foreground?.state = intArrayOf(android.R.attr.state_enabled)
                true
            }
            MotionEvent.ACTION_UP -> {
                when {
                    isClick -> {
                        performClick()
                        content.foreground?.state = intArrayOf(android.R.attr.state_enabled)
                        return false
                    }
                    leftThreshold -> {
                        if (leftAction?.action?.invoke() == true) {
                            content.animate().translationX(width.toFloat())
                                .setDuration(200)
                                .setInterpolator(AccelerateInterpolator())
                                .start()
                        } else {
                            content.animate().translationX(0f)
                                .setDuration(300)
                                .start()
                        }
                    }
                    rightThreshold -> {
                        if (rightAction?.action?.invoke() == true) {
                            content.animate().translationX(-width.toFloat())
                                .setDuration(200)
                                .setInterpolator(AccelerateInterpolator())
                                .start()
                        } else {
                            content.animate().translationX(0f)
                                .setDuration(300)
                                .start()
                        }
                    }
                    else -> {
                        content.animate().translationX(0f).setDuration(300).start()
                    }
                }
                true

            }
            MotionEvent.ACTION_CANCEL -> {
                content.animate().translationX(0f).setDuration(300).start()
                handler?.removeCallbacks(longClickRunnable)
                content.foreground?.state = intArrayOf(android.R.attr.state_enabled)
                false
            }
            MotionEvent.ACTION_OUTSIDE -> {
                handler?.removeCallbacks(longClickRunnable)
                content.foreground?.state = intArrayOf(android.R.attr.state_enabled)
                true
            }
            else -> false
        }
    }

    open class SwipeAction(
        @DrawableRes var icon: Int,
        var color: Int,
        /**
         * Action that is performed after a swipe.
         * returns true if the card should be animated out or false if it should be animated back.
         */
        var action: () -> Boolean
    )
}

class FavoriteSwipeAction(val context: Context, val searchable: Searchable) :
    SwipeCardView.SwipeAction(
        R.drawable.ic_star_solid,
        ContextCompat.getColor(context, R.color.amber),
        { false }
    ) {
    val viewModel: FavoritesViewModel by (context as AppCompatActivity).viewModel()

    private val pinned = viewModel.isPinned(searchable)


    init {
        pinned.observe(context as LifecycleOwner) {
            setPinned(it)
        }
    }

    private fun setPinned(pinned: Boolean) {
        if (pinned) {
            icon = R.drawable.ic_star_outline
            action = {
                viewModel.unpinItem(
                    searchable
                )
                false
            }
        } else {
            icon = R.drawable.ic_star_solid
            action = {
                viewModel.pinItem(
                    searchable
                )
                false
            }
        }
    }
}

class HideSwipeAction(val context: Context, val searchable: Searchable) : SwipeCardView.SwipeAction(
    R.drawable.ic_visibility_off,
    ContextCompat.getColor(context, R.color.blue),
    { false }
) {
    val viewModel: FavoritesViewModel by (context as AppCompatActivity).viewModel()
    private val hidden = viewModel.isHidden(searchable)

    init {
        hidden.observe(context as LifecycleOwner) {
            setHidden(it)
        }
    }

    private fun setHidden(hidden: Boolean) {
        if (hidden) {
            icon = R.drawable.ic_visibility
            action = {
                viewModel.unhideItem(
                    searchable
                )
                true
            }
        } else {
            icon = R.drawable.ic_visibility_off
            action = {
                viewModel.hideItem(
                    searchable
                )
                true
            }
        }
    }
}