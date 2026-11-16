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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.favorites.favoriteCoordinateOf
import com.vitorpamplona.amethyst.commons.frontpage.AgentModel
import com.vitorpamplona.amethyst.commons.frontpage.CronWords
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageClock
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageGate
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageModels
import com.vitorpamplona.amethyst.commons.frontpage.FrontPagePrinter
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageRun
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageSetup
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageViewModel
import com.vitorpamplona.amethyst.commons.frontpage.MASTHEAD
import com.vitorpamplona.amethyst.commons.frontpage.ModelChoices
import com.vitorpamplona.amethyst.commons.frontpage.MorningScheduleView
import com.vitorpamplona.amethyst.commons.frontpage.ObserverCalendar
import com.vitorpamplona.amethyst.commons.frontpage.ObserverFailure
import com.vitorpamplona.amethyst.commons.frontpage.ObserverPrefs
import com.vitorpamplona.amethyst.commons.frontpage.ObserverStatus
import com.vitorpamplona.amethyst.commons.frontpage.ObserverToday
import com.vitorpamplona.amethyst.commons.frontpage.PastEdition
import com.vitorpamplona.amethyst.commons.frontpage.PrintLog
import com.vitorpamplona.amethyst.commons.frontpage.PrintResult
import com.vitorpamplona.amethyst.commons.frontpage.PrintStep
import com.vitorpamplona.amethyst.commons.frontpage.PrintedBy
import com.vitorpamplona.amethyst.commons.frontpage.RelativeDay
import com.vitorpamplona.amethyst.commons.frontpage.SavedEdition
import com.vitorpamplona.amethyst.commons.frontpage.ScheduleState
import com.vitorpamplona.amethyst.commons.frontpage.ShownEdition
import com.vitorpamplona.amethyst.commons.frontpage.Violation
import com.vitorpamplona.amethyst.commons.frontpage.WritingEffort
import com.vitorpamplona.amethyst.commons.frontpage.dayOfWeek
import com.vitorpamplona.amethyst.commons.frontpage.formatUsd
import com.vitorpamplona.amethyst.commons.frontpage.relativeDay
import com.vitorpamplona.amethyst.commons.frontpage.shortNpub
import com.vitorpamplona.amethyst.commons.frontpage.withPaperCsp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_a_blossom_server
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.resources.dlist_day_friday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_monday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_saturday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_sunday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_thursday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_tuesday
import com.vitorpamplona.amethyst.commons.resources.dlist_day_wednesday
import com.vitorpamplona.amethyst.commons.resources.front_page_add_to_bar
import com.vitorpamplona.amethyst.commons.resources.front_page_added_to_bar
import com.vitorpamplona.amethyst.commons.resources.front_page_assistant_ai_settings
import com.vitorpamplona.amethyst.commons.resources.front_page_assistant_not_printed
import com.vitorpamplona.amethyst.commons.resources.front_page_assistant_prints
import com.vitorpamplona.amethyst.commons.resources.front_page_back_to_today
import com.vitorpamplona.amethyst.commons.resources.front_page_by_assistant
import com.vitorpamplona.amethyst.commons.resources.front_page_cancel_confirm_text
import com.vitorpamplona.amethyst.commons.resources.front_page_cancel_confirm_title
import com.vitorpamplona.amethyst.commons.resources.front_page_cancel_printing
import com.vitorpamplona.amethyst.commons.resources.front_page_cannot_show
import com.vitorpamplona.amethyst.commons.resources.front_page_changes_title
import com.vitorpamplona.amethyst.commons.resources.front_page_connect
import com.vitorpamplona.amethyst.commons.resources.front_page_cost_label
import com.vitorpamplona.amethyst.commons.resources.front_page_details
import com.vitorpamplona.amethyst.commons.resources.front_page_download
import com.vitorpamplona.amethyst.commons.resources.front_page_edition_label
import com.vitorpamplona.amethyst.commons.resources.front_page_edition_unreadable
import com.vitorpamplona.amethyst.commons.resources.front_page_effort
import com.vitorpamplona.amethyst.commons.resources.front_page_effort_deep
import com.vitorpamplona.amethyst.commons.resources.front_page_effort_deep_note
import com.vitorpamplona.amethyst.commons.resources.front_page_effort_standard
import com.vitorpamplona.amethyst.commons.resources.front_page_fixed_once
import com.vitorpamplona.amethyst.commons.resources.front_page_keep_printing
import com.vitorpamplona.amethyst.commons.resources.front_page_last_printed
import com.vitorpamplona.amethyst.commons.resources.front_page_last_printed_ago
import com.vitorpamplona.amethyst.commons.resources.front_page_last_printed_by_assistant
import com.vitorpamplona.amethyst.commons.resources.front_page_model
import com.vitorpamplona.amethyst.commons.resources.front_page_model_hint
import com.vitorpamplona.amethyst.commons.resources.front_page_model_id
import com.vitorpamplona.amethyst.commons.resources.front_page_needs_device_key
import com.vitorpamplona.amethyst.commons.resources.front_page_never_printed
import com.vitorpamplona.amethyst.commons.resources.front_page_new_edition
import com.vitorpamplona.amethyst.commons.resources.front_page_no_tor
import com.vitorpamplona.amethyst.commons.resources.front_page_not_now
import com.vitorpamplona.amethyst.commons.resources.front_page_not_printed_since
import com.vitorpamplona.amethyst.commons.resources.front_page_not_ready
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_app_label
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_notice_body
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_notice_title
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_pin
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_pinned
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_pinned_label
import com.vitorpamplona.amethyst.commons.resources.front_page_observer_unpinned
import com.vitorpamplona.amethyst.commons.resources.front_page_on_observer_site
import com.vitorpamplona.amethyst.commons.resources.front_page_open_observer
import com.vitorpamplona.amethyst.commons.resources.front_page_past_editions
import com.vitorpamplona.amethyst.commons.resources.front_page_pick_time
import com.vitorpamplona.amethyst.commons.resources.front_page_posts_label
import com.vitorpamplona.amethyst.commons.resources.front_page_print
import com.vitorpamplona.amethyst.commons.resources.front_page_print_again
import com.vitorpamplona.amethyst.commons.resources.front_page_print_busy
import com.vitorpamplona.amethyst.commons.resources.front_page_print_estimate
import com.vitorpamplona.amethyst.commons.resources.front_page_print_from_device
import com.vitorpamplona.amethyst.commons.resources.front_page_print_from_my_devices
import com.vitorpamplona.amethyst.commons.resources.front_page_print_from_my_devices_text
import com.vitorpamplona.amethyst.commons.resources.front_page_print_from_my_devices_title
import com.vitorpamplona.amethyst.commons.resources.front_page_printed_by
import com.vitorpamplona.amethyst.commons.resources.front_page_printed_by_assistant
import com.vitorpamplona.amethyst.commons.resources.front_page_publish
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_confirm
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_no_servers
import com.vitorpamplona.amethyst.commons.resources.front_page_publish_read_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_publishing
import com.vitorpamplona.amethyst.commons.resources.front_page_quotes_label
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_body
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_title
import com.vitorpamplona.amethyst.commons.resources.front_page_runner_conflict
import com.vitorpamplona.amethyst.commons.resources.front_page_runner_not_a_key
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_cannot_sign
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_change_time
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_daily
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_days
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_every_hours
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_in_zone
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_locked
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_monthly
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_no_write_relays
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_no_zone
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_not_accepted
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_not_expressible
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_not_set_up
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_read_only
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_times_a_day
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_unreachable
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_unreadable
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_weekdays
import com.vitorpamplona.amethyst.commons.resources.front_page_schedule_weekends
import com.vitorpamplona.amethyst.commons.resources.front_page_scheduled
import com.vitorpamplona.amethyst.commons.resources.front_page_set_up
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_change
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_done
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_key
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_lens
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_on
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_ranked_by
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_title
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_title_again
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_to_do
import com.vitorpamplona.amethyst.commons.resources.front_page_setup_using
import com.vitorpamplona.amethyst.commons.resources.front_page_show_failed
import com.vitorpamplona.amethyst.commons.resources.front_page_step_check
import com.vitorpamplona.amethyst.commons.resources.front_page_step_probe
import com.vitorpamplona.amethyst.commons.resources.front_page_step_pull
import com.vitorpamplona.amethyst.commons.resources.front_page_tagline
import com.vitorpamplona.amethyst.commons.resources.front_page_title
import com.vitorpamplona.amethyst.commons.resources.front_page_tokens
import com.vitorpamplona.amethyst.commons.resources.front_page_tokens_label
import com.vitorpamplona.amethyst.commons.resources.front_page_too_large
import com.vitorpamplona.amethyst.commons.resources.front_page_tor_pictures
import com.vitorpamplona.amethyst.commons.resources.front_page_unknown_params
import com.vitorpamplona.amethyst.commons.resources.front_page_unsupported
import com.vitorpamplona.amethyst.commons.resources.front_page_use_my_devices
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_image
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_link
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_markup
import com.vitorpamplona.amethyst.commons.resources.front_page_violation_quote
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.relay_settings
import com.vitorpamplona.amethyst.commons.resources.retry
import com.vitorpamplona.amethyst.commons.resources.routstr_balance_low
import com.vitorpamplona.amethyst.commons.resources.routstr_balance_low_schedule
import com.vitorpamplona.amethyst.commons.resources.routstr_top_up
import com.vitorpamplona.amethyst.commons.resources.today
import com.vitorpamplona.amethyst.commons.resources.tor_waiting
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.resources.yesterday
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.components.PillColors
import com.vitorpamplona.amethyst.commons.ui.components.PillTextField
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.components.RoundedDropdownMenu
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuItemModifier
import com.vitorpamplona.amethyst.commons.ui.components.RoundedMenuSection
import com.vitorpamplona.amethyst.commons.ui.components.rememberFileBytesAccess
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.FeedContentMaxWidth
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.calendarYearAndDay
import com.vitorpamplona.amethyst.commons.ui.note.durationPhrase
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.platform.AppBottomBar
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.platform.rememberAppLauncher
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.BottomBarEditing
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.RoutstrPrintNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.rememberRoutstrPrintNote
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.routstr.satsText
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.util.formatGrouped
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.wot.onboarding.brainstorm.BrainstormOnboarding
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The house stylesheet's `--paper`: the newsprint the paper sits on, whatever the app's theme. */
private val Newsprint = Color(0xFFFAF8F3)

