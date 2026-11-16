package de.mm20.launcher2.comms.stremio

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class LibraryEntry(val type: String, val id: String, val name: String, val poster: String?, val addedAt: Long)

/** Actual player state, keyed by catalog/video identity rather than an expiring stream URL. */
data class WatchProgress(
    val type: String, val id: String, val name: String, val poster: String?, val videoId: String,
    val videoTitle: String, val watchedAt: Long, val stream: String?,
    val positionMs: Long = 0, val durationMs: Long = 0, val timeWatchedMs: Long = 0,
    val completed: Boolean = false, val videoIds: List<String> = emptyList(),
    val resumePercent: Double? = null, val nextVideoId: String? = null,
)

fun StreamItem.streamKey(): String = "$addonName|${url ?: infoHash ?: ytId ?: externalUrl.orEmpty()}"

/** Account-scoped local mirror. All file IO and mutations are serialized off Main. */
class StremioLibrary(context: Context) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private var file = File(app.filesDir, "stremio_library.json")
    private var activeSession = "anonymous"
    private var writable = true
    private val ready = CompletableDeferred<Unit>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _library = MutableStateFlow<List<LibraryEntry>>(emptyList())
    private val _watching = MutableStateFlow<List<WatchProgress>>(emptyList())
    private val _pending = MutableStateFlow<List<WatchProgress>>(emptyList())
    val pendingProgress: StateFlow<List<WatchProgress>> get() = _pending
    val library: StateFlow<List<LibraryEntry>> get() = _library
    val continueWatching: StateFlow<List<WatchProgress>> get() = _watching

    init {
        scope.launch {
            try {
                mutex.withLock {
                    val prefs = app.getSharedPreferences("stremio_account", Context.MODE_PRIVATE)
                    val email = prefs.getString("email", null)
                    val local = StremioLocalProfiles.accountKey(StremioLocalProfiles(app).active())
                    activeSession = prefs.getString("playback_session", if (email == null) "anonymous" else "legacy").orEmpty()
                    val target = accountFile(email ?: local)
                    // Migrate only into the already signed-in account. Never upload an anonymous/other account's library on login.
                    if (email != null && file.isFile && !target.exists()) check(file.renameTo(target))
                    file = target
                    load()
                }
                ready.complete(Unit)
            } catch (error: Exception) { ready.completeExceptionally(error) }
        }
    }

    private fun accountFile(email: String?): File {
        if (email == null) return File(app.filesDir, "stremio_library.json")
        val digest = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase(Locale.ROOT).toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(app.filesDir, "stremio_library_$digest.json")
    }

    suspend fun activateAccount(email: String?, session: String = if (email == null) "anonymous" else "legacy") = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            val target = accountFile(email)
            if (target != file) {
                val previousFile = file; val saved = _library.value; val watching = _watching.value; val pending = _pending.value
                val oldWritable = writable
                try { file = target; load(); writable = true } catch (error: Throwable) {
                    if (email == null) {
                        writable = false; activeSession = session; _library.value = emptyList(); _watching.value = emptyList(); _pending.value = emptyList()
                    } else { file = previousFile; _library.value = saved; _watching.value = watching; _pending.value = pending; writable = oldWritable }
                    throw error
                }
            }
            activeSession = session
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun load() {
        if (!file.exists()) { _library.value = emptyList(); _watching.value = emptyList(); _pending.value = emptyList(); return }
        check(file.length() <= 8L * 1024 * 1024) { "Stremio library is too large" }
        val root = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
        _library.value = root.optJSONArray("library").objects().take(10_000).map {
            LibraryEntry(it.optString("type"), it.optString("id"), it.optString("name"), it.optString("poster").takeIf(String::isNotBlank), it.optLong("at"))
        }
        val orders = root.optJSONObject("orders") ?: JSONObject()
        val decodedOrders = HashMap<String, List<String>>()
        fun decodeProgress(it: JSONObject): WatchProgress {
            val key = it.optString("type") + ":" + it.optString("id")
            val videoIds = decodedOrders.getOrPut(key) {
                val ids = orders.optJSONArray(key) ?: it.optJSONArray("videoIds")
                val values = if (ids == null) emptyList() else (0 until ids.length().coerceAtMost(10_000)).map { i -> ids.optString(i) }
                check(values.sumOf(String::length) <= 64_000 && values.all { id -> id.length <= 512 })
                values
            }
            return WatchProgress(it.optString("type"), it.optString("id"), it.optString("name"), it.optString("poster").takeIf(String::isNotBlank),
                it.optString("video"), it.optString("videoTitle"), it.optLong("at"), it.optString("stream").takeIf(String::isNotBlank),
                it.optLong("position"), it.optLong("duration"), it.optLong("timeWatched"), it.optBoolean("completed"),
                videoIds,
                it.optDouble("percent", Double.NaN).takeIf(Double::isFinite), it.optString("nextVideo").takeIf(String::isNotBlank))
        }
        _watching.value = root.optJSONArray("watching").objects().take(MAX_HISTORY).map(::decodeProgress)
        _pending.value = root.optJSONArray("pending").objects().take(MAX_PENDING).map(::decodeProgress)
    }

    private fun save() {
        fun encodeProgress(it: WatchProgress) = JSONObject().put("type", it.type).put("id", it.id).put("name", it.name).put("poster", it.poster.orEmpty())
            .put("video", it.videoId).put("videoTitle", it.videoTitle).put("at", it.watchedAt).put("stream", it.stream.orEmpty())
            .put("position", it.positionMs).put("duration", it.durationMs).put("timeWatched", it.timeWatchedMs)
            .put("completed", it.completed).put("percent", it.resumePercent ?: JSONObject.NULL).put("nextVideo", it.nextVideoId ?: JSONObject.NULL)
        val orders = JSONObject()
        (_watching.value + _pending.value).forEach {
            if (it.videoIds.isNotEmpty()) orders.put(it.type + ":" + it.id, JSONArray(it.videoIds))
        }
        val root = JSONObject().put("library", JSONArray().also { a -> _library.value.forEach {
            a.put(JSONObject().put("type", it.type).put("id", it.id).put("name", it.name).put("poster", it.poster.orEmpty()).put("at", it.addedAt))
        } }).put("watching", JSONArray().also { a -> _watching.value.forEach { a.put(encodeProgress(it)) } })
            .put("pending", JSONArray().also { a -> _pending.value.forEach { a.put(encodeProgress(it)) } }).put("orders", orders)
        val bytes = root.toString().toByteArray()
        check(bytes.size <= 8 * 1024 * 1024)
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(bytes); atomic.finishWrite(output) } catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    private suspend fun mutate(block: () -> Unit) = withContext(Dispatchers.IO) {
        ready.await(); mutex.withLock {
            check(writable) { "Stremio library could not be loaded" }
            val saved = _library.value; val watching = _watching.value; val pending = _pending.value
            try { block(); save() } catch (error: Throwable) { _library.value = saved; _watching.value = watching; _pending.value = pending; throw error }
        }
    }

    fun isSaved(id: String): Boolean = _library.value.any { it.id == id }

    suspend fun setSaved(type: String, id: String, name: String, poster: String?, saved: Boolean) = mutate {
        _library.value = if (!saved) _library.value.filterNot { it.id == id }
        else if (isSaved(id)) _library.value else (listOf(LibraryEntry(type, id, name, poster, System.currentTimeMillis())) + _library.value).take(10_000)
    }

    suspend fun toggle(type: String, id: String, name: String, poster: String?) = setSaved(type, id, name, poster, !isSaved(id))

    suspend fun markWatched(p: WatchProgress, session: String): Boolean = withContext(Dispatchers.IO) {
        ready.await()
        mutex.withLock {
            if (!writable || session != activeSession) return@withLock false
            val oldPending = _pending.value.firstOrNull { it.type == p.type && it.id == p.id && it.videoId == p.videoId }
            if (oldPending != null && p.watchedAt < oldPending.watchedAt) return@withLock false
            val previous = _watching.value; val pending = _pending.value
            try {
                // Canonicalize the metadata order so queued episodes of one series share one list.
                val order = (previous + pending).firstOrNull { it.type == p.type && it.id == p.id && it.videoIds == p.videoIds }?.videoIds ?: p.videoIds
                val value = p.copy(videoIds = order)
                val latest = previous.firstOrNull { it.id == p.id && it.type == p.type }
                if (latest == null || p.watchedAt >= latest.watchedAt) {
                    _watching.value = (listOf(value) + previous.filterNot { it.id == p.id && it.type == p.type }).take(MAX_HISTORY)
                }
                if (session != "anonymous") {
                    val other = pending.filterNot { it.type == p.type && it.id == p.id && it.videoId == p.videoId }
                    check(other.size < MAX_PENDING) { "Stremio synchronization queue is full" }
                    _pending.value = other + value
                }
                save()
                true
            } catch (error: Throwable) { _watching.value = previous; _pending.value = pending; throw error }
        }
    }

    suspend fun acknowledge(p: WatchProgress) = mutate {
        _pending.value = _pending.value.filterNot { it.type == p.type && it.id == p.id && it.videoId == p.videoId && it.watchedAt <= p.watchedAt }
    }

    suspend fun mergeRemote(saved: List<LibraryEntry>, watching: List<WatchProgress>, removedIds: Map<String, Long>) = mutate {
        val kept = _library.value.filterNot { e -> removedIds[e.id]?.let { it > e.addedAt } == true }
        _library.value = (kept + saved.filter { s -> kept.none { it.id == s.id } && removedIds[s.id] == null }).take(10_000)
        val merged = _watching.value.toMutableList()
        for (w in watching) {
            val i = merged.indexOfFirst { it.id == w.id }
            if (i < 0) merged += w
            else if (w.watchedAt > merged[i].watchedAt || (w.watchedAt == merged[i].watchedAt && merged[i].completed && !w.completed && w.videoId != merged[i].videoId)) merged[i] = w.copy(stream = merged[i].stream.takeIf { merged[i].videoId == w.videoId })
        }
        _watching.value = merged.sortedByDescending { it.watchedAt }.take(MAX_HISTORY)
    }

    fun lastStream(id: String): String? = _watching.value.firstOrNull { it.id == id }?.stream
    suspend fun removeProgress(id: String) = mutate { _watching.value = _watching.value.filterNot { it.id == id } }
    companion object { const val MAX_HISTORY = 50; const val MAX_PENDING = 500 }
}
