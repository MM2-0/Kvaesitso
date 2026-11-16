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

import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * App-global state holder for a single privacy-lock [scope].
 *
 * One instance per gated route (Messages, Wallet, …) is provided via
 * [LocalPrivacyLockState] at the App composition root. All instances share
 * the same [PrivacyLockSettings], but each scope has its own switch
 * ([enabledFor]), its own [LockState] and its
 * own idle-timer [Job] so unlock, leave-route, and inactivity transitions
 * apply independently per route.
 *
 * - Initial value is seeded synchronously from the scope's switch
 *   so the first composition sees [LockState.Locked] without flashing
 *   content (deep-link race fix, plan §Security Hardening H1).
 * - The underlying StateFlow is hot (`MutableStateFlow`); notification path
 *   can read `state.value` synchronously without subscribing.
 */
class PrivacyLockState(
    val scope: LockScope,
    private val settings: PrivacyLockSettings,
    private val coroutineScope: CoroutineScope,
    /** Wall-clock millis; tests pass their virtual clock. */
    private val nowMs: () -> Long = { TimeUtils.nowMillis() },
) {
    private val enabled = settings.enabledFor(scope)

    private val seed: LockState =
        if (enabled.value) LockState.Locked else LockState.Disabled

    private val mutableState = MutableStateFlow(seed)
    val state: StateFlow<LockState> = mutableState.asStateFlow()

    /** Until when unlocking is refused after too many failures; shared by every scope. */
    val lockedUntilEpochMs: StateFlow<Long?> get() = settings.lockedUntilEpochMs

    private var idleTimerJob: Job? = null

    /** When the user last touched, typed or clicked; the idle timer counts from here. */
    private var lastInteractionMs = 0L

    init {
        enabled
            .onEach { enabled ->
                if (!enabled) {
                    cancelIdleTimer()
                    mutableState.value = LockState.Disabled
                } else if (mutableState.value is LockState.Disabled) {
                    mutableState.value = LockState.Locked
                }
            }.launchIn(coroutineScope)

        combine(enabled, settings.inactivityTimer) { enabled, timer -> enabled to timer }
            .onEach { _ ->
                if (mutableState.value is LockState.Unlocked) restartIdleTimer()
            }.launchIn(coroutineScope)
    }

    /**
     * Resets the inactivity timer. No-op unless currently Unlocked. Called for every pointer move and
     * key press, so it only records the time: the running timer re-arms itself from it.
     */
    fun onUserInteraction() {
        if (mutableState.value !is LockState.Unlocked) return
        lastInteractionMs = nowMs()
        if (idleTimerJob?.isActive != true) restartIdleTimer()
    }

    /** Re-lock immediately on route exit or account switch. Idempotent. */
    fun onLeaveRoute() {
        if (mutableState.value is LockState.Unlocked) {
            cancelIdleTimer()
            mutableState.value = LockState.Locked
        }
    }

    /**
     * Mark the session as authenticated. Transitions from either
     * [LockState.Locked] (normal unlock path) or [LockState.Disabled]
     * (first-run banner path — enabling the lock while the user is
     * actively in a gated route should NOT flash the lock screen).
     * No-op if already [LockState.Unlocked]. Starts the idle timer.
     */
    fun onUnlockSuccess() {
        if (mutableState.value !is LockState.Unlocked) {
            mutableState.value = LockState.Unlocked
            restartIdleTimer()
        }
        // Always clear failed-attempt state on any authenticated flow — even
        // when transitioning from Disabled (banner "enable" path).
        onUnlockAttemptResetToZero()
    }

    /**
     * Triggered when biometric / OS credential is permanently unavailable.
     * Auto-disables the lock (flips every scope to [LockState.Disabled]
     * via the shared setting) so the user can keep accessing gated routes.
     */
    fun onCredentialUnavailable() {
        cancelIdleTimer()
        settings.setLockEnabled(false)
        mutableState.value = LockState.Disabled
    }

    /**
     * Record a failed unlock attempt. Applies exponential backoff after
     * [PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES] failures: base 30 s,
     * doubling each further failure, capped at 5 min.
     *
     * Backoff state is shared across scopes — a mistyped password on the
     * Wallet gate locks out the Messages gate too (and vice versa). This is
     * intentional anti-brute-force behaviour.
     *
     * @param nowMs current epoch millis (injected for testability).
     * @return the new [PrivacyLockSettings.lockedUntilEpochMs] value, or
     *   null when no lockout yet applies.
     */
    fun onFailedUnlockAttempt(nowMs: Long): Long? = settings.recordFailedUnlock(nowMs)

    /** Clear the failed-attempt counter and any active lockout. */
    fun onUnlockAttemptResetToZero() = settings.resetFailedUnlocks()

    private fun restartIdleTimer() {
        cancelIdleTimer()
        lastInteractionMs = nowMs()
        val millis = settings.inactivityTimer.value.millis ?: return
        idleTimerJob =
            coroutineScope.launch {
                // Sleeps until the idle time is up, counted from the latest interaction.
                while (true) {
                    val left = lastInteractionMs + millis - nowMs()
                    if (left <= 0) break
                    delay(left)
                }
                if (mutableState.value is LockState.Unlocked) {
                    mutableState.value = LockState.Locked
                }
            }
    }

    private fun cancelIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = null
    }
}
