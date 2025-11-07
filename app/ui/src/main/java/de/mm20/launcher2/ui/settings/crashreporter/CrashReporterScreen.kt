package de.mm20.launcher2.ui.settings.crashreporter

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.mm20.launcher2.crashreporter.CrashReportType
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.locals.LocalNavController
import java.net.URLEncoder

@Composable
fun CrashReporterScreen() {
    val viewModel: CrashReporterScreenVM = viewModel()
    val navController = LocalNavController.current
    val reports by viewModel.reports
    val showExceptions by viewModel.showExceptions
    val showCrashes by viewModel.showCrashes
    PreferenceScreen(
        title = stringResource(R.string.preference_crash_reporter),
        helpUrl = "https://kvaesitso.mm20.de/docs/user-guide/troubleshooting/crashreporter"
    ) {
        reports?.let {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    IconToggleButton(checked = showExceptions, onCheckedChange = { value ->
                        viewModel.setShowExceptions(value)
                    }) {
                        Icon(
                            painterResource(if (showExceptions) R.drawable.warning_24px_filled else R.drawable.warning_24px),
                            contentDescription = null,
                            modifier = Modifier.alpha(if (showExceptions) 1f else 0.5f)
                        )
                    }
                    IconToggleButton(checked = showCrashes, onCheckedChange = { value ->
                        viewModel.setShowCrashes(value)
                    }) {
                        Icon(
                            painterResource(if (showCrashes) R.drawable.error_24px_filled else R.drawable.error_24px),
                            contentDescription = null,
                            modifier = Modifier.alpha(if (showCrashes) 1f else 0.5f)
                        )
                    }
                }
            }
            items(it) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController?.navigate("settings/debug/crashreporter/report?fileName=${it.filePath}")
                            }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = DateUtils.formatDateTime(
                                LocalContext.current,
                                it.time.time,
                                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides if (it.type == CrashReportType.Exception) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            ) {
                                Icon(
                                    modifier = Modifier.padding(end = 8.dp),
                                    painter = painterResource(
                                        if (it.type == CrashReportType.Exception) R.drawable.warning_24px else R.drawable.error_24px
                                    ),
                                    contentDescription = null
                                )
                                Text(
                                    text = if (it.type == CrashReportType.Exception) "Exception" else "Crash",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        Text(
                            text = it.summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } ?: item {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}