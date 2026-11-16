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
package com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinFallbackPolicy
import kotlinx.serialization.Serializable

/**
 * Immutable data class representing the current Namecoin resolution config.
 *
 * Two backends are available:
 *
 * - **ElectrumX** (default): zero-config — public servers handle lookups
 *   unless the user adds custom entries via [customServers]. When custom
 *   servers are configured, they are used EXCLUSIVELY (defaults ignored)
 *   unless [fallbackToDefaultElectrumx] is enabled.
 *
 * - **Namecoin Core RPC**: queries a user-supplied Namecoin Core full
 *   node directly. Most sovereign option. [namecoinCoreRpc] holds the
 *   URL / credentials. Fallback to ElectrumX is opt-in via
 *   [fallbackToCustomElectrumx] / [fallbackToDefaultElectrumx].
 *
 * Lives in `commons` so both Android and Desktop persistence layers can
 * share the same schema. Each platform brings its own storage adapter
 * (DataStore on Android, [java.util.prefs.Preferences] on Desktop) and
 * its own pinned-cert store, but the round-tripped data class is the
 * same on both.
 */
@Serializable
@Stable
data class NamecoinSettings(
    /** Whether Namecoin resolution is enabled at all. */
    val enabled: Boolean = true,
    /**
     * Custom ElectrumX servers. Each entry is `host:port` (TLS) or
     * `host:port:tcp` (plaintext). When non-empty, these replace the
     * defaults *for the ElectrumX backend*.
     */
    val customServers: List<String> = emptyList(),
    /** Which backend is the *primary* lookup path. */
    val backend: NamecoinBackend = NamecoinBackend.ELECTRUMX,
    /** Namecoin Core RPC connection details (only meaningful when backend == NAMECOIN_CORE_RPC). */
    val namecoinCoreRpc: NamecoinCoreRpcConfig = NamecoinCoreRpcConfig(),
    /**
     * If the primary backend fails (Core RPC unreachable, or custom
     * ElectrumX servers all dead), fall back to the user's custom
     * ElectrumX servers.
     *
     * Only meaningful when backend == NAMECOIN_CORE_RPC. Ignored when the
     * ElectrumX backend is primary (because custom servers ARE the
     * primary in that case).
     */
    val fallbackToCustomElectrumx: Boolean = false,
    /**
     * If everything above fails, try the hardcoded public ElectrumX
     * defaults. Applies to both backends: when backend == ELECTRUMX with
     * custom servers configured, enabling this widens the search to the
     * defaults too instead of stopping after the custom list.
     */
    val fallbackToDefaultElectrumx: Boolean = false,
) {
    /** True when the user has configured at least one custom ElectrumX server. */
    val hasCustomServers: Boolean get() = customServers.isNotEmpty()

    /** True when Namecoin Core RPC settings are filled in enough to use. */
    val hasUsableCoreRpc: Boolean get() = namecoinCoreRpc.isUsable

    /**
     * Convert custom ElectrumX entries into [ElectrumxServer] instances.
     * Returns `null` when none are valid (resolver should fall back to
     * defaults, subject to the configured policy).
     */
    fun toElectrumxServers(): List<ElectrumxServer>? {
        if (customServers.isEmpty()) return null
        return customServers
            .mapNotNull { parseServerString(it) }
            .ifEmpty { null }
    }

    /** Translate the fallback toggles to the quartz [NamecoinFallbackPolicy]. */
    fun toFallbackPolicy(): NamecoinFallbackPolicy =
        NamecoinFallbackPolicy(
            fallbackToCustomElectrumx = fallbackToCustomElectrumx,
            fallbackToDefaultElectrumx = fallbackToDefaultElectrumx,
        )

    companion object {
        val DEFAULT = NamecoinSettings()

        /**
         * Parse `host:port` or `host:port:tcp` into an [ElectrumxServer].
         *
         * TLS is the default protocol. Append `:tcp` for plaintext
         * (useful for `.onion` addresses and local servers).
         *
         * All custom servers get `usePinnedTrustStore = true` — the
         * ElectrumXClient layer then decides whether to require a pinned
         * cert (TLS) or skip cert checks entirely (onion / plaintext).
         */
        fun parseServerString(s: String): ElectrumxServer? {
            val parts = s.trim().split(":")
            if (parts.size < 2) return null
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: return null
            if (host.isEmpty() || port <= 0 || port > 65535) return null
            val useSsl = parts.getOrNull(2)?.trim()?.lowercase() != "tcp"
            return ElectrumxServer(
                host = host,
                port = port,
                useSsl = useSsl,
                usePinnedTrustStore = true,
            )
        }

        /** Format an [ElectrumxServer] back to the `host:port[:tcp]` string form. */
        fun formatServerString(server: ElectrumxServer): String {
            val base = "${server.host}:${server.port}"
            return if (server.useSsl) base else "$base:tcp"
        }
    }
}
