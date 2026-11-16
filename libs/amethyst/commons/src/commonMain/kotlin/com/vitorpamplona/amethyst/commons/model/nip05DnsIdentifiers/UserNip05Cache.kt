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
package com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Stable
class UserNip05Cache {
    val flow: MutableStateFlow<Nip05State> = MutableStateFlow(Nip05State.NotFound)

    fun newMetadata(
        nip05: String?,
        ownerPubKey: HexKey,
    ) {
        if (nip05.isNullOrBlank()) {
            flow.tryEmit(Nip05State.NotFound)
        } else {
            val current = flow.value
            val parsedNip05 = Nip05Id.parse(nip05)

            if (parsedNip05 == null) {
                flow.tryEmit(Nip05State.NotFound)
                return
            }

            if (current is Nip05State.Exists && current.nip05 == parsedNip05) return

            flow.update { current ->
                if (current is Nip05State.Exists && current.nip05 == parsedNip05) {
                    current
                } else {
                    Nip05State.Exists(parsedNip05, ownerPubKey)
                }
            }
        }
    }
}
