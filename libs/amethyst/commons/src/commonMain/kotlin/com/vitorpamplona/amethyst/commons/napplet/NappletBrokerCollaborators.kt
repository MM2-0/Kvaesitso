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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

/**
 * Asks the user to decide a capability the napplet does not yet have a standing grant for.
 * The implementation drives the consent UI (on Android, an Activity in the **main** process)
 * and suspends until the user answers. Returning [GrantState.DENY] (or any non-allowing
 * state) blocks the in-flight request.
 */
fun interface NappletConsentPrompt {
    suspend fun request(
        identity: NappletIdentity,
        capability: NappletCapability,
        request: NappletRequest,
    ): GrantState
}

/**
 * Bridges the broker to the user's relays for the [NappletCapability.RELAY] capability —
 * publishing the user's events and reading events back. Supplied by the host (an
 * OkHttp/NostrClient-backed implementation in `amethyst`); kept as an interface so the broker
 * stays platform-agnostic and testable. A `null` gateway makes the broker answer relay
 * requests with [com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse.Unsupported].
 */
interface NappletRelayGateway {
    /** Publishes [event] and returns the relay URLs that accepted it (empty = nowhere reached). */
    suspend fun publish(event: Event): List<String>

    /** Returns events matching any of [filters] (e.g. from the local cache and/or a bounded relay fetch). */
    suspend fun query(filters: List<Filter>): List<Event>
}

/**
 * Bridges the broker to read-only account data for the [NappletCapability.IDENTITY] capability
 * beyond the public key — profile (`getProfile`), relays (`getRelays`), follows (`getFollows`),
 * mutes (`getMutes`), blocks (`getBlocked`), lists (`getList`, [argument] is the list type), zaps
 * (`getZaps`), and badges (`getBadges`). The host returns the datum as a JSON value string (the
 * literal `"null"` for an absent value), or `null` if this shell does not implement [method] (the
 * broker then answers `Unsupported`). A `null` gateway makes every such read `Unsupported`.
 */
fun interface NappletIdentityGateway {
    suspend fun read(
        method: String,
        argument: String?,
    ): String?
}

/**
 * A per-applet sandboxed key-value store for the [NappletCapability.STORAGE] capability. The
 * broker namespaces every call by the applet's coordinate, so one napplet can never read or
 * overwrite another's data — and none of it is the app's own storage.
 */
interface NappletStorage {
    suspend fun get(
        coordinate: String,
        key: String,
    ): String?

    suspend fun set(
        coordinate: String,
        key: String,
        value: String,
    )

    /**
     * [set], charged to the quota of [quotaCoordinate]: the app's shared namespace, which its instance
     * namespaces ([coordinate]) share one quota with. A store without quotas ignores it.
     */
    suspend fun set(
        coordinate: String,
        key: String,
        value: String,
        quotaCoordinate: String,
    ) = set(coordinate, key, value)

    suspend fun remove(
        coordinate: String,
        key: String,
    )

    /** Lists the keys this applet (identified by [coordinate]) has stored. */
    suspend fun keys(coordinate: String): List<String>
}

/**
 * Bridges the broker to the user's wallet for the [NappletCapability.VALUE] capability. A
 * `null` gateway makes value requests answer with `Unsupported` — there is intentionally no
 * default payment path, since a money-moving bridge must be verified end-to-end before it ships.
 */
fun interface NappletWalletGateway {
    /** Pays a BOLT-11 [invoice] and returns the preimage on success, or `null` if unconfirmed. */
    suspend fun payInvoice(invoice: String): String?
}

/** The host's current theme, as hex color strings the applet maps to CSS variables. */
data class NappletThemeColors(
    val background: String,
    val text: String,
    val primary: String,
)

/**
 * Bridges the broker to the host's current theme for [NappletCapability.THEME] (`theme.get`). A
 * `null` gateway answers `Unsupported`. Read-only and never prompts — it exposes only cosmetic colors.
 */
fun interface NappletThemeGateway {
    suspend fun current(): NappletThemeColors
}

/** A user-facing notification a napplet created, tracked per applet coordinate. */
class NappletNotification(
    val id: String,
    val title: String,
    val body: String,
)

/**
 * Bridges the broker to user-facing notifications for [NappletCapability.NOTIFY] (`notify.*`). The
 * host shows the notification and keeps a per-coordinate registry so a napplet can list and dismiss
 * its own (and only its own). A `null` gateway answers `Unsupported`.
 */
interface NappletNotifyGateway {
    /** Creates a notification for [coordinate]; returns the host-assigned id. */
    suspend fun create(
        coordinate: String,
        title: String,
        body: String,
    ): String

    /** Lists [coordinate]'s active notifications. */
    suspend fun list(coordinate: String): List<NappletNotification>

    /** Dismisses [coordinate]'s notification [id] (no-op if unknown). */
    suspend fun dismiss(
        coordinate: String,
        id: String,
    )
}

/** A fetched resource: its [bytes] and best-effort [contentType]. */
class NappletResource(
    val bytes: ByteArray,
    val contentType: String,
)

sealed interface NappletResourceResult {
    data class Success(
        val resource: NappletResource,
    ) : NappletResourceResult

    data class Failure(
        val error: String,
        val message: String? = null,
    ) : NappletResourceResult
}

/**
 * Bridges the broker to sandboxed resource fetching for [NappletCapability.RESOURCE]
 * (`resource.bytes`). The host fetches https/blossom/nostr/data URLs on the applet's behalf —
 * the applet itself has no direct network (CSP `connect-src 'none'`). Returns `null` for an
 * unsupported scheme or a failed fetch.
 *
 * [coordinate] is the calling applet's identity coordinate (`author:identifier`), so the host can
 * route the fetch the same way the applet's own page loads — through Tor or the open web — per that
 * applet's/site's network mode. Failures carry the stable NAP-RESOURCE error code rather than
 * collapsing policy rejections and network failures into one nullable result.
 */
fun interface NappletResourceGateway {
    suspend fun fetch(
        url: String,
        coordinate: String,
    ): NappletResourceResult
}

/** A completed upload: where the blob lives plus NIP-94-ish metadata. */
class NappletUploadResult(
    val url: String,
    val sha256: String? = null,
    val size: Long? = null,
    val mimeType: String? = null,
)

/**
 * Bridges the broker to Blossom upload for [NappletCapability.UPLOAD] (NAP-UPLOAD's `blossom` rail).
 * Returns the upload result, or `null` on failure. A `null` gateway answers with `Unsupported`.
 */
fun interface NappletUploadGateway {
    /**
     * The largest upload accepted, in bytes. The host's transport bounds it: on Android the bytes
     * cross a Binder transaction from the sandbox process, which holds far less than a desktop window.
     */
    val maxBytes: Long get() = DEFAULT_MAX_BYTES

    /** Whether a server to upload to is configured, for `upload.info` and to fail early. */
    suspend fun hasServer(): Boolean = true

    suspend fun upload(
        bytes: ByteArray,
        contentType: String,
        filename: String?,
    ): NappletUploadResult?

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 16L * 1024 * 1024
    }
}

/**
 * What NAP-COMMON needs of the account beyond the relays: its current kind 3 follow list, which a
 * follow or unfollow edits (keeping every other tag and the content). Null when none is known.
 */
fun interface NappletCommonGateway {
    fun followList(): ContactListEvent?
}
