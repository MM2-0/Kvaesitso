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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ChatLayoutType
import com.vitorpamplona.amethyst.commons.ui.note.CommentIcon
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalBlankLineScale
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatAuthorAvatarSize
import com.vitorpamplona.amethyst.commons.ui.theme.ChatBubbleMaxSizeModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatPaddingGroupedModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatPaddingInnerQuoteModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ChatPaddingModifier
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.chatBubbleMe
import com.vitorpamplona.amethyst.commons.ui.theme.chatBubbleThem
import com.vitorpamplona.amethyst.commons.ui.theme.chatDraftBackground
import com.vitorpamplona.amethyst.commons.ui.theme.messageBubbleLimits
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// Half the height of a reaction chip, so the chip row overlaps the bubble by
// exactly its own vertical center.
private val ChatChipOverlapArrangement = Arrangement.spacedBy((-12).dp)

// Extra space reserved at the bottom of a bubble that has overlapping chips but no
// footer, so the chips ride over padding instead of the last line of text. The bubble
// already has 6dp bottom padding; the chips poke ~12dp in, so ~8dp more clears them.
private val ChatChipOverlapReserve = 8.dp

// The column the author face sits in, beside a group chat's bubbles, and its gap to them.
private val ChatAuthorGutterModifier = Modifier.width(ChatAuthorAvatarSize)
private val ChatAuthorGutterGap = Modifier.width(6.dp)

// Swipe-to-reply: releasing past the threshold fires the reply; the bubble never
// drags further than the max.
private val SwipeReplyThreshold = 56.dp
private val SwipeReplyMaxDrag = 72.dp

// COMPACT (IRC/Twitch): one dense line per message. A gap opens a new author run; lines of one
// run sit almost flush, told apart by the name every line starts with.
private val ChatCompactPaddingModifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp)
private val ChatCompactPaddingGroupedModifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp)

// The author column of a COMPACT line: a fixed width, names right-aligned against the text, so
// every message starts at the same x and the eye can run down one left edge. Longer names
// ellipsize. A repeated name (same author as the line above) is dimmed: it still marks where a
// message starts, without six identical bright labels down the column.
private val ChatCompactNameColumn = Modifier.width(96.dp)
private const val CHAT_COMPACT_REPEATED_NAME_ALPHA = 0.4f
private val ChatCompactNameGap = Modifier.width(8.dp)
private val ChatCompactRowShape = RoundedCornerShape(4.dp)
private val ChatCompactRowInset = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)

// ChatChipFlowRow insets its chips 8dp so they clear a bubble's rounded corner; with no bubble
// that inset only knocks them off the text's left edge.
private val ChatChipBubbleInsetUndo = Modifier.offset(x = (-8).dp)

// OPEN (AI-assistant style): someone else's message is open text with no bubble. A message's own
// blank lines are ~20dp tall, so messages need at least that between them to read as separate.
private val ChatOpenPaddingModifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 1.dp)
private val ChatOpenPaddingGroupedModifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 1.dp)
private val ChatOpenRowShape = RoundedCornerShape(8.dp)
private val ChatOpenRowInset = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
private val ChatOpenHeaderGap = Modifier.width(8.dp)

// The face in an OPEN run's header: about one line of body text tall, so the header weighs no more
// than a line of the message. The gutter's 28dp, beside a 13sp name, made every run open with a
// block taller than the text under it; at 20dp a photo and its follow badge (a third of it) are
// too small to read.
val ChatOpenAvatarSize = 22.dp
private val ChatOpenStackSpacing = Arrangement.spacedBy(4.dp)

// Open text keeps a readable measure on wide windows: past ~75 characters a line is hard to
// track back to the start of the next.
private val ChatOpenMaxWidth = Modifier.widthIn(max = 680.dp)

// Without a bubble edge, a blank line inside a message must be clearly shorter than the gap to
// the next message: half a line (~10dp) under OPEN's 12dp, and COMPACT marks each message with a name.
private const val CHAT_OPEN_BLANK_LINE_SCALE = 0.5f

