package de.mm20.launcher2.ui.comms.radio

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import de.mm20.launcher2.comms.model.RadioStation
import de.mm20.launcher2.comms.repository.RadioRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideCompositionLocals
import de.mm20.launcher2.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.UUID

/**
 * Receives a station from outside (telos-radio://add deep link, shared text, or a link to a stream or playlist)
 * and asks before adding it to the collection. Nothing is played and no network request is made here.
 */
class RadioAddActivity : BaseActivity() {
    private val repository: RadioRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val candidate = RadioShare.parse(intent)
        if (candidate == null) {
            Toast.makeText(this, I18nR.string.au10_radio_invalid_link, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            ProvideCompositionLocals {
                LauncherTheme {
                    ConfirmDialog(candidate)
                }
            }
        }
    }

    @Composable
    private fun ConfirmDialog(candidate: RadioCandidate) {
        var name by remember { mutableStateOf(candidate.name) }
        var url by remember { mutableStateOf(candidate.url) }
        val cleanUrl = RadioShare.cleanUrl(url)
        AlertDialog(
            onDismissRequest = { finish() },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au10_radio_add_title, RadioShare.cleanName(name)))  } },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(RadioShare.MAX_NAME) },
                        label = { Text(stringResource(I18nR.string.au10_radio_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it.take(RadioShare.MAX_URL) },
                        label = { Text(stringResource(I18nR.string.au10_radio_stream_url)) },
                        isError = cleanUrl == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = cleanUrl != null && RadioShare.cleanName(name).isNotBlank(),
                    onClick = { save(RadioShare.cleanName(name), cleanUrl ?: return@TextButton, candidate.logo) },
                ) { Text(stringResource(I18nR.string.hc_add)) }
            },
            dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
    }

    private fun save(name: String, url: String, logo: String) {
        val app = applicationContext
        lifecycleScope.launch {
            val exists = runCatching { repository.observeFavorites().first().any { it.streamUrl == url } }.getOrDefault(false)
            val msg = if (exists) {
                getString(I18nR.string.au10_radio_already_added)
            } else {
                runCatching {
                    repository.saveStation(
                        RadioStation(
                            id = "local-" + UUID.randomUUID(),
                            name = name,
                            streamUrl = url,
                            faviconUrl = logo,
                            nameManuallySet = true,
                        )
                    )
                }.fold({ getString(I18nR.string.au_radio_station_added, name) }, { getString(I18nR.string.au_radio_add_failed) })
            }
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
