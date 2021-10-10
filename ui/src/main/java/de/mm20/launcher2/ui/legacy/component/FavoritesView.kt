package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import kotlinx.android.synthetic.main.view_favorites.view.*
import org.koin.androidx.viewmodel.ext.android.viewModel

class FavoritesView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val favorites: LiveData<List<Searchable>>


    init {
        View.inflate(context, R.layout.view_favorites, this)
        val viewModel: FavoritesViewModel by (context as AppCompatActivity).viewModel()
        favorites = viewModel.getFavorites(context.resources.getInteger(R.integer.config_columnCount))
        favorites.observe(context as AppCompatActivity, Observer {
            visibility = if (it?.isEmpty() == true) View.GONE else View.VISIBLE
            favoritesGrid.submitItems(it)
        })
        
        layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
        favoritesCard.layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
    }
}