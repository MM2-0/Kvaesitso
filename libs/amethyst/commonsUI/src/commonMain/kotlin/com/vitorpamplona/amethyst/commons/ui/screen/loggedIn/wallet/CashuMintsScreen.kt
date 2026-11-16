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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip60Cashu.CashuMintDirectoryEntry
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cashu_add_mint
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_matches_header
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_popular_header
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_reachable
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_reachable_named
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_unreachable
import com.vitorpamplona.amethyst.commons.resources.cashu_mint_url
import com.vitorpamplona.amethyst.commons.resources.cashu_mints
import com.vitorpamplona.amethyst.commons.resources.cashu_remove_mint
import com.vitorpamplona.amethyst.commons.resources.cashu_verify
import com.vitorpamplona.amethyst.commons.resources.cashu_wallet_autosaves
import com.vitorpamplona.amethyst.commons.resources.cashu_wallet_saving
import com.vitorpamplona.amethyst.commons.resources.wallet_add_cashu_title
import com.vitorpamplona.amethyst.commons.resources.wallet_edit_cashu_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.CashuWalletCreateState
import com.vitorpamplona.amethyst.commons.wallet.CashuWalletViewModel
import com.vitorpamplona.amethyst.commons.wallet.MintPingState

/**
 * Manages the mints a Cashu wallet uses (and creates the wallet on the first
 * mint added). Reached only from Cashu Wallet Settings → "My mints" once a
 * wallet exists; first-time setup comes in via the find-or-create wizard. Not
 * an "add wallet" entry point — discovery/creation lives in the wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashuMintsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = rememberViewModel { CashuWalletViewModel() }
    // Synchronous init so state-flow getters don't hit a null `account` on
    // the first composition pass. init() is idempotent — just sets refs.
    viewModel.init(accountViewModel)

    val existingWallet by viewModel.walletEvent.collectAsState()
    val existingMints by viewModel.mints.collectAsState()
    val isEditMode = existingWallet != null

    val mints = remember { mutableStateListOf<String>() }
    var mintInput by remember { mutableStateOf("") }

    // Kick the one-shot backfill so the mint-URL autocomplete has
    // suggestions on first open instead of waiting for the next relay
    // round-trip. Cheap (one sweep over the existing cache); idempotent.
    LaunchedEffect(Unit) { LocalCache.ensureMintDirectoryBackfilled() }
    val createState by viewModel.createState.collectAsState()

    // Pre-fill the mints list with the existing wallet's mints whenever we
    // enter edit mode (or the existing mints update). Without this, hitting
    // the Edit button silently wipes the user's mint list because the local
    // `mints` state holder starts empty.
    //
    // Mutates `mints` directly rather than going through `publishMints` so the
    // pre-fill never re-publishes the wallet — only genuine user add/remove
    // actions do.
    LaunchedEffect(existingMints) {
        if (existingMints.isNotEmpty()) {
            val current = mints.toSet()
            existingMints.forEach { if (it !in current) mints.add(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringRes(
                            if (isEditMode) Res.string.wallet_edit_cashu_title else Res.string.wallet_add_cashu_title,
                        ),
                    )
                },
                navigationIcon = {
                    if (nav.canPop()) {
                        IconButton(onClick = { nav.popBack() }) {
                            Icon(
                                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                                contentDescription = stringRes(Res.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text(
                text = stringRes(Res.string.cashu_mints),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val verifications by viewModel.mintVerifications.collectAsState()
            mints.forEachIndexed { index, mint ->
                val verifyState = verifications[mint.trim().trimEnd('/')]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = mint,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(
                                onClick = { viewModel.verifyMint(mint) },
                                enabled = verifyState !is MintPingState.Pinging,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                if (verifyState is MintPingState.Pinging) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        stringRes(Res.string.cashu_verify),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                mints.removeAt(index)
                                viewModel.publishMints(mints.toList())
                            }) {
                                Icon(
                                    symbol = MaterialSymbols.Delete,
                                    contentDescription = stringRes(Res.string.cashu_remove_mint),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        when (val vs = verifyState) {
                            is MintPingState.Ok -> {
                                Text(
                                    text =
                                        if (vs.name.isNullOrBlank()) {
                                            stringRes(Res.string.cashu_mint_reachable)
                                        } else {
                                            stringRes(Res.string.cashu_mint_reachable_named, vs.name)
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            is MintPingState.Failed -> {
                                Text(
                                    text = stringRes(Res.string.cashu_mint_unreachable, vs.message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            else -> Unit
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            val pingState by viewModel.mintPingState.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = mintInput,
                    onValueChange = {
                        mintInput = it
                        viewModel.resetMintPing()
                    },
                    label = { Text(stringRes(Res.string.cashu_mint_url)) },
                    placeholder = {
                        Text(
                            "https://mint.example.com",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { viewModel.pingMint(mintInput.trim().trimEnd('/')) },
                    enabled = mintInput.isNotBlank() && pingState !is MintPingState.Pinging,
                ) {
                    if (pingState is MintPingState.Pinging) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringRes(Res.string.cashu_verify))
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = {
                        val trimmed = mintInput.trim().trimEnd('/')
                        if (trimmed.isNotEmpty() && trimmed !in mints) {
                            mints.add(trimmed)
                            mintInput = ""
                            viewModel.resetMintPing()
                            viewModel.publishMints(mints.toList())
                        }
                    },
                    enabled = mintInput.isNotBlank(),
                ) {
                    Icon(
                        MaterialSymbols.Add,
                        contentDescription = stringRes(Res.string.cashu_add_mint),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Live mint directory subscription drives the rich
            // suggestion rows below — open while this screen is on,
            // closed via DisposableEffect when the user backs out so we
            // don't keep relay subscriptions alive after the screen is
            // dismissed.
            DisposableEffect(Unit) {
                viewModel.openMintDirectory()
                onDispose { viewModel.closeMintDirectory() }
            }
            val directoryEntries by viewModel.directory.entries.collectAsState()

            // Empty input → "Popular mints" (the same ranked list the
            // old Browse sheet showed). Typed input → "Matching mints"
            // filtered case-insensitively. Either way we exclude what
            // the user already added so the list isn't padded with
            // disabled rows.
            val suggestions by remember(directoryEntries, mints) {
                derivedStateOf {
                    val typed = mintInput.trim().trimEnd('/').lowercase()
                    val alreadyAdded = mints.map { it.lowercase().trimEnd('/') }.toSet()
                    viewModel.directory
                        .search(typed, limit = 6)
                        .filter { it.url.lowercase().trimEnd('/') !in alreadyAdded }
                }
            }
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringRes(
                            if (mintInput.isBlank()) {
                                Res.string.cashu_mint_popular_header
                            } else {
                                Res.string.cashu_mint_matches_header
                            },
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                MintSuggestionList(
                    suggestions = suggestions,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    verifications = verifications,
                    onVerify = { url -> viewModel.verifyMint(url) },
                    onAdd = { entry ->
                        val trimmed = entry.url.trim().trimEnd('/')
                        if (trimmed.isNotEmpty() && trimmed !in mints) {
                            mints.add(trimmed)
                            viewModel.resetMintPing()
                            viewModel.publishMints(mints.toList())
                        }
                    },
                )
            }

            when (val ps = pingState) {
                is MintPingState.Ok -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text =
                            if (ps.name.isNullOrBlank()) {
                                stringRes(Res.string.cashu_mint_reachable)
                            } else {
                                stringRes(Res.string.cashu_mint_reachable_named, ps.name)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is MintPingState.Failed -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringRes(Res.string.cashu_mint_unreachable, ps.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(16.dp))

            // No Save button: the wallet's kind:17375 + kind:10019 are
            // published the moment the first mint is added and re-published on
            // every later add/remove (see CashuWalletViewModel.publishMints).
            // This line tells the user the screen auto-saves and that the
            // nutzap key is generated for them — the old explicit key picker is
            // gone; advanced key rotation lives in the settings Danger Zone.
            Text(
                text = stringRes(Res.string.cashu_wallet_autosaves),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (createState is CashuWalletCreateState.Saving) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringRes(Res.string.cashu_wallet_saving),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val err = (createState as? CashuWalletCreateState.Error)?.message
            if (err != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Mint directory autocomplete rendered inline under the mint input.
 * Each row uses [MintDirectoryRow] so the user sees follower avatars
 * and recommendation counts the same way they would in a dedicated
 * picker. Each row carries a small Verify button (to check the mint is
 * reachable) and, to its right, a `+` button that adds the mint straight
 * to the wallet's mint list. The verify result renders under the row.
 *
 * Rows are visually separated by an outlined surface and divider so
 * a long list reads as discrete entries instead of merging into a
 * single coloured block.
 */
