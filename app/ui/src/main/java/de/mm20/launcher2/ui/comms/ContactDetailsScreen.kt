package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.produceState
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.PhoneNumbers
import de.mm20.launcher2.comms.intent.MessengerIntentUtils
import de.mm20.launcher2.comms.model.CallLogEntry
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.CallLogRepository
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.comms.repository.SpamRepository
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data class ContactDetailsRoute(
    val contactId: Long = -1L,
    val phoneNumber: String = "",
) : NavKey

internal data class ContactDetailsUi(
    val contact: DialerContact?,
    val recents: List<CallLogEntry>,
    val blockStatus: ContactBlockStatus,
)

class ContactDetailsViewModel : ViewModel(), KoinComponent {
    private val contactDir: ContactDirectoryRepository by inject()
    private val callLog: CallLogRepository by inject()
    private val spam: SpamRepository by inject()
    private val commsSettings: CommsSettings by inject()

    val sim1Color = commsSettings.sim1Color.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "green")
    val sim2Color = commsSettings.sim2Color.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "blue")

    fun setSpeedDial(digit: Int, number: String) {
        commsSettings.setSpeedDial(digit, number)
    }

    val clirPrefix = commsSettings.clirPrefix.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "",
    )
    val clirEnabled = commsSettings.clirEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )
    val callerNotes = commsSettings.callerNotes.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap(),
    )
    val numberDefaultSim = commsSettings.numberDefaultSim.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap(),
    )
    val contactDefaultNumbers = commsSettings.contactDefaultNumbers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap(),
    )
    val contactDefaultCallApp = commsSettings.contactDefaultCallApp.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap(),
    )
    val contactDefaultMessageApp = commsSettings.contactDefaultMessageApp.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap(),
    )

    fun setDefaultCallApp(number: String, appId: String?) {
        commsSettings.setContactDefaultCallApp(number, appId)
    }

    fun setDefaultMessageApp(number: String, appId: String?) {
        commsSettings.setContactDefaultMessageApp(number, appId)
    }
    val hiddenNumbers = commsSettings.hiddenNumbers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap(),
    )
    val protectedCallNumbers = commsSettings.protectedCallNumbers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap(),
    )

    /** Bumped after the block list changed so that the blocked state is read again */
    private val blockedTick = MutableStateFlow(0)

    private var cachedKey: Pair<Long, String>? = null
    private var cachedUi: StateFlow<ContactDetailsUi>? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun observeDetails(contactId: Long, phoneNumber: String): StateFlow<ContactDetailsUi> {
        val key = contactId to phoneNumber
        cachedUi?.let { if (cachedKey == key) return it }
        cachedKey = key
        val contactFlow = contactDir.observeContacts().map { list ->
            list.find { it.id == contactId }
                ?: list.find { c ->
                    phoneNumber.isNotEmpty() && c.phoneNumbers.any { PhoneNumbers.match(it, phoneNumber) }
                }
                ?: phoneNumber.takeIf { it.isNotEmpty() }?.let {
                    DialerContact(id = -1L, displayName = it, phoneNumbers = listOf(it))
                }
        }.distinctUntilChanged()

        val recentsFlow = contactFlow.flatMapLatest { contact ->
            val numbers = contact?.phoneNumbers.orEmpty()
            if (numbers.isEmpty()) flowOf(emptyList()) else callLog.observeForNumbers(numbers)
        }
        val blockedFlow = combine(contactFlow, blockedTick) { contact, _ -> contact }.flatMapLatest { contact ->
            val number = contact?.phoneNumbers?.firstOrNull().orEmpty()
            if (number.isEmpty()) flowOf(ContactBlockStatus.Unavailable) else flow {
                emit(ContactBlockStatus.Loading)
                emit(readContactBlockStatus({ spam.isNumberBlocked(number) }) {
                    android.util.Log.e("ContactDetails", "Could not read encrypted block status", it)
                })
            }
        }

        val ui = combine(contactFlow, recentsFlow, blockedFlow) { contact, recents, blocked ->
            ContactDetailsUi(contact, recents, blocked)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ContactDetailsUi(null, emptyList(), ContactBlockStatus.Loading),
        )
        cachedUi = ui
        return ui
    }

    fun retryBlockStatus() { blockedTick.value += 1 }

    fun dial(context: Context, number: String) {
        if (number.isEmpty()) return
        viewModelScope.launch {
            val simLabel = numberDefaultSim.value[number]
            val handle = de.mm20.launcher2.comms.telephony.TelosDialer.callCapableSims(context)
                .find { it.label == simLabel }?.handle
            de.mm20.launcher2.comms.privacy.CallGuard.place(context, number, handle)
        }
    }

    fun dialSip(context: Context, number: String) {
        if (number.isEmpty()) return
        viewModelScope.launch {
            val ok = de.mm20.launcher2.comms.privacy.CallGuard.placeSip(context, number)
            if (!ok) {
                android.widget.Toast.makeText(
                    context,
                    de.mm20.launcher2.comms.sip.SipDialer.lastFailure
                        .ifBlank { context.getString(I18nR.string.au_phoneb_sip_err_not_connected) },
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** [onFailed] runs on the main thread when the contact could not be changed */
    fun setStarred(context: Context, contactId: Long, starred: Boolean, onFailed: () -> Unit) {
        if (contactId < 0) {
            onFailed()
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
                    }
                    context.contentResolver.update(
                        android.content.ContentUris.withAppendedId(
                            android.provider.ContactsContract.Contacts.CONTENT_URI,
                            contactId,
                        ),
                        values,
                        null,
                        null,
                    ) > 0
                }.getOrDefault(false)
            }
            if (!ok) onFailed()
        }
    }

    fun setHidden(numbers: List<String>, hidden: Boolean) {
        commsSettings.setHiddenNumbers(numbers, hidden)
        if (hidden) de.mm20.launcher2.comms.privacy.PrivacySession.lockHider()
    }

    fun setCallProtected(number: String, protected: Boolean) {
        commsSettings.setProtectedCallNumber(number, protected)
    }

    fun setDefaultNumber(contactId: Long, number: String?) {
        commsSettings.setContactDefaultNumber(contactId.toString(), number)
    }

    fun share(context: Context, contact: DialerContact) {
        de.mm20.launcher2.ui.common.share.ShareActions.shareContactVcard(context, contact.displayName, contact.phoneNumbers, contact.emails)
    }

    fun setNote(number: String, note: String?) {
        commsSettings.setCallerNote(number, note)
    }

    fun setDefaultSim(number: String, sim: String?) {
        commsSettings.setNumberDefaultSim(number, sim)
    }

    fun deleteHistory(numbers: List<String>) {
        viewModelScope.launch { callLog.deleteForNumbers(numbers) }
    }

    fun setBlocked(context: Context, number: String, blocked: Boolean) {
        viewModelScope.launch {
            updateBlockedWithFeedback(context, spam, number, blocked)
            blockedTick.value += 1
        }
    }

    fun moveContactToAccount(contactId: Long, accountName: String?, accountType: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = contactDir.moveContactToAccount(contactId, accountName, accountType)
            onResult(ok)
        }
    }
}