/** Everything but the paper: at the feed's reading width, centred, however wide the pane. */
private val readable = Modifier.widthIn(max = FeedContentMaxWidth).fillMaxWidth()

/** Below this, the top bar keeps Print again and About and moves Download into its menu. */
private val COMPACT_BAR_WIDTH = 480.dp

/** Ink on newsprint: the paper's frame is always light, whatever the app's theme. */
private val Ink = Color(0xFF1A1A1A)

/** "Tue, Nov 10": the shown edition's day, as the top bar's title. */
private val editionDateFormatter = DateSkeletonFormatter("MMMEd")

/**
 * The edition's own day (`yyyy-MM-dd`), as the title: noon UTC of that date, which falls on the
 * same calendar day in every zone from UTC-11 to UTC+11. Its print time can be later than the
 * day it belongs to, or absent.
 */
private fun editionTitle(day: String): String = dayNoon(day)?.let { editionDateFormatter.format(it) } ?: day

/**
 * An instant (ms) on [day] (`yyyy-MM-dd`) in the reader's zone: noon UTC of that date, which falls
 * on the same calendar day in every zone from UTC-11 to UTC+11; null for anything else.
 */
private fun dayNoon(day: String): Long? {
    val parts = day.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    val days = SearchDate(parts[0], parts[1], parts[2]).daysFromEpoch()
    val wanted = parts[0] * 1000 + (days - SearchDate(parts[0], 1, 1).daysFromEpoch() + 1).toInt()
    val noonUtc = (days * 86_400L + 43_200L) * 1000
    // Noon UTC is that date only from UTC-11 to UTC+11: past those (UTC+12 to +14), step half a
    // day until the reader's own zone lands on it.
    return listOf(noonUtc, noonUtc - 43_200_000L, noonUtc + 43_200_000L).firstOrNull { calendarYearAndDay(it) == wanted } ?: noonUtc
}

