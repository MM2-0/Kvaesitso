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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub

/**
 * The paper's links, per app: where a citation and a byline point. The brief names them only as
 * `<CITE_BASE>` and `<PROFILE_BASE>` and the writer's message gives each app's ([linksLine]), so
 * the brief is the same text in every app. The writer cites an event as `<citeBase><64-hex id>`
 * and a person as `<profileBase><64-hex id of one of their posts>`; resolve encodes the first into
 * an nevent, or an naddr for an addressable event (a stream, a listing, a calendar entry, a
 * long-form post or a recipe), and the second into the author's npub ([citation], [profile]);
 * the boundary accepts exactly those encoded forms, for events and authors the edition read.
 *
 * Amethyst uses jumble.social ([JUMBLE]): the published page has to work in any browser, and a
 * tap inside Amethyst opens the same forms here ([PaperLinks.toNostrUri]). Brainstorm uses
 * `https://brainstorm.world/e/` (nevent and naddr) and `https://brainstorm.world/p/` (npub).
 */
@Immutable
class PaperLinkSet(
    val citeBase: String,
    val profileBase: String,
) {
    private val cite = Regex.escape(citeBase)
    private val people = Regex.escape(profileBase)
    private val citeHex = Regex("^$cite([0-9a-fA-F]{64})$TAIL")
    private val citeBech32 = Regex("^$cite((?:nevent1|naddr1|note1)[0-9a-zA-Z]+)$TAIL", RegexOption.IGNORE_CASE)
    private val profileHex = Regex("^$people([0-9a-fA-F]{64})$TAIL")
    private val profileNpub = Regex("^$people(npub1[0-9a-zA-Z]+)$TAIL", RegexOption.IGNORE_CASE)

    /** The writer's citation of [eventId]: what the digest tells it to write. */
    fun writerCitation(eventId: HexKey) = citeBase + eventId.lowercase()

    /** The page's citation of [event]: its naddr when it is addressable and its address encodes, else its nevent. */
    fun citation(event: Event): String = citeBase + (addressOf(event) ?: toNevent(event.id))

    /** The page's byline link to [pubkey]: their npub. */
    fun profile(pubkey: HexKey): String = profileBase + npubOf(pubkey)

    /** The event id of a writer's `<citeBase><64-hex>`, or null. */
    fun writerCited(href: String): HexKey? =
        citeHex
            .find(href)
            ?.groupValues
            ?.get(1)
            ?.lowercase()

    /** The decoded entity of an encoded citation (`<citeBase>nevent1…`, `naddr1…`, `note1…`), or null. */
    fun cited(href: String): Any? = citeBech32.find(href)?.let { Nip19Parser.uriToRoute(it.groupValues[1])?.entity }

    /** The hex a writer's `<profileBase><64-hex>` names (a post's id, or leniently a pubkey), or null. */
    fun writerProfile(href: String): HexKey? =
        profileHex
            .find(href)
            ?.groupValues
            ?.get(1)
            ?.lowercase()

    /** The pubkey of an encoded byline link (`<profileBase>npub1…`), or null. */
    fun profiled(href: String): HexKey? = profileNpub.find(href)?.let { fromNpub(it.groupValues[1]) }

    /** `nostr:<bech32>` for a link in one of this set's encoded forms, decoded and of the right entity; else null. */
    fun toNostrUri(url: String): String? {
        val trimmed = url.trim()
        citeBech32.find(trimmed)?.let { found ->
            val bech32 = found.groupValues[1].lowercase()
            val entity = Nip19Parser.uriToRoute(bech32)?.entity ?: return null
            return if (entity is NEvent || entity is NAddress || entity is NNote) "nostr:$bech32" else null
        }
        profileNpub.find(trimmed)?.let { found ->
            val bech32 = found.groupValues[1].lowercase()
            return if (Nip19Parser.uriToRoute(bech32)?.entity is NPub) "nostr:$bech32" else null
        }
        return null
    }

    companion object {
        private const val TAIL = "(?:[/?#].*)?$"

        /** Amethyst's: jumble.social, which any browser opens and Amethyst routes in the app. */
        val JUMBLE = PaperLinkSet("https://jumble.social/notes/", "https://jumble.social/users/")

        /** An addressable event's naddr: kinds 30000-39999 with a `d` tag that encodes (at most 255 bytes); else null. */
        fun addressOf(event: Event): String? {
            if (event.kind !in 30_000..39_999) return null
            val d = event.firstTagValue("d") ?: return null
            if (d.encodeToByteArray().size > 255) return null
            return try {
                NAddress.createOrNull(event.kind, event.pubKey, d, emptyList())
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** The writer's message line naming the app's link bases. */
fun linksLine(links: PaperLinkSet): String = "Links: citations ${links.citeBase}<id>, people ${links.profileBase}<id>"

/**
 * The paper's links, as Amethyst opens them. The page keeps web URLs so a downloaded copy works
 * anywhere; inside Amethyst a tap on a citation or a byline in the paper's link set opens the
 * thread, stream, listing, calendar entry or profile here instead of a web client. Anything else
 * is not ours to route and keeps going to the browser.
 */
object PaperLinks {
    /** `nostr:<bech32>` for one of the paper's own link forms in [links], DECODED, not just matched; else null. */
    fun toNostrUri(
        url: String,
        links: PaperLinkSet = PaperLinkSet.JUMBLE,
    ): String? = links.toNostrUri(url)
}
