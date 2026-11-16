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
package com.vitorpamplona.nestsclient.moq

import com.vitorpamplona.nestsclient.moq.MoqSession.Companion.client
import com.vitorpamplona.nestsclient.moq.MoqSession.Companion.server
import com.vitorpamplona.nestsclient.transport.WebTransportBidiStream
import com.vitorpamplona.nestsclient.transport.WebTransportSession
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * MoQ-transport draft version constants. Value shape: `0xff00000000 | draft`
 * is how some draft tests label versions; draft-ietf-moq-transport-17 is
 * commonly represented as 0xff000011 but implementations vary. The actual
 * version used on the wire is negotiated in [MoqSession.setup].
 *
 * Listed as `Long` to survive the MSB-set encoding on JVM.
 */
object MoqVersion {
    /** draft-ietf-moq-transport-11 (a popular stable draft). */
    const val DRAFT_11: Long = 0xff00000bL

    /** draft-ietf-moq-transport-17 (newer draft). */
    const val DRAFT_17: Long = 0xff000011L
}

/**
 * Session wrapper over a [WebTransportSession] that speaks IETF
 * `draft-ietf-moq-transport-17` (CLIENT_SETUP / SERVER_SETUP, namespace
 * tuples, OBJECT_DATAGRAM with `track_alias`).
 *
 * **Wire-protocol scope:** this is the IETF MoQ-transport flavour, NOT
 * kixelated's moq-lite. The two are wire-incompatible (single-string
 * paths vs. tuple namespaces, different ANNOUNCE/SUBSCRIBE shape).
 * nostrnests's reference relay (`kixelated/moq-rs`) speaks moq-lite, so
 * this session cannot exchange a single MoQ message with the real
 * nostrnests stack. See `nestsClient/plans/2026-04-26-moq-lite-gap.md`
 * for the gap and the planned moq-lite parallel codec.
 *
 * Lifecycle:
 *   1. [client] / [server] attaches to a transport. No traffic yet.
 *   2. [setup] runs the SETUP handshake synchronously. After it returns
 *      successfully, two background pumps start in [pumpScope]:
 *       * a control-stream pump that buffers chunks, decodes complete frames,
 *         and dispatches each (currently SUBSCRIBE_OK / SUBSCRIBE_ERROR);
 *       * a datagram pump that decodes OBJECT_DATAGRAMs and routes them to
 *         the matching per-track flow.
 *   3. [subscribe] sends a SUBSCRIBE, awaits the OK, returns a
 *      [SubscribeHandle] whose [SubscribeHandle.objects] flow yields every
 *      OBJECT_DATAGRAM tagged with that subscription's track alias.
 *   4. [close] cancels the pumps and closes the transport.
 */