/**
 * Your front page: the Nostr Observer's daily paper, written by the reader's own AI key from the
 * last 24 hours of their web of trust, checked, kept on the device for a week, and shown on the
 * same sandboxed surface as an nSite. The print belongs to the account, not to this screen.
 *
 * About today, always ([ObserverToday]): today's paper fills the pane, as in the reader, with
 * printing again behind a top-bar action. With no paper printed today, the screen is the view
 * that prints it (the setup checklist while something is missing). An older paper is never shown
 * here as the current one: the kept ones are in Past editions, and open in the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrontPageScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
    day: String? = null,
) {
    val services = LocalAppServices.current
    val printers = services.frontPagePrinters
    if (printers == null) {
        Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.front_page_title), nav) }) { padding ->
            Box(Modifier.padding(padding).padding(16.dp)) { Notice(stringRes(Res.string.front_page_unsupported)) }
        }
        return
    }
    val account = accountViewModel.account
    val printer = printers.of(account)
    val viewModel =
        rememberViewModel(key = "front-page:${account.signer.pubKey}") {
            FrontPageViewModel(account, accountViewModel.httpClientBuilder, printer, printers.morning, day)
        }
    // While this screen shows the paper, a finished print needs no notification. Only while it is
    // started: a backgrounded app (or a screen behind another) isn't anyone watching.
    LifecycleStartEffect(printer) {
        val release = printer.watch()
        onStopOrDispose { release() }
    }
    // A signer that needs the reader is asked to open the schedule only while this screen is on
    // screen: never for a screen left in the back stack.
    LifecycleStartEffect(viewModel) {
        val release = viewModel.scheduleOpened()
        onStopOrDispose { release() }
    }
    val printRefused by viewModel.printRefused.collectAsStateWithLifecycle()
    // Cancelling throws away a print that may already be paid for: always confirmed first.
    var confirmCancel by remember { mutableStateOf(false) }
    val askCancel: () -> Unit = { confirmCancel = true }
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    // The log changes per streamed reasoning step: collected only where it is drawn, not here.
    val log = printer.log
    val today by viewModel.today.collectAsStateWithLifecycle()
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    val estimate by viewModel.estimate.collectAsStateWithLifecycle()
    val effort by viewModel.effort.collectAsStateWithLifecycle()
    val estimateSats by viewModel.estimateSats.collectAsStateWithLifecycle()
    val printKey by viewModel.key.collectAsStateWithLifecycle()
    // A Routstr key pays in sats: the print says what in sats, and warns when the balance may not cover it.
    val routstrNote = rememberRoutstrPrintNote(accountViewModel, nav, printKey, models.selectedModel, estimateSats)
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val scheduleProblem by viewModel.scheduleProblem.collectAsStateWithLifecycle()
    val schedulePending by viewModel.schedulePending.collectAsStateWithLifecycle()
    val printedBy by viewModel.printedBy.collectAsStateWithLifecycle()
    // The runner's profile, asked for while The Observer shows, so "Printed by" can name it.
    printedBy?.takeIf { it.name == null && !it.isAssistant }?.let { by ->
        LoadUser(by.runner) { user -> if (user != null) UserFinderFilterAssemblerSubscription(user, accountViewModel) }
    }
    val scheduleActions =
        remember(viewModel, nav) {
            ScheduleActions(viewModel::setScheduleEnabled, viewModel::setScheduleTime, viewModel::setPrintsHere, viewModel::retryScheduleDecrypt, viewModel::printFromMyDevices) { nav.nav(Route.EditRelays) }
        }
    val observerPrefs by viewModel.observerPrefs.collectAsStateWithLifecycle()
    val observer by viewModel.observer.collectAsStateWithLifecycle()

    val pastDay by viewModel.pastDay.collectAsStateWithLifecycle()
    // A past edition (Past editions, a notification, a link) shows in place of today's; back
    // from it, by the arrow or the system's back, is today.
    val past = today.pastEdition(pastDay)
    PlatformBackHandler(enabled = past != null) { viewModel.showToday() }
    val barNav = remember(nav, viewModel, past != null) { if (past != null) BackToToday(nav, viewModel::showToday) else nav }

    var printSheet by remember { mutableStateOf(false) }
    var pastSheet by remember { mutableStateOf(false) }
    // The public-site confirm comes after a clean print, before anything is uploaded (ObserverLine).
    val requestPrint: () -> Unit = viewModel::print
    val publishPrefs = if (viewModel.canPublish) observerPrefs else null
    var detailsSheet by remember { mutableStateOf(false) }
    val download = rememberEditionDownload()

    // The bottom bar, for the one-tap shortcut: the front page as a tab.
    val bottomBar by accountViewModel.bottomBarItemsFlow().collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val busyText = stringRes(Res.string.front_page_print_busy)
    LaunchedEffect(printRefused) {
        if (printRefused) {
            viewModel.printRefusedShown()
            snackbar.showSnackbar(busyText)
        }
    }
    val addedToBar = stringRes(Res.string.front_page_added_to_bar)
    // Only where there is a bottom bar to add it to: not beside the permanent drawer.
    val hasBottomBar = LocalScreenLayout.current.navigationStyle == NavigationStyle.BOTTOM_BAR
    val addToBar: (() -> Unit)? =
        if (hasBottomBar && FrontPageShortcuts.offersBottomBar(bottomBar)) {
            {
                accountViewModel.changeBottomBarItems(FrontPageShortcuts.withFrontPage(bottomBar))
                scope.launch { snackbar.showSnackbar(addedToBar) }
            }
        } else {
            null
        }
    val openPast: (() -> Unit)? = if (today.past.isNotEmpty()) ({ pastSheet = true }) else null

    // The edition on screen: the past one picked, else today's; null is the print view.
    val paper = past?.edition ?: today.paper
    // Its day on the calendar "today" is read on, for the title: "Today", "Yesterday", "Tuesday"…
    val paperDay = past?.day ?: today.day
    // Its page, once read: never the page of the edition shown before it.
    val page = shown?.takeIf { paper != null && it.edition.day == paper.day && it.edition.code == paper.code }
    // A narrow pane (a phone, a deck column) holds the frequent actions; Download goes in the menu,
    // so the title keeps its room. Decided by the pane's own width, not the window's.
    var paneWidth by remember { mutableStateOf(Dp.Unspecified) }
    val density = LocalDensity.current
    val compactBar = paneWidth == Dp.Unspecified || paneWidth < COMPACT_BAR_WIDTH
    Scaffold(
        modifier = Modifier.onSizeChanged { paneWidth = with(density) { it.width.toDp() } },
        snackbarHost = { SnackbarHost(snackbar) },
        // As every screen the drawer opens: the bar stays (it hides itself on wide windows and pushes).
        bottomBar = {
            AppBottomBar(Route.FrontPage(), nav, accountViewModel) { route ->
                if (route != Route.FrontPage()) nav.navBottomBar(route)
            }
        },
        topBar = {
            if (paper == null) {
                // The print view: nothing to print again, read or download; only what the menu holds.
                if (openPast == null && addToBar == null) {
                    TopBarWithBackButton(stringRes(Res.string.front_page_title), nav)
                } else {
                    TopBarWithBackButton(stringRes(Res.string.front_page_title), nav) {
                        OverflowMenu { close ->
                            openPast?.let { PastEditionsItem(close, it) }
                            addToBar?.let { AddToBarItem(close, it) }
                        }
                    }
                }
            } else {
                val canDownload = page?.html != null
                TopBarWithBackButton(relativeTitle(paperDay, today.day), barNav, captionDescription = relativeDescription(paperDay, today.day)) {
                    // Printing again is today's: the print sheet also holds the schedule and the
                    // publish switch, so they stay reachable while today's paper shows.
                    if (past == null && gate != FrontPageGate.UNSUPPORTED) {
                        IconButton(onClick = { printSheet = true }) {
                            Icon(MaterialSymbols.AutoAwesome, contentDescription = stringRes(Res.string.front_page_print_again))
                        }
                    }
                    // A past edition's way back, besides the arrow: named, in the bar, so nothing
                    // covers the paper's dateline. The bar's title gives way to it, never the other way.
                    if (past != null) {
                        val backToToday = stringRes(Res.string.front_page_back_to_today)
                        TextButton(onClick = viewModel::showToday) {
                            Text(
                                stringRes(Res.string.today),
                                maxLines = 1,
                                modifier = Modifier.clearAndSetSemantics { contentDescription = backToToday },
                            )
                        }
                    }
                    IconButton(onClick = { detailsSheet = true }) {
                        Icon(MaterialSymbols.Info, contentDescription = stringRes(Res.string.front_page_details))
                    }
                    if (!compactBar && canDownload) {
                        IconButton(onClick = { page?.let(download) }) {
                            Icon(MaterialSymbols.Download, contentDescription = stringRes(Res.string.front_page_download))
                        }
                    }
                    if ((compactBar && canDownload) || openPast != null || addToBar != null) {
                        OverflowMenu { close ->
                            if (compactBar && canDownload) {
                                MenuItem(MaterialSymbols.Download, stringRes(Res.string.front_page_download)) {
                                    close()
                                    page?.let(download)
                                }
                            }
                            openPast?.let { PastEditionsItem(close, it) }
                            addToBar?.let { AddToBarItem(close, it) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (paper != null) {
            // The paper fills the pane, edge to edge: today's, or the past one picked.
            Box(Modifier.padding(padding).fillMaxSize()) {
                PaperPane(paper, page, loading = page == null, Modifier.fillMaxSize())
                // What goes on meanwhile (a print again, a publish to confirm or that failed) floats
                // over the foot of the paper, at a reading width.
                Column(
                    Modifier.align(Alignment.BottomCenter).then(readable).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (gate == FrontPageGate.READY) DismissibleRunStatus(run, log, onCancel = askCancel)
                    ObserverLine(
                        observer,
                        onPublish = { viewModel.answerObserverNotice(publish = true) },
                        onNotNow = { viewModel.answerObserverNotice(publish = false) },
                        onAddServer = { nav.nav(Route.EditMediaServers) },
                        onRetry = viewModel::retryPublish,
                    )
                }
            }
        } else {
            PrintView(
                Modifier.padding(padding).fillMaxSize(),
                gate,
                hero = {
                    if (gate == FrontPageGate.NO_KEY || gate == FrontPageGate.NO_LENS) {
                        SetupChecklist(
                            accountViewModel,
                            setup,
                            hasEditions = today.hasEditions,
                            onKey = { nav.nav(Route.AgentKeysSettings) },
                            onLens = { nav.nav(Route.WebOfTrust) },
                        )
                    } else if (gate == FrontPageGate.READY) {
                        PrintControls(
                            models,
                            run,
                            estimate,
                            printedToday = false,
                            schedule,
                            scheduleProblem,
                            schedulePending != null,
                            scheduleActions,
                            printedBy,
                            onModel = viewModel::selectModel,
                            effort = effort,
                            onEffort = viewModel::selectEffort,
                            onPrint = requestPrint,
                            onCancel = askCancel,
                            publish = publishPrefs,
                            onPublish = viewModel::setPublish,
                            routstrNote = routstrNote,
                            editionSats = viewModel::editionSats,
                        )
                    }
                },
                status = {
                    if (gate == FrontPageGate.READY) RunStatus(run, log, onCancel = askCancel)
                    ObserverLine(
                        observer,
                        onPublish = { viewModel.answerObserverNotice(publish = true) },
                        onNotNow = { viewModel.answerObserverNotice(publish = false) },
                        onAddServer = { nav.nav(Route.EditMediaServers) },
                        onRetry = viewModel::retryPublish,
                    )
                },
                onPastEditions = openPast,
            )
        }
    }

    if (printSheet && paper != null && past == null) {
        ModalBottomSheet(onDismissRequest = { printSheet = false }) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringRes(Res.string.front_page_new_edition), style = MaterialTheme.typography.titleLarge)
                when (gate) {
                    FrontPageGate.READY ->
                        PrintControls(
                            models,
                            run,
                            estimate,
                            printedToday = true,
                            schedule,
                            scheduleProblem,
                            schedulePending != null,
                            scheduleActions,
                            printedBy,
                            onModel = viewModel::selectModel,
                            effort = effort,
                            onEffort = viewModel::selectEffort,
                            onPrint = {
                                printSheet = false
                                requestPrint()
                            },
                            onCancel = askCancel,
                            publish = publishPrefs,
                            onPublish = viewModel::setPublish,
                            routstrNote = routstrNote,
                            editionSats = viewModel::editionSats,
                        )
                    // A step removed since this paper printed: what the next one needs, as on the print view.
                    FrontPageGate.NO_KEY, FrontPageGate.NO_LENS ->
                        SetupChecklist(
                            accountViewModel,
                            setup,
                            hasEditions = true,
                            onKey = {
                                printSheet = false
                                nav.nav(Route.AgentKeysSettings)
                            },
                            onLens = {
                                printSheet = false
                                nav.nav(Route.WebOfTrust)
                            },
                        )
                    FrontPageGate.UNSUPPORTED -> Notice(stringRes(Res.string.front_page_unsupported))
                }
            }
        }
    }
    if (pastSheet) {
        PastEditionsSheet(
            today,
            onOpen = { edition ->
                pastSheet = false
                viewModel.showEdition(edition)
            },
            onDismiss = { pastSheet = false },
        )
    }
    if (confirmCancel) {
        ConfirmCancelDialog(onCancel = viewModel::cancel, onDismiss = { confirmCancel = false })
    }
    if (detailsSheet && paper != null) {
        AboutEditionSheet(paper, printer, accountViewModel, nav, snackbar, onCancelPrint = askCancel, onDismiss = { detailsSheet = false })
    }
}

/**
 * No paper printed today: the view that prints it, at the feed's reading width. The card holds the
 * setup checklist while something is missing, else the print controls; under it, the print as it
 * runs, and the way to the kept papers.
 */
