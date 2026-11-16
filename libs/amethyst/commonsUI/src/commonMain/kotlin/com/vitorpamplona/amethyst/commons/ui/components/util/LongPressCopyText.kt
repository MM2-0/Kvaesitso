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
package com.vitorpamplona.amethyst.commons.ui.components.util

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Text composable that opens [onClick] on a tap and copies [copyValue] to the
 * system clipboard on a long-press (with a short notice confirming it).
 *
 * This is the long-press-to-copy counterpart of [com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary].
 * It deliberately uses a plain [Text] + [combinedClickable] outer modifier
 * rather than an inline `LinkAnnotation.Clickable` inside an `AnnotatedString`,
 * because annotation-level clicks can't see a parent [combinedClickable]'s
 * long-press: the parent's tap area sits above the annotation's hit-test
 * region and would consume the tap before the annotation could fire it.
 *
 * @param displayText  text shown to the user (may be a stripped form, e.g.
 *                     "example.com" for a website value "https://example.com").
 * @param copyValue    raw value placed on the clipboard on long-press
 *                     (typically the full, unmodified profile field).
 * @param onClick      tap action — usually "open the link" or "expand zap UI".
 * @param toastResId   string resource shown as a short notice after the
 *                     value is placed on the clipboard. Defaults to a generic
 *                     "Copied to clipboard" message.
 * @param onLongClickLabelResId  accessibility label exposed to TalkBack for
 *                     the long-press action. Defaults to "Copy to clipboard".
 */
@Composable
fun LongPressCopyText(
    displayText: String,
    copyValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = LocalTextStyle.current,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
    toastResId: StringResource = Res.string.copied_to_clipboard,
    onLongClickLabelResId: StringResource = Res.string.copy_to_clipboard,
) {
    val shortNotice = rememberShortNotice()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val longClickLabel = stringRes(onLongClickLabelResId)

    Text(
        text = displayText,
        color = color,
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        modifier =
            modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    scope.launch {
                        clipboard.setText(copyValue)
                        shortNotice.show(loadStringRes(toastResId))
                    }
                },
                onLongClickLabel = longClickLabel,
            ),
    )
}
