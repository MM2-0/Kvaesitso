package de.mm20.launcher2.ui.legacy.component

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.postDelayed
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.SearchStyles
import de.mm20.launcher2.search.SearchViewModel
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.view.LauncherCardView
import kotlinx.android.synthetic.main.view_search_bar.view.*
import org.koin.androidx.viewmodel.ext.android.viewModel

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

    init {
        View.inflate(context, R.layout.view_search_bar, this)
        overflowMenu.setImageDrawable(rightDrawable)
        searchEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val text = searchEdit.text.toString()
                onSearchQueryChanged?.invoke(searchEdit.text.toString())
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

        val viewModel = (context as AppCompatActivity).viewModel<SearchViewModel>().value

        viewModel.isSearching.observe(context, Observer {
            searchProgressBar.visibility = if (it) View.VISIBLE else View.GONE
        })

        overflowMenu.setOnClickListener {
            if (getSearchQuery().isEmpty()) onRightIconClick?.invoke(it)
            else (setSearchQuery(""))
        }

        postDelayed(1) {
            hide()
        }

        layoutTransition = ChangingLayoutTransition()
    }

    fun setRightIcon(iconRes: Int) {
        overflowMenu.setImageResource(iconRes)
    }

    var onSearchQueryChanged: ((String) -> Unit)? = null
    var onRightIconClick: ((View) -> Unit)? = null

    override fun setOnTouchListener(l: OnTouchListener?) {
        searchEdit.setOnTouchListener(l)
    }

    fun setSearchQuery(text: String) {
        searchEdit.setText(text)
    }

    fun getSearchQuery(): String {
        return searchEdit.text.toString()
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
        return webSearchView
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
                searchEdit.setShadowLayer(shadowR, 0f, shadowY, shadowC)
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
                            ObjectAnimator.ofArgb(searchEdit, "hintTextColor", searchEdit.hintTextColors.defaultColor, Color.WHITE),
                            ObjectAnimator.ofArgb(searchIcon, "colorFilter", iconColor, Color.WHITE),
                            ObjectAnimator.ofArgb(overflowMenu, "colorFilter", iconColor, Color.WHITE),
                            ObjectAnimator.ofFloat(searchIcon, "alpha", 1f),
                            ObjectAnimator.ofFloat(overflowMenu, "alpha", 1f),
                            ObjectAnimator.ofFloat(searchIcon, "elevation", cardElevation),
                            ObjectAnimator.ofFloat(overflowMenu, "elevation", cardElevation)
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
                searchEdit.setShadowLayer(0f, 0f, 0f, 0)
                AnimatorSet().apply {
                    duration = 200
                    playTogether(
                            ObjectAnimator.ofFloat(this@SearchBar, "translationZ", 0f).apply {
                                interpolator = AccelerateInterpolator(3f)
                            },
                            ObjectAnimator.ofInt(this@SearchBar, "backgroundOpacity", LauncherPreferences.instance.cardOpacity).apply {
                                interpolator = DecelerateInterpolator(3f)
                            },
                            ObjectAnimator.ofArgb(searchEdit, "hintTextColor", Color.WHITE, hint),
                            ObjectAnimator.ofArgb(searchIcon, "colorFilter", Color.WHITE, iconColor),
                            ObjectAnimator.ofArgb(overflowMenu, "colorFilter", Color.WHITE, iconColor),
                            ObjectAnimator.ofFloat(searchIcon, "alpha", iconAlpha),
                            ObjectAnimator.ofFloat(overflowMenu, "alpha", iconAlpha),
                            ObjectAnimator.ofFloat(searchIcon, "elevation", 0f),
                            ObjectAnimator.ofFloat(overflowMenu, "elevation", 0f)
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