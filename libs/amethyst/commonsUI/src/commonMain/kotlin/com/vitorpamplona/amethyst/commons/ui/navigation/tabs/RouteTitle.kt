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
package com.vitorpamplona.amethyst.commons.ui.navigation.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipsOnNostr
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.knownKindName
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.rememberNipsDefining
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.application_preferences
import com.vitorpamplona.amethyst.commons.resources.event_kind_title
import com.vitorpamplona.amethyst.commons.resources.nip_catalog_title
import com.vitorpamplona.amethyst.commons.resources.nip_number
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent

private const val MAX_TITLE = 40

private val CamelCaseBoundary = Regex("(?<=[a-z0-9])(?=[A-Z])")

/**
 * A short name for the screen [route] shows, for a tab or a window title: the person's name on a
 * profile (updated as their metadata arrives), a note's first line, the hashtag, the bar's label
 * for a section, or else the route's own name spelled out ("NewShortNote" reads "New short note").
 */
@Composable
fun routeTitle(
    route: Route,
    accountViewModel: AccountViewModel,
): String {
    when (route) {
        is Route.Profile -> {
            // Created if missing, so a tab restored before the person loaded follows them as they arrive.
            val user = remember(route.id) { LocalCache.checkGetOrCreateUser(route.id) }
            if (user != null) {
                val info by observeUserInfo(user, accountViewModel)
                return (info?.info?.bestName() ?: user.toBestDisplayName()).take(MAX_TITLE)
            }
        }
        is Route.Note -> {
            // A tab restored at start shows its note's first line as soon as the note arrives.
            val note = remember(route.id) { LocalCache.checkGetOrCreateNote(route.id) }
            if (note != null) {
                if (note.event == null) EventFinderFilterAssemblerSubscription(note)
                val state by note
                    .flow()
                    .metadata.stateFlow
                    .collectAsState()
                val event = state.note.event
                // A titled kind (NIP, article, wiki) by its title; any other note by its first line.
                event
                    ?.tags
                    ?.firstOrNull { it.size > 1 && it[0] == "title" && it[1].isNotBlank() }
                    ?.let { return it[1].trim().take(MAX_TITLE) }
                event
                    ?.content
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.let { return it.trim().take(MAX_TITLE) }
            }
        }
        is Route.Hashtag -> return "#${route.hashtag}"
        is Route.Nip -> return stringRes(Res.string.nip_number, route.number)
        is Route.Nips -> return stringRes(Res.string.nip_catalog_title)
        is Route.EventKind -> {
            knownKindName(route.kind)?.let { return it }
            val definitions = rememberNipsDefining(route.kind, accountViewModel)
            return remember(definitions) { NipsOnNostr.labelOf(route.kind, definitions) } ?: stringRes(Res.string.event_kind_title, route.kind)
        }
        // The page's own title as it loads, else its host.
        is Route.WebPage ->
            return LocalAppPlatform.current
                .platformScreenTitle(route)
                ?.takeIf { it.isNotBlank() }
                ?.take(MAX_TITLE) ?: BrowserChrome.displayHost(route.url)
        is Route.WebApp ->
            return LocalAppPlatform.current
                .platformScreenTitle(route)
                ?.takeIf { it.isNotBlank() }
                ?.take(MAX_TITLE) ?: BrowserChrome.displayHost(route.url)
        // The app's own name, else its identifier.
        is Route.NostrApp ->
            return LocalAppPlatform.current
                .platformScreenTitle(route)
                ?.takeIf { it.isNotBlank() }
                ?.take(MAX_TITLE) ?: route.coordinate
                .substringAfter(':')
                .substringAfter(':')
                .ifBlank { spellOut("NostrApp") }
        // Its name alone ("Settings") is the same as All Settings'; the screen calls itself this.
        is Route.Settings -> return stringRes(Res.string.application_preferences)
        else -> {}
    }
    val section = NavBarCatalog.values.firstOrNull { it.resolveRoute(accountViewModel)::class == route::class }
    if (section != null && route !is Route.Profile) return stringRes(section.labelRes)
    return spellOut(route::class.simpleName ?: "")
}

/** The icon a tab shows for [route] when it has no picture of its own: the bar's icon for a section. */
fun routeIcon(
    route: Route,
    accountViewModel: AccountViewModel,
): MaterialSymbol =
    when (route) {
        is Route.Hashtag -> MaterialSymbols.Tag
        is Route.Search -> MaterialSymbols.Search
        is Route.Profile -> MaterialSymbols.Person
        is Route.Room -> MaterialSymbols.Mail
        is Route.Note -> MaterialSymbols.AutoMirrored.Article
        is Route.WebPage, is Route.WebApp -> MaterialSymbols.Language
        // A site's globe, a napplet's grid, read off the coordinate's kind.
        is Route.NostrApp -> {
            val kind = route.coordinate.substringBefore(':').toIntOrNull()
            if (kind == RootSiteEvent.KIND || kind == NamedSiteEvent.KIND) MaterialSymbols.Language else MaterialSymbols.Apps
        }
        else ->
            NavBarCatalog.values.firstOrNull { it.resolveRoute(accountViewModel)::class == route::class }?.icon
                ?: if (route::class.simpleName.orEmpty().contains("Settings")) MaterialSymbols.Settings else MaterialSymbols.Description
    }

/** A link to what [route] shows (`nostr:`, or a web page's address), for a tab's Copy link; null when it shows nothing linkable. */
fun routeLink(route: Route): String? =
    when (route) {
        is Route.Note -> LocalCache.getNoteIfExists(route.id)?.toNostrUri()
        is Route.Profile -> LocalCache.getUserIfExists(route.id)?.toNostrUri() ?: "nostr:${NPub.create(route.id)}"
        is Route.WebPage -> route.url
        is Route.WebApp -> route.url
        else -> null
    }

/**
 * A number that changes when something new happens on [route]'s screen while it is out of sight:
 * a thread's replies, a chat's messages. Null for screens that have nothing to watch. A tab shows
 * a dot when it differs from what it was when the user last looked.
 */
@Composable
fun rememberRouteActivity(
    route: Route,
    accountViewModel: AccountViewModel,
): Int? =
    when (route) {
        is Route.Note -> {
            val note = remember(route.id) { LocalCache.checkGetOrCreateNote(route.id) }
            if (note == null) {
                null
            } else {
                val replies by note
                    .flow()
                    .replies.stateFlow
                    .collectAsState()
                remember(replies) { note.replies.size }
            }
        }
        is Route.Room -> {
            val room = remember(route.id) { accountViewModel.account.chatroomList.getOrCreatePrivateChatroom(route.toKey()) }
            var changes by remember(room) { mutableIntStateOf(0) }
            LaunchedEffect(room) { room.changesFlow().collect { changes++ } }
            changes
        }
        else -> null
    }

/** "NewShortNote" to "New short note". */
internal fun spellOut(className: String): String =
    className
        .replace(CamelCaseBoundary, " ")
        .lowercase()
        .replaceFirstChar { it.uppercase() }
