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
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.HomeFeedType
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.conversations
import com.vitorpamplona.amethyst.commons.resources.home_content_type_articles
import com.vitorpamplona.amethyst.commons.resources.home_content_type_attestations
import com.vitorpamplona.amethyst.commons.resources.home_content_type_birds
import com.vitorpamplona.amethyst.commons.resources.home_content_type_calendar_events
import com.vitorpamplona.amethyst.commons.resources.home_content_type_calendar_rsvps
import com.vitorpamplona.amethyst.commons.resources.home_content_type_chess
import com.vitorpamplona.amethyst.commons.resources.home_content_type_classifieds
import com.vitorpamplona.amethyst.commons.resources.home_content_type_code_snippets
import com.vitorpamplona.amethyst.commons.resources.home_content_type_comments
import com.vitorpamplona.amethyst.commons.resources.home_content_type_ebooks
import com.vitorpamplona.amethyst.commons.resources.home_content_type_ephemeral_chat
import com.vitorpamplona.amethyst.commons.resources.home_content_type_fundraisers
import com.vitorpamplona.amethyst.commons.resources.home_content_type_git
import com.vitorpamplona.amethyst.commons.resources.home_content_type_highlights
import com.vitorpamplona.amethyst.commons.resources.home_content_type_interactive_stories
import com.vitorpamplona.amethyst.commons.resources.home_content_type_live_activities
import com.vitorpamplona.amethyst.commons.resources.home_content_type_magazines
import com.vitorpamplona.amethyst.commons.resources.home_content_type_music
import com.vitorpamplona.amethyst.commons.resources.home_content_type_nips
import com.vitorpamplona.amethyst.commons.resources.home_content_type_pictures
import com.vitorpamplona.amethyst.commons.resources.home_content_type_podcasts
import com.vitorpamplona.amethyst.commons.resources.home_content_type_polls
import com.vitorpamplona.amethyst.commons.resources.home_content_type_ratings
import com.vitorpamplona.amethyst.commons.resources.home_content_type_reposts
import com.vitorpamplona.amethyst.commons.resources.home_content_type_shorts
import com.vitorpamplona.amethyst.commons.resources.home_content_type_text_notes
import com.vitorpamplona.amethyst.commons.resources.home_content_type_threads
import com.vitorpamplona.amethyst.commons.resources.home_content_type_torrents
import com.vitorpamplona.amethyst.commons.resources.home_content_type_videos
import com.vitorpamplona.amethyst.commons.resources.home_content_type_voice
import com.vitorpamplona.amethyst.commons.resources.home_content_type_wiki
import com.vitorpamplona.amethyst.commons.resources.home_tab_everything
import com.vitorpamplona.amethyst.commons.resources.home_tabs_settings
import com.vitorpamplona.amethyst.commons.resources.new_threads
import com.vitorpamplona.amethyst.commons.resources.settings_section_home_content_types
import com.vitorpamplona.amethyst.commons.resources.settings_section_home_tabs
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import org.jetbrains.compose.resources.StringResource

@Preview
@Composable
fun HomeTabsSettingsScreenPreview() {
    ThemeComparisonRow {
        HomeTabsSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun HomeTabsSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = Res.string.home_tabs_settings), nav)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeTabsSection(accountViewModel.settings.uiSettingsFlow)
            HomeContentTypesSection(accountViewModel)
        }
    }
}

@Composable
private fun HomeTabsSection(ui: UiSettingsFlow) {
    val showNewThreads by ui.showHomeNewThreadsTab.collectAsStateWithLifecycle()
    val showConversations by ui.showHomeConversationsTab.collectAsStateWithLifecycle()
    val showEverything by ui.showHomeEverythingTab.collectAsStateWithLifecycle()

    val activeCount = listOf(showNewThreads, showConversations, showEverything).count { it }

    SettingsSection(Res.string.settings_section_home_tabs) {
        SettingsSwitchTile(
            icon = MaterialSymbols.Forum,
            title = Res.string.new_threads,
            checked = showNewThreads,
            // Don't allow disabling the last remaining tab.
            enabled = !(showNewThreads && activeCount == 1),
            onCheckedChange = { ui.showHomeNewThreadsTab.tryEmit(it) },
        )
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Chat,
            title = Res.string.conversations,
            checked = showConversations,
            enabled = !(showConversations && activeCount == 1),
            onCheckedChange = { ui.showHomeConversationsTab.tryEmit(it) },
        )
        SettingsDivider()
        SettingsSwitchTile(
            icon = MaterialSymbols.Public,
            title = Res.string.home_tab_everything,
            checked = showEverything,
            enabled = !(showEverything && activeCount == 1),
            onCheckedChange = { ui.showHomeEverythingTab.tryEmit(it) },
        )
    }
}

/** One toggleable Home content group, mapping a [HomeFeedType] to its display title + icon. */
private data class HomeFeedTypeUi(
    val type: HomeFeedType,
    val titleRes: StringResource,
    val icon: MaterialSymbol,
)

