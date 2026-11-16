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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.geocaches.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.action_cancel
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_archive
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_archive_confirm
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_edit
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_lock_ftf
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_lock_ftf_explain
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_no_finders
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_qr_warning
import com.vitorpamplona.amethyst.commons.resources.geocache_owner_show_qr
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.QrCodeDrawer
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nipCCGeocaching.firstToFind.FirstToFindResolver
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingRevision
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.launch

/**
 * The owner's lifecycle: edit, archive, lock in the first finder, and show the printable
 * verification code.
 *
 * Everything here republishes the listing at the same address, which is what makes 37516
 * replaceable useful — an archive is not a delete, it is a new revision carrying `archived` in
 * `t`, and every client that already renders the cache picks it up.
 *
 * Archiving and locking in are both irreversible in practice and both get a confirmation. The
 * verification code is not stored by the app at all: this sheet can only show what the owner
 * still holds, which the copy says plainly rather than implying a vault that does not exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeocacheOwnerActions(
    listing: GeocacheListingEvent,
    address: Address,
    logs: GeocacheLogs,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (listing.pubKey != accountViewModel.userProfile().pubkeyHex) return

    var menuOpen by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var confirmLockIn by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val provisionalWinner = remember(listing, logs.finds) { FirstToFindResolver.provisionalWinningLog(listing, logs.finds)?.pubKey }

    IconButton(onClick = { menuOpen = true }) {
        Icon(
            symbol = MaterialSymbols.MoreVert,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(stringRes(Res.string.geocache_owner_edit)) },
            onClick = {
                menuOpen = false
                nav.nav(Route.EditGeocache(address))
            },
        )

        if (!listing.isArchived()) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.geocache_owner_archive)) },
                onClick = {
                    menuOpen = false
                    confirmArchive = true
                },
            )
        }

        if (listing.isFirstToFind() && !FirstToFindResolver.isLockedIn(listing)) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.geocache_owner_lock_ftf)) },
                onClick = {
                    menuOpen = false
                    confirmLockIn = true
                },
            )
        }

        if (listing.requiresVerification()) {
            DropdownMenuItem(
                text = { Text(stringRes(Res.string.geocache_owner_show_qr)) },
                onClick = {
                    menuOpen = false
                    showQr = true
                },
            )
        }
    }

    if (confirmArchive) {
        ConfirmDialog(
            body = stringRes(Res.string.geocache_owner_archive_confirm),
            onConfirm = {
                confirmArchive = false
                scope.launch {
                    runCatchingCancellable {
                        accountViewModel.account.signAndComputeBroadcast(
                            GeocacheListingRevision.archived(listing),
                        )
                    }
                }
            },
            onDismiss = { confirmArchive = false },
        )
    }

    if (confirmLockIn) {
        if (provisionalWinner == null) {
            ConfirmDialog(
                body = stringRes(Res.string.geocache_owner_no_finders),
                onConfirm = { confirmLockIn = false },
                onDismiss = { confirmLockIn = false },
            )
        } else {
            ConfirmDialog(
                body = stringRes(Res.string.geocache_owner_lock_ftf_explain, provisionalWinner.take(8)),
                onConfirm = {
                    confirmLockIn = false
                    scope.launch {
                        runCatchingCancellable {
                            accountViewModel.account.signAndComputeBroadcast(
                                GeocacheListingRevision.withFirstToFindWinner(listing, provisionalWinner),
                            )
                        }
                    }
                },
                onDismiss = { confirmLockIn = false },
            )
        }
    }

    if (showQr) {
        val key = listing.verificationKey()
        ModalBottomSheet(onDismissRequest = { showQr = false }, sheetState = rememberModalSheetState()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (key != null) QrCodeDrawer(key)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringRes(Res.string.geocache_owner_qr_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringRes(Res.string.geocache_owner_archive)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.action_cancel)) } },
    )
}