@Composable
private fun PrintView(
    modifier: Modifier,
    gate: FrontPageGate,
    hero: @Composable ColumnScope.() -> Unit,
    status: @Composable ColumnScope.() -> Unit,
    onPastEditions: (() -> Unit)?,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Hero(readable, hero)
        if (gate == FrontPageGate.UNSUPPORTED) Box(readable) { Notice(stringRes(Res.string.front_page_unsupported)) }
        Column(readable, verticalArrangement = Arrangement.spacedBy(10.dp), content = status)
        onPastEditions?.let { open ->
            Box(readable) {
                TextButton(onClick = open, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(stringRes(Res.string.front_page_past_editions))
                    Icon(MaterialSymbols.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** "Cancel this print?": a print may already be paid for, so Cancel is never one tap. */
@Composable
private fun ConfirmCancelDialog(
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.front_page_cancel_confirm_title)) },
        text = { Text(stringRes(Res.string.front_page_cancel_confirm_text)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onCancel()
            }) { Text(stringRes(Res.string.front_page_cancel_printing)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.front_page_keep_printing)) } },
    )
}

/**
 * About this edition, as a sheet: its particulars, the reader's Observer site (open, share, pin)
 * and Cancel for a print running now. The same from today's paper and from the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutEditionSheet(
    edition: SavedEdition,
    printer: FrontPagePrinter,
    accountViewModel: AccountViewModel,
    nav: INav,
    snackbar: SnackbarHostState,
    onCancelPrint: () -> Unit,
    onDismiss: () -> Unit,
) {
    val run by printer.run.collectAsStateWithLifecycle()
    val site by printer.site.collectAsStateWithLifecycle()
    // The favorites and the bottom bar, for the one-tap shortcut: the reader's Observer site as a pinned app.
    val favoriteApps = LocalAppServices.current.favoriteApps
    val favorites by favoriteApps.favorites.collectAsStateWithLifecycle()
    val bottomBar by accountViewModel.bottomBarItemsFlow().collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val pinnedText = stringRes(Res.string.front_page_observer_pinned)
    val unpinnedText = stringRes(Res.string.front_page_observer_unpinned)
    val observerLabel = stringRes(Res.string.front_page_observer_app_label)
    val siteFavoriteId = site?.let { "nostr:${favoriteCoordinateOf(it)}" }
    val sitePinned = siteFavoriteId != null && favorites.any { it.id == siteFavoriteId }
    val togglePin: (NamedSiteEvent) -> Unit = { event ->
        val favorite = FrontPageShortcuts.observerFavorite(event, event.title()?.ifBlank { null } ?: observerLabel, TimeUtils.nowMillis())
        val tab = FrontPageShortcuts.favoriteTab(favorite.id)
        if (favoriteApps.isFavorite(favorite.id)) {
            favoriteApps.remove(favorite.id)
            accountViewModel.changeBottomBarItems(BottomBarEditing.unpin(bottomBar, tab))
            scope.launch { snackbar.showSnackbar(unpinnedText) }
        } else {
            // Pinned as an nSite card pins it, and put in the bar, where pinned apps sit.
            favoriteApps.add(favorite)
            accountViewModel.changeBottomBarItems(BottomBarEditing.pin(bottomBar, tab))
            scope.launch { snackbar.showSnackbar(pinnedText) }
        }
    }
    val sharer = rememberTextSharer()
    val launcher = rememberAppLauncher()
    val shareTitle = stringRes(Res.string.front_page_open_observer)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Details(
            edition,
            printing = run is FrontPageRun.Running,
            onCancelPrint = onCancelPrint,
            site,
            sitePinned = sitePinned,
            onPinSite = togglePin,
            onOpenSite =
                if (launcher.opensNapplets) {
                    { event ->
                        onDismiss()
                        launcher.openSite(event, nav)
                    }
                } else {
                    null
                },
            onShareSite = { event ->
                NAddress.createOrNull(event.kind, event.pubKey, event.dTag(), null)?.let { sharer.share("nostr:$it", event.title(), shareTitle) }
            },
            Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }
}

/**
 * The kept papers before today's, newest first: the day, the headline, and whether it is on the
 * reader's Observer site. Tapping one opens it in the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastEditionsSheet(
    today: ObserverToday,
    onOpen: (SavedEdition) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringRes(Res.string.front_page_past_editions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp).semantics { heading() },
            )
            LazyColumn {
                items(today.past, key = { it.edition.day + it.edition.code }) { past ->
                    PastEditionRow(past, today, onClick = { onOpen(past.edition) })
                }
            }
        }
    }
}

/** "Yesterday  Nov 11", the headline under it, and "On your Observer site" when it is. */
@Composable
private fun PastEditionRow(
    past: PastEdition,
    today: ObserverToday,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val edition = past.edition
    val description = relativeDescription(past.day, today.day)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    relativeTitle(past.day, today.day),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = description },
                )
                // The date beside a name that isn't one already.
                if (relativeDay(past.day, today.day) != RelativeDay.Date) {
                    Spacer(Modifier.width(8.dp))
                    Text(shortDate(past.day), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            edition.headline?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (edition.byRunner) {
                // Printed by the reader's Assistant, on its own Observer site for them.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringRes(Res.string.front_page_by_assistant), style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                }
            } else if (edition.published) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(MaterialSymbols.Public, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringRes(Res.string.front_page_on_observer_site), style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                }
            }
        }
        Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = scheme.outline)
    }
}

/**
 * The navigation the top bar's back arrow uses while a past edition shows: back is today, not
 * the screen before The Observer.
 */
private class BackToToday(
    private val nav: INav,
    private val toToday: () -> Unit,
) : INav by nav {
    @Composable
    override fun canPop(): Boolean = true

    // The bar stays as The Observer's: a past edition is still this screen.
    @Composable
    override fun showsBottomBar(): Boolean = nav.showsBottomBar()

    override fun popBack() = toToday()
}

/** Saves an edition's page where the reader picks. */
@Composable
private fun rememberEditionDownload(): (ShownEdition) -> Unit {
    val files = rememberFileBytesAccess()
    val scope = rememberCoroutineScope()
    return remember(files, scope) {
        { shown ->
            val html = shown.html
            // The copy leaves the app's sandbox: it carries the paper's CSP (older editions were kept without it).
            if (html != null) scope.launch { files.save("front-page-${shown.edition.day}-${shown.edition.code}.html", "text/html") { withPaperCsp(html).encodeToByteArray() } }
        }
    }
}

/**
 * First run and setup: what the page is, with whatever comes next inside it. A quiet surface in
 * the app's own colours: the paper is what the page is for, not this card.
 */
