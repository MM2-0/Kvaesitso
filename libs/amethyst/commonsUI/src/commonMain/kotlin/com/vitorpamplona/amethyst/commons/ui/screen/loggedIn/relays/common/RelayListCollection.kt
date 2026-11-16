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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.common

import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.blocked_section
import com.vitorpamplona.amethyst.commons.resources.blocked_section_explainer
import com.vitorpamplona.amethyst.commons.resources.broadcast_section
import com.vitorpamplona.amethyst.commons.resources.broadcast_section_explainer
import com.vitorpamplona.amethyst.commons.resources.favorite_section
import com.vitorpamplona.amethyst.commons.resources.favorite_section_explainer
import com.vitorpamplona.amethyst.commons.resources.indexer_section
import com.vitorpamplona.amethyst.commons.resources.indexer_section_explainer
import com.vitorpamplona.amethyst.commons.resources.local_section
import com.vitorpamplona.amethyst.commons.resources.local_section_explainer
import com.vitorpamplona.amethyst.commons.resources.private_inbox_section
import com.vitorpamplona.amethyst.commons.resources.private_inbox_section_explainer
import com.vitorpamplona.amethyst.commons.resources.private_outbox_section
import com.vitorpamplona.amethyst.commons.resources.private_outbox_section_explainer
import com.vitorpamplona.amethyst.commons.resources.proxy_section
import com.vitorpamplona.amethyst.commons.resources.proxy_section_explainer
import com.vitorpamplona.amethyst.commons.resources.public_home_section
import com.vitorpamplona.amethyst.commons.resources.public_home_section_explainer
import com.vitorpamplona.amethyst.commons.resources.public_notif_section
import com.vitorpamplona.amethyst.commons.resources.public_notif_section_explainer
import com.vitorpamplona.amethyst.commons.resources.search_section
import com.vitorpamplona.amethyst.commons.resources.search_section_explainer
import com.vitorpamplona.amethyst.commons.resources.trusted_section
import com.vitorpamplona.amethyst.commons.resources.trusted_section_explainer
import org.jetbrains.compose.resources.StringResource

data class RelayListCollection(
    val homeRelays: List<BasicRelaySetupInfo>,
    val notifRelays: List<BasicRelaySetupInfo>,
    val dmRelays: List<BasicRelaySetupInfo>,
    val privateOutboxRelays: List<BasicRelaySetupInfo>,
    val proxyRelays: List<BasicRelaySetupInfo>,
    val broadcastRelays: List<BasicRelaySetupInfo>,
    val indexerRelays: List<BasicRelaySetupInfo>,
    val searchRelays: List<BasicRelaySetupInfo>,
    val localRelays: List<BasicRelaySetupInfo>,
    val trustedRelays: List<BasicRelaySetupInfo>,
    val favoriteRelays: List<BasicRelaySetupInfo>,
    val blockedRelays: List<BasicRelaySetupInfo>,
) {
    fun sections(): List<RelaySection> =
        listOf(
            RelaySection("home", Res.string.public_home_section, Res.string.public_home_section_explainer, homeRelays),
            RelaySection("notifications", Res.string.public_notif_section, Res.string.public_notif_section_explainer, notifRelays),
            RelaySection("private_inbox", Res.string.private_inbox_section, Res.string.private_inbox_section_explainer, dmRelays),
            RelaySection("private_outbox", Res.string.private_outbox_section, Res.string.private_outbox_section_explainer, privateOutboxRelays),
            RelaySection("proxy", Res.string.proxy_section, Res.string.proxy_section_explainer, proxyRelays),
            RelaySection("broadcast", Res.string.broadcast_section, Res.string.broadcast_section_explainer, broadcastRelays),
            RelaySection("indexer", Res.string.indexer_section, Res.string.indexer_section_explainer, indexerRelays),
            RelaySection("search", Res.string.search_section, Res.string.search_section_explainer, searchRelays),
            RelaySection("local", Res.string.local_section, Res.string.local_section_explainer, localRelays),
            RelaySection("trusted", Res.string.trusted_section, Res.string.trusted_section_explainer, trustedRelays),
            RelaySection("favorites", Res.string.favorite_section, Res.string.favorite_section_explainer, favoriteRelays),
            RelaySection("blocked", Res.string.blocked_section, Res.string.blocked_section_explainer, blockedRelays),
        )
}

data class RelaySection(
    val fileName: String,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val relays: List<BasicRelaySetupInfo>,
)
