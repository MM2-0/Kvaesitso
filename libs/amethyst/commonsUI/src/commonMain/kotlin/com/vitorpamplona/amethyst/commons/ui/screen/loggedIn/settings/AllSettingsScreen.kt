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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_confirm_action
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_confirm_body
import com.vitorpamplona.amethyst.commons.resources.reset_marmot_confirm_title
import com.vitorpamplona.amethyst.commons.resources.settings_search_no_results
import com.vitorpamplona.amethyst.commons.ui.keystorage.LocalKeyStorage
import com.vitorpamplona.amethyst.commons.ui.navigation.host.LocalNavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SettingsSearchTopBar
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsItem
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsEntry
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsIcon
import com.vitorpamplona.amethyst.commons.ui.settings.filterSettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

@Preview
@Composable
fun AllSettingsScreenPreview() {
    ThemeComparisonColumn {
        AllSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun AllSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showResetMarmotDialog by remember { mutableStateOf(false) }
    val isResettingMarmot by accountViewModel.isResettingMarmot.collectAsState()
    val scrollState = rememberScrollState()
    val hasPrivateKey = accountViewModel.account.settings.keyPair.privKey != null

    var query by rememberSaveable { mutableStateOf("") }

    // The catalog is structurally stable for the screen's lifetime, so it is rebuilt only when
    // an input actually changes — not on every keystroke. `onResetMarmot` reads the volatile
    // `isResettingMarmot` through `rememberUpdatedState` so the memoized closure never goes stale.
    val onResetMarmot by rememberUpdatedState(newValue = { if (!isResettingMarmot) showResetMarmotDialog = true })
    val appPlatform = LocalAppPlatform.current
    val destinations = LocalNavDestinations.current
    val hasKeyStorage = LocalKeyStorage.current != null
    val catalog =
        remember(hasPrivateKey, nav, uriHandler, appPlatform, destinations, hasKeyStorage) {
            buildSettingsCatalog(
                nav = nav,
                uriHandler = uriHandler,
                hasPrivateKey = hasPrivateKey,
                legal = appPlatform.legalSettingsCategory(uriHandler),
                onResetMarmot = { onResetMarmot() },
                canOpen = { route -> destinations?.has(route) ?: true },
                hasKeyStorage = hasKeyStorage,
            )
        }

    // stringRes reads from composition, so every label the filter could touch is
    // resolved up front and handed over as a plain map lookup. That keeps
    // filterSettings itself Compose-free and unit-testable.
    val labels = rememberCatalogLabels(catalog)
    val filtered =
        filterSettings(
            catalog = catalog,
            query = query,
            stringLookup = { labels[it].orEmpty() },
        )

    Scaffold(
        topBar = {
            SettingsSearchTopBar(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" },
                nav = nav,
            )
        },
        bottomBar = {
            AppBottomBar(Route.AllSettings, nav, accountViewModel) { route ->
                if (route == Route.AllSettings) {
                    scope.launch { scrollState.animateScrollTo(0) }
                } else {
                    nav.navBottomBar(route)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Filtering happens in place: the list below is the search result. A blank query
            // yields the whole catalog, narrowing as the user types via the top-bar search field.
            if (filtered.isEmpty()) {
                SettingsSearchEmptyState(
                    query = query,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(scrollState)
                            .padding(top = 12.dp)
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    filtered.forEach { category -> SettingsCategoryCard(category) }
                }
            }
        }
    }

    if (showResetMarmotDialog) {
        ResetMarmotStateDialog(
            onConfirm = {
                showResetMarmotDialog = false
                // Runs on the account's scope and reports its outcome itself, so it finishes even
                // if the user leaves this screen.
                accountViewModel.resetMarmotState()
            },
            onDismiss = { showResetMarmotDialog = false },
        )
    }
}

@Composable
private fun SettingsSearchEmptyState(
    query: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringRes(Res.string.settings_search_no_results, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun SettingsCategoryCard(category: SettingsCategory) {
    SettingsSection(category.titleRes, category.isDanger) {
        category.entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            SettingsEntryRow(entry)
        }
    }
}

@Composable
private fun SettingsEntryRow(entry: SettingsEntry) {
    when (val icon = entry.icon) {
        is SettingsIcon.Symbol ->
            SettingsItem(
                title = entry.titleRes,
                icon = icon.symbol,
                isDanger = entry.isDanger,
                onClick = entry.onClick,
            )
        is SettingsIcon.Painter ->
            SettingsItem(
                title = entry.titleRes,
                iconPainter = icon.iconPainter,
                iconPainterRef = icon.iconPainterRef,
                isDanger = entry.isDanger,
                onClick = entry.onClick,
            )
    }
}

@Composable
private fun ResetMarmotStateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringRes(Res.string.reset_marmot_confirm_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(text = stringRes(Res.string.reset_marmot_confirm_body))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringRes(Res.string.reset_marmot_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/** Resolves every title and keyword string in [catalog] so the filter can run outside composition. */
@Composable
private fun rememberCatalogLabels(catalog: List<SettingsCategory>): Map<StringResource, String> {
    val out = mutableMapOf<StringResource, String>()
    catalog.forEach { category ->
        out[category.titleRes] = stringRes(category.titleRes)
        category.entries.forEach { entry ->
            out[entry.titleRes] = stringRes(entry.titleRes)
            entry.keywordsRes?.let { out[it] = stringRes(it) }
        }
    }
    return out
}
