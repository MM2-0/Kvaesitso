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
package com.vitorpamplona.amethyst.commons.browser.ui.pill

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission.Decision
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow

/** Sample states for the pill prototypes, shared by the previews and the offscreen render test. */
object BrowserPillSamples {
    val primal =
        BrowserPillUi(
            title = "Primal",
            chrome =
                BrowserChrome.State(
                    surface = BrowserChrome.Surface.WEB,
                    presentation = BrowserChrome.Presentation.FULL_SCREEN,
                    url = "https://primal.net/home",
                    startUrl = "https://primal.net/",
                    canGoBack = true,
                    torOn = false,
                ),
            isFavorite = true,
            consoleErrors = 3,
            sitePermissions = mapOf(BrowserSitePermission.CAMERA to Decision.ALLOW, BrowserSitePermission.LOCATION to Decision.BLOCK),
        )

    val overTorOutOfScope =
        BrowserPillUi(
            title = "Sign in – Accounts",
            chrome =
                BrowserChrome.State(
                    surface = BrowserChrome.Surface.WEB,
                    presentation = BrowserChrome.Presentation.EMBEDDED,
                    url = "https://accounts.example.com/login",
                    startUrl = "https://snort.social/",
                    canGoBack = true,
                    canGoForward = true,
                    isLoading = true,
                    torOn = true,
                ),
            loadProgress = 0.62f,
            textZoom = 115,
            desktopSite = true,
        )

    val insecure =
        BrowserPillUi(
            title = "Old Forum",
            chrome = BrowserChrome.State(BrowserChrome.Surface.WEB, BrowserChrome.Presentation.EMBEDDED, "http://oldforum.example.org/", "http://oldforum.example.org/"),
            loadProgress = 0.3f,
        )

    val napplet =
        BrowserPillUi(
            title = "Zap Poll",
            chrome =
                BrowserChrome.State(
                    surface = BrowserChrome.Surface.NAPPLET,
                    presentation = BrowserChrome.Presentation.FULL_SCREEN,
                    url = "",
                    startUrl = "",
                    canFavorite = true,
                    hasAccessInfo = true,
                    torOn = true,
                ),
        )

    val suggestions =
        listOf(
            AddressSuggestion("Primal", "https://primal.net/", isFavorite = true),
            AddressSuggestion("Snort", "https://snort.social/"),
            AddressSuggestion("Habla — long-form Nostr", "https://habla.news/"),
            AddressSuggestion("", "https://nostr.band/search?q=amethyst"),
        )

    val console =
        listOf(
            ConsoleLine(ConsoleLine.Level.INFO, "Connected to wss://relay.damus.io", "app.js", 112),
            ConsoleLine(ConsoleLine.Level.WARNING, "window.nostr.getRelays is deprecated", "nostr.js", 40),
            ConsoleLine(ConsoleLine.Level.LOG, "feed: 42 events in 180ms", "feed.js", 9),
            ConsoleLine(ConsoleLine.Level.ERROR, "Uncaught TypeError: Cannot read properties of undefined (reading 'pubkey')", "https://primal.net/assets/index-4f2a.js", 2211),
            ConsoleLine(ConsoleLine.Level.ERROR, "Failed to load (-2): net::ERR_NAME_NOT_RESOLVED", "https://cdn.example.com/x.png", 0),
            ConsoleLine(ConsoleLine.Level.DEBUG, "cache hit: profile 7a1c…", "cache.js", 77),
        )

    val certificate = CertificateInfo(issuedTo = "primal.net", issuedBy = "Let's Encrypt", validUntil = "Dec 14, 2026")
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    ThemeComparisonRow {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceDim).padding(bottom = 12.dp)) { content() }
    }
}

@Preview(widthDp = 820, heightDp = 1000)
@Composable
fun BrowserPillExpandedPreview() {
    PreviewFrame { BrowserPill(BrowserPillSamples.primal, expanded = true, onExpandedChange = {}, onEvent = {}, showClose = true) }
}

@Preview(widthDp = 820, heightDp = 1100)
@Composable
fun BrowserPillTorOutOfScopePreview() {
    PreviewFrame { BrowserPill(BrowserPillSamples.overTorOutOfScope, expanded = true, onExpandedChange = {}, onEvent = {}, initiallyTextSizeOpen = true) }
}

