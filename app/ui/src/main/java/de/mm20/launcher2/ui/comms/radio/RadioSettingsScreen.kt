package de.mm20.launcher2.ui.comms.radio
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.radio.RadioSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

internal val LocalRadioUiSettings = staticCompositionLocalOf { RadioSettings.Values() }

/** Scoped to Radio: changing its appearance never changes the launcher or another virtual app. */
@Composable
internal fun RadioAppearance(values: RadioSettings.Values, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (values.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = when {
        values.theme == "system" -> MaterialTheme.colorScheme
        values.dynamicColors && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (values.translucent) colors.copy(
        background = colors.background.copy(alpha = 0f),
        surface = colors.surface.copy(alpha = 0f),
        surfaceContainerLow = colors.surfaceContainerLow,
        surfaceContainer = colors.surfaceContainer,
    ) else colors
    CompositionLocalProvider(LocalRadioUiSettings provides values, LocalContentColor provides scheme.onSurface) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RadioSettingsScreen(
    settings: RadioSettings,
    values: RadioSettings.Values,
    onBack: () -> Unit,
    onUpdateImages: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        TopAppBar(title = { de.mm20.launcher2.ui.component.TelosCyberAppBarTitle(stringResource(I18nR.string.hc_settings)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_back)) }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Preference(title = stringResource(I18nR.string.preference_theme), controls = {
                    var expanded by remember { mutableStateOf(false) }
                    val options = listOf("system" to I18nR.string.preference_theme_system, "light" to I18nR.string.preference_theme_light, "dark" to I18nR.string.preference_theme_dark)
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(stringResource(options.firstOrNull { it.first == values.theme }?.second ?: I18nR.string.preference_theme_system)) }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { (key, title) -> DropdownMenuItem(text = { Text(stringResource(title)) }, onClick = { settings.update { it.copy(theme = key) }; expanded = false }) }
                        }
                    }
                })
            }
            item { SwitchPreference(stringResource(I18nR.string.radio_dynamic_colors), value = values.dynamicColors, onValueChanged = { value -> settings.update { it.copy(dynamicColors = value) } }) }
            item { SwitchPreference(stringResource(I18nR.string.radio_translucent), value = values.translucent, onValueChanged = { value -> settings.update { it.copy(translucent = value) } }) }
            item { SwitchPreference(stringResource(I18nR.string.radio_controls_scroll), value = values.controlsScroll, onValueChanged = { value -> settings.update { it.copy(controlsScroll = value) } }) }
            item { SwitchPreference(stringResource(I18nR.string.radio_tap_plays), value = values.tapPlays, onValueChanged = { value -> settings.update { it.copy(tapPlays = value) } }) }
            item { SwitchPreference(stringResource(I18nR.string.radio_large_buffer), value = values.largeBuffer, onValueChanged = { value -> settings.update { it.copy(largeBuffer = value) } }) }
            item { SwitchPreference(stringResource(I18nR.string.radio_edit_metadata), value = values.editMetadata, onValueChanged = { value -> settings.update { it.copy(editMetadata = value) } }) }
            item { SwitchPreference(stringResource(I18nR.string.radio_edit_streams), value = values.editStreamUrls, onValueChanged = { value -> settings.update { it.copy(editStreamUrls = value) } }) }
            item { SwitchPreference(stringResource(I18nR.string.radio_auto_images), value = values.updateImages, onValueChanged = { value -> settings.update { it.copy(updateImages = value) } }) }
            item { Preference(stringResource(I18nR.string.radio_update_images), onClick = onUpdateImages) }
            item { HorizontalDivider() }
            item { Preference(stringResource(I18nR.string.hc_import_playlist_m3u_pls), onClick = onImport) }
            item { Preference(stringResource(I18nR.string.hc_export_playlist_m3u), onClick = onExport) }
            item { Preference(stringResource(I18nR.string.hc_back_up_collection), summary = stringResource(I18nR.string.radio_backup_images), onClick = onBackup) }
            item { Preference(stringResource(I18nR.string.hc_restore_backup), onClick = onRestore) }
        }
    }
}
