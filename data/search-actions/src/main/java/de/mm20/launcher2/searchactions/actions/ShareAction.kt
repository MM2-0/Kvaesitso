package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import de.mm20.launcher2.ktx.tryStartActivity

data class ShareAction(
    override val label: String,
    val text: String,
) : SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Share
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            null
        )
        context.tryStartActivity(intent)
    }
}
