package de.mm20.launcher2.ui.media.stremio
import de.mm20.launcher2.i18n.R as I18nR

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.comms.stremio.Addon
import de.mm20.launcher2.comms.stremio.AddonCatalog
import de.mm20.launcher2.comms.stremio.DirectoryAddon
import de.mm20.launcher2.comms.stremio.StremioAccount
import de.mm20.launcher2.comms.stremio.LibraryEntry
import de.mm20.launcher2.comms.stremio.Meta
import de.mm20.launcher2.comms.stremio.MetaPreview
import de.mm20.launcher2.comms.stremio.StreamItem
import de.mm20.launcher2.comms.stremio.StremioAddons
import de.mm20.launcher2.comms.stremio.StremioClient
import de.mm20.launcher2.comms.stremio.StremioLibrary
import de.mm20.launcher2.comms.stremio.StremioPlaybackSettings
import de.mm20.launcher2.comms.stremio.StremioStreamingServer
import de.mm20.launcher2.comms.stremio.StremioProtocol
import de.mm20.launcher2.comms.stremio.Video
import de.mm20.launcher2.comms.stremio.WatchProgress
import de.mm20.launcher2.comms.stremio.streamKey
import de.mm20.launcher2.ui.media.video.PlayerChoice
import de.mm20.launcher2.ui.media.video.VideoPlayerActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** A row of posters: one catalog of one add-on. [items] is null while it loads. */
data class CatalogRow(val key: String, val title: String, val items: List<MetaPreview>?)

/** The detail page: the preview that was tapped, and the full metadata once an add-on answered */
data class DetailState(val preview: MetaPreview, val meta: Meta? = null, val loading: Boolean = true)

/** The list of streams for one movie or episode */
data class StreamsState(
    val type: String,
    val metaId: String,
    val videoId: String,
    val name: String,
    val poster: String?,
    val title: String,
    val items: List<StreamItem> = emptyList(),
    val loading: Boolean = true,
    val lastUsed: String? = null,
)

/**
 * State of the Stremio space of Telos Media: the add-ons, their catalogs, search, the detail page, the stream list,
 * the library and the "continue watching" shelf.
 */
class StremioViewModel : ViewModel(), KoinComponent {
    private val store: StremioAddons by inject()
    private val client: StremioClient by inject()
    private val library: StremioLibrary by inject()
    private val account: StremioAccount by inject()
    private val appContext: Context by inject()

    val addons: StateFlow<List<Addon>> get() = store.addons
    val noticeAccepted: StateFlow<Boolean> get() = store.noticeAccepted
    val saved: StateFlow<List<LibraryEntry>> = combine(library.library, de.mm20.launcher2.comms.media.video.trakt.Trakt.libraryState) { local, trakt ->
        (local + trakt.library).groupBy { it.type to it.id }.values.map { it.maxBy { e -> e.addedAt } }.sortedByDescending { it.addedAt }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progressHistory: StateFlow<List<WatchProgress>> get() = library.continueWatching
    val watching: StateFlow<List<WatchProgress>> = combine(library.continueWatching, de.mm20.launcher2.comms.media.video.trakt.Trakt.libraryState) { local, trakt ->
        (local + trakt.watching).groupBy { it.type to it.id }.values.map { it.maxBy { e -> e.watchedAt } }
            .filterNot { it.completed }.sortedByDescending { it.watchedAt }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun acceptNotice(installDefaults: Boolean = false) {
        if (installDefaults) {
            viewModelScope.launch {
                StremioAddons.DEFAULT_ADDONS.forEach { url ->
                    store.add(url)
                }
                account.addonAdded()
            }
        }
        store.acceptNotice()
    }

    // ---- add-ons ----

    fun addAddon(input: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            store.add(input)
                .onSuccess {
                    onResult(null)
                    account.addonAdded()
                }
                .onFailure { onResult(it.message ?: "Failed") }
        }
    }

    fun removeAddon(baseUrl: String) {
        store.remove(baseUrl)
        viewModelScope.launch { account.addonRemoved(baseUrl) }
    }

    // ---- account ----

    val accountEmail: StateFlow<String?> get() = account.email
    val accountBusy: StateFlow<Boolean> get() = account.busy
    val accountWatched get() = account.watched
    val traktVersion: StateFlow<Int> get() = de.mm20.launcher2.comms.media.video.trakt.Trakt.watchedVersion

    fun refreshAccounts(context: Context, force: Boolean = false) {
        viewModelScope.launch {
            launch { account.awaitReady(); if (account.signedIn) account.syncAll() }
            launch {
                de.mm20.launcher2.comms.media.video.trakt.Trakt.refreshLibrary(appContext, force)
            }
        }
    }

    fun signIn(email: String, password: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            account.signIn(email, password)
                .onSuccess { account.syncAll().onSuccess { onResult(null) }.onFailure { onResult(it.message) } }
                .onFailure { onResult(it.message ?: "Failed") }
        }
    }

