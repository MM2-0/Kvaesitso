package de.mm20.launcher2.ui.common

sealed interface SelectorTarget {
    data object Favorites : SelectorTarget
    data object Latest : SelectorTarget
    data class CustomTag(val tagName: String) : SelectorTarget
}