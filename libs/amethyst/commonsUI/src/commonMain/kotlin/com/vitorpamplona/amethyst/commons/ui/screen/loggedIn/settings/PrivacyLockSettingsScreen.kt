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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.InactivityTimer
import com.vitorpamplona.amethyst.commons.privacylock.LocalPrivacyLockState
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_app
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_app_description
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_explanation
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_explanation_password
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_locks
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_messages
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_messages_description
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_not_available
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_change
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_confirm
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_current
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_mismatch
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_new
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_none
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_remove
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_remove_description
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_set
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_set_description
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_too_short
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_password_wrong
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_timeout
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_timeout_15m
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_timeout_1h
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_timeout_1m
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_timeout_5m
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_timeout_never
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_title
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_too_many_attempts
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_wallet
import com.vitorpamplona.amethyst.commons.resources.privacy_lock_wallet_description
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.privacylock.CredentialPrompter
import com.vitorpamplona.amethyst.commons.ui.privacylock.LocalCredentialPrompter
import com.vitorpamplona.amethyst.commons.ui.privacylock.LocalPrivacyLockSettings
import com.vitorpamplona.amethyst.commons.ui.privacylock.PromptResult
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** Shortest app password the desktop accepts. */
private const val MIN_LOCK_PASSWORD_LENGTH = 6

private fun InactivityTimer.label(): StringResource =
    when (this) {
        InactivityTimer.OneMin -> Res.string.privacy_lock_timeout_1m
        InactivityTimer.FiveMin -> Res.string.privacy_lock_timeout_5m
        InactivityTimer.FifteenMin -> Res.string.privacy_lock_timeout_15m
        InactivityTimer.OneHour -> Res.string.privacy_lock_timeout_1h
        InactivityTimer.Never -> Res.string.privacy_lock_timeout_never
    }

/**
 * Turns the three privacy locks on and off (the whole app, private messages, the wallet), picks how
 * long the app may sit idle before they close again and, on the desktop, sets the app password
 * they unlock with. Turning a lock off or the timer up asks the user to prove it is them first, so
 * someone holding an unlocked device cannot just switch the locks off.
 */
@Composable
fun PrivacyLockSettingsScreen(nav: INav) {
    val settings = LocalPrivacyLockSettings.current
    Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.privacy_lock_title), nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (settings == null) {
                Text(stringRes(Res.string.privacy_lock_not_available))
            } else {
                PrivacyLockSettingsBody(settings, LocalCredentialPrompter.current)
            }
        }
    }
}

