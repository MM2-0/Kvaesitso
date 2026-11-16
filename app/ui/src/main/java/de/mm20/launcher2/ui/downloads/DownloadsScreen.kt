package de.mm20.launcher2.ui.downloads

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.downloads.AfterFinish
import de.mm20.launcher2.downloads.DownloadCategory
import de.mm20.launcher2.downloads.DownloadEvent
import de.mm20.launcher2.downloads.DownloadManager
import de.mm20.launcher2.downloads.DownloadState
import de.mm20.launcher2.downloads.DownloadTask
import de.mm20.launcher2.downloads.DownloadType
import de.mm20.launcher2.downloads.engine.TorrentController
import de.mm20.launcher2.downloads.logic.TorrentSources
import de.mm20.launcher2.downloads.logic.BlockReason
import de.mm20.launcher2.downloads.logic.Formatting
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.MediaFrame
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.component.SearchEmptyState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class Filter { All, Active, Queued, Completed, Failed, Torrents, Media }

private fun Filter.matches(t: DownloadTask) = when (this) {
    Filter.All -> true
    Filter.Active -> t.state.isActive
    Filter.Queued -> t.state == DownloadState.Queued || t.state == DownloadState.Paused
    Filter.Completed -> t.state == DownloadState.Completed
    Filter.Failed -> t.state == DownloadState.Failed
    Filter.Torrents -> t.type == DownloadType.Torrent
    Filter.Media -> t.type == DownloadType.Media
}

