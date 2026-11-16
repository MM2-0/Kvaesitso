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
package com.vitorpamplona.amethyst.commons.account.ui.signup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffScaffold
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffTitle
import com.vitorpamplona.amethyst.commons.account.ui.SwitchScreenFooter
import com.vitorpamplona.amethyst.commons.account.ui.login.LoggedOffPrimaryButton
import com.vitorpamplona.amethyst.commons.account.ui.login.LoggedOffShape
import com.vitorpamplona.amethyst.commons.account.ui.login.loginErrorText
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.already_have_an_account
import com.vitorpamplona.amethyst.commons.resources.create_account
import com.vitorpamplona.amethyst.commons.resources.how_should_we_call_you
import com.vitorpamplona.amethyst.commons.resources.login
import com.vitorpamplona.amethyst.commons.resources.my_awesome_name
import com.vitorpamplona.amethyst.commons.resources.welcome
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun SignUpPagePreview() {
    val signUpViewModel: SignUpViewModel = rememberViewModel { SignUpViewModel() }
    signUpViewModel.init(TorSettingsFlow())

    ThemeComparisonRow(
        toPreview = {
            SignUpPage(signUpViewModel) {}
        },
    )
}

@Composable
fun SignUpPage(
    accountSessionManager: AccountSessionManager,
    onWantsToLogin: () -> Unit,
) {
    val requiresTerms = LocalAppPlatform.current.requiresTermsAcceptance
    val signUpViewModel: SignUpViewModel = rememberViewModel { SignUpViewModel(requiresTerms) }
    signUpViewModel.init(accountSessionManager)
    signUpViewModel.init(LocalAppServices.current.torSettings)

    SignUpPage(signUpViewModel, onWantsToLogin)
}

@Composable
fun SignUpPage(
    signUpViewModel: SignUpViewModel,
    onWantsToLogin: () -> Unit,
) {
    val platform = LocalAppPlatform.current
    val toaster = platform.rememberToaster()

    LoggedOffScaffold(
        torSettingsFlow = signUpViewModel.torSettings,
        onTorError = { toaster.show(it, long = true) },
        footer = {
            SwitchScreenFooter(
                question = stringRes(Res.string.already_have_an_account),
                action = stringRes(Res.string.login),
                onClick = onWantsToLogin,
            )
        },
    ) {
        LoggedOffTitle(stringRes(Res.string.welcome))

        val nameError = loginErrorText(signUpViewModel.errorManager)
        OutlinedTextField(
            value = signUpViewModel.displayName,
            onValueChange = signUpViewModel::updateDisplayName,
            modifier = Modifier.fillMaxWidth(),
            shape = LoggedOffShape,
            singleLine = true,
            label = { Text(stringRes(Res.string.how_should_we_call_you)) },
            leadingIcon = { Icon(MaterialSymbols.Person, contentDescription = null) },
            // Attached to the field, not a row of the form below it.
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            placeholder = {
                Text(
                    text = stringRes(Res.string.my_awesome_name),
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
            keyboardOptions =
                KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { signUpViewModel.signup() }),
        )

        platform.TermsGate(
            checked = signUpViewModel.acceptedTerms,
            onCheckedChange = signUpViewModel::updateAcceptedTerms,
            showError = signUpViewModel.termsAcceptanceIsRequiredError,
        )

        LoggedOffPrimaryButton(
            text = stringRes(Res.string.create_account),
            // Like Login: faded until there is a name to create the account with.
            enabled = signUpViewModel.acceptedTerms && signUpViewModel.displayName.text.isNotBlank(),
            onClick = signUpViewModel::signup,
        )
    }
}