@Composable
private fun Hero(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainerLow)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(scheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(MaterialSymbols.AutoMirrored.Article, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                // The top bar names the screen; the hero only says what it is for.
                Text(
                    stringRes(Res.string.front_page_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            content()
        }
    }
}

/**
 * Model, Print (with its estimate) or Cancel, the morning schedule and the publish switch: on the
 * print view, or in the sheet "Print again" opens over today's paper ([printedToday]).
 */
@Composable
private fun PrintControls(
    models: FrontPageModels,
    run: FrontPageRun,
    estimate: Double?,
    printedToday: Boolean,
    schedule: MorningScheduleView,
    scheduleProblem: ScheduleEdit?,
    scheduleBusy: Boolean,
    scheduleActions: ScheduleActions,
    printedBy: PrintedBy?,
    onModel: (String) -> Unit,
    effort: WritingEffort,
    onEffort: (WritingEffort) -> Unit,
    onPrint: () -> Unit,
    onCancel: () -> Unit,
    publish: ObserverPrefs?,
    onPublish: (Boolean) -> Unit,
    routstrNote: RoutstrPrintNote? = null,
    editionSats: ((AgentModel?) -> Long?)? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelPicker(models, onModel, PillColors.onCard(), ink, editionSats)
        EffortRow(effort, onEffort, ink)
        // A Print that can't be pressed says why: the provider lists nothing and no model is named.
        if (!models.loading && models.ids.isEmpty() && models.selected == null) {
            Text(
                stringRes(Res.string.front_page_model_hint),
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        if (run is FrontPageRun.Running) {
            CancelPrintButton(onCancel, Modifier.fillMaxWidth().height(52.dp), ink)
        } else {
            Button(
                onClick = onPrint,
                enabled = models.selected != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(MaterialSymbols.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp), filled = true)
                Spacer(Modifier.width(8.dp))
                val label = stringRes(if (printedToday) Res.string.front_page_print_again else Res.string.front_page_print)
                // The reader pays for this: say roughly what before they press it (NIP-FA).
                // A Routstr key pays in sats: the estimate is in what it pays with.
                val sats = routstrNote?.estimateSats
                Text(
                    when {
                        sats != null -> stringRes(Res.string.front_page_print_estimate, label, satsText(sats))
                        estimate != null -> stringRes(Res.string.front_page_print_estimate, label, formatUsd(estimate))
                        else -> label
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        if (routstrNote?.lowBalance == true) RoutstrLowBalance(stringRes(Res.string.routstr_balance_low), routstrNote.onTopUp, ink)
        ScheduleRow(schedule, scheduleProblem, scheduleBusy, scheduleActions, printedBy, ink, lowBalance = routstrNote?.lowBalance == true)
        publish?.let { PublishRow(it.publish, onPublish, ink) }
    }
}

/** "Publish to my Observer": each clean edition also goes to the reader's public site. */
@Composable
private fun PublishRow(
    on: Boolean,
    onChange: (Boolean) -> Unit,
    ink: Color,
) = SwitchRow(
    label = stringRes(Res.string.front_page_publish),
    checked = on,
    enabled = true,
    onChange = onChange,
    ink = ink,
    icon = MaterialSymbols.Public,
    style = MaterialTheme.typography.bodyLarge,
)

/** "Cancel printing": what stops a print, after the screen's confirmation ([onCancel] opens it). */
@Composable
private fun CancelPrintButton(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.onSurface,
) {
    OutlinedButton(
        onClick = onCancel,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, ink.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
    ) {
        Text(stringRes(Res.string.front_page_cancel_printing), style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Under the facts: the confirm before the first publish (nothing is uploaded until the reader
 * says Publish), the publish in progress, or why it failed. Nothing when fine.
 */
@Composable
private fun ObserverLine(
    status: ObserverStatus,
    onPublish: () -> Unit,
    onNotNow: () -> Unit,
    onAddServer: () -> Unit,
    onRetry: () -> Unit,
) {
    when (status) {
        ObserverStatus.AwaitingConsent ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(MaterialSymbols.Public, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes(Res.string.front_page_observer_notice_title), style = MaterialTheme.typography.titleSmall)
                    }
                    Text(stringRes(Res.string.front_page_observer_notice_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = onNotNow) { Text(stringRes(Res.string.front_page_not_now)) }
                        Button(onClick = onPublish, shape = RoundedCornerShape(14.dp)) { Text(stringRes(Res.string.front_page_publish_confirm)) }
                    }
                }
            }
        ObserverStatus.Publishing ->
            StatusCard {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringRes(Res.string.front_page_publishing), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        is ObserverStatus.Failed ->
            StatusCard {
                ObserverFailed(status.reason, onAddServer, onRetry)
            }
        else -> {}
    }
}

/** A line about the paper, on a card of its own: it may float over the paper's foot. */
@Composable
private fun StatusCard(content: @Composable RowScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** Why the last publish failed, and what fixes it. */
@Composable
private fun RowScope.ObserverFailed(
    reason: ObserverFailure,
    onAddServer: () -> Unit,
    onRetry: () -> Unit,
) {
    val (text, fix) =
        when (reason) {
            ObserverFailure.NO_SERVERS -> Res.string.front_page_publish_no_servers to Res.string.add_a_blossom_server
            ObserverFailure.READ -> Res.string.front_page_publish_read_failed to Res.string.retry
            else -> Res.string.front_page_publish_failed to Res.string.retry
        }
    Text(stringRes(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
    TextButton(onClick = if (reason == ObserverFailure.NO_SERVERS) onAddServer else onRetry) { Text(stringRes(fix)) }
}

/**
 * "Writing effort": Standard or Deep, kept in the schedule (its `effort` param) so every app and
 * the Assistant print with it. Deep says it costs more; the Print button's estimate shows how much.
 */
@Composable
private fun EffortRow(
    effort: WritingEffort,
    onEffort: (WritingEffort) -> Unit,
    ink: Color,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringRes(Res.string.front_page_effort),
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 16.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WritingEffort.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = effort == entry,
                    onClick = { onEffort(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = WritingEffort.entries.size),
                ) {
                    Text(stringRes(if (entry == WritingEffort.DEEP) Res.string.front_page_effort_deep else Res.string.front_page_effort_standard))
                }
            }
        }
        if (effort == WritingEffort.DEEP) {
            Text(
                stringRes(Res.string.front_page_effort_deep_note),
                style = MaterialTheme.typography.bodySmall,
                color = ink.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun ModelPicker(
    models: FrontPageModels,
    onModel: (String) -> Unit,
    colors: PillColors,
    ink: Color,
    editionSats: ((AgentModel?) -> Long?)? = null,
) {
    if (models.loading) {
        Row(Modifier.heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = ink)
            Spacer(Modifier.width(12.dp))
            Text(stringRes(Res.string.front_page_model), style = MaterialTheme.typography.bodyMedium, color = ink)
        }
        return
    }
    if (models.ids.isEmpty()) {
        // The provider lists no models: the reader names one, in the same pill as the key field.
        PillTextField(
            value = models.selected.orEmpty(),
            onValueChange = onModel,
            placeholder = stringRes(Res.string.front_page_model_id),
            leading = MaterialSymbols.AutoAwesome,
            colors = colors,
        )
        return
    }
    var picking by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val selected = models.selected
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .background(colors.container)
            .clickable { picking = true }
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = colors.icon, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringRes(Res.string.front_page_model), style = MaterialTheme.typography.labelSmall, color = colors.placeholder)
            Text(
                selected?.let { ModelChoices.readableName(it, models.byId[it]?.name) }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(MaterialSymbols.ExpandMore, contentDescription = null, tint = colors.icon)
    }
    if (picking) {
        ModelPickerDialog(models.ids, models.byId, selected, onSelect = onModel, onDismiss = { picking = false }, editionSats = editionSats)
    }
}

/** What the schedule controls do: the first two edit the synced schedule, the third this device's part in it. */
@Stable
class ScheduleActions(
    val onEnabled: (Boolean) -> Unit,
    val onTime: (hour: Int, minute: Int) -> Unit,
    val onPrintsHere: (Boolean) -> Unit,
    val onRetryDecrypt: () -> Unit,
    /**
     * Hands a schedule that names a runner back to the reader's devices. Clear-only: choosing a
     * runner is the provider's call, made in Brainstorm, never this app's.
     */
    val onPrintFromMyDevices: () -> Unit,
    val onRelaySettings: () -> Unit,
)

/**
 * The schedule all of the reader's devices share: its switch and what it says in words ("Every
 * weekday at 6:00 AM", on this device's clock; tap to change the time when this editor can), then
 * "Print from this device", who printed last, and a warning when a due morning didn't print.
 * While an edit is on its way ([busy]) the switches show it and every control waits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRow(
    schedule: MorningScheduleView,
    problem: ScheduleEdit?,
    busy: Boolean,
    actions: ScheduleActions,
    printedBy: PrintedBy?,
    ink: Color,
    /** A Routstr key whose balance may not cover the next paper: said next to the schedule, when it is on. */
    lowBalance: Boolean = false,
) {
    if (schedule.state == ScheduleState.LOADING) return
    var picking by remember { mutableStateOf(false) }
    val note = ink.copy(alpha = 0.75f)
    val indent = Modifier.padding(start = 34.dp)
    val canToggle = schedule.state == ScheduleState.READY || schedule.state == ScheduleState.NOT_SET_UP
    Column {
        SwitchRow(
            label = stringRes(Res.string.front_page_scheduled),
            checked = schedule.enabled,
            enabled = canToggle && !busy,
            onChange = actions.onEnabled,
            ink = ink,
            icon = MaterialSymbols.Schedule,
            style = MaterialTheme.typography.bodyLarge,
        )
        when (schedule.state) {
            ScheduleState.NOT_SET_UP -> ScheduleNote(stringRes(Res.string.front_page_schedule_not_set_up), note, indent)
            ScheduleState.CANT_DECRYPT -> {
                ScheduleNote(stringRes(if (schedule.retryable) Res.string.front_page_schedule_locked else Res.string.front_page_schedule_read_only), note, indent)
                if (schedule.retryable) {
                    TextButton(onClick = actions.onRetryDecrypt, modifier = indent, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text(stringRes(Res.string.try_again)) }
                }
            }
            ScheduleState.UNREADABLE -> {
                ScheduleNote(stringRes(Res.string.front_page_schedule_unreadable), note, indent)
                schedule.words?.let { ScheduleNote(scheduleWords(it), note, indent) }
            }
            else ->
                schedule.words?.let { words ->
                    val sentence = scheduleWords(words)
                    val editable = schedule.editableTime != null && !busy
                    Text(
                        sentence,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink,
                        modifier =
                            indent
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = editable, onClickLabel = stringRes(Res.string.front_page_schedule_change_time), role = Role.Button) { picking = true }
                                .padding(vertical = 12.dp),
                    )
                }
        }
        if (lowBalance && schedule.enabled) {
            Row(indent, verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringRes(Res.string.routstr_balance_low_schedule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        // When it last printed belongs to the schedule, not to this device's switch below.
        LastPrinted(schedule, note, indent)
        ScheduleWarnings(schedule, indent)
        if (!schedule.hasWriteRelays) {
            ScheduleNote(stringRes(Res.string.front_page_schedule_no_write_relays), note, indent)
            TextButton(onClick = actions.onRelaySettings, modifier = indent, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text(stringRes(Res.string.relay_settings)) }
        }
        // NIP-FB, `param`: a schedule with settings this app doesn't know is shown, never run here.
        if (schedule.unknownParams.isNotEmpty()) {
            ScheduleNote(stringRes(Res.string.front_page_unknown_params, schedule.unknownParams.joinToString(", ")), note, indent)
        }
        // Who prints on schedule, when the schedule names a runner (NIP-FB, Runner). Shown, never
        // picked here: the Assistant is chosen in Brainstorm. The reader can only take it back.
        printedBy?.let { RunnerPrints(it, busy, actions.onPrintFromMyDevices, ink, note, indent) }
        // A conflict, or a `p` that isn't a key: nobody prints it, and only the devices are offered.
        if (schedule.nobodyPrints) NobodyPrints(schedule, busy, actions.onPrintFromMyDevices, ink, indent)
        // Printed by a runner (or by nobody, in a conflict): this device's part is moot and hidden;
        // the reader's choice is kept for when the devices print again.
        if (!schedule.namesRunner) {
            // Off for every device, this device's part is moot: shown off and unavailable, not ON.
            val scheduleOn = schedule.enabled
            SwitchRow(
                label = stringRes(Res.string.front_page_print_from_device),
                checked = schedule.printsHere && schedule.canPrintHere && scheduleOn,
                enabled = scheduleOn && schedule.canPrintHere && !busy && schedule.state != ScheduleState.CANT_DECRYPT && schedule.state != ScheduleState.UNREADABLE,
                onChange = actions.onPrintsHere,
                ink = ink,
                modifier = indent,
                style = MaterialTheme.typography.bodyMedium,
            )
            // Amber and remote signers need the reader to decrypt the AI key: say so, in few words.
            if (!schedule.canPrintHere) ScheduleNote(stringRes(Res.string.front_page_needs_device_key), note, indent)
        }
        problem?.let { ScheduleProblem(stringRes(scheduleProblemText(it)), indent) }
    }
    if (picking) {
        val start = remember(schedule.editableTime) { schedule.editableTime ?: (7 to 0) }
        val state = rememberTimePickerState(initialHour = start.first, initialMinute = start.second, is24Hour = rememberIs24HourClock())
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringRes(Res.string.front_page_pick_time)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    actions.onTime(state.hour, state.minute)
                }) { Text(stringRes(Res.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringRes(Res.string.cancel)) } },
        )
    }
}

/** A labelled switch whose whole row toggles it, and is one switch to a screen reader. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    ink: Color,
    modifier: Modifier = Modifier,
    icon: MaterialSymbol? = null,
    style: TextStyle,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(label, style = style, color = ink, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * "Printed by …" for a schedule that names one runner: the reader's Brainstorm Assistant (with
 * the line saying it prints with its own AI key, and the link to give it one), else the runner's
 * profile name. "Print from my devices instead", confirmed, hands the schedule back.
 */
@Composable
private fun RunnerPrints(
    by: PrintedBy,
    busy: Boolean,
    onPrintFromMyDevices: () -> Unit,
    ink: Color,
    note: Color,
    modifier: Modifier,
) {
    var confirming by remember { mutableStateOf(false) }
    Text(
        if (by.isAssistant) {
            stringRes(Res.string.front_page_printed_by_assistant)
        } else {
            stringRes(Res.string.front_page_printed_by, by.name ?: shortNpub(by.runner))
        },
        style = MaterialTheme.typography.bodyMedium,
        color = ink,
        modifier = modifier.padding(top = 8.dp),
    )
    if (by.isAssistant) {
        // The Assistant prints with its own AI key, never the reader's: it needs one from Brainstorm.
        ScheduleNote(stringRes(Res.string.front_page_assistant_prints), note, modifier)
        val uriHandler = LocalUriHandler.current
        TextButton(
            onClick = { runCatching { uriHandler.openUri(BrainstormOnboarding.AI_MODEL_SETTINGS) } },
            modifier = modifier,
            colors = ButtonDefaults.textButtonColors(contentColor = ink),
        ) { Text(stringRes(Res.string.front_page_assistant_ai_settings)) }
    }
    TextButton(
        onClick = { confirming = true },
        enabled = !busy,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = ink),
    ) { Text(stringRes(Res.string.front_page_print_from_my_devices)) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringRes(Res.string.front_page_print_from_my_devices_title)) },
            text = { Text(stringRes(Res.string.front_page_print_from_my_devices_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onPrintFromMyDevices()
                }) { Text(stringRes(Res.string.front_page_use_my_devices)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringRes(Res.string.cancel)) } },
        )
    }
}

/** A schedule that names runners but none prints (a conflict, or a `p` that isn't a key): the fix is the reader's devices. */
@Composable
private fun NobodyPrints(
    schedule: MorningScheduleView,
    busy: Boolean,
    onPrintFromMyDevices: () -> Unit,
    ink: Color,
    modifier: Modifier,
) {
    Column(modifier) {
        ScheduleProblem(
            if (schedule.runnerConflict) {
                pluralStringRes(Res.plurals.front_page_runner_conflict, schedule.runners.size, schedule.runners.size)
            } else {
                stringRes(Res.string.front_page_runner_not_a_key)
            },
        )
        TextButton(
            onClick = onPrintFromMyDevices,
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = ink),
        ) { Text(stringRes(Res.string.front_page_use_my_devices)) }
    }
}

private fun scheduleProblemText(problem: ScheduleEdit) =
    when (problem) {
        ScheduleEdit.Unreadable -> Res.string.front_page_schedule_unreadable
        ScheduleEdit.CannotSign -> Res.string.front_page_schedule_cannot_sign
        ScheduleEdit.NotAccepted -> Res.string.front_page_schedule_not_accepted
        ScheduleEdit.NoWriteRelays -> Res.string.front_page_schedule_no_write_relays
        ScheduleEdit.NotExpressible -> Res.string.front_page_schedule_not_expressible
        ScheduleEdit.NoZone -> Res.string.front_page_schedule_no_zone
        else -> Res.string.front_page_schedule_unreachable
    }

/**
 * The schedule in words, on this device's clock: "Every weekday at 6:00 AM", "3 times a day at
 * …", "Every 6 hours at :15", "Monthly on day 1 at …", the zone named when the times are the
 * schedule's own, else the expression as written.
 */
@Composable
private fun scheduleWords(words: CronWords): String {
    val format = rememberTimeOfDayFormatter()
    val dayNames = remember { DateSkeletonFormatter("EEE") }
    val now = TimeUtils.now()

    fun time(
        hour: Int,
        minute: Int,
    ) = format(FrontPageClock.todayAt(now, hour, minute) * 1000)
    val (text, zone) =
        when (words) {
            is CronWords.Days -> {
                val at = time(words.time.hour, words.time.minute)
                val days = words.time.weekdays?.takeIf { it.size in 1..6 }
                val said =
                    when (days) {
                        null -> stringRes(Res.string.front_page_schedule_daily, at)
                        WEEKDAYS -> stringRes(Res.string.front_page_schedule_weekdays, at)
                        WEEKEND -> stringRes(Res.string.front_page_schedule_weekends, at)
                        else -> {
                            // A noon of each weekday this week, on this device's clock, named the platform's way.
                            val noon = FrontPageClock.todayAt(now, 12, 0)
                            val today = dayOfWeek(FrontPageClock.dayKey(noon)) ?: 1
                            val names = days.sorted().joinToString(", ") { dayNames.format((noon + (it - today) * 86_400L) * 1000) }
                            stringRes(Res.string.front_page_schedule_days, names, at)
                        }
                    }
                said to words.zone
            }
            is CronWords.TimesADay -> pluralStringRes(Res.plurals.front_page_schedule_times_a_day, words.times.size, words.times.size, words.times.joinToString(", ") { time(it.first, it.second) }) to words.zone
            is CronWords.EveryHours -> pluralStringRes(Res.plurals.front_page_schedule_every_hours, words.hours, words.hours, words.minute.toString().padStart(2, '0')) to null
            is CronWords.Monthly -> stringRes(Res.string.front_page_schedule_monthly, words.day, time(words.hour, words.minute)) to words.zone
            is CronWords.Raw -> words.cron to null
        }
    return if (zone != null) stringRes(Res.string.front_page_schedule_in_zone, text, zone) else text
}

private val WEEKDAYS = setOf(1, 2, 3, 4, 5)
private val WEEKEND = setOf(6, 7)

@Composable
private fun ScheduleNote(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** Why the last change wasn't saved, or a morning that didn't print, announced politely. */
@Composable
private fun ScheduleProblem(
    text: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.error
    Row(modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
        Icon(MaterialSymbols.Warning, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * "Last printed by Desktop (Linux), 2 hours ago": the finished claim's device and when; "Last
 * printed 2 days ago" when the last run isn't the latest claim's; "Not printed yet" before the first.
 */
@Composable
private fun LastPrinted(
    schedule: MorningScheduleView,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (schedule.state != ScheduleState.READY) return
    val ago = schedule.lastRanAgo
    if (schedule.lastRan == null || ago == null) {
        if (schedule.enabled) ScheduleNote(stringRes(Res.string.front_page_never_printed), color, modifier)
        return
    }
    val span = durationPhrase(ago)
    val by = schedule.lastBy
    val line =
        when {
            // From the runner's own Observer site: it can't write the schedule's `ran` (NIP-FB, Runner).
            schedule.lastByRunner -> stringRes(Res.string.front_page_last_printed_by_assistant, span)
            by != null -> stringRes(Res.string.front_page_last_printed, by, span)
            else -> stringRes(Res.string.front_page_last_printed_ago, span)
        }
    ScheduleNote(line, color, modifier)
}

/** A due morning that didn't print, shown on every device: dates on this device's calendar. */
@Composable
private fun ScheduleWarnings(
    schedule: MorningScheduleView,
    modifier: Modifier = Modifier,
) {
    schedule.missedSince?.let { ran ->
        // On the calendar The Observer reads "today" on: the schedule's zone.
        val calendar = ObserverCalendar.forSchedule(schedule)
        ScheduleProblem(stringRes(Res.string.front_page_not_printed_since, relativeTitle(calendar.dayOf(ran), calendar.dayOf(TimeUtils.now()))), modifier)
    }
    // A runner that missed its first morning: there is no earlier print to date the warning from.
    if (schedule.missedSince == null && schedule.missedDue != null) ScheduleProblem(stringRes(Res.string.front_page_assistant_not_printed), modifier)
}

/** The top bar's ⋮: the actions that don't earn an icon of their own. */
@Composable
private fun OverflowMenu(content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(MaterialSymbols.MoreVert, contentDescription = stringRes(Res.string.more_options))
        }
        RoundedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RoundedMenuSection { content { open = false } }
        }
    }
}

@Composable
private fun MenuItem(
    icon: MaterialSymbol,
    text: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Size20Modifier) },
        modifier = RoundedMenuItemModifier,
        onClick = onClick,
    )
}