@Composable
fun DownloadsScreen(initialUrls: List<String> = emptyList()) {
    val context = LocalContext.current
    val manager: DownloadManager = koinInject()
    val torrentController: TorrentController = koinInject()
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val tasks by manager.tasks.collectAsState()
    val settings by manager.settings.values.collectAsState()
    val blocked by manager.blockReason.collectAsState()

    var filter by rememberSaveable { mutableStateOf(Filter.All) }
    var query by rememberSaveable { mutableStateOf("") }

    val initialIsTorrent = initialUrls.isNotEmpty() && initialUrls.any { TorrentSources.containsTorrent(it) || it.startsWith("file:") }
    var showAdd by rememberSaveable { mutableStateOf(initialUrls.isNotEmpty() && !initialIsTorrent) }
    var showAddTorrent by rememberSaveable { mutableStateOf(initialIsTorrent) }
    var addUrls by rememberSaveable { mutableStateOf(initialUrls.joinToString("\n")) }
    var showMedia by rememberSaveable { mutableStateOf(false) }
    var mediaUrl by rememberSaveable { mutableStateOf("") }
    var clip by remember { mutableStateOf<String?>(null) }
    var handledClip by rememberSaveable { mutableStateOf("") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<DownloadTask?>(null) }
    var menu by remember { mutableStateOf(false) }

    // links that arrive while the screen is open (the app is opened again by a share)
    // remembered across rotation: a sheet the user already closed must not open again
    var handledInitial by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(initialUrls) {
        val key = initialUrls.joinToString("\n")
        if (initialUrls.isNotEmpty() && key != handledInitial) {
            handledInitial = key
            addUrls = initialUrls.joinToString("\n")
            if (initialIsTorrent) showAddTorrent = true else showAdd = true
        }
    }

    // optional: offer the link in the clipboard whenever the screen comes to the foreground
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, settings.detectClipboard) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && manager.settings.current.detectClipboard) {
                clip = clipboardLink(context)?.takeIf { it != handledClip }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // after finish: open or share a finished file while this screen is shown
    LaunchedEffect(manager) {
        manager.events.collect { e ->
            if (e is DownloadEvent.Completed) {
                val ok = when (manager.settings.current.afterFinish) {
                    AfterFinish.Open -> openDownload(context, e.task)
                    AfterFinish.Share -> shareDownload(context, e.task)
                    AfterFinish.Nothing -> true
                }
                if (!ok) Toast.makeText(context, I18nR.string.dl_no_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val counts = remember(tasks) { Filter.entries.associateWith { f -> tasks.count { f.matches(it) } } }
    val filterLabels = Filter.entries.associateWith { f ->
        stringResource(
            when (f) {
                Filter.All -> I18nR.string.dl_tab_all
                Filter.Active -> I18nR.string.dl_tab_active
                Filter.Queued -> I18nR.string.dl_tab_queued
                Filter.Completed -> I18nR.string.dl_tab_completed
                Filter.Failed -> I18nR.string.dl_tab_failed
                Filter.Torrents -> I18nR.string.dl_tab_torrents
                Filter.Media -> I18nR.string.dl_m_tab
            }
        )
    }
    val categoryLabels = DownloadCategory.entries.associateWith { c ->
        stringResource(
            when (c) {
                DownloadCategory.Video -> I18nR.string.dl_category_video
                DownloadCategory.Audio -> I18nR.string.dl_category_audio
                DownloadCategory.Documents -> I18nR.string.dl_category_documents
                DownloadCategory.Archives -> I18nR.string.dl_category_archives
                DownloadCategory.Programs -> I18nR.string.dl_category_programs
                DownloadCategory.Other -> I18nR.string.dl_category_other
            }
        )
    }
    val visible by remember(tasks, filter, query) {
        derivedStateOf {
            tasks.filter { filter.matches(it) }
                .filter {
                    query.isBlank() || de.mm20.launcher2.comms.search.TelosSearch.matches(
                        query, it.displayName, it.name,
                        runCatching { java.net.URI(it.url).host }.getOrNull() ?: it.url,
                        Filter.entries.filter { f -> f != Filter.All && f.matches(it) }
                            .joinToString(" ") { f -> filterLabels[f].orEmpty() },
                        it.category?.let { c -> categoryLabels[c] },
                    )
                }
                .sortedWith(
                    compareByDescending<DownloadTask> { it.state.isActive }
                        .thenByDescending { it.priority }
                        .thenByDescending { it.createdAt }
                )
        }
    }

    var showUploadSheet by rememberSaveable { mutableStateOf(false) }
    var showTorrentCreatorSheet by rememberSaveable { mutableStateOf(false) }

    MediaFrame(
        stringResource(I18nR.string.dl_title),
        askNotifications = true,
        guardKey = "telos_downloads_app://downloads",
        actions = {
            var showFontDialog by remember { mutableStateOf(false) }
            if (showFontDialog) {
                de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
                    appKey = "downloader",
                    appName = stringResource(I18nR.string.dl_title),
                    onDismiss = { showFontDialog = false }
                )
            }
            IconButton(onClick = { showFontDialog = true }) {
                Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
            }
            IconButton(onClick = { menu = true }) {
                Icon(painterResource(BaseR.drawable.more_vert_24px), stringResource(I18nR.string.action_more_actions))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Upload File to Cloud") }, onClick = { menu = false; showUploadSheet = true })
                DropdownMenuItem(text = { Text("Create Torrent / Magnet") }, onClick = { menu = false; showTorrentCreatorSheet = true })
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_pause_all)) }, onClick = { menu = false; manager.pauseAll() })
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_resume_all)) }, onClick = { menu = false; manager.resumeAll() })
                DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_clear_completed)) }, onClick = { menu = false; manager.clearFinished(false) })
                DropdownMenuItem(
                    text = { Text(stringResource(I18nR.string.dl_settings)) },
                    onClick = { menu = false; backStack.add(DownloadsSettingsRoute) },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                de.mm20.launcher2.ui.component.TelosCommandHud(
                    searchQuery = query,
                    onSearchQueryChange = { query = it },
                    searchPlaceholder = stringResource(I18nR.string.dl_search),
                    actions = Filter.entries.mapIndexed { idx, f ->
                        val labelRes = when (f) {
                            Filter.All -> I18nR.string.dl_tab_all
                            Filter.Active -> I18nR.string.dl_tab_active
                            Filter.Queued -> I18nR.string.dl_tab_queued
                            Filter.Completed -> I18nR.string.dl_tab_completed
                            Filter.Failed -> I18nR.string.dl_tab_failed
                            Filter.Torrents -> I18nR.string.dl_tab_torrents
                            Filter.Media -> I18nR.string.dl_m_tab
                        }
                        val count = counts[f] ?: 0
                        de.mm20.launcher2.ui.component.TelosHudAction(
                            id = f.name,
                            icon = when (f) {
                                Filter.All -> BaseR.drawable.apps_24px
                                Filter.Active -> BaseR.drawable.download_24px
                                Filter.Queued -> BaseR.drawable.pause_24px
                                Filter.Completed -> BaseR.drawable.check_circle_24px
                                Filter.Failed -> BaseR.drawable.error_24px
                                Filter.Torrents -> BaseR.drawable.cloud_20px
                                Filter.Media -> BaseR.drawable.movie_24px
                            },
                            label = stringResource(labelRes) + if (count > 0) " $count" else "",
                            selected = filter == f,
                            badge = count.takeIf { it > 0 }?.toString(),
                            accent = TelosCyber.accentFor(idx),
                            onClick = { filter = f },
                        )
                    },
                )
                clip?.takeIf { settings.detectClipboard }?.let { c ->
                    ClipboardBanner(c, onAdd = {
                        handledClip = c; clip = null
                        when (de.mm20.launcher2.downloads.logic.MediaUrls.classify(c)) {
                            de.mm20.launcher2.downloads.logic.LinkKind.Magnet -> { addUrls = c; showAddTorrent = true }
                            de.mm20.launcher2.downloads.logic.LinkKind.Media -> { mediaUrl = c; showMedia = true }
                            de.mm20.launcher2.downloads.logic.LinkKind.File -> manager.add(de.mm20.launcher2.downloads.DownloadRequest(url = c))
                            else -> { addUrls = c; showAdd = true }
                        }
                    }, onDismiss = { handledClip = c; clip = null })
                }
                if (blocked != null) BlockBanner(blocked!!)
                SpeedHeader(tasks)
                if (filter == Filter.Torrents) TorrentSpeedHeader(tasks)
                if (visible.isEmpty() && query.isNotBlank() && tasks.isNotEmpty()) {
                    SearchEmptyState(query)
                } else if (filter == Filter.Torrents && visible.isEmpty()) {
                    EmptyState(I18nR.string.dl_t_empty_title, I18nR.string.dl_t_empty_text)
                } else if (visible.isEmpty()) {
                    if (tasks.isEmpty()) EmptyState(I18nR.string.dl_empty_title, I18nR.string.dl_empty_text)
                    else EmptyState(I18nR.string.dl_empty_filter_title, I18nR.string.dl_empty_filter_text)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.id }) { t ->
                            if (t.type == DownloadType.Torrent && filter == Filter.Torrents) {
                                TorrentCard(t, manager, onOpenDetails = { detailId = t.id }, onDelete = { deleting = t })
                            } else SwipeableTaskCard(
                                task = t,
                                manager = manager,
                                onOpenDetails = { detailId = t.id },
                                onDelete = { deleting = t },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = {
                    addUrls = ""
                    if (filter == Filter.Torrents) showAddTorrent = true
                    else if (filter == Filter.Media) { mediaUrl = ""; showMedia = true }
                    else showAdd = true
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { Icon(painterResource(BaseR.drawable.add_24px), null) },
                text = { Text(stringResource(I18nR.string.dl_add)) },
            )
        }
    }

    if (showAdd) {
        AddDownloadSheet(
            initialText = addUrls,
            manager = manager,
            onDismiss = { showAdd = false },
            onTorrent = { text -> showAdd = false; addUrls = text; showAddTorrent = true },
            onMedia = { text -> showAdd = false; mediaUrl = text; showMedia = true },
        )
    }
    if (showMedia) {
        AddMediaSheet(initialUrl = mediaUrl, manager = manager, onDismiss = { showMedia = false })
    }
    if (showAddTorrent) {
        AddTorrentSheet(initialText = addUrls, manager = manager, onDismiss = { showAddTorrent = false })
    }
    detailId?.let { id ->
        val t = tasks.firstOrNull { it.id == id }
        if (t == null) detailId = null
        else if (t.type == DownloadType.Torrent) TorrentDetailSheet(t, manager, torrentController, onDismiss = { detailId = null }, onDelete = { deleting = t })
        else TaskDetailSheet(t, manager, onDismiss = { detailId = null }, onDelete = { deleting = t })
    }
    deleting?.let { t ->
        DeleteDialog(t, onDismiss = { deleting = null }) { withFile ->
            if (detailId == t.id) detailId = null
            manager.remove(t.id, withFile)
            deleting = null
        }
    }
    if (showUploadSheet) {
        TransferUploadSheet(onDismiss = { showUploadSheet = false })
    }
    if (showTorrentCreatorSheet) {
        TransferTorrentCreatorSheet(onDismiss = { showTorrentCreatorSheet = false })
    }
}

