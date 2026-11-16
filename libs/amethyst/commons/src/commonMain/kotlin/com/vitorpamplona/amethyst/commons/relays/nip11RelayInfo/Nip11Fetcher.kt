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
package com.vitorpamplona.amethyst.commons.relays.nip11RelayInfo

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation

/** Why a NIP-11 document could not be had. */
enum class Nip11ErrorCode {
    FAIL_TO_ASSEMBLE_URL,
    FAIL_TO_REACH_SERVER,
    FAIL_TO_PARSE_RESULT,
    FAIL_WITH_HTTP_STATUS,
}

/** Fetches a relay's NIP-11 document over whatever HTTP stack the platform has. */
interface Nip11Fetcher {
    suspend fun loadRelayInfo(
        relay: NormalizedRelayUrl,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (NormalizedRelayUrl, Nip11ErrorCode, String?) -> Unit,
    )

    /** No network: every fetch fails as unreachable. For hosts that provide no fetcher. */
    object Offline : Nip11Fetcher {
        override suspend fun loadRelayInfo(
            relay: NormalizedRelayUrl,
            onInfo: (Nip11RelayInformation) -> Unit,
            onError: (NormalizedRelayUrl, Nip11ErrorCode, String?) -> Unit,
        ) = onError(relay, Nip11ErrorCode.FAIL_TO_REACH_SERVER, null)
    }
}
