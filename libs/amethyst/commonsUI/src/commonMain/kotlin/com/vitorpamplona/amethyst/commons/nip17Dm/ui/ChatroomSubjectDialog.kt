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
package com.vitorpamplona.amethyst.commons.nip17Dm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_subject
import com.vitorpamplona.amethyst.commons.resources.messages_new_message_subject_caption
import com.vitorpamplona.amethyst.commons.resources.messages_new_subject_message
import com.vitorpamplona.amethyst.commons.resources.messages_new_subject_message_placeholder
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.note.buttons.CloseButton
import com.vitorpamplona.amethyst.commons.ui.note.buttons.PostButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

/**
 * Rename a private group chat (NIP-17): edit the subject and write the message that carries the
 * change. [onPost] receives the subject (possibly blank) and the message, then the dialog closes.
 * Posting is disabled while both are blank.
 */
@Composable
fun ChatroomSubjectDialog(
    initialSubject: String,
    onPost: (subject: String, message: String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                dismissOnClickOutside = false,
            ),
    ) {
        Surface {
            val groupName = remember { mutableStateOf(initialSubject) }
            val message = remember { mutableStateOf("") }

            Column(
                modifier =
                    Modifier
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onPress = { onClose() })

                    PostButton(
                        onPost = {
                            onPost(groupName.value, message.value)
                            onClose()
                        },
                        // Nothing to send without a new subject or a message.
                        isActive = groupName.value.isNotBlank() || message.value.isNotBlank(),
                    )
                }

                Spacer(modifier = Modifier.height(15.dp))

                SpellCheckedOutlinedTextField(
                    label = { Text(text = stringRes(Res.string.messages_new_message_subject)) },
                    modifier = Modifier.fillMaxWidth(),
                    value = groupName.value,
                    onValueChange = { groupName.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.messages_new_message_subject_caption),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                )

                Spacer(modifier = Modifier.height(15.dp))

                SpellCheckedOutlinedTextField(
                    label = { Text(text = stringRes(Res.string.messages_new_subject_message)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                    value = message.value,
                    onValueChange = { message.value = it },
                    placeholder = {
                        Text(
                            text = stringRes(Res.string.messages_new_subject_message_placeholder),
                            color = MaterialTheme.colorScheme.placeholderText,
                        )
                    },
                    keyboardOptions =
                        KeyboardOptions.Default.copy(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                    maxLines = 10,
                )
            }
        }
    }
}
