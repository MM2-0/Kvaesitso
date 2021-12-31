package de.mm20.launcher2.ui.launcher.modals

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setMargins
import androidx.core.widget.NestedScrollView
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.ui.legacy.search.SearchGridView
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HiddenItemsView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : NestedScrollView(context, attrs) {
    private val viewModel: HiddenItemsVM by (context as AppCompatActivity).viewModels()

    init {
        clipChildren = false
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val hiddenItemsGrid = SearchGridView(context)
        hiddenItemsGrid.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins((8 * dp).toInt())
        }
        val hiddenItems = viewModel.hiddenItems
        hiddenItems.observe(context as AppCompatActivity) {
            hiddenItemsGrid.submitItems(it)
        }
        addView(hiddenItemsGrid)
    }

    private var job: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val job = Job()
        this.job = job
        lifecycleScope.launch(job) {
            viewModel.onActive()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job?.cancel()
    }
}