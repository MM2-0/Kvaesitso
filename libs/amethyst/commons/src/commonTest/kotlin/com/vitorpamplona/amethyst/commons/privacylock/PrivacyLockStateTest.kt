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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyLockStateTest {
    private class FakeSettings(
        lockEnabled: Boolean = false,
        timer: InactivityTimer = InactivityTimer.OneMin,
        password: String? = null,
    ) : PrivacyLockSettings {
        private val mutableLockEnabled = MutableStateFlow(lockEnabled)
        private val mutableTimer = MutableStateFlow(timer)
        private val mutableRedaction = MutableStateFlow(DmRedactionLevel.DEFAULT)
        private val mutableFirstRunSeen = MutableStateFlow(false)
        private val mutablePasswordHashed = MutableStateFlow<String?>(password)
        private val mutableFailedAttempts = MutableStateFlow(0)
        private val mutableLockedUntil = MutableStateFlow<Long?>(null)

        override val lockEnabled: StateFlow<Boolean> = mutableLockEnabled.asStateFlow()
        override val inactivityTimer: StateFlow<InactivityTimer> = mutableTimer.asStateFlow()
        override val dmRedactionLevel: StateFlow<DmRedactionLevel> = mutableRedaction.asStateFlow()
        override val firstRunCardSeen: StateFlow<Boolean> = mutableFirstRunSeen.asStateFlow()
        override val passwordHashed: StateFlow<String?> = mutablePasswordHashed.asStateFlow()
        override val failedUnlockAttempts: StateFlow<Int> = mutableFailedAttempts.asStateFlow()
        override val lockedUntilEpochMs: StateFlow<Long?> = mutableLockedUntil.asStateFlow()

        override fun setLockEnabled(enabled: Boolean) {
            mutableLockEnabled.value = enabled
        }

        override fun setInactivityTimer(timer: InactivityTimer) {
            mutableTimer.value = timer
        }

        override fun setDmRedactionLevel(level: DmRedactionLevel) {
            mutableRedaction.value = level
        }

        override fun setFirstRunCardSeen(seen: Boolean) {
            mutableFirstRunSeen.value = seen
        }

        override fun setPasswordHashed(saltAndHash: String?) {
            mutablePasswordHashed.value = saltAndHash
            // Mirror the production cascade — no credential means no gate.
            if (saltAndHash == null && mutableLockEnabled.value) mutableLockEnabled.value = false
        }

        override fun setFailedUnlockAttempts(count: Int) {
            mutableFailedAttempts.value = count
        }

        override fun setLockedUntilEpochMs(millis: Long?) {
            mutableLockedUntil.value = millis
        }
    }

    @Test
    fun cold_start_with_lock_enabled_seeds_to_locked() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun cold_start_with_lock_disabled_seeds_to_disabled() =
        runTest {
            val settings = FakeSettings(lockEnabled = false)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            assertEquals(LockState.Disabled, state.state.value)
        }

    @Test
    fun unlock_success_transitions_to_unlocked_and_idle_timer_fires() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.OneMin)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            state.onUnlockSuccess()
            assertEquals(LockState.Unlocked, state.state.value)
            advanceTimeBy(InactivityTimer.OneMin.millis!! + 1_000L)
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun leave_route_locks_immediately() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.OneHour)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            state.onUnlockSuccess()
            assertEquals(LockState.Unlocked, state.state.value)
            state.onLeaveRoute()
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun toggling_lock_off_transitions_to_disabled() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = FakeSettings(lockEnabled = true)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            state.onUnlockSuccess()
            assertEquals(LockState.Unlocked, state.state.value)
            settings.setLockEnabled(false)
            assertEquals(LockState.Disabled, state.state.value)
        }

    @Test
    fun never_timer_does_not_fire() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.Never)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            state.onUnlockSuccess()
            advanceTimeBy(InactivityTimer.OneHour.millis!! * 2)
            assertEquals(LockState.Unlocked, state.state.value)
        }

    @Test
    fun user_interaction_resets_idle_timer() =
        runTest {
            val settings = FakeSettings(lockEnabled = true, timer = InactivityTimer.OneMin)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            state.onUnlockSuccess()
            advanceTimeBy(InactivityTimer.OneMin.millis!! - 1_000L)
            state.onUserInteraction()
            advanceTimeBy(InactivityTimer.OneMin.millis - 1_000L)
            assertTrue(state.state.value is LockState.Unlocked)
            advanceTimeBy(2_000L)
            assertEquals(LockState.Locked, state.state.value)
        }

    @Test
    fun credential_unavailable_disables_lock() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            state.onCredentialUnavailable()
            assertEquals(LockState.Disabled, state.state.value)
            assertEquals(false, settings.lockEnabled.value)
        }

    @Test
    fun unlock_success_from_disabled_transitions_to_unlocked() =
        runTest {
            // First-run banner path: user enables lock + sets password while
            // already viewing a gated route. State is Disabled at that moment,
            // and we want to stay Unlocked so the user isn't kicked to the lock
            // screen right after enabling.
            val settings = FakeSettings(lockEnabled = false)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            assertEquals(LockState.Disabled, state.state.value)
            state.onUnlockSuccess()
            assertEquals(LockState.Unlocked, state.state.value)
        }

    @Test
    fun failed_attempts_below_threshold_do_not_trip_lockout() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            val now = 1_000_000L
            repeat(PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES - 1) {
                assertEquals(null, state.onFailedUnlockAttempt(now))
            }
            assertEquals(null, settings.lockedUntilEpochMs.value)
            assertEquals(
                PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES - 1,
                settings.failedUnlockAttempts.value,
            )
        }

    @Test
    fun fifth_failure_trips_base_lockout() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            val now = 1_000_000L
            repeat(PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES) {
                state.onFailedUnlockAttempt(now)
            }
            val expected = now + PrivacyLockSettings.LOCKOUT_BASE_MS
            assertEquals(expected, settings.lockedUntilEpochMs.value)
        }

    @Test
    fun lockout_doubles_and_caps_at_maximum() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            val now = 1_000_000L
            // 5th failure → base (30s)
            repeat(PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES) { state.onFailedUnlockAttempt(now) }
            val base = settings.lockedUntilEpochMs.value!! - now
            assertEquals(PrivacyLockSettings.LOCKOUT_BASE_MS, base)
            // 6th → doubles to 60s
            state.onFailedUnlockAttempt(now)
            assertEquals(PrivacyLockSettings.LOCKOUT_BASE_MS * 2, settings.lockedUntilEpochMs.value!! - now)
            // Enough further failures to hit the cap
            repeat(20) { state.onFailedUnlockAttempt(now) }
            assertEquals(PrivacyLockSettings.LOCKOUT_MAX_MS, settings.lockedUntilEpochMs.value!! - now)
        }

    @Test
    fun unlock_success_clears_backoff_state() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val state = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            val now = 1_000_000L
            repeat(PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES) { state.onFailedUnlockAttempt(now) }
            assertTrue(settings.lockedUntilEpochMs.value != null)
            assertTrue(settings.failedUnlockAttempts.value > 0)
            state.onUnlockSuccess()
            assertEquals(null, settings.lockedUntilEpochMs.value)
            assertEquals(0, settings.failedUnlockAttempts.value)
        }

    // ---- Wallet-lock reuse additions ----

    @Test
    fun two_scopes_have_independent_lock_state() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = FakeSettings(lockEnabled = true)
            val messages = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            val wallet = PrivacyLockState(LockScope.Wallet, settings, backgroundScope)
            assertEquals(LockState.Locked, messages.state.value)
            assertEquals(LockState.Locked, wallet.state.value)

            messages.onUnlockSuccess()
            assertEquals(LockState.Unlocked, messages.state.value)
            assertEquals(LockState.Locked, wallet.state.value)

            messages.onLeaveRoute()
            assertEquals(LockState.Locked, messages.state.value)
            assertEquals(LockState.Locked, wallet.state.value)
        }

    @Test
    fun failed_unlock_counter_is_shared_across_scopes() =
        runTest {
            val settings = FakeSettings(lockEnabled = true)
            val messages = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            val wallet = PrivacyLockState(LockScope.Wallet, settings, backgroundScope)
            val now = 1_000_000L
            // Three failures on Messages, two on Wallet → shared counter hits 5
            repeat(3) { messages.onFailedUnlockAttempt(now) }
            repeat(2) { wallet.onFailedUnlockAttempt(now) }
            assertEquals(
                PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES,
                settings.failedUnlockAttempts.value,
            )
            // The 5th failure trips the base lockout regardless of which scope
            // it came from — either scope now sees the countdown.
            assertEquals(
                now + PrivacyLockSettings.LOCKOUT_BASE_MS,
                settings.lockedUntilEpochMs.value,
            )
        }

    @Test
    fun clearing_password_cascades_to_disable_the_master_lock() =
        runTest(UnconfinedTestDispatcher()) {
            val settings = FakeSettings(lockEnabled = true, password = "salt\$hash")
            val messages = PrivacyLockState(LockScope.Messages, settings, backgroundScope) { testScheduler.currentTime }
            val wallet = PrivacyLockState(LockScope.Wallet, settings, backgroundScope)
            assertEquals(LockState.Locked, messages.state.value)
            assertEquals(LockState.Locked, wallet.state.value)

            // User clears the password from Settings → cascade fires
            settings.setPasswordHashed(null)

            assertEquals(false, settings.lockEnabled.value)
            assertEquals(LockState.Disabled, messages.state.value)
            assertEquals(LockState.Disabled, wallet.state.value)
            assertNull(settings.passwordHashed.value)
        }
}
