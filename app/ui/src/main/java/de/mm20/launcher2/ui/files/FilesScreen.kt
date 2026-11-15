package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import coil.compose.AsyncImage
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.collectAsState
import de.mm20.launcher2.preferences.ui.PerformanceSettings
import org.koin.compose.koinInject
import de.mm20.launcher2.ui.component.rememberVirtualAppBlur
import de.mm20.launcher2.ui.component.virtualAppElevated
import de.mm20.launcher2.ui.component.virtualAppGlass
import de.mm20.launcher2.ui.component.virtualAppSurface
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import de.mm20.launcher2.ui.files.remote.ConnectionsRoute
import de.mm20.launcher2.ui.files.remote.RemotePath
import java.io.File

@Serializable
data object FilesRoute : NavKey

private typealias Icons = de.mm20.launcher2.base.R.drawable

/** From this many bytes to fetch from a network storage, sharing asks first */
private const val SHARE_WARN_BYTES = 50L * 1024 * 1024

private fun hasAllFilesAccess(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

/**
 * Telos Files: a file manager. The layout follows Solid Explorer (storage drawer, breadcrumbs,
 * selection bar, paste bar, tinted type icons) and MiXplorer (details such as permissions and
 * checksums, root tools).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilesBrowser(vm: FilesViewModel, handlesBack: Boolean = true) {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val filesPrefs = remember { context.getSharedPreferences("telos_files", android.content.Context.MODE_PRIVATE) }
    var ftpPath by remember { mutableStateOf<String?>(null) }
    ftpPath?.let { path -> de.mm20.launcher2.ui.files.remote.FilesFtpServerDialog(path) { ftpPath = null } }
    var lockOnOpen by remember { mutableStateOf(filesPrefs.getBoolean("lock_on_open", false)) }

    var hasAccess by remember { mutableStateOf(hasAllFilesAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = hasAllFilesAccess(context); vm.refreshVolumes(); vm.reloadConnections(); vm.reload() }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasAccess = hasAllFilesAccess(context) }

    if (!hasAccess) {
        AccessGate(onAllow = {
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else {
                legacyPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        })
        return
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val blur = rememberVirtualAppBlur()
    val bar = virtualAppElevated()
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }

    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<FilesDialog?>(null) }
    var showTrash by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    if (showApps) FilesAppManagerDialog { showApps = false }
    var showTransfers by remember { mutableStateOf(false) }
    var encryptionEntry by remember { mutableStateOf<Pair<FsEntry, Boolean>?>(null) }
    encryptionEntry?.let { (entry, decrypt) -> FilesEncryptionDialog(java.io.File(entry.path), decrypt,
        { encryptionEntry = null }, { encryptionEntry = null; vm.clearSelection(); vm.reload(); vm.onFilesChanged() }) }
    var showCollections by remember { mutableStateOf(false) }
    var sharingPath by remember { mutableStateOf<String?>(null) }
    sharingPath?.let { FilesSharingDialog(it) { sharingPath = null } }
    var showToolbarSettings by remember { mutableStateOf(false) }
    var toolbarConfig by remember { mutableStateOf(FilesToolbarPreferences.read(context)) }
    val localTransfers by vm.transferQueue.items.collectAsState()
    LaunchedEffect(localTransfers.count { it.status in setOf(FilesTransferQueue.Status.COMPLETE, FilesTransferQueue.Status.ORIGINAL_RETAINED, FilesTransferQueue.Status.FAILED, FilesTransferQueue.Status.CANCELLED) }) {
        vm.reload(); vm.onFilesChanged()
    }
    if (showTransfers) FilesTransfersDialog(vm.transferQueue) { showTransfers = false }
    if (showToolbarSettings) FilesToolbarSettingsDialog(toolbarConfig, { showToolbarSettings = false }) { config ->
        FilesToolbarPreferences.write(context, config); toolbarConfig = config; showToolbarSettings = false
    }
    var showSearchFilters by remember { mutableStateOf(false) }
    if (showSearchFilters) FilesSearchDialog(vm) { showSearchFilters = false }
    if (showTrash) de.mm20.launcher2.ui.trash.TrashDialog({ showTrash = false }, { vm.reload(); vm.onFilesChanged() })
    var analysisPath by remember { mutableStateOf<String?>(null) }
    analysisPath?.let { StorageAnalysisDialog(it, vm::open, { analysisPath = null }) }
    var viewMenu by remember { mutableStateOf(false) }
    var peekEntry by remember { mutableStateOf<FsEntry?>(null) }
    val keyboardFocus = remember { FocusRequester() }
    LaunchedEffect(handlesBack) { if (handlesBack) keyboardFocus.requestFocus() }
    val performanceSettings: PerformanceSettings = koinInject()
    val reduceFlow = remember(performanceSettings) { performanceSettings.reduceAnimations }
    val reduceAnimations by reduceFlow.collectAsState(false)
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val gridMode = vm.viewMode != 0
    // the place in the folder stays when the view changes; a new folder starts at the top
    LaunchedEffect(gridMode) {
        if (gridMode) gridState.scrollToItem(listState.firstVisibleItemIndex) else listState.scrollToItem(gridState.firstVisibleItemIndex)
    }
    LaunchedEffect(vm.path) { listState.scrollToItem(0); gridState.scrollToItem(0) }

    BackHandler(handlesBack && drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(handlesBack && !drawer.isOpen) {
        if (searching && vm.query.isEmpty() && vm.searchResults == null) searching = false
        else if (!vm.up()) backStack.removeLastOrNull()
        if (vm.query.isEmpty()) searching = false
    }

    val selecting = vm.selection.isNotEmpty()
    val shown = vm.visible
    val onOpenEntry: (FsEntry) -> Unit = { entry ->
        when {
            vm.selection.isNotEmpty() -> vm.toggleSelected(entry)
            entry.isDir && !RemotePath.isRemote(entry.path) && !ArchivePath.isArchive(entry.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(entry.path) &&
                de.mm20.launcher2.ui.files.vault.VaultPath.isVaultFolder(java.io.File(entry.path)) ->
                if (de.mm20.launcher2.ui.files.vault.VaultSessions.isUnlocked(entry.path)) vm.open(de.mm20.launcher2.ui.files.vault.VaultPath.build(entry.path, "/")) else dialog = FilesDialog.Unlock(entry)
            entry.isDir -> vm.open(entry.path)
            ArchivePath.canOpen(entry.name) && !de.mm20.launcher2.ui.media.docs.DocumentTypes.supports(entry.name) && !RemotePath.isRemote(entry.path) && !ArchivePath.isArchive(entry.path) -> dialog = FilesDialog.Archive(entry)
            RemotePath.isRemote(entry.path) && (entry.kind == FileKind.Video || entry.kind == FileKind.Audio) -> FileActions.playRemoteVideo(context, entry, vm.entries)
            (RemotePath.isRemote(entry.path) || ArchivePath.isArchive(entry.path) || de.mm20.launcher2.ui.files.vault.VaultPath.isVault(entry.path)) -> vm.download(entry) { file ->
                scope.launch { FileActions.open(context, FsEntry(file.path, file.name, false, file.length(), file.lastModified()), emptyList(), false) }
            }
            else -> { val root = vm.rootMode; val adb = vm.shizukuMode; scope.launch { FileActions.open(context, entry, vm.entries, root, adb) }; Unit }
        }
    }

    if (showCollections) FilesCollectionsDialog(vm, { showCollections = false }) { entry ->
        showCollections = false; vm.clearSelection(); onOpenEntry(entry)
    }

    ModalNavigationDrawer(
        modifier = Modifier.navigationBarsPadding().focusRequester(keyboardFocus).onPreviewKeyEvent { event ->
            if (!handlesBack || event.type != KeyEventType.KeyDown) false else when {
                event.isCtrlPressed && event.key == Key.F && vm.path != null -> { searching = true; true }
                event.key == Key.Escape && searching -> { searching = false; vm.clearSearch(); true }
                event.isCtrlPressed && event.key == Key.R -> { vm.reload(); true }
                searching -> false
                event.isCtrlPressed && event.key == Key.A -> { vm.selectAll(); true }
                event.isCtrlPressed && event.key == Key.C && selecting -> { vm.copyToClipboard(false); true }
                event.isCtrlPressed && event.key == Key.X && selecting -> { vm.copyToClipboard(true); true }
                event.isCtrlPressed && event.key == Key.V && vm.clipboard != null && vm.task == null -> { vm.paste(); true }
                event.isCtrlPressed && event.key == Key.H -> { vm.toggleHidden(); true }
                event.isCtrlPressed && event.key == Key.N && vm.path != null -> { dialog = FilesDialog.NewFolder; true }
                event.isAltPressed && event.key == Key.DirectionUp -> vm.up()
                event.key == Key.Backspace && !selecting -> vm.up()
                event.key == Key.Escape && selecting -> { vm.clearSelection(); true }
                event.key == Key.Delete && selecting -> { dialog = FilesDialog.Delete(vm.selectedEntries()); true }
                event.key == Key.F2 && selecting -> {
                    val picked = vm.selectedEntries()
                    dialog = picked.singleOrNull()?.let { FilesDialog.Rename(it) } ?: FilesDialog.BatchRename(picked); true
                }
                else -> false
            }
        }.focusable(),
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(0.86f),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                StorageDrawer(
                    vm = vm,
                    onGo = { target -> vm.open(target); scope.launch { drawer.close() } },
                    onRoot = { scope.launch { drawer.close() }; if (vm.rootMode) vm.disableRoot() else dialog = FilesDialog.EnableRoot },
                    onManage = { scope.launch { drawer.close() }; backStack.add(ConnectionsRoute) },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column(Modifier.virtualAppGlass(blur, bar)) {
                    when {
                        selecting -> SelectionBar(
                            count = vm.selection.size,
                            single = vm.selectedEntries().singleOrNull(),
                            onClose = vm::clearSelection,
                            onSelectAll = vm::selectAll,
                            onCopy = { vm.copyToClipboard(false) },
                            onCut = { vm.copyToClipboard(true) },
                            onDelete = { dialog = FilesDialog.Delete(vm.selectedEntries()) },
                            onShare = {
                                val picked = vm.selectedEntries()
                                val bytes = vm.shareDownloadBytes(picked)
                                if (bytes >= SHARE_WARN_BYTES) dialog = FilesDialog.ShareLarge(picked, bytes) else vm.shareEntries(picked)
                            },
                            onRename = { val picked = vm.selectedEntries(); dialog = picked.singleOrNull()?.let { FilesDialog.Rename(it) } ?: FilesDialog.BatchRename(picked) },
                            onCompress = { dialog = FilesDialog.Compress(vm.selectedEntries()) },
                            onProperties = { vm.selectedEntries().singleOrNull()?.let { dialog = FilesDialog.Properties(it) } },
                            onBookmark = { vm.selectedEntries().singleOrNull()?.let { vm.toggleBookmark(it.path); vm.clearSelection() } },
                            onEncrypt = vm.selectedEntries().singleOrNull()?.takeIf { !vm.rootMode && !it.isDir && !it.isLink && !RemotePath.isRemote(it.path) && !ArchivePath.isArchive(it.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(it.path) }?.let { entry -> { encryptionEntry = entry to false } },
                            onDecrypt = vm.selectedEntries().singleOrNull()?.takeIf { !vm.rootMode && !it.isDir && !it.isLink && it.name.endsWith(".telos-aes", true) && !RemotePath.isRemote(it.path) && !ArchivePath.isArchive(it.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(it.path) }?.let { entry -> { encryptionEntry = entry to true } },
                            onOpenAsArchive = vm.selectedEntries().singleOrNull()
                                ?.takeIf { !it.isDir && de.mm20.launcher2.ui.media.docs.DocumentTypes.supports(it.name) && ArchivePath.canOpen(it.name) && !RemotePath.isRemote(it.path) && !ArchivePath.isArchive(it.path) }
                                ?.let { archive -> { dialog = FilesDialog.Archive(archive); vm.clearSelection() } },
                        )
                        searching -> SearchBar(
                            query = vm.query,
                            onQuery = vm::search,
                            onClose = { searching = false; vm.clearSearch() },
                            onFilters = { showSearchFilters = true },
                        )
                        else -> TopAppBar(
                            title = {
                                TelosCyberAppBarTitle(
                                    title = vm.path?.let { vm.connectionName(it)?.takeIf { _ -> RemotePath.isRoot(it) } ?: if (ArchivePath.isRoot(it)) nameOf(ArchivePath.archiveOf(it)) else if (de.mm20.launcher2.ui.files.vault.VaultPath.isRoot(it)) nameOf(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(it)) else nameOf(it) } ?: stringResource(I18nR.string.hc_telos_files),
                                    status = stringResource(I18nR.string.hc_telos_files),
                                    statusColor = TelosCyber.NeonCyan,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(painterResource(Icons.storage_24px), contentDescription = stringResource(I18nR.string.hc_storage))
                                }
                            },
                            actions = {
                                if (vm.path != null) {
                                    IconButton(onClick = { vm.path?.let { dialog = FilesDialog.TelosDrop(it) } }) {
                                        Icon(painterResource(BaseR.drawable.share_24px), contentDescription = "Telos Drop")
                                    }
                                    IconButton(onClick = { searching = true }) {
                                        Icon(painterResource(Icons.search_24px), contentDescription = stringResource(I18nR.string.hc_search))
                                    }
                                }
                                toolbarConfig.actions.forEach { action ->
                                if (vm.path != null && action == FilesToolbarAction.View) Box {
                                    IconButton(onClick = { viewMenu = true }) {
                                        Icon(
                                            painterResource(when (vm.viewMode) { 1 -> Icons.apps_24px; 2 -> Icons.dashboard_2_24px; else -> Icons.table_rows_24px }),
                                            contentDescription = stringResource(I18nR.string.au22_files_view_mode),
                                        )
                                    }
                                    DropdownMenu(expanded = viewMenu, onDismissRequest = { viewMenu = false }) {
                                        listOf(
                                            Triple(0, I18nR.string.au22_files_view_list, Icons.table_rows_24px),
                                            Triple(1, I18nR.string.au22_files_view_grid_medium, Icons.apps_24px),
                                            Triple(2, I18nR.string.au22_files_view_grid_large, Icons.dashboard_2_24px),
                                        ).forEach { (mode, label, icon) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(label)) },
                                                leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                                                trailingIcon = { if (vm.viewMode == mode) Icon(painterResource(Icons.check_24px), contentDescription = null) },
                                                onClick = { viewMenu = false; vm.updateViewMode(mode) },
                                            )
                                        }
                                    }
                                }
                                if (action != FilesToolbarAction.View) {
                                    val label = when (action) {
                                        FilesToolbarAction.Sort -> I18nR.string.hc_sort_by_2
                                        FilesToolbarAction.Hidden -> if (vm.showHidden) I18nR.string.hf_files_hide_hidden else I18nR.string.hf_files_show_hidden
                                        else -> I18nR.string.hc_refresh
                                    }
                                    IconButton(onClick = { when (action) {
                                        FilesToolbarAction.Sort -> dialog = FilesDialog.Sort
                                        FilesToolbarAction.Hidden -> vm.toggleHidden()
                                        else -> { vm.reload(); vm.refreshVolumes() }
                                    } }) {
                                        Icon(painterResource(when (action) {
                                            FilesToolbarAction.Sort -> Icons.tune_24px
                                            FilesToolbarAction.Hidden -> Icons.visibility_24px
                                            else -> Icons.restart_alt_24px
                                        }), contentDescription = stringResource(label))
                                    }
                                }
                                }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(painterResource(Icons.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more))
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_collections)) }, onClick = { menuOpen = false; showCollections = true })
                                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_apps)) }, onClick = { menuOpen = false; showApps = true })
                                        vm.path?.takeIf { !vm.rootMode && !RemotePath.isRemote(it) && !ArchivePath.isArchive(it) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(it) }?.let { folder ->
                                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_sharing_title)) }, onClick = { menuOpen = false; sharingPath = folder })
                                            DropdownMenuItem(text = { Text("FTP") }, onClick = { menuOpen = false; ftpPath = folder })
                                        }
                                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_transfers_title)) }, onClick = { menuOpen = false; showTransfers = true })
                                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_toolbar_title)) }, onClick = { menuOpen = false; showToolbarSettings = true })
                                        DropdownMenuItem(
                                            text = { Text(stringResource(if (lockOnOpen) I18nR.string.telos_files_lock_on_open_off else I18nR.string.telos_files_lock_on_open)) },
                                            onClick = {
                                                menuOpen = false
                                                val next = !lockOnOpen
                                                lockOnOpen = next
                                                filesPrefs.edit().putBoolean("lock_on_open", next).apply()
                                            },
                                        )
                                        if (vm.path != null && FilesToolbarAction.View !in toolbarConfig.actions) {
                                            listOf(0 to I18nR.string.au22_files_view_list, 1 to I18nR.string.au22_files_view_grid_medium, 2 to I18nR.string.au22_files_view_grid_large).forEach { (mode, label) ->
                                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { menuOpen = false; vm.updateViewMode(mode) })
                                            }
                                        }
                                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_trash_title)) }, onClick = { menuOpen = false; showTrash = true })
                                        if (vm.path != null) DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_search_filters)) }, onClick = { menuOpen = false; searching = true; showSearchFilters = true })
                                        if (vm.path != null && !vm.rootMode && !RemotePath.isRemote(vm.path!!) && !ArchivePath.isArchive(vm.path!!) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(vm.path!!))
                                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_analyze)) }, onClick = { menuOpen = false; analysisPath = vm.path })
                                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_sort_by_2)) }, onClick = { menuOpen = false; dialog = FilesDialog.Sort })
                                        DropdownMenuItem(text = { Text(if (vm.showHidden) stringResource(I18nR.string.hf_files_hide_hidden) else stringResource(I18nR.string.hf_files_show_hidden)) }, onClick = { menuOpen = false; vm.toggleHidden() })
                                        vm.path?.takeIf { de.mm20.launcher2.ui.files.vault.VaultPath.isVault(it) }?.let { vp ->
                                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_lock_vault)) }, onClick = { menuOpen = false; vm.lockVault(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(vp)) })
                                        }
                                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_refresh)) }, onClick = { menuOpen = false; vm.reload(); vm.refreshVolumes() })
                                        vm.path?.let { p ->
                                            DropdownMenuItem(
                                                text = { Text(if (vm.isBookmarked(p)) stringResource(I18nR.string.hf_files_remove_favorite) else stringResource(I18nR.string.hc_add_to_favorites)) },
                                                onClick = { menuOpen = false; vm.toggleBookmark(p) },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(if (vm.shizukuMode) stringResource(I18nR.string.telos_files_shizuku) else if (vm.rootMode) stringResource(I18nR.string.hf_files_root_off) else stringResource(I18nR.string.hf_files_root_menu)) },
                                            onClick = { menuOpen = false; if (vm.rootMode) vm.disableRoot() else dialog = FilesDialog.EnableRoot },
                                        )
                                        if (!vm.rootMode) DropdownMenuItem(
                                            text = { Text(stringResource(I18nR.string.telos_files_shizuku)) },
                                            onClick = { menuOpen = false; vm.enableShizuku() },
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                            ),
                        )
                    }
                    if (vm.path != null && !selecting) Breadcrumbs(vm) { vm.open(it) }
                    if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (vm.searchLimited) Text(stringResource(if (vm.path?.let { RemotePath.isRemote(it) } == true) I18nR.string.telos_files_remote_search_partial else I18nR.string.telos_files_search_limited), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
                }
            },
            floatingActionButton = {
                if (vm.path != null && !selecting && vm.error == null) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)) {
                        FloatingActionButton(onClick = { addMenu = true }) { Icon(painterResource(Icons.add_24px), contentDescription = stringResource(I18nR.string.hc_new)) }
                        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_new_folder)) }, onClick = { addMenu = false; dialog = FilesDialog.NewFolder })
                            DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_new_file)) }, onClick = { addMenu = false; dialog = FilesDialog.NewFile })
                        }
                    }
                }
            },
            bottomBar = {
                Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                    vm.task?.let { TaskBar(it) }
                    if (localTransfers.any { it.status in setOf(FilesTransferQueue.Status.RUNNING, FilesTransferQueue.Status.QUEUED, FilesTransferQueue.Status.PAUSED) })
                        TextButton(onClick = { showTransfers = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(I18nR.string.telos_files_transfers_title)) }
                    val clip = vm.clipboard
                    if (clip != null && vm.path != null && vm.task == null) PasteBar(
                        count = clip.paths.size, cut = clip.cut,
                        onPaste = vm::paste, onCancel = vm::clearClipboard,
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(virtualAppSurface())) {
                val current = vm.path
                when {
                    current == null -> HomePage(vm)
                    vm.error != null -> ErrorPage(vm.error!!, rootMode = vm.rootMode, onRoot = { dialog = FilesDialog.EnableRoot })
                    else -> Column(Modifier.fillMaxSize()) {
                        if (FileActions.isSystemPath(current, vm.rootMode)) RootBanner()
                        FolderFilmstrip(shown) { onOpenEntry(it) }
                        if (shown.isEmpty() && !vm.loading) {
                            if (vm.query.isNotBlank()) de.mm20.launcher2.ui.component.SearchEmptyState(vm.query.trim(), Modifier.fillMaxSize())
                            else EmptyPage(stringResource(I18nR.string.hf_files_folder_empty))
                        } else {
                            val unique = remember(shown) { shown.distinctBy { it.path } }
                            Crossfade(targetState = gridMode, animationSpec = if (reduceAnimations) snap() else tween(200), label = "filesView") { isGrid ->
                                if (isGrid) {
                                    val large = vm.viewMode == 2
                                    LazyVerticalGrid(
                                        state = gridState,
                                        columns = GridCells.Adaptive(if (large) 156.dp else 104.dp),
                                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(if (large) 16.dp else 12.dp),
                                    ) {
                                        gridItems(unique, key = { it.path }) { e ->
                                            FileTile(
                                                e, e.path in vm.selection, selecting, large, reduceAnimations,
                                                onClick = { onOpenEntry(e) }, onLongClick = { if (selecting) vm.toggleSelected(e) else peekEntry = e },
                                                modifier = (if (reduceAnimations) Modifier else Modifier.animateItem()).then(fileDragSource(e, vm)),
                                            )
                                        }
                                    }
                                } else {
                                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                                        items(unique, key = { it.path }) { e ->
                                            FileRow(e, e.path in vm.selection, vm.rootMode, { onOpenEntry(e) }, { if (selecting) vm.toggleSelected(e) else peekEntry = e }, fileDragSource(e, vm))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    FilesDialogs(vm, dialog) { dialog = null }
    vm.archivePrompt?.let { ArchivePasswordDialog(vm, it) }
    peekEntry?.let { e ->
        FilePeekSheet(e, onDismiss = { peekEntry = null }, onOpen = { onOpenEntry(e) }, onSelect = { vm.toggleSelected(e) })
    }
}

// ---------------------------------------------------------------- dialogs

private sealed interface FilesDialog {
    data object NewFolder : FilesDialog
    data object NewFile : FilesDialog
    data object Sort : FilesDialog
    data object EnableRoot : FilesDialog
    data class Rename(val entry: FsEntry) : FilesDialog
    data class BatchRename(val entries: List<FsEntry>) : FilesDialog
    data class Delete(val entries: List<FsEntry>) : FilesDialog
    data class Compress(val entries: List<FsEntry>) : FilesDialog
    data class Properties(val entry: FsEntry) : FilesDialog
    data class Archive(val entry: FsEntry) : FilesDialog
    data class Unlock(val entry: FsEntry) : FilesDialog
    data class ShareLarge(val entries: List<FsEntry>, val bytes: Long) : FilesDialog
    data class TelosDrop(val path: String) : FilesDialog
}

@Composable
private fun FilesDialogs(vm: FilesViewModel, dialog: FilesDialog?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    when (dialog) {
        null -> {}
        FilesDialog.NewFolder -> NameDialog(stringResource(I18nR.string.hc_new_folder), "", stringResource(I18nR.string.hf_files_create), onDismiss) { vm.newFolder(it); onDismiss() }
        FilesDialog.NewFile -> NameDialog(stringResource(I18nR.string.hc_new_file), "", stringResource(I18nR.string.hf_files_create), onDismiss) { vm.newFile(it); onDismiss() }
        is FilesDialog.Rename -> NameDialog(stringResource(I18nR.string.hc_rename), dialog.entry.name, stringResource(I18nR.string.hc_rename), onDismiss) { vm.rename(dialog.entry, it); onDismiss() }
        is FilesDialog.BatchRename -> BatchRenameDialog(vm, dialog.entries, onDismiss)
        is FilesDialog.Compress -> CompressDialog(vm, dialog.entries, onDismiss)
        is FilesDialog.Delete -> de.mm20.launcher2.ui.trash.TrashDeleteDialog(
            count = dialog.entries.size,
            canTrash = vm.canTrash(dialog.entries),
            onTrash = { vm.moveToTrash(dialog.entries) },
            onPermanent = { vm.delete(dialog.entries) },
            onDismiss = onDismiss,
            warning = if (dialog.entries.any { FileActions.isSystemPath(it.path, vm.rootMode) }) stringResource(I18nR.string.hf_files_delete_system_warning) else null,
        )
        is FilesDialog.ShareLarge -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au9_share_large_title))  } },
            text = { Text(stringResource(I18nR.string.au9_share_large_message, formatSize(dialog.bytes))) },
            confirmButton = { TextButton(onClick = { vm.shareEntries(dialog.entries); onDismiss() }) { Text(stringResource(I18nR.string.au9_share_large_confirm)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
        FilesDialog.Sort -> SortDialog(vm, onDismiss)
        FilesDialog.EnableRoot -> RootWarningDialog(onDismiss) { vm.enableRoot { onDismiss() } }
        is FilesDialog.Archive -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { TelosCyberHeading {  Text(dialog.entry.name)  } },
            text = { Text(stringResource(I18nR.string.hc_look_inside_the_archive_or_unpack_it_int)) },
            confirmButton = { TextButton(onClick = { vm.open(ArchivePath.build(dialog.entry.path, "/")); onDismiss() }) { Text(stringResource(I18nR.string.hc_browse)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.extract(dialog.entry); onDismiss() }) { Text(stringResource(I18nR.string.hc_extract_here)) }
                    TextButton(onClick = { scope.launch { FileActions.openWith(context, dialog.entry, vm.rootMode, vm.shizukuMode); onDismiss() } }) { Text(stringResource(I18nR.string.hc_open_with_2)) }
                }
            },
        )
        is FilesDialog.Unlock -> UnlockVaultDialog(vm, dialog.entry, onDismiss)
        is FilesDialog.Properties -> PropertiesDialog(vm, dialog.entry, onDismiss)
        is FilesDialog.TelosDrop -> FilesSharingDialog(dialog.path, onDismiss)
    }
}

/** Asks for the password of an encrypted archive. A wrong password keeps the dialog open. */
@Composable
private fun ArchivePasswordDialog(vm: FilesViewModel, prompt: ArchivePrompt) {
    var password by remember(prompt) { mutableStateOf("") }
    var visible by remember(prompt) { mutableStateOf(false) }
    var error by remember(prompt) { mutableStateOf<String?>(null) }
    var busy by remember(prompt) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) vm.cancelArchivePrompt() },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au21_arch_pw_title))  } },
        text = {
            Column {
                Text(stringResource(I18nR.string.au21_arch_pw_info, nameOf(prompt.archive)))
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text(stringResource(I18nR.string.hc_password)) }, singleLine = true,
                    visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(stringResource(if (visible) I18nR.string.au21_arch_hide else I18nR.string.au21_arch_show)) } },
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = {
                busy = true
                vm.unlockArchive(password.toCharArray(), onError = { error = it; busy = false }, onDone = { busy = false })
            }) { Text(if (busy) stringResource(I18nR.string.au21_arch_checking) else stringResource(I18nR.string.au21_arch_ok)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { vm.cancelArchivePrompt() }) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

/** Name, format (zip, 7z, tar.gz) and, for zip, an optional AES-256 password */
@Composable
private fun CompressDialog(vm: FilesViewModel, entries: List<FsEntry>, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(entries.first().name.substringBeforeLast('.')) }
    var format by remember { mutableStateOf(CompressFormat.Zip) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var split by remember { mutableStateOf(false) }
    var partMiB by remember { mutableStateOf("100") }
    val splitBytes = if (format == CompressFormat.Zip) splitZipBytes(split, partMiB) else null
    val splitValid = format != CompressFormat.Zip || !split || splitBytes != null
    val mismatch = format.supportsPassword && password.isNotEmpty() && password != repeat
    val hide = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au21_arch_compress))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(I18nR.string.au21_arch_format), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    CompressFormat.values().forEach { f ->
                        Row(Modifier.clickable { format = f }, verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.RadioButton(selected = format == f, onClick = { format = f })
                            Text(stringResource(when (f) {
                                CompressFormat.Zip -> I18nR.string.au21_arch_fmt_zip
                                CompressFormat.SevenZ -> I18nR.string.au21_arch_fmt_7z
                                CompressFormat.TarGz -> I18nR.string.au21_arch_fmt_targz
                            }))
                        }
                    }
                }
                if (format == CompressFormat.Zip) SplitZipOptions(split, { split = it }, partMiB, { partMiB = it })
                if (format.supportsPassword) {
                    OutlinedTextField(
                        password, { password = it }, label = { Text(stringResource(I18nR.string.au21_arch_pw_optional)) }, singleLine = true,
                        visualTransformation = hide,
                        trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(stringResource(if (visible) I18nR.string.au21_arch_hide else I18nR.string.au21_arch_show)) } },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    if (password.isNotEmpty()) {
                        OutlinedTextField(
                            repeat, { repeat = it }, label = { Text(stringResource(I18nR.string.au21_arch_pw_repeat)) }, singleLine = true,
                            visualTransformation = hide,
                            isError = mismatch, supportingText = if (mismatch) { { Text(stringResource(I18nR.string.au21_arch_pw_mismatch)) } } else null,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                    Text(stringResource(I18nR.string.au21_arch_zip_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(stringResource(I18nR.string.au21_arch_no_pw_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && '/' !in name && !mismatch && splitValid, onClick = {
                val secret = if (format.supportsPassword && password.isNotEmpty()) password.toCharArray() else null
                vm.compress(entries, name.trim(), format, secret, splitBytes)
                vm.clearSelection()
                onDismiss()
            }) { Text(stringResource(I18nR.string.hf_files_compress_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, action: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(title)  } },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank() && '/' !in text, onClick = { onDone(text.trim()) }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
private fun SortDialog(vm: FilesViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_sort_by))  } },
        text = {
            Column {
                SortKey.values().forEach { key ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.updateSort(vm.sort.copy(key = key)) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = vm.sort.key == key, onClick = { vm.updateSort(vm.sort.copy(key = key)) })
                        Text(stringResource(key.labelRes), Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SwitchRow(stringResource(I18nR.string.hf_files_sort_descending), !vm.sort.ascending) { vm.updateSort(vm.sort.copy(ascending = !it)) }
                SwitchRow(stringResource(I18nR.string.hf_files_sort_folders_first), vm.sort.foldersFirst) { vm.updateSort(vm.sort.copy(foldersFirst = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_done)) } },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RootWarningDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var understood by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(Icons.terminal_24px), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_root_explorer))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(I18nR.string.hc_telos_files_will_ask_for_superuser_acces))
                Text(
                    stringResource(I18nR.string.hc_careless_changes_here_can_stop_apps_from) +
                        stringResource(I18nR.string.hf_files_root_warning_tail),
                    color = MaterialTheme.colorScheme.error,
                )
                Text(stringResource(I18nR.string.hc_your_root_manager_magisk_kernelsu_will_a))
                Row(Modifier.clickable { understood = !understood }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text(stringResource(I18nR.string.hc_i_understand_the_risks))
                }
            }
        },
        confirmButton = { TextButton(enabled = understood, onClick = onConfirm) { Text(stringResource(I18nR.string.hc_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
private fun PropertiesDialog(vm: FilesViewModel, entry: FsEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var size by remember { mutableStateOf<Long?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    var hashes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var mode by remember { mutableStateOf("") }
    val unavailableLabel = stringResource(I18nR.string.au_files_unavailable)
    val scope = rememberCoroutineScope()
    LaunchedEffect(entry.path) {
        size = vm.sizeOf(entry)
        if (entry.isDir) count = vm.childCount(entry)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Detail(stringResource(I18nR.string.hf_files_prop_type), stringResource(entry.kind.labelRes) + if (entry.extension.isNotEmpty()) " (.${entry.extension})" else "")
                Detail(stringResource(I18nR.string.hf_files_prop_location), parentOf(entry.path) ?: "/")
                Detail(stringResource(I18nR.string.hf_files_prop_size), size?.let { if (it < 0) stringResource(I18nR.string.hf_files_unknown) else formatSize(it) + if (it >= 1024) " (" + stringResource(I18nR.string.hf_files_bytes_exact, it) + ")" else "" } ?: stringResource(I18nR.string.hf_files_calculating))
                count?.let { if (it >= 0) Detail(stringResource(I18nR.string.hf_files_prop_contains), "$it") }
                Detail(stringResource(I18nR.string.hf_files_prop_modified), formatDate(entry.modified).ifEmpty { stringResource(I18nR.string.hf_files_unknown) })
                if (entry.permissions.isNotEmpty()) Detail(stringResource(I18nR.string.hf_files_prop_permissions), entry.permissions)
                if (entry.owner.isNotEmpty()) Detail(stringResource(I18nR.string.hf_files_prop_owner), entry.owner)
                entry.linkTarget?.takeIf { it.isNotEmpty() }?.let { Detail(stringResource(I18nR.string.hf_files_prop_link_to), it) }
                if (!entry.isDir) {
                    HorizontalDivider()
                    Text(stringResource(I18nR.string.hc_checksums), style = MaterialTheme.typography.labelLarge)
                    listOf("MD5" to "MD5", "SHA-1" to "SHA-1", "SHA-256" to "SHA-256").forEach { (label, algorithm) ->
                        val value = hashes[label]
                        if (value == null) {
                            TextButton(onClick = { scope.launch { hashes = hashes + (label to vm.checksum(entry, algorithm).ifEmpty { unavailableLabel }) } }) { Text(stringResource(I18nR.string.hc_calculate_label, label)) }
                        } else Detail(label, value)
                    }
                }
                if (vm.rootMode) {
                    HorizontalDivider()
                    Text(stringResource(I18nR.string.hc_change_permissions_octal_for_example_644), style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(mode, { mode = it.filter { c -> c in '0'..'7' }.take(4) }, singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = mode.length >= 3, onClick = { vm.chmod(entry, mode); onDismiss() }) { Text(stringResource(I18nR.string.hc_apply)) }
                    }
                    if (FileActions.isSystemPath(entry.path, true)) {
                        Text(stringResource(I18nR.string.hc_this_is_a_system_location_a_wrong_permis), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) } },
        dismissButton = {
            if (!entry.isDir) Row {
                TextButton(onClick = { scope.launch { FileActions.openWith(context, entry, vm.rootMode, vm.shizukuMode) } }) { Text(stringResource(I18nR.string.hc_open_with_2)) }
                TextButton(onClick = { vm.shareEntries(listOf(entry)) }) { Text(stringResource(I18nR.string.hc_share)) }
            }
        },
    )
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- bars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int, single: FsEntry?, onClose: () -> Unit, onSelectAll: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit,
    onDelete: () -> Unit, onShare: () -> Unit, onRename: () -> Unit, onCompress: () -> Unit, onProperties: () -> Unit, onBookmark: () -> Unit,
    onOpenAsArchive: (() -> Unit)? = null,
    onEncrypt: (() -> Unit)? = null, onDecrypt: (() -> Unit)? = null,
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au_files_selected_count, count))  } },
        navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(Icons.close_24px), contentDescription = stringResource(I18nR.string.hc_close)) } },
        actions = {
            IconButton(onClick = onShare) { Icon(painterResource(Icons.share_24px), contentDescription = stringResource(I18nR.string.hc_share)) }
            IconButton(onClick = onCopy) { Icon(painterResource(Icons.content_copy_24px), contentDescription = stringResource(I18nR.string.hc_copy)) }
            IconButton(onClick = onCut) { Icon(painterResource(Icons.content_cut_24px), contentDescription = stringResource(I18nR.string.hc_cut)) }
            IconButton(onClick = onDelete) { Icon(painterResource(Icons.delete_24px), contentDescription = stringResource(I18nR.string.hc_delete)) }
            Box {
                IconButton(onClick = { more = true }) { Icon(painterResource(Icons.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more)) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_select_all)) }, onClick = { more = false; onSelectAll() })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_share)) }, onClick = { more = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(I18nR.string.au21_arch_compress)) }, onClick = { more = false; onCompress() })
                    if (onEncrypt != null) DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_encrypt)) }, onClick = { more = false; onEncrypt() })
                    if (onDecrypt != null) DropdownMenuItem(text = { Text(stringResource(I18nR.string.telos_files_decrypt)) }, onClick = { more = false; onDecrypt() })
                    if (onOpenAsArchive != null) DropdownMenuItem(text = { Text(stringResource(I18nR.string.au22_files_open_as_archive)) }, onClick = { more = false; onOpenAsArchive() })
                    DropdownMenuItem(text = { Text(stringResource(if (single != null) I18nR.string.hc_rename else I18nR.string.telos_files_batch_rename)) }, onClick = { more = false; onRename() })
                    if (single != null) {
                        DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_properties)) }, onClick = { more = false; onProperties() })
                        if (single.isDir) DropdownMenuItem(text = { Text(stringResource(I18nR.string.hc_add_to_favorites)) }, onClick = { more = false; onBookmark() })
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, onFilters: () -> Unit) {
    TopAppBar(
        title = {
            TelosCyberHeading {
                de.mm20.launcher2.ui.component.TelosSearchTopBar(
                    value = query, onValueChange = onQuery,
                    placeholder = stringResource(I18nR.string.hc_search_in_this_folder),
                    autoFocus = true,
                )
            }
        },
        navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(Icons.arrow_back_24px), contentDescription = stringResource(I18nR.string.hc_back)) } },
        actions = { IconButton(onClick = onFilters) { Icon(painterResource(Icons.tune_24px), contentDescription = stringResource(I18nR.string.telos_files_search_filters)) } },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun Breadcrumbs(vm: FilesViewModel, onGo: (String?) -> Unit) {
    val p = vm.path ?: return
    val inArchive = ArchivePath.isArchive(p)
    val inVault = de.mm20.launcher2.ui.files.vault.VaultPath.isVault(p)
    val remote = RemotePath.isRemote(p)
    val volume = if (!vm.rootMode && !remote && !inArchive && !inVault) vm.volumes.firstOrNull { p == it.path || p.startsWith(it.path + "/") } else null
    val base = if (remote) "rem://" + RemotePath.idOf(p) else if (inArchive) ArchivePath.build(ArchivePath.archiveOf(p), "/").trimEnd('/') else if (inVault) de.mm20.launcher2.ui.files.vault.VaultPath.build(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(p), "/").trimEnd('/') else volume?.path ?: "/"
    val homeLabel = stringResource(I18nR.string.hf_files_home)
    val storageLabel = stringResource(I18nR.string.hc_storage)
    val crumbs = buildList<Pair<String, String?>> {
        add(homeLabel to null)
        add((if (remote) vm.connectionName(p) ?: storageLabel else if (inArchive) nameOf(ArchivePath.archiveOf(p)) else if (inVault) nameOf(de.mm20.launcher2.ui.files.vault.VaultPath.vaultOf(p)) else volume?.name?.substringBefore(" (") ?: "/") to base)
        var acc = base
        val rest = if (remote) RemotePath.innerOf(p) else if (inArchive) ArchivePath.innerOf(p) else if (inVault) de.mm20.launcher2.ui.files.vault.VaultPath.innerOf(p) else p.removePrefix(base)
        rest.trim('/').split('/').filter { it.isNotEmpty() }.forEach { seg -> acc = joinPath(acc, seg); add(seg to acc) }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(p) { scroll.animateScrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        crumbs.forEachIndexed { i, (label, target) ->
            val last = i == crumbs.lastIndex
            Surface(
                shape = RoundedCornerShape(50),
                color = if (last) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !last) { onGo(target) },
            ) {
                Text(
                    label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), maxLines = 1,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (last) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!last) Icon(painterResource(Icons.arrow_right_24px), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TaskBar(task: TaskState) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(task.title + "…", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { task.cancel.cancelled = true }) { Text(stringResource(I18nR.string.hc_cancel)) }
            }
            if (task.progress != null) LinearProgressIndicator(progress = { task.progress }, Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PasteBar(count: Int, cut: Boolean, onPaste: () -> Unit, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (cut) stringResource(I18nR.string.hf_files_paste_to_move, count) else stringResource(I18nR.string.hf_files_paste_to_copy, count), Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.titleSmall,
            )
            TextButton(onClick = onCancel) { Text(stringResource(I18nR.string.hc_cancel)) }
            Button(onClick = onPaste) { Text(if (cut) stringResource(I18nR.string.hf_files_move_here) else stringResource(I18nR.string.hf_files_paste_here)) }
        }
    }
}

@Composable
private fun RootBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(Icons.terminal_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
            Text(
                stringResource(I18nR.string.hc_system_files_changes_here_can_break_the), Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ---------------------------------------------------------------- pages

@Composable
private fun AccessGate(onAllow: () -> Unit) {
    val backStack = LocalBackStack.current
    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(FileKind.Folder.color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(Icons.folder_24px), contentDescription = null, tint = FileKind.Folder.color, modifier = Modifier.size(44.dp))
            }
            Text(stringResource(I18nR.string.hc_telos_files), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
            Text(
                stringResource(I18nR.string.hc_to_show_and_manage_the_files_on_your_pho),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(onClick = onAllow) { Text(stringResource(I18nR.string.hc_allow_access)) }
            TextButton(onClick = { backStack.removeLastOrNull() }) { Text(stringResource(I18nR.string.hc_not_now)) }
        }
    }
}

@Composable
private fun EmptyPage(text: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(Icons.folder_24px), contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
        Text(text, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorPage(error: String, rootMode: Boolean, onRoot: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(painterResource(Icons.lock_24px), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(error, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!rootMode) TextButton(onClick = onRoot) { Text(stringResource(I18nR.string.hc_open_with_root_explorer)) }
    }
}

@Composable
private fun HomePage(vm: FilesViewModel) {
    val lblDownloads = stringResource(I18nR.string.hf_files_dir_downloads)
    val lblCamera = stringResource(I18nR.string.hf_files_dir_camera)
    val lblPictures = stringResource(I18nR.string.hf_files_dir_pictures)
    val lblMusic = stringResource(I18nR.string.hf_files_dir_music)
    val lblMovies = stringResource(I18nR.string.hf_files_dir_movies)
    val lblDocuments = stringResource(I18nR.string.hf_files_dir_documents)
    val shortcuts = remember(lblDownloads) {
        listOf(
            lblDownloads to Environment.DIRECTORY_DOWNLOADS, lblCamera to Environment.DIRECTORY_DCIM, lblPictures to Environment.DIRECTORY_PICTURES,
            lblMusic to Environment.DIRECTORY_MUSIC, lblMovies to Environment.DIRECTORY_MOVIES, lblDocuments to Environment.DIRECTORY_DOCUMENTS,
        ).map { (label, dir) -> label to Environment.getExternalStoragePublicDirectory(dir).path }
    }
    val colors = listOf(FileKind.Archive.color, FileKind.Image.color, FileKind.Video.color, FileKind.Audio.color, FileKind.Document.color, FileKind.Apk.color)
    val icons = listOf(Icons.download_24px, Icons.photo_24px, Icons.photo_24px, Icons.music_note_24px, Icons.videocam_24px, Icons.description_24px)
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle(stringResource(I18nR.string.hc_storage)) }
        items(vm.volumes, key = { it.path }) { v -> StorageCard(v) { vm.open(v.path) } }
        item { SectionTitle(stringResource(I18nR.string.hf_files_quick_access)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shortcuts.chunked(3).forEachIndexed { row, chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        chunk.forEachIndexed { col, (label, path) ->
                            val i = row * 3 + col
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(colors[i].copy(alpha = 0.14f))
                                    .clickable { if (File(path).exists() || File(path).mkdirs()) vm.open(path) }.padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(painterResource(icons[i]), contentDescription = null, tint = colors[i], modifier = Modifier.size(30.dp))
                                Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        if (vm.bookmarks.isNotEmpty()) {
            item { SectionTitle(stringResource(I18nR.string.hc_favorites)) }
            items(vm.bookmarks, key = { "b$it" }) { b ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.open(b) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeTile(FileKind.Folder, null, false)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(nameOf(b), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(b, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { vm.toggleBookmark(b) }) { Icon(painterResource(Icons.close_20px), contentDescription = stringResource(I18nR.string.hc_remove)) }
                }
            }
        }
        if (vm.connections.isNotEmpty()) {
            item { SectionTitle(stringResource(I18nR.string.hc_network_and_cloud)) }
            items(vm.connections, key = { "c" + it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.open(RemotePath.build(c.id, "/")) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (c.type.cloud) Icons.cloud_20px else Icons.storage_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val space = vm.remoteSpaces[c.id]
                        Text(
                            if (space != null && space.total > 0) stringResource(I18nR.string.au_files_free_of, formatSize(space.free), formatSize(space.total))
                            else c.type.label,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { SectionTitle(stringResource(I18nR.string.hc_tools)) }
        item {
            Card(
                onClick = { vm.open("/") }, enabled = vm.rootMode,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (vm.rootMode) 1f else 0.4f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(Icons.terminal_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                        Text(stringResource(I18nR.string.hc_root_file_system), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            if (vm.rootMode) stringResource(I18nR.string.hf_files_root_on_hint) else stringResource(I18nR.string.hf_files_root_off_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StorageCard(v: StorageVolume, onClick: () -> Unit) {
    val used = (v.total - v.free).coerceAtLeast(0)
    val fraction = if (v.total > 0) used.toFloat() / v.total else 0f
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = virtualAppElevated()),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(Icons.storage_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(I18nR.string.au_files_free_of, formatSize(v.free), formatSize(v.total)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth().padding(top = 14.dp).height(8.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun StorageDrawer(vm: FilesViewModel, onGo: (String?) -> Unit, onRoot: () -> Unit, onManage: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(stringResource(I18nR.string.hc_telos_files), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
        DrawerItem(Icons.home_24px, stringResource(I18nR.string.hf_files_home), null, vm.path == null) { onGo(null) }
        Text(stringResource(I18nR.string.hc_storage), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        vm.volumes.forEach { v ->
            DrawerItem(Icons.storage_24px, v.name, stringResource(I18nR.string.au_files_free, formatSize(v.free)), vm.path?.startsWith(v.path) == true && !vm.rootMode) { onGo(v.path) }
        }
        if (vm.bookmarks.isNotEmpty()) {
            Text(stringResource(I18nR.string.hc_favorites), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
            vm.bookmarks.forEach { b -> DrawerItem(Icons.star_24px_filled, nameOf(b), null, vm.path == b) { onGo(b) } }
        }
        Text(stringResource(I18nR.string.hc_network_and_cloud), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        vm.connections.forEach { c ->
            DrawerItem(
                if (c.type.cloud) Icons.cloud_20px else Icons.storage_24px,
                c.name,
                vm.remoteSpaces[c.id]?.let { if (it.total > 0) stringResource(I18nR.string.au_files_free_of, formatSize(it.free), formatSize(it.total)) else c.type.label } ?: c.type.label,
                vm.path?.startsWith("rem://" + c.id) == true,
            ) { onGo(RemotePath.build(c.id, "/")) }
        }
        DrawerItem(Icons.add_24px, stringResource(I18nR.string.hf_files_add_or_manage), null, false, onManage)
        Text(stringResource(I18nR.string.hc_tools), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        DrawerItem(Icons.terminal_24px, if (vm.shizukuMode) stringResource(I18nR.string.telos_files_shizuku) else if (vm.rootMode) stringResource(I18nR.string.hf_files_root_explorer_on) else stringResource(I18nR.string.hc_root_explorer), if (vm.rootMode) stringResource(I18nR.string.hf_files_tap_to_turn_off) else stringResource(I18nR.string.hf_files_needs_root), vm.rootMode, onRoot)
        if (vm.rootMode) {
            DrawerItem(Icons.folder_24px, stringResource(I18nR.string.hf_files_root_fs), stringResource(I18nR.string.hf_files_system_files), vm.path == "/") { onGo("/") }
            if (!vm.shizukuMode) DrawerItem(Icons.lock_24px, stringResource(I18nR.string.hf_files_system_writable), stringResource(I18nR.string.hf_files_careful), false) { vm.remount("/system", true) }
            if (!vm.shizukuMode) DrawerItem(Icons.lock_open_20px, stringResource(I18nR.string.hf_files_system_readonly), null, false) { vm.remount("/system", false) }
        }
    }
}

@Composable
private fun DrawerItem(icon: Int, label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = 16.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- file items

/** The rounded square with the type icon, or a thumbnail for pictures, with a check mark when selected. */
@Composable
private fun TypeTile(kind: FileKind, thumbnail: String?, selected: Boolean, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.4f))
            .background(if (selected) MaterialTheme.colorScheme.primary else kind.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(painterResource(Icons.check_24px), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            thumbnail != null -> AsyncImage(model = File(thumbnail), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Icon(painterResource(kind.icon), contentDescription = null, tint = kind.color, modifier = Modifier.size(size / 2))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FileRow(e: FsEntry, selected: Boolean, rootMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeTile(e.kind, if (e.kind == FileKind.Image && e.extension != "svg" && !RemotePath.isRemote(e.path) && !ArchivePath.isArchive(e.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(e.path)) e.path else null, selected)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val detail = listOfNotNull(
                if (!e.isDir && e.size >= 0) formatSize(e.size) else null,
                formatDate(e.modified).ifEmpty { null },
                if (rootMode && e.permissions.isNotEmpty()) e.permissions else null,
            ).joinToString("  ·  ")
            if (detail.isNotEmpty()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.linkTarget != null) Text("→ ${e.linkTarget}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GridCell(e: FsEntry, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else virtualAppElevated())
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            TypeTile(e.kind, if (e.kind == FileKind.Image && e.extension != "svg" && !RemotePath.isRemote(e.path) && !ArchivePath.isArchive(e.path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(e.path)) e.path else null, selected, 72.dp)
        }
        Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}


@Composable
private fun UnlockVaultDialog(vm: FilesViewModel, entry: FsEntry, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_unlock_vault))  } },
        text = {
            Column {
                Text(stringResource(I18nR.string.hf_files_vault_info, entry.name))
                OutlinedTextField(
                    password, { password = it; error = null }, label = { Text(stringResource(I18nR.string.hc_password)) }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = {
                busy = true
                vm.unlockVault(entry.path, password, onError = { error = it; busy = false }) { busy = false; onDismiss() }
            }) { Text(if (busy) stringResource(I18nR.string.hf_files_unlocking) else stringResource(I18nR.string.hc_unlock)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
