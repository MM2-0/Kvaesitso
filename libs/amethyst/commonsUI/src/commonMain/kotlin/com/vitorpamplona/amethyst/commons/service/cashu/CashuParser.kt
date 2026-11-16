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
package com.vitorpamplona.amethyst.commons.service.cashu

import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.quartz.nip60Cashu.token.CashuToken
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenB64Parser
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Thin Amethyst adapter over the protocol-level [CashuTokenB64Parser] in
 * Quartz: it adds the off-main-thread guard and wraps the result in the UI's
 * [GenericLoadable] so composables can render loading/error/loaded states.
 */
class CashuParser {
    fun parse(cashuToken: String): GenericLoadable<ImmutableList<CashuToken>> {
        val parsed =
            CashuTokenB64Parser.parse(cashuToken)
                ?: return GenericLoadable.Error("Could not parse this cashu token")

        if (parsed.isEmpty()) return GenericLoadable.Error("No token found")

        return GenericLoadable.Loaded(parsed.toImmutableList())
    }
}
