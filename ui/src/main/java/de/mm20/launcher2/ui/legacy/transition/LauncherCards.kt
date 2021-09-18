package de.mm20.launcher2.ui.legacy.transition

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.transition.Transition
import androidx.transition.TransitionValues
import de.mm20.launcher2.ui.legacy.view.LauncherCardView

class LauncherCards : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues) {
        val view = transitionValues.view
        if (view is LauncherCardView) {
            transitionValues.values[PROP_ELEVATION] = view.cardElevation
            transitionValues.values[PROP_BG_OPACITY] = view.backgroundOpacity
        }
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        val view = transitionValues.view
        if (view is LauncherCardView) {
            transitionValues.values[PROP_ELEVATION] = view.cardElevation
            transitionValues.values[PROP_BG_OPACITY] = view.backgroundOpacity
        }
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null) return null

        if (startValues.view !is LauncherCardView) return null
        val endView = endValues.view as? LauncherCardView
                ?: return null

        val startElevation = startValues.values[PROP_ELEVATION] as Float
        val endElevation = endValues.values[PROP_ELEVATION] as Float

        val startBgOpacity = startValues.values[PROP_BG_OPACITY] as Int
        val endBgOpacity = endValues.values[PROP_BG_OPACITY] as Int

        if(startBgOpacity < endBgOpacity) {
            return AnimatorSet().apply {
                playTogether(
                        ObjectAnimator.ofFloat(endView, "cardElevation", startElevation, startElevation, endElevation).apply {
                            interpolator = AccelerateInterpolator()
                        },
                        ObjectAnimator.ofInt(endView, "backgroundOpacity", startBgOpacity, endBgOpacity).apply {
                            interpolator = DecelerateInterpolator()
                        }
                )
            }
        }

        return AnimatorSet().apply {
            playTogether(
                    ObjectAnimator.ofFloat(endView, "cardElevation", startElevation, endElevation, endElevation).apply {
                        interpolator = DecelerateInterpolator()
                    },
                    ObjectAnimator.ofInt(endView, "backgroundOpacity", startBgOpacity, endBgOpacity).apply {
                        interpolator = AccelerateInterpolator()
                    }
            )
        }
    }

    companion object {
        private const val PROP_ELEVATION = "mm20:app:elevation"
        private const val PROP_BG_OPACITY = "mm20:app:bg_opacity"
    }
}