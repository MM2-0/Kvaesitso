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
package com.vitorpamplona.amethyst.commons.cordn.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_coordinator
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_details
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_differs
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_dimension_content
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_dimension_membership
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_dimension_messaging
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_level_identified
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_level_none
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_level_pseudonymous
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_subtitle
import com.vitorpamplona.amethyst.commons.resources.cordn_exposure_title
import com.vitorpamplona.amethyst.commons.resources.cordn_note_encryption_bug
import com.vitorpamplona.amethyst.commons.resources.cordn_note_history
import com.vitorpamplona.amethyst.commons.resources.cordn_note_linked
import com.vitorpamplona.amethyst.commons.resources.cordn_note_membership
import com.vitorpamplona.amethyst.commons.resources.cordn_note_publication
import com.vitorpamplona.amethyst.commons.resources.cordn_note_sizes
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.cordn.client.ExposureLevel
import com.vitorpamplona.cordn.client.ExposureNote
import com.vitorpamplona.cordn.client.GroupExposure
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import org.jetbrains.compose.resources.stringResource

/**
 * What the coordinator behind a cordn group learns, as a panel.
 *
 * §8 of cordn-kmp's `cordn/plans/2026-09-17-cordn-interop.md` ends with a requirement:
 * the exposure analysis "should be surfaced in the UI if we ship this, not
 * buried. A Marmot group and a cordn group have materially different metadata
 * exposure and users cannot infer that from either one looking like a group
 * chat." This is that surface.
 *
 * Two deliberate choices about how it reads:
 *
 * - **It does not rank the two.** cordn is weaker against the operator and
 *   stronger against the network — the coordinator never sees an IP (§8.5).
 *   Which trade is right depends on who runs the coordinator, which is the
 *   user's call. So the card states facts and stops.
 * - **The notes come from [GroupExposure.notes], not from this file.** They are
 *   computed from the group's real state, so a group linked to five others says
 *   so and a lone group does not. A hand-written paragraph would drift from the
 *   truth the moment either changed.
 */
@Composable
fun CordnExposureCard(
    exposure: GroupExposure,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.PrivacyTip,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.cordn_exposure_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = stringResource(Res.string.cordn_exposure_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExposureRow(stringResource(Res.string.cordn_exposure_dimension_content), exposure.content)
            ExposureRow(stringResource(Res.string.cordn_exposure_dimension_membership), exposure.membership)
            ExposureRow(stringResource(Res.string.cordn_exposure_dimension_messaging), exposure.messaging)

            HorizontalDivider()

            Text(
                text = stringResource(Res.string.cordn_exposure_details),
                style = MaterialTheme.typography.labelLarge,
            )
            exposure.notes().forEach { NoteRow(it) }

            // Every cordn group differs from a Marmot one: a coordinator holds what relays
            // would spread out. Said unconditionally until a self-hosted coordinator changes
            // that, which is this app's comparison to make, not cordn's.
            Text(
                text = stringResource(Res.string.cordn_exposure_differs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                // An npub, not a hex head. What somebody checks this against is
                // the npub they were sent, and a hex prefix cannot be compared
                // with one at all -- different encoding, different alphabet.
                //
                // Name and face are deliberately absent: this card is shared
                // code with no AccountViewModel to read a profile through, and
                // it is a footnote under a disclosure rather than an identity
                // surface. Every screen that embeds it names the coordinator
                // properly nearby.
                text = stringResource(Res.string.cordn_exposure_coordinator, remember(exposure.coordinator) { shortNpub(exposure.coordinator) }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The coordinator key as a short npub.
 *
 * Shortened with the same prefix `User.pubkeyDisplayHex` uses, so a coordinator
 * key reads identically here and on every other screen that prints one.
 *
 * Falls back to the hex if the key is not decodable, which no stored exposure
 * should carry -- a label that is wrong is still better than a card that
 * crashes on one malformed group. Decoded with the total `decode64OrNull`
 * rather than `Hex.decode`, which validates no characters at all: it indexes a
 * 256-entry table by char code, so anything above U+00FF throws an
 * ArrayIndexOutOfBounds that no IllegalArgumentException guard would catch.
 */
private const val NPUB_PREFIX = 5

private fun shortNpub(pubKeyHex: String): String = Hex.decode64OrNull(pubKeyHex)?.toNpub()?.toShortDisplay(NPUB_PREFIX) ?: pubKeyHex.take(16)

@Composable
private fun ExposureRow(
    dimension: String,
    level: ExposureLevel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = level.symbol(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = level.tint(),
        )
        Text(
            text = dimension,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = level.label(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = level.weight(),
            color = level.tint(),
        )
    }
}

@Composable
private fun NoteRow(note: ExposureNote) {
    val isBug = note == ExposureNote.ENCRYPTION_NOT_PINNED
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = if (isBug) MaterialSymbols.Warning else MaterialSymbols.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isBug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = note.label(),
            style = MaterialTheme.typography.bodySmall,
            color = if (isBug) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The colour of a level, chosen so the scale reads without the words.
 *
 * It has to be **monotonic**, and getting that wrong is easy: an earlier
 * version used `tertiary` for the middle level, which rendered pink against a
 * dark `onSurface` for the worst one — so the row a user should worry about
 * least of the two looked like the alarming one. Severity now runs
 * affirmative → muted → emphatic, and emphasis for the worst case comes from
 * [weight] rather than another hue.
 *
 * Nothing uses `error`. Everything on this card is how cordn works, not a
 * fault, and painting normal operation red teaches people to ignore red.
 */
@Composable
private fun ExposureLevel.tint(): Color =
    when (this) {
        ExposureLevel.NONE -> MaterialTheme.colorScheme.primary
        ExposureLevel.PSEUDONYMOUS -> MaterialTheme.colorScheme.onSurfaceVariant
        ExposureLevel.IDENTIFIED -> MaterialTheme.colorScheme.onSurface
    }

/** The other half of the scale: only the worst level is emphasised. */
private fun ExposureLevel.weight(): FontWeight =
    when (this) {
        ExposureLevel.IDENTIFIED -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }

private fun ExposureLevel.symbol(): MaterialSymbol =
    when (this) {
        ExposureLevel.NONE -> MaterialSymbols.Lock
        ExposureLevel.PSEUDONYMOUS -> MaterialSymbols.NoAccounts
        ExposureLevel.IDENTIFIED -> MaterialSymbols.Person
    }

@Composable
private fun ExposureLevel.label(): String =
    stringResource(
        when (this) {
            ExposureLevel.NONE -> Res.string.cordn_exposure_level_none
            ExposureLevel.PSEUDONYMOUS -> Res.string.cordn_exposure_level_pseudonymous
            ExposureLevel.IDENTIFIED -> Res.string.cordn_exposure_level_identified
        },
    )

@Composable
private fun ExposureNote.label(): String =
    stringResource(
        when (this) {
            ExposureNote.MEMBERSHIP_IS_IDENTIFIED -> Res.string.cordn_note_membership
            ExposureNote.GROUPS_LINKED_BY_SESSION -> Res.string.cordn_note_linked
            ExposureNote.SINGLE_OPERATOR_HOLDS_HISTORY -> Res.string.cordn_note_history
            ExposureNote.PUBLICATION_IS_A_SIGNED_RECORD -> Res.string.cordn_note_publication
            ExposureNote.MESSAGE_SIZES_UNPADDED -> Res.string.cordn_note_sizes
            ExposureNote.ENCRYPTION_NOT_PINNED -> Res.string.cordn_note_encryption_bug
        },
    )
