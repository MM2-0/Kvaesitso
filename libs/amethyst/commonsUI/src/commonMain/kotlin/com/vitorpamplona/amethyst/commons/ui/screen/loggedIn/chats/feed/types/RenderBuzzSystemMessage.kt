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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vitorpamplona.amethyst.commons.chats.ui.ChatSystemMessage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_system_admin_kick
import com.vitorpamplona.amethyst.commons.resources.buzz_system_channel_archived
import com.vitorpamplona.amethyst.commons.resources.buzz_system_channel_auto_archived
import com.vitorpamplona.amethyst.commons.resources.buzz_system_channel_created
import com.vitorpamplona.amethyst.commons.resources.buzz_system_channel_deleted
import com.vitorpamplona.amethyst.commons.resources.buzz_system_channel_unarchived
import com.vitorpamplona.amethyst.commons.resources.buzz_system_dm_created
import com.vitorpamplona.amethyst.commons.resources.buzz_system_member_added
import com.vitorpamplona.amethyst.commons.resources.buzz_system_member_joined
import com.vitorpamplona.amethyst.commons.resources.buzz_system_member_left
import com.vitorpamplona.amethyst.commons.resources.buzz_system_member_removed
import com.vitorpamplona.amethyst.commons.resources.buzz_system_message_deleted
import com.vitorpamplona.amethyst.commons.resources.buzz_system_message_deleted_reason
import com.vitorpamplona.amethyst.commons.resources.buzz_system_purpose_changed
import com.vitorpamplona.amethyst.commons.resources.buzz_system_purpose_cleared
import com.vitorpamplona.amethyst.commons.resources.buzz_system_topic_changed
import com.vitorpamplona.amethyst.commons.resources.buzz_system_topic_cleared
import com.vitorpamplona.amethyst.commons.resources.buzz_system_ttl_cleared
import com.vitorpamplona.amethyst.commons.resources.buzz_system_ttl_set
import com.vitorpamplona.amethyst.commons.resources.buzz_system_unknown
import com.vitorpamplona.amethyst.commons.resources.buzz_system_visibility_open
import com.vitorpamplona.amethyst.commons.resources.buzz_system_visibility_other
import com.vitorpamplona.amethyst.commons.resources.buzz_system_visibility_private
import com.vitorpamplona.amethyst.commons.resources.duration_days
import com.vitorpamplona.amethyst.commons.resources.duration_hours
import com.vitorpamplona.amethyst.commons.resources.duration_minutes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size18dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.stream.SystemMessagePayload
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A Buzz kind-40099 system message: relay-authored narration of a channel state change
 * ("Alice joined", "Bob made this channel private"). It narrates the room rather than
 * speaking in it, so it renders as a centered system line like the NIP-28 admin events —
 * with the avatar of whoever the line is about, tapping through to their profile.
 *
 * The event's own author is the **relay keypair**, never a person, so the people in the
 * sentence come from the signed JSON payload's `actor`/`target` pubkeys, not from
 * `note.author`.
 */
