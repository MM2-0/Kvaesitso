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
package com.vitorpamplona.amethyst.commons.relays.eventsync

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.ExplainedFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync.Companion.MAX_ACTIVITY_LOG
import com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync.Companion.MAX_CONCURRENT_RELAYS
import com.vitorpamplona.amethyst.commons.relays.eventsync.EventSync.LiveSyncActivity.SourceRelayInfo
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesFromPool
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentMap
import com.vitorpamplona.quartz.utils.concurrent.ConcurrentSet
import com.vitorpamplona.quartz.utils.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Syncs the user's events across all known relays:
 *  1. Downloads all events authored by the user and sends them to their outbox relays.
 *  2. Downloads all events that p-tag the user (non-DM) and sends them to their inbox relays.
 *  3. Downloads kind-4, kind-1059 and kind-21059 events that p-tag the user and sends them to DM relays.
 *
 * Up to [MAX_CONCURRENT_RELAYS] relays are queried in parallel. As soon as one relay is fully
 * exhausted (all pages retrieved) the next relay from the list starts immediately, keeping the
 * concurrency window full at all times.
 *
 * Each relay is paginated individually: after EOSE the oldest [Event.createdAt] seen on that
 * relay becomes the next `until` cursor, repeating until the relay returns no new events.
 *
 * OK (true) responses from destination relays are tracked via [RelayConnectionListener] and
 * attributed back to the source relay that contributed each event.
 *
 * Live activity is emitted via [liveActivity] so the UI can show a per-relay log of events
 * received and events accepted by destination relays.
 *
 * Scoped to the AccountViewModel so the sync survives navigation within the same session.
 */
