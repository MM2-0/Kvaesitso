package de.mm20.launcher2.ui.media.stremio

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.comms.stremio.StreamItem
import de.mm20.launcher2.comms.stremio.StremioStreamTargets
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.ui.downloads.openInTelosDownloads

@Composable
internal fun StremioStreamExtraActions(stream: StreamItem, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewUri = StremioStreamTargets.externalViewUri(stream, title)
    val download = StremioStreamTargets.downloadUrl(stream, title)
    if (viewUri == null && download == null) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (viewUri != null) {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_VIEW, Uri.parse(viewUri)),
                            context.getString(I18nR.string.hc_open_with),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure {
                    Toast.makeText(context, I18nR.string.au22_stremio_unsupported, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(I18nR.string.hc_open_with)) }
        }
        if (download != null) {
            TextButton(onClick = { openInTelosDownloads(context, download) }) {
                Text(stringResource(I18nR.string.dl_add_title))
            }
        }
    }
}
