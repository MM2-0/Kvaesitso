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
package com.vitorpamplona.amethyst.commons.account.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.account_switch_add_account_dialog_title
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    newAccountKey: String?,
    accountSessionManager: AccountSessionManager,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        // Edge to edge, as the activity: a dialog window that fits the system bars left the status
        // bar's strip showing the drawer behind it, and took the keyboard's inset away from the
        // form, which then could not shrink or scroll to keep its button above the keyboard.
        properties = edgeToEdgeDialogProperties(),
    ) {
        SetDialogToEdgeToEdge()
        Surface(modifier = Modifier.fillMaxSize()) {
            // The bar above the form, not over it: the form fits itself to the height left, and on
            // a small phone the bar covered its brand.
            Column {
                TopAppBar(
                    title = {
                        Text(text = stringRes(Res.string.account_switch_add_account_dialog_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) { ArrowBackIcon() }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
                // The bar already keeps clear of the status bar; the form's own system-bar padding
                // would add it again.
                Box(Modifier.weight(1f).consumeWindowInsets(TopAppBarDefaults.windowInsets)) {
                    LoginOrSignupScreen(newAccountKey, accountSessionManager, isFirstLogin = false)
                }
            }
        }
    }
}
