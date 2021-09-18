package de.mm20.launcher2.ui.legacy.transition

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.ViewGroup
import androidx.transition.Transition
import androidx.transition.TransitionValues
import de.mm20.launcher2.ui.legacy.view.LauncherIconView

class LauncherIconViewTransition : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues) {
        if (transitionValues.view is LauncherIconView) {
            transitionValues.values[PROP_FG_SCALE] = (transitionValues.view as LauncherIconView).foregroundScale
            transitionValues.values[PROP_BG_SCALE] = (transitionValues.view as LauncherIconView).backgroundScale
        }
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        if (transitionValues.view is LauncherIconView) {
            transitionValues.values[PROP_FG_SCALE] = (transitionValues.view as LauncherIconView).foregroundScale
            transitionValues.values[PROP_BG_SCALE] = (transitionValues.view as LauncherIconView).backgroundScale
        }
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null) return null
        if(startValues.view !is LauncherIconView || endValues.view !is LauncherIconView) return null
        val startFg = startValues.values[PROP_FG_SCALE] as Float
        val endFg = endValues.values[PROP_FG_SCALE] as Float
        val startBg = startValues.values[PROP_BG_SCALE] as Float
        val endBg = endValues.values[PROP_BG_SCALE] as Float
        return AnimatorSet().apply {
            playTogether(
                    ObjectAnimator.ofFloat(startValues.view as LauncherIconView, "foregroundScale", startFg, endFg),
                    ObjectAnimator.ofFloat(startValues.view as LauncherIconView, "backgroundScale", startBg, endBg)

            )
        }
    }

    companion object {
        private const val PROP_FG_SCALE = "mm20:app:launcherIconFgScale"
        private const val PROP_BG_SCALE = "mm20:app:launcherIconBgScale"
        private const val PROP_SIZE = "mm20:app:launcherIconSize"
    }
}