/** "Add to bottom bar": offered only while the front page isn't there ([FrontPageShortcuts.offersBottomBar]). */
@Composable
private fun AddToBarItem(
    close: () -> Unit,
    onAdd: () -> Unit,
) = MenuItem(MaterialSymbols.AddToHomeScreen, stringRes(Res.string.front_page_add_to_bar)) {
    close()
    onAdd()
}

/** "Past editions": the kept papers before today's, in a sheet. */
@Composable
private fun PastEditionsItem(
    close: () -> Unit,
    onOpen: () -> Unit,
) = MenuItem(MaterialSymbols.History, stringRes(Res.string.front_page_past_editions)) {
    close()
    onOpen()
}

/** The edition's particulars: code, counts, model, tokens, cost, and anything the check took out. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(
    edition: SavedEdition,
    printing: Boolean,
    onCancelPrint: () -> Unit,
    site: NamedSiteEvent?,
    sitePinned: Boolean,
    onPinSite: (NamedSiteEvent) -> Unit,
    onOpenSite: ((NamedSiteEvent) -> Unit)?,
    onShareSite: (NamedSiteEvent) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(stringRes(Res.string.front_page_details), style = MaterialTheme.typography.titleLarge)
            Text(fullDate(edition.day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (edition.byRunner) Text(stringRes(Res.string.front_page_by_assistant), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        // A print running now (the reader's, or the morning's) can be stopped from here too, confirmed.
        if (printing) CancelPrintButton(onCancelPrint, Modifier.fillMaxWidth())
        // The reader's public Observer site, once there is one: open it as any nSite, or share it.
        site?.let { event ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                onOpenSite?.let { open ->
                    OutlinedButton(onClick = { open(event) }, shape = RoundedCornerShape(14.dp)) {
                        Icon(MaterialSymbols.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.front_page_open_observer))
                    }
                }
                OutlinedButton(onClick = { onShareSite(event) }, shape = RoundedCornerShape(14.dp)) {
                    Icon(MaterialSymbols.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringRes(Res.string.quick_action_share))
                }
                // A favorite app, like a pinned nSite, so the site sits in the bottom bar; tap again to unpin.
                if (sitePinned) {
                    FilledTonalButton(onClick = { onPinSite(event) }, shape = RoundedCornerShape(14.dp)) {
                        Icon(MaterialSymbols.PushPin, contentDescription = null, modifier = Modifier.size(18.dp), filled = true)
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.front_page_observer_pinned_label))
                    }
                } else {
                    OutlinedButton(onClick = { onPinSite(event) }, shape = RoundedCornerShape(14.dp)) {
                        Icon(MaterialSymbols.PushPin, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(Res.string.front_page_observer_pin))
                    }
                }
            }
        }
        DetailRow(stringRes(Res.string.front_page_edition_label), edition.code)
        DetailRow(stringRes(Res.string.front_page_posts_label), grouped(edition.posts.toLong()))
        DetailRow(stringRes(Res.string.front_page_quotes_label), grouped(edition.quotes.toLong()))
        DetailRow(stringRes(Res.string.front_page_model), edition.model)
        edition.tokens?.let { tokens ->
            DetailRow(
                stringRes(Res.string.front_page_tokens_label),
                pluralStringRes(Res.plurals.front_page_tokens, tokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), grouped(tokens)),
            )
        }
        edition.costUsd?.let { DetailRow(stringRes(Res.string.front_page_cost_label), formatUsd(it)) }
        if (edition.rounds == 2) Text(stringRes(Res.string.front_page_fixed_once), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (edition.removed.isNotEmpty()) {
            HorizontalDivider()
            Text(stringRes(Res.string.front_page_changes_title), style = MaterialTheme.typography.titleSmall)
            edition.removed.take(10).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * The paper filling its pane, edge to edge, on newsprint: today's, or a past one picked. A spinner while its page is read ([loading]); a page that can't be read says so.
 */
