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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.localPrivKeyOrNull
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The account's morning schedule as this device knows it. */
@Immutable
sealed interface SyncedSchedule {
    /** No schedule event seen yet: none exists (not set up), or it hasn't arrived. */
    data object Unknown : SyncedSchedule

    data class Readable(
        val event: ScheduledTaskEvent,
        val task: ScheduledTask,
    ) : SyncedSchedule

    /**
     * Not opened yet: this signer needs the reader to decrypt (a NIP-46 bunker, a NIP-55 app), so
     * it is opened when the front page is, or when the reader acts, never in the background.
     */
    data class Locked(
        val event: ScheduledTaskEvent,
    ) : SyncedSchedule

    /**
     * The signer didn't open it: it refused, timed out, or can't decrypt at all (a read-only
     * login, [retryable] false). Nothing wrong with the schedule itself.
     */
    data class CantDecrypt(
        val event: ScheduledTaskEvent,
        val retryable: Boolean,
    ) : SyncedSchedule

    /** Content that isn't a schedule this app can read: never overwritten. */
    data class Unreadable(
        val event: ScheduledTaskEvent,
    ) : SyncedSchedule
}

/**
 * The newest schedule as a runner reads it before claiming: [quorum] when more than half of the
 * write [relays] answered (NIP-FB: otherwise the device doesn't run). [event] is the newest the
 * relays returned, never this device's own copy of an event they didn't take; [floor] is the
 * newest this device knows of at all, which a write's `created_at` must still beat.
 */
class QuorumRead(
    val event: ScheduledTaskEvent?,
    val task: ScheduledTask?,
    val quorum: Boolean,
    val relays: Set<NormalizedRelayUrl>,
    val floor: ScheduledTaskEvent? = event,
    /**
     * The newest NIP-09 deletion of the task that any answering relay holds, when it deletes the
     * newest event: then there is no task ([event] null), and a new one must be newer than it.
     */
    val deletedAt: Long? = null,
)

/** How an edit of the schedule ended. */
@Immutable
sealed interface ScheduleEdit {
    data class Saved(
        val task: ScheduledTask,
    ) : ScheduleEdit

    /** The edit changed nothing, so nothing was written. */
    data object Unchanged : ScheduleEdit

    /** Not more than half of the reader's write relays answered (or none is set): nothing was written. */
    data object Unreachable : ScheduleEdit

    /** Some of the write relays took it, but not more than half: other devices may not see it. */
    data object NotAccepted : ScheduleEdit

    /** The newest event can't be read here: rewriting it would lose what it holds. */
    data object Unreadable : ScheduleEdit

    /** The signer refused or failed to sign or encrypt. */
    data object CannotSign : ScheduleEdit

    /** The reader has no NIP-65 write relays: there is nowhere to keep a shared schedule. */
    data object NoWriteRelays : ScheduleEdit

    /** The time picked can't be said as one time in the schedule's zone (a clock change that day). */
    data object NotExpressible : ScheduleEdit

    /** This device can't name its own time zone, so it writes nothing that needs one. */
    data object NoZone : ScheduleEdit
}

/**
 * What an edit writes, from what was read: [current] is the newest event's task (null when there
 * is no event, or it is unreadable: [hasEvent] tells them apart), [quorum] whether more than half
 * of the write relays answered the read. Nothing is written without that: too few answers is
 * not "no schedule". Null task in the result means nothing is written, with that outcome.
 */
fun planScheduleEdit(
    hasEvent: Boolean,
    current: ScheduledTask?,
    quorum: Boolean,
    transform: (ScheduledTask?) -> ScheduledTask?,
): Pair<ScheduledTask?, ScheduleEdit> {
    if (!quorum) return null to ScheduleEdit.Unreachable
    if (hasEvent && current == null) return null to ScheduleEdit.Unreadable
    val next = transform(current) ?: return null to ScheduleEdit.Unchanged
    if (next == current) return null to ScheduleEdit.Unchanged
    return next to ScheduleEdit.Saved(next)
}

/** How one event's private tags came out. */
sealed interface Decrypted {
    data class Task(
        val task: ScheduledTask,
    ) : Decrypted

    data class SignerFailed(
        val retryable: Boolean,
    ) : Decrypted

    data object BadContent : Decrypted
}

/**
 * What the claim protocol and the editors need from the reader's relays: one quorum read, one
 * publish, a watch, a read-modify-write. [ObserverScheduleState] is the real one; tests use their own.
 */
interface ScheduleRelays {
    /** The newest schedule across every write relay, read to EOSE. */
    suspend fun readQuorum(): QuorumRead

    /** [event]'s tags, public and private; null when this signer can't open them. */
    suspend fun taskOf(event: ScheduledTaskEvent): ScheduledTask?

    /** Signs [task] as the event after [previous], sends it to [relays]; true when more than half accepted it. */
    suspend fun publish(
        previous: ScheduledTaskEvent?,
        task: ScheduledTask,
        relays: Set<NormalizedRelayUrl>,
    ): Pair<ScheduledTaskEvent, Boolean>

