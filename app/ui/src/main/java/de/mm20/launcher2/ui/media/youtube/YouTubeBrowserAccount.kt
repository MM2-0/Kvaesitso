package de.mm20.launcher2.ui.media.youtube

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import de.mm20.launcher2.ktx.tryStartActivity

/** Opens both websites in the user's web browser or custom tab. Excludes Play Store redirects. */
internal object YouTubeBrowserAccount {
    fun open(context: Context, music: Boolean): Boolean = try {
        val url = if (music) "https://music.youtube.com/" else "https://www.youtube.com/"
        val uri = Uri.parse(url)

        val pm = context.packageManager
        val customTabProvider = CustomTabsClient.getPackageName(context, null)
        val targetPackage = if (customTabProvider != null && customTabProvider != "com.android.vending" && customTabProvider != "com.google.android.finsky") {
            customTabProvider
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).addCategory(Intent.CATEGORY_BROWSABLE)
            val resolveInfo = pm.resolveActivity(browserIntent, 0)
            val pkg = resolveInfo?.activityInfo?.packageName
            if (pkg != null && pkg != "com.android.vending" && pkg != "com.google.android.finsky") pkg else null
        }

        // An implicit fallback would return to Telos itself after it becomes the link handler.
        check(!targetPackage.isNullOrBlank() && targetPackage != context.packageName) { "Browser unavailable" }
        val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
        if (targetPackage != null) {
            tab.intent.setPackage(targetPackage)
        }
        tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.tryStartActivity(tab.intent.setData(uri))
    } catch (_: Exception) {
        false
    }
}
