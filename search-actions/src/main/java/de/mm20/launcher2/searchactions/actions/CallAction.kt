package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.searchactions.R

data class CallAction(
    override val label: String,
    val number: String,
): SearchAction {

    override val icon: SearchActionIcon = SearchActionIcon.Phone
    override val iconColor: Int = 0
    override val customIcon: String? = null

    override fun start(context: Context) {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        context.tryStartActivity(intent)
    }
}