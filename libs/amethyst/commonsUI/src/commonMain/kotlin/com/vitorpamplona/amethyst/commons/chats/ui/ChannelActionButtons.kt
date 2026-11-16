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
package com.vitorpamplona.amethyst.commons.chats.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.join
import com.vitorpamplona.amethyst.commons.resources.leave
import com.vitorpamplona.amethyst.commons.resources.mute_notifications
import com.vitorpamplona.amethyst.commons.resources.unmute_notifications
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfHorzModifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.ZeroPadding

// Membership actions shared by every channel header (NIP-28 public chats and ephemeral chats).
// They take the action as a callback so the header that knows the account decides what
// joining, leaving or muting means.

@Composable
fun JoinChannelButton(onClick: () -> Unit) {
    FilledTonalButton(
        modifier = HalfHalfHorzModifier,
        onClick = onClick,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(Res.string.join))
    }
}

@Composable
fun LeaveChannelButton(onClick: () -> Unit) {
    FilledTonalButton(
        modifier = HalfHalfHorzModifier,
        onClick = onClick,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(Res.string.leave))
    }
}

/** Bell toggle for a channel's notifications; [isMuted] picks the icon and label. */
@Composable
fun MuteChannelButton(
    isMuted: Boolean,
    onToggle: () -> Unit,
) {
    val label =
        stringRes(
            if (isMuted) Res.string.unmute_notifications else Res.string.mute_notifications,
        )

    FilledTonalButton(
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .width(50.dp),
        onClick = onToggle,
        contentPadding = ZeroPadding,
    ) {
        Icon(
            symbol = if (isMuted) MaterialSymbols.NotificationsOff else MaterialSymbols.Notifications,
            contentDescription = label,
            modifier = Size20Modifier,
        )
    }
}
