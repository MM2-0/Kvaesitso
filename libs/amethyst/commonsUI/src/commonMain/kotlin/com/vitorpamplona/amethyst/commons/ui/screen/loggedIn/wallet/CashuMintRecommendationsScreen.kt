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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_add_recommendation
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_delete_confirm_body
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_delete_confirm_title
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_delete_recommendation
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_my_recommendations
import com.vitorpamplona.amethyst.commons.resources.cashu_settings_no_recommendations
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wallet.CashuWalletViewModel
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import androidx.compose.material3.Icon as Material3Icon

/**
 * NIP-87 mint recommendations management, split out of the Cashu wallet
 * settings hub. Lets the user publicly vouch for mints they trust (kind:38000)
 * and retract those recommendations (NIP-09). The list is fed by
 * [CashuWalletState.ownRecommendations], which the wallet's own filter
 * assembler pulls automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashuMintRecommendationsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: CashuWalletViewModel = rememberViewModel { CashuWalletViewModel() }
    viewModel.init(accountViewModel)

    val recommendations by viewModel.ownRecommendations.collectAsState()
    var pendingDelete by remember { mutableStateOf<MintRecommendationEvent?>(null) }

    // Kick the one-shot directory backfill so the autocomplete is useful
    // on first screen open instead of waiting for new relay deliveries.
    LaunchedEffect(Unit) { LocalCache.ensureMintDirectoryBackfilled() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(Res.string.cashu_settings_my_recommendations)) },
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
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePaddingSafe()
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                AddRecommendationSection(
                    recommendations = recommendations,
                    onRecommendMint = { viewModel.recommendMint(it) },
                )
            }

            if (recommendations.isEmpty()) {
                item { EmptyRecommendationsHint() }
            } else {
                items(recommendations, key = { it.id }) { event ->
                    RecommendationRow(event = event, onDelete = { pendingDelete = event })
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringRes(Res.string.cashu_settings_delete_confirm_title)) },
            text = {
                Text(
                    stringRes(
                        Res.string.cashu_settings_delete_confirm_body,
                        target.mintUrls().firstOrNull() ?: target.dTag().ifEmpty { target.id.take(8) },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecommendation(target)
                        pendingDelete = null
                    },
                ) { Text(stringRes(Res.string.cashu_settings_delete_recommendation)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun AddRecommendationSection(
    recommendations: List<MintRecommendationEvent>,
    onRecommendMint: (String) -> Unit,
) {
    var newRecommendationInput by remember { mutableStateOf("") }

    val alreadyRecommended =
        remember(recommendations) {
            recommendations
                .flatMap { it.mintUrls() }
                .map { it.lowercase().trimEnd('/') }
                .toSet()
        }
    val suggestions by remember(newRecommendationInput, alreadyRecommended) {
        derivedStateOf {
            val typed = newRecommendationInput.trim().trimEnd('/').lowercase()
            // Only react to what the user types — never show
            // the full directory on an empty field, which
            // would dump every mint we've ever seen as
            // unsolicited suggestions.
            if (typed.isEmpty()) {
                emptyList()
            } else {
                LocalCache.mintDirectory
                    .suggest(typed, limit = 6)
                    .filter { it != typed && it !in alreadyRecommended }
            }
        }
    }
    AddRecommendationRow(
        input = newRecommendationInput,
        onInputChange = { newRecommendationInput = it },
        canAdd =
            newRecommendationInput.isNotBlank() &&
                newRecommendationInput.trim().trimEnd('/').lowercase() !in alreadyRecommended,
        onAdd = {
            val trimmed = newRecommendationInput.trim().trimEnd('/')
            if (trimmed.isNotEmpty()) {
                onRecommendMint(trimmed)
                newRecommendationInput = ""
            }
        },
    )
    if (suggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        RecommendationSuggestionList(
            suggestions = suggestions,
            onPick = { url ->
                // One-tap recommend: publish straight from
                // the suggestion and clear the field so the
                // user can chain multiple adds without
                // re-tapping the text input.
                onRecommendMint(url)
                newRecommendationInput = ""
            },
        )
    }
}

@Composable
private fun AddRecommendationRow(
    input: String,
    onInputChange: (String) -> Unit,
    canAdd: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(stringRes(Res.string.cashu_settings_add_recommendation)) },
            placeholder = { Text("https://mint.example.com") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onAdd, enabled = canAdd) {
            Icon(
                symbol = MaterialSymbols.Add,
                contentDescription = stringRes(Res.string.cashu_settings_add_recommendation),
                modifier = Modifier.size(22.dp),
                tint =
                    if (canAdd) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun RecommendationSuggestionList(
    suggestions: List<String>,
    onPick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            suggestions.forEach { url ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(url) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Material3Icon(
                        imageVector = CustomHashTagIcons.Cashu,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        symbol = MaterialSymbols.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecommendationsHint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringRes(Res.string.cashu_settings_no_recommendations),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecommendationRow(
    event: MintRecommendationEvent,
    onDelete: () -> Unit,
) {
    // Primary label: first u-tag URL when present, falling back to the
    // d-tag (which may be the mint's announcement pubkey or the URL itself
    // when no announcement was cached at publish time).
    val mintUrl = remember(event.id) { event.mintUrls().firstOrNull() }
    // dTag() is "" when the tag is missing.
    val dTag = remember(event.id) { event.dTag().ifEmpty { null } }
    val title = mintUrl ?: dTag ?: event.id.take(16)
    val subtitle = if (mintUrl != null && dTag != null && mintUrl != dTag) dTag else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.ThumbUp,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (event.content.isNotBlank()) {
                    Text(
                        text = event.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    symbol = MaterialSymbols.Delete,
                    contentDescription = stringRes(Res.string.cashu_settings_delete_recommendation),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ============================================================
// @Preview composables — Android Studio rendering only
// ============================================================
// Each preview is rendered in both dark and light themes via
// ThemeComparisonColumn so we can eyeball both at once. Plain-data
// composables only — anything that takes AccountViewModel / INav /
// CashuWalletViewModel can't be cheaply mocked, so those are skipped.

/** Synthesise a [MintRecommendationEvent] for previews without touching the signer. */
private fun fakeRecommendation(
    mintUrl: String,
    review: String = "",
): MintRecommendationEvent =
    MintRecommendationEvent(
        id = "0".repeat(64),
        pubKey = "1".repeat(64),
        createdAt = 1_700_000_000L,
        tags =
            arrayOf(
                arrayOf("d", mintUrl),
                arrayOf("k", "38172"),
                arrayOf("u", mintUrl),
            ),
        content = review,
        sig = "2".repeat(128),
    )

