package de.mm20.launcher2.ui.common

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.backup.BackupCompatibility
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BottomSheet
import de.mm20.launcher2.ui.component.LargeMessage
import de.mm20.launcher2.ui.component.SmallMessage

@Composable
fun RestoreBackupSheet(
    uri: Uri?,
    onDismissRequest: () -> Unit
) {
    BottomSheet(
        expanded = uri != null,
        onDismissRequest = onDismissRequest,
    ) {

        val viewModel: RestoreBackupSheetVM = viewModel()

        if (uri != null) {
            LaunchedEffect(uri) {
                viewModel.setInputUri(uri)
            }
        }

        val state by viewModel.state
        val compatibility by viewModel.compatibility

        Column(
            modifier = Modifier
                .wrapContentHeight(),
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.preference_restore))
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = onDismissRequest
                    ) {
                        Icon(painterResource(R.drawable.close_24px), stringResource(R.string.close))
                    }
                }
            )
            AnimatedContent(
                state,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (it) {
                    RestoreBackupState.Parsing, RestoreBackupState.Restoring -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    RestoreBackupState.InvalidFile -> {
                        LargeMessage(
                            modifier = Modifier.navigationBarsPadding(),
                            icon = R.drawable.error_48px,
                            text = stringResource(id = R.string.restore_invalid_file)
                        )
                    }

                    RestoreBackupState.Ready -> {
                        val metadata by viewModel.metadata

                        if (metadata != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SmallMessage(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .wrapContentHeight(),
                                    icon = R.drawable.info_24px,
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .navigationBarsPadding(),
                                        icon = R.drawable.error_48px,
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
                                                .navigationBarsPadding(),
                                            icon = R.drawable.warning_24px,
                                            text =
                                                stringResource(
                                                    R.string.restore_different_minor_version,
                                                    stringResource(R.string.app_name)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    RestoreBackupState.Restored -> {
                        LargeMessage(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            icon = R.drawable.check_circle_48px,
                            text = stringResource(
                                id = R.string.restore_complete
                            )
                        )
                    }
                }
            }

            AnimatedVisibility(state == RestoreBackupState.Ready && compatibility != BackupCompatibility.Incompatible) {
                FlexibleBottomAppBar(
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = { viewModel.restore() },
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        Text(stringResource(R.string.preference_restore))
                    }
                }
            }
        }
    }
}
