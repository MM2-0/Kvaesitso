package de.mm20.launcher2.comms.stremio

import android.content.Context
import de.mm20.launcher2.comms.media.video.Http
import de.mm20.launcher2.comms.remote.SecretBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Optional sign-in to a Stremio account, to keep the add-ons, the library and "continue watching" in step with the
 * account (this is how the Stremio apps back these up). It talks to Stremio's account service at api.strem.io with the
 * requests its open-source core (stremio-core, MIT) documents: login, addonCollectionGet/Set and datastoreGet/Put.
 * The password is sent once and never stored; only the session key it returns is kept, encrypted with the Android Keystore.
 * Stremio can change or block this service at any time; nothing here is endorsed by Stremio.
 */
class StremioAccount(
    context: Context,
    private val addons: StremioAddons,
    private val library: StremioLibrary,
) {
    private val app = context.applicationContext
    private val prefs by lazy { app.getSharedPreferences("stremio_account", Context.MODE_PRIVATE) }
    private val initialized = CompletableDeferred<Unit>()
    @Volatile private var keyCache: String? = null
    @Volatile private var sessionCache = "anonymous"
    private val mutex = Mutex()

    private val _email = MutableStateFlow<String?>(null)
    /** The email of the account that is signed in, or null */
    val email: StateFlow<String?> get() = _email

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> get() = _busy

    private val _watched = MutableStateFlow<Map<String, StremioWatched>>(emptyMap())
    val watched: StateFlow<Map<String, StremioWatched>> get() = _watched

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val key = prefs.getString("key", null)?.let { runCatching { SecretBox.decrypt(it) }.getOrNull() }?.takeIf { it.isNotBlank() }
                val email = prefs.getString("email", null)?.takeIf { key != null }
                val session = if (email == null) "anonymous" else prefs.getString("playback_session", "legacy").orEmpty()
                library.activateAccount(email, session)
                sessionCache = session; keyCache = key; _email.value = email
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { keyCache = null; _email.value = null }
            finally { initialized.complete(Unit) }
        }
    }
    suspend fun awaitReady() { initialized.await() }
    private suspend fun authKey(): String? { initialized.await(); return keyCache }
    val signedIn: Boolean get() = keyCache != null

    // ---- requests ----

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val c = Http.open("$API/$path", StremioClient.USER_AGENT)
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val answer = JSONObject(Http.readText(c))
        answer.optJSONObject("error")?.let { error(it.optString("message", "Stremio refused the request")) }
        answer
    }

    /** An opaque generation prevents a player started under one login from writing into another. */
    val playbackSession: String get() = sessionCache
    private val lastPush = LinkedHashMap<Triple<String, String, String>, Long>()
    private val lastSent = LinkedHashMap<Triple<String, String, String>, WatchProgress>()

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        initialized.await()
        runCatching {
            val answer = post("login", JSONObject().put("type", "Login").put("email", email.trim()).put("password", password).put("facebook", false))
            val key = answer.optJSONObject("result")?.optString("authKey").orEmpty()
            check(key.isNotBlank()) { "Stremio sent no session" }
            mutex.withLock {
                val session = java.util.UUID.randomUUID().toString()
                library.activateAccount(email.trim(), session)
                prefs.edit().putString("key", SecretBox.encrypt(key)).putString("email", email.trim())
                    .putString("playback_session", session).apply()
                keyCache = key; sessionCache = session
                _email.value = email.trim(); _watched.value = emptyMap(); lastPush.clear(); lastSent.clear()
            }
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        initialized.await()
        val key = mutex.withLock {
            val currentKey = authKey()
            try { library.activateAccount(StremioLocalProfiles.accountKey(StremioLocalProfiles(app).active())) } finally {
                prefs.edit().clear().apply(); keyCache = null; sessionCache = "anonymous"; _email.value = null; _watched.value = emptyMap(); lastPush.clear(); lastSent.clear()
            }
            currentKey
        }
        if (key != null) runCatching { post("logout", JSONObject().put("type", "Logout").put("authKey", key)) }
        Unit
    }

    // ---- add-ons ----

    private suspend fun remoteAddons(key: String): List<JSONObject> {
        val result = post("addonCollectionGet", JSONObject().put("type", "AddonCollectionGet").put("authKey", key).put("update", false))
            .optJSONObject("result") ?: return emptyList()
        val arr = result.optJSONArray("addons") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun descriptorOf(a: Addon): JSONObject {
        val manifest = runCatching { JSONObject(a.rawManifest) }.getOrNull() ?: JSONObject()
        return JSONObject().put("manifest", manifest).put("transportUrl", StremioProtocol.manifestUrl(a.baseUrl))
            .put("flags", JSONObject().put("official", a.official).put("protected", a.protected))
    }

    /** What the account has is merged into the installed add-ons; add-ons only found here are sent to the account */
    private suspend fun syncAddons(key: String): Int {
        val remote = remoteAddons(key)
        val fromAccount = remote.mapNotNull { d ->
            val manifestObject = d.optJSONObject("manifest") ?: return@mapNotNull null
            val raw = manifestObject.toString()
            val manifest = StremioProtocol.parseManifest(raw) ?: return@mapNotNull null
            val base = StremioProtocol.baseUrl(d.optString("transportUrl")) ?: return@mapNotNull null
            val flags = d.optJSONObject("flags")
            Addon(base, manifest, raw, flags?.optBoolean("official") ?: false, flags?.optBoolean("protected") ?: false)
        }
        val before = addons.addons.value.size
        addons.upsert(fromAccount)
        val local = addons.addons.value
        val onlyLocal = local.filter { l -> remote.none { StremioProtocol.baseUrl(it.optString("transportUrl")) == l.baseUrl } }
        if (onlyLocal.isNotEmpty()) pushAddons(key, remote)
        return addons.addons.value.size - before
    }

    /** Sets the account's add-ons to everything the account already has plus what is installed here (never fewer) */
    private suspend fun pushAddons(key: String, remote: List<JSONObject>, removed: String? = null) {
        val list = JSONArray()
        val seen = HashSet<String>()
        // the account's own entries first, as they are (a removed one is left out unless the account protects it)
        for (d in remote) {
            val base = StremioProtocol.baseUrl(d.optString("transportUrl"))
            val protectedFlag = d.optJSONObject("flags")?.optBoolean("protected") ?: false
            if (base != null && base == removed && !protectedFlag) continue
            list.put(d)
            if (base != null) seen += base
        }
        addons.addons.value.filter { it.baseUrl !in seen && it.baseUrl != removed }.forEach { list.put(descriptorOf(it)) }
        post("addonCollectionSet", JSONObject().put("type", "AddonCollectionSet").put("authKey", key).put("addons", list))
    }

    /** Called after the user removed an add-on here: it is removed from the account as well */
    suspend fun addonRemoved(baseUrl: String) {
        val key = authKey() ?: return
        mutex.withLock { runCatching { pushAddons(key, remoteAddons(key), removed = baseUrl) } }
    }

    /** Called after the user added an add-on here */
    suspend fun addonAdded() {
        val key = authKey() ?: return
        mutex.withLock { runCatching { pushAddons(key, remoteAddons(key)) } }
    }

    // ---- library and continue watching ----

    private suspend fun remoteLibrary(key: String, ids: List<String> = emptyList()): List<JSONObject> {
        val body = JSONObject().put("authKey", key).put("collection", "libraryItem").put("ids", JSONArray(ids)).put("all", ids.isEmpty())
        val arr = requireNotNull(post("datastoreGet", body).optJSONArray("result")) { "Invalid Stremio library response" }
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun millis(s: String?): Long = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    private fun nullIfBlank(s: String?): String? = s?.takeIf { it.isNotBlank() && it != "null" }

    private suspend fun syncLibrary(key: String) {
        val items = remoteLibrary(key)
        val saved = ArrayList<LibraryEntry>()
        val watching = ArrayList<WatchProgress>()
        val removed = HashMap<String, Long>()
        val watched = HashMap<String, StremioWatched>()
        for (o in items) {
            val id = o.optString("_id")
            val type = o.optString("type")
            if (id.isBlank() || type == "other") continue
            val poster = nullIfBlank(o.optString("poster"))
            val state = o.optJSONObject("state")
            if (state != null) watched[id] = StremioWatched(
                movie = type == "movie" && (state.optInt("timesWatched") > 0 || state.optInt("flaggedWatched") > 0),
                videos = nullIfBlank(state.optString("watched")),
            )
            val isRemoved = o.optBoolean("removed")
            val temp = o.optBoolean("temp")
            val mtime = millis(o.optString("_mtime"))
            if (isRemoved) removed[id] = mtime
            else if (!temp) saved += LibraryEntry(type, id, o.optString("name"), poster, millis(nullIfBlank(o.optString("_ctime"))).takeIf { it > 0 } ?: mtime)
            if ((!isRemoved || temp) && (state?.optLong("timeOffset") ?: 0L) > 0L) {
                val video = nullIfBlank(state?.optString("video_id")) ?: id
                watching += WatchProgress(
                    type, id, o.optString("name"), poster, video, video.takeIf { it != id }.orEmpty(),
                    millis(nullIfBlank(state?.optString("lastWatched"))).takeIf { it > 0 } ?: mtime, null,
                    positionMs = state?.optLong("timeOffset")?.coerceAtLeast(0) ?: 0,
                    durationMs = state?.optLong("duration")?.coerceAtLeast(0) ?: 0,
                    timeWatchedMs = state?.optLong("timeWatched")?.coerceAtLeast(0) ?: 0,
                    completed = (state?.optLong("duration") ?: 0) > 0 && (state?.optLong("timeOffset") ?: 0).toDouble() > (state?.optLong("duration") ?: 0) * StremioProgressState.CREDITS_THRESHOLD,
                )
            }
        }
        library.mergeRemote(saved, watching, removed)
        _watched.value = watched
        // Retry newer locally recorded positions after an offline/failed write. Never copy a previous account's data.
        val pending = library.pendingProgress.value
        val queued = (pending + library.continueWatching.value).groupBy { Triple(it.type, it.id, it.videoId) }
            .values.map { it.maxBy { p -> p.watchedAt } }.sortedBy { it.watchedAt }
        for (progress in queued) {
            val remote = items.firstOrNull { it.optString("_id") == progress.id }
            val remoteTime = millis(remote?.optJSONObject("state")?.optString("lastWatched"))
            if (progress.durationMs > 0 && (progress.watchedAt > remoteTime || pending.any { it.type == progress.type && it.id == progress.id && it.videoId == progress.videoId })) {
                putProgress(key, progress)
                library.acknowledge(progress)
            }
        }
        // titles saved only here go to the account
        val remoteIds = items.map { it.optString("_id") }.toSet()
        library.library.value.filter { it.id !in remoteIds }.forEach { e ->
            runCatching { putItem(key, e.type, e.id, e.name, e.poster, removed = false, ctime = e.addedAt) }
        }
    }

    /** Writes one title to the account's library, changing only what changed: the rest of an existing item is kept */
    private suspend fun putItem(
        key: String, type: String, id: String, name: String, poster: String?,
        removed: Boolean, ctime: Long? = null,
    ) {
        val now = Instant.now().toString()
        val item = remoteLibrary(key, listOf(id)).firstOrNull() ?: JSONObject()
            .put("_id", id).put("name", name).put("type", type).put("poster", poster ?: JSONObject.NULL).put("posterShape", "poster")
            .put("temp", false).put("_ctime", Instant.ofEpochMilli(ctime ?: System.currentTimeMillis()).toString())
            .put("state", JSONObject().put("lastWatched", JSONObject.NULL).put("timeWatched", 0).put("timeOffset", 0).put("overallTimeWatched", 0)
                .put("timesWatched", 0).put("flaggedWatched", 0).put("duration", 0).put("video_id", JSONObject.NULL).put("watched", JSONObject.NULL).put("noNotif", false))
            .put("behaviorHints", JSONObject().put("defaultVideoId", JSONObject.NULL).put("featuredVideoId", JSONObject.NULL).put("hasScheduledVideos", false))
        item.put("removed", removed).put("_mtime", now)
        post("datastorePut", JSONObject().put("authKey", key).put("collection", "libraryItem").put("changes", JSONArray().put(item)))
    }

    /** After the user saved or removed a title here */
    suspend fun saveChanged(type: String, id: String, name: String, poster: String?, removed: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = authKey() ?: return@withLock
            runCatching { putItem(key, type, id, name, poster, removed) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
        Unit
    }

    /** Persist before finishing BroadcastReceiver.goAsync; network work must not hold its deadline. */
    suspend fun recordPlayback(progress: WatchProgress, session: String): Boolean { initialized.await(); return library.markWatched(progress, session) }

    /** Upload an already persisted position; failures remain available for the next sync. */
    suspend fun playbackProgress(progress: WatchProgress, session: String, force: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (session != playbackSession) return@withLock
            val key = authKey() ?: return@withLock
            val identity = Triple(progress.type, progress.id, progress.videoId)
            val now = android.os.SystemClock.elapsedRealtime()
            if (!force && now - (lastPush[identity] ?: Long.MIN_VALUE / 2) < 30_000) return@withLock
            if (force && lastSent[identity]?.copy(watchedAt = progress.watchedAt) == progress) return@withLock
            lastPush[identity] = now
            if (lastPush.size > StremioLibrary.MAX_HISTORY) lastPush.remove(lastPush.keys.first())
            runCatching { putProgress(key, progress); library.acknowledge(progress); lastSent[identity] = progress
                if (lastSent.size > StremioLibrary.MAX_HISTORY) lastSent.remove(lastSent.keys.first())
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    private suspend fun putProgress(key: String, progress: WatchProgress, existing: JSONObject? = null) {
        val item = existing ?: remoteLibrary(key, listOf(progress.id)).firstOrNull() ?: JSONObject()
            .put("_id", progress.id).put("type", progress.type).put("name", progress.name)
            .put("poster", progress.poster ?: JSONObject.NULL).put("posterShape", "poster")
            .put("removed", !library.isSaved(progress.id)).put("temp", !library.isSaved(progress.id))
            .put("_ctime", Instant.ofEpochMilli(progress.watchedAt).toString())
        var current = progress
        if (progress.type == "series" && progress.timeWatchedMs.toDouble() > progress.durationMs * StremioProgressState.WATCHED_THRESHOLD) {
            val field = item.optJSONObject("state")?.optString("watched")?.takeIf { it.isNotBlank() && it != "null" }
            val anchor = field?.substringBeforeLast(':')?.substringBeforeLast(':')
            if (progress.videoId !in progress.videoIds || (anchor != null && anchor !in progress.videoIds)) {
                val client = StremioClient()
                var order: List<String>? = null
                for (addon in addons.addons.value.take(10)) {
                    val meta = client.meta(addon, progress.type, progress.id) ?: continue
                    val ids = StremioProgressState.orderedVideoIds(meta.videos)
                    if (progress.videoId in ids && (anchor == null || anchor in ids) && ids.size <= 10_000 && ids.sumOf(String::length) <= 64_000) {
                        order = ids; break
                    }
                }
                current = progress.copy(videoIds = checkNotNull(order) { "Episode metadata is needed to synchronize watched history" })
            }
        }
        val update = StremioProgressState.update(item, current) ?: return
        post("datastorePut", JSONObject().put("authKey", key).put("collection", "libraryItem").put("changes", JSONArray().put(update)))
        val state = update.getJSONObject("state")
        _watched.value = _watched.value + (progress.id to StremioWatched(
            movie = progress.type == "movie" && (state.optInt("timesWatched") > 0 || state.optInt("flaggedWatched") > 0),
            videos = nullIfBlank(state.optString("watched")),
        ))
    }

    // ---- all of it ----

    /** Brings add-ons, library and continue watching in step with the account; the result tells how many add-ons were new here */
    suspend fun syncAll(): Result<Int> = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            mutex.withLock {
                runCatching {
                    val key = authKey() ?: error("Not signed in")
                    library.activateAccount(_email.value, playbackSession)
                    // An add-on failure must not prevent watch state from being refreshed.
                    val addonResult = runCatching { syncAddons(key) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    syncLibrary(key)
                    addonResult.getOrThrow()
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            }
        } finally { _busy.value = false }
    }

    companion object {
        private const val API = "https://api.strem.io/api"
    }
}
