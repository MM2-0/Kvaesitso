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
package com.vitorpamplona.amethyst.commons.ui.theme

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Shapes
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarSize

val Shapes =
    Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(0.dp),
    )

val RippleRadius45dp = 45.dp // Ripple should be +10.dp over the component size

val BottomTopHeight = Modifier.height(50.dp)
val TabRowHeight = Modifier

val SmallestBorder = RoundedCornerShape(5.dp)
val SmallBorder = RoundedCornerShape(7.dp)
val SmallishBorder = RoundedCornerShape(9.dp)

/** The corner radius of [QuoteBorder], the rounded frame of note images. */
val ImageCornerRadius = 15.dp
val QuoteBorder = RoundedCornerShape(ImageCornerRadius)

val ButtonBorder = RoundedCornerShape(20.dp)
val LeftHalfCircleButtonBorder = ButtonBorder.copy(topEnd = CornerSize(0f), bottomEnd = CornerSize(0f))
val EditFieldBorder = RoundedCornerShape(25.dp)

// Chat bubble shapes live in commons (ChatTheme.kt) so Android and Desktop share
// one geometry: see ChatBubbleShapeMe/Them and their grouped Top/Middle/Bottom
// variants there.

val StdButtonSizeModifier = Modifier.size(19.dp)

val HalfVertSpacer = Modifier.height(2.dp)

val MinHorzSpacer = Modifier.width(1.dp)

val HalfHorzSpacer = Modifier.width(3.dp)

val StdHorzSpacer = Modifier.width(5.dp)
val StdVertSpacer = Modifier.height(5.dp)

val DoubleHorzSpacer = Modifier.width(10.dp)
val DoubleVertSpacer = Modifier.height(10.dp)

val Height100Modifier = Modifier.height(100.dp)

val HalfDoubleVertSpacer = Modifier.height(7.dp)

val Size0dp = 0.dp
val Size2dp = 2.dp
val Size3dp = 3.dp
val Size5dp = 5.dp
val Size6dp = 6.dp
val Size8dp = 8.dp
val Size10dp = 10.dp
val Size12dp = 12.dp
val Size13dp = 13.dp
val Size14dp = 14.dp
val Size15dp = 15.dp
val Size16dp = 16.dp
val Size17dp = 17.dp
val Size18dp = 18.dp
val Size19dp = 19.dp
val Size20dp = 20.dp
val Size22dp = 22.dp
val Size23dp = 23.dp
val Size24dp = 24.dp
val Size25dp = 25.dp
val Size27dp = 27.dp
val Size30dp = 30.dp
val Size34dp = 34.dp
val Size35dp = 35.dp
val Size36dp = 36.dp
val Size40dp = 40.dp
val Size50dp = 50.dp
val Size55dp = 55.dp
val Size75dp = 75.dp
val Size100dp = 100.dp
val Size110dp = 110.dp
val Size165dp = 165.dp

/**
 * Max width for a label sitting beside a room name (the NIP-29 relay host, the Concord community, the
 * cordn coordinator on a Messages-list row; the relay/community chips in the Notifications feed).
 * Roughly half a phone row so a long relay URL or community name is truncated (middle ellipsis)
 * instead of crowding the room name out — the name is weighted and keeps whatever the label doesn't take.
 */
val ChatLabelMaxWidth = 140.dp

/**
 * Avatar size of a Messages-list row. Smaller than the 55dp feed avatar on purpose: in a list that is
 * read by name, a big saturated picture on every row outweighs the name it sits next to.
 */
val ChatRowAvatarSize = 48.dp
val ChatRowAvatarModifier = Modifier.size(ChatRowAvatarSize)
val ChatRowPictureModifier = Modifier.size(ChatRowAvatarSize).clip(shape = CircleShape)

/**
 * Content padding of the Messages list: a little air on top, and enough at the bottom that the last
 * row's timestamp scrolls clear of the new-conversation FAB.
 */
val ChatListPadding = PaddingValues(top = 4.dp, bottom = 96.dp)

val StdEndPadding = Modifier.padding(end = 10.dp)
val HalfEndPadding = Modifier.padding(end = 5.dp)
val HalfStartPadding = Modifier.padding(start = 5.dp)
val StdStartPadding = Modifier.padding(start = 10.dp)
val StdTopPadding = Modifier.padding(top = 10.dp)
val HalfTopPadding = Modifier.padding(top = 5.dp)
val HalfHalfTopPadding = Modifier.padding(top = 3.dp)

val HalfHalfVertPadding = Modifier.padding(vertical = 3.dp)
val HalfHalfHorzModifier = Modifier.padding(horizontal = 3.dp)

