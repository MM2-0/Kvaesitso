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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.connect_through_your_orbot_setup_short
import com.vitorpamplona.amethyst.commons.resources.orbot_socks_port
import com.vitorpamplona.amethyst.commons.resources.privacy_options
import com.vitorpamplona.amethyst.commons.resources.settings_section_tor_engine
import com.vitorpamplona.amethyst.commons.resources.settings_section_tor_routing
import com.vitorpamplona.amethyst.commons.resources.tor_preset
import com.vitorpamplona.amethyst.commons.resources.tor_preset_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_progress_notification_description
import com.vitorpamplona.amethyst.commons.resources.tor_progress_notification_title
import com.vitorpamplona.amethyst.commons.resources.tor_use_applets_and_websites
import com.vitorpamplona.amethyst.commons.resources.tor_use_applets_and_websites_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_dm_relays
import com.vitorpamplona.amethyst.commons.resources.tor_use_dm_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_images
import com.vitorpamplona.amethyst.commons.resources.tor_use_images_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_media_uploads
import com.vitorpamplona.amethyst.commons.resources.tor_use_media_uploads_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_money_operations
import com.vitorpamplona.amethyst.commons.resources.tor_use_money_operations_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_new_relays
import com.vitorpamplona.amethyst.commons.resources.tor_use_new_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_nip05_verification
import com.vitorpamplona.amethyst.commons.resources.tor_use_nip05_verification_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_onion_address
import com.vitorpamplona.amethyst.commons.resources.tor_use_onion_address_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_trusted_relays
import com.vitorpamplona.amethyst.commons.resources.tor_use_trusted_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_url_previews
import com.vitorpamplona.amethyst.commons.resources.tor_use_url_previews_explainer
import com.vitorpamplona.amethyst.commons.resources.tor_use_videos
import com.vitorpamplona.amethyst.commons.resources.tor_use_videos_explainer
import com.vitorpamplona.amethyst.commons.resources.use_internal_tor
import com.vitorpamplona.amethyst.commons.resources.use_internal_tor_explainer
import com.vitorpamplona.amethyst.commons.tor.TorPresetType
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.tor.explainerId
import com.vitorpamplona.amethyst.commons.tor.resourceId
import com.vitorpamplona.amethyst.commons.tor.torDefaultPreset
import com.vitorpamplona.amethyst.commons.tor.torFullyPrivate
import com.vitorpamplona.amethyst.commons.tor.torOnlyWhenNeededPreset
import com.vitorpamplona.amethyst.commons.tor.torSmallPayloadsPreset
import com.vitorpamplona.amethyst.commons.tor.whichPreset
import com.vitorpamplona.amethyst.commons.ui.components.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SegmentedChoiceTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsBlockTile
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsControlRow
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsDivider
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSection
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsSwitchTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOptionsScreen(nav: INav) {
    PrivacyOptionsScreen(LocalAppServices.current.torSettings, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyOptionsScreen(
    torSettings: TorSettingsFlow,
    nav: INav,
) {
    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.privacy_options), nav) },
    ) { padding ->
        PrivacyOptionsContent(torSettings, Modifier.padding(padding))
    }
}

