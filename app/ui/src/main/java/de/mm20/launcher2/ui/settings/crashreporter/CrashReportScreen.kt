package de.mm20.launcher2.ui.settings.crashreporter

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.crashreporter.CrashReportType
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen

@Composable
fun CrashReportScreen(fileName: String) {
    val viewModel: CrashReportScreenVM = viewModel()
    val context = LocalContext.current
    val crashReport by remember(fileName) { viewModel.getCrashReport(fileName) }.collectAsState(null)
    PreferenceScreen(
        title = when (crashReport?.type) {
            CrashReportType.Exception -> "Exception"
            CrashReportType.Crash -> "Crash"
            null -> ""
        },
        topBarActions = {
            IconButton(onClick = { crashReport?.let { viewModel.shareCrashReport(context, it) } }) {
                Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
            }
            if (crashReport?.type == CrashReportType.Crash) {
                IconButton(onClick = { crashReport?.let { viewModel.createIssue(context, it) } }) {
                    Icon(imageVector = Icons.Rounded.BugReport, contentDescription = null)
                }
            }
        }
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                color = if (crashReport?.type == CrashReportType.Crash) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                ) {
                    crashReport?.stacktrace?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(text = "Device Information", style = MaterialTheme.typography.titleMedium)
                val deviceInformation = remember { viewModel.getDeviceInformation(context) }
                Text(
                    text = deviceInformation,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
        }
    }
}