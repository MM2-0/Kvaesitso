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
package com.vitorpamplona.amethyst.commons.service.crashreports

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.crashreport_found
import com.vitorpamplona.amethyst.commons.resources.crashreport_found_send
import com.vitorpamplona.amethyst.commons.resources.would_you_like_to_send_the_recent_crash_report_to_amethyst_in_a_dm_no_personal_information_will_be_shared
import com.vitorpamplona.amethyst.commons.service.crashreports.DEV_REPORT_PUBKEY
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size16dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@Composable
fun DisplayCrashMessages(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val stackTrace = remember { mutableStateOf<String?>(null) }
    val services = LocalAppServices.current

    LaunchedEffect(accountViewModel) {
        // Already chosen in the desktop's crash window: straight to the draft, no second question.
        services.takeCrashReportToSend()?.let { stack ->
            nav.nav { routeToDevReport(stack, accountViewModel) }
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            stackTrace.value = services.takeCrashReport()
        }
    }

    stackTrace.value?.let { stack ->
        AlertDialog(
            onDismissRequest = { stackTrace.value = null },
            title = { Text(stringRes(Res.string.crashreport_found)) },
            text = {
                SelectionContainer {
                    Text(stringRes(Res.string.would_you_like_to_send_the_recent_crash_report_to_amethyst_in_a_dm_no_personal_information_will_be_shared))
                }
            },
            dismissButton = {
                TextButton(onClick = { stackTrace.value = null }) {
                    Text(stringRes(Res.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        nav.nav { routeToDevReport(stack, accountViewModel) }
                        stackTrace.value = null
                    },
                    contentPadding = PaddingValues(horizontal = Size16dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Done,
                            contentDescription = stringRes(Res.string.crashreport_found_send),
                        )
                        Spacer(StdHorzSpacer)
                        Text(stringRes(Res.string.crashreport_found_send))
                    }
                }
            },
        )
    }
}

/** A DM draft to Amethyst's developers holding [stack], expiring in 30 days. */
private fun routeToDevReport(
    stack: String,
    accountViewModel: AccountViewModel,
) = routeToMessage(
    user = LocalCache.getOrCreateUser(DEV_REPORT_PUBKEY),
    draftMessage = stack,
    accountViewModel = accountViewModel,
    expiresDays = 30,
)
