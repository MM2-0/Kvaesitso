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
package com.vitorpamplona.amethyst.commons.mediaServers

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.AppServices
import com.vitorpamplona.amethyst.commons.service.upload.BlossomPaymentException
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomPaymentHandler
import com.vitorpamplona.amethyst.commons.service.upload.blossom.PaymentPromptLedger
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentProof
import com.vitorpamplona.quartz.nipB7Blossom.BlossomPaymentRequired
import com.vitorpamplona.quartz.nipB7Blossom.BlossomReport
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Whether one of the user's servers holds a blob, or an operation on it is in flight. */
enum class PresenceState {
    /** Confirmed present (green). */
    PRESENT,

    /** Confirmed absent (grey). */
    MISSING,

    /** An operation is in flight — first-load HEAD probe, a mirror, or a delete (spinner). */
    PENDING,
}

@Immutable
data class ServerPresence(
    val server: String,
    val host: String,
    val state: PresenceState,
)

/**
 * One stored blob, plus the state of each of the user's Blossom servers for it —
 * the "seen files per server" view (BUD-01 HEAD / BUD-02 list).
 */
@Immutable
data class BlobRow(
    val hash: HexKey,
    val url: String?,
    val size: Long?,
    val type: String?,
    val servers: List<ServerPresence>,
) {
    val presentServers get() = servers.filter { it.state == PresenceState.PRESENT }.map { it.server }
    val missingServers get() = servers.filter { it.state == PresenceState.MISSING }.map { it.server }
    val hasPresent get() = servers.any { it.state == PresenceState.PRESENT }
    val hasMissing get() = servers.any { it.state == PresenceState.MISSING }
    val presentCount get() = servers.count { it.state == PresenceState.PRESENT }
}

/** A BUD-07 payment prompt raised while mirroring [hash] to [target]. */
@Immutable
data class PendingMirrorPayment(
    val hash: HexKey,
    val sourceUrl: String,
    val target: String,
    val targetHost: String,
    val payment: BlossomPaymentRequired,
    val amountSats: Long?,
)

/**
 * Backs the Blossom blob-manager screen. For the active account it fans a
 * `GET /list/<pubkey>` (BUD-02) across every server in the user's kind-10063 list
 * in parallel, shows the result immediately, then backfills only the servers that
 * do NOT implement `/list` with parallel `HEAD /<sha256>` probes (BUD-01) — a server
 * that returned a list already reports its full holdings, so no probe is needed for
 * it. Delete (BUD-02), mirror-to-missing (BUD-04) and report (BUD-09) update the
 * affected pill in place (spinner → green/grey) instead of reloading the screen.
 */
@Stable
class BlossomBlobManagerViewModel : ViewModel() {
    private lateinit var account: Account
    private lateinit var services: AppServices

    private val _blobs = MutableStateFlow<List<BlobRow>>(emptyList())
    val blobs = _blobs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _pendingPayment = MutableStateFlow<PendingMirrorPayment?>(null)
    val pendingPayment = _pendingPayment.asStateFlow()

    private var resultCollectorStarted = false

    /**
     * Caps BUD-07 payment prompts at one per (blob, server) per user-initiated
     * mirror, so a server that keeps replying 402 after being paid cannot drive an
     * unbounded pay-prompt cycle. See [PaymentPromptLedger].
     */
    private val promptLedger = PaymentPromptLedger()

    fun init(
        accountViewModel: BaseAccountViewModel,
        services: AppServices,
    ) {
        this.services = services
        this.account = accountViewModel.account
        // Reflect the app-level sync sweep's per-server results onto the pills, so an
        // open manager turns dots green live even though the work runs in the background.
        if (!resultCollectorStarted) {
            resultCollectorStarted = true
            viewModelScope.launch {
                services.blossomMirrorQueue.results.collect { r ->
                    setServerState(r.hash, r.server, if (r.ok) PresenceState.PRESENT else PresenceState.MISSING)
                }
            }
        }
    }

    private fun clientFor(server: String) = services.blossomClient(server)

