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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.backup_keys_nudge_already_saved
import com.vitorpamplona.amethyst.commons.resources.backup_keys_nudge_backup_now
import com.vitorpamplona.amethyst.commons.resources.backup_keys_nudge_body
import com.vitorpamplona.amethyst.commons.resources.backup_keys_nudge_dismiss
import com.vitorpamplona.amethyst.commons.resources.backup_keys_nudge_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Whether Home shows [BackupKeysNudge]: for a freshly generated account that hasn't saved its
 * secret key yet. False until the flag is read, so the card never flashes for everyone else.
 */
@Composable
fun rememberShowBackupKeysNudge(accountViewModel: AccountViewModel): Boolean {
    val npub =
        remember(accountViewModel) {
            accountViewModel.account.signer.pubKey
                .hexToByteArray()
                .toNpub()
        }

    val flow by produceState<StateFlow<Boolean>?>(initialValue = null, key1 = npub) {
        value = accountViewModel.host.hasBackedUpKeys(npub)
    }
    val hasBackedUp = flow?.collectAsStateWithLifecycle()?.value ?: true
    return !hasBackedUp
}

/**
 * Soft, dismissible "back up your keys" card at the top of the home feed, shown while
 * [rememberShowBackupKeysNudge] says so. It never blocks navigation: the user either backs up
 * (navigates to [Route.AccountBackup]) or confirms they already saved the key. Both actions flip
 * the per-account backed-up flag so the card stops appearing.
 */
@Composable
fun BackupKeysNudge(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val npub =
        remember(accountViewModel) {
            accountViewModel.account.signer.pubKey
                .hexToByteArray()
                .toNpub()
        }

    BackupKeysNudgeCard(
        modifier = modifier,
        onBackupNow = {
            // Best-effort: opening the backup screen counts as backing up so the
            // nudge doesn't linger after the user follows through.
            scope.launch { accountViewModel.host.setHasBackedUpKeys(npub, true) }
            nav.nav(Route.AccountBackup)
        },
        onAlreadySaved = {
            scope.launch { accountViewModel.host.setHasBackedUpKeys(npub, true) }
        },
    )
}

@Composable
private fun BackupKeysNudgeCard(
    modifier: Modifier = Modifier,
    onBackupNow: () -> Unit,
    onAlreadySaved: () -> Unit,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    symbol = MaterialSymbols.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = StdHorzSpacer)
                Text(
                    text = stringRes(Res.string.backup_keys_nudge_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onAlreadySaved) {
                    Icon(
                        symbol = MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.backup_keys_nudge_dismiss),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = StdVertSpacer)

            Text(
                text = stringRes(Res.string.backup_keys_nudge_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = StdVertSpacer)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onAlreadySaved) {
                    Text(stringRes(Res.string.backup_keys_nudge_already_saved))
                }
                Spacer(modifier = StdHorzSpacer)
                Button(onClick = onBackupNow) {
                    Text(stringRes(Res.string.backup_keys_nudge_backup_now))
                }
            }
        }
    }
}
