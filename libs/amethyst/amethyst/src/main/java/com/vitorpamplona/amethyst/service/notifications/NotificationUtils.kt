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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.notifications.NotificationMessage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_mark_read_label
import com.vitorpamplona.amethyst.commons.resources.app_notification_me
import com.vitorpamplona.amethyst.commons.resources.app_notification_private_message
import com.vitorpamplona.amethyst.commons.resources.app_notification_reply_label
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Low-level tray-notification builder. Every per-kind renderer funnels through
 * [postStandard] (BigText / BigPicture) or [postConversation] (MessagingStyle),
 * both driven by a [NotificationCategory] that supplies the channel, accent
 * color, status-bar icon, group, and summary.
 *
 * Notifications are keyed by `id.hashCode()` (the triggering event id) so that
 * (a) reading the underlying post in-app clears the tray entry via
 * [dismissNotificationForEvent], and (b) re-posting the same id from the
 * enrichment path replaces the notification in place. `setOnlyAlertOnce(true)`
 * keeps those replacements silent.
 */
object NotificationUtils {
    const val REPLY_ACTION = "com.vitorpamplona.amethyst.REPLY_ACTION"
    const val PUBLIC_REPLY_ACTION = "com.vitorpamplona.amethyst.PUBLIC_REPLY_ACTION"
    const val MARMOT_REPLY_ACTION = "com.vitorpamplona.amethyst.MARMOT_REPLY_ACTION"
    const val MARK_READ_ACTION = "com.vitorpamplona.amethyst.MARK_READ_ACTION"
    const val DISMISS_ACTION = "com.vitorpamplona.amethyst.DISMISS_ACTION"
    const val KEY_REPLY_TEXT = "key_reply_text"
    const val KEY_NOTIFICATION_ID = "key_notification_id"

    /**
     * Hex id of the event this notification was posted for, carried on every action
     * and on the delete intent so the receiver can mark it dismissed. Distinct from
     * [KEY_TARGET_EVENT_ID], which is the note an inline reply is addressed to.
     */
    const val KEY_EVENT_ID = "key_event_id"

    const val KEY_ACCOUNT_NPUB = "key_account_npub"
    const val KEY_CHATROOM_MEMBERS = "key_chatroom_members"
    const val KEY_TARGET_EVENT_ID = "key_target_event_id"
    const val KEY_MARMOT_GROUP_ID = "key_marmot_group_id"
    const val KEY_MARMOT_REPLY_TO_INNER_ID = "key_marmot_reply_to_inner_id"
    const val KEY_MARMOT_REPLY_TO_INNER_AUTHOR = "key_marmot_reply_to_inner_author"

    const val REPLY_GROUP_KEY_PREFIX = "com.vitorpamplona.amethyst.REPLY_NOTIFICATION"
    private const val REPLY_SUMMARY_ID_BASE = 0x50000

    /**
     * Every group key this object posts under starts with this. Used to tell our own
     * summaries apart from the ones the system creates when it force-groups us (those
     * live under `userId|pkg|g:Aggregate_…`), so the cleanup below never fights the
     * platform over a bundle it owns.
     */
    private const val OWN_GROUP_PREFIX = "com.vitorpamplona.amethyst."

    // Event ids the user is done with. The enrichment path re-posts a notification
    // as metadata arrives; without this guard a notification the user already got
    // rid of would be resurrected seconds later when its author's kind:0 lands, and
    // the enricher would go on holding a relay window and a wakelock open for it.
    // Every way a user can be done with a notification has to record here — reading
    // the event in-app, swiping the notification away, "mark as read", and replying
    // inline — or that path leaks the resurrection. Keyed by the event id string
    // (not the hashCode) so distinct events can't collide. Entries self-expire after
    // a window comfortably longer than the 25s enrichment window.
    private const val DISMISS_GUARD_MS = 90_000L
    private val recentlyDismissed = ConcurrentHashMap<String, Long>()

    fun markDismissed(eventId: String) {
        val now = SystemClock.elapsedRealtime()
        recentlyDismissed[eventId] = now + DISMISS_GUARD_MS
        if (recentlyDismissed.size > 256) {
            recentlyDismissed.entries.removeAll { it.value < now }
        }
    }

    /** True if [eventId] was read/dismissed in-app within the guard window. */
    fun wasDismissed(eventId: String): Boolean {
        val expiry = recentlyDismissed[eventId] ?: return false
        if (SystemClock.elapsedRealtime() > expiry) {
            recentlyDismissed.remove(eventId)
            return false
        }
        return true
    }

