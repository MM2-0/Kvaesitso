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
package com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks

import com.vitorpamplona.amethyst.commons.actions.DeletionActions
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.DONE_REASON_EOSE
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.withoutClientTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.nipFBScheduledTasks.tags.TaskTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How a deletion of a scheduled task ended. */
sealed interface ScheduleDeletion {
    /** More than half of the write relays took [deletion] of [target], the newest event they held. */
    data class Deleted(
        val deletion: DeletionRequestEvent,
        val target: ScheduledTaskEvent,
    ) : ScheduleDeletion

    /** The write relays answered and hold no such task. */
    data object NotFound : ScheduleDeletion

    /** Not more than half of the write relays answered the read: nothing was sent. */
    data object Unreachable : ScheduleDeletion

    /** Some of the write relays took [deletion], but not more than half. */
    data class NotAccepted(
        val deletion: DeletionRequestEvent,
    ) : ScheduleDeletion

    /** The signer refused or failed to sign. */
    data object CannotSign : ScheduleDeletion

    /** The user has no NIP-65 write relays. */
    data object NoWriteRelays : ScheduleDeletion
}

/** One task as the write relays hold it: [task] null when this signer can't read [event]'s content. */
class ScheduledTaskEntry(
    val dTag: String,
    val event: ScheduledTaskEvent,
    val task: ScheduledTask?,
)

/** Every task of one author, the newest event per `d`; [quorum] when more than half of [relays] answered. */
class ScheduledTaskListing(
    val entries: List<ScheduledTaskEntry>,
    val quorum: Boolean,
    val relays: Set<NormalizedRelayUrl>,
)

/**
 * NIP-FB's relay side for one task (kind 34817 under [dTag]) with no cache behind it: the quorum
 * read of the user's write relays, the publish, the read-modify-write (NIP-FB, Editing) and the
 * NIP-09 deletion. More than half of the write relays must answer each read and accept each
 * write. [ObserverScheduleState] runs the morning schedule through one, with the app's cache as
 * its hooks; `amy schedule` runs any task through one, with amy's event store.
 *
 * Signed without a `client` tag: a scheduled task says nothing public but its `d`.
 */
