package de.mm20.launcher2.ui.legacy.component

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import de.mm20.launcher2.favorites.FavoritesItem
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.ktx.dp
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.ui.R
import kotlinx.android.synthetic.main.edit_favorites_row.view.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class EditFavoritesRow @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, val favoritesItem: FavoritesItem
) : LinearLayout(context, attrs, defStyleAttr) {
    init {
        View.inflate(context, R.layout.edit_favorites_row, this)
        label.text = favoritesItem.searchable?.label
        lifecycleScope.launch {
            IconRepository.getInstance(context).getIcon(favoritesItem.searchable!!, (48*dp).toInt()).collect{
                icon.icon = it
            }
        }
    }

    fun getDragHandle(): View {
        return dragHandle
    }
}