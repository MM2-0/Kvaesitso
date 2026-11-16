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
package com.vitorpamplona.amethyst.commons.service.pow

import androidx.compose.runtime.Immutable
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

enum class PoWJobPhase {
    /** Waiting for a mining worker. Cancellable. */
    QUEUED,

    /** A worker is searching for the nonce. Cancellable. */
    MINING,

    /** Nonce found; signing and broadcasting. No longer cancellable. */
    PUBLISHING,
}

/**
 * Snapshot of one queued/mining/publishing job, for display in the broadcast
 * banner. [miningStartedAt] (epoch seconds) is set when a worker picks the
 * job up, so the UI can show how long the nonce search has been running.
 */
@Immutable
data class PoWJobState(
    val id: String,
    val kind: Int,
    val difficulty: Int,
    val phase: PoWJobPhase = PoWJobPhase.QUEUED,
    val miningStartedAt: Long? = null,
    /**
     * True when the job carries a plain, un-mined fallback the user can publish
     * right away instead of waiting for (or discarding) the nonce search — the
     * template send paths. Opaque work jobs (reactions, reposts, anonymous
     * posts, gift wraps) have no such fallback and are false.
     */
    val canSendWithoutPow: Boolean = false,
    /**
     * The event's block layout, for a hash-rate estimate that matches how the
     * miner will hash it. Known for template jobs; null for opaque work jobs,
     * whose event only exists inside the work (estimate with a typical shape).
     */
    val shape: PoWEventShape? = null,
) {
    val isMining: Boolean get() = phase == PoWJobPhase.MINING
    val isCancellable: Boolean get() = phase != PoWJobPhase.PUBLISHING
}

/** Emitted when a job dies after mining or while publishing, so the UI can toast. */
@Immutable
data class PoWJobFailure(
    val kind: Int,
    /** true when a checkpoint survived and the restorer will retry it on next login. */
    val willRetryOnRestart: Boolean,
    val message: String?,
)

/**
 * Fire-and-forget NIP-13 mining queue: the Post button enqueues the finished,
 * unsigned template here and returns immediately; mining runs on a small
 * worker pool and, once the nonce is found, the job hands the mined template
 * to the normal sign+broadcast continuation captured at enqueue time.
 *
 * FIFO with at most [maxConcurrent] concurrent miners so a burst of posts
 * queues up instead of spawning unbounded CPU work. Each job's nonce search
 * runs on [minerThreads] parallel workers (see PoWMiner.mine) — callers that
 * bring their own mine lambda via [enqueueStaged]/[enqueueWork] should reuse
 * this value to stay inside the queue's CPU budget. Jobs are cancellable
 * while QUEUED or MINING; once PUBLISHING starts, cancel is a no-op.
 *
 * Template jobs enqueued with a [PersistedPoWJob] record are checkpointed to
 * [persistence]. The checkpoint is deleted only after the [onMined]
 * continuation COMPLETES (publish included) or the user cancels — never at
 * mining-complete — so a process death mid-sign or mid-broadcast is replayed
 * by the restorer on the next login. A continuation that throws keeps its
 * checkpoint (retry on restart) and reports through [failures]. Opaque
 * [enqueueWork] jobs (reactions, reposts — and anonymous posts, deliberately,
 * so a throwaway key and its content never touch disk) stay in-memory only.
 *
 * [onQueueActive] fires on every enqueue; the Android layer uses it to start
 * the mining foreground service so backgrounding the app doesn't freeze the
 * workers mid-nonce.
 */
