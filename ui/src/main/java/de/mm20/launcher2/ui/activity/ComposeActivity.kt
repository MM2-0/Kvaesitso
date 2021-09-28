package de.mm20.launcher2.ui.activity

import android.appwidget.AppWidgetHost
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.insets.ProvideWindowInsets
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.preferences.dataStore
import de.mm20.launcher2.ui.LauncherTheme
import de.mm20.launcher2.ui.locals.LocalAppWidgetHost
import de.mm20.launcher2.ui.locals.LocalColorScheme
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.locals.LocalWindowSize
import de.mm20.launcher2.ui.screens.LauncherMainScreen
import de.mm20.launcher2.ui.screens.settings.*
import de.mm20.launcher2.ui.theme.colors.*
import de.mm20.launcher2.ui.theme.wallpaperColorsAsState
import kotlinx.coroutines.flow.map

class ComposeActivity : AppCompatActivity() {

    private lateinit var widgetHost: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //WindowCompat.setDecorFitsSystemWindows(window, false)
        widgetHost = AppWidgetHost(applicationContext, 0xacac)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val navController = rememberNavController()
            val context = LocalContext.current

            var windowSize by remember { mutableStateOf(Size(0f, 0f)) }
            findViewById<View>(android.R.id.content).doOnLayout {
                windowSize = Size(it.width.toFloat(), it.height.toFloat())
            }

            if (windowSize.height <= 0 || windowSize.width <= 0) return@setContent

            val colorSchemePreference by remember { dataStore.data.map { it.appearance.colorScheme } }
                .collectAsState(initial = Settings.AppearanceSettings.ColorScheme.Default)

            val colorScheme = when (colorSchemePreference) {
                Settings.AppearanceSettings.ColorScheme.MM20 -> MM20ColorScheme()
                Settings.AppearanceSettings.ColorScheme.Wallpaper -> {
                    if (isAtLeastApiLevel(Build.VERSION_CODES.O_MR1)) {
                        val wallpaperColors by wallpaperColorsAsState()
                        WallpaperColorScheme(wallpaperColors)
                    } else DefaultColorScheme()
                }
                Settings.AppearanceSettings.ColorScheme.MaterialYou -> {
                    if (isAtLeastApiLevel(Build.VERSION_CODES.S)) {
                        SystemColorScheme(context)
                    } else DefaultColorScheme()
                }
                Settings.AppearanceSettings.ColorScheme.BlackAndWhite -> BlackWhiteColorScheme()
                Settings.AppearanceSettings.ColorScheme.Custom -> {
                    val customColors by customColorsAsState()
                    CustomColorScheme(customColors)
                }
                else -> DefaultColorScheme()
            }



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
                            composable("settings/accounts") {
                                SettingsAccountScreen()
                            }
                            composable("settings/appearance") {
                                SettingsAppearanceScreen()
                            }
                            composable("settings/appearance/colors") {
                                SettingsColorsScreen()
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