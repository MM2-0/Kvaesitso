package de.mm20.launcher2.transition

import android.animation.LayoutTransition

class ChangingLayoutTransition: LayoutTransition() {
    init {
        enableTransitionType(CHANGING)
    }
}