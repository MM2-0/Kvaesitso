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
package com.vitorpamplona.amethyst.commons.model.buzz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupDeletions
import com.vitorpamplona.amethyst.commons.relayClient.channel.relayGroup.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.amethyst.commons.util.logSignerErrors
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.workspace.isBuzzDm
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Discovers the Buzz workspace channels the user already belongs to on a relay (joined via the
 * Buzz web/desktop app) so they can add them to Amethyst. Hosted by the relay group-list screen,
 * which folds this membership-scoped section in when the relay is a Buzz relay.
 *
 * Buzz membership is server-side (no NIP-29 kind-10009 join event), so a Buzz relay exposes no
 * public group directory — the generic "browse a relay" flow returns nothing. Instead the relay
 * addresses each member a kind-44100 member-added notification (`#p` = me). This warm-authenticates
 * the relay (NIP-42) and reads those, then fetches each channel's kind-39000 metadata to keep only
 * the **non-DM** workspace channels (DMs surface separately). Adding one calls [Account.follow] to
 * append it to the user's kind-10009 list — which makes it appear in the Messages list and load at
 * boot through the existing relay-group machinery, no separate registry needed.
 */
class BuzzRelayImportViewModel : ViewModel() {
    @Volatile private var account: Account? = null
    private var relay: NormalizedRelayUrl? = null

    val relayUrl: NormalizedRelayUrl? get() = relay

    sealed interface Status {
        data object Loading : Status

        data object Ready : Status

        data class Error(
            val message: String,
        ) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The non-DM workspace channels the user is a member of on this relay. */
    private val _channels = MutableStateFlow<List<GroupId>>(emptyList())
    val channels: StateFlow<List<GroupId>> = _channels.asStateFlow()

    /**
     * Channel ids on this relay that are currently in the user's kind-10009 list. Mirrors the live
     * list rather than snapshotting it: it used to be seeded once at [bind] and only ever grow, so a
     * channel taken off Messages anywhere else still rendered as "Added" — with the Add action
     * disabled, leaving no way back.
     */
    private val _added = MutableStateFlow<Set<String>>(emptySet())
    val added: StateFlow<Set<String>> = _added.asStateFlow()

    fun bind(
        account: Account,
        relayUrlStr: String,
    ) {
        if (this.account != null) return
        val normalized =
            RelayUrlNormalizer.normalizeOrNull(relayUrlStr) ?: run {
                _status.value = Status.Error("Invalid relay URL")
                return
            }
        this.account = account
        this.relay = normalized

        // The user came here to import from THIS relay: remember it as a joined workspace (persisted,
        // marks the Buzz dialect) and pre-approve NIP-42 auth so the `#p=me` read below is served.
        val newlyJoined = account.buzzWorkspaces.join(normalized)
        viewModelScope.launch { account.relayAuthLedger.setDecision(normalized.url, RelayAuthDecision.ALLOW) }

        // Unlocks the persistent group-roster (39002) subscription — see [reconnectPoolAfterJoin].
        if (newlyJoined) reconnectPoolAfterJoin(account.client)

        // Track "already added" against the live kind-10009 list, scoped to this relay, so the rows
        // follow every add/remove — from here, from the channel's top bar, or from another device.
        viewModelScope.launch(Dispatchers.IO) {
            account.relayGroupList.liveRelayGroupIds.collect { groups ->
                _added.value = groups.filter { it.relayUrl == normalized }.mapTo(mutableSetOf()) { it.id }
            }
        }

        discover(account, normalized)
    }

