package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.comms.sms.BlockedMessage
import de.mm20.launcher2.comms.sms.BlockedMessages
import de.mm20.launcher2.comms.sms.SmsSims
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.res.painterResource
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.privacy.HiddenContacts
import de.mm20.launcher2.comms.privacy.PrivacySession
import de.mm20.launcher2.comms.sms.SmsConversation
import de.mm20.launcher2.comms.sms.SmsMessage
import de.mm20.launcher2.comms.sms.SmsRole
import de.mm20.launcher2.comms.sms.SmsStore
import de.mm20.launcher2.comms.sms.SmsThreads
import de.mm20.launcher2.preferences.comms.CommsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import de.mm20.launcher2.comms.sms.ScheduledSmsStore
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

/**
 * The text and multimedia messages of the phone: the conversations, one conversation with a reply
 * field, and new messages. Conversations with a hidden contact are only listed while the hidden
 * contacts are unlocked. When Telos is not the default SMS app the system keeps the messages, and
 * its own messaging app still shows (and notifies about) every conversation.
 */
@Composable
fun MessagesScreen(
    searchQuery: String = "",
    initialNumber: String = "",
    initialBody: String = "",
    initialAttachments: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val settings: CommsSettings = koinInject()
    val sendScope = rememberCoroutineScope()
    val hidden by settings.hiddenNumbers.collectAsStateWithLifecycle(emptyMap())
    val unlocked by PrivacySession.hiderUnlocked.collectAsStateWithLifecycle()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[Manifest.permission.READ_SMS] == true }

    var isDefault by remember { mutableStateOf(SmsRole.isDefault(context)) }
    var roleSettingsFallback by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = SmsRole.isDefault(context)
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        // Android may suppress a repeatedly denied role prompt. Offer the system picker explicitly.
        roleSettingsFallback = !isDefault
    }
    fun requestSmsRole() {
        try { roleLauncher.launch(if (roleSettingsFallback) SmsRole.settingsIntent() else SmsRole.requestIntent(context)) }
        catch (_: Exception) {
            roleSettingsFallback = true
            try { roleLauncher.launch(SmsRole.settingsIntent()) } catch (_: Exception) { }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = SmsRole.isDefault(context)
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(I18nR.string.hc_messages), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(I18nR.string.hc_allow_telos_to_read_and_send_text_messag),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Button(onClick = { if (!isDefault) requestSmsRole() else launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)) }) { Text(stringResource(I18nR.string.hc_allow)) }
        }
        return
    }


    var refresh by remember { mutableStateOf(0) }
    var all by remember { mutableStateOf<List<SmsConversation>?>(null) }
    LaunchedEffect(refresh, isDefault) { all = withContext(Dispatchers.IO) { SmsThreads.conversations(context) } }
    var open by remember { mutableStateOf<SmsConversation?>(null) }
    var newMessage by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }
    val spam: SpamRepository = koinInject()
    var blockedCount by remember { mutableStateOf(0) }
    LaunchedEffect(refresh, showBlocked) { blockedCount = withContext(Dispatchers.IO) { BlockedMessages.count(context) } }
    var body by remember { mutableStateOf(initialBody) }
    var attachments by remember { mutableStateOf(initialAttachments.map { Uri.parse(it) }) }

    fun isHidden(c: SmsConversation) = c.address.split(", ").any { HiddenContacts.matches(it, hidden) }
    // an existing conversation with a number, or a new one. The history of a hidden contact stays
    // closed while the hidden contacts are locked.
    fun conversationFor(number: String): SmsConversation {
        val existing = all?.firstOrNull { c -> c.address.split(", ").any { PhoneNumbers.match(it, number) } }
        return if (existing != null && (unlocked || !isHidden(existing))) existing
        else SmsConversation(-1, number, null, "", 0, 0)
    }

    // something was shared to Telos Messages: write to that number (once, not again after going back)
    var initialHandled by remember(initialNumber) { mutableStateOf(false) }
    LaunchedEffect(initialNumber, all) {
        if (all == null || initialHandled) return@LaunchedEffect
        if (initialNumber.isNotBlank() && open == null) open = conversationFor(initialNumber)
        initialHandled = true
    }
    val archived by settings.archivedConversations.collectAsStateWithLifecycle(emptyMap())
    val pinned by settings.pinnedConversations.collectAsStateWithLifecycle(emptyMap())
    var showArchived by remember { mutableStateOf(false) }
    fun convKey(address: String) = address.filter { it.isDigit() || it == ',' }
    fun isArchived(c: SmsConversation) = archived.containsKey(convKey(c.address))
    fun isPinned(c: SmsConversation) = pinned.containsKey(convKey(c.address))
    val listQuery = searchQuery
    // message texts per thread, loaded in the background while a search is active
    val bodies = remember { androidx.compose.runtime.mutableStateMapOf<Long, List<String>>() }
    LaunchedEffect(listQuery.isNotBlank(), all) {
        val list = all
        if (listQuery.isBlank() || list == null) return@LaunchedEffect
        for (c in list) {
            if (c.threadId in bodies) continue
            val texts = withContext(Dispatchers.IO) {
                runCatching { SmsThreads.messages(context, c).map { it.body } }.getOrDefault(emptyList())
            }
            bodies[c.threadId] = texts
        }
    }
    val visibleAll = all?.filter { unlocked || !isHidden(it) }
        ?.filter { if (showArchived) isArchived(it) else !isArchived(it) }
    val searched = if (visibleAll == null || listQuery.isBlank()) visibleAll else
        de.mm20.launcher2.comms.search.TelosSearch.filter(visibleAll, listQuery) { c ->
            listOf(c.name, c.address, c.snippet) + (bodies[c.threadId] ?: emptyList())
        }
    val unreadOnTop by settings.unreadOnTop.collectAsStateWithLifecycle(true)
    val names by settings.conversationNames.collectAsStateWithLifecycle(emptyMap())
    fun displayName(c: SmsConversation) = names[convKey(c.address)] ?: c.name ?: c.address
    LaunchedEffect(Unit) {
        val days = settings.smsAutoDeleteDays.first()
        if (days > 0) withContext(Dispatchers.IO) {
            SmsStore.deleteOlderThan(context, System.currentTimeMillis() - days * 86_400_000L)
        }
    }
    val shown = searched?.sortedWith(
        compareByDescending<SmsConversation> { isPinned(it) }
            .thenByDescending { if (unreadOnTop) it.unread else 0 }
            .thenByDescending { it.date }
    )

    if (showBlocked) {
        BackHandler { showBlocked = false; refresh++ }
        BlockedMessagesView(onBack = { showBlocked = false; refresh++ })
        return
    }

    val current = open
    if (current != null) {
        BackHandler { open = null; refresh++ }
        ThreadView(
            conversation = current,
            isDefault = isDefault,
            body = body,
            onBody = { body = it },
            attachments = attachments,
            onAttachments = { attachments = it },
            onBack = { open = null; refresh++ },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (!isDefault) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(I18nR.string.hc_make_telos_your_sms_app_to_receive_messa),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = ::requestSmsRole) { Text(stringResource(if (roleSettingsFallback) I18nR.string.hc_settings else I18nR.string.hc_set)) }
                }
            }
        }
        androidx.compose.material3.FilledTonalButton(
            onClick = { newMessage = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(painterResource(BaseR.drawable.sms_24px), contentDescription = null)
            Text(stringResource(I18nR.string.hc_new_message), Modifier.padding(start = 12.dp))
        }
        if (blockedCount > 0) TextButton(
            onClick = { showBlocked = true },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(I18nR.string.au2_msg2_blocked_messages_count, blockedCount)) }
        TextButton(
            onClick = { showArchived = !showArchived },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(if (showArchived) I18nR.string.comms_unarchive_conversation else I18nR.string.comms_archived)) }
        when {
            shown == null -> Box(Modifier.fillMaxSize())
            shown.isEmpty() && listQuery.isNotBlank() -> SearchEmptyState(listQuery)
            shown.isEmpty() -> EmptyCommsTab(stringResource(I18nR.string.au_messages_no_conversations), stringResource(I18nR.string.au_messages_no_conversations_hint))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.threadId.toString() + it.address }) { c ->
                    val dismiss = androidx.compose.material3.rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> {
                                    settings.setConversationArchived(c.address, !isArchived(c))
                                    true
                                }
                                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> {
                                    if (isDefault) sendScope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (c.unread > 0) SmsStore.markThreadRead(context, c.threadId)
                                            else SmsStore.markThreadUnread(context, c.threadId)
                                        }
                                        refresh++
                                    }
                                    false
                                }
                                else -> false
                            }
                        },
                    )
                    androidx.compose.material3.SwipeToDismissBox(
                        state = dismiss,
                        backgroundContent = {
                            val color = if (dismiss.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                                MaterialTheme.colorScheme.primary
                            } else if (dismiss.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                Color.Transparent
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(color)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = if (dismiss.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                val icon = if (dismiss.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
                                    painterResource(if (isArchived(c)) BaseR.drawable.unarchive_24px else BaseR.drawable.archive_24px)
                                } else {
                                    painterResource(if (c.unread > 0) BaseR.drawable.draft_24px else BaseR.drawable.search_24px)
                                }
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (c.unread > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { open = c },
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text((c.name ?: c.address).trim().take(1).uppercase(), Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    (if (isPinned(c)) "📌 " else "") + displayName(c),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(shortDate(c.date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (c.snippet.isNotBlank()) Text(c.snippet, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (c.unread > 0) androidx.compose.material3.Badge { Text(c.unread.toString()) }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { open = c }) {
                                    Icon(painterResource(BaseR.drawable.sms_24px), contentDescription = stringResource(I18nR.string.comms_reply))
                                }
                                var cardMenuOpen by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { cardMenuOpen = true }) {
                                        Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.comms_menu))
                                    }
                                    androidx.compose.material3.DropdownMenu(
                                        expanded = cardMenuOpen,
                                        onDismissRequest = { cardMenuOpen = false }
                                    ) {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(if (isPinned(c)) stringResource(I18nR.string.comms_unpin_conversation) else stringResource(I18nR.string.comms_pin_conversation)) },
                                            onClick = {
                                                cardMenuOpen = false
                                                settings.setConversationPinned(c.address, !isPinned(c))
                                            },
                                            leadingIcon = { Icon(painterResource(BaseR.drawable.add_24px), contentDescription = null) }
                                        )
                                        if (isDefault) {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(if (c.unread > 0) stringResource(I18nR.string.comms_mark_read) else stringResource(I18nR.string.comms_mark_unread)) },
                                                onClick = {
                                                    cardMenuOpen = false
                                                    sendScope.launch {
                                                        withContext(Dispatchers.IO) {
                                                            if (c.unread > 0) SmsStore.markThreadRead(context, c.threadId)
                                                            else SmsStore.markThreadUnread(context, c.threadId)
                                                        }
                                                        refresh++
                                                    }
                                                },
                                                leadingIcon = { Icon(painterResource(BaseR.drawable.search_24px), contentDescription = null) }
                                            )
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(stringResource(I18nR.string.comms_delete_conversation), color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    cardMenuOpen = false
                                                    sendScope.launch {
                                                        withContext(Dispatchers.IO) { SmsStore.deleteThread(context, c.threadId) }
                                                        refresh++
                                                    }
                                                },
                                                leadingIcon = { Icon(painterResource(BaseR.drawable.close_24px), contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                            )
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
    }

    if (newMessage) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newMessage = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_new_message))  } },
            text = { OutlinedTextField(number, { number = it }, label = { Text(stringResource(I18nR.string.hc_number)) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = number.isNotBlank(), onClick = {
                    val n = number.trim()
                    newMessage = false
                    open = conversationFor(n)
                }) { Text(stringResource(I18nR.string.hc_write)) }
            },
            dismissButton = { TextButton(onClick = { newMessage = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }
}

@Composable
private fun ThreadView(
    conversation: SmsConversation,
    isDefault: Boolean,
    body: String,
    onBody: (String) -> Unit,
    attachments: List<Uri>,
    onAttachments: (List<Uri>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var version by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf(emptyList<SmsMessage>()) }
    LaunchedEffect(version) {
        withContext(Dispatchers.IO) { SmsStore.markThreadRead(context, conversation.threadId) }
        messages = withContext(Dispatchers.IO) { SmsThreads.messages(context, conversation) }
    }
    var failed by remember { mutableStateOf(false) }
    val sendScope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()
    var searching by remember { mutableStateOf(false) }
    var threadQuery by remember { mutableStateOf("") }
    val shownMessages = (if (threadQuery.isBlank()) messages else messages.filter {
        de.mm20.launcher2.comms.search.TelosSearch.matches(threadQuery, it.body)
    }).filter { !de.mm20.launcher2.comms.sms.EncryptedSms.isHandshake(it.body) }
    LaunchedEffect(shownMessages.size) { if (shownMessages.isNotEmpty()) listState.scrollToItem(shownMessages.lastIndex) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { picked ->
        if (picked.isNotEmpty()) onAttachments(attachments + picked)
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) onBody(if (body.isBlank()) spoken else "$body $spoken")
    }
    val settings: CommsSettings = koinInject()
    val messageApps by settings.contactDefaultMessageApp.collectAsStateWithLifecycle(emptyMap())
    var delaySend by remember { mutableStateOf(false) }
    val addresses = conversation.address.split(", ").filter { it.isNotBlank() }
    // the SIM that sends (only offered on a phone with two or more active SIMs)
    val sims = remember { SmsSims.active(context) }
    var simId by remember(conversation.address) { mutableStateOf(SmsSims.preselected(context, conversation.address, sims)?.subId ?: -1) }
    val spam: SpamRepository = koinInject()
    var blocked by remember(conversation.address) { mutableStateOf(false) }
    val single = addresses.size == 1
    LaunchedEffect(conversation.address) {
        blocked = single && runCatching { spam.isNumberBlocked(addresses[0]) }.getOrDefault(false)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(I18nR.string.hc_back)) }
            val names by settings.conversationNames.collectAsStateWithLifecycle(emptyMap())
            val title = names[conversation.address.filter { it.isDigit() || it == ',' }] ?: conversation.name ?: conversation.address
            var rename by remember { mutableStateOf(false) }
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { rename = true })
            if (rename) {
                var draft by remember { mutableStateOf(title) }
                AlertDialog(
                    onDismissRequest = { rename = false },
                    title = { TelosCyberHeading {  Text(stringResource(I18nR.string.comms_rename_conversation))  } },
                    text = { OutlinedTextField(draft, { draft = it }, singleLine = true) },
                    confirmButton = {
                        TextButton(onClick = { settings.setConversationName(conversation.address, draft); rename = false }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                    dismissButton = { TextButton(onClick = { rename = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
                )
            }
            if (single) TextButton(onClick = {
                val number = addresses[0]
                val block = !blocked
                sendScope.launch {
                    withContext(Dispatchers.IO) {
                        updateBlockedWithFeedback(context, spam, number, block)
                        blocked = spam.isNumberBlocked(number)
                        // unblocking returns what was kept while the number was blocked
                        if (!block) BlockedMessages.restore(context, number)
                    }
                    if (!block) version++
                }
            }) { Text(stringResource(if (blocked) I18nR.string.au2_msg2_unblock_number else I18nR.string.au2_msg2_block_number)) }
            IconButton(onClick = { searching = !searching; if (!searching) threadQuery = "" }) {
                Icon(
                    painterResource(if (searching) BaseR.drawable.close_24px else BaseR.drawable.search_24px),
                    contentDescription = stringResource(I18nR.string.ts_search),
                )
            }
        }
        if (searching) TelosSearchBar(
            value = threadQuery,
            onValueChange = { threadQuery = it },
            placeholder = stringResource(I18nR.string.tsp_search_in_conversation),
            autoFocus = true,
        )
        if (searching && threadQuery.isNotBlank() && shownMessages.isEmpty()) SearchEmptyState(threadQuery, Modifier.weight(1f))
        else LazyColumn(
            Modifier.weight(1f).fillMaxWidth(), state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(12.dp),
        ) {
            items(shownMessages, key = { it.id + it.date }) { m ->
                var messageMenuOpen by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                    @OptIn(ExperimentalFoundationApi::class)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (m.outgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.widthIn(max = 300.dp).combinedClickable(
                            onClick = {
                                if (m.body.isNotBlank()) de.mm20.launcher2.comms.sms.SmsTts.speak(context, m.body)
                            },
                            onLongClick = {
                                messageMenuOpen = true
                            }
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            m.attachments.forEach { a ->
                                if (a.mimeType.startsWith("image/") || a.mimeType.startsWith("video/")) {
                                    AsyncImage(
                                        model = a.uri, contentDescription = stringResource(I18nR.string.au_messages_attachment), contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                    )
                                } else Text(stringResource(I18nR.string.au_messages_attachment) + " (${a.mimeType})", style = MaterialTheme.typography.labelSmall)
                            }
                            if (m.body.isNotBlank()) Text(m.body)
                            Text(
                                shortDate(m.date) + if (m.failed) " · " + stringResource(I18nR.string.au_messages_not_sent) else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (m.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = messageMenuOpen,
                            onDismissRequest = { messageMenuOpen = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(stringResource(I18nR.string.hc_copy)) },
                                onClick = {
                                    messageMenuOpen = false
                                    if (m.body.isNotBlank()) {
                                        val clip = android.content.ClipData.newPlainText("message", m.body)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(clip)
                                    }
                                },
                                leadingIcon = { Icon(painterResource(BaseR.drawable.search_24px), contentDescription = null) }
                            )
                            if (isDefault) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(I18nR.string.comms_delete_conversation), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        messageMenuOpen = false
                                        sendScope.launch {
                                            withContext(Dispatchers.IO) {
                                                SmsStore.deleteMessage(context, m.id)
                                            }
                                            version++
                                        }
                                    },
                                    leadingIcon = { Icon(painterResource(BaseR.drawable.close_24px), contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (failed) Text(stringResource(I18nR.string.hc_the_message_could_not_be_sent), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        if (attachments.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(I18nR.string.au_messages_attached_count, attachments.size), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onAttachments(emptyList()) }) { Text(stringResource(I18nR.string.hc_remove)) }
            }
        }
        if (sims.size >= 2) SimChooser(sims, simId, { simId = it }, Modifier.padding(horizontal = 12.dp))
        if (single) {
            val ch = de.mm20.launcher2.comms.intent.MessengerApps.resolvePref(messageApps, addresses[0]).let {
                if (it == de.mm20.launcher2.comms.intent.MessengerChannel.Phone) de.mm20.launcher2.comms.intent.MessengerChannel.Sms else it
            }
            if (ch != de.mm20.launcher2.comms.intent.MessengerChannel.Sms) {
                Text(
                    stringResource(I18nR.string.comms_send_via) + " " + ch.id,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
        val e2eOn by settings.smsE2eEnabled.collectAsStateWithLifecycle(false)
        val e2eReady = single && e2eOn && de.mm20.launcher2.comms.sms.EncryptedSms.hasSession(context, addresses[0])
        var sendVia by remember { mutableStateOf<String?>(null) }
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isDefault) IconButton(onClick = { picker.launch("*/*") }) {
                Icon(painterResource(BaseR.drawable.add_24px), contentDescription = stringResource(I18nR.string.au_messages_attach))
            }
            IconButton(onClick = {
                runCatching {
                    speech.launch(
                        android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                    )
                }
            }) {
                Icon(painterResource(BaseR.drawable.search_24px), contentDescription = stringResource(I18nR.string.comms_speak))
            }
            OutlinedTextField(body, onBody, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(I18nR.string.hc_message)) })
            androidx.compose.material3.FilledIconButton(
                enabled = body.isNotBlank() || attachments.isNotEmpty(),
                onClick = {
                    val asMms = attachments.isNotEmpty() || addresses.size > 1
                    val text = body
                    val picked = attachments
                    val sub = if (sims.size >= 2) simId else -1
                    if (sub >= 0) SmsSims.remember(context, conversation.address, sub)
                    failed = false
                    val wait = if (delaySend) 3000L else 0L
                    val dest = addresses.firstOrNull().orEmpty()
                    sendScope.launch {
                        if (wait > 0) kotlinx.coroutines.delay(wait)
                        val signature = settings.smsSignature.first()
                        val outbound = if (signature.isBlank()) text else "$text\n$signature"
                        val channel = de.mm20.launcher2.comms.intent.MessengerChannel.fromId(
                            sendVia ?: de.mm20.launcher2.comms.intent.MessengerApps.resolvePref(messageApps, dest).id
                        ).let {
                            if (it == de.mm20.launcher2.comms.intent.MessengerChannel.Phone) de.mm20.launcher2.comms.intent.MessengerChannel.Sms else it
                        }
                        if (channel != de.mm20.launcher2.comms.intent.MessengerChannel.Sms && !asMms) {
                            val opened = de.mm20.launcher2.comms.intent.MessengerApps.openChat(context, channel, dest, outbound)
                            if (opened) { onBody(""); onAttachments(emptyList()); return@launch }
                            android.widget.Toast.makeText(
                                context,
                                context.getString(I18nR.string.comms_messenger_fallback_sms, channel.id),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        val ok = withContext(Dispatchers.IO) {
                            if (asMms) SmsThreads.sendMms(context, addresses, outbound, picked, sub)
                            else {
                                val wire = if (e2eReady) de.mm20.launcher2.comms.sms.EncryptedSms.encrypt(context, dest, outbound) else null
                                SmsThreads.send(context, dest, outbound, sub, wire)
                            }
                        }
                        failed = !ok
                        if (ok) { onBody(""); onAttachments(emptyList()); version++ }
                    }
                },
            ) {
                Icon(painterResource(BaseR.drawable.sms_24px), contentDescription = stringResource(I18nR.string.hc_send))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = delaySend, onClick = { delaySend = !delaySend }, label = { Text("3s") })
            var scheduleOpen by remember { mutableStateOf(false) }
            FilterChip(selected = false, onClick = { scheduleOpen = true }, label = { Text(stringResource(I18nR.string.comms_schedule_send)) })
            if (scheduleOpen) {
                val fmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
                var whenText by remember { mutableStateOf(fmt.format(java.util.Date(System.currentTimeMillis() + 3_600_000L))) }
                AlertDialog(
                    onDismissRequest = { scheduleOpen = false },
                    title = { TelosCyberHeading {  Text(stringResource(I18nR.string.comms_schedule_send))  } },
                    text = {
                        OutlinedTextField(whenText, { whenText = it }, label = { Text("yyyy-MM-dd HH:mm") }, singleLine = true)
                    },
                    confirmButton = {
                        TextButton(
                            enabled = body.isNotBlank() && single,
                            onClick = {
                                val dest = addresses.firstOrNull().orEmpty()
                                val whenMs = runCatching { fmt.parse(whenText)?.time }.getOrNull() ?: return@TextButton
                                if (whenMs <= System.currentTimeMillis()) return@TextButton
                                ScheduledSmsStore.add(context, dest, body, whenMs, if (sims.size >= 2) simId else -1)
                                onBody("")
                                scheduleOpen = false
                            },
                        ) { Text(stringResource(android.R.string.ok)) }
                    },
                    dismissButton = { TextButton(onClick = { scheduleOpen = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
                )
            }
            if (single && e2eOn) {
                TextButton(onClick = {
                    sendScope.launch {
                        withContext(Dispatchers.IO) {
                            SmsThreads.send(context, addresses[0], de.mm20.launcher2.comms.sms.EncryptedSms.handshakeSms(context))
                        }
                    }
                }) { Text(stringResource(I18nR.string.comms_e2e_start)) }
                de.mm20.launcher2.comms.sms.EncryptedSms.safetyNumber(context, addresses[0])?.let { sn ->
                    Text(sn, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp))
                    ContactQrImage(sn.replace("\n", " "), Modifier.padding(12.dp))
                }
            }
            if (single) {
                val installed = listOf(
                    "sms" to stringResource(I18nR.string.comms_channel_sms),
                    "signal" to stringResource(I18nR.string.action_signal),
                    "whatsapp" to stringResource(I18nR.string.action_whatsapp),
                    "telegram" to stringResource(I18nR.string.action_telegram),
                    "viber" to stringResource(I18nR.string.action_viber),
                ).filter { (id, _) ->
                    id == "sms" || de.mm20.launcher2.comms.intent.MessengerApps.installedPackage(
                        context, de.mm20.launcher2.comms.intent.MessengerChannel.fromId(id),
                    ) != null
                }
                val current = sendVia ?: de.mm20.launcher2.comms.intent.MessengerApps.resolvePref(messageApps, addresses[0]).let {
                    if (it == de.mm20.launcher2.comms.intent.MessengerChannel.Phone) "sms" else it.id
                }
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    installed.forEach { (id, label) ->
                        val pkg = remember(id) {
                            if (id == "sms") null
                            else de.mm20.launcher2.comms.intent.MessengerApps.installedPackage(
                                context, de.mm20.launcher2.comms.intent.MessengerChannel.fromId(id)
                            )
                        }
                        val iconDrawable = remember(pkg) {
                            if (pkg != null) runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() else null
                        }
                        Surface(
                            onClick = { sendVia = id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (current == id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (current == id) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (iconDrawable != null) {
                                    AsyncImage(
                                        model = iconDrawable,
                                        contentDescription = label,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(BaseR.drawable.sms_24px),
                                        contentDescription = label,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

private fun shortDate(ms: Long): String =
    if (ms <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ms))

/** The SIM that sends: one chip per active SIM (slot and carrier) */
@Composable
internal fun SimChooser(sims: List<SmsSims.Sim>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(I18nR.string.au2_msg2_send_with), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sims.forEach { sim ->
            FilterChip(
                selected = sim.subId == selected,
                onClick = { onSelect(sim.subId) },
                label = { Text(sim.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** The kept messages of blocked numbers, and the blocked numbers with a way to unblock them */
@Composable
private fun BlockedMessagesView(onBack: () -> Unit) {
    val context = LocalContext.current
    val spam: SpamRepository = koinInject()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf(emptyList<BlockedMessage>()) }
    var numbers by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            messages = BlockedMessages.list(context)
            numbers = runCatching { spam.getAllBlocked() }.getOrDefault(emptyList())
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(I18nR.string.hc_back)) }
            Text(stringResource(I18nR.string.au2_msg2_blocked_messages), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (numbers.isNotEmpty()) {
                item {
                    Text(
                        stringResource(I18nR.string.au2_msg2_blocked_numbers), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(numbers, key = { "n$it" }) { n ->
                    ListItem(
                        headlineContent = { Text(SmsThreads.displayNameOf(context, n) ?: n, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (SmsThreads.displayNameOf(context, n) != null) ({ Text(n) }) else null,
                        trailingContent = {
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        updateBlockedWithFeedback(context, spam, n, false)
                                        BlockedMessages.restore(context, n)
                                    }
                                    version++
                                }
                            }) { Text(stringResource(I18nR.string.au2_msg2_unblock_number)) }
                        },
                    )
                }
                item {
                    Text(
                        stringResource(I18nR.string.au2_msg2_blocked_restore_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (messages.isEmpty()) {
                item {
                    EmptyCommsTab(stringResource(I18nR.string.au2_msg2_blocked_empty_title), stringResource(I18nR.string.au2_msg2_blocked_empty_hint))
                }
            }
            items(messages, key = { "m${it.id}" }) { m ->
                ListItem(
                    headlineContent = {
                        Text(
                            SmsThreads.displayNameOf(context, m.address) ?: m.address,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { BlockedMessages.delete(context, m.id) }
                                version++
                            }
                        }) { Text(stringResource(I18nR.string.hc_delete)) }
                    },
                    supportingContent = {
                        Column {
                            m.attachments.forEach { a ->
                                if (a.mimeType.startsWith("image/")) AsyncImage(
                                    model = a.uri, contentDescription = stringResource(I18nR.string.au_messages_attachment), contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                                ) else Text(stringResource(I18nR.string.au_messages_attachment) + " (${a.mimeType})", style = MaterialTheme.typography.labelSmall)
                            }
                            if (m.body.isNotBlank()) Text(m.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            Text(shortDate(m.date), style = MaterialTheme.typography.labelSmall)
                        }
                    },
                )
            }
        }
    }
}
