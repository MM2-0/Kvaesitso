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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.MandatoryDrawerItems
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.drawer_settings
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_always_on
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_description
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_hidden
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_hidden_count
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_hide_all
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_sections
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_show_all
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_title
import com.vitorpamplona.amethyst.commons.resources.drawer_settings_visible
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSection
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSectionVisibility
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSections
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.CatalogCard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.CatalogRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.FlushPickerEditsOnExit
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.LeadingGlyph
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.PickerHeroCard
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.PickerSectionHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.RestoreDefaultRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.TogglePill
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.rememberExpandedKeys
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Composable
@Preview(device = "spec:width=2100px,height=2340px,dpi=440")
fun DrawerSettingsScreenPreview() {
    ThemeComparisonRow {
        DrawerSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun DrawerSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.drawer_settings), nav)
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            DrawerSettingsContent(accountViewModel)
        }
    }
}

/**
 * Show/hide editor for the side menu's rows. It renders [DrawerSections] directly — the very list the
 * drawer renders — so a destination added to a section shows up here with no work, and a row that
 * exists here always exists there.
 */
@Composable
fun DrawerSettingsContent(accountViewModel: AccountViewModel) {
    // Per-account, synced through the NIP-78 app-specific data event.
    val savedHidden by accountViewModel.hiddenDrawerItemsFlow().collectAsStateWithLifecycle()

    // All show/hide logic lives in the holder (unit-tested); the composable only renders and forwards
    // events. Each edit republishes the account's NIP-78 settings event. syncFrom re-seeds when the
    // saved set changes elsewhere.
    //
    // Deliberately unkeyed. The holder captures this `accountViewModel` in its persist lambda, so a
    // holder that outlived an account switch would write account A's edits to account B. It cannot:
    // AccountScopedViewModelStore wraps the whole logged-in tree in `key(account.signer.pubKey)`,
    // so a switch disposes this composable (and the NavController with it) and re-runs this remember
    // against the new account's ViewModel. Keying on accountViewModel here would be a no-op that
    // implies the subtree survives a switch — if that ever becomes true, this comment is the bug.
    val state = remember { DrawerSettingsState(savedHidden) { accountViewModel.changeHiddenDrawerItems(it) } }
    LaunchedEffect(savedHidden) { state.syncFrom(savedHidden) }

    // Edits publish on a debounce; make sure leaving the screen doesn't strand the last one.
    FlushPickerEditsOnExit(accountViewModel)

    // Sections start collapsed: expanded, they are ~50 rows of scrolling. The header's hidden
    // counter is what tells the user which one to open.
    val expandedSections = rememberExpandedKeys<DrawerSectionId>()

    val totalHidden = state.totalHidden()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))

        SummaryCard(totalHidden)

        RestoreDefaultRow(onClick = { state.restoreDefault() })

        Spacer(Modifier.height(4.dp))

        PickerSectionHeader(title = stringRes(Res.string.drawer_settings_sections))

        // A section with no catalog rows has nothing to configure (Create is composer entry points),
        // so it isn't listed here even though the drawer renders it.
        DrawerSections.forEach { section ->
            if (section.items.isEmpty()) return@forEach
            SectionCard(
                section = section,
                state = state,
                expanded = expandedSections.isExpanded(section.id),
                onToggleExpand = { expandedSections.toggle(section.id) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** What the setting does and how far from stock the menu currently is. */
@Composable
private fun SummaryCard(totalHidden: Int) {
    PickerHeroCard(
        title = stringRes(Res.string.drawer_settings_title),
        trailing = {
            Text(
                text = pluralStringRes(Res.plurals.drawer_settings_hidden_count, totalHidden, totalHidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        Text(
            text = stringRes(Res.string.drawer_settings_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(
    section: DrawerSection,
    state: DrawerSettingsState,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
) {
    // Each card reads the same coarse `hidden` state, so without derivedStateOf a toggle in one
    // section would recompose (and re-count) all of them.
    val hiddenHere by remember(section) { derivedStateOf { state.hiddenCount(section) } }

    CatalogCard(
        icon = section.icon,
        title = stringRes(section.titleRes),
        expanded = expanded,
        onToggleExpand = onToggleExpand,
        trailing = {
            if (hiddenHere > 0) {
                Text(
                    text = pluralStringRes(Res.plurals.drawer_settings_hidden_count, hiddenHere, hiddenHere),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        // Bulk actions: turning ~29 feed rows off one at a time is the kind of chore that makes
        // people give up halfway and leave the menu in a worse state than they found it.
        if (DrawerSectionVisibility.hasHideableRows(section)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { state.showAll(section) }) {
                    Text(stringRes(Res.string.drawer_settings_show_all))
                }
                TextButton(onClick = { state.hideAll(section) }) {
                    Text(stringRes(Res.string.drawer_settings_hide_all))
                }
            }
        }

        section.items.forEach { item ->
            val def = NavBarCatalog[item] ?: return@forEach
            val mandatory = item in MandatoryDrawerItems
            val visible = state.isVisible(item)
            CatalogRow(
                leading = { LeadingGlyph(def.icon, def.imageVector) },
                label = stringRes(def.labelRes),
                onToggle = if (mandatory) null else ({ state.toggle(item) }),
            ) {
                VisibilityPill(visible = visible, mandatory = mandatory, onClick = { state.toggle(item) })
            }
        }
    }
}

/**
 * Filled "Visible" / outlined "Hidden" — the bottom bar's Add/Added pill, saying what this screen
 * says instead. A mandatory row gets a locked "Always on" badge: it reads as deliberately fixed
 * rather than as a control that ignores taps.
 */
@Composable
private fun VisibilityPill(
    visible: Boolean,
    mandatory: Boolean,
    onClick: () -> Unit,
) {
    // One branch decides both halves of the pill, so a label can't drift away from its glyph.
    val (labelRes, icon) =
        when {
            mandatory -> Res.string.drawer_settings_always_on to MaterialSymbols.Lock
            visible -> Res.string.drawer_settings_visible to MaterialSymbols.Visibility
            else -> Res.string.drawer_settings_hidden to MaterialSymbols.VisibilityOff
        }

    TogglePill(
        on = visible,
        label = stringRes(labelRes),
        icon = icon,
        enabled = !mandatory,
        onClick = onClick,
    )
}
