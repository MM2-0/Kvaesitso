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
import android.graphics.drawable.BitmapDrawable
import android.os.PowerManager
import android.os.SystemClock
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.isMutedPublicChatMessage
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallManager
import com.vitorpamplona.amethyst.commons.notifications.NotificationDraft
import com.vitorpamplona.amethyst.commons.notifications.composers.ChessNotificationComposer
import com.vitorpamplona.amethyst.commons.notifications.composers.MentionNotificationComposer
import com.vitorpamplona.amethyst.commons.notifications.composers.StandardNotificationComposer
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.commons.relayClient.auth.ScreenAuthAccount
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderQueryState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_chess_challenge_accepted
import com.vitorpamplona.amethyst.commons.resources.app_notification_chess_your_turn
import com.vitorpamplona.amethyst.commons.wot.curatedHidesByTrust
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.service.call.notification.CallNotifier
import com.vitorpamplona.amethyst.service.notifications.renderers.BuzzDmNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.DirectMessageNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.GroupMessageNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.ReplyNotification
import com.vitorpamplona.amethyst.service.notifications.renderers.StandardNotification
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.experimental.notifications.wake.WakeUpEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import com.vitorpamplona.quartz.nip64Chess.challenge.accept.LiveChessGameAcceptEvent
import com.vitorpamplona.quartz.nip64Chess.move.LiveChessMoveEvent
import com.vitorpamplona.quartz.nipACWebRtcCalls.events.CallOfferEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "EventNotificationConsumer"

/**
 * Turns a notification-relevant [Event] into a tray notification. Owns only the
 * *policy* — account matching, the shared suppress-when-foreground / don't-notify-
 * myself / muted-thread gates, and the routing of each kind to its renderer.
 * The *presentation* (title, body, style, accent, observability) lives in the
 * per-kind files under `renderers/`.
 */