class PoWPublishQueue(
    private val scope: CoroutineScope,
    maxConcurrent: Int = 1,
    val minerThreads: Int = 1,
    miningDispatcher: CoroutineDispatcher = PoWMiner.MiningDispatcher,
    private val persistence: PoWJobPersistence? = null,
    private val onQueueActive: () -> Unit = {},
) {
    private class MiningJob(
        val id: String,
        val kind: Int,
        val difficulty: Int,
        val persisted: Boolean,
        val dedupeKey: String?,
        val owner: HexKey?,
        val work: suspend (isActive: () -> Boolean) -> Unit,
        // When non-null, publishes the event WITHOUT proof of work — the plain
        // template send path, run in place of the (abandoned) nonce search.
        val sendWithoutPow: (suspend () -> Unit)?,
    ) {
        @Volatile
        var cancelled = false

        @Volatile
        var publishing = false

        // Set by cancel-and-send-now: stop the miner and publish [sendWithoutPow].
        @Volatile
        var sendUnminedRequested = false
    }

    private val queue = Channel<MiningJob>(UNLIMITED)

    private val _jobs = MutableStateFlow<ImmutableList<PoWJobState>>(persistentListOf())

    /** Queued + mining + publishing jobs, in enqueue order. */
    val jobs: StateFlow<ImmutableList<PoWJobState>> = _jobs.asStateFlow()

    private val _failures = MutableSharedFlow<PoWJobFailure>(extraBufferCapacity = 16)

    /** Post-mining failures (signer rejected, broadcast threw). */
    val failures: SharedFlow<PoWJobFailure> = _failures.asSharedFlow()

    // Jobs the workers haven't finished yet, so cancel() can reach the flag of
    // a job that is still sitting in the channel. StateFlow.update gives us
    // atomic CAS updates across the UI thread and the mining workers. This map
    // and _jobs are only ever mutated together inside addJob/setPhase/removeEntry.
    private val pending = MutableStateFlow<PersistentMap<String, MiningJob>>(persistentMapOf())

    init {
        repeat(maxConcurrent.coerceAtLeast(1)) {
            scope.launch(miningDispatcher) {
                for (job in queue) {
                    process(job)
                }
            }
        }
    }

    /**
     * Mines [template] at [difficulty] and hands the mined template to
     * [onMined] on the queue's scope. [onMined] should run the exact
     * sign+broadcast path the caller would have used without PoW; the job
     * (and its checkpoint) lives until that continuation finishes.
     *
     * When [persistAs] is given, the job is checkpointed under the record's
     * id. Re-enqueueing an id already in the queue is a no-op — that makes
     * restore-on-login idempotent.
     *
     * [refreshCreatedAt] keeps the template's created_at current — re-stamped to
     * "now" when a worker picks the job up, and again roughly once a second for
     * as long as the miner runs. NIP-13 recommends updating created_at while
     * mining, and a job that waited in the queue (or was restored after a
     * process death, or simply mined for two minutes) would otherwise publish
     * visibly in the past. Must stay false for scheduled posts, whose future
     * created_at is intentional.
     */
    fun <T : Event> enqueue(
        template: EventTemplate<T>,
        pubKey: HexKey,
        difficulty: Int,
        persistAs: PersistedPoWJob? = null,
        refreshCreatedAt: Boolean = false,
        onMined: suspend (EventTemplate<T>) -> Unit,
    ) {
        val clock = if (refreshCreatedAt) TimeUtils::now else null

        enqueueStaged(
            kind = template.kind,
            difficulty = difficulty,
            persistAs = persistAs,
            owner = pubKey,
            shape = PoWEventShape.of(template, pubKey, difficulty),
            // the miner stamps the first pass from the same clock, so the job
            // picks up "now" whether it waited in the queue or was restored
            // from disk — no need to re-wrap the template here.
            mine = { isActive -> PoWMiner.mine(template, pubKey, difficulty, minerThreads, isActive, clock) },
            publish = onMined,
            // send-now fallback: the same template, minus the nonce the miner would
            // have added. created_at follows the mined path — refreshed to "now" for
            // ordinary posts, left intact for scheduled ones.
            sendWithoutPow = {
                if (clock != null) {
                    // same clamp the miner applies, so abandoning the nonce search
                    // can never stamp a template further back than mining would have.
                    EventTemplate<T>(maxOf(template.createdAt, clock()), template.kind, template.tags, template.content)
                } else {
                    template
                }
            },
        )
    }

    /**
     * The staged primitive behind [enqueue]: [mine] runs on the capped worker
     * pool (CPU only — it must not touch the user's signer or the network);
     * [publish] runs detached on the queue's scope so a slow external signer
     * or broadcast never holds a mining slot. The job entry and its optional
     * checkpoint live until [publish] completes.
     */
    fun <R> enqueueStaged(
        kind: Int,
        difficulty: Int,
        persistAs: PersistedPoWJob? = null,
        dedupeKey: String? = null,
        owner: HexKey? = null,
        shape: PoWEventShape? = null,
        mine: suspend (isActive: () -> Boolean) -> R,
        publish: suspend (R) -> Unit,
        // When non-null, produces the value [publish] would have received
        // WITHOUT mining, so the user can abandon the nonce search and send the
        // event right away (see [sendWithoutPow]). Null for stages whose result
        // only exists after mining (gift wraps).
        sendWithoutPow: (suspend () -> R)? = null,
    ) {
        val id = persistAs?.id ?: RandomInstance.randomChars(16)
        addJob(
            id = id,
            kind = kind,
            difficulty = difficulty,
            persistAs = persistAs,
            dedupeKey = dedupeKey,
            owner = owner ?: persistAs?.accountPubkey,
            shape = shape,
            sendWithoutPow = sendWithoutPow?.let { produce -> { publish(produce()) } },
        ) { isActive ->
            val mined = mine(isActive)
            // frees the mining worker: signing may wait on an external signer
            // (Amber/bunker) and broadcasting is IO, neither belongs on the
            // pool. The job entry + checkpoint survive until publish finishes.
            setPhase(id, PoWJobPhase.PUBLISHING)
            scope.launch { finishDetached(id, kind, persisted = isCheckpointed(persistAs)) { publish(mined) } }
        }
    }

    /**
     * Enqueues arbitrary in-memory mining work — used by flows where the
     * mining happens inside a larger build step (e.g. gift wraps, where each
     * recipient's ephemeral-key wrap is mined right before its local
     * signature) and by anonymous posts, whose throwaway key must not be
     * written to disk. Lost on process death.
     *
     * A non-null [dedupeKey] makes the enqueue idempotent against pending
     * jobs with the same key (see [cancelByKey] for toggle semantics).
     */
    fun enqueueWork(
        kind: Int,
        difficulty: Int,
        dedupeKey: String? = null,
        owner: HexKey? = null,
        work: suspend (isActive: () -> Boolean) -> Unit,
    ) = addJob(RandomInstance.randomChars(16), kind, difficulty, persistAs = null, dedupeKey = dedupeKey, owner = owner, shape = null, sendWithoutPow = null, work = work)

    // A record only survives a restart when there is somewhere to save it: a queue built without
    // [persistence] (a desktop copy that is not the primary one) mines it in memory only, and must not
    // tell the user a failed job will retry on restart.
    private fun isCheckpointed(persistAs: PersistedPoWJob?) = persistAs != null && persistence != null

    private fun addJob(
        id: String,
        kind: Int,
        difficulty: Int,
        persistAs: PersistedPoWJob?,
        dedupeKey: String?,
        owner: HexKey?,
        shape: PoWEventShape?,
        sendWithoutPow: (suspend () -> Unit)?,
        work: suspend (isActive: () -> Boolean) -> Unit,
    ) {
        val current = pending.value
        if (current.containsKey(id)) {
            Log.d(TAG) { "PoW job $id already queued; skipping duplicate enqueue" }
            return
        }
        if (dedupeKey != null && current.values.any { it.dedupeKey == dedupeKey && !it.cancelled }) {
            Log.d(TAG) { "PoW job with key $dedupeKey already queued; skipping duplicate enqueue" }
            return
        }

        val job = MiningJob(id, kind, difficulty, persisted = isCheckpointed(persistAs), dedupeKey = dedupeKey, owner = owner, work = work, sendWithoutPow = sendWithoutPow)
        persistAs?.let { persistence?.save(it) }
        pending.update { it.putting(job.id, job) }
        _jobs.update { (it + PoWJobState(job.id, job.kind, job.difficulty, canSendWithoutPow = sendWithoutPow != null, shape = shape)).toImmutableList() }
        Log.d(TAG) {
            val durability = if (job.persisted) "persisted" else "in-memory only, lost on process death"
            "Enqueued PoW job ${job.id} kind=${job.kind} difficulty=${job.difficulty} ($durability)"
        }
        queue.trySend(job)
        onQueueActive()
    }

    /**
     * Cancels a queued or mining job. No-op if the job already finished or is
     * already publishing (the event may be half-signed/half-broadcast — there
     * is nothing safe to abort).
     */
    fun cancel(jobId: String) {
        val job = pending.value[jobId] ?: return
        if (job.publishing) return
        job.cancelled = true
        removeEntry(jobId, dropCheckpoint = true, persisted = job.persisted)
        Log.d(TAG) { "Cancelled PoW job $jobId" }
    }

    /**
     * Cancels the pending job carrying [dedupeKey], if any. Returns true when
     * a job was cancelled — callers use this for toggle semantics (tapping
     * "like" again while the first like is still mining un-likes it).
     */
    fun cancelByKey(dedupeKey: String): Boolean {
        val job = pending.value.values.firstOrNull { it.dedupeKey == dedupeKey && !it.publishing } ?: return false
        cancel(job.id)
        return true
    }

    /** Cancels everything still queued or mining. */
    fun cancelAll() {
        pending.value.keys.forEach { cancel(it) }
    }

    /**
     * Abandons the nonce search for [jobId] and publishes its event immediately
     * WITHOUT proof of work, through the same sign+broadcast continuation the
     * mined template would have used. No-op if the job already finished, is
     * publishing, was cancelled, or was enqueued without an un-mined fallback
     * (opaque work jobs — reactions, reposts, anonymous posts, gift wraps — have
     * no plain template to fall back to). The running worker picks up the flag
     * on its next [isActive] poll and does the publish; a still-QUEUED job does
     * it as soon as a worker takes it.
     */
    fun sendWithoutPow(jobId: String) {
        val job = pending.value[jobId] ?: return
        if (job.publishing || job.cancelled || job.sendWithoutPow == null) return
        job.sendUnminedRequested = true
        Log.d(TAG) { "Requested send-without-PoW for job $jobId" }
    }

    /** Sends every job that supports it without proof of work; others keep mining. */
    fun sendAllWithoutPow() {
        pending.value.keys.forEach { sendWithoutPow(it) }
    }

    /**
     * Cancels every queued/mining job enqueued for [owner]'s account — used at
     * log-off so a deleted account's posts can't publish after the fact.
     * Publishing jobs are left to finish (cancel is unsafe mid-broadcast).
     */
    fun cancelForOwner(owner: HexKey) {
        pending.value.values
            .filter { it.owner == owner }
            .forEach { cancel(it.id) }
    }

    private suspend fun process(job: MiningJob) {
        if (job.cancelled) {
            removeEntry(job.id, dropCheckpoint = true, persisted = job.persisted)
            return
        }

        setPhase(job.id, PoWJobPhase.MINING)

        val workerJob = currentCoroutineContext().job
        var detached = false

        try {
            job.work { !job.cancelled && !job.sendUnminedRequested && workerJob.isActive }
            detached = job.publishing
            Log.d(TAG) { "Finished mining PoW job ${job.id} kind=${job.kind} difficulty=${job.difficulty}" }
        } catch (e: CancellationException) {
            // the worker itself was cancelled (scope teardown): propagate.
            if (!currentCoroutineContext().isActive) throw e

            val sendWithoutPow = job.sendWithoutPow
            if (job.sendUnminedRequested && !job.cancelled && sendWithoutPow != null) {
                // user chose "send now": abandon the nonce search and publish the
                // plain template through the same continuation, off the pool.
                Log.d(TAG) { "PoW job ${job.id} sending without proof of work" }
                setPhase(job.id, PoWJobPhase.PUBLISHING)
                detached = true
                scope.launch { finishDetached(job.id, job.kind, persisted = job.persisted, onMined = sendWithoutPow) }
            } else {
                Log.d(TAG) { "PoW job ${job.id} cancelled while mining" }
            }
        } catch (e: Exception) {
            // mining-stage failures are deterministic (bad difficulty, broken
            // template): drop the checkpoint too, or every restore re-crashes.
            Log.w(TAG, "PoW job ${job.id} kind=${job.kind} failed while mining", e)
            _failures.tryEmit(PoWJobFailure(job.kind, willRetryOnRestart = false, message = e.message))
        } finally {
            // detached template jobs remove themselves in finishDetached once
            // the sign+broadcast continuation completes.
            if (!detached) {
                removeEntry(job.id, dropCheckpoint = true, persisted = job.persisted)
            }
        }
    }

    /**
     * Runs the post-mining continuation off the worker pool. Success drops the
     * checkpoint; failure keeps it (the restorer replays it headlessly on the
     * next login) and surfaces through [failures].
     */
    private suspend fun finishDetached(
        jobId: String,
        kind: Int,
        persisted: Boolean,
        onMined: suspend () -> Unit,
    ) {
        try {
            onMined()
            Log.d(TAG) { "Published PoW job $jobId kind=$kind" }
            removeEntry(jobId, dropCheckpoint = true, persisted = persisted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PoW job $jobId kind=$kind failed after mining", e)
            removeEntry(jobId, dropCheckpoint = false, persisted = persisted)
            _failures.tryEmit(PoWJobFailure(kind, willRetryOnRestart = persisted, message = e.message))
        }
    }

    private fun setPhase(
        jobId: String,
        phase: PoWJobPhase,
    ) {
        if (phase == PoWJobPhase.PUBLISHING) pending.value[jobId]?.publishing = true
        _jobs.update { list ->
            list
                .map {
                    when {
                        it.id != jobId -> it
                        phase == PoWJobPhase.MINING -> it.copy(phase = phase, miningStartedAt = TimeUtils.now())
                        else -> it.copy(phase = phase)
                    }
                }.toImmutableList()
        }
    }

    private fun removeEntry(
        jobId: String,
        dropCheckpoint: Boolean,
        persisted: Boolean,
    ) {
        // drop the checkpoint before the entry leaves [jobs]: anyone observing
        // the job disappear must also see its checkpoint gone.
        if (persisted && dropCheckpoint) persistence?.remove(jobId)
        pending.update { it.removing(jobId) }
        _jobs.update { list -> list.filter { it.id != jobId }.toImmutableList() }
    }

    companion object {
        private const val TAG = "PoWPublishQueue"
    }
}
