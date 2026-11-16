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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffScaffold
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffSwitchRow
import com.vitorpamplona.amethyst.commons.account.ui.LoggedOffTitle
import com.vitorpamplona.amethyst.commons.account.ui.OrDivider
import com.vitorpamplona.amethyst.commons.account.ui.SwitchScreenFooter
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.don_t_have_an_account
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.login
import com.vitorpamplona.amethyst.commons.resources.login_welcome_back
import com.vitorpamplona.amethyst.commons.resources.ncryptsec_password
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.commons.resources.sign_up
import com.vitorpamplona.amethyst.commons.resources.temporary_account
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.keystorage.KeyStorageLoginOptions
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import kotlinx.coroutines.delay

/** The fields and buttons of the logged-off screens: rounded, full width. */
val LoggedOffShape = RoundedCornerShape(16.dp)

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun LoginPagePreview() {
    val loginViewModel: LoginViewModel = rememberViewModel { LoginViewModel() }
    loginViewModel.init(TorSettingsFlow())

    ThemeComparisonRow(
        toPreview = {
            LoginPage(loginViewModel) {}
        },
    )
}

@Composable
fun LoginPage(
    accountSessionManager: AccountSessionManager,
    isFirstLogin: Boolean,
    newAccountKey: String? = null,
    onWantsToLogin: () -> Unit,
) {
    val requiresTerms = LocalAppPlatform.current.requiresTermsAcceptance
    val loginViewModel: LoginViewModel = rememberViewModel { LoginViewModel(requiresTerms) }
    loginViewModel.init(accountSessionManager)
    loginViewModel.init(LocalAppServices.current.torSettings)

    LaunchedEffect(loginViewModel, isFirstLogin, newAccountKey) {
        loginViewModel.load(isFirstLogin, newAccountKey)
    }

    LoginPage(loginViewModel, isFirstLogin, onWantsToLogin)
}

/** [isFirstLogin] false is the Add New Account dialog, whose bar already names the screen. */
@Composable
fun LoginPage(
    loginViewModel: LoginViewModel,
    isFirstLogin: Boolean = true,
    onWantsToSignUp: () -> Unit,
) {
    val platform = LocalAppPlatform.current
    val toaster = platform.rememberToaster()
    loginViewModel.remoteSignerLoginSupported = platform.supportsRemoteSignerLogin

    LoggedOffScaffold(
        torSettingsFlow = loginViewModel.torSettings,
        onTorError = { toaster.show(it, long = true) },
        footer = {
            SwitchScreenFooter(
                question = stringRes(Res.string.don_t_have_an_account),
                action = stringRes(Res.string.sign_up),
                onClick = onWantsToSignUp,
            )
        },
    ) {
        // Not "Welcome back" over a form that adds another account.
        if (isFirstLogin) LoggedOffTitle(stringRes(Res.string.login_welcome_back))

        // Before everything it gates: every way in below stays disabled until it is checked.
        if (loginViewModel.isFirstLogin) {
            platform.TermsGate(
                checked = loginViewModel.acceptedTerms,
                onCheckedChange = loginViewModel::updateAcceptedTerms,
                showError = loginViewModel.termsAcceptanceIsRequiredError,
            )
        }

        // The signers lead on every platform, in one order: the key never reaches Amethyst. A
        // signer app (Amber and the like) where one is installed, filled; a remote signer anywhere
        // it works. Each one's error sits under its own button.
        val hasSignerApp = platform.rememberHasExternalSignerLogin()
        if (hasSignerApp) {
            WithErrorBelow(loginViewModel.signerErrorManager) {
                platform.ExternalSignerLoginButton(loginViewModel)
            }
        }
        if (platform.supportsRemoteSignerLogin) {
            WithErrorBelow(loginViewModel.remoteSignerErrorManager) {
                RemoteSignerLoginButton(loginViewModel)
            }
        }
        if (hasSignerApp || platform.supportsRemoteSignerLogin) {
            OrDivider()
        }

        KeyTextField(
            value = loginViewModel.key,
            onValueChange = loginViewModel::updateKey,
            onLogin = loginViewModel::login,
            acceptsBunker = platform.supportsRemoteSignerLogin,
            error = loginErrorText(loginViewModel.errorManager),
        )

        PasswordField(loginViewModel)

        if (loginViewModel.offerTemporaryLogin) {
            OfferTemporaryAccount(
                checked = loginViewModel.isTemporary,
                onCheckedChange = { loginViewModel.isTemporary = it },
            )
        }

        // A temporary login keeps nothing, so whether to keep the key only matters otherwise.
        if (!loginViewModel.isTemporary) {
            KeyStorageLoginOptions(
                rememberKey = loginViewModel.rememberKey,
                onRememberKeyChange = { loginViewModel.rememberKey = it },
                // An npub, a bunker:// or a NIP-05 brings no private key to keep.
                offerRememberKey = loginViewModel.keyMayBePrivate,
            )
        }

        LoggedOffPrimaryButton(
            text = stringRes(Res.string.login),
            // Lights up once there is a key to log in with, so it never outshines a signer above
            // an empty field. Stays enabled while it works: a disabled button would grey out under
            // its spinner. login() ignores taps until the attempt ends.
            enabled = loginViewModel.acceptedTerms && loginViewModel.key.text.isNotBlank(),
            // A remote signer's login has its own dialog; the button only spins for its own.
            loading = loginViewModel.processingLogin && !loginViewModel.nostrConnectPending,
            onClick = loginViewModel::login,
        )
    }

    platform.IncomingLoginKeys { key ->
        loginViewModel.updateKey(TextFieldValue(key), true)
        loginViewModel.updateOfferTemporaryLogin(true)
    }
}

