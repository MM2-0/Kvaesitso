package de.mm20.launcher2.searchactions.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.ktx.tryStartActivity
import java.net.URL

data class OpenUrlAction(
    override val label: String,
    val url: Uri,
    override val icon: SearchActionIcon = SearchActionIcon.Website,
    override val iconColor: Int = 0,
    override val customIcon: String? = null,
) : SearchAction {


    override fun start(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = url
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.tryStartActivity(intent)
    }
}