// Every control writes straight to [TorSettingsFlow] via `tryEmit`; a debounced collector in
// TorSettingsStore persists the change automatically, so this screen has no Save/Cancel — the
// back arrow is the only chrome and the state is already saved by the time the user leaves.
@Composable
fun PrivacyOptionsContent(
    torSettings: TorSettingsFlow,
    modifier: Modifier = Modifier,
) {
    val torType by torSettings.torType.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SettingsSection(Res.string.settings_section_tor_engine) {
            TorEngineTile(torSettings, torType)
            AnimatedVisibility(
                visible = torType == TorType.EXTERNAL,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    SettingsDivider()
                    OrbotPortTile(torSettings)
                }
            }
            if (LocalAppPlatform.current.hasTorProgressNotification) {
                AnimatedVisibility(
                    visible = torType == TorType.INTERNAL,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        SettingsDivider()
                        TorSwitchTile(torSettings.progressNotification, MaterialSymbols.Notifications, Res.string.tor_progress_notification_title, Res.string.tor_progress_notification_description)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = torType != TorType.OFF,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                SettingsSection(Res.string.settings_section_tor_routing) {
                    PresetTile(torSettings)
                    SettingsDivider()
                    TorSwitchTile(torSettings.onionRelaysViaTor, MaterialSymbols.Public, Res.string.tor_use_onion_address, Res.string.tor_use_onion_address_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.dmRelaysViaTor, MaterialSymbols.Mail, Res.string.tor_use_dm_relays, Res.string.tor_use_dm_relays_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.newRelaysViaTor, MaterialSymbols.Dns, Res.string.tor_use_new_relays, Res.string.tor_use_new_relays_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.trustedRelaysViaTor, MaterialSymbols.Shield, Res.string.tor_use_trusted_relays, Res.string.tor_use_trusted_relays_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.moneyOperationsViaTor, MaterialSymbols.CurrencyBitcoin, Res.string.tor_use_money_operations, Res.string.tor_use_money_operations_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.nip05VerificationsViaTor, MaterialSymbols.AlternateEmail, Res.string.tor_use_nip05_verification, Res.string.tor_use_nip05_verification_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.urlPreviewsViaTor, MaterialSymbols.Link, Res.string.tor_use_url_previews, Res.string.tor_use_url_previews_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.imagesViaTor, MaterialSymbols.Image, Res.string.tor_use_images, Res.string.tor_use_images_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.videosViaTor, MaterialSymbols.Videocam, Res.string.tor_use_videos, Res.string.tor_use_videos_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.mediaUploadsViaTor, MaterialSymbols.CloudUpload, Res.string.tor_use_media_uploads, Res.string.tor_use_media_uploads_explainer)
                    SettingsDivider()
                    TorSwitchTile(torSettings.appletsAndWebsitesViaTor, MaterialSymbols.Language, Res.string.tor_use_applets_and_websites, Res.string.tor_use_applets_and_websites_explainer)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TorEngineTile(
    torSettings: TorSettingsFlow,
    torType: TorType,
) {
    SegmentedChoiceTile(
        icon = MaterialSymbols.Security,
        title = Res.string.use_internal_tor,
        description = Res.string.use_internal_tor_explainer,
        options = TorType.entries,
        labelRes = { it.resourceId },
        selected = torType,
        onSelect = { torSettings.torType.tryEmit(it) },
    )
}

@Composable
private fun OrbotPortTile(torSettings: TorSettingsFlow) {
    val port by torSettings.externalSocksPort.collectAsStateWithLifecycle()
    // Local text state so the field can hold a transient, mid-typing value; only a value that
    // parses to a valid Int is pushed to the flow (and thereby saved).
    var text by remember { mutableStateOf(port.toString()) }

    SettingsBlockTile(
        icon = MaterialSymbols.Dns,
        title = stringRes(Res.string.orbot_socks_port),
        description = stringRes(Res.string.connect_through_your_orbot_setup_short),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                it.toIntOrNull()?.let { validPort -> torSettings.externalSocksPort.tryEmit(validPort) }
            },
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Number,
                ),
            placeholder = {
                Text(
                    text = "9050",
                    color = MaterialTheme.colorScheme.placeholderText,
                )
            },
        )
    }
}

@Composable
private fun PresetTile(torSettings: TorSettingsFlow) {
    // propertyWatchFlow re-emits a full TorSettings on every toggle change, so the preset label
    // re-derives live (flipping to "Custom" the moment the flags stop matching a named preset).
    val current by torSettings.propertyWatchFlow.collectAsStateWithLifecycle(initialValue = torSettings.toSettings())
    val preset = whichPreset(current)
    var showPicker by remember { mutableStateOf(false) }

    SettingsControlRow(
        icon = MaterialSymbols.Tune,
        title = stringRes(Res.string.tor_preset),
        description = stringRes(Res.string.tor_preset_explainer),
        onClick = { showPicker = true },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringRes(preset.resourceId),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            Icon(
                symbol = MaterialSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )
        }
    }

    if (showPicker) {
        val options =
            TorPresetType.entries
                .map { TitleExplainer(stringRes(it.resourceId), stringRes(it.explainerId)) }
                .toImmutableList()
        SpinnerSelectionDialog(
            options = options,
            onDismiss = { showPicker = false },
        ) { index ->
            showPicker = false
            torSettings.applyPreset(TorPresetType.entries[index])
        }
    }
}

@Composable
private fun TorSwitchTile(
    flow: MutableStateFlow<Boolean>,
    icon: MaterialSymbol,
    title: StringResource,
    description: StringResource,
) {
    val checked by flow.collectAsStateWithLifecycle()
    SettingsSwitchTile(
        icon = icon,
        title = title,
        description = description,
        checked = checked,
        onCheckedChange = { flow.tryEmit(it) },
    )
}

/**
 * Applies a named preset by pushing its per-usage flags into the flow (auto-saving each). Only the
 * routing flags are touched — the engine choice and Orbot port are left as the user set them.
 * [TorPresetType.CUSTOM] is a no-op: it isn't a preset to apply, it's what the flags read as when
 * they match none of the named presets.
 */
private fun TorSettingsFlow.applyPreset(preset: TorPresetType) {
    val settings =
        when (preset) {
            TorPresetType.ONLY_WHEN_NEEDED -> torOnlyWhenNeededPreset
            TorPresetType.DEFAULT -> torDefaultPreset
            TorPresetType.SMALL_PAYLOADS -> torSmallPayloadsPreset
            TorPresetType.FULL_PRIVACY -> torFullyPrivate
            TorPresetType.CUSTOM -> return
        }
    onionRelaysViaTor.tryEmit(settings.onionRelaysViaTor)
    dmRelaysViaTor.tryEmit(settings.dmRelaysViaTor)
    newRelaysViaTor.tryEmit(settings.newRelaysViaTor)
    trustedRelaysViaTor.tryEmit(settings.trustedRelaysViaTor)
    urlPreviewsViaTor.tryEmit(settings.urlPreviewsViaTor)
    profilePicsViaTor.tryEmit(settings.profilePicsViaTor)
    imagesViaTor.tryEmit(settings.imagesViaTor)
    videosViaTor.tryEmit(settings.videosViaTor)
    moneyOperationsViaTor.tryEmit(settings.moneyOperationsViaTor)
    nip05VerificationsViaTor.tryEmit(settings.nip05VerificationsViaTor)
    mediaUploadsViaTor.tryEmit(settings.mediaUploadsViaTor)
    appletsAndWebsitesViaTor.tryEmit(settings.appletsAndWebsitesViaTor)
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun PrivacyOptionsScreenPreview() {
    ThemeComparisonRow {
        PrivacyOptionsScreen(TorSettingsFlow(), EmptyNav())
    }
}
