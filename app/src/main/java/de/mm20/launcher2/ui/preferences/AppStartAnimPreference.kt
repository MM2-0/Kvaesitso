package de.mm20.launcher2.ui.preferences

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.postDelayed
import androidx.preference.Preference
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.airbnb.lottie.LottieAnimationView
import de.mm20.launcher2.R
import de.mm20.launcher2.preferences.AppStartAnimation
import de.mm20.launcher2.preferences.LauncherPreferences

class AppStartAnimPreference @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = R.attr.preferenceStyle) : Preference(context, attrs, defStyleAttr) {

    init {
        summary = getNameForAnimation(LauncherPreferences.instance.appStartAnim)

        setOnPreferenceClickListener {
            val anims = mutableListOf(
                    AppStartAnimation.M to R.raw.app_start_anim_m,
                    AppStartAnimation.SLIDE_BOTTOM to R.raw.app_start_anim_slide_bottom,
                    AppStartAnimation.FADE to R.raw.app_start_anim_fade
            )

            val dialog = MaterialDialog(context)
            val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            val list = LinearLayout(context)

            list.orientation = LinearLayout.VERTICAL

            anims.forEachIndexed { _, anim ->
                val view = View.inflate(context, R.layout.preference_start_anim_item, null)
                view.findViewById<LottieAnimationView>(R.id.icon).also { iconView ->
                    iconView.setAnimation(anim.second)
                    iconView.addAnimatorListener(object : Animator.AnimatorListener {
                        override fun onAnimationRepeat(animation: Animator?) {
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            iconView.postDelayed(500) {
                                iconView.frame = 0
                            }
                            iconView.postDelayed(1300) {
                                iconView.playAnimation()
                            }
                        }

                        override fun onAnimationCancel(animation: Animator?) {
                        }

                        override fun onAnimationStart(animation: Animator?) {
                        }

                    })
                    iconView.postDelayed(300) {
                        iconView.playAnimation()
                    }
                }
                view.findViewById<TextView>(R.id.label).also { labelView ->
                    labelView.setText(getNameForAnimation(anim.first))
                }
                view.layoutParams = layoutParams
                list.addView(view)
                view.setOnClickListener {
                    LauncherPreferences.instance.appStartAnim = anim.first
                    summary = getNameForAnimation(anim.first)
                    dialog.dismiss()
                }
            }

            dialog.customView(view = list, scrollable = true)
                    .title(R.string.preference_app_start_animation)
                    .negativeButton(android.R.string.cancel) {
                        dialog.cancel()
                    }
                    .show()
            true
        }
    }

    private fun getNameForAnimation(anim: AppStartAnimation): String {
        return when (anim) {
            AppStartAnimation.FADE -> context.getString(R.string.preference_app_start_animation_fade)
            AppStartAnimation.SLIDE_BOTTOM -> context.getString(R.string.preference_app_start_animation_slide_bottom)
            AppStartAnimation.M -> context.getString(R.string.preference_app_start_animation_m)
            else -> context.getString(R.string.preference_app_start_animation_default)
        }
    }
}