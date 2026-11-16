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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.nip34Git.GitRepositoryBrowserViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.ui.CodeHighlighter
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitLoadingBox
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitMessageBox
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.git_file_blame_truncated
import com.vitorpamplona.amethyst.commons.resources.git_repo_binary_file
import com.vitorpamplona.amethyst.commons.resources.git_repo_code_loading
import com.vitorpamplona.amethyst.commons.resources.git_repo_copy_file
import com.vitorpamplona.amethyst.commons.resources.git_repo_file_load_error
import com.vitorpamplona.amethyst.commons.resources.git_repo_plain_text
import com.vitorpamplona.amethyst.commons.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.git.GitBlame
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.git.GitTreeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

private val CodeFontSize = 13.sp
private val CodeLineHeight = 20.sp

/**
 * Renders a single file from the repository. Markdown files render as rich text,
 * text files render with a line-number gutter and syntax highlighting, and binary
 * files show a notice.
 */
@Composable
fun GitFileViewer(
    snapshot: GitRepoSnapshot,
    viewModel: GitRepositoryBrowserViewModel,
    entry: GitTreeEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    /** The file's path when its blame should show beside the code, else null. */
    blamePath: List<String>? = null,
) {
    // Null while loading; then the blame, or a failure (also when the file has no history here),
    // so the gutter's progress bar stops either way.
    val blameResult by
        produceState<Result<GitBlame>?>(null, entry.oid, blamePath) {
            value = null
            val path = blamePath ?: return@produceState
            value =
                try {
                    viewModel.blame(snapshot, path)?.let { Result.success(it) } ?: Result.failure(IllegalStateException("no history"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
        }
    val blame = blameResult?.getOrNull()
    val result by
        produceState<Result<ByteArray>?>(null, entry.oid) {
            value =
                try {
                    Result.success(viewModel.readBlob(snapshot, entry.oid))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
        }

    val bytes = result
    when {
        bytes == null -> GitLoadingBox(stringRes(Res.string.git_repo_code_loading), modifier)
        bytes.isFailure -> GitMessageBox(MaterialSymbols.ErrorOutline, stringRes(Res.string.git_repo_file_load_error), modifier)
        else -> {
            val data = bytes.getOrThrow()
            when {
                isMarkdownFile(entry.name) ->
                    MarkdownFile(data.decodeToString(), accountViewModel, nav, modifier)
                isImageFile(entry.name) ->
                    ImageFile(data, entry.name, modifier)
                isProbablyBinary(data) ->
                    GitMessageBox(
                        symbol = MaterialSymbols.Description,
                        text = stringRes(Res.string.git_repo_binary_file, humanSize(data.size)),
                        modifier = modifier,
                    )
                else ->
                    HighlightedCode(data.decodeToString(), entry.name, modifier, blame.takeIf { blamePath != null }, loadingBlame = blamePath != null && blameResult == null)
            }
        }
    }
}

@Composable
private fun MarkdownFile(
    content: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf(background) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        RichTextViewer(
            content = content,
            canPreview = true,
            quotesLeft = 1,
            modifier = Modifier.fillMaxWidth(),
            tags = EmptyTagList,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun HighlightedCode(
    code: String,
    fileName: String,
    modifier: Modifier,
    blame: GitBlame? = null,
    loadingBlame: Boolean = false,
) {
    // The app's theme, not the system's: they differ when the user picks one in Settings, and
    // the highlighter paints plain code and punctuation for the background it is told about.
    val darkMode = !MaterialTheme.colorScheme.isLight
    val language = remember(fileName) { CodeHighlighter.languageForFile(fileName) }

    val annotated by
        produceState(AnnotatedString(code), code, fileName, darkMode) {
            value =
                withContext(Dispatchers.Default) {
                    if (language == null) AnnotatedString(code) else CodeHighlighter.highlight(code, language, darkMode)
                }
        }

    Column(modifier.fillMaxSize()) {
        CodeBar(languageLabel = language?.name?.let(::prettyLanguage) ?: stringRes(Res.string.git_repo_plain_text), code = code)
        HorizontalDivider(thickness = 0.5.dp)
        if (loadingBlame) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (blame?.truncated == true) {
            Text(
                pluralStringRes(Res.plurals.git_file_blame_truncated, blame.versions.size, blame.versions.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
        }
        CodeWithLineNumbers(annotated, Modifier.fillMaxSize(), blame)
    }
}

@Composable
private fun CodeBar(
    languageLabel: String,
    code: String,
) {
    val clipboard = LocalClipboard.current
    val shortNotice = rememberShortNotice()
    val scope = rememberCoroutineScope()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = languageLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = {
                scope.launch {
                    clipboard.setText(code)
                    shortNotice.show(loadStringRes(Res.string.copied_to_clipboard))
                }
            },
        ) {
            Icon(
                symbol = MaterialSymbols.ContentCopy,
                contentDescription = stringRes(Res.string.git_repo_copy_file),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeWithLineNumbers(
    annotated: AnnotatedString,
    modifier: Modifier,
    blame: GitBlame? = null,
) {
    val lineRanges = remember(annotated) { computeLineRanges(annotated.text) }
    val gutterDigits = lineRanges.size.toString().length
    val gutterWidth = (gutterDigits * 9 + 20).dp

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val gutterColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    Row(modifier.verticalScroll(verticalScroll)) {
        if (blame != null) BlameGutter(blame, lineRanges.size, gutterColor)
        // Sticky line-number gutter (only the code area scrolls horizontally).
        Column(
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .width(gutterWidth)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.End,
        ) {
            for (i in lineRanges.indices) {
                Text(
                    text = (i + 1).toString(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = CodeFontSize,
                    lineHeight = CodeLineHeight,
                    color = gutterColor,
                    textAlign = TextAlign.End,
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .horizontalScroll(horizontalScroll)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            for ((start, end) in lineRanges) {
                Text(
                    text = annotated.subSequence(start, end),
                    fontFamily = FontFamily.Monospace,
                    fontSize = CodeFontSize,
                    lineHeight = CodeLineHeight,
                    softWrap = false,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

/**
 * Who last changed each line: the commit and author beside the first line of each run a commit
 * wrote, shaded per run so a change reads as one block, as `git blame` views show it.
 */
@Composable
private fun BlameGutter(
    blame: GitBlame,
    lineCount: Int,
    textColor: Color,
) {
    val shade = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    Column(
        modifier =
            Modifier
                .width(BlameGutterWidth)
                .padding(vertical = 8.dp),
    ) {
        var band = false
        for (i in 0 until lineCount) {
            val owner = blame.lines.getOrNull(i) ?: -1
            val startsRun = i == 0 || owner != blame.lines.getOrNull(i - 1)
            if (startsRun) band = !band
            val version = blame.versions.getOrNull(owner)
            Text(
                text = if (startsRun && version != null) version.commit.shortOid + " " + version.commit.authorName else "",
                fontFamily = FontFamily.Monospace,
                fontSize = CodeFontSize,
                lineHeight = CodeLineHeight,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(if (band) shade else Color.Transparent)
                        .padding(horizontal = 6.dp),
            )
        }
    }
}

private val BlameGutterWidth = 170.dp

/** Splits text into per-line character ranges, dropping a single trailing newline's empty line. */
private fun computeLineRanges(text: String): List<Pair<Int, Int>> {
    val ranges = ArrayList<Pair<Int, Int>>()
    var lineStart = 0
    var i = 0
    while (i < text.length) {
        if (text[i] == '\n') {
            ranges.add(lineStart to i)
            lineStart = i + 1
        }
        i++
    }
    ranges.add(lineStart to text.length)
    if (ranges.size > 1 && ranges.last().let { it.first == it.second }) {
        ranges.removeAt(ranges.size - 1)
    }
    return ranges
}

private fun prettyLanguage(enumName: String): String =
    when (enumName) {
        "CPP" -> "C++"
        "CSHARP" -> "C#"
        "JAVASCRIPT" -> "JavaScript"
        "TYPESCRIPT" -> "TypeScript"
        "COFFEESCRIPT" -> "CoffeeScript"
        "PHP" -> "PHP"
        else -> enumName.lowercase().replaceFirstChar { it.uppercase() }
    }

@Composable
private fun ImageFile(
    bytes: ByteArray,
    name: String,
    modifier: Modifier,
) {
    val context = LocalPlatformContext.current
    val request = remember(bytes) { ImageRequest.Builder(context).data(bytes).build() }
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AsyncImage(
            model = request,
            contentDescription = name,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun isMarkdownFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext == "md" || ext == "markdown" || ext == "mdown" || ext == "mkd"
}

private fun isImageFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "gif" || ext == "webp" || ext == "bmp"
}

/** Heuristic: a NUL byte in the first chunk means the content isn't text. */
private fun isProbablyBinary(data: ByteArray): Boolean {
    val limit = minOf(data.size, 8000)
    for (i in 0 until limit) if (data[i].toInt() == 0) return true
    return false
}

private fun humanSize(bytes: Int): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
