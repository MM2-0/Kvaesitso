package de.mm20.launcher2.ui.settings.log

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.debug.DebugInformationDumper
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

@Serializable
data object LogRoute: NavKey

@Composable
fun LogScreen() {
    var lines by remember { mutableStateOf(emptyList<LogcatLine>()) }
    val listState = rememberLazyListState()

    LaunchedEffect(null) {
        val process = Runtime.getRuntime().exec("/system/bin/logcat -v time")

        val pattern = Pattern.compile(
            "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  /* timestamp [1] */
                    "(\\w)/(.+?)\\(\\s*(\\d+)\\): (.*)$"
        )  /* level, tag, pid, msg [2-5] */

        launch(Dispatchers.IO) {
            val inputStream = process.inputStream.bufferedReader()
            while (isActive) {
                val line = try {
                    val line = inputStream.readLine() ?: continue
                    val matcher = pattern.matcher(line)
                    if (matcher.matches()) {
                        FormattedLogcatLine(
                            message = matcher.group(5) ?: "",
                            tag = matcher.group(3) ?: "",
                            level = matcher.group(2) ?: "",
                            timestamp = matcher.group(1) ?: "<no date>",
                        )
                    } else {
                        RawLogcatLine(line)
                    }
                } catch (e: IOException) {
                    break
                }
                lines = (lines + line)
            }
        }
        try {
            awaitCancellation()
        } finally {
            process.destroy()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    PreferenceScreen(
        title = stringResource(id = R.string.preference_logs),
        topBarActions = {
            IconButton(onClick = {
                scope.launch {
                    val path = DebugInformationDumper().dump(context)
                    context.tryStartActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_STREAM, FileProvider.getUriForFile(
                                        context,
                                        context.applicationContext.packageName + ".fileprovider",
                                        File(path)
                                    )
                                )
                            }, null
                        )
                    )
                }
            }) {
                Icon(painterResource(R.drawable.share_24px), contentDescription = null)
            }
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        floatingActionButton = {
            AnimatedVisibility(
                listState.canScrollForward,
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                SmallFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = {
                    scope.launch {
                        listState.animateScrollToItem(lines.lastIndex)
                    }
                }) {
                    Icon(
                        painterResource(R.drawable.arrow_downward_24px),
                        null,
                    )
                }
            }
        },
        lazyColumnState = listState,
    ) {
        itemsIndexed(lines) { i, it ->
            val xs = MaterialTheme.shapes.extraSmall
            val md = MaterialTheme.shapes.medium
            val shape = xs.copy(
                topStart = if (i == 0) md.topStart else xs.topStart,
                topEnd = if (i == 0) md.topEnd else xs.topEnd,
                bottomStart = if (i == lines.lastIndex) md.bottomStart else xs.bottomStart,
                bottomEnd = if (i == lines.lastIndex) md.bottomEnd else xs.bottomEnd,
            )

            if (it is RawLogcatLine) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, shape)
                        .padding(16.dp),
                    text = it.line,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (it is FormattedLogcatLine) {
                val contentColor = when (it.level) {
                    "E" -> MaterialTheme.colorScheme.error
                    "W" -> MaterialTheme.colorScheme.primary
                    "D" -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, shape)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(
                                when (it.level) {
                                    "E" -> R.drawable.error_20px
                                    "W" -> R.drawable.warning_20px
                                    "D" -> R.drawable.bug_report_20px
                                    else -> R.drawable.info_20px
                                },
                            ),
                            null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = it.timestamp + " • " + it.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor
                        )
                    }
                    Text(
                        modifier = Modifier.padding(top = 8.dp),
                        text = it.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

sealed interface LogcatLine

data class FormattedLogcatLine(
    val level: String,
    val timestamp: String,
    val tag: String,
    val message: String,
) : LogcatLine

data class RawLogcatLine(val line: String) : LogcatLine