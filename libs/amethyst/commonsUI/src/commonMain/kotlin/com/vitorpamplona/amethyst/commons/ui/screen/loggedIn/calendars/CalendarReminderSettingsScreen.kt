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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.preferences.CalendarReminderSettings
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_settings_enabled_subtitle
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_settings_enabled_title
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_settings_lead_choice
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_settings_lead_subtitle
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_settings_lead_title
import com.vitorpamplona.amethyst.commons.resources.calendar_reminder_settings_title
import com.vitorpamplona.amethyst.commons.resources.settings_section_reminders
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsBlockTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarReminderSettingsScreen(nav: INav) {
    val services = LocalAppServices.current
    val scope = rememberCoroutineScope()
    val store = remember(services) { services.calendarReminderSettings }

    // DataStore reads are suspend, so the first frame renders the defaults and
    // the stored values arrive right after. Collecting the flow rather than
    // reading once also keeps the screen correct if the worker path or another
    // screen changes a value while this one is open.
    val settings by store.flow.collectAsStateWithLifecycle(CalendarReminderSettings())
    val enabled = settings.enabled
    val leadMinutes = settings.leadMinutes

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.calendar_reminder_settings_title), nav)
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SettingsSection(Res.string.settings_section_reminders) {
                // The switch writes straight to the device-scoped prefs and re-schedules (or
                // cancels) the WorkManager job on the spot — no Save button, the change is applied
                // the instant it's toggled.
                SettingsSwitchTile(
                    icon = MaterialSymbols.Notifications,
                    title = Res.string.calendar_reminder_settings_enabled_title,
                    description = Res.string.calendar_reminder_settings_enabled_subtitle,
                    checked = enabled,
                    onCheckedChange = {
                        scope.launch { store.setEnabled(it) }
                        // Cancel eagerly on disable so the periodic worker stops waking the
                        // process; re-enabling re-schedules immediately, and the ACCEPTED-RSVP
                        // observer in AppModules re-schedules on the next relevant RSVP too.
                        services.setCalendarRemindersScheduled(it)
                    },
                )
                SettingsDivider()
                SettingsBlockTile(
                    icon = MaterialSymbols.Schedule,
                    title = stringRes(Res.string.calendar_reminder_settings_lead_title),
                    description = stringRes(Res.string.calendar_reminder_settings_lead_subtitle),
                ) {
                    val choices = CalendarReminderSettings.LEAD_TIME_CHOICES
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        choices.forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = choice == leadMinutes,
                                enabled = enabled,
                                onClick = {
                                    scope.launch { store.setLeadMinutes(choice) }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                                icon = {},
                            ) {
                                Text(pluralStringRes(Res.plurals.calendar_reminder_settings_lead_choice, choice, choice))
                            }
                        }
                    }
                }
            }
        }
    }
}
