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
package com.vitorpamplona.amethyst.commons.browser

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp

/** One suggested web app: the launchable [app] plus a short [description] of what it does. */
@Immutable
data class SuggestedWebApp(
    val app: FavoriteApp.WebApp,
    val description: String,
)

/**
 * The Nostr **web apps** offered as starting points in the browser launcher when the user hasn't
 * pinned or visited anything of their own yet. They are shown under a "Discover" section below Recent,
 * so they're discoverable without ever getting in the way of the user's own favorites/history.
 *
 * The list is drawn from the [nostrapps.com](https://nostrapps.com) directory (cross-checked against
 * [awesome-nostr](https://github.com/aljazceru/awesome-nostr)) — every entry that ships a
 * browser-openable web version — grouped here by what it does. Entries that are browser *extensions* or
 * signer-only tools (nos2x, Nostrame, …) are intentionally left out: they aren't something you open in
 * an in-app browser. URLs are the apps' own canonical domains.
 *
 * Each entry carries:
 * - a short curated [SuggestedWebApp.description] (a trimmed version of the app's own meta description)
 *   shown as the row subtitle, since these are apps the user likely hasn't seen before;
 * - the app's **own** logo as [FavoriteApp.iconUrl] (the PNG/SVG declared in its
 *   `<link rel="apple-touch-icon">` / `<link rel="icon">`), so it matches the favicon look of the
 *   Favorites/Recent rows without routing through any third-party favicon service. Only PNG/SVG are
 *   used — Coil has no ICO decoder — so apps whose only icon is a `.ico` (and a few that couldn't be
 *   resolved) are left icon-less and fall back to the globe glyph until their real favicon is captured
 *   the normal way on first visit.
 *
 * The [app]s are plain [FavoriteApp.WebApp] entries, so tapping one opens it like any other web
 * favorite and the user can star it to pin it for real. They are **not** persisted as favorites: the
 * list is hardcoded, device-local, and never becomes account state.
 */
