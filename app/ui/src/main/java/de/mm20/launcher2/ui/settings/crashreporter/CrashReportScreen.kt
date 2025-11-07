package de.mm20.launcher2.ui.settings.crashreporter

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.crashreporter.CrashReportType
import de.mm20.launcher2.ui.R
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
                Icon(painterResource(R.drawable.share_24px), contentDescription = null)
            }
            if (crashReport?.type == CrashReportType.Crash) {
                IconButton(onClick = { crashReport?.let { viewModel.createIssue(context, it) } }) {
                    Icon(painterResource(R.drawable.bug_report_24px), contentDescription = null)
                }
            }
        }
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .horizontalScroll(
                        rememberScrollState()
                    ),
            ) {
                crashReport?.stacktrace?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (crashReport?.type == CrashReportType.Crash) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                    .padding(12.dp),
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