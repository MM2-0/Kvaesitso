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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_backup
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_backup_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_coordinators
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_coordinators_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_explainer
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_keypackages
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_keypackages_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_link
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_link_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_migrate
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_migrate_desc
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_section_device
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_section_service
import com.vitorpamplona.amethyst.commons.resources.cordn_hub_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import org.jetbrains.compose.resources.StringResource

/**
 * One door into cordn, instead of five rows in the account settings list.
 *
 * cordn's pages — coordinators, key packages, link inspection, backup,
 * migration — are each visited rarely and only by someone already thinking
 * about cordn. As flat entries they made it the biggest feature in that list
 * by count and among the least used, pushing everything else down. Grouping
 * them costs one tap and keeps every page searchable under a name a user
 * would actually look for.
 *
 * Built from [SettingsSection] and [SettingsItem] like every other settings
 * page. The first version hand-rolled its own cards, which made the one screen
 * reached FROM the settings list the one screen that did not look like it.
 */
@Composable
fun CordnHubScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.cordn_hub_title), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringRes(Res.string.cordn_hub_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            // Split by what the page is about rather than listed flat: the
            // first three are about a coordinator, the last two about this
            // device's own state.
            SettingsSection(Res.string.cordn_hub_section_service) {
                HubEntry(MaterialSymbols.Dns, Res.string.cordn_hub_coordinators, Res.string.cordn_hub_coordinators_desc) {
                    nav.nav(Route.CordnCoordinators)
                }
                SettingsDivider()
                HubEntry(MaterialSymbols.Key, Res.string.cordn_hub_keypackages, Res.string.cordn_hub_keypackages_desc) {
                    nav.nav(Route.CordnKeyPackages)
                }
                SettingsDivider()
                HubEntry(MaterialSymbols.Link, Res.string.cordn_hub_link, Res.string.cordn_hub_link_desc) {
                    nav.nav(Route.CordnLink())
                }
            }

            SettingsSection(Res.string.cordn_hub_section_device) {
                HubEntry(MaterialSymbols.Save, Res.string.cordn_hub_backup, Res.string.cordn_hub_backup_desc) {
                    nav.nav(Route.CordnBackup)
                }
                SettingsDivider()
                HubEntry(MaterialSymbols.SwapHoriz, Res.string.cordn_hub_migrate, Res.string.cordn_hub_migrate_desc) {
                    nav.nav(Route.CordnMigrate)
                }
            }
        }
    }
}

/**
 * A navigation row that keeps its description.
 *
 * [SettingsItem] is the plain navigation row and has no room for one. Every
 * page behind this hub is obscure enough that its title alone does not say
 * what it does, so the row with a description — and a chevron supplied as the
 * trailing slot, since [SettingsControlRow] is built for inline controls —
 * is the honest fit.
 */
@Composable
private fun HubEntry(
    icon: MaterialSymbol,
    title: StringResource,
    description: StringResource,
    onClick: () -> Unit,
) {
    SettingsControlRow(
        icon = icon,
        title = stringRes(title),
        description = stringRes(description),
        onClick = onClick,
    ) {
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
fun CordnHubScreenPreview() {
    ThemeComparisonColumn {
        CordnHubScreen(mockAccountViewModel(), EmptyNav())
    }
}
