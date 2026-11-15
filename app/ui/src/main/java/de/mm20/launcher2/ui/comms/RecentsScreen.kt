package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.search.GreekFold
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import de.mm20.launcher2.ui.component.TelosCyber
import de.mm20.launcher2.ui.component.TelosTechPill
import de.mm20.launcher2.ui.component.virtualAppPopup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.search.ContactSearch
import de.mm20.launcher2.comms.search.GreekText
import de.mm20.launcher2.comms.model.CallType
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class RecentsFilter {
    All, Today, Missed, Incoming, Outgoing, Rejected, TalkTime
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentsScreen(searchQuery: String = "", showFilters: Boolean = false) {
    val viewModel: RecentsViewModel = viewModel()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val hasCallLogPermission by viewModel.hasCallLogPermission.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    var filter by remember { mutableStateOf(RecentsFilter.All) }
    var cabCall by remember { mutableStateOf<GroupedRecent?>(null) }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val tapToCall by viewModel.tapToCall.collectAsStateWithLifecycle()
    val confirmBeforeCall by viewModel.confirmBeforeCall.collectAsStateWithLifecycle()
    val showNumbers by viewModel.showNumbers.collectAsStateWithLifecycle()
    val commsSettings: de.mm20.launcher2.preferences.comms.CommsSettings = org.koin.compose.koinInject()
    val sim1Key by commsSettings.sim1Color.collectAsStateWithLifecycle("green")
    val sim2Key by commsSettings.sim2Color.collectAsStateWithLifecycle("blue")
    val sims = remember { de.mm20.launcher2.comms.telephony.TelosDialer.callCapableSims(context) }

    if (!hasCallLogPermission) {
        Box(Modifier.fillMaxSize()) {
            EmptyCommsTab(
                title = stringResource(I18nR.string.permission_call_log_req),
                message = stringResource(I18nR.string.permission_call_log_msg),
            )
            Button(
                onClick = {
                    (context as? AppCompatActivity)?.let { viewModel.requestCallLogPermission(it) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
            ) {
                Text(stringResource(I18nR.string.permission_grant))
            }
        }
        return
    }

    val filtered = remember(recents, filter, searchQuery) {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val byType = when (filter) {
            RecentsFilter.All -> recents
            RecentsFilter.Today -> recents.filter { it.timestamp >= startOfDay }
            RecentsFilter.Missed -> recents.filter { it.type == CallType.Missed }
            RecentsFilter.Incoming -> recents.filter { it.type == CallType.Incoming }
            RecentsFilter.Outgoing -> recents.filter { it.type == CallType.Outgoing }
            RecentsFilter.Rejected -> recents.filter { it.type == CallType.Rejected }
            RecentsFilter.TalkTime -> recents.filter { it.durationSeconds > 0 }
        }
        if (searchQuery.isBlank()) byType
        else byType.filter { call ->
            de.mm20.launcher2.comms.search.TelosSearch.matches(searchQuery, call.displayName, call.phoneNumber)
        }
    }
    val collapsed = remember(filtered) { collapseRecents(filtered) }
    val contactsViewModel: ContactsViewModel = viewModel()
    val contacts by contactsViewModel.contacts.collectAsStateWithLifecycle()
    val defaultNumbers by contactsViewModel.defaultNumbers.collectAsStateWithLifecycle()
    val matchingContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else ContactSearch.search(searchQuery, contacts)
    }

    Column(Modifier.fillMaxSize()) {
        if (showFilters || filter != RecentsFilter.All) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { viewModel.export(context, filtered) }) {
                Icon(painterResource(BaseR.drawable.share_24px), contentDescription = stringResource(I18nR.string.hc_export))
            }
        }
        if (showFilters || filter != RecentsFilter.All) LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                TelosTechPill(
                    text = stringResource(I18nR.string.filter_all),
                    isSelected = filter == RecentsFilter.All,
                    onClick = { filter = RecentsFilter.All },
                    accentColor = TelosCyber.NeonCyan,
                )
            }
            item {
                TelosTechPill(
                    text = stringResource(I18nR.string.hc_today),
                    isSelected = filter == RecentsFilter.Today,
                    onClick = { filter = RecentsFilter.Today },
                    accentColor = TelosCyber.NeonGreen,
                )
            }
            item {
                TelosTechPill(
                    text = stringResource(I18nR.string.filter_missed),
                    isSelected = filter == RecentsFilter.Missed,
                    onClick = { filter = RecentsFilter.Missed },
                    accentColor = TelosCyber.NeonMagenta,
                )
            }
            item {
                TelosTechPill(
                    text = stringResource(I18nR.string.filter_incoming),
                    isSelected = filter == RecentsFilter.Incoming,
                    onClick = { filter = RecentsFilter.Incoming },
                    accentColor = TelosCyber.NeonCyan,
                )
            }
            item {
                TelosTechPill(
                    text = stringResource(I18nR.string.filter_outgoing),
                    isSelected = filter == RecentsFilter.Outgoing,
                    onClick = { filter = RecentsFilter.Outgoing },
                    accentColor = TelosCyber.NeonOrange,
                )
            }
            item {
                TelosTechPill(
                    text = stringResource(I18nR.string.filter_rejected),
                    isSelected = filter == RecentsFilter.Rejected,
                    onClick = { filter = RecentsFilter.Rejected },
                    accentColor = TelosCyber.NeonMagenta,
                )
            }
            item {
                val talk = recents.sumOf { it.durationSeconds }
                TelosTechPill(
                    text = stringResource(I18nR.string.hc_talk_duration, formatCallDuration(talk)),
                    isSelected = filter == RecentsFilter.TalkTime,
                    onClick = { filter = RecentsFilter.TalkTime },
                    accentColor = TelosCyber.NeonGreen,
                )
            }
        }

        if (filtered.isEmpty() && matchingContacts.isEmpty() && searchQuery.isNotBlank()) {
            de.mm20.launcher2.ui.component.SearchEmptyState(searchQuery)
        } else if (filtered.isEmpty() && searchQuery.isBlank()) {
            val isDefaultDialer = remember(context) {
                val pkg = context.packageName
                val tm = context.getSystemService(android.telecom.TelecomManager::class.java)
                tm?.defaultDialerPackage == pkg
            }
            EmptyCommsTab(
                title = stringResource(I18nR.string.recents_empty_title),
                message = if (isDefaultDialer) {
                    stringResource(I18nR.string.recents_empty_msg)
                } else {
                    stringResource(I18nR.string.au_phonea_history_empty_hint)
                },
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (searchQuery.isNotBlank() && collapsed.isNotEmpty()) item(key = "recents-heading") {
                    Text(stringResource(I18nR.string.au_phonea_recents), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall)
                }
                items(collapsed, key = { it.call.id }) { group ->
                    RecentCallRow(
                        call = group.call,
                        count = group.count,
                        showNumber = showNumbers,
                        simSlot = if (sims.size >= 2) {
                            sims.indexOfFirst { it.handle.id == group.call.simAccountId }.takeIf { it >= 0 }
                        } else null,
                        simColor = simAccentColor(
                            if (sims.indexOfFirst { it.handle.id == group.call.simAccountId } == 1) sim2Key else sim1Key
                        ),
                        onCall = {
                            if (!tapToCall) {
                                backStack.add(ContactDetailsRoute(phoneNumber = group.call.phoneNumber))
                            } else if (confirmBeforeCall) {
                                pendingCall = group.call.phoneNumber
                            } else {
                                viewModel.dial(context, group.call.phoneNumber)
                            }
                        },
                        onSms = { context.tryStartActivity(MessengerIntentUtils.sms(group.call.phoneNumber)) },
                        onDelete = { group.calls.forEach { viewModel.delete(it) } },
                        onDetails = {
                            backStack.add(ContactDetailsRoute(phoneNumber = group.call.phoneNumber))
                        },
                        onLongPress = { cabCall = group },
                    )
                }
                if (matchingContacts.isNotEmpty()) item(key = "contacts-heading") {
                    Text(stringResource(I18nR.string.preference_search_contacts), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleSmall)
                }
                items(matchingContacts, key = { "contact-${it.id}" }) { contact ->
                    val number = remember(contact, defaultNumbers) { contactsViewModel.numberFor(contact) }
                    val open = { backStack.add(ContactDetailsRoute(contactId = contact.id)); Unit }
                    ContactRow(
                        contact = contact, query = searchQuery, onOpen = open,
                        onCall = {
                            if (!tapToCall || number.isEmpty()) open()
                            else if (confirmBeforeCall) pendingCall = number
                            else viewModel.dial(context, number)
                        },
                        onSms = { if (number.isNotEmpty()) context.tryStartActivity(MessengerIntentUtils.sms(number)); Unit },
                        onLongPress = open,
                    )
                }
            }
        }
        pendingCall?.let { number ->
            AlertDialog(
                onDismissRequest = { pendingCall = null },
                containerColor = virtualAppPopup(),
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
        cabCall?.let { selectedGroup ->
            val selected = selectedGroup.call
            CommsCabSheet(
                title = selected.displayName?.ifBlank { null } ?: selected.phoneNumber,
                actions = listOf(
                    CommsCabAction(BaseR.drawable.rd_ic_phone_green_vector, stringResource(I18nR.string.search_action_call)) {
                        viewModel.dial(context, selected.phoneNumber)
                    },
                    CommsCabAction(BaseR.drawable.rd_ic_messages, stringResource(I18nR.string.search_action_message)) {
                        context.tryStartActivity(MessengerIntentUtils.sms(selected.phoneNumber))
                    },
                    CommsCabAction(BaseR.drawable.info_24px, stringResource(I18nR.string.contact_details_title)) {
                        backStack.add(ContactDetailsRoute(phoneNumber = selected.phoneNumber))
                    },
                    CommsCabAction(BaseR.drawable.share_24px, stringResource(I18nR.string.search_action_share)) {
                        de.mm20.launcher2.ui.common.share.ShareActions.shareContactVcard(
                            context,
                            selected.displayName?.ifBlank { null } ?: selected.phoneNumber,
                            listOf(selected.phoneNumber),
                            emptyList(),
                        )
                    },
                    CommsCabAction(BaseR.drawable.delete_24px, stringResource(I18nR.string.comms_clear_history_confirm), destructive = true) {
                        selectedGroup.calls.forEach { viewModel.delete(it) }
                    },
                ),
                onDismiss = { cabCall = null },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun RecentCallRow(
    call: CallLogEntry,
    count: Int = 1,
    showNumber: Boolean = true,
    simSlot: Int? = null,
    simColor: Color = Color.Unspecified,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val missed = call.type == CallType.Missed || call.type == CallType.Rejected
    val nameColor = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val typeIcon = when (call.type) {
        CallType.Outgoing -> BaseR.drawable.rd_ic_call_made_vector
        CallType.Missed, CallType.Rejected -> BaseR.drawable.rd_ic_call_missed_vector
        else -> BaseR.drawable.rd_ic_call_received_vector
    }
    
    val dismissState = // the row leaves the list when the call log changes; if deleting is not
        // allowed it springs back instead of staying dismissed
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        onDelete()
                        true
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        onSms()
                        false
                    }
                    else -> false
                }
            },
            positionalThreshold = { it * 0.4f },
        )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> RdSwipePurple.copy(alpha = 0.35f)
                    SwipeToDismissBoxValue.EndToStart -> RdRedCall.copy(alpha = 0.35f)
                    else -> Color.Transparent
                },
                label = "recents-swipe",
            )
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> BaseR.drawable.rd_ic_messages
                else -> BaseR.drawable.delete_24px
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
                .combinedClickable(onClick = onCall, onLongClick = onLongPress)
                .heightIn(min = 64.dp)
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommsAvatar(
                name = call.displayName ?: call.phoneNumber,
                photoUri = call.photoUri,
                size = 48.dp,
                modifier = Modifier.clickable(onClick = onDetails),
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                ) {
                    val callerLabel = if (call.callerNameSuggested && !call.displayName.isNullOrBlank())
                        stringResource(I18nR.string.caller_suggested_name, call.displayName!!) else call.displayName?.ifBlank { null } ?: call.phoneNumber
                    Text(
                        text = buildString {
                            append(callerLabel)
                            if (count > 1) append(" ($count)")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(typeIcon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                        if (simSlot != null) {
                            Spacer(Modifier.width(6.dp))
                            SimBadge(
                                slot = simSlot,
                                color = simColor,
                                label = null,
                            )
                        }
                        val secondary = listOfNotNull(
                            call.durationSeconds.takeIf { it > 0 }?.let { formatCallDuration(it) },
                            call.numberLabel
                                ?: call.phoneNumber.takeIf { showNumber && call.displayName != null },
                        ).joinToString("  ")
                        if (secondary.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = secondary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                )
            }
            Text(
                text = formatRowDate(call.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(onClick = onDetails, modifier = Modifier.padding(end = 4.dp)) {
                Icon(
                    painterResource(BaseR.drawable.info_24px),
                    contentDescription = stringResource(I18nR.string.contact_details_title),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private data class GroupedRecent(val call: CallLogEntry, val count: Int, val calls: List<CallLogEntry>)

private fun collapseRecents(recents: List<CallLogEntry>): List<GroupedRecent> {
    val out = mutableListOf<GroupedRecent>()
    for (call in recents) {
        val last = out.lastOrNull()
        if (last != null && PhoneNumbers.match(last.call.phoneNumber, call.phoneNumber)) {
            out[out.lastIndex] = last.copy(count = last.count + 1, calls = last.calls + call)
        } else {
            out += GroupedRecent(call, 1, listOf(call))
        }
    }
    return out
}

private fun dayKey(timestamp: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

@Composable
private fun formatDayHeader(day: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val yesterday = today - 24L * 60 * 60 * 1000
    return when (day) {
        today -> stringResource(I18nR.string.comms_today)
        yesterday -> stringResource(I18nR.string.comms_yesterday)
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(day))
    }
}

private fun formatRowDate(timestamp: Long): String {
    val pattern = if (dayKey(timestamp) == dayKey(System.currentTimeMillis())) "HH:mm" else "dd.MM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}
