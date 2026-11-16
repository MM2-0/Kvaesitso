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
package com.vitorpamplona.amethyst.commons.ui.privacylock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_app_locked
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_app_locked_description
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_messages_locked
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_messages_locked_description
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_wrong
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_try_again_in
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_unlock
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_wallet_locked
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_wallet_locked_description
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What a locked [scope] shows instead of its content. On Android the unlock button (pressed for
 * the user once on arrival) opens the biometric or device-credential prompt; on the desktop the
 * user types the app password. After too many wrong passwords the field waits out the backoff.
 */
@Composable
internal fun LockScreen(
    scope: LockScope,
    lockState: PrivacyLockState,
) {
    val prompter = LocalCredentialPrompter.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(prompter) {
        // Nothing can unlock it (a desktop with no password), so the lock lets go rather than
        // shutting the user out.
        if (!prompter.available) lockState.onCredentialUnavailable()
    }

    val (title, subtitle) =
        when (scope) {
            LockScope.App -> Res.string.privacy_lock_app_locked to Res.string.privacy_lock_app_locked_description
            LockScope.Wallet -> Res.string.privacy_lock_wallet_locked to Res.string.privacy_lock_wallet_locked_description
            else -> Res.string.privacy_lock_messages_locked to Res.string.privacy_lock_messages_locked_description
        }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                symbol = MaterialSymbols.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Box(modifier = Modifier.size(16.dp))
            Text(text = stringRes(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Box(modifier = Modifier.size(8.dp))
            Text(
                text = stringRes(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Box(modifier = Modifier.size(32.dp))

            if (prompter.usesPassword) {
                PasswordUnlock(prompter, lockState)
            } else {
                val unlock = {
                    coroutineScope.launch {
                        when (prompter.prompt()) {
                            PromptResult.Success -> lockState.onUnlockSuccess()
                            PromptResult.Unavailable -> lockState.onCredentialUnavailable()
                            else -> Unit
                        }
                    }
                }
                // Ask straight away: the user came here to get in.
                LaunchedEffect(Unit) { unlock() }
                Button(onClick = { unlock() }, enabled = prompter.available) {
                    Text(text = stringRes(Res.string.privacy_lock_unlock))
                }
            }
        }
    }
}

@Composable
private fun PasswordUnlock(
    prompter: CredentialPrompter,
    lockState: PrivacyLockState,
) {
    val coroutineScope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val lockedUntil by lockState.lockedUntilEpochMs.collectAsState()
    val secondsLeft by produceState(0L, lockedUntil) {
        while (true) {
            val left = ((lockedUntil ?: 0L) - TimeUtils.nowMillis()).coerceAtLeast(0L)
            value = (left + 999) / 1000
            if (left <= 0L) break
            delay(1000)
        }
    }
    val waiting = secondsLeft > 0
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val submit = {
        if (!waiting && !checking && password.isNotEmpty()) {
            checking = true
            val typed = password.toCharArray()
            coroutineScope.launch {
                when (prompter.verifyPassword(typed)) {
                    PromptResult.Success -> {
                        lockState.onUnlockSuccess()
                    }

                    PromptResult.Unavailable -> {
                        lockState.onCredentialUnavailable()
                    }

                    // The prompter counted the miss and set any lockout; the countdown shows it.
                    PromptResult.Failed -> {
                        wrong = true
                        password = ""
                    }

                    else -> {
                        password = ""
                    }
                }
                typed.fill(' ')
                checking = false
            }
        }
    }

    OutlinedTextField(
        value = password,
        onValueChange = {
            password = it
            wrong = false
        },
        label = { Text(stringRes(Res.string.privacy_lock_password)) },
        singleLine = true,
        enabled = !waiting && !checking,
        isError = wrong,
        supportingText =
            when {
                waiting -> {
                    { Text(stringRes(Res.string.privacy_lock_try_again_in, secondsLeft.toInt())) }
                }

                wrong -> {
                    { Text(stringRes(Res.string.privacy_lock_password_wrong)) }
                }

                else -> {
                    null
                }
            },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.widthIn(max = 320.dp).focusRequester(focusRequester),
    )
    Box(modifier = Modifier.size(16.dp))
    Button(onClick = { submit() }, enabled = !waiting && !checking && password.isNotEmpty()) {
        Text(text = stringRes(Res.string.privacy_lock_unlock))
    }
}
