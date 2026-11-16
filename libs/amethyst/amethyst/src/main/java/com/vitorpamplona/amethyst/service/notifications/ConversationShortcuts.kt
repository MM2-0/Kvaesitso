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
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.notifications.NotificationRoutes
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.app_notification_shortcut_account_removed
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.utils.Log

/**
 * The long-lived shortcut that turns a MessagingStyle notification into an actual
 * *conversation* as far as the system is concerned.
 *
 * MessagingStyle on its own is only half the contract. Since Android 11 the shade sorts a
 * notification into the Conversations section — and offers the per-conversation controls
 * that come with it (mark a person Priority, silence one room without silencing every DM) —
 * only when the notification names a **long-lived shortcut** through `setShortcutId`.
 * Without one it is ranked as an ordinary alerting notification, which is what every DM,
 * group message and Buzz room got before this existed, despite the KDoc claiming otherwise.
 *
 * The same shortcut is the prerequisite for two things worth having later: bubbles
 * (`setBubbleMetadata`) and Direct Share targets. Neither is wired yet.
 *
 * ### What gets published
 *
 * One shortcut per conversation, keyed by [NotificationUtils.Conversation.id] — which
 * includes the account, so the same counterparty under two logins does not collapse into one
 * launcher entry pointing at the wrong inbox. The intent is the same deep link the
 * notification taps through ([NotificationRoutes]), so the launcher entry and the tap agree.
 *
 * ### Why this is gated on a setting
 *
 * A dynamic shortcut is visible in the launcher's long-press menu: publishing one puts a
 * contact's name and avatar outside the app, where someone holding the phone can read it
 * without unlocking anything of ours. That is the disclosure `showMessagesInNotifications`
 * exists to control, so callers pass [NotificationUtils.Conversation] only when it is on.
 */
object ConversationShortcuts {
    private const val TAG = "ConversationShortcuts"

    /**
     * Publishes (or refreshes) the conversation's shortcut and returns its id, or null when
     * the system would not take it.
     *
     * Null matters: a `setShortcutId` pointing at a shortcut that does not exist is worse
     * than none — the system logs it and still refuses the conversation treatment — so the
     * caller must only stamp the notification when this succeeded.
     *
     * [ShortcutManagerCompat.pushDynamicShortcut] already handles the two things that bite
     * here: it evicts the least-recently-used shortcut when the per-activity cap is reached,
     * and it is rate-limit aware. It can still throw on a malformed shortcut, so the whole
     * call is guarded — a conversation that cannot be published is a notification that
     * renders normally, never a crash on the notification path.
     */
    fun push(
        context: Context,
        conversation: NotificationUtils.Conversation,
        uri: String,
        person: Person,
        icon: Bitmap?,
    ): String? =
        try {
            val target =
                Intent(context, MainActivity::class.java).apply {
                    // A shortcut intent without an action is rejected outright.
                    action = Intent.ACTION_VIEW
                    data = uri.toUri()
                }

            val shortcut =
                ShortcutInfoCompat
                    .Builder(context, conversation.id)
                    .setShortLabel(conversation.label)
                    .setLongLabel(conversation.label)
                    .setIntent(target)
                    // Without this the system drops the shortcut as soon as it leaves the
                    // dynamic list, and the conversation loses its history and its ranking.
                    .setLongLived(true)
                    .apply {
                        // A group is not a person. Attaching the speaker would make the
                        // launcher entry's face flip to whoever last spoke while the label
                        // stayed the group, and would rank the chat as that one contact.
                        if (!conversation.isGroup) setPerson(person)
                        // Plain, not adaptive: `icon` is already circle-cropped, and the
                        // adaptive mask would crop that circle a second time — zoomed, with
                        // the edges clipped off.
                        icon?.let { setIcon(IconCompat.createWithBitmap(it)) }
                    }.build()

            if (ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)) {
                conversation.id
            } else {
                Log.d(TAG) { "System declined the shortcut for ${conversation.label}" }
                null
            }
        } catch (e: Exception) {
            // Builder.build() throws on a malformed shortcut (an empty label, say) and it is
            // inside the guard for that reason: this runs from postConversation, whose caller
            // swallows exceptions, so a throw escaping here would silently drop the whole
            // notification rather than just its conversation status.
            Log.d(TAG) { "Could not publish a shortcut for ${conversation.label}: ${e.message}" }
            null
        }

    /**
     * Drops every conversation this account published into the launcher.
     *
     * Logging out has to take these with it. They are the one part of an account that lives
     * outside the app's own storage — names and avatars of people it talked to, sitting in the
     * launcher's long-press menu — so an account that is gone from Amethyst but still lists its
     * contacts on the home screen would be a worse leak than not publishing them at all.
     *
     * Long-lived shortcuts are also removed from the system's cache, not just the dynamic list:
     * the cache is what keeps a conversation alive after it falls out of the top slots, so
     * dropping only the dynamic copy would leave it recoverable.
     */
    suspend fun removeForAccount(
        context: Context,
        accountNpub: String,
    ) {
        val scope = ":$accountNpub:"
        try {
            // Every list the system keeps, not just the dynamic one. `pushDynamicShortcut`
            // evicts the least-recently-used shortcut from the dynamic list when the cap is
            // reached, and a long-lived shortcut that has been used in a notification is kept
            // in the system *cache* rather than deleted — so the conversations most likely to
            // have fallen out of the dynamic list are exactly the ones a dynamic-only sweep
            // would leave behind, which is the leak this is here to close.
            val mine =
                ShortcutManagerCompat
                    .getShortcuts(
                        context,
                        ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or
                            ShortcutManagerCompat.FLAG_MATCH_CACHED or
                            ShortcutManagerCompat.FLAG_MATCH_PINNED,
                    ).map { it.id }
                    .filter { it.contains(scope) }

            if (mine.isEmpty()) return

            // A shortcut the user pinned to their home screen cannot be deleted by us, only
            // disabled — so disable first, then remove. Without this a pinned conversation
            // would survive the logout as a live tile.
            ShortcutManagerCompat.disableShortcuts(
                context,
                mine,
                loadStringRes(Res.string.app_notification_shortcut_account_removed),
            )
            ShortcutManagerCompat.removeLongLivedShortcuts(context, mine)
        } catch (e: Exception) {
            Log.d(TAG) { "Could not clear shortcuts for $accountNpub: ${e.message}" }
        }
    }
}
