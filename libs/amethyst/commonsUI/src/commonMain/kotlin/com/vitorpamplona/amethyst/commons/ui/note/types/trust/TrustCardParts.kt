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
package com.vitorpamplona.amethyst.commons.ui.note.types.trust

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Hex

private val CardShape = RoundedCornerShape(12.dp)
private val TileShape = RoundedCornerShape(10.dp)
private val ChipShape = RoundedCornerShape(50)

/**
 * The frame the trust cards (NIP-85 assertions, the 10040 designation, Trusted Lists) and the
 * D-list cards share: a softly tinted, rounded panel, the same shape the attestation card uses.
 */
@Composable
fun TrustCardFrame(content: @Composable ColumnScope.() -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(CardShape)
                .border(1.dp, tint.copy(alpha = 0.25f), CardShape)
                .background(tint.copy(alpha = 0.05f))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Icon, title and an optional one-line explanation under it. */
@Composable
fun TrustCardHeader(
    symbol: MaterialSymbol,
    title: String,
    subtitle: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A small label over a section of a card ("About", "Scored", ...). */
@Composable
fun TrustSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A number with its name under it. [emphasized] is the card's headline figure (a rank);
 * [warning] tints a count that is a negative signal (reporters, muters) when it is not zero.
 */
@Composable
fun StatTile(
    value: String,
    label: String,
    emphasized: Boolean = false,
    warning: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val background =
        when {
            emphasized -> scheme.primary.copy(alpha = 0.14f)
            warning -> scheme.error.copy(alpha = 0.12f)
            else -> scheme.surfaceContainerHigh
        }
    val valueColor =
        when {
            emphasized -> scheme.primary
            warning -> scheme.error
            else -> scheme.onSurface
        }

    Column(
        modifier =
            Modifier
                .clip(TileShape)
                .background(background)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A tonal pill for a short fact: a parameter, a service, a field name. */
@Composable
fun TonalChip(
    text: String,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (emphasized) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .clip(ChipShape)
                .background(if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * The first [maxShown] of [items], then a "+N more" chip that shows the rest. Emits its children
 * into the caller's `FlowRow`, so a card can put fixed tiles or chips before them. Event authors
 * decide how many tags a card gets, and a feed row should not draw a thousand chips unasked.
 */
@Composable
fun <T> CappedChips(
    items: List<T>,
    maxShown: Int,
    item: @Composable (T) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded || items.size <= maxShown) items else items.subList(0, maxShown)
    shown.forEach { item(it) }

    val hidden = items.size - shown.size
    if (hidden > 0) {
        TonalChip(
            text = pluralStringRes(Res.plurals.chips_n_more, hidden, hidden),
            emphasized = true,
            modifier = Modifier.clickable { expanded = true },
        )
    }
}

/** A label followed by a person, small avatar and name, which opens their profile. */
@Composable
fun LabeledInlineUser(
    label: String,
    pubKey: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (!Hex.isHex64(pubKey)) return
    LoadUser(pubKey) { user ->
        if (user == null) return@LoadUser
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable { nav.nav(routeFor(user)) },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            UserPicture(user, Size20dp, accountViewModel = accountViewModel, nav = nav)
            UsernameDisplay(user, fontWeight = FontWeight.SemiBold, accountViewModel = accountViewModel)
        }
    }
}
