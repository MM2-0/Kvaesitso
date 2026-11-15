package de.mm20.launcher2.ui.files

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.component.virtualAppElevated
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.telosTechSurface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.ui.R
import java.io.File

internal data class FilesTab(val id: Long, val browser: FilesViewModel, val store: ViewModelStore)

internal class FilesWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val clipboard = FilesClipboard()
    var tabs by mutableStateOf<List<FilesTab>>(emptyList())
        private set
    var left by mutableStateOf(0L)
        private set
    var right by mutableStateOf(0L)
        private set
    var dual by mutableStateOf(false)
        private set
    var activeRight by mutableStateOf(false)
    private var nextId = 0L
    private val prefs = application.getSharedPreferences("telos_files", android.content.Context.MODE_PRIVATE)
    private var restoring = false
    val transferQueue = FilesTransferQueue.get(application)
    init {
        val saved = prefs.getString("workspace", null)?.let { text -> runCatching {
            val defaults = org.json.JSONObject(FilesSettingsTransfer.export(prefs, FilesSettingsTransfer.Workspace(listOf(null), 0, 0, false, false)))
            defaults.put("workspace", org.json.JSONObject(text))
            FilesSettingsTransfer.parse(defaults.toString()).workspace
        }.getOrNull() }
        restore(saved ?: FilesSettingsTransfer.Workspace(listOf(null), 0, 0, false, false))
    }
    fun snapshot() = FilesSettingsTransfer.Workspace(tabs.map { it.browser.path }, tabs.indexOfFirst { it.id == left }.coerceAtLeast(0), tabs.indexOfFirst { it.id == right }.coerceAtLeast(0), dual, activeRight)
    fun persist() { if (!restoring && tabs.isNotEmpty()) prefs.edit().putString("workspace", FilesSettingsTransfer.workspaceJson(snapshot()).toString()).apply() }
    fun restore(saved: FilesSettingsTransfer.Workspace) {
        check(tabs.isEmpty() || canImportSettings())
        restoring = true
        tabs.forEach { it.store.clear() }; tabs = emptyList(); dual = false; activeRight = false
        saved.paths.forEach { addTabAt(it) }
        left = tabs[saved.left].id; right = tabs[saved.right].id
        dual = saved.dual; activeRight = saved.activeRight
        restoring = false; persist()
    }
    fun canImportSettings() = tabs.none { it.browser.task != null } && !transferQueue.hasPending
    fun exportSettings() = FilesSettingsTransfer.export(prefs, snapshot())
    fun importSettings(settings: FilesSettingsTransfer.Settings) {
        check(canImportSettings())
        FilesSettingsTransfer.apply(prefs, settings)
        restore(settings.workspace)
    }
    fun addTab() {
        val current = tabs.firstOrNull { it.id == if (activeRight) right else left }?.browser?.path
        addTabAt(current)
        persist()
    }
    private fun addTabAt(current: String?) {
        if (tabs.size >= FilesSettingsTransfer.MaxTabs) return
        val store = ViewModelStore()
        val browser = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()))[FilesViewModel::class.java]
        browser.sharedClipboard = clipboard
        browser.onFilesChanged = { tabs.filter { it.browser !== browser }.forEach { it.browser.reload() } }
        browser.archiveInUseElsewhere = { archive -> tabs.any { tab ->
            tab.browser !== browser && tab.browser.path?.let { ArchivePath.isArchive(it) && ArchivePath.archiveOf(it) == archive } == true
        } }
        browser.onLocationChanged = { persist() }
        val tab = FilesTab(++nextId, browser, store)
        tabs = tabs + tab
        if (dual && activeRight) right = tab.id else left = tab.id
        browser.open(current)
    }
    fun select(id: Long) {
        when {
            id == left -> activeRight = false
            dual && id == right -> activeRight = true
            dual && activeRight -> right = id
            else -> left = id
        }
        persist()
    }
    fun close(id: Long) {
        if (tabs.size <= 1 || tabs.firstOrNull { it.id == id }?.browser?.task != null) return
        val tab = tabs.first { it.id == id }
        tabs = tabs.filterNot { it.id == id }
        if (left == id) left = tabs.firstOrNull { it.id != right }?.id ?: tabs.first().id
        if (right == id) right = tabs.firstOrNull { it.id != left }?.id ?: left
        if (tabs.size == 1) { dual = false; activeRight = false }
        tab.browser.open(null)
        tab.store.clear()
        persist()
    }
    fun togglePanels() {
        if (dual) { dual = false; activeRight = false; persist(); return }
        if (tabs.size == 1) { addTab() }
        right = tabs.first { it.id != left }.id
        dual = true
        persist()
    }
    override fun onCleared() {
        tabs.forEach { it.store.clear() }
        val app: Application = getApplication()
        File(app.cacheDir, "remote_open").deleteRecursively()
        File(app.cacheDir, "share_dl").deleteRecursively()
        ArchiveSessions.clearAll()
        clipboard.value = null
        super.onCleared()
    }
}