// A quoted reply in the COMPACT and OPEN layouts: a tinted block with a bar down its leading edge
// instead of a bubble inside a bubble.
private val ChatQuoteBlockShape = RoundedCornerShape(6.dp)
private val ChatQuoteBarWidth = 3.dp
private val ChatQuoteBlockInset = Modifier.padding(start = ChatQuoteBarWidth + 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubbleLayout(
    isLoggedInUser: Boolean,
    isDraft: Boolean,
    innerQuote: Boolean,
    drawAuthorInfo: Boolean,
    groupPosition: ChatGroupPosition = ChatGroupPosition.SINGLE,
    // Jumbo-emoji messages render without a bubble fill.
    transparentBubble: Boolean = false,
    parentBackgroundColor: MutableState<Color>? = null,
    shouldHighlight: Boolean = false,
    onHighlightFinished: (() -> Unit)? = null,
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)? = null,
    // Drag the bubble toward the screen center to quote-reply. Null disables the
    // gesture (inner quotes, drafts).
    onSwipeReply: (() -> Unit)? = null,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    reactionsRow: (@Composable () -> Unit)? = null,
    // Small footer in the bubble's bottom corner (timestamp, delivery, per-message
    // status glyphs). Null skips it entirely.
    footerRow: (@Composable () -> Unit)? = null,
    drawAuthorLine: @Composable () -> Unit,
    // The author's face in a group chat, drawn at the size it is handed. BUBBLES and COMPACT
    // put it OUTSIDE the bubble, beside the last bubble of the author's run (the other bubbles of
    // the run keep its width as an indent so the run lines up); OPEN puts a smaller one in the
    // run's header, beside the name. Null for one-on-one chats, own messages and quotes.
    authorAvatar: (@Composable (size: Dp) -> Unit)? = null,
    // How to lay the message out; follows the user's Chat Layout setting by default.
    layout: ChatLayoutType = LocalDisplaySettings.current.chatLayout,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val loggedInColors = MaterialTheme.colorScheme.chatBubbleMe
    val otherColors = MaterialTheme.colorScheme.chatBubbleThem
    val defaultBackground = MaterialTheme.colorScheme.background
    val draftColor = MaterialTheme.colorScheme.chatDraftBackground

    // Keyed on the theme-derived inputs so bubbles recolor when the user switches
    // light/dark or accent while the chat is composed (MaterialTheme recomposes
    // in place — nothing recreates this composable on a theme change).
    // Rows without a bubble: every COMPACT line and, in OPEN, everyone else's messages. They sit
    // straight on the screen's background, so that is the color their content composites over.
    // Quoted replies keep their tint in every layout.
    val openRow =
        !innerQuote &&
            when (layout) {
                ChatLayoutType.BUBBLES -> false
                ChatLayoutType.COMPACT -> true
                ChatLayoutType.OPEN -> !isLoggedInUser
            }

    val parentBg = parentBackgroundColor?.value
    val bgColor =
        remember(isLoggedInUser, isDraft, openRow, loggedInColors, otherColors, draftColor, defaultBackground, parentBg) {
            val base = parentBg ?: defaultBackground
            val bubble =
                when {
                    openRow -> Color.Transparent
                    isLoggedInUser && isDraft -> draftColor
                    isLoggedInUser -> loggedInColors
                    else -> otherColors
                }
            mutableStateOf(bubble.compositeOver(base))
        }

    val highlightActive = remember { mutableStateOf(false) }

    LaunchedEffect(shouldHighlight) {
        if (shouldHighlight) {
            highlightActive.value = true
            delay(1500)
            highlightActive.value = false
            onHighlightFinished?.invoke()
        }
    }

    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
    // An open row has no fill of its own: it rests fully transparent and only the scroll-to
    // highlight tints it, fading in and back out like a bubble's would.
    val restingColor = if (openRow) bgColor.value.copy(alpha = 0f) else bgColor.value
    val animatedColor by animateColorAsState(
        targetValue = if (highlightActive.value) highlightColor.compositeOver(bgColor.value) else restingColor,
        animationSpec = tween(durationMillis = if (highlightActive.value) 300 else 800),
        label = "highlightAnimation",
    )

    val haptic = LocalHapticFeedback.current

    // Plain state during the drag (a coroutine per pointer frame is wasted work);
    // a single animation job settles it back on release.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val swipeScope = rememberCoroutineScope()
    // The caller passes a fresh onSwipeReply lambda every recomposition (it captures
    // the note), so read it through updated-state and key pointerInput on the stable
    // swipe direction instead. Keying on the lambda would tear down and restart the
    // detector on every recomposition — stranding an in-flight drag and churning the
    // gesture coroutine on every idle row update.
    val latestOnSwipeReply by rememberUpdatedState(onSwipeReply)
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { SwipeReplyThreshold.toPx() } }
    val swipeMaxPx = remember(density) { with(density) { SwipeReplyMaxDrag.toPx() } }

    // The swipe pulls a message toward the middle of the screen: from the right for a message on
    // the right, from the left for one on the left. COMPACT puts every message on the left.
    val swipeFromEnd = isLoggedInUser && layout != ChatLayoutType.COMPACT

    Box(
        modifier =
            when {
                innerQuote -> ChatPaddingInnerQuoteModifier
                layout == ChatLayoutType.COMPACT && groupPosition.isConnectedAbove -> ChatCompactPaddingGroupedModifier
                layout == ChatLayoutType.COMPACT -> ChatCompactPaddingModifier
                openRow && groupPosition.isConnectedAbove -> ChatOpenPaddingGroupedModifier
                openRow -> ChatOpenPaddingModifier
                groupPosition.isConnectedAbove -> ChatPaddingGroupedModifier
                else -> ChatPaddingModifier
            },
    ) {
        if (onSwipeReply != null) {
            // Gate composition on a boolean that flips only at the 0<->non-0 edges, so
            // the icon is added/removed twice per gesture instead of recomposing every
            // frame; the fade/scale reads dragOffset inside graphicsLayer (layer phase),
            // so the whole swipe animates without recomposing ChatBubbleLayout.
            val swiping by remember { derivedStateOf { dragOffset != 0f } }
            if (swiping) {
                Box(
                    modifier =
                        Modifier
                            .align(if (swipeFromEnd) Alignment.CenterEnd else Alignment.CenterStart)
                            .graphicsLayer {
                                val progress = (abs(dragOffset) / swipeThresholdPx).coerceIn(0f, 1f)
                                alpha = progress
                                scaleX = 0.6f + 0.4f * progress
                                scaleY = 0.6f + 0.4f * progress
                            },
                ) {
                    CommentIcon(Size20Modifier, MaterialTheme.colorScheme.placeholderText)
                }
            }
        }

        val swipeModifier =
            if (onSwipeReply != null) {
                Modifier
                    .graphicsLayer { translationX = dragOffset }
                    .pointerInput(swipeFromEnd) {
                        // Drag toward the screen center only; a haptic tick marks the
                        // commit point, releasing past it fires the reply.
                        var crossedThreshold = false

                        val settleBack = {
                            settleJob =
                                swipeScope.launch {
                                    animate(dragOffset, 0f) { value, _ -> dragOffset = value }
                                }
                        }

                        val applyDrag = { dragAmount: Float ->
                            val newOffset =
                                if (swipeFromEnd) {
                                    (dragOffset + dragAmount).coerceIn(-swipeMaxPx, 0f)
                                } else {
                                    (dragOffset + dragAmount).coerceIn(0f, swipeMaxPx)
                                }
                            if (!crossedThreshold && abs(newOffset) >= swipeThresholdPx) {
                                crossedThreshold = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            dragOffset = newOffset
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // Claim the pointer only once the motion is clearly more
                            // horizontal than vertical; a mostly-vertical drag leaves the
                            // pointer unconsumed so the enclosing scroll keeps it and the
                            // bubble never moves while you scroll.
                            var overSlop = Offset.Zero
                            val drag =
                                awaitTouchSlopOrCancellation(down.id) { change, over ->
                                    if (abs(over.x) > abs(over.y)) {
                                        change.consume()
                                        overSlop = over
                                    }
                                }

                            if (drag != null) {
                                // Take over from any in-flight settle only now that we've
                                // committed to a horizontal drag. Cancelling earlier (on
                                // the down) would strand dragOffset when the gesture turns
                                // out to be a vertical scroll, since settleBack() below
                                // only runs on this path.
                                settleJob?.cancel()
                                crossedThreshold = false
                                applyDrag(overSlop.x)
                                try {
                                    val completed =
                                        horizontalDrag(drag.id) { change ->
                                            applyDrag(change.positionChange().x)
                                            change.consume()
                                        }
                                    if (completed && abs(dragOffset) >= swipeThresholdPx) {
                                        latestOnSwipeReply?.invoke()
                                    }
                                } finally {
                                    // Settle even if the drag coroutine is cancelled (e.g.
                                    // the bubble leaves composition mid-swipe); settleBack
                                    // launches on swipeScope, not this pointer coroutine,
                                    // so it still runs while that scope is alive.
                                    settleBack()
                                }
                            }
                        }
                    }
            } else {
                Modifier
            }

        CompositionLocalProvider(
            LocalBlankLineScale provides if (layout == ChatLayoutType.BUBBLES) LocalBlankLineScale.current else CHAT_OPEN_BLANK_LINE_SCALE,
        ) {
            when {
                innerQuote && layout != ChatLayoutType.BUBBLES -> {
                    ChatQuoteBlock(
                        isLoggedInUser = isLoggedInUser,
                        // A filled card is a second block inside a one-line layout; compact keeps the bar only.
                        filled = layout != ChatLayoutType.COMPACT,
                        drawAuthorInfo = drawAuthorInfo || layout == ChatLayoutType.COMPACT,
                        animatedColor = animatedColor,
                        bgColor = bgColor,
                        onClick = onClick,
                        onDoubleTap = onDoubleTap,
                        onAuthorClick = onAuthorClick,
                        actionMenu = actionMenu,
                        drawAuthorLine = drawAuthorLine,
                        inner = inner,
                    )
                }

                layout == ChatLayoutType.COMPACT -> {
                    CompactChatLine(
                        modifier = swipeModifier,
                        repeatsAuthorAbove = groupPosition.isConnectedAbove,
                        animatedColor = animatedColor,
                        bgColor = bgColor,
                        onClick = onClick,
                        onDoubleTap = onDoubleTap,
                        onAuthorClick = onAuthorClick,
                        actionMenu = actionMenu,
                        reactionsRow = reactionsRow,
                        footerRow = footerRow,
                        drawAuthorLine = drawAuthorLine,
                        inner = inner,
                    )
                }

                openRow -> {
                    OpenChatMessage(
                        modifier = swipeModifier,
                        drawAuthorInfo = drawAuthorInfo,
                        animatedColor = animatedColor,
                        bgColor = bgColor,
                        onClick = onClick,
                        onDoubleTap = onDoubleTap,
                        onAuthorClick = onAuthorClick,
                        actionMenu = actionMenu,
                        reactionsRow = reactionsRow,
                        footerRow = footerRow,
                        drawAuthorLine = drawAuthorLine,
                        authorAvatar = authorAvatar,
                        inner = inner,
                    )
                }

                else -> {
                    // A top-level bubble's row spans the screen so the bubble can sit on its side. A quoted
                    // reply is drawn INSIDE another bubble, where filling the width pushed that bubble out to
                    // its maximum: a one-word answer to a one-word message stretched across the screen.
                    Row(
                        modifier = (if (innerQuote) Modifier else Modifier.fillMaxWidth()).then(swipeModifier),
                        horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        if (authorAvatar != null && !isLoggedInUser && !innerQuote) {
                            Box(ChatAuthorGutterModifier) {
                                if (groupPosition.isLastOfGroup) authorAvatar(ChatAuthorAvatarSize)
                            }
                            Spacer(ChatAuthorGutterGap)
                        }
                        InnerChatBubble(
                            isLoggedInUser = isLoggedInUser,
                            innerQuote = innerQuote,
                            drawAuthorInfo = drawAuthorInfo,
                            groupPosition = groupPosition,
                            transparentBubble = transparentBubble,
                            animatedColor = animatedColor,
                            highlightActive = highlightActive.value,
                            bgColor = bgColor,
                            onClick = onClick,
                            onDoubleTap = onDoubleTap,
                            onAuthorClick = onAuthorClick,
                            actionMenu = actionMenu,
                            reactionsRow = reactionsRow,
                            footerRow = footerRow,
                            drawAuthorLine = drawAuthorLine,
                            inner = inner,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InnerChatBubble(
    isLoggedInUser: Boolean,
    innerQuote: Boolean,
    drawAuthorInfo: Boolean,
    groupPosition: ChatGroupPosition,
    transparentBubble: Boolean,
    animatedColor: Color,
    highlightActive: Boolean,
    bgColor: MutableState<Color>,
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)?,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    reactionsRow: (@Composable () -> Unit)?,
    footerRow: (@Composable () -> Unit)?,
    drawAuthorLine: @Composable () -> Unit,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val press = rememberChatPress(onClick, onDoubleTap)
    val pressScale by press.scale

    Column(
        horizontalAlignment = if (isLoggedInUser) Alignment.End else Alignment.Start,
        // Negative spacing pulls the reaction chips up so their vertical center
        // rides the bubble's bottom border instead of floating detached below it.
        verticalArrangement = if (reactionsRow != null) ChatChipOverlapArrangement else Arrangement.Top,
        modifier = if (innerQuote) Modifier else ChatBubbleMaxSizeModifier,
    ) {
        Surface(
            // Jumbo emoji show bare; the scroll-to highlight still tints them.
            color = if (transparentBubble && !highlightActive) Color.Transparent else animatedColor,
            shape = chatBubbleShapeFor(isLoggedInUser, if (innerQuote) ChatGroupPosition.SINGLE else groupPosition),
            modifier =
                press.modifier.graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        ) {
            Column(modifier = messageBubbleLimits, verticalArrangement = RowColSpacing5dp) {
                if (drawAuthorInfo) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isLoggedInUser) Arrangement.End else Arrangement.Start,
                        modifier = HalfHalfVertPadding.clickable(onClick = onAuthorClick),
                    ) {
                        drawAuthorLine()
                    }
                }

                inner(bgColor)

                if (footerRow != null) {
                    // Mirror the footer with the bubble so it sits on the same
                    // center-facing edge for both sides: the left for the logged-in user's
                    // own (right-aligned) bubbles, the right for everyone else's.
                    Box(modifier = Modifier.align(if (isLoggedInUser) Alignment.Start else Alignment.End)) {
                        footerRow()
                    }
                } else if (reactionsRow != null) {
                    // No footer to sit over — reserve space so the overlapping chips don't
                    // cover the last line of text.
                    Spacer(Modifier.height(ChatChipOverlapReserve))
                }
            }
        }

        reactionsRow?.invoke()
    }

    ChatActionMenuHost(press, actionMenu)
}

/** Tap, long-press (the action menu) and double-tap handling shared by every chat layout. */
private class ChatPressState(
    val modifier: Modifier,
    val popupExpanded: MutableState<Boolean>,
    val scale: State<Float>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberChatPress(
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)?,
): ChatPressState {
    val haptic = LocalHapticFeedback.current
    val popupExpanded = remember { mutableStateOf(false) }

    val pressInteractionSource = remember { MutableInteractionSource() }
    val isPressed by pressInteractionSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(if (isPressed) 0.97f else 1f, label = "bubblePressScale")

    val indication = LocalIndication.current
    // The modifier is built once, so it reads the callbacks through these: capturing them directly
    // kept the first composition's onClick, which scrolls to a quoted message by an index from the
    // list as it was then, off by however many messages arrived since.
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val hasDoubleTap = onDoubleTap != null
    val clickableModifier =
        remember(hasDoubleTap, indication) {
            Modifier.combinedClickable(
                interactionSource = pressInteractionSource,
                indication = indication,
                onClick = {
                    // Only inner-quote bubbles do anything on tap (scroll to the quoted
                    // message); a normal bubble tap is a no-op.
                    currentOnClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    popupExpanded.value = true
                },
                onDoubleClick =
                    if (hasDoubleTap) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnDoubleTap?.invoke()
                        }
                    } else {
                        null
                    },
            )
        }

    return remember(clickableModifier, pressScale) { ChatPressState(clickableModifier, popupExpanded, pressScale) }
}

@Composable
private fun ChatActionMenuHost(
    press: ChatPressState,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
) {
    if (press.popupExpanded.value) {
        actionMenu {
            press.popupExpanded.value = false
        }
    }
}

/**
 * The COMPACT (IRC/Twitch) line: the author's colored name, then the message in the rest of the
 * width, then the footer (time, delivery, glyphs) at the end. Every line names its author, the
 * user included, since nothing else tells one side from the other.
 */
@Composable
private fun CompactChatLine(
    modifier: Modifier,
    repeatsAuthorAbove: Boolean,
    animatedColor: Color,
    bgColor: MutableState<Color>,
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)?,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    reactionsRow: (@Composable () -> Unit)?,
    footerRow: (@Composable () -> Unit)?,
    drawAuthorLine: @Composable () -> Unit,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val press = rememberChatPress(onClick, onDoubleTap)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(ChatCompactRowShape)
                .background(animatedColor)
                .then(press.modifier)
                .then(ChatCompactRowInset),
        verticalAlignment = Alignment.Top,
    ) {
        // Baseline-aligned with the first line of the message; a body that reports no baseline
        // (media) falls back to the top, which is where the name belongs beside it anyway.
        Box(
            modifier =
                ChatCompactNameColumn
                    .alignByBaseline()
                    .graphicsLayer { alpha = if (repeatsAuthorAbove) CHAT_COMPACT_REPEATED_NAME_ALPHA else 1f },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(Modifier.clickable(onClick = onAuthorClick)) {
                drawAuthorLine()
            }
        }
        Spacer(ChatCompactNameGap)
        // The footer goes under the text, not beside it: beside it, a long timestamp narrowed
        // every line of the message and left a ragged second column down the right.
        Column(Modifier.weight(1f).alignByBaseline()) {
            CompositionLocalProvider(LocalChatJumboEmoji provides false) {
                inner(bgColor)
            }
            if (reactionsRow != null) {
                Box(ChatChipBubbleInsetUndo) { reactionsRow() }
            }
            footerRow?.invoke()
        }
    }

    ChatActionMenuHost(press, actionMenu)
}