@Composable
fun ContactDetailsScreen(contactId: Long, phoneNumber: String = "") {
    val viewModel: ContactDetailsViewModel = viewModel()
    val ui by viewModel.observeDetails(contactId, phoneNumber).collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val notes by viewModel.callerNotes.collectAsStateWithLifecycle()
    val numberSims by viewModel.numberDefaultSim.collectAsStateWithLifecycle()
    val defaultNumbers by viewModel.contactDefaultNumbers.collectAsStateWithLifecycle()
    val defaultCallApp by viewModel.contactDefaultCallApp.collectAsStateWithLifecycle()
    val defaultMessageApp by viewModel.contactDefaultMessageApp.collectAsStateWithLifecycle()
    val hiddenNumbers by viewModel.hiddenNumbers.collectAsStateWithLifecycle()
    val protectedNumbers by viewModel.protectedCallNumbers.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteContact by remember { mutableStateOf<DialerContact?>(null) }
    var editingNote by remember { mutableStateOf(false) }
    var starOverride by remember { mutableStateOf<Boolean?>(null) }
    var showReminder by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showSpeedDial by remember { mutableStateOf(false) }
    var showMoveAccount by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val contact = ui.contact
    val blocked = ui.blockStatus == ContactBlockStatus.Blocked
    val primary = contact?.phoneNumbers?.firstOrNull().orEmpty()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ringtoneLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val picked = androidx.core.content.IntentCompat.getParcelableExtra(
                data,
                android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java,
            )
            contact?.let {
                scope.launch {
                    if (!ContactActions.setRingtone(context, it.id, picked)) {
                        android.widget.Toast.makeText(
                            context, I18nR.string.au_phonea_action_failed, android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }
    val hasEmail = !contact?.emails.isNullOrEmpty()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { backStack.removeLastOrNull() }) {
                    Icon(
                        painterResource(BaseR.drawable.arrow_back_24px),
                        contentDescription = stringResource(I18nR.string.hc_back),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (contact != null && contact.id >= 0) {
                    IconButton(onClick = { ContactActions.edit(context, contact.id) }) {
                        Icon(painterResource(BaseR.drawable.edit_24px), contentDescription = stringResource(I18nR.string.net_dns_action_edit))
                    }
                }
                val starred = starOverride ?: (contact?.starred == true)
                IconButton(
                    onClick = {
                        contact?.let {
                            starOverride = !starred
                            viewModel.setStarred(context, it.id, !starred) {
                                starOverride = null
                                android.widget.Toast.makeText(
                                    context, I18nR.string.au_phonea_action_failed, android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                    enabled = contact != null && contact.id >= 0,
                ) {
                    Icon(
                        painterResource(
                            if (starred) BaseR.drawable.star_24px_filled else BaseR.drawable.star_24px_outlined
                        ),
                        contentDescription = stringResource(I18nR.string.favorites),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = { contact?.let { viewModel.share(context, it) } },
                    enabled = contact != null,
                ) {
                    Icon(
                        painterResource(BaseR.drawable.share_24px),
                        contentDescription = stringResource(I18nR.string.search_action_share),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (contact != null && contact.id >= 0) {
                    IconButton(onClick = { deleteContact = contact }) {
                        Icon(painterResource(BaseR.drawable.delete_24px), contentDescription = stringResource(I18nR.string.hc_delete))
                    }
                }
                var more by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { more = true }) {
                        Icon(painterResource(BaseR.drawable.more_vert_24px), contentDescription = stringResource(I18nR.string.hc_more))
                    }
                    androidx.compose.material3.DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(I18nR.string.comms_clear_history)) },
                            enabled = contact != null && ui.recents.isNotEmpty(),
                            onClick = { more = false; confirmDelete = true },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (contact == null) {
            EmptyCommsTab(
                title = stringResource(I18nR.string.contact_details_title),
                message = stringResource(I18nR.string.contacts_empty_msg),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (contact.id < 0 && primary.isNotBlank()) item {
                CallerLookupCard(primary, hiddenNumbers)
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CommsAvatar(
                        name = contact.displayName,
                        photoUri = contact.photoUri,
                        size = 88.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!contact.company.isNullOrBlank()) {
                        Text(
                            text = contact.company.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!contact.jobTitle.isNullOrBlank()) {
                        Text(
                            text = contact.jobTitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (contact.birthdayMillis != null) {
                        Text(
                            text = java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault())
                                .format(java.util.Date(contact.birthdayMillis!!)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CommsActionCard(
                            icon = BaseR.drawable.rd_ic_messages,
                            label = stringResource(I18nR.string.search_action_message).uppercase(),
                            enabled = primary.isNotEmpty(),
                            onClick = { context.tryStartActivity(MessengerIntentUtils.sms(primary)) },
                            modifier = Modifier.weight(1f),
                        )
                        CommsActionCard(
                            icon = BaseR.drawable.rd_ic_phone_green_vector,
                            label = stringResource(I18nR.string.search_action_call).uppercase(),
                            enabled = primary.isNotEmpty(),
                            onClick = { viewModel.dial(context, primary) },
                            modifier = Modifier.weight(1f),
                        )
                        CommsActionCard(
                            icon = BaseR.drawable.videocam_24px,
                            label = stringResource(I18nR.string.comms_action_video).uppercase(),
                            enabled = primary.isNotEmpty(),
                            onClick = { context.tryStartActivity(MessengerIntentUtils.whatsApp(primary)) },
                            modifier = Modifier.weight(1f),
                        )
                        CommsActionCard(
                            icon = BaseR.drawable.mail_24px,
                            label = stringResource(I18nR.string.search_action_email).uppercase(),
                            enabled = hasEmail,
                            onClick = {
                                contact.emails.firstOrNull()?.let {
                                    context.tryStartActivity(MessengerIntentUtils.email(it))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                CommsDetailCard {
                    if (ui.recents.isEmpty()) {
                        Text(
                            text = stringResource(I18nR.string.comms_no_previous_calls),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        ui.recents.take(8).forEach { CommsHistoryRow(it) }
                    }
                }
            }

            items(contact.phoneNumbers) { number ->
                val isDefault = defaultNumbers[contact.id.toString()] == number ||
                    (defaultNumbers[contact.id.toString()] == null && number == primary)
                CommsDetailCard(onClick = { viewModel.dial(context, number) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(I18nR.string.hc_mobile),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = number,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (contact.phoneNumbers.size > 1) {
                                TextButton(onClick = { viewModel.setDefaultNumber(contact.id, number) }) {
                                    Text(stringResource(if (isDefault) I18nR.string.au_phonea_default_number else I18nR.string.au_phonea_set_default))
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.dial(context, number) }) {
                            Icon(
                                painterResource(BaseR.drawable.rd_ic_phone_green_vector),
                                contentDescription = stringResource(I18nR.string.search_action_call),
                                tint = RdGreenCall,
                            )
                        }
                    }
                }
            }

            if (primary.isNotEmpty()) {
                // Messenger apps: chat, plus voice and video calls where the app adds them to the contact
                item {
                    val actions by produceState(initialValue = emptyMap<String, DataAction?>(), contact.id) {
                        value = withContext(Dispatchers.IO) {
                            mapOf(
                                "wa_call" to ContactActions.find(context, contact.id, ContactActions.whatsAppCall),
                                "wa_video" to ContactActions.find(context, contact.id, ContactActions.whatsAppVideo),
                                "tg_call" to ContactActions.find(context, contact.id, ContactActions.telegramCall),
                                "sg_call" to ContactActions.find(context, contact.id, ContactActions.signalCall),
                                "vb_call" to ContactActions.find(context, contact.id, ContactActions.viberCall),
                            )
                        }
                    }
                    fun launcher(action: DataAction?): (() -> Unit)? =
                        if (action == null) null else ({ context.tryStartActivity(action.intent()); Unit })
                    CommsDetailCard {
                        Text(
                            text = stringResource(I18nR.string.comms_integrations),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        MessengerRow(
                            label = stringResource(I18nR.string.action_whatsapp),
                            icon = BaseR.drawable.rd_ic_whatsapp_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.whatsApp(primary)) },
                            onCall = launcher(actions["wa_call"]),
                            onVideo = launcher(actions["wa_video"]),
                        )
                        MessengerRow(
                            label = stringResource(I18nR.string.action_telegram),
                            icon = BaseR.drawable.rd_ic_telegram_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.telegram(primary)) },
                            onCall = launcher(actions["tg_call"]),
                            onVideo = null,
                        )
                        MessengerRow(
                            label = stringResource(I18nR.string.action_signal),
                            icon = BaseR.drawable.rd_ic_signal_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.signal(primary)) },
                            onCall = launcher(actions["sg_call"]),
                            onVideo = null,
                        )
                        MessengerRow(
                            label = stringResource(I18nR.string.action_viber),
                            icon = BaseR.drawable.rd_ic_viber_vector,
                            onChat = { context.tryStartActivity(MessengerIntentUtils.viber(primary)) },
                            onCall = launcher(actions["vb_call"]),
                            onVideo = null,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(I18nR.string.comms_pref_default_call),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ChannelChips(
                            selected = de.mm20.launcher2.comms.intent.MessengerApps.resolvePref(defaultCallApp, primary).id,
                            options = listOf(
                                "phone" to stringResource(I18nR.string.comms_channel_phone),
                                "signal" to stringResource(I18nR.string.action_signal),
                                "whatsapp" to stringResource(I18nR.string.action_whatsapp),
                                "telegram" to stringResource(I18nR.string.action_telegram),
                                "viber" to stringResource(I18nR.string.action_viber),
                            ).filter { (id, _) ->
                                id == "phone" || de.mm20.launcher2.comms.intent.MessengerApps.installedPackage(
                                    context,
                                    de.mm20.launcher2.comms.intent.MessengerChannel.fromId(id),
                                ) != null
                            },
                            onSelect = { viewModel.setDefaultCallApp(primary, it) },
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(I18nR.string.comms_pref_default_message),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ChannelChips(
                            selected = de.mm20.launcher2.comms.intent.MessengerApps.resolvePref(defaultMessageApp, primary).let {
                                if (it == de.mm20.launcher2.comms.intent.MessengerChannel.Phone) "sms" else it.id
                            },
                            options = listOf(
                                "sms" to stringResource(I18nR.string.comms_channel_sms),
                                "signal" to stringResource(I18nR.string.action_signal),
                                "whatsapp" to stringResource(I18nR.string.action_whatsapp),
                                "telegram" to stringResource(I18nR.string.action_telegram),
                                "viber" to stringResource(I18nR.string.action_viber),
                            ).filter { (id, _) ->
                                id == "sms" || de.mm20.launcher2.comms.intent.MessengerApps.installedPackage(
                                    context,
                                    de.mm20.launcher2.comms.intent.MessengerChannel.fromId(id),
                                ) != null
                            },
                            onSelect = { viewModel.setDefaultMessageApp(primary, it) },
                        )
                    }
                }
            }

            items(contact.emails) { email ->
                CommsDetailCard(
                    onClick = { context.tryStartActivity(MessengerIntentUtils.email(email)) },
                ) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            val currentContact = contact
            if (currentContact != null && currentContact.id >= 0) {
                item {
                    CommsDetailCard(onClick = { showMoveAccount = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(I18nR.string.hc_account),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(2.dp))
                                val deviceAccountStr = stringResource(I18nR.string.contacts_account_device)
                                val accountSummary = if (currentContact.accounts.isEmpty()) {
                                    deviceAccountStr
                                } else {
                                    currentContact.accounts.joinToString(", ") { it.name ?: deviceAccountStr }
                                }
                                Text(
                                    text = accountSummary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Icon(
                                painterResource(BaseR.drawable.open_in_new_24px),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (primary.isNotEmpty()) {
                item {
                    val sims = remember { de.mm20.launcher2.comms.telephony.TelosDialer.callCapableSims(context) }
                    if (sims.size >= 2 && de.mm20.launcher2.comms.telephony.TelosDialer.hasDualActiveSims(context)) {
                        val sim1Key by viewModel.sim1Color.collectAsStateWithLifecycle()
                        val sim2Key by viewModel.sim2Color.collectAsStateWithLifecycle()
                        CommsDetailCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(I18nR.string.hc_always_use_this_sim_for_this_number),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                sims.take(2).forEachIndexed { index, sim ->
                                    val selected = numberSims[primary] == sim.label
                                    val accent = simAccentColor(if (index == 1) sim2Key else sim1Key)
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(40.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                if (selected) accent
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                            .clickable {
                                                viewModel.setDefaultSim(primary, if (selected) null else sim.label)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = (index + 1).toString(),
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    val sipSettings: CommsSettings = org.koin.compose.koinInject()
                    val sipEnabled by sipSettings.sipEnabled.collectAsStateWithLifecycle(false)
                    val sipMode by sipSettings.sipOutgoing.collectAsStateWithLifecycle("choose")
                    val sipRegistration by de.mm20.launcher2.comms.sip.SipEngine.registration.collectAsStateWithLifecycle()
                    if (sipEnabled && sipMode != "off" &&
                        sipRegistration == de.mm20.launcher2.comms.sip.SipRegistration.Registered
                    ) {
                        CommsDetailCard(onClick = { viewModel.dialSip(context, primary) }) {
                            Text(
                                text = stringResource(I18nR.string.hc_call_over_sip),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                item {
                    val note = notes[primary]
                    CommsDetailCard(onClick = { editingNote = true }) {
                        Text(
                            text = if (note.isNullOrBlank()) stringResource(I18nR.string.au_phonea_add_notes) else note,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (note.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                item {
                    CommsDetailCard {
                        Text(
                            text = stringResource(I18nR.string.hc_more),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(
                                            android.content.ClipData.newPlainText("number", primary)
                                        )
                                        android.widget.Toast.makeText(context, context.getString(I18nR.string.hc_copied), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text(stringResource(I18nR.string.hc_copy_number)) },
                                )
                            }
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = { showReminder = true },
                                    label = { Text(stringResource(I18nR.string.hc_remind_me)) },
                                )
                            }
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = { showQr = true },
                                    label = { Text(stringResource(I18nR.string.hc_qr_code)) },
                                )
                            }
                            item {
                                androidx.compose.material3.AssistChip(
                                    onClick = { showSpeedDial = true },
                                    label = { Text(stringResource(I18nR.string.hc_speed_dial)) },
                                )
                            }
                            if (contact.id >= 0) item {
                                androidx.compose.material3.AssistChip(
                                    onClick = {
                                        ringtoneLauncher.launch(
                                            Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
                                                .putExtra(
                                                    android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                    android.media.RingtoneManager.TYPE_RINGTONE,
                                                )
                                                .putExtra(
                                                    android.media.RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                    contact.displayName,
                                                )
                                        )
                                    },
                                    label = { Text(stringResource(I18nR.string.hc_ringtone)) },
                                )
                            }
                        }
                    }
                }
                item {
                    val hidden = de.mm20.launcher2.comms.privacy.HiddenContacts.contactHidden(contact, hiddenNumbers)
                    CommsDetailCard(onClick = { viewModel.setHidden(contact.phoneNumbers, !hidden) }) {
                        Text(
                            text = stringResource(if (hidden) I18nR.string.au_phonea_unhide_contact else I18nR.string.au_phonea_hide_contact),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                item {
                    val guarded = de.mm20.launcher2.comms.privacy.HiddenContacts.matches(primary, protectedNumbers)
                    CommsDetailCard(onClick = { viewModel.setCallProtected(primary, !guarded) }) {
                        Text(
                            text = stringResource(if (guarded) I18nR.string.au_phonea_unprotect_call else I18nR.string.au_phonea_protect_call),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                item {
                    if (ui.blockStatus == ContactBlockStatus.Loading) {
                        CommsDetailCard {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    } else if (ui.blockStatus == ContactBlockStatus.Unavailable) {
                        CommsDetailCard {
                            Text(stringResource(I18nR.string.comms_block_number))
                            Text(stringResource(I18nR.string.shortcut_label_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = viewModel::retryBlockStatus) { Text(stringResource(I18nR.string.hc_retry)) }
                        }
                    } else CommsDetailCard(
                        onClick = {
                            val next = !blocked
                            viewModel.setBlocked(context, primary, next)
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (blocked) I18nR.string.comms_unblock_number else I18nR.string.comms_block_number
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (blocked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }

    if (showReminder && contact != null) {
        AlertDialog(
            onDismissRequest = { showReminder = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_remind_me_to_call_back))  } },
            text = {
                Column {
                    listOf(5, 15, 30, 60, 180).forEach { minutes ->
                        TextButton(onClick = {
                            de.mm20.launcher2.comms.reminder.CallbackReminder.schedule(
                                context, primary, contact.displayName, minutes,
                            )
                            showReminder = false
                        }) {
                            Text(
                                if (minutes < 60) stringResource(I18nR.string.au_phonea_in_minutes, minutes)
                                else stringResource(I18nR.string.au_phonea_in_hours, minutes / 60)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showReminder = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }

    if (showQr && contact != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { TelosCyberHeading {  Text(contact.displayName)  } },
            text = {
                ContactQrImage(
                    payload = contactVcard(contact.displayName, contact.phoneNumbers, contact.emails),
                    modifier = Modifier.size(240.dp),
                )
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(stringResource(I18nR.string.hc_close)) } },
        )
    }

    if (showSpeedDial && contact != null) {
        AlertDialog(
            onDismissRequest = { showSpeedDial = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_assign_to_speed_dial))  } },
            text = {
                Column {
                    Text(stringResource(I18nR.string.hc_long_press_digit_to_call, contact.displayName))
                    (2..9).chunked(4).forEach { digits ->
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            digits.forEach { digit ->
                                TextButton(
                                    onClick = {
                                        viewModel.setSpeedDial(digit, primary)
                                        showSpeedDial = false
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                    modifier = Modifier.size(48.dp),
                                ) { Text(digit.toString()) }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSpeedDial = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }

    if (showMoveAccount && contact != null) {
        val accounts = remember { android.accounts.AccountManager.get(context).accounts }
        AlertDialog(
            onDismissRequest = { showMoveAccount = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_account))  } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showMoveAccount = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                viewModel.moveContactToAccount(contact.id, null, null) { ok ->
                                    if (!ok) android.widget.Toast.makeText(context, I18nR.string.au_phonea_action_failed, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(I18nR.string.contacts_account_device))
                    }
                    accounts.forEach { acc ->
                        TextButton(
                            onClick = {
                                showMoveAccount = false
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    viewModel.moveContactToAccount(contact.id, acc.name, acc.type) { ok ->
                                        if (!ok) android.widget.Toast.makeText(context, I18nR.string.au_phonea_action_failed, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${acc.name} (${acc.type})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMoveAccount = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }

    deleteContact?.let { DeleteContactDialog(it, onDeleted = { backStack.removeLastOrNull() }) { deleteContact = null } }
    if (editingNote && contact != null && primary.isNotEmpty()) {
        var draft by remember(primary) { mutableStateOf(notes[primary].orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_notes))  } },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setNote(primary, draft)
                    editingNote = false
                }) { Text(stringResource(I18nR.string.action_done)) }
            },
            dismissButton = {
                TextButton(onClick = { editingNote = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (confirmDelete && contact != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.comms_clear_history))  } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteHistory(contact.phoneNumbers)
                    confirmDelete = false
                }) { Text(stringResource(I18nR.string.comms_clear_history_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ChannelChips(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun MessengerRow(
    label: String,
    icon: Int,
    onChat: () -> Unit,
    onCall: (() -> Unit)?,
    onVideo: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (onVideo != null) MessengerButton(BaseR.drawable.videocam_24px, stringResource(I18nR.string.au_phonea_video_call), onVideo)
        if (onCall != null) MessengerButton(BaseR.drawable.call_24px, stringResource(I18nR.string.search_action_call), onCall)
        MessengerButton(BaseR.drawable.rd_ic_messages, stringResource(I18nR.string.search_action_message), onChat)
    }
}

@Composable
private fun MessengerButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}


/** A visible opt-in and source link instead of a silent, unexplained missing caller result. */
@Composable
private fun CallerLookupCard(number: String, hidden: Map<String, String>) {
    val settings: CommsSettings = org.koin.compose.koinInject()
    val lookup: de.mm20.launcher2.comms.callerid.CallerLookup = org.koin.compose.koinInject()
    val enabled by settings.onlineCallerLookup.collectAsStateWithLifecycle(false)
    val results by lookup.results.collectAsStateWithLifecycle()
    val key = remember(number) { de.mm20.launcher2.comms.callerid.CallerLookupParser.greekNumber(number) }
    val isHidden = remember(number, hidden) { de.mm20.launcher2.comms.privacy.HiddenContacts.matches(number, hidden) }
    var loading by remember(number) { mutableStateOf(false) }
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(number, enabled, isHidden) {
        if (enabled && !isHidden && key != null) {
            loading = true
            try {
                // Initial UI defaults are not proof that the number is public or unknown.
                val privacy = settings.snapshot.first()
                val canRead = androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val directory: ContactDirectoryRepository =
                    org.koin.mp.KoinPlatform.getKoin().get()
                val saved = !canRead || try { directory.containsNumber(number) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { true }
                if (canRead && !saved && privacy.onlineCallerLookup &&
                    !de.mm20.launcher2.comms.privacy.HiddenContacts.matches(number, privacy.hiddenNumbers)) lookup.lookup(number)
            } finally { loading = false }
        }
    }
    if (key == null || isHidden) return
    CommsDetailCard {
        de.mm20.launcher2.ui.component.preferences.SwitchPreference(
            title = stringResource(I18nR.string.caller_online_lookup),
            summary = stringResource(I18nR.string.caller_online_disclosure),
            value = enabled, onValueChanged = settings::setOnlineCallerLookup,
        )
        val result = results[key].takeIf { enabled }
        if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
        else if (result != null) {
            result.suggestedName?.let { Text(stringResource(I18nR.string.caller_suggested_name, it)) }
            if (result.likelySpam) Text(stringResource(I18nR.string.caller_likely_spam), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { context.tryStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.sourceUrl))) }) {
                Text("white-pages.gr")
            }
        } else if (enabled) Text(stringResource(I18nR.string.shortcut_label_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
