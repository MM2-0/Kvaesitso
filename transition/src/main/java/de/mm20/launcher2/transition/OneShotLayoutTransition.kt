package de.mm20.launcher2.transition

import android.animation.LayoutTransition
import android.view.View
import android.view.ViewGroup

class OneShotLayoutTransition(view: ViewGroup) : LayoutTransition() {
    init {
        enableTransitionType(CHANGING)
        addTransitionListener(object: TransitionListener{
            override fun startTransition(p0: LayoutTransition?, p1: ViewGroup?, p2: View?, p3: Int) {
            }

            override fun endTransition(p0: LayoutTransition?, p1: ViewGroup?, p2: View?, p3: Int) {
                removeTransitionListener(this)
                view.layoutTransition = null
            }

        })
    }
    companion object {
        fun run(view: ViewGroup) {
            view.layoutTransition = OneShotLayoutTransition(view)
        }
    }
}