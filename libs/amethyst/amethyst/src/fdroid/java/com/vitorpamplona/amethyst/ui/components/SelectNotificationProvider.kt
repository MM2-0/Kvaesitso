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
package com.vitorpamplona.amethyst.ui.components

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_dialog_button_ok
import com.vitorpamplona.amethyst.commons.resources.push_server_explainer
import com.vitorpamplona.amethyst.commons.resources.push_server_install_app
import com.vitorpamplona.amethyst.commons.resources.push_server_install_app_description
import com.vitorpamplona.amethyst.commons.resources.push_server_none
import com.vitorpamplona.amethyst.commons.resources.push_server_none_explainer
import com.vitorpamplona.amethyst.commons.resources.push_server_title
import com.vitorpamplona.amethyst.commons.resources.push_server_uses_app_explainer
import com.vitorpamplona.amethyst.commons.resources.quick_action_dont_show_again_button
import com.vitorpamplona.amethyst.commons.resources.select_push_server
import com.vitorpamplona.amethyst.commons.ui.components.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.commons.ui.components.TextSpinner
import com.vitorpamplona.amethyst.commons.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.SettingsBlockTile
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.service.notifications.PushDistributorHandler
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SelectNotificationProvider(sharedPrefs: UiSettingsFlow) {
    val notificationPermissionGranted =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermissionState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

            if (!notificationPermissionState.status.isGranted) {
                val dontAskForNotificationPermissions by sharedPrefs.dontAskForNotificationPermissions.collectAsStateWithLifecycle()
                if (!dontAskForNotificationPermissions) {
                    sharedPrefs.dontAskForNotificationPermissions()

                    // This will pause the APP, including the connection with relays.
                    LaunchedEffect(notificationPermissionState) {
                        notificationPermissionState.launchPermissionRequest()
                    }
                }
            }

            notificationPermissionState.status.isGranted
        } else {
            true
        }

    if (notificationPermissionGranted) {
        val pushNotificationSelector by sharedPrefs.dontShowPushNotificationSelector.collectAsStateWithLifecycle()
        if (!pushNotificationSelector) {
            val context = LocalContext.current
            var distributorPresent by remember {
                mutableStateOf(PushDistributorHandler.savedDistributorExists())
            }
            if (!distributorPresent) {
                LoadDistributors { currentDistributor, list, readableListWithExplainer ->
                    if (readableListWithExplainer.size > 1) {
                        SpinnerSelectionDialog(
                            title = stringRes(id = Res.string.select_push_server),
                            options = readableListWithExplainer,
                            onSelect = { index ->
                                if (list[index] == "None") {
                                    PushDistributorHandler.forceRemoveDistributor(context)
                                    sharedPrefs.dontAskForNotificationPermissions()
                                    sharedPrefs.dontShowPushNotificationSelector()
                                } else {
                                    val fullDistributorName = list[index]
                                    PushDistributorHandler.saveDistributor(fullDistributorName)
                                }
                                distributorPresent = true
                                Log.d("Amethyst", "NotificationScreen: Distributor registered.")
                            },
                            onDismiss = {
                                distributorPresent = true
                                Log.d("Amethyst", "NotificationScreen: Distributor dialog dismissed.")
                            },
                        )
                    } else {
                        AlertDialog(
                            onDismissRequest = { distributorPresent = true },
                            title = { Text(stringRes(Res.string.push_server_install_app)) },
                            text = {
                                val content = stringRes(Res.string.push_server_install_app_description)

                                val astNode =
                                    remember {
                                        CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(content)
                                    }

                                RichText(
                                    style = RichTextStyle().resolveDefaults(),
                                    renderer = null,
                                ) {
                                    BasicMarkdown(astNode)
                                }
                            },
                            confirmButton = {
                                Row(
                                    modifier = Modifier.padding(all = 8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    TextButton(
                                        onClick = {
                                            distributorPresent = true
                                            sharedPrefs.dontShowPushNotificationSelector()
                                        },
                                    ) {
                                        Text(stringRes(Res.string.quick_action_dont_show_again_button))
                                    }
                                    Button(
                                        onClick = { distributorPresent = true },
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                symbol = MaterialSymbols.Check,
                                                contentDescription = null,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringRes(Res.string.error_dialog_button_ok))
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadDistributors(onInner: @Composable (String, ImmutableList<String>, ImmutableList<TitleExplainer>) -> Unit) {
    val currentDistributor = PushDistributorHandler.getSavedDistributor().ifBlank { null } ?: "None"

    val list =
        remember {
            PushDistributorHandler.getInstalledDistributors().plus("None").toImmutableList()
        }

    val readableListWithExplainer =
        PushDistributorHandler
            .formattedDistributorNames()
            .mapIndexed { index, name ->
                TitleExplainer(
                    name,
                    stringRes(id = Res.string.push_server_uses_app_explainer, list[index]),
                )
            }.plus(
                TitleExplainer(
                    stringRes(id = Res.string.push_server_none),
                    stringRes(id = Res.string.push_server_none_explainer),
                ),
            ).toImmutableList()

    onInner(
        currentDistributor,
        list,
        readableListWithExplainer,
    )
}

fun hasPushNotificationProvider(): Boolean = true

@Composable
fun PushNotificationProviderTile(sharedPrefs: UiSettingsFlow) {
    val context = LocalContext.current

    LoadDistributors { currentDistributor, list, readableListWithExplainer ->
        val selectedIndex = list.indexOf(currentDistributor).coerceAtLeast(0)
        SettingsBlockTile(
            icon = MaterialSymbols.CloudSync,
            title = stringRes(Res.string.push_server_title),
            description = stringRes(Res.string.push_server_explainer),
        ) {
            TextSpinner(
                label = null,
                placeholder = readableListWithExplainer[selectedIndex].title,
                options = readableListWithExplainer,
                onSelect = { index ->
                    if (list[index] == "None") {
                        sharedPrefs.dontAskForNotificationPermissions()
                        sharedPrefs.dontShowPushNotificationSelector()
                        PushDistributorHandler.forceRemoveDistributor(context)
                    } else {
                        PushDistributorHandler.saveDistributor(list[index])
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
