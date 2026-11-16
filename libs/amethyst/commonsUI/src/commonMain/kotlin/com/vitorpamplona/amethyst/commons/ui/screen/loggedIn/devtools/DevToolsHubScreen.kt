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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

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
import com.vitorpamplona.amethyst.commons.resources.active_subs_title
import com.vitorpamplona.amethyst.commons.resources.devtools_active_subs_desc
import com.vitorpamplona.amethyst.commons.resources.devtools_console_desc
import com.vitorpamplona.amethyst.commons.resources.devtools_console_title
import com.vitorpamplona.amethyst.commons.resources.devtools_event_desc
import com.vitorpamplona.amethyst.commons.resources.devtools_event_title
import com.vitorpamplona.amethyst.commons.resources.devtools_explainer
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_desc
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_title
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_desc
import com.vitorpamplona.amethyst.commons.resources.devtools_nip19_title
import com.vitorpamplona.amethyst.commons.resources.devtools_section_data
import com.vitorpamplona.amethyst.commons.resources.devtools_section_network
import com.vitorpamplona.amethyst.commons.resources.devtools_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import org.jetbrains.compose.resources.StringResource

/**
 * The door into Amethyst's developer tools: the in-app equivalents of nak web,
 * nostr-tools.com, kinds.amethyst.social and a WebSocket console, built on the same Quartz
 * code the app and `amy` use. The relay diagnostics that already existed (active
 * subscriptions) are linked here too, so a developer finds everything in one place.
 */
@Composable
fun DevToolsHubScreen(nav: INav) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_title), nav) },
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
                text = stringRes(Res.string.devtools_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SettingsSection(Res.string.devtools_section_data) {
                HubEntry(MaterialSymbols.Key, Res.string.devtools_nip19_title, Res.string.devtools_nip19_desc) {
                    nav.nav(Route.DevToolsNip19())
                }
                SettingsDivider()
                HubEntry(MaterialSymbols.Code, Res.string.devtools_event_title, Res.string.devtools_event_desc) {
                    nav.nav(Route.DevToolsEventInspector())
                }
                SettingsDivider()
                HubEntry(MaterialSymbols.Numbers, Res.string.devtools_kind_lookup_title, Res.string.devtools_kind_lookup_desc) {
                    nav.nav(Route.DevToolsKinds)
                }
            }

            SettingsSection(Res.string.devtools_section_network) {
                HubEntry(MaterialSymbols.SettingsInputAntenna, Res.string.devtools_console_title, Res.string.devtools_console_desc) {
                    nav.nav(Route.DevToolsRelayConsole())
                }
                SettingsDivider()
                HubEntry(MaterialSymbols.CloudSync, Res.string.active_subs_title, Res.string.devtools_active_subs_desc) {
                    nav.nav(Route.ActiveSubscriptions)
                }
            }
        }
    }
}

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
fun DevToolsHubScreenPreview() {
    ThemeComparisonColumn {
        DevToolsHubScreen(EmptyNav())
    }
}
