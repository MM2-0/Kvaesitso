package de.mm20.launcher2.ui.launcher.modals

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.favorites.FavoritesItem
import de.mm20.launcher2.ktx.lifecycleScope
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.databinding.DialogEditFavoritesBinding
import de.mm20.launcher2.ui.databinding.EditFavoritesTitleBinding
import de.mm20.launcher2.ui.legacy.component.EditFavoritesRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditFavoritesView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    val viewModel: EditFavoritesVM by (context as AppCompatActivity).viewModels()

    private val binding = DialogEditFavoritesBinding.inflate(LayoutInflater.from(context), this)

    init {
        lifecycleScope.launch {
            initView()
        }
    }

    private lateinit var favorites: MutableList<FavoritesItem>

    suspend fun initView() {
        favorites = withContext(Dispatchers.IO) {
            viewModel.getFavorites().toMutableList()
        }
        binding.progressBar.visibility = View.GONE
        binding.itemList.addView(getLabel(R.string.edit_favorites_dialog_stage0))

        binding.itemList.setContainerScrollView(binding.scrollView)

        var stage = 0
        for (favorite in favorites) {
            if (favorite.pinPosition <= 1 && stage == 0) {
                getLabel(R.string.edit_favorites_dialog_stage1).let {
                    it.tag = "stage1"
                    binding.itemList.addDragView(it, it.getChildAt(1))
                }
                stage++
            }
            if (favorite.pinPosition == 0 && stage == 1) {
                getLabel(R.string.edit_favorites_dialog_stage2).let {
                    it.tag = "stage2"
                    binding.itemList.addDragView(it, it.getChildAt(1))
                }
                stage++
            }
            val view = EditFavoritesRow(context, favoritesItem = favorite)
            binding.itemList.addDragView(view, view.getDragHandle())
        }
        if (stage == 0) {
            getLabel(R.string.edit_favorites_dialog_stage1).let {
                it.tag = "stage1"
                binding.itemList.addDragView(it, it.getChildAt(1))
            }
            stage++
        }
        if (stage == 1) {
            getLabel(R.string.edit_favorites_dialog_stage2).let {
                it.tag = "stage2"
                binding.itemList.addDragView(it, it.getChildAt(1))
            }
        }

        binding.itemList.setOnViewSwapListener { firstView, firstPosition, secondView, secondPosition ->
            if (firstView is EditFavoritesRow && secondView is EditFavoritesRow) {
                val firstItem = firstView.favoritesItem
                val secondItem = secondView.favoritesItem
                val i = firstItem.pinPosition
                firstItem.pinPosition = secondItem.pinPosition
                secondItem.pinPosition = i
                return@setOnViewSwapListener
            }
            val fw = if (firstPosition > secondPosition) secondView else firstView
            val sw = if (firstPosition > secondPosition) firstView else secondView
            if (fw.tag == "stage1" && sw is EditFavoritesRow) {
                favorites.forEach {
                    if (it.pinPosition > 1) {
                        it.pinPosition++
                    }
                }
                sw.favoritesItem.pinPosition = 2
                return@setOnViewSwapListener
            }
            if (sw.tag == "stage1" && fw is EditFavoritesRow) {
                favorites.forEach {
                    if (it.pinPosition > 1) {
                        it.pinPosition--
                    }
                }
                return@setOnViewSwapListener
            }
            if (fw.tag == "stage2" && sw is EditFavoritesRow) {
                sw.favoritesItem.pinPosition = 1
                return@setOnViewSwapListener
            }
            if (sw.tag == "stage2" && fw is EditFavoritesRow) {
                fw.favoritesItem.pinPosition = 0
                return@setOnViewSwapListener
            }
        }
    }

    fun save() {
        viewModel.saveFavorites(favorites)
    }

    private fun getLabel(@StringRes label: Int): FrameLayout {
        return EditFavoritesTitleBinding.inflate(LayoutInflater.from(context)).also {
            it.text.setText(label)
        }.root

    }
}