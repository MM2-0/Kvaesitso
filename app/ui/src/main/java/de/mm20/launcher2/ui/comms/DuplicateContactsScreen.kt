package de.mm20.launcher2.ui.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.ui.res.pluralStringResource
import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.comms.model.DialerContact
import de.mm20.launcher2.comms.repository.ContactDirectoryRepository
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
data object DuplicateContactsRoute : NavKey

class DuplicateContactsViewModel : ViewModel(), KoinComponent {
    private val repo: ContactDirectoryRepository by inject()
    suspend fun load(): List<List<DialerContact>> =
        runCatching { repo.findDuplicateGroups() }.getOrDefault(emptyList())

    /**
     * The contacts that go away when the first one of every group is kept. A contact that is the
     * first one of any group is never deleted, even when it is an extra one in another group.
     */
    fun idsToDelete(groups: List<List<DialerContact>>): Set<Long> {
        val kept = groups.mapNotNull { it.firstOrNull()?.id }.toSet()
        return groups.flatMap { it.drop(1) }.map { it.id }.toSet() - kept
    }

    /** Returns the number of contacts that could not be deleted */
    suspend fun keepFirst(groups: List<List<DialerContact>>): Int =
        idsToDelete(groups).count { id -> !runCatching { repo.deleteContact(id) }.getOrDefault(false) }

    suspend fun mergeGroup(group: List<DialerContact>): Boolean =
        runCatching { repo.mergeContactGroup(group) }.getOrDefault(false)

    suspend fun mergeAll(): Int =
        runCatching { repo.mergeAllDuplicateGroups() }.getOrDefault(0)
}

@Composable
fun DuplicateContactsScreen() {
    val viewModel: DuplicateContactsViewModel = viewModel()
    val backStack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var groups by remember { mutableStateOf<List<List<DialerContact>>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<List<DialerContact>>?>(null) }

    LaunchedEffect(Unit) {
        groups = viewModel.load()
        loaded = true
    }

    pending?.let { toClean ->
        val count = viewModel.idsToDelete(toClean).size
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pending = null },
            title = { TelosCyberHeading {  Text(pluralStringResource(I18nR.plurals.hc_delete_contacts_question, count, count))  } },
            text = { Text(stringResource(I18nR.string.hc_delete_duplicates_message, count)) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        val failed = viewModel.keepFirst(toClean)
                        groups = viewModel.load()
                        if (failed > 0) {
                            Toast.makeText(context, I18nR.string.au_phonea_action_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(stringResource(I18nR.string.hc_delete)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }

    PreferenceScreen(title = { Text(stringResource(I18nR.string.hc_duplicate_contacts)) }) {
        if (!loaded) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        if (loaded && groups.isEmpty()) {
            item {
                Text(
                    stringResource(I18nR.string.hc_no_duplicate_numbers_found),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        groups.forEachIndexed { index, group ->
            item {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(I18nR.string.hc_group_number_phone, index + 1, group.first().phoneNumbers.firstOrNull().orEmpty()),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        )
                        group.forEach { contact ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                CommsAvatar(name = contact.displayName, photoUri = contact.photoUri, size = 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${contact.displayName} · ${contact.phoneNumbers.joinToString()}")
                                    contact.accounts.forEach { account ->
                                        Text(
                                            "${account.name ?: stringResource(I18nR.string.contacts_account_device)}${account.type?.let { " · $it" }.orEmpty()}",
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { backStack.add(ContactDetailsRoute(contactId = group.first().id)) }
                            ) { Text(stringResource(I18nR.string.hc_open)) }
                            OutlinedButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        val ok = viewModel.mergeGroup(group)
                                        groups = viewModel.load()
                                        if (!ok) {
                                            Toast.makeText(context, I18nR.string.au_phonea_action_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) { Text(stringResource(I18nR.string.comms_merge)) }
                            Button(
                                onClick = { pending = listOf(group) }
                            ) { Text(stringResource(I18nR.string.hc_keep_first_delete_extras)) }
                        }
                    }
                }
            }
        }
        if (groups.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                viewModel.mergeAll()
                                groups = viewModel.load()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(I18nR.string.comms_merge)) }
                    TextButton(
                        onClick = { pending = groups },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(I18nR.string.hc_keep_first_in_every_group_delete_extras)) }
                }
            }
        }
    }
}
