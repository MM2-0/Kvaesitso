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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.concurrent.Volatile

// Contrast colour (black or white) for content drawn on top of an accent swatch.
fun contentColorOnAccent(color: Color): Color = onAccent(color)

// Black or white — whichever has the higher WCAG contrast ratio against a solid accent fill
// (primary/secondary/tertiary). Runs once per scheme build (not on a render path), so the
// luminance() cost is irrelevant here. Picks black on the light pastel accents used in the dark
// theme and on the purple theme's bright teal secondary, white on the deep accents used in light.
private fun onAccent(color: Color): Color {
    val luminance = color.luminance()
    val contrastOnBlack = (luminance + 0.05f) / 0.05f
    val contrastOnWhite = 1.05f / (luminance + 0.05f)
    return if (contrastOnBlack >= contrastOnWhite) Color.Black else Color.White
}

// Fill for the Material3 "container" roles (primaryContainer, etc.). A moderately saturated tone,
// mirroring Material's baseline containers but recoloured to the accent, so filled shapes such as
// the settings icon boxes, tonal buttons and selected chips stay clearly visible (not a faint
// wash). Dark themes darken the light accent toward a mid tone; light themes lighten the deep
// accent toward a pale tint. Left unset, these roles fall back to Material's baseline violet.
private fun accentContainer(
    accent: Color,
    dark: Boolean,
): Color = if (dark) lerp(accent, Color.Black, 0.58f) else lerp(accent, Color.White, 0.85f)

// High-contrast content for an accentContainer: near-white in dark (so icons/labels read as white
// on the accent), deep accent in light.
private fun onAccentContainer(
    accent: Color,
    dark: Boolean,
): Color = if (dark) lerp(accent, Color.White, 0.85f) else lerp(accent, Color.Black, 0.40f)

fun amethystDarkColorScheme(
    primary: Color,
    secondary: Color,
    inversePrimary: Color,
): ColorScheme =
    darkColorScheme(
        primary = primary,
        // Content on a primary fill (filled buttons, FAB glyphs). Picked by contrast against the
        // fill: on the dark theme `primary` is a light pastel, so this resolves to black (white on
        // it is only ~2.6:1). Left as white it washed out every filled button/FAB.
        onPrimary = onAccent(primary),
        primaryContainer = accentContainer(primary, dark = true),
        onPrimaryContainer = onAccentContainer(primary, dark = true),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        // Container roles derive from the accent (primary), not the teal secondary. Secondary/tertiary
        // container surfaces (FilledTonalButton, tonal chips) were neutral before; deriving them from
        // teal made them read as teal-on-teal, so they follow the accent hue instead.
        secondaryContainer = accentContainer(primary, dark = true),
        onSecondaryContainer = onAccentContainer(primary, dark = true),
        tertiary = secondary,
        onTertiary = onAccent(secondary),
        tertiaryContainer = accentContainer(primary, dark = true),
        onTertiaryContainer = onAccentContainer(primary, dark = true),
        inversePrimary = inversePrimary,
        surfaceTint = primary,
        // Neutral (hue-free) base + surface ramp. Left unset, these fall back to Material's
        // violet-tinted baseline greys, which read as a faint purple wash independent of the
        // accent. The greys below keep Material's lightness so contrast is unchanged.
        background = Color.Black,
        onBackground = Color(0xFFE6E6E6),
        surface = Color.Black,
        onSurface = Color(0xFFE6E6E6),
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = Color(0xFFCACACA),
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF383838),
        surfaceContainerLowest = Color(0xFF141414),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF252525),
        surfaceContainerHigh = Color(0xFF2E2E2E),
        surfaceContainerHighest = Color(0xFF383838),
        outline = Color(0xFF909090),
        outlineVariant = Color(0xFF454545),
    )

