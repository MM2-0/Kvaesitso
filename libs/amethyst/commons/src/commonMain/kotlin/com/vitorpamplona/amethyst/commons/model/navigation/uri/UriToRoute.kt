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
package com.vitorpamplona.amethyst.commons.model.navigation.uri

import com.vitorpamplona.amethyst.commons.actions.ConcordActions
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeForPointer
import com.vitorpamplona.amethyst.commons.model.navigation.uri.findQueryParameterValue
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipLinks
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.cordn.client.CordnLinkInspection
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip29RelayGroups.GroupInviteLink
import com.vitorpamplona.quartz.nip29RelayGroups.GroupNAddrInvite
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip52Calendar.appt.day.CalendarDateSlotEvent
import com.vitorpamplona.quartz.nip52Calendar.appt.time.CalendarTimeSlotEvent
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.utils.UriParser
import com.vitorpamplona.quartz.utils.UrlEncoder
import kotlinx.coroutines.CancellationException

private const val NOSTR_URI_PREFIX = "nostr:"

fun isNotificationRoute(uri: String) = uri.startsWith("notifications", true) || uri.startsWith("nostr:notifications", true)

fun isHashtagRoute(uri: String) = uri.startsWith("hashtag?id=") || uri.startsWith("nostr:hashtag?id=")

/**
 * A markdown link target that is a bare hashtag fragment, e.g. the `#NostrMultiplayerGames` in
 * `[Games](#NostrMultiplayerGames)`. Returns the tag without the leading `#`, or null when the
 * uri is anything else (full URLs with anchors don't start with `#`). Reuses the same character
 * class that linkifies #hashtags in plain text, so validity matches how tags parse everywhere else.
 */
fun fragmentHashtagOrNull(uri: String): String? {
    val match = RichTextParser.hashTagsPattern.matchEntire(uri) ?: return null
    if (!match.groups[2]?.value.isNullOrEmpty()) return null
    return match.groups[1]?.value
}

fun isUrlRoute(uri: String) = uri.startsWith("url?id=") || uri.startsWith("nostr:url?id=")

/**
 * A private chatroom, addressed by its participants rather than by a message.
 * Posted by DM notifications — see `NotificationRoutes.chatroomUri` for why a DM
 * cannot deep-link through its own note.
 */
fun isChatroomRoute(uri: String) = uri.startsWith("chatroom?id=") || uri.startsWith("nostr:chatroom?id=")

/**
 * Note what this deliberately does *not* do: register the room on the account.
 *
 * `routeToMessage` would, and that is right for the in-app callers, but wrong here twice over.
 * `uriToRoute` runs against whichever account is current, *before* `?account=` is read and the
 * switch happens (`AppNavigation.NavigateIfIntentRequested`), so a DM notification for account B
 * tapped while A is on screen would insert an empty conversation into **A**'s message list. And
 * `nostr:` is an exported, browsable scheme, so any web page could post
 * `nostr:chatroom?id=<hex>&account=…` and inject a room of its choosing.
 *
 * Nothing needs it: `ChatroomFeedFilter.chatroom()` calls `getOrCreatePrivateChatroom` when the
 * screen actually opens, by which point the switch has happened and the room lands on the right
 * account.
 *
 * The ids are still checked here — a room key is a set of pubkeys, so anything that isn't one is
 * not a room, and a bad deep link should resolve to nothing rather than to an unopenable screen.
 */
fun chatroomRoute(uri: String): Route? {
    val users =
        uri
            .findQueryParameterValue("id")
            ?.split(',')
            ?.map { it.trim() }
            ?.takeIf { it.isNotEmpty() && it.all(::isPubKeyHex) }
            ?.toSet() ?: return null

    return Route.Room(ChatroomKey(users))
}

/** A bare 32-byte lowercase-hex pubkey, the only thing a chatroom key is made of. */
private fun isPubKeyHex(value: String) = value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

/**
 * A NIP-17 private note or reply, addressed by the rumor's own id because its `nevent`
 * names the undeliverable gift wrap instead — see `NotificationRoutes.privateNoteUri`.
 */
fun isPrivateNoteRoute(uri: String) = uri.startsWith("privatenote?id=") || uri.startsWith("nostr:privatenote?id=")