@Composable
fun FilesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val filesPrefs = remember { context.getSharedPreferences("telos_files", android.content.Context.MODE_PRIVATE) }
    var lockOnOpen by remember { mutableStateOf(filesPrefs.getBoolean("lock_on_open", false)) }
    val filesUnlocked by de.mm20.launcher2.comms.privacy.PrivacySession.filesUnlocked.collectAsState()
    val backStack = de.mm20.launcher2.ui.locals.LocalBackStack.current
    de.mm20.launcher2.ui.component.AuthGate(
        enabled = lockOnOpen,
        title = stringResource(I18nR.string.auth_gate_files),
        unlocked = filesUnlocked,
        onUnlocked = { de.mm20.launcher2.comms.privacy.PrivacySession.unlockFiles() },
        onCancelled = { backStack.removeLastOrNull() },
    ) {
    FilesScreenBody()
    }
}

@Composable
private fun FilesScreenBody() {
    val workspace: FilesWorkspaceViewModel = viewModel()
    var dropped by remember { mutableStateOf<Triple<FilesViewModel, FilesInternalDrag, String>?>(null) }
    val dragScope = remember(workspace) { FilesDragScope(java.util.UUID.randomUUID().toString()) { target, data -> target.path?.let { dropped = Triple(target, data, it) } } }
    dropped?.let { (target, data, destination) ->
        AlertDialog(onDismissRequest = { dropped = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_files_drop_title, data.paths.size))  } },
            text = { Text(nameOf(destination)) },
            confirmButton = { TextButton(onClick = { target.transferDragged(data.paths, data.root, false, destination, data.shizuku); dropped = null }) { Text(stringResource(I18nR.string.hf_files_paste_here)) } },
            dismissButton = { Row {
                TextButton(onClick = { target.transferDragged(data.paths, data.root, true, destination, data.shizuku); dropped = null }) { Text(stringResource(I18nR.string.hf_files_move_here)) }
                TextButton(onClick = { dropped = null }) { Text(stringResource(I18nR.string.hc_cancel)) }
            } })
    }
    CompositionLocalProvider(LocalFilesDragScope provides dragScope) {
    Column(Modifier.fillMaxSize().statusBarsPadding().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) false else when (event.key) {
            Key.T -> { workspace.addTab(); true }
            Key.W -> { workspace.close(if (workspace.dual && workspace.activeRight) workspace.right else workspace.left); true }
            Key.Tab -> {
                val active = if (workspace.dual && workspace.activeRight) workspace.right else workspace.left
                val index = workspace.tabs.indexOfFirst { it.id == active }
                val next = (index + if (event.isShiftPressed) workspace.tabs.size - 1 else 1) % workspace.tabs.size
                workspace.select(workspace.tabs[next].id); true
            }
            else -> false
        }
    }) {
        FilesWorkspaceChrome(workspace)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val first = workspace.tabs.first { it.id == workspace.left }
            val second = workspace.tabs.firstOrNull { it.id == workspace.right }
            if (!workspace.dual || second == null) FilesPane(first, true, {}, Modifier.fillMaxSize())
            else if (de.mm20.launcher2.ui.layout.AdaptiveLayoutPolicy.sideBySide(
                maxWidth.value, maxHeight.value, androidx.compose.ui.platform.LocalDensity.current.fontScale,
            )) Row(Modifier.fillMaxSize().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilesPane(first, !workspace.activeRight, { workspace.activeRight = false; workspace.persist() }, Modifier.weight(1f).fillMaxHeight())
                FilesPane(second, workspace.activeRight, { workspace.activeRight = true; workspace.persist() }, Modifier.weight(1f).fillMaxHeight())
            } else {
                FilesPane(if (workspace.activeRight) second else first, true, {}, Modifier.fillMaxSize())
            }
        }
    }
    }
}

