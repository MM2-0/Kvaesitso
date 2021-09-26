package de.mm20.launcher2.ui.activity

import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.insets.ProvideWindowInsets
import de.mm20.launcher2.ui.LauncherTheme
import de.mm20.launcher2.ui.locals.LocalAppWidgetHost
import de.mm20.launcher2.ui.locals.LocalColorScheme
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.screens.LauncherMainScreen
import de.mm20.launcher2.ui.screens.settings.SettingsAboutScreen
import de.mm20.launcher2.ui.screens.settings.SettingsBadgesScreen
import de.mm20.launcher2.ui.screens.settings.SettingsLicenseScreen
import de.mm20.launcher2.ui.screens.settings.SettingsMainScreen
import de.mm20.launcher2.ui.theme.WallpaperColors
import de.mm20.launcher2.ui.theme.colors.DefaultColorScheme
import de.mm20.launcher2.ui.theme.colors.WallpaperColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeActivity : AppCompatActivity() {

    private lateinit var widgetHost: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //WindowCompat.setDecorFitsSystemWindows(window, false)
        widgetHost = AppWidgetHost(applicationContext, 0xacac)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val navController = rememberNavController()

            var windowSize by remember { mutableStateOf(Size(0f, 0f)) }
            findViewById<View>(android.R.id.content).doOnLayout {
                windowSize = Size(it.width.toFloat(), it.height.toFloat())
            }

            var wallpaperColors by remember { mutableStateOf<WallpaperColors?>(null) }

            DisposableEffect(null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val wallpaperManager = WallpaperManager.getInstance(this@ComposeActivity)
                    val callback = { colors: android.app.WallpaperColors?, which: Int ->
                        if (colors != null && which or WallpaperManager.FLAG_SYSTEM != 0) {
                            wallpaperColors = WallpaperColors.fromPlatformType(colors)
                        }
                    }
                    wallpaperManager.addOnColorsChangedListener(
                        callback,
                        Handler(Looper.getMainLooper())
                    )

                    lifecycleScope.launch {
                        val colors = withContext(Dispatchers.IO) {
                            wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                        } ?: return@launch
                        wallpaperColors = WallpaperColors.fromPlatformType(colors)
                    }
                    return@DisposableEffect onDispose {
                        wallpaperManager.removeOnColorsChangedListener(callback)
                    }
                }
                onDispose {}
            }

            if (windowSize.height <= 0 || windowSize.width <= 0) return@setContent

            val colorScheme = wallpaperColors?.let { WallpaperColorScheme(it) } ?: DefaultColorScheme()



            ProvideWindowInsets {
                CompositionLocalProvider(
                    LocalAppWidgetHost provides widgetHost,
                    LocalWindowSize provides windowSize,
                    LocalColorScheme provides colorScheme,
                    LocalNavController provides navController
                ) {
                    LauncherTheme {
                        NavHost(navController = navController, startDestination = "home") {
                            composable("home") {
                                LauncherMainScreen()
                            }
                            composable("settings") {
                                SettingsMainScreen()
                            }
                            composable("settings/about") {
                                SettingsAboutScreen()
                            }
                            composable("settings/badges") {
                                SettingsBadgesScreen()
                            }
                            composable(
                                "settings/license?library={libraryName}",
                                arguments = listOf(navArgument("libraryName") {
                                    nullable = true
                                })
                            ) {
                                SettingsLicenseScreen(it.arguments?.getString("libraryName"))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }
}