@Composable
private fun PaperPane(
    edition: SavedEdition,
    shown: ShownEdition?,
    loading: Boolean,
    modifier: Modifier,
) {
    Box(modifier.background(Newsprint)) {
        val html = shown?.html
        when {
            html != null -> Paper(edition, html, Modifier.fillMaxSize())
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.5.dp, color = Ink.copy(alpha = 0.6f))
            else -> Box(Modifier.align(Alignment.TopCenter).then(readable).padding(16.dp)) { Notice(stringRes(Res.string.front_page_edition_unreadable), error = true) }
        }
    }
}

/**
 * The checked page on the nSite surface, filling [modifier]: there is nothing else to scroll, so
 * the wheel scrolls it straight away. The status shows over it until it paints.
 */
@Composable
private fun Paper(
    edition: SavedEdition,
    html: String,
    modifier: Modifier,
) {
    val platform = LocalAppPlatform.current
    if (!platform.showsPapersInline) {
        Box(modifier.padding(16.dp)) { Notice(stringRes(Res.string.front_page_cannot_show)) }
        return
    }
    // What the surface says about itself: a blank frame never goes unexplained.
    var status by remember(edition.code) { mutableStateOf(InlineNappletStatus.LOADING) }
    var attempt by remember(edition.code) { mutableIntStateOf(0) }
    Box(modifier) {
        platform.InlinePaperSurface(
            html = html,
            title = "$MASTHEAD · ${edition.code}",
            // A new id on Retry opens a fresh session rather than waking the failed one.
            instanceId = "front-page-${edition.day}-${edition.code}-$attempt",
            onStatus = { status = it },
            modifier = Modifier.fillMaxSize(),
            takesWheel = true,
        )
        PaperStatus(status, onRetry = {
            attempt++
            status = InlineNappletStatus.LOADING
        })
    }
}

/**
 * Over the paper while it isn't showing. Tor gets its own words: with Tor on, the surface
 * fetches nothing (the art) until Tor runs, and gives up rather than go around it.
 */
