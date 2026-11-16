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

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * What the broker encrypted for a web app in the last few minutes, so a consent prompt for the
 * event that carries that ciphertext can say what is inside it.
 *
 * A NIP-17 sender builds its own NIP-59 layers: it asks `nip44.encrypt(recipient, rumor)` and then
 * `signEvent` for a kind-13 seal whose content is that ciphertext. The seal has no tags, so on its
 * own the prompt can only show base64 and cannot even name the recipient. The broker just produced
 * that ciphertext, though, so it can answer "a private message to Vitor: hi" instead.
 *
 * Memory only, never persisted: the plaintext is the user's own message, already shown to them when
 * the encrypt was approved. Bounded by [capacity] and [maxAgeSeconds] so it cannot grow with a long
 * session, and keyed by the exact ciphertext, which is unique per encryption (random nonce).
 */
class NappletRecentEncryptions(
    private val capacity: Int = 32,
    private val maxAgeSeconds: Long = 600,
    private val now: () -> Long = { TimeUtils.now() },
) {
    class Entry(
        val recipient: HexKey,
        val plaintext: String,
        val at: Long,
    )

    private val lock = KmpLock()

    // Insertion-ordered: the oldest entry is the first to go when [capacity] is reached.
    private val entries = LinkedHashMap<String, Entry>()

    fun record(
        ciphertext: String,
        recipient: HexKey,
        plaintext: String,
    ) = lock.withLock {
        entries.remove(ciphertext)
        entries[ciphertext] = Entry(recipient, plaintext, now())
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    /** The encryption that produced [ciphertext], if it happened here within [maxAgeSeconds]. */
    fun lookup(ciphertext: String): Entry? =
        lock.withLock {
            val entry = entries[ciphertext] ?: return@withLock null
            if (now() - entry.at > maxAgeSeconds) {
                entries.remove(ciphertext)
                null
            } else {
                entry
            }
        }
}
