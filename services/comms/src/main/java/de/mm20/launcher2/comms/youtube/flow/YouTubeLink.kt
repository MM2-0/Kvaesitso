// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2025-2026 A-EDev (Flow). Adapted for Telos Launcher.
// Source: Flow-Tube/Flow @ 4c93b64790c3d89eed57ba96abc997bb955c05f2
package de.mm20.launcher2.comms.youtube.flow

import java.net.URI
import java.net.URLDecoder

/** What a YouTube, YouTube Music or front-end link points at. */
sealed interface YouTubeLink {
    /** [playlistId] is the list a `watch?v=…&list=…` link plays the video from. */
    data class Video(
        val id: String,
        val isMusic: Boolean,
        val playlistId: String? = null,
    ) : YouTubeLink

    data class Short(
        val id: String,
    ) : YouTubeLink

    data class Playlist(
        val id: String,
        val isMusic: Boolean,
    ) : YouTubeLink

    data class Album(
        val browseId: String,
    ) : YouTubeLink

    data class Channel(
        val id: String,
        val isMusic: Boolean,
    ) : YouTubeLink

    /** An `@handle`, kept with its `@`. */
    data class ChannelHandle(
        val handle: String,
    ) : YouTubeLink

    /** A `/c/name`, `/user/name` or bare `/name` channel link; [kind] is `c`, `user` or empty. */
    data class LegacyChannel(
        val kind: String,
        val name: String,
    ) : YouTubeLink {
        val url: String get() = if (kind.isEmpty()) "https://www.youtube.com/$name" else "https://www.youtube.com/$kind/$name"
    }

    data class Search(
        val query: String,
    ) : YouTubeLink
}

private val URL_IN_TEXT = Regex("""(?i)\bhttps?://\S+""")
private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")
private val CHANNEL_ID = Regex("""UC[A-Za-z0-9_-]{22}""")
private val PLAYLIST_ID = Regex("""[A-Za-z0-9_-]{2,}""")
private val ALBUM_ID = Regex("""MPREb_[A-Za-z0-9_-]+""")
private val HANDLE = Regex("""@[\p{L}\p{N}._-]{3,}""")
private val CHANNEL_NAME = Regex("""[\p{L}\p{N}._-]+""")
private const val TRAILING_PUNCTUATION = ".,;:!?)]}>\"'"

private val YOUTUBE_DOMAINS = listOf("youtube.com", "youtube-nocookie.com")
private val FRONT_END_DOMAINS = listOf("piped.video", "yewtu.be")
private val MUSIC_HOSTS = setOf("music.youtube.com", "m.music.youtube.com")
private val VIDEO_PATHS = setOf("live", "embed", "v", "e")
private val CHANNEL_TABS = setOf("featured", "videos", "shorts", "streams", "playlists", "community", "posts", "about")

// Top-level youtube.com pages a bare `/name` channel link can never be, including every path
// pathLink reads, so a malformed watch or shorts link is not mistaken for a channel.
private val RESERVED_PATHS =
    VIDEO_PATHS +
        setOf("watch", "playlist", "shorts", "channel", "browse", "c", "user", "results") +
        setOf(
            "about",
            "account",
            "ads",
            "attribution_link",
            "clip",
            "creators",
            "feed",
            "gaming",
            "hashtag",
            "howyoutubeworks",
            "iframe_api",
            "jobs",
            "kids",
            "live_chat",
            "logout",
            "music",
            "new",
            "oembed",
            "post",
            "premium",
            "redirect",
            "reporthistory",
            "s",
            "signin",
            "sitemap.xml",
            "robots.txt",
            "favicon.ico",
            "studio",
            "t",
            "upload",
            "trending",
        )

/**
 * The first YouTube link in [text], which may be a bare link or a share message around one.
 * Anything the parser does not recognise is null: an unknown path is never guessed to be a video.
 */
