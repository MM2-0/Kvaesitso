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
package com.vitorpamplona.amethyst.commons.ui.actions.mediaServers

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.service.AppServices
import com.vitorpamplona.amethyst.commons.service.upload.blossom.BlossomMirrorQueue
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUploadResult
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.Rfc3986
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

/** Per-source outcome of the last scan, so each row can report what it found. */
@Immutable
sealed interface SourceScanState {
    /** Never scanned, or reset after the source list changed. */
    data object Idle : SourceScanState

    /** A `/list` request is in flight for this source. */
    data object Scanning : SourceScanState

    /** The source answered; [count] is how many of the user's blobs it holds. */
    data class Found(
        val count: Int,
    ) : SourceScanState

    /** The source could not be listed (unreachable, no `/list`, error). */
    data class Failed(
        val reason: String,
    ) : SourceScanState
}

/**
 * One server the user can pull their files FROM. Seeded from [DEFAULT_MEDIA_SERVERS]
 * (minus the servers already in the user's own kind-10063 list) plus any address the
 * user typed in. [custom] rows are removable; recommended rows are not.
 */
@Immutable
data class ImportSource(
    val baseUrl: String,
    val host: String,
    val name: String,
    val enabled: Boolean,
    val custom: Boolean,
    val scan: SourceScanState = SourceScanState.Idle,
)

/** Outcome of tapping "import": either the sweep started, the queue was busy, or nothing to do. */
sealed interface ImportStart {
    data class Started(
        val count: Int,
    ) : ImportStart

    data object Busy : ImportStart

    data object Empty : ImportStart
}

/**
 * A blob found on a source server that at least one of the user's own servers is
 * missing — i.e. something worth importing. [sourceUrl] is the absolute URL the
 * user's servers will mirror (BUD-04) from.
 */
@Immutable
data class ImportCandidate(
    val hash: HexKey,
    val sourceUrl: String,
    val sourceHost: String,
    val url: String?,
    val size: Long?,
    val type: String?,
    val missingTargets: List<String>,
)

/**
 * Backs the "import files from other Blossom servers" screen. The user picks a set of
 * source servers (recommended or hand-typed); [scan] fans a `GET /list/<pubkey>`
 * (BUD-02) across the enabled ones, works out which of those blobs are absent from the
 * user's own kind-10063 servers, and [importSelected] hands the gaps to the app-level
 * [BlossomMirrorQueue] so the user's servers fetch them (BUD-04) with the same floating
 * progress banner the "sync all" sweep uses.
 */
@Stable
class BlossomImportViewModel : ViewModel() {
    private lateinit var account: Account
    private lateinit var services: AppServices
    private var seeded = false

    private val _sources = MutableStateFlow<List<ImportSource>>(emptyList())
    val sources = _sources.asStateFlow()

    private val _candidates = MutableStateFlow<List<ImportCandidate>>(emptyList())
    val candidates = _candidates.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    /** True once a scan has completed at least once, so the UI can tell "not scanned yet" from "found nothing". */
    private val _scanned = MutableStateFlow(false)
    val scanned = _scanned.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun init(
        accountViewModel: AccountViewModel,
        services: AppServices,
    ) {
        this.services = services
        // Re-point at the current account every call (matches the sibling BlobManager VM), but
        // seed the source list only once so we don't clobber the user's toggles on recomposition.
        this.account = accountViewModel.account
        if (seeded) return
        seeded = true
        seedSources()
    }

    /** The user's own servers — where imported blobs land. */
    private fun targets(): List<String> =
        account.blossomServers.flow.value
            .distinct()

    private fun seedSources() {
        val ownHosts = targets().mapTo(HashSet()) { BlossomServerUrl.domain(it) }
        _sources.value =
            DEFAULT_MEDIA_SERVERS
                .filter { it.type == ServerType.Blossom }
                // Importing from a server that's already yours is pointless — that's what "sync" covers.
                .filter { BlossomServerUrl.domain(it.baseUrl) !in ownHosts }
                .map {
                    ImportSource(
                        baseUrl = it.baseUrl,
                        host = BlossomServerUrl.domain(it.baseUrl),
                        name = it.name,
                        enabled = false,
                        custom = false,
                    )
                }
    }

    fun toggle(baseUrl: String) {
        _sources.update { list -> list.map { if (it.baseUrl == baseUrl) it.copy(enabled = !it.enabled) else it } }
        invalidateResults()
    }

