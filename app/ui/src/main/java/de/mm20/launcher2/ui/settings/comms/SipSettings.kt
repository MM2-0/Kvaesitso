package de.mm20.launcher2.ui.settings.comms

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.mm20.launcher2.comms.remote.SecretBox
import de.mm20.launcher2.comms.sip.SipEngine
import de.mm20.launcher2.comms.sip.SipRegistration
import de.mm20.launcher2.preferences.comms.CommsSettings
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import org.koin.compose.koinInject

@Composable
fun SipSettings() {
    val settings: CommsSettings = koinInject()
    val snap by settings.snapshot.collectAsStateWithLifecycle(null)
    val registration by SipEngine.registration.collectAsStateWithLifecycle()
    val error by SipEngine.lastError.collectAsStateWithLifecycle()
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val current = snap ?: return
    val context = androidx.compose.ui.platform.LocalContext.current

    // SIP calls need the microphone, and incoming calls the notification permission (Android 13+)
    val permissions = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[android.Manifest.permission.RECORD_AUDIO] != false) settings.setSipEnabled(true)
        else android.widget.Toast.makeText(context, context.getString(I18nR.string.hc_sip_calls_need_the_microphone_permission), android.widget.Toast.LENGTH_LONG).show()
    }
    fun enable() {
        val needed = buildList {
            add(android.Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) settings.setSipEnabled(true) else permissions.launch(needed.toTypedArray())
    }

    val status = when {
        !SipEngine.available -> stringResource(I18nR.string.au3_commsfreeze_sip_unavailable)
        !current.sipEnabled -> stringResource(I18nR.string.au3_commsfreeze_sip_off)
        registration == SipRegistration.Registered -> stringResource(I18nR.string.au3_commsfreeze_sip_registered)
        registration == SipRegistration.Registering -> stringResource(I18nR.string.au3_commsfreeze_sip_connecting)
        registration == SipRegistration.Failed ->
            if (error.isNotBlank()) stringResource(I18nR.string.au3_commsfreeze_sip_failed_error, error)
            else stringResource(I18nR.string.au3_commsfreeze_sip_failed)
        else -> stringResource(I18nR.string.au3_commsfreeze_sip_starting)
    }

    PreferenceCategory(title = stringResource(I18nR.string.hc_sip_voip_account)) {
        SwitchPreference(
            title = stringResource(I18nR.string.hc_use_sip_account),
            summary = stringResource(I18nR.string.hc_sip_keep_registered_summary, status),
            value = current.sipEnabled,
            enabled = SipEngine.available && current.sipUser.isNotBlank(),
            onValueChanged = { if (it) enable() else settings.setSipEnabled(false) },
        )
        Preference(
            title = stringResource(I18nR.string.hc_account),
            summary = if (current.sipUser.isBlank()) stringResource(I18nR.string.au3_commsfreeze_not_configured)
            else "${current.sipUser} @ ${current.sipDomain}",
            onClick = { showDialog = true },
        )
        ListPreference(
            title = stringResource(I18nR.string.hc_outgoing_calls),
            items = listOf(
                stringResource(I18nR.string.au3_commsfreeze_sip_out_off) to "off",
                stringResource(I18nR.string.au3_commsfreeze_sip_out_choose) to "choose",
                stringResource(I18nR.string.au3_commsfreeze_sip_out_default) to "default",
            ),
            value = current.sipOutgoing,
            onValueChanged = { settings.setSipOutgoing(it) },
        )
    }

    if (showDialog) {
        var user by remember { mutableStateOf(current.sipUser) }
        var domain by remember { mutableStateOf(current.sipDomain) }
        var name by remember { mutableStateOf(current.sipDisplayName) }
        var pass by remember { mutableStateOf(SecretBox.decrypt(current.sipPasswordEnc)) }
        // new accounts verify the certificate; accounts saved before keep their stored (off) value
        var verify by remember { mutableStateOf(if (current.sipUser.isBlank()) true else current.sipVerifyServer) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_sip_account))  } },
            text = {
                Column {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text(stringResource(I18nR.string.hc_server_fritz_box_fritz_box)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text(stringResource(I18nR.string.hc_user_name_fritz_box_the_ip_phone_user)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text(stringResource(I18nR.string.hc_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(I18nR.string.hc_display_name_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(I18nR.string.au2_callsec_sip_verify))
                            Text(
                                stringResource(I18nR.string.au2_callsec_sip_verify_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = verify, onCheckedChange = { verify = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = domain.isNotBlank() && domain.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == ':' || it == '_' }, onClick = {
                    settings.setSipAccount(user, domain, name, SecretBox.encrypt(pass), verify)
                    showDialog = false
                }) { Text(stringResource(I18nR.string.hc_save)) }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }
}
