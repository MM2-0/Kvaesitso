package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.ui.databinding.ViewFavoritesBinding
import de.mm20.launcher2.ui.launcher.search.SearchViewModel

class FavoritesView : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    private val binding = ViewFavoritesBinding.inflate(LayoutInflater.from(context), this, true)


    init {
        val viewModel: SearchViewModel by (context as AppCompatActivity).viewModels()
        val favorites = viewModel.favorites
        val hide = viewModel.hideFavorites
        favorites.observe(context as AppCompatActivity) {
            visibility = if (it?.isEmpty() == true || hide.value == true) View.GONE else View.VISIBLE
            binding.favoritesGrid.submitItems(it)
        }

        hide.observe(context as AppCompatActivity) {
            visibility = if(it == true || favorites.value?.isEmpty() == true) View.GONE else View.VISIBLE
        }
        
        layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
        binding.favoritesCard.layoutTransition = LayoutTransition().apply { enableTransitionType(LayoutTransition.CHANGING) }
    }
}