    private fun discover(
        account: Account,
        relay: NormalizedRelayUrl,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _status.value = Status.Loading
            try {
                val myPubkey = account.userProfile().pubkeyHex
                // Written from the fetch callback's thread, read once it returns.
                val seenChannelIds = ConcurrentSet<String>()

                // 1. Warm-auth then read the relay's kind-44100 member-added notifications for me.
                account.client.taggedAs(SubPurpose.RELAY_GROUPS).fetchAllWithHooks(
                    filters =
                        mapOf(
                            relay to
                                listOf(
                                    Filter(
                                        kinds = listOf(MemberAddedNotificationEvent.KIND),
                                        tags = mapOf("p" to listOf(myPubkey)),
                                    ),
                                ),
                        ),
                    idleTimeoutMs = 8_000,
                    pendingOnAuthRequired = true,
                ) { _, event ->
                    (event as? MemberAddedNotificationEvent)?.channel()?.let { seenChannelIds.add(it) }
                    false
                }
                val channelIds: Set<String> = seenChannelIds.snapshot()

                // 2. Fetch each channel's NIP-29 metadata (39000-39003, `#d`-scoped) so its name + Buzz
                //    `t` type load, AND the relay's kind-40099 system messages (`#h`-scoped) so a
                //    `channel_deleted` one is seen. The relay soft-deletes a deleted channel's 39000
                //    and never retracts the kind-44100 that seeded `channelIds`, so without pulling the
                //    40099 a deleted channel — its metadata now blank — would show optimistically here
                //    forever. LocalCache records the delete into RelayGroupDeletions on consume.
                if (channelIds.isNotEmpty()) {
                    account.client.taggedAs(SubPurpose.RELAY_GROUPS).fetchAllWithHooks(
                        filters =
                            mapOf(
                                relay to
                                    listOf(
                                        Filter(kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf("d" to channelIds.toList())),
                                        Filter(kinds = listOf(SystemMessageEvent.KIND), tags = mapOf("h" to channelIds.toList())),
                                    ),
                            ),
                        idleTimeoutMs = 8_000,
                        pendingOnAuthRequired = true,
                    ) { _, _ -> false }
                }

                // 3. Keep only the non-DM workspace channels the relay still serves metadata for.
                //
                //    A deleted (or never-really-there) channel keeps its kind-44100 — the relay never
                //    retracts it — but the relay soft-deletes its 39000, so it arrives here with NO
                //    metadata. That absence is the reliable "it's gone" signal (the relay does not serve
                //    a `channel_deleted` 40099 for an already-deleted channel, so the fetch above can't
                //    catch this case). Before, such a channel showed optimistically — as a bare UUID row
                //    with "No messages yet", which is exactly the leak reported.
                //
                //    So drop channels with no metadata — but ONLY when the fetch clearly worked (at least
                //    one channel came back with a 39000). If none did, the read failed (auth/connectivity)
                //    and we keep the whole set rather than blank the community; a genuinely new channel
                //    whose 39000 is merely slow re-appears on the next bind once its metadata is cached.
                val channels = channelIds.associateWith { LocalCache.getOrCreateRelayGroupChannel(GroupId(it, relay)) }
                val fetchHadMetadata = channels.values.any { it.event != null }
                _channels.value =
                    channelIds
                        .mapNotNull { id ->
                            val groupId = GroupId(id, relay)
                            if (RelayGroupDeletions.isDeleted(groupId)) return@mapNotNull null
                            val channel = channels.getValue(id)
                            if (fetchHadMetadata && channel.event == null) return@mapNotNull null
                            if (channel.event?.isBuzzDm() == true) null else groupId
                        }.sortedBy { it.id }
                _status.value = Status.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.value = Status.Error(e.message ?: "Could not load your channels")
            }
        }
    }

    /**
     * Append [groupId] to the user's kind-10009 list (public group tag), so it shows in Messages, and
     * clear any earlier dismissal so the relay's kind-44100 re-announcement isn't filtered back out.
     * [_added] is not touched here — the live-list collector in [bind] reflects the new event.
     */
    fun add(groupId: GroupId) {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            logSignerErrors("BuzzRelayImport") {
                val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
                account.settings.undismissChannelInvite(groupId.id)
                account.follow(channel)
            }
        }
    }

    /**
     * Take [groupId] off the kind-10009 list without leaving the channel: no kind-9022, so the relay
     * roster (and therefore this very list of channels) is untouched and it can be added back. The
     * dismissal keeps a Buzz relay's kind-44100 re-announcement from bouncing it back as an invite —
     * mirrors `AccountViewModel.removeRelayGroupFromMessages`.
     */
    fun remove(groupId: GroupId) {
        val account = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            logSignerErrors("BuzzRelayImport") {
                val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
                account.settings.dismissChannelInvite(groupId.id)
                account.unfollow(channel)
            }
        }
    }

    fun addAll() {
        _channels.value.forEach(::add)
    }
}
