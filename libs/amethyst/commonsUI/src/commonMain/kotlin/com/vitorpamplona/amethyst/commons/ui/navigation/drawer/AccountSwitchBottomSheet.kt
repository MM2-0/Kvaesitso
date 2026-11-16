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
package com.vitorpamplona.amethyst.commons.ui.navigation.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.account.ui.AddAccountDialog
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.account_switch_active_account
import com.vitorpamplona.amethyst.commons.resources.account_switch_add_account_btn
import com.vitorpamplona.amethyst.commons.resources.account_switch_select_account
import com.vitorpamplona.amethyst.commons.resources.are_you_sure_you_want_to_log_out
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.log_out
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_logout_toast
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_logout_toast_zero
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts_logout_warning
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.ui.components.PromptDialog
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuItemModifier
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.loadPluralStringRes
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.AccountPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitchBottomSheet(
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    var popupExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.verticalScroll(scrollState)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Size10dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringRes(Res.string.account_switch_select_account), fontWeight = FontWeight.Bold)
        }
        DisplayAllAccounts(accountViewModel, accountSessionManager)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Size10dp, bottom = Size55dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { popupExpanded = true }) {
                Text(stringRes(Res.string.account_switch_add_account_btn))
            }
        }
    }

    if (popupExpanded) {
        AddAccountDialog(null, accountSessionManager) { popupExpanded = false }
    }
}

@Composable
private fun DisplayAllAccounts(
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    val accounts by accountSessionManager.localPreferences
        .accountsFlow()
        .collectAsStateWithLifecycle()

    // Trigger lazy load from encrypted storage if the flow hasn't been populated yet.
    // accountsFlow() starts as null and only gets a value when allSavedAccounts() is called.
    LaunchedEffect(Unit) {
        if (accounts == null) {
            accountSessionManager.localPreferences.allSavedAccounts()
        }
    }

    accounts?.forEach { acc ->
        // Keyed, so removing an account does not hand its row's remembered state to the next one.
        key(acc.npub) { DisplayAccount(acc, accountViewModel, accountSessionManager) }
    }
}

@Composable
fun DisplayAccount(
    acc: AccountInfo,
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    val baseUser = rememberAccountUser(acc)

    baseUser?.let {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { accountSessionManager.switchUser(acc) }
                    .padding(16.dp, 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(55.dp)
                                .padding(0.dp),
                    ) {
                        AccountPicture(it, accountViewModel)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) { AccountName(acc, it, accountViewModel) }
                    Column(modifier = Modifier.width(32.dp)) { ActiveMarker(acc, accountViewModel) }
                }
            }

            LogoutButton(acc, accountSessionManager)
        }
    }
}

