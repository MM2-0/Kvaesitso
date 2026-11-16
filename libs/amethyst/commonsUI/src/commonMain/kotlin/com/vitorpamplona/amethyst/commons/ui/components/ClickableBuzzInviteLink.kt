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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.quartz.buzz.invite.BuzzInviteLink
import kotlinx.coroutines.launch

/**
 * Renders a Buzz workspace invite link (`https://<host>/invite/<token>`) inline as a tappable
 * link that opens the in-app join flow ([Route.BuzzInvite], which confirms the workspace and
 * hands off to the `window.nostr` browser for the policy + NIP-98 claim) instead of the
 * external browser. Long-press copies the full link. Falls back to plain text if the literal
 * can't be parsed (detection should guarantee it does).
 */
@Composable
fun ClickableBuzzInviteLink(
    linkText: String,
    nav: INav,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val parsed = remember(linkText) { BuzzInviteLink.parse(linkText) }

    if (parsed == null) {
        Text(text = linkText)
        return
    }

    val clickableModifier =
        remember(linkText) {
            Modifier.combinedClickable(
                onLongClick = { scope.launch { clipboardManager.setText(linkText) } },
                onClick = { nav.nav(Route.BuzzInvite(linkText)) },
            )
        }

    Text(
        text = linkText,
        modifier = clickableModifier,
        color = MaterialTheme.colorScheme.primary,
        overflow = TextOverflow.MiddleEllipsis,
        maxLines = 1,
    )
}
