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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import kotlinx.coroutines.launch

private val InsetShape = RoundedCornerShape(12.dp)
private val Mono = TextStyle(fontFamily = FontFamily.Monospace)

/**
 * A one-line shell command in a terminal-like inset, with a button that copies it. [display]
 * can shorten what is shown (an npub, say) so the end of the command stays visible; the copy
 * is always the full [command].
 */
@Composable
fun CommandLine(
    command: String,
    modifier: Modifier = Modifier,
    display: String = command,
) {
    val clipboard = LocalClipboard.current
    val notice = rememberShortNotice()
    val scope = rememberCoroutineScope()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(InsetShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                .padding(start = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$ ",
            style = MaterialTheme.typography.bodySmall.merge(Mono),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        Text(
            text = display,
            style = MaterialTheme.typography.bodySmall.merge(Mono),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )
        IconButton(
            modifier = Modifier.align(Alignment.CenterVertically),
            onClick = {
                scope.launch {
                    clipboard.setText(command)
                    notice.show(loadStringRes(Res.string.copied_to_clipboard))
                }
            },
        ) {
            Icon(
                MaterialSymbols.ContentCopy,
                contentDescription = stringRes(Res.string.copy_to_clipboard),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}
