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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.bird_detection_title
import com.vitorpamplona.amethyst.commons.resources.birdex_species_count
import com.vitorpamplona.amethyst.commons.resources.birdex_species_more
import com.vitorpamplona.amethyst.commons.resources.show_less
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.birdstar.BirdDetectionEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexEvent
import com.vitorpamplona.quartz.experimental.birdstar.BirdexSpecies
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How many species names to list before the rest go behind the "+N more" toggle. */
private const val SPECIES_PREVIEW_LIMIT = 6

/**
 * How many more names one tap on "+N more" reveals. Every rendered link is a
 * composable of its own (Compose lays out an interaction region per link), so a
 * life list of a thousand species is revealed a page at a time rather than
 * dropping a thousand of them into a single feed row at once.
 */
private const val SPECIES_PAGE_SIZE = 30

/** Bird emoji prefix shared by both Birdstar card titles. */
private const val BIRD_PREFIX = "🐦 "

/**
 * Summary card for a Birdstar "Birdex" (kind 12473).
 *
 * The event has no body and no images, only a species list, so the card is
 * built from it: the count, then the scientific names, each one a link to the
 * Wikidata entity the publisher paired it with. A Birdex grows without bound,
 * so only [SPECIES_PREVIEW_LIMIT] names show at first and "+N more" reveals
 * another [SPECIES_PAGE_SIZE] at a time in place — no images and no network
 * calls either way. The card is identical in the feed and the opened view, so
 * it takes no makeItShort flag.
 */
@Composable
fun BirdexCard(noteEvent: BirdexEvent) {
    val species = remember(noteEvent) { noteEvent.species() }
    var revealed by rememberSaveable(noteEvent) { mutableIntStateOf(SPECIES_PREVIEW_LIMIT) }
    val shown = minOf(revealed, species.size)
    val hidden = species.size - shown

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Text(
            text = BIRD_PREFIX + pluralStringResource(Res.plurals.birdex_species_count, species.size, species.size),
            style = MaterialTheme.typography.titleMedium,
        )

        if (species.isNotEmpty()) {
            val uriHandler = LocalUriHandler.current
            val linkColor = MaterialTheme.colorScheme.primary
            val plainColor = MaterialTheme.colorScheme.placeholderText

            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    remember(species, shown, linkColor, plainColor, uriHandler) {
                        speciesList(species, shown, linkColor, plainColor, uriHandler)
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (hidden > 0) {
            Spacer(Modifier.height(4.dp))
            ClickableTextPrimary(
                text = pluralStringResource(Res.plurals.birdex_species_more, hidden, hidden),
                style = MaterialTheme.typography.bodyMedium,
            ) { revealed = shown + SPECIES_PAGE_SIZE }
        } else if (species.size > SPECIES_PREVIEW_LIMIT) {
            Spacer(Modifier.height(4.dp))
            ClickableTextPrimary(
                text = stringResource(Res.string.show_less),
                style = MaterialTheme.typography.bodyMedium,
            ) { revealed = SPECIES_PREVIEW_LIMIT }
        }
    }
}

/**
 * The first [shown] species names as one comma-separated run of italics — the
 * convention for scientific names — where each name that carries a Wikidata
 * reference is a link to it. Names without one stay plain: nothing to open.
 *
 * The click goes through [uriHandler] inside a `runCatching`, as
 * [com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl] does: these
 * URLs come from a stranger's event, and an unopenable one throws from the
 * platform handler (`URISyntaxException` on Desktop, no browser installed on
 * Android). Compose's own link handling only swallows `IllegalArgumentException`,
 * so a bad `i` tag would otherwise take the app down on tap.
 */
private fun speciesList(
    species: List<BirdexSpecies>,
    shown: Int,
    linkColor: Color,
    plainColor: Color,
    uriHandler: UriHandler,
): AnnotatedString =
    buildAnnotatedString {
        val separator = SpanStyle(color = plainColor)
        val plainName = SpanStyle(color = plainColor, fontStyle = FontStyle.Italic)
        val linkedName = SpanStyle(color = linkColor, fontStyle = FontStyle.Italic)

        for (index in 0 until shown) {
            val entry = species[index]
            if (index > 0) withStyle(separator) { append(", ") }

            val reference = entry.reference
            if (reference != null) {
                val link =
                    LinkAnnotation.Url(reference, TextLinkStyles(linkedName)) {
                        runCatching { uriHandler.openUri(reference) }
                    }
                withLink(link) { append(entry.name) }
            } else {
                withStyle(plainName) { append(entry.name) }
            }
        }
    }

/**
 * Minimal card for a single Birdstar bird detection (kind 2473).
 *
 * The event has no body and no images. The title is the common name parsed from
 * the publisher's `alt` tag (a generic label when absent), and the scientific
 * name renders as an italic link to the Wikidata species entry from the `i` tag
 * when one is present. The `g` geohash is surfaced by the generic note-location
 * UI, not here.
 */
@Composable
fun BirdDetectionCard(noteEvent: BirdDetectionEvent) {
    val detection =
        remember(noteEvent) {
            BirdDetectionInfo(
                commonName = noteEvent.commonName(),
                species = noteEvent.speciesName(),
                reference = noteEvent.speciesReference(),
            )
        }

    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Text(
            text = BIRD_PREFIX + (detection.commonName ?: stringResource(Res.string.bird_detection_title)),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (detection.species != null) {
            Spacer(Modifier.height(6.dp))
            if (detection.reference != null) {
                val typography = MaterialTheme.typography
                val speciesStyle = remember(typography) { typography.bodyMedium.copy(fontStyle = FontStyle.Italic) }
                ClickableUrl(
                    url = detection.reference,
                    displayText = detection.species,
                    style = speciesStyle,
                )
            } else {
                Text(
                    text = detection.species,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Tag values parsed once per event for the detection card. */
private class BirdDetectionInfo(
    val commonName: String?,
    val species: String?,
    val reference: String?,
)