@Composable
private fun BoxScope.PaperStatus(
    status: InlineNappletStatus,
    onRetry: () -> Unit,
) {
    if (status == InlineNappletStatus.SHOWING) return
    Column(
        Modifier.matchParentSize().background(Newsprint).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        when (status) {
            InlineNappletStatus.LOADING -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Ink.copy(alpha = 0.6f))
            InlineNappletStatus.WAITING_FOR_TOR -> {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = Ink.copy(alpha = 0.6f))
                Text(stringRes(Res.string.tor_waiting), style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(stringRes(Res.string.front_page_tor_pictures), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
            InlineNappletStatus.NO_TOR, InlineNappletStatus.FAILED -> {
                Text(
                    stringRes(if (status == InlineNappletStatus.NO_TOR) Res.string.front_page_no_tor else Res.string.front_page_show_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                if (status == InlineNappletStatus.NO_TOR) {
                    Text(stringRes(Res.string.front_page_tor_pictures), style = MaterialTheme.typography.bodySmall, color = Ink.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Ink.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                ) { Text(stringRes(Res.string.retry)) }
            }
            InlineNappletStatus.TOO_LARGE -> {
                // Retrying can't shrink it; the Download can still open it in a browser.
                Text(stringRes(Res.string.front_page_too_large), style = MaterialTheme.typography.titleMedium, color = Ink, textAlign = TextAlign.Center)
            }
            InlineNappletStatus.SHOWING -> {}
        }
    }
}

/**
 * Both steps before the first paper, in the setup card at once: the reader sees the second
 * before finishing the first. A done row stays tappable, to change it. A reader with papers
 * already, who removed a step, isn't told it is their first.
 */
@Composable
private fun SetupChecklist(
    accountViewModel: AccountViewModel,
    setup: FrontPageSetup,
    hasEditions: Boolean,
    onKey: () -> Unit,
    onLens: () -> Unit,
) {
    Column {
        Text(
            stringRes(if (hasEditions) Res.string.front_page_setup_title_again else Res.string.front_page_setup_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        SetupStep(
            label = stringRes(Res.string.front_page_setup_key),
            status = setup.provider?.let { stringRes(Res.string.front_page_setup_using, it) },
            action = stringRes(Res.string.front_page_connect),
            onClick = onKey,
        )
        // A lens this device has never seen: its profile is asked for while the checklist shows,
        // so the name fills in.
        setup.lens?.takeIf { setup.lensName == null }?.let { lens ->
            LoadUser(lens) { user -> if (user != null) UserFinderFilterAssemblerSubscription(user, accountViewModel) }
        }
        SetupStep(
            label = stringRes(Res.string.front_page_setup_lens),
            status =
                if (setup.hasLens) {
                    setup.lensName?.let { stringRes(Res.string.front_page_setup_ranked_by, it) } ?: stringRes(Res.string.front_page_setup_on)
                } else {
                    null
                },
            action = stringRes(Res.string.front_page_set_up),
            onClick = onLens,
        )
    }
}

/**
 * One checklist row: a check when [status] says it is done, an empty circle and [action] when
 * not. One element for a screen reader, its state announced, and a button that names what it does.
 */
@Composable
private fun SetupStep(
    label: String,
    status: String?,
    action: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val done = status != null
    val state = stringRes(if (done) Res.string.front_page_setup_done else Res.string.front_page_setup_to_do)
    val clickLabel = if (done) stringRes(Res.string.front_page_setup_change) else action
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = state }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) MaterialSymbols.CheckCircle else MaterialSymbols.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) scheme.primary else scheme.outline,
            filled = done,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        // The status or the action, at the end of the row; a long provider name is cut, not the label.
        Text(
            status ?: action,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
            color = if (done) scheme.onSurfaceVariant else scheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            MaterialSymbols.AutoMirrored.KeyboardArrowRight,
            contentDescription = null,
            tint = if (done) scheme.outline else scheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun Notice(
    text: String,
    error: Boolean = false,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** `12,480` in the reader's locale (`12.480`, `12 480`, …). */
private fun grouped(n: Long): String = formatGrouped(n)

/** The print in progress, or why the last one gave no page. A clean page shows as a kept edition. */
@Composable
private fun RunStatus(
    run: FrontPageRun,
    log: StateFlow<PrintLog>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (run) {
            FrontPageRun.Idle, FrontPageRun.Cancelled -> {}
            is FrontPageRun.Running -> Progress(run.step, log, onCancel)
            is FrontPageRun.Failed -> Notice(failureText(run), error = true)
            is FrontPageRun.Done ->
                when (val result = run.result) {
                    is PrintResult.NotReady -> Notice(stringRes(Res.string.front_page_not_ready))
                    is PrintResult.Refused -> Refused(result.violations)
                    is PrintResult.Printed -> {}
                }
        }
        // How an ended print went, step by step, for the reader who wants to know why.
        val ended = run is FrontPageRun.Failed || (run is FrontPageRun.Done && run.result !is PrintResult.Printed)
        if (ended) EndedDetails(log)
    }
}

/** An ended print's log, for the reader who wants to know why: read only once the print ended. */
@Composable
private fun EndedDetails(log: StateFlow<PrintLog>) {
    val entries by remember(log) { log.map { it.entries.isNotEmpty() }.distinctUntilChanged() }.collectAsStateWithLifecycle(log.value.entries.isNotEmpty())
    if (!entries) return
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        PrintDetailsToggle(log, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/**
 * [RunStatus] over today's paper: a print again as it runs, or why it gave no page, until the
 * reader puts that away (the paper under it is still today's).
 */
@Composable
private fun DismissibleRunStatus(
    run: FrontPageRun,
    log: StateFlow<PrintLog>,
    onCancel: () -> Unit,
) {
    var dismissed by remember { mutableStateOf<FrontPageRun?>(null) }
    val says =
        when (run) {
            is FrontPageRun.Running, is FrontPageRun.Failed -> true
            is FrontPageRun.Done -> run.result !is PrintResult.Printed
            else -> false
        }
    if (!says || run === dismissed) return
    Box {
        RunStatus(run, log, onCancel)
        if (run !is FrontPageRun.Running) {
            IconButton(onClick = { dismissed = run }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.dismiss), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Progress(
    step: PrintStep,
    log: StateFlow<PrintLog>,
    onCancel: () -> Unit,
) {
    val text =
        when (step) {
            PrintStep.Probe -> stringRes(Res.string.front_page_step_probe)
            is PrintStep.Pull -> stringRes(Res.string.front_page_step_pull, step.desksDone, step.desksTotal)
            is PrintStep.Write -> writeStepText(step)
            is PrintStep.Check -> stringRes(Res.string.front_page_step_check)
        }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // The print belongs to the account: where the reader can go meanwhile is the platform's to say.
                    Text(stringRes(LocalAppPlatform.current.printLeaveNote), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                }
                TextButton(onClick = onCancel) { Text(stringRes(Res.string.front_page_cancel_printing)) }
            }
            PrintDetailsToggle(log, Modifier.padding(start = 22.dp))
        }
    }
}

@Composable
private fun failureText(run: FrontPageRun.Failed): String = writerFailureSentence(run.failure, run.status, run.shortfallSats)

/** "Your Routstr balance may not cover the next paper", with Top up. */
@Composable
private fun RoutstrLowBalance(
    text: String,
    onTopUp: (() -> Unit)?,
    ink: Color,
) {
    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(MaterialSymbols.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        onTopUp?.let { TextButton(onClick = it, colors = ButtonDefaults.textButtonColors(contentColor = ink)) { Text(stringRes(Res.string.routstr_top_up)) } }
    }
}

/** The boundary's verdict kinds in the reader's words, never the enum's name. */
@Composable
private fun violationLabel(kind: Violation.Kind): String =
    stringRes(
        when (kind) {
            Violation.Kind.QUOTE -> Res.string.front_page_violation_quote
            Violation.Kind.IMAGE -> Res.string.front_page_violation_image
            Violation.Kind.LINK -> Res.string.front_page_violation_link
            Violation.Kind.MARKUP -> Res.string.front_page_violation_markup
        },
    )

@Composable
private fun Refused(violations: List<Violation>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(Res.string.front_page_refused_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringRes(Res.string.front_page_refused_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            violations.take(12).forEach {
                Text("${violationLabel(it.kind)} · ${it.excerpt.take(90)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/** "Nov 10": a past edition's date beside its day's name. */
private val shortDateFormatter = DateSkeletonFormatter("MMMd")

/** "Wednesday, November 11": the whole date, for screen readers and About this edition. */
private val fullDateFormatter = DateSkeletonFormatter("MMMMEEEEd")

/** [day]'s date, short, as the reader's locale writes it (the same noon as [editionTitle] picks). */
private fun shortDate(day: String): String = dayNoon(day)?.let { shortDateFormatter.format(it) } ?: day

/** [day]'s whole date: "Wednesday, November 11". */
private fun fullDate(day: String): String = dayNoon(day)?.let { fullDateFormatter.format(it) } ?: day

/**
 * [day] named as the rest of the app names days ([relativeDay]): "Today", "Yesterday", the weekday
 * within the past week, then "Tue, Nov 3". [today] is on the same calendar ([ObserverNow]).
 */
@Composable
private fun relativeTitle(
    day: String,
    today: String,
): String =
    when (val named = relativeDay(day, today)) {
        RelativeDay.Today -> stringRes(Res.string.today)
        RelativeDay.Yesterday -> stringRes(Res.string.yesterday)
        is RelativeDay.Weekday -> weekdayName(named.isoDay)
        RelativeDay.Date -> editionTitle(day)
    }

/** What a screen reader says for [relativeTitle]: "Today, Wednesday, November 11". */
@Composable
private fun relativeDescription(
    day: String,
    today: String,
): String = if (relativeDay(day, today) == RelativeDay.Date) fullDate(day) else relativeTitle(day, today) + ", " + fullDate(day)

@Composable
private fun weekdayName(isoDay: Int): String =
    stringRes(
        when (isoDay) {
            1 -> Res.string.dlist_day_monday
            2 -> Res.string.dlist_day_tuesday
            3 -> Res.string.dlist_day_wednesday
            4 -> Res.string.dlist_day_thursday
            5 -> Res.string.dlist_day_friday
            6 -> Res.string.dlist_day_saturday
            else -> Res.string.dlist_day_sunday
        },
    )
