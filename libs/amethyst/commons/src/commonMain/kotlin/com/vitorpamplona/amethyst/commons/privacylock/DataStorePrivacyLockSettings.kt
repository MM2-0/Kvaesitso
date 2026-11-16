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
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The privacy lock's settings in the app's shared settings DataStore, the same on Android and
 * desktop. Device-wide, not per account: the lock protects the device's session, whoever is logged
 * in. Load it with [load] before the first composition, so a locked app never flashes its content.
 */
@Stable
class DataStorePrivacyLockSettings(
    initial: Snapshot,
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : PrivacyLockSettings {
    /** Everything the lock reads at start. */
    data class Snapshot(
        val lockApp: Boolean = false,
        val lockMessages: Boolean = false,
        val lockWallet: Boolean = false,
        val inactivityTimer: InactivityTimer = InactivityTimer.DEFAULT,
        val passwordHashed: String? = null,
        val failedUnlockAttempts: Int = 0,
        val lockedUntilEpochMs: Long? = null,
    )

    private val mutableLockApp = MutableStateFlow(initial.lockApp)
    private val mutableLockMessages = MutableStateFlow(initial.lockMessages)
    private val mutableLockWallet = MutableStateFlow(initial.lockWallet)
    private val mutableAny = MutableStateFlow(initial.lockApp || initial.lockMessages || initial.lockWallet)
    private val mutableTimer = MutableStateFlow(initial.inactivityTimer)
    private val mutablePassword = MutableStateFlow(initial.passwordHashed)
    private val mutableFailed = MutableStateFlow(initial.failedUnlockAttempts)
    private val mutableLockedUntil = MutableStateFlow(initial.lockedUntilEpochMs)

    override val lockApp: StateFlow<Boolean> = mutableLockApp.asStateFlow()
    override val lockMessages: StateFlow<Boolean> = mutableLockMessages.asStateFlow()
    override val lockWallet: StateFlow<Boolean> = mutableLockWallet.asStateFlow()
    override val lockEnabled: StateFlow<Boolean> = mutableAny.asStateFlow()
    override val inactivityTimer: StateFlow<InactivityTimer> = mutableTimer.asStateFlow()
    override val dmRedactionLevel: StateFlow<DmRedactionLevel> = MutableStateFlow(DmRedactionLevel.Full)
    override val firstRunCardSeen: StateFlow<Boolean> = MutableStateFlow(true)
    override val passwordHashed: StateFlow<String?> = mutablePassword.asStateFlow()
    override val failedUnlockAttempts: StateFlow<Int> = mutableFailed.asStateFlow()
    override val lockedUntilEpochMs: StateFlow<Long?> = mutableLockedUntil.asStateFlow()

    override fun setScopeLocked(
        scope: LockScope,
        locked: Boolean,
    ) {
        when (scope) {
            LockScope.App -> mutableLockApp.value = locked
            LockScope.Messages -> mutableLockMessages.value = locked
            LockScope.Wallet -> mutableLockWallet.value = locked
            LockScope.KeyBackup -> return
        }
        mutableAny.value = mutableLockApp.value || mutableLockMessages.value || mutableLockWallet.value
        write {
            it[LOCK_APP] = mutableLockApp.value
            it[LOCK_MESSAGES] = mutableLockMessages.value
            it[LOCK_WALLET] = mutableLockWallet.value
        }
    }

    /** Turns every lock on or off together; the legacy single switch. */
    override fun setLockEnabled(enabled: Boolean) {
        LockScope.entries.forEach { setScopeLocked(it, enabled) }
    }

    override fun setInactivityTimer(timer: InactivityTimer) {
        mutableTimer.value = timer
        write { it[TIMER] = timer.ordinal }
    }

    override fun setDmRedactionLevel(level: DmRedactionLevel) {}

    override fun setFirstRunCardSeen(seen: Boolean) {}

    override fun setPasswordHashed(saltAndHash: String?) {
        mutablePassword.value = saltAndHash
        write { prefs -> mutablePassword.value.let { if (it != null) prefs[PASSWORD] = it else prefs.remove(PASSWORD) } }
    }

    override fun setFailedUnlockAttempts(count: Int) {
        mutableFailed.value = count
        write { it[FAILED] = mutableFailed.value }
    }

    override fun setLockedUntilEpochMs(millis: Long?) {
        mutableLockedUntil.value = millis
        write { prefs -> mutableLockedUntil.value.let { if (it != null) prefs[LOCKED_UNTIL] = it else prefs.remove(LOCKED_UNTIL) } }
    }

    // Each write stores the value current when it runs, not when it was asked for: two quick
    // changes (a failure, then the reset after a success) cannot land on disk in the wrong order.
    private fun write(block: (MutablePreferences) -> Unit) {
        scope.launch {
            try {
                store.edit { block(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("PrivacyLockSettings", "Could not save the privacy lock settings", e)
            }
        }
    }

    companion object {
        private val LOCK_APP = booleanPreferencesKey("privacyLock.app")
        private val LOCK_MESSAGES = booleanPreferencesKey("privacyLock.messages")
        private val LOCK_WALLET = booleanPreferencesKey("privacyLock.wallet")
        private val TIMER = intPreferencesKey("privacyLock.inactivityTimer")
        private val PASSWORD = stringPreferencesKey("privacyLock.passwordHashed")
        private val FAILED = intPreferencesKey("privacyLock.failedUnlockAttempts")
        private val LOCKED_UNTIL = longPreferencesKey("privacyLock.lockedUntilEpochMs")

        /** Reads the stored settings; on a read error everything starts off. */
        suspend fun load(store: DataStore<Preferences>): Snapshot =
            try {
                val prefs = store.data.first()
                Snapshot(
                    lockApp = prefs[LOCK_APP] ?: false,
                    lockMessages = prefs[LOCK_MESSAGES] ?: false,
                    lockWallet = prefs[LOCK_WALLET] ?: false,
                    inactivityTimer = prefs[TIMER]?.let { InactivityTimer.fromOrdinal(it) } ?: InactivityTimer.DEFAULT,
                    passwordHashed = prefs[PASSWORD],
                    failedUnlockAttempts = prefs[FAILED] ?: 0,
                    lockedUntilEpochMs = prefs[LOCKED_UNTIL],
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("PrivacyLockSettings", "Could not read the privacy lock settings", e)
                Snapshot()
            }

        /** True when nothing has been stored yet, so a one-time migration may fill it. */
        suspend fun isEmpty(store: DataStore<Preferences>): Boolean =
            try {
                val prefs = store.data.first()
                listOf(LOCK_APP, LOCK_MESSAGES, LOCK_WALLET, TIMER, PASSWORD).none { prefs.contains(it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
    }
}
