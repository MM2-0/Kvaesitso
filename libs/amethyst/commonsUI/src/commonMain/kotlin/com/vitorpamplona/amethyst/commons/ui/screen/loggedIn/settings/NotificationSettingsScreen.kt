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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.notification_settings
import com.vitorpamplona.amethyst.commons.resources.notification_settings_section_display
import com.vitorpamplona.amethyst.commons.resources.show_messages_in_notifications_setting_description
import com.vitorpamplona.amethyst.commons.resources.show_messages_in_notifications_setting_title
import com.vitorpamplona.amethyst.commons.resources.split_notifications_setting_description
import com.vitorpamplona.amethyst.commons.resources.split_notifications_setting_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.NotificationCategorySettings
import com.vitorpamplona.amethyst.commons.ui.platform.NotificationDeliverySettings
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Composable
fun NotificationSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(id = Res.string.notification_settings), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            NotificationDeliverySettings(accountViewModel)
            DisplaySection(accountViewModel)
            NotificationCategorySettings()
        }
    }
}

@Composable
private fun DisplaySection(accountViewModel: AccountViewModel) {
    val splitByFollows by accountViewModel.account.settings.splitNotificationsEnabled
        .collectAsStateWithLifecycle()
    val showMessages by accountViewModel.account.settings.showMessagesInNotifications
        .collectAsStateWithLifecycle()

    SettingsSection(Res.string.notification_settings_section_display) {
        SettingsSwitchTile(
            icon = MaterialSymbols.Forum,
            title = Res.string.split_notifications_setting_title,
            description = Res.string.split_notifications_setting_description,
            checked = splitByFollows,
            onCheckedChange = { accountViewModel.account.settings.toggleSplitNotificationsEnabled() },
        )
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Mail,
            title = Res.string.show_messages_in_notifications_setting_title,
            description = Res.string.show_messages_in_notifications_setting_description,
            checked = showMessages,
            onCheckedChange = { accountViewModel.account.settings.toggleShowMessagesInNotifications() },
        )
    }
}

@Preview
@Composable
fun NotificationSettingsScreenPreview() {
    ThemeComparisonColumn {
        NotificationSettingsScreen(mockAccountViewModel(), EmptyNav())
    }
}