fun amethystLightColorScheme(
    primary: Color,
    secondary: Color,
    inversePrimary: Color,
): ColorScheme =
    lightColorScheme(
        primary = primary,
        // Contrast-picked content on the primary fill; the light theme's deep primary resolves to
        // white (unchanged), while keeping the rule identical across both schemes.
        onPrimary = onAccent(primary),
        primaryContainer = accentContainer(primary, dark = false),
        onPrimaryContainer = onAccentContainer(primary, dark = false),
        secondary = secondary,
        onSecondary = onAccent(secondary),
        // Container roles derive from the accent (primary), not the teal secondary. Secondary/tertiary
        // container surfaces (FilledTonalButton, tonal chips) were neutral before; deriving them from
        // teal made them read as teal-on-teal, so they follow the accent hue instead.
        secondaryContainer = accentContainer(primary, dark = false),
        onSecondaryContainer = onAccentContainer(primary, dark = false),
        tertiary = secondary,
        onTertiary = onAccent(secondary),
        tertiaryContainer = accentContainer(primary, dark = false),
        onTertiaryContainer = onAccentContainer(primary, dark = false),
        inversePrimary = inversePrimary,
        surfaceTint = primary,
        // Neutral (hue-free) base + surface ramp. Left unset, these fall back to Material's
        // violet-tinted baseline greys (e.g. surface #FEF7FF), which read as a faint purple wash
        // independent of the accent. The greys below keep Material's lightness so contrast is
        // unchanged.
        background = Color(0xFFFDFDFD),
        onBackground = Color(0xFF1C1C1C),
        surface = Color(0xFFFDFDFD),
        onSurface = Color(0xFF1C1C1C),
        surfaceVariant = Color(0xFFFAFAFA),
        onSurfaceVariant = Color(0xFF484848),
        surfaceDim = Color(0xFFDEDEDE),
        surfaceBright = Color(0xFFFDFDFD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F7F7),
        surfaceContainer = Color(0xFFF2F2F2),
        surfaceContainerHigh = Color(0xFFECECEC),
        surfaceContainerHighest = Color(0xFFEBEBEB),
        outline = Color(0xFF767676),
        outlineVariant = Color(0xFFCACACA),
    )

// The default (purple) palettes. The derived tints below are frozen from these at class
// load; the accent only changes primary/secondary, never the neutral ramp they read.
internal val DarkColorPalette = amethystDarkColorScheme(Purple200, Teal200, Purple500)
internal val LightColorPalette = amethystLightColorScheme(Purple500, Teal200, Purple200)

internal val DarkTransparentBackground = DarkColorPalette.background.copy(0.32f)
internal val LightTransparentBackground = LightColorPalette.background.copy(0.32f)

internal val DarkGrayText = DarkColorPalette.onSurface.copy(alpha = 0.52f)
internal val LightGrayText = LightColorPalette.onSurface.copy(alpha = 0.52f)

internal val DarkOnSurface65 = DarkColorPalette.onSurface.copy(alpha = 0.65f).compositeOver(DarkColorPalette.surface)
internal val LightOnSurface65 = LightColorPalette.onSurface.copy(alpha = 0.65f).compositeOver(LightColorPalette.surface)

internal val DarkPlaceholderText = DarkColorPalette.onSurface.copy(alpha = 0.42f)
internal val LightPlaceholderText = LightColorPalette.onSurface.copy(alpha = 0.42f)

internal val DarkOnBackgroundColorFilter = ColorFilter.tint(DarkColorPalette.onBackground)
internal val LightOnBackgroundColorFilter = ColorFilter.tint(LightColorPalette.onBackground)

internal val DarkSubtleButton = DarkColorPalette.onSurface.copy(alpha = 0.22f)
internal val LightSubtleButton = LightColorPalette.onSurface.copy(alpha = 0.22f)

internal val DarkSubtleBorder = DarkColorPalette.onSurface.copy(alpha = 0.12f)
internal val LightSubtleBorder = LightColorPalette.onSurface.copy(alpha = 0.05f)

