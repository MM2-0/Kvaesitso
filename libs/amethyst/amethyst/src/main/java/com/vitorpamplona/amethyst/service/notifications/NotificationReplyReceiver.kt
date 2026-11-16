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

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.composer.NewMessageTagger
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.cancelAndPrune
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils.cancelChildlessGroupSummaries
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip22Comments.notify
import com.vitorpamplona.quartz.nip89AppHandlers.clientTag.isClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class NotificationReplyReceiver : BroadcastReceiver() {
    companion object {
        /**
         * How long a send may run before the notification stops claiming it is sending. Set
         * under the ten seconds a foreground broadcast gets, so the honest state is rendered
         * while this process is still alive to render it.
         */
        private const val UNCONFIRMED_AFTER_MS = 8_000L
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val notificationId = intent.getIntExtra(NotificationUtils.KEY_NOTIFICATION_ID, 0)

        // Whatever the action, the user is done with this notification, so record it before
        // doing anything else. An enrichment window may still be open on the event (up to
        // 25s from the first post), and it re-posts the notification every time metadata
        // lands — without this the notification the user just dealt with comes back, and the
        // enricher keeps a relay subscription and a wakelock alive for it until the window
        // elapses. Replies mark it after the send succeeds instead, so a failure leaves the
        // notification to enrich and retry.
        val eventId = intent.getStringExtra(NotificationUtils.KEY_EVENT_ID)
        if (intent.action != NotificationUtils.REPLY_ACTION &&
            intent.action != NotificationUtils.PUBLIC_REPLY_ACTION &&
            intent.action != NotificationUtils.MARMOT_REPLY_ACTION
        ) {
            eventId?.let { NotificationUtils.markDismissed(it) }
        }

        val notificationManager =
            ContextCompat.getSystemService(context, NotificationManager::class.java)
                as NotificationManager

        when (intent.action) {
            NotificationUtils.MARK_READ_ACTION -> {
                notificationManager.cancelAndPrune(notificationId)
            }

            // The user swiped the notification away. It is already gone; all that is left
            // is to take its group summary with it when it was the last child.
            NotificationUtils.DISMISS_ACTION -> {
                notificationManager.cancelChildlessGroupSummaries(alreadyGone = notificationId)
            }

            NotificationUtils.REPLY_ACTION -> {
                val replyText = replyTextFrom(intent)
                if (replyText.isNullOrBlank()) return

                val accountNpub = intent.getStringExtra(NotificationUtils.KEY_ACCOUNT_NPUB) ?: return
                val chatroomMembersStr = intent.getStringExtra(NotificationUtils.KEY_CHATROOM_MEMBERS) ?: return
                val members = chatroomMembersStr.split(",").filter { it.isNotBlank() }

                if (members.isEmpty()) return

                runOnRelay(context, notificationManager, notificationId, eventId, replyText, intent) {
                    sendReply(accountNpub, members, replyText)
                }
            }

            NotificationUtils.PUBLIC_REPLY_ACTION -> {
                val replyText = replyTextFrom(intent)
                if (replyText.isNullOrBlank()) return

                val accountNpub = intent.getStringExtra(NotificationUtils.KEY_ACCOUNT_NPUB) ?: return
                val targetEventId = intent.getStringExtra(NotificationUtils.KEY_TARGET_EVENT_ID) ?: return

                runOnRelay(context, notificationManager, notificationId, eventId, replyText, intent) {
                    sendPublicReply(accountNpub, targetEventId, replyText)
                }
            }

            NotificationUtils.MARMOT_REPLY_ACTION -> {
                val replyText = replyTextFrom(intent)
                if (replyText.isNullOrBlank()) return

                val accountNpub = intent.getStringExtra(NotificationUtils.KEY_ACCOUNT_NPUB) ?: return
                val nostrGroupId = intent.getStringExtra(NotificationUtils.KEY_MARMOT_GROUP_ID) ?: return
                val replyToInnerId = intent.getStringExtra(NotificationUtils.KEY_MARMOT_REPLY_TO_INNER_ID)
                val replyToInnerAuthor = intent.getStringExtra(NotificationUtils.KEY_MARMOT_REPLY_TO_INNER_AUTHOR)

                runOnRelay(context, notificationManager, notificationId, eventId, replyText, intent) {
                    sendMarmotReply(accountNpub, nostrGroupId, replyToInnerId, replyToInnerAuthor, replyText)
                }
            }
        }
    }

    /**
     * The text of an inline reply: typed into the shade, or carried by a Retry re-sending one
     * that failed. A RemoteInput cannot be pre-filled, so a retry has to bring its own copy.
     */
    private fun replyTextFrom(intent: Intent): String? =
        RemoteInput
            .getResultsFromIntent(intent)
            ?.getCharSequence(NotificationUtils.KEY_REPLY_TEXT)
            ?.toString()
            ?: intent.getStringExtra(NotificationUtils.KEY_REPLY_TEXT)

    /**
     * Sends [block] and keeps the notification honest about how it went.
     *
     * The notification stays up rather than being cancelled on success. That is what every
     * other messenger does — the reply appears in the thread you replied to — and it is what
     * makes a failure visible at all: there is something left on screen to put the error on.
     * It clears the usual way, when the conversation is read in the app.
     *
     * The dismissal guard is recorded on **both** outcomes. On success it stops the enrichment
     * window resurrecting the pre-reply version seconds later; on failure it stops that same
     * re-render overwriting the error and the Retry that carries the user's text. A nicer
     * avatar is not worth losing an unsent message to.
     */
    private fun runOnRelay(
        context: Context,
        notificationManager: NotificationManager,
        notificationId: Int,
        eventId: String?,
        replyText: String,
        source: Intent,
        block: suspend () -> Unit,
    ) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val appContext = context.applicationContext

        scope.launch {
            val collectionJob =
                scope.launch {
                    Amethyst.instance.relayProxyClientConnector.relayServices
                        .collect()
                }

            // Stops "Sending…" becoming a lie the shade keeps telling. A broadcast receiver is
            // killed around ten seconds in, and a NIP-17 send can outlive that on its own — an
            // Amber round trip, a relay publish, proof-of-work mining — so whatever is on
            // screen at that moment is what the user is left with.
            //
            // A watchdog, deliberately, not a `withTimeout`: timing the send out would *cancel*
            // it, and a publish cancelled halfway is a worse outcome than a slow one. This only
            // changes what the notification says. If the send does finish afterwards, the Sent
            // or Failed render below overwrites it.
            val watchdog =
                scope.launch {
                    delay(UNCONFIRMED_AFTER_MS)
                    notificationManager.renderReplyState(
                        appContext,
                        notificationId,
                        ReplyState.Unconfirmed(replyText),
                    )
                }

            try {
                // Inside the try: everything from here on must reach the `finally`, which is
                // what calls pendingResult.finish() and releases the broadcast.
                notificationManager.renderReplyState(appContext, notificationId, ReplyState.Sending(replyText))
                block()
                // Joined, not just cancelled: if the watchdog is already inside its render, the
                // two notify() calls would land in an undefined order and the shade could keep
                // "Still sending…" over a message that went out.
                watchdog.cancelAndJoin()
                eventId?.let { NotificationUtils.markDismissed(it) }
                notificationManager.renderReplyState(appContext, notificationId, ReplyState.Sent)
            } catch (e: Exception) {
                // Rethrown first: joining is a suspend call, which an already-cancelled
                // coroutine cannot make. `scope.cancel()` below takes the watchdog with it.
                if (e is CancellationException) throw e
                watchdog.cancelAndJoin()
                Log.e("NotificationReply") { "Failed to send reply: ${e.message}" }
                eventId?.let { NotificationUtils.markDismissed(it) }
                notificationManager.renderReplyState(
                    appContext,
                    notificationId,
                    ReplyState.Failed(replyText, retryIntent(appContext, notificationId, replyText, source)),
                )
            } finally {
                pendingResult.finish()
                collectionJob.cancel()
                scope.cancel()
            }
        }
    }

    /**
     * A one-tap re-send of exactly what the user typed.
     *
     * A copy of [source] — which already carries the action and the account, room, group or
     * target this reply was addressed to — plus the text. Keeping the original action is what
     * makes this free: [onReceive] routes it back to the branch it came from with no alias to
     * resolve, the dismissal guard already treats it as the reply action it is, and
     * [replyTextFrom] already prefers a RemoteInput result and falls back to this extra.
     */
    private fun retryIntent(
        applicationContext: Context,
        notificationId: Int,
        replyText: String,
        source: Intent,
    ): PendingIntent {
        val intent =
            Intent(source).apply {
                setClass(applicationContext, NotificationReplyReceiver::class.java)
                putExtra(NotificationUtils.KEY_REPLY_TEXT, replyText)
            }

        return PendingIntent.getBroadcast(
            applicationContext,
            // The other three request codes for this notification are notId, +1 (mark read)
            // and +2 (dismiss).
            notificationId + 3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private suspend fun sendReply(
        accountNpub: String,
        chatroomMembers: List<String>,
        replyText: String,
    ) {
        val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(accountNpub) ?: return
        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)

        val recipients = chatroomMembers.map { PTag(it) }
        val template = ChatMessageEvent.build(msg = replyText, to = recipients)

        account.sendNip17PrivateMessage(template)
    }

    private suspend fun sendMarmotReply(
        accountNpub: String,
        nostrGroupId: String,
        replyToInnerEventId: String?,
        replyToInnerAuthor: String?,
        replyText: String,
    ) {
        val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(accountNpub) ?: return
        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)

        val manager = account.marmotManager ?: return

        // Use id+author from the Intent so the reply is threaded even when
        // LocalCache hasn't been rehydrated yet (cold-process broadcast
        // receiver: Account.restoreAll runs async on init and may not have
        // finished by the time we get here).
        // Only the rumor here: sendMarmotGroupMessage does the MLS encryption
        // and the group-state write, and building an envelope we would throw
        // away would ratchet the group an extra step for nothing.
        val innerEvent =
            manager.buildTextRumor(
                text = replyText,
                replyToEventId = replyToInnerEventId,
                replyToAuthorPubKey = replyToInnerAuthor,
            )

        account.marmot.sendMarmotGroupMessage(nostrGroupId, innerEvent, account.marmot.marmotGroupRelays(nostrGroupId))
    }

    private suspend fun sendPublicReply(
        accountNpub: String,
        targetEventId: String,
        replyText: String,
    ) {
        val accountSettings = LocalPreferences.loadAccountConfigFromEncryptedStorage(accountNpub) ?: return
        val account = Amethyst.instance.accountsCache.loadAccount(accountSettings)

        val targetEvent = LocalCache.getNoteIfExists(targetEventId)?.event ?: return

        // Resolve @/nostr: mentions typed into the notification reply, so a cited member is tagged
        // (`p`) and linkable — the same enrichment the in-app composers do. The comment builders
        // already tag the reply-parent author, so drop it from the body mentions to avoid a
        // duplicate `p` (kind-1 doesn't auto-tag the parent, so nothing is lost there).
        val tagger = NewMessageTagger(replyText, dao = LocalCache)
        tagger.run()
        val mentions = tagger.pTags?.mapNotNull { pt -> pt.pubkeyHex.takeIf { it != targetEvent.pubKey } }.orEmpty()

        val template =
            when {
                // A brand-new Amethyst kind-1 thread root is replied to with a NIP-22
                // kind 1111 Comment instead of a kind 1 reply.
                targetEvent is TextNoteEvent &&
                    targetEvent.isNewThread() &&
                    targetEvent.isClient(AccountCacheState.CLIENT_TAG_NAME) -> {
                    CommentEvent.replyBuilder(
                        msg = tagger.message,
                        replyingTo = EventHintBundle(targetEvent),
                    ) {
                        notify(mentions.map { PTag(it) })
                    }
                }

                targetEvent is TextNoteEvent -> {
                    TextNoteEvent.build(
                        note = tagger.message,
                        replyingTo = EventHintBundle(targetEvent),
                    ) {
                        notify(mentions.map { PTag(it) })
                    }
                }

                else -> {
                    // NIP-22 CommentEvent and other non-threaded events (e.g. long-form articles)
                    // both reply via NIP-22 comments.
                    CommentEvent.replyBuilder(
                        msg = tagger.message,
                        replyingTo = EventHintBundle(targetEvent),
                    ) {
                        notify(mentions.map { PTag(it) })
                    }
                }
            }

        account.signAndComputeBroadcast(template)
    }
}