// Ordered by how common each group is on a typical home feed (everyday posts first, niche last).
private val HOME_FEED_TYPES =
    listOf(
        HomeFeedTypeUi(HomeFeedType.TEXT_NOTES, Res.string.home_content_type_text_notes, MaterialSymbols.EditNote),
        HomeFeedTypeUi(HomeFeedType.REPOSTS, Res.string.home_content_type_reposts, MaterialSymbols.Forward),
        HomeFeedTypeUi(HomeFeedType.COMMENTS, Res.string.home_content_type_comments, MaterialSymbols.Chat),
        HomeFeedTypeUi(HomeFeedType.PICTURES, Res.string.home_content_type_pictures, MaterialSymbols.Image),
        HomeFeedTypeUi(HomeFeedType.VIDEOS, Res.string.home_content_type_videos, MaterialSymbols.Videocam),
        HomeFeedTypeUi(HomeFeedType.SHORTS, Res.string.home_content_type_shorts, MaterialSymbols.SmartDisplay),
        HomeFeedTypeUi(HomeFeedType.ARTICLES, Res.string.home_content_type_articles, MaterialSymbols.AutoMirrored.Article),
        HomeFeedTypeUi(HomeFeedType.WIKI, Res.string.home_content_type_wiki, MaterialSymbols.MenuBook),
        HomeFeedTypeUi(HomeFeedType.HIGHLIGHTS, Res.string.home_content_type_highlights, MaterialSymbols.FormatQuote),
        HomeFeedTypeUi(HomeFeedType.POLLS, Res.string.home_content_type_polls, MaterialSymbols.Poll),
        HomeFeedTypeUi(HomeFeedType.CLASSIFIEDS, Res.string.home_content_type_classifieds, MaterialSymbols.Storefront),
        HomeFeedTypeUi(HomeFeedType.TORRENTS, Res.string.home_content_type_torrents, MaterialSymbols.Download),
        HomeFeedTypeUi(HomeFeedType.VOICE, Res.string.home_content_type_voice, MaterialSymbols.Mic),
        HomeFeedTypeUi(HomeFeedType.LIVE_ACTIVITIES, Res.string.home_content_type_live_activities, MaterialSymbols.Sensors),
        HomeFeedTypeUi(HomeFeedType.EPHEMERAL_CHAT, Res.string.home_content_type_ephemeral_chat, MaterialSymbols.Forum),
        HomeFeedTypeUi(HomeFeedType.INTERACTIVE_STORIES, Res.string.home_content_type_interactive_stories, MaterialSymbols.AutoAwesome),
        HomeFeedTypeUi(HomeFeedType.CHESS, Res.string.home_content_type_chess, MaterialSymbols.ChessKnight),
        HomeFeedTypeUi(HomeFeedType.BIRDS, Res.string.home_content_type_birds, MaterialSymbols.TravelExplore),
        HomeFeedTypeUi(HomeFeedType.ATTESTATIONS, Res.string.home_content_type_attestations, MaterialSymbols.Shield),
        HomeFeedTypeUi(HomeFeedType.NIPS, Res.string.home_content_type_nips, MaterialSymbols.Code),
        HomeFeedTypeUi(HomeFeedType.MUSIC, Res.string.home_content_type_music, MaterialSymbols.MusicNote),
        HomeFeedTypeUi(HomeFeedType.PODCASTS, Res.string.home_content_type_podcasts, MaterialSymbols.Podcasts),
        HomeFeedTypeUi(HomeFeedType.FUNDRAISERS, Res.string.home_content_type_fundraisers, MaterialSymbols.Paid),
        HomeFeedTypeUi(HomeFeedType.EBOOKS, Res.string.home_content_type_ebooks, MaterialSymbols.MenuBook),
        HomeFeedTypeUi(HomeFeedType.MAGAZINES, Res.string.home_content_type_magazines, MaterialSymbols.AutoMirrored.Article),
        HomeFeedTypeUi(HomeFeedType.RATINGS, Res.string.home_content_type_ratings, MaterialSymbols.Star),
        HomeFeedTypeUi(HomeFeedType.CALENDAR_RSVPS, Res.string.home_content_type_calendar_rsvps, MaterialSymbols.CalendarMonth),
        HomeFeedTypeUi(HomeFeedType.THREADS, Res.string.home_content_type_threads, MaterialSymbols.Topic),
        HomeFeedTypeUi(HomeFeedType.CALENDAR_EVENTS, Res.string.home_content_type_calendar_events, MaterialSymbols.EventAvailable),
        HomeFeedTypeUi(HomeFeedType.CODE_SNIPPETS, Res.string.home_content_type_code_snippets, MaterialSymbols.Code),
        HomeFeedTypeUi(HomeFeedType.GIT, Res.string.home_content_type_git, MaterialSymbols.ForkRight),
    )

/**
 * Per-content-type load toggles for the Home feed. Turning one off both drops its event kinds from
 * the always-on home relay filters AND hides them from the New Threads / Conversations / Everything
 * tabs. Everything is on by default.
 */
@Composable
private fun HomeContentTypesSection(accountViewModel: AccountViewModel) {
    val enabled by accountViewModel.account.settings.enabledHomeFeedTypes
        .collectAsStateWithLifecycle()

    SettingsSection(Res.string.settings_section_home_content_types) {
        HOME_FEED_TYPES.forEachIndexed { index, item ->
            if (index > 0) SettingsDivider()
            SettingsSwitchTile(
                icon = item.icon,
                title = item.titleRes,
                checked = item.type in enabled,
                onCheckedChange = { accountViewModel.account.settings.setHomeFeedTypeEnabled(item.type, it) },
            )
        }
    }
}