@Preview(widthDp = 820, heightDp = 700)
@Composable
fun BrowserPillAddressEditorPreview() {
    PreviewFrame {
        BrowserPill(
            BrowserPillSamples.primal,
            expanded = true,
            onExpandedChange = {},
            onEvent = {},
            suggestionsFor = { BrowserPillSamples.suggestions },
            clipboardUrl = "https://njump.me/npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z",
            initiallyEditing = true,
        )
    }
}

@Preview(widthDp = 820, heightDp = 800)
@Composable
fun BrowserPillNappletPreview() {
    PreviewFrame { BrowserPill(BrowserPillSamples.napplet, expanded = true, onExpandedChange = {}, onEvent = {}, showClose = true) }
}

@Preview(widthDp = 820, heightDp = 160)
@Composable
fun PillHandlesPreview() {
    ThemeComparisonRow {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceDim).padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            PillHandle(BrowserPillSamples.primal.copy(consoleErrors = 0), expanded = false, onExpandedChange = {})
            PillHandle(BrowserPillSamples.insecure, expanded = false, onExpandedChange = {})
            PillHandle(BrowserPillSamples.overTorOutOfScope.copy(consoleErrors = 2), expanded = false, onExpandedChange = {})
        }
    }
}

@Preview(widthDp = 820, heightDp = 220)
@Composable
fun FindInPagePreview() {
    ThemeComparisonRow {
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceDim)) {
            FindInPagePill(query = "zap", onQueryChange = {}, active = 2, total = 12, onNext = {}, onClose = {}, autoFocus = false)
            FindInPagePill(query = "lightning address", onQueryChange = {}, active = 0, total = 0, onNext = {}, onClose = {}, autoFocus = false)
        }
    }
}

@Preview(widthDp = 820, heightDp = 380)
@Composable
fun ConsoleSheetPreview() {
    ThemeComparisonRow { ConsoleSheet(BrowserPillSamples.console, onCopy = {}, onClear = {}, onCopyLine = {}) }
}

@Preview(widthDp = 820, heightDp = 700)
@Composable
fun PermissionPromptPreview() {
    PreviewFrame {
        Box(Modifier.padding(16.dp)) {
            PermissionPromptCard("meet.example.com", BrowserChrome.Security.TOR, setOf(BrowserSitePermission.CAMERA, BrowserSitePermission.MICROPHONE), onAllow = {}, onAllowOnce = {}, onDeny = {})
        }
    }
}

@Preview(widthDp = 820, heightDp = 560)
@Composable
fun PageDialogPreview() {
    PreviewFrame {
        Box(Modifier.padding(16.dp)) {
            PageDialogCard(
                type = PageDialogType.PROMPT,
                host = "snort.social",
                security = BrowserChrome.Security.HTTPS,
                message = "Name this relay set",
                defaultValue = "Friends",
                offerBlock = true,
                onResult = { _, _, _ -> },
                autoFocus = false,
            )
        }
    }
}

@Preview(widthDp = 820, heightDp = 420)
@Composable
fun LeaveSiteDialogPreview() {
    PreviewFrame {
        Box(Modifier.padding(16.dp)) {
            PageDialogCard(PageDialogType.BEFORE_UNLOAD, "habla.news", BrowserChrome.Security.HTTPS, message = "", onResult = { _, _, _ -> })
        }
    }
}

@Preview(widthDp = 820, heightDp = 1250)
@Composable
fun PageInfoPreview() {
    PreviewFrame {
        Box(Modifier.padding(16.dp).width(380.dp)) {
            PageInfoSheet(BrowserPillSamples.primal, BrowserPillSamples.certificate, onPermissionChange = { _, _ -> }, onClearSiteData = {}, onClearAllSiteData = {}, initiallyConfirmingClear = ClearScope.SITE)
        }
    }
}

@Preview
@Composable
fun AccessInfoPreview() {
    PreviewFrame {
        Box(Modifier.padding(16.dp).width(380.dp)) {
            AccessInfoSheet(
                title = "Habla",
                isWebsite = true,
                capabilities = listOf("Sign events as you", "Publish to your relays", "Upload files"),
                torOn = true,
                onManagePermissions = {},
                onDone = {},
            )
        }
    }
}
