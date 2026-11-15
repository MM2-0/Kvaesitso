package de.mm20.launcher2.ui.media.video

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import de.mm20.launcher2.comms.media.video.VideoItem
import de.mm20.launcher2.comms.media.video.VideoPrefs
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.files.remote.ConnectionStore
import de.mm20.launcher2.ui.files.remote.RemoteClient
import de.mm20.launcher2.ui.files.remote.RemoteEntry
import de.mm20.launcher2.ui.files.remote.RemotePath
import de.mm20.launcher2.ui.files.remote.RemoteRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Videos on the network storages of Telos Files, played from `rem://<connection>/<path>` addresses. */
object RemoteVideo {
    val extensions = setOf("mkv", "mp4", "avi", "mov", "webm", "m4v", "ts", "mpg", "mpeg", "wmv", "flv")

    fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase() in extensions

    /** The player address of a remote path (`rem://id/folder/file`), properly encoded */
    fun uriOf(remotePath: String): Uri =
        Uri.Builder().scheme("rem").authority(RemotePath.idOf(remotePath))
            .encodedPath(Uri.encode(RemotePath.innerOf(remotePath), "/")).build()

    fun isRemote(uri: Uri) = uri.scheme == "rem"

    private const val CACHE = "remote_videos.json"

    fun cached(context: Context): List<VideoItem> = runCatching {
        val array = JSONArray(File(context.filesDir, CACHE).readText())
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val uri = Uri.parse(o.getString("uri"))
            val name = o.getString("name")
            VideoItem(
                id = -(uri.toString().hashCode().toLong() and 0x7fffffffL) - 1,
                uri = uri, title = name.substringBeforeLast('.'), fileName = name, durationMs = 0L,
                sizeBytes = o.optLong("size"), folder = o.optString("folder"), dateAddedSeconds = o.optLong("mod") / 1000,
            )
        }
    }.getOrDefault(emptyList())

    /** Lists the video files below every saved folder (three levels deep, at most 3000 files) */
    suspend fun scan(context: Context): Int = withContext(Dispatchers.IO) {
        val out = JSONArray()
        val connections = ConnectionStore(context)
        for (source in VideoPrefs.networkSources(context)) {
            if (out.length() >= 3000) break
            val id = RemotePath.idOf(source)
            val connectionName = connections.get(id)?.name ?: continue
            runCatching {
                val client = RemoteRegistry.client(context, id)
                fun walk(inner: String, depth: Int) {
                    if (out.length() >= 3000) return
                    val entries = client.list(inner)
                    for (e in entries) {
                        if (e.isDir) {
                            if (depth < 3) walk(e.path, depth + 1)
                        } else if (isVideo(e.name)) {
                            out.put(
                                JSONObject().put("uri", uriOf(RemotePath.build(id, e.path)).toString()).put("name", e.name)
                                    .put("size", e.size).put("mod", e.modified)
                                    .put("folder", connectionName + inner.trimEnd('/').substringAfterLast('/').let { if (it.isEmpty()) "" else " / $it" })
                            )
                        }
                    }
                }
                walk(RemotePath.innerOf(source), 0)
            }
        }
        File(context.filesDir, CACHE).writeText(out.toString())
        out.length()
    }
}

/** Reads `rem://` addresses through the client of the storage; any other address goes to [fallback]. */
@OptIn(UnstableApi::class)
internal class RemoteRoutingDataSource(private val context: Context, private val fallback: DataSource) : DataSource {
    private val remote = RemoteVideoDataSource(context)
    private var active: DataSource? = null
    private val networkGate = de.mm20.launcher2.comms.network.TransferNetworkGate.get(context, "torrent")
    private var managedNetwork = false
    private fun checkNetwork() {
        if (managedNetwork && !networkGate.allowed.value) {
            runCatching { active?.close() }
            throw java.io.IOException(context.getString(I18nR.string.netfw_blocked))
        }
    }

    override fun addTransferListener(listener: TransferListener) {
        fallback.addTransferListener(listener)
        remote.addTransferListener(listener)
    }

    override fun open(dataSpec: DataSpec): Long {
        managedNetwork = dataSpec.uri.scheme in setOf("http", "https") &&
            dataSpec.uri.host !in setOf("127.0.0.1", "localhost", "::1", "[::1]")
        networkGate.refresh()
        checkNetwork()
        val d = if (RemoteVideo.isRemote(dataSpec.uri)) remote else fallback
        active = d
        return d.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkNetwork()
        return active!!.read(buffer, offset, length)
    }
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()
    override fun close() {
        try { active?.close() } finally { active = null }
    }

    companion object {
        fun factory(context: Context): DataSource.Factory {
            val base = DefaultDataSource.Factory(context)
            return DataSource.Factory { RemoteRoutingDataSource(context, base.createDataSource()) }
        }
    }
}

/**
 * A range read on top of the plain `openRead` of the storages: it opens the file and skips to the
 * start position, so a jump far ahead has to read through the data in between on protocols that
 * cannot seek.
 */