@Preview
@Composable
fun EmptyRecommendationsHintPreview() {
    ThemeComparisonColumn {
        EmptyRecommendationsHint()
    }
}

@Preview
@Composable
fun AddRecommendationRowEmptyPreview() {
    ThemeComparisonColumn {
        AddRecommendationRow(
            input = "",
            onInputChange = {},
            canAdd = false,
            onAdd = {},
        )
    }
}

@Preview
@Composable
fun AddRecommendationRowTypingPreview() {
    ThemeComparisonColumn {
        AddRecommendationRow(
            input = "https://mint.minibits.cash",
            onInputChange = {},
            canAdd = true,
            onAdd = {},
        )
    }
}

@Preview
@Composable
fun RecommendationSuggestionListPreview() {
    ThemeComparisonColumn {
        RecommendationSuggestionList(
            suggestions =
                listOf(
                    "https://mint.minibits.cash/bitcoin",
                    "https://mint.coinos.io",
                    "https://nutmix.cash",
                ),
            onPick = {},
        )
    }
}

@Preview
@Composable
fun RecommendationRowPreview() {
    ThemeComparisonColumn {
        RecommendationRow(
            event = fakeRecommendation("https://mint.minibits.cash/bitcoin"),
            onDelete = {},
        )
    }
}

@Preview
@Composable
fun RecommendationRowWithReviewPreview() {
    ThemeComparisonColumn {
        RecommendationRow(
            event =
                fakeRecommendation(
                    mintUrl = "https://mint.coinos.io",
                    review = "Fast and reliable, been using for months.",
                ),
            onDelete = {},
        )
    }
}
