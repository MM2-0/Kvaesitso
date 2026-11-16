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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.WarningType
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.parseWarningType
import com.vitorpamplona.amethyst.commons.model.resourceId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.blocked_users
import com.vitorpamplona.amethyst.commons.resources.filter_spam_from_strangers_explainer
import com.vitorpamplona.amethyst.commons.resources.filter_spam_from_strangers_title
import com.vitorpamplona.amethyst.commons.resources.hidden_words
import com.vitorpamplona.amethyst.commons.resources.hide_community_rules_violations_explainer
import com.vitorpamplona.amethyst.commons.resources.hide_community_rules_violations_title
import com.vitorpamplona.amethyst.commons.resources.max_hashtag_limit_explainer
import com.vitorpamplona.amethyst.commons.resources.max_hashtag_limit_title
import com.vitorpamplona.amethyst.commons.resources.report_warning_threshold_explainer
import com.vitorpamplona.amethyst.commons.resources.report_warning_threshold_title
import com.vitorpamplona.amethyst.commons.resources.security_filters
import com.vitorpamplona.amethyst.commons.resources.security_section_blocked_content
import com.vitorpamplona.amethyst.commons.resources.security_section_filtering_preferences
import com.vitorpamplona.amethyst.commons.resources.security_unlimited
import com.vitorpamplona.amethyst.commons.resources.settings_muted_threads_title
import com.vitorpamplona.amethyst.commons.resources.show_sensitive_content_explainer
import com.vitorpamplona.amethyst.commons.resources.show_sensitive_content_title
import com.vitorpamplona.amethyst.commons.resources.spamming_users
import com.vitorpamplona.amethyst.commons.resources.warn_when_posts_have_reports_from_your_follows_explainer
import com.vitorpamplona.amethyst.commons.resources.warn_when_posts_have_reports_from_your_follows_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Composable
fun SecurityFiltersScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(id = Res.string.security_filters), nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SecurityPreferencesSection(accountViewModel)
            BlockedContentSection(accountViewModel, nav)
        }
    }
}

@Composable
private fun SecurityPreferencesSection(accountViewModel: AccountViewModel) {
    SettingsSection(Res.string.security_section_filtering_preferences) {
        SensitiveContentTile(accountViewModel)
        SettingsDivider()
        FilterSpamTile(accountViewModel)
        SettingsDivider()
        HideCommunityViolationsTile(accountViewModel)
        SettingsDivider()
        WarnReportsTile(accountViewModel)
        SettingsDivider()
        MaxHashtagsTile(accountViewModel)
    }
}

@Composable
private fun SensitiveContentTile(accountViewModel: AccountViewModel) {
    val security = accountViewModel.account.settings.syncedSettings.security
    val showSensitive by security.showSensitiveContent.collectAsStateWithLifecycle()
    val current = parseWarningType(showSensitive)
    val options = WarningType.entries

    SettingsBlockTile(
        icon = MaterialSymbols.Visibility,
        title = stringRes(Res.string.show_sensitive_content_title),
        description = stringRes(Res.string.show_sensitive_content_explainer),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = current == type,
                    onClick = { accountViewModel.updateShowSensitiveContent(type.prefCode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(stringRes(type.resourceId))
                }
            }
        }
    }
}

@Composable
private fun FilterSpamTile(accountViewModel: AccountViewModel) {
    val filterSpam by accountViewModel.account.settings.syncedSettings.security
        .filterSpamFromStrangers
        .collectAsStateWithLifecycle()

    SettingsSwitchTile(
        icon = MaterialSymbols.FilterAlt,
        title = Res.string.filter_spam_from_strangers_title,
        description = Res.string.filter_spam_from_strangers_explainer,
        checked = filterSpam,
        onCheckedChange = accountViewModel::updateFilterSpam,
    )
}

@Composable
private fun HideCommunityViolationsTile(accountViewModel: AccountViewModel) {
    val hideViolations by accountViewModel.account.settings.hideCommunityRulesViolations
        .collectAsStateWithLifecycle()

    SettingsSwitchTile(
        icon = MaterialSymbols.Shield,
        title = Res.string.hide_community_rules_violations_title,
        description = Res.string.hide_community_rules_violations_explainer,
        checked = hideViolations,
        onCheckedChange = { accountViewModel.account.settings.changeHideCommunityRulesViolations(it) },
    )
}

@Composable
private fun WarnReportsTile(accountViewModel: AccountViewModel) {
    val security = accountViewModel.account.settings.syncedSettings.security
    val warnReports by security.warnAboutPostsWithReports.collectAsStateWithLifecycle()
    val threshold by security.reportWarningThreshold.collectAsStateWithLifecycle()

    SettingsSwitchTile(
        icon = MaterialSymbols.Report,
        title = Res.string.warn_when_posts_have_reports_from_your_follows_title,
        description = Res.string.warn_when_posts_have_reports_from_your_follows_explainer,
        checked = warnReports,
        onCheckedChange = accountViewModel::updateWarnReports,
    )
    SettingsSubControlRow(
        title = stringRes(Res.string.report_warning_threshold_title),
        description = stringRes(Res.string.report_warning_threshold_explainer),
        enabled = warnReports,
    ) {
        SettingsStepper(
            value = threshold,
            min = 1,
            max = 999,
            enabled = warnReports,
            onValueChange = accountViewModel::updateReportWarningThreshold,
        )
    }
}

@Composable
private fun MaxHashtagsTile(accountViewModel: AccountViewModel) {
    val maxHashtags by accountViewModel.account.settings.syncedSettings.security
        .maxHashtagLimit
        .collectAsStateWithLifecycle()

    SettingsControlRow(
        icon = MaterialSymbols.Tag,
        title = stringRes(Res.string.max_hashtag_limit_title),
        description = stringRes(Res.string.max_hashtag_limit_explainer),
    ) {
        SettingsStepper(
            value = maxHashtags,
            min = 0,
            max = 99,
            unsetLabel = stringRes(Res.string.security_unlimited),
            onValueChange = accountViewModel::updateMaxHashtagLimit,
        )
    }
}

@Composable
private fun BlockedContentSection(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val hidden by accountViewModel.account.hiddenUsers.flow
        .collectAsStateWithLifecycle()

    SettingsSection(Res.string.security_section_blocked_content) {
        SettingsItem(
            title = Res.string.blocked_users,
            icon = MaterialSymbols.PersonOff,
            trailing = { SettingsCountBadge(hidden.hiddenUsers.size) },
            onClick = { nav.nav(Route.BlockedUsers) },
        )
        SettingsDivider()
        SettingsItem(
            title = Res.string.spamming_users,
            icon = MaterialSymbols.Block,
            trailing = { SettingsCountBadge(hidden.spammers.size) },
            onClick = { nav.nav(Route.SpammingUsers) },
        )
        SettingsDivider()
        SettingsItem(
            title = Res.string.hidden_words,
            icon = MaterialSymbols.VisibilityOff,
            trailing = { SettingsCountBadge(hidden.hiddenWords.size) },
            onClick = { nav.nav(Route.HiddenWords) },
        )
        SettingsDivider()
        SettingsItem(
            title = Res.string.settings_muted_threads_title,
            icon = MaterialSymbols.Forum,
            trailing = { SettingsCountBadge(hidden.mutedThreads.size) },
            onClick = { nav.nav(Route.MutedThreads) },
        )
    }
}

@Preview
@Composable
fun SecurityFiltersScreenPreview() {
    ThemeComparisonColumn {
        SecurityFiltersScreen(mockAccountViewModel(), EmptyNav())
    }
}
