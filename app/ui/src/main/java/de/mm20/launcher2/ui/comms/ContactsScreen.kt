package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.comms.model.isInAccounts
import android.content.Intent
import android.provider.ContactsContract
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.search.ContactSearch
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.SearchEmptyState
import de.mm20.launcher2.ui.component.TelosSearchBar
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private sealed class ContactListItem {
    data class Header(val letter: Char) : ContactListItem()
    data class Person(val contact: DialerContact) : ContactListItem()
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ContactsScreen(
    searchQuery: String = "",
    starredOnly: Boolean = false,
    showLocalSearch: Boolean = true,
    showAddFab: Boolean = true,
) {
    val viewModel: ContactsViewModel = viewModel()
    val permissionsManager: PermissionsManager = koinInject()
    val context = LocalContext.current
    val backStack = LocalBackStack.current

    val hasPermission by permissionsManager.hasPermission(PermissionGroup.Contacts)
        .collectAsStateWithLifecycle(false)
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val accountPrefs = remember(context) { context.getSharedPreferences("contact_account_filter", android.content.Context.MODE_PRIVATE) }
    var selectedAccounts by remember { mutableStateOf(accountPrefs.getStringSet("accounts", null)?.toSet()) }
    var showAccounts by remember { mutableStateOf(false) }
    val accounts = remember(contacts) { contacts.flatMap { it.accounts }.distinctBy { it.key }.sortedBy { it.name.orEmpty() } }
    var localQuery by remember { mutableStateOf("") }
    var localStarred by remember { mutableStateOf(false) }
    var gridMode by remember { mutableStateOf(false) }
    var deleteContact by remember { mutableStateOf<DialerContact?>(null) }
    var cabContact by remember { mutableStateOf<DialerContact?>(null) }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val tapToCall by viewModel.tapToCall.collectAsStateWithLifecycle()
    val defaultNumbers by viewModel.defaultNumbers.collectAsStateWithLifecycle()
    val confirmBeforeCall by viewModel.confirmBeforeCall.collectAsStateWithLifecycle()
    val query = if (showLocalSearch) localQuery else searchQuery
    val starredFilter = starredOnly || localStarred

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(I18nR.string.comms_contacts_permission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                (context as? AppCompatActivity)?.let {
                    permissionsManager.requestPermission(it, PermissionGroup.Contacts)
                }
            }) {
                Text(stringResource(I18nR.string.permission_grant))
            }
        }
        return
    }

    val filtered = remember(contacts, query, starredFilter, selectedAccounts) {
        val visible = contacts.filter { it.isInAccounts(selectedAccounts) }
        val pool = if (starredFilter) visible.filter { it.starred } else visible
        ContactSearch.search(query, pool)
    }
    val rows = remember(filtered, query) {
        val grouped: Map<Char, List<DialerContact>> = if (query.isBlank()) {
            filtered.groupBy {
                val first = GreekText.fold(it.displayName).firstOrNull()?.uppercaseChar()
                if (first != null && first.isLetter()) first else '#'
            }.toSortedMap()
        } else {
            linkedMapOf(' ' to filtered)
        }
        grouped.flatMap { (letter, people) ->
            listOf(ContactListItem.Header(letter)) + people.map { ContactListItem.Person(it) }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.material3.AssistChip(
                onClick = { showAccounts = true },
                label = { Text(if (selectedAccounts == null) stringResource(I18nR.string.contacts_accounts_all)
                    else stringResource(I18nR.string.contacts_accounts_selected, selectedAccounts!!.size)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (showLocalSearch) TelosSearchBar(
                value = localQuery,
                onValueChange = { localQuery = it },
                placeholder = stringResource(I18nR.string.tsp_search_contacts),
                trailing = {
                    IconButton(onClick = { localStarred = !localStarred }) {
                        Icon(
                            painterResource(
                                if (localStarred) BaseR.drawable.star_24px_filled else BaseR.drawable.star_24px_outlined
                            ),
                            contentDescription = stringResource(I18nR.string.filter_starred),
                            tint = if (localStarred) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )

            if (showLocalSearch && query.isBlank() && !starredFilter) {
                val starred = remember(contacts) { contacts.filter { it.starred } }
                if (starred.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        lazyRowItems(starred, key = { "fav-${it.id}" }) { contact ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable {
                                        backStack.add(ContactDetailsRoute(contactId = contact.id))
                                    },
                            ) {
                                CommsAvatar(contact.displayName, contact.photoUri, 56.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    contact.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (showLocalSearch) Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = { gridMode = !gridMode }) {
                    Icon(
                        painterResource(if (gridMode) BaseR.drawable.person_24px else BaseR.drawable.apps_24px),
                        contentDescription = stringResource(I18nR.string.au_phonea_toggle_grid),
                    )
                }
            }
            if (filtered.isEmpty() && query.isNotBlank()) {
                SearchEmptyState(query)
            } else if (filtered.isEmpty()) {
                EmptyCommsTab(
                    title = stringResource(I18nR.string.contacts_empty_title),
                    message = stringResource(I18nR.string.contacts_empty_msg),
                )
            } else {
                Box(Modifier.weight(1f)) {
                    if (gridMode) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 80.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(filtered, key = { it.id }) { contact ->
                                Column(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .combinedClickable(
                                            onClick = {
                                                backStack.add(ContactDetailsRoute(contactId = contact.id))
                                            },
                                            onLongClick = { cabContact = contact },
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CommsAvatar(contact.displayName, contact.photoUri, 72.dp)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        contact.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    } else LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = 20.dp, bottom = 80.dp),
                    ) {
                        itemsIndexed(rows, key = { index, item ->
                            when (item) {
                                is ContactListItem.Header -> "h-${item.letter}"
                                is ContactListItem.Person -> "c-${item.contact.id}"
                            }
                        }) { _, item ->
                            when (item) {
                                is ContactListItem.Header -> if (item.letter != ' ') {
                                    Text(
                                        text = item.letter.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                    )
                                }
                                is ContactListItem.Person -> {
                                    val number = viewModel.numberFor(item.contact)
                                    ContactRow(
                                        contact = item.contact,
                                        query = query,
                                        onOpen = {
                                            backStack.add(ContactDetailsRoute(contactId = item.contact.id))
                                        },
                                        onCall = {
                                            if (!tapToCall || number.isEmpty()) {
                                                backStack.add(ContactDetailsRoute(contactId = item.contact.id))
                                            } else if (confirmBeforeCall) {
                                                pendingCall = number
                                            } else {
                                                viewModel.dial(context, number)
                                            }
                                        },
                                        onSms = {
                                            if (number.isNotEmpty()) {
                                                context.tryStartActivity(MessengerIntentUtils.sms(number))
                                            }
                                        },
                                        onLongPress = { cabContact = item.contact },
                                    )
                                }
                            }
                        }
                    }

                }
            }
        }

        if (showAddFab) FloatingActionButton(
            onClick = {
                context.tryStartActivity(Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                })
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = RdGreenCall,
            contentColor = Color.White,
        ) {
            Icon(
                painterResource(BaseR.drawable.person_add_24px),
                contentDescription = stringResource(I18nR.string.comms_create_contact),
            )
        }

        pendingCall?.let { number ->
            AlertDialog(
                onDismissRequest = { pendingCall = null },
                title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_place_call))  } },
                text = { Text(number) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dial(context, number)
                        pendingCall = null
                    }) { Text(stringResource(I18nR.string.search_action_call)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCall = null }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        if (showAccounts) AlertDialog(
            onDismissRequest = { showAccounts = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.contacts_accounts))  } },
            text = {
                androidx.compose.foundation.rememberScrollState().let { scroll ->
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(scroll)) {
                        TextButton(onClick = {
                            selectedAccounts = null
                            accountPrefs.edit().remove("accounts").apply()
                        }) { Text(stringResource(I18nR.string.contacts_accounts_all)) }
                        accounts.forEach { account ->
                            Row(Modifier.fillMaxWidth().clickable {
                                val next = (selectedAccounts ?: accounts.map { it.key }.toSet()).toMutableSet()
                                if (!next.add(account.key)) next.remove(account.key)
                                selectedAccounts = next
                                accountPrefs.edit().putStringSet("accounts", next).apply()
                            }, verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = selectedAccounts?.contains(account.key) ?: true, onCheckedChange = null,
                                )
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(account.name ?: stringResource(I18nR.string.contacts_account_device))
                                    account.type?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAccounts = false }) { Text(stringResource(android.R.string.ok)) } },
        )
        deleteContact?.let { DeleteContactDialog(it) { deleteContact = null } }
        cabContact?.let { selected ->
            val number = viewModel.numberFor(selected)
            val callLabel = stringResource(I18nR.string.search_action_call)
            val messageLabel = stringResource(I18nR.string.search_action_message)
            val detailsLabel = stringResource(I18nR.string.contact_details_title)
            val editLabel = stringResource(I18nR.string.net_dns_action_edit)
            val deleteLabel = stringResource(I18nR.string.hc_delete)
            val shareLabel = stringResource(I18nR.string.search_action_share)
            val blockLabel = stringResource(I18nR.string.comms_block_number)
            CommsCabSheet(
                title = selected.displayName,
                actions = buildList {
                    if (number.isNotEmpty()) {
                        add(CommsCabAction(BaseR.drawable.rd_ic_phone_green_vector, callLabel) {
                            viewModel.dial(context, number)
                        })
                        add(CommsCabAction(BaseR.drawable.rd_ic_messages, messageLabel) {
                            context.tryStartActivity(MessengerIntentUtils.sms(number))
                        })
                    }
                    add(CommsCabAction(BaseR.drawable.info_24px, detailsLabel) {
                        backStack.add(ContactDetailsRoute(contactId = selected.id))
                    })
                    add(CommsCabAction(BaseR.drawable.edit_24px, editLabel) { ContactActions.edit(context, selected.id) })
                    add(CommsCabAction(BaseR.drawable.delete_24px, deleteLabel, destructive = true) { deleteContact = selected })
                    add(CommsCabAction(BaseR.drawable.share_24px, shareLabel) {
                        viewModel.share(context, selected)
                    })
                    if (number.isNotEmpty()) {
                        add(CommsCabAction(BaseR.drawable.delete_24px, blockLabel, destructive = true) {
                            viewModel.block(context, number)
                        })
                    }
                },
                onDismiss = { cabContact = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactRow(
    contact: DialerContact,
    query: String,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onLongPress: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.tertiaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
                label = "contact-swipe",
            )
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> BaseR.drawable.rd_ic_messages
                else -> BaseR.drawable.rd_ic_phone_green_vector
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment,
            ) {
                if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Icon(painterResource(icon), contentDescription = null)
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommsAvatar(
                name = contact.displayName,
                photoUri = contact.photoUri,
                size = 48.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = highlightedName(
                            contact.displayName,
                            query,
                            MaterialTheme.colorScheme.primary,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (contact.starred) {
                        Icon(
                            painterResource(BaseR.drawable.rd_ic_star_vector),
                            contentDescription = stringResource(I18nR.string.favorites),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .width(14.dp)
                                .height(14.dp),
                        )
                    }
                }
                Text(
                    text = contact.phoneNumbers.firstOrNull() ?: contact.emails.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCall, enabled = contact.phoneNumbers.isNotEmpty()) {
                Icon(
                    painterResource(BaseR.drawable.rd_ic_phone_green_vector),
                    contentDescription = stringResource(I18nR.string.search_action_call),
                    tint = RdGreenCall,
                )
            }
        }
    }
}