@OptIn(UnstableApi::class)
internal class RemoteVideoDataSource(private val context: Context) : BaseDataSource(true) {
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val id = dataSpec.uri.authority ?: throw IOException("No storage in the address")
        val inner = dataSpec.uri.path ?: "/"
        val client = RemoteRegistry.client(context, id)
        val size = sizeOf(client, id, inner)
        if (size != null && dataSpec.position > size) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        val s = try {
            client.openRead(inner)
        } catch (e: IOException) {
            RemoteRegistry.reset(id)
            RemoteRegistry.client(context, id).openRead(inner)
        }
        try {
            var left = dataSpec.position
            while (left > 0) {
                val n = s.skip(left)
                if (n > 0) left -= n
                else {
                    if (s.read() < 0) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
                    left--
                }
            }
        } catch (e: Throwable) {
            // a failed skip must not leave the connection (FTP, SFTP, SMB sessions) open
            runCatching { s.close() }
            throw e
        }
        stream = s
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            size != null -> size - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val want = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val n = stream!!.read(buffer, offset, want)
        if (n < 0) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { stream?.close() } catch (_: IOException) {}
        stream = null
        uri = null
        if (opened) { opened = false; transferEnded() }
    }

    private fun sizeOf(client: RemoteClient, id: String, inner: String): Long? {
        sizes["$id$inner"]?.let { return it }
        val parent = inner.substringBeforeLast('/', "").ifEmpty { "/" }
        val name = inner.substringAfterLast('/')
        val size = runCatching { client.list(parent).firstOrNull { it.name == name && !it.isDir }?.size }.getOrNull()
        return size?.takeIf { it > 0 }?.also { sizes["$id$inner"] = it }
    }

    private companion object {
        val sizes = ConcurrentHashMap<String, Long>()
    }
}

/** Choose network folders whose videos appear in the library. */
@Composable
internal fun NetworkSourcesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(VideoPrefs.networkSources(context)) }
    var status by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    // browsing: null = not adding; "" = choosing a storage; otherwise a remote path
    var browsing by remember { mutableStateOf<String?>(null) }
    var folders by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var browseError by remember { mutableStateOf("") }
    val connections = remember { ConnectionStore(context).all() }

    LaunchedEffect(browsing) {
        val path = browsing
        folders = emptyList()
        browseError = ""
        if (path.isNullOrEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching { RemoteRegistry.client(context, RemotePath.idOf(path)).list(RemotePath.innerOf(path)).filter { it.isDir }.sortedBy { it.name.lowercase() } }
        }.onSuccess { folders = it }.onFailure { browseError = it.message ?: "" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { TelosCyberHeading {  Text(stringResource(I18nR.string.vn_network_scan_title))  } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val b = browsing
                if (b == null) {
                    Text(stringResource(I18nR.string.vn_network_folders_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (connections.isEmpty()) {
                        Text(stringResource(I18nR.string.vn_no_connections), style = MaterialTheme.typography.bodyMedium)
                    }
                    sources.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (ConnectionStore(context).get(RemotePath.idOf(s))?.name ?: "?") + RemotePath.innerOf(s),
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                sources = sources - s
                                VideoPrefs.setNetworkSources(context, sources)
                            }) { Text(stringResource(I18nR.string.vn_remove)) }
                        }
                    }
                    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(I18nR.string.vn_network_slow_seek), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (b.isEmpty()) {
                    Text(stringResource(I18nR.string.vn_choose_storage), style = MaterialTheme.typography.titleSmall)
                    connections.forEach { c ->
                        Text(c.name, modifier = Modifier.fillMaxWidth().clickable { browsing = RemotePath.build(c.id, "/") }.padding(vertical = 10.dp))
                    }
                } else {
                    Text(RemotePath.innerOf(b), style = MaterialTheme.typography.titleSmall)
                    if (browseError.isNotEmpty()) Text(browseError, color = MaterialTheme.colorScheme.error)
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        if (RemotePath.innerOf(b) != "/") {
                            item {
                                Text("..", modifier = Modifier.fillMaxWidth().clickable {
                                    browsing = RemotePath.build(RemotePath.idOf(b), RemotePath.innerOf(b).substringBeforeLast('/', "/"))
                                }.padding(vertical = 10.dp))
                            }
                        }
                        items(folders, key = { it.path }) { f ->
                            Text(f.name, modifier = Modifier.fillMaxWidth().clickable { browsing = RemotePath.build(RemotePath.idOf(b), f.path) }.padding(vertical = 10.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            val b = browsing
            if (b == null) {
                Row {
                    TextButton(enabled = connections.isNotEmpty(), onClick = { browsing = "" }) { Text(stringResource(I18nR.string.vn_add_folder)) }
                    TextButton(enabled = !scanning && sources.isNotEmpty(), onClick = {
                        scanning = true
                        status = context.getString(I18nR.string.vn_scanning)
                        scope.launch {
                            try {
                                val n = RemoteVideo.scan(context)
                                status = context.getString(I18nR.string.vn_scan_done, n)
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                status = e.message ?: context.getString(I18nR.string.au_video_unknown_error)
                            } finally {
                                scanning = false
                            }
                        }
                    }) { Text(stringResource(I18nR.string.vn_rescan)) }
                }
            } else if (b.isNotEmpty()) {
                TextButton(onClick = {
                    sources = (sources + b).distinct()
                    VideoPrefs.setNetworkSources(context, sources)
                    browsing = null
                }) { Text(stringResource(I18nR.string.vn_use_this_folder)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (browsing != null) browsing = null else onDismiss() }) {
                Text(stringResource(if (browsing != null) I18nR.string.hc_cancel else I18nR.string.vn_close))
            }
        },
    )
}
