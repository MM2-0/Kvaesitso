package de.mm20.launcher2.searchable

enum class VisibilityLevel(val value: Int) {
    /**
     * Default visibility:
     * - apps are shown in app drawer
     * - calendar events are shown in calendar widget
     * - everything else is shown in search results
     * - items can appear in frequently used section
     */
    Default(0),

    /**
     * Search only visibility:
     * - items are only shown in search results
     * - items can appear in frequently used section
     * - items are not shown in app drawer or calendar widget
     */
    SearchOnly(1),

    /**
     * Hidden visibility:
     * - items are not shown in search results
     * - items are not shown in app drawer or calendar widget
     * - items are not shown in frequently used section
     */
    Hidden(2);

    companion object {
        internal fun fromInt(value: Int) = entries.firstOrNull() { it.value == value } ?: Default
    }
}