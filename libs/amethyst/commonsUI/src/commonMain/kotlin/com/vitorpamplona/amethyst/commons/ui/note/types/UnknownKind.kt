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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipLinks
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.rememberNipsDefining
import com.vitorpamplona.amethyst.commons.rendering.unknown.BlobEncoding
import com.vitorpamplona.amethyst.commons.rendering.unknown.ContentShape
import com.vitorpamplona.amethyst.commons.rendering.unknown.DigestMedia
import com.vitorpamplona.amethyst.commons.rendering.unknown.RawTag
import com.vitorpamplona.amethyst.commons.rendering.unknown.UnknownEventDigest
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip_number
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_about
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_client
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_defined_by
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encoded_base64
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encoded_hex
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encoded_json
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encoded_opaque
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_encrypted
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_ends
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_hide_tags
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_identifier
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_json_items
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_kinds
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_links
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_location
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_published
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_relays
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_show_tags
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_starts
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_title
import com.vitorpamplona.amethyst.commons.resources.unknown_kind_unsupported
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nipsOnNostr.NipTextEvent
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.utils.Hex

/**
 * The body of a note whose kind the app has no renderer for. Before, these fell into the text path,
 * which prints whatever the content holds (JSON, ciphertext, base64) and drops every tag.
 *
 * It is a [KindCard] drawn without its frame, since the note row already frames it: the event's own
 * title (or the kind's name) with an "Unsupported" pill, the content when it is words (the fields of
 * a JSON object, one line saying what it is otherwise), then the facts [UnknownEventDigest] found in
 * the tags, the people, the first referenced event, and the remaining tags folded away.
 *
 * What the note row's first line and footer already show is not repeated: expiration, the `g`
 * location, PoW, OTS and zap splits. The whole body sits behind the row's content-warning gate.
 */
@Composable
fun RenderUnknownKind(
    note: Note,
    makeItShort: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event ?: return
    val digest = remember(event) { UnknownEventDigest.of(event) }
    SensitivityWarning(note = note, accountViewModel = accountViewModel) {
        UnknownKindCard(note, digest, makeItShort, quotesLeft, backgroundColor, accountViewModel, nav)
    }
}

