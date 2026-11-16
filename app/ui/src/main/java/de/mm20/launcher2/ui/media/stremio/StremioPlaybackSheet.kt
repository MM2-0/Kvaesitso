package de.mm20.launcher2.ui.media.stremio
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.stremio.StremioPlaybackSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.util.Locale
import de.mm20.launcher2.ui.downloads.torrentSettingsItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StremioPlaybackSheet(cacheOnly: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf<StremioPlaybackSettings?>(null) }
    LaunchedEffect(Unit) {
        settings = withContext(Dispatchers.IO) { StremioPlaybackSettings.load(context) }
        snapshotFlow { settings }.filterNotNull().collect { value ->
            withContext(Dispatchers.IO) { StremioPlaybackSettings.save(context, value) }
        }
    }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val languages = remember(locale) {
        Locale.getISOLanguages().map { it to Locale.forLanguageTag(it).getDisplayLanguage(locale) }.sortedBy { it.second }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text(stringResource(I18nR.string.settings), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
            settings?.let { s ->
                item {
                    ListPreference(title = stringResource(I18nR.string.stremio_http_cache), value = s.cacheGb,
                        items = StremioPlaybackSettings.cacheChoices.map { ListPreferenceItem("$it GB", it) },
                        onValueChanged = { settings = s.copy(cacheGb = it) })
                }
                item { StremioSharedCacheControls(s) { settings = it } }
                if (!cacheOnly) item {
                    val choices = listOf(ListPreferenceItem(stringResource(I18nR.string.dl_t_select_none), "")) +
                        languages.map { ListPreferenceItem(it.second, it.first) }
                    ListPreference(title = stringResource(I18nR.string.dl_category_audio) + " · " + stringResource(I18nR.string.au3_appearance_cs_primary), value = s.audio,
                        items = choices, onValueChanged = { settings = s.copy(audio = it) })
                    ListPreference(title = stringResource(I18nR.string.dl_category_audio) + " · " + stringResource(I18nR.string.au3_appearance_cs_secondary), value = s.audioFallback,
                        items = choices, onValueChanged = { settings = s.copy(audioFallback = it) })
                    ListPreference(title = stringResource(I18nR.string.hc_subtitles) + " · " + stringResource(I18nR.string.au3_appearance_cs_primary), value = s.subtitles,
                        items = choices, onValueChanged = { settings = s.copy(subtitles = it) })
                    ListPreference(title = stringResource(I18nR.string.hc_subtitles) + " · " + stringResource(I18nR.string.au3_appearance_cs_secondary), value = s.subtitleFallback,
                        items = choices, onValueChanged = { settings = s.copy(subtitleFallback = it) })
                    SwitchPreference(title = stringResource(I18nR.string.stremio_auto_next), value = s.autoNextEpisode,
                        onValueChanged = { settings = s.copy(autoNextEpisode = it) })
                    ListPreference(title = stringResource(I18nR.string.screenrec_resolution), value = s.resolution,
                        items = listOf(0, 480, 720, 1080, 1440, 2160).map {
                            ListPreferenceItem(if (it == 0) stringResource(I18nR.string.dl_t_select_none) else "${it}p", it)
                        }, onValueChanged = { settings = s.copy(resolution = it) })
                    ListPreference(title = stringResource(I18nR.string.au20_viewer_info_size), value = s.maxSizeGb,
                        items = listOf(0, 1, 2, 5, 10, 20, 50).map {
                            ListPreferenceItem(if (it == 0) stringResource(I18nR.string.dl_t_select_none) else "≤ $it GB", it)
                        }, onValueChanged = { settings = s.copy(maxSizeGb = it) })
                    SwitchPreference(title = stringResource(I18nR.string.stremio_hide_screeners), value = s.hideScreeners,
                        onValueChanged = { settings = s.copy(hideScreeners = it) })
                    SwitchPreference(title = stringResource(I18nR.string.au22_stremio_notify_episodes), value = s.notifyNewEpisodes,
                        onValueChanged = { settings = s.copy(notifyNewEpisodes = it) })

                }
            }
            if (!cacheOnly) torrentSettingsItems()
        }
    }
}
