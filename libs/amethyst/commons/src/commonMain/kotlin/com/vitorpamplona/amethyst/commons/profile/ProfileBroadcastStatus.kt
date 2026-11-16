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
package com.vitorpamplona.amethyst.commons.profile

/**
 * Status of profile metadata broadcast to relays.
 */
sealed class ProfileBroadcastStatus {
    data object Idle : ProfileBroadcastStatus()

    data class Broadcasting(
        val fieldName: String,
        val successCount: Int,
        val totalRelays: Int,
    ) : ProfileBroadcastStatus() {
        val progress: Float get() = if (totalRelays > 0) successCount.toFloat() / totalRelays else 0f
    }

    data class Success(
        val fieldName: String,
        val relayCount: Int,
    ) : ProfileBroadcastStatus()

    data class Failed(
        val fieldName: String,
        val error: String,
    ) : ProfileBroadcastStatus()
}
