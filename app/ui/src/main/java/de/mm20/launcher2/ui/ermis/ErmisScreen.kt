package de.mm20.launcher2.ui.ermis

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.net.Uri
import android.content.Intent
import android.widget.Toast
import android.provider.ContactsContract
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.flow.first
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.luminance
import de.mm20.launcher2.ui.locals.LocalBackStack
import android.accounts.AccountManager
import androidx.activity.compose.LocalActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.ermis.*
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosCyberAppBarTitle
import de.mm20.launcher2.ui.component.TelosCyberSectionLabel
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.telosTechSurface
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Serializable
data object ErmisRoute : NavKey

@Composable
fun ErmisScreen() {
    // VirtualAppTheme deliberately clears surface colors. Reconstruct readable mail surfaces
    // from a tonal container before applying translucency; text must never sit on raw wallpaper.
    val inherited = MaterialTheme.colorScheme
    val dark = inherited.onSurface.luminance() > .5f
    val paper = if (dark) androidx.compose.ui.graphics.Color(0xFF141A20) else androidx.compose.ui.graphics.Color(0xFFF6F8FC)
    val ink = if (dark) androidx.compose.ui.graphics.Color(0xFFE6EAF0) else androidx.compose.ui.graphics.Color(0xFF18212A)
    val colors = remember(inherited, dark) { inherited.copy(surface = paper, background = paper,
        surfaceContainer = paper, surfaceContainerLow = paper, surfaceContainerHigh = paper,
        surfaceContainerHighest = paper, onSurface = ink, onBackground = ink,
        onSurfaceVariant = ink.copy(alpha = .78f)) }
    MaterialTheme(colorScheme = colors) {
        Surface(color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.fillMaxSize()) { ErmisMailbox() }
    }
}

