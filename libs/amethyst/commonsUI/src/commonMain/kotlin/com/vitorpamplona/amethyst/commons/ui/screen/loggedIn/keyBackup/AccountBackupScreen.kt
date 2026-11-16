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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.keyBackup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.LocalPrivacyLockState
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.LockState
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypt
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypt_again
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypted_body
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypted_password
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypted_password_mismatch
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypted_repeat_password
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypted_saved_hint
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypted_title
import com.vitorpamplona.amethyst.commons.resources.account_backup_encrypting
import com.vitorpamplona.amethyst.commons.resources.account_backup_headline
import com.vitorpamplona.amethyst.commons.resources.account_backup_intro
import com.vitorpamplona.amethyst.commons.resources.account_backup_password_min_length
import com.vitorpamplona.amethyst.commons.resources.account_backup_qr_code
import com.vitorpamplona.amethyst.commons.resources.account_backup_tap_to_reveal
import com.vitorpamplona.amethyst.commons.resources.account_backup_tip_developers
import com.vitorpamplona.amethyst.commons.resources.account_backup_tip_storage
import com.vitorpamplona.amethyst.commons.resources.account_backup_tip_trust
import com.vitorpamplona.amethyst.commons.resources.account_backup_write_down_hint
import com.vitorpamplona.amethyst.commons.resources.backup_keys
import com.vitorpamplona.amethyst.commons.resources.backup_keys_copy
import com.vitorpamplona.amethyst.commons.resources.backup_keys_external_signer
import com.vitorpamplona.amethyst.commons.resources.backup_keys_hide
import com.vitorpamplona.amethyst.commons.resources.backup_keys_reveal
import com.vitorpamplona.amethyst.commons.resources.backup_keys_secret_label
import com.vitorpamplona.amethyst.commons.resources.failed_to_encrypt_key
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.secret_key_copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.commons.ui.components.HideFromScreenshots
import com.vitorpamplona.amethyst.commons.ui.components.KeyTranscriptionGrid
import com.vitorpamplona.amethyst.commons.ui.components.ShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.components.util.setSensitiveText
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.keystorage.KeyStorageStatusCard
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.privacylock.rememberDeviceAuthenticator
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Best-effort delay before the plaintext nsec is wiped from the clipboard. */
private const val CLIPBOARD_CLEAR_DELAY_MS = 60_000L

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun AccountBackupScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    AccountBackupScreenContent(accountViewModel, nav)
}

@Preview(device = "spec:width=2160px,height=2340px,dpi=440")
@Composable
fun AccountBackupScreenPreview() {
    ThemeComparisonRow {
        AccountBackupScreenContent(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
private fun AccountBackupScreenContent(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Redact the secret key from screenshots and the app switcher while this
    // screen is on-screen. Cleared on dispose so the flag never leaks to other
    // screens. This is the only FLAG_SECURE usage in the app — scoped on purpose.
    HideFromScreenshots()

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                stringRes(Res.string.backup_keys),
                nav = nav,
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(it)
                .consumeWindowInsets(it)
                // Keeps the password fields and the Encrypt button above the keyboard.
                .imePaddingSafe()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackupHeader()

            // Where this device keeps the key, and the fix when it can't read it (desktop only).
            KeyStorageStatusCard()

            val nsec =
                remember(accountViewModel) {
                    accountViewModel.account.settings.keyPair.privKey
                        ?.toNsec()
                }

            if (nsec == null) {
                // Only where a signer really holds the key. An account whose key did not load here
                // (a locked key file, a keychain error) also arrives without one: the card above
                // says why, and calling that an external signer would be false.
                val settings = accountViewModel.account.settings
                if (settings.externalSignerPackageName != null || settings.usesRemoteSigner()) {
                    TipRow(MaterialSymbols.Info, stringRes(Res.string.backup_keys_external_signer))
                }
            } else {
                val gate = rememberKeyAccessGate(accountViewModel)

                SecretKeyCard(nsec, gate, accountViewModel)

                EncryptedKeyCard(accountViewModel, gate)

                BackupTips()
            }
        }
    }
}

@Composable
private fun BackupHeader() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringRes(Res.string.account_backup_headline),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringRes(Res.string.account_backup_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SecretKeyCard(
    nsec: String,
    gate: KeyAccessGate,
    accountViewModel: AccountViewModel,
) {
    val shortNotice = rememberShortNotice()
    val clipboard = LocalClipboard.current

    var revealed by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    // Never leave the key on screen while the app is in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        revealed = false
        showQr = false
        gate.lock()
    }

    val reveal = { gate.withAccess { revealed = true } }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = CardSurfaces.color,
        border = CardSurfaces.border,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle(MaterialSymbols.Key, stringRes(Res.string.backup_keys_secret_label), Modifier.weight(1f))
                IconButton(onClick = { if (revealed) revealed = false else reveal() }) {
                    Icon(
                        symbol = if (revealed) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                        contentDescription = stringRes(if (revealed) Res.string.backup_keys_hide else Res.string.backup_keys_reveal),
                    )
                }
            }

            Column(Modifier.padding(end = 8.dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardSurfaces.well)
                            .clickable(enabled = !revealed, onClick = reveal)
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    KeyTranscriptionGrid(
                        bech32 = nsec,
                        masked = !revealed,
                        color = if (revealed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!revealed) {
                        RevealChip()
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringRes(Res.string.account_backup_write_down_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { gate.withAccess { copyNSec(shortNotice, accountViewModel.viewModelScope, nsec, clipboard) } },
                    ) {
                        ButtonContent(MaterialSymbols.ContentCopy, stringRes(Res.string.backup_keys_copy))
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { gate.withAccess { showQr = true } },
                    ) {
                        ButtonContent(MaterialSymbols.QrCode2, stringRes(Res.string.account_backup_qr_code))
                    }
                }
            }
        }
    }

    if (showQr) {
        ShowKeyQRDialog(nsec, onClose = { showQr = false })
    }
}

