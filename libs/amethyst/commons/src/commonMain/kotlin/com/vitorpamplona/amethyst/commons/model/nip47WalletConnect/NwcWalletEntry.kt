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
package com.vitorpamplona.amethyst.commons.model.nip47WalletConnect

import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Serializable
data class NwcWalletEntry(
    val id: String = Uuid.random().toString(),
    val name: String,
    val uri: Nip47WalletConnect.Nip47URI,
) {
    fun normalize(): NwcWalletEntryNorm? =
        uri.normalize()?.let {
            NwcWalletEntryNorm(id, name, it)
        }
}

data class NwcWalletEntryNorm(
    val id: String,
    val name: String,
    val uri: Nip47WalletConnect.Nip47URINorm,
) {
    fun denormalize(): NwcWalletEntry? =
        uri.denormalize()?.let {
            NwcWalletEntry(id, name, it)
        }
}
