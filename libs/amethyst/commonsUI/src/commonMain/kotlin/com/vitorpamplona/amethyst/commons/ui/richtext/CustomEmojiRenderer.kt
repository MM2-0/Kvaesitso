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
package com.vitorpamplona.amethyst.commons.ui.richtext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * Inline custom emoji: renders [text], replacing each `:shortcode:` present in [emojis] with its
 * image. Falls back to plain [Text] when the text carries no known emoji. Same path as
 * [CreateTextWithEmoji], so rich text and names resolve emojis identically.
 */
@Composable
fun RenderCustomEmoji(
    text: String,
    emojis: ImmutableMap<String, String>,
    modifier: Modifier = Modifier,
) = CreateTextWithEmoji(text = text, emojis = emojis, modifier = modifier)

/**
 * Renders an already-assembled list of [CustomEmoji.Renderable]s: text spans as
 * text, image spans as inline [AsyncImage]s sized to ~1.1x the current font.
 */
@Composable
fun InLineIconRenderer(
    wordsInOrder: ImmutableList<CustomEmoji.Renderable>,
    style: SpanStyle,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val placeholderSize = remember(fontSize) { emojiPlaceholderSize(fontSize) }

    // Remembered like annotatedText below: rebuilding this map handed Text a
    // fresh unstable Map (plus a Placeholder and lambda per emoji) on every
    // recomposition of every name/note with a custom emoji.
    val inlineContent =
        remember(wordsInOrder, placeholderSize) {
            wordsInOrder.buildInlineContent(placeholderSize, imagePadding = 0.dp)
        }

    val annotatedText =
        remember(wordsInOrder, style) {
            buildAnnotatedString {
                wordsInOrder.forEachIndexed { idx, value ->
                    withStyle(style) {
                        when (value) {
                            is CustomEmoji.TextType -> append(value.text)
                            is CustomEmoji.ImageUrlType -> appendInlineContent("inlineContent$idx", "[icon]")
                            else -> {}
                        }
                    }
                }
            }
        }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        fontSize = fontSize,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

private fun emojiPlaceholderSize(fontSize: TextUnit): TextUnit = if (fontSize == TextUnit.Unspecified) 22.sp else fontSize.times(1.1f)

private fun ImmutableList<CustomEmoji.Renderable>.buildInlineContent(
    placeholderSize: TextUnit,
    imagePadding: Dp,
): Map<String, InlineTextContent> =
    mapIndexedNotNull { idx, value ->
        if (value is CustomEmoji.ImageUrlType) {
            "inlineContent$idx" to
                InlineTextContent(
                    Placeholder(
                        width = placeholderSize,
                        height = placeholderSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    AsyncImage(
                        model = value.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(imagePadding),
                    )
                }
        } else {
            null
        }
    }.associate { it.first to it.second }

/**
 * Calls [onEmojiText] with [text] split into text and emoji spans when it resolves any custom emoji
 * from the event's [tags], and [onRegularText] otherwise (and while the split is being assembled).
 */
@Composable
fun CustomEmojiChecker(
    text: String,
    tags: ImmutableListOfLists<String>?,
    onRegularText: @Composable (String) -> Unit,
    onEmojiText: @Composable (ImmutableList<CustomEmoji.Renderable>) -> Unit,
) = EmojiChecker(
    text = text,
    source = tags,
    mightContainEmoji = { CustomEmoji.fastMightContainEmoji(text, tags?.lists) },
    assemble = { CustomEmoji.assembleAnnotatedList(text, tags?.lists) },
    onRegularText = onRegularText,
    onEmojiText = onEmojiText,
)

/** [CustomEmojiChecker] for an already-resolved `shortcode -> url` map. */
@Composable
fun CustomEmojiChecker(
    text: String,
    emojis: ImmutableMap<String, String>,
    onRegularText: @Composable (String) -> Unit,
    onEmojiText: @Composable (ImmutableList<CustomEmoji.Renderable>) -> Unit,
) = EmojiChecker(
    text = text,
    source = emojis,
    mightContainEmoji = { CustomEmoji.fastMightContainEmoji(text, emojis) },
    assemble = { CustomEmoji.assembleAnnotatedList(text, emojis) },
    onRegularText = onRegularText,
    onEmojiText = onEmojiText,
)

/**
 * The one implementation behind both [CustomEmojiChecker]s. [source] is whatever the emoji come
 * from (tags or a map); it keys the cached answers together with [text].
 */
@Composable
private fun EmojiChecker(
    text: String,
    source: Any?,
    mightContainEmoji: () -> Boolean,
    assemble: () -> ImmutableList<CustomEmoji.Renderable>?,
    onRegularText: @Composable (String) -> Unit,
    onEmojiText: @Composable (ImmutableList<CustomEmoji.Renderable>) -> Unit,
) {
    val mayContainEmoji = remember(text, source) { mightContainEmoji() }

    if (mayContainEmoji) {
        var emojiList by remember(text, source) { mutableStateOf<ImmutableList<CustomEmoji.Renderable>?>(null) }

        LaunchedEffect(text, source) {
            val newEmojiList = assemble()
            if (newEmojiList != null) {
                emojiList = newEmojiList
            }
        }

        emojiList?.let { onEmojiText(it) } ?: onRegularText(text)
    } else {
        onRegularText(text)
    }
}

/** [text] with its custom emojis, resolved from the event's [tags]. */
@Composable
fun CreateTextWithEmoji(
    text: String,
    tags: ImmutableListOfLists<String>?,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) = CustomEmojiChecker(
    text = text,
    tags = tags,
    onRegularText = { PlainText(it, color, textAlign, fontWeight, fontSize, maxLines, overflow, modifier) },
    onEmojiText = { EmojiText(it, color, textAlign, fontWeight, fontSize, maxLines, overflow, modifier) },
)

/** [text] with its custom emojis, resolved from an already-built `shortcode -> url` map. */
@Composable
fun CreateTextWithEmoji(
    text: String,
    emojis: ImmutableMap<String, String>,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier,
) = CustomEmojiChecker(
    text = text,
    emojis = emojis,
    onRegularText = { PlainText(it, color, textAlign, fontWeight, fontSize, maxLines, overflow, modifier) },
    onEmojiText = { EmojiText(it, color, textAlign, fontWeight, fontSize, maxLines, overflow, modifier) },
)

@Composable
private fun resolveTextColor(color: Color): Color = color.takeOrElse { LocalTextStyle.current.color.takeOrElse { LocalContentColor.current } }

@Composable
private fun PlainText(
    text: String,
    color: Color,
    textAlign: TextAlign?,
    fontWeight: FontWeight?,
    fontSize: TextUnit,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier,
) {
    Text(
        text = text,
        color = resolveTextColor(color),
        textAlign = textAlign,
        fontWeight = fontWeight,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

@Composable
private fun EmojiText(
    words: ImmutableList<CustomEmoji.Renderable>,
    color: Color,
    textAlign: TextAlign?,
    fontWeight: FontWeight?,
    fontSize: TextUnit,
    maxLines: Int,
    overflow: TextOverflow,
    modifier: Modifier,
) {
    val textColor = resolveTextColor(color)
    val currentStyle = LocalTextStyle.current
    // Keyed on everything merged in: keying on the ambient style alone kept the first color /
    // weight / size when the caller changed them (e.g. a row turning selected).
    val style =
        remember(currentStyle, textColor, fontWeight, fontSize) {
            currentStyle
                .merge(
                    TextStyle(
                        color = textColor,
                        fontWeight = fontWeight,
                        fontSize = fontSize,
                    ),
                ).toSpanStyle()
        }

    InLineIconRenderer(words, style, fontSize, maxLines, overflow, modifier, textAlign)
}

/** A clickable [clickablePart] with its custom emojis resolved from [tags]. */
@Composable
fun CreateClickableTextWithEmoji(
    clickablePart: String,
    maxLines: Int = Int.MAX_VALUE,
    tags: ImmutableListOfLists<String>?,
    style: TextStyle,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    CustomEmojiChecker(
        text = clickablePart,
        tags = tags,
        onRegularText = {
            val linkStyle = remember(style) { TextLinkStyles(style = style.toSpanStyle()) }
            val annotated =
                remember(it, linkStyle) {
                    buildAnnotatedString {
                        withLink(LinkAnnotation.Clickable("me", linkStyle) { currentOnClick() }) {
                            append(it)
                        }
                    }
                }
            Text(text = annotated, style = style, maxLines = maxLines)
        },
        onEmojiText = {
            val spanStyle = remember(style) { style.toSpanStyle() }
            ClickableInLineIconRenderer(it, maxLines, spanStyle, onClick = { currentOnClick() })
        },
    )
}

/**
 * [InLineIconRenderer] where the whole run (text and emoji) is one link that calls [onClick], with
 * an optional non-clickable [suffix].
 */
@Composable
fun ClickableInLineIconRenderer(
    wordsInOrder: ImmutableList<CustomEmoji.Renderable>,
    maxLines: Int = Int.MAX_VALUE,
    style: SpanStyle,
    suffix: String? = null,
    nonClickableStype: SpanStyle? = null,
    onClick: () -> Unit,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val placeholderSize = remember(style.fontSize) { emojiPlaceholderSize(style.fontSize) }

    // Remembered for the same reason as InLineIconRenderer's: this renders every clickable name
    // with emoji in a feed, and rebuilding both on each recomposition allocated a new map,
    // placeholders, lambdas and string every time.
    val inlineContent =
        remember(wordsInOrder, placeholderSize) {
            wordsInOrder.buildInlineContent(placeholderSize, imagePadding = 1.dp)
        }

    val annotatedText =
        remember(wordsInOrder, style, suffix, nonClickableStype) {
            val linkStyles = TextLinkStyles(style)
            buildAnnotatedString {
                wordsInOrder.forEachIndexed { idx, value ->
                    withLink(LinkAnnotation.Clickable("link", linkStyles) { currentOnClick() }) {
                        when (value) {
                            is CustomEmoji.TextType -> append(value.text)
                            is CustomEmoji.ImageUrlType -> appendInlineContent("inlineContent$idx", "[icon]")
                            else -> {}
                        }
                    }
                }

                if (suffix != null && nonClickableStype != null) {
                    withStyle(nonClickableStype) {
                        append(suffix)
                    }
                }
            }
        }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        maxLines = maxLines,
    )
}
