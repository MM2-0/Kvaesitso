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

import com.vitorpamplona.quartz.nip01Core.core.Event

/** Hand-made events for the front page tests: nothing here verifies signatures. */
internal object Fixtures {
    const val OBSERVER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val ALICE = "1111111111111111111111111111111111111111111111111111111111111111"
    const val BOB = "2222222222222222222222222222222222222222222222222222222222222222"

    fun id(n: Int) = n.toString(16).padStart(64, '0')

    fun event(
        n: Int,
        kind: Int = 1,
        content: String = "",
        pubkey: String = ALICE,
        tags: Array<Array<String>> = emptyArray(),
        createdAt: Long = 1_794_200_000L + n,
    ) = Event(id(n), pubkey, createdAt, kind, tags, content, "0".repeat(128))

    fun corpus(
        desks: Map<String, List<Event>>,
        control: List<Event> = emptyList(),
        profiles: Map<String, Profile> = mapOf(ALICE to Profile("Alice Moreira", null)),
    ) = assemble(OBSERVER, "wss://search.brainstorm.world/", DEFAULT_TRUST_FLOOR, 1_794_182_400L, 1_794_268_800L, desks, control, profiles)
}
