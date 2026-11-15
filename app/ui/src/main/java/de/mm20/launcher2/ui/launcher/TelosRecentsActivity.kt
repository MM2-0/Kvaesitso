package de.mm20.launcher2.ui.launcher

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.globalactions.RecentsStore
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.theme.LauncherTheme

class TelosRecentsActivity : BaseActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                ProvideSettings {
                    OverlayHost {
                        val tasks by RecentsStore.tasks.collectAsState()
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { TelosCyberHeading {  Text(stringResource(I18nR.string.telos_recents_title))  } },
                                    navigationIcon = {
                                        IconButton(onClick = { finish() }) {
                                            Icon(
                                                painter = painterResource(BaseR.drawable.arrow_back_24px),
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                    actions = {
                                        TextButton(onClick = { RecentsStore.clear() }) {
                                            Text(stringResource(I18nR.string.telos_recents_clear))
                                        }
                                    },
                                )
                            }
                        ) { padding ->
                            LazyColumn(modifier = Modifier.padding(padding)) {
                                items(tasks, key = { it.packageName }) { task ->
                                    val label = task.label?.toString()?.ifBlank { null }
                                        ?: task.packageName
                                    ListItem(
                                        headlineContent = { Text(label) },
                                        supportingContent = { Text(task.packageName) },
                                        modifier = Modifier.clickable {
                                            val launch = packageManager.getLaunchIntentForPackage(task.packageName)
                                            if (launch != null) {
                                                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                startActivity(launch)
                                                finish()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
