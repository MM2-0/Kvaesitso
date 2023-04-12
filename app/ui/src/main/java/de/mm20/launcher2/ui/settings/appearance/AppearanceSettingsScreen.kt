package de.mm20.launcher2.ui.settings.appearance

import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltipBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.google.accompanist.pager.rememberPagerState
import de.mm20.launcher2.icons.StaticIconLayer
import de.mm20.launcher2.icons.StaticLauncherIcon
import de.mm20.launcher2.preferences.Settings.AppearanceSettings
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.ColorScheme
import de.mm20.launcher2.preferences.Settings.AppearanceSettings.Theme
import de.mm20.launcher2.preferences.Settings.IconSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarColors
import de.mm20.launcher2.preferences.Settings.SearchBarSettings.SearchBarStyle
import de.mm20.launcher2.preferences.Settings.SystemBarsSettings.SystemBarColors
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchBar
import de.mm20.launcher2.ui.component.SearchBarLevel
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.getShape
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.component.preferences.label
import de.mm20.launcher2.ui.component.preferences.value
import de.mm20.launcher2.ui.locals.LocalNavController
import de.mm20.launcher2.ui.theme.getTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AppearanceSettingsScreen() {
    val viewModel: AppearanceSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val navController = LocalNavController.current
    PreferenceScreen(title = stringResource(id = R.string.preference_screen_appearance)) {
        item {
            PreferenceCategory {
                val theme by viewModel.theme.observeAsState()
                ListPreference(
                    title = stringResource(id = R.string.preference_theme),
                    items = listOf(
                        stringResource(id = R.string.preference_theme_system) to Theme.System,
                        stringResource(id = R.string.preference_theme_light) to Theme.Light,
                        stringResource(id = R.string.preference_theme_dark) to Theme.Dark,
                    ),
                    value = theme,
                    onValueChanged = { newValue ->
                        if (newValue == null) return@ListPreference
                        viewModel.setTheme(newValue)
                    }
                )
                val colorScheme by viewModel.colorScheme.observeAsState()
                Preference(
                    title = stringResource(id = R.string.preference_screen_colors),
                    summary = when (colorScheme) {
                        ColorScheme.Default -> stringResource(R.string.preference_colors_default)
                        ColorScheme.BlackAndWhite -> stringResource(R.string.preference_colors_bw)
                        ColorScheme.Custom -> stringResource(R.string.preference_colors_custom)
                        else -> null
                    },
                    onClick = {
                        navController?.navigate("settings/appearance/colorscheme")
                    }
                )
                val font by viewModel.font.observeAsState()
                ListPreference(
                    title = stringResource(R.string.preference_font),
                    items = listOf(
                        "Outfit" to AppearanceSettings.Font.Outfit,
                        stringResource(R.string.preference_font_system) to AppearanceSettings.Font.SystemDefault,
                    ),
                    value = font,
                    onValueChanged = {
                        if (it != null) viewModel.setFont(it)
                    },
                    itemLabel = {
                        val typography = remember(it.value) {
                            getTypography(context, it.value)
                        }
                        Text(it.first, style = typography.titleMedium)
                    }
                )

                Preference(
                    title = stringResource(R.string.preference_cards),
                    summary = stringResource(R.string.preference_cards_summary),
                    onClick = {
                        navController?.navigate("settings/appearance/cards")
                    }
                )
            }
        }
    }
}