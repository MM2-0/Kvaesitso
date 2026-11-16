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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.login_with_a_signer_app
import com.vitorpamplona.amethyst.commons.resources.login_with_signer_app
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * Android's signer-app login. Filled: where a signer app is installed, it is the way in. Named
 * and drawn as the app itself ([appName], [appIcon]) when there is just one to pick.
 */
@Composable
fun SignerAppLoginButton(
    enabled: Boolean,
    onClick: () -> Unit,
    appName: String? = null,
    appIcon: (@Composable () -> Unit)? = null,
) {
    LoggedOffPrimaryButton(
        text =
            if (appName != null) {
                stringRes(Res.string.login_with_signer_app, appName)
            } else {
                stringRes(Res.string.login_with_a_signer_app)
            },
        enabled = enabled,
        onClick = onClick,
        icon = appIcon ?: { Icon(MaterialSymbols.Security, contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
}
