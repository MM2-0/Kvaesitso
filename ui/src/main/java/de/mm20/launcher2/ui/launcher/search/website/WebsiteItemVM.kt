package de.mm20.launcher2.ui.launcher.search.website

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.search.data.Website
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WebsiteItemVM(
    private val website: Website
): SearchableItemVM(website) {

    fun share(context: AppCompatActivity) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(
            Intent.EXTRA_TEXT,
            "${website.label}\n\n${website.description}\n\n${website.url}"
        )
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}