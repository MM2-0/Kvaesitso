package de.mm20.launcher2.ui.media.stremio

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.comms.stremio.StremioPlaybackSettings
import de.mm20.launcher2.comms.stremio.StremioStreamingServer
import de.mm20.launcher2.i18n.R as I18nR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StremioServerStatusMenuItem(menuOpen: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf<Boolean?>(null) }
    suspend fun probe() {
        connected = withContext(Dispatchers.IO) {
            runCatching {
                val addr = StremioPlaybackSettings.load(context).streamingServer
                StremioStreamingServer.settings(addr)
                true
            }.getOrDefault(false)
        }
    }
    LaunchedEffect(menuOpen) { if (menuOpen) probe() }
    DropdownMenuItem(
        text = {
            Text(
                when (connected) {
                    true -> stringResource(I18nR.string.au22_stremio_server_connected)
                    false -> stringResource(I18nR.string.stremio_shared_unavailable)
                    null -> stringResource(I18nR.string.au22_stremio_test_server)
                },
            )
        },
        onClick = { scope.launch { probe() } },
    )
}