    fun signOut() {
        closeDetail()
        closeStreams()
        viewModelScope.launch { account.signOut() }
    }

    /** Syncs add-ons, library and continue watching with the account; [onResult] gets null on success, otherwise the reason */
    fun syncNow(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            account.syncAll().onSuccess { onResult(null) }.onFailure { onResult(it.message ?: "Failed") }
            de.mm20.launcher2.comms.media.video.trakt.Trakt.refreshLibrary(appContext, force = true)
        }
    }

    // ---- the directory of add-ons ----

    private val _directory = MutableStateFlow<List<DirectoryAddon>?>(null)
    /** Add-ons found in the directories of the installed add-ons and in Stremio's public list; null while loading */
    val directory: StateFlow<List<DirectoryAddon>?> get() = _directory

    fun loadDirectory() {
        _directory.value = null
        viewModelScope.launch {
            val found = ArrayList<DirectoryAddon>()
            for (a in addons.value) {
                for (c in a.manifest.addonCatalogs.take(4)) found += client.addonCatalog(a, c)
            }
            found += client.addonList(StremioAddons.PUBLIC_LIST)
            _directory.value = found.distinctBy { it.baseUrl }.sortedBy { it.manifest.name.lowercase() }
        }
    }

    fun installDirectory(d: DirectoryAddon) {
        store.install(d)
        viewModelScope.launch { account.addonAdded() }
    }

    fun refreshAddons() {
        viewModelScope.launch { store.refresh() }
    }

    private val _discover = MutableStateFlow<List<MetaPreview>?>(null)
    val discover: StateFlow<List<MetaPreview>?> get() = _discover
    private var discoverJob: Job? = null

    fun loadDiscover(
        type: String,
        sort: String,
        genre: String?,
        year: String?,
        traktOnly: Boolean,
    ) {
        discoverJob?.cancel()
        _discover.value = null
        discoverJob = viewModelScope.launch {
            val extra = linkedMapOf<String, String>()
            if (!genre.isNullOrBlank() && genre != "All") extra["genre"] = genre
            if (!year.isNullOrBlank() && year != "All") extra["year"] = year
            val items = ArrayList<MetaPreview>()
            val seen = HashSet<String>()
            for (addon in addons.value) {
                if (traktOnly && !addon.manifest.id.contains("trakt", true) && !addon.manifest.name.contains("trakt", true)) continue
                val catalogs = addon.manifest.catalogs.filter { it.type == type }
                val chosen = pickDiscoverCatalog(catalogs, sort) ?: catalogs.firstOrNull() ?: continue
                val extras = extra.filterKeys { key -> chosen.extras.any { it.name == key } }
                val page = runCatching { client.catalog(addon, chosen, extras) }.getOrDefault(emptyList())
                for (m in page) if (seen.add(m.id)) items += m
            }
            _discover.value = items
        }
    }

    fun discoverGenres(type: String): List<String> {
        val names = LinkedHashSet<String>()
        for (addon in addons.value) {
            for (c in addon.manifest.catalogs) {
                if (c.type != type) continue
                c.extras.filter { it.name == "genre" }.flatMap { it.options }.forEach { names += it }
            }
        }
        return names.toList()
    }

    // ---- home and search ----

    private val _rows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val rows: StateFlow<List<CatalogRow>> get() = _rows

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> get() = _query

    private val _searchRows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val searchRows: StateFlow<List<CatalogRow>> get() = _searchRows

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private val limit = Semaphore(4)

    init {
        viewModelScope.launch { store.addons.collect { loadHome(it) } }
    }

    private fun pickDiscoverCatalog(catalogs: List<AddonCatalog>, sort: String): AddonCatalog? {
        fun hit(c: AddonCatalog, vararg keys: String) =
            keys.any { k -> c.id.contains(k, true) || c.name.contains(k, true) }
        return when (sort) {
            "popular" -> catalogs.firstOrNull { hit(it, "popular", "top", "imdb") }
            "new" -> catalogs.firstOrNull { hit(it, "new", "year", "added", "latest", "calendar") }
            "trending" -> catalogs.firstOrNull { hit(it, "trending", "trend") }
            "featured" -> catalogs.firstOrNull { hit(it, "featured", "board", "top") }
            else -> catalogs.firstOrNull()
        } ?: catalogs.firstOrNull()
    }

    private fun rowKey(a: Addon, c: AddonCatalog) = "${a.baseUrl}|${c.type}|${c.id}"
    private fun rowTitle(a: Addon, c: AddonCatalog) = "${c.name} (${c.type}) · ${a.name}"

    private fun loadHome(list: List<Addon>) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            val catalogs = list.flatMap { a -> a.manifest.catalogs.filter { !it.needsExtra }.map { a to it } }.take(MAX_ROWS)
            _rows.value = catalogs.map { (a, c) -> CatalogRow(rowKey(a, c), rowTitle(a, c), null) }
            catalogs.forEach { (a, c) ->
                launch {
                    val items = limit.withPermit { client.catalog(a, c) }
                    _rows.update { rows -> rows.map { if (it.key == rowKey(a, c)) it.copy(items = items) else it } }
                }
            }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _searchRows.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            val catalogs = addons.value.flatMap { a ->
                a.manifest.catalogs.filter { c -> c.searchable && c.extras.none { it.isRequired && it.name != "search" } }.map { a to it }
            }.take(MAX_ROWS)
            _searchRows.value = catalogs.map { (a, c) -> CatalogRow(rowKey(a, c), rowTitle(a, c), null) }
            catalogs.forEach { (a, c) ->
                launch {
                    val items = limit.withPermit { client.catalog(a, c, mapOf("search" to q.trim())) }
                    _searchRows.update { rows -> rows.map { if (it.key == rowKey(a, c)) it.copy(items = items) else it } }
                }
            }
        }
    }

    // ---- detail ----

    private val _detail = MutableStateFlow<DetailState?>(null)
    val detail: StateFlow<DetailState?> get() = _detail

    fun open(preview: MetaPreview) {
        _detail.value = DetailState(preview)
        viewModelScope.launch {
            var meta: Meta? = null
            for (a in addons.value.filter { it.manifest.supports("meta", preview.type, preview.id) }) {
                meta = client.meta(a, preview.type, preview.id)
                if (meta != null) break
            }
            // without a meta add-on the preview has to do
            val fallback = Meta(preview.id, preview.type, preview.name, poster = preview.poster, description = preview.description,
                releaseInfo = preview.releaseInfo, imdbRating = preview.imdbRating)
            _detail.update { if (it?.preview?.id == preview.id) it.copy(meta = meta ?: fallback, loading = false) else it }
        }
    }

    fun openSaved(e: LibraryEntry) = open(MetaPreview(e.id, e.type, e.name, e.poster))

    fun openProgress(p: WatchProgress) = open(MetaPreview(p.id, p.type, p.name, p.poster))

    fun closeDetail() {
        _detail.value = null
    }

    fun isSaved(id: String): Boolean = saved.value.any { it.id == id }

    fun toggleSaved(meta: Meta) {
        val desired = !isSaved(meta.id)
        val traktSession = de.mm20.launcher2.comms.media.video.trakt.Trakt.session(appContext)
        viewModelScope.launch {
            library.setSaved(meta.type, meta.id, meta.name, meta.poster, desired)
            launch { account.saveChanged(meta.type, meta.id, meta.name, meta.poster, !desired) }
            val updated = de.mm20.launcher2.comms.media.video.trakt.Trakt.setWatchlistId(appContext, meta.type, meta.id, meta.name, desired, traktSession)
            if (updated == false) android.widget.Toast.makeText(appContext,
                appContext.getString(I18nR.string.au7_trakt_title) + " · " + appContext.getString(I18nR.string.pdft_error_title),
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun removeProgress(id: String) { viewModelScope.launch { library.removeProgress(id) } }

    // ---- streams ----

    private val _streams = MutableStateFlow<StreamsState?>(null)
    val streams: StateFlow<StreamsState?> get() = _streams

    private var playJob: Job? = null
    private var streamsJob: Job? = null

    /** Asks all stream add-ons for [video] (or for the movie itself when [video] is null) */
    fun showStreams(meta: Meta, video: Video?) {
        val videoId = video?.id ?: meta.id
        val title = if (video == null) meta.name else listOfNotNull(
            meta.name,
            video.season?.let { s -> video.episode?.let { e -> "S%02dE%02d".format(s, e) } },
            video.title.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        val last = library.lastStream(meta.id)
        _streams.value = StreamsState(meta.type, meta.id, videoId, meta.name, meta.poster, title, lastUsed = last)
        streamsJob?.cancel()
        streamsJob = viewModelScope.launch {
            val all = client.allStreams(addons.value, meta.type, videoId)
            // the stream that was used last first, then what Telos can play, the rest at the end
            val sorted = all.sortedWith(compareBy<StreamItem>({ it.streamKey() != last }, { it.kind == StreamItem.Kind.Unsupported }))
            _streams.update { if (it?.videoId == videoId) it.copy(items = sorted, loading = false) else it }
        }
    }

    fun closeStreams() {
        playJob?.cancel()
        streamsJob?.cancel()
        _streams.value = null
    }

    /**
     * Starts the player (or the browser) for [stream]. Subtitles that come with the stream, and those of subtitle
     * add-ons (asked for a few seconds at most), go along. [onDone] says whether Telos could open this kind of stream.
     */
    fun play(context: Context, stream: StreamItem, state: StreamsState, onDone: (Boolean) -> Unit) {
        if (stream.kind == StreamItem.Kind.Unsupported) {
            onDone(false)
            return
        }
        _streams.update { it?.copy(loading = true) }
        playJob?.cancel()
        playJob = viewModelScope.launch {
            account.awaitReady()
            // Wait for any in-flight login sync before selecting a resume point.
            de.mm20.launcher2.comms.media.video.trakt.Trakt.refreshLibrary(appContext)
            if (_streams.value?.videoId != state.videoId) return@launch
            val playbackSettings = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { StremioPlaybackSettings.load(appContext) }
            val sharedTorrent = if (stream.kind == StreamItem.Kind.Torrent && playbackSettings.sharedServer) {
                try {
                    StremioStreamingServer.settings(playbackSettings.streamingServer)
                    StremioStreamingServer.streamingUrl(playbackSettings.streamingServer, stream) ?: error("Unsupported torrent")
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) {
                    _streams.update { it?.copy(loading = false) }
                    android.widget.Toast.makeText(context, I18nR.string.stremio_shared_unavailable, android.widget.Toast.LENGTH_LONG).show()
                    onDone(false)
                    return@launch
                }
            } else null
            val needsPlayer = stream.kind == StreamItem.Kind.Web || stream.kind == StreamItem.Kind.Torrent
            val subtitles = if (!needsPlayer) emptyList() else {
                val extra = stream.filename?.let { mapOf("filename" to it) }.orEmpty()
                val fromAddons = withTimeoutOrNull(6_000) { client.allSubtitles(addons.value, state.type, state.videoId, extra) }.orEmpty()
                (stream.subtitles + fromAddons).distinctBy { it.url }.take(MAX_SUBTITLES)
            }
            val remaining = de.mm20.launcher2.comms.stremio.StremioEpisodeQueue.after(
                state.type, _detail.value?.meta?.videos.orEmpty(), state.videoId,
            )
            val storedProgress = (library.continueWatching.value + de.mm20.launcher2.comms.media.video.trakt.Trakt.libraryState.value.watching)
                .filter { it.id == state.metaId && it.videoId == state.videoId && !it.completed }.maxByOrNull { it.watchedAt }
            val selectedVideo = _detail.value?.meta?.videos?.firstOrNull { it.id == state.videoId }
            val episodeOrder = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                de.mm20.launcher2.comms.stremio.StremioProgressState.orderedVideoIds(_detail.value?.meta?.videos.orEmpty())
                    .takeIf { it.size <= 10_000 && it.sumOf(String::length) <= 64_000 }.orEmpty()
            }
            fun withSubtitles(intent: Intent): Intent = intent
                .putExtra("trakt_session", de.mm20.launcher2.comms.media.video.trakt.Trakt.session(appContext))
                .apply { selectedVideo?.season?.let { putExtra("trakt_season", it) }; selectedVideo?.episode?.let { putExtra("trakt_episode", it) } }
                .putExtra("stremio_meta_id", state.metaId)
                .putExtra("stremio_video_id", state.videoId)
                .putExtra("stremio_next_video_id", remaining.firstOrNull()?.takeIf { video ->
                    video.released == null || runCatching { java.time.Instant.parse(video.released).toEpochMilli() <= System.currentTimeMillis() }.getOrDefault(false)
                }?.id)
                .putExtra("stremio_meta_name", state.name.take(512))
                .putExtra("stremio_meta_poster", state.poster?.take(4096))
                .putExtra("stremio_video_title", state.title.take(512))
                .putExtra("stremio_stream_key", stream.streamKey().take(4096))
                .putExtra("stremio_session", account.playbackSession)
                .putExtra("stremio_resume_ms", storedProgress?.positionMs ?: 0L)
                .putExtra("stremio_resume_percent", storedProgress?.resumePercent ?: Double.NaN)
                .putExtra("stremio_watched_ms", storedProgress?.timeWatchedMs ?: 0L)
                .putStringArrayListExtra("stremio_episode_order", ArrayList(episodeOrder))
                .putStringArrayListExtra("stremio_next_ids", ArrayList(remaining.map { it.id }))
                .putStringArrayListExtra("stremio_next_titles", ArrayList(remaining.map { it.title }))
                .putIntegerArrayListExtra("stremio_next_seasons", ArrayList(remaining.map { it.season ?: -1 }))
                .putIntegerArrayListExtra("stremio_next_episodes", ArrayList(remaining.map { it.episode ?: -1 }))
                .putExtra("stremio_type", state.type)
                .putExtra("stremio_addon", stream.addonName)
                .putExtra("stremio_kind", stream.kind.name)
                .putExtra("stremio_playback", true)
                .putExtra("stremio_shared_server", sharedTorrent != null)
                .putStringArrayListExtra(VideoPlayerActivity.EXTRA_SUB_URLS, ArrayList(subtitles.map { it.url }))
                .putStringArrayListExtra(VideoPlayerActivity.EXTRA_SUB_LANGS, ArrayList(subtitles.map { it.lang }))
                .putStringArrayListExtra(VideoPlayerActivity.EXTRA_SUB_LABELS, ArrayList(subtitles.map { it.label.orEmpty() }))
            val opened = when (stream.kind) {
                StreamItem.Kind.Web -> {
                    context.startActivity(
                        withSubtitles(
                            Intent(context, PlayerChoice.playerClass(context))
                                .putStringArrayListExtra(VideoPlayerActivity.EXTRA_URIS, arrayListOf(stream.url!!))
                                .putStringArrayListExtra(VideoPlayerActivity.EXTRA_TITLES, arrayListOf(state.title))
                        )
                    )
                    true
                }
                StreamItem.Kind.Torrent -> {
                    val magnet = StremioProtocol.magnet(stream, state.title)
                    if (sharedTorrent != null) {
                        context.startActivity(withSubtitles(Intent(context, PlayerChoice.playerClass(context))
                            .putStringArrayListExtra(VideoPlayerActivity.EXTRA_URIS, arrayListOf(sharedTorrent))
                            .putStringArrayListExtra(VideoPlayerActivity.EXTRA_TITLES, arrayListOf(state.title))))
                        true
                    } else if (magnet == null) false else {
                        // the torrent player opens the torrent (finding peers), then plays the file the add-on named, or the biggest
                        // video; the torrent route and Wi-Fi rules of Telos Video apply
                        context.startActivity(
                            withSubtitles(
                                Intent(context, PlayerChoice.playerClass(context))
                                    .putExtra(VideoPlayerActivity.EXTRA_SOURCE, magnet)
                                    .apply { stream.fileIdx?.let { putExtra(VideoPlayerActivity.EXTRA_FILE_IDX, it) } }
                            )
                        )
                        true
                    }
                }
                StreamItem.Kind.YouTube -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${Uri.encode(stream.ytId)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }
                StreamItem.Kind.External -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(stream.externalUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }
                StreamItem.Kind.Unsupported -> false
            }
            _streams.update { it?.copy(loading = false) }
            onDone(opened)
        }
    }

    companion object {
        private const val MAX_ROWS = 30
        private const val MAX_SUBTITLES = 15
    }
}
