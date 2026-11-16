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
package com.vitorpamplona.amethyst.commons.walletScrutiny

import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.tags.BuildStatus

/**
 * How a reader should take a WalletScrutiny verdict, which decides the colour and icon its card
 * leads with.
 *
 * - [TRUSTED]: the build was reproduced, so the published binary is the published source.
 * - [FAILED]: the verifier showed the binary is *not* what the source builds, or that there is no
 *   honest source to build (closed, obfuscated), or that the listing is spam.
 * - [CAUTION]: nothing was proven either way: the build failed, the release has no source tag, or
 *   the verifier flagged a warning. Not a pass, but not evidence of tampering either.
 * - [UNKNOWN]: a verdict code this version of Amethyst does not know; shown as written.
 */
enum class VerdictTone {
    TRUSTED,
    FAILED,
    CAUTION,
    UNKNOWN,
}

fun verdictToneOf(status: BuildStatus?): VerdictTone =
    when (status) {
        BuildStatus.REPRODUCIBLE -> VerdictTone.TRUSTED
        BuildStatus.NOT_REPRODUCIBLE,
        BuildStatus.NOSOURCE,
        BuildStatus.OBFUSCATED,
        BuildStatus.SPAM,
        -> VerdictTone.FAILED
        BuildStatus.FTBFS,
        BuildStatus.NOTAG,
        BuildStatus.WARNING,
        -> VerdictTone.CAUTION
        null -> VerdictTone.UNKNOWN
    }

/**
 * The product line of a verification card: `id · version · platform`, skipping whichever the
 * verifier left out. Null when all three are missing, so the card can fall back to its description.
 */
fun verifiedReleaseLine(
    productId: String?,
    version: String?,
    platform: String?,
): String? = listOfNotNull(productId, version, platform).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null }
