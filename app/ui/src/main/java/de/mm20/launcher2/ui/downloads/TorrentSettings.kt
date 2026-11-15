package de.mm20.launcher2.ui.downloads

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.blocklist.BlockListKind
import de.mm20.launcher2.comms.media.video.torrent.TorrentEncryption
import de.mm20.launcher2.comms.media.video.torrent.TorrentSession
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.BlockListsSection
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.ListPreferenceItem
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference

private val SPEED_CHOICES = listOf(0, 128, 256, 512, 1024, 2048, 5120, 10240)

@Composable
private fun speedLabel(kbps: Int) = when {
    kbps == 0 -> stringResource(I18nR.string.dl_limit_none)
    kbps >= 1024 -> "${kbps / 1024} MB/s"
    else -> "$kbps KB/s"
}

/** The "Torrents" part of the Telos Downloads settings: session, limits, seeding defaults and the peer block lists */
fun LazyListScope.torrentSettingsItems() {
    item { TorrentNetworkCategory() }
    item { TorrentLimitsCategory() }
    item { TorrentSeedingCategory() }
    item {
        PreferenceCategory(title = stringResource(I18nR.string.dl_t_set_blocklists)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceBright,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BlockListsSection(BlockListKind.TORRENT_IP, Modifier.padding(16.dp))
            }
        }
    }
    item {
        Text(
            stringResource(I18nR.string.dl_t_set_shared_note) + "\n\n" + stringResource(I18nR.string.dl_t_set_staging_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun TorrentNetworkCategory() {
    val context = LocalContext.current
    val c by TorrentSession.config.collectAsState()
    var editPort by remember { mutableStateOf(false) }
    PreferenceCategory(title = stringResource(I18nR.string.dl_t_settings_title)) {
        TransferNetworkPreferences("torrent")
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_dht), summary = stringResource(I18nR.string.dl_t_set_dht_s),
            value = c.dht, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(dht = v) } },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_pex), summary = stringResource(I18nR.string.dl_t_set_pex_s),
            value = c.pex, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(pex = v) } },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_lsd), summary = stringResource(I18nR.string.dl_t_set_lsd_s),
            value = c.lsd, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(lsd = v) } },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_utp), summary = stringResource(I18nR.string.dl_t_set_utp_s),
            value = c.utp, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(utp = v) } },
        )
        ListPreference(
            title = stringResource(I18nR.string.dl_t_set_enc),
            items = listOf(
                ListPreferenceItem(stringResource(I18nR.string.dl_t_enc_prefer), TorrentEncryption.Prefer),
                ListPreferenceItem(stringResource(I18nR.string.dl_t_enc_require), TorrentEncryption.Require),
                ListPreferenceItem(stringResource(I18nR.string.dl_t_enc_disable), TorrentEncryption.Disable),
            ),
            value = c.encryption,
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(encryption = v) } },
        )
        Preference(
            title = stringResource(I18nR.string.dl_t_set_port),
            summary = if (c.listenPort == 0) stringResource(I18nR.string.dl_t_port_random) else stringResource(I18nR.string.dl_t_port_fixed, c.listenPort),
            onClick = { editPort = true },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_upnp), summary = stringResource(I18nR.string.dl_t_set_upnp_s),
            value = c.upnp, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(upnp = v) } },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_natpmp),
            value = c.natPmp, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(natPmp = v) } },
        )
    }
    if (editPort) {
        var text by remember { mutableStateOf(if (c.listenPort == 0) "" else c.listenPort.toString()) }
        val value = text.toIntOrNull()
        val valid = text.isEmpty() || (value != null && value in 1024..65535)
        AlertDialog(
            onDismissRequest = { editPort = false },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.dl_t_set_port))  } },
            text = {
                OutlinedTextField(
                    text, { text = it.filter(Char::isDigit).take(5) }, Modifier.fillMaxWidth(), singleLine = true, isError = !valid,
                    label = { Text(stringResource(I18nR.string.dl_t_port_hint)) },
                    supportingText = { Text(stringResource(I18nR.string.dl_t_port_info)) },
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { TorrentSession.updateConfig(context) { it.copy(listenPort = value ?: 0) }; editPort = false }) {
                    Text(stringResource(I18nR.string.dl_save))
                }
            },
            dismissButton = { TextButton(onClick = { editPort = false }) { Text(stringResource(I18nR.string.dl_cancel)) } },
        )
    }
}

@Composable
private fun TorrentLimitsCategory() {
    val context = LocalContext.current
    val c by TorrentSession.config.collectAsState()
    PreferenceCategory(title = stringResource(I18nR.string.dl_t_set_limits)) {
        ListPreference(
            title = stringResource(I18nR.string.dl_t_set_down_limit),
            items = SPEED_CHOICES.map { ListPreferenceItem(speedLabel(it), it) },
            value = c.downloadLimitKBps, summary = speedLabel(c.downloadLimitKBps),
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(downloadLimitKBps = v) } },
        )
        ListPreference(
            title = stringResource(I18nR.string.dl_t_set_up_limit),
            items = SPEED_CHOICES.map { ListPreferenceItem(speedLabel(it), it) },
            value = c.uploadLimitKBps, summary = speedLabel(c.uploadLimitKBps),
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(uploadLimitKBps = v) } },
        )
        SliderPreference(
            title = stringResource(I18nR.string.dl_t_set_max_active), value = c.maxActiveDownloads, min = 1, max = 10,
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(maxActiveDownloads = v) } },
            label = { Text(it.toString()) },
        )
        SliderPreference(
            title = stringResource(I18nR.string.dl_t_set_max_seeds), value = c.maxActiveSeeds, min = 0, max = 10,
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(maxActiveSeeds = v) } },
            label = { Text(it.toString()) },
        )
        SliderPreference(
            title = stringResource(I18nR.string.dl_t_set_max_conn), value = c.maxConnections / 50, min = 1, max = 20,
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(maxConnections = v * 50) } },
            label = { Text((it * 50).toString()) },
        )
    }
}

@Composable
private fun TorrentSeedingCategory() {
    val context = LocalContext.current
    val c by TorrentSession.config.collectAsState()
    PreferenceCategory(title = stringResource(I18nR.string.dl_t_set_seed_defaults)) {
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_stop_done), summary = stringResource(I18nR.string.dl_t_set_stop_done_s),
            value = c.stopAtDone, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(stopAtDone = v) } },
        )
        ListPreference(
            title = stringResource(I18nR.string.dl_t_set_ratio),
            items = RATIO_CHOICES.map { ListPreferenceItem(ratioLabel(it), it) },
            value = c.seedRatioX100, summary = ratioLabel(c.seedRatioX100), enabled = !c.stopAtDone,
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(seedRatioX100 = v) } },
        )
        ListPreference(
            title = stringResource(I18nR.string.dl_t_set_minutes),
            items = MINUTE_CHOICES.map { ListPreferenceItem(minutesLabel(it), it) },
            value = c.seedMinutes, summary = minutesLabel(c.seedMinutes), enabled = !c.stopAtDone,
            onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(seedMinutes = v) } },
        )
        SwitchPreference(
            title = stringResource(I18nR.string.dl_t_set_sequential_default),
            value = c.sequentialByDefault, onValueChanged = { v -> TorrentSession.updateConfig(context) { it.copy(sequentialByDefault = v) } },
        )
    }
}