fun parseYouTubeLink(text: String): YouTubeLink? {
    val uri = firstUri(text.trim()) ?: return null
    val host = uri.host?.lowercase() ?: return null
    val segments =
        uri.path
            .orEmpty()
            .split('/')
            .filter(String::isNotEmpty)
    return when {
        host.isOrIsUnder("youtu.be") -> {
            segments.firstOrNull()?.takeIf(VIDEO_ID::matches)?.let { YouTubeLink.Video(it, isMusic = false) }
        }

        YOUTUBE_DOMAINS.any(host::isOrIsUnder) -> {
            val isMusic = host in MUSIC_HOSTS
            pathLink(segments, queryOf(uri), isMusic) ?: customNameChannel(segments).takeUnless { isMusic }
        }

        FRONT_END_DOMAINS.any(host::isOrIsUnder) -> {
            pathLink(segments, queryOf(uri), isMusic = false)
        }

        else -> {
            null
        }
    }
}

private fun firstUri(text: String): URI? {
    val candidate =
        URL_IN_TEXT.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }
            ?: text.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }?.let { "https://$it" }
            ?: return null
    return runCatching { URI(candidate) }.getOrNull()
}

private fun String.isOrIsUnder(domain: String): Boolean = this == domain || endsWith(".$domain")

private fun queryOf(uri: URI): Map<String, String> =
    uri.rawQuery
        .orEmpty()
        .split('&')
        .mapNotNull { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            if (key.isEmpty()) null else key to runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
        }.toMap()

private fun pathLink(
    segments: List<String>,
    query: Map<String, String>,
    isMusic: Boolean,
): YouTubeLink? {
    val first = segments.firstOrNull() ?: return null
    val second = segments.getOrNull(1)
    return when {
        first == "watch" -> {
            val list = query["list"]?.takeIf(PLAYLIST_ID::matches)
            query["v"]?.takeIf(VIDEO_ID::matches)?.let { YouTubeLink.Video(it, isMusic, list) }
                ?: list?.let { playlist(it, isMusic) }
        }

        first == "playlist" -> {
            query["list"]?.let { playlist(it, isMusic) }
        }

        first == "shorts" -> {
            second?.takeIf(VIDEO_ID::matches)?.let(YouTubeLink::Short)
        }

        first in VIDEO_PATHS -> {
            second?.takeIf(VIDEO_ID::matches)?.let { YouTubeLink.Video(it, isMusic) }
        }

        first == "channel" -> {
            second?.takeIf(CHANNEL_ID::matches)?.let { YouTubeLink.Channel(it, isMusic) }
        }

        first == "browse" -> {
            second?.let { youTubeBrowseLink(it, isMusic) }
        }

        first == "c" || first == "user" -> {
            second?.takeIf(CHANNEL_NAME::matches)?.let { YouTubeLink.LegacyChannel(first, it) }
        }

        first.startsWith("@") -> {
            first.takeIf(HANDLE::matches)?.let(YouTubeLink::ChannelHandle)
        }

        first == "search" && isMusic -> {
            query["q"]?.trim()?.takeIf(String::isNotEmpty)?.let(YouTubeLink::Search)
        }

        first == "results" -> {
            query["search_query"]?.trim()?.takeIf(String::isNotEmpty)?.let(YouTubeLink::Search)
        }

        else -> {
            null
        }
    }
}

/** A channel's old custom URL with no `/c/` (`youtube.com/officialpsy`), optionally on one of its tabs. */
private fun customNameChannel(segments: List<String>): YouTubeLink? {
    val name = segments.firstOrNull() ?: return null
    if (segments.size > 2 || (segments.size == 2 && segments[1] !in CHANNEL_TABS)) return null
    if (name.lowercase() in RESERVED_PATHS || !CHANNEL_NAME.matches(name)) return null
    return YouTubeLink.LegacyChannel(kind = "", name = name)
}

/** What an InnerTube browse id opens: an album, a channel or a playlist. Null for any other page. */
fun youTubeBrowseLink(
    browseId: String,
    isMusic: Boolean = false,
): YouTubeLink? =
    when {
        ALBUM_ID.matches(browseId) -> YouTubeLink.Album(browseId)
        CHANNEL_ID.matches(browseId) -> YouTubeLink.Channel(browseId, isMusic)
        browseId.startsWith("VL") -> playlist(browseId.removePrefix("VL"), isMusic)
        else -> null
    }

/** Album playlists (`OLAK5uy_`) are music wherever they are linked from. */
private fun playlist(
    id: String,
    isMusic: Boolean,
): YouTubeLink? = id.takeIf(PLAYLIST_ID::matches)?.let { YouTubeLink.Playlist(it, isMusic || it.startsWith("OLAK5uy_")) }
