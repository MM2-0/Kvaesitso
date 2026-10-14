package dev.intelia.launcher.ui

import android.app.Application as AndroidApplication
import android.app.role.RoleManager
import android.content.Context
import android.net.Uri
import android.os.Process
import android.os.Build
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.appshortcuts.AppShortcutRepository
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.backup.BackupCompatibility
import de.mm20.launcher2.backup.BackupManager
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.searchactions.SearchActionRepository
import de.mm20.launcher2.searchactions.builders.FlowAliasActionBuilder
import de.mm20.launcher2.searchactions.flow.*
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalCoroutinesApi::class)
class InteliaHomeViewModel(application: AndroidApplication) : AndroidViewModel(application), KoinComponent {
    private val appRepository: AppRepository by inject()
    private val shortcutRepository: AppShortcutRepository by inject()
    private val saved: SavableSearchableRepository by inject()
    private val repository: SearchActionRepository by inject()
    private val runtime: FlowAliasRuntime by inject()
    private val preferences: InteliaUiPreferences by inject()
    private val backups: BackupManager by inject()
    private val context get() = getApplication<AndroidApplication>()
    val query = MutableStateFlow("")
    val ui = preferences.state
    val message = MutableStateFlow<Int?>(null)
    val editor = MutableStateFlow(false)
    val editingAlias = MutableStateFlow<FlowAlias?>(null)
    val aliasError = MutableStateFlow<String?>(null)
    val savingAlias = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val restoreReady = MutableStateFlow(false)
    private var restoreUri: Uri? = null
    val isDefaultHome = MutableStateFlow(false)

