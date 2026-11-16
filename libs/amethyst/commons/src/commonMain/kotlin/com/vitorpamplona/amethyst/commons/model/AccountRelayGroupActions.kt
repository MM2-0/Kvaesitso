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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzHuddleLivenessState
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunPayload
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupDeletions
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupMembership
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.SubPurpose
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.taggedAs
import com.vitorpamplona.quartz.buzz.dm.DmAddMemberEvent
import com.vitorpamplona.quartz.buzz.dm.DmHideEvent
import com.vitorpamplona.quartz.buzz.dm.DmOpenEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleLivenessEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.presence.TypingIndicatorEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminAddMemberEvent
import com.vitorpamplona.quartz.buzz.relayAdmin.RelayAdminRemoveMemberEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalDenyEvent
import com.vitorpamplona.quartz.buzz.workflow.ApprovalGrantEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowDefEvent
import com.vitorpamplona.quartz.buzz.workflow.WorkflowTriggerEvent
import com.vitorpamplona.quartz.buzz.workflow.workflowChannel
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_ROLE_ADMIN
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_ROLE_BOT
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_ROLE_GUEST
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_ROLE_MEMBER
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_ROLE_OWNER
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_VISIBILITY_OPEN
import com.vitorpamplona.quartz.buzz.workspace.BUZZ_VISIBILITY_PRIVATE
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PublishResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndCollectResults
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.hTag
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.CreateGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.DeleteGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupCreateInviteEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupEditMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupPutUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupRemoveUserEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.GroupUpdatePinListEvent
import com.vitorpamplona.quartz.nip29RelayGroups.moderation.previous
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupJoinRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.request.GroupLeaveRequestEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.AddressPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupIdTag
import com.vitorpamplona.quartz.nip29RelayGroups.tags.GroupPin
import com.vitorpamplona.quartz.nip7DThreads.ThreadEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * NIP-29 relay-group and Buzz-workspace orchestration for an [Account]:
 * join/leave/create/delete/archive groups, threads, invites, pins, member and
 * role management, metadata edits, plus the Buzz dialect's DMs, jobs,
 * workflows, and typing signals. Event building lives in quartz builders;
 * this class wires them to the account's signer and the group's host relay.
 */
