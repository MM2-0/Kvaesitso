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

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * User-tunable settings for the Messages privacy lock.
 *
 * Device-global per the brainstorm decision (no NIP-78 sync, no per-account
 * variants). Platform implementations back this with `java.util.prefs`
 * (Desktop) or `SharedPreferences` (Android). Initial values must be read
 * synchronously from storage in the constructor so the first `setContent { }`
 * sees the seeded state — required to close the deep-link race fix (security
 * hardening H1 in the plan).
 */
@Stable
interface PrivacyLockSettings {
    /** True while any lock is on. */
    val lockEnabled: StateFlow<Boolean>

    /** Locks the whole app on start and after the idle timeout. */
    val lockApp: StateFlow<Boolean> get() = NEVER_LOCKED

    /** Locks the private messages: the conversation list and every chat room. */
    val lockMessages: StateFlow<Boolean> get() = lockEnabled

    /** Locks the wallet screens. */
    val lockWallet: StateFlow<Boolean> get() = lockEnabled

    /** Turns the lock for [scope] on or off. */
    fun setScopeLocked(
        scope: LockScope,
        locked: Boolean,
    ) = setLockEnabled(locked)

    val inactivityTimer: StateFlow<InactivityTimer>
    val dmRedactionLevel: StateFlow<DmRedactionLevel>
    val firstRunCardSeen: StateFlow<Boolean>

    /**
     * Non-null when the user has set a password on this device. Value is
     * either `salt$hash` (legacy, 100k PBKDF2 iterations) or `v1$salt$hash`
     * (current, 600k iterations) — never a raw password.
     * Platforms may use this differently: Android does not use it today
     * (biometric is authoritative); Desktop uses it as the unlock gate.
     */
    val passwordHashed: StateFlow<String?>

    /**
     * Number of consecutive failed unlock attempts. Reset to 0 on successful
     * unlock. Persisted across app restarts so a reboot cannot reset the
     * exponential backoff (see [lockedUntilEpochMs]).
     */
    val failedUnlockAttempts: StateFlow<Int>

    /**
     * Epoch millis until which the user is locked out from attempting to
     * unlock. `null` when no lockout is active. Persists across app
     * restarts. Trip point is 5 consecutive failures; schedule doubles
     * each subsequent failure and caps at 5 minutes.
     */
    val lockedUntilEpochMs: StateFlow<Long?>

    fun setLockEnabled(enabled: Boolean)

    fun setInactivityTimer(timer: InactivityTimer)

    fun setDmRedactionLevel(level: DmRedactionLevel)

    fun setFirstRunCardSeen(seen: Boolean)

    /** Store a versioned or legacy hash string; pass `null` to clear. */
    fun setPasswordHashed(saltAndHash: String?)

    fun setFailedUnlockAttempts(count: Int)

    fun setLockedUntilEpochMs(millis: Long?)

    companion object {
        private val NEVER_LOCKED: StateFlow<Boolean> = MutableStateFlow(false)

        const val DEFAULT_LOCK_ENABLED = false
        const val NODE_NAME = "com/vitorpamplona/amethyst/privacylock"
        const val KEY_LOCK_ENABLED = "lock_enabled"
        const val KEY_INACTIVITY_TIMER = "inactivity_timer_ordinal"
        const val KEY_REDACTION_LEVEL = "redaction_level_ordinal"
        const val KEY_FIRST_RUN_CARD_SEEN = "first_run_card_seen"
        const val KEY_PASSWORD_HASHED = "password_hashed"
        const val KEY_FAILED_UNLOCK_ATTEMPTS = "failed_unlock_attempts"
        const val KEY_LOCKED_UNTIL_EPOCH_MS = "locked_until_epoch_ms"

        /** Threshold at which exponential backoff begins. */
        const val LOCKOUT_TRIP_AFTER_FAILURES = 5

        /** Base lockout on the [LOCKOUT_TRIP_AFTER_FAILURES]th failure. Doubles each subsequent failure. */
        const val LOCKOUT_BASE_MS = 30_000L

        /** Max lockout duration after repeated failures. */
        const val LOCKOUT_MAX_MS = 5L * 60_000L
    }
}

/** The switch that guards [scope]; revealing the key backup is guarded while any lock is on. */
fun PrivacyLockSettings.enabledFor(scope: LockScope): StateFlow<Boolean> =
    when (scope) {
        LockScope.App -> lockApp
        LockScope.Messages -> lockMessages
        LockScope.Wallet -> lockWallet
        LockScope.KeyBackup -> lockEnabled
    }

/**
 * Counts a failed unlock and, from [PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES] on, refuses
 * unlocking for a while: [PrivacyLockSettings.LOCKOUT_BASE_MS], doubling with each further failure,
 * at most [PrivacyLockSettings.LOCKOUT_MAX_MS]. Every path that checks a password goes through here
 * (the lock screen and the settings screen alike), so none of them can be used to guess without it.
 * Returns when the lockout ends, or null when there is none yet.
 */
fun PrivacyLockSettings.recordFailedUnlock(nowMs: Long): Long? {
    val next = failedUnlockAttempts.value + 1
    setFailedUnlockAttempts(next)
    val overshoot = next - PrivacyLockSettings.LOCKOUT_TRIP_AFTER_FAILURES
    if (overshoot < 0) {
        setLockedUntilEpochMs(null)
        return null
    }
    // Capped before shifting: a long run of failures would otherwise overflow back to no lockout.
    val duration = (PrivacyLockSettings.LOCKOUT_BASE_MS shl overshoot.coerceAtMost(MAX_LOCKOUT_DOUBLINGS)).coerceAtMost(PrivacyLockSettings.LOCKOUT_MAX_MS)
    val until = nowMs + duration
    setLockedUntilEpochMs(until)
    return until
}

private const val MAX_LOCKOUT_DOUBLINGS = 16

/** Clears the failed-attempt counter and any lockout, after a successful unlock. */
fun PrivacyLockSettings.resetFailedUnlocks() {
    if (failedUnlockAttempts.value != 0) setFailedUnlockAttempts(0)
    if (lockedUntilEpochMs.value != null) setLockedUntilEpochMs(null)
}

/** Whether unlocking is refused right now after too many failures. */
fun PrivacyLockSettings.isLockedOut(nowMs: Long): Boolean = (lockedUntilEpochMs.value ?: 0L) > nowMs
