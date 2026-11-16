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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.battery_optimization_description
import com.vitorpamplona.amethyst.commons.resources.battery_optimization_fix_now
import com.vitorpamplona.amethyst.commons.resources.battery_optimization_title
import com.vitorpamplona.amethyst.commons.resources.notification_channel_status_off
import com.vitorpamplona.amethyst.commons.resources.notification_channel_status_on
import com.vitorpamplona.amethyst.commons.resources.notification_channel_status_silent
import com.vitorpamplona.amethyst.commons.resources.notification_service_accounts_title
import com.vitorpamplona.amethyst.commons.resources.notification_service_master_description
import com.vitorpamplona.amethyst.commons.resources.notification_service_master_title
import com.vitorpamplona.amethyst.commons.resources.notification_settings_categories_explainer
import com.vitorpamplona.amethyst.commons.resources.notification_settings_section_categories
import com.vitorpamplona.amethyst.commons.resources.notification_settings_section_delivery
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsItem
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.notifications.BatteryOptimizationHelper
import com.vitorpamplona.amethyst.service.notifications.NotificationChannels
import com.vitorpamplona.amethyst.ui.components.PushNotificationProviderTile
import com.vitorpamplona.amethyst.ui.components.hasPushNotificationProvider
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.launch

@Composable
fun AndroidNotificationDeliverySettings(accountViewModel: AccountViewModel) {
    // Global master switch (persisted, all accounts). produceState + runCatching keeps
    // the @Preview safe when Amethyst.instance / LocalPreferences aren't available.
    val master by produceState(initialValue = true) {
        runCatchingCancellable {
            LocalPreferences.notificationServiceEnabledFlow().collect { value = it }
        }
    }

    SettingsSection(Res.string.notification_settings_section_delivery) {
        if (hasPushNotificationProvider()) {
            PushNotificationProviderTile(accountViewModel.settings.uiSettingsFlow)
            SettingsDivider()
        }
        SettingsSwitchTile(
            icon = MaterialSymbols.Notifications,
            title = Res.string.notification_service_master_title,
            description = Res.string.notification_service_master_description,
            checked = master,
            onCheckedChange = { LocalPreferences.setNotificationServiceEnabled(it) },
        )
    }

    if (master) {
        BackgroundAccountsSection(accountViewModel)
        BatteryOptimizationBanner()
    }
}

/**
 * Per-account participation list, shown under the master switch: one "keep active in the
 * background" toggle per write-enabled account. Each row toggles that account's own
 * [AccountSettings.alwaysOnNotificationService]; because LocalPreferences caches one
 * AccountSettings per npub, the toggle reaches the same instance the always-on manager
 * observes, so participation changes take effect live.
 */
@Composable
private fun BackgroundAccountsSection(accountViewModel: AccountViewModel) {
    val accounts by produceState<List<Pair<AccountInfo, AccountSettings>>>(emptyList()) {
        value =
            runCatching {
                LocalPreferences
                    .allSavedAccounts()
                    .filter { it.hasPrivKey || it.loggedInWithExternalSigner }
                    .mapNotNull { info ->
                        LocalPreferences.loadAccountConfigFromEncryptedStorage(info.npub)?.let { info to it }
                    }
            }.getOrDefault(emptyList())
    }

    if (accounts.isEmpty()) return

    SettingsSection(Res.string.notification_service_accounts_title) {
        accounts.forEachIndexed { index, (info, settings) ->
            if (index > 0) SettingsDivider()
            AccountParticipationRow(info, settings, accountViewModel)
        }
    }
}