internal val DarkChatBackground = DarkColorPalette.onSurface.copy(alpha = 0.12f)
internal val LightChatBackground = LightColorPalette.onSurface.copy(alpha = 0.08f)

// Bubble fill for other users' chat messages. Stronger than chatBackground so the
// bubble clearly separates from the screen background in both themes. Light lands on
// RGB 234 over the #FDFDFD background (0.11 gave 228, which read too dark).
internal val DarkChatBubbleThem = DarkColorPalette.onSurface.copy(alpha = 0.18f)
internal val LightChatBubbleThem = LightColorPalette.onSurface.copy(alpha = 0.084f)

internal val DarkChatDraftBackground = DarkColorPalette.onSurface.copy(alpha = 0.15f)
internal val LightChatDraftBackground = LightColorPalette.onSurface.copy(alpha = 0.15f)

internal val DarkOverPictureBackground = DarkColorPalette.background.copy(0.62f)
internal val LightOverPictureBackground = LightColorPalette.background.copy(0.62f)

val DarkImageModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, DarkSubtleBorder, QuoteBorder)

val LightImageModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, LightSubtleBorder, QuoteBorder)

val DarkVideoModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = RectangleShape)
        .border(1.dp, DarkSubtleBorder, RectangleShape)

val LightVideoModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = RectangleShape)
        .border(1.dp, LightSubtleBorder, RectangleShape)

val DarkProfile35dpModifier =
    Modifier
        .size(Size35dp)
        .clip(shape = CircleShape)

val LightProfile35dpModifier =
    Modifier
        .fillMaxWidth()
        .clip(shape = CircleShape)

val DarkReplyBorderModifier =
    Modifier
        .padding(top = 5.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, DarkSubtleBorder, QuoteBorder)

val LightReplyBorderModifier =
    Modifier
        .padding(top = 5.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, LightSubtleBorder, QuoteBorder)

val DarkInnerPostBorderModifier =
    Modifier
        .padding(vertical = 4.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, DarkSubtleBorder, QuoteBorder)

val LightInnerPostBorderModifier =
    Modifier
        .padding(vertical = 4.dp)
        .fillMaxWidth()
        .clip(shape = QuoteBorder)
        .border(1.dp, LightSubtleBorder, QuoteBorder)

val DarkMaxWidthWithBackground =
    Modifier
        .fillMaxWidth()
        .background(DarkColorPalette.background)

val LightMaxWidthWithBackground =
    Modifier
        .fillMaxWidth()
        .background(LightColorPalette.background)

// Geometry only — no color. The fill is applied live from the scheme's secondaryContainer in
// the selectedReactionBoxModifier getter so the selected-reaction highlight follows the accent
// instead of freezing to the purple palette captured at class load.
val SelectedReactionBoxOuterModifier =
    Modifier
        .padding(horizontal = 5.dp, vertical = 5.dp)
        .size(Size40dp)
        .clip(shape = SmallBorder)

val DarkChannelNotePictureModifier =
    Modifier
        .size(20.dp)
        .clip(shape = CircleShape)
        .border(2.dp, DarkColorPalette.background, CircleShape)

val LightChannelNotePictureModifier =
    Modifier
        .size(20.dp)
        .clip(shape = CircleShape)
        .border(2.dp, LightColorPalette.background, CircleShape)

val LightProfilePictureBorder =
    Modifier.border(
        3.dp,
        DarkColorPalette.background,
        CircleShape,
    )

val DarkProfilePictureBorder =
    Modifier.border(
        3.dp,
        LightColorPalette.background,
        CircleShape,
    )

val LightRelayIconModifier =
    Modifier
        .size(Size13dp)
        .clip(shape = CircleShape)

val DarkRelayIconModifier =
    Modifier
        .size(Size13dp)
        .clip(shape = CircleShape)

val darkLargeProfilePictureModifier =
    Modifier
        .size(120.dp)
        .clip(shape = CircleShape)
        .border(3.dp, DarkColorPalette.onBackground, CircleShape)

