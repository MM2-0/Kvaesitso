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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.ProfileGalleryType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.resourceId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.gallery_style
import com.vitorpamplona.amethyst.commons.resources.gallery_style_description
import com.vitorpamplona.amethyst.commons.resources.profile_ui_setting_app_recommendations
import com.vitorpamplona.amethyst.commons.resources.profile_ui_setting_badges
import com.vitorpamplona.amethyst.commons.resources.profile_ui_setting_followers_feed
import com.vitorpamplona.amethyst.commons.resources.profile_ui_setting_onchain_wallet
import com.vitorpamplona.amethyst.commons.resources.profile_ui_setting_zap_received_feed
import com.vitorpamplona.amethyst.commons.resources.profile_ui_settings
import com.vitorpamplona.amethyst.commons.resources.settings_section_appearance
import com.vitorpamplona.amethyst.commons.resources.settings_section_profile_sections
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Preview
@Composable
fun ProfileUiSettingsScreenPreview() {
    ThemeComparisonRow {
        ProfileUiSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun ProfileUiSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.profile_ui_settings), nav)
        },
    ) { padding ->
        ProfileUiSettingsContent(accountViewModel.settings.uiSettingsFlow, Modifier.padding(padding))
    }
}

@Composable
fun ProfileUiSettingsContent(
    ui: UiSettingsFlow,
    modifier: Modifier = Modifier,
) {
    val showBadges by ui.showProfileBadges.collectAsStateWithLifecycle()
    val showAppRecommendations by ui.showProfileAppRecommendations.collectAsStateWithLifecycle()
    val showZapReceived by ui.showProfileZapReceivedFeed.collectAsStateWithLifecycle()
    val showFollowers by ui.showProfileFollowersFeed.collectAsStateWithLifecycle()
    val showOnchainWallet by ui.showOnchainWallet.collectAsStateWithLifecycle()
    val gallery by ui.gallerySet.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(Res.string.settings_section_profile_sections) {
            SettingsSwitchTile(
                icon = MaterialSymbols.MilitaryTech,
                title = Res.string.profile_ui_setting_badges,
                checked = showBadges,
                onCheckedChange = { ui.showProfileBadges.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Recommend,
                title = Res.string.profile_ui_setting_app_recommendations,
                checked = showAppRecommendations,
                onCheckedChange = { ui.showProfileAppRecommendations.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Bolt,
                title = Res.string.profile_ui_setting_zap_received_feed,
                checked = showZapReceived,
                onCheckedChange = { ui.showProfileZapReceivedFeed.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.Group,
                title = Res.string.profile_ui_setting_followers_feed,
                checked = showFollowers,
                onCheckedChange = { ui.showProfileFollowersFeed.tryEmit(it) },
            )
            SettingsDivider()
            SettingsSwitchTile(
                icon = MaterialSymbols.AccountBalanceWallet,
                title = Res.string.profile_ui_setting_onchain_wallet,
                checked = showOnchainWallet,
                onCheckedChange = { ui.showOnchainWallet.tryEmit(it) },
            )
        }

        SettingsSection(Res.string.settings_section_appearance) {
            SegmentedChoiceTile(
                icon = MaterialSymbols.Collections,
                title = Res.string.gallery_style,
                description = Res.string.gallery_style_description,
                options = ProfileGalleryType.entries,
                labelRes = { it.resourceId },
                selected = gallery,
                onSelect = { ui.gallerySet.tryEmit(it) },
            )
        }
    }
}
