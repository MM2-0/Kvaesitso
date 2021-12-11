package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import de.mm20.launcher2.favorites.FavoritesItem
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.ui.databinding.EditFavoritesRowBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EditFavoritesRow @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, val favoritesItem: FavoritesItem
) : LinearLayout(context, attrs, defStyleAttr), KoinComponent {

    val iconRepository: IconRepository by inject()

    private val binding = EditFavoritesRowBinding.inflate(LayoutInflater.from(context), this, false)

    init {
        binding.label.text = favoritesItem.searchable?.label
        lifecycleScope.launch {
            iconRepository.getIcon(favoritesItem.searchable!!, (48*dp).toInt()).collect{
                binding.icon.icon = it
            }
        }
    }

    fun getDragHandle(): View {
        return binding.dragHandle
    }
}