class MoqSession private constructor(
    private val transport: WebTransportSession,
    /** The single bidi stream every MoQ exchange uses for control. */
    private val controlStream: WebTransportBidiStream,
    private val role: Role,
    private val pumpScope: CoroutineScope,
) {
    enum class Role { Client, Server }

    /** Server's selected version; null until [setup] returns. */
    var selectedVersion: Long? = null
        private set

    /** Parameters the server sent back in SERVER_SETUP. Empty until [setup] returns. */
    var serverParameters: List<SetupParameter> = emptyList()
        private set

    private val stateMutex = Mutex()
    private val writeMutex = Mutex()
    private var nextSubscribeId = 0L

    /** subscribe_id → CompletableDeferred awaiting SUBSCRIBE_OK / SUBSCRIBE_ERROR. */
    private val pendingSubscribes = HashMap<Long, CompletableDeferred<SubscribeOk>>()

    /** track_alias → bounded channel that fans matching OBJECT_DATAGRAMs to the subscriber.
     *
     *  We use a [Channel] with [BufferOverflow.DROP_OLDEST] (rather than a
     *  [MutableSharedFlow]) because objects must be buffered between
     *  [subscribe] returning and the caller attaching to [SubscribeHandle.objects],
     *  even when no collector exists yet. SharedFlow with `replay=0` drops
     *  pre-subscription emissions, which races real-time audio delivery.
     */
    private val sinks = HashMap<Long, Channel<MoqObject>>()

    /** subscribe_id → track_alias, so [unsubscribe] can clean up the matching sink. */
    private val aliasBySubscribeId = HashMap<Long, Long>()

    // ------- Publisher-side state (Phase M5) ----------------------------------

    /** namespace → CompletableDeferred awaiting ANNOUNCE_OK / ANNOUNCE_ERROR. */
    private val pendingAnnounces = HashMap<TrackNamespace, CompletableDeferred<Unit>>()

    /** namespace → AnnounceHandleImpl (alive while we still publish that namespace). */
    private val announces = HashMap<TrackNamespace, AnnounceHandleImpl>()

    /**
     * Subscribers attached to one of our published tracks. Keyed by the
     * subscribeId chosen by the remote subscriber. Each carries the
     * (namespace, trackName, trackAlias, publisherPriority) tuple we need to
     * format outbound OBJECT_DATAGRAMs.
     */
    private val inboundSubscribers = HashMap<Long, InboundSubscription>()

    /**
     * subscribers attached per published track. The list is rewritten
     * (copy-on-write) under [stateMutex] so [TrackPublisherImpl.send] can
     * read a snapshot without acquiring a lock per OBJECT.
     */
    private val publisherSubscribers = HashMap<TrackPublisherImpl, List<InboundSubscription>>()

    private var controlPumpJob: Job? = null
    private var datagramPumpJob: Job? = null
    private var closed = false

    /**
     * Run the SETUP handshake.
     *
     * Client side: writes CLIENT_SETUP with [supportedVersions] + [clientParameters],
     *   then reads exactly one SERVER_SETUP, stores the result, and starts the
     *   background pumps.
     * Server side: reads exactly one CLIENT_SETUP, selects the first mutually
     *   supported version from [supportedVersions], writes SERVER_SETUP, and
     *   starts the background pumps.
     *
     * @throws MoqProtocolException if the peer sent an unexpected message, or
     *   if no version overlap exists (server side).
     */
    suspend fun setup(
        supportedVersions: List<Long>,
        clientParameters: List<SetupParameter> = emptyList(),
        handshakeTimeoutMs: Long = 10_000,
    ) {
        withTimeout(handshakeTimeoutMs) {
            when (role) {
                Role.Client -> runClientSetup(supportedVersions, clientParameters)
                Role.Server -> runServerSetup(supportedVersions, clientParameters)
            }
        }
        startPumps()
    }

    private suspend fun runClientSetup(
        supportedVersions: List<Long>,
        clientParameters: List<SetupParameter>,
    ) {
        controlStream.write(MoqCodec.encode(ClientSetup(supportedVersions, clientParameters)))
        val reply = readOneSetupMessage()
        val server =
            reply as? ServerSetup
                ?: throw MoqProtocolException("expected SERVER_SETUP, got ${reply.type.name}")
        if (server.selectedVersion !in supportedVersions) {
            throw MoqProtocolException(
                "server picked version 0x${server.selectedVersion.toString(16)} which we did not offer",
            )
        }
        selectedVersion = server.selectedVersion
        serverParameters = server.parameters
    }

    private suspend fun runServerSetup(
        acceptedVersions: List<Long>,
        serverParameters: List<SetupParameter>,
    ) {
        val incoming = readOneSetupMessage()
        val client =
            incoming as? ClientSetup
                ?: throw MoqProtocolException("expected CLIENT_SETUP, got ${incoming.type.name}")
        val overlap =
            client.supportedVersions.firstOrNull { it in acceptedVersions }
                ?: throw MoqProtocolException(
                    "no mutually-supported MoQ version (client offered ${client.supportedVersions})",
                )
        controlStream.write(MoqCodec.encode(ServerSetup(overlap, serverParameters)))
        this.selectedVersion = overlap
        this.serverParameters = serverParameters
    }

    /**
     * Bootstrap helper: read exactly one SETUP-phase message from the control
     * stream. Used only during the synchronous handshake (before the pump
     * starts). SETUP frames always fit in a single transport write, so we can
     * safely take the first chunk and decode.
     */
    private suspend fun readOneSetupMessage(): MoqMessage {
        val chunk = controlStream.incoming().first()
        val decoded =
            MoqCodec.decode(chunk)
                ?: throw MoqProtocolException(
                    "control-stream chunk did not contain a complete MoQ frame (size=${chunk.size})",
                )
        return decoded.message
    }

    // ---------------------------------------------------------------- Subscribe

    /**
     * Send SUBSCRIBE and suspend until SUBSCRIBE_OK arrives. Returns a
     * [SubscribeHandle] whose [SubscribeHandle.objects] flow emits every
     * OBJECT_DATAGRAM tagged with this subscription's track alias.
     *
     * @throws MoqProtocolException if the publisher rejects the subscribe with
     *   SUBSCRIBE_ERROR, or if the session is closed mid-flight.
     */
    suspend fun subscribe(
        namespace: TrackNamespace,
        trackName: ByteArray,
        priority: Int = 0x80,
        filter: SubscribeFilter = SubscribeFilter.LatestGroup,
        objectBufferCapacity: Int = DEFAULT_OBJECT_BUFFER,
    ): SubscribeHandle {
        check(!closed) { "session is closed" }

        val subscribeId: Long
        val trackAlias: Long
        val deferred = CompletableDeferred<SubscribeOk>()
        val sink =
            Channel<MoqObject>(
                capacity = objectBufferCapacity,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        stateMutex.withLock {
            check(!closed) { "session is closed" }
            subscribeId = nextSubscribeId++
            trackAlias = subscribeId // 1:1 alias = id keeps things simple
            pendingSubscribes[subscribeId] = deferred
            sinks[trackAlias] = sink
            aliasBySubscribeId[subscribeId] = trackAlias
        }

        val frame =
            MoqCodec.encode(
                Subscribe(
                    subscribeId = subscribeId,
                    trackAlias = trackAlias,
                    namespace = namespace,
                    trackName = trackName,
                    subscriberPriority = priority,
                    filter = filter,
                ),
            )
        try {
            writeMutex.withLock { controlStream.write(frame) }
            val ok = deferred.await()
            return SubscribeHandle(
                subscribeId = subscribeId,
                trackAlias = trackAlias,
                ok = ok,
                objects = sink.receiveAsFlow(),
                unsubscribeAction = { unsubscribe(subscribeId) },
            )
        } catch (t: Throwable) {
            // Roll back state on any failure (write error, deferred cancellation,
            // SUBSCRIBE_ERROR, session close).
            stateMutex.withLock {
                pendingSubscribes.remove(subscribeId)
                sinks.remove(trackAlias)?.close()
                aliasBySubscribeId.remove(subscribeId)
            }
            throw t
        }
    }

    // ---------------------------------------------------------------- Announce (publisher)

    /**
     * Send ANNOUNCE for [namespace] and suspend until ANNOUNCE_OK arrives.
     * Returns an [AnnounceHandle] you can [AnnounceHandle.openTrack] on for
     * each speaker / sub-track you intend to publish under that namespace.
     *
     * The session keeps the announce alive until you call
     * [AnnounceHandle.unannounce] (or [close]); during that time, inbound
     * SUBSCRIBE messages whose namespace matches will be routed to the
     * publisher you registered via [AnnounceHandle.openTrack].
     *
     * @throws MoqProtocolException if the peer rejects with ANNOUNCE_ERROR
     *   or the session closes mid-flight.
     * @throws IllegalStateException if [namespace] is already announced on
     *   this session.
     */
    suspend fun announce(
        namespace: TrackNamespace,
        parameters: List<SetupParameter> = emptyList(),
    ): AnnounceHandle {
        check(!closed) { "session is closed" }
        val deferred = CompletableDeferred<Unit>()
        val handle = AnnounceHandleImpl(namespace)

        stateMutex.withLock {
            check(!closed) { "session is closed" }
            check(!announces.containsKey(namespace)) { "namespace $namespace is already announced" }
            check(!pendingAnnounces.containsKey(namespace)) { "namespace $namespace announce already in flight" }
            announces[namespace] = handle
            pendingAnnounces[namespace] = deferred
        }

        val frame = MoqCodec.encode(Announce(namespace = namespace, parameters = parameters))
        try {
            writeMutex.withLock { controlStream.write(frame) }
            deferred.await()
            return handle
        } catch (t: Throwable) {
            stateMutex.withLock {
                pendingAnnounces.remove(namespace)
                announces.remove(namespace)
            }
            throw t
        }
    }

    /**
     * Send UNSUBSCRIBE and tear down all local state for [subscribeId]. Safe
     * to call multiple times — second call is a no-op.
     */
    suspend fun unsubscribe(subscribeId: Long) {
        val alias =
            stateMutex.withLock {
                pendingSubscribes.remove(subscribeId)?.completeExceptionally(
                    MoqProtocolException("unsubscribed before SUBSCRIBE_OK arrived"),
                )
                aliasBySubscribeId.remove(subscribeId)?.also { sinks.remove(it)?.close() }
            } ?: return

        if (closed) return
        runCatching {
            writeMutex.withLock { controlStream.write(MoqCodec.encode(Unsubscribe(subscribeId))) }
        }
        // Suppress write errors on unsubscribe — the session is being torn down
        // and the alias is already gone from local state. `alias` is intentionally
        // referenced so the compiler keeps the cleanup expression's side effects.
        @Suppress("UNUSED_VARIABLE")
        val ignored = alias
    }

    // ---------------------------------------------------------------- Pumps

    private fun startPumps() {
        // If a pump exits unexpectedly (transport died, peer hung up), the
        // session is no longer healthy — failing in-flight subscribe/announce
        // waiters and flipping `closed=true` prevents new operations from
        // hanging on a peer that will never reply. The control pump is the
        // primary signal for transport death; the datagram pump exiting on
        // its own is rare but treated the same way.
        controlPumpJob =
            pumpScope.launch {
                try {
                    runControlPump()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    runCatching { close(0, "control pump failed: ${t.message}") }
                }
            }
        datagramPumpJob =
            pumpScope.launch {
                try {
                    runDatagramPump()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    runCatching { close(0, "datagram pump failed: ${t.message}") }
                }
            }
    }

    private suspend fun runControlPump() {
        var buffer = ByteArray(0)
        controlStream.incoming().collect { chunk ->
            buffer =
                if (buffer.isEmpty()) {
                    chunk
                } else {
                    val merged = ByteArray(buffer.size + chunk.size)
                    buffer.copyInto(merged, 0)
                    chunk.copyInto(merged, buffer.size)
                    merged
                }
            while (true) {
                val decoded =
                    try {
                        MoqCodec.decode(buffer) ?: break
                    } catch (e: MoqUnknownTypeException) {
                        // Forward-compat: a peer sending a draft-17 control
                        // message we don't enumerate (FETCH, GOAWAY,
                        // MAX_SUBSCRIBE_ID, …) shouldn't poison the rest of
                        // the buffer. Skip just this frame and parse on.
                        buffer = buffer.copyOfRange(e.bytesConsumed, buffer.size)
                        continue
                    } catch (e: MoqCodecException) {
                        // Genuinely-corrupted bytes — we have no idea where
                        // the next valid frame starts. Drop the buffer; the
                        // pump stays alive so the next valid frame can
                        // recover the session.
                        buffer = ByteArray(0)
                        break
                    }
                buffer = buffer.copyOfRange(decoded.bytesConsumed, buffer.size)
                dispatchControlMessage(decoded.message)
            }
        }
    }

    private suspend fun dispatchControlMessage(msg: MoqMessage) {
        when (msg) {
            is SubscribeOk -> {
                val deferred =
                    stateMutex.withLock { pendingSubscribes.remove(msg.subscribeId) }
                deferred?.complete(msg)
            }

            is SubscribeError -> {
                val deferred =
                    stateMutex.withLock {
                        val d = pendingSubscribes.remove(msg.subscribeId)
                        aliasBySubscribeId.remove(msg.subscribeId)?.also { sinks.remove(it)?.close() }
                        d
                    }
                deferred?.completeExceptionally(
                    MoqProtocolException(
                        "SUBSCRIBE rejected: code=${msg.errorCode} reason=${msg.reasonPhrase}",
                    ),
                )
            }

            // ----- Publisher-side -------------------------------------------

            is AnnounceOk -> {
                val deferred = stateMutex.withLock { pendingAnnounces.remove(msg.namespace) }
                deferred?.complete(Unit)
            }

            is AnnounceError -> {
                // Two cases:
                //   (a) ANNOUNCE_ERROR arrives BEFORE ANNOUNCE_OK — the peer
                //       refused our announce. The deferred is still pending;
                //       fail it and remove the announces[] entry we
                //       optimistically inserted in announce().
                //   (b) ANNOUNCE_ERROR arrives AFTER ANNOUNCE_OK — the peer
                //       is revoking a previously-accepted announce
                //       (session-level kick, e.g. policy change). Mark the
                //       handle session-closed so further sends/openTracks
                //       short-circuit, but don't remove from announces[]
                //       since unannounce() is still the canonical removal
                //       path and inbound SUBSCRIBE must continue to see the
                //       namespace as "withdrawn" rather than missing.
                val handle =
                    stateMutex.withLock {
                        val pending = pendingAnnounces.remove(msg.namespace)
                        if (pending != null) {
                            announces.remove(msg.namespace)
                            pending.completeExceptionally(
                                MoqProtocolException(
                                    "ANNOUNCE rejected: code=${msg.errorCode} reason=${msg.reasonPhrase}",
                                ),
                            )
                            null
                        } else {
                            announces[msg.namespace]
                        }
                    }
                handle?.let { existing ->
                    val shouldWrite =
                        stateMutex.withLock {
                            existing.markSessionClosedLocked()
                            // Same compare-and-set as unannounce() to avoid
                            // a duplicate UNANNOUNCE on the wire when a
                            // concurrent unannounce() is mid-flight.
                            if (existing.unannounceWritten) {
                                false
                            } else {
                                existing.unannounceWritten = true
                                true
                            }
                        }
                    if (shouldWrite) {
                        runCatching {
                            writeMutex.withLock { controlStream.write(MoqCodec.encode(Unannounce(msg.namespace))) }
                        }
                    }
                    stateMutex.withLock { announces.remove(msg.namespace) }
                }
            }

            is Subscribe -> {
                handleInboundSubscribe(msg)
            }

            is Unsubscribe -> {
                handleInboundUnsubscribe(msg)
            }

            else -> {
                // Echoed SETUP and other unhandled control messages are
                // intentionally dropped — they don't change session state.
            }
        }
    }

    /**
     * Server (or peer) sent us a SUBSCRIBE for a track we ANNOUNCEd. Look up
     * the matching publisher; on hit reply with SUBSCRIBE_OK and start
     * routing this subscriber's OBJECTs. On miss reply with SUBSCRIBE_ERROR
     * so the peer doesn't hang.
     */
    private suspend fun handleInboundSubscribe(msg: Subscribe) {
        val (publisher, sub) =
            stateMutex.withLock {
                val handle = announces[msg.namespace]
                val publisher = handle?.publisherForLocked(msg.trackName)
                if (publisher == null) {
                    null to null
                } else {
                    val sub =
                        InboundSubscription(
                            subscribeId = msg.subscribeId,
                            trackAlias = msg.trackAlias,
                            namespace = msg.namespace,
                            trackName = msg.trackName,
                            publisher = publisher,
                        )
                    inboundSubscribers[msg.subscribeId] = sub
                    val current = publisherSubscribers[publisher].orEmpty()
                    publisherSubscribers[publisher] = current + sub
                    publisher to sub
                }
            }

        if (publisher == null || sub == null) {
            val err =
                SubscribeError(
                    subscribeId = msg.subscribeId,
                    errorCode = ErrorCode.TRACK_DOES_NOT_EXIST,
                    reasonPhrase = "no publisher for that namespace+name",
                    trackAlias = msg.trackAlias,
                )
            runCatchingCancellable { writeMutex.withLock { controlStream.write(MoqCodec.encode(err)) } }
            return
        }

        val ok =
            SubscribeOk(
                subscribeId = msg.subscribeId,
                expiresMs = 0L,
                groupOrder = 0,
                contentExists = false,
            )
        runCatchingCancellable { writeMutex.withLock { controlStream.write(MoqCodec.encode(ok)) } }
    }

    private suspend fun handleInboundUnsubscribe(msg: Unsubscribe) {
        val removed =
            stateMutex.withLock {
                val sub = inboundSubscribers.remove(msg.subscribeId) ?: return@withLock null
                val current = publisherSubscribers[sub.publisher].orEmpty()
                val updated = current.filter { it.subscribeId != msg.subscribeId }
                if (updated.isEmpty()) {
                    publisherSubscribers.remove(sub.publisher)
                } else {
                    publisherSubscribers[sub.publisher] = updated
                }
                sub
            } ?: return

        // Best-effort SUBSCRIBE_DONE so the peer's local state matches.
        val done =
            SubscribeDone(
                subscribeId = removed.subscribeId,
                statusCode = SubscribeDoneStatus.UNSUBSCRIBED,
                streamCount = 0L,
                reasonPhrase = "unsubscribed",
            )
        runCatching { writeMutex.withLock { controlStream.write(MoqCodec.encode(done)) } }
    }

    private suspend fun runDatagramPump() {
        transport.incomingDatagrams().collect { datagram ->
            val obj =
                try {
                    MoqObjectDatagram.decode(datagram)
                } catch (e: MoqCodecException) {
                    // Drop malformed datagrams — UDP-style transport, so partial
                    // / corrupted packets are normal.
                    return@collect
                }
            val sink = stateMutex.withLock { sinks[obj.trackAlias] }
            // trySend on a DROP_OLDEST channel always succeeds (drops oldest on
            // overflow). Returning value is intentionally ignored.
            sink?.trySend(obj)
        }
    }

    /** Cancel the pumps and close the underlying transport. Idempotent. */
    suspend fun close(
        code: Int = 0,
        reason: String = "",
    ) {
        stateMutex.withLock {
            if (closed) return
            closed = true
            // Fail any in-flight subscribe waiters with a domain exception
            // (not Cancellation — that would propagate as scope cancellation
            // in the awaiting coroutine instead of a recoverable error).
            val sessionGone = MoqProtocolException("session closed")
            for ((_, deferred) in pendingSubscribes) {
                deferred.completeExceptionally(sessionGone)
            }
            pendingSubscribes.clear()
            for ((_, ch) in sinks) ch.close()
            sinks.clear()
            aliasBySubscribeId.clear()
            // Fail in-flight announce waiters the same way.
            for ((_, deferred) in pendingAnnounces) {
                deferred.completeExceptionally(sessionGone)
            }
            pendingAnnounces.clear()
            announces.values.forEach { it.markSessionClosedLocked() }
            announces.clear()
            inboundSubscribers.clear()
            publisherSubscribers.clear()
        }
        // Cancel pumps then JOIN them, so any in-flight pump write
        // (handleInboundSubscribe writing SUBSCRIBE_OK / SUBSCRIBE_ERROR,
        // handleInboundUnsubscribe writing SUBSCRIBE_DONE) finishes its
        // writeMutex.withLock { ... } critical section before we send FIN
        // on the control stream.
        //
        // Self-join guard: when close() is called from a pump's own
        // exception handler (the try/catch in startPumps), `coroutineContext[Job]`
        // is the very Job we're trying to join. Joining ourselves
        // suspends forever — the lambda can't finish until close() returns,
        // and close() can't return until the lambda finishes. Skip the
        // join in that case (round-2 audit MoQ #1).
        val ctrlPump = controlPumpJob
        val datagramPump = datagramPumpJob
        ctrlPump?.cancel()
        datagramPump?.cancel()
        val self = currentCoroutineContext()[Job]
        runCatching { if (ctrlPump != null && ctrlPump !== self) ctrlPump.join() }
        runCatching { if (datagramPump != null && datagramPump !== self) datagramPump.join() }
        runCatching { writeMutex.withLock { controlStream.finish() } }
        runCatching { transport.close(code, reason) }
    }

    // ---------------------------------------------------------------- Publisher implementations

    /**
     * Public, addressable announce on this session. Returned by [announce] and
     * lives until [unannounce] (or session [close]).
     */
    interface AnnounceHandle {
        val namespace: TrackNamespace

        /**
         * Register a publisher for one track name under this namespace. nests
         * uses the speaker's pubkey hex (UTF-8 bytes) as the track name.
         *
         * The returned [TrackPublisher] is the only way to push OBJECTs out;
         * each call to [TrackPublisher.send] becomes one OBJECT_DATAGRAM
         * fanned out to every active inbound subscriber for this track.
         */
        suspend fun openTrack(name: ByteArray): TrackPublisher

        /**
         * Withdraw the namespace: send UNANNOUNCE, send SUBSCRIBE_DONE to
         * every inbound subscriber that's still attached, and close every
         * registered [TrackPublisher]. Idempotent.
         */
        suspend fun unannounce()
    }

    /** A single outbound track. Push OBJECTs with [send]; tear down with [close]. */
    interface TrackPublisher {
        val name: ByteArray

        /**
         * Send one OBJECT (one Opus frame for nests audio) as an
         * OBJECT_DATAGRAM to every inbound subscriber currently attached.
         * Group/object ids are managed internally; this matches the
         * audio-rooms NIP draft (one group, monotonic object ids).
         *
         * @return true if at least one datagram was queued for delivery.
         *   Returns false (and is a silent no-op) if no inbound subscriber
         *   exists yet — callers can keep producing audio without buffering.
         */
        suspend fun send(payload: ByteArray): Boolean

        /**
         * Stop publishing this track. Sends SUBSCRIBE_DONE to every attached
         * subscriber, removes the track from the parent announce. Idempotent.
         */
        suspend fun close()
    }

    /**
     * Internal accounting for a peer's inbound SUBSCRIBE on one of our
     * published tracks. We send each `publisher.send(payload)` as a separate
     * OBJECT_DATAGRAM keyed by this subscription's negotiated trackAlias.
     */
    internal class InboundSubscription(
        val subscribeId: Long,
        val trackAlias: Long,
        val namespace: TrackNamespace,
        val trackName: ByteArray,
        val publisher: TrackPublisherImpl,
    )

    internal inner class AnnounceHandleImpl(
        override val namespace: TrackNamespace,
    ) : AnnounceHandle {
        // Non-suspending reads/writes on `tracks` are all guarded by the
        // session's `stateMutex` (held by the suspend caller).
        internal val tracks = HashMap<TrackKey, TrackPublisherImpl>()
        internal var sessionClosed = false

        // True once UNANNOUNCE has been written on the wire, so a
        // concurrent unannounce() + post-OK AnnounceError handler don't
        // both emit it (audit round-2 MoQ #2). Read/written under stateMutex.
        internal var unannounceWritten = false

        override suspend fun openTrack(name: ByteArray): TrackPublisher {
            val key = TrackKey(name)
            val publisher = TrackPublisherImpl(name)
            stateMutex.withLock {
                check(!closed) { "session is closed" }
                check(!sessionClosed) { "namespace is unannounced" }
                check(tracks[key] == null) { "track ${name.decodeToString()} already published" }
                tracks[key] = publisher
                publisher.parent = this
            }
            return publisher
        }

        // Caller must hold stateMutex.
        fun publisherForLocked(trackName: ByteArray): TrackPublisherImpl? = tracks[TrackKey(trackName)]

        override suspend fun unannounce() {
            val toClose: List<TrackPublisherImpl>
            stateMutex.withLock {
                if (sessionClosed) return
                sessionClosed = true
                toClose = tracks.values.toList()
                tracks.clear()
                // Note: keep `announces[namespace] = this` until UNANNOUNCE
                // is on the wire. Inbound SUBSCRIBE during this window will
                // see `sessionClosed=true` via publisherForLocked → null →
                // we reply SUBSCRIBE_ERROR(TRACK_DOES_NOT_EXIST), which is
                // accurate (no track to serve). Removing now would race
                // with the control pump and leave a window where the peer
                // thinks the namespace exists but our state has dropped it.
            }
            toClose.forEach { runCatching { it.close() } }
            // Race guard: a post-OK AnnounceError handler may have already
            // written UNANNOUNCE for this namespace (audit round-2 MoQ #2).
            // Compare-and-set under stateMutex so only one writer fires
            // the wire frame.
            val shouldWrite =
                stateMutex.withLock {
                    if (unannounceWritten) {
                        false
                    } else {
                        unannounceWritten = true
                        true
                    }
                }
            if (shouldWrite && !closed) {
                runCatching {
                    writeMutex.withLock { controlStream.write(MoqCodec.encode(Unannounce(namespace))) }
                }
            }
            // Now safe to forget the namespace entirely — UNANNOUNCE is on
            // the wire so any later inbound SUBSCRIBE was sent without
            // knowing the namespace was withdrawn.
            stateMutex.withLock { announces.remove(namespace) }
        }

        /**
         * Called from session.close while the caller already holds stateMutex.
         * Drops every track without trying to send a wire UNANNOUNCE.
         */
        fun markSessionClosedLocked() {
            sessionClosed = true
            tracks.values.forEach { it.markSessionDeadLocked() }
            tracks.clear()
        }
    }

    internal inner class TrackPublisherImpl(
        override val name: ByteArray,
    ) : TrackPublisher {
        var parent: AnnounceHandleImpl? = null
        internal var nextObjectId = 0L
        internal val groupId = 0L // single-group track per audio-rooms NIP draft
        internal var trackClosed = false
        internal var sessionDead = false

        override suspend fun send(payload: ByteArray): Boolean {
            val snapshotAndId =
                stateMutex.withLock {
                    if (trackClosed || sessionDead) return false
                    val snapshot = publisherSubscribers[this]
                    if (snapshot.isNullOrEmpty()) return false
                    val id = nextObjectId
                    nextObjectId += 1
                    snapshot to id
                }
            val (snapshot, objectId) = snapshotAndId
            var anySent = false
            for (sub in snapshot) {
                val datagram =
                    MoqObjectDatagram.encode(
                        MoqObject(
                            trackAlias = sub.trackAlias,
                            groupId = groupId,
                            objectId = objectId,
                            publisherPriority = DEFAULT_PUBLISHER_PRIORITY,
                            payload = payload,
                        ),
                    )
                runCatchingCancellable { transport.sendDatagram(datagram) }
                    .onSuccess { anySent = true }
            }
            // Roll the objectId back if the entire fan-out failed (e.g.
            // transport down). The audio-rooms NIP wants object ids
            // monotonic; gaps from real network loss are acceptable per
            // spec, but a gap caused by a fully-failed local send is
            // pure noise we can avoid. We only roll back if no
            // concurrent send has already grabbed an id past us.
            if (!anySent) {
                stateMutex.withLock {
                    if (nextObjectId == objectId + 1) nextObjectId = objectId
                }
            }
            return anySent
        }

        override suspend fun close() {
            val snapshot: List<InboundSubscription>
            stateMutex.withLock {
                if (trackClosed) return
                trackClosed = true
                snapshot = publisherSubscribers.remove(this).orEmpty()
                snapshot.forEach { inboundSubscribers.remove(it.subscribeId) }
                parent?.tracks?.remove(TrackKey(name))
            }
            if (closed || sessionDead) return
            for (sub in snapshot) {
                val done =
                    SubscribeDone(
                        subscribeId = sub.subscribeId,
                        statusCode = SubscribeDoneStatus.TRACK_ENDED,
                        streamCount = 0L,
                        reasonPhrase = "track closed",
                    )
                runCatching { writeMutex.withLock { controlStream.write(MoqCodec.encode(done)) } }
            }
        }

        /** Caller already holds stateMutex (called from session.close). */
        fun markSessionDeadLocked() {
            sessionDead = true
            trackClosed = true
            publisherSubscribers.remove(this)
        }
    }

    /** Equality wrapper for byte-array keys in [AnnounceHandleImpl.tracks]. */
    internal data class TrackKey(
        val name: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is TrackKey) return false
            return name.contentEquals(other.name)
        }

        override fun hashCode(): Int = name.contentHashCode()
    }

    companion object {
        /**
         * Default per-subscription buffer for unread objects. 64 frames at
         * Opus 20 ms = ~1.3 s of audio backlog before DROP_OLDEST kicks in,
         * which matches a typical real-time listener's tolerance.
         */
        const val DEFAULT_OBJECT_BUFFER: Int = 64

        /**
         * Publisher priority byte used for outbound OBJECT_DATAGRAMs from
         * [TrackPublisher.send]. 0x80 is the middle of the byte range — a
         * sensible neutral default until nests cares about prioritising
         * speakers over each other.
         */
        const val DEFAULT_PUBLISHER_PRIORITY: Int = 0x80

        /**
         * Attach to a [WebTransportSession] in the client role.
         *
         * @param pumpScope where the post-handshake pumps live. Tests typically
         *   pass `backgroundScope` from `runTest`; production code passes the
         *   owning ViewModel scope so pumps are cancelled on screen exit.
         */
        suspend fun client(
            transport: WebTransportSession,
            pumpScope: CoroutineScope,
        ): MoqSession {
            val control = transport.openBidiStream()
            return MoqSession(transport, control, Role.Client, pumpScope)
        }

        /**
         * Attach to a [WebTransportSession] in the server role over an
         * already-accepted control stream. Used in tests — a real server
         * accepts the client's first bidi.
         */
        fun server(
            transport: WebTransportSession,
            control: WebTransportBidiStream,
            pumpScope: CoroutineScope,
        ): MoqSession = MoqSession(transport, control, Role.Server, pumpScope)
    }
}

