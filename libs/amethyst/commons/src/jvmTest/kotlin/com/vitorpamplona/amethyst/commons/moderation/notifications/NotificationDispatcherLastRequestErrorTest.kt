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
package com.vitorpamplona.amethyst.commons.moderation.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Pins the [NotificationDispatcher.lastRequestError] contract: implementations
 * that never surface OS-level request errors must default to a null-valued
 * flow so the settings UI can rely on the property existing on every
 * dispatcher (avoids `is` checks against NucleusNotificationDispatcher).
 */
class NotificationDispatcherLastRequestErrorTest {
    private class MinimalDispatcher : NotificationDispatcher {
        override val permission: StateFlow<PermissionState> = MutableStateFlow(PermissionState.NotApplicable)
        override val nativeAvailable: StateFlow<Boolean> = MutableStateFlow(false)

        override suspend fun requestPermission(): PermissionState = PermissionState.NotApplicable

        override suspend fun refreshPermission(): PermissionState = PermissionState.NotApplicable

        override suspend fun send(spec: NotificationSpec): SendResult = SendResult.Suppressed("test")

        override fun release() {}
    }

    @Test
    fun `default lastRequestError is null-valued`() {
        assertNull(MinimalDispatcher().lastRequestError.value)
    }
}