@Composable
private fun BlockBanner(reason: BlockReason) {
    val text = when (reason) {
        BlockReason.NetworkPolicy -> I18nR.string.netfw_blocked
        BlockReason.Offline -> I18nR.string.dl_block_offline
        BlockReason.WifiOnly -> I18nR.string.dl_block_wifi
        BlockReason.LowBattery -> I18nR.string.dl_block_battery
        BlockReason.Schedule -> I18nR.string.dl_p3_block_schedule
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(if (reason == BlockReason.Offline) BaseR.drawable.wifi_off_24px else BaseR.drawable.schedule_24px), null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ClipboardBanner(link: String, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(BaseR.drawable.link_24px), null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(I18nR.string.dl_p3_clip_found, link.take(60)),
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd) { Text(stringResource(I18nR.string.dl_p3_clip_add)) }
            IconButton(onClick = onDismiss) { Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.dl_cancel)) }
        }
    }
}

@Composable
private fun SpeedHeader(tasks: List<DownloadTask>) {
    val active = tasks.filter { it.state.isActive }
    if (active.isEmpty()) return
    val speed = active.sumOf { it.speedBps }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .telosTechSurface(borderColor = TelosCyber.NeonGreen, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TelosCyberSectionLabel(
            pluralStringResource(I18nR.plurals.dl_header_active, active.size, active.size) + " · " + Formatting.speed(speed),
            accent = TelosCyber.NeonGreen,
        )
    }
}