@Composable
private fun ActiveMarker(
    acc: AccountInfo,
    accountViewModel: AccountViewModel,
) {
    val isCurrentUser by
        remember(accountViewModel, acc.npub) {
            derivedStateOf { accountViewModel.account.userProfile().pubkeyNpub() == acc.npub }
        }

    if (isCurrentUser) {
        Icon(
            symbol = MaterialSymbols.RadioButtonChecked,
            contentDescription = stringRes(Res.string.account_switch_active_account),
            tint = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun AccountPicture(
    user: User,
    accountViewModel: AccountViewModel,
    modifier: Modifier = AccountPictureModifier,
) {
    val userInfo by observeUserInfo(user, accountViewModel)

    RobohashFallbackAsyncImage(
        robot = user.pubkeyHex,
        model = userInfo?.info?.profilePicture(),
        contentDescription = stringRes(Res.string.profile_image),
        modifier = modifier,
        loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
        loadRobohash = LocalDisplaySettings.current.loadRobohash,
        autoPlayGif =
            accountViewModel.settings.autoPlayVideosFlow
                .collectAsStateWithLifecycle()
                .value,
    )
}

@Composable
private fun AccountName(
    acc: AccountInfo,
    user: User,
    accountViewModel: AccountViewModel,
) {
    val info by observeUserInfo(user, accountViewModel)

    info?.let {
        it.info.bestName()?.let { name ->
            CreateTextWithEmoji(
                text = name,
                tags = it.tags,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    Text(
        text = remember(user) { acc.npub.toShortDisplay() },
    )
}

@Composable
private fun LogoutButton(
    acc: AccountInfo,
    accountSessionManager: AccountSessionManager,
) {
    var logoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toaster = LocalAppPlatform.current.rememberToaster()
    if (logoutDialog) {
        LogoutConfirmationDialog(acc, accountSessionManager, scope, toaster) { logoutDialog = false }
    }

    IconButton(
        onClick = { logoutDialog = true },
    ) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.Logout,
            contentDescription = stringRes(Res.string.log_out),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Asks before logging [acc] out, warning about its unpublished scheduled posts. The toast after
 * the log out runs in the caller's [scope] and [toaster], which outlive this dialog.
 */
@Composable
private fun LogoutConfirmationDialog(
    acc: AccountInfo,
    accountSessionManager: AccountSessionManager,
    scope: CoroutineScope,
    toaster: Toaster,
    onDismiss: () -> Unit,
) {
    val scheduledPostsLogoutToastZeroStr = stringRes(Res.string.scheduled_posts_logout_toast_zero)
    val scheduledPostStore = LocalAppServices.current.scheduledPostStore
    val accountHex = remember(acc) { decodePublicKeyAsHexOrNull(acc.npub) }
    val allPosts by scheduledPostStore.flow
        .collectAsStateWithLifecycle()
    val unpublishedCount by remember(accountHex) {
        derivedStateOf {
            if (accountHex == null) {
                0
            } else {
                allPosts.count {
                    it.accountPubkey == accountHex &&
                        (
                            it.status == ScheduledPostStatus.PENDING ||
                                it.status == ScheduledPostStatus.PUBLISHING ||
                                it.status == ScheduledPostStatus.FAILED
                        )
                }
            }
        }
    }
    // The redesigned prompt the key-storage questions use, its badge red when posts would be lost.
    PromptDialog(
        symbol = MaterialSymbols.AutoMirrored.Logout,
        title = stringRes(Res.string.log_out),
        description =
            if (unpublishedCount > 0) {
                pluralStringRes(Res.plurals.scheduled_posts_logout_warning, unpublishedCount, unpublishedCount)
            } else {
                stringRes(Res.string.are_you_sure_you_want_to_log_out)
            },
        confirmText = stringRes(Res.string.log_out),
        onConfirm = {
            // Snapshot the count *now* so the user-facing Toast matches what
            // the dialog displayed, even if the store mutates between this
            // tap and the cleanup completing.
            val confirmedCount = unpublishedCount
            onDismiss()
            // Guard against a malformed npub: skip the Toast so we don't
            // claim "Logged out" when logOff's coroutine bails early.
            if (accountHex == null) return@PromptDialog
            accountSessionManager.logOff(acc)
            // The plural depends on the snapshot taken above, so it cannot be
            // read in composition; this onClick borrows the screen's scope.
            scope.launch {
                val toastMessage =
                    if (confirmedCount > 0) {
                        loadPluralStringRes(
                            Res.plurals.scheduled_posts_logout_toast,
                            confirmedCount,
                            confirmedCount,
                        )
                    } else {
                        scheduledPostsLogoutToastZeroStr
                    }
                toaster.show(toastMessage, long = false)
            }
        },
        dismissText = stringRes(Res.string.cancel),
        onDismiss = onDismiss,
        isError = unpublishedCount > 0,
        dismissible = true,
    )
}

/**
 * The account switcher as a dropdown, for the docked drawer: one row per saved account (a check
 * on the active one), then Add account and Log out. Callers compose it whether or not it is
 * [expanded], because the dialogs it opens outlive the menu.
 */
@Composable
fun AccountSwitchMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    var addAccount by remember { mutableStateOf(false) }
    var logoutAccount by remember { mutableStateOf<AccountInfo?>(null) }
    val scope = rememberCoroutineScope()
    val toaster = LocalAppPlatform.current.rememberToaster()
    val currentNpub = remember(accountViewModel) { accountViewModel.account.userProfile().pubkeyNpub() }

    RoundedDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Collected only while the menu is open: the drawer composes this menu for its whole life.
        val accounts by accountSessionManager.localPreferences
            .accountsFlow()
            .collectAsStateWithLifecycle()

        // accountsFlow() stays null until allSavedAccounts() loads it, as in DisplayAllAccounts.
        LaunchedEffect(Unit) {
            if (accounts == null) {
                accountSessionManager.localPreferences.allSavedAccounts()
            }
        }

        RoundedMenuSection {
            accounts?.forEach { acc ->
                key(acc.npub) {
                    AccountMenuItem(acc, isCurrent = acc.npub == currentNpub, accountViewModel) {
                        onDismiss()
                        if (acc.npub != currentNpub) accountSessionManager.switchUser(acc)
                    }
                }
            }
        }

        RoundedMenuSection {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.account_switch_add_account_btn)) },
                leadingIcon = { Icon(symbol = MaterialSymbols.PersonAdd, contentDescription = null, modifier = Size20Modifier) },
                modifier = RoundedMenuItemModifier,
                onClick = {
                    onDismiss()
                    addAccount = true
                },
            )

            accounts?.firstOrNull { it.npub == currentNpub }?.let { current ->
                DropdownMenuItem(
                    text = { Text(stringRes(Res.string.log_out)) },
                    leadingIcon = { Icon(symbol = MaterialSymbols.AutoMirrored.Logout, contentDescription = null, modifier = Size20Modifier) },
                    modifier = RoundedMenuItemModifier,
                    onClick = {
                        onDismiss()
                        logoutAccount = current
                    },
                )
            }
        }
    }

    if (addAccount) {
        AddAccountDialog(null, accountSessionManager) { addAccount = false }
    }

    logoutAccount?.let {
        LogoutConfirmationDialog(it, accountSessionManager, scope, toaster) { logoutAccount = null }
    }
}

@Composable
private fun AccountMenuItem(
    acc: AccountInfo,
    isCurrent: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val user = rememberAccountUser(acc) ?: return

    DropdownMenuItem(
        text = { Column { AccountName(acc, user, accountViewModel) } },
        leadingIcon = { AccountPicture(user, accountViewModel, AccountMenuPictureModifier) },
        trailingIcon =
            if (isCurrent) {
                {
                    Icon(
                        symbol = MaterialSymbols.Check,
                        contentDescription = stringRes(Res.string.account_switch_active_account),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        modifier = RoundedMenuItemModifier,
        onClick = onClick,
    )
}

private val AccountMenuPictureModifier = Modifier.size(32.dp).clip(CircleShape)

/** The cached [User] behind a saved account, created off the main thread when it is not cached yet. */
@Composable
private fun rememberAccountUser(acc: AccountInfo): User? {
    var baseUser by remember(acc) {
        mutableStateOf(
            decodePublicKeyAsHexOrNull(acc.npub)?.let {
                LocalCache.getUserIfExists(it)
            },
        )
    }

    if (baseUser == null) {
        LaunchedEffect(key1 = acc.npub) {
            launch(Dispatchers.IO) {
                baseUser =
                    decodePublicKeyAsHexOrNull(acc.npub)?.let {
                        LocalCache.getOrCreateUser(it)
                    }
            }
        }
    }

    return baseUser
}
