package de.mm20.launcher2.comms.stremio

/** http(s) or magnet targets for Open with / Telos Downloads. Does not start a second torrent engine. */
object StremioStreamTargets {
    fun httpUrl(value: String?): String? =
        value?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }

    /** ACTION_VIEW target: web http(s) URL, or a magnet for torrents. */
    fun externalViewUri(stream: StreamItem, title: String): String? = when (stream.kind) {
        StreamItem.Kind.Web -> httpUrl(stream.url)
        StreamItem.Kind.Torrent -> StremioProtocol.magnet(stream, title)
        else -> null
    }

    /** Telos Downloads extra: http(s) file URL or magnet. */
    fun downloadUrl(stream: StreamItem, title: String): String? = when (stream.kind) {
        StreamItem.Kind.Web -> httpUrl(stream.url)
        StreamItem.Kind.Torrent -> StremioProtocol.magnet(stream, title)
        else -> null
    }
}
