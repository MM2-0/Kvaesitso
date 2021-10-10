package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import de.mm20.launcher2.legacy.helper.ActivityStarter
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.wikipedia.WikipediaViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class WikipediaView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    val wikipedia: LiveData<Wikipedia?>

    init {
        View.inflate(context, R.layout.view_search_category_single_item, this)
        val websiteView = SearchableView(context, SearchableView.REPRESENTATION_LIST)
        val params = ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val card = findViewById<ViewGroup>(R.id.card)
        websiteView.layoutParams = params
        card.addView(websiteView)
        val viewModel: WikipediaViewModel by (context as AppCompatActivity).viewModel()
        wikipedia = viewModel.wikipedia
        wikipedia.observe(context as AppCompatActivity, Observer {
            visibility = if (it == null) View.GONE else View.VISIBLE
            card.setOnClickListener { _ ->
                ActivityStarter.start(context, websiteView, item = it)
            }
            websiteView.searchable = it
        })
    }
}