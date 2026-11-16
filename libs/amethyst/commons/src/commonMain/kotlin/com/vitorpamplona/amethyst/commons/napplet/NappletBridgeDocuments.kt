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

import com.vitorpamplona.amethyst.commons.util.withString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Keeps a browser surface's NIP-07 traffic with the document that started it.
 *
 * A browser tab has one broker channel but hosts a sequence of documents: every navigation brings a new
 * page, with a new bridge reply proxy [P], and each page numbers its requests from scratch (`r0`, `r1`, …).
 * Delivering broker replies to "whichever page posted last" would hand a.com's signature or decrypted
 * message to b.com once the user navigates while a consent sheet is up (b.com's own `r0` would even
 * resolve with it).
 *
 * So every page gets a document sequence number. [brokerIdFor] stamps it on the page's request id before
 * the request leaves for the broker, and [resolve] only turns a reply back into the page's id — and hands
 * back the proxy to post it on — when it belongs to the document on screen now. Replies for a replaced
 * document are dropped. The stamp is deterministic per (document, page id), so a later message that
 * reuses a request's id (a cancel) still reaches the same broker-side request.
 *
 * Relay subscriptions get the same treatment: a page names its own (`s0`, …), and the broker pushes their
 * events — decrypted DMs included — keyed by that name. [stampSubscription] stamps the document on the
 * `subId` of what the page sends, and [resolvePush] only lets a push through, with the page's own name
 * back, when it is for the document on screen now.
 *
 * Single-threaded: call from the WebView's (main) thread.
 */
class NappletBridgeDocuments<P : Any> {
    private var current: P? = null
    private var document = 0L

    /** The bridge reply proxy of the document on screen, or null before the first message. */
    val currentProxy: P? get() = current

    /**
     * Records that a main-frame bridge message arrived through [proxy]. Returns true when it came from a
     * NEW document replacing an earlier one — the caller then drops the old page's broker state.
     */
    fun onMessage(proxy: P): Boolean {
        if (current === proxy) return false
        val replaced = current != null
        current = proxy
        document++
        return replaced
    }

    /** The id to send to the broker for the current document's request [pageId]. */
    fun brokerIdFor(pageId: String): String = "$document$SEPARATOR$pageId"

    /**
     * Resolves a broker reply's [brokerId] into the page's own id and the proxy to post it on, or null when
     * the reply belongs to a document that is gone (or was never stamped by [brokerIdFor]).
     */
    fun resolve(brokerId: String): Pair<String, P>? {
        val proxy = current ?: return null
        val cut = brokerId.indexOf(SEPARATOR)
        if (cut <= 0 || brokerId.substring(0, cut).toLongOrNull() != document) return null
        return brokerId.substring(cut + 1) to proxy
    }

    /**
     * [envelope] with the current document stamped on its `subId` (`relay.subscribe`, `relay.close`), or
     * null when it carries none and goes to the broker unchanged.
     */
    fun stampSubscription(envelope: JsonObject): JsonObject? {
        val subId = envelope.quotedString(SUB_ID) ?: return null
        return envelope.withString(SUB_ID, brokerIdFor(subId))
    }

    /**
     * A broker push to hand to the page on screen: unchanged when it isn't for a subscription, with the page's
     * own `subId` back when it is for one this document opened, or null — drop it — when it is for a
     * subscription of a document that is gone (or when nothing is on screen).
     */
    fun resolvePush(push: JsonObject): JsonObject? {
        if (current == null) return null
        val brokerSubId = push.quotedString(SUB_ID) ?: return push
        val cut = brokerSubId.indexOf(SEPARATOR)
        if (cut <= 0 || brokerSubId.substring(0, cut).toLongOrNull() != document) return null
        return push.withString(SUB_ID, brokerSubId.substring(cut + 1))
    }

    private fun JsonObject.quotedString(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * A main-frame navigation began: whatever document was on screen is on its way out, even if the new one
     * never talks to the bridge. Returns true when there was one — the caller then drops its broker state.
     */
    fun onNavigation(): Boolean {
        val had = current != null
        clear()
        return had
    }

    /** The surface went away (session closed, renderer died): nothing on screen can receive a reply. */
    fun clear() {
        current = null
        document++
    }

    private companion object {
        const val SEPARATOR = ':'
        const val SUB_ID = "subId"
    }
}
