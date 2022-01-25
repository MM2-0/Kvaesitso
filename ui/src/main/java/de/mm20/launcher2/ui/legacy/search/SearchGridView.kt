package de.mm20.launcher2.ui.legacy.search

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import de.mm20.launcher2.ktx.ceilToInt
import de.mm20.launcher2.ktx.lifecycleOwner
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.legacy.helper.ActivityStarterCallback
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.math.min

class SearchGridView : ViewGroup, ActivityStarterCallback, KoinComponent {
    override fun onResume() {
        while (postponedDiffs.isNotEmpty()) {
            postponedDiffs.poll()?.let { applyDiff(it, true) }
        }
    }

    val dataStore: LauncherDataStore by inject()

    var job: Job? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        job?.cancel()
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                dataStore.data.map { it.grid.columnCount }.distinctUntilChanged().collectLatest {
                    columnCount = it
                }
            }
        }
    }


    var columnCount: Int = 1
        set(value) {
            if (value > 0) {
                field = value
                requestLayout()
            }
            else throw IllegalArgumentException("columnCount must be positive (is $value)")
        }

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
        if (items.getOrNull(expandedItem)?.key != currentItems.getOrNull(expandedItem)?.key) expandedItem =
            -1
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

    private val postponedDiffs = ArrayDeque<Queue<DiffAction>>()


    /**
     * The height of each row. An absolute pixel size or [ROW_HEIGHT_AUTO]
     */
    var rowHeight: Int = ROW_HEIGHT_AUTO

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    ) {
        attrs?.let {
            val ta =
                context.theme.obtainStyledAttributes(it, R.styleable.SearchGridView, 0, defStyleRes)
            rowHeight = ta.getDimensionPixelSize(R.styleable.SearchGridView_rowHeight, -1)
            ta.recycle()
        }
        layoutTransition = LayoutTransition().also {
            it.enableTransitionType(LayoutTransition.CHANGING)
        }
        clipChildren = false
        ActivityStarter.registerCallback(this)
    }

    init {
        columnCount = runBlocking {
            dataStore.data.map { it.grid.columnCount }.first().takeIf { it > 0 } ?: 5
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthSpec = MeasureSpec.makeMeasureSpec(
            (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) / columnCount,
            MeasureSpec.EXACTLY
        )
        val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        val colWidth = 0
        children.forEachIndexed { i, v ->
            if (i == expandedItem) {
                v.measure(widthMeasureSpec, heightSpec)
            } else {
                v.measure(widthSpec, heightSpec)
            }
        }
        val rowHeight = if (rowHeight != ROW_HEIGHT_AUTO) rowHeight else {
            children.maxByOrNull {
                if (indexOfChild(it) == expandedItem) return@maxByOrNull 0
                it.measuredHeight
            }?.measuredHeight ?: 0
        }

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            MeasureSpec.AT_MOST -> min(
                colWidth * columnCount + paddingLeft + paddingRight,
                MeasureSpec.getSize(widthMeasureSpec)
            )
            MeasureSpec.UNSPECIFIED -> colWidth * columnCount + paddingLeft + paddingRight
            else -> colWidth * columnCount
        }


        val visibleChildCount = children.count { it.visibility != View.GONE }
        val rowCount = (visibleChildCount / columnCount.toFloat()).ceilToInt()
        var height = rowHeight * rowCount + (getChildAt(expandedItem)?.measuredHeight
            ?: 0) + paddingTop + paddingBottom

        if (expandedItem == childCount - 1 && (childCount % columnCount == 1) || expandedItem != -1 && columnCount == 1) {
            height -= rowHeight
        }

        setMeasuredDimension(
            View.resolveSize(width, widthMeasureSpec),
            View.resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rowHeight = if (rowHeight != ROW_HEIGHT_AUTO) rowHeight else {
            children.maxByOrNull {
                if (indexOfChild(it) == expandedItem) return@maxByOrNull 0
                it.measuredHeight
            }?.measuredHeight ?: 0
        }
        val width = measuredWidth
        val colWidth = (width - paddingLeft - paddingRight) / columnCount


        val visibleChildCount = children.count { it.visibility != View.GONE }
        val rowCount = (visibleChildCount / columnCount.toFloat()).ceilToInt()

        var x: Int
        var y = paddingTop
        var i = 0
        for (row in 0 until rowCount) {
            x = paddingLeft
            if (row * columnCount <= expandedItem && expandedItem < (row + 1) * columnCount) {
                if (row == 0) y = 0
                val child = getChildAt(expandedItem) ?: continue
                child.layout(0, y, x + child.measuredWidth, y + child.measuredHeight)
                y += child.measuredHeight
                if (columnCount == 1) {
                    i++
                    continue
                }
            }

            for (col in 0 until columnCount) {
                if (i == expandedItem) {
                    x += colWidth
                    i++
                    continue
                }
                val child = getChildAt(i) ?: break
                child.layout(x, y, x + colWidth, y + rowHeight)
                x += colWidth
                i++
            }
            y += rowHeight
        }
    }

    /**
     * Applies a diff queue. Enqueues to postponedDiffs if an activity is starting (leaving this view
     * in an unstable state) or if postponedDiffs is not empty and [force] is not set.
     */
    private fun applyDiff(diff: Queue<DiffAction>, force: Boolean = false) {
        if (ActivityStarter.isStarting() || (postponedDiffs.isNotEmpty() && !force)) {
            postponedDiffs.push(diff)
            return
        }
        val representation =
            if (columnCount == 1) SearchableView.REPRESENTATION_LIST else SearchableView.REPRESENTATION_GRID
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

    companion object {
        /**
         * Row height is automatically set to match the largest children
         */
        const val ROW_HEIGHT_AUTO = -1
    }
}

class QueueUpdateCallback : ListUpdateCallback {

    val operations = mutableListOf<DiffAction>()

    override fun onChanged(position: Int, count: Int, payload: Any?) {
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        operations += DiffAction(action = DiffAction.ACTION_DELETE, position = fromPosition)
        operations += DiffAction(action = DiffAction.ACTION_INSERT, position = toPosition)
    }

    override fun onInserted(position: Int, count: Int) {
        for (i in 0 until count) {
            operations += DiffAction(action = DiffAction.ACTION_INSERT, position = position + i)
        }
    }

    override fun onRemoved(position: Int, count: Int) {
        for (i in 1..count) {
            operations += DiffAction(
                action = DiffAction.ACTION_DELETE,
                position = position + (count - i)
            )
        }
    }

}

object SearchDiffUtil {
    fun calculateDiff(oldItems: List<Searchable>, newItems: List<Searchable>): Queue<DiffAction> {

        if (oldItems.isEmpty() && newItems.isEmpty()) return ArrayDeque()

        val callback = object : DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldItems[oldItemPosition].key == newItems[newItemPosition].key
            }

            override fun getOldListSize(): Int {
                return oldItems.size
            }

            override fun getNewListSize(): Int {
                return newItems.size
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return areItemsTheSame(oldItemPosition, newItemPosition)
            }
        }

        val diffResult = DiffUtil.calculateDiff(callback, false)

        val updateCallback = QueueUpdateCallback()

        diffResult.dispatchUpdatesTo(updateCallback)

        val result = ArrayDeque<DiffAction>()

        val mutableNewItems = mutableListOf<Searchable?>()
        mutableNewItems.addAll(newItems)

        for (i in updateCallback.operations.asReversed()) {
            if (i.action == DiffAction.ACTION_INSERT) {
                i.item = mutableNewItems[i.position]
                mutableNewItems.removeAt(i.position)
            } else {
                mutableNewItems.add(i.position, null)
            }
        }

        result.addAll(updateCallback.operations)

        return result
    }
}

data class DiffAction(val action: Int, val position: Int, var item: Searchable? = null) {
    companion object {
        const val ACTION_INSERT = 1
        const val ACTION_DELETE = -1
    }
}