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
package com.vitorpamplona.amethyst.commons.model.nip78AppSpecific

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.AccountSyncedSettingsInternal
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.awaitCreatedAtToSupersede
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class AppSpecificState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    companion object {
        const val APP_SPECIFIC_DATA_D_TAG = "AmethystSettings"
        private const val MAX_TRACKED_SNAPSHOTS = 16
    }

    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val amethystSettingsNote = cache.getOrCreateAddressableNote(getAppSpecificDataAddress())

    fun getAppSpecificDataAddress() = AppSpecificDataEvent.createAddress(signer.pubKey, APP_SPECIFIC_DATA_D_TAG)

    fun getAppSpecificDataFlow(): StateFlow<NoteState> = amethystSettingsNote.flow().metadata.stateFlow

    /**
     * Serializes the state snapshot and the timestamp it is stamped with. Two rapid toggles publish
     * from separate coroutines on the signer's dispatcher; without this they could read the same
     * previous timestamp and collide again, or take timestamps in the opposite order to the state
     * they captured. Signing and encrypting stay outside the lock — those can wait on an external
     * signer, and they don't affect ordering.
     */
    private val stampOrder = Mutex()

    /**
     * The newest version this instance has published, which is not always in [amethystSettingsNote]
     * yet: the cache is only updated once the event comes back through the broadcaster.
     */
    private var lastPublishedAt = 0L

    /**
     * Ids of the versions [saveNewAppSpecificData] built from the live settings, the latest few.
     *
     * When one comes back through the cache its content is a snapshot the live settings already
     * hold, or have moved past: the pickers publish after a debounce, so the echo of one publish
     * can land while newer toggles wait for the next. Merging it would revert those toggles, and
     * the next publish would then read and send the reverted state. So it only becomes the backup.
     * A restore re-signs an older version on purpose and is not one of these: that echo merges.
     *
     * An androidx LruCache, which synchronizes internally: written after signing, read by the collector.
     */
    private val snapshotsSignedHere = LruCache<HexKey, Boolean>(MAX_TRACKED_SNAPSHOTS)

    suspend fun saveNewAppSpecificData(): AppSpecificDataEvent {
        val (toInternal, createdAt) =
            stampOrder.withLock {
                val snapshot = settings.toSyncedSettingsInternal()
                val stamp = awaitCreatedAtToSupersede(maxOf(lastPublishedAt, amethystSettingsNote.event?.createdAt ?: 0L))
                lastPublishedAt = stamp
                snapshot to stamp
            }

        val signed =
            signer.sign(
                AppSpecificDataEvent.build(
                    dTag = APP_SPECIFIC_DATA_D_TAG,
                    description = signer.nip44Encrypt(JsonMapper.toJson(toInternal), signer.pubKey),
                    createdAt = createdAt,
                ),
            )
        snapshotsSignedHere.put(signed.id, true)
        return signed
    }

    init {
        if (settings.isWriteable()) {
            settings.backupAppSpecificData?.let { event ->
                Log.d("AccountRegisterObservers") { "Loading saved app specific data ${event.toJson()}" }
                @OptIn(DelicateCoroutinesApi::class)
                scope.launch(Dispatchers.IO) {
                    LocalCache.justConsumeMyOwnEvent(event)
                    try {
                        val decrypted = signer.decrypt(event.content, event.pubKey)
                        val syncedSettings = JsonMapper.fromJson<AccountSyncedSettingsInternal>(decrypted)
                        settings.syncedSettings.updateFrom(syncedSettings)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences", e)
                    }
                }
            }

            scope.launch(Dispatchers.IO) {
                Log.d("AccountRegisterObservers", "AppSpecificData Collector Start")
                getAppSpecificDataFlow().collect {
                    try {
                        Log.d("AccountRegisterObservers") { "Updating AppSpecificData for ${signer.pubKey}" }
                        (it.note.event as? AppSpecificDataEvent)?.let { event ->
                            // Our own snapshot coming back: see [snapshotsSignedHere]. Nothing to
                            // decrypt either, which spares an external signer a round trip.
                            if (snapshotsSignedHere[event.id] != null) {
                                settings.keepOwnAppSpecificData(event)
                                return@let
                            }

                            // A blob another app emptied has nothing to decrypt, so it used to die
                            // in the catch below — yet an empty version is exactly the wipe the
                            // backup guard exists to question, and [AccountSettings] already
                            // accepts one (`isEmpty`). Without this it never got there: the guard
                            // was never called, no conflict was raised, and the NIP-78 card could
                            // not appear for the case it was written for.
                            //
                            // The settings handed over are never merged — updateAppSpecificData
                            // returns before that whenever the content is empty — so defaults
                            // here cannot overwrite the real ones.
                            if (event.content.isBlank()) {
                                settings.updateAppSpecificData(event, AccountSyncedSettingsInternal())
                                return@let
                            }

                            val decrypted = signer.decrypt(event.content, event.pubKey)
                            try {
                                val syncedSettings = JsonMapper.fromJson<AccountSyncedSettingsInternal>(decrypted)
                                settings.updateAppSpecificData(event, syncedSettings)
                            } catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences", e)
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Log.w("LocalPreferences", "Error Decrypting latestAppSpecificData from Preferences", e)
                    }
                }
            }
        }
    }
}
