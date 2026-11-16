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
package com.vitorpamplona.amethyst.commons.relays.health

/**
 * Per-relay latency signals that feed the slow-relay classifier.
 *
 * - [OK_ACK]: time from EVENT sent → relay's OK message for that event_id (NIP-01).
 * - [EOSE]: time from REQ sent → relay's EOSE for that sub_id.
 * - [FIRST_RESULT]: time from REQ sent → first matching EVENT for that sub_id. Filter-dependent;
 *   labelled "First result" in the UI with a tooltip caveat — see the deepened plan.
 * - [PING]: per-(re)connect TCP/WS handshake time captured by [com.vitorpamplona.quartz
 *   .nip01Core.relay.client.listeners.RelayConnectionListener.onConnected].
 */
enum class LatencyMetric {
    OK_ACK,
    EOSE,
    FIRST_RESULT,
    PING,
}