@Composable
fun RenderBuzzSystemMessage(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? SystemMessageEvent ?: return
    val payload = remember(event) { event.payload() }
    val subject = remember(payload) { payload?.subject() }

    ChatSystemMessage(
        text = buzzSystemMessageText(event, accountViewModel),
        onClick = subject?.let { { nav.nav(Route.Profile(it)) } },
        leading =
            subject?.let {
                {
                    UserPicture(
                        userHex = it,
                        size = Size18dp,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            },
    )
}

/**
 * The one-line sentence for a Buzz kind-40099 system message, with `actor`/`target` pubkeys
 * resolved to the names the viewer knows them by (petname first, like everywhere else).
 *
 * Shared by the in-chat system line and the Messages-list preview so the two can never word
 * the same event differently. A payload this version has no sentence for degrades to
 * "name: the_raw_type" rather than vanishing — new relay vocabulary stays legible.
 */
@Composable
fun buzzSystemMessageText(
    event: SystemMessageEvent,
    accountViewModel: AccountViewModel,
): String {
    val payload = remember(event) { event.payload() } ?: return remember(event) { event.content.take(120) }

    val actor = observeUserNameByHex(payload.actor, accountViewModel)
    val target = observeUserNameByHex(payload.target, accountViewModel)
    val subject = if (payload.target != null) target else actor

    return when (payload.type) {
        // The relay emits the same type for "I joined" and "someone added me", separated only by
        // actor == target. Wording them the same would credit a self-join to whoever invited.
        SystemMessagePayload.MEMBER_JOINED ->
            if (payload.target == null || payload.target == payload.actor) {
                stringRes(Res.string.buzz_system_member_joined, subject)
            } else {
                stringRes(Res.string.buzz_system_member_added, target, actor)
            }

        SystemMessagePayload.MEMBER_LEFT -> stringRes(Res.string.buzz_system_member_left, subject)

        SystemMessagePayload.MEMBER_REMOVED ->
            if (payload.target == null || payload.target == payload.actor) {
                stringRes(Res.string.buzz_system_member_left, subject)
            } else {
                stringRes(Res.string.buzz_system_member_removed, target, actor)
            }

        SystemMessagePayload.TOPIC_CHANGED ->
            payload.topic?.takeIf { it.isNotBlank() }?.let {
                stringRes(Res.string.buzz_system_topic_changed, actor, it)
            } ?: stringRes(Res.string.buzz_system_topic_cleared, actor)

        SystemMessagePayload.PURPOSE_CHANGED ->
            payload.purpose?.takeIf { it.isNotBlank() }?.let {
                stringRes(Res.string.buzz_system_purpose_changed, actor, it)
            } ?: stringRes(Res.string.buzz_system_purpose_cleared, actor)

        // Buzz has exactly two visibility modes; spell out what each one means for the reader
        // rather than echoing the relay's token, and keep a literal fallback for a third.
        SystemMessagePayload.VISIBILITY_CHANGED ->
            when (payload.visibility) {
                SystemMessagePayload.VISIBILITY_OPEN -> stringRes(Res.string.buzz_system_visibility_open, actor)
                SystemMessagePayload.VISIBILITY_PRIVATE -> stringRes(Res.string.buzz_system_visibility_private, actor)
                else -> stringRes(Res.string.buzz_system_visibility_other, actor, payload.visibility ?: "")
            }

        // A null ttl_seconds is the relay clearing the TTL (messages become permanent).
        SystemMessagePayload.TTL_CHANGED ->
            payload.ttlSeconds?.takeIf { it > 0 }?.let {
                stringRes(Res.string.buzz_system_ttl_set, actor, ttlDurationText(it))
            } ?: stringRes(Res.string.buzz_system_ttl_cleared, actor)

        SystemMessagePayload.CHANNEL_ARCHIVED -> stringRes(Res.string.buzz_system_channel_archived, actor)
        SystemMessagePayload.CHANNEL_UNARCHIVED -> stringRes(Res.string.buzz_system_channel_unarchived, actor)
        SystemMessagePayload.CHANNEL_CREATED -> stringRes(Res.string.buzz_system_channel_created, actor)
        SystemMessagePayload.CHANNEL_DELETED -> stringRes(Res.string.buzz_system_channel_deleted, actor)

        SystemMessagePayload.MESSAGE_DELETED ->
            payload.publicReason?.takeIf { it.isNotBlank() }?.let {
                stringRes(Res.string.buzz_system_message_deleted_reason, actor, it)
            } ?: stringRes(Res.string.buzz_system_message_deleted, actor)

        SystemMessagePayload.DM_CREATED -> stringRes(Res.string.buzz_system_dm_created, actor)

        // Relay-authored with no actor: the administrator who acted is deliberately not named.
        SystemMessagePayload.ADMIN_KICK -> stringRes(Res.string.buzz_system_admin_kick, target)

        SystemMessagePayload.CHANNEL_AUTO_ARCHIVED -> stringRes(Res.string.buzz_system_channel_auto_archived)

        else -> stringRes(Res.string.buzz_system_unknown, actor, payload.type.replace('_', ' '))
    }
}

/**
 * A TTL as a rounded, pluralized duration ("7 days", "12 hours"), reusing the same duration
 * plurals as the last-seen line. Rounds to the largest whole unit that fits, which is what the
 * relay's own values are (a day, a week) and reads better than "604800 seconds".
 */
@Composable
private fun ttlDurationText(seconds: Long): String =
    when {
        seconds >= TimeUtils.ONE_DAY -> {
            val n = (seconds / TimeUtils.ONE_DAY).toInt()
            pluralStringRes(Res.plurals.duration_days, n, n)
        }
        seconds >= TimeUtils.ONE_HOUR -> {
            val n = (seconds / TimeUtils.ONE_HOUR).toInt()
            pluralStringRes(Res.plurals.duration_hours, n, n)
        }
        else -> {
            val n = (seconds / TimeUtils.ONE_MINUTE).toInt().coerceAtLeast(1)
            pluralStringRes(Res.plurals.duration_minutes, n, n)
        }
    }

/**
 * The display name for a pubkey that arrived inside an event's *payload* rather than as its
 * author — resolving it needs a [com.vitorpamplona.amethyst.commons.model.User] first, which may
 * not be in the cache yet.
 *
 * Returns the shortened hex until the profile loads, and empty string for a null pubkey so a
 * caller can format a sentence whose subject the relay omitted without printing "null".
 */
@Composable
fun observeUserNameByHex(
    pubkey: HexKey?,
    accountViewModel: AccountViewModel,
): String {
    if (pubkey == null) return ""

    var user by remember(pubkey) { mutableStateOf(LocalCache.getUserIfExists(pubkey)) }

    if (user == null) {
        LaunchedEffect(pubkey) { user = LocalCache.checkGetOrCreateUser(pubkey) }
    }

    val loaded = user ?: return remember(pubkey) { pubkey.take(8) }
    val name by observeUserName(loaded, accountViewModel)
    return name
}
