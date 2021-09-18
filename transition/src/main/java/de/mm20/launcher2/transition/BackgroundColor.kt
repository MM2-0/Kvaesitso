package de.mm20.launcher2.transition

import android.animation.Animator
import android.animation.ObjectAnimator
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import androidx.core.graphics.alpha
import androidx.transition.Transition
import androidx.transition.TransitionValues

class BackgroundColor : Transition() {
    override fun captureStartValues(transitionValues: TransitionValues) {
        if(transitionValues.view is CardView) return
        transitionValues.values[PROP_BG_COLOR] = (transitionValues.view.background as? ColorDrawable)?.color ?: 0
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        if(transitionValues.view is CardView) return
        transitionValues.values[PROP_BG_COLOR] = (transitionValues.view.background as? ColorDrawable)?.color ?: 0
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null || endValues.view == null) return null
        var startColor = startValues.values[PROP_BG_COLOR] as? Int ?: return null
        var endColor = endValues.values[PROP_BG_COLOR] as? Int ?: return null
        //If end color is transparent, match it with stat color so it doesn't fade to black
        if(startColor.alpha == 0) startColor = endColor and 0x00FFFFFF
        if(endColor.alpha == 0) endColor = startColor and 0x00FFFFFF
        return ObjectAnimator.ofArgb(endValues.view, "backgroundColor", startColor, endColor)
    }

    companion object {
        private const val PROP_BG_COLOR = "mm20:app:backgroundColor"
    }
}