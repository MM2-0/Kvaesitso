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
package com.vitorpamplona.amethyst.commons.account.ui.login

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.ic_qrcode
import com.vitorpamplona.amethyst.commons.resources.login_key_placeholder_bunker
import com.vitorpamplona.amethyst.commons.resources.login_with_qr_code
import com.vitorpamplona.amethyst.commons.resources.nsec_npub_hex_private_key
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import androidx.compose.material3.Icon as PainterIcon

/**
 * The key, `npub` or address to log in with. [acceptsBunker] says so in the placeholder where a
 * `bunker://` address from a remote signer works too. [error], when set, sits right under it.
 */
@Composable
fun KeyTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue, throughQR: Boolean) -> Unit,
    onLogin: () -> Unit,
    acceptsBunker: Boolean = false,
    error: String? = null,
) {
    var scannerOpen by remember { mutableStateOf(false) }
    var showCharsKey by remember { mutableStateOf(false) }

    if (scannerOpen) {
        SimpleQrCodeScanner {
            scannerOpen = false
            if (!it.isNullOrEmpty()) {
                onValueChange(TextFieldValue(it), true)
            }
        }
    }

    OutlinedTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentType = ContentType.Password },
        value = value,
        onValueChange = { onValueChange(it, false) },
        shape = LoggedOffShape,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
        placeholder = {
            Text(
                text = stringRes(if (acceptsBunker) Res.string.login_key_placeholder_bunker else Res.string.nsec_npub_hex_private_key),
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
            )
        },
        leadingIcon = { Icon(MaterialSymbols.Key, contentDescription = null) },
        // Attached to the field, not a row of the form below it.
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        trailingIcon = {
            Row {
                IconButton(onClick = { scannerOpen = true }) {
                    PainterIcon(
                        painter = painterRes(Res.drawable.ic_qrcode, 5),
                        contentDescription = stringRes(Res.string.login_with_qr_code),
                        modifier = Modifier.size(22.dp),
                    )
                }
                // Nothing to reveal in an empty field; its room goes to the placeholder on a narrow phone.
                if (value.text.isNotEmpty()) {
                    IconButton(onClick = { showCharsKey = !showCharsKey }) {
                        Icon(
                            symbol = if (showCharsKey) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                            contentDescription = stringRes(if (showCharsKey) Res.string.hide_password else Res.string.show_password),
                        )
                    }
                }
            }
        },
        visualTransformation =
            if (showCharsKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardActions = KeyboardActions(onGo = { onLogin() }),
    )
}