    /**
     * Derives a stable summary notification id for a per-thread reply group.
     * Uses the thread root id hash mixed with the base id so different threads
     * don't collide with each other or with the other channel summaries.
     */
    fun replySummaryIdFor(threadRootId: String): Int = REPLY_SUMMARY_ID_BASE xor threadRootId.hashCode()

    fun replyGroupKeyFor(threadRootId: String): String = "$REPLY_GROUP_KEY_PREFIX:$threadRootId"

    /**
     * Payload for wiring a RemoteInput-powered inline reply action onto a public
     * note notification. The receiver resolves the target event from LocalCache
     * via [targetEventId] and signs the appropriate kind (1 for NIP-10, 1111
     * for NIP-22) under the account identified by [accountNpub].
     */
    data class InlineReplyTarget(
        val accountNpub: String,
        val targetEventId: String,
    )

    /**
     * Wiring for a direct-message / group inline reply action. One of the three
     * shapes routes to the matching action in [NotificationReplyReceiver].
     */
    sealed interface ReplyAction {
        data class Dm(
            val accountNpub: String,
            val chatroomMembers: String,
        ) : ReplyAction

        data class Marmot(
            val accountNpub: String,
            val nostrGroupId: String,
            val replyToInnerEventId: String?,
            val replyToInnerAuthor: String?,
        ) : ReplyAction
    }

    /**
     * Identity of the chat a conversation notification belongs to, so the system can treat it
     * as one — see [ConversationShortcuts] for what that buys and why it needs a shortcut.
     *
     * [id] must be stable for the life of the conversation and unique across accounts; [label]
     * is what the conversation is called, which is not always the sender (a group message is
     * from a person but belongs to the group).
     *
     * Callers pass this only when the account allows message content in notifications: the
     * shortcut it publishes is readable from the launcher.
     */
    data class Conversation(
        val id: String,
        val label: String,
        /**
         * The chat's own picture, when it has one. Null falls back to the message sender's
         * avatar, which is the right face for a one-to-one DM and the wrong one for a group —
         * hence [isGroup], which suppresses that fallback along with the sender `Person`.
         */
        val iconUrl: String? = null,
        val isGroup: Boolean = false,
    )

    /** A prior message rendered above the main one in a MessagingStyle notification (thread context). */
    data class ParentMessage(
        val senderName: String,
        val body: String,
        val pictureUrl: String?,
        /**
         * True when the parent was authored by the logged-in account, so it is
         * attributed to the MessagingStyle `me` Person (avatar + "you") rather than
         * shown as a separate participant. False for a third party's note — e.g. a
         * reply to someone else's reply in a thread the account started.
         */
        val isFromMe: Boolean = false,
    )

    // ---------------------------------------------------------------------
    // Standard notification (BigText, or BigPicture when [bigPictureUrl] set)
    // ---------------------------------------------------------------------

    /** Posts a [NotificationMessage] built by a shared composer, on its topic's channel. */
    suspend fun NotificationManager.postStandard(
        message: NotificationMessage,
        applicationContext: Context,
    ) = postStandard(
        category = message.topic.category(),
        id = message.id,
        messageTitle = message.title,
        messageBody = message.body,
        time = message.time,
        pictureUrl = message.pictureUrl,
        uri = message.uri,
        applicationContext = applicationContext,
        bigPictureUrl = message.bigPictureUrl,
        badgeUrl = message.badgeUrl,
    )

    suspend fun NotificationManager.postStandard(
        category: NotificationCategory,
        id: String,
        messageTitle: String,
        messageBody: String,
        time: Long,
        pictureUrl: String?,
        uri: String,
        applicationContext: Context,
        bigPictureUrl: String? = null,
        badgeUrl: String? = null,
        inlineReply: InlineReplyTarget? = null,
        groupKey: String = category.group,
        summaryId: Int = category.summaryId,
    ) {
        // The user read/dismissed this event in-app while enrichment was still
        // running — don't resurrect it (and skip the bitmap work).
        if (wasDismissed(id)) return

        val channelId = category.ensureChannel(applicationContext)
        val notId = id.hashCode()

        val avatar = pictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }
        val largeIcon = badgeUrl?.let { overlayBadge(avatar, it, applicationContext) } ?: avatar
        val bigPicture = bigPictureUrl?.let { loadBitmap(it, applicationContext) }