@Composable
private fun EmptyState(title: Int, text: Int) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(BaseR.drawable.download_24px), null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.size(20.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SwipeableTaskCard(task: DownloadTask, manager: DownloadManager, onOpenDetails: () -> Unit, onDelete: () -> Unit) {
    // the state outlives recompositions and keeps the lambda it was created with: read the current task and callback
    val currentTask by androidx.compose.runtime.rememberUpdatedState(task)
    val currentDelete by androidx.compose.runtime.rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> currentDelete()
                SwipeToDismissBoxValue.StartToEnd -> togglePause(manager, currentTask)
                else -> {}
            }
            false // the card springs back, the action decides what changes
        },
    )
    SwipeToDismissBox(
        state = state,
        // a finished download has nothing to pause or resume
        enableDismissFromStartToEnd = task.state != DownloadState.Completed,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .background(if (toEnd) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                val paused = task.state == DownloadState.Paused || task.state == DownloadState.Failed
                Icon(
                    painterResource(if (toEnd) (if (paused) BaseR.drawable.play_arrow_24px else BaseR.drawable.pause_24px) else BaseR.drawable.delete_24px),
                    null,
                )
            }
        },
    ) {
        TaskCard(task, manager, onOpenDetails, onDelete)
    }
}

private fun togglePause(manager: DownloadManager, t: DownloadTask) {
    when (t.state) {
        DownloadState.Paused, DownloadState.Failed -> manager.resume(t.id)
        DownloadState.Completed -> {}
        else -> manager.pause(t.id)
    }
}