@Composable
private fun ErmisMailbox() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val backStack = LocalBackStack.current
    val repository = remember { ErmisRepository.get(context.applicationContext) }
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var starred by rememberSaveable { mutableStateOf(false) }
    var accounts by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    var showingDrafts by remember { mutableStateOf(false) }
    var editingDraft by remember { mutableStateOf<ErmisSavedDraft?>(null) }
    var reply by remember { mutableStateOf<ErmisMessage?>(null) }
    var replyAll by remember { mutableStateOf(false) }
    var forwarding by remember { mutableStateOf<ErmisForwardSource?>(null) }
    var mailboxId by rememberSaveable { mutableStateOf("INBOX") }
    var foldersOpen by remember { mutableStateOf(false) }
    var editingFolder by remember { mutableStateOf<ErmisFolder?>(null) }
    var folderEditorOpen by remember { mutableStateOf(false) }
    var folderName by rememberSaveable { mutableStateOf("") }
    var moving by remember { mutableStateOf<ErmisMessage?>(null) }
    var moveTarget by remember { mutableStateOf<Pair<ErmisMessage, ErmisFolder>?>(null) }
    var serverResults by remember { mutableStateOf<Set<Long>?>(null) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var selected by remember { mutableStateOf<ErmisMessage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun task(block: suspend () -> Unit) { scope.launch { try { block(); repository.state.value.error?.let { error = it } } catch (e: CancellationException) { throw e } catch (_: Exception) { error = repository.state.value.error ?: context.getString(I18nR.string.ermis_operation_failed) } } }
    fun selectAccount(id: String?) {
        accountId = id; mailboxId = "INBOX"; serverResults = null
        if (id != null) task { repository.loadFolders(id); repository.sync(id) }
        else task { repository.sync() }
    }
    LaunchedEffect(Unit) {
        val initial = repository.state.first { !it.loading }
        if (initial.accounts.isNotEmpty()) task { repository.sync() }
    }
    val accountFolders = remember(state.folders, accountId) { state.folders.filter { it.accountId == accountId } }
    val visible = remember(state.messages, accountId, mailboxId, starred, query, serverResults) { state.messages.filter { message ->
        (accountId == null || message.accountId == accountId) && message.folder == mailboxId &&
            (serverResults == null || message.uid in serverResults.orEmpty()) && (!starred || message.starred) &&
            (serverResults != null || query.isBlank() || listOf(message.from, message.subject, message.preview).any { it.contains(query, true) })
    }.sortedByDescending { it.date } }
    val visibleDrafts = remember(state.drafts, accountId, query) {
        state.drafts.filter { draft -> (accountId == null || draft.accountId == accountId) &&
            (query.isBlank() || listOf(draft.to, draft.cc, draft.bcc, draft.subject, draft.body).any { it.contains(query, true) }) }
    }
    val conversations = remember(visible) { ErmisConversations.group(visible) }
    val selectedConversation = remember(state.messages, selected?.accountId, selected?.folder, selected?.uid) { ErmisConversations.group(state.messages).firstOrNull { thread -> thread.any { it.accountId == selected?.accountId && it.folder == selected?.folder && it.uid == selected?.uid } }.orEmpty() }
    val dateFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    var showFontDialog by remember { mutableStateOf(false) }
    if (showFontDialog) {
        de.mm20.launcher2.ui.component.PerAppFontScaleDialog(
            appKey = "ermis",
            appName = stringResource(I18nR.string.ermis_title),
            onDismiss = { showFontDialog = false }
        )
    }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .telosTechSurface(borderColor = TelosCyber.NeonCyan, shape = MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TelosCyberAppBarTitle(
                    title = stringResource(I18nR.string.ermis_title),
                    status = mailboxId,
                    statusColor = TelosCyber.NeonCyan,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    IconButton(onClick = { backStack.removeLastOrNull() }) {
                        Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_close))
                    }
                    OutlinedTextField(
                        query, { query = it; serverResults = null },
                        placeholder = { Text(stringResource(I18nR.string.ermis_search)) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    IconButton(onClick = { showFontDialog = true }) {
                        Icon(painterResource(BaseR.drawable.settings_24px), contentDescription = stringResource(I18nR.string.per_app_font_size))
                    }
                    IconButton(onClick = { accounts = true }) {
                        Icon(painterResource(BaseR.drawable.person_24px), stringResource(I18nR.string.ermis_accounts))
                    }
                }
            }
        },
        floatingActionButton = { if (state.accounts.isNotEmpty()) ExtendedFloatingActionButton(
            onClick = { editingDraft = null; reply = null; replyAll = false; forwarding = null; composing = true }, icon = { Icon(painterResource(BaseR.drawable.edit_24px), null) },
            text = { Text(stringResource(I18nR.string.ermis_compose)) }) }
    ) { padding ->
      LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelosTechPill(stringResource(I18nR.string.ermis_inbox), !starred && !showingDrafts && mailboxId == "INBOX", { starred = false; showingDrafts = false; mailboxId = "INBOX"; serverResults = null; task { repository.sync(accountId) } }, accentColor = TelosCyber.NeonCyan)
                    TelosTechPill(stringResource(I18nR.string.ermis_starred), starred && !showingDrafts, { starred = true; showingDrafts = false }, accentColor = TelosCyber.NeonOrange)
                    TelosTechPill(stringResource(I18nR.string.ermis_drafts), showingDrafts, { showingDrafts = true }, accentColor = TelosCyber.NeonMagenta)
                    TextButton(onClick = { task { repository.sync(accountId, mailboxId); serverResults = null } }, enabled = !state.loading && state.accounts.isNotEmpty()) { Text(stringResource(I18nR.string.ermis_sync)) }
                }
                if (state.accounts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(accountId == null, onClick = { selectAccount(null) }, label = { Text(stringResource(I18nR.string.ermis_all_accounts)) })
                        state.accounts.forEach { account -> FilterChip(accountId == account.id, onClick = { selectAccount(account.id) }, label = { Text(account.email) }) }
                    }
                }
                if (state.accounts.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = !state.loading, onClick = { foldersOpen = true; accountId?.let { id -> task { repository.loadFolders(id) } } }) { Text(stringResource(I18nR.string.ermis_folders)) }
                        if (accountId != null && query.isNotBlank()) TextButton(enabled = !state.loading, onClick = {
                            val id = accountId ?: return@TextButton
                            val searchedFolder = mailboxId
                            val searchedQuery = query
                            task {
                                val matches = repository.serverSearch(id, searchedFolder, searchedQuery).map { it.uid }.toSet()
                                if (accountId == id && mailboxId == searchedFolder && query == searchedQuery) serverResults = matches
                            }
                        }) { Text(stringResource(I18nR.string.ermis_search_server)) }
                        if (serverResults != null) FilterChip(true, onClick = { serverResults = null }, label = { Text(stringResource(I18nR.string.ermis_show_folder)) })
                    }
                    if (!showingDrafts) Text(if (serverResults == null) mailboxId else stringResource(I18nR.string.ermis_server_results), style = MaterialTheme.typography.labelLarge)
                    if (accountId != null && query.isNotBlank()) Text(stringResource(I18nR.string.ermis_search_server_help), style = MaterialTheme.typography.bodySmall)
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (state.accounts.isEmpty()) item {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = { adding = true }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(I18nR.string.ermis_add_account), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(I18nR.string.ermis_native_intro))
                }
            }
        }
        if (!showingDrafts && visible.isEmpty() && state.accounts.isNotEmpty() && !state.loading) item { Text(stringResource(I18nR.string.ermis_empty), Modifier.padding(24.dp)) }
        if (showingDrafts) items(visibleDrafts, key = { "draft:${it.id}" }) { draft ->
            ListItem(headlineContent = { Text(draft.subject.ifBlank { stringResource(I18nR.string.ermis_no_subject) }) },
                supportingContent = { Text(draft.to.ifBlank { draft.cc.ifBlank { draft.bcc } } + "\n" + draft.body.take(160), maxLines = 3) },
                leadingContent = { Icon(painterResource(BaseR.drawable.draft_24px), null) },
                trailingContent = { IconButton(onClick = { task { repository.deleteDraft(draft.id) } }) { Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.ermis_remove)) } },
                modifier = Modifier.clickable { editingDraft = draft; reply = null; replyAll = false; forwarding = draft.forwardSource; composing = true })
        }
        if (!showingDrafts) items(conversations, key = { "${it.first().accountId}:${it.first().folder}:${it.first().uid}" }) { conversation ->
            val message = conversation.first()
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .telosTechSurface(borderColor = TelosCyber.accentFor(message.uid.hashCode()), shape = MaterialTheme.shapes.medium),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            ) {
                Row(Modifier.fillMaxWidth().clickable {
                    selected = message
                    task { val loaded = repository.loadMessage(message.accountId, message.uid, message.folder) ?: message; if (selected?.accountId == message.accountId && selected?.folder == message.folder && selected?.uid == message.uid) selected = loaded; repository.loadFolders(message.accountId) }
                }.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(message.from.firstOrNull()?.uppercase() ?: "@") }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(message.from, fontWeight = if (message.read) FontWeight.Normal else FontWeight.Bold)
                        Text(message.subject.ifBlank { context.getString(I18nR.string.ermis_no_subject) }, style = MaterialTheme.typography.titleMedium, fontWeight = if (message.read) FontWeight.Normal else FontWeight.SemiBold)
                        Text(message.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(dateFormatter.format(Date(message.date)), style = MaterialTheme.typography.labelSmall)
                        if (conversation.size > 1) Text(stringResource(I18nR.string.ermis_conversation_count, conversation.size), style = MaterialTheme.typography.labelSmall)
                    }
                    IconToggleButton(checked = message.starred, onCheckedChange = { task { repository.star(message.accountId, message.uid, it, message.folder) } }) { Icon(painterResource(BaseR.drawable.star_24px), stringResource(I18nR.string.ermis_starred), tint = if (message.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                }
            }
        }
      }
    }
    if (accounts) AlertDialog(onDismissRequest = { accounts = false }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_accounts))  } }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.accounts.forEach { account ->
                Text(account.email, style = MaterialTheme.typography.titleMedium)
                Text("IMAP ${account.imapHost}:${account.imapPort}\nSMTP ${account.smtpHost}:${account.smtpPort}", style = MaterialTheme.typography.bodySmall)
                val googleAccount = account.googleAccount
                if (googleAccount != null && activity != null) TextButton(enabled = !state.loading, onClick = {
                    task { repository.addAccount(account, ErmisGoogleAuth.consent(activity, googleAccount)); repository.sync(account.id) }
                }) { Text(stringResource(I18nR.string.ermis_google_sign_in)) }
                TextButton(onClick = { task { repository.removeAccount(account.id); if (accountId == account.id) accountId = null } }) { Text(stringResource(I18nR.string.ermis_remove)) }
            }
            Button(onClick = { adding = true; accounts = false }) { Text(stringResource(I18nR.string.ermis_add_account)) }
        }
    }, confirmButton = { TextButton(onClick = { accounts = false }) { Text(stringResource(I18nR.string.hc_close)) } })
    if (adding) AddAccountDialog(state.loading, onDismiss = { adding = false }, onAuthError = { error = context.getString(I18nR.string.ermis_google_unavailable) }) { account, password -> task { repository.addAccount(account, password); adding = false; if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS); repository.sync(account.id) } }
    fun closeComposer() { composing = false; reply = null; editingDraft = null; forwarding = null; replyAll = false }
    if (composing) ComposeDialog(state.accounts, accountId, reply, replyAll, forwarding, editingDraft, state.loading,
        onDismiss = { closeComposer() },
        onSave = { id, draft -> task { repository.saveDraft(id, draft); closeComposer() } },
        onDiscard = { id -> task { repository.deleteDraft(id); closeComposer() } },
        onSend = { id, draft -> task {
            repository.send(draft)
            val sentWarning = repository.state.value.error
            closeComposer() // SMTP already accepted the mail; never leave Send available during cache cleanup.
            if (repository.state.value.drafts.any { it.id == id }) repository.deleteDraft(id)
            error = sentWarning
        } })
    selected?.let { message ->
        val messageFolders = remember(state.folders, message.accountId) { state.folders.filter { it.accountId == message.accountId } }
        MessageDialog(message, conversation = selectedConversation,
        folders = messageFolders,
        onSelect = { other -> selected = other; task { val loaded = repository.loadMessage(other.accountId, other.uid, other.folder) ?: other; if (selected?.accountId == other.accountId && selected?.folder == other.folder && selected?.uid == other.uid) selected = loaded } },
        onDismiss = { selected = null },
        onReply = { editingDraft = null; replyAll = false; forwarding = null; reply = message; selected = null; composing = true },
        onReplyAll = { editingDraft = null; replyAll = true; forwarding = null; reply = message; selected = null; composing = true },
        onForward = { task {
            val source = repository.forwardSource(message.accountId, message.folder, message.uid)
            editingDraft = null; replyAll = false; forwarding = source; reply = message; selected = null; composing = true
        } },
        onMarkUnread = { task { repository.setRead(message.accountId, message.uid, false, message.folder); selected = null } },
        onMove = { target -> moveTarget = message to target },
        onMoveAny = { moving = message; task { repository.loadFolders(message.accountId) } },
        onSaveNote = { task { repository.saveNote(message.accountId, message.uid, it, message.folder); selected = selected?.copy(note = it) } },
        onDownload = { attachment, uri -> task { repository.downloadAttachment(message.accountId, message.uid, attachment.index, uri, message.folder) } }) }
    if (foldersOpen) AlertDialog(onDismissRequest = { foldersOpen = false }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_folders))  } }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            if (accountId == null) {
                item { Text(stringResource(I18nR.string.ermis_select_account)) }
                items(state.accounts, key = { it.id }) { account -> TextButton(enabled = !state.loading, onClick = { selectAccount(account.id) }) { Text(account.email) } }
            } else {
                item { TextButton(enabled = !state.loading, onClick = {
                    editingFolder = null; folderName = ""; folderEditorOpen = true; foldersOpen = false
                }) { Text(stringResource(I18nR.string.hc_new_folder)) } }
                items(accountFolders, key = { it.id }) { folder ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(modifier = Modifier.weight(1f), enabled = !state.loading, onClick = { mailboxId = folder.id; foldersOpen = false; serverResults = null; showingDrafts = false; query = ""; task { repository.sync(accountId, folder.id) } }) { Text(folder.name) }
                        if (folder.role == ErmisFolderRole.OTHER) TextButton(enabled = !state.loading, onClick = {
                            editingFolder = folder; folderName = folder.id; folderEditorOpen = true; foldersOpen = false
                        }) { Text(stringResource(I18nR.string.hc_rename)) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { foldersOpen = false }) { Text(stringResource(I18nR.string.hc_close)) } })
    if (folderEditorOpen) AlertDialog(onDismissRequest = { if (!state.loading) folderEditorOpen = false },
        title = { TelosCyberHeading {  Text(stringResource(if (editingFolder == null) I18nR.string.hc_new_folder else I18nR.string.hf_folder_rename))  } },
        text = { OutlinedTextField(folderName, { folderName = it }, enabled = !state.loading,
            label = { Text(stringResource(I18nR.string.hf_folder_name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = !state.loading && folderName.isNotBlank() && accountId != null, onClick = {
            val id = accountId ?: return@TextButton
            val original = editingFolder
            val requestedName = folderName
            task {
                if (original == null) repository.createFolder(id, requestedName)
                else repository.renameFolder(id, original.id, requestedName)
                folderEditorOpen = false; foldersOpen = true
                if (original != null && mailboxId == original.id) {
                    mailboxId = requestedName.trim(); serverResults = null; selected = null
                }
                repository.loadFolders(id)
                repository.sync(id, mailboxId)
            }
        }) { Text(stringResource(I18nR.string.hc_save)) } },
        dismissButton = { TextButton(enabled = !state.loading, onClick = { folderEditorOpen = false }) { Text(stringResource(I18nR.string.hc_cancel)) } })
    moving?.let { message ->
        val choices = remember(state.folders, message.accountId, message.folder) { state.folders.filter { it.accountId == message.accountId && it.id != message.folder } }
        AlertDialog(onDismissRequest = { moving = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_move))  } }, text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) { items(choices, key = { it.id }) { folder ->
                TextButton(enabled = !state.loading, onClick = { moving = null; moveTarget = message to folder }) { Text(folder.name) }
            } }
        }, confirmButton = { TextButton(onClick = { moving = null }) { Text(stringResource(I18nR.string.hc_cancel)) } })
    }
    moveTarget?.let { (message, target) -> AlertDialog(onDismissRequest = { moveTarget = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_move))  } }, text = {
        Text(message.subject + "\n" + target.name)
    }, confirmButton = { TextButton(enabled = !state.loading, onClick = { task {
        repository.moveMessage(message.accountId, message.uid, message.folder, target.id)
        moveTarget = null; selected = null; serverResults = null; repository.sync(accountId, mailboxId)
    } }) { Text(stringResource(I18nR.string.hc_save)) } }, dismissButton = { TextButton(onClick = { moveTarget = null }) { Text(stringResource(I18nR.string.hc_cancel)) } }) }
    error?.let { detail -> AlertDialog(onDismissRequest = { error = null }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_operation_failed))  } }, text = { Text(detail, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(I18nR.string.hc_close)) } }) }
}