@Composable
private fun RevealChip() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MaterialSymbols.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.account_backup_tap_to_reveal), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EncryptedKeyCard(
    accountViewModel: AccountViewModel,
    gate: KeyAccessGate,
) {
    val shortNotice = rememberShortNotice()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var expanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var encrypted by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }

    // Drop the result and the typed password while in the background, so neither sits in memory
    // (or on the recents thumbnail, with the password shown) behind another app. The cost: leaving
    // to copy a password out of a password manager app clears what was typed; autofill, which
    // fills in place without stopping this activity, is unaffected.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        encrypted = null
        showQr = false
        password = ""
        repeated = ""
        showPassword = false
    }

    // A typo in the password makes the backup permanently useless, so it must be typed twice.
    val mismatch = repeated.isNotEmpty() && repeated != password
    val longEnough = Nip49.isLongEnough(password)
    val canEncrypt = longEnough && repeated == password && !working

    fun encrypt() {
        if (!canEncrypt) return
        // Read before the gate: a device-credential prompt is its own activity, so it stops this
        // one and the ON_STOP above clears the fields before the unlock callback runs.
        val currentPassword = password
        gate.withAccess {
            val privKey = accountViewModel.account.settings.keyPair.privKey ?: return@withAccess
            working = true
            // NIP-49 runs scrypt, which takes a noticeable moment: keep it off the main thread.
            scope.launch {
                val result =
                    withContext(Dispatchers.Default) {
                        runCatching {
                            Nip49().encrypt(privKey, currentPassword, Nip49.DEFAULT_LOG_N, Nip49.EncryptedInfo.CLIENT_DOES_NOT_TRACK)
                        }.getOrNull()
                    }
                working = false
                if (result != null) {
                    encrypted = result
                    // The password has done its job: don't leave it in the fields behind the result.
                    password = ""
                    repeated = ""
                } else {
                    shortNotice.show(loadStringRes(Res.string.failed_to_encrypt_key))
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = CardSurfaces.color,
        border = CardSurfaces.border,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = if (expanded) 8.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    CardTitle(MaterialSymbols.Lock, stringRes(Res.string.account_backup_encrypted_title))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringRes(Res.string.account_backup_encrypted_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    symbol = if (expanded) MaterialSymbols.KeyboardArrowUp else MaterialSymbols.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    val encryptedValue = encrypted
                    if (encryptedValue == null) {
                        val visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()

                        OutlinedTextField(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { contentType = ContentType.NewPassword },
                            value = password,
                            onValueChange = { password = it },
                            // What gets encrypted is the password at tap time: don't let it change underneath.
                            enabled = !working,
                            singleLine = true,
                            label = { Text(stringRes(Res.string.account_backup_encrypted_password)) },
                            supportingText = {
                                Text(
                                    text = pluralStringRes(Res.plurals.account_backup_password_min_length, Nip49.MIN_PASSWORD_LENGTH, Nip49.MIN_PASSWORD_LENGTH),
                                    color = if (longEnough) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Next,
                                ),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        symbol = if (showPassword) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                                        contentDescription = stringRes(if (showPassword) Res.string.hide_password else Res.string.show_password),
                                    )
                                }
                            },
                            visualTransformation = visualTransformation,
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { contentType = ContentType.NewPassword },
                            value = repeated,
                            onValueChange = { repeated = it },
                            enabled = !working,
                            singleLine = true,
                            label = { Text(stringRes(Res.string.account_backup_encrypted_repeat_password)) },
                            isError = mismatch,
                            supportingText =
                                if (mismatch) {
                                    { Text(stringRes(Res.string.account_backup_encrypted_password_mismatch)) }
                                } else {
                                    null
                                },
                            trailingIcon =
                                if (canEncrypt) {
                                    { Icon(MaterialSymbols.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                } else {
                                    null
                                },
                            keyboardOptions =
                                KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        // Close the keyboard so the result and its buttons are visible. Not via
                                        // clearFocus(): that hands focus to the embed tab's RemoteImeView, which
                                        // keeps the keyboard up.
                                        keyboardController?.hide()
                                        encrypt()
                                    },
                                ),
                            visualTransformation = visualTransformation,
                        )

                        Spacer(Modifier.height(12.dp))

                        FilledTonalButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canEncrypt,
                            onClick = ::encrypt,
                        ) {
                            if (working) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringRes(Res.string.account_backup_encrypting))
                            } else {
                                ButtonContent(MaterialSymbols.Lock, stringRes(Res.string.account_backup_encrypt))
                            }
                        }
                    } else {
                        Text(
                            text = encryptedValue,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardSurfaces.well)
                                    .padding(12.dp),
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringRes(Res.string.account_backup_encrypted_saved_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        clipboard.setText(encryptedValue)
                                        shortNotice.show(loadStringRes(Res.string.secret_key_copied_to_clipboard))
                                    }
                                },
                            ) {
                                ButtonContent(MaterialSymbols.ContentCopy, stringRes(Res.string.backup_keys_copy))
                            }
                            FilledTonalButton(
                                modifier = Modifier.weight(1f),
                                onClick = { showQr = true },
                            ) {
                                ButtonContent(MaterialSymbols.QrCode2, stringRes(Res.string.account_backup_qr_code))
                            }
                        }

                        TextButton(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            onClick = {
                                encrypted = null
                                password = ""
                                repeated = ""
                            },
                        ) {
                            Text(stringRes(Res.string.account_backup_encrypt_again))
                        }
                    }
                }
            }
        }
    }

    encrypted?.let {
        if (showQr) {
            ShowKeyQRDialog(it, onClose = { showQr = false })
        }
    }
}