@Composable
private fun TaskCard(task: DownloadTask, manager: DownloadManager, onOpenDetails: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val failed = task.state == DownloadState.Failed
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpenDetails),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (failed) BaseR.drawable.error_24px else categoryIcon(task.category)), null,
                    tint = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(2.dp))
                Text(
                    if (failed && !task.error.isNullOrBlank()) task.error!! else stateLabel(task) + " · " + progressText(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.state != DownloadState.Completed && task.state != DownloadState.Failed) {
                    Spacer(Modifier.size(8.dp))
                    if (task.totalBytes > 0) {
                        LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth(), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    } else if (task.state.isActive) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // ring with the main action in the middle
            when (task.state) {
                DownloadState.Completed -> IconButton(onClick = {
                    if (!openDownload(context, task)) Toast.makeText(context, I18nR.string.dl_no_app, Toast.LENGTH_SHORT).show()
                }) { Icon(painterResource(BaseR.drawable.open_in_new_24px), stringResource(I18nR.string.dl_open)) }
                DownloadState.Failed -> IconButton(onClick = { manager.resume(task.id) }) {
                    Icon(painterResource(BaseR.drawable.restart_alt_24px), stringResource(I18nR.string.dl_retry))
                }
                else -> Box(contentAlignment = Alignment.Center) {
                    if (task.totalBytes > 0) CircularProgressIndicator(progress = { task.progress }, modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    else if (task.state.isActive) CircularProgressIndicator(modifier = Modifier.size(44.dp), strokeWidth = 3.dp)
                    IconButton(onClick = { togglePause(manager, task) }, modifier = Modifier.size(44.dp)) {
                        val paused = task.state == DownloadState.Paused
                        Icon(
                            painterResource(if (paused) BaseR.drawable.play_arrow_24px else BaseR.drawable.pause_24px),
                            stringResource(if (paused) I18nR.string.dl_resume else I18nR.string.dl_pause),
                            Modifier.size(20.dp),
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(BaseR.drawable.more_vert_24px), stringResource(I18nR.string.action_more_actions)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (task.state == DownloadState.Completed) {
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_open)) }, onClick = {
                            menu = false
                            if (!openDownload(context, task)) Toast.makeText(context, I18nR.string.dl_no_app, Toast.LENGTH_SHORT).show()
                        })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_share)) }, onClick = {
                            menu = false
                            scope.launch {
                                if (!shareDownload(context, task)) {
                                    Toast.makeText(context, I18nR.string.dl_no_app, Toast.LENGTH_SHORT).show()
                                }
                            }
                        })
                    }
                    if (task.state == DownloadState.Paused) DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_resume)) }, onClick = { menu = false; manager.resume(task.id) })
                    if (task.state.isActive || task.state == DownloadState.Queued) DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_pause)) }, onClick = { menu = false; manager.pause(task.id) })
                    if (failed) DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_retry)) }, onClick = { menu = false; manager.resume(task.id) })
                    if (task.state == DownloadState.Queued || task.state == DownloadState.Paused) {
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_p3_move_top)) }, onClick = { menu = false; manager.moveToTop(task.id) })
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_p3_move_bottom)) }, onClick = { menu = false; manager.moveToBottom(task.id) })
                    }
                    if (task.state == DownloadState.Completed && de.mm20.launcher2.downloads.logic.ArchiveLogic.isZip(task.name) && !task.fileMissing) {
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_p3_extract)) }, onClick = { menu = false; manager.extract(task.id) })
                    }
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_copy_link)) }, onClick = { menu = false; copyLink(context, task.url) })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_details)) }, onClick = { menu = false; onOpenDetails() })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.dl_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun DeleteDialog(task: DownloadTask, onDismiss: () -> Unit, onConfirm: (deleteFile: Boolean) -> Unit) {
    val done = task.state == DownloadState.Completed || (task.torrent != null && task.completedAt > 0)
    val uris = remember(task) { (task.media?.outputUris.orEmpty() + task.torrent?.files.orEmpty().mapNotNull { it.uri } + listOfNotNull(task.fileUri)).distinct().map(android.net.Uri::parse) }
    var deleteFiles by remember(task.id) { mutableStateOf(false) }
    if (deleteFiles && uris.isNotEmpty()) {
        de.mm20.launcher2.ui.trash.UriDeleteDialog(uris, { onConfirm(false) }, { deleteFiles = false }, { onConfirm(true) })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.dl_delete_title))  } },
        text = { Text(stringResource(if (done) I18nR.string.dl_delete_text_done else I18nR.string.dl_delete_text_partial, task.displayName)) },
        confirmButton = {
            Row {
                if (done) TextButton(onClick = { if (uris.isNotEmpty()) deleteFiles = true else onConfirm(true) }) { Text(stringResource(I18nR.string.dl_delete_with_file)) }
                TextButton(onClick = { onConfirm(false) }) { Text(stringResource(if (done) I18nR.string.dl_delete_keep_file else I18nR.string.dl_delete)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.dl_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferUploadSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedService by remember { mutableStateOf(de.mm20.launcher2.downloads.UploadService.FileIo) }
    var busy by remember { mutableStateOf(false) }
    var uploadResult by remember { mutableStateOf<de.mm20.launcher2.downloads.UploadResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedUri = uri
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Upload File to Cloud", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

            Text("Select a file from your device and upload to anonymous cloud file hosting.", style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { picker.launch("*/*") }, enabled = !busy) {
                    Text(if (selectedUri != null) "Change File" else "Choose File")
                }
                selectedUri?.let { uri ->
                    Text(uri.lastPathSegment ?: "File selected", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Text("Target Hosting Service:", style = MaterialTheme.typography.titleSmall)

            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                de.mm20.launcher2.downloads.UploadService.entries.forEach { service ->
                    FilterChip(
                        selected = selectedService == service,
                        onClick = { selectedService = service },
                        label = { Text(service.displayName) }
                    )
                }
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            uploadResult?.let { result ->
                Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Upload Complete!", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("File: ${result.fileName} (${Formatting.size(result.sizeBytes)})", style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.OutlinedTextField(
                            value = result.downloadUrl,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Share Link") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Upload Link", result.downloadUrl))
                                Toast.makeText(context, "Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy Link")
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.dl_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        busy = true
                        error = null
                        scope.launch {
                            val res = de.mm20.launcher2.downloads.TransferUploader.upload(context, uri, selectedService)
                            busy = false
                            res.onSuccess {
                                uploadResult = it
                            }.onFailure {
                                error = it.message ?: "Upload failed"
                            }
                        }
                    },
                    enabled = selectedUri != null && !busy
                ) {
                    Text("Start Upload")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferTorrentCreatorSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var torrentResult by remember { mutableStateOf<de.mm20.launcher2.downloads.TorrentResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedUri = uri
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Create Torrent & Magnet", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

            Text("Generate a .torrent file and Magnet URI to seed or share any local file.", style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { picker.launch("*/*") }, enabled = !busy) {
                    Text(if (selectedUri != null) "Change File" else "Choose File")
                }
                selectedUri?.let { uri ->
                    Text(uri.lastPathSegment ?: "File selected", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            torrentResult?.let { result ->
                Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Torrent & Magnet Generated!", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("File: ${result.name} (${Formatting.size(result.sizeBytes)})", style = MaterialTheme.typography.bodySmall)
                        androidx.compose.material3.OutlinedTextField(
                            value = result.magnetUrl,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Magnet Link") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Magnet Link", result.magnetUrl))
                                Toast.makeText(context, "Magnet link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy Magnet Link")
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.dl_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        busy = true
                        error = null
                        scope.launch {
                            val res = de.mm20.launcher2.downloads.TorrentCreator.create(context, uri)
                            busy = false
                            res.onSuccess {
                                torrentResult = it
                            }.onFailure {
                                error = it.message ?: "Torrent creation failed"
                            }
                        }
                    },
                    enabled = selectedUri != null && !busy
                ) {
                    Text("Create Torrent")
                }
            }
        }
    }
}