    /**
     * Every schedule event [relays] send while collected (verified, this author's), as one growing
     * list. [onSubscribed] hears each relay once its REQ is in place (its EOSE, or its CLOSED), so
     * a claim is published only after the watch for a competing one is.
     */
    fun watch(
        relays: Set<NormalizedRelayUrl>,
        onSubscribed: (NormalizedRelayUrl) -> Unit = {},
    ): Flow<List<ScheduledTaskEvent>>

    /**
     * Read-modify-write (NIP-FB, Editing). With [resend], an edit whose result the newest event
     * already holds sends that event to every write relay again (a `ran` that too few took).
     */
    suspend fun edit(
        now: Long = TimeUtils.now(),
        resend: Boolean = false,
        transform: (ScheduledTask?) -> ScheduledTask?,
    ): ScheduleEdit
}

/**
 * The account's morning Observer schedule, a NIP-FB scheduled task (kind 34817, `d` =
 * `observer`), decrypted from the cache as it arrives with the account's own lists, read and
 * written only on the reader's NIP-65 write relays ([writeRelays], never a default or fallback):
 * more than half of them must answer each read and accept each write
 * (`quartz/plans/2026-11-11-observer-site.md`, "The morning schedule, synced").
 *
 * Signed without the account's `client` tag: the schedule says nothing public about the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserverScheduleState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    private val client: INostrClient,
    /** The reader's NIP-65 write relays, nothing else. */
    val writeRelays: StateFlow<Set<NormalizedRelayUrl>>,
) : ScheduleRelays {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val scheduleNote = cache.getOrCreateAddressableNote(ObserverSchedule.address(signer.pubKey))

    /** A key held here decrypts on its own; anything else asks the reader, so only when they are here. */
    private val decryptsAlone get() = signer.localPrivKeyOrNull() != null

    private fun event(): ScheduledTaskEvent? = scheduleNote.event as? ScheduledTaskEvent

    /**
     * One decrypt per event id: relay copies of one event are new instances, and the screens, the
     * claim and its watch all ask. Only answers keep: a signer that failed is asked again later.
     */
    private val decrypted = LinkedHashMap<HexKey, Decrypted>()
    private val decrypting = Mutex()

    /** [event]'s private tags, decrypted once per id. */
    suspend fun decrypt(event: ScheduledTaskEvent): Decrypted {
        if (event.pubKey != signer.pubKey) return Decrypted.BadContent
        if (event.content.isBlank()) return Decrypted.Task(ScheduledTask(event.tags, emptyArray()))
        return decrypting.withLock {
            decrypted[event.id]?.let { return@withLock it }
            val result = decryptNow(event)
            if (result !is Decrypted.SignerFailed) {
                decrypted[event.id] = result
                while (decrypted.size > KEEP_DECRYPTED) decrypted.remove(decrypted.keys.first())
            }
            result
        }
    }

    private suspend fun decryptNow(event: ScheduledTaskEvent): Decrypted {
        if (!signer.isWriteable()) return Decrypted.SignerFailed(retryable = false)
        // NIP-FB, Runner: with a runner named in a public `p`, the private tags are NIP-44 between
        // the reader and that runner; else the reader's to themselves. Each candidate is tried.
        val peers = event.privateTagsPeers(signer.pubKey).ifEmpty { listOf(signer.pubKey) }
        var plain: String? = null
        var failure: Exception? = null
        for (peer in peers) {
            try {
                plain = signer.nip44Decrypt(event.content, peer)
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                // As Quartz's PrivateTagArrayEvent.decrypt: only a failed decryption means "try the
                // next peer". The signer's own answer (the reader refused, it timed out, it needs the
                // foreground) holds for every peer: asking again would prompt them once per peer.
                // Not kept ([decrypt]): the next open asks again.
                if (!mayBeWrongPeer(e)) break
            }
        }
        if (plain == null) {
            // A key on the device that can't open it: the content is bad. A remote signer: it refused, or timed out.
            Log.w(TAG, "Could not decrypt the morning schedule", failure)
            return if (decryptsAlone) Decrypted.BadContent else Decrypted.SignerFailed(retryable = true)
        }
        return try {
            Decrypted.Task(ScheduledTask(event.tags, PrivateTagsInContent.decode(plain)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Decrypted.BadContent
        }
    }

    override suspend fun taskOf(event: ScheduledTaskEvent): ScheduledTask? = (decrypt(event) as? Decrypted.Task)?.task

    /**
     * Whether [e], thrown decrypting with one peer, may only mean the content was encrypted to
     * another: the signer couldn't decrypt it, or it isn't a signer's answer at all.
     */
    private fun mayBeWrongPeer(e: Exception): Boolean =
        e !is SignerExceptions ||
            e is SignerExceptions.CouldNotPerformException ||
            e is SignerExceptions.UnauthorizedDecryptionException

    /** What is known without asking the signer: a decrypt already done, or nothing. */
    private fun known(event: ScheduledTaskEvent): Decrypted? {
        if (event.content.isBlank() && event.pubKey == signer.pubKey) return Decrypted.Task(ScheduledTask(event.tags, emptyArray()))
        return if (decrypting.tryLock()) {
            try {
                decrypted[event.id]
            } finally {
                decrypting.unlock()
            }
        } else {
            null
        }
    }

    private suspend fun decode(
        event: ScheduledTaskEvent?,
        mayAsk: Boolean,
    ): SyncedSchedule {
        if (event == null) return SyncedSchedule.Unknown
        val result = if (mayAsk || decryptsAlone) decrypt(event) else known(event) ?: return SyncedSchedule.Locked(event)
        return when (result) {
            is Decrypted.Task -> SyncedSchedule.Readable(event, result.task)
            is Decrypted.SignerFailed -> SyncedSchedule.CantDecrypt(event, result.retryable)
            Decrypted.BadContent -> SyncedSchedule.Unreadable(event)
        }
    }

    /** How many screens want the schedule opened now, and each "try again". */
    private val asks = MutableStateFlow(Asks(0, 0))

    private data class Asks(
        val open: Int,
        val tries: Int,
    )

    /**
     * A front page screen is showing: a signer that needs the reader may be asked now. Returns
     * the call that says it went.
     */
    fun opened(): () -> Unit {
        asks.update { it.copy(open = it.open + 1) }
        var done = false
        return {
            if (!done) {
                done = true
                asks.update { it.copy(open = (it.open - 1).coerceAtLeast(0)) }
            }
        }
    }

    /** The reader asked to try the signer again. */
    fun retryDecrypt() = asks.update { it.copy(tries = it.tries + 1) }

    /**
     * The schedule as the cache holds it. With a key on this device it is decrypted as it
     * arrives; with a signer that needs the reader, only while a screen asks ([opened]) or the
     * reader tries again ([retryDecrypt]), once each, with no retry loop.
     */
    val flow: StateFlow<SyncedSchedule> =
        combine(
            scheduleNote
                .flow()
                .metadata.stateFlow
                .map { it.note.event as? ScheduledTaskEvent }
                .onStart { emit(event()) }
                .distinctUntilChanged(),
            asks,
        ) { event, ask -> Triple(event, ask.open > 0, ask.tries) }
            .distinctUntilChanged()
            .mapLatest { (event, open, tries) -> decode(event, mayAsk = open || tries > 0) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, SyncedSchedule.Unknown)

    /** The quorum read, the publish and the read-modify-write, with this cache as their memory. */
    private val relays =
        ScheduledTaskRelays(
            signer = signer,
            client = client,
            dTag = ObserverSchedule.D_TAG,
            writeRelays = { writeRelays.value },
            decrypt = ::taskOf,
            known = ::event,
            onRead = { event, relay -> cache.justConsume(event, relay, wasVerified = true) },
            onSigned = { cache.justConsumeMyOwnEvent(it) },
        )

    override suspend fun readQuorum(): QuorumRead = relays.readQuorum()

    override suspend fun publish(
        previous: ScheduledTaskEvent?,
        task: ScheduledTask,
        relays: Set<NormalizedRelayUrl>,
    ): Pair<ScheduledTaskEvent, Boolean> = this.relays.publish(previous, task, relays)

    override fun watch(
        relays: Set<NormalizedRelayUrl>,
        onSubscribed: (NormalizedRelayUrl) -> Unit,
    ): Flow<List<ScheduledTaskEvent>> =
        callbackFlow {
            val subId = RandomInstance.randomChars(10)
            // Verified once each: an event already seen is never checked again.
            val seen = mutableSetOf<HexKey>()
            val events = mutableListOf<ScheduledTaskEvent>()
            val lock = Mutex()
            val listener =
                object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (event !is ScheduledTaskEvent || event.pubKey != signer.pubKey || !ObserverSchedule.isObserverSchedule(event)) return
                        val snapshot =
                            lock.withLock {
                                if (!seen.add(event.id) || !event.verify()) return
                                events += event
                                events.toList()
                            }
                        trySend(snapshot)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) = onSubscribed(relay)

                    override fun onClosed(
                        message: String,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) = onSubscribed(relay)
                }
            client.subscribe(subId, relays.associateWith { listOf(ObserverSchedule.filter(signer.pubKey)) }, listener)
            awaitClose { client.unsubscribe(subId) }
        }.conflate()

    override suspend fun edit(
        now: Long,
        resend: Boolean,
        transform: (ScheduledTask?) -> ScheduledTask?,
    ): ScheduleEdit = relays.edit(now, resend, transform)

    companion object {
        private const val TAG = "ObserverScheduleState"
        private const val KEEP_DECRYPTED = 8
    }
}
