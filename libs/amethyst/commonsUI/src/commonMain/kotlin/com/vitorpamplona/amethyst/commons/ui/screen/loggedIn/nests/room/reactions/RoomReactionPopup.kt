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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.reactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.ReactionChoicePopupContent
import com.vitorpamplona.amethyst.commons.ui.note.popupAnimationEnter
import com.vitorpamplona.amethyst.commons.ui.note.popupAnimationExit
import com.vitorpamplona.amethyst.commons.ui.note.rememberVisibilityState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import kotlinx.collections.immutable.persistentSetOf

/**
 * Room-scoped reaction picker. Visually identical to
 * [com.vitorpamplona.amethyst.commons.ui.note.ReactionChoicePopup] — same
 * pop-up animation, same user-configured reaction set (including
 * NIP-30 custom emojis), same "change reactions" affordance — but
 * with two semantic deviations specific to live audio rooms:
 *
 *  1. **Repeatable.** A note's heart `ReactionChoicePopup` calls
 *     `reactToOrDelete`, which toggles a `(note, emoji)` pair: a
 *     second tap of the same emoji DELETES the first. For an audio
 *     room every tap is an ephemeral cheer — the user should be
 *     able to fire `🔥` ten times during a great moment without the
 *     second tap nuking the first. We bypass `reactToOrDelete` and
 *     call `account.reactTo` directly, which always emits a fresh
 *     kind-7.
 *
 *  2. **No "already reacted" gate.** The standard popup passes a
 *     `toRemove` set so already-reacted buttons render with a
 *     destructive hint (next tap = delete). For us every button is
 *     always "fresh", so `toRemove` is empty.
 *
 * Anchoring + animation mirror the original popup so the affordance
 * feels the same when the user enters/exits the room.
 */
@Composable
fun RoomReactionPopup(
    roomNote: AddressableNote,
    iconSize: Dp,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    onChangeAmount: () -> Unit,
) {
    val iconSizePx = with(LocalDensity.current) { -iconSize.toPx().toInt() }
    val visibilityState = rememberVisibilityState(onDismiss)
    val reactions by accountViewModel.reactionChoicesFlow().collectAsState()

    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, iconSizePx),
        onDismissRequest = { visibilityState.targetState = false },
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = popupAnimationEnter,
            exit = popupAnimationExit,
        ) {
            ReactionChoicePopupContent(
                listOfReactions = reactions,
                // Empty set — never mark a reaction as "already
                // emitted" so the user can fire the same emoji
                // repeatedly throughout the room.
                toRemove = persistentSetOf(),
                onClick = { reactionType ->
                    accountViewModel.launchSigner {
                        // Bypass `Account.reactTo` (which delegates to
                        // `ReactionAction.reactTo(note, …)` and
                        // short-circuits on `note.hasReacted(by, reaction)`
                        // — line 181 in ReactionAction.kt). For a live
                        // audio room we WANT the second tap of the same
                        // emoji to fire another kind-7. Build the
                        // template directly and hand it to
                        // `signAndComputeBroadcast`, which signs +
                        // publishes + writes to LocalCache without the
                        // duplicate gate.
                        val eventHint = roomNote.toEventHint<Event>()
                        if (eventHint != null) {
                            val template =
                                if (reactionType.startsWith(":")) {
                                    val emojiUrl = EmojiUrlTag.decode(reactionType)
                                    if (emojiUrl != null) {
                                        ReactionEvent.build(emojiUrl, eventHint)
                                    } else {
                                        ReactionEvent.build(reactionType, eventHint)
                                    }
                                } else {
                                    ReactionEvent.build(reactionType, eventHint)
                                }
                            accountViewModel.account.signAndComputeBroadcast(template)
                        }
                    }
                    visibilityState.targetState = false
                },
                onChangeAmount = onChangeAmount,
            )
        }
    }
}
