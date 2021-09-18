package de.mm20.launcher2.ui.legacy.widget

interface CompactView {

    fun setTranslucent(translucent: Boolean)
    fun update() {}

    var goToParent: (() -> Unit)?
}