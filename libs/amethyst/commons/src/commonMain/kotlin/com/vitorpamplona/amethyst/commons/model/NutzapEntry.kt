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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable

/**
 * Per-nutzap entry attached to a target Note.
 *
 * @property source The kind:9321 NutzapEvent note. `source.author` is the sender
 *                  shown in the reactions gallery, the multi-set notifications card,
 *                  and the dedicated cashu row in the ReactionDetailGallery.
 * @property claimedSats Sum of `amount` fields across the source's proof tags,
 *                       computed once at consumption time so the UI hot path doesn't
 *                       re-parse JSON on every recomposition. This is the sender-
 *                       CLAIMED amount; the proofs are verifiable against the mint,
 *                       but that verification is the recipient wallet's redeem-time
 *                       responsibility, not the cache's.
 */
@Stable
data class NutzapEntry(
    val source: Note,
    val claimedSats: Long,
)