val HalfPadding = Modifier.padding(5.dp)
val StdPadding = Modifier.padding(10.dp)
val BigPadding = Modifier.padding(15.dp)

val RowColSpacing = Arrangement.spacedBy(3.dp)
val RowColSpacing5dp = Arrangement.spacedBy(5.dp)
val RowColSpacing10dp = Arrangement.spacedBy(10.dp)

val HalfHorzPadding = Modifier.padding(horizontal = 5.dp)
val HalfVertPadding = Modifier.padding(vertical = 5.dp)

/**
 * The space above and below a block in a post's text: an image, a video, a gallery, a PDF or a
 * link card. The same on both sides, so a block in the middle of a post sits evenly between its
 * paragraphs; the blank lines an author types around a block are dropped
 * (`dropBlankLinesAroundBlocks`), so this is the whole gap.
 */
val RichTextBlockSpacing = 8.dp

/** [RichTextBlockSpacing] above and below a block. */
val RichTextBlockPadding = Modifier.padding(vertical = RichTextBlockSpacing)

val HorzPadding = Modifier.padding(horizontal = 10.dp)
val VertPadding = Modifier.padding(vertical = 10.dp)

val HorzHalfVertPadding = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)

val DoubleHorzPadding = Modifier.padding(horizontal = 20.dp)
val DoubleVertPadding = Modifier.padding(vertical = 20.dp)

val MaxWidthWithHorzPadding = Modifier.fillMaxWidth().padding(horizontal = 10.dp)

val Size5Modifier = Modifier.size(5.dp)
val Size10Modifier = Modifier.size(10.dp)
val Size14Modifier = Modifier.size(14.dp)
val Size15Modifier = Modifier.size(15.dp)
val Size16Modifier = Modifier.size(16.dp)
val Size17Modifier = Modifier.size(17.dp)
val Size18Modifier = Modifier.size(18.dp)
val Size19Modifier = Modifier.size(19.dp)
val Size20Modifier = Modifier.size(20.dp)
val Size22Modifier = Modifier.size(22.dp)
val Size24Modifier = Modifier.size(24.dp)
val Size25Modifier = Modifier.size(25.dp)
val Size26Modifier = Modifier.size(26.dp)
val Size27Modifier = Modifier.size(27.dp)
val Size28Modifier = Modifier.size(28.dp)
val Size30Modifier = Modifier.size(30.dp)
val Size35Modifier = Modifier.size(35.dp)
val Size39Modifier = Modifier.size(39.dp)
val Size40Modifier = Modifier.size(40.dp)
val Size50Modifier = Modifier.size(50.dp)
val Size55Modifier = Modifier.size(55.dp)
val Size75Modifier = Modifier.size(75.dp)

val TinyBorders = Modifier.padding(2.dp)
val NoSoTinyBorders = Modifier.padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 2.dp)
val ReactionRowZapraiserWithPadding = Modifier.defaultMinSize(minHeight = 4.dp).padding(start = Size75dp).fillMaxWidth()
val ReactionRowZapraiser = Modifier.defaultMinSize(minHeight = 4.dp).fillMaxWidth()

val ReactionRowExpandButton = Modifier.width(65.dp).padding(start = 31.dp)

// The reactions row of a note whose author sits above a full-width body: the actions start at the
// body's 12dp edge and the expand button takes a fixed slot at the end instead of the author column.
val ReactionRowExpandButtonAtEnd = Modifier.width(28.dp)
val ReactionRowHeightAlignedToText = Modifier.padding(vertical = 6.dp).heightIn(min = 24.dp).padding(horizontal = 12.dp)
val ReactionRowZapraiserAlignedToText = Modifier.defaultMinSize(minHeight = 4.dp).padding(horizontal = 12.dp).fillMaxWidth()

val WidthAuthorPictureModifier = Modifier.width(55.dp)
val WidthAuthorPictureModifierWithPadding = Modifier.width(65.dp)

val VideoReactionColumnPadding = Modifier.padding(bottom = 75.dp)

val DividerThickness = 0.25.dp

val ReactionRowHeight = Modifier.padding(vertical = 7.dp).heightIn(min = 24.dp)
val ReactionRowHeightWithPadding = Modifier.padding(vertical = 6.dp).heightIn(min = 24.dp).padding(horizontal = 10.dp)
val ReactionRowHeightChat = Modifier.height(20.dp)
val ReactionRowHeightChatMaxWidth = Modifier.height(25.dp).fillMaxWidth()
val UserNameRowHeight = Modifier.fillMaxWidth()
val UserNameMaxRowHeight = Modifier.fillMaxWidth()