val lightLargeProfilePictureModifier =
    Modifier
        .size(120.dp)
        .clip(shape = CircleShape)
        .border(3.dp, LightColorPalette.onBackground, CircleShape)

// Geometry only — no color. The fill is applied live from the scheme's primary in the
// newItemBubbleModifier getter so the unread dot follows the accent (matching its sibling
// newItemBackgroundColor) instead of freezing to the purple palette captured at class load.
val NewItemBubbleShapeModifier =
    Modifier
        .size(10.dp)
        .clip(shape = CircleShape)

val darkBlackTagModifier =
    Modifier
        .clip(SmallestBorder)
        .background(DarkColorPalette.onBackground)
        .padding(horizontal = 5.dp)

val lightBlackTagModifier =
    Modifier
        .clip(SmallestBorder)
        .background(LightColorPalette.onBackground)
        .padding(horizontal = 5.dp)

// Hot path: this getter fans out to hundreds of themed-color call sites during rendering, so the
// two Amethyst backgrounds (accent choice never changes them) are answered with one reference
// comparison each. Any other ramp - desktop's own palette, or an audio room that overrides the
// background with its theme colour - is decided by luminance, which is memoized for the last
// background seen: those screens keep asking about the same colour, so they pay for
// luminance() once per background instead of once per call.
val ColorScheme.isLight: Boolean
    get() {
        val bg = background
        if (bg == Color.Black) return false
        if (bg == LightColorPalette.background) return true
        val memo = lastIsLightMemo
        if (memo != null && memo.background == bg) return memo.isLight
        return (bg.luminance() > 0.5f).also { lastIsLightMemo = IsLightMemo(bg, it) }
    }

private class IsLightMemo(
    val background: Color,
    val isLight: Boolean,
)

// One immutable pair behind a volatile reference, so a reader never sees a background from one
// write paired with the answer from another.
@Volatile
private var lastIsLightMemo: IsLightMemo? = null

// The accent-derived tints below are computed from the live scheme's primary so they follow
// the selected accent color. Color is an inline value class, so these copies don't allocate.
val ColorScheme.newItemBackgroundColor: Color
    get() = primary.copy(alpha = 0.12f)

val ColorScheme.transparentBackground: Color
    get() = if (isLight) LightTransparentBackground else DarkTransparentBackground

val ColorScheme.selectedNote: Color
    get() = primary.copy(alpha = 0.12f).compositeOver(background)

val ColorScheme.secondaryButtonBackground: Color
    get() = primary.copy(alpha = 0.32f).compositeOver(background)

val ColorScheme.lessImportantLink: Color
    get() = primary.copy(alpha = 0.52f)

val ColorScheme.mediumImportanceLink: Color
    get() = primary.copy(alpha = 0.32f)

val ColorScheme.placeholderText: Color
    get() = if (isLight) LightPlaceholderText else DarkPlaceholderText

// NIP-05 identifiers were a fixed purple; follow the selected accent instead.
val ColorScheme.nip05: Color
    get() = primary

val ColorScheme.onBackgroundColorFilter: ColorFilter
    get() = if (isLight) LightOnBackgroundColorFilter else DarkOnBackgroundColorFilter

val ColorScheme.grayText: Color
    get() = if (isLight) LightGrayText else DarkGrayText

val ColorScheme.onSurface65: Color
    get() = if (isLight) LightOnSurface65 else DarkOnSurface65

val ColorScheme.subtleBorder: Color
    get() = if (isLight) LightSubtleBorder else DarkSubtleBorder

val ColorScheme.chatBackground: Color
    get() = if (isLight) LightChatBackground else DarkChatBackground

