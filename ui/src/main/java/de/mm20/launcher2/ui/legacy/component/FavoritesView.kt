package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import de.mm20.launcher2.favorites.FavoritesViewModel
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.ViewFavoritesBinding
import org.koin.androidx.viewmodel.ext.android.viewModel

class FavoritesView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val favorites: LiveData<List<Searchable>>

    private val binding = ViewFavoritesBinding.inflate(LayoutInflater.from(context), this, true)


    init {
        val viewModel: FavoritesViewModel by (context as AppCompatActivity).viewModel()
        favorites = viewModel.getFavorites(context.resources.getInteger(R.integer.config_columnCount))
        favorites.observe(context as AppCompatActivity, Observer {
            visibility = if (it?.isEmpty() == true) View.GONE else View.VISIBLE
            binding.favoritesGrid.submitItems(it)
        })
        
        layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
        binding.favoritesCard.layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
    }
}