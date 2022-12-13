package de.mm20.launcher2.ui.launcher.search.website

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.search.data.Website
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM

class WebsiteItemVM(
    private val website: Website
) : SearchableItemVM(website) {

    fun share(context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(
            Intent.EXTRA_TEXT,
            "${website.label}\n\n${website.description}\n\n${website.url}"
        )
        shareIntent.type = "text/plain"
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}