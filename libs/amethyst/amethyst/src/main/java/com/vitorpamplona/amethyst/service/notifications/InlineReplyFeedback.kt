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

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_reply_failed
import com.vitorpamplona.amethyst.commons.resources.app_notification_reply_retry
import com.vitorpamplona.amethyst.commons.resources.app_notification_reply_sending
import com.vitorpamplona.amethyst.commons.resources.app_notification_reply_unconfirmed
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.utils.Log

/**
 * What became of a reply the user typed into the shade.
 *
 * The three outcomes are not two: a send that has neither returned nor thrown is its own
 * thing, and saying so is the only honest option — see [Unconfirmed].
 */
sealed interface ReplyState {
    /** On its way. [text] joins the thread so the user can see what they sent. */
    data class Sending(
        val text: String,
    ) : ReplyState

    /** It went out — drop the marker and leave the thread as it now reads. */
    data object Sent : ReplyState

    /**
     * Long enough that "Sending…" has stopped being true, without an answer either way.
     *
     * A broadcast receiver is killed around ten seconds in, and a NIP-17 send can exceed that
     * on its own: an Amber round trip, a relay publish, proof-of-work mining. Whatever the
     * shade is showing when the process dies is what it shows until the conversation is read,
     * so it must not be a claim we cannot stand behind.
     *
     * Deliberately offers no Retry. The message may well have gone out — re-sending would
     * deliver it twice, which is worse than leaving the user to open the app and look.
     */
    data class Unconfirmed(
        val text: String,
    ) : ReplyState

    /**
     * It threw. [retry] re-sends the same text on one tap, carrying the text itself because a
     * RemoteInput cannot be pre-filled and re-typing it is the thing this prevents.
     */
    data class Failed(
        val text: String,
        val retry: PendingIntent,
    ) : ReplyState
}

/**
 * Re-renders the live notification for [notId] to say what happened to an inline reply.
 *
 * Until this existed a reply typed in the shade was posted into silence: the send either
 * worked, and the notification vanished with no sign the message had gone, or it threw, and
 * nothing at all happened — same notification, text gone, user believing it sent. A denied
 * signature or a dead socket was indistinguishable from delivery.
 *
 * The live notification is the only place the reply can be shown, so it is rebuilt from
 * itself rather than from scratch: [NotificationCompat.Builder] can recover a builder from a
 * posted [Notification], and MessagingStyle can be extracted and extended. Callers that are
 * not conversations (BigText notifications carrying an inline reply) have no thread to append
 * to and get `setRemoteInputHistory`, which is the same idea in the shape that style supports.
 *
 * Returns false when nothing is posted under [notId] any more — the user swiped it away while
 * the reply was in flight. Nothing is re-posted in that case: they are done with it.
 */
suspend fun NotificationManager.renderReplyState(
    applicationContext: Context,
    notId: Int,
    state: ReplyState,
): Boolean =
    try {
        renderReplyStateOrThrow(applicationContext, notId, state)
    } catch (e: Exception) {
        // Feedback must never break the thing it reports on. Reading the live notification
        // and posting it back both go through the system, and both can throw (a revoked
        // POST_NOTIFICATIONS between check and notify, say) — and this runs either side of
        // the send, where an escape would cost the user the reply itself.
        Log.w(TAG, "Could not render the reply state", e)
        false
    }

private const val TAG = "InlineReplyFeedback"

private suspend fun NotificationManager.renderReplyStateOrThrow(
    applicationContext: Context,
    notId: Int,
    state: ReplyState,
): Boolean {
    val existing = activeNotifications.firstOrNull { it.id == notId }?.notification ?: return false

    val builder =
        NotificationCompat
            .Builder(applicationContext, existing)
            // The thread already alerted when the message arrived; an update about the user's
            // own reply must not buzz again.
            .setOnlyAlertOnce(true)

    val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existing)

    when (state) {
        is ReplyState.Sending -> {
            // `null` attributes the message to the MessagingStyle's own user.
            style?.addMessage(state.text, System.currentTimeMillis(), null as Person?)
                ?: builder.setRemoteInputHistory(arrayOf(state.text))
            builder.setSubText(loadStringRes(Res.string.app_notification_reply_sending))
        }

        ReplyState.Sent -> {
            // Sending already put the text in the thread — appending here would double it.
            builder.setSubText(null)
        }

        is ReplyState.Unconfirmed -> {
            if (style == null) builder.setRemoteInputHistory(arrayOf(state.text))
            builder.setSubText(loadStringRes(Res.string.app_notification_reply_unconfirmed))
        }

        is ReplyState.Failed -> {
            if (style == null) builder.setRemoteInputHistory(arrayOf(state.text))
            builder.setSubText(loadStringRes(Res.string.app_notification_reply_failed))

            val retryLabel = loadStringRes(Res.string.app_notification_reply_retry)

            // Rebuilt from the posted notification, so its actions come with it — and a bare
            // clearActions() would take Reply and Mark Read with them, leaving a failed reply
            // with no way to write a different one. Keep them, drop any Retry left by an
            // earlier attempt so repeated failures don't stack, then add this one.
            val kept =
                (0 until NotificationCompat.getActionCount(existing))
                    .mapNotNull { NotificationCompat.getAction(existing, it) }
                    .filter { it.title?.toString() != retryLabel }

            builder.clearActions()
            kept.forEach { builder.addAction(it) }
            builder.addAction(
                NotificationCompat.Action
                    .Builder(R.drawable.ic_action_reply, retryLabel, state.retry)
                    .build(),
            )
        }
    }

    style?.let { builder.setStyle(it) }

    notify(notId, builder.build())
    return true
}
