package de.mm20.launcher2.ui.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.common.RestoreBackupSheet
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data object BackupSettingsRoute: NavKey

@Composable
fun BackupSettingsScreen() {
    val viewModel: BackupSettingsScreenVM = viewModel()

    val restoreUri by viewModel.restoreUri

    val showBackupSheet by viewModel.showBackupSheet

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = {
            viewModel.setRestoreUri(it)
        }
    )

    PreferenceScreen(stringResource(R.string.preference_screen_backup)) {
        item {
            PreferenceCategory {
                Preference(
                    title = stringResource(id = R.string.preference_backup),
                    summary = stringResource(id = R.string.preference_backup_summary),
                    onClick = {
                        viewModel.setShowBackupSheet(true)
                    })
                Preference(
                    title = stringResource(id = R.string.preference_restore),
                    summary = stringResource(id = R.string.preference_restore_summary),
                    onClick = {
                        restoreLauncher.launch(arrayOf("*/*"))
                    })
            }
        }
    }

    val uri = restoreUri

    if (uri != null) {
        RestoreBackupSheet(uri = uri, onDismissRequest = { viewModel.setRestoreUri(null) })
    }

    if(showBackupSheet) {
        CreateBackupSheet(onDismissRequest = {
            viewModel.setShowBackupSheet(false)
        })
    }
}