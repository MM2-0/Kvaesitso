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
package com.vitorpamplona.amethyst.model.accountsCache

import com.vitorpamplona.marmot.appComponents.agentTextStream.transport.MarmotQuicTransport
import com.vitorpamplona.marmot.quic.QuicAgentTextStreamTransport
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import kotlinx.coroutines.CoroutineScope

/**
 * The app's raw-QUIC transport for Marmot agent text stream previews (`transports/quic.md`).
 * Preview brokers are commonly self-signed and the binding expects that; the platform trust
 * store is still the default answer, and a deployment that pins does it here.
 */
fun defaultMarmotStreamTransport(scope: CoroutineScope): MarmotQuicTransport =
    QuicAgentTextStreamTransport(
        parentScope = scope,
        certificateValidator = JdkCertificateValidator(),
    )