object DefaultWebClients {
    val list: List<SuggestedWebApp> =
        buildList {
            // Social / microblogging clients
            webApp("Primal", "https://primal.net", "All-in-one client with a built-in wallet", "https://primal.net/assets/apple-touch-icon-a536f430.png")
            webApp("Coracle", "https://coracle.social", "Relay-savvy client for regular people", "https://coracle.social/icons/apple-touch-icon-76x76.png")
            webApp("Snort", "https://snort.social", "Fast, feature-packed social client", "https://snort.social/img/apple-touch-icon.png")
            webApp("noStrudel", "https://nostrudel.ninja", "Power-user client for exploring Nostr", "https://nostrudel.ninja/apple-touch-icon.png")
            webApp("Iris", "https://iris.to", "Simple, fast social client", "https://iris.to/img/apple-touch-icon.png")
            webApp("Nostter", "https://nostter.app", "Lightweight web social client", "https://nostter.app/apple-touch-icon.png")
            webApp("Jumble", "https://jumble.social", "Explore feeds relay by relay", "https://jumble.social/favicon.svg")
            webApp("Nostria", "https://nostria.app", "Social without the noise", "https://nostria.app/icons/icon-192x192-maskable.png")
            webApp("Nosotros", "https://nosotros.app", "A weirdly fast social client", "https://nosotros.app/apple-touch-icon-144x144.png")
            webApp("lumilumi", "https://lumilumi.app", "Lightweight Nostr client", "https://lumilumi.app/apple-touch-icon-180x180.png")
            webApp("Phoenix", "https://phoenix.social", "Snort-based social client", "https://phoenix.social/img/apple-touch-icon.png")
            webApp("Shosho", "https://shosho.live", "Live-streaming marketplace", "https://shosho.live/apple-touch-icon.png")
            webApp("ants", "https://ants.sh", "Advanced Nostr text search", "https://ants.sh/apple-touch-icon.png")
            webApp("YakiHonne", "https://yakihonne.com", "Decentralized media & long-form")
            webApp("Ditto", "https://ditto.pub", "Your content, your vibe, your rules", "https://ditto.pub/apple-touch-icon.png")
            webApp("x21", "https://x21.social", "Relay feed explorer", "https://x21.social/apple-touch-icon.png?v=4")
            webApp("Ghostr", "https://ghostr.org", "Draft & delegated publishing", "https://ghostr.org/favicon/apple-touch-icon.png")
            webApp("Mutable", "https://mutable.top", "Your mute list manager", "https://mutable.top/mutable_logo.svg")

            // Reading / long-form / feeds
            webApp("Habla", "https://habla.news", "Long-form articles & blogs")
            webApp("Highlighter", "https://highlighter.com", "Articles, highlights & communities", "https://highlighter.com/apple-touch-icon-180x180.png")
            webApp("Boris", "https://readwithboris.com", "Distraction-free reading & highlights", "https://readwithboris.com/apple-touch-icon.png")
            webApp("Noflux", "https://noflux.nostr.technology", "RSS-style feed reader")

            // Communities / chat
            webApp("Flotilla", "https://flotilla.social", "Community spaces & chat", "https://framerusercontent.com/images/8UjnVxSvRkmvY2lEYU5z8OMOw0M.png")
            webApp("Chachi", "https://chachi.chat", "Group chat & communities")
            webApp("NostrChat", "https://www.nostrchat.io", "Decentralized chat", "https://www.nostrchat.io/logo192.png")
            webApp("NymChat", "https://www.nymchat.com", "Anonymous, ephemeral chat")
            webApp("HiveTalk", "https://hivetalk.org", "Lightning-powered video conferencing", "https://hivetalk.org/_astro/apple-touch-icon.BAevOzwc.png")

            // Media — video, photo, audio, podcasts, files
            webApp("zap.stream", "https://zap.stream", "Live streaming with Lightning", "https://zap.stream/logo.png")
            webApp("Divine Video", "https://divine.video", "6-second looping videos", "https://divine.video/app_icon.png")
            webApp("Olas", "https://olas.app", "Photo & media sharing", "https://olas.app/favicon.png")
            webApp("Zappix", "https://zappix.app", "Share & discover images", "https://zappix.app/icon-192.png")
            webApp("Slidestr", "https://slidestr.net", "Media slideshow viewer", "https://slidestr.net/slidestr.svg")
            webApp("Bouquet", "https://bouquet.slidestr.net", "Blossom media manager", "https://bouquet.slidestr.net/bouquet.png")
            webApp("YakBak", "https://yakbak.app", "Voice messages", "https://yakbak.app/yakbak-logo.png")
            webApp("ZapTrax", "https://zaptrax.app", "Music streaming with Wavlake", "https://zaptrax.app/icon-192.png")
            webApp("Podstr", "https://podstr.org", "Podcasts on Nostr", "https://podstr.org/favicon.svg")
            webApp("Nests", "https://nostrnests.com", "Live audio rooms", "https://nostrnests.com/apple-touch-icon.png")

            // Knowledge / wiki
            webApp("Wikifreedia", "https://wikifreedia.xyz", "Decentralized encyclopedia", "https://wikifreedia.xyz/favicon.svg")
            webApp("Wikistr", "https://wikistr.com", "A wiki built on Nostr", "https://wikistr.com/favicon.png")

            // Marketplace / food
            webApp("Shopstr", "https://shopstr.store", "Bitcoin-native marketplace")
            webApp("Plebeian Market", "https://plebeian.market", "Decentralized marketplace", "https://plebeian.market/logo-st5zpap9.svg")
            webApp("Zap Cooking", "https://zap.cooking", "Recipes & food culture", "https://zap.cooking/favicon.svg")

            // Tools / utilities
            webApp("Emojito", "https://emojito.meme", "Custom emoji sets")
            webApp("Formstr", "https://formstr.app", "Decentralized forms", "https://formstr.app/logo192.png")
            webApp("Nostree", "https://nostree.me", "Link-in-bio pages")
            webApp("Badges", "https://badges.page", "Create & award badges")
            webApp("Nstart", "https://nstart.me", "Guided account onboarding", "https://nstart.me/favicon.png")
            webApp("alphaama", "https://alphaama.com", "Nostr tools & experiments")
            webApp("Treasures", "https://treasures.to", "Geocaching on Nostr", "https://treasures.to/apple-touch-icon.png")
            webApp("Yondar", "https://yondar.me", "Places & maps", "https://yondar.me/apple-touch-icon.png")
            webApp("DTAN", "https://dtan.xyz", "Torrents on Nostr")
            webApp("Nostrocket", "https://nostrocket.org", "Project coordination")
            webApp("Plektos", "https://plektos.app", "Decentralized meetup events", "https://plektos.app/icon-180.png")
            webApp("Zaplytics", "https://zaplytics.app", "Zap analytics for creators")
            webApp("Brainstorm", "https://brainstorm.world", "Web-of-trust explorer", "https://brainstorm.world/apple-touch-icon.png")
            webApp("MAKIMONO", "https://makimono.lumilumi.app", "Long-form article editor", "https://makimono.lumilumi.app/favicon3.png")
            webApp("Primal Studio", "https://studio.primal.net", "Schedule & publish content")
            webApp("nostr.build", "https://nostr.build", "Media & image uploads", "https://nostr.build/apple-touch-icon.png")
            webApp("nostrcheck", "https://nostrcheck.me", "Media hosting & NIP-05", "https://nostrcheck.me/apple-touch-icon.png")
            webApp("Metadata", "https://metadata.nostr.com", "Edit your profile metadata")

            // Games
            webApp("Plebs vs Zombies", "https://www.plebsvszombies.cc", "Clean up your follow list", "https://www.plebsvszombies.cc/favicon.svg")
            webApp("Blobbi", "https://www.blobbi.pet", "A virtual pet game", "https://www.blobbi.pet/icons/apple-touch-icon.png")
        }

    // addedAt is 0L: these are hardcoded suggestions, not user-added favorites, so they never need a
    // real "added" timestamp for ordering — the curated order in `list` is what matters. `icon` is the
    // app's own logo URL, or null to fall back to the globe glyph until a favicon is captured on visit.
    private fun MutableList<SuggestedWebApp>.webApp(
        label: String,
        url: String,
        description: String,
        icon: String? = null,
    ) {
        add(SuggestedWebApp(FavoriteApp.WebApp(url = url, label = label, addedAt = 0L, iconUrl = icon), description))
    }
}