class ScheduledTaskRelays(
    val signer: NostrSigner,
    private val client: INostrClient,
    val dTag: String,
    /** The user's NIP-65 write relays, nothing else: never a default or a fallback. */
    private val writeRelays: () -> Set<NormalizedRelayUrl>,
    /** [event]'s tags, public and private; null when this signer can't open them. */
    private val decrypt: suspend (ScheduledTaskEvent) -> ScheduledTask? = { it.task(signer) },
    /** The newest event this device already holds, which a write's `created_at` must still beat. */
    private val known: () -> ScheduledTaskEvent? = { null },
    /** Each event a relay returned, verified and this author's. */
    private val onRead: suspend (ScheduledTaskEvent, NormalizedRelayUrl) -> Unit = { _, _ -> },
    /** Each event this signs, before it is sent. */
    private val onSigned: suspend (Event) -> Unit = {},
    /**
     * Sends a signed event to the relays and says which accepted it: the client's own publish
     * path (amy's answers NIP-42 challenges and reports where it went), by default a plain one.
     */
    private val sender: suspend (Event, Set<NormalizedRelayUrl>) -> Set<NormalizedRelayUrl> = { signed, relays ->
        client.publishAndCollectResults(signed, relays, timeoutInSeconds = WRITE_CONFIRM_SECONDS).filterValues { it.accepted }.keys
    },
) {
    /** The task is never signed with the account's `client` tag. */
    private val plainSigner = signer.withoutClientTag()

    private val editing = Mutex()

    fun filter(): Filter = ScheduledTaskEvent.filter(signer.pubKey, dTag)

    private fun isThisTask(event: Event) = event is ScheduledTaskEvent && event.pubKey == signer.pubKey && event.dTag() == dTag

    /**
     * The newest event across every write relay, read to EOSE, and whether that read made quorum.
     * A NIP-09 deletion of the task that any answering relay holds, newer than that event, means
     * there is no task: a relay that missed the deletion doesn't bring it back.
     */
    suspend fun readQuorum(): QuorumRead {
        val relays = writeRelays()
        if (relays.isEmpty()) return QuorumRead(null, null, false, relays)
        val address = ScheduledTaskEvent.createAddress(signer.pubKey, dTag).toValue()
        val deletions = Filter(kinds = listOf(DeletionRequestEvent.KIND), authors = listOf(signer.pubKey), tags = mapOf("a" to listOf(address)))
        val (read, deleted, answered) = fetchToEose(client, relays, listOf(filter(), deletions), ::isThisTask, onRead)
        // Only what the relays returned is evidence: this device's own copy of a write they
        // didn't take is not the task. A new write still has to beat it.
        val found = ScheduledTaskClaims.winner(read)
        val deletedAt = found?.let { deletionOf(it, deleted) } ?: deleted.maxOfOrNull { it.createdAt }?.takeIf { found == null }
        val newest = found?.takeIf { deletionOf(it, deleted) == null }
        val floor = ScheduledTaskClaims.winner(listOfNotNull(found, known()))
        return QuorumRead(newest, newest?.let { decrypt(it) }, ScheduledTaskClaims.hasQuorum(relays.size, answered), relays, floor, deletedAt)
    }

    /** Sends [signed] to [relays]; how many accepted it, 0 when the send itself failed. */
    suspend fun send(
        signed: Event,
        relays: Set<NormalizedRelayUrl>,
    ): Int =
        try {
            sender(signed, relays).size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not publish the scheduled task", e)
            0
        }

    /** Signs [task] as the event after [previous], sends it to [relays]; true when more than half accepted it. */
    suspend fun publish(
        previous: ScheduledTaskEvent?,
        task: ScheduledTask,
        relays: Set<NormalizedRelayUrl>,
        now: Long = TimeUtils.now(),
    ): Pair<ScheduledTaskEvent, Boolean> {
        val signed = plainSigner.sign<ScheduledTaskEvent>(ScheduledTaskEvent.rewrite(task, plainSigner, dTag, previous, now))
        onSigned(signed)
        return signed to ScheduledTaskClaims.hasQuorum(relays.size, send(signed, relays))
    }

    /**
     * Read-modify-write (NIP-FB, Editing): [transform] gets the newest task (null when there is
     * none) and returns the task to write, or null to write nothing. With [resend], an edit whose
     * result the newest event already holds sends that event to every write relay again (a `ran`
     * that too few took).
     */
    suspend fun edit(
        now: Long = TimeUtils.now(),
        resend: Boolean = false,
        transform: (ScheduledTask?) -> ScheduledTask?,
    ): ScheduleEdit =
        editing.withLock {
            if (!signer.isWriteable()) return@withLock ScheduleEdit.CannotSign
            if (writeRelays().isEmpty()) return@withLock ScheduleEdit.NoWriteRelays
            val read = readQuorum()
            val (next, outcome) = planScheduleEdit(read.event != null, read.task, read.quorum, transform)
            if (next == null) {
                val again = read.event
                if (resend && outcome == ScheduleEdit.Unchanged && again != null && read.task != null && transform(read.task) == read.task) {
                    val accepted = send(again, read.relays)
                    return@withLock if (ScheduledTaskClaims.hasQuorum(read.relays.size, accepted)) outcome else ScheduleEdit.NotAccepted
                }
                return@withLock outcome
            }
            val signed =
                try {
                    // After a deletion, newer than it: the same second would be deleted with it (NIP-09).
                    val at = read.deletedAt?.let { maxOf(now, it + 1) } ?: now
                    plainSigner.sign<ScheduledTaskEvent>(ScheduledTaskEvent.rewrite(next, plainSigner, dTag, read.floor, at))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not sign the scheduled task", e)
                    return@withLock ScheduleEdit.CannotSign
                }
            onSigned(signed)
            // A send that fails is the network's, not the signer's.
            if (ScheduledTaskClaims.hasQuorum(read.relays.size, send(signed, read.relays))) outcome else ScheduleEdit.NotAccepted
        }

    /**
     * NIP-09 deletion of the task's address (its `a`, and the newest event's `e`), sent to every
     * write relay after a quorum read found it. Content that can't be decrypted can still be deleted.
     */
    suspend fun delete(): ScheduleDeletion =
        editing.withLock {
            if (!signer.isWriteable()) return@withLock ScheduleDeletion.CannotSign
            if (writeRelays().isEmpty()) return@withLock ScheduleDeletion.NoWriteRelays
            val read = readQuorum()
            if (!read.quorum) return@withLock ScheduleDeletion.Unreachable
            val target = read.event ?: return@withLock ScheduleDeletion.NotFound
            val deletion =
                try {
                    DeletionActions.delete(listOf(target), plainSigner).single()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not sign the deletion of a scheduled task", e)
                    return@withLock ScheduleDeletion.CannotSign
                }
            onSigned(deletion)
            if (ScheduledTaskClaims.hasQuorum(read.relays.size, send(deletion, read.relays))) {
                ScheduleDeletion.Deleted(deletion, target)
            } else {
                ScheduleDeletion.NotAccepted(deletion)
            }
        }

    companion object {
        private const val TAG = "ScheduledTaskRelays"
        internal const val READ_IDLE_MS = 8_000L
        private const val WRITE_CONFIRM_SECONDS = 10L

        /**
         * Every task [signer] wrote, the newest event per `d`, read from [relays] (the user's write
         * relays) to EOSE and decrypted with [decrypt].
         */
        suspend fun readAll(
            client: INostrClient,
            signer: NostrSigner,
            relays: Set<NormalizedRelayUrl>,
            decrypt: suspend (ScheduledTaskEvent) -> ScheduledTask? = { it.task(signer) },
            onRead: suspend (ScheduledTaskEvent, NormalizedRelayUrl) -> Unit = { _, _ -> },
        ): ScheduledTaskListing {
            if (relays.isEmpty()) return ScheduledTaskListing(emptyList(), false, relays)
            val isMine = { event: Event -> event is ScheduledTaskEvent && event.pubKey == signer.pubKey }
            // NIP-09 deletions name the kind they delete (`k`): one REQ finds them for every task.
            val deletions = Filter(kinds = listOf(DeletionRequestEvent.KIND), authors = listOf(signer.pubKey), tags = mapOf("k" to listOf(ScheduledTaskEvent.KIND.toString())))
            val (read, deleted, answered) = fetchToEose(client, relays, listOf(ScheduledTaskEvent.filter(signer.pubKey), deletions), isMine, onRead)
            val entries =
                read
                    .groupBy { it.dTag() }
                    .mapNotNull { (d, versions) -> ScheduledTaskClaims.winner(versions)?.takeIf { deletionOf(it, deleted) == null }?.let { ScheduledTaskEntry(d, it, decrypt(it)) } }
                    .sortedBy { it.dTag }
            return ScheduledTaskListing(entries, ScheduledTaskClaims.hasQuorum(relays.size, answered), relays)
        }

        /**
         * When a NIP-09 deletion among [deletions] deletes [event] (its id, or its address at or
         * after its `created_at`), the newest such deletion's `created_at`; else null.
         */
        fun deletionOf(
            event: ScheduledTaskEvent,
            deletions: List<DeletionRequestEvent>,
        ): Long? {
            val address = event.address().toValue()
            return deletions
                .filter { it.pubKey == event.pubKey && (event.id in it.deleteEventIds() || (address in it.deleteAddressIds() && it.createdAt >= event.createdAt)) }
                .maxOfOrNull { it.createdAt }
        }

        /** What [relays] returned for [filters] to EOSE, verified: the tasks [accept] keeps, the author's deletions, and how many relays answered. */
        private suspend fun fetchToEose(
            client: INostrClient,
            relays: Set<NormalizedRelayUrl>,
            filters: List<Filter>,
            accept: (Event) -> Boolean,
            onRead: suspend (ScheduledTaskEvent, NormalizedRelayUrl) -> Unit,
        ): Triple<List<ScheduledTaskEvent>, List<DeletionRequestEvent>, Int> {
            val read = mutableListOf<ScheduledTaskEvent>()
            val deletions = mutableListOf<DeletionRequestEvent>()
            val author = filters.firstNotNullOfOrNull { it.authors?.firstOrNull() }
            val result =
                try {
                    client.fetchAllWithHooks(relays.associateWith { filters }, idleTimeoutMs = READ_IDLE_MS) { relay, event ->
                        if (event is ScheduledTaskEvent && accept(event) && event.verify()) {
                            onRead(event, relay)
                            read += event
                        } else if (event is DeletionRequestEvent && event.pubKey == author && event.verify()) {
                            deletions += event
                        }
                        true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read the scheduled tasks", e)
                    null
                }
            return Triple(read, deletions, result?.doneReasons?.values?.count { it == DONE_REASON_EOSE } ?: 0)
        }
    }
}

/**
 * The plans `amy schedule` and other editors apply to a task, as [ScheduledTaskRelays.edit]
 * transforms. Pure: no relay, no clock, no zone database.
 */
object ScheduledTaskEdits {
    /**
     * [current] running [cron] in [zone], set at [now] (NIP-FB, Editing: changing `cron` sets its
     * last value to now). A new task is created under [dTag] running [taskName] (default: [dTag]),
     * in [zone] or else [localZone], and [paused] when asked. An existing one keeps its zone unless
     * [zone] names another, and keeps its `cron` tag as it was when nothing in it changes; a
     * [taskName] replaces its `task`; [paused] pauses it too (without it, a paused task stays paused).
     */
    fun withSchedule(
        current: ScheduledTask?,
        dTag: String,
        cron: CronExpression,
        zone: String?,
        localZone: String,
        now: Long,
        taskName: String? = null,
        paused: Boolean = false,
    ): ScheduledTask {
        if (current == null) return ScheduledTask.create(dTag, taskName ?: dTag, cron.text, zone ?: localZone, now, paused)
        val nextZone = zone ?: current.tz ?: localZone
        val timed = if (current.cron == cron.text && current.tz == nextZone) current else current.withCron(cron.text, nextZone, now)
        val named = if (taskName != null && taskName != current.task) timed.replacing(TaskTag::isTag, TaskTag.assemble(taskName)) else timed
        return if (paused && !named.paused) named.withEnabled(false, now) else named
    }

    /** The next time [task] runs after [now] in [zone]: null while paused, or when its `cron` or zone can't be read. */
    fun nextRun(
        task: ScheduledTask,
        zone: CronZone?,
        now: Long,
    ): Long? {
        if (task.paused || zone == null || task.tz == null) return null
        return task.expression()?.nextAfter(maxOf(now, task.cronSetAt - 1), zone)
    }
}
