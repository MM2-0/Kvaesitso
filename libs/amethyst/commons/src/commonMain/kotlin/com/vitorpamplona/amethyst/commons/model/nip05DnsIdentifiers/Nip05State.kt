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
import com.vitorpamplona.quartz.nip05DnsIdentifiers.INip05Client
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Id
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.cancellation.CancellationException

@Stable
sealed interface Nip05State {
    object NotFound : Nip05State

    @Stable
    class Exists(
        val nip05: Nip05Id,
        val hexKey: HexKey,
    ) : Nip05State {
        val verificationState = MutableStateFlow<Nip05VerifState>(Nip05VerifState.NotStarted)

        fun markAsVerified() = verificationState.tryEmit(Nip05VerifState.Verified(TimeUtils.oneHourAhead()))

        fun markAsInvalid() = verificationState.tryEmit(Nip05VerifState.Failed(TimeUtils.oneHourAhead()))

        fun markAsVerifying() = verificationState.tryEmit(Nip05VerifState.Verifying(TimeUtils.fiveMinutesAhead()))

        fun markAsError() = verificationState.tryEmit(Nip05VerifState.Error(TimeUtils.fiveMinutesAhead()))

        fun reset() = verificationState.tryEmit(Nip05VerifState.NotStarted)

        suspend fun checkAndUpdate(nip05ClientBuilder: () -> INip05Client) {
            if (verificationState.value.isExpired()) {
                markAsVerifying()
                try {
                    if (nip05ClientBuilder().verify(nip05, hexKey)) {
                        markAsVerified()
                    } else {
                        markAsInvalid()
                    }
                } catch (e: Exception) {
                    markAsError()
                    if (e is CancellationException) throw e
                }
            }
        }
    }
}
