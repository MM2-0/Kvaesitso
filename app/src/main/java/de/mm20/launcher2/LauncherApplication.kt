package de.mm20.launcher2

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import de.mm20.launcher2.debug.Debug
import de.mm20.launcher2.icons.IconRepository
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.preferences.Themes
import de.mm20.launcher2.ui.legacy.helper.WallpaperBlur
import kotlinx.coroutines.*
import java.text.Collator
import kotlin.coroutines.CoroutineContext

class LauncherApplication : Application(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + SupervisorJob()

    var blurredWallpaper: Bitmap? = null


    private val appReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            IconRepository.getInstance(this@LauncherApplication).requestIconPackListUpdate()
        }
    }


    override fun onCreate() {
        super.onCreate()
        Debug()
        instance = this
        LauncherPreferences.initialize(this)
        IconRepository.getInstance(this).requestIconPackListUpdate()

        registerReceiver(appReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        val theme = LauncherPreferences.instance.theme
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                Themes.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO // light
                Themes.DARK -> AppCompatDelegate.MODE_NIGHT_YES // dark, black
                Themes.AUTO -> AppCompatDelegate.MODE_NIGHT_AUTO // auto
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM //system
            }
        )
        WallpaperBlur.requestBlur(this)
        @Suppress("DEPRECATION") // We need to access the wallpaper directly to blur it
        registerReceiver(WallpaperReceiver(), IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    companion object {
        lateinit var instance: LauncherApplication

        val collator: Collator by lazy {
            Collator.getInstance().apply { strength = Collator.SECONDARY }
        }
    }

}

object PermissionRequests {
    const val CALENDAR = 309
    const val LOCATION = 410
    const val ALL = 666
}

class WallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WallpaperBlur.requestBlur(context)
    }

}