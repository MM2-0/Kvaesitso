package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.ktx.tryStartActivity

class CustomIntentAction(
    override val label: String,
    val query: String,
    private val queryKey: String,
    private val baseIntent: Intent,
    override val icon: SearchActionIcon = SearchActionIcon.Custom,
    override val iconColor: Int = 1,
    override val customIcon: String? = null,
) : SearchAction {
    override fun start(context: Context) {
        val intent = Intent(baseIntent).also {
            it.putExtra(queryKey, query)
        }
        context.tryStartActivity(intent)
    }
}