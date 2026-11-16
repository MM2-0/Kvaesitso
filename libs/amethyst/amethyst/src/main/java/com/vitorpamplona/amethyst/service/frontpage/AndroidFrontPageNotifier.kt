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
package com.vitorpamplona.amethyst.service.frontpage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageNotifier
import com.vitorpamplona.amethyst.commons.frontpage.PrintJobEnd
import com.vitorpamplona.amethyst.commons.frontpage.SavedEdition
import com.vitorpamplona.amethyst.commons.frontpage.npubOf
import com.vitorpamplona.amethyst.commons.model.navigation.uri.frontPageUri
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.front_page_channel
import com.vitorpamplona.amethyst.commons.resources.front_page_channel_id
import com.vitorpamplona.amethyst.commons.resources.front_page_failed_title
import com.vitorpamplona.amethyst.commons.resources.front_page_needs_you_body
import com.vitorpamplona.amethyst.commons.resources.front_page_needs_you_title
import com.vitorpamplona.amethyst.commons.resources.front_page_ready_title
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage.loadPrintEndSentence
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * "Today's Observer is ready" with the lead headline, "Your Observer is waiting" when a scheduled
 * edition needs the reader, or why a print the reader started failed. Tapping opens the front page on that edition, through the same
 * `amethyst:` route any deep link takes, switching to the account it was printed for.
 */
class AndroidFrontPageNotifier(
    private val context: Context,
) : FrontPageNotifier {
    override suspend fun paperReady(
        account: HexKey,
        edition: SavedEdition,
    ) {
        post(notificationId(account, Kind.READY), loadStringRes(Res.string.front_page_ready_title), edition.headline.orEmpty(), frontPageUri(edition.day, npubOf(account)))
        // A paper resolves what came before it: the failure and the "waiting" go.
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notificationId(account, Kind.FAILED))
        manager.cancel(notificationId(account, Kind.NEEDS_YOU))
    }

    override suspend fun needsYou(account: HexKey) {
        post(notificationId(account, Kind.NEEDS_YOU), loadStringRes(Res.string.front_page_needs_you_title), loadStringRes(Res.string.front_page_needs_you_body), frontPageUri(accountNpub = npubOf(account)))
    }

    /** A print the reader started gave no paper while they were away: why, with a tap back to the front page. */
    override suspend fun printFailed(
        account: HexKey,
        end: PrintJobEnd,
    ) {
        // Its own row: a failed print never replaces a paper the reader hasn't opened yet.
        post(notificationId(account, Kind.FAILED), loadStringRes(Res.string.front_page_failed_title), loadPrintEndSentence(end).orEmpty(), frontPageUri(accountNpub = npubOf(account)))
    }

    private suspend fun post(
        id: Int,
        title: String,
        body: String,
        uri: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        // POST_NOTIFICATIONS is runtime-granted on Android 13+.
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val tap =
            Intent(Intent.ACTION_VIEW, uri.toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, id, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder =
            NotificationCompat
                .Builder(context, loadStringRes(Res.string.front_page_channel_id))
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check above and notify().
        }
    }

    /** What a notification says: each has its own row per account. */
    enum class Kind { READY, FAILED, NEEDS_YOU }

    companion object {
        private const val NOT_ID_BASE = 0x7A000

        /**
         * One row per account and [kind]: tomorrow's paper replaces today's, a new failure the
         * last failure, and none of them another kind.
         */
        fun notificationId(
            account: HexKey,
            kind: Kind,
        ): Int = (NOT_ID_BASE xor account.hashCode()) * 3 + kind.ordinal

        @Volatile
        private var channel: NotificationChannel? = null

        suspend fun ensureChannel(context: Context) {
            if (channel != null) return
            channel =
                NotificationChannel(
                    loadStringRes(Res.string.front_page_channel_id),
                    loadStringRes(Res.string.front_page_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel!!)
        }
    }
}