class EventNotificationConsumer(
    private val applicationContext: Context,
    /** Reports how long each notification-processing wakelock was held (resource-usage ledger). */
    private val onWakeLockHeld: ((heldMs: Long) -> Unit)? = null,
) {
    companion object {
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
        private const val WAKEUP_WINDOW_MS = 30_000L
    }

    /**
     * Acquires a partial WakeLock during notification processing to ensure
     * the CPU stays awake long enough to decrypt, process, and dispatch
     * the notification, even in Doze mode.
     */
    private inline fun <T> withWakeLock(block: () -> T): T {
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "amethyst:notification_processing",
            )
        val heldSince = SystemClock.elapsedRealtime()
        wakeLock.acquire(WAKELOCK_TIMEOUT_MS)
        try {
            return block()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            onWakeLockHeld?.invoke(SystemClock.elapsedRealtime() - heldSince)
        }
    }

    /**
     * Entry point for notification-relevant events arriving into [LocalCache]
     * from any source (FCM push, UnifiedPush, Pokey, active relay subscriptions,
     * NotificationRelayService). Matches the event to a logged-in account by its
     * `p` tags and dispatches to [dispatchForAccount].
     */
    suspend fun consumeFromCache(event: Event) =
        withWakeLock {
            Log.d(TAG) { "New Notification from cache: kind=${event.kind} id=${event.id}" }

            if (!applicationContext.notificationManager().areNotificationsEnabled()) return@withWakeLock

            val matchingNote: Note? =
                if (event is WakeUpEvent) {
                    null
                } else {
                    LocalCache.getNoteIfExists(event) ?: return@withWakeLock
                }

            LocalPreferences.allSavedAccounts().forEach { savedAccount ->
                if (!savedAccount.hasPrivKey && !savedAccount.loggedInWithExternalSigner) return@forEach

                val accountHex = npubToHexOrNull(savedAccount.npub) ?: return@forEach
                if (matchingNote != null) {
                    val taggedOrPublicChatReply =
                        event.isTaggedUser(accountHex) ||
                            NotificationFeedFilter.isNotifiablePublicChatReply(matchingNote, accountHex)
                    if (!taggedOrPublicChatReply) return@forEach
                    if (!NotificationFeedFilter.tagsAnEventByUser(matchingNote, accountHex)) return@forEach
                }

                val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(savedAccount.npub) ?: return@forEach
                try {
                    val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)
                    dispatchForAccount(event, account)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG) { "Failed to dispatch ${event.kind} ${event.id} for ${savedAccount.npub}: ${e.message}" }
                }
            }
        }

    private fun npubToHexOrNull(npub: String): String? =
        runCatching { npub.bechToBytes("npub").toHexKey() }
            .onFailure { Log.d(TAG) { "Skipping non-decodable npub $npub: ${it.message}" } }
            .getOrNull()

    // GitReplyEvent (kind 1622) is deprecated in favour of NIP-22 comments, but
    // events already on relays still arrive and still have to be routed.
    @Suppress("DEPRECATION")
    private suspend fun dispatchForAccount(
        event: Event,
        account: Account,
    ) {
        // Calls and wake-ups are high-priority and always notify, even when MainActivity is visible.
        when (event) {
            is CallOfferEvent -> {
                notifyIncomingCall(event, account)
                return
            }

            is WakeUpEvent -> {
                wakeUpFor(event, account)
                return
            }
        }

        // Everything else is suppressed while the user is actively on the home screen.
        if (MainActivity.isResumed) return

        // Don't push-notify events this account authored.
        if (event.pubKey == account.signer.pubKey) return

        // A DM protocol turned off in Settings › Messages doesn't notify.
        if (event is ChatroomKeyable && !account.chatFeedToggles.isEnabled(event)) return

        // Drop reactions/zaps/reposts whose target note lives on a muted thread, or in a
        // public chat the user has silenced (matches the in-app feed, which mutes all four).
        // Without the second check, muting a channel still let a like on your own message
        // there notify you — the row's glyph promises silence, so it has to mean it.
        if (event is ReactionEvent || event is ZapReceiptEvent || event is RepostEvent || event is GenericRepostEvent) {
            val target = LocalCache.getNoteIfExists(event)?.replyTo?.lastOrNull()
            if (target != null &&
                (
                    account.isThreadMuted(account.resolveThreadRoot(target)) ||
                        isMutedPublicChatMessage(target.event, account.settings.mutedPublicChats.value)
                )
            ) {
                return
            }
        }

        // Curated notifications apply the Web of Trust to push as well, with the in-app feed's
        // exemptions (curatedHidesByTrust). A process just woken by this push may not have read
        // the network index yet, so wait for it (tens of ms) before deciding.
        if (account.settings.defaultNotificationFollowList.value is TopFilter.Selected) {
            account.trustNetwork.awaitLoaded()
            val inJoinedCommunity = LocalCache.getNoteIfExists(event)?.let { account.isConcordActivity(it) } == true
            if (account.curatedHidesByTrust(event, inJoinedCommunity = inJoinedCommunity)) return
        }

        when (event) {
            is EncryptedDmEvent -> DirectMessageNotification.notify(applicationContext, account, event)
            is ChatMessageEvent -> DirectMessageNotification.notify(applicationContext, account, event)
            is ChatMessageEncryptedFileHeaderEvent -> DirectMessageNotification.notify(applicationContext, account, event)

            // Buzz DM messages (participant-routed; the predicate already scoped
            // these kinds to Buzz DMs addressed to me).
            is StreamMessageV2Event -> BuzzDmNotification.notify(applicationContext, account, event)
            is ChatEvent -> BuzzDmNotification.notify(applicationContext, account, event)

            is TextNoteEvent -> notifyTextNote(event, account)
            is CommentEvent -> notifyComment(event, account)
            is ChannelMessageEvent -> notifyChannelMessage(event, account)

            // NIP-64 chess is a debug-only feature for now; don't notify in release.
            is LiveChessGameAcceptEvent -> if (isDebug) post(account, ChessNotificationComposer.compose(account, event, Res.string.app_notification_chess_challenge_accepted))
            is LiveChessMoveEvent -> if (isDebug) post(account, ChessNotificationComposer.compose(account, event, Res.string.app_notification_chess_your_turn))

            // Zaps, reactions, reposts, badges, media, polls, articles and git.
            else -> post(account, StandardNotificationComposer.compose(account, event))
        }
    }

    private fun post(
        account: Account,
        draft: NotificationDraft?,
    ) = StandardNotification.post(applicationContext, account, draft)

    // Reply-vs-mention decisions are source-kind-specific, so they stay in the
    // dispatcher; the actual rendering is in ReplyNotification / MentionNotificationComposer.

    private suspend fun notifyTextNote(
        event: TextNoteEvent,
        account: Account,
    ) {
        val replyTargetId = event.replyingTo()
        if (replyTargetId != null) {
            val repliedNote = LocalCache.getNoteIfExists(replyTargetId)
            if (repliedNote?.author?.pubkeyHex == account.signer.pubKey) {
                val threadRoot = event.markedRoot()?.eventId ?: event.unmarkedRoot()?.eventId ?: replyTargetId
                ReplyNotification.notify(applicationContext, account, event, repliedNote, threadRoot)
                return
            }
        }
        post(account, MentionNotificationComposer.compose(account, event))
    }

    private suspend fun notifyComment(
        event: CommentEvent,
        account: Account,
    ) {
        val pubKey = account.signer.pubKey
        val isTarget = event.replyAuthorKeys().contains(pubKey) || event.rootAuthorKeys().contains(pubKey)
        if (!isTarget) return

        val parentNote = event.replyingTo()?.let { LocalCache.getNoteIfExists(it) }
        val threadRoot =
            event.rootEventIds().firstOrNull()
                ?: event.rootAddressIds().firstOrNull()
                ?: event.replyingToAddressOrEvent()
                ?: event.id

        ReplyNotification.notify(applicationContext, account, event, parentNote, threadRoot)
    }

    private suspend fun notifyChannelMessage(
        event: ChannelMessageEvent,
        account: Account,
    ) {
        // Reads local device state, NOT the NIP-78 blob: on a push-driven cold start
        // AppSpecificState may not have decrypted yet (and for a NIP-55 account that is
        // an Amber IPC round-trip that can fail outright in the background). Losing this
        // race would post exactly the notification the mute exists to prevent.
        if (isMutedPublicChatMessage(event, account.settings.mutedPublicChats.value)) return

        val note = LocalCache.getNoteIfExists(event.id) ?: return

        if (NotificationFeedFilter.isNotifiablePublicChatReply(note, account.signer.pubKey)) {
            val parentNote = note.replyTo?.lastOrNull()
            val threadRoot = event.channelId() ?: event.id
            ReplyNotification.notify(applicationContext, account, event, parentNote, threadRoot)
        } else {
            post(account, MentionNotificationComposer.compose(account, event))
        }
    }

    // ---------------------------------------------------------------------
    // Directly-dispatched Marmot events (no `p` tag → routed by the caller)
    // ---------------------------------------------------------------------

    suspend fun notifyWelcome(
        event: WelcomeEvent,
        account: Account,
    ) = withWakeLock {
        GroupMessageNotification.notifyWelcome(applicationContext, account, event)
    }

    suspend fun notifyGroupMessage(
        innerEvent: ChatEvent,
        nostrGroupId: String,
        account: Account,
    ) = withWakeLock {
        GroupMessageNotification.notifyGroupMessage(applicationContext, account, innerEvent, nostrGroupId)
    }

    // ---------------------------------------------------------------------
    // Calls & wake-ups keep their bespoke, high-priority handling here.
    // ---------------------------------------------------------------------

    private suspend fun wakeUpFor(
        event: WakeUpEvent,
        account: Account,
    ) {
        val referencedTags = event.events().distinctBy { it.eventId }
        if (referencedTags.isEmpty()) {
            Log.d(TAG) { "WakeUp ${event.id} has no referenced events — skipping" }
            return
        }

        val referencedNotes = referencedTags.map { LocalCache.getOrCreateNote(it.eventId) }
        val authorCandidates =
            (event.authorKeys() + referencedTags.mapNotNull { it.author })
                .distinct()
                .ifEmpty { listOf(event.pubKey) }
                .map { LocalCache.getOrCreateUser(it) }

        coroutineScope {
            launch {
                try {
                    withTimeout(WAKEUP_WINDOW_MS) {
                        Amethyst.instance.relayProxyClientConnector.relayServices
                            .collect()
                    }
                } catch (_: TimeoutCancellationException) {
                    Log.d(TAG) { "WakeUp ${event.id} — ${WAKEUP_WINDOW_MS}ms relay window elapsed" }
                }
            }

            launch {
                val accountState = ScreenAuthAccount(account)
                val eventStates = referencedNotes.map { EventFinderQueryState(it, account) }
                val authorStates = authorCandidates.map { UserFinderQueryState(it, account) }

                try {
                    Amethyst.instance.authCoordinator.subscribe(accountState)
                    eventStates.forEach {
                        Amethyst.instance.sources.eventFinder
                            .subscribe(it)
                    }
                    authorStates.forEach {
                        Amethyst.instance.sources.userFinder
                            .subscribe(it)
                    }
                    delay(WAKEUP_WINDOW_MS)
                } finally {
                    Amethyst.instance.authCoordinator.unsubscribe(accountState)
                    eventStates.forEach {
                        Amethyst.instance.sources.eventFinder
                            .unsubscribe(it)
                    }
                    authorStates.forEach {
                        Amethyst.instance.sources.userFinder
                            .unsubscribe(it)
                    }
                }
            }
        }
    }

    private suspend fun notifyIncomingCall(
        event: CallOfferEvent,
        account: Account,
    ) {
        // Calls turned off in Settings (or with NIP-17, which carries them) never ring.
        if (!account.chatFeedToggles.isCallingActive()) return
        if (!account.isFollowing(event.pubKey)) return
        if (TimeUtils.now() - event.createdAt > CallManager.MAX_EVENT_AGE_SECONDS) return

        val callerUser = LocalCache.getOrCreateUser(event.pubKey)

        if (callerUser.metadataOrNull()?.bestName() == null) {
            val authorState = UserFinderQueryState(callerUser, account)
            try {
                Amethyst.instance.sources.userFinder
                    .subscribe(authorState)
                withTimeoutOrNull(5_000L) {
                    callerUser
                        .metadata()
                        .flow
                        .first { it?.info?.bestName() != null }
                }
            } finally {
                Amethyst.instance.sources.userFinder
                    .unsubscribe(authorState)
            }
        }

        val callerName = callerUser.toBestDisplayName()

        val callerBitmap =
            callerUser.profilePicture()?.let { pictureUrl ->
                withContext(Dispatchers.IO) {
                    try {
                        val request =
                            ImageRequest
                                .Builder(applicationContext)
                                .data(pictureUrl)
                                .allowHardware(false)
                                .build()
                        val result = ImageLoader(applicationContext).execute(request)
                        (result.image?.asDrawable(applicationContext.resources) as? BitmapDrawable)?.bitmap
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        null
                    }
                }
            }

        CallNotifier.send(
            callerName = callerName,
            callerBitmap = callerBitmap,
            applicationContext = applicationContext,
        )
    }
}