    fun setAll(enabled: Boolean) {
        _sources.update { list -> list.map { it.copy(enabled = enabled) } }
        invalidateResults()
    }

    /** Add a hand-typed server. Ignores blanks and duplicates (matched by host). */
    fun addCustom(rawUrl: String) {
        val normalized =
            try {
                Rfc3986.normalize(rawUrl.trim())
            } catch (e: Exception) {
                rawUrl.trim()
            }
        if (normalized.isBlank()) return
        val host =
            try {
                BlossomServerUrl.domain(normalized)
            } catch (e: Exception) {
                normalized
            }
        _sources.update { list ->
            if (list.any { it.host == host }) {
                list
            } else {
                list + ImportSource(normalized, host, host, enabled = true, custom = true)
            }
        }
        invalidateResults()
    }

    fun remove(baseUrl: String) {
        _sources.update { list -> list.filterNot { it.baseUrl == baseUrl } }
        invalidateResults()
    }

    /**
     * Drop the previous scan's results whenever the source selection changes — otherwise the
     * "Import N files" button could mirror blobs sourced from a server the user just disabled
     * or removed. Forces a fresh scan against the current selection.
     */
    private fun invalidateResults() {
        // Cancel an in-flight scan too, so its results (for the old selection) can't land after the change.
        scanJob?.cancel()
        _isScanning.value = false
        _candidates.value = emptyList()
        _scanned.value = false
        _error.value = null
        _sources.update { list -> list.map { if (it.scan == SourceScanState.Idle) it else it.copy(scan = SourceScanState.Idle) } }
    }

    private fun clientFor(server: String) = services.blossomClient(server)

    private var scanJob: Job? = null

    fun scan() {
        val targets = targets()
        if (targets.isEmpty()) {
            _error.value = null
            return
        }
        val enabled = _sources.value.filter { it.enabled }
        if (enabled.isEmpty()) return

        scanJob?.cancel()
        scanJob =
            viewModelScope.launch(Dispatchers.IO) {
                _isScanning.value = true
                _error.value = null
                _candidates.value = emptyList()
                setScanStates(enabled.map { it.baseUrl }, SourceScanState.Scanning)
                try {
                    scanSources(enabled.map { it.baseUrl }, targets)
                    _scanned.value = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("BlossomImport", "scan failed", e)
                    _error.value = e.message?.ifBlank { null } ?: e::class.simpleName
                } finally {
                    _isScanning.value = false
                }
            }
    }

    private suspend fun scanSources(
        sources: List<String>,
        targets: List<String>,
    ) {
        val pubkey = account.signer.pubKey
        // A BUD-02 `t=list` token with no `server` tag is generic, so one signature covers
        // every source AND target list call. Signing once (instead of per server) avoids a
        // round-trip storm with remote NIP-46 signers.
        val listAuth = account.createBlossomListAuth("List blobs").toAuthorizationHeader()

        // Phase 1 — /list each enabled source. Collect the user's blobs and remember the
        // first source that can serve each hash (its descriptor URL is the mirror source).
        val listings =
            coroutineScope {
                sources
                    .map { source ->
                        async {
                            val listed =
                                try {
                                    clientFor(source).list(source, pubkey, listAuth)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w("BlossomImport", "list failed on $source", e)
                                    setScanState(source, SourceScanState.Failed(e.shortReason()))
                                    return@async null
                                }
                            setScanState(source, SourceScanState.Found(listed.count { it.sha256 != null }))
                            source to listed
                        }
                    }.awaitAll()
            }

        // Merged after every list returns, in source order, so the first enabled source that
        // holds a hash is the one it is mirrored from.
        val meta = HashMap<HexKey, CandidateMeta>()
        listings.filterNotNull().forEach { (source, listed) ->
            listed.forEach { d ->
                val hash = d.sha256 ?: return@forEach
                if (hash !in meta) {
                    meta[hash] = CandidateMeta(sourceUrlFor(source, d, hash), BlossomServerUrl.domain(source), d.url, d.size, d.type)
                }
            }
        }

        val allHashes = meta.keys.toList()
        if (allHashes.isEmpty()) {
            _candidates.value = emptyList()
            return
        }

        // Phase 2 — which of the user's own servers already hold each hash? /list where the
        // server supports it, HEAD-probe (bounded) the rest, so we only offer the true gaps.
        val holders = targetHolders(allHashes, targets, listAuth)

        _candidates.value =
            allHashes
                .mapNotNull { hash ->
                    val missing = targets.filter { hash !in holders.getOrElse(it) { emptySet() } }
                    if (missing.isEmpty()) return@mapNotNull null
                    val m = meta.getValue(hash)
                    ImportCandidate(hash, m.sourceUrl, m.sourceHost, m.url, m.size, m.type, missing)
                }.sortedByDescending { it.missingTargets.size }
    }

