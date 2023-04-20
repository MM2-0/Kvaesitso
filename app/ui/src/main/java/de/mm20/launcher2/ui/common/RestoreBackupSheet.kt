package de.mm20.launcher2.ui.common

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.backup.BackupCompatibility
import de.mm20.launcher2.backup.BackupComponent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.SmallMessage

@Composable
fun RestoreBackupSheet(
    uri: Uri,
    onDismissRequest: () -> Unit
) {
    val viewModel: RestoreBackupSheetVM = viewModel()

    LaunchedEffect(uri) {
        viewModel.setInputUri(uri)
    }

    val state by viewModel.state
    val selectedComponents by viewModel.selectedComponents
    val compatibility by viewModel.compatibility

    BottomSheetDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(id = R.string.preference_restore),
            )
        },
        confirmButton = {

            if (state == RestoreBackupState.Ready && compatibility != BackupCompatibility.Incompatible) {
                Button(
                    enabled = selectedComponents.isNotEmpty(),
                    onClick = { viewModel.restore() }) {
                    Text(stringResource(R.string.preference_restore))
                }
            } else if (state == RestoreBackupState.InvalidFile || state == RestoreBackupState.Restored || state == RestoreBackupState.Ready) {
                OutlinedButton(
                    onClick = onDismissRequest
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        },
        dismissButton = if (state == RestoreBackupState.Ready && compatibility != BackupCompatibility.Incompatible) {
            {
                OutlinedButton(
                    onClick = onDismissRequest
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        } else null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .verticalScroll(rememberScrollState())
                .padding(it)
        ) {
            when (state) {
                RestoreBackupState.Parsing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                RestoreBackupState.InvalidFile -> {
                    LargeMessage(
                        modifier = Modifier.aspectRatio(1f),
                        icon = Icons.Rounded.ErrorOutline,
                        text = stringResource(id = R.string.restore_invalid_file)
                    )
                }
                RestoreBackupState.Ready -> {
                    val metadata by viewModel.metadata

                    if (metadata != null) {
                        Column {
                            SmallMessage(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(bottom = 16.dp),
                                icon = Icons.Rounded.Info,
                                text = stringResource(
                                    R.string.restore_meta,
                                    DateUtils.formatDateTime(
                                        LocalContext.current,
                                        metadata!!.timestamp,
                                        DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
                                    ),
                                    metadata!!.deviceName,
                                    stringResource(R.string.app_name) + " " + metadata!!.appVersionName,
                                )
                            )
                            if (compatibility == BackupCompatibility.Incompatible) {
                                LargeMessage(
                                    modifier = Modifier.aspectRatio(1f),
                                    icon = Icons.Rounded.ErrorOutline,
                                    text = stringResource(
                                        id = R.string.restore_incompatible_file,
                                        stringResource(R.string.app_name)
                                    )
                                )
                            } else {
                                if (compatibility == BackupCompatibility.PartiallyCompatible) {
                                    SmallMessage(
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        icon = Icons.Rounded.Warning,
                                        text =
                                        stringResource(
                                            R.string.restore_different_minor_version,
                                            stringResource(R.string.app_name)
                                        )
                                    )
                                }
                                Text(
                                    stringResource(R.string.restore_select_components),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                                val components by viewModel.availableComponents
                                for (component in components) {
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                viewModel.toggleComponent(
                                                    component
                                                )
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (component) {
                                                BackupComponent.Favorites -> Icons.Rounded.Star
                                                BackupComponent.Settings -> Icons.Rounded.Settings
                                                BackupComponent.SearchActions -> Icons.Rounded.ArrowOutward
                                                BackupComponent.Widgets -> Icons.Rounded.Widgets
                                                BackupComponent.Customizations -> Icons.Rounded.Edit
                                            },
                                            contentDescription = null
                                        )
                                        Text(
                                            text = stringResource(
                                                when (component) {
                                                    BackupComponent.Favorites -> R.string.backup_component_favorites
                                                    BackupComponent.Settings -> R.string.backup_component_settings
                                                    BackupComponent.SearchActions -> R.string.backup_component_searchactions
                                                    BackupComponent.Widgets -> R.string.backup_component_widgets
                                                    BackupComponent.Customizations -> R.string.backup_component_customizations
                                                }
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 16.dp)
                                        )
                                        Checkbox(
                                            checked = selectedComponents.contains(
                                                component
                                            ),
                                            onCheckedChange = {
                                                viewModel.toggleComponent(component)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                RestoreBackupState.Restoring -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                RestoreBackupState.Restored -> {
                    LargeMessage(
                        modifier = Modifier.aspectRatio(1f),
                        icon = Icons.Rounded.CheckCircleOutline,
                        text = stringResource(
                            id = R.string.restore_complete
                        )
                    )
                }
            }
        }
    }
}
