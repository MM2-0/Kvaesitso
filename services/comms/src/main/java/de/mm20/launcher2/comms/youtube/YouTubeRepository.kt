package de.mm20.launcher2.comms.youtube

import android.content.Context
import de.mm20.launcher2.comms.youtube.flow.AudioStreamSelector
import de.mm20.launcher2.comms.youtube.flow.NewPipeDownloader
import de.mm20.launcher2.comms.youtube.flow.YouTubeLink
import de.mm20.launcher2.comms.youtube.flow.parseYouTubeLink
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

data class YouTubeEntry(
    val url: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val durationSeconds: Long
)

data class YouTubePlaylistSummary(
    val id: String,
    val url: String,
    val title: String,
    val subtitle: String,
    val thumbnail: String?
)

data class YouTubePlayback(
    val entry: YouTubeEntry,
    val streamUrl: String,
    val audioUrl: String? = null,
    val audioOnly: Boolean
)

class YouTubeRepository(private val context: Context) {

    private val musicVersionLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var musicVersion: Pair<Long, String>? = null
    private suspend fun musicClientVersion(): String = musicVersionLock.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        musicVersion?.takeIf { now - it.first < 3_600_000 }?.let { return@withLock it.second }
        val response = NewPipe.getDownloader().execute(Request("GET", "https://music.youtube.com/", emptyMap(), null, null, false))
        require(response.responseCode() in 200..299)
        val value = musicClientVersionFromPage(response.responseBody()) ?: error("Music client unavailable")
        musicVersion = now to value
        value
    }

    suspend fun fetchMusicHome(): List<YouTubeEntry> = withContext(Dispatchers.IO) {
        initialize()
        val account = YouTubeAccountManager.getCookie(context)
        val payload = JSONObject().put("browseId", "FEmusic_home").put("context", JSONObject().put("client",
            JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", musicClientVersion())
                .put("hl", Locale.getDefault().language).put("gl", Locale.getDefault().country.ifBlank { "GR" })))
        val response = NewPipe.getDownloader().execute(Request("POST", "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",
            mapOf("Content-Type" to listOf("application/json"), "Origin" to listOf("https://music.youtube.com")),
            payload.toString().toByteArray(), null, false))
        require(response.responseCode() in 200..299) { "Music home unavailable" }
        if (YouTubeAccountManager.getCookie(context) != account) return@withContext emptyList()
        homeVideoEntries(response.responseBody())
    }

    private fun initialize() = synchronized(INIT_LOCK) {
        if (!initialized) {
            val locale = Locale.getDefault()
            NewPipe.init(
                NewPipeDownloader(context),
                Localization(locale.language, locale.country),
                ContentCountry(locale.country.ifBlank { "GR" })
            )
            initialized = true
        }
    }

    fun isLoggedIn(): Boolean {
        val cookie = YouTubeAccountManager.getCookie(context)
        return cookie != null && YouTubeSessionPolicy.sapisid(cookie) != null
    }

    suspend fun artistRadio(entry: YouTubeEntry): List<YouTubeEntry> = withContext(Dispatchers.IO) {
        val id = when (val link = parseYouTubeLink(entry.url)) {
            is YouTubeLink.Video -> link.id
            is YouTubeLink.Short -> link.id
            else -> error("A video seed is required")
        }
        val tracks = fetchPlaylist("https://www.youtube.com/watch?v=$id&list=RDAMVM$id").second.distinctBy { it.url }
        require(tracks.isNotEmpty()) { "Empty artist radio" }
        tracks
    }

    suspend fun genreRadio(genre: String): List<YouTubeEntry> = withContext(Dispatchers.IO) {
        search("$genre music radio", true)
    }

    /** Fetches tracks inside any YouTube / YT Music playlist URL (including private ones when logged in). */
    suspend fun fetchPlaylist(url: String): Pair<String, List<YouTubeEntry>> = withContext(Dispatchers.IO) {
        initialize()
        val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
        val name: String = info.name
        val entries: List<YouTubeEntry> = info.relatedItems.filterIsInstance<StreamInfoItem>().map {
            YouTubeEntry(it.url, it.name, it.uploaderName.orEmpty(), it.thumbnails.lastOrNull()?.url, it.duration)
        }
        Pair(name, entries)
    }

    suspend fun fetchHomeFeed(): List<YouTubeEntry> = withContext(Dispatchers.IO) {
        initialize()
        val account = YouTubeAccountManager.getCookie(context)
        val page = NewPipe.getDownloader().execute(Request("GET", "https://www.youtube.com/",
            mapOf("Accept-Language" to listOf(Locale.getDefault().toLanguageTag())), null, null, false))
        require(page.responseCode() in 200..299) { "Home feed unavailable" }
        if (YouTubeAccountManager.getCookie(context) != account) return@withContext emptyList()
        val data = initialYouTubeData(page.responseBody()) ?: error("Home data unavailable")
        val entries = homeVideoEntries(data)
        if (entries.isNotEmpty()) entries else {
            // A history-disabled account may legitimately have no recommendations.
            org.schabi.newpipe.extractor.kiosk.KioskInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/feed/trending")
                .relatedItems.filterIsInstance<StreamInfoItem>().map {
                    YouTubeEntry(it.url, it.name, it.uploaderName.orEmpty(), it.thumbnails.lastOrNull()?.url, it.duration)
                }
        }
    }

    /** Shortcut to fetch the logged-in user's "Liked Music" (LM) playlist. */
    suspend fun fetchLikedMusic(): Pair<String, List<YouTubeEntry>> {
        val lmUrl = "https://" + "www.youtube.com/playlist?list=LM"
        return fetchPlaylist(lmUrl)
    }

    /** Shortcut to fetch the logged-in user's "Liked Videos" (LL) playlist. */
    suspend fun fetchLikedVideos(): Pair<String, List<YouTubeEntry>> {
        val llUrl = "https://" + "www.youtube.com/playlist?list=LL"
        return fetchPlaylist(llUrl)
    }

    /**
     * Fetches all personal & saved playlists from the user's YouTube Music Library (`FEmusic_liked_playlists`).
     * Always includes "Liked Music" (LM) and "Liked Videos" (LL) at the top when logged in.
     */
    suspend fun fetchUserPlaylists(): List<YouTubePlaylistSummary> = withContext(Dispatchers.IO) {
        initialize()
        if (!isLoggedIn()) return@withContext emptyList()

        val results = mutableListOf<YouTubePlaylistSummary>()
        val seenIds = HashSet<String>()

        // 1. Always provide quick access to Liked Music (LM) & Liked Videos (LL)
        val ytPlaylistPrefix = "https://" + "www.youtube.com/playlist?list="
        results.add(
            YouTubePlaylistSummary(
                id = "LM",
                url = ytPlaylistPrefix + "LM",
                title = "Liked Music",
                subtitle = "YouTube Music Auto-Playlist",
                thumbnail = null
            )
        )
        seenIds.add("LM")

        results.add(
            YouTubePlaylistSummary(
                id = "LL",
                url = ytPlaylistPrefix + "LL",
                title = "Liked Videos",
                subtitle = "YouTube Auto-Playlist",
                thumbnail = null
            )
        )
        seenIds.add("LL")

        // 2. Query YouTube Music Innertube API for the user's saved/created playlists
        val account = requireNotNull(YouTubeAccountManager.getCookie(context))
        val accountHash = java.security.MessageDigest.getInstance("SHA-256").digest(account.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val cacheFile = java.io.File(context.cacheDir, "user_playlists_cache-$accountHash.json")
        try {
            val endpoint = "https://" + "music.youtube.com/youtubei/v1/browse?prettyPrint=false"
            val locale = Locale.getDefault()
            val payload = JSONObject().apply {
                put("browseId", "FEmusic_liked_playlists")
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", musicClientVersion())
                        put("hl", locale.language.ifBlank { "el" })
                        put("gl", locale.country.ifBlank { "GR" })
                    })
                })
            }.toString().toByteArray(Charsets.UTF_8)

            val headers = mapOf(
                "Content-Type" to listOf("application/json"),
                "X-Goog-AuthUser" to listOf("0")
            )

            val request = Request("POST", endpoint, headers, payload, null, false)
            val response = NewPipe.getDownloader().execute(request)

            val body = response.responseBody()
            if (YouTubeAccountManager.getCookie(context) != account) return@withContext emptyList()
            if (YouTubeSessionPolicy.expired(response.responseCode(), body) || body.contains("UNAUTHENTICATED")) {
                YouTubeAccountManager.markExpired(context, true)
                return@withContext emptyList()
            } else if (response.responseCode() in 200..299 && body.isNotBlank()) {
                val root = JSONObject(body)
                runCatching {
                    val atomic = android.util.AtomicFile(cacheFile)
                    val stream = atomic.startWrite()
                    try { stream.write(body.toByteArray()); atomic.finishWrite(stream) }
                    catch (e: Exception) { atomic.failWrite(stream); throw e }
                }
                extractPlaylistsFromInnertubeJson(root, results, seenIds, ytPlaylistPrefix)
            } else if (cacheFile.exists() && cacheFile.length() <= 8 * 1024 * 1024) {
                val root = JSONObject(cacheFile.readText())
                extractPlaylistsFromInnertubeJson(root, results, seenIds, ytPlaylistPrefix)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) {
            if (cacheFile.exists() && cacheFile.length() <= 8 * 1024 * 1024) {
                runCatching {
                    val root = JSONObject(cacheFile.readText())
                    extractPlaylistsFromInnertubeJson(root, results, seenIds, ytPlaylistPrefix)
                }
            }
        }

        if (YouTubeAccountManager.getCookie(context) == account) results else emptyList()
    }

    private fun extractPlaylistsFromInnertubeJson(
        node: Any?,
        out: MutableList<YouTubePlaylistSummary>,
        seenIds: HashSet<String>,
        urlPrefix: String,
        depth: Int = 0
    ) {
        if (depth > 64 || out.size >= 1000) return
        when (node) {
            is JSONObject -> {
                if (node.has("musicTwoRowItemRenderer")) {
                    parseMusicTwoRowItem(node.getJSONObject("musicTwoRowItemRenderer"), out, seenIds, urlPrefix)
                } else if (node.has("musicResponsiveListItemRenderer")) {
                    parseResponsiveListItem(node.getJSONObject("musicResponsiveListItemRenderer"), out, seenIds, urlPrefix)
                } else {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        extractPlaylistsFromInnertubeJson(node.opt(key), out, seenIds, urlPrefix, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    extractPlaylistsFromInnertubeJson(node.opt(i), out, seenIds, urlPrefix, depth + 1)
                }
            }
        }
    }

    private fun parseMusicTwoRowItem(
        renderer: JSONObject,
        out: MutableList<YouTubePlaylistSummary>,
        seenIds: HashSet<String>,
        urlPrefix: String
    ) {
        val rawBrowseId = renderer
            .optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            .orEmpty()

        val playlistId = rawBrowseId.removePrefix("VL").trim()
        if (playlistId.isBlank() || playlistId == "FEmusic_new_playlist" || !seenIds.add(playlistId)) return

        val title = extractRunsText(renderer.optJSONObject("title"))
        if (title.isBlank()) return
        val subtitle = extractRunsText(renderer.optJSONObject("subtitle"))
        val thumbnail = extractLastThumbnailUrl(renderer.optJSONObject("thumbnailRenderer"))

        out.add(
            YouTubePlaylistSummary(
                id = playlistId,
                url = urlPrefix + playlistId,
                title = title,
                subtitle = subtitle,
                thumbnail = thumbnail
            )
        )
    }

    private fun parseResponsiveListItem(
        renderer: JSONObject,
        out: MutableList<YouTubePlaylistSummary>,
        seenIds: HashSet<String>,
        urlPrefix: String
    ) {
        val rawBrowseId = renderer
            .optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")
            ?.optString("browseId")
            .orEmpty()

        val playlistId = rawBrowseId.removePrefix("VL").trim()
        if (playlistId.isBlank() || playlistId == "FEmusic_new_playlist" || !seenIds.add(playlistId)) return

        val flexColumns = renderer.optJSONArray("flexColumns") ?: return
        val title = flexColumns.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let { extractRunsText(it) }
            .orEmpty()
        if (title.isBlank()) return

        val subtitle = flexColumns.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.let { extractRunsText(it) }
            .orEmpty()

        val thumbnail = extractLastThumbnailUrl(renderer.optJSONObject("thumbnail"))

        out.add(
            YouTubePlaylistSummary(
                id = playlistId,
                url = urlPrefix + playlistId,
                title = title,
                subtitle = subtitle,
                thumbnail = thumbnail
            )
        )
    }

    private fun extractRunsText(textObj: JSONObject?): String {
        val runs = textObj?.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun extractLastThumbnailUrl(thumbContainer: JSONObject?): String? {
        if (thumbContainer == null) return null
        val thumbnails = thumbContainer
            .optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: thumbContainer.optJSONArray("thumbnails")
            ?: return null
        if (thumbnails.length() == 0) return null
        val url = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
        return cleanThumbnailUrl(url)
    }

    suspend fun search(query: String, music: Boolean): List<YouTubeEntry> = withContext(Dispatchers.IO) {
        initialize()
        val link = parseYouTubeLink(query)
        val videoId = when (link) {
            is YouTubeLink.Video -> link.id
            is YouTubeLink.Short -> link.id
            else -> null
        }
        if (videoId != null) {
            val watchUrl = "https://" + "www.youtube.com/watch?v=$videoId"
            return@withContext listOf(entry(StreamInfo.getInfo(ServiceList.YouTube, watchUrl)))
        }
        if (link is YouTubeLink.Channel || link is YouTubeLink.ChannelHandle || link is YouTubeLink.LegacyChannel) {
            val channel = ChannelInfo.getInfo(ServiceList.YouTube, query)
            val videos = channel.tabs.firstOrNull { "videos" in it.contentFilters } ?: channel.tabs.firstOrNull()
                ?: return@withContext emptyList()
            return@withContext ChannelTabInfo.getInfo(ServiceList.YouTube, videos).relatedItems
                .filterIsInstance<StreamInfoItem>().map {
                    YouTubeEntry(it.url, it.name, it.uploaderName.orEmpty(), cleanThumbnailUrl(it.thumbnails.lastOrNull()?.url), it.duration)
                }
        }
        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery((link as? YouTubeLink.Search)?.query ?: query, if (music) listOf("music_songs") else listOf("videos"), "")
        SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<StreamInfoItem>().map {
            YouTubeEntry(it.url, it.name, it.uploaderName.orEmpty(), cleanThumbnailUrl(it.thumbnails.lastOrNull()?.url), it.duration)
        }
    }

    suspend fun related(item: YouTubeEntry): List<YouTubeEntry> = withContext(Dispatchers.IO) {
        initialize()
        StreamInfo.getInfo(ServiceList.YouTube, item.url).relatedItems
            .filterIsInstance<StreamInfoItem>().map {
                YouTubeEntry(it.url, it.name, it.uploaderName.orEmpty(), cleanThumbnailUrl(it.thumbnails.lastOrNull()?.url), it.duration)
            }.distinctBy { it.url }
    }

    suspend fun resolve(item: YouTubeEntry, audioOnly: Boolean): YouTubePlayback = withContext(Dispatchers.IO) {
        initialize()
        val info = StreamInfo.getInfo(ServiceList.YouTube, item.url)
        val audio = AudioStreamSelector.selectPreferredAudioStream(info.audioStreams.filter { it.isUrl }, "original")
        val selected = entry(info)
        if (audioOnly) {
            val url = audio?.content ?: info.hlsUrl.takeIf { !it.isNullOrBlank() }
                ?: throw IllegalStateException("No playable audio stream")
            return@withContext YouTubePlayback(selected, url, audioOnly = true)
        }
        val combined = info.videoStreams.filter { it.isUrl }.maxByOrNull { it.height }
        if (combined != null) return@withContext YouTubePlayback(selected, combined.content, audioOnly = false)
        val video = info.videoOnlyStreams.filter { it.isUrl && it.height <= 1080 }.maxByOrNull { it.height }
        if (video != null && audio != null) return@withContext YouTubePlayback(selected, video.content, audio.content, false)
        val hls = info.hlsUrl.takeIf { !it.isNullOrBlank() } ?: throw IllegalStateException("No playable video stream")
        YouTubePlayback(selected, hls, audioOnly = false)
    }

    private fun entry(info: StreamInfo) = YouTubeEntry(
        info.url,
        info.name,
        info.uploaderName.orEmpty(),
        cleanThumbnailUrl(info.thumbnails.lastOrNull()?.url),
        info.duration
    )

    private companion object {
        val INIT_LOCK = Any()
        var initialized = false
    }
}
