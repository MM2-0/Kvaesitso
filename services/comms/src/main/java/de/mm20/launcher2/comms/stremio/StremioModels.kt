package de.mm20.launcher2.comms.stremio

/*
 * Telos' own client for the open Stremio add-on protocol (https://github.com/Stremio/stremio-addon-sdk,
 * docs/protocol.md, MIT). Only the public protocol description was used; no code of the Stremio apps or
 * of other clients is part of this. Telos is not affiliated with Stremio.
 */

/** One extra property of a catalog (`search`, `genre`, `skip`, ...) */
/** Result of [StremioProtocol.parseDeepLink]. */
sealed class StremioDeepLink {
    data class AddonInstall(val url: String) : StremioDeepLink()
    data class Detail(val type: String, val id: String) : StremioDeepLink()
}

data class CatalogExtra(val name: String, val isRequired: Boolean = false, val options: List<String> = emptyList())

data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extras: List<CatalogExtra> = emptyList(),
) {
    /** True when the catalog needs a property to answer at all (then it cannot be shown as a plain row) */
    val needsExtra: Boolean get() = extras.any { it.isRequired }
    val searchable: Boolean get() = extras.any { it.name == "search" }
}

/** A resource an add-on answers (`catalog`, `meta`, `stream`, `subtitles`), possibly limited to types and id prefixes */
data class AddonResource(val name: String, val types: List<String> = emptyList(), val idPrefixes: List<String> = emptyList())

data class AddonManifest(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "",
    val logo: String? = null,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String> = emptyList(),
    val catalogs: List<AddonCatalog> = emptyList(),
    /** Catalogs of other add-ons (a directory), see the `addon_catalog` resource */
    val addonCatalogs: List<AddonCatalog> = emptyList(),
    val resources: List<AddonResource> = emptyList(),
    /** The add-on says it streams with BitTorrent (this reveals the IP address to other peers) */
    val p2p: Boolean = false,
    val adult: Boolean = false,
    val configurationRequired: Boolean = false,
    /** HTTPS page the add-on uses to let the user configure it (`behaviorHints.configurationURL`). */
    val configurationURL: String? = null,
) {
    /** True when the add-on answers [resource] for content of [type] with an id that starts with one of its prefixes */
    fun supports(resource: String, type: String, id: String): Boolean {
        val r = resources.firstOrNull { it.name == resource } ?: return false
        val types = r.types.ifEmpty { this.types }
        if (types.isNotEmpty() && type !in types) return false
        val prefixes = r.idPrefixes.ifEmpty { idPrefixes }
        return prefixes.isEmpty() || prefixes.any { id.startsWith(it) }
    }

    fun provides(resource: String): Boolean = resources.any { it.name == resource }
}

/**
 * An installed add-on: where it lives (the address without `/manifest.json`) and what its manifest said.
 * [rawManifest] is the manifest exactly as the add-on sent it (needed to put the add-on into a Stremio account);
 * [official] and [protected] are the flags Stremio gives its own add-ons.
 */
data class Addon(
    val baseUrl: String,
    val manifest: AddonManifest,
    val rawManifest: String = "",
    val official: Boolean = false,
    val protected: Boolean = false,
) {
    val name: String get() = manifest.name
}

/** An item of a catalog (what the poster rows show) */
data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    /** `poster` (default), `square` or `landscape` */
    val posterShape: String = "poster",
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val description: String? = null,
)

data class Video(
    val id: String,
    val title: String,
    val released: String? = null,
    val thumbnail: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
)

/** The detail page of an item */
data class Meta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: List<String> = emptyList(),
    val videos: List<Video> = emptyList(),
    val defaultVideoId: String? = null,
) {
    val isSeries: Boolean get() = videos.any { it.season != null || it.episode != null }
}

data class SubtitleItem(val id: String, val url: String, val lang: String, val label: String? = null)

/** One way to watch something, as offered by a stream add-on */
data class StreamItem(
    val addonName: String,
    val name: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val ytId: String? = null,
    val externalUrl: String? = null,
    /** Tracker and DHT hints that belong to [infoHash] (`tracker:udp://...`, `dht:...`) */
    val sources: List<String> = emptyList(),
    val subtitles: List<SubtitleItem> = emptyList(),
    val filename: String? = null,
    val sizeBytes: Long? = null,
    val fileMustInclude: List<String> = emptyList(),
) {
    /** What Telos can do with it; other kinds (Usenet, archives) are not supported */
    val kind: Kind
        get() = when {
            !url.isNullOrBlank() && (url.startsWith("http://", true) || url.startsWith("https://", true)) -> Kind.Web
            !infoHash.isNullOrBlank() -> Kind.Torrent
            !ytId.isNullOrBlank() -> Kind.YouTube
            !externalUrl.isNullOrBlank() && (externalUrl.startsWith("http://", true) || externalUrl.startsWith("https://", true)) -> Kind.External
            else -> Kind.Unsupported
        }

    enum class Kind { Web, Torrent, YouTube, External, Unsupported }
}

/** An add-on that a directory lists: its address (of the manifest) and its manifest */
data class DirectoryAddon(val baseUrl: String, val manifest: AddonManifest, val rawManifest: String)
