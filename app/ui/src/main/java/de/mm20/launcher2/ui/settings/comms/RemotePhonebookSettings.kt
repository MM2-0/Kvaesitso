package de.mm20.launcher2.ui.settings.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.remote.RemotePhonebook
import de.mm20.launcher2.comms.remote.SecretBox
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.DateFormat
import java.util.Date

@Composable
fun RemotePhonebookSettings() {
    val settings: CommsSettings = koinInject()
    val scope = rememberCoroutineScope()
    val enabled by settings.remotePhonebookEnabled.collectAsStateWithLifecycle(false)
    val host by settings.remotePhonebookHost.collectAsStateWithLifecycle("fritz.box")
    val user by settings.remotePhonebookUser.collectAsStateWithLifecycle("")
    val passwordEnc by settings.remotePhonebookPasswordEnc.collectAsStateWithLifecycle("")
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val syncingText = stringResource(I18nR.string.au3_commsfreeze_syncing)
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val syncedText: (Int) -> String = { resources.getString(I18nR.string.au3_commsfreeze_synced_count, it) }
    val failedText: (String) -> String = { resources.getString(I18nR.string.au3_commsfreeze_sync_failed, it) }

    PreferenceCategory(title = stringResource(I18nR.string.hc_remote_phonebook_fritz_box)) {
        SwitchPreference(
            title = stringResource(I18nR.string.hc_identify_callers_from_fritz_box),
            summary = stringResource(I18nR.string.hc_show_names_from_the_fritz_box_telephone),
            value = enabled,
            onValueChanged = {
                settings.setRemotePhonebook(it, host, user, passwordEnc)
                if (!it) RemotePhonebook.clear()
            },
        )
        Preference(
            title = stringResource(I18nR.string.hc_connection),
            summary = if (user.isBlank()) stringResource(I18nR.string.au3_commsfreeze_not_configured) else "$user @ $host",
            onClick = { showDialog = true },
        )
        Preference(
            title = stringResource(I18nR.string.hc_sync_now),
            summary = status ?: RemotePhonebook.lastSyncMillis.takeIf { it > 0 }?.let {
                stringResource(I18nR.string.au3_commsfreeze_last_sync, DateFormat.getDateTimeInstance().format(Date(it)))
            } ?: stringResource(I18nR.string.au3_commsfreeze_never_synced),
            onClick = {
                if (busy || !enabled || user.isBlank()) return@Preference
                busy = true
                status = syncingText
                scope.launch {
                    val result = RemotePhonebook.sync(host, user, SecretBox.decrypt(passwordEnc))
                    status = result.fold(
                        onSuccess = { syncedText(it) },
                        onFailure = { failedText(it.message.orEmpty()) },
                    )
                    busy = false
                }
            },
        )
    }

    if (showDialog) {
        var hostInput by remember { mutableStateOf(host) }
        var userInput by remember { mutableStateOf(user) }
        var passInput by remember { mutableStateOf(SecretBox.decrypt(passwordEnc)) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_fritz_box_connection))  } },
            text = {
                Column {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text(stringResource(I18nR.string.hc_address_e_g_fritz_box_or_192_168_178_1)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text(stringResource(I18nR.string.hc_user)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = passInput,
                        onValueChange = { passInput = it },
                        label = { Text(stringResource(I18nR.string.hc_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    settings.setRemotePhonebook(
                        enabled = enabled,
                        host = hostInput,
                        user = userInput,
                        passwordEnc = SecretBox.encrypt(passInput),
                    )
                    showDialog = false
                }) { Text(stringResource(I18nR.string.hc_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(I18nR.string.hc_cancel)) }
            },
        )
    }
}
