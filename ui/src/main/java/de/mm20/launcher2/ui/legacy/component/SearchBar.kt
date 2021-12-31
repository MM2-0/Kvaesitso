package de.mm20.launcher2.ui.legacy.component

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.postDelayed
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.SearchStyles
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewSearchBarBinding
import de.mm20.launcher2.ui.legacy.view.LauncherCardView

class SearchBar @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = R.attr.materialCardViewStyle
) : LauncherCardView(context, attrs, defStyleAttr) {

    private var raised = false
    private var visible = true
    private var currentAnimator: Animator? = null

    private val rightDrawable = LottieDrawable().apply {
        composition = LottieCompositionFactory.fromRawResSync(context, R.raw.ic_menu_to_clear).value
        repeatMode = LottieDrawable.REVERSE
    }

    private val binding = ViewSearchBarBinding.inflate(LayoutInflater.from(context), this)

    init {
        binding.overflowMenu.setImageDrawable(rightDrawable)
        binding.searchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = binding.searchEdit.text.toString()
                onSearchQueryChanged?.invoke(binding.searchEdit.text.toString())
                if (text.isEmpty()) {
                    if (rightDrawable.frame > rightDrawable.minFrame.toInt()) {
                        rightDrawable.speed = -1f
                        rightDrawable.resumeAnimation()
                    }
                } else {
                    if (rightDrawable.frame < rightDrawable.maxFrame.toInt()) {
                        rightDrawable.speed = 1f
                        rightDrawable.resumeAnimation()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

        })

        binding.overflowMenu.setOnClickListener {
            if (getSearchQuery().isEmpty()) onRightIconClick?.invoke(it)
            else (setSearchQuery(""))
        }

        postDelayed(1) {
            hide()
        }

        layoutTransition = ChangingLayoutTransition()
    }

    fun setRightIcon(iconRes: Int) {
        binding.overflowMenu.setImageResource(iconRes)
    }

    var onSearchQueryChanged: ((String) -> Unit)? = null
    var onRightIconClick: ((View) -> Unit)? = null

    override fun setOnTouchListener(l: OnTouchListener?) {
        binding.searchEdit.setOnTouchListener(l)
    }

    fun setSearchQuery(text: String) {
        binding.searchEdit.setText(text)
    }

    fun getSearchQuery(): String {
        return binding.searchEdit.text.toString()
    }

    /**
     * Elevates the search bar to be higher than the other cards
     */
    fun raise() {
        if (raised) return
        currentAnimator?.takeIf { it.isStarted }?.end()
        currentAnimator = AnimatorSet().apply {
            duration = 200
            playTogether(
                    ObjectAnimator.ofFloat(this@SearchBar, "translationZ", elevation, 7 * dp).apply {
                        interpolator = AccelerateInterpolator(3f)
                    },
                    ObjectAnimator.ofInt(this@SearchBar, "backgroundOpacity", 0xFF).apply {
                        interpolator = DecelerateInterpolator(3f)
                    }
            )
        }
        currentAnimator?.start()
        raised = true
    }

    /**
     * Drops the search bar back down to the other cards niveau
     */

    fun drop() {
        if (!raised) return
        currentAnimator?.takeIf { it.isStarted }?.end()
        currentAnimator = AnimatorSet().apply {
            duration = 200
            playTogether(
                    ObjectAnimator.ofFloat(this@SearchBar, "translationZ", 0f).apply {
                        interpolator = DecelerateInterpolator(3f)
                    },
                    ObjectAnimator.ofInt(this@SearchBar, "backgroundOpacity", LauncherPreferences.instance.cardOpacity).apply {
                        interpolator = AccelerateInterpolator(3f)
                    }
            )
        }
        currentAnimator?.start()
        raised = false
    }

    fun show() {
        if (visible) return
        currentAnimator?.takeIf { it.isStarted }?.end()
        currentAnimator = getShowAnimator()
        currentAnimator?.start()
        visible = true
    }

    fun hide() {
        if (!visible) return
        currentAnimator?.takeIf { it.isStarted }?.end()
        currentAnimator = getHideAnimator()
        currentAnimator?.start()
        visible = false
    }

    fun getWebSearchView(): View {
        return binding.webSearchView
    }

    private fun getHideAnimator(): AnimatorSet {
        val searchStyle = LauncherPreferences.instance.searchStyle
        return when (searchStyle) {
            SearchStyles.NO_BG -> {
                val iconColor = ContextCompat.getColor(context, R.color.icon_color)
                val cardElevation = resources.getDimension(R.dimen.card_elevation)
                val shadowY = resources.getDimension(R.dimen.elevation_shadow_1dp_y)
                val shadowR = resources.getDimension(R.dimen.elevation_shadow_1dp_radius)
                val shadowC = Color.argb(66, 0, 0, 0)
                binding.searchEdit.setShadowLayer(shadowR, 0f, shadowY, shadowC)
                AnimatorSet().apply {
                    duration = 200
                    playTogether(
                            ObjectAnimator.ofInt(this@SearchBar, "backgroundOpacity", 0).apply {
                                interpolator = AccelerateInterpolator(3f)
                            },
                            ObjectAnimator.ofFloat(this@SearchBar, "translationZ", -elevation).apply {
                                interpolator = DecelerateInterpolator(3f)
                                duration = 150
                            },
                            ObjectAnimator.ofArgb(binding.searchEdit, "hintTextColor", binding.searchEdit.hintTextColors.defaultColor, Color.WHITE),
                            ObjectAnimator.ofArgb(binding.searchIcon, "colorFilter", iconColor, Color.WHITE),
                            ObjectAnimator.ofArgb(binding.overflowMenu, "colorFilter", iconColor, Color.WHITE),
                            ObjectAnimator.ofFloat(binding.searchIcon, "alpha", 1f),
                            ObjectAnimator.ofFloat(binding.overflowMenu, "alpha", 1f),
                            ObjectAnimator.ofFloat(binding.searchIcon, "elevation", cardElevation),
                            ObjectAnimator.ofFloat(binding.overflowMenu, "elevation", cardElevation)
                    )
                }
            }
            // Solid style
            SearchStyles.SOLID -> {
                AnimatorSet()
            }
            // Hidden style
            else -> {
                AnimatorSet().apply {
                    duration = 200
                    playTogether(
                            ObjectAnimator.ofFloat(this@SearchBar, "alpha", 0f)
                    )
                }
            }
        }
    }

    private fun getShowAnimator(): AnimatorSet? {
        return when (LauncherPreferences.instance.searchStyle) {
            // Transparent style
            SearchStyles.NO_BG -> {
                val hint = ContextCompat.getColor(context, R.color.text_color_primary_disabled)
                val iconAttrs = context.obtainStyledAttributes(R.style.LauncherTheme_IconStyle, intArrayOf(android.R.attr.alpha))
                val iconAlpha = iconAttrs.getFloat(0, 0f)
                iconAttrs.recycle()
                val iconColor = ContextCompat.getColor(context, R.color.icon_color)
                binding.searchEdit.setShadowLayer(0f, 0f, 0f, 0)
                AnimatorSet().apply {
                    duration = 200
                    playTogether(
                            ObjectAnimator.ofFloat(this@SearchBar, "translationZ", 0f).apply {
                                interpolator = AccelerateInterpolator(3f)
                            },
                            ObjectAnimator.ofInt(this@SearchBar, "backgroundOpacity", LauncherPreferences.instance.cardOpacity).apply {
                                interpolator = DecelerateInterpolator(3f)
                            },
                            ObjectAnimator.ofArgb(binding.searchEdit, "hintTextColor", Color.WHITE, hint),
                            ObjectAnimator.ofArgb(binding.searchIcon, "colorFilter", Color.WHITE, iconColor),
                            ObjectAnimator.ofArgb(binding.overflowMenu, "colorFilter", Color.WHITE, iconColor),
                            ObjectAnimator.ofFloat(binding.searchIcon, "alpha", iconAlpha),
                            ObjectAnimator.ofFloat(binding.overflowMenu, "alpha", iconAlpha),
                            ObjectAnimator.ofFloat(binding.searchIcon, "elevation", 0f),
                            ObjectAnimator.ofFloat(binding.overflowMenu, "elevation", 0f)
                    )
                }
            }
            // Solid style
            SearchStyles.SOLID -> {
                null
            }
            // Hidden style
            else -> {
                AnimatorSet().apply {
                    duration = 200
                    playTogether(
                            ObjectAnimator.ofFloat(this@SearchBar, "alpha", 1f)
                    )
                }
            }
        }
    }

}