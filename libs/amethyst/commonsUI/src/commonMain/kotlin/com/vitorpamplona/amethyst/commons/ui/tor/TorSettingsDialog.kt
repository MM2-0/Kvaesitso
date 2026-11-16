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
package com.vitorpamplona.amethyst.commons.ui.tor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.connect_through_your_orbot_setup_short
import com.vitorpamplona.amethyst.commons.resources.invalid_port_number
import com.vitorpamplona.amethyst.commons.resources.orbot_socks_port
import com.vitorpamplona.amethyst.commons.resources.privacy_options
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
import com.vitorpamplona.amethyst.commons.tor.TorDialogViewModel
import com.vitorpamplona.amethyst.commons.tor.TorPresetType
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.tor.explainerId
import com.vitorpamplona.amethyst.commons.tor.parseTorPresetType
import com.vitorpamplona.amethyst.commons.tor.parseTorType
import com.vitorpamplona.amethyst.commons.tor.resourceId
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.components.edgeToEdgeDialogProperties
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource

@Composable
fun ConnectTorDialog(
    torSettings: TorSettings = TorSettings(),
    onClose: () -> Unit,
    onPost: (torSettings: TorSettings) -> Unit,
    onError: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = edgeToEdgeDialogProperties(),
    ) {
        SetDialogToEdgeToEdge()
        TorDialogContents(
            torSettings,
            onClose,
            onPost,
            onError,
        )
    }
}

@Preview
@Composable
fun TorDialogContentsPreview() {
    ThemeComparisonColumn {
        TorDialogContents(
            onClose = {},
            onPost = { },
            onError = {},
            torSettings = TorSettings(torType = TorType.EXTERNAL),
        )
    }
}

@Composable
fun TorDialogContents(
    torSettings: TorSettings,
    onClose: () -> Unit,
    onPost: (torSettings: TorSettings) -> Unit,
    onError: (String) -> Unit,
) {
    val dialogViewModel = rememberViewModel { TorDialogViewModel() }

    // runs only once and before the rest of the screen is build
    // to avoid blinking and animations from the default/previous
    // state to the current state
    val init =
        remember(dialogViewModel) {
            dialogViewModel.reset(torSettings)
            torSettings
        }

    TorDialogContents(dialogViewModel, onClose, onPost, onError)
}

@Composable
fun TorDialogContents(
    dialogViewModel: TorDialogViewModel,
    onClose: () -> Unit,
    onPost: (torSettings: TorSettings) -> Unit,
    onError: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            val toastMessage = stringRes(Res.string.invalid_port_number)
            SavingTopBar(
                titleRes = Res.string.privacy_options,
                onCancel = onClose,
                onPost = {
                    try {
                        onPost(dialogViewModel.save())
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        onError(toastMessage)
                    }
                },
            )
        },
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState(),
                ).padding(horizontal = 10.dp),
        ) {
            PrivacySettingsBody(dialogViewModel)
        }
    }
}

