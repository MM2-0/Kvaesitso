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
package com.vitorpamplona.amethyst.commons.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.PageLoadFailure
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.page_error_cleartext_body
import com.vitorpamplona.amethyst.commons.resources.page_error_cleartext_title
import com.vitorpamplona.amethyst.commons.resources.page_error_insecure_body
import com.vitorpamplona.amethyst.commons.resources.page_error_insecure_title
import com.vitorpamplona.amethyst.commons.resources.page_error_not_found_body
import com.vitorpamplona.amethyst.commons.resources.page_error_not_found_title
import com.vitorpamplona.amethyst.commons.resources.page_error_offline_body
import com.vitorpamplona.amethyst.commons.resources.page_error_offline_title
import com.vitorpamplona.amethyst.commons.resources.page_error_onion_body
import com.vitorpamplona.amethyst.commons.resources.page_error_onion_title
import com.vitorpamplona.amethyst.commons.resources.page_error_open_with_tor
import com.vitorpamplona.amethyst.commons.resources.page_error_other_body
import com.vitorpamplona.amethyst.commons.resources.page_error_other_title
import com.vitorpamplona.amethyst.commons.resources.page_error_proxy_down_body
import com.vitorpamplona.amethyst.commons.resources.page_error_proxy_down_title
import com.vitorpamplona.amethyst.commons.resources.page_error_redirect_loop_body
import com.vitorpamplona.amethyst.commons.resources.page_error_redirect_loop_title
import com.vitorpamplona.amethyst.commons.resources.page_error_timed_out_body
import com.vitorpamplona.amethyst.commons.resources.page_error_timed_out_title
import com.vitorpamplona.amethyst.commons.resources.page_error_try_without_tor
import com.vitorpamplona.amethyst.commons.resources.page_error_try_without_tor_note
import com.vitorpamplona.amethyst.commons.resources.page_error_unreachable_body
import com.vitorpamplona.amethyst.commons.resources.page_error_unreachable_title
import com.vitorpamplona.amethyst.commons.resources.page_error_unreachable_tor_body
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource

/**
 * The browser's own page for a main frame that failed to load, in place of the WebView's built-in one
 * (an Android robot over raw "net::ERR_…" text, unreadable in dark mode and with no way to retry).
 * Names the cause in words, keeps the raw [detail] code small underneath for anyone debugging, and
 * offers [onRetry], which turns into a spinner while [retrying]. [viaTor] rewords the reachability case: over Tor every failure to reach the site
 * looks the same, so it can't promise the address exists. [onSwitchTor], when given, adds the one route
 * change that could help: "Open with Tor" for an onion address, else "Try without Tor" with a note on
 * what that gives away — the caller offers it only where switching would actually change the route.
 */
@Composable
fun PageLoadError(
    failure: PageLoadFailure,
    host: String,
    detail: String?,
    viaTor: Boolean,
    retrying: Boolean,
    onRetry: () -> Unit,
    onSwitchTor: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val copy = failure.errorCopy(viaTor)
    // A dead name over Tor fails again within a millisecond, which would make Try again look like it did
    // nothing. Hold the spinner briefly so every tap visibly retries.
    var justTapped by remember { mutableStateOf(false) }
    LaunchedEffect(justTapped) {
        if (justTapped) {
            delay(MIN_RETRY_SPINNER_MS)
            justTapped = false
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 420.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                symbol = copy.symbol,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringRes(copy.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringRes(copy.body, host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                if (retrying || justTapped) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                } else {
                    Button(
                        onClick = {
                            justTapped = true
                            onRetry()
                        },
                    ) {
                        Text(stringRes(Res.string.try_again))
                    }
                }
            }
            if (onSwitchTor != null && !retrying && !justTapped) {
                val toTor = failure == PageLoadFailure.ONION_NEEDS_TOR
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        justTapped = true
                        onSwitchTor()
                    },
                ) {
                    Text(stringRes(if (toTor) Res.string.page_error_open_with_tor else Res.string.page_error_try_without_tor))
                }
                if (!toTor) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringRes(Res.string.page_error_try_without_tor_note, host),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val MIN_RETRY_SPINNER_MS = 700L

private class PageErrorCopy(
    val symbol: MaterialSymbol,
    val title: StringResource,
    val body: StringResource,
)

private fun PageLoadFailure.errorCopy(viaTor: Boolean): PageErrorCopy =
    when (this) {
        PageLoadFailure.NOT_FOUND -> PageErrorCopy(MaterialSymbols.PublicOff, Res.string.page_error_not_found_title, Res.string.page_error_not_found_body)
        PageLoadFailure.UNREACHABLE ->
            PageErrorCopy(
                MaterialSymbols.PublicOff,
                Res.string.page_error_unreachable_title,
                if (viaTor) Res.string.page_error_unreachable_tor_body else Res.string.page_error_unreachable_body,
            )
        PageLoadFailure.PROXY_DOWN -> PageErrorCopy(MaterialSymbols.SyncProblem, Res.string.page_error_proxy_down_title, Res.string.page_error_proxy_down_body)
        PageLoadFailure.TIMED_OUT -> PageErrorCopy(MaterialSymbols.Timer, Res.string.page_error_timed_out_title, Res.string.page_error_timed_out_body)
        PageLoadFailure.OFFLINE -> PageErrorCopy(MaterialSymbols.PublicOff, Res.string.page_error_offline_title, Res.string.page_error_offline_body)
        PageLoadFailure.INSECURE -> PageErrorCopy(MaterialSymbols.NoEncryption, Res.string.page_error_insecure_title, Res.string.page_error_insecure_body)
        PageLoadFailure.REDIRECT_LOOP -> PageErrorCopy(MaterialSymbols.Warning, Res.string.page_error_redirect_loop_title, Res.string.page_error_redirect_loop_body)
        PageLoadFailure.ONION_NEEDS_TOR -> PageErrorCopy(MaterialSymbols.Lock, Res.string.page_error_onion_title, Res.string.page_error_onion_body)
        PageLoadFailure.CLEARTEXT_BLOCKED -> PageErrorCopy(MaterialSymbols.NoEncryption, Res.string.page_error_cleartext_title, Res.string.page_error_cleartext_body)
        PageLoadFailure.OTHER -> PageErrorCopy(MaterialSymbols.PublicOff, Res.string.page_error_other_title, Res.string.page_error_other_body)
    }
