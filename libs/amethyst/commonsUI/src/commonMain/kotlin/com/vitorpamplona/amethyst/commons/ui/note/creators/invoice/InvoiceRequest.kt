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
package com.vitorpamplona.amethyst.commons.ui.note.creators.invoice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.hashtags.Lightning
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.amount_in_sats
import com.vitorpamplona.amethyst.commons.resources.lightning_tips
import com.vitorpamplona.amethyst.commons.resources.note_to_receiver
import com.vitorpamplona.amethyst.commons.resources.send_sats
import com.vitorpamplona.amethyst.commons.resources.thank_you_so_much
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun InvoiceRequest(
    lud16: String,
    user: User,
    accountViewModel: AccountViewModel,
    titleText: String? = null,
    buttonText: String? = null,
    onNewInvoice: (String) -> Unit,
    onError: (String, String) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
        ) {
            Icon(
                imageVector = CustomHashTagIcons.Lightning,
                null,
                modifier = Size20Modifier,
                tint = Color.Unspecified,
            )

            Text(
                text = titleText ?: stringRes(Res.string.lightning_tips),
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        HorizontalDivider(thickness = DividerThickness)

        var message by remember { mutableStateOf("") }
        var amount by remember { mutableLongStateOf(1000L) }

        SpellCheckedOutlinedTextField(
            label = { Text(text = stringRes(Res.string.note_to_receiver)) },
            modifier = Modifier.fillMaxWidth(),
            value = message,
            onValueChange = { message = it },
            placeholder = {
                Text(
                    text = stringRes(Res.string.thank_you_so_much),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            singleLine = true,
        )

        OutlinedTextField(
            label = { Text(text = stringRes(Res.string.amount_in_sats)) },
            modifier = Modifier.fillMaxWidth(),
            value = amount.toString(),
            onValueChange = {
                runCatching {
                    if (it.isEmpty()) {
                        amount = 0
                    } else {
                        amount = it.toLong()
                    }
                }
            },
            placeholder = {
                Text(
                    text = "1000",
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                ),
            singleLine = true,
        )

        Button(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            onClick = {
                accountViewModel.sendSats(
                    lnAddress = lud16,
                    user = user,
                    milliSats = amount * 1000,
                    message = message,
                    onNewInvoice = onNewInvoice,
                    onError = onError,
                    onProgress = {},
                )
            },
            shape = QuoteBorder,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            Text(
                text = buttonText ?: stringRes(Res.string.send_sats),
                color = Color.White,
                fontSize = 20.sp,
            )
        }
    }
}