@Composable
private fun UnknownKindCard(
    note: Note,
    digest: UnknownEventDigest,
    makeItShort: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val kindLabel = unknownKindLabel(digest)
    KindCard(
        icon = MaterialSymbols.Extension,
        title = digest.title?.clip() ?: kindLabel,
        subtitle = if (digest.title != null) kindLabel else digest.alt?.takeIf { digest.content !is ContentShape.Prose }?.clip(),
        tone = KindTone.Neutral,
        status = stringRes(Res.string.unknown_kind_unsupported),
        framed = false,
    ) {
        // Only where the row may load media: not in quotes, short rows or hidden notes.
        if (kindCardCanPreview()) digest.media.firstOrNull()?.let { UnknownKindImage(it, accountViewModel) }
        digest.summary?.let {
            Text(it.clip(), style = MaterialTheme.typography.bodyMedium, maxLines = if (makeItShort) 2 else 5, overflow = TextOverflow.Ellipsis)
        }

        when (val content = digest.content) {
            is ContentShape.Prose -> {
                if (makeItShort) {
                    Text(content.text.clip(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    // Gated once, around the whole body, by RenderUnknownKind.
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        KindCardText(note, content.text, backgroundColor, accountViewModel, nav, quotesLeft, gated = false, screened = true)
                    }
                }
            }

            is ContentShape.Json -> {
                if (!makeItShort) {
                    if (content.isArray || content.fields.isEmpty()) {
                        UnknownContentLine(content)
                        KindCardCode(content.pretty, maxLines = 6)
                    } else {
                        content.fields.take(MAX_JSON_FIELDS).forEach { KindCardField(readableToken(it.key), it.value, monospace = it.nested) }
                        KindCardMore(content.fields.size - MAX_JSON_FIELDS)
                    }
                }
            }

            else -> {
                // With no title, the alt is already the subtitle.
                if (digest.title != null) digest.alt?.let { Text(it.clip(), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                UnknownContentLine(content)
            }
        }

        if (makeItShort) return@KindCard

        UnknownKindFacts(digest)
        // Remembered: a new list each recomposition would keep the chip rows from skipping.
        val hashtagChips = remember(digest) { digest.uncitedHashtags.map { "#$it" } }
        val people = remember(digest) { digest.people.map { it.pubKey } }
        KindCardChips(hashtagChips)
        KindCardUsers(people, accountViewModel, nav)
        UnknownKindFirstReference(digest, quotesLeft, backgroundColor, accountViewModel, nav)
        UnknownKindOtherTags(digest.otherTags, accountViewModel, nav)
        KindDefinedBy(digest.kind, accountViewModel, nav)
    }
}

/**
 * Where to learn what this kind is: its official NIP when the kind registry knows one, else a NIP
 * published on Nostr whose `k` tags define it (watched on the relays while the card is on screen,
 * one subscription per kind however many cards show it), else the kind's own page.
 */
@Composable
private fun KindDefinedBy(
    kind: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val official = remember(kind) { KindNames.nipFor(kind)?.let { NipLinks.normalize(it) } }
    val onNostr = if (official == null) rememberNipsDefining(kind, accountViewModel).firstOrNull() else null

    val nipTitle = (onNostr?.event as? NipTextEvent)?.let { it.title() ?: it.dTag() }
    val label =
        when {
            official != null -> stringRes(Res.string.unknown_kind_defined_by, stringRes(Res.string.nip_number, official))
            nipTitle != null -> stringRes(Res.string.unknown_kind_defined_by, nipTitle)
            else -> stringRes(Res.string.unknown_kind_about, kind)
        }

    Row(
        modifier =
            Modifier.clickable {
                val route =
                    when {
                        official != null -> Route.Nip(official)
                        onNostr != null -> routeFor(onNostr, accountViewModel.account)
                        else -> null
                    }
                nav.nav(route ?: Route.EventKind(kind))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Recipe" when quartz knows the kind's name, "Kind 31337" when it doesn't. */
@Composable
private fun unknownKindLabel(digest: UnknownEventDigest): String = if (digest.kindName != null) kindName(digest.kind) else stringRes(Res.string.unknown_kind_title, digest.kind)

/** One quiet line saying what the content is when it isn't words: ciphertext, a blob, a JSON list. */
@Composable
private fun UnknownContentLine(content: ContentShape) {
    val text =
        when (content) {
            is ContentShape.Encrypted -> stringRes(Res.string.unknown_kind_encrypted, content.scheme.name.let { it.substring(0, 3) + "-" + it.substring(3) })
            is ContentShape.Encoded ->
                when (content.encoding) {
                    BlobEncoding.HEX -> pluralStringRes(Res.plurals.unknown_kind_encoded_hex, content.length, content.length)
                    BlobEncoding.BASE64 -> pluralStringRes(Res.plurals.unknown_kind_encoded_base64, content.length, content.length)
                    BlobEncoding.JSON -> pluralStringRes(Res.plurals.unknown_kind_encoded_json, content.length, content.length)
                    BlobEncoding.OPAQUE -> pluralStringRes(Res.plurals.unknown_kind_encoded_opaque, content.length, content.length)
                }
            is ContentShape.Json -> if (content.isArray) pluralStringRes(Res.plurals.unknown_kind_json_items, content.size, content.size) else null
            else -> null
        } ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            symbol = if (content is ContentShape.Encrypted) MaterialSymbols.Lock else MaterialSymbols.Code,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The facts the digest found, as a table: only the rows that have a value draw. Expiration and the
 * `g` geohash are left to the note row's first line, which already shows them.
 */
@Composable
private fun UnknownKindFacts(digest: UnknownEventDigest) {
    KindCardField(stringRes(Res.string.unknown_kind_identifier), digest.identifier, monospace = true, maxLines = 1)
    KindCardField(stringRes(Res.string.unknown_kind_published), digest.publishedAt?.let { formatSeconds(it) })
    KindCardField(stringRes(Res.string.unknown_kind_starts), digest.startsAt?.let { formatSeconds(it) })
    KindCardField(stringRes(Res.string.unknown_kind_ends), digest.endsAt?.let { formatSeconds(it) })
    KindCardField(stringRes(Res.string.unknown_kind_location), digest.location)
    if (digest.kinds.isNotEmpty()) KindCardField(stringRes(Res.string.unknown_kind_kinds), digest.kinds.map { kindName(it) }.joinToString(", "))
    KindCardField(stringRes(Res.string.unknown_kind_client), digest.client)
    if (digest.links.isNotEmpty()) {
        KindCardSection(stringRes(Res.string.unknown_kind_links))
        digest.links.take(MAX_LINKS).forEach { ClickableUrl(url = it, style = MaterialTheme.typography.bodySmall) }
        KindCardMore(digest.links.size - MAX_LINKS)
    }
    if (digest.relays.isNotEmpty()) {
        val relays = remember(digest) { digest.relays.map { it.removePrefix("wss://").removeSuffix("/") } }
        KindCardSectionChips(stringRes(Res.string.unknown_kind_relays), relays, max = 4)
    }
}

/** The first event the record points at, quoted; the record is usually about it. */
@Composable
private fun UnknownKindFirstReference(
    digest: UnknownEventDigest,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = digest.events.firstOrNull()
    if (event != null) {
        KindCardQuoteEvent(event.id, quotesLeft, backgroundColor, accountViewModel, nav)
        return
    }
    digest.addresses.firstOrNull()?.let { KindCardQuoteAddress(it.address, quotesLeft, backgroundColor, accountViewModel, nav) }
}

@Composable
private fun UnknownKindImage(
    media: DigestMedia,
    accountViewModel: AccountViewModel,
) {
    MyAsyncImage(
        imageUrl = media.url,
        contentDescription = media.alt,
        contentScale = ContentScale.Crop,
        mainImageModifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)),
        loadedImageModifier = Modifier,
        accountViewModel = accountViewModel,
        onLoadingBackground = null,
        onError = null,
    )
}

/** The tags the card has no row for, folded under "Show N tags", each by its own name. */
@Composable
private fun UnknownKindOtherTags(
    tags: List<RawTag>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (tags.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = !open }.padding(2.dp),
    ) {
        Icon(
            symbol = if (open) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (open) stringRes(Res.string.unknown_kind_hide_tags) else pluralStringRes(Res.plurals.unknown_kind_show_tags, tags.size, tags.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) {
        // A list kind can carry thousands of tags, and this column is not lazy.
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            tags.take(MAX_RAW_TAGS).forEach { RawTagRow(it, accountViewModel, nav) }
        }
        KindCardMore(tags.size - MAX_RAW_TAGS)
    }
}

/** One tag: its name in a code chip, then its values drawn by what they are. */
@Composable
private fun RawTagRow(
    tag: RawTag,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.Top) {
        Row(Modifier.width(104.dp).padding(end = 8.dp)) { TagNameChip(tag.name) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val value = tag.values.firstOrNull() ?: ""
            // Address.parse logs every value it rejects: only try the ones shaped like "kind:pubkey:d".
            val address = remember(value) { if (value.length >= 66 && value.indexOf(':') in 1..5) Address.parse(value) else null }
            when {
                // NIP-22's upper-case root tags carry the same keys and ids as `p` and `e`.
                tag.name == "P" && Hex.isHex64(value) -> {
                    KindCardPerson(value, accountViewModel, nav)
                    TagExtras(tag.values.drop(1))
                }

                tag.name == "E" && Hex.isHex64(value) -> {
                    TagLink(value.take(8) + "…" + value.takeLast(4)) { nav.nav(Route.EventRedirect(value)) }
                    TagExtras(tag.values.drop(1))
                }

                address != null -> {
                    TagLink("${kindName(address.kind)} · ${address.dTag.ifEmpty { address.pubKeyHex.take(8) }}") {
                        routeFor(LocalCache.getOrCreateAddressableNote(address), accountViewModel.account)?.let { nav.nav(it) }
                    }
                    TagExtras(tag.values.drop(1))
                }

                else -> tag.values.filter { it.isNotEmpty() }.forEach { TagValue(it) }
            }
        }
    }
}

@Composable
private fun TagExtras(values: List<String>) {
    val rest = values.filter { it.isNotBlank() }
    if (rest.isEmpty()) return
    Text(
        text = rest.joinToString("  ·  ").clip(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TagValue(value: String) {
    if (value.startsWith("https://") || value.startsWith("http://")) {
        ClickableUrl(url = value, style = MaterialTheme.typography.bodySmall)
    } else {
        Text(value.clip(), style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TagLink(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TagNameChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private fun formatSeconds(epochSeconds: Long): String = formatDateTime(epochSeconds * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)

/**
 * At most [max] characters of [this], for a Text that shows a few lines of it: Text lays out the
 * whole string before cutting it to maxLines, and a tag value or summary from a relay can be huge.
 */
private fun String.clip(max: Int = MAX_TEXT_CHARS): String = if (length > max) take(max) + "…" else this

private const val MAX_TEXT_CHARS = 600
private const val MAX_RAW_TAGS = 50
private const val MAX_JSON_FIELDS = 8
private const val MAX_LINKS = 3
