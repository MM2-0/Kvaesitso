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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.follow_set_creation_action_btn_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_creation_desc_label
import com.vitorpamplona.amethyst.commons.resources.follow_set_creation_dialog_title
import com.vitorpamplona.amethyst.commons.resources.follow_set_creation_name_label
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedTextField
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import org.jetbrains.compose.resources.StringResource

@Composable
fun NewPeopleListCreationDialog(
    title: StringResource = Res.string.follow_set_creation_dialog_title,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onCreateList: (name: String, description: String?) -> Unit,
) {
    val newListName = remember { mutableStateOf("") }
    val newListDescription = remember { mutableStateOf<String?>(null) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringRes(title),
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // For the new list name
                TextField(
                    value = newListName.value,
                    onValueChange = { newListName.value = it },
                    label = {
                        Text(text = stringRes(Res.string.follow_set_creation_name_label))
                    },
                )
                Spacer(modifier = DoubleVertSpacer)
                // For the set description
                SpellCheckedTextField(
                    value =
                        (
                            if (newListDescription.value != null) newListDescription.value else ""
                        ).toString(),
                    onValueChange = { newListDescription.value = it },
                    label = {
                        Text(text = stringRes(Res.string.follow_set_creation_desc_label))
                    },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateList(newListName.value, newListDescription.value)
                    onDismiss()
                },
            ) {
                Text(stringRes(Res.string.follow_set_creation_action_btn_label))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
            ) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}