private typealias WsIcons = de.mm20.launcher2.base.R.drawable

@Composable
private fun FilesWorkspaceChrome(workspace: FilesWorkspaceViewModel) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = RoundedCornerShape(28.dp))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TelosCyberSectionLabel(stringResource(I18nR.string.hc_telos_files), accent = TelosCyber.NeonCyan, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            workspace.tabs.forEachIndexed { index, tab ->
                val selected = tab.id == if (workspace.activeRight && workspace.dual) workspace.right else workspace.left
                val accent = TelosCyber.accentFor(index)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
                        .then(
                            if (selected) Modifier.border(1.2.dp, accent, RoundedCornerShape(20.dp))
                            else Modifier
                        )
                        .clickable { workspace.select(tab.id) }
                        .padding(start = 14.dp, end = if (workspace.tabs.size > 1) 4.dp else 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.browser.path?.let { nameOf(it) } ?: stringResource(I18nR.string.telos_files_tab, index + 1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 128.dp),
                        color = if (selected) accent else scheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    if (workspace.tabs.size > 1) {
                        Icon(
                            painterResource(WsIcons.close_24px),
                            contentDescription = stringResource(I18nR.string.telos_files_close_tab, index + 1),
                            tint = if (selected) accent else scheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { workspace.close(tab.id) }
                                .padding(5.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
        ChromeIcon(
            icon = WsIcons.add_24px,
            label = stringResource(I18nR.string.telos_files_new_tab),
            enabled = workspace.tabs.size < FilesSettingsTransfer.MaxTabs,
            selected = false,
            onClick = workspace::addTab,
        )
        ChromeIcon(
            icon = WsIcons.dashboard_2_24px,
            label = stringResource(I18nR.string.telos_files_two_panels),
            enabled = true,
            selected = workspace.dual,
            onClick = workspace::togglePanels,
        )
        FilesSettingsTransferButton(workspace)
        }
    }
}

@Composable
internal fun ChromeIcon(
    icon: Int,
    label: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary.copy(alpha = 0.92f) else scheme.onSurface.copy(alpha = 0.08f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = label,
            tint = when {
                !enabled -> scheme.onSurface.copy(alpha = 0.38f)
                selected -> scheme.onPrimary
                else -> scheme.onSurface
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FilesPane(tab: FilesTab, active: Boolean, onActivate: () -> Unit, modifier: Modifier) {
    val scope = LocalFilesDragScope.current
    val activate by rememberUpdatedState(onActivate)
    var receiving by remember { mutableStateOf(false) }
    fun accepts(event: DragAndDropEvent): Boolean {
        val data = event.toAndroidDragEvent().localState as? FilesInternalDrag ?: return false
        val path = tab.browser.path ?: return false
        return data.workspace == scope?.workspace && tab.browser.task == null && !ArchivePath.isArchive(path) && !de.mm20.launcher2.ui.files.vault.VaultPath.isVault(path)
    }
    val target = remember(tab, scope) { object : DragAndDropTarget {
        override fun onEntered(event: DragAndDropEvent) { receiving = accepts(event) }
        override fun onExited(event: DragAndDropEvent) { receiving = false }
        override fun onEnded(event: DragAndDropEvent) { receiving = false }
        override fun onDrop(event: DragAndDropEvent): Boolean {
            receiving = false
            if (!accepts(event)) return false
            val data = event.toAndroidDragEvent().localState as FilesInternalDrag
            activate(); scope?.dropped?.invoke(tab.browser, data); return true
        }
    } }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .dragAndDropTarget(::accepts, target)
            .pointerInput(onActivate) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) onActivate()
                    }
                }
            },
    ) {
        FilesBrowser(tab.browser, handlesBack = active)
        if (receiving) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            )
        }
    }
}