        val contentPendingIntent = contentIntent(applicationContext, notId, uri)

        val builderPublic =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setContentTitle(messageTitle)
                .setContentText(loadStringRes(Res.string.app_notification_private_message))
                .setContentIntent(contentPendingIntent)
                .setPriority(category.priority())
                .setAutoCancel(true)
                .setWhen(time * 1000)

        val builder =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setContentTitle(messageTitle)
                .setContentText(messageBody)
                .setLargeIcon(largeIcon)
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(category.priority())
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setGroup(groupKey)
                .setDeleteIntent(dismissIntent(applicationContext, notId, id))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(time * 1000)

        if (category.colorized) builder.setColorized(true)

        if (bigPicture != null) {
            builder.setStyle(
                NotificationCompat
                    .BigPictureStyle()
                    .bigPicture(bigPicture)
                    .bigLargeIcon(null as Bitmap?),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(messageBody))
        }

        if (inlineReply != null) {
            builder.addAction(publicReplyAction(applicationContext, notId, id, inlineReply))
        }

        notify(notId, builder.build())
        sendGroupSummary(category, groupKey, summaryId, time, applicationContext)
    }

    // ---------------------------------------------------------------------
    // Conversation notification (MessagingStyle: DMs, replies, group chat)
    // ---------------------------------------------------------------------

    suspend fun NotificationManager.postConversation(
        category: NotificationCategory,
        id: String,
        senderName: String,
        pictureUrl: String?,
        messageBody: String,
        time: Long,
        uri: String,
        applicationContext: Context,
        accountPictureUrl: String? = null,
        parent: ParentMessage? = null,
        replyAction: ReplyAction? = null,
        publicInlineReply: InlineReplyTarget? = null,
        addMarkRead: Boolean = true,
        conversation: Conversation? = null,
        groupKey: String = category.group,
        summaryId: Int = category.summaryId,
    ) {
        // The user read/dismissed this event in-app while enrichment was still
        // running — don't resurrect it (and skip the bitmap work).
        if (wasDismissed(id)) return

        val channelId = category.ensureChannel(applicationContext)
        val notId = id.hashCode()

        val avatar = pictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }
        val accountAvatar = accountPictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }

        val sender =
            Person
                .Builder()
                .setName(senderName)
                .apply { avatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()

        val me =
            Person
                .Builder()
                .setName(loadStringRes(Res.string.app_notification_me))
                .apply { accountAvatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                .build()

        val messagingStyle = NotificationCompat.MessagingStyle(me)

        if (parent != null) {
            val parentSender =
                if (parent.isFromMe) {
                    // The account authored the parent — reuse the `me` Person so it
                    // renders as self with the account's avatar, not a stranger.
                    me
                } else {
                    val parentAvatar = parent.pictureUrl?.let { loadBitmap(it, applicationContext) }?.let { circleCrop(it) }
                    Person
                        .Builder()
                        .setName(parent.senderName)
                        .apply { parentAvatar?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                        .build()
                }
            messagingStyle.addMessage(parent.body, (time - 1) * 1000, parentSender)
        }
        messagingStyle.addMessage(messageBody, time * 1000, sender)

        val contentPendingIntent = contentIntent(applicationContext, notId, uri)

        // Published before the notification that names it: a shortcutId the system cannot
        // resolve is worse than none, so the id is only stamped below when this succeeded.
        val shortcutId =
            conversation?.let {
                val conversationIcon =
                    it.iconUrl?.let { url -> loadBitmap(url, applicationContext)?.let { bmp -> circleCrop(bmp) } }
                        ?: avatar.takeUnless { _ -> it.isGroup }

                ConversationShortcuts.push(applicationContext, it, uri, sender, conversationIcon)
            }

        val builderPublic =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setContentTitle(senderName)
                .setContentText(loadStringRes(Res.string.app_notification_private_message))
                .setLargeIcon(avatar)
                .setContentIntent(contentPendingIntent)
                .setPriority(category.priority())
                .setAutoCancel(true)
                .setWhen(time * 1000)

        val builder =
            NotificationCompat
                .Builder(applicationContext, channelId)
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setLargeIcon(avatar)
                .setStyle(messagingStyle)
                .setContentIntent(contentPendingIntent)
                .setPublicVersion(builderPublic.build())
                .setPriority(category.priority())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setGroup(groupKey)
                .setDeleteIntent(dismissIntent(applicationContext, notId, id))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(time * 1000)

        // The three halves of the conversation contract: the shortcut the shade resolves, the
        // locus that ties this notification to it, and the people it is with. All three have to
        // be present or the notification is ranked as an ordinary alert.
        if (shortcutId != null) {
            builder.setShortcutId(shortcutId)
            builder.setLocusId(LocusIdCompat(shortcutId))
        }
        builder.addPerson(sender)

        when (replyAction) {
            is ReplyAction.Dm -> builder.addAction(dmReplyAction(applicationContext, notId, id, replyAction))
            is ReplyAction.Marmot -> builder.addAction(marmotReplyAction(applicationContext, notId, id, replyAction))
            null -> publicInlineReply?.let { builder.addAction(publicReplyAction(applicationContext, notId, id, it)) }
        }

        if (addMarkRead) builder.addAction(markReadAction(applicationContext, notId, id))

        notify(notId, builder.build())
        sendGroupSummary(category, groupKey, summaryId, time, applicationContext)
    }

    // ---------------------------------------------------------------------
    // Intents & actions
    // ---------------------------------------------------------------------

    private fun contentIntent(
        applicationContext: Context,
        notId: Int,
        uri: String,
    ): PendingIntent {
        val contentIntent =
            Intent(applicationContext, MainActivity::class.java).apply { data = uri.toUri() }
        return PendingIntent.getActivity(
            applicationContext,
            notId,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Fires when the user swipes this notification away (or hits "Clear all"), so the
     * group summary can follow its last child out.
     *
     * We can't rely on the shade to take the summary with it: SystemUI hides a group
     * with a single child and renders that child at the top level
     * (`ShadeListBuilder.MIN_CHILDREN_FOR_GROUP`), and once promoted the child no
     * longer counts as "the only child in its group", so dismissing it leaves our
     * summary behind. A childless summary is not harmless — SystemUI promotes it into
     * the shade on its own, and the system force-groups it
     * (`GroupHelper.isGroupSummaryWithoutChildren`) into the same aggregate bundle we
     * post summaries to stay out of.
     */
    private fun dismissIntent(
        applicationContext: Context,
        notId: Int,
        eventId: String,
    ): PendingIntent {
        val intent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                action = DISMISS_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_EVENT_ID, eventId)
            }
        return PendingIntent.getBroadcast(
            applicationContext,
            notId + 2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private suspend fun replyRemoteInput(applicationContext: Context): RemoteInput =
        RemoteInput
            .Builder(KEY_REPLY_TEXT)
            .setLabel(loadStringRes(Res.string.app_notification_reply_label))
            .build()

    private suspend fun buildReplyAction(
        applicationContext: Context,
        notId: Int,
        intent: Intent,
    ): NotificationCompat.Action {
        val replyPendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                notId,
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Action
            .Builder(R.drawable.ic_action_reply, loadStringRes(Res.string.app_notification_reply_label), replyPendingIntent)
            .addRemoteInput(replyRemoteInput(applicationContext))
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private suspend fun dmReplyAction(
        applicationContext: Context,
        notId: Int,
        eventId: String,
        action: ReplyAction.Dm,
    ): NotificationCompat.Action {
        val intent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                this.action = REPLY_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_EVENT_ID, eventId)
                putExtra(KEY_ACCOUNT_NPUB, action.accountNpub)
                putExtra(KEY_CHATROOM_MEMBERS, action.chatroomMembers)
            }
        return buildReplyAction(applicationContext, notId, intent)
    }

    private suspend fun marmotReplyAction(
        applicationContext: Context,
        notId: Int,
        eventId: String,
        action: ReplyAction.Marmot,
    ): NotificationCompat.Action {
        val intent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                this.action = MARMOT_REPLY_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_EVENT_ID, eventId)
                putExtra(KEY_ACCOUNT_NPUB, action.accountNpub)
                putExtra(KEY_MARMOT_GROUP_ID, action.nostrGroupId)
                action.replyToInnerEventId?.let { putExtra(KEY_MARMOT_REPLY_TO_INNER_ID, it) }
                action.replyToInnerAuthor?.let { putExtra(KEY_MARMOT_REPLY_TO_INNER_AUTHOR, it) }
            }
        return buildReplyAction(applicationContext, notId, intent)
    }

    private suspend fun publicReplyAction(
        applicationContext: Context,
        notId: Int,
        eventId: String,
        target: InlineReplyTarget,
    ): NotificationCompat.Action {
        val intent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                action = PUBLIC_REPLY_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_EVENT_ID, eventId)
                putExtra(KEY_ACCOUNT_NPUB, target.accountNpub)
                putExtra(KEY_TARGET_EVENT_ID, target.targetEventId)
            }
        return buildReplyAction(applicationContext, notId, intent)
    }

    private suspend fun markReadAction(
        applicationContext: Context,
        notId: Int,
        eventId: String,
    ): NotificationCompat.Action {
        val markReadIntent =
            Intent(applicationContext, NotificationReplyReceiver::class.java).apply {
                action = MARK_READ_ACTION
                putExtra(KEY_NOTIFICATION_ID, notId)
                putExtra(KEY_EVENT_ID, eventId)
            }
        val markReadPendingIntent =
            PendingIntent.getBroadcast(
                applicationContext,
                notId + 1,
                markReadIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Action
            .Builder(R.drawable.ic_action_mark_read, loadStringRes(Res.string.app_notification_mark_read_label), markReadPendingIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .build()
    }

    // ---------------------------------------------------------------------
    // Bitmap helpers
    // ---------------------------------------------------------------------

    private suspend fun loadBitmap(
        pictureUrl: String,
        applicationContext: Context,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    ImageRequest
                        .Builder(applicationContext)
                        .data(pictureUrl)
                        .allowHardware(false)
                        .build()
                val imageLoader = SingletonImageLoader.get(applicationContext)
                val result = imageLoader.execute(request)
                (result.image?.asDrawable(applicationContext.resources) as? BitmapDrawable)?.bitmap
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                null
            }
        }

    /** Crops [src] to a centered circle so avatars render round in the tray. */
    private suspend fun circleCrop(src: Bitmap): Bitmap =
        withContext(Dispatchers.Default) {
            try {
                val size = min(src.width, src.height)
                val squared =
                    if (src.width != src.height) {
                        Bitmap.createBitmap(src, (src.width - size) / 2, (src.height - size) / 2, size, size)
                    } else {
                        src
                    }
                val output = createBitmap(size, size)
                val canvas = Canvas(output)
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        isFilterBitmap = true
                        shader = BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    }
                val r = size / 2f
                canvas.drawCircle(r, r, r, paint)
                output
            } catch (_: Exception) {
                src
            }
        }

    /**
     * Draws the badge image (e.g. a NIP-30 custom-emoji reaction) onto the bottom-right corner
     * of the base avatar. Returns the base unchanged if the badge can't be loaded, or the badge
     * alone if there is no base avatar.
     */
    private suspend fun overlayBadge(
        base: Bitmap?,
        badgeUrl: String,
        applicationContext: Context,
    ): Bitmap? {
        val badge = loadBitmap(badgeUrl, applicationContext) ?: return base
        if (base == null) return badge

        return withContext(Dispatchers.Default) {
            val result = base.copy(base.config ?: Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            val badgeSize = min(result.width, result.height) * 0.45f
            val dest =
                RectF(
                    result.width - badgeSize,
                    result.height - badgeSize,
                    result.width.toFloat(),
                    result.height.toFloat(),
                )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            canvas.drawBitmap(badge, null, dest, paint)
            result
        }
    }

    // ---------------------------------------------------------------------
    // Group summaries, dedup, dismissal
    // ---------------------------------------------------------------------

    /**
     * Posts (or refreshes) our own summary for [groupKey].
     *
     * The summary goes up with the **first** child, not once a second one shows up.
     * Android 16 counts a group child whose summary is missing as ungrouped
     * (`GroupHelper.isGroupChildWithoutSummary`) and force-groups it into the
     * package's per-section aggregate bundle, next to every other ungrouped
     * notification in the same shade section. The bar is low: `config_autoGroupAtCount`
     * is 2, so a single summary-less child plus one other ungrouped notification is a
     * bundle. The always-on relay service is exactly that other notification — ongoing
     * and IMPORTANCE_LOW, it shares the Silent section with our two IMPORTANCE_LOW
     * kinds (reactions and reposts), so one lone repost would end up bundled with it.
     * The bundle then refuses to swipe away, because the system's aggregate summary
     * inherits FLAG_ONGOING_EVENT from any child carrying it — and the service
     * notification always does.
     *
     * Providing the summary from the start keeps the group ours and the system leaves
     * it alone. It costs nothing visually: the shade hides any group with fewer than
     * two children and shows the child on its own.
     */
    private suspend fun NotificationManager.sendGroupSummary(
        category: NotificationCategory,
        groupKey: String,
        summaryId: Int,
        time: Long,
        applicationContext: Context,
    ) {
        val summaryBuilder =
            NotificationCompat
                .Builder(applicationContext, category.channelId(applicationContext))
                .setSmallIcon(category.smallIcon)
                .setColor(category.color)
                .setGroup(groupKey)
                .setGroupSummary(true)
                // The children do the alerting. Without this the summary would buzz on
                // its own the moment it starts going up alongside the first child.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                // Pinned to the child's event time rather than left to default to "now".
                // The summary is re-posted on every one of the enrichment path's re-renders,
                // and a fresh timestamp each time would keep re-sorting the group in the
                // shade while the user is looking at it.
                .setWhen(time * 1000)
                .setStyle(
                    NotificationCompat
                        .InboxStyle()
                        .setSummaryText(loadStringRes(category.summaryTextRes)),
                )

        notify(summaryId, summaryBuilder.build())
    }

    /** Cancels all notifications. */
    fun NotificationManager.cancelNotifications() {
        cancelAll()
    }

    /**
     * Dismisses the tray notification posted for [eventId] — used to auto-clear a
     * notification once the user reads the underlying event in-app.
     *
     * Per-event notifications are keyed by `id.hashCode()`, so hashing the same
     * event id targets exactly the notification posted for it. Cancelling an id
     * that isn't currently shown is a harmless no-op. After removing the child,
     * any group summary left without children is cancelled too so the tray
     * doesn't keep an empty summary around.
     */
    fun NotificationManager.dismissNotificationForEvent(eventId: HexKey) {
        // Record the dismissal first, unconditionally, so an in-flight enrichment
        // window can't re-post this notification after the user has read it — even
        // in the race where the initial post isn't visible in activeNotifications yet.
        markDismissed(eventId)

        val notId = eventId.hashCode()

        // Most events the user reads never had a tray notification (regular feed
        // items), so bail out before touching anything when nothing is posted for it.
        if (activeNotifications.none { it.id == notId }) return

        cancelAndPrune(notId)
    }

    /**
     * Cancels [notId] and drops the group summary it leaves behind, if it was the last
     * child. Use this instead of a bare [NotificationManager.cancel] for anything we
     * posted through [postStandard] / [postConversation] — every one of those is a
     * group child with a summary above it.
     */
    fun NotificationManager.cancelAndPrune(notId: Int) {
        cancel(notId)
        cancelChildlessGroupSummaries(alreadyGone = notId)
    }

    /**
     * Drops our summaries that no longer have any children.
     *
     * [alreadyGone] is the id of a notification cancelled moments ago: both
     * [NotificationManager.cancel] and [NotificationManager.notify] are asynchronous,
     * so [NotificationManager.activeNotifications] can still be listing it and would
     * otherwise keep its summary alive forever.
     *
     * Only summaries under [OWN_GROUP_PREFIX] are touched. The system's own aggregate
     * summaries also carry FLAG_GROUP_SUMMARY and show up in this list; cancelling one
     * only makes the platform rebuild it.
     */
    fun NotificationManager.cancelChildlessGroupSummaries(alreadyGone: Int? = null) {
        val active: Array<StatusBarNotification> = activeNotifications

        // Collect the groups that still have a child in one pass, then cancel the
        // summaries not in that set. Every child now ships with a summary, so this list
        // is about twice as long as it used to be and the pairwise scan it replaces grew
        // four-fold. Membership is decided by the summary flag rather than by comparing
        // ids, which is also what makes it correct when a child's id happens to equal the
        // summary's.
        val groupsWithChildren = HashSet<String>(active.size)
        for (child in active) {
            if (child.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (child.id == alreadyGone) continue
            child.notification.group?.let { groupsWithChildren.add(it) }
        }

        for (summary in active) {
            if (summary.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) continue
            val group = summary.notification.group ?: continue
            if (!group.startsWith(OWN_GROUP_PREFIX)) continue
            if (group !in groupsWithChildren) cancel(summary.id)
        }
    }

    private fun NotificationCategory.priority(): Int =
        when (importance) {
            NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> NotificationCompat.PRIORITY_HIGH
            NotificationManager.IMPORTANCE_LOW, NotificationManager.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
}

internal fun Context.notificationManager(): NotificationManager = ContextCompat.getSystemService(this, NotificationManager::class.java) as NotificationManager
