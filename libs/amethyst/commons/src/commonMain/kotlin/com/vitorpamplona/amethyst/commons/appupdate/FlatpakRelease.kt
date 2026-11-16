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
package com.vitorpamplona.amethyst.commons.appupdate

/**
 * Amethyst's Flatpak as Orchard (https://github.com/Letdown2491/orchard) lists it: the
 * `amethyst-desktop-<version>-linux-<x64|arm64>.flatpak` bundles of the GitHub Release, named by
 * NIP-82 events signed with Amethyst's key. Orchard (0.2.0 and later) downloads a bundle from the
 * asset's URL and installs it only if it hashes to the asset's `x`.
 */
object FlatpakRelease {
    /** The Flatpak app id, Orchard's listing id (the APK's and the desktop's ids are others). */
    const val APP_ID = "com.vitorpamplona.amethyst.Amethyst"

    /** The asset type Orchard installs (it skips the AppImages and APKs of other ids' releases). */
    const val MIME_TYPE = "application/vnd.flatpak"

    /** relay.orchard.rocks is the one Orchard reads first. */
    val RELAYS = listOf("wss://relay.orchard.rocks", "wss://nos.lol", "wss://relay.primal.net")

    /** The Flatpak architectures, as NIP-82 platforms. */
    val PLATFORMS = listOf("linux-x86_64", "linux-aarch64")

    // The listing, as Orchard shows it. Its `h` tag is the homepage in Orchard's reading.
    const val NAME = "Amethyst"
    const val SUMMARY = "Join the social network you control"
    const val ICON = "https://raw.githubusercontent.com/vitorpamplona/amethyst/main/desktopApp/packaging/flatpak/icons/512/com.vitorpamplona.amethyst.Amethyst.png"

    /** The same expression as the metainfo's project_license: the app plus the bundled JRE. */
    const val LICENSE = "MIT AND LGPL-2.1-or-later AND BSD-2-Clause AND Apache-2.0 AND GPL-2.0-only WITH Classpath-exception-2.0"
    const val REPOSITORY = "https://github.com/vitorpamplona/amethyst"
    const val HOMEPAGE = "https://amethyst.social"

    /** AppStream categories, the same as the metainfo's. */
    val CATEGORIES = listOf("Network", "InstantMessaging")

    val DESCRIPTION =
        """
        Amethyst is a client for Nostr, an open protocol for a censorship-resistant social network. There is no central server and no company account: your identity is a cryptographic key, and your posts are signed and sent to the relays you choose.

        With Amethyst you can:

        - Read your home feed and post notes, replies, reposts, reactions and quotes
        - Upload photos and videos to the media servers you choose
        - Send end-to-end encrypted direct messages and join group chats
        - Browse articles, pictures, videos, live streams, communities and more
        - Send and receive Bitcoin Lightning zaps
        - Pick and manage the relays that store and deliver your data

        Your private key stays in the system keyring. You can also log in with a remote signer (NIP-46) or with a public key in read-only mode.
        """.trimIndent()

    /** Reads the release bundles' names, `amethyst-desktop-<version>-linux-<x64|arm64>.flatpak`, as platforms. */
    fun platformOfBundle(
        filename: String,
        version: String,
    ): String? =
        when (filename) {
            "amethyst-desktop-$version-linux-x64.flatpak" -> "linux-x86_64"
            "amethyst-desktop-$version-linux-arm64.flatpak" -> "linux-aarch64"
            else -> null
        }

    /** The Flatpak arch of a NIP-82 platform: `linux-x86_64` to `x86_64`. */
    fun refArch(platform: String) = platform.removePrefix("linux-")

    /**
     * The deploy ref a bundle's header carries, `app/<id>/<arch>/<branch>`, read as Orchard reads it:
     * after the `flatpak\0` magic, from the first `app/` to the next NUL. [head] is the file's
     * first bytes (the header sits well inside 64 KiB). Null if it is not a Flatpak bundle.
     */
    fun bundleRef(head: ByteArray): String? {
        if (head.size < MAGIC.size || !MAGIC.indices.all { head[it] == MAGIC[it] }) return null
        val start = head.indexOf(APP_PREFIX).takeIf { it >= 0 } ?: return null
        val end = (start until head.size).firstOrNull { head[it] == 0.toByte() } ?: return null
        return head.decodeToString(start, end)
    }

    private val MAGIC = "flatpak\u0000".encodeToByteArray()
    private val APP_PREFIX = "app/".encodeToByteArray()

    private fun ByteArray.indexOf(needle: ByteArray): Int = (0..size - needle.size).firstOrNull { start -> needle.indices.all { this[start + it] == needle[it] } } ?: -1
}

/** One release bundle, hashed and ready to be described by a signed asset event. */
data class HashedFlatpakBundle(
    val filename: String,
    val platform: String,
    val sha256: String,
    val sizeInBytes: Long,
    val url: String,
)
