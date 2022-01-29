package de.mm20.launcher2.ui.legacy.search

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.ui.legacy.helper.ActivityStarter
import de.mm20.launcher2.ui.legacy.helper.ActivityStarterCallback
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.transition.ChangingLayoutTransition
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SearchListView : LinearLayout {

    @ObsoleteCoroutinesApi
    private val updateActor = lifecycleScope
            .actor<List<Searchable>>(Dispatchers.Main, capacity = Channel.CONFLATED) {
                for (newItems in channel) {
                    val oldItems = currentItems
                    val diffResult = withContext(Dispatchers.Default) {
                        SearchDiffUtil.calculateDiff(oldItems, newItems)
                    }
                    currentItems = newItems
                    applyDiff(diffResult)
                }
            }

    @ObsoleteCoroutinesApi
    fun submitItems(items: List<Searchable>?) {
        if (items == null) return
        if (items.getOrNull(expandedItem)?.key != currentItems.getOrNull(expandedItem)?.key) expandedItem = -1
        lifecycleScope.launch {
            updateActor.send(items)
        }
    }

    private var expandedItem = -1
        set(value) {
            (getChildAt(field) as? SearchableView)?.back()
            requestLayout()
            field = value
        }

    private var currentItems = listOf<Searchable>()

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes) {
        layoutTransition = ChangingLayoutTransition()
        clipChildren = false
        orientation = VERTICAL
    }

    /**
     * Applies a diff queue. Enqueues to postponedDiffs if an activity is starting (leaving this view
     * in an unstable state) or if postponedDiffs is not empty and [force] is not set.
     */
    private fun applyDiff(diff: Queue<DiffAction>) {
        val representation = SearchableView.REPRESENTATION_LIST
        while (diff.isNotEmpty()) {
            val action = diff.poll() ?: continue
            if (action.action == DiffAction.ACTION_INSERT) {
                val searchableView = SearchableView.getView(context, action.item, representation)
                searchableView.representation = representation
                searchableView.searchable = action.item
                searchableView.onRepresentationChange = { _, newRepr ->
                    expandedItem = if (newRepr == SearchableView.REPRESENTATION_FULL) {
                        (getChildAt(expandedItem) as? SearchableView)?.back()
                        indexOfChild(searchableView)
                    } else -1
                }
                val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                searchableView.layoutParams = params
                addView(searchableView, action.position)
            }
            if (action.action == DiffAction.ACTION_DELETE) {
                removeViewAt(action.position)
            }
        }
    }
}