val Height24dpModifier = Modifier.height(24.dp)
val Height4dpModifier = Modifier.height(4.dp)
val Height25Modifier = Modifier.height(Size25dp)

val Height24dpFilledModifier = Modifier.fillMaxWidth().height(24.dp)
val Height4dpFilledModifier = Modifier.fillMaxWidth().height(4.dp)

val AccountPictureModifier = Modifier.size(55.dp).clip(shape = CircleShape)
val HeaderPictureModifier = Modifier.size(34.dp).clip(shape = CircleShape)

val ShowMoreRelaysButtonIconButtonModifier = Modifier.size(15.dp)
val ShowMoreRelaysButtonIconModifier = Modifier.size(20.dp)
val ShowMoreRelaysButtonBoxModifer = Modifier.width(55.dp).height(17.dp)

// Caps a bubble at ~3/4 of the row: past that, a long message turns into a wide slab
// that reads like a document instead of a line of conversation.
val ChatBubbleMaxSizeModifier = Modifier.fillMaxWidth(0.78f)

val ModifierWidth3dp = Modifier.width(3.dp)

val NotificationIconModifier = Modifier.width(55.dp).padding(end = 5.dp)
val NotificationIconModifierSmaller = Modifier.width(55.dp).padding(end = 4.dp)

val ZapPictureCommentModifier = Modifier.height(35.dp).widthIn(min = 35.dp)
val ChatHeadlineBorders = StdPadding

val VolumeBottomIconSize = Modifier.size(60.dp).padding(5.dp)
val PinBottomIconSize = Modifier.size(60.dp).padding(5.dp)
val PlayIconSize = Modifier.size(110.dp).padding(10.dp)
val NIP05IconSize = Modifier.size(13.dp).padding(top = 1.dp, start = 1.dp, end = 1.dp)

val CashuCardBorders = Modifier.fillMaxWidth().padding(10.dp).clip(shape = QuoteBorder)

val EditFieldModifier =
    Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp, top = 5.dp).fillMaxWidth()
val EditFieldTrailingIconModifier = Modifier.padding(start = 5.dp, end = 0.dp)

val ZeroPadding = PaddingValues(0.dp)
val HalfFeedPadding = PaddingValues(5.dp)
val FeedPadding = PaddingValues(top = 10.dp, bottom = 10.dp)
val ButtonPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)

val ChatPaddingInnerQuoteModifier = Modifier

// Two gaps only, so the column has a rhythm: ~2dp between bubbles of one author run and
// ~10dp where a new run starts. The gap lives on top of the bubble that opens a run.
val ChatPaddingModifier =
    Modifier
        .fillMaxWidth(1f)
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 9.dp,
            bottom = 1.dp,
        )

// Tighter top gap for a message visually connected to the previous message of the
// same author run (grouped bubbles).
val ChatPaddingGroupedModifier =
    Modifier
        .fillMaxWidth(1f)
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 1.dp,
            bottom = 1.dp,
        )

val profileContentHeaderModifier =
    Modifier.fillMaxWidth().padding(top = 70.dp, start = Size25dp, end = Size25dp)
val bannerModifier = Modifier.fillMaxWidth().height(120.dp)
val drawerSpacing = Modifier.padding(top = Size10dp, start = Size25dp, end = Size25dp)

val IconRowTextModifier = Modifier.padding(start = 16.dp)
val IconRowModifier = Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 25.dp)
val DrawerSectionHeaderModifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 25.dp, end = 15.dp)

val Width16Space = Modifier.width(Size16dp)

val emptyLineItemModifier = Modifier.height(Size75dp).fillMaxWidth()

val imageHeaderBannerSize = Modifier.fillMaxWidth().height(150.dp)

val authorNotePictureForImageHeader = Modifier.size(75.dp).padding(10.dp)

val normalWithTopMarginNoteModifier =
    Modifier
        .fillMaxWidth()
        .padding(
            start = 12.dp,
            end = 12.dp,
            top = 10.dp,
        )

val boostedNoteModifier =
    Modifier
        .fillMaxWidth()
        .padding(
            start = 0.dp,
            end = 0.dp,
            top = 0.dp,
        )

val liveStreamTag =
    Modifier
        .clip(SmallBorder)
        .background(Color.Black)
        .padding(horizontal = Size5dp)

val chatAuthorBox = Modifier.size(20.dp)
val chatAuthorImage = Modifier.size(20.dp).clip(shape = CircleShape)
val AuthorInfoVideoFeed = Modifier.width(75.dp).padding(end = 15.dp)