// Accent-following bubble fill for the logged-in user's own chat messages. A tint, not a
// fill: enough accent that "mine" vs "theirs" reads at a glance, low enough that a screen
// of my own messages is a deep muted tone carrying white text, not a wall of purple (at
// 45% the bubbles were the loudest thing on the screen and the gray captions on them
// were hard to read). The accent is first pulled halfway to mid gray: a straight tint of a
// near-pure accent like Purple500 keeps all its saturation and reads as neon lavender.
val ColorScheme.chatBubbleMe: Color
    get() = dustyTint(primary, alpha = if (isLight) 0.22f else 0.30f)

// Plain sRGB average with mid gray (Compose's Color lerp blends in Oklab, which keeps more
// of the accent's chroma than intended here).
private fun dustyTint(
    accent: Color,
    alpha: Float,
): Color =
    Color(
        red = (accent.red + 0.5f) / 2f,
        green = (accent.green + 0.5f) / 2f,
        blue = (accent.blue + 0.5f) / 2f,
        alpha = alpha,
    )

val ColorScheme.chatBubbleThem: Color
    get() = if (isLight) LightChatBubbleThem else DarkChatBubbleThem

val ColorScheme.chatDraftBackground: Color
    get() = if (isLight) LightChatDraftBackground else DarkChatDraftBackground

val ColorScheme.subtleButton: Color
    get() = if (isLight) LightSubtleButton else DarkSubtleButton

val ColorScheme.overPictureBackground: Color
    get() = if (isLight) LightOverPictureBackground else DarkOverPictureBackground

val ColorScheme.bitcoinColor: Color
    get() = if (isLight) BitcoinLight else BitcoinDark

val ColorScheme.redColorOnSecondSurface: Color
    get() = if (isLight) LightRedColorOnSecondSurface else DarkRedColorOnSecondSurface

val ColorScheme.warningColor: Color
    get() = if (isLight) LightWarningColor else DarkWarningColor

val ColorScheme.warningColorOnSecondSurface: Color
    get() = if (isLight) LightWarningColorOnSecondSurface else DarkWarningColorOnSecondSurface

val ColorScheme.allGoodColor: Color
    get() = if (isLight) LightAllGoodColor else DarkAllGoodColor

val ColorScheme.fundraiserProgressColor: Color
    get() = if (isLight) LightFundraiserProgressColor else DarkFundraiserProgressColor

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.imageModifier: Modifier
    get() = if (isLight) LightImageModifier else DarkImageModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.videoGalleryModifier: Modifier
    get() = if (isLight) LightVideoModifier else DarkVideoModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.profile35dpModifier: Modifier
    get() = if (isLight) LightProfile35dpModifier else DarkProfile35dpModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.replyModifier: Modifier
    get() = if (isLight) LightReplyBorderModifier else DarkReplyBorderModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.innerPostModifier: Modifier
    get() = if (isLight) LightInnerPostBorderModifier else DarkInnerPostBorderModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.maxWidthWithBackground: Modifier
    get() = if (isLight) LightMaxWidthWithBackground else DarkMaxWidthWithBackground

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.channelNotePictureModifier: Modifier
    get() = if (isLight) LightChannelNotePictureModifier else DarkChannelNotePictureModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.userProfileBorderModifier: Modifier
    get() = if (isLight) LightProfilePictureBorder else DarkProfilePictureBorder

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.relayIconModifier: Modifier
    get() = if (isLight) LightRelayIconModifier else DarkRelayIconModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.selectedReactionBoxModifier: Modifier
    get() = SelectedReactionBoxOuterModifier.background(secondaryContainer).padding(5.dp)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.largeProfilePictureModifier: Modifier
    get() = if (isLight) lightLargeProfilePictureModifier else darkLargeProfilePictureModifier

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.newItemBubbleModifier: Modifier
    get() = NewItemBubbleShapeModifier.background(primary)

@Suppress("ModifierFactoryExtensionFunction")
val ColorScheme.blackTagModifier: Modifier
    get() = if (isLight) lightBlackTagModifier else darkBlackTagModifier
