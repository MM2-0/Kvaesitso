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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import com.vitorpamplona.quartz.nip11RelayInfo.relayInformation
import java.io.File

/**
 * Relay-side handle for the NIP-11 information document. Wraps the
 * client-side [Nip11RelayInformation] model and provides loaders for
 * config files plus a default doc that advertises the NIPs this relay
 * actually implements.
 */
data class RelayInfo(
    val document: Nip11RelayInformation,
) {
    /** Pre-rendered JSON, ready to write into the HTTP response body. */
    val json: String by lazy { JsonMapper.toJson(document) }

    companion object {
        const val NAME = "Geode"
        const val DESCRIPTION = "Nostr relay from Amethyst"
        const val SOFTWARE = "https://github.com/vitorpamplona/amethyst/tree/main/geode"
        const val VERSION = BuildConfig.VERSION

        /**
         * NIPs this relay implements out of the box. Single source of
         * truth — both [default] and [com.vitorpamplona.geode.config.StaticConfig.resolveInfo]
         * consult this list. Add a NIP here when its handler is wired
         * into [com.vitorpamplona.quartz.nip01Core.relay.server.RelaySession]
         * (or in this module's policy stack).
         *
         * Currently:
         *  -  1 NIP-01 basic
         *  -  9 NIP-09 deletion (DeletionRequestModule)
         *  - 11 NIP-11 this doc
         *  - 40 NIP-40 expiration (ExpirationModule)
         *  - 42 NIP-42 AUTH (when policy enables)
         *  - 45 NIP-45 COUNT
         *  - 50 NIP-50 search (SQLite FTS)
         *  - 62 NIP-62 right to vanish
         *  - 77 NIP-77 negentropy reconciliation
         *  - 86 NIP-86 relay management API (when admin pubkeys configured)
         *  - FE NIP-FE relay commands over HTTP (KtorRelay; `[http].enabled`)
         */
        val SUPPORTED_NIPS: List<String> =
            listOf("1", "9", "11", "40", "42", "45", "50", "62", "77", "86", "FE")

        /** Pre-built default for `RelayEngine(url = ...)` — advertises the supported NIPs. */
        fun default(url: NormalizedRelayUrl): RelayInfo =
            RelayInfo(
                relayInformation {
                    name = NAME
                    description = DESCRIPTION
                    software = SOFTWARE
                    version = VERSION
                    supports(*SUPPORTED_NIPS.toTypedArray())
                },
            )

        /** Loads a NIP-11 doc from a JSON file (e.g. a relay operator's config). */
        fun fromFile(file: File): RelayInfo = RelayInfo(Nip11RelayInformation.fromJson(file.readText()))

        /** Parses a NIP-11 doc from a raw JSON string. */
        fun fromJson(json: String): RelayInfo = RelayInfo(Nip11RelayInformation.fromJson(json))
    }
}
