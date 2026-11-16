package de.mm20.launcher2.ui.media.stremio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.i18n.R as I18nR

@Composable
internal fun StremioProfilesMenuItems(vm: StremioViewModel, onCloseMenu: () -> Unit, onShowSheet: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(I18nR.string.au22_stremio_profiles)) },
        onClick = { onCloseMenu(); onShowSheet() },
    )
}

@Composable
internal fun StremioProfilesDialog(vm: StremioViewModel, onDismiss: () -> Unit) {
    var names by remember { mutableStateOf(vm.localProfileNames()) }
    var active by remember { mutableStateOf(vm.activeLocalProfile()) }
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(I18nR.string.au22_stremio_profiles)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { vm.switchLocalProfile(null); active = null }) {
                    Text(stringResource(I18nR.string.au22_stremio_profile_anonymous) + if (active == null) " ✓" else "")
                }
                names.forEach { name ->
                    TextButton(onClick = { vm.switchLocalProfile(name); active = name }) {
                        Text(name + if (active == name) " ✓" else "")
                    }
                }
                OutlinedTextField(
                    draft,
                    { draft = it },
                    label = { Text(stringResource(I18nR.string.au22_stremio_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (vm.addLocalProfile(draft)) {
                    names = vm.localProfileNames()
                    active = vm.activeLocalProfile()
                    draft = ""
                }
            }) { Text(stringResource(I18nR.string.hc_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_close)) }
        },
    )
}
