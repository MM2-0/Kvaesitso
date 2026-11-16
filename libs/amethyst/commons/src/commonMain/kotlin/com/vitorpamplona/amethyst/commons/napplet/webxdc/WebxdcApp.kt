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
package com.vitorpamplona.amethyst.commons.napplet.webxdc

import androidx.compose.runtime.Immutable
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * A webxdc app (a `.xdc` zip of a sandboxed HTML5 app) attached to a post, as the NIP-DC draft
 * shares one: a URL in a kind-1 note with an `imeta`, or a kind-1063 file event, typed
 * `application/x-webxdc`. Everything here comes off the post's own tags; the app's real name and
 * icon are in its `manifest.toml`, read once it is downloaded.
 */
@Immutable
class WebxdcApp(
    val url: String,
    /** The archive's sha256, from `x` or a Blossom-style URL; null when the post gives none, and it runs unverified. */
    val hash: HexKey?,
    /** NIP-DC's `webxdc` id: posts that share it share the app's state. Null when the post has none. */
    val webxdcId: String?,
    /** What the post calls it (`summary`, else `alt`), before the manifest is read. */
    val title: String?,
    /** A picture the post gives for it. */
    val image: String?,
    val sizeBytes: Long?,
) {
    /**
     * The app instance [postId] runs: posts that share a `webxdc` id are one app with one state, as
     * NIP-DC means them to be; without one, each post is its own app (webxdc's "every message is a
     * separate app").
     */
    fun instance(postId: HexKey): String = webxdcId?.takeIf { it.isNotBlank() } ?: postId

    /** One run of [poster]'s app in post [postId], as the feed tracks it: its inline stage and poster frame. */
    fun stageKey(
        poster: HexKey,
        postId: HexKey,
    ): String = "webxdc:$poster:${instance(postId)}"

    companion object {
        const val MIME_TYPE = "application/x-webxdc"
        const val EXTENSION = ".xdc"

        /** What the draft's clients put before the name in `alt`. */
        private const val ALT_PREFIX = "Webxdc app:"

        /** Whether a file at [url], typed [mimeType], is a webxdc app: its declared type, else its extension. */
        fun isWebxdc(
            url: String,
            mimeType: String?,
        ): Boolean {
            val declared = mimeType?.substringBefore(';')?.trim()
            if (!declared.isNullOrEmpty()) return declared.equals(MIME_TYPE, ignoreCase = true)
            return pathOf(url).endsWith(EXTENSION, ignoreCase = true)
        }

        /**
         * The app at [url] as its tags describe it: a NIP-92 `imeta`'s properties or a NIP-94 event's
         * tags, by name ([property] returns the first value). Null when it isn't a webxdc app.
         */
        fun from(
            url: String,
            property: (String) -> String?,
        ): WebxdcApp? {
            if (!isWebxdc(url, property("m"))) return null
            val declaredHash = property("x")?.lowercase()?.takeIf(::isHash)
            return WebxdcApp(
                url = url,
                hash = declaredHash ?: hashFromUrl(url),
                webxdcId = property("webxdc")?.trim()?.ifEmpty { null },
                title = property("summary")?.trim()?.ifEmpty { null } ?: property("alt")?.let(::nameFromAlt),
                image = property("image")?.trim()?.ifEmpty { null },
                sizeBytes = property("size")?.toLongOrNull()?.takeIf { it > 0 },
            )
        }

        /** The sha256 a Blossom URL names its file by (`…/<sha256>[.ext]`), or null. */
        fun hashFromUrl(url: String): HexKey? =
            pathOf(url)
                .substringAfterLast('/')
                .substringBefore('.')
                .lowercase()
                .takeIf(::isHash)

        /**
         * The `selfAddr` [pubKey] has in instance [instance] of [poster]'s app: the same every time and
         * for everyone who computes it, and a different one in every app, so two apps can't match their
         * players by it. It is not a secret pseudonym: there is no secret in it, so anyone who knows
         * [poster] and [instance] (the app's author does) can hash every pubkey they know and find whose
         * it is. It is public by design, since other members compute it from an update's author.
         */
        fun selfAddr(
            pubKey: HexKey,
            poster: HexKey,
            instance: String,
        ): String = sha256("$ADDR_SEED$poster:$instance:$pubKey".encodeToByteArray()).toHexKey().take(ADDR_LENGTH)

        private const val ADDR_SEED = "webxdc-addr:"
        private const val ADDR_LENGTH = 40

        private fun nameFromAlt(alt: String): String? =
            alt
                .trim()
                .removePrefix(ALT_PREFIX)
                .trim()
                .ifEmpty { null }

        private fun pathOf(url: String): String = url.substringBefore('#').substringBefore('?')

        private fun isHash(value: String): Boolean = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