@Stable
class EventSync(
    private val accountPubKey: HexKey,
    private val relayDb: () -> List<NormalizedRelayUrl>,
    private val outboxTargets: () -> Set<NormalizedRelayUrl>,
    private val inboxTargets: () -> Set<NormalizedRelayUrl>,
    private val dmTargets: () -> Set<NormalizedRelayUrl>,
    private val clientBuilder: () -> INostrClient,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Maximum number of relays queried at the same time. */
        const val MAX_CONCURRENT_RELAYS = 50

        /** How long (ms) to wait for a single relay to reply per page before giving up. */
        const val RELAY_TIMEOUT_MS = 30_000L

        /** Maximum number of completed-relay entries kept in the activity log. */
        const val MAX_ACTIVITY_LOG = 5000

        /** Poll interval while waiting for the last forwarded events to be acknowledged. */
        const val OUTBOX_DRAIN_POLL_MS = 100L
    }

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

    sealed class SyncState {
        object Idle : SyncState()

        data class Running(
            val relaysCompleted: MutableStateFlow<Int>,
            val totalRelays: MutableStateFlow<Int>,
            val eventsSent: MutableStateFlow<Int>,
            val eventsReceived: MutableStateFlow<Int>,
            val eventsAccepted: MutableStateFlow<Int>,
            val sinceFilter: Long? = null,
            val untilFilter: Long? = null,
        ) : SyncState() {
            constructor(relaysCompleted: Int, totalRelays: Int, eventsSent: Int, eventsReceived: Int, eventsAccepted: Int, sinceFilter: Long? = null, untilFilter: Long? = null) :
                this(
                    relaysCompleted = MutableStateFlow(relaysCompleted),
                    totalRelays = MutableStateFlow(totalRelays),
                    eventsSent = MutableStateFlow(eventsSent),
                    eventsReceived = MutableStateFlow(eventsReceived),
                    eventsAccepted = MutableStateFlow(eventsAccepted),
                    sinceFilter = sinceFilter,
                    untilFilter = untilFilter,
                )
        }

        data class Done(
            val totalEventsReceived: Int,
            val totalEventsSent: Int,
            val totalEventsAccepted: Int,
            val durationMs: Long,
            val sinceFilter: Long? = null,
            val untilFilter: Long? = null,
        ) : SyncState()

        data class Error(
            val message: String,
            val sinceFilter: Long? = null,
            val untilFilter: Long? = null,
        ) : SyncState()
    }

    /**
     * Per-relay activity snapshot emitted continuously while the sync runs.
     *
     * @param completedRelays  Last [MAX_ACTIVITY_LOG] relays that finished, sorted by most events
     *                         found (descending) so the most productive sources appear first.
     * @param outboxTargets  Relays receiving events authored by the user.
     * @param inboxTargets  Relays receiving events that mention the user.
     * @param dmTargets  Relays receiving DMs addressed to the user.
     */
    @Stable
    data class LiveSyncActivity(
        val runningRelays: Map<NormalizedRelayUrl, SourceRelayInfo> = emptyMap(),
        val completedRelays: Map<NormalizedRelayUrl, SourceRelayInfo> = emptyMap(),
        val outboxTargets: Map<NormalizedRelayUrl, DestinationRelayInfo> = emptyMap(),
        val inboxTargets: Map<NormalizedRelayUrl, DestinationRelayInfo> = emptyMap(),
        val dmTargets: Map<NormalizedRelayUrl, DestinationRelayInfo> = emptyMap(),
    ) {
        val sortedCompletedRelays =
            completedRelays.values.let {
                // precompute to avoid: Comparison method violates its general contract

                val orderCacheEventsFound = it.associateWith { it.eventsFound.value }
                val orderCacheCompleted = it.associateWith { it.status.value == ConnectionStatus.Completed }
                val orderComparator =
                    compareByDescending<SourceRelayInfo> {
                        orderCacheEventsFound[it]
                    }.thenByDescending {
                        orderCacheCompleted[it]
                    }

                it.sortedWith(orderComparator)
            }

        constructor(
            runningRelays: List<SourceRelayInfo>,
            completedRelays: List<SourceRelayInfo>,
            outboxTargets: List<DestinationRelayInfo>,
            inboxTargets: List<DestinationRelayInfo>,
            dmTargets: List<DestinationRelayInfo>,
        ) : this(
            runningRelays.associateBy { it.relay },
            completedRelays.associateBy { it.relay },
            outboxTargets.associateBy { it.relay },
            inboxTargets.associateBy { it.relay },
            dmTargets.associateBy { it.relay },
        )

        sealed interface ConnectionStatus {
            object Connecting : ConnectionStatus

            object Querying : ConnectionStatus

            class Error(
                val msg: String,
            ) : ConnectionStatus

            object Completed : ConnectionStatus
        }

        /**
         * @param eventsFound  Total events received from this relay across all pages.
         * @param eventsAccepted  Events from this relay that destination relays accepted as new
         *                        (OK true). Reflects the count at relay-completion time; late
         *                        OK responses may not be included.
         */
        @Stable
        data class SourceRelayInfo(
            val relay: NormalizedRelayUrl,
            val status: MutableStateFlow<ConnectionStatus>,
            val eventsFound: MutableStateFlow<Int>,
            val eventsAccepted: MutableStateFlow<Int>,
            val pageUntil: MutableStateFlow<Long?> = MutableStateFlow(null),
        ) {
            constructor(relay: NormalizedRelayUrl, status: ConnectionStatus, eventsFound: Int, eventsAccepted: Int, untilPage: Long? = null) :
                this(
                    relay = relay,
                    status = MutableStateFlow(status),
                    eventsFound = MutableStateFlow(eventsFound),
                    eventsAccepted = MutableStateFlow(eventsAccepted),
                    pageUntil = MutableStateFlow(untilPage),
                )
        }

        /**
         * @param relay  The destination relay URL.
         * @param eventsSent  Number of events sent to this relay.
         * @param eventsAccepted  Number of OK=true responses received from this relay.
         */
        @Stable
        data class DestinationRelayInfo(
            val relay: NormalizedRelayUrl,
            val eventsSent: MutableStateFlow<Int>,
            val eventsAccepted: MutableStateFlow<Int>,
        ) {
            constructor(relay: NormalizedRelayUrl, eventsSent: Int, eventsAccepted: Int) :
                this(relay, MutableStateFlow(eventsSent), MutableStateFlow(eventsAccepted))
        }
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _liveActivity = MutableStateFlow(LiveSyncActivity())
    val liveActivity: StateFlow<LiveSyncActivity> = _liveActivity

    private fun emitLiveSnapshot(
        runningRelays: Set<NormalizedRelayUrl> = emptySet(),
        completedRelays: Set<NormalizedRelayUrl> = emptySet(),
        liveOutboxTargets: Set<NormalizedRelayUrl> = emptySet(),
        liveInboxTargets: Set<NormalizedRelayUrl> = emptySet(),
        liveDmTargets: Set<NormalizedRelayUrl> = emptySet(),
    ) {
        _liveActivity.value =
            LiveSyncActivity(
                runningRelays =
                    runningRelays.associateWith {
                        SourceRelayInfo(
                            relay = it,
                            status = MutableStateFlow(LiveSyncActivity.ConnectionStatus.Connecting),
                            eventsFound = MutableStateFlow<Int>(0),
                            eventsAccepted = MutableStateFlow<Int>(0),
                        )
                    },
                completedRelays =
                    completedRelays.associateWith {
                        SourceRelayInfo(
                            relay = it,
                            status = MutableStateFlow(LiveSyncActivity.ConnectionStatus.Connecting),
                            eventsFound = MutableStateFlow<Int>(0),
                            eventsAccepted = MutableStateFlow<Int>(0),
                        )
                    },
                outboxTargets =
                    liveOutboxTargets.associateWith { relay ->
                        LiveSyncActivity.DestinationRelayInfo(
                            relay = relay,
                            eventsSent = MutableStateFlow<Int>(0),
                            eventsAccepted = MutableStateFlow<Int>(0),
                        )
                    },
                inboxTargets =
                    liveInboxTargets.associateWith { relay ->
                        LiveSyncActivity.DestinationRelayInfo(
                            relay = relay,
                            eventsSent = MutableStateFlow<Int>(0),
                            eventsAccepted = MutableStateFlow<Int>(0),
                        )
                    },
                dmTargets =
                    liveDmTargets.associateWith { relay ->
                        LiveSyncActivity.DestinationRelayInfo(
                            relay = relay,
                            eventsSent = MutableStateFlow<Int>(0),
                            eventsAccepted = MutableStateFlow<Int>(0),
                        )
                    },
            )
    }

    // -------------------------------------------------------------------------
    // Control functions
    // -------------------------------------------------------------------------

    /** User-selected date range for the sync filter (epoch seconds). */
    val sinceSecs = MutableStateFlow<Long?>(null)
    val untilSecs = MutableStateFlow<Long?>(null)

    private var syncJob: Job? = null

    fun start() {
        if (_syncState.value is SyncState.Running) return
        _liveActivity.value = LiveSyncActivity()
        val capturedSince = sinceSecs.value
        val capturedUntil = untilSecs.value
        syncJob =
            scope.launch(Dispatchers.IO) {
                runSync(capturedSince, capturedUntil)
            }
    }

    fun cancel() {
        syncJob?.cancel()
    }

    // -------------------------------------------------------------------------
    // Sync logic
    // -------------------------------------------------------------------------
    suspend fun runSync(
        filterSince: Long? = null,
        filterUntil: Long? = null,
    ) {
        val startTime = currentTimeMillis()

        _liveActivity.value = LiveSyncActivity()

        val myPubKey = accountPubKey

        val relaysToProcess = relayDb()

        if (relaysToProcess.isEmpty()) {
            _syncState.value =
                SyncState.Error("No known relays found. Browse some content first to discover relays.", filterSince, filterUntil)
            return
        }

        val totalRelays = relaysToProcess.size

        val outboxTargets = outboxTargets()
        val inboxTargets = inboxTargets()
        val dmTargets = dmTargets()

        val defaultFilters =
            buildList {
                if (outboxTargets.isNotEmpty()) add(ExplainedFilter(authors = listOf(myPubKey), since = filterSince, until = filterUntil, purpose = SubPurpose.ACCOUNT_DATA, purposeDetail = "Event Sync"))
                if (inboxTargets.isNotEmpty() || dmTargets.isNotEmpty()) {
                    add(ExplainedFilter(tags = mapOf("p" to listOf(myPubKey)), since = filterSince, until = filterUntil, purpose = SubPurpose.NOTIFICATIONS, purposeDetail = "Event Sync"))
                }
            }

        if (defaultFilters.isEmpty()) {
            _syncState.value = SyncState.Error("No outbox, inbox, or DM relays configured.", filterSince, filterUntil)
            return
        }

        val usersRelays = outboxTargets + inboxTargets + dmTargets

        val perRelayFilters =
            relaysToProcess.associateWith {
                if (it !in usersRelays) {
                    defaultFilters
                } else {
                    buildList {
                        if (it !in outboxTargets) add(ExplainedFilter(authors = listOf(myPubKey), since = filterSince, until = filterUntil, purpose = SubPurpose.ACCOUNT_DATA, purposeDetail = "Event Sync"))
                        if (it !in inboxTargets && it !in dmTargets) {
                            add(ExplainedFilter(tags = mapOf("p" to listOf(myPubKey)), since = filterSince, until = filterUntil, purpose = SubPurpose.NOTIFICATIONS, purposeDetail = "Event Sync"))
                        }
                    }
                }
            }

        emitLiveSnapshot(
            emptySet(),
            emptySet(),
            outboxTargets,
            inboxTargets,
            dmTargets,
        )

        // Thread-safe dedup sets — prevent the same event from being forwarded twice when
        // multiple source relays return the same event concurrently.
        val outboxDedup = ConcurrentSet<String>()
        val inboxDedup = ConcurrentSet<String>()
        val dmDedup = ConcurrentSet<String>()

        val sourceRelayOfEvent = ConcurrentMap<HexKey, NormalizedRelayUrl>()

        // (event id, destination) pairs already counted as sent. The outbox is
        // at-least-once: it writes an event as soon as the socket is ready and
        // resends everything still unacknowledged when the connection finishes
        // syncing, so one event can hit the same relay twice before its OK lands.
        // The relay dedups the second copy; the counters must too.
        val sentPairs = ConcurrentSet<String>()

        val runningState =
            SyncState.Running(
                relaysCompleted = 0,
                totalRelays = totalRelays,
                eventsSent = 0,
                eventsReceived = 0,
                eventsAccepted = 0,
                sinceFilter = filterSince,
                untilFilter = filterUntil,
            )

        val okListener =
            object : RelayConnectionListener {
                override fun onCannotConnect(
                    relay: IRelayClient,
                    errorMessage: String,
                ) {
                    super.onCannotConnect(relay, errorMessage)
                    val currentStatus = liveActivity.value.runningRelays[relay.url]?.status
                    if (currentStatus?.value !is LiveSyncActivity.ConnectionStatus.Error) {
                        currentStatus?.tryEmit(LiveSyncActivity.ConnectionStatus.Error(errorMessage))
                    }
                }

                override fun onSent(
                    relay: IRelayClient,
                    cmdStr: String,
                    cmd: Command,
                    success: Boolean,
                ) {
                    super.onSent(relay, cmdStr, cmd, success)
                    // `success` is "written to the socket", not "OK received". A write to a
                    // destination that is still connecting fails and the outbox resends it
                    // once the socket opens; counting the failed attempt too made every
                    // cold destination report one extra event sent. Likewise a successful
                    // resend of an unacknowledged event is the same send, not a second one.
                    if (cmd is EventCmd && success && sentPairs.add(cmd.event.id + relay.url.url)) {
                        var hasSent = false

                        if (outboxDedup.contains(cmd.event.id)) {
                            liveActivity.value.outboxTargets[relay.url]
                                ?.eventsSent
                                ?.update { it + 1 }
                            hasSent = true
                        }
                        if (inboxDedup.contains(cmd.event.id)) {
                            liveActivity.value.inboxTargets[relay.url]
                                ?.eventsSent
                                ?.update { it + 1 }
                            hasSent = true
                        }
                        if (dmDedup.contains(cmd.event.id)) {
                            liveActivity.value.dmTargets[relay.url]
                                ?.eventsSent
                                ?.update { it + 1 }
                            hasSent = true
                        }

                        if (hasSent) {
                            runningState.eventsSent.update { it + 1 }
                        }
                    } else if (cmd is ReqCmd) {
                        val currentStatus = liveActivity.value.runningRelays[relay.url]?.status
                        if (currentStatus?.value != LiveSyncActivity.ConnectionStatus.Querying) {
                            currentStatus?.tryEmit(LiveSyncActivity.ConnectionStatus.Querying)
                        }
                    }
                }

                override suspend fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    if (msg is OkMessage && msg.success && msg.message.isBlank()) {
                        // remove() is atomic: returns non-null only for the first OK per event.
                        val sourceRelay = sourceRelayOfEvent.remove(msg.eventId)
                        if (sourceRelay != null) {
                            liveActivity.value.runningRelays[sourceRelay]
                                ?.eventsAccepted
                                ?.update { it + 1 }
                        }

                        if (outboxDedup.contains(msg.eventId)) {
                            val relayTarget = liveActivity.value.outboxTargets[relay.url]
                            if (relayTarget != null) {
                                relayTarget.eventsAccepted.update { it + 1 }
                                runningState.eventsAccepted.update { it + 1 }
                            }
                        }
                        if (dmDedup.contains(msg.eventId)) {
                            val relayTarget = liveActivity.value.dmTargets[relay.url]
                            if (relayTarget != null) {
                                relayTarget.eventsAccepted.update { it + 1 }
                                runningState.eventsAccepted.update { it + 1 }
                            }
                        }
                        if (inboxDedup.contains(msg.eventId)) {
                            val relayTarget = liveActivity.value.inboxTargets[relay.url]
                            if (relayTarget != null) {
                                relayTarget.eventsAccepted.update { it + 1 }
                                runningState.eventsAccepted.update { it + 1 }
                            }
                        }
                    }
                }
            }

        _syncState.emit(runningState)

        clientBuilder().use { client ->
            client.addConnectionListener(okListener)
            try {
                client.fetchAllPagesFromPool(
                    filters = perRelayFilters,
                    idleTimeoutMs = RELAY_TIMEOUT_MS,
                    maxConcurrentRelays = MAX_CONCURRENT_RELAYS,
                    onNewPage = { until, sourceRelay ->
                        _liveActivity.value.runningRelays[sourceRelay]
                            ?.pageUntil
                            ?.tryEmit(until)
                    },
                    onEvent = { event, sourceRelay ->
                        val isMyEvent = event.pubKey == myPubKey
                        val mentionsMe = event.tags.isTaggedUser(myPubKey)
                        val isDmKind = event.kind == 4 || event.kind == GiftWrapEvent.KIND || event.kind == EphemeralGiftWrapEvent.KIND

                        val live = liveActivity.value

                        var newEvent = false
                        var matchesAtLeastOneFilter = false

                        // Each routing rule is independent: an event can match more than one.
                        if (isMyEvent && outboxTargets.isNotEmpty()) {
                            if (outboxDedup.add(event.id)) {
                                client.publish(event, outboxTargets)
                                newEvent = true
                            }
                            matchesAtLeastOneFilter = true
                        }
                        if (mentionsMe && isDmKind && dmTargets.isNotEmpty()) {
                            if (dmDedup.add(event.id)) {
                                client.publish(event, dmTargets)
                                newEvent = true
                            }
                            matchesAtLeastOneFilter = true
                        }
                        if (mentionsMe && !isDmKind && inboxTargets.isNotEmpty()) {
                            if (inboxDedup.add(event.id)) {
                                client.publish(event, inboxTargets)
                                newEvent = true
                            }
                            matchesAtLeastOneFilter = true
                        }

                        if (newEvent) {
                            sourceRelayOfEvent[event.id] = sourceRelay
                        }

                        if (matchesAtLeastOneFilter) {
                            runningState.eventsReceived.update { it + 1 }

                            live.runningRelays[sourceRelay]?.eventsFound?.update { it + 1 }
                            live.completedRelays[sourceRelay]?.eventsFound?.update { it + 1 }
                        }
                    },
                    onRelayStart = { relay ->
                        _liveActivity.update {
                            it.copy(
                                runningRelays =
                                    it.runningRelays +
                                        Pair(relay, SourceRelayInfo(relay, LiveSyncActivity.ConnectionStatus.Connecting, 0, 0)),
                            )
                        }
                    },
                    onRelayComplete = { relay, _ ->
                        _liveActivity.update {
                            val newCompleted = it.runningRelays[relay]
                            it.copy(
                                runningRelays = it.runningRelays.minus(relay),
                                completedRelays =
                                    if (newCompleted != null) {
                                        it.completedRelays.plus(relay to newCompleted)
                                    } else {
                                        it.completedRelays
                                    },
                            )
                        }

                        val status = _liveActivity.value.completedRelays[relay]?.status

                        if (status?.value !is LiveSyncActivity.ConnectionStatus.Error) {
                            status?.tryEmit(LiveSyncActivity.ConnectionStatus.Completed)
                        }

                        runningState.relaysCompleted.update { it + 1 }
                    },
                )

                // `publish` is fire-and-forget through the client's outbox, and `use` closes
                // the client as soon as this block returns. Without a drain, the events
                // forwarded from the last page of the last relay are still waiting for a
                // socket or an OK when the outbox is destroyed — the sync reports Done and
                // silently never delivers them. Bounded by the same per-relay timeout.
                awaitOutboxDrain(client, outboxDedup.snapshot() + inboxDedup.snapshot() + dmDedup.snapshot())

                _syncState.value =
                    SyncState.Done(
                        totalEventsReceived = runningState.eventsReceived.value,
                        totalEventsSent = runningState.eventsSent.value,
                        totalEventsAccepted = runningState.eventsAccepted.value,
                        durationMs = currentTimeMillis() - startTime,
                        sinceFilter = filterSince,
                        untilFilter = filterUntil,
                    )
            } catch (e: Exception) {
                _syncState.value =
                    SyncState.Done(
                        totalEventsReceived = runningState.eventsReceived.value,
                        totalEventsSent = runningState.eventsSent.value,
                        totalEventsAccepted = runningState.eventsAccepted.value,
                        durationMs = currentTimeMillis() - startTime,
                        sinceFilter = filterSince,
                        untilFilter = filterUntil,
                    )

                if (e is CancellationException) throw e
                _syncState.value = SyncState.Error(e.message ?: "Unknown error", filterSince, filterUntil)
            } finally {
                client.removeConnectionListener(okListener)
            }
        }
    }

    /**
     * Waits until no forwarded event in [ids] has a relay left in the client's outbox, or
     * until [RELAY_TIMEOUT_MS] passes. Ids that drain are dropped from the working set so
     * each poll only revisits what is still pending.
     */
    private suspend fun awaitOutboxDrain(
        client: INostrClient,
        ids: Set<HexKey>,
    ) {
        val pending = ids.toMutableSet()
        withTimeoutOrNull(RELAY_TIMEOUT_MS) {
            while (pending.isNotEmpty()) {
                pending.removeAll { client.pendingPublishRelaysFor(it).isNullOrEmpty() }
                if (pending.isNotEmpty()) delay(OUTBOX_DRAIN_POLL_MS)
            }
        }
    }
}