/**
 * Handle to an active subscription. [objects] emits every OBJECT_DATAGRAM the
 * publisher delivers for this subscription's track. Call [unsubscribe] when
 * done — equivalent to [MoqSession.unsubscribe] with this handle's id.
 */
class SubscribeHandle internal constructor(
    val subscribeId: Long,
    val trackAlias: Long,
    val ok: SubscribeOk,
    val objects: Flow<MoqObject>,
    private val unsubscribeAction: suspend () -> Unit,
    private val broadcastEndedCountProbe: () -> Long = { 0L },
) {
    suspend fun unsubscribe() = unsubscribeAction()

    /**
     * How many times this session has seen the subscribed broadcast's
     * announce go `Ended` (moq-lite), read live. Each `Ended` is a
     * publisher session going away, so a rise between two subscriptions
     * means the later one is served by a new publisher session, whose
     * groups may restart at 0. Always 0 for transports without announces,
     * and on the handle `connectReconnectingNestsListener` returns, which
     * outlives the publisher sessions it follows.
     */
    val broadcastEndedCount: Long get() = broadcastEndedCountProbe()
}

/** Thrown when the peer violates the MoQ-transport state machine. */
class MoqProtocolException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * MoQ-transport SUBSCRIBE_ERROR / ANNOUNCE_ERROR error codes. Draft revisions
 * shuffle these around; the values here track the most stable subset.
 */
object ErrorCode {
    /** Track the publisher knows nothing about. */
    const val TRACK_DOES_NOT_EXIST: Long = 0x04L
}

/**
 * MoQ-transport SUBSCRIBE_DONE status_code values. Per draft-ietf-moq-transport
 * (draft-11+): 0x00 = UNSUBSCRIBED, 0x03 = TRACK_ENDED. The constants
 * shipped initially had these inverted, which the audit caught — a peer
 * decoded our "track closed" SUBSCRIBE_DONE as INTERNAL_ERROR.
 */
object SubscribeDoneStatus {
    /** Subscriber explicitly UNSUBSCRIBEd; publisher is acknowledging. */
    const val UNSUBSCRIBED: Long = 0x00L

    /** The publisher has no more objects coming for this track. */
    const val TRACK_ENDED: Long = 0x03L
}
