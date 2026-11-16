package de.mm20.launcher2.comms.stremio

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Addresses and answers of the add-on protocol. Pure functions, so that they can be tested without a network. */
object StremioProtocol {

    /**
     * Turns what the user typed or pasted into the base address of an add-on: `stremio://host/path/manifest.json`
     * and `https://host/path/manifest.json` both become `https://host/path`. Null if it is not a web address.
     */
    fun baseUrl(input: String): String? {
        var s = input.trim()
        if (s.startsWith("stremio://", true)) s = "https://" + s.substring("stremio://".length)
        if (!s.startsWith("https://", true) && !s.startsWith("http://", true)) return null
        s = s.substringBefore('#').substringBefore('?')
        s = s.removeSuffix("/manifest.json").trimEnd('/')
        return s.takeIf { it.substringAfter("://").isNotBlank() }
    }

    private fun seg(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%3A", ":")

    /** `/{resource}/{type}/{id}.json`, with `/{extra}` before `.json` when there are extra properties */
    fun resourceUrl(baseUrl: String, resource: String, type: String, id: String, extra: Map<String, String> = emptyMap()): String {
        val extras = extra.entries.joinToString("&") { "${seg(it.key)}=${seg(it.value)}" }
        return buildString {
            append(baseUrl.trimEnd('/')).append('/').append(resource).append('/').append(seg(type)).append('/').append(seg(id))
            if (extras.isNotEmpty()) append('/').append(extras)
            append(".json")
        }
    }

    fun manifestUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/manifest.json"

    /** `stremio:///detail/{type}/{id}`, `stremio://detail/{type}/{id}`, or an add-on `manifest.json` URL. */
    fun parseDeepLink(input: String): StremioDeepLink? {
        val raw = input.trim()
        if (raw.isEmpty()) return null
        val afterScheme = when {
            raw.startsWith("stremio://", true) -> raw.substring("stremio://".length)
            else -> null
        }
        if (afterScheme != null) {
            val segs = afterScheme.trimStart('/').substringBefore('?').substringBefore('#').split('/')
            if (segs.size >= 3 && segs[0].equals("detail", ignoreCase = true)) {
                val type = segs[1]
                val id = segs.drop(2).joinToString("/")
                if (type.isNotBlank() && id.isNotBlank()) return StremioDeepLink.Detail(type, id)
                return null
            }
        }
        return if (baseUrl(raw) != null) StremioDeepLink.AddonInstall(raw) else null
    }

    private fun JSONObject.str(key: String): String? = optString(key, "").takeIf { it.isNotBlank() && it != "null" }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it, "").takeIf { s -> s.isNotBlank() } }

    private inline fun <T> JSONArray?.objects(map: (JSONObject) -> T?): List<T> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(map) }

    fun parseManifest(json: String): AddonManifest? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val id = o.str("id") ?: return null
        val name = o.str("name") ?: return null
        val resources = (o.optJSONArray("resources")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                when (val item = arr.opt(i)) {
                    is String -> AddonResource(item)
                    is JSONObject -> item.str("name")?.let { AddonResource(it, item.optJSONArray("types").strings(), item.optJSONArray("idPrefixes").strings()) }
                    else -> null
                }
            }
        }).orEmpty()
        val catalogs = o.optJSONArray("catalogs").objects { c ->
            val type = c.str("type") ?: return@objects null
            val cid = c.str("id") ?: return@objects null
            val extras = c.optJSONArray("extra").objects { e ->
                e.str("name")?.let { CatalogExtra(it, e.optBoolean("isRequired", false), e.optJSONArray("options").strings()) }
            }
            AddonCatalog(type, cid, c.str("name") ?: cid, extras)
        }
        val addonCatalogs = o.optJSONArray("addonCatalogs").objects { c ->
            val type = c.str("type") ?: return@objects null
            val cid = c.str("id") ?: return@objects null
            AddonCatalog(type, cid, c.str("name") ?: cid)
        }
        val hints = o.optJSONObject("behaviorHints")
        return AddonManifest(
            id = id,
            name = name,
            description = o.optString("description", ""),
            version = o.optString("version", ""),
            logo = o.str("logo"),
            types = o.optJSONArray("types").strings(),
            idPrefixes = o.optJSONArray("idPrefixes").strings(),
            catalogs = catalogs,
            addonCatalogs = addonCatalogs,
            resources = resources,
            p2p = hints?.optBoolean("p2p", false) ?: false,
            adult = hints?.optBoolean("adult", false) ?: false,
            configurationRequired = hints?.optBoolean("configurationRequired", false) ?: false,
            configurationURL = hints?.str("configurationURL"),
        )
    }

    private fun directoryAddon(o: JSONObject): DirectoryAddon? {
        val manifestObject = o.optJSONObject("manifest") ?: return null
        val raw = manifestObject.toString()
        val manifest = parseManifest(raw) ?: return null
        val base = baseUrl(o.str("transportUrl") ?: return null) ?: return null
        return DirectoryAddon(base, manifest, raw)
    }

    /** The answer of an `addon_catalog` request: `{ "addons": [ { transportUrl, manifest } ] }` */
    fun parseAddonCatalog(json: String): List<DirectoryAddon> {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return o.optJSONArray("addons").objects(::directoryAddon).distinctBy { it.baseUrl }
    }

    /** A plain list of add-ons: either `[ {...} ]` or `{ "addons": [ {...} ] }` */
    fun parseAddonList(json: String): List<DirectoryAddon> {
        val arr = runCatching { JSONArray(json) }.getOrNull()
            ?: runCatching { JSONObject(json).optJSONArray("addons") }.getOrNull()
            ?: return emptyList()
        return arr.objects(::directoryAddon).distinctBy { it.baseUrl }
    }

    private fun previewOf(m: JSONObject): MetaPreview? {
        val id = m.str("id") ?: return null
        val name = m.str("name") ?: return null
        return MetaPreview(
            id = id,
            type = m.str("type") ?: "movie",
            name = name,
            poster = m.str("poster"),
            posterShape = m.str("posterShape") ?: "poster",
            releaseInfo = m.str("releaseInfo"),
            imdbRating = m.str("imdbRating"),
            description = m.str("description"),
        )
    }

    fun parseCatalog(json: String): List<MetaPreview> {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return o.optJSONArray("metas").objects(::previewOf).distinctBy { it.id }
    }

    fun parseMeta(json: String): Meta? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val m = root.optJSONObject("meta") ?: root.optJSONArray("meta")?.optJSONObject(0) ?: return null
        val id = m.str("id") ?: return null
        val name = m.str("name") ?: return null
        val videos = m.optJSONArray("videos").objects { v ->
            val vid = v.str("id") ?: return@objects null
            Video(
                id = vid,
                title = v.str("title") ?: v.str("name") ?: vid,
                released = v.str("released"),
                thumbnail = v.str("thumbnail"),
                season = if (v.has("season") && !v.isNull("season")) v.optInt("season") else null,
                episode = if (v.has("episode") && !v.isNull("episode")) v.optInt("episode") else null,
                overview = v.str("overview") ?: v.str("description"),
            )
        }
        return Meta(
            id = id,
            type = m.str("type") ?: "movie",
            name = name,
            poster = m.str("poster"),
            background = m.str("background"),
            logo = m.str("logo"),
            description = m.str("description"),
            releaseInfo = m.str("releaseInfo") ?: m.str("year"),
            imdbRating = m.str("imdbRating"),
            runtime = m.str("runtime"),
            genres = m.optJSONArray("genres").strings(),
            cast = m.optJSONArray("cast").strings(),
            director = m.optJSONArray("director").strings(),
            videos = videos,
            defaultVideoId = m.optJSONObject("behaviorHints")?.str("defaultVideoId"),
        )
    }

    fun parseStreams(json: String, addonName: String): List<StreamItem> {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return o.optJSONArray("streams").objects { s ->
            StreamItem(
                addonName = addonName,
                name = s.str("name"),
                // `title` is the older name of `description`
                description = s.str("description") ?: s.str("title"),
                url = s.str("url"),
                infoHash = s.str("infoHash"),
                fileIdx = if (s.has("fileIdx") && !s.isNull("fileIdx")) s.optInt("fileIdx") else null,
                ytId = s.str("ytId"),
                externalUrl = s.str("externalUrl"),
                sources = (s.optJSONArray("sources") ?: s.optJSONArray("announce")).strings(),
                fileMustInclude = s.optJSONArray("fileMustInclude").strings().take(32),
                subtitles = s.optJSONArray("subtitles").objects(::subtitleOf),
                filename = s.optJSONObject("behaviorHints")?.str("filename"),
                sizeBytes = s.optJSONObject("behaviorHints")?.optLong("videoSize", 0)?.takeIf { it > 0 },
            )
        }
    }

    private fun subtitleOf(s: JSONObject): SubtitleItem? {
        val url = s.str("url") ?: return null
        return SubtitleItem(s.str("id") ?: url, url, s.str("lang") ?: "und", s.str("label"))
    }

    fun parseSubtitles(json: String): List<SubtitleItem> {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return o.optJSONArray("subtitles").objects(::subtitleOf)
    }

    /** A magnet link for a torrent stream: the info hash, a name and the trackers the add-on named */
    fun magnet(stream: StreamItem, title: String): String? {
        val hash = stream.infoHash?.trim()?.takeIf { Regex("^[0-9a-fA-F]{40}$|^[A-Za-z2-7]{32}$").matches(it) } ?: return null
        return buildString {
            append("magnet:?xt=urn:btih:").append(hash)
            if (title.isNotBlank()) append("&dn=").append(seg(title))
            stream.sources.filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") }
                .filter { it.startsWith("udp://") || it.startsWith("http://") || it.startsWith("https://") }
                .take(20).forEach { append("&tr=").append(URLEncoder.encode(it, "UTF-8")) }
        }
    }
}
