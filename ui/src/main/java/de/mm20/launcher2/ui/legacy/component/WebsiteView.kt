package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.Website
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.SearchViewModel
import de.mm20.launcher2.ui.legacy.searchable.SearchableView

class WebsiteView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleRes
    )

    private val website: LiveData<Website?>

    init {
        View.inflate(context, R.layout.view_search_category_single_item, this)
        val websiteView = SearchableView(context, SearchableView.REPRESENTATION_LIST)
        val params =
            ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val card = findViewById<ViewGroup>(R.id.card)
        websiteView.layoutParams = params
        card.addView(websiteView)
        val viewModel: SearchViewModel by (context as AppCompatActivity).viewModels()
        website = viewModel.websiteResult
        website.observe(context as AppCompatActivity, Observer {
            visibility = if (it == null) View.GONE else View.VISIBLE
            card.setOnClickListener { _ ->
                ActivityStarter.start(context, websiteView, item = it)
            }
            websiteView.searchable = it
        })
    }
}