@Composable
private fun MintSuggestionList(
    suggestions: List<CashuMintDirectoryEntry>,
    accountViewModel: AccountViewModel,
    nav: INav,
    verifications: Map<String, MintPingState>,
    onVerify: (String) -> Unit,
    onAdd: (CashuMintDirectoryEntry) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            suggestions.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                val verifyState = verifications[entry.url.trim().trimEnd('/')]
                Column {
                    MintDirectoryRow(
                        entry = entry,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    ) {
                        OutlinedButton(
                            onClick = { onVerify(entry.url) },
                            enabled = verifyState !is MintPingState.Pinging,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            if (verifyState is MintPingState.Pinging) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    stringRes(Res.string.cashu_verify),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { onAdd(entry) }) {
                            Icon(
                                symbol = MaterialSymbols.Add,
                                contentDescription = stringRes(Res.string.cashu_add_mint),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    when (val vs = verifyState) {
                        is MintPingState.Ok -> {
                            Text(
                                text =
                                    if (vs.name.isNullOrBlank()) {
                                        stringRes(Res.string.cashu_mint_reachable)
                                    } else {
                                        stringRes(Res.string.cashu_mint_reachable_named, vs.name)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            )
                        }
                        is MintPingState.Failed -> {
                            Text(
                                text = stringRes(Res.string.cashu_mint_unreachable, vs.message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