@Composable
private fun AccountParticipationRow(
    info: AccountInfo,
    settings: AccountSettings,
    accountViewModel: AccountViewModel,
) {
    val participates by settings.alwaysOnNotificationService.collectAsStateWithLifecycle()
    val pubkeyHex = remember(info) { decodePublicKeyAsHexOrNull(info.npub) }
    val npubShort = remember(info) { info.npub.toShortDisplay() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { settings.toggleAlwaysOnNotificationService() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Weight built here in RowScope, then captured by the LoadUser content lambda
        // (which is not itself a RowScope) so the name still expands to fill the row.
        val nameModifier =
            Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 12.dp)

        // These are other/background accounts, not the logged-in one, so LoadUser
        // resolves (and lazily creates) the User behind each npub off the main thread,
        // then the shared UserPicture / UsernameDisplay observe its live metadata.
        if (pubkeyHex != null) {
            LoadUser(pubkeyHex) { user ->
                if (user != null) {
                    ClickableUserPicture(
                        baseUser = user,
                        size = Size35dp,
                        accountViewModel = accountViewModel,
                    )
                    UsernameDisplay(
                        baseUser = user,
                        weight = nameModifier,
                        accountViewModel = accountViewModel,
                    )
                } else {
                    Text(
                        text = npubShort,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = nameModifier,
                    )
                }
            }
        } else {
            Text(
                text = npubShort,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = nameModifier,
            )
        }

        Switch(
            checked = participates,
            onCheckedChange = { settings.toggleAlwaysOnNotificationService() },
        )
    }
}

@Composable
fun AndroidNotificationCategorySettings() {
    val context = LocalContext.current
    val entries = NotificationChannels.contentChannels
    val scope = rememberCoroutineScope()

    // Read each channel's importance after every resume so toggling
    // sound/importance in the system page reflects back here. The map IS
    // the state — no key-bump trick needed.
    var statuses by remember {
        mutableStateOf<Map<String, NotificationChannels.ChannelStatus>>(emptyMap())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        // Re-read each channel's importance on every resume, so toggling
        // sound/importance in the system page reflects back here. The map IS
        // the state - no key-bump trick needed. Reading a channel id is a
        // suspend call now (Compose resources), hence a coroutine rather than
        // LifecycleResumeEffect.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            statuses =
                entries.associate {
                    val id = it.channelId(context)
                    id to NotificationChannels.statusOf(context, id)
                }
        }
    }

    SettingsSection(Res.string.notification_settings_section_categories) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            val channelId by produceState("", entry) { value = entry.channelId(context) }
            // Default to ON for channels not yet created — matches Android's
            // own default importance, so the badge isn't misleading before the
            // user has interacted with the channel.
            val status = statuses[channelId] ?: NotificationChannels.ChannelStatus.ON
            SettingsItem(
                title = entry.nameRes,
                icon = entry.icon,
                trailing = { ChannelStatusBadge(status) },
                onClick = {
                    scope.launch {
                        // Lazy-create the channel right before opening so the system
                        // per-channel page has something to display; idempotent.
                        entry.ensure(context)
                        NotificationChannels.openChannelSettings(context, channelId)
                    }
                },
            )
        }
    }

    Text(
        text = stringRes(Res.string.notification_settings_categories_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun ChannelStatusBadge(status: NotificationChannels.ChannelStatus) {
    when (status) {
        NotificationChannels.ChannelStatus.ON ->
            StatusChip(
                label = stringRes(Res.string.notification_channel_status_on),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        NotificationChannels.ChannelStatus.SILENT ->
            StatusChip(
                label = stringRes(Res.string.notification_channel_status_silent),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        NotificationChannels.ChannelStatus.OFF ->
            StatusChip(
                label = stringRes(Res.string.notification_channel_status_off),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
    }
}

@Composable
private fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(containerColor)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun BatteryOptimizationBanner() {
    val context = LocalContext.current
    var isExempt by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    LifecycleResumeEffect(Unit) {
        isExempt = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose {}
    }

    if (isExempt) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringRes(Res.string.battery_optimization_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringRes(Res.string.battery_optimization_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = { BatteryOptimizationHelper.requestBatteryOptimizationExemption(context) },
            ) {
                Text(stringRes(Res.string.battery_optimization_fix_now))
            }
        }
    }
}
