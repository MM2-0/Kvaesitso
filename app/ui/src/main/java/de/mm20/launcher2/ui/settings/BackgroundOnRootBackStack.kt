package de.mm20.launcher2.ui.settings

/** Root Back keeps the document task and its saveable screen state alive in Recents. */
internal class BackgroundOnRootBackStack<T>(
    private val entries: MutableList<T>,
    private val background: () -> Unit,
) : AbstractMutableList<T>() {
    override val size: Int
        get() = entries.size

    override fun get(index: Int): T = entries[index]

    override fun add(index: Int, element: T) = entries.add(index, element)

    override fun set(index: Int, element: T): T = entries.set(index, element)

    override fun removeAt(index: Int): T {
        val entry = entries[index]
        if (entries.size == 1) {
            background()
            return entry
        }
        return entries.removeAt(index)
    }

    // Replacing a route is navigation, rather than a user Back action.
    override fun clear() = entries.clear()
}
