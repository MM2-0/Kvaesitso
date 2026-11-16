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
package com.vitorpamplona.amethyst.commons.model.mediaServers

import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl

/**
 * The Blossom servers an upload tries, in order: the one the user picked, then the rest
 * of the list the picker offered them (their kind-10063 servers, or the defaults when
 * they have none), top down.
 *
 * Servers differ in what they accept, and nothing tells the user in advance: primal,
 * azzamo and blossom.band answer an encrypted (application/octet-stream) blob with
 * 415, nostr.download refuses an image it finds perceptually identical to one it holds
 * (409, `X-Identical-Media`), some demand payment, some are simply down. With a single target, picking the
 * wrong one meant a failed upload and a retry by hand; the settings screen already told
 * users uploads "try each server from the top down".
 *
 * Blossom only: a NIP-96 upload keeps its single target, because falling back
 * from one would change the kind of event the upload produces.
 */
fun blossomUploadOrder(
    selected: ServerName,
    offered: List<ServerName>,
): List<ServerName> {
    if (selected.type != ServerType.Blossom) return listOf(selected)
    val seen = mutableSetOf(BlossomServerUrl.domain(selected.baseUrl))
    val rest = offered.filter { it.type == ServerType.Blossom && seen.add(BlossomServerUrl.domain(it.baseUrl)) }
    return listOf(selected) + rest
}
