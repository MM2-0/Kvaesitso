package de.mm20.launcher2.ui.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.backup.BackupComponent
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheetDialog
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.SmallMessage
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun CreateBackupSheet(
    onDismissRequest: () -> Unit
) {

    val viewModel: CreateBackupSheetVM = viewModel()

    LaunchedEffect(null) {
        viewModel.reset()
    }

    val components by viewModel.selectedComponents.observeAsState(emptySet())
    val state by viewModel.state.observeAsState(CreateBackupState.Ready)


    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vendor.de.mm20.launcher2.backup"),
        onResult = {
            if (it != null) viewModel.createBackup(it)
        }
    )

    BottomSheetDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(id = R.string.preference_backup),
            )
        },
        confirmButton = {
            if (state == CreateBackupState.Ready) {
                Button(
                    enabled = components.isNotEmpty(),
                    onClick = {
                        val fileName = "${
                            ZonedDateTime.now().format(
                                DateTimeFormatter.ISO_INSTANT
                            )
                        }.kvaesitso"
                        backupLauncher.launch(fileName)
                    }) {
                    Text(stringResource(R.string.preference_backup))
                }
            } else if (state == CreateBackupState.BackedUp) {
                OutlinedButton(
                    onClick = onDismissRequest
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        },
        dismissButton = if (state == CreateBackupState.Ready) {
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
        ) {
            when (state) {
                CreateBackupState.Ready -> {
                    Column {
                        Text(
                            stringResource(R.string.backup_select_components),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        BackupableComponent(
                            title = stringResource(R.string.backup_component_settings),
                            icon = Icons.Rounded.Settings,
                            checked = components.contains(BackupComponent.Settings),
                            onCheckedChange = {
                                viewModel.toggleComponent(BackupComponent.Settings)
                            }
                        )
                        BackupableComponent(
                            title = stringResource(R.string.backup_component_favorites),
                            icon = Icons.Rounded.Star,
                            checked = components.contains(BackupComponent.Favorites),
                            onCheckedChange = {
                                viewModel.toggleComponent(BackupComponent.Favorites)
                            }
                        )
                        BackupableComponent(
                            title = stringResource(R.string.backup_component_widgets),
                            icon = Icons.Rounded.Widgets,
                            checked = components.contains(BackupComponent.Widgets),
                            onCheckedChange = {
                                viewModel.toggleComponent(BackupComponent.Widgets)
                            }
                        )
                        BackupableComponent(
                            title = stringResource(R.string.backup_component_websearches),
                            icon = Icons.Rounded.TravelExplore,
                            checked = components.contains(BackupComponent.Websearches),
                            onCheckedChange = {
                                viewModel.toggleComponent(BackupComponent.Websearches)
                            }
                        )
                        SmallMessage(
                            modifier = Modifier.padding(top = 8.dp),
                            icon = Icons.Rounded.Warning,
                            text = stringResource(R.string.backup_not_included)
                        )
                    }
                }
                CreateBackupState.BackingUp -> {
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
                CreateBackupState.BackedUp -> {
                    LargeMessage(
                        modifier = Modifier.aspectRatio(1f),
                        icon = Icons.Rounded.CheckCircleOutline,
                        text = stringResource(
                            id = R.string.backup_complete
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupableComponent(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable {
                onCheckedChange(!checked)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

