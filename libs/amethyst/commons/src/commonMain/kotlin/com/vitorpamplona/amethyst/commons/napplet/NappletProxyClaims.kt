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

/**
 * Decides the ONE proxy route shared by every WebView in the sandbox process.
 *
 * Android's WebView proxy override is process-global, yet Tor is chosen per surface (per site): a
 * browser tab, an nSite, a full-screen page. When each surface set or cleared the override for itself,
 * the last one to do so won for everyone — opening an open-web page silently moved an already-open Tor
 * page onto the open web, with no reload and nothing on screen to say so.
 *
 * So every live surface files a claim, and the route is derived from all of them: **Tor always wins.**
 * While ANY claim wants Tor, the whole process goes through Tor (the most recent Tor claim's port — a Tor
 * restart can move it), open-web surfaces included; with no Tor claim at all there is no proxy.
 *
 * There are deliberately no per-host exemptions. Exempting an open-web page's host would let a Tor page
 * reach that host directly — and an attacker who got one page onto the open web (a site opened before Tor
 * was up, say) could then have a Tor page load `https://x.attacker.com/<id>` and tie the user's real IP to
 * the Tor session. The cost is that an open-web page goes through Tor while another open page needs it;
 * surfaces show why (see [Route.usesTor]).
 *
 * A Tor claim filed before Tor's port is known ([TOR_NOT_READY]) makes the route [Route.blocked]
 * unless another claim already has the port: Tor is wanted and can't be reached, so nothing may go
 * out, not even directly. A host whose browser has no network of its own until a page asks for one
 * (the desktop's) also treats [isEmpty] as blocked.
 *
 * Not thread-safe: the caller serializes access (the sandbox touches it only on its main thread).
 */
class NappletProxyClaims {
    /** The route to apply: through Tor on [torPort] when [usesTor], nowhere when [blocked], else no proxy. */
    data class Route(
        val torPort: Int,
    ) {
        val usesTor: Boolean get() = torPort > 0

        /** Tor is wanted and its port isn't known yet. */
        val blocked: Boolean get() = torPort == TOR_NOT_READY
    }

    // Insertion-ordered; a re-claim moves the owner to the end, so the last entry is the latest claim.
    // Each value is the Tor SOCKS port the owner wants, or [NO_PROXY] for the open web.
    private val claims = LinkedHashMap<Any, Int>()

    /** Files (or replaces) [owner]'s claim — Tor on [torPort] (> 0), or [NO_PROXY] — and returns the route. */
    fun claim(
        owner: Any,
        torPort: Int,
    ): Route {
        claims.remove(owner)
        claims[owner] = torPort
        return route()
    }

    /** Withdraws [owner]'s claim (the surface is gone) and returns the resulting route. */
    fun release(owner: Any): Route {
        claims.remove(owner)
        return route()
    }

    /**
     * Whether a surface other than [owner] wants Tor — in which case the process stays on Tor even if
     * [owner] switches to the open web, so offering [owner] an open-web retry would change nothing.
     */
    fun torWantedByOthers(owner: Any): Boolean = claims.any { (other, port) -> other !== owner && port > 0 }

    fun route(): Route =
        Route(
            claims.values.lastOrNull { it > 0 }
                ?: if (TOR_NOT_READY in claims.values) TOR_NOT_READY else NO_PROXY,
        )

    /** No surface has a claim. */
    fun isEmpty(): Boolean = claims.isEmpty()

    companion object {
        const val NO_PROXY = -1

        /** A Tor claim whose port isn't known yet. */
        const val TOR_NOT_READY = 0
        val DIRECT = Route(NO_PROXY)
    }
}
