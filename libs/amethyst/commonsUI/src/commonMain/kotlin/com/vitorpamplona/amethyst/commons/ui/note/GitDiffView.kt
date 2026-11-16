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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.ui.CodeHighlighter
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_diff_binary
import com.vitorpamplona.amethyst.commons.resources.git_diff_files_changed
import com.vitorpamplona.amethyst.commons.resources.git_diff_split
import com.vitorpamplona.amethyst.commons.resources.git_diff_unified
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.quartz.nip34Git.patch.CharSpan
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffFile
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLine
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLineType
import com.vitorpamplona.quartz.nip34Git.patch.GitFileChange
import com.vitorpamplona.quartz.nip34Git.patch.IntralineDiff
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import dev.snipme.highlights.model.SyntaxLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val AddColor = Color(0xFF1F883D)
private val DeleteColor = Color(0xFFCF222E)
private val DiffFontSize = 12.sp
private val DiffLineHeight = 17.sp

// A file with more diff lines than this is shown without syntax highlighting: highlighting runs
// in the background as a file opens, and only opened files pay for it.
private const val FILE_HIGHLIGHT_LIMIT = 1500

// Highlighted lines across a whole diff: a change of many small files (each opened at once) still
// highlights its first files, and draws the rest plain rather than tokenising thousands of lines
// before any of them shows.
private const val DIFF_HIGHLIGHT_BUDGET = 3000

/**
 * Renders a parsed [ParsedPatch] as a GitHub-style file-by-file diff: a stat
 * summary, then one collapsible card per file with +/- line coloring, old/new
 * line numbers, and (for reasonably sized diffs) syntax highlighting reused from
 * the repository code browser.
 */
@Composable
fun GitDiffView(
    parsed: ParsedPatch,
    modifier: Modifier = Modifier,
    /** Tapping a line: to comment on it (a review). Null when lines can't be commented on. */
    onLineClick: ((GitDiffFile, GitDiffLine) -> Unit)? = null,
    /** Drawn under each line: the review comments on it, when there are any. */
    lineAnnotations: (@Composable (GitDiffFile, GitDiffLine) -> Unit)? = null,
    /** Whether [lineAnnotations] draws anything under a line; the code around such a line scrolls on its own. */
    hasAnnotations: (GitDiffFile, GitDiffLine) -> Boolean = { _, _ -> false },
) {
    if (!parsed.hasDiff) return
    // Side by side needs the width of a large window; a phone keeps the unified view.
    val wide = LocalScreenLayout.current.isLargeScreen
    var split by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { GitDiffStatSummary(parsed) }
            if (wide) {
                FilterChip(selected = !split, onClick = { split = false }, label = { Text(stringResource(Res.string.git_diff_unified)) })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = split, onClick = { split = true }, label = { Text(stringResource(Res.string.git_diff_split)) })
            }
        }
        val highlighted =
            remember(parsed) {
                var left = DIFF_HIGHLIGHT_BUDGET
                parsed.files.map { file ->
                    val size = file.hunks.sumOf { it.lines.size }
                    when {
                        size > FILE_HIGHLIGHT_LIMIT -> false
                        // A folded file pays only when the user opens it, one at a time: it costs the budget nothing.
                        size > FOLD_LINES -> true
                        size <= left -> {
                            left -= size
                            true
                        }
                        else -> false
                    }
                }
            }
        parsed.files.forEachIndexed { index, file ->
            DiffFileCard(file, split && wide, highlighted[index], onLineClick, lineAnnotations, hasAnnotations)
        }
    }
}

