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
package com.vitorpamplona.amethyst.napplethost

import android.os.Message
import androidx.webkit.JavaScriptReplyProxy
import com.vitorpamplona.amethyst.commons.napplet.NappletBridgeDocuments
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.util.parseJsonObjectOrNull
import com.vitorpamplona.amethyst.commons.util.withString
import kotlinx.serialization.json.JsonObject

/**
 * Answers a queued broker [request] (a [NappletIpc.MSG_REQUEST] that never left) with a failure, delivered
 * to the page that made it — the same reply shape the broker uses, so the page's promise rejects with
 * [reason] instead of waiting forever. Dropped if that page has since been navigated away from.
 */
fun NappletBridgeDocuments<JavaScriptReplyProxy>.failRequest(
    request: Message,
    reason: String,
) {
    val data = request.data ?: return
    val brokerId = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return
    val raw = data.getString(NappletIpc.KEY_PAYLOAD) ?: return
    val (pageId, proxy) = resolve(brokerId) ?: return
    val type = runCatching { NappletProtocolJson.readType(raw) }.getOrNull() ?: "napplet"
    val reply = parseJsonObjectOrNull(NappletProtocolJson.encodeResponse(type, NappletResponse.Failed(reason))) ?: JsonObject(emptyMap())
    runCatching { proxy.postMessage(reply.withString("id", pageId).toString()) }
}

/**
 * Answers a napplet's queued broker [request] with a failure on [this] proxy. A napplet's request ids aren't
 * stamped per document (its shell never navigates), so the id goes back as the page sent it.
 */
fun JavaScriptReplyProxy.failRequest(
    request: Message,
    reason: String,
) {
    val data = request.data ?: return
    val id = data.getString(NappletIpc.KEY_REQUEST_ID) ?: return
    val raw = data.getString(NappletIpc.KEY_PAYLOAD) ?: return
    val type = runCatching { NappletProtocolJson.readType(raw) }.getOrNull() ?: "napplet"
    val reply = parseJsonObjectOrNull(NappletProtocolJson.encodeResponse(type, NappletResponse.Failed(reason))) ?: JsonObject(emptyMap())
    runCatching { postMessage(reply.withString("id", id).toString()) }
}