/**
 * Someone else's message in the OPEN layout: open, full-width text with no bubble, like a reply in
 * an AI assistant's conversation view. The first message of a group chat run opens with the
 * author's face and name; reactions and the footer follow underneath.
 */
@Composable
private fun OpenChatMessage(
    modifier: Modifier,
    drawAuthorInfo: Boolean,
    animatedColor: Color,
    bgColor: MutableState<Color>,
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)?,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    reactionsRow: (@Composable () -> Unit)?,
    footerRow: (@Composable () -> Unit)?,
    drawAuthorLine: @Composable () -> Unit,
    authorAvatar: (@Composable (size: Dp) -> Unit)?,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val press = rememberChatPress(onClick, onDoubleTap)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(ChatOpenMaxWidth)
                .clip(ChatOpenRowShape)
                .background(animatedColor)
                .then(press.modifier)
                .then(ChatOpenRowInset),
        verticalArrangement = ChatOpenStackSpacing,
    ) {
        if (drawAuthorInfo) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (authorAvatar != null) {
                    authorAvatar(ChatOpenAvatarSize)
                    Spacer(ChatOpenHeaderGap)
                }
                Box(Modifier.clickable(onClick = onAuthorClick)) {
                    drawAuthorLine()
                }
            }
        }

        inner(bgColor)

        if (reactionsRow != null) {
            Box(ChatChipBubbleInsetUndo) { reactionsRow() }
        }

        if (footerRow != null) {
            footerRow()
        }
    }

    ChatActionMenuHost(press, actionMenu)
}

