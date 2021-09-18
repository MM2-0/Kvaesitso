package de.mm20.launcher2.transition

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.ViewGroup
import androidx.transition.Transition
import androidx.transition.TransitionValues

class Elevation : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues) {
        transitionValues.values[PROP_ELEVATION] = transitionValues.view.elevation
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        transitionValues.values[PROP_ELEVATION] = transitionValues.view.elevation
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null) return null
        val startElevation = startValues.values[PROP_ELEVATION] as Float
        val endElevation = endValues.values[PROP_ELEVATION] as Float
        return ObjectAnimator.ofFloat(endValues.view, "elevation", startElevation, endElevation)
    }

    companion object {
        private const val PROP_ELEVATION = "mm20:app:elevation"
    }
}