/** The words of [errorManager]'s error, or null while there is none. */
@Composable
fun loginErrorText(errorManager: LoginErrorManager): String? =
    when (val error = errorManager.error) {
        is LoginErrorManager.SingleErrorMsg -> stringRes(error.errorResId)
        is LoginErrorManager.ParamsErrorMsg -> stringRes(error.errorResId, *error.params)
        else -> null
    }

/**
 * [content] (a button) with [errorManager]'s error right under it, as close as a text field's
 * supporting text: the form's own row spacing would set it as far from the button as the next row.
 */
@Composable
fun WithErrorBelow(
    errorManager: LoginErrorManager,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content()
        val text = loginErrorText(errorManager) ?: return@Column
        Text(
            text = text,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            // Where a text field's supporting text starts.
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

/** The screen's main action: a full-width filled button, with a spinner while it works. */
@Composable
fun LoggedOffPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) = LoggedOffButton(text, enabled, onClick, loading, filled = true, icon = icon)

/**
 * A full-width button of the logged-off screens: filled for the screen's main way in, outlined
 * for an alternative. Shows a spinner instead of its [icon] while [loading].
 */
@Composable
fun LoggedOffButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
    filled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            icon()
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
    val modifier = Modifier.fillMaxWidth().height(52.dp)
    if (filled) {
        Button(onClick = onClick, enabled = enabled, shape = LoggedOffShape, colors = CardSurfaces.mainButtonColors(), modifier = modifier) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, shape = LoggedOffShape, modifier = modifier) { content() }
    }
}

/** Another way in (a signer app, a remote signer): a full-width outlined button with an icon. */
@Composable
fun LoggedOffSecondaryButton(
    text: String,
    symbol: MaterialSymbol,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = LoggedOffSecondaryButton(
    text = text,
    icon = { Icon(symbol, contentDescription = null, modifier = Modifier.size(20.dp)) },
    enabled = enabled,
    onClick = onClick,
)

@Composable
fun LoggedOffSecondaryButton(
    text: String,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    LoggedOffButton(text, enabled, onClick, filled = false, icon = icon)
}

@Composable
fun OfferTemporaryAccount(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) = LoggedOffSwitchRow(MaterialSymbols.Timer, stringRes(Res.string.temporary_account), checked, onCheckedChange)

@Composable
private fun PasswordField(loginViewModel: LoginViewModel) {
    if (loginViewModel.needsPassword) {
        val passwordFocusRequester = remember { FocusRequester() }

        PasswordField(
            value = loginViewModel.password,
            onValueChange = loginViewModel::updatePassword,
            passwordFocusRequester = passwordFocusRequester,
            onGo = loginViewModel::login,
            error = loginErrorText(loginViewModel.passwordErrorManager),
        )

        // Only once the key is complete: a pasted ncryptsec jumps straight to the password,
        // one being typed keeps its focus until the last character.
        val keyComplete = loginViewModel.isCompleteNcryptsec
        LaunchedEffect(keyComplete) {
            if (keyComplete) {
                delay(300)
                passwordFocusRequester.requestFocus()
            }
        }
    }
}

@Composable
fun PasswordField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    passwordFocusRequester: FocusRequester,
    onGo: () -> Unit,
    error: String? = null,
) {
    var showCharsPassword by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .semantics { contentType = ContentType.Password },
        value = value,
        onValueChange = onValueChange,
        shape = LoggedOffShape,
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
        label = { Text(stringRes(Res.string.ncryptsec_password), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        leadingIcon = { Icon(MaterialSymbols.Lock, contentDescription = null) },
        // Nothing to reveal in an empty field; its room goes to the label.
        trailingIcon =
            if (value.text.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { showCharsPassword = !showCharsPassword }) {
                        Icon(
                            symbol = if (showCharsPassword) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                            contentDescription =
                                if (showCharsPassword) {
                                    stringRes(Res.string.hide_password)
                                } else {
                                    stringRes(Res.string.show_password)
                                },
                        )
                    }
                }
            },
        visualTransformation =
            if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
    )
}
