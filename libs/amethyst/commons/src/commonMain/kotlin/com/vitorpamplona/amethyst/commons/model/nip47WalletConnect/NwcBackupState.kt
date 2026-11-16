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
package com.vitorpamplona.amethyst.commons.model.nip47WalletConnect

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Opt-in NWC wallet backup to relays (see [NwcBackupFormat]). Writeability is asked of
 * the signer, not AccountSettings: bunker accounts hold no local key yet can sign.
 */
class NwcBackupState(
    private val signer: NostrSigner,
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val settings: AccountSettings,
    private val outboxRelaysFlow: StateFlow<Set<NormalizedRelayUrl>>,
    /** Restore also reads these: other apps write their backups to read relays too. */
    private val readRelaysFlow: StateFlow<Set<NormalizedRelayUrl>>,
) {
    sealed interface Failure {
        data object CannotSign : Failure

        data object NoRelays : Failure

        data class Rejected(
            val reason: String?,
        ) : Failure

        data class Error(
            val message: String?,
        ) : Failure
    }

    sealed interface Status {
        data object Idle : Status

        data object Publishing : Status

        data object NothingToBackUp : Status

        data class Published(
            val walletCount: Int,
            val relays: List<String>,
        ) : Status

        data class Failed(
            val failure: Failure,
        ) : Status
    }

    sealed interface DeleteOutcome {
        data class Deleted(
            val relayCount: Int,
        ) : DeleteOutcome

        data class PartiallyDeleted(
            val deletedCount: Int,
            val remaining: List<String>,
        ) : DeleteOutcome

        data class Failed(
            val failure: Failure,
        ) : DeleteOutcome
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var started = false
    private val jobs = mutableListOf<Job>()

    /** Serializes publishes and deletes so a queued publish can't resurrect a deleted backup. */
    private val opMutex = Mutex()

    private var lastPublishedJson: String? = null

    /** Call only after Account's initializers complete, so a publish can't land on a half-built Account. */
    fun start() {
        if (started) return
        started = true

        jobs +=
            scope.launch(Dispatchers.IO) {
                @OptIn(kotlinx.coroutines.FlowPreview::class)
                combine(
                    settings.backupNwcWallets,
                    settings.nwcWallets,
                    settings.defaultPaymentSourceId,
                ) { enabled, wallets, defaultId ->
                    // Null when disabled, so the debounced emission after opting out can't republish.
                    if (enabled) {
                        Snapshot(
                            json = NwcBackupFormat.encode(wallets, defaultId?.takeIf { id -> wallets.any { it.id == id } }),
                            walletIds = wallets.mapTo(HashSet()) { it.id },
                        )
                    } else {
                        null
                    }
                }
                    // A relaunch must not sign a fresh event for an unchanged wallet list.
                    .drop(1)
                    .debounce(PUBLISH_DEBOUNCE_MS)
                    .collect { snapshot ->
                        snapshot?.let { publishBackup(it) }
                    }
            }
    }

    fun destroy() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    suspend fun publishNow(): Boolean = publishBackup(currentSnapshot())

    suspend fun republishNow(): Boolean {
        lastPublishedJson = null
        return publishBackup(currentSnapshot())
    }

    /** Only relays that confirm the tombstone leave [AccountSettings.nwcBackupRelays]; the rest stay listed. */
    suspend fun deleteBackup(): DeleteOutcome =
        opMutex.withLock {
            deleteBackupLocked()
        }

    private suspend fun deleteBackupLocked(): DeleteOutcome {
        if (!signer.isWriteable()) return fail(Failure.CannotSign)

        val recorded = settings.nwcBackupRelays.value
        val targets = recorded.mapNotNullTo(LinkedHashSet()) { RelayUrlNormalizer.normalizeOrNull(it) } + awaitOutboxRelays(RELAY_READY_WAIT_MS)
        if (targets.isEmpty()) return fail(Failure.NoRelays)

        return try {
            val tombstone =
                signer.sign(
                    AppSpecificDataEvent.build(dTag = NwcBackupFormat.D_TAG, description = "") {
                        add(DELETED_TAG)
                    },
                )
            val results = client.publishAndCollectResults(tombstone, targets, timeoutInSeconds = PUBLISH_CONFIRM_TIMEOUT_S)
            val confirmed = results.filterValues { it.accepted || it.isDuplicate() }.keys
            if (confirmed.isEmpty()) {
                Log.w(TAG) { "NWC backup deletion confirmed by no relay (${targets.size} tried): " + describe(results) }
                return fail(Failure.Rejected(firstRelayReason(results)))
            }

            client.publish(signer.sign(DeletionRequestEvent.build(listOf(tombstone))), targets)
            lastPublishedJson = null

            val confirmedUrls = confirmed.mapTo(HashSet()) { it.url }
            val remaining = recorded.filter { (RelayUrlNormalizer.normalizeOrNull(it)?.url ?: it) !in confirmedUrls }
            settings.updateNwcBackupRelays(remaining)
            _status.value = Status.Idle

            if (remaining.isEmpty()) {
                settings.updateBackedUpNwcWalletIds(emptySet())
                DeleteOutcome.Deleted(confirmed.size)
            } else {
                Log.w(TAG) { "NWC backup still on ${remaining.size} relay(s) after deletion: " + describe(results) }
                DeleteOutcome.PartiallyDeleted(confirmed.size, remaining)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to delete NWC backup", e)
            fail(Failure.Error(e.message))
        }
    }

    private fun fail(failure: Failure): DeleteOutcome {
        _status.value = Status.Failed(failure)
        return DeleteOutcome.Failed(failure)
    }

    data class RestorableWallet(
        val wallet: NwcWalletEntry,
        val dTag: String,
        val savedAt: Long,
    )

    data class FoundNwcBackup(
        val wallets: List<RestorableWallet>,
        val defaultId: String?,
    )

    /**
     * True when a live (not deleted) backup sits on the user's relays, false when none does, and null
     * when the relays could not be asked (none known yet, or the fetch failed). Never decrypts, so it
     * needs no signer prompt.
     */
    suspend fun hasBackup(): Boolean? = fetchLiveBackupEvents()?.isNotEmpty()

    /** Reads Wisp, Sidecar and zap.cooking backups too. Does not merge: the user confirms what to restore. */
    suspend fun fetchBackup(): FoundNwcBackup? {
        val wallets = ArrayList<RestorableWallet>()
        val seen = HashSet<Nip47WalletConnect.Nip47URINorm>()
        var defaultId: String? = null
        for (event in fetchLiveBackupEvents().orEmpty()) {
            val payload = decryptAndDecode(event) ?: continue
            val dTag = NwcBackupFormat.dTagOf(event.tags) ?: continue
            if (dTag == NwcBackupFormat.D_TAG) defaultId = payload.defaultId
            payload.wallets.forEach { entry ->
                val key = entry.uri.normalize() ?: return@forEach
                if (seen.add(key)) wallets += RestorableWallet(entry, dTag, event.createdAt)
            }
        }
        if (wallets.isEmpty()) return null
        return FoundNwcBackup(
            wallets = wallets,
            defaultId = defaultId?.takeIf { id -> wallets.any { it.wallet.id == id } },
        )
    }

    /** The live backup events on the user's relays, or null when the relays could not be asked. */
    private suspend fun fetchLiveBackupEvents(): List<Event>? {
        val relays = awaitOutboxRelays(RELAY_READY_WAIT_MS) + readRelaysFlow.value
        if (relays.isEmpty()) {
            Log.w(TAG) { "Outbox relays unavailable; cannot look for a backup yet" }
            return null
        }

        // A REQ sent into a still-connecting socket reads as an empty backup, so wait for a live one.
        relays.forEach { client.keepConnected(it) }
        try {
            withTimeoutOrNull(RELAY_CONNECT_WAIT_MS) {
                client.connectedRelaysFlow().first { connected -> relays.any { it in connected } }
            } ?: Log.w(TAG) { "No relay connected within ${RELAY_CONNECT_WAIT_MS}ms; asking anyway" }

            // One filter per d-tag so limit = 1 yields the newest event for each app.
            val filters =
                NwcBackupFormat.RESTORE_D_TAGS.map { dTag ->
                    Filter(
                        kinds = listOf(AppSpecificDataEvent.KIND),
                        authors = listOf(signer.pubKey),
                        tags = mapOf("d" to listOf(dTag)),
                        limit = 1,
                    )
                }

            val events =
                try {
                    client.fetchAll(
                        filters = relays.associateWith { filters },
                        idleTimeoutMs = RESTORE_IDLE_TIMEOUT_MS,
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Failed to fetch the NWC backup", e)
                    return null
                }.filter { it.pubKey == signer.pubKey }

            return NwcBackupFormat.liveBackups(events)
        } finally {
            relays.forEach { client.releaseConnection(it) }
        }
    }

    private suspend fun decryptAndDecode(event: Event): NwcBackupPayload? =
        try {
            // decrypt also handles zap.cooking's older NIP-04 backups.
            NwcBackupFormat.decodePlaintext(signer.decrypt(event.content, event.pubKey))
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to decrypt NWC backup ${event.id.take(8)}", e)
            null
        }

    private class Snapshot(
        val json: String,
        val walletIds: Set<String>,
    )

    private fun currentSnapshot(): Snapshot {
        val wallets = settings.nwcWallets.value
        val defaultId = settings.defaultPaymentSourceId.value?.takeIf { id -> wallets.any { it.id == id } }
        return Snapshot(
            json = NwcBackupFormat.encode(wallets, defaultId),
            walletIds = wallets.mapTo(HashSet()) { it.id },
        )
    }

    private suspend fun publishBackup(snapshot: Snapshot): Boolean =
        opMutex.withLock {
            if (!settings.backupNwcWallets.value) {
                Log.i(TAG) { "Backup disabled while this publish waited; skipping" }
                return@withLock true
            }
            publishBackupLocked(snapshot)
        }

    private suspend fun publishBackupLocked(snapshot: Snapshot): Boolean {
        // Never publish an empty list: the last real backup must stay restorable.
        if (snapshot.walletIds.isEmpty()) {
            Log.i(TAG) { "No NWC wallets left; keeping the last published backup" }
            _status.value = Status.NothingToBackUp
            return true
        }
        if (!signer.isWriteable()) {
            _status.value = Status.Failed(Failure.CannotSign)
            return false
        }
        if (snapshot.json == lastPublishedJson) return true

        val relays = awaitOutboxRelays(RELAY_READY_WAIT_MS)
        if (relays.isEmpty()) {
            _status.value = Status.Failed(Failure.NoRelays)
            return false
        }

        _status.value = Status.Publishing
        try {
            val encrypted = signer.nip44Encrypt(snapshot.json, signer.pubKey)
            val event =
                signer.sign(
                    AppSpecificDataEvent.build(dTag = NwcBackupFormat.D_TAG, description = encrypted) {
                        add(NwcBackupFormat.contentTag())
                    },
                )
            val results = client.publishAndCollectResults(event, relays, timeoutInSeconds = PUBLISH_CONFIRM_TIMEOUT_S)
            // duplicate/replaced: the relay already holds this or a newer backup.
            val confirmed = results.filterValues { it.accepted || it.isDuplicate() || it.message.startsWith("replaced", ignoreCase = true) }.keys
            if (confirmed.isEmpty()) {
                Log.w(TAG) { "NWC backup rejected by every relay (${relays.size} tried): " + describe(results) }
                _status.value = Status.Failed(Failure.Rejected(firstRelayReason(results)))
                return false
            }

            lastPublishedJson = snapshot.json
            val confirmedUrls = confirmed.map { it.url }.sorted()
            // Earlier relays still hold an older copy, so they stay recorded until a delete.
            settings.updateNwcBackupRelays((settings.nwcBackupRelays.value + confirmedUrls).distinct().sorted())
            settings.updateBackedUpNwcWalletIds(snapshot.walletIds)
            _status.value = Status.Published(walletCount = snapshot.walletIds.size, relays = confirmedUrls)
            Log.i(TAG) { "Published NWC backup for ${signer.pubKey.take(8)} (${snapshot.walletIds.size} wallets, ${confirmed.size} relay(s) confirmed)" }
            return true
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to publish NWC backup", e)
            _status.value = Status.Failed(Failure.Error(e.message))
            return false
        }
    }

    private suspend fun awaitOutboxRelays(timeoutMs: Long): Set<NormalizedRelayUrl> =
        outboxRelaysFlow.value.ifEmpty {
            withTimeoutOrNull(timeoutMs) {
                outboxRelaysFlow.first { it.isNotEmpty() }
            } ?: emptySet()
        }

    private fun PublishResult.isDuplicate() = message.startsWith("duplicate", ignoreCase = true)

    private fun firstRelayReason(results: Map<NormalizedRelayUrl, PublishResult>): String? =
        (results.values.firstOrNull { !it.isTransportFailure } ?: results.values.firstOrNull())
            ?.message
            ?.take(MAX_REASON_LENGTH)

    private fun describe(results: Map<NormalizedRelayUrl, PublishResult>): String = results.entries.joinToString("; ") { "${it.key.url}: ${it.value.message}" }

    companion object {
        private const val TAG = "NwcBackup"

        private const val PUBLISH_DEBOUNCE_MS = 2_000L

        private const val RESTORE_IDLE_TIMEOUT_MS = 10_000L

        private const val RELAY_READY_WAIT_MS = 5_000L

        private const val RELAY_CONNECT_WAIT_MS = 10_000L

        private const val PUBLISH_CONFIRM_TIMEOUT_S = 10L

        private const val MAX_REASON_LENGTH = 140

        /** Wisp's tombstone marker. */
        private val DELETED_TAG = arrayOf("deleted", "true")
    }
}