    private val hidden = saved.getKeys(includeTypes = listOf("app"), minVisibility = VisibilityLevel.Hidden,
        maxVisibility = VisibilityLevel.Hidden, limit = Int.MAX_VALUE)
    private val hiddenItems = saved.getKeys(minVisibility = VisibilityLevel.Hidden, maxVisibility = VisibilityLevel.Hidden, limit = Int.MAX_VALUE)
    val apps: StateFlow<List<Application>> = query.flatMapLatest { text ->
        if (text.isBlank()) appRepository.findMany().map { it.toList() } else appRepository.search(text, false)
    }.combine(hidden) { installed, hiddenKeys ->
        val excluded = hiddenKeys.toHashSet()
        installed.filter { it.user == Process.myUserHandle() && !it.isPrivate && !it.isSuspended &&
            it.key !in excluded && it.componentName.packageName != context.packageName }
            .sortedWith(compareByDescending<Application> { if (query.value.isBlank()) 0f else it.score.score }
                .thenBy { (it.labelOverride ?: it.label).lowercase() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hiddenApps = combine(appRepository.findMany(), hidden) { installed, hiddenKeys ->
        installed.filter { it.key in hiddenKeys && it.user == Process.myUserHandle() && !it.isPrivate }
            .sortedBy { it.label }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val targets = runtime.targets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val arcApps = combine(appRepository.findMany(), hidden, ui) { installed, hiddenKeys, preferences ->
        preferences.arc.map { key -> installed.find { it.key == key && it.key !in hiddenKeys &&
            it.user == Process.myUserHandle() && !it.isPrivate && !it.isSuspended && it.componentName.packageName != context.packageName } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val eligible = combine(appRepository.findMany(), hidden) { installed, hiddenKeys ->
        installed.filter { it.key !in hiddenKeys && it.user == Process.myUserHandle() && !it.isPrivate && !it.isSuspended }
    }
    val shortcuts: StateFlow<List<AppShortcut>> = query.flatMapLatest { text ->
        if (text.trim().length < 3) flowOf(emptyList()) else shortcutRepository.search(text, false).map { it.toList() }
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }
    }.combine(eligible) { matches, parents -> matches.filter { shortcut ->
        shortcut.user == Process.myUserHandle() && !shortcut.isUnavailable && parents.any { it.componentName.packageName == shortcut.packageName }
    }.take(8) }.combine(hiddenItems) { matches, hiddenKeys -> matches.filter { it.key !in hiddenKeys } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())
    suspend fun launchShortcut(shortcut: AppShortcut, activity: Context): Boolean {
        if (eligible.first().none { it.componentName.packageName == shortcut.packageName } || shortcut.user != Process.myUserHandle() || shortcut.key in hiddenItems.first()) return false
        val current = shortcutRepository.findMany(user = Process.myUserHandle(), manifest = true, dynamic = true, pinned = true, limit = 1000)
            .first().find { it.key == shortcut.key && !it.isUnavailable } ?: return false
        currentCoroutineContext().ensureActive()
        return current.launch(activity, null)
    }
    fun toggleArc(app: Application) = changeUi { state ->
        if (app.key in state.arc) state.copy(arc = state.arc - app.key)
        else if (state.arc.size < 5) state.copy(arc = state.arc + app.key) else state
    }
    val aliases = repository.getSearchActionBuilders().map { list -> list.filterIsInstance<FlowAliasActionBuilder>().map { it.alias } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val actions = combine(query, aliases, targets) { text, savedAliases, currentTargets ->
        if (text.isBlank()) emptyList() else savedAliases.filter { FlowAliases.matches(it, text) }
            .distinctBy { FlowAliases.normalize(it.alias) }.mapNotNull { alias ->
                currentTargets.find { it.key == alias.targetKey }?.let { FlowAliasAction(alias, it, runtime) }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshHomeRole() {
        isDefaultHome.value = if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true
        } else {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName == context.packageName
        }
    }
    fun edit(alias: FlowAlias?) { editingAlias.value = alias; aliasError.value = null; editor.value = true }
    fun hide(app: Application) { saved.upsert(app, visibility = VisibilityLevel.Hidden) }
    fun unhide(app: Application) { saved.upsert(app, visibility = VisibilityLevel.Default) }
    suspend fun launch(app: Application, activity: Context): Boolean {
        val current = appRepository.findMany().first().find { it.key == app.key } ?: return false
        if (current.user != Process.myUserHandle() || current.isPrivate || current.isSuspended || current.key in hidden.first()) return false
        currentCoroutineContext().ensureActive()
        return current.launch(activity, null)
    }
    fun dismissEditor() { if (!savingAlias.value) editor.value = false }
    fun save(alias: FlowAlias) = mutateAlias {
        val available = runtime.targets().first().any { it.key == alias.targetKey }
        val error = if (available) repository.saveFlowAlias(alias) else FlowAliasError.InvalidTarget
        aliasError.value = error?.let { context.getString(when (it) {
            FlowAliasError.Empty -> R.string.flow_alias_empty
            FlowAliasError.ControlCharacter -> R.string.flow_alias_control
            FlowAliasError.TooLong -> R.string.flow_alias_too_long
            FlowAliasError.Duplicate -> R.string.flow_alias_duplicate
            FlowAliasError.InvalidTarget -> R.string.flow_alias_target_unavailable
        }) }
        if (error == null) editor.value = false
    }
    fun delete(id: String) = mutateAlias { repository.removeFlowAlias(id); editor.value = false }
    private fun mutateAlias(block: suspend () -> Unit) {
        if (savingAlias.value) return
        savingAlias.value = true
        viewModelScope.launch {
            try { block() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { aliasError.value = context.getString(R.string.flow_alias_save_failed) }
            finally { savingAlias.value = false }
        }
    }
    fun theme(mode: Int) = changeUi { it.copy(theme = mode) }
    fun clock(enabled: Boolean) = changeUi { it.copy(showClock = enabled) }
    fun motion(reduced: Boolean) = changeUi { it.copy(reduceMotion = reduced) }
    fun wallpaper(mode: Int) = changeUi { it.copy(wallpaper = mode) }
    private fun changeUi(transform: (InteliaUiState) -> InteliaUiState) {
        viewModelScope.launch {
            try { preferences.update(transform) } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = R.string.intelia_settings_failed }
        }
    }
    fun export(uri: Uri) = backupJob(R.string.intelia_backup_done) { backups.backup(uri) }
    fun prepareRestore(uri: Uri) {
        if (busy.value) return
        viewModelScope.launch {
            try {
                val meta = backups.readBackupMeta(uri)
                if (meta == null || backups.checkCompatibility(meta) == BackupCompatibility.Incompatible) {
                    message.value = R.string.intelia_invalid_backup
                } else { restoreUri = uri; restoreReady.value = true }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = R.string.intelia_invalid_backup }
        }
    }
    fun cancelRestore() { restoreReady.value = false; restoreUri = null }
    fun restore() {
        val uri = restoreUri ?: return
        cancelRestore()
        backupJob(R.string.intelia_restore_done) { backups.restore(uri) }
    }
    fun exportSource(uri: Uri) = backupJob(R.string.intelia_source_done) {
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Cannot create source file")
            output.use { stream -> context.assets.open("intelia-source.zip").use { it.copyTo(stream) } }
        }
    }
    private fun backupJob(success: Int, block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block(); message.value = success }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message.value = R.string.intelia_backup_failed }
            finally { busy.value = false }
        }
    }
}