/** "3 files changed +12 -4", the line that heads a diff and stands for it where the diff isn't drawn. */
@Composable
fun GitDiffStatSummary(parsed: ParsedPatch) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val fileCount = parsed.files.size
        Text(
            text = pluralStringResource(Res.plurals.git_diff_files_changed, fileCount, fileCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text("+${parsed.totalAdditions}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = AddColor)
        Text("-${parsed.totalDeletions}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = DeleteColor)
    }
}

/** Files with more diff lines than this start folded. */
private const val FOLD_LINES = 200

@Composable
private fun DiffFileCard(
    file: GitDiffFile,
    split: Boolean,
    highlightEnabled: Boolean,
    onLineClick: ((GitDiffFile, GitDiffLine) -> Unit)?,
    lineAnnotations: (@Composable (GitDiffFile, GitDiffLine) -> Unit)?,
    hasAnnotations: (GitDiffFile, GitDiffLine) -> Boolean,
) {
    // A long file starts folded, so one generated lockfile doesn't bury the rest of the change.
    var expanded by rememberSaveable(file.displayPath) { mutableStateOf(file.hunks.sumOf { it.lines.size } <= FOLD_LINES) }
    val language = remember(file.displayPath) { CodeHighlighter.languageForFile(file.displayPath) }
    // The app's theme, not the system's: they differ when the user picks one in Settings, and
    // the highlighter paints plain code and punctuation for the background it is told about.
    val darkMode = !MaterialTheme.colorScheme.isLight
    // Tokenised off the main thread once the file is open; until then its lines draw plain.
    val highlights by produceState(emptyMap<String, AnnotatedString>(), file, language, darkMode, expanded, highlightEnabled) {
        if (expanded && highlightEnabled && language != null) {
            value = withContext(Dispatchers.Default) { highlightLines(file, language, darkMode) }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .gitCard(),
    ) {
        // File header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (symbol, tint) = changeBadge(file.change)
            Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
            Text(
                text = file.displayPath,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (file.additions > 0) Text("+${file.additions}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = AddColor)
            if (file.deletions > 0) Text("-${file.deletions}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = DeleteColor)
            Icon(
                symbol = if (expanded) MaterialSymbols.KeyboardArrowUp else MaterialSymbols.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            if (file.isBinary) {
                Text(
                    text = stringResource(Res.string.git_diff_binary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else if (split) {
                val gutterWidth = remember(file) { gutterWidthFor(file) }
                Column(Modifier.fillMaxWidth()) {
                    file.hunks.forEach { hunk ->
                        HunkHeaderRow(hunk.header)
                        val emphasis = remember(hunk) { IntralineDiff.emphasis(hunk.lines) }
                        val rows = remember(hunk) { splitRows(hunk.lines) }
                        rows.forEach { (leftIndex, rightIndex) ->
                            val left = leftIndex?.let { hunk.lines[it] }
                            val right = rightIndex?.let { hunk.lines[it] }
                            // As tall as the side that wraps more, so both sides' colours fill the row.
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                SplitCell(file, left, true, leftIndex?.let { emphasis[it] }, gutterWidth, highlights, onLineClick, Modifier.weight(1f))
                                SplitCell(file, right, false, rightIndex?.let { emphasis[it] }, gutterWidth, highlights, onLineClick, Modifier.weight(1f))
                            }
                            // Comments on either side, under the pair.
                            lineAnnotations?.let { annotate ->
                                left?.let { annotate(file, it) }
                                right?.takeIf { it !== left }?.let { annotate(file, it) }
                            }
                        }
                    }
                }
            } else {
                val gutterWidth = remember(file) { gutterWidthFor(file) }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = maxWidth
                    Column(Modifier.fillMaxWidth()) {
                        file.hunks.forEachIndexed { hunkIndex, hunk ->
                            HunkHeaderRow(hunk.header)
                            val emphasis = remember(hunk) { IntralineDiff.emphasis(hunk.lines) }
                            // The code scrolls sideways; the comments under a line don't, so they wrap to
                            // the card's width. Each run of lines up to a commented one is its own block.
                            val annotated = lineAnnotations != null
                            val runs = remember(hunk, annotated, hasAnnotations) { hunkRuns(hunk.lines) { annotated && hasAnnotations(file, it) } }
                            runs.forEach { run ->
                                key(hunkIndex, run.first) {
                                    // At least the card's width and as wide as its longest line, with
                                    // every row filling it, so a row's colour spans the card.
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .widthIn(min = cardWidth)
                                            .width(IntrinsicSize.Max),
                                    ) {
                                        for (index in run) {
                                            val line = hunk.lines[index]
                                            DiffLineRow(
                                                line,
                                                gutterWidth,
                                                highlights[line.content],
                                                emphasis[index],
                                                onClick = onLineClick?.let { { it(file, line) } },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                    val last = hunk.lines[run.last]
                                    if (lineAnnotations != null && hasAnnotations(file, last)) lineAnnotations(file, last)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HunkHeaderRow(header: String) {
    Text(
        text = header,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontSize = DiffFontSize,
        lineHeight = DiffLineHeight,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        maxLines = 1,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun DiffLineRow(
    line: GitDiffLine,
    gutterWidth: Dp,
    /** The line's syntax colours, or null to draw it plain. */
    highlighted: AnnotatedString?,
    emphasis: CharSpan?,
    onClick: (() -> Unit)? = null,
    showOld: Boolean = true,
    showNew: Boolean = true,
    wrap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val background =
        when (line.type) {
            GitDiffLineType.ADD -> AddColor.copy(alpha = 0.12f)
            GitDiffLineType.DELETE -> DeleteColor.copy(alpha = 0.12f)
            GitDiffLineType.CONTEXT -> Color.Transparent
        }
    val emphasisColor =
        when (line.type) {
            GitDiffLineType.ADD -> AddColor.copy(alpha = 0.30f)
            GitDiffLineType.DELETE -> DeleteColor.copy(alpha = 0.30f)
            GitDiffLineType.CONTEXT -> Color.Transparent
        }
    val marker =
        when (line.type) {
            GitDiffLineType.ADD -> "+"
            GitDiffLineType.DELETE -> "-"
            GitDiffLineType.CONTEXT -> " "
        }
    val markerColor =
        when (line.type) {
            GitDiffLineType.ADD -> AddColor
            GitDiffLineType.DELETE -> DeleteColor
            GitDiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        }
    val numberColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    val content =
        remember(line.content, highlighted, emphasis) {
            val base = highlighted ?: AnnotatedString(line.content)
            if (emphasis != null && !emphasis.isEmpty) {
                buildAnnotatedString {
                    append(base)
                    addStyle(
                        SpanStyle(background = emphasisColor),
                        emphasis.start.coerceIn(0, line.content.length),
                        emphasis.end.coerceIn(0, line.content.length),
                    )
                }
            } else {
                base
            }
        }

    Row(
        modifier =
            modifier
                .background(background)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.Top,
    ) {
        if (showOld) LineNumber(line.oldNumber, gutterWidth, numberColor)
        if (showNew) LineNumber(line.newNumber, gutterWidth, numberColor)
        Text(
            text = marker,
            fontFamily = FontFamily.Monospace,
            fontSize = DiffFontSize,
            lineHeight = DiffLineHeight,
            color = markerColor,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = DiffFontSize,
            lineHeight = DiffLineHeight,
            softWrap = wrap,
            maxLines = if (wrap) Int.MAX_VALUE else 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (wrap) Modifier.weight(1f).padding(start = 4.dp, end = 8.dp) else Modifier.padding(start = 4.dp, end = 12.dp),
        )
    }
}

/** [lines]' indices cut after each line that [breaksAfter] (one with comments under it), in order. */
private fun hunkRuns(
    lines: List<GitDiffLine>,
    breaksAfter: (GitDiffLine) -> Boolean,
): List<IntRange> {
    if (lines.isEmpty()) return emptyList()
    val runs = ArrayList<IntRange>()
    var start = 0
    lines.forEachIndexed { index, line ->
        if (breaksAfter(line) && index < lines.lastIndex) {
            runs.add(start..index)
            start = index + 1
        }
    }
    runs.add(start..lines.lastIndex)
    return runs
}

/**
 * The rows of a side-by-side hunk: an unchanged line on both sides, and each run of removed lines
 * beside the added lines that replace it, padded with blanks where one side is longer.
 */
private fun splitRows(lines: List<GitDiffLine>): List<Pair<Int?, Int?>> {
    // Indices into [lines], so each cell finds its own intraline emphasis without searching.
    val rows = ArrayList<Pair<Int?, Int?>>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.type == GitDiffLineType.CONTEXT) {
            rows.add(i to i)
            i++
            continue
        }
        val deleted = ArrayList<Int>()
        while (i < lines.size && lines[i].type == GitDiffLineType.DELETE) deleted.add(i++)
        val added = ArrayList<Int>()
        while (i < lines.size && lines[i].type == GitDiffLineType.ADD) added.add(i++)
        for (k in 0 until maxOf(deleted.size, added.size)) rows.add(deleted.getOrNull(k) to added.getOrNull(k))
    }
    return rows
}

/** Each distinct line of [file], coloured for [language]: one pass, on a background thread. */
private fun highlightLines(
    file: GitDiffFile,
    language: SyntaxLanguage,
    darkMode: Boolean,
): Map<String, AnnotatedString> {
    val out = HashMap<String, AnnotatedString>()
    file.hunks.forEach { hunk ->
        hunk.lines.forEach { line ->
            if (line.content.isNotEmpty() && line.content !in out) out[line.content] = CodeHighlighter.highlight(line.content, language, darkMode)
        }
    }
    return out
}

/** One side of a side-by-side row: the old side shows old numbers, the new side new ones. */
@Composable
private fun SplitCell(
    file: GitDiffFile,
    line: GitDiffLine?,
    oldSide: Boolean,
    emphasis: CharSpan?,
    gutterWidth: Dp,
    highlights: Map<String, AnnotatedString>,
    onLineClick: ((GitDiffFile, GitDiffLine) -> Unit)?,
    modifier: Modifier,
) {
    if (line == null) {
        Box(modifier.fillMaxHeight().heightIn(min = with(LocalDensity.current) { DiffLineHeight.toDp() }).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)))
        return
    }
    // An unchanged line is drawn on both sides; each shows its own number. Half a window is narrow,
    // so long lines wrap instead of running out of sight.
    DiffLineRow(
        line,
        gutterWidth,
        highlights[line.content],
        emphasis,
        onClick = onLineClick?.let { { it(file, line) } },
        showOld = oldSide,
        showNew = !oldSide,
        wrap = true,
        modifier = modifier.fillMaxHeight().clipToBounds(),
    )
}

@Composable
private fun LineNumber(
    number: Int?,
    width: Dp,
    color: Color,
) {
    Text(
        text = number?.toString() ?: "",
        fontFamily = FontFamily.Monospace,
        fontSize = DiffFontSize,
        lineHeight = DiffLineHeight,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
    )
}

@Composable
private fun changeBadge(change: GitFileChange): Pair<MaterialSymbol, Color> =
    when (change) {
        GitFileChange.ADD -> MaterialSymbols.AddCircle to AddColor
        GitFileChange.DELETE -> MaterialSymbols.Cancel to DeleteColor
        GitFileChange.RENAME -> MaterialSymbols.AltRoute to MaterialTheme.colorScheme.primary
        GitFileChange.MODIFY -> MaterialSymbols.Edit to MaterialTheme.colorScheme.primary
    }

private fun gutterWidthFor(file: GitDiffFile): Dp {
    val maxLine =
        file.hunks.maxOfOrNull { hunk ->
            hunk.lines.maxOfOrNull { maxOf(it.oldNumber ?: 0, it.newNumber ?: 0) } ?: 0
        } ?: 0
    val digits = maxLine.toString().length.coerceAtLeast(2)
    return (digits * 8 + 8).dp
}
