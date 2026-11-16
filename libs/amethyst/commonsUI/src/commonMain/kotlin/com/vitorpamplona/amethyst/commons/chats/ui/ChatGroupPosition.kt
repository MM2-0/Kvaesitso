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
package com.vitorpamplona.amethyst.commons.chats.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.privateChats.isSubjectOnlyChatMessage
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMe
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMeBottom
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMeMiddle
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeMeTop
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThem
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThemBottom
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThemMiddle
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleShapeThemTop
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent
import com.vitorpamplona.quartz.nip53LiveActivities.raid.LiveActivitiesRaidEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import kotlin.math.abs

/**
 * Where a message sits inside a run of consecutive bubbles by the same author.
 * [TOP] is the visually-first (oldest) message of the run and the only one that
 * shows the author line; [BOTTOM] is the visually-last (newest) and carries the
 * bubble tail.
 */
enum class ChatGroupPosition {
    SINGLE,
    TOP,
    MIDDLE,
    BOTTOM,
    ;

    val isFirstOfGroup: Boolean
        get() = this == SINGLE || this == TOP

    val isLastOfGroup: Boolean
        get() = this == SINGLE || this == BOTTOM

    val isConnectedAbove: Boolean
        get() = this == MIDDLE || this == BOTTOM
}

fun chatBubbleShapeFor(
    isLoggedInUser: Boolean,
    position: ChatGroupPosition,
): RoundedCornerShape =
    if (isLoggedInUser) {
        when (position) {
            ChatGroupPosition.SINGLE -> ChatBubbleShapeMe
            ChatGroupPosition.TOP -> ChatBubbleShapeMeTop
            ChatGroupPosition.MIDDLE -> ChatBubbleShapeMeMiddle
            ChatGroupPosition.BOTTOM -> ChatBubbleShapeMeBottom
        }
    } else {
        when (position) {
            ChatGroupPosition.SINGLE -> ChatBubbleShapeThem
            ChatGroupPosition.TOP -> ChatBubbleShapeThemTop
            ChatGroupPosition.MIDDLE -> ChatBubbleShapeThemMiddle
            ChatGroupPosition.BOTTOM -> ChatBubbleShapeThemBottom
        }
    }

/** Messages more than this far apart never group, even from the same author. */
const val CHAT_GROUP_WINDOW_SECONDS = 10 * 60L

/**
 * Event kinds that don't render as regular bubbles (zaps, raids, clips) or that
 * read as system messages (channel admin events, NIP-17 renames) never join an
 * author run.
 */
private fun isGroupableEvent(event: Event?): Boolean =
    event != null &&
        event !is ZapReceiptEvent &&
        event !is LiveActivitiesRaidEvent &&
        event !is LiveActivitiesClipEvent &&
        event !is ChannelCreateEvent &&
        event !is ChannelMetadataEvent &&
        !event.isSubjectOnlyChatMessage()

/**
 * Whether [newer] continues the author run started by [older]. Mirrors the break
 * conditions of `NewDateOrSubjectDivisor`: a date change or a subject header between
 * the two also breaks the bubble group.
 */
private fun groupsWith(
    newer: Note,
    older: Note,
): Boolean {
    val newerEvent = newer.event
    val olderEvent = older.event
    if (!isGroupableEvent(newerEvent) || !isGroupableEvent(olderEvent)) return false
    if (newerEvent == null || olderEvent == null) return false

    val newerAuthor = newer.author?.pubkeyHex ?: return false
    val olderAuthor = older.author?.pubkeyHex ?: return false
    if (newerAuthor != olderAuthor) return false

    if (abs(newerEvent.createdAt - olderEvent.createdAt) > CHAT_GROUP_WINDOW_SECONDS) return false

    // A subject header renders as a divisor above the newer message.
    if (newerEvent.subject() != null) return false

    // The same calendar day the day divisor compares.
    return chatDayKey(newerEvent.createdAt) == chatDayKey(olderEvent.createdAt)
}

/**
 * Computes the group position for [note] given its feed neighbors. The chat feed is
 * reverse-laid-out: [newer] is the message rendered visually below, [older] the one
 * visually above.
 */
fun computeChatGroupPosition(
    newer: Note?,
    note: Note,
    older: Note?,
): ChatGroupPosition {
    val connectedAbove = older != null && groupsWith(note, older)
    val connectedBelow = newer != null && groupsWith(newer, note)

    return when {
        connectedAbove && connectedBelow -> ChatGroupPosition.MIDDLE
        connectedAbove -> ChatGroupPosition.BOTTOM
        connectedBelow -> ChatGroupPosition.TOP
        else -> ChatGroupPosition.SINGLE
    }
}

/**
 * [computeChatGroupPosition] that recomputes when any of the three notes' events or
 * authors finish loading. The grouping math reads `event`/`author`, which resolve
 * asynchronously on the same Note objects, so caching by Note identity alone
 * latches a stale position for neighbors that compose before their data arrives.
 */
@Composable
fun watchChatGroupPosition(
    newer: Note?,
    note: Note,
    older: Note?,
): ChatGroupPosition {
    val noteVersion by note
        .flow()
        .metadata.stateFlow
        .collectAsStateWithLifecycle()
    val newerVersion =
        newer
            ?.flow()
            ?.metadata
            ?.stateFlow
            ?.collectAsStateWithLifecycle()
            ?.value
    val olderVersion =
        older
            ?.flow()
            ?.metadata
            ?.stateFlow
            ?.collectAsStateWithLifecycle()
            ?.value

    return remember(noteVersion, newerVersion, olderVersion, newer, older) {
        computeChatGroupPosition(newer, note, older)
    }
}