    /** For each target server, the set of hashes it already holds. */
    private suspend fun targetHolders(
        hashes: List<HexKey>,
        targets: List<String>,
        listAuth: String,
    ): Map<String, Set<HexKey>> {
        val pubkey = account.signer.pubKey
        val listed: List<Pair<String, Set<HexKey>?>> =
            coroutineScope {
                targets
                    .map { target ->
                        async {
                            target to
                                try {
                                    clientFor(target).list(target, pubkey, listAuth).mapNotNull { it.sha256 }.toSet()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    null
                                }
                        }
                    }.awaitAll()
            }

        val holders = HashMap<String, MutableSet<HexKey>>()
        val nonListTargets = ArrayList<String>()
        listed.forEach { (target, set) ->
            if (set != null) holders[target] = set.toMutableSet() else nonListTargets.add(target)
        }

        // Servers without /list: HEAD-probe each hash, bounded so a big library doesn't fan out unbounded.
        if (nonListTargets.isNotEmpty()) {
            val limiter = Semaphore(MAX_HEAD_PROBES)
            val probes =
                coroutineScope {
                    nonListTargets
                        .flatMap { target ->
                            hashes.map { hash ->
                                async { limiter.withPermit { Triple(target, hash, clientFor(target).has(hash, target)) } }
                            }
                        }.awaitAll()
                }
            probes.forEach { (target, hash, present) ->
                if (present) holders.getOrPut(target) { HashSet() }.add(hash)
            }
        }
        return holders
    }

    /**
     * Hand every discovered gap to the app-level mirror queue, which asks each of the
     * user's servers to fetch the blob from its source. Reuses the same queue (and
     * floating progress banner) as the on-screen "sync all".
     *
     * There is a single global queue, so if a sweep (or another import) is already in
     * flight the queue would silently drop this one — [ImportStart.Busy] lets the caller
     * keep the screen up and tell the user instead of navigating away to nothing.
     */
    fun importSelected(): ImportStart {
        val candidates = _candidates.value
        val tasks = candidates.map { BlossomMirrorQueue.Task(it.hash, it.sourceUrl, it.size, it.type, it.missingTargets) }
        if (tasks.isEmpty()) return ImportStart.Empty
        // start() itself atomically no-ops if a sweep is already running, so key off its return
        // rather than a separate isRunning check that could race with a sweep starting.
        return if (services.blossomMirrorQueue.start(account, tasks)) {
            ImportStart.Started(candidates.size)
        } else {
            ImportStart.Busy
        }
    }

    private fun setScanState(
        server: String,
        state: SourceScanState,
    ) {
        _sources.update { list -> list.map { if (it.baseUrl == server) it.copy(scan = state) else it } }
    }

    private fun setScanStates(
        servers: List<String>,
        state: SourceScanState,
    ) {
        val set = servers.toHashSet()
        _sources.update { list -> list.map { if (it.baseUrl in set) it.copy(scan = state) else it } }
    }

    fun hostOf(serverBaseUrl: String): String = BlossomServerUrl.domain(serverBaseUrl)

    private fun sourceUrlFor(
        server: String,
        descriptor: BlossomUploadResult,
        hash: HexKey,
    ): String = descriptor.url?.takeIf { it.isNotBlank() } ?: BlossomServerUrl.blob(server, hash)

    private fun Exception.shortReason(): String = message?.ifBlank { null } ?: this::class.simpleName ?: "Exception"

    private data class CandidateMeta(
        val sourceUrl: String,
        val sourceHost: String,
        val url: String?,
        val size: Long?,
        val type: String?,
    )

    companion object {
        /** Cap on concurrent HEAD probes when checking non-/list target servers. */
        private const val MAX_HEAD_PROBES = 8
    }
}
