package de.mm20.launcher2.ui.media.video

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import de.mm20.launcher2.ui.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.media.video.VideoServices
import kotlinx.coroutines.launch
import de.mm20.launcher2.comms.media.video.VideoServicesConfig

/** Opens the player with a web address, a magnet link or a .torrent file. */
internal fun openSource(context: Context, source: String, torrentFile: Boolean = false) {
    context.startActivity(
        Intent(context, PlayerChoice.playerClass(context))
            .putExtra(VideoPlayerActivity.EXTRA_SOURCE, source.trim())
            .putExtra(VideoPlayerActivity.EXTRA_IS_TORRENT, torrentFile)
    )
}

@Composable
internal fun OpenSourceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // a magnet link or address on the clipboard is filled in right away
    var text by remember {
        mutableStateOf(
            clipboard.getText()?.text?.trim()?.takeIf {
                it.startsWith("magnet:", true) || it.startsWith("http://", true) || it.startsWith("https://", true)
            }.orEmpty()
        )
    }
    val torrentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            openSource(context, uri.toString(), torrentFile = true)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_play_from_the_web))  } },
        text = {
            Column {
                Text(
                    stringResource(I18nR.string.hc_web_address_of_a_video_or_stream_hls_das),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(I18nR.string.hc_address_or_magnet_link)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text(stringResource(I18nR.string.hc_paste)) }
                    TextButton(onClick = { torrentPicker.launch(arrayOf("*/*")) }) { Text(stringResource(I18nR.string.hc_choose_torrent_file)) }
                }
                // magnet links and .torrent addresses can also be kept and seeded by Telos Downloads
                if (de.mm20.launcher2.downloads.logic.TorrentSources.containsTorrent(text)) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent().setClassName(context.packageName, de.mm20.launcher2.applock.SettingsDeepLinkContract.ACTIVITY_CLASS_NAME)
                                .putExtra(de.mm20.launcher2.applock.SettingsDeepLinkContract.EXTRA_ROUTE, de.mm20.launcher2.applock.SettingsDeepLinkContract.ROUTE_DOWNLOADS)
                                .putStringArrayListExtra(
                                    de.mm20.launcher2.applock.SettingsDeepLinkContract.EXTRA_DOWNLOAD_URLS,
                                    ArrayList(de.mm20.launcher2.downloads.logic.TorrentSources.extract(text)),
                                )
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        onDismiss()
                    }) { Text(stringResource(I18nR.string.dl_t_download_in_downloads)) }
                }
                Text(
                    stringResource(I18nR.string.hc_torrents_are_downloaded_while_they_play),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    openSource(context, text)
                    onDismiss()
                },
            ) { Text(stringResource(I18nR.string.hc_play)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

@Composable
internal fun VideoServicesDialog(onDismiss: () -> Unit) {
    var config by remember { mutableStateOf<VideoServicesConfig?>(null) }
    LaunchedEffect(Unit) { config = VideoServices.config() }
    val c = config ?: return

    var tmdb by remember { mutableStateOf(c.tmdbKey) }
    var subKey by remember { mutableStateOf(c.subtitleKey) }
    var subUser by remember { mutableStateOf(c.subtitleUser) }
    var subPass by remember { mutableStateOf(c.subtitlePassword) }
    var languages by remember { mutableStateOf(c.languages) }
    var auto by remember { mutableStateOf(c.autoDownload) }
    var wifiOnly by remember { mutableStateOf(c.torrentWifiOnly) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var isolated by remember { mutableStateOf(PlayerChoice.isolated(appContext)) }
    var sources by remember { mutableStateOf(de.mm20.launcher2.comms.media.video.VideoPrefs.subtitleSources(appContext)) }
    var userAgent by remember { mutableStateOf(with(de.mm20.launcher2.comms.media.video.VideoPrefs) { appContext.legacyUserAgent }) }
    var showNetwork by remember { mutableStateOf(false) }
    if (showNetwork) NetworkSourcesDialog { showNetwork = false }
    var showTrakt by remember { mutableStateOf(false) }
    if (showTrakt) TraktDialog { showTrakt = false }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.hc_video_services))  } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(I18nR.string.hc_posters_and_descriptions), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(I18nR.string.hc_posters_work_without_a_key_from_wikipedi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(tmdb, { tmdb = it }, label = { Text(stringResource(I18nR.string.hc_tmdb_api_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text(stringResource(I18nR.string.vn_subtitle_sources), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text(
                    stringResource(I18nR.string.vn_subtitle_sources_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val moveUp = stringResource(I18nR.string.vn_move_up)
                val moveDown = stringResource(I18nR.string.vn_move_down)
                sources.forEachIndexed { i, src ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = src.enabled,
                            onCheckedChange = { on -> sources = sources.toMutableList().also { it[i] = src.copy(enabled = on) } },
                        )
                        Text(
                            stringResource(
                                when (src.id) {
                                    de.mm20.launcher2.comms.media.video.PodnapisiProvider.ID -> I18nR.string.vn_source_podnapisi
                                    de.mm20.launcher2.comms.media.video.OpenSubtitlesComProvider.ID -> I18nR.string.vn_source_os_com
                                    else -> I18nR.string.vn_source_os_legacy
                                }
                            ),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        TextButton(
                            enabled = i > 0,
                            onClick = { sources = sources.toMutableList().also { java.util.Collections.swap(it, i, i - 1) } },
                        ) { Text("▲", modifier = Modifier.semantics { contentDescription = moveUp }) }
                        TextButton(
                            enabled = i < sources.lastIndex,
                            onClick = { sources = sources.toMutableList().also { java.util.Collections.swap(it, i, i + 1) } },
                        ) { Text("▼", modifier = Modifier.semantics { contentDescription = moveDown }) }
                    }
                }
                OutlinedTextField(
                    userAgent, { userAgent = it }, label = { Text(stringResource(I18nR.string.vn_os_user_agent)) }, singleLine = true,
                    supportingText = { Text(stringResource(I18nR.string.vn_os_user_agent_help)) }, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(I18nR.string.hc_subtitles), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Text(
                    stringResource(I18nR.string.hc_opensubtitles_api_key_opensubtitles_com),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(subKey, { subKey = it }, label = { Text(stringResource(I18nR.string.hc_opensubtitles_api_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subUser, { subUser = it }, label = { Text(stringResource(I18nR.string.hc_user_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    subPass, { subPass = it }, label = { Text(stringResource(I18nR.string.hc_password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    languages, { languages = it }, label = { Text(stringResource(I18nR.string.hc_languages_e_g_el_en)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(I18nR.string.hc_download_subtitles_automatically), modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }

                TextButton(onClick = { showTrakt = true }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(I18nR.string.au7_trakt_open_settings))
                }

                Text(stringResource(I18nR.string.vn_network_folders), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                TextButton(onClick = { showNetwork = true }) { Text(stringResource(I18nR.string.vn_network_scan_title)) }

                Text(stringResource(I18nR.string.dl_tab_torrents), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                de.mm20.launcher2.ui.downloads.TransferNetworkPreferences("torrent")
                Text(stringResource(I18nR.string.blocklists_torrent_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                de.mm20.launcher2.ui.component.BlockListsSection(de.mm20.launcher2.comms.blocklist.BlockListKind.TORRENT_IP)

                Text(stringResource(I18nR.string.hc_player), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(I18nR.string.hc_play_in_a_separate_process), modifier = Modifier.weight(1f))
                    Switch(checked = isolated, onCheckedChange = { isolated = it })
                }
                Text(
                    stringResource(I18nR.string.hc_experimental_a_crash_of_the_player_then),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                PlayerChoice.setIsolated(appContext, isolated)
                de.mm20.launcher2.comms.media.video.VideoPrefs.setSubtitleSources(appContext, sources)
                with(de.mm20.launcher2.comms.media.video.VideoPrefs) { appContext.legacyUserAgent = userAgent }
                VideoServices.save(VideoServicesConfig(tmdb, subKey, subUser, subPass, languages, auto, wifiOnly))
                onDismiss()
            }) { Text(stringResource(I18nR.string.hc_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}

internal fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}
