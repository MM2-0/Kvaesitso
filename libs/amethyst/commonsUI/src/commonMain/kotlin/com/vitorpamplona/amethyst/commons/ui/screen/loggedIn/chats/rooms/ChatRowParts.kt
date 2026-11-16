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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.ui.theme.ChatLabelMaxWidth
import com.vitorpamplona.amethyst.commons.ui.theme.ChatRowPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText

private val ChatRowTypeIconModifier = Modifier.size(15.dp)
private val ChatRowLabelIconModifier = Modifier.size(13.dp)

/**
 * The room-kind glyph after a Messages-row title (globe for a public chat, lock for a Marmot group,
 * pin for a geohash cell, ...). Icon only: the glyph is enough to tell kinds apart at a glance, and a
 * word in a chip on every row competed with the room name for the eye. [contentDescription] carries
 * the word for screen readers.
 */
@Composable
fun ChatRowTypeIcon(
    symbol: MaterialSymbol,
    contentDescription: String?,
) {
    Icon(
        symbol = symbol,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.placeholderText,
        modifier = ChatRowTypeIconModifier,
    )
}

/**
 * Muted metadata after a Messages-row title when the room needs a *name* to be told apart, not just a
 * kind: the relay a NIP-29 group lives on, the Concord community a channel belongs to, the cordn
 * coordinator. No fill — it reads as a caption of the title, not as a second title. Capped at
 * [ChatLabelMaxWidth] with a middle ellipsis so it never crowds the room name out.
 *
 * When [onClick] is set, only a tap that lands on the label itself reaches it. The whole row opens the
 * chat, which is what a tap almost always means; Compose would otherwise grow this ~16dp-tall target
 * to the 48dp minimum touch size, and that slop covers the room name and the preview line around it.
 */
@Composable
fun ChatRowLabel(
    symbol: MaterialSymbol,
    text: String,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    if (onClick == null) {
        ChatRowLabelContent(symbol, text, contentDescription, Modifier)
    } else {
        val viewConfiguration = LocalViewConfiguration.current
        val exactTouchTarget = remember(viewConfiguration) { ExactTouchTargetViewConfiguration(viewConfiguration) }
        CompositionLocalProvider(LocalViewConfiguration provides exactTouchTarget) {
            ChatRowLabelContent(symbol, text, contentDescription, Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onClick))
        }
    }
}

@Composable
private fun ChatRowLabelContent(
    symbol: MaterialSymbol,
    text: String,
    contentDescription: String?,
    clickModifier: Modifier,
) {
    val color = MaterialTheme.colorScheme.placeholderText
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.widthIn(max = ChatLabelMaxWidth).then(clickModifier),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = contentDescription,
            tint = color,
            modifier = ChatRowLabelIconModifier,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Normal,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

/**
 * The platform's [ViewConfiguration] minus the minimum-touch-target expansion, so a pointer target
 * small enough to sit inside a larger clickable only accepts taps within its own bounds.
 */
private class ExactTouchTargetViewConfiguration(
    base: ViewConfiguration,
) : ViewConfiguration by base {
    override val minimumTouchTargetSize: DpSize get() = DpSize.Zero
}

/**
 * The picture of a room that has none: a quiet tonal disc with the room-kind glyph. Replaces the
 * robohash for rooms (not people) — a robot face says nothing about a geohash cell or a relay group,
 * and its saturated colors made picture-less rooms the loudest rows in the list.
 */
@Composable
fun ChatRowTonalAvatar(symbol: MaterialSymbol) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = ChatRowPictureModifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.size(22.dp),
        )
    }
}
