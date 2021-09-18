package de.mm20.launcher2.transition

import android.animation.Animator
import android.animation.ObjectAnimator
import androidx.transition.Transition
import androidx.transition.TransitionValues
import android.view.ViewGroup
import androidx.cardview.widget.CardView

class CardCorners : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues) {
        if (transitionValues.view is CardView) {
            transitionValues.values[PROP_CARD_RADIUS] = (transitionValues.view as CardView).radius
        }
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        if (transitionValues.view is CardView) {
            transitionValues.values[PROP_CARD_RADIUS] = (transitionValues.view as CardView).radius
        }
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null) return null
        val startRadius = startValues.values[PROP_CARD_RADIUS] as? Float ?: return null
        val endRadius = endValues.values[PROP_CARD_RADIUS] as? Float ?: return null
        return ObjectAnimator.ofFloat(endValues.view as CardView, "radius", startRadius, endRadius)
    }

    companion object {
        private const val PROP_CARD_RADIUS = "mm20:app:cardCornerRadius"
    }
}