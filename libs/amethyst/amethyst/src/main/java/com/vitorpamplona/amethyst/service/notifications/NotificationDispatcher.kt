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
package com.vitorpamplona.amethyst.service.notifications

import android.content.Context
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.service.notifications.renderers.BuzzDmNotification
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusDraftEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusOpenEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.zap.Bolt12ZapEvent
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Observes [LocalCache] for notification-relevant events and hands them to
 * [EventNotificationConsumer]. Events can reach [LocalCache] from any source —
 * FCM push, UnifiedPush, Pokey, active relay subscriptions, or the
 * [NotificationRelayService] — and the observer fires once per new insertion,
 * giving us a single dedup'd source of truth for notification triggers.
 *
 * The dispatcher itself is always on (any of the delivery paths can fire it).
 * Foreground suppression and per-event filtering are handled downstream in
 * [EventNotificationConsumer].
 */
class NotificationDispatcher(
    private val context: Context,
    private val scope: CoroutineScope,
    /** Forwarded to [EventNotificationConsumer]: reports wakelock held-time to the resource-usage ledger. */
    onWakeLockHeld: ((heldMs: Long) -> Unit)? = null,
) : MarmotGroupNotifier {
    companion object {
        private const val TAG = "NotificationDispatcher"

        // The dispatcher observes the *final* notification payload kinds.
        // GiftWrap/EphemeralGiftWrap (1059/21059) and Seal (13) are NOT
        // listed here — by the time we care, Account.newNotesPreProcessor has
        // already unwrapped them and inserted the inner payload into LocalCache,
        // which fires the observer a second time on the inner event.
        //
        // WelcomeEvent (kind:444) is also excluded: it has no `p` tag, so
        // consumeFromCache can't route it. It's delivered directly via
        // [notifyWelcome] from processMarmotWelcomeFlow, which does know the
        // recipient account.
        // `internal` (was `private`) so the notification-kinds contract test
        // can pin the push-side kind set against the in-app feed's kind set.
        // GitReplyEvent (kind 1622) is deprecated in favour of NIP-22 comments, but
        // events already on relays still arrive and still have to be routed.
        @Suppress("DEPRECATION")
        internal val NOTIFICATION_KINDS: Set<Int> =
            setOf(
                // Direct-arrival
                EncryptedDmEvent.KIND,
                ZapReceiptEvent.KIND,
                NutzapEvent.KIND,
                OnchainZapEvent.KIND,
                Bolt12ZapEvent.KIND,
                ReactionEvent.KIND,
                RepostEvent.KIND,
                GenericRepostEvent.KIND,
                BadgeAwardEvent.KIND,
                TextNoteEvent.KIND,
                CommentEvent.KIND,
                // Public content kinds — routed to their channel when p-tagged.
                PictureEvent.KIND,
                VideoNormalEvent.KIND,
                VideoShortEvent.KIND,
                AddressableNormalVideoEvent.KIND,
                AddressableShortVideoEvent.KIND,
                ChannelMessageEvent.KIND,
                PollEvent.KIND,
                GitPatchEvent.KIND,
                GitIssueEvent.KIND,
                GitPullRequestEvent.KIND,
                GitPullRequestUpdateEvent.KIND,
                // NIP-34 threaded activity: legacy git-reply comment (1622)
                // and the four status transitions (open/applied/closed/draft,
                // kinds 1630-1633). Same push channel as issues/patches/PRs.
                GitReplyEvent.KIND,
                GitStatusOpenEvent.KIND,
                GitStatusAppliedEvent.KIND,
                GitStatusClosedEvent.KIND,
                GitStatusDraftEvent.KIND,
                HighlightEvent.KIND,
                LongFormContentEvent.KIND,
                WikiArticleEvent.KIND,
                LiveChessGameAcceptEvent.KIND,
                LiveChessMoveEvent.KIND,
                WakeUpEvent.KIND,
                // Buzz DM messages in a `t=dm` relay-group channel (participant-
                // routed; gated to Buzz DMs only in the predicate so ordinary
                // kind-9 chats — Concord/NIP-C7 — don't leak into the tray).
                StreamMessageV2Event.KIND,
                ChatEvent.KIND,
                // Unwrapped from GiftWrap → Seal
                ChatMessageEvent.KIND,
                ChatMessageEncryptedFileHeaderEvent.KIND,
                // Unwrapped from EphemeralGiftWrap
                CallOfferEvent.KIND,
            )
    }

    private val consumer = EventNotificationConsumer(context, onWakeLockHeld)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        Log.d(TAG, "Starting notification dispatcher")

        // Only fire on events created after the dispatcher starts — equivalent
        // to the relay protocol's `limit: 0` subscription semantics, so we
        // don't retrigger on historical re-broadcasts of events the user has
        // already seen. Captured once and shared across filter rebuilds
        // triggered by account changes.
        val dispatcherSince = TimeUtils.now()

        job =
            scope.launch {
                // Ensure the saved-accounts StateFlow is primed from disk.
                // accountsFlow() exposes the backing MutableStateFlow which
                // starts as null and only populates on the first suspend read.
                LocalPreferences.allSavedAccounts()

                LocalPreferences
                    .accountsFlow()
                    .filterNotNull()
                    .map { accounts ->
                        accounts
                            .filter { it.hasPrivKey || it.loggedInWithExternalSigner }
                            .mapNotNullTo(mutableSetOf()) { npubToHexOrNull(it.npub) }
                    }.distinctUntilChanged()
                    .collectLatest { pubkeys ->
                        if (pubkeys.isEmpty()) {
                            Log.d(TAG) { "No notifiable accounts; observer idle." }
                            return@collectLatest
                        }

                        Log.d(TAG) { "Observing notifications for ${pubkeys.size} account(s)." }

                        // Single observer predicate. Each check is cheap and
                        // short-circuits so kind mismatch (by far the most
                        // common case) rejects before any allocation.
                        //
                        // - kind ∈ NOTIFICATION_KINDS    — channel-relevant types
                        // - createdAt ≥ dispatcherSince  — `limit: 0` semantics,
                        //   drops re-broadcasts from before this session
                        // - createdAt ≥ fifteenMinutesAgo — rolling freshness,
                        //   matches the downstream per-channel policy. Calls
                        //   use a stricter 20s check in notifyIncomingCall so
                        //   they still pass through.
                        // - WakeUpEvent bypasses recipient matching: its
                        //   [Event.notifies] is `true` unconditionally, meaning
                        //   "wake every signed-in account" (p-tags on a WakeUp
                        //   name referenced-event authors, not recipients).
                        // - Everything else uses isTaggedUser + tagsAnEventByUser
                        //   — same pair the in-app Notifications feed uses, so
                        //   push and feed agree on what counts as a mention,
                        //   reply, citation, fork, or community moderation.
                        //   Note: NIP-22 CommentEvents where the user is only
                        //   the root author (uppercase `P`) no longer match —
                        //   intentional alignment with the feed, which gates
                        //   on lowercase `p` only.
                        val predicate = predicate@{ event: Event ->
                            if (event.kind !in NOTIFICATION_KINDS) return@predicate false
                            if (event.createdAt < dispatcherSince) return@predicate false
                            if (event.createdAt < TimeUtils.fifteenMinutesAgo()) return@predicate false
                            if (event is WakeUpEvent) return@predicate true

                            // getNoteIfExists(event) — not (event.id) — so
                            // AddressableEvent kinds (LongFormContent, WikiArticle,
                            // LiveChess*, AddressableNormalVideo/AddressableShortVideo) resolve to
                            // their address-keyed replaceable note. The id-keyed
                            // version note has its replyTo moved away during
                            // insertion (LocalCache.consumeBaseReplaceable), so
                            // tagsAnEventByUser's replyTo check would otherwise
                            // always miss for replies into addressable posts.
                            val note = LocalCache.getNoteIfExists(event) ?: return@predicate false

                            // Buzz DM messages (kind 40002 / kind 9 in a `t=dm`
                            // relay-group channel) are participant-routed, not
                            // p-tagged. Gate them EXCLUSIVELY on Buzz-DM
                            // membership so ordinary kind-9 chats (Concord /
                            // NIP-C7) don't fall through the generic gate below.
                            if (event is StreamMessageV2Event || event is ChatEvent) {
                                return@predicate pubkeys.any { pubkey ->
                                    BuzzDmNotification.buzzDmChannelForMe(note, pubkey) != null
                                }
                            }

                            // Reactions/reposts are routed by tagsAnEventByUser
                            // (the reacted note's author == me), not by a `p`
                            // tag — so a Buzz-style bare `["e", id]` like, which
                            // carries no `p` tag, still notifies. tagsAnEventByUser
                            // below keeps it scoped to reactions on MY note.
                            val isReactionOrRepost =
                                event is ReactionEvent || event is RepostEvent || event is GenericRepostEvent
                            pubkeys.any { pubkey ->
                                // Public chat replies into my own messages often
                                // omit the `p` tag; relax the gate for them (the
                                // tagsAnEventByUser check still scopes it to
                                // messages actually replying to me).
                                (
                                    event.isTaggedUser(pubkey) ||
                                        isReactionOrRepost ||
                                        NotificationFeedFilter.isNotifiablePublicChatReply(note, pubkey)
                                ) &&
                                    NotificationFeedFilter.tagsAnEventByUser(note, pubkey)
                            }
                        }

                        LocalCache
                            .observeNewEvents<Event>(predicate)
                            .collect { event ->
                                try {
                                    consumer.consumeFromCache(event)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(TAG, "Failed to dispatch notification for ${event.kind} ${event.id}", e)
                                }
                            }
                    }
            }
    }

    private fun npubToHexOrNull(npub: String): String? =
        runCatching { npub.bechToBytes("npub").toHexKey() }
            .onFailure { Log.d(TAG) { "Skipping non-decodable npub $npub: ${it.message}" } }
            .getOrNull()

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Direct-invocation entry point for [WelcomeEvent]. Bypasses the
     * cache-observer path because Welcomes have no `p` tag for account
     * routing. Called from processMarmotWelcomeFlow once MLS group join
     * succeeds — at which point we know which account the invite was for.
     */
    override suspend fun notifyWelcome(
        event: WelcomeEvent,
        account: Account,
    ) {
        try {
            consumer.notifyWelcome(event, account)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to dispatch Welcome notification ${event.id}", e)
        }
    }

    /**
     * Direct-invocation entry point for Marmot kind:445 group messages.
     * Bypasses the cache-observer path because GroupEvents are routed by
     * the `h` tag (nostr_group_id), not by `p` tag. Called from
     * [com.vitorpamplona.amethyst.commons.model.GroupEventHandler]
     * once the MLS-decrypted inner event has been parsed and indexed.
     */
    override suspend fun notifyGroupMessage(
        innerEvent: ChatEvent,
        nostrGroupId: String,
        account: Account,
    ) {
        try {
            consumer.notifyGroupMessage(innerEvent, nostrGroupId, account)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to dispatch Group Message notification ${innerEvent.id}", e)
        }
    }
}
