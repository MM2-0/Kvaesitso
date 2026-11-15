package de.mm20.launcher2.ui.media.video

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.mm20.launcher2.comms.media.video.EpisodeParser
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One file that would move, and the media store folder it would move to */
private data class OrganizeMove(val item: VideoItem, val target: String, val series: Boolean)

private val illegalChars = Regex("[\\\\/:*?\"<>|]")

private fun cleanName(s: String) = s.replace(illegalChars, " ").replace(Regex("\\s+"), " ").trim(' ', '.')

private fun titleCase(s: String) = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/**
 * Works out which videos would move. Only local videos that lie in Movies/ or Download/ of the shared storage
 * are considered (camera and other folders stay untouched), and only films with a year in the name and
 * episodes with season and episode numbers (the same recognition as the library tabs).
 */
private fun planMoves(context: Context, items: List<VideoItem>): List<OrganizeMove> {
    val local = items.filter { it.id > 0 && it.uri.scheme == "content" }
    if (local.isEmpty()) return emptyList()
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val paths = HashMap<Long, String>()
    runCatching {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.RELATIVE_PATH),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) paths[c.getLong(0)] = c.getString(1).orEmpty()
        }
    }
    val moves = mutableListOf<OrganizeMove>()
    for (item in local) {
        val current = paths[item.id] ?: continue
        if (!(current.startsWith("Movies/") || current.startsWith("Download/"))) continue
        val parsed = EpisodeParser.parse(item.fileName, item.locationHint)
        val title = cleanName(titleCase(parsed.title))
        if (title.isBlank()) continue
        val target = when {
            parsed.isEpisode -> "Movies/Series/$title/Season %02d/".format(parsed.season ?: 0)
            parsed.year != null -> "Movies/Movies/$title (${parsed.year})/"
            else -> continue
        }
        if (current == target) continue
        moves += OrganizeMove(item, target, parsed.isEpisode)
    }
    return moves
}

/**
 * Asks before moving the recognised videos into Movies/Movies/<title (year)>/ and Movies/Series/<show>/Season NN/.
 * The system asks for the write access to exactly these files; nothing moves before both confirmations.
 * [onClose] gets true when files were moved, so the library can be read again.
 */
@Composable
internal fun VideoOrganizeDialog(items: List<VideoItem>, onClose: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        AlertDialog(
            onDismissRequest = { onClose(false) },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au7_vidfolders_title))  } },
            text = { Text(stringResource(I18nR.string.au7_vidfolders_unsupported)) },
            confirmButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
        return
    }
    val plan by produceState<List<OrganizeMove>?>(null, items) {
        value = withContext(Dispatchers.IO) { runCatching { planMoves(context, items) }.getOrDefault(emptyList()) }
    }
    val moves = plan
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK || moves == null) {
            onClose(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val failed = withContext(Dispatchers.IO) {
                moves.count { m ->
                    runCatching {
                        val values = ContentValues().apply { put(MediaStore.Video.Media.RELATIVE_PATH, m.target) }
                        // the id and the uri stay the same: watch positions and marks are kept
                        context.contentResolver.update(m.item.uri, values, null, null) > 0
                    }.getOrDefault(false).not()
                }
            }
            toast(
                context,
                if (failed == 0) context.getString(I18nR.string.au7_vidfolders_done, moves.size)
                else context.getString(I18nR.string.au7_vidfolders_partial, moves.size - failed, failed),
            )
            onClose(true)
        }
    }
    if (moves == null) return
    if (moves.isEmpty()) {
        AlertDialog(
            onDismissRequest = { onClose(false) },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au7_vidfolders_title))  } },
            text = { Text(stringResource(I18nR.string.au7_vidfolders_nothing)) },
            confirmButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(I18nR.string.hc_cancel)) } },
        )
        return
    }
    val films = moves.count { !it.series }
    val episodes = moves.count { it.series }
    AlertDialog(
        onDismissRequest = { onClose(false) },
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.au7_vidfolders_title))  } },
        text = {
            Text(
                stringResource(I18nR.string.au7_vidfolders_confirm, films, episodes) + "\n\n" +
                    moves.take(5).joinToString("\n") { it.item.fileName + "\n  → " + it.target } +
                    (if (moves.size > 5) "\n…" else "")
            )
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val request = MediaStore.createWriteRequest(context.contentResolver, moves.map { it.item.uri })
                    writeLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }.onFailure {
                    toast(context, context.getString(I18nR.string.au7_vidfolders_failed))
                    onClose(false)
                }
            }) { Text(stringResource(I18nR.string.au7_vidfolders_move)) }
        },
        dismissButton = { TextButton(onClick = { onClose(false) }) { Text(stringResource(I18nR.string.hc_cancel)) } },
    )
}
