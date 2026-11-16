// === TELOS_PENDING_REVIEW_START: radio_browser_ktor ===
package de.mm20.launcher2.ui.comms.radio

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.comms.RadioViewModel
import de.mm20.launcher2.ui.component.LauncherCard
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.component.virtualAppPopup
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date

@Serializable
data object RadioDashboardRoute : NavKey

@Composable
fun RadioDashboardScreen() {
    val context = LocalContext.current
    val settings = remember(context.applicationContext) { de.mm20.launcher2.comms.radio.RadioSettings(context.applicationContext) }
    val values by settings.values.collectAsStateWithLifecycle()
    RadioAppearance(values) { RadioDashboardContent(settings, values) }
}

@Composable
private fun RadioDashboardContent(
    settings: de.mm20.launcher2.comms.radio.RadioSettings,
    values: de.mm20.launcher2.comms.radio.RadioSettings.Values,
) {
    val viewModel: RadioDashboardScreenVM = viewModel()
    val playerViewModel: RadioViewModel = viewModel()
    val context = LocalContext.current
    val backStack = de.mm20.launcher2.ui.locals.LocalBackStack.current
    // the player connects to the radio service here too, not only in the mini player
    LaunchedEffect(Unit) { playerViewModel.initialize(context) }

    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val sleepEndsAt by playerViewModel.sleepEndsAt.collectAsStateWithLifecycle()
    val recordingState by playerViewModel.recordingState.collectAsStateWithLifecycle()
    val isRecording = recordingState is de.mm20.launcher2.comms.radio.RadioRecorder.State.Recording
    val isRadioPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var collectionQuery by rememberSaveable { mutableStateOf("") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(searchExpanded) {
        if (!searchExpanded) { keyboard?.hide(); focus.clearFocus() }
    }
    var collectionSearchExpanded by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(selectedTabIndex != 0 && !searchExpanded) { selectedTabIndex = 0 }
    androidx.activity.compose.BackHandler(searchExpanded) {
        searchExpanded = false
        viewModel.updateSearchQuery("")
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    var showRecordings by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<RadioStation?>(null) }

    LaunchedEffect(message) {
        message?.let {
            val text = if (it.arg != null) context.getString(it.resId, it.arg) else context.getString(it.resId)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPlaylist(context, uri)
    }
    val exportM3uLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri -> if (uri != null) viewModel.exportM3u(context, uri) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) viewModel.exportBackup(context, uri) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    if (showSettings) {
        RadioSettingsScreen(settings, values, onBack = { showSettings = false },
            onUpdateImages = { viewModel.refreshStationImages(context) },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onExport = { exportM3uLauncher.launch("telos-radio.m3u") },
            onBackup = { backupLauncher.launch("telos-radio-backup.zip") },
            onRestore = { restoreLauncher.launch(arrayOf("*/*")) })
        return
    }
    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            containerColor = virtualAppPopup(),
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_restore_backup))  } },
            text = { Text(stringResource(I18nR.string.radio_restore_replace_summary)) },
            confirmButton = { TextButton(onClick = { viewModel.restoreBackup(context, uri, replace = true); pendingRestore = null }) { Text(stringResource(I18nR.string.hc_restore_backup)) } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(I18nR.string.hc_cancel)) } })
    }

    de.mm20.launcher2.ui.media.MediaFrame(stringResource(I18nR.string.hc_radio), askNotifications = true, guardKey = "telos_radio_app://radio", actions = {
        de.mm20.launcher2.ui.media.MediaSearchAction(searchExpanded, searchQuery.isNotBlank()) {
            searchExpanded = !searchExpanded
            if (!searchExpanded) viewModel.updateSearchQuery("")
        }
        IconButton(onClick = { showAdd = true }) {
            Icon(painterResource(BaseR.drawable.add_24px), contentDescription = stringResource(I18nR.string.hc_add_station))
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.tsm_search_my_stations)) },
                    leadingIcon = { Icon(painterResource(BaseR.drawable.search_24px), null) },
                    onClick = { menuOpen = false; searchExpanded = false; selectedTabIndex = 0; collectionSearchExpanded = true })
                DropdownMenuItem(
                    text = { Text(stringResource(if (selectedTabIndex == 0) I18nR.string.au_radio_tab_history else I18nR.string.au_radio_tab_collection)) },
                    leadingIcon = { Icon(painterResource(if (selectedTabIndex == 0) BaseR.drawable.schedule_24px else BaseR.drawable.radio_24px), null) },
                    onClick = { menuOpen = false; searchExpanded = false; selectedTabIndex = if (selectedTabIndex == 0) 2 else 0 },
                )
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_settings)) },
                    leadingIcon = { Icon(painterResource(BaseR.drawable.settings_24px), null) },
                    onClick = { menuOpen = false; showSettings = true })
                DropdownMenuItem(
                    text = { Text(stringResource(if (isRecording) I18nR.string.au2_radio2_stop_recording else I18nR.string.au2_radio2_record)) },
                    leadingIcon = { Icon(painterResource(BaseR.drawable.radio_button_checked_24px), null,
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        menuOpen = false
                        playerViewModel.toggleRecording(context)?.let { Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show() }
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.hc_sleep_timer)) },
                    leadingIcon = { Icon(painterResource(BaseR.drawable.timer_24px), null,
                        tint = if (sleepEndsAt > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { menuOpen = false; showSleep = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.au2_radio2_recordings)) },
                    onClick = {
                        menuOpen = false
                        showRecordings = true
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.hc_scrobbling)) },
                    onClick = {
                        menuOpen = false
                        backStack.add(de.mm20.launcher2.ui.settings.comms.ScrobbleSettingsRoute)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.hc_import_playlist_m3u_pls)) },
                    onClick = {
                        menuOpen = false
                        importLauncher.launch(arrayOf("*/*"))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.hc_export_playlist_m3u)) },
                    onClick = {
                        menuOpen = false
                        exportM3uLauncher.launch("telos-radio.m3u")
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.hc_back_up_collection)) },
                    onClick = {
                        menuOpen = false
                        backupLauncher.launch("telos-radio-backup.zip")
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.hc_restore_backup)) },
                    onClick = {
                        menuOpen = false
                        restoreLauncher.launch(arrayOf("*/*"))
                    },
                )
            }
        }
    }) {
    Column(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .telosTechSurface(borderColor = if (isRadioPlaying) TelosCyber.NeonGreen else TelosCyber.NeonCyan, shape = RoundedCornerShape(16.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            de.mm20.launcher2.ui.media.AudioSpectrumVisualizer(isPlaying = isRadioPlaying, modifier = Modifier.fillMaxWidth())
        }

        when (if (searchExpanded) 1 else selectedTabIndex) {
            0 -> {
                val shownFavorites = androidx.compose.runtime.remember(favorites, collectionQuery) {
                    de.mm20.launcher2.comms.search.TelosSearch.filter(favorites, collectionQuery) { listOf(it.name) }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                if (favorites.isNotEmpty()) {
                    de.mm20.launcher2.ui.media.MediaSearchBar(
                        collectionQuery, { collectionQuery = it }, stringResource(I18nR.string.tsm_search_my_stations),
                        expanded = collectionSearchExpanded, onClose = { collectionQuery = ""; collectionSearchExpanded = false },
                    )
                }
                RadioHome(
                    favorites = shownFavorites,
                    player = playerViewModel,
                    onLongClick = { editTarget = it },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    header = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TelosCyberSectionLabel(
                                text = stringResource(I18nR.string.hc_radio),
                                accent = if (isRadioPlaying) TelosCyber.NeonGreen else TelosCyber.NeonCyan,
                            )
                            if (shownFavorites.isEmpty() && favorites.isNotEmpty() && collectionQuery.isNotBlank()) {
                                de.mm20.launcher2.ui.component.SearchEmptyState(collectionQuery)
                            }
                            if (favorites.isEmpty()) {
                                RadioEmptyState(stringResource(I18nR.string.hc_no_stations_yet_search_for_a_station_add))
                            }
                        }
                    },
                )
                }
            }

            1 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    de.mm20.launcher2.ui.media.MediaSearchBar(
                        searchQuery, { viewModel.updateSearchQuery(it) }, stringResource(I18nR.string.tsm_search_radio_browser),
                        expanded = searchExpanded, onClose = { viewModel.updateSearchQuery(""); searchExpanded = false },
                    )
                    val reduceAnim by org.koin.compose.koinInject<de.mm20.launcher2.preferences.ui.PerformanceSettings>().reduceAnimations.collectAsState(false)
                    if (isSearching && searchResults.isEmpty()) {
                        RadioRowsSkeleton(reduceAnim, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                    } else if (isSearching) {
                        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                    }
                    searchError?.let {
                        Text(
                            text = if (it.arg != null) stringResource(it.resId, it.arg) else stringResource(it.resId),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (!isSearching && searchError == null && searchQuery.isNotBlank() && searchResults.isEmpty()) {
                        RadioEmptyState(stringResource(I18nR.string.hc_no_stations_found))
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults.distinctBy { it.id }, key = { it.id }) { station ->
                            val isFavorite = favorites.any { it.id == station.id }
                            StationRow(
                                station = station,
                                isFavorite = isFavorite,
                                onFavoriteClick = { viewModel.toggleFavorite(station) },
                                onClick = { if (values.tapPlays) playerViewModel.toggleStation(station) else editTarget = station },
                                player = playerViewModel,
                                onLongClick = { editTarget = station },
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TelosCyberSectionLabel(stringResource(I18nR.string.au_radio_tab_history))
                            TextButton(onClick = { selectedTabIndex = 0 }) { Text(stringResource(I18nR.string.au_radio_tab_collection)) }
                        }
                    }
                    if (values.controlsScroll) item { RadioNowPlayingBar(favorites, onOpen = { showNowPlaying = true }) }
                    if (history.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(I18nR.string.hc_tracks_announced_by_the_stations_you_lis),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        item {
                            TextButton(onClick = { viewModel.clearHistory() }) { Text(stringResource(I18nR.string.hc_clear_history)) }
                        }
                    }
                    items(history, key = { it.id }) { entry ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = entry.stationName + " · " +
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(entry.playedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    }
    }

    RadioRecordingResultEffect()
    RadioRecordingsEntry(showCard = false, open = showRecordings, onDismiss = { showRecordings = false })

    if (showNowPlaying) {
        RadioNowPlayingSheet(
            onDismiss = { showNowPlaying = false },
            stations = (favorites + searchResults).distinctBy { it.id },
            favoriteIds = favorites.map { it.id }.toSet(),
            onToggleFavorite = { viewModel.toggleFavorite(it) },
        )
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_add_station))  } },
            text = {
                Column {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(I18nR.string.hc_stream_or_playlist_address)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(I18nR.string.hc_name_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addStation(name, address)
                    showAdd = false
                }) { Text(stringResource(I18nR.string.hc_add)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_sleep_timer))  } },
            text = {
                Column {
                    listOf(15, 30, 45, 60, 90).forEach { minutes ->
                        TextButton(onClick = {
                            playerViewModel.setSleepTimer(minutes)
                            showSleep = false
                        }) { Text(stringResource(I18nR.string.hc_stop_playback_in_minutes, minutes)) }
                    }
                    if (sleepEndsAt > 0) {
                        TextButton(onClick = {
                            playerViewModel.cancelSleepTimer()
                            showSleep = false
                        }) { Text(stringResource(I18nR.string.hc_turn_timer_off)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSleep = false }) { Text(stringResource(I18nR.string.hc_close)) } },
        )
    }

    editTarget?.let { station ->
        var renaming by remember(station.id) { mutableStateOf(false) }
        var newName by remember(station.id) { mutableStateOf(station.name) }
        var newUrl by remember(station.id) { mutableStateOf(station.streamUrl) }
        var newImage by remember(station.id) { mutableStateOf(station.faviconUrl) }
        if (!renaming) {
            val saved = favorites.any { it.id == station.id }
            AlertDialog(
                onDismissRequest = { editTarget = null },
                title = { TelosCyberHeading {  Text(station.name)  } },
                text = {
                    Column {
                        Text(station.streamUrl, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = {
                            de.mm20.launcher2.ui.comms.radio.RadioShare.share(context, station)
                            editTarget = null
                        }) { Text(stringResource(I18nR.string.au10_radio_share)) }
                        if (saved) {
                            TextButton(onClick = { renaming = true }) { Text(stringResource(if (values.editMetadata || values.editStreamUrls) I18nR.string.hc_edit else I18nR.string.hc_rename)) }
                            TextButton(onClick = {
                                viewModel.deleteStation(station.id)
                                editTarget = null
                            }) { Text(stringResource(I18nR.string.hc_remove)) }
                        } else {
                            TextButton(onClick = {
                                viewModel.toggleFavorite(station)
                                editTarget = null
                            }) { Text(stringResource(I18nR.string.hc_favorite)) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { editTarget = null }) { Text(stringResource(I18nR.string.hc_close)) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { editTarget = null },
                title = { TelosCyberHeading {  Text(stringResource(if (values.editMetadata || values.editStreamUrls) I18nR.string.hc_edit else I18nR.string.hc_rename_station))  } },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (values.editMetadata) OutlinedTextField(value = newImage, onValueChange = { newImage = it },
                        label = { Text(stringResource(I18nR.string.radio_station_image_url)) }, modifier = Modifier.fillMaxWidth())
                    if (values.editStreamUrls) OutlinedTextField(value = newUrl, onValueChange = { newUrl = it },
                        label = { Text(stringResource(I18nR.string.hc_stream_or_playlist_address)) }, modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateStation(station.copy(name = newName.trim().ifBlank { station.name }, nameManuallySet = true,
                            faviconUrl = if (values.editMetadata) newImage.trim() else station.faviconUrl,
                            streamUrl = if (values.editStreamUrls) newUrl.trim().ifBlank { station.streamUrl } else station.streamUrl))
                        editTarget = null
                    }) { Text(stringResource(I18nR.string.hc_save)) }
                },
                dismissButton = { TextButton(onClick = { editTarget = null }) { Text(stringResource(I18nR.string.hc_cancel)) } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StationRow(
    station: RadioStation,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onClick: () -> Unit,
    player: RadioViewModel,
    onLongClick: (() -> Unit)? = null,
) {
    val reduce by org.koin.compose.koinInject<de.mm20.launcher2.preferences.ui.PerformanceSettings>().reduceAnimations.collectAsState(false)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(reduce, onClick = onClick, onLongClick = onLongClick)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RadioLogo(station, Modifier.size(64.dp), corner = 8.dp, pad = 6.dp)

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = de.mm20.launcher2.ui.comms.radioStationSubtitle(station),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            de.mm20.launcher2.ui.comms.RadioPlayButton(station, player)

            // Favorite Button
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    painter = painterResource(if (isFavorite) BaseR.drawable.star_24px_filled else BaseR.drawable.star_24px),
                    contentDescription = stringResource(I18nR.string.hc_favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
// === TELOS_PENDING_REVIEW_END: radio_browser_ktor ===
