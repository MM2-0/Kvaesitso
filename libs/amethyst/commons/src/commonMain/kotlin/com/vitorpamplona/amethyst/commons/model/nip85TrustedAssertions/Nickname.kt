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
package com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists

/**
 * The decrypted private fields of the account's contact card about a user: the
 * nickname (petname) and the private note (summary), plus the card's decrypted
 * tag list so renderers can resolve any NIP-30 `:shortcode:` custom emojis they
 * use (the `emoji` mappings live encrypted next to them). Only built when at
 * least one of the two fields is present.
 */
@Immutable
class Nickname(
    val petName: String?,
    val summary: String?,
    val tags: ImmutableListOfLists<String>,
) {
    // content equality so flow distinctUntilChanged() dedupes re-decryptions of
    // the same card (ImmutableListOfLists itself compares by identity)
    override fun equals(other: Any?): Boolean =
        other is Nickname &&
            petName == other.petName &&
            summary == other.summary &&
            tags.lists.contentDeepEquals(other.tags.lists)

    override fun hashCode(): Int = 31 * (31 * petName.hashCode() + summary.hashCode()) + tags.contentHash()
}