@Composable
private fun AddAccountDialog(busy: Boolean, onDismiss: () -> Unit, onAuthError: () -> Unit, onConnect: (ErmisAccount, String) -> Unit) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var authenticating by remember { mutableStateOf(false) }
    val googleChooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.takeIf { it.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE) == "com.google" }?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode == android.app.Activity.RESULT_OK && email != null && activity != null) scope.launch {
            authenticating = true
            try {
                val token = ErmisGoogleAuth.consent(activity, email)
                onConnect(ErmisAccount(UUID.randomUUID().toString(), email, email, "imap.gmail.com", 993, "smtp.gmail.com", googleAccount = email), token)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { onAuthError() }
            finally { authenticating = false }
        } else if (result.resultCode == android.app.Activity.RESULT_OK) onAuthError()
    }
    var email by rememberSaveable { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }; var imap by rememberSaveable { mutableStateOf("") }; var smtp by rememberSaveable { mutableStateOf("") }
    var imapPort by rememberSaveable { mutableStateOf("993") }; var smtpPort by rememberSaveable { mutableStateOf("465") }; var startTls by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_add_account))  } }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(I18nR.string.ermis_tls_help))
            OutlinedButton(enabled = !busy && !authenticating && activity != null, onClick = {
                runCatching { googleChooser.launch(AccountManager.newChooseAccountIntent(null, null, arrayOf("com.google"), null, null, null, null)) }.onFailure { onAuthError() }
            }) { Text(stringResource(I18nR.string.ermis_google_sign_in)) }
            Text(stringResource(I18nR.string.ermis_google_help), style = MaterialTheme.typography.bodySmall)
            if (authenticating) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it; if (it.trim().endsWith("@gmail.com", true) || it.trim().endsWith("@googlemail.com", true)) { imap = "imap.gmail.com"; smtp = "smtp.gmail.com" } }, label = { Text(stringResource(I18nR.string.ermis_email)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(username, { username = it }, label = { Text(stringResource(I18nR.string.ermis_username)) }, supportingText = { Text(stringResource(I18nR.string.ermis_username_help)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(I18nR.string.ermis_password)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(imap, { imap = it }, label = { Text(stringResource(I18nR.string.ermis_imap_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(imapPort, { imapPort = it.filter(Char::isDigit) }, label = { Text(stringResource(I18nR.string.ermis_imap_port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(smtp, { smtp = it }, label = { Text(stringResource(I18nR.string.ermis_smtp_host)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(smtpPort, { smtpPort = it.filter(Char::isDigit) }, label = { Text(stringResource(I18nR.string.ermis_smtp_port)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(startTls, { startTls = it; smtpPort = if (it) "587" else "465" }); Text(stringResource(I18nR.string.ermis_starttls), Modifier.weight(1f)) }
        }
    }, confirmButton = { TextButton(enabled = !busy && !authenticating && email.contains('@') && password.isNotEmpty() && imap.isNotBlank() && smtp.isNotBlank() && (imapPort.toIntOrNull() ?: 0) in 1..65535 && (smtpPort.toIntOrNull() ?: 0) in 1..65535, onClick = {
        onConnect(ErmisAccount(UUID.randomUUID().toString(), email.trim(), email.trim(), imap.trim(), imapPort.toInt(), smtp.trim(), smtpPort.toInt(), startTls, username.trim().ifBlank { email.trim() }), password)
    }) { Text(stringResource(I18nR.string.ermis_connect)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } })
}

@Composable
private fun ComposeDialog(accounts: List<ErmisAccount>, initialAccount: String?, reply: ErmisMessage?, replyAll: Boolean, forwarding: ErmisForwardSource?, saved: ErmisSavedDraft?, busy: Boolean, onDismiss: () -> Unit, onSave: (String, ErmisDraft) -> Unit, onDiscard: (String) -> Unit, onSend: (String, ErmisDraft) -> Unit) {
    val context = LocalContext.current
    val recipients = remember(reply, replyAll, accounts, forwarding) {
        if (reply == null || forwarding != null) ErmisReplyRecipients("", "")
        else runCatching { ErmisReplies.recipients(reply, accounts.map { it.email }, replyAll) }.getOrDefault(ErmisReplyRecipients("", ""))
    }
    var original by remember { mutableStateOf(saved?.forwardSource ?: forwarding) }
    val draftId = rememberSaveable { saved?.id ?: UUID.randomUUID().toString() }
    var confirmClose by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(saved?.accountId ?: reply?.accountId ?: initialAccount ?: accounts.firstOrNull()?.id) }
    var to by rememberSaveable { mutableStateOf(saved?.to ?: recipients.to) }; var subject by rememberSaveable { mutableStateOf(saved?.subject ?: reply?.subject?.let { if (forwarding != null) { if (it.startsWith("Fwd:", true)) it else "Fwd: $it" } else if (it.startsWith("Re:", true)) it else "Re: $it" }.orEmpty()) }; var body by rememberSaveable { mutableStateOf(saved?.body ?: if (forwarding != null) reply?.body.orEmpty() else "") }
    var cc by rememberSaveable { mutableStateOf(saved?.cc ?: recipients.cc) }
    var bcc by rememberSaveable { mutableStateOf(saved?.bcc.orEmpty()) }
    var attachments by remember { mutableStateOf(saved?.attachmentUris?.map(Uri::parse).orEmpty()) }
    val scope = rememberCoroutineScope()
    var contactError by remember { mutableStateOf(false) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) scope.launch {
            val address = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } }.getOrNull()
            }
            if (address.isNullOrBlank()) contactError = true
            else { contactError = false; to = if (to.isBlank()) address else to.trimEnd().trimEnd(',') + ", " + address }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        attachments = (attachments + uris).distinct()
    }
    fun currentDraft() = ErmisDraft(account!!, to.trim(), subject, body, attachments,
        saved?.inReplyTo ?: reply?.messageId?.takeIf { forwarding == null }, saved?.references ?: if (forwarding != null) emptyList() else (reply?.references.orEmpty() + listOfNotNull(reply?.messageId)).distinct(), cc = cc.trim(), bcc = bcc.trim(), forwardSource = original)
    fun requestClose() {
        val changed = if (saved != null) account != saved.accountId || to != saved.to || subject != saved.subject || body != saved.body || cc != saved.cc || bcc != saved.bcc || attachments.map { it.toString() } != saved.attachmentUris || original != saved.forwardSource
            else to.isNotBlank() || subject.isNotBlank() || body.isNotBlank() || cc.isNotBlank() || bcc.isNotBlank() || attachments.isNotEmpty() || original != null
        if (changed) confirmClose = true else onDismiss()
    }
    MailPage(onDismiss = { if (!busy) requestClose() }, title = stringResource(I18nR.string.ermis_compose), action = {
        IconButton(enabled = !busy && account != null, onClick = { onSave(draftId, currentDraft()) }) { Icon(painterResource(BaseR.drawable.save_24px), stringResource(I18nR.string.hc_save)) }
        TextButton(enabled = !busy && account != null && listOf(to, cc, bcc).any { it.contains('@') }, onClick = { onSend(draftId, currentDraft()) }) { Text(stringResource(I18nR.string.ermis_send)) }
    }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow { accounts.forEach { a -> FilterChip(account == a.id, onClick = { account = a.id }, label = { Text(a.email) }) } }
            OutlinedTextField(to, { to = it }, label = { Text(stringResource(I18nR.string.ermis_to)) }, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { IconButton(onClick = { runCatching { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI)) }.onFailure { contactError = true } }) {
                    Icon(painterResource(BaseR.drawable.person_24px), stringResource(I18nR.string.tsp_search_contacts))
                } })
            OutlinedTextField(cc, { cc = it }, label = { Text(stringResource(I18nR.string.ermis_cc)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(bcc, { bcc = it }, label = { Text(stringResource(I18nR.string.ermis_bcc)) }, modifier = Modifier.fillMaxWidth())
            if (contactError) Text(stringResource(I18nR.string.hc_no_contacts_found), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(subject, { subject = it }, label = { Text(stringResource(I18nR.string.ermis_subject)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it }, label = { Text(stringResource(I18nR.string.ermis_body)) }, minLines = 5, modifier = Modifier.fillMaxWidth())
    var cloudUploading by remember { mutableStateOf(false) }
    val cloudUploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cloudUploading = true
            scope.launch {
                val res = de.mm20.launcher2.downloads.TransferUploader.upload(context, uri, de.mm20.launcher2.downloads.UploadService.FileIo)
                cloudUploading = false
                res.onSuccess { r ->
                    body += (if (body.isNotBlank()) "\n\n" else "") + "Attachment Link (${r.fileName}): ${r.downloadUrl}"
                    Toast.makeText(context, "Attachment uploaded & link added to email!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Upload failed: ${err.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Icon(painterResource(BaseR.drawable.attach_file_24px), null); Spacer(Modifier.width(8.dp)); Text(stringResource(I18nR.string.ermis_attach)) }
    TextButton(onClick = { cloudUploadPicker.launch("*/*") }, enabled = !cloudUploading) {
        if (cloudUploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(painterResource(BaseR.drawable.share_24px), null)
        Spacer(Modifier.width(8.dp))
        Text(if (cloudUploading) "Uploading..." else "Upload & Attach Link")
    }
            Text(stringResource(I18nR.string.ermis_saf_help), style = MaterialTheme.typography.bodySmall)
            if (original != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(I18nR.string.ermis_forward_original), Modifier.weight(1f))
                IconButton(onClick = { original = null }) { Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.ermis_remove)) }
            }
            attachments.forEach { uri ->
                val name by produceState(uri.lastPathSegment.orEmpty(), uri) { value = withContext(Dispatchers.IO) { runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull() ?: uri.lastPathSegment.orEmpty() } }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f)); IconButton(onClick = { attachments = attachments - uri }) { Icon(painterResource(BaseR.drawable.close_24px), stringResource(I18nR.string.ermis_remove)) } }
            }
        }
    }
    if (confirmClose) AlertDialog(onDismissRequest = { confirmClose = false }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_drafts))  } },
        text = { Text(subject.ifBlank { stringResource(I18nR.string.ermis_no_subject) }) },
        confirmButton = { TextButton(enabled = !busy && account != null, onClick = { onSave(draftId, currentDraft()) }) { Text(stringResource(I18nR.string.hc_save)) } },
        dismissButton = { FlowRow { TextButton(onClick = { confirmClose = false }) { Text(stringResource(I18nR.string.hc_cancel)) }; TextButton(enabled = !busy, onClick = { onDiscard(draftId) }) { Text(stringResource(I18nR.string.ermis_remove)) } } })

}

@Composable
private fun MessageDialog(message: ErmisMessage, conversation: List<ErmisMessage>, folders: List<ErmisFolder>, onSelect: (ErmisMessage) -> Unit, onDismiss: () -> Unit, onReply: () -> Unit, onReplyAll: () -> Unit, onForward: () -> Unit, onMarkUnread: () -> Unit, onMove: (ErmisFolder) -> Unit, onMoveAny: () -> Unit, onSaveNote: (String) -> Unit, onDownload: (ErmisAttachment, Uri) -> Unit) {
    val scope = rememberCoroutineScope()
    var conversationOpen by remember { mutableStateOf(false) }
    var showHtml by remember(message.accountId, message.folder, message.uid, message.html) { mutableStateOf(message.html.isNotBlank()) }
    val htmlHeight = (LocalConfiguration.current.screenHeightDp * .6f).coerceIn(180f, 640f).dp
    val moveActions = remember(folders, message.folder) {
        listOfNotNull(
            (folders.firstOrNull { it.role == ErmisFolderRole.ARCHIVE } ?: folders.firstOrNull { it.role == ErmisFolderRole.ALL })?.let { I18nR.string.ermis_archive to it },
            folders.firstOrNull { it.role == ErmisFolderRole.TRASH }?.let { I18nR.string.ermis_trash to it },
            folders.firstOrNull { it.role == ErmisFolderRole.SPAM }?.let { I18nR.string.ermis_spam to it }
        ).filter { it.second.id != message.folder }
    }
    var note by remember(message.accountId, message.folder, message.uid, message.note) { mutableStateOf(message.note) }
    var attachment by remember { mutableStateOf<ErmisAttachment?>(null) }
    val download = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) attachment?.let { onDownload(it, uri) } }
    MailPage(onDismiss, title = message.subject.ifBlank { stringResource(I18nR.string.ermis_no_subject) }, action = { TextButton(onClick = onReply) { Text(stringResource(I18nR.string.ermis_reply)) } }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (conversation.size > 1) {
                Text(stringResource(I18nR.string.ermis_conversation_count, conversation.size), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { conversationOpen = true }) { Text(stringResource(I18nR.string.ermis_conversation_count, conversation.size)) }
            }
            Text(message.from, style = MaterialTheme.typography.titleMedium)
            
            var summary by remember { mutableStateOf<String?>(null) }
            var summarizing by remember { mutableStateOf(false) }

            if (summary != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(BaseR.drawable.auto_awesome_20dp), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(I18nR.string.ai_summary_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Text(summary!!, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        summarizing = true
                        scope.launch {
                            val plain = message.body.ifBlank { message.preview }
                            val result = de.mm20.launcher2.search.TelosSearchSummarizer.summarize(plain)
                            summary = result.summaryBullets.joinToString("\n")
                            summarizing = false
                        }
                    },
                    enabled = !summarizing,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(painterResource(BaseR.drawable.auto_awesome_20dp), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (summarizing) stringResource(I18nR.string.ai_summarizing) else stringResource(I18nR.string.ai_summarize))
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReplyAll) { Text(stringResource(I18nR.string.ermis_reply_all)) }
                TextButton(onClick = onForward) { Text(stringResource(I18nR.string.ermis_forward)) }
                TextButton(onClick = onMoveAny) { Text(stringResource(I18nR.string.ermis_move)) }
                TextButton(onClick = onMarkUnread) { Text(stringResource(I18nR.string.comms_mark_unread)) }
                moveActions.forEach { (label, folder) -> TextButton(onClick = { onMove(folder) }) { Text(stringResource(label)) } }
            }
            if (message.html.isNotBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(showHtml, onClick = { showHtml = true }, label = { Text(stringResource(I18nR.string.ermis_html_view)) })
                FilterChip(!showHtml, onClick = { showHtml = false }, label = { Text(stringResource(I18nR.string.ermis_text_view)) })
            }
            if (showHtml && message.html.isNotBlank()) ErmisHtmlBody(message.html, message.body.ifBlank { message.preview }, Modifier.fillMaxWidth().height(htmlHeight))
            else SelectionContainer { Text(message.body.ifBlank { message.preview }) }
            message.attachments.forEach { item -> OutlinedButton(onClick = { attachment = item; download.launch(item.name) }) { Text(item.name) } }
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(I18nR.string.ermis_note)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            TextButton(onClick = { onSaveNote(note) }) { Text(stringResource(I18nR.string.ermis_note_save)) }
        }
    }
    if (conversationOpen) AlertDialog(onDismissRequest = { conversationOpen = false }, title = { TelosCyberHeading {  Text(stringResource(I18nR.string.ermis_conversation_count, conversation.size))  } }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp)) { items(conversation, key = { "${it.accountId}:${it.folder}:${it.uid}" }) { other ->
            TextButton(onClick = { conversationOpen = false; onSelect(other) }) { Text(other.from + "\n" + other.subject) }
        } }
    }, confirmButton = { TextButton(onClick = { conversationOpen = false }) { Text(stringResource(I18nR.string.hc_close)) } })

}

@Composable
private fun MailPage(onDismiss: () -> Unit, title: String, action: @Composable () -> Unit, content: @Composable () -> Unit) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        CompositionLocalProvider(de.mm20.launcher2.ui.component.LocalGlassSurface provides false) {
            de.mm20.launcher2.ui.component.VirtualAppTheme {
                Surface(
                    modifier = if (isWide) Modifier.fillMaxWidth(0.75f).fillMaxHeight(0.88f) else Modifier.fillMaxSize(),
                    shape = if (isWide) RoundedCornerShape(28.dp) else androidx.compose.ui.graphics.RectangleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
                    tonalElevation = 6.dp
                ) {
                    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, topBar = {
                        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) { Icon(painterResource(BaseR.drawable.arrow_back_24px), stringResource(I18nR.string.hc_close)) }
                            Text(title, Modifier.weight(1f), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            action()
                        }
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp)) { content() }
                    }
                }
            }
        }
    }
}
