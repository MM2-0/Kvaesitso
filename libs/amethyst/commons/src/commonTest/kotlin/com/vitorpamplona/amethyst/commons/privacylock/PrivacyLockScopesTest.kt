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
package com.vitorpamplona.amethyst.commons.privacylock

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Each of the three locks (app, messages, wallet) has its own switch and its own state. */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyLockScopesTest {
    private class ScopedSettings(
        app: Boolean = false,
        messages: Boolean = false,
        wallet: Boolean = false,
    ) : PrivacyLockSettings {
        private val appFlow = MutableStateFlow(app)
        private val messagesFlow = MutableStateFlow(messages)
        private val walletFlow = MutableStateFlow(wallet)
        private val anyFlow = MutableStateFlow(app || messages || wallet)

        override val lockApp: StateFlow<Boolean> = appFlow
        override val lockMessages: StateFlow<Boolean> = messagesFlow
        override val lockWallet: StateFlow<Boolean> = walletFlow
        override val lockEnabled: StateFlow<Boolean> = anyFlow
        override val inactivityTimer = MutableStateFlow(InactivityTimer.Never)
        override val dmRedactionLevel = MutableStateFlow(DmRedactionLevel.Full)
        override val firstRunCardSeen = MutableStateFlow(true)
        override val passwordHashed = MutableStateFlow<String?>(null)
        override val failedUnlockAttempts = MutableStateFlow(0)
        override val lockedUntilEpochMs = MutableStateFlow<Long?>(null)

        override fun setScopeLocked(
            scope: LockScope,
            locked: Boolean,
        ) {
            when (scope) {
                LockScope.App -> appFlow.value = locked
                LockScope.Messages -> messagesFlow.value = locked
                LockScope.Wallet -> walletFlow.value = locked
                LockScope.KeyBackup -> return
            }
            anyFlow.value = appFlow.value || messagesFlow.value || walletFlow.value
        }

        override fun setLockEnabled(enabled: Boolean) = LockScope.entries.forEach { setScopeLocked(it, enabled) }

        override fun setInactivityTimer(timer: InactivityTimer) {}

        override fun setDmRedactionLevel(level: DmRedactionLevel) {}

        override fun setFirstRunCardSeen(seen: Boolean) {}

        override fun setPasswordHashed(saltAndHash: String?) {}

        override fun setFailedUnlockAttempts(count: Int) {
            failedUnlockAttempts.value = count
        }

        override fun setLockedUntilEpochMs(millis: Long?) {
            lockedUntilEpochMs.value = millis
        }
    }

    @Test
    fun onlyTheScopesSwitchedOnStartLocked() =
        runTest {
            val settings = ScopedSettings(messages = true)
            assertEquals(LockState.Disabled, PrivacyLockState(LockScope.App, settings, backgroundScope).state.value)
            assertEquals(LockState.Locked, PrivacyLockState(LockScope.Messages, settings, backgroundScope).state.value)
            assertEquals(LockState.Disabled, PrivacyLockState(LockScope.Wallet, settings, backgroundScope).state.value)
        }

    @Test
    fun turningOneScopeOnLocksOnlyThatScope() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = ScopedSettings()
            val app = PrivacyLockState(LockScope.App, settings, backgroundScope)
            val wallet = PrivacyLockState(LockScope.Wallet, settings, backgroundScope)

            settings.setScopeLocked(LockScope.Wallet, true)

            assertEquals(LockState.Disabled, app.state.value)
            assertEquals(LockState.Locked, wallet.state.value)
        }

    @Test
    fun unlockingOneScopeLeavesTheOthersLocked() =
        runTest {
            val settings = ScopedSettings(app = true, messages = true)
            val app = PrivacyLockState(LockScope.App, settings, backgroundScope)
            val messages = PrivacyLockState(LockScope.Messages, settings, backgroundScope)

            app.onUnlockSuccess()

            assertEquals(LockState.Unlocked, app.state.value)
            assertEquals(LockState.Locked, messages.state.value)
        }

    @Test
    fun keyBackupIsGuardedWhileAnyLockIsOn() =
        runTest {
            assertEquals(LockState.Locked, PrivacyLockState(LockScope.KeyBackup, ScopedSettings(wallet = true), backgroundScope).state.value)
            assertEquals(LockState.Disabled, PrivacyLockState(LockScope.KeyBackup, ScopedSettings(), backgroundScope).state.value)
        }
}
