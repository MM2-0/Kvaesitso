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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.checkout.GitCheckoutCommands
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.git_checkout_copy_patch
import com.vitorpamplona.amethyst.commons.resources.git_checkout_open_jetbrains
import com.vitorpamplona.amethyst.commons.resources.git_checkout_open_vscode
import com.vitorpamplona.amethyst.commons.resources.git_checkout_title
import com.vitorpamplona.amethyst.commons.ui.components.CommandLine
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import kotlinx.coroutines.launch

private val CheckoutChipShape = RoundedCornerShape(8.dp)

/**
 * "Check out locally": the commands that bring a pull request (or a patch series) into a reader's
 * own clone, ngit's first and plain git's after, plus the repository opened in an IDE. For a
 * patch, [patch] is its text, offered for copying to `git am`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitLocalCheckoutSection(
    note: Note,
    branchName: String?,
    cloneUrls: List<String>,
    tip: String?,
    patch: String? = null,
) {
    var expanded by remember(note.idHex) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier =
                Modifier
                    .clip(CheckoutChipShape)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(MaterialSymbols.Code, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.grayText)
            Text(stringRes(Res.string.git_checkout_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Icon(
                symbol = if (expanded) MaterialSymbols.KeyboardArrowUp else MaterialSymbols.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )
        }
        if (!expanded) return@Column

        val nevent = remember(note.idHex) { note.toNEvent() }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (patch == null) {
                CommandLine(GitCheckoutCommands.ngitCheckout(nevent))
                val branch = remember(note.idHex, branchName) { GitCheckoutCommands.ngitBranch(note.idHex, branchName) }
                GitCheckoutCommands.gitFetchAndCheckout(cloneUrls.firstOrNull(), tip, branch)?.forEach { CommandLine(it) }
            } else {
                CommandLine(GitCheckoutCommands.ngitApply(nevent))
            }

            val uriHandler = LocalUriHandler.current
            val clipboard = LocalClipboard.current
            val notice = rememberShortNotice()
            val scope = rememberCoroutineScope()
            val cloneUrl = cloneUrls.firstOrNull { it.startsWith("https://") || it.startsWith("http://") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (patch != null) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            clipboard.setText(patch)
                            notice.show(loadStringRes(Res.string.copied_to_clipboard))
                        }
                    }) { Text(stringRes(Res.string.git_checkout_copy_patch)) }
                }
                if (cloneUrl != null) {
                    OutlinedButton(onClick = { runCatching { uriHandler.openUri(GitCheckoutCommands.vsCodeClone(cloneUrl)) } }) {
                        Text(stringRes(Res.string.git_checkout_open_vscode))
                    }
                    OutlinedButton(onClick = { runCatching { uriHandler.openUri(GitCheckoutCommands.jetBrainsClone(cloneUrl)) } }) {
                        Text(stringRes(Res.string.git_checkout_open_jetbrains))
                    }
                }
            }
        }
    }
}
