/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.model.mediaServers

import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.serialization.Serializable

@Serializable
data class ServerName(
    val name: String,
    val baseUrl: String,
    val type: ServerType = ServerType.Blossom,
)

/**
 * The upload protocols this version speaks. NIP-95 (files embedded in events) is no longer
 * offered: a saved default of that type reads as unknown and falls back to the first default.
 */
enum class ServerType {
    Blossom,
    NIP96,

    /**
     * A Buzz workspace's own Blossom endpoint (`https://<workspace>/upload`). Blobs there are
     * private to the workspace and the relay only accepts `imeta` URLs under its `/media/`, so an
     * upload to it never falls back to, or mirrors onto, a public server.
     */
    BuzzWorkspace,
}

/** The media server of the Buzz workspace served at [relayUrl] (`wss://host` → `https://host`). */
fun buzzWorkspaceServer(relayUrl: String): ServerName {
    val host =
        relayUrl
            .substringAfter("://")
            .substringBefore("/")
    return ServerName(host, "https://$host", ServerType.BuzzWorkspace)
}

val DEFAULT_MEDIA_SERVERS: List<ServerName> =
    listOf(
        ServerName("Nostr.Build", "https://blossom.band/", ServerType.Blossom),
        ServerName("Nostrcheck.me", "https://cdn.nostrcheck.me", ServerType.Blossom),
        ServerName("24242.io", "https://24242.io/", ServerType.Blossom),
        ServerName("Azzamo", "https://blossom.azzamo.media", ServerType.Blossom),
        ServerName("YakiHonne", "https://blossom.yakihonne.com/", ServerType.Blossom),
        ServerName("Primal", "https://blossom.primal.net/", ServerType.Blossom),
        ServerName("Sovbit", "https://files.sovbit.host", ServerType.Blossom),
        ServerName("Nostr.Download", "https://nostr.download", ServerType.Blossom),
        ServerName("Satellite (Paid)", "https://cdn.satellite.earth", ServerType.Blossom),
        ServerName("NostrMedia (Paid)", "https://nostrmedia.com", ServerType.Blossom),
    )

/**
 * Recommended servers keyed by host, which is what a list row's `name` holds. Keeps default order.
 * Must stay below [DEFAULT_MEDIA_SERVERS]: top-level properties initialize in the order written.
 */
private val RECOMMENDED_BY_HOST: Map<String, ServerName> =
    DEFAULT_MEDIA_SERVERS.associateBy { Rfc3986.host(it.baseUrl) }

/** The recommended entry for [host], or null when the host is not one of the defaults. */
fun recommendedServer(host: String): ServerName? = RECOMMENDED_BY_HOST[host]

/** The defaults not yet in a list whose rows carry [addedHosts], in `DEFAULT_MEDIA_SERVERS` order. */
fun missingRecommendedServers(addedHosts: Set<String>): List<ServerName> = RECOMMENDED_BY_HOST.filterKeys { it !in addedHosts }.values.toList()