@Composable
fun PrivacySettingsBody(dialogViewModel: TorDialogViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 5.dp),
        verticalArrangement = Arrangement.spacedBy(Size10dp),
    ) {
        SettingsRow(
            Res.string.use_internal_tor,
            Res.string.use_internal_tor_explainer,
            persistentListOf(
                TitleExplainer(stringRes(TorType.OFF.resourceId)),
                TitleExplainer(stringRes(TorType.INTERNAL.resourceId)),
                TitleExplainer(stringRes(TorType.EXTERNAL.resourceId)),
            ),
            dialogViewModel.torType.value.screenCode,
        ) {
            dialogViewModel.torType.value = parseTorType(it)
        }

        AnimatedVisibility(
            visible = dialogViewModel.torType.value == TorType.EXTERNAL,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SettingsRow(
                Res.string.orbot_socks_port,
                Res.string.connect_through_your_orbot_setup_short,
            ) {
                OutlinedTextField(
                    value = dialogViewModel.socksPortStr.value,
                    onValueChange = { dialogViewModel.socksPortStr.value = it },
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

        if (LocalAppPlatform.current.hasTorProgressNotification) {
            AnimatedVisibility(
                visible = dialogViewModel.torType.value == TorType.INTERNAL,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                SwitchSettingsRow(
                    Res.string.tor_progress_notification_title,
                    Res.string.tor_progress_notification_description,
                    dialogViewModel.progressNotification,
                )
            }
        }
    }

    AnimatedVisibility(
        visible = dialogViewModel.torType.value != TorType.OFF,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp),
            verticalArrangement = Arrangement.spacedBy(Size10dp),
        ) {
            SettingsRow(
                Res.string.tor_preset,
                Res.string.tor_preset_explainer,
                persistentListOf(
                    TitleExplainer(stringRes(TorPresetType.ONLY_WHEN_NEEDED.resourceId), stringRes(TorPresetType.ONLY_WHEN_NEEDED.explainerId)),
                    TitleExplainer(stringRes(TorPresetType.DEFAULT.resourceId), stringRes(TorPresetType.DEFAULT.explainerId)),
                    TitleExplainer(stringRes(TorPresetType.SMALL_PAYLOADS.resourceId), stringRes(TorPresetType.SMALL_PAYLOADS.explainerId)),
                    TitleExplainer(stringRes(TorPresetType.FULL_PRIVACY.resourceId), stringRes(TorPresetType.FULL_PRIVACY.explainerId)),
                    TitleExplainer(stringRes(TorPresetType.CUSTOM.resourceId), stringRes(TorPresetType.CUSTOM.explainerId)),
                ),
                dialogViewModel.preset.value.screenCode,
            ) {
                dialogViewModel.setPreset(parseTorPresetType(it))
            }

            SwitchSettingsRow(
                Res.string.tor_use_onion_address,
                Res.string.tor_use_onion_address_explainer,
                dialogViewModel.onionRelaysViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_dm_relays,
                Res.string.tor_use_dm_relays_explainer,
                dialogViewModel.dmRelaysViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_new_relays,
                Res.string.tor_use_new_relays_explainer,
                dialogViewModel.newRelaysViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_trusted_relays,
                Res.string.tor_use_trusted_relays_explainer,
                dialogViewModel.trustedRelaysViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_money_operations,
                Res.string.tor_use_money_operations_explainer,
                dialogViewModel.moneyOperationsViaTor,
            )

            /*
             * Too hard to separate Coil into regular images and profile pics
             SwitchSettingsRow(
             Res.string.tor_use_profile_pictures,
             Res.string.tor_use_profile_pictures_explainer,
             dialogViewModel.profilePicsViaTor,
             )
             */

            SwitchSettingsRow(
                Res.string.tor_use_nip05_verification,
                Res.string.tor_use_nip05_verification_explainer,
                dialogViewModel.nip05VerificationsViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_url_previews,
                Res.string.tor_use_url_previews_explainer,
                dialogViewModel.urlPreviewsViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_images,
                Res.string.tor_use_images_explainer,
                dialogViewModel.imagesViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_videos,
                Res.string.tor_use_videos_explainer,
                dialogViewModel.videosViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_media_uploads,
                Res.string.tor_use_media_uploads_explainer,
                dialogViewModel.mediaUploadsViaTor,
            )

            SwitchSettingsRow(
                Res.string.tor_use_applets_and_websites,
                Res.string.tor_use_applets_and_websites_explainer,
                dialogViewModel.appletsAndWebsitesViaTor,
            )
        }
    }
}

@Composable
fun SwitchSettingsRow(
    name: StringResource,
    desc: StringResource,
    checked: MutableState<Boolean>,
) {
    SettingsRow(
        name,
        desc,
    ) {
        Switch(checked.value, onCheckedChange = { checked.value = it })
    }
}