fun privateNoteRoute(uri: String): Route? {
    val id = uri.findQueryParameterValue("id") ?: return null
    return Route.EventRedirect(id, isPrivate = true)
}

fun isConnectedAppRoute(uri: String) = uri.startsWith("connectedapp?coordinate=") || uri.startsWith("nostr:connectedapp?coordinate=")

/**
 * The Connected Apps permission-detail route for an app coordinate (`pubkey:dtag` for a napplet/nsite,
 * `browser:<origin>` for a web client). Fired by the sandbox host so a running full-screen surface can
 * jump straight to its editable permissions; the coordinate is URL-encoded into the [coordinate] param.
 */
fun connectedAppRoute(uri: String): Route.ConnectedAppDetail? {
    val coordinate =
        runCatching {
            val raw = uri.removePrefix(NOSTR_URI_PREFIX).findQueryParameterValue("coordinate") ?: return null
            UrlEncoder.decode(raw)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null

    return Route.ConnectedAppDetail(coordinate)
}

fun urlRoute(uri: String): Route.Url? {
    val url =
        runCatching {
            val rawUrl = uri.removePrefix(NOSTR_URI_PREFIX).findQueryParameterValue("id") ?: return null
            UrlEncoder.decode(rawUrl)
        }.getOrNull() ?: return null

    return UrlId.toScopeOrNull(url)?.let { Route.Url(it) }
}

fun isWalletConnectRoute(uri: String) = uri.startsWith("dlnwc?value=") || uri.startsWith("amethyst+walletconnect:dlnwc?value=") || uri.startsWith("amethyst+walletconnect://dlnwc?value=")

fun isMarmotGroupRoute(uri: String) = uri.startsWith("marmot:")

/**
 * Tapping the always-on service notification. That notification's whole subject is the relay
 * connections it is holding open, so it lands on the screen that explains them rather than on
 * whatever tab happened to be last open.
 */
fun isActiveSubscriptionsRoute(uri: String) = uri.startsWith("activesubs", true) || uri.startsWith("nostr:activesubs", true)

/**
 * The launcher shortcut (res/xml/shortcuts.xml) fires `amethyst:scanqr`, caught by MainActivity's
 * existing scheme filter. A pseudo-uri rather than a hardcoded component, because debug and
 * benchmark builds carry an applicationId suffix a hardcoded targetPackage would miss, and
 * manifest placeholders are not substituted into resource XML.
 *
 * `nostr:scanqr` is still accepted so an older pinned shortcut keeps working, but the shortcut
 * itself uses our own scheme: an implicit `nostr:` VIEW intent can be answered by any Nostr client
 * installed alongside us.
 */
fun isScanQrRoute(uri: String) =
    uri.equals("scanqr", true) ||
        uri.equals("amethyst:scanqr", true) ||
        uri.equals("nostr:scanqr", true)

private val MARMOT_HEX = Regex("^[0-9a-fA-F]+$")

private val FRONT_PAGE_DAY = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/**
 * The URI a "your paper is ready" notification opens. With [accountNpub], opening it switches to that account first
 * (`?account=`, as notifications do): a paper printed for one account never opens on another's.
 */
fun frontPageUri(
    day: String? = null,
    accountNpub: String? = null,
): String {
    val query = listOfNotNull(day?.let { "day=$it" }, accountNpub?.let { "account=$it" })
    return if (query.isEmpty()) "amethyst:frontpage" else "amethyst:frontpage?" + query.joinToString("&")
}

/** `amethyst:frontpage`, optionally `?day=yyyy-MM-dd`: the front page, on that kept edition. */
fun frontPageRoute(uri: String): Route.FrontPage? {
    if (!uri.startsWith("amethyst:frontpage", ignoreCase = true)) return null
    val day = uri.findQueryParameterValue("day")?.takeIf { FRONT_PAGE_DAY.matches(it) }
    return Route.FrontPage(day)
}

fun uriToRoute(
    uri: String,
    account: Account,
): Route? {
    if (isNotificationRoute(uri)) {
        val scrollTo = uri.findQueryParameterValue("scrollTo")
        return Route.Notification(scrollToEventId = scrollTo)
    }
    if (isActiveSubscriptionsRoute(uri)) {
        return Route.ActiveSubscriptions
    }
    if (isScanQrRoute(uri)) {
        return Route.QRDisplay(account.signer.pubKey, startScanning = true)
    }
    frontPageRoute(uri)?.let { return it }
    if (isHashtagRoute(uri)) {
        return Route.Hashtag(uri.removePrefix(NOSTR_URI_PREFIX).removePrefix("hashtag?id=").lowercase())
    }
    fragmentHashtagOrNull(uri)?.let {
        return Route.Hashtag(it.lowercase())
    }
    if (isUrlRoute(uri)) {
        return urlRoute(uri)
    }
    if (isChatroomRoute(uri)) {
        return chatroomRoute(uri)
    }
    if (isPrivateNoteRoute(uri)) {
        return privateNoteRoute(uri)
    }
    if (isConnectedAppRoute(uri)) {
        return connectedAppRoute(uri)
    }

    // A scanned/opened `nostrconnect://` offer is an app asking to connect to our signer: open the
    // NIP-46 signer screen and let it run the pairing (it enables the signer as part of connecting).
    if (uri.startsWith(NostrConnectURI.NOSTRCONNECT_SCHEME)) {
        return Route.Nip46Signer(connectUri = uri)
    }

    relayGroupInviteRoute(uri)?.let { return it }
    concordInviteRoute(uri)?.let { return it }
    buzzInviteRoute(uri)?.let { return it }
    cordnLinkRoute(uri)?.let { return it }

    // A link to an official NIP (the nips repo, nips.nostr.com) opens in the in-app reader.
    NipLinks.numberFromUrl(uri)?.let { return Route.Nip(it, NipLinks.anchorFromUrl(uri)) }

    val parsedNip19 = Nip19Parser.uriToRoute(uri)
    val nip19 = parsedNip19?.entity
    if (nip19 != null) {
        LocalCache.consume(nip19)

        val route =
            when (nip19) {
                is NPub -> {
                    Route.Profile(nip19.hex)
                }

                is NProfile -> {
                    Route.Profile(nip19.hex)
                }

                is NNote -> {
                    Route.Note(nip19.hex)
                }

                is NEvent -> {
                    val note = LocalCache.getOrCreateNote(nip19.hex)
                    // Only fall back to the pointer's own kind while the body is missing: once the
                    // event is cached it may belong somewhere the kind alone can't name (a channel,
                    // a chatroom), and routeFor knows that.
                    if (note.event == null) {
                        routeForPointer(nip19.kind, nip19.hex) ?: Route.EventRedirect(nip19.hex)
                    } else {
                        routeFor(note = note, loggedIn = account) ?: Route.EventRedirect(nip19.hex)
                    }
                }

                is NAddress -> {
                    relayGroupDirectRoute(nip19, parsedNip19.additionalChars)
                        ?: routeFor(
                            note = LocalCache.getOrCreateAddressableNote(nip19.address()),
                            loggedIn = account,
                        ) ?: calendarDirectRoute(nip19) ?: Route.EventRedirect(nip19.aTag())
                }

                is NEmbed -> {
                    val noteEvent = nip19.event
                    if (noteEvent is AddressableEvent) {
                        routeFor(
                            note = LocalCache.getOrCreateAddressableNote(noteEvent.address()),
                            loggedIn = account,
                        ) ?: Route.EventRedirect(noteEvent.addressTag())
                    } else {
                        routeFor(
                            note = LocalCache.getOrCreateNote(nip19.event.id),
                            loggedIn = account,
                        ) ?: Route.EventRedirect(nip19.event.id)
                    }
                }

                else -> {
                    null
                }
            }

        if (route != null) {
            return route
        }
    }

    if (isMarmotGroupRoute(uri)) {
        // marmot:<groupHex>?account=<npub>
        val groupHex =
            uri
                .removePrefix("marmot:")
                .substringBefore("?")
                .substringBefore("&")
        if (groupHex.matches(MARMOT_HEX)) {
            return Route.MarmotGroupChat(groupHex)
        }
    }

    if (isWalletConnectRoute(uri)) {
        try {
            val url = UriParser(uri)
            val nip47Uri = url.getQueryParameter("value")?.firstOrNull()
            if (nip47Uri != null) {
                Nip47WalletConnect.parse(nip47Uri)
                return Route.WalletAddNwc(nip47Uri)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    try {
        Nip47WalletConnect.parse(uri)
        return Route.WalletAddNwc(uri)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
    }

    return null
}

/**
 * Direct route for an `naddr` whose event hasn't arrived in [LocalCache] yet. When a notification
 * is tapped (or a `nostr:naddr…` deep link arrives) for a calendar appointment we know is kind
 * 31922/31923, route straight to the dedicated detail screen instead of bouncing through
 * [Route.EventRedirect]. The detail screen issues its own per-event subscription, so the user
 * sees the calendar-specific loading placeholder while the event arrives, not the generic
 * redirect screen.
 */
private fun calendarDirectRoute(nip19: NAddress): Route? =
    when (nip19.kind) {
        CalendarTimeSlotEvent.KIND,
        CalendarDateSlotEvent.KIND,
        -> Route.CalendarEventDetail(nip19.kind, nip19.author, nip19.dTag)
        else -> null
    }

/**
 * Direct route for a NIP-29 group `naddr` (kind 39000) shared as a link. The group
 * only exists on its host relay, so the naddr's relay hint is mandatory — the group
 * id alone can't be resolved. With a hint we open the group chat straight away
 * (cold start included); without one there's nowhere to look, so fall through.
 */
private fun relayGroupDirectRoute(
    nip19: NAddress,
    additionalChars: String?,
): Route? {
    if (nip19.kind != GroupMetadataEvent.KIND) return null
    val relay = nip19.relay.firstOrNull() ?: return null
    // The spec allows an invite code appended as `naddr1…?invite=<code>`; it arrives
    // in the trailing chars after the bech32 body. Carry it so the group auto-joins.
    val inviteCode = GroupNAddrInvite.parse(additionalChars)
    return Route.RelayGroup(nip19.dTag, relay.url, inviteCode = inviteCode)
}

/**
 * Direct route for a NIP-29 group invite link in the de-facto `<relay>'<groupId>[?code=<code>]`
 * form shared by Wisp and 0xchat (e.g. `wss://groups.0xchat.com'abc123`). The link carries its
 * own host relay, so we can open the group straight away. Returns null for anything that isn't
 * this exact shape, so it's safe to try before the nostr-entity parser.
 */
private fun relayGroupInviteRoute(uri: String): Route? {
    val link = GroupInviteLink.parse(uri.removePrefix(NOSTR_URI_PREFIX)) ?: return null
    return Route.RelayGroup(link.groupId, link.relayUrl.url, inviteCode = link.code)
}

/**
 * A shared Concord invite URL (`…/invite/<naddr>#<fragment>`). Cheap substring gates
 * keep the parse off the hot path; the whole URL (fragment included) is carried into
 * the route so the redeem flow still has the unlock token.
 */
private fun concordInviteRoute(uri: String): Route? =
    if (uri.contains("/invite/") && uri.contains('#') && ConcordActions.parseInviteLink(uri) != null) {
        Route.ConcordInvite(uri)
    } else {
        null
    }

/**
 * A cordn group link: a bare `cordn1…` or a cordn.net share link
 * (`https://cordn.net/chat/cordn1…?m=…`). Opens the link screen, which shows
 * the coordinator and what joining would expose before anyone asks to join.
 */
private fun cordnLinkRoute(uri: String): Route? =
    if (uri.contains("cordn1", ignoreCase = true) && CordnLinkInspection.looksLikeLink(uri)) {
        Route.CordnLink(uri)
    } else {
        null
    }

/**
 * A Buzz workspace invite (`https://<host>/invite/<token>`) — a plain `/invite/` https URL with
 * no fragment, so disjoint from the Concord shape above. Opens the in-app join flow
 * ([Route.BuzzInvite]) instead of the external browser so the claim signs with the user's key.
 */
private fun buzzInviteRoute(uri: String): Route? =
    if (uri.contains("/invite/") && BuzzInviteLink.parse(uri) != null) {
        Route.BuzzInvite(uri)
    } else {
        null
    }
