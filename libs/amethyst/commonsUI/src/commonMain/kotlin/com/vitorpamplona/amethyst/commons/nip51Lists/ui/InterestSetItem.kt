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
package com.vitorpamplona.amethyst.commons.nip51Lists.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestSets.InterestSet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.interest_set_actions_dialog_title
import com.vitorpamplona.amethyst.commons.resources.interest_set_clone
import com.vitorpamplona.amethyst.commons.resources.interest_set_hashtag_count
import com.vitorpamplona.amethyst.commons.resources.interest_set_rename
import com.vitorpamplona.amethyst.commons.resources.quick_action_delete
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBox
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionDialog
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.commons.ui.components.M3ActionSection
import com.vitorpamplona.amethyst.commons.ui.note.VerticalDotsIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.NoSoTinyBorders
import com.vitorpamplona.amethyst.commons.ui.theme.Size40Modifier

@Composable
fun InterestSetItem(
    modifier: Modifier = Modifier,
    interestSet: InterestSet,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ListItem(
                headlineContent = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(interestSet.title, maxLines = 1, overflow = TextOverflow.Ellipsis)

                        Column(
                            modifier = NoSoTinyBorders,
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.End,
                        ) {
                            InterestSetOptionsButton(
                                onRename = onRename,
                                onClone = onClone,
                                onDelete = onDelete,
                            )
                        }
                    }
                },
                supportingContent = {
                    Text(
                        text = stringRes(Res.string.interest_set_hashtag_count, interestSet.allHashtags.size),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                },
                leadingContent = {
                    Icon(
                        symbol = MaterialSymbols.Tag,
                        contentDescription = null,
                        modifier = Size40Modifier,
                    )
                },
            )
        }
    }
}

@Composable
private fun InterestSetOptionsButton(
    onRename: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
) {
    val isMenuOpen = remember { mutableStateOf(false) }

    ClickableBox(
        onClick = { isMenuOpen.value = true },
    ) {
        VerticalDotsIcon()
    }

    if (isMenuOpen.value) {
        M3ActionDialog(
            title = stringRes(Res.string.interest_set_actions_dialog_title),
            onDismiss = { isMenuOpen.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Edit, text = stringRes(Res.string.interest_set_rename)) {
                    onRename()
                    isMenuOpen.value = false
                }
                M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(Res.string.interest_set_clone)) {
                    onClone()
                    isMenuOpen.value = false
                }
            }
            M3ActionSection {
                M3ActionRow(icon = MaterialSymbols.Delete, text = stringRes(Res.string.quick_action_delete), isDestructive = true) {
                    onDelete()
                    isMenuOpen.value = false
                }
            }
        }
    }
}