class AccountRelayGroupActions(
    private val account: Account,
) {
    // All group commands are published ONLY to the group's host relay, where
    // relay29 authorizes them. The relay is the source of truth; the kind-10009
    // list is our own cross-device bookkeeping of what we joined.

    /** Send a kind 9021 join request to the group's host relay and remember it. */
    suspend fun joinRelayGroup(
        channel: RelayGroupChannel,
        code: String? = null,
    ) {
        val template = GroupJoinRequestEvent.build(channel.groupId.id, inviteCode = code)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        account.follow(channel)
    }

    // "relay|profile content" pairs already pushed this session, so each profile version goes to each
    // workspace once. Keyed on content, not event id: the copy we sign comes back from the relay as a
    // newer kind-0 with the same content, and must not trigger another signature.
    private val profileSharedWith = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Push my current kind-0 profile to the Buzz workspace at [relay]. Buzz reads member names and
     * pictures only from its own relay, and its people search (mentions, Add people, new DM) is by
     * name — without this, Buzz showed me as a bare npub and nobody could find or add me.
     *
     * Buzz refuses an event whose timestamp is far from its clock ("event timestamp too far from
     * server time"), so the already-signed profile — usually days old — can't be relayed as-is. Like
     * Buzz's own client, this signs a fresh copy with the same content and tags, and sends it ONLY to
     * that workspace, once per profile version.
     */
    suspend fun shareProfileWithBuzzWorkspace(relay: NormalizedRelayUrl) {
        if (!account.isWriteable()) return
        val profile = account.userMetadata.getUserMetadataEvent() ?: return
        val key = "${relay.url}|${profile.content.hashCode()}|${profile.tags.contentDeepHashCode()}"
        while (true) {
            val current = profileSharedWith.value
            if (key in current) return
            if (profileSharedWith.compareAndSet(current, current + key)) break
        }
        try {
            val fresh =
                if (TimeUtils.now() - profile.createdAt < BUZZ_FRESH_PROFILE_SECS) {
                    profile
                } else {
                    account.signer.sign<MetadataEvent>(TimeUtils.now(), MetadataEvent.KIND, profile.tags, profile.content)
                }
            account.client.publish(fresh, setOf(relay))
        } catch (e: Throwable) {
            // Not shared (signer refused, timed out, …): forget the attempt so a later visit retries.
            profileSharedWith.update { it - key }
            throw e
        }
    }

    /**
     * Fire a Buzz kind-20002 typing heartbeat for [channel] to its host relay. Ephemeral
     * (never stored) and fire-and-forget — no delivery tracking, no local echo (we filter
     * our own typing in the UI). Throttled by the composer to [BuzzTypingState.TYPING_HEARTBEAT_SECS].
     * [threadRootId]/[replyToId] scope it to a thread, as Buzz's thread composer does.
     */
    suspend fun sendBuzzTyping(
        channel: RelayGroupChannel,
        threadRootId: HexKey? = null,
        replyToId: HexKey? = null,
    ) {
        if (!account.isWriteable()) return
        // In a thread, the `e` markers scope the signal to it: Buzz shows it in that thread's pane.
        val signed = account.signer.sign(TypingIndicatorEvent.build(channel.groupId.id, threadRootId, replyToId))
        account.client.publish(signed, setOf(channel.groupId.relayUrl))
    }

    /**
     * Open (or re-surface) a Buzz DM with [participants] on [relay] via a kind-41010
     * command. [participants] are the OTHER 1-8 people — the relay adds me, derives the
     * canonical channel UUID, and confirms with a relay-signed [DmCreatedEvent]
     * (kind-41001) that lands in [com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmRegistry].
     * We never assign the channel id ourselves, so callers discover the materialized DM
     * by watching that registry rather than from this call's return.
     */
    suspend fun openBuzzDm(
        relay: NormalizedRelayUrl,
        participants: List<HexKey>,
    ): String? {
        val signed = account.signer.sign(DmOpenEvent.build(participants))
        // The relay confirms the DM synchronously in the OK as `response:{"channel_id":"…"}` —
        // the authoritative, relay-assigned channel UUID (the deployed relay does not emit a
        // queryable kind-41001). Read it straight from the ack so the caller can open the chat.
        var results = account.client.publishAndCollectResults(signed, setOf(relay))
        var channelId = buzzDmChannelIdFromAck(results)

        // NIP-42 write race: on a cold connection the relay rejects the first publish with
        // `auth-required` (our AUTH reply lands async and the write path doesn't re-send). Warm
        // the connection with a pendingOnAuthRequired read so the auth coordinator completes the
        // handshake, then retry the publish on the now-authed socket. Mirrors the amy CLI fix.
        if (channelId == null && results.values.any { !it.accepted && it.message.contains("auth-required", ignoreCase = true) }) {
            account.client.taggedAs(SubPurpose.RELAY_GROUPS).fetchAllWithHooks(
                filters = mapOf(relay to listOf(Filter(kinds = listOf(DmOpenEvent.KIND), limit = 1))),
                idleTimeoutMs = 8_000,
                pendingOnAuthRequired = true,
            ) { _, _ -> false }
            results = account.client.publishAndCollectResults(signed, setOf(relay))
            channelId = buzzDmChannelIdFromAck(results)
        }
        return channelId
    }

    /** The relay-assigned DM channel id from a DM-open OK message (`response:{"channel_id":"…"}`). */
    private fun buzzDmChannelIdFromAck(results: Map<NormalizedRelayUrl, PublishResult>): String? =
        results.values
            .firstOrNull { it.accepted }
            ?.message
            ?.substringAfter("\"channel_id\":\"", "")
            ?.substringBefore('"')
            ?.takeIf { it.isNotBlank() }

    /**
     * Asks [channel]'s Buzz relay which huddle sessions in it are live right now and records them
     * in [BuzzHuddleLivenessState]. The relay synthesizes kind-48104 answers only for a REQ whose
     * every filter is exactly `kinds:[48104]` with an authorized `#h`, so this is its own REQ, never
     * part of the channel's timeline subscription. Buzz's desktop repeats it every 10 seconds.
     */
    suspend fun refreshBuzzHuddleLiveness(channel: RelayGroupChannel) {
        val filter = HuddleLivenessEvent.filter(listOf(channel.groupId.id))
        val now = TimeUtils.now()
        account.client.taggedAs(SubPurpose.RELAY_GROUPS, "Huddle liveness").fetchAll(channel.groupId.relayUrl, filter, idleTimeoutMs = 8_000).forEach { event ->
            if (event !is HuddleLivenessEvent || event.channelId() != channel.groupId.id) return@forEach
            event.sessionId()?.let { BuzzHuddleLivenessState.record(channel.groupId.id, it, now) }
        }
    }

    /** Hide a Buzz DM from my sidebar with a kind-41012 command (re-opening it un-hides). */
    suspend fun hideBuzzDm(channel: RelayGroupChannel) {
        val template = DmHideEvent.build(channel.groupId.id)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /** Add [member] to an existing group DM with a kind-41011 command (creates a new DM set). */
    suspend fun addBuzzDmMember(
        channel: RelayGroupChannel,
        member: HexKey,
    ) {
        val template = DmAddMemberEvent.build(channel.groupId.id, member)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * File a Buzz agent job (kind-43001) into channel [channelId] on [relay] — a shared
     * feature-request the workspace bot can pick up. Untargeted: any agent watching the
     * channel may accept it. Returns the new job id (the request event id), or null when the
     * account can't write. See [com.vitorpamplona.amethyst.commons.model.buzz.BuzzJobAggregator].
     */
    suspend fun fileBuzzJob(
        relay: NormalizedRelayUrl,
        channelId: String,
        request: String,
    ): HexKey? {
        if (!account.isWriteable()) return null
        val signed = account.signer.sign(JobRequestEvent.build(request, channelId, null))
        // Reflect it locally so the board updates immediately (publish only sends to relays).
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /** Cancel a Buzz job [jobId] with a kind-43005 scoped to [channelId] on [relay]. */
    suspend fun cancelBuzzJob(
        relay: NormalizedRelayUrl,
        channelId: String,
        jobId: HexKey,
    ) {
        if (!account.isWriteable()) return
        val signed = account.signer.sign(JobCancelEvent.build(jobId, "", channelId))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
    }

    /**
     * Trigger a Buzz **workflow** run (kind-46020) for [workflowId] into channel [channelId] on
     * [relay], carrying [task] as the run's request. The trigger's event id IS the run id (and the
     * approval token), returned here. A run pauses on a human-approval gate before anything ships —
     * see [com.vitorpamplona.amethyst.commons.model.buzz.WorkflowRunAggregator].
     */
    suspend fun triggerBuzzWorkflow(
        relay: NormalizedRelayUrl,
        channelId: String,
        workflowId: String,
        task: String,
    ): HexKey? {
        if (!account.isWriteable()) return null
        val content = Json.encodeToString(WorkflowRunPayload(task = task, workflow = workflowId))
        val signed = account.signer.sign(WorkflowTriggerEvent.build(workflowId, content) { workflowChannel(channelId) })
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /**
     * Publish a Buzz **workflow definition** (kind-30620) into channel [channelId] on [relay]: an
     * addressable event whose `d` tag is a freshly-minted workflow UUID (returned here), carrying a
     * human-readable [name] and the workflow's [yaml] recipe. On a real Buzz relay the relay parses
     * the YAML and runs it; self-hosted on geode the definition is a named catalog entry the picker
     * offers and `amy` triggers by id. Returns the new workflow id, or null when the account can't write.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun publishBuzzWorkflowDef(
        relay: NormalizedRelayUrl,
        channelId: String,
        name: String,
        yaml: String,
    ): String? {
        if (!account.isWriteable()) return null
        // The relay parses the `d` tag as a UUID and refuses anything else.
        val workflowId = Uuid.random().toString()
        val signed = account.signer.sign(WorkflowDefEvent.build(workflowId, channelId, yaml, name.ifBlank { null }))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return workflowId
    }

    /**
     * Grant a paused Buzz workflow run's approval gate (kind-46030). [runId] is the run id, which
     * doubles as the approval token (the grant's `d` tag). Resuming lets the runner ship the work.
     * Publishing to the single group [relay]; the runner discovers the decision by author.
     */
    suspend fun approveBuzzWorkflowRun(
        relay: NormalizedRelayUrl,
        runId: HexKey,
        note: String = "",
    ): HexKey? {
        if (!account.isWriteable()) return null
        val signed = account.signer.sign(ApprovalGrantEvent.build(runId, note))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /** Deny a paused Buzz workflow run's approval gate (kind-46031); the run is terminal (DENIED). */
    suspend fun denyBuzzWorkflowRun(
        relay: NormalizedRelayUrl,
        runId: HexKey,
        note: String = "",
    ): HexKey? {
        if (!account.isWriteable()) return null
        val signed = account.signer.sign(ApprovalDenyEvent.build(runId, note))
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
        return signed.id
    }

    /**
     * Upvote a Buzz job [jobId] (authored by [jobAuthor]) — a NIP-25 like (kind-7 `+`) `e`-tagging
     * the request, `p`-tagging its author and `k`-tagging the reacted kind per NIP-25, and
     * `h`-scoped to [channelId] so the scheduler (and the board) count it toward priority.
     */
    suspend fun upvoteBuzzJob(
        relay: NormalizedRelayUrl,
        channelId: String,
        jobId: HexKey,
        jobAuthor: HexKey?,
    ) {
        if (!account.isWriteable()) return
        val template =
            eventTemplate<ReactionEvent>(ReactionEvent.KIND, ReactionEvent.LIKE) {
                addUnique(ETag.assemble(jobId, null, null))
                jobAuthor?.let { addUnique(PTag.assemble(it, null)) }
                addUnique(arrayOf("k", JobRequestEvent.KIND.toString()))
                addUnique(GroupIdTag.assemble(channelId))
            }
        val signed = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(signed)
        account.client.publish(signed, setOf(relay))
    }

    /** Send a kind 9022 leave request to the host relay and drop it from our list. */
    suspend fun leaveRelayGroup(channel: RelayGroupChannel) {
        val template = GroupLeaveRequestEvent.build(channel.groupId.id)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        account.unfollow(channel)
    }

    /**
     * Delete the whole group with a kind 9008 delete-group event (owner/admin only — the relay
     * enforces this). Unlike [leaveRelayGroup], this destroys the channel for everyone rather than
     * just removing me; the relay drops the group and its messages. Also drops it from our own list
     * so it disappears from Messages immediately instead of lingering as a now-dead id.
     */
    suspend fun deleteRelayGroup(channel: RelayGroupChannel) {
        val template = DeleteGroupEvent.build(channel.groupId.id)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
        account.unfollow(channel)
        // Remember the deletion so the channel leaves the community's browse list immediately and
        // stays gone across a restart — the relay drops the group but our cached 39000 metadata (and a
        // stale re-announced 44100 on a Buzz relay) would otherwise keep it visible.
        RelayGroupDeletions.markDeleted(channel.groupId)
    }

    /**
     * Create a new group on [relay]: kind 9007 (create-group) then kind 9002
     * (edit-metadata) with the chosen name/visibility, then remember it. Returns
     * the new group's id.
     */
    suspend fun createRelayGroup(
        relay: NormalizedRelayUrl,
        groupId: String,
        name: String,
        about: String? = null,
        picture: String? = null,
        isPrivate: Boolean = false,
        isClosed: Boolean = false,
        isHidden: Boolean = false,
        isRestricted: Boolean = false,
        hashtags: List<String> = emptyList(),
        geohashes: List<String> = emptyList(),
        parent: String? = null,
        channelType: String? = null,
        banner: String? = null,
    ): GroupId {
        // The metadata rides the create event as well as the 9002 below. A plain NIP-29 relay takes
        // its metadata from the 9002 and ignores these tags; Buzz rejects the 9007 outright without
        // a `name` (see CreateGroupEvent.build), which used to make "create group" on a Buzz relay
        // publish two events and produce nothing at all.
        account.broadcaster.signAndSendPrivatelyOrBroadcast(
            CreateGroupEvent.build(
                groupId = groupId,
                name = name,
                about = about,
                visibility = if (isPrivate) BUZZ_VISIBILITY_PRIVATE else BUZZ_VISIBILITY_OPEN,
                channelType = channelType,
            ),
        ) { listOf(relay) }

        val edit =
            GroupEditMetadataEvent.build(
                groupId,
                name = name,
                about = about,
                picture = picture,
                banner = banner,
                status = relayGroupStatus(isPrivate, isClosed, isHidden, isRestricted),
                hashtags = hashtags,
                geohashes = geohashes,
                parent = parent,
            )
        account.broadcaster.signAndSendPrivatelyOrBroadcast(edit) { listOf(relay) }

        val id = GroupId(groupId, relay)
        account.follow(LocalCache.getOrCreateRelayGroupChannel(id))
        return id
    }

    /**
     * The set of NIP-29 status flags to emit on a kind-9002 metadata event. Flags are
     * presence-only — public/open/visible/unrestricted are simply the ABSENCE of their
     * restrictive counterpart — so only the enabled restrictive flags are added.
     */
    private fun relayGroupStatus(
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
    ): Set<GroupMetadataEvent.GroupStatus> =
        buildSet {
            if (isPrivate) add(GroupMetadataEvent.GroupStatus.PRIVATE)
            if (isClosed) add(GroupMetadataEvent.GroupStatus.CLOSED)
            if (isHidden) add(GroupMetadataEvent.GroupStatus.HIDDEN)
            if (isRestricted) add(GroupMetadataEvent.GroupStatus.RESTRICTED)
        }

    /** Post a kind 11 thread (forum-style) to the group, scoped by its `h` tag. */
    suspend fun postRelayGroupThread(
        channel: RelayGroupChannel,
        title: String,
        body: String,
    ) {
        val template =
            ThreadEvent.build(body, title) {
                hTag(channel.groupId.id)
                previous(channel.previousEventRefs(account.pubKey))
            }
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /** Mint a kind 9009 invite code for the group (admin/moderator only). */
    suspend fun createRelayGroupInvite(
        channel: RelayGroupChannel,
        code: String,
    ) {
        val template = GroupCreateInviteEvent.build(channel.groupId.id, code)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Replace the group's pin list with a kind 9010 update-pin-list event (admin/moderator
     * only). NIP-29 carries the FULL ordered list — `e` pins and `a` pins — so the relay applies
     * it and republishes the kind-39005 [com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent].
     */
    suspend fun updateRelayGroupPins(
        channel: RelayGroupChannel,
        pins: List<GroupPin>,
    ) {
        val template = GroupUpdatePinListEvent.build(channel.groupId.id, pins)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Pin [note] by appending it to the current list (no-op if already pinned). An addressable
     * note is pinned by its address (`a`), so the pin follows its latest version; anything else
     * by event id (`e`). Every existing pin — including other clients' `a` pins — is kept.
     */
    suspend fun pinRelayGroupMessage(
        channel: RelayGroupChannel,
        note: Note,
    ) {
        val pin = pinFor(note) ?: return
        if (channel.isPinned(pin.ref)) return
        updateRelayGroupPins(channel, channel.pinsWith(pin))
    }

    /** Unpin [note] (by address or id) from the current list, keeping every other pin intact. */
    suspend fun unpinRelayGroupMessage(
        channel: RelayGroupChannel,
        note: Note,
    ) {
        val refs = pinRefsFor(note)
        if (refs.none { channel.isPinned(it) }) return
        updateRelayGroupPins(channel, channel.pins.filter { it.ref !in refs })
    }

    private fun addressOf(note: Note): Address? = note.address() ?: (note.event as? AddressableEvent)?.address()

    private fun pinFor(note: Note): GroupPin? {
        addressOf(note)?.let { return AddressPin(it) }
        return EventPin(note.event?.id ?: note.idHex)
    }

    /** Both references a note can be pinned under: its address (if addressable) and its event id. */
    private fun pinRefsFor(note: Note): Set<String> =
        buildSet {
            addressOf(note)?.let { add(it.toValue()) }
            note.event?.id?.let { add(it) }
            add(note.idHex)
        }

    /**
     * NIP-29 group migration: the same group id now lives on [newRelay] (moved or forked). Swap this
     * group's entry in our kind-10009 for the one on [newRelay] and return the new channel, which the
     * caller opens so its events load from the new relay.
     */
    suspend fun moveRelayGroup(
        channel: RelayGroupChannel,
        newRelay: NormalizedRelayUrl,
    ): RelayGroupChannel {
        val target = LocalCache.getOrCreateRelayGroupChannel(GroupId(channel.groupId.id, newRelay))
        account.sendMyPublicAndPrivateOutbox(account.relayGroupList.move(channel, target))
        return target
    }

    /** Kick [pubkey] out of the group with a kind 9001 remove-user event (moderator only). */
    suspend fun removeRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
    ) {
        val template = GroupRemoveUserEvent.build(channel.groupId.id, listOf(pubkey))
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Add [pubkey] to the group as a plain member with a kind 9000 put-user event, without
     * touching the role of someone who is already in it.
     *
     * On Buzz this sends **no** `role` tag: the relay reads a missing role as "no role change",
     * keeping an existing member's role and defaulting only a newcomer to `member`. Sending
     * `role=member` instead would demote an admin who happened to be re-added, or be refused
     * outright (`only owners/admins may change an active member's role`) when the actor is not
     * elevated. Ground truth: `handle_put_user` in `buzz-relay/src/handlers/side_effects.rs` and
     * `decide_put_user` in `channel_authz.rs`.
     */
    suspend fun addRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
    ) {
        val template = GroupPutUserEvent.build(channel.groupId.id, listOf(pubkey to emptyList()))
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Set [pubkey]'s roles in the group with a kind 9000 put-user event (moderator only). An
     * empty [roles] list makes them a plain member - on Buzz that is an explicit demotion to
     * `member`. To add someone without changing a role, use [addRelayGroupUser].
     */
    suspend fun putRelayGroupUser(
        channel: RelayGroupChannel,
        pubkey: HexKey,
        roles: List<String>,
    ) {
        // Buzz ignores the roles inside the `p` tag and reads a top-level `role` tag instead, in its
        // own vocabulary — so map ours onto its set before sending. Anything it cannot parse fails
        // the whole put-user, which is why an unmapped role must become `member` rather than travel.
        val buzzRole =
            if (BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)) {
                buzzRoleFor(roles)
            } else {
                null
            }
        val template = GroupPutUserEvent.build(channel.groupId.id, listOf(pubkey to roles), buzzRole = buzzRole)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Add [pubkey] to a Buzz **community** (the whole relay/tenant, not one channel) via the
     * relay-admin add-member command (kind 9030). Owner/admin only — the relay validates the
     * sender's role and, on a new insert, updates its NIP-43 membership list (13534). Published to
     * [relay] with no channel scope.
     */
    suspend fun addCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
        role: String? = null,
    ) {
        account.broadcaster.signAndSendPrivatelyOrBroadcast(RelayAdminAddMemberEvent.build(pubkey, role)) { listOf(relay) }
    }

    /** Remove [pubkey] from a Buzz community via the relay-admin remove-member command (kind 9031). */
    suspend fun removeCommunityMember(
        relay: NormalizedRelayUrl,
        pubkey: HexKey,
    ) {
        account.broadcaster.signAndSendPrivatelyOrBroadcast(RelayAdminRemoveMemberEvent.build(pubkey)) { listOf(relay) }
    }

    /**
     * Edit the group's relay-signed metadata with a kind 9002 event (admin only).
     *
     * NIP-29 §Subgroups makes the metadata edit a full replacement of the hierarchy
     * links: a 9002 with no `parent` tag re-roots the group, and one that drops any
     * existing `child` is rejected by the relay. So unless the caller is explicitly
     * re-parenting, we re-carry the group's current [parent] and full [children] list
     * from its latest known metadata to keep the tree intact across a plain name/flag
     * edit. Pass an explicit value to change them.
     *
     * A 9002 also carries "all the fields of group-metadata", so the current 39000's [banner]
     * (unless replaced) and every tag this form doesn't manage (`livekit`, `supported_kinds`, a
     * newer spec field…) ride along verbatim — otherwise a rename would erase them on a relay that
     * rebuilds the metadata from the edit.
     */
    suspend fun editRelayGroupMetadata(
        channel: RelayGroupChannel,
        name: String?,
        about: String?,
        picture: String?,
        isPrivate: Boolean,
        isClosed: Boolean,
        isHidden: Boolean,
        isRestricted: Boolean,
        hashtags: List<String> = emptyList(),
        geohashes: List<String> = emptyList(),
        parent: String? = channel.parentGroupId(),
        children: List<String> = channel.childGroupIds(),
        banner: String? = channel.bannerPicture(),
    ) {
        // On a Buzz relay, visibility rides a `visibility` ("open"/"private") tag — the relay does NOT
        // read NIP-29's `private` status flag — so a Buzz channel's visibility only actually changes on
        // edit when we send that tag. A plain NIP-29 relay ignores it and honours the status flag.
        val isBuzz = BuzzRelayDialect.isBuzz(channel.groupId.relayUrl)
        val template =
            GroupEditMetadataEvent.build(
                channel.groupId.id,
                name = name,
                about = about,
                picture = picture,
                banner = banner,
                status = relayGroupStatus(isPrivate, isClosed, isHidden, isRestricted),
                hashtags = hashtags,
                geohashes = geohashes,
                parent = parent,
                children = children,
                visibility = if (isBuzz) (if (isPrivate) BUZZ_VISIBILITY_PRIVATE else BUZZ_VISIBILITY_OPEN) else null,
                // Buzz honours only its own subset of tags on a 9002 and stamps relay-internal ones on
                // its 39000, so don't echo those back; a NIP-29 relay gets the unmanaged tags verbatim.
                extraTags = if (isBuzz) emptyList() else channel.event?.unmanagedTags() ?: emptyList(),
            )
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }

    /**
     * Archive or unarchive a Buzz channel (a minimal kind-9002 carrying only the `archived` tag). The
     * relay hides an archived channel from the sidebar and stamps the 39000, but keeps it and its
     * history — the reversible counterpart to [deleteRelayGroup]. Admin/owner only; the relay enforces.
     */
    suspend fun archiveRelayGroup(
        channel: RelayGroupChannel,
        archived: Boolean,
    ) {
        val template = GroupEditMetadataEvent.build(channel.groupId.id, archived = archived)
        account.broadcaster.signAndSendPrivatelyOrBroadcast(template) { channel.relays().toList() }
    }
}

/**
 * Maps NIP-29 role names onto Buzz's closed role set (owner/admin/member/guest/bot), picking the
 * most privileged one present. Anything else, including NIP-29's `moderator`, becomes `member`.
 */
internal fun buzzRoleFor(roles: List<String>): String {
    fun has(role: String) = roles.any { it.equals(role, true) }
    return when {
        has(BUZZ_ROLE_OWNER) -> BUZZ_ROLE_OWNER
        has(RelayGroupMembership.ROLE_ADMIN) || has(BUZZ_ROLE_ADMIN) -> BUZZ_ROLE_ADMIN
        has(BUZZ_ROLE_BOT) -> BUZZ_ROLE_BOT
        has(BUZZ_ROLE_GUEST) -> BUZZ_ROLE_GUEST
        else -> BUZZ_ROLE_MEMBER
    }
}

/** A profile signed this recently is within Buzz's timestamp window and can be relayed as-is. */
private const val BUZZ_FRESH_PROFILE_SECS = 60L
