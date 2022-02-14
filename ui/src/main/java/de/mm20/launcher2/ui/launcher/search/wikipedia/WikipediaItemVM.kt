package de.mm20.launcher2.ui.launcher.search.wikipedia

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import de.mm20.launcher2.favorites.FavoritesRepository
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.ui.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WikipediaItemVM(
    private val wikipedia: Wikipedia
) : KoinComponent {
    private val favoritesRepository: FavoritesRepository by inject()

    val isPinned = favoritesRepository.isPinned(wikipedia)
    fun pin() {
        favoritesRepository.pinItem(wikipedia)
    }

    fun unpin() {
        favoritesRepository.unpinItem(wikipedia)
    }

    fun share(context: AppCompatActivity) {
        val text = HtmlCompat.fromHtml(wikipedia.text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(
            Intent.EXTRA_TEXT, "${wikipedia.label}\n\n" +
                    "${text.substring(0, 200)}…\n\n" +
                    "${wikipedia.wikipediaUrl}/wiki?curid=${wikipedia.id}"
        )
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}