@Composable
private fun PrivacyLockSettingsBody(
    settings: PrivacyLockSettings,
    prompter: CredentialPrompter,
) {
    val scope = rememberCoroutineScope()
    val lockApp by settings.lockApp.collectAsState()
    val lockMessages by settings.lockMessages.collectAsState()
    val lockWallet by settings.lockWallet.collectAsState()
    val timer by settings.inactivityTimer.collectAsState()
    val passwordHash by settings.passwordHashed.collectAsState()
    val hasPassword = passwordHash != null

    // A change that loosens the lock, waiting for the user to prove it is them.
    var pendingConfirmation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var passwordDialog by remember { mutableStateOf<PasswordDialogMode?>(null) }
    // A lock to turn on once the first password is set.
    var enableAfterPassword by remember { mutableStateOf<LockScope?>(null) }

    // Turning a lock on happens here, already past the lock: it should not lock this screen at once.
    val lockStates = LocalPrivacyLockState.current
    val enableLock = { lockScope: LockScope ->
        settings.setScopeLocked(lockScope, true)
        lockStates[lockScope]?.onUnlockSuccess()
    }

    val confirmThen = { action: () -> Unit ->
        if (prompter.usesPassword) {
            pendingConfirmation = action
        } else {
            scope.launch { if (prompter.prompt() == PromptResult.Success) action() }
        }
        Unit
    }

    val setLock = { lockScope: LockScope, on: Boolean ->
        when {
            !on -> {
                confirmThen { settings.setScopeLocked(lockScope, false) }
            }

            prompter.usesPassword && !hasPassword -> {
                enableAfterPassword = lockScope
                passwordDialog = PasswordDialogMode.Set
            }

            prompter.usesPassword -> {
                enableLock(lockScope)
            }

            else -> {
                // Proves the device can unlock before anything is locked behind it.
                scope.launch { if (prompter.prompt() == PromptResult.Success) enableLock(lockScope) }
            }
        }
        Unit
    }

    Text(
        text = stringRes(if (prompter.usesPassword) Res.string.privacy_lock_explanation_password else Res.string.privacy_lock_explanation),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SettingsSection(Res.string.privacy_lock_locks) {
        SettingsSwitchTile(MaterialSymbols.Lock, Res.string.privacy_lock_app, Res.string.privacy_lock_app_description, lockApp) { setLock(LockScope.App, it) }
        SettingsDivider()
        SettingsSwitchTile(MaterialSymbols.Mail, Res.string.privacy_lock_messages, Res.string.privacy_lock_messages_description, lockMessages) { setLock(LockScope.Messages, it) }
        SettingsDivider()
        SettingsSwitchTile(MaterialSymbols.AccountBalanceWallet, Res.string.privacy_lock_wallet, Res.string.privacy_lock_wallet_description, lockWallet) { setLock(LockScope.Wallet, it) }
    }

    SettingsSection(Res.string.privacy_lock_timeout) {
        InactivityTimer.entries.forEach { option ->
            val longer = (option.millis ?: Long.MAX_VALUE) > (timer.millis ?: Long.MAX_VALUE)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (option != timer) {
                                if (longer) confirmThen { settings.setInactivityTimer(option) } else settings.setInactivityTimer(option)
                            }
                        }.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == timer, onClick = null)
                Text(stringRes(option.label()), modifier = Modifier.padding(start = 12.dp))
            }
        }
    }

    if (prompter.usesPassword) {
        SettingsSection(Res.string.privacy_lock_password) {
            if (hasPassword) {
                SettingsItem(Res.string.privacy_lock_password_change, MaterialSymbols.Key) { passwordDialog = PasswordDialogMode.Change }
                SettingsDivider()
                SettingsItem(Res.string.privacy_lock_password_remove, MaterialSymbols.Key, isDanger = true) { passwordDialog = PasswordDialogMode.Remove }
            } else {
                SettingsItem(Res.string.privacy_lock_password_set, MaterialSymbols.Key) { passwordDialog = PasswordDialogMode.Set }
            }
        }
        if (!hasPassword) {
            Text(
                text = stringRes(Res.string.privacy_lock_password_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingConfirmation?.let { action ->
        PasswordDialog(
            mode = PasswordDialogMode.Confirm,
            prompter = prompter,
            onDismiss = { pendingConfirmation = null },
            onDone = {
                pendingConfirmation = null
                action()
            },
        )
    }

    passwordDialog?.let { mode ->
        PasswordDialog(
            mode = mode,
            prompter = prompter,
            onDismiss = {
                passwordDialog = null
                enableAfterPassword = null
            },
            onDone = {
                passwordDialog = null
                enableAfterPassword?.let { enableLock(it) }
                enableAfterPassword = null
            },
        )
    }
}

private enum class PasswordDialogMode { Set, Change, Remove, Confirm }

/** Sets, changes, removes or just checks the desktop's app password. */
@Composable
private fun PasswordDialog(
    mode: PasswordDialogMode,
    prompter: CredentialPrompter,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val asksCurrent = mode != PasswordDialogMode.Set
    val asksNew = mode == PasswordDialogMode.Set || mode == PasswordDialogMode.Change
    val wrongStr = stringRes(Res.string.privacy_lock_password_wrong)
    val shortStr = pluralStringRes(Res.plurals.privacy_lock_password_too_short, MIN_LOCK_PASSWORD_LENGTH, MIN_LOCK_PASSWORD_LENGTH)
    val mismatchStr = stringRes(Res.string.privacy_lock_password_mismatch)
    val lockedOutStr = stringRes(Res.string.privacy_lock_too_many_attempts)

    val submit = submit@{
        if (working) return@submit
        if (asksNew && new.length < MIN_LOCK_PASSWORD_LENGTH) {
            error = shortStr
            return@submit
        }
        if (asksNew && new != confirm) {
            error = mismatchStr
            return@submit
        }
        working = true
        scope.launch {
            val result =
                when (mode) {
                    PasswordDialogMode.Confirm -> prompter.verifyPassword(current.toCharArray())
                    PasswordDialogMode.Remove -> prompter.changePassword(current.toCharArray(), null)
                    else -> prompter.changePassword(current.takeIf { asksCurrent }?.toCharArray(), new.toCharArray())
                }
            working = false
            when (result) {
                PromptResult.Success -> onDone()
                PromptResult.TemporaryLockout -> error = lockedOutStr
                else -> error = wrongStr
            }
        }
        Unit
    }

    val title =
        when (mode) {
            PasswordDialogMode.Set -> Res.string.privacy_lock_password_set
            PasswordDialogMode.Change -> Res.string.privacy_lock_password_change
            PasswordDialogMode.Remove -> Res.string.privacy_lock_password_remove
            PasswordDialogMode.Confirm -> Res.string.privacy_lock_password_current
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (mode) {
                    PasswordDialogMode.Set -> Text(stringRes(Res.string.privacy_lock_password_set_description), style = MaterialTheme.typography.bodySmall)
                    PasswordDialogMode.Remove -> Text(stringRes(Res.string.privacy_lock_password_remove_description), style = MaterialTheme.typography.bodySmall)
                    else -> Unit
                }
                if (asksCurrent) PasswordField(current, Res.string.privacy_lock_password_current) { current = it }
                if (asksNew) {
                    PasswordField(new, Res.string.privacy_lock_password_new) { new = it }
                    PasswordField(confirm, Res.string.privacy_lock_password_confirm) { confirm = it }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = !working) { Text(stringRes(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    label: StringResource,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringRes(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
