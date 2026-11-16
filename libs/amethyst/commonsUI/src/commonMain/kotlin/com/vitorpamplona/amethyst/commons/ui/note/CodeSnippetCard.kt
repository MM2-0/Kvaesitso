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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.code_snippet_default_name
import com.vitorpamplona.amethyst.commons.resources.code_snippet_deps
import com.vitorpamplona.amethyst.commons.resources.code_snippet_language_snippet
import com.vitorpamplona.amethyst.commons.resources.code_snippet_license
import com.vitorpamplona.amethyst.commons.resources.code_snippet_runtime
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.quartz.nipC0CodeSnippets.CodeSnippetEvent

/** Compact feed preview for a NIP-C0 code snippet: name, language pill, description, first lines. */
@Composable
fun CodeSnippetCard(noteEvent: CodeSnippetEvent) {
    val name =
        noteEvent.snippetName()
            ?: noteEvent.language()?.let { stringRes(Res.string.code_snippet_language_snippet, it) }
            ?: stringRes(Res.string.code_snippet_default_name)
    val language = remember(noteEvent) { noteEvent.language() ?: noteEvent.extension() }
    val description = remember(noteEvent) { noteEvent.snippetDescription() }
    val codePreview =
        remember(noteEvent) {
            noteEvent.content
                .lines()
                .take(5)
                .joinToString("\n")
        }

    Column(MaterialTheme.colorScheme.replyModifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            language?.let {
                Spacer(modifier = StdHorzSpacer)
                Box(
                    modifier =
                        Modifier
                            .clip(QuoteBorder)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        description?.ifBlank { null }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = StdVertSpacer)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(QuoteBorder)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), QuoteBorder)
                    .padding(10.dp),
        ) {
            Text(
                text = codePreview,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Full snippet header shown at the top of a thread: full code plus license/runtime/deps metadata. */
@Composable
fun RenderCodeSnippetHeaderForThread(noteEvent: CodeSnippetEvent) {
    val name =
        noteEvent.snippetName()
            ?: noteEvent.language()?.let { stringRes(Res.string.code_snippet_language_snippet, it) }
            ?: stringRes(Res.string.code_snippet_default_name)
    val language = remember(noteEvent) { noteEvent.language() ?: noteEvent.extension() }
    val description = remember(noteEvent) { noteEvent.snippetDescription() }
    val license = remember(noteEvent) { noteEvent.license() }
    val runtime = remember(noteEvent) { noteEvent.runtime() }
    val deps = remember(noteEvent) { noteEvent.deps() }

    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            language?.let {
                Spacer(modifier = StdHorzSpacer)
                Box(
                    modifier =
                        Modifier
                            .clip(QuoteBorder)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        description?.ifBlank { null }?.let {
            Spacer(modifier = StdVertSpacer)
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = StdVertSpacer)

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(QuoteBorder)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), QuoteBorder)
                    .padding(12.dp),
        ) {
            Text(
                text = noteEvent.content,
                style =
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
            )
        }

        license?.let {
            Spacer(modifier = StdVertSpacer)
            Text(
                text = stringRes(Res.string.code_snippet_license, it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        runtime?.let {
            Text(
                text = stringRes(Res.string.code_snippet_runtime, it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (deps.isNotEmpty()) {
            Text(
                text = stringRes(Res.string.code_snippet_deps, deps.joinToString(", ")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
