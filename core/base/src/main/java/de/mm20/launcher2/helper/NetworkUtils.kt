package de.mm20.launcher2.helper

import android.content.Context
import android.net.ConnectivityManager

object NetworkUtils {

    fun isOffline(context: Context, allowMobile: Boolean = true): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = connectivityManager.activeNetworkInfo ?: return true
        return when {
            info.type == ConnectivityManager.TYPE_WIFI -> false
            info.type == ConnectivityManager.TYPE_MOBILE && allowMobile -> false
            else -> true
        }
    }
}