val messageDetailsModifier = Modifier.height(Size25dp)
val messageBubbleLimits = Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 6.dp)

val inlinePlaceholder =
    Placeholder(
        width = Font17SP,
        height = Font17SP,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
    )

val IncognitoIconModifier = Modifier.padding(top = 1.dp).size(14.dp)
val IncognitoIconButtonModifier = Modifier.padding(top = 2.dp).size(20.dp)

val hashVerifierMark = Modifier.width(40.dp).height(40.dp).padding(10.dp)

val noteComposeRelayBox = Modifier.width(55.dp).heightIn(min = 17.dp).padding(start = 2.dp, end = 1.dp)

val previewCardImageModifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(bottom = 5.dp)

val reactionBox =
    Modifier
        .padding(horizontal = 6.dp, vertical = 6.dp)
        .size(Size40dp)
        .padding(5.dp)

val ripple24dp = ripple(bounded = false, radius = Size24dp)

val defaultTweenDuration = 100
val defaultTweenFloatSpec = tween<Float>(durationMillis = defaultTweenDuration)
val defaultTweenIntOffsetSpec = tween<IntOffset>(durationMillis = defaultTweenDuration)

val StreamingHeaderModifier =
    Modifier
        .fillMaxWidth()
        .heightIn(min = 50.dp, max = 300.dp)

val PostKeyboard =
    KeyboardOptions.Default.copy(
        autoCorrectEnabled = true,
        capitalization = KeyboardCapitalization.Sentences,
    )

val SettingsCategoryFirstModifier = Modifier.padding(bottom = 8.dp)
val SettingsCategorySpacingModifier = Modifier.padding(top = 24.dp, bottom = 8.dp)

val SettingsCategoryFirstWithHorzBorderModifier = Modifier.padding(bottom = 8.dp, start = 10.dp, end = 10.dp)
val SettingsCategorySpacingWithHorzBorderModifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 10.dp, end = 10.dp)

val SquaredQuoteBorderModifier = Modifier.aspectRatio(1f).clip(shape = QuoteBorder)
val FillWidthQuoteBorderModifier = Modifier.fillMaxWidth().clip(shape = QuoteBorder)

val MediumRelayIconModifier =
    Modifier
        .size(Size35dp)
        .clip(shape = CircleShape)

val LargeRelayIconModifier =
    Modifier
        .size(Size55dp)
        .clip(shape = CircleShape)

val FollowSetImageModifier =
    Modifier
        .fillMaxWidth()
        .clip(QuoteBorder)
        .aspectRatio(ratio = 21f / 9f)

val SimpleImage75Modifier = Modifier.size(Size75dp).clip(QuoteBorder)
val SimpleImage35Modifier = Modifier.size(Size34dp).clip(shape = CircleShape)

val SimpleImageBorder = Modifier.fillMaxSize().clip(QuoteBorder)

val SimpleHeaderImage = Modifier.fillMaxWidth().heightIn(max = 200.dp)

val BadgePictureModifier = Modifier.size(35.dp).clip(shape = CutCornerShape(20))

val MaxWidthPaddingTop5dp = Modifier.fillMaxWidth().padding(top = 5.dp)

val VoiceHeightModifier = Modifier.fillMaxWidth().height(100.dp)

val PaddingHorizontal12Modifier = Modifier.padding(horizontal = 12.dp)

val QuickActionPopupShadow = Modifier.shadow(elevation = Size6dp, shape = SmallestBorder)

val SpacedBy2dp = Arrangement.spacedBy(Size2dp)
val SpacedBy3dp = Arrangement.spacedBy(Size3dp)
val SpacedBy5dp = Arrangement.spacedBy(Size5dp)
val SpacedBy10dp = Arrangement.spacedBy(Size10dp)
val SpacedBy55dp = Arrangement.spacedBy(Size55dp)

val PopupUpEffect = RoundedCornerShape(0.dp, 0.dp, 15.dp, 15.dp)

val Size50ModifierOffset10 = Modifier.size(50.dp).offset(y = (-10).dp)

val SuggestionListDefaultHeightChat = Modifier.heightIn(0.dp, 200.dp)
val SuggestionListDefaultHeightPage = Modifier.heightIn(0.dp, 300.dp)

val FollowPackHeaderModifier = Modifier.fillMaxWidth().height(TopBarSize)

val Size22ModifierWith4Padding = Modifier.size(22.dp).padding(end = 4.dp)

val TextStyleBottomNavBar =
    TextLinkStyles(
        SpanStyle(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
