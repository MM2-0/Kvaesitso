package de.mm20.launcher2.searchactions.actions

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.ktx.tryStartActivity

data class WebsearchAction(
    override val label: String,
    val query: String,
): SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.WebSearch
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.tryStartActivity(intent)
    }
}