    // The user's explicitly configured kind-10063 servers (empty if they never set
    // a list) — not the DEFAULT_MEDIA_SERVERS fallback, so the matrix reflects the
    // servers the user actually chose.
    private fun servers(): List<String> =
        account.blossomServers.flow.value
            .distinct()

    private var refreshJob: Job? = null

    fun refresh() {
        // Cancel any in-flight load so two quick refreshes can't interleave their writes
        // or fight over _isLoading.
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                _isLoading.value = true
                _error.value = null
                try {
                    loadMatrix()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("BlossomBlobManager", "Failed to load blob list", e)
                    _error.value = e.message?.ifBlank { null } ?: e::class.simpleName
                } finally {
                    _isLoading.value = false
                }
            }
    }

    private suspend fun loadMatrix() {
        val pubkey = account.signer.pubKey
        val servers = servers()
        if (servers.isEmpty()) {
            _blobs.value = emptyList()
            return
        }

        // Phase 1 — /list every server in parallel. A null result means the server
        // doesn't implement /list (or errored) and needs HEAD backfill in phase 2.
        val listed: List<Pair<String, List<BlossomUploadResult>?>> =
            coroutineScope {
                servers
                    .map { server ->
                        async {
                            server to
                                try {
                                    val auth = account.createBlossomListAuth("List blobs").toAuthorizationHeader()
                                    clientFor(server).list(server, pubkey, auth)
                                } catch (e: Exception) {
                                    currentCoroutineContext().ensureActive()
                                    Log.w("BlossomBlobManager", "list failed on $server", e)
                                    null
                                }
                        }
                    }.awaitAll()
            }

        val meta = HashMap<HexKey, BlobMeta>()
        val hashesByServer = HashMap<String, Set<HexKey>>()
        val listCapable = HashSet<String>()
        listed.forEach { (server, blobs) ->
            if (blobs != null) {
                listCapable.add(server)
                hashesByServer[server] = blobs.mapNotNull { it.sha256 }.toSet()
                blobs.forEach { d -> d.sha256?.let { meta.getOrPut(it) { BlobMeta(d.url, d.size, d.type) } } }
            }
        }
        val allHashes = meta.keys.toList()

        fun rows(head: Map<Pair<String, HexKey>, Boolean> = emptyMap()): List<BlobRow> =
            allHashes
                .map { hash ->
                    val presences =
                        servers.map { server ->
                            val state =
                                when {
                                    server in listCapable ->
                                        if (hash in hashesByServer[server].orEmpty()) PresenceState.PRESENT else PresenceState.MISSING
                                    else ->
                                        head[server to hash]?.let { if (it) PresenceState.PRESENT else PresenceState.MISSING }
                                            ?: PresenceState.PENDING
                                }
                            ServerPresence(server, hostOf(server), state)
                        }
                    val m = meta[hash]
                    BlobRow(hash, m?.url, m?.size, m?.type, presences)
                }.sortedByDescending { it.presentCount }

        // Show the /list result right away; the non-list servers appear as spinning
        // pills until their HEAD probe lands.
        _blobs.value = rows()
        _isLoading.value = false

        // Phase 2 — HEAD-probe ONLY the non-list servers, for the known hashes. Bounded so a
        // user with hundreds of blobs on a non-/list server doesn't spawn hundreds of probes
        // at once; OkHttp still caps per-host, this caps the coroutine/allocation breadth.
        val nonListServers = servers.filter { it !in listCapable }
        if (nonListServers.isNotEmpty() && allHashes.isNotEmpty()) {
            val limiter = Semaphore(MAX_HEAD_PROBES)
            val head =
                coroutineScope {
                    nonListServers
                        .flatMap { server ->
                            allHashes.map { hash ->
                                async { limiter.withPermit { (server to hash) to clientFor(server).has(hash, server) } }
                            }
                        }.awaitAll()
                }.toMap()
            _blobs.value = rows(head)
        }
    }

    private fun currentRow(hash: HexKey) = _blobs.value.firstOrNull { it.hash == hash }

    private fun setServerState(
        hash: HexKey,
        server: String,
        state: PresenceState,
    ) {
        // Atomic read-modify-write: the app-level sync results collector (Main) and the
        // per-row delete/mirror actions (IO) both mutate _blobs concurrently, so a plain
        // `_blobs.value = _blobs.value.map{}` would lose updates.
        _blobs.update { list ->
            list.map { row ->
                if (row.hash != hash) {
                    row
                } else {
                    row.copy(servers = row.servers.map { if (it.server == server) it.copy(state = state) else it })
                }
            }
        }
    }

    /** BUD-02 delete: remove [hash] from a single [server]; the pill spins then goes grey. */
    fun delete(
        hash: HexKey,
        server: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            setServerState(hash, server, PresenceState.PENDING)
            val ok =
                try {
                    val auth = account.createBlossomDeleteAuth(hash, "Delete blob").toAuthorizationHeader()
                    clientFor(server).delete(hash, server, auth)
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.w("BlossomBlobManager", "delete failed on $server", e)
                    false
                }
            setServerState(hash, server, if (ok) PresenceState.MISSING else PresenceState.PRESENT)
            // Drop the row entirely once it's gone from every server.
            _blobs.update { list -> if (list.firstOrNull { it.hash == hash }?.hasPresent == false) list.filter { it.hash != hash } else list }
        }
    }

    /**
     * BUD-04: mirror a blob to every server that doesn't have it; each pill spins
     * then turns green. This is the user-initiated entry point, so it resets the
     * "already asked for payment" ledger — see [promptedForPayment].
     */
    fun mirrorToMissing(row: BlobRow) {
        promptLedger.beginUserAction()
        mirrorMissingTargets(row)
    }

    private fun mirrorMissingTargets(row: BlobRow) {
        val source = row.url ?: return
        val targets = currentRow(row.hash)?.missingServers ?: row.missingServers
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (target in targets) {
                setServerState(row.hash, target, PresenceState.PENDING)
                try {
                    mirrorOne(source, row.hash, row.size, row.type, target, null)
                    setServerState(row.hash, target, PresenceState.PRESENT)
                } catch (e: BlossomPaymentException) {
                    setServerState(row.hash, target, PresenceState.MISSING)
                    if (BlossomPaymentHandler.canPay(account, e.payment)) {
                        // Bounded: a server that pockets the preimage and replies 402
                        // again must not be able to spin up an endless pay-prompt
                        // cycle. One prompt per target per user-initiated mirror.
                        if (!promptLedger.shouldPrompt(row.hash, target)) {
                            Log.w("BlossomBlobManager") { "mirror to $target asked for payment again after being paid; not re-prompting" }
                            _error.value = "${hostOf(target)} asked for payment again after being paid. Amethyst stopped to avoid paying twice."
                            continue
                        }
                        _pendingPayment.value =
                            PendingMirrorPayment(row.hash, source, target, hostOf(target), e.payment, BlossomPaymentHandler.amountSats(e.payment))
                        return@launch
                    }
                    Log.w("BlossomBlobManager", "mirror to $target needs unsupported payment", e)
                } catch (e: Exception) {
                    setServerState(row.hash, target, PresenceState.MISSING)
                    currentCoroutineContext().ensureActive()
                    Log.w("BlossomBlobManager", "mirror to $target failed", e)
                }
            }
        }
    }

    /**
     * BUD-04 sweep: hand the whole "fill every gap" job to the app-level
     * [BlossomMirrorQueue] so it keeps running (with a floating progress banner) as
     * the user navigates away. The pills we're about to fill go straight to a spinner;
     * the queue's [results] stream (collected in [init]) flips each to green/grey as it
     * lands, so an open manager stays in sync with the background sweep.
     */
    fun syncAll() {
        val tasks =
            _blobs.value
                .filter { it.hasMissing && it.url != null }
                .map { BlossomMirrorQueue.Task(it.hash, it.url!!, it.size, it.type, it.missingServers) }
        if (tasks.isEmpty()) return

        _blobs.update { list ->
            list.map { row ->
                if (!row.hasMissing) {
                    row
                } else {
                    row.copy(servers = row.servers.map { if (it.state == PresenceState.MISSING) it.copy(state = PresenceState.PENDING) else it })
                }
            }
        }
        services.blossomMirrorQueue.start(account, tasks)
    }

    private suspend fun mirrorOne(
        source: String,
        hash: HexKey,
        size: Long?,
        contentType: String?,
        target: String,
        proof: BlossomPaymentProof?,
    ) {
        val auth = account.createBlossomUploadAuth(hash, size ?: 0L, "Mirror $hash").toAuthorizationHeader()
        clientFor(target).mirrorOrUpload(source, hash, contentType ?: DEFAULT_MIME_TYPE, target, auth, proof)
    }

    /** User confirmed the BUD-07 prompt: pay via the wallet, retry that server, then continue. */
    fun confirmPendingPayment() {
        val pending = _pendingPayment.value ?: return
        _pendingPayment.value = null
        viewModelScope.launch(Dispatchers.IO) {
            setServerState(pending.hash, pending.target, PresenceState.PENDING)
            // pending.amountSats is exactly what the dialog showed; pay() re-derives
            // the amount from the invoice and refuses if the two disagree or the
            // amount is above the cap.
            val result = BlossomPaymentHandler.pay(account, pending.payment, pending.amountSats)
            val proof =
                when (result) {
                    is BlossomPaymentHandler.PayResult.Paid -> result.proof
                    is BlossomPaymentHandler.PayResult.Refused -> {
                        setServerState(pending.hash, pending.target, PresenceState.MISSING)
                        _error.value = result.reason
                        return@launch
                    }
                    BlossomPaymentHandler.PayResult.TimedOut -> {
                        setServerState(pending.hash, pending.target, PresenceState.MISSING)
                        _error.value =
                            "Your wallet did not confirm in time. If the payment does go through, retry the mirror — " +
                            "Amethyst will not send this invoice again."
                        return@launch
                    }
                    BlossomPaymentHandler.PayResult.Unavailable -> {
                        setServerState(pending.hash, pending.target, PresenceState.MISSING)
                        _error.value = "Payment failed or was not confirmed by the wallet."
                        return@launch
                    }
                }
            try {
                val row = currentRow(pending.hash)
                mirrorOne(pending.sourceUrl, pending.hash, row?.size, row?.type, pending.target, proof)
                setServerState(pending.hash, pending.target, PresenceState.PRESENT)
            } catch (e: Exception) {
                setServerState(pending.hash, pending.target, PresenceState.MISSING)
                currentCoroutineContext().ensureActive()
                Log.w("BlossomBlobManager", "paid mirror to ${pending.target} failed", e)
            }
            // Continue with any remaining missing servers. Targets already prompted
            // in this action (including this one) will NOT prompt again.
            currentRow(pending.hash)?.let { mirrorMissingTargets(it) }
        }
    }

    fun cancelPendingPayment() {
        _pendingPayment.value = null
    }

    /** BUD-09: report a blob to a server as problematic content. */
    fun report(
        hash: HexKey,
        server: String,
        type: ReportType,
        comment: String,
        onDone: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok =
                try {
                    val event = account.signer.sign(BlossomReport.build(hash, type, account.signer.pubKey, comment))
                    clientFor(server).report(server, event.toJson())
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.w("BlossomBlobManager", "report failed on $server", e)
                    false
                }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    fun hostOf(serverBaseUrl: String): String = BlossomServerUrl.domain(serverBaseUrl)

    private data class BlobMeta(
        val url: String?,
        val size: Long?,
        val type: String?,
    )

    companion object {
        /** Cap on concurrent HEAD probes during the /list backfill. */
        private const val MAX_HEAD_PROBES = 8

        /** Fallback MIME for a `/upload` when a target lacks `/mirror` and the row has no type. */
        private const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}
