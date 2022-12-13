package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.ktx.tryStartActivity

data class EmailAction(
    override val label: String,
    val email: String,
) : SearchAction {
    override val icon: SearchActionIcon = SearchActionIcon.Email
    override val iconColor: Int = 0
    override val customIcon: String? = null
    override fun start(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            type = "*/*"
            data = Uri.parse("mailto:$email")
        }
        context.tryStartActivity(intent)
    }
}