@Composable
private fun BackupTips() {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TipRow(MaterialSymbols.EditNote, stringRes(Res.string.account_backup_tip_storage))
        TipRow(MaterialSymbols.Warning, stringRes(Res.string.account_backup_tip_trust))
        TipRow(MaterialSymbols.Shield, stringRes(Res.string.account_backup_tip_developers))
    }
}

@Composable
private fun CardTitle(
    symbol: MaterialSymbol,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TipRow(
    symbol: MaterialSymbol,
    text: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ButtonContent(
    symbol: MaterialSymbol,
    label: String,
) {
    Icon(symbol, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label)
}

/**
 * Asks for the device credential (biometric or lock screen) once per visit, before
 * the key is first revealed, copied or shown as a QR code. [lock] forgets the approval,
 * e.g. when the app goes to the background.
 */
@Stable
private class KeyAccessGate {
    var isUnlocked by mutableStateOf(false)
        private set

    var prompt: ((onApproved: () -> Unit) -> Unit)? = null

    fun withAccess(action: () -> Unit) {
        if (isUnlocked) {
            action()
        } else {
            prompt?.invoke {
                isUnlocked = true
                action()
            }
        }
    }

    fun lock() {
        isUnlocked = false
    }
}

@Composable
private fun rememberKeyAccessGate(accountViewModel: AccountViewModel): KeyAccessGate {
    val authenticator = rememberDeviceAuthenticator()
    val authTitle = stringRes(Res.string.backup_keys)
    val gate = remember { KeyAccessGate() }
    val keyBackupLock = LocalPrivacyLockState.current[LockScope.KeyBackup]

    SideEffect {
        gate.prompt = { onApproved ->
            // With a privacy lock on, this screen opened only after unlocking it: no second prompt.
            if (keyBackupLock?.state?.value is LockState.Unlocked) {
                onApproved()
            } else {
                authenticator.authenticate(
                    title = authTitle,
                    onApproved = onApproved,
                    onError = { title, message -> accountViewModel.toastManager.toast(title, message) },
                )
            }
        }
    }

    return gate
}

private fun copyNSec(
    shortNotice: ShortNotice,
    scope: CoroutineScope,
    nsec: String,
    clipboardManager: Clipboard,
) {
    // The auto-clear below must outlive this screen, so [scope] is not a composition scope.
    scope.launch {
        clipboardManager.setSensitiveText(nsec)
        shortNotice.show(loadStringRes(Res.string.secret_key_copied_to_clipboard))

        // Best-effort auto-clear: after a delay, wipe the clipboard only if it
        // still holds this exact nsec (don't clobber anything copied since).
        delay(CLIPBOARD_CLEAR_DELAY_MS)
        if (clipboardManager.getText() == nsec) {
            clipboardManager.setText("")
        }
    }
}

@Composable
private fun ShowKeyQRDialog(
    qrCode: String,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onPress = onClose)
                }

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrCodeDrawer(qrCode)
                }
            }
        }
    }
}