/**
 * A quoted reply in the COMPACT and OPEN layouts: a tinted block with a bar down its leading
 * edge, the author's name over a preview of what they said. Tapping it goes to the message.
 */
@Composable
private fun ChatQuoteBlock(
    isLoggedInUser: Boolean,
    filled: Boolean,
    drawAuthorInfo: Boolean,
    animatedColor: Color,
    bgColor: MutableState<Color>,
    onClick: () -> Boolean,
    onDoubleTap: (() -> Unit)?,
    onAuthorClick: () -> Unit,
    actionMenu: @Composable (onDismiss: () -> Unit) -> Unit,
    drawAuthorLine: @Composable () -> Unit,
    inner: @Composable (MutableState<Color>) -> Unit,
) {
    val press = rememberChatPress(onClick, onDoubleTap)
    val barColor = if (isLoggedInUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.placeholderText

    // The bar is drawn behind rather than laid out as a child, so the block never has to ask its
    // content for intrinsic sizes (a quoted message can hold media that cannot answer that).
    Column(
        modifier =
            Modifier
                .clip(ChatQuoteBlockShape)
                .background(if (filled) animatedColor else Color.Transparent)
                .drawBehind { drawRect(barColor, size = Size(ChatQuoteBarWidth.toPx(), size.height)) }
                .then(press.modifier)
                .then(ChatQuoteBlockInset),
        verticalArrangement = RowColSpacing5dp,
    ) {
        if (drawAuthorInfo) {
            Box(Modifier.clickable(onClick = onAuthorClick)) {
                drawAuthorLine()
            }
        }
        inner(bgColor)
    }

    ChatActionMenuHost(press, actionMenu)
}

@Preview
@Composable
private fun BubblePreview() {
    val bgColor =
        remember {
            mutableStateOf(Color.Transparent)
        }

    Column {
        ChatBubbleLayout(
            isLoggedInUser = false,
            isDraft = false,
            innerQuote = false,
            drawAuthorInfo = true,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape)
                                    .background(Color.LightGray),
                        )
                    },
                    name = {
                        Text("Someone else", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("This is my note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = false,
            innerQuote = false,
            drawAuthorInfo = true,
            groupPosition = ChatGroupPosition.TOP,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("This is a very long long loong note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = true,
            innerQuote = false,
            drawAuthorInfo = true,
            groupPosition = ChatGroupPosition.MIDDLE,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("This is a draft note")
        }

        ChatBubbleLayout(
            isLoggedInUser = true,
            isDraft = false,
            innerQuote = false,
            drawAuthorInfo = false,
            groupPosition = ChatGroupPosition.BOTTOM,
            parentBackgroundColor = bgColor,
            onClick = { false },
            onAuthorClick = {},
            actionMenu = { onDismiss ->
            },
            drawAuthorLine = {
                UserDisplayNameLayout(
                    picture = {
                        Icon(
                            symbol = MaterialSymbols.Person,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(Size20dp)
                                    .clip(CircleShape),
                        )
                    },
                    name = {
                        Text("Me", fontWeight = FontWeight.Bold)
                    },
                )
            },
        ) { _ ->
            Text("Short note")
        }
    }
}
