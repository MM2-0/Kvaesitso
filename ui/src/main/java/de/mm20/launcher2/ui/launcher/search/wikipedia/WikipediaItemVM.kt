package de.mm20.launcher2.ui.launcher.search.wikipedia

import android.content.Context
import android.content.Intent
import androidx.core.text.HtmlCompat
import de.mm20.launcher2.search.data.Wikipedia
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM

class WikipediaItemVM(
    private val wikipedia: Wikipedia
) : SearchableItemVM(wikipedia) {

    fun share(context: Context) {
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