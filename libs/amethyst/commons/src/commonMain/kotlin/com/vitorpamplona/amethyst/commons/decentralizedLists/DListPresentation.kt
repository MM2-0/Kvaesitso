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
package com.vitorpamplona.amethyst.commons.decentralizedLists

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.decentralizedLists.description
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.FieldDeclaration
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.FieldRequirement
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.displayHints
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.fieldDeclarations
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.listImage
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.DisplayHint
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.DisplayHintTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.header.tags.FieldTypeTag
import com.vitorpamplona.quartz.experimental.decentralizedLists.inheritFrom
import com.vitorpamplona.quartz.experimental.decentralizedLists.isDeliberatelyUnaffiliated
import com.vitorpamplona.quartz.experimental.decentralizedLists.taggings.TagElementContent
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * The three tiers an item is drawn in, after Brainstorm's ADR 0007:
 *
 * 1. Raw: the item's tags as they are, when no definition can be read (or it declares nothing).
 * 2. Dynamic: what every concept gets from its governing definition: field declarations, display
 *    hints and URL templates ([dListItemView]).
 * 3. Custom: a view made by hand for one community concept (`tiers/`), still reading the item's
 *    values through the governing definition's fields.
 *
 * Nothing in tier two names a concept; tier three plugs in from above.
 */

/**
 * How a D-list header reads: its names, what it says it is, its picture, the fields its items
 * carry, its display hints and link templates. A header with no `names` falls back to `name`,
 * then to its `d`, as Brainstorm's `headerNames` does; a lone singular stands in for the plural.
 */
@Immutable
data class DListHeaderView(
    val singular: String,
    val plural: String,
    val description: String?,
    val image: String?,
    val fields: List<FieldDeclaration>,
    val hints: List<DisplayHint>,
    /** URL templates the header pins (provisional, [LinkRef]). */
    val links: List<LinkRef> = emptyList(),
    /** Every coordinate a `b` tag points at: a copy names its community concept here. */
    val pointers: List<String> = emptyList(),
    /** A copy withdrawn on purpose (`["b", "b-tag-deferred"]`): affiliated with no list. */
    val unaffiliated: Boolean,
) {
    fun hint(role: String) = hints.firstOrNull { it.role == role }?.field

    fun field(name: String?) = name?.let { n -> fields.firstOrNull { it.name == n } }

    fun pointsAt(coordinate: String) = coordinate in pointers
}

/** One "label: value" line of an item. [label] null reads the field's name as words. */
@Immutable
data class DListFact(
    val field: String,
    val label: String?,
    val value: String,
    /** How many more values the item gives the same field. */
    val extra: Int,
    /**
     * What the value opens, when its field's declared type says and the value is one ([fieldHref]):
     * a `url` field's http(s) URL, a `tel` field's `tel:`, an `email` field's `mailto:`. Never
     * guessed from the value: an untyped field or an undeclared tag is text, whatever it looks like.
     */
    val href: String?,
)

enum class DListMediaKind { AUDIO, VIDEO }

@Immutable
data class DListMedia(
    val url: String,
    val kind: DListMediaKind,
)

/** Where an item is: the most precise geohash its `location` field gives, decoded. */
@Immutable
data class DListLocation(
    val geohash: String,
    val lat: Double,
    val lon: Double,
) {
    /** Six characters and up (about a kilometre and under) is a spot worth a pin; less is an area. */
    val isSpot get() = geohash.length >= 6

    /** OpenStreetMap at a zoom that frames the cell. */
    fun osmUrl(): String {
        val zoom = (geohash.length * 2 + 3).coerceIn(3, 18)
        val la = degreesText(lat)
        val lo = degreesText(lon)
        val pin = if (isSpot) "?mlat=$la&mlon=$lo" else ""
        return "https://www.openstreetmap.org/$pin#map=$zoom/$la/$lo"
    }
}

/**
 * A coordinate as a map URL wants it: six decimals (about 10 cm), never the scientific notation
 * Kotlin writes for small values (`-5.0E-4` near the equator or the prime meridian).
 */
fun degreesText(value: Double): String {
    val micro = (value * 1_000_000).roundToLong()
    val sign = if (micro < 0) "-" else ""
    val whole = abs(micro)
    return "$sign${whole / 1_000_000}.${(whole % 1_000_000).toString().padStart(6, '0')}"
}

/** How a picture is cut where the item is one among many: round, as a face or a logo, or a portrait, as a cover. */
enum class DListImageShape { ROUND, PORTRAIT }

/**
 * How a D-list item reads under its governing definition (Brainstorm `lib/itemPresentation.ts`).
 * The header's display hints decide the title, summary, picture, link, media and location; without
 * them the first required field is the title and a declared `summary`/`description` is the summary.
 * The choice is per definition, never per item: an item missing its title field is "Untitled".
 *
 * [facts] are only the fields the header names as a `fact`; the declared fields nothing shows
 * wait in [moreFields], and tags no field declares in [undeclared], both behind a fold.
 */
@Immutable
data class DListItemView(
    /** False for tier one: no definition, or one that declares no fields; the tags speak for themselves. */
    val defined: Boolean,
    val title: String?,
    val summary: String?,
    val image: String?,
    val imageShape: DListImageShape = DListImageShape.ROUND,
    /** The item's own `link` field, labelled by the field's description, else its host. */
    val link: DListLink?,
    val media: DListMedia? = null,
    val location: DListLocation? = null,
    val facts: List<DListFact>,
    val moreFields: List<DListFact> = emptyList(),
    val undeclared: List<DListFact> = emptyList(),
    /** Required fields the item leaves empty. */
    val missing: List<String>,
    /** The fields a role, a fact or a link template already shows: a custom view lists only the rest. */
    val usedFields: Set<String> = emptySet(),
)

/**
 * The trimmed value when it is an http(s) URL whose host reads as what it is: no spaces, control
 * characters or backslashes (browsers read `\` as `/`, so `https://evil.com\@github.com` goes to
 * evil.com) and no `user@` before the host, so the host shown beside a link is the one it opens.
 */
fun httpUrl(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    val lower = trimmed.lowercase()
    val start =
        when {
            lower.startsWith("https://") -> 8
            lower.startsWith("http://") -> 7
            else -> return null
        }
    if (trimmed.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
    val authority = authorityOf(trimmed, start)
    return if (isPlainHost(authority)) trimmed else null
}

private fun authorityOf(
    url: String,
    start: Int,
): String {
    var end = start
    while (end < url.length && url[end] != '/' && url[end] != '?' && url[end] != '#') end++
    return url.substring(start, end)
}

/** `host` or `host:port`: letters, digits, dots and dashes; nothing that could hide another host. */
internal fun isPlainHost(authority: String): Boolean {
    val host = authority.substringBefore(':')
    val port = authority.substringAfter(':', "")
    if (host.isEmpty() || host.startsWith('.') || host.startsWith('-')) return false
    if (host.any { !it.isLetterOrDigit() && it != '.' && it != '-' }) return false
    if (':' in authority && (port.isEmpty() || port.length > 5 || port.any { it !in '0'..'9' })) return false
    return true
}

/** The host of an [httpUrl], without `www.`. */
fun httpHost(url: String): String =
    authorityOf(url, url.indexOf("://") + 3)
        .substringBefore(':')
        .lowercase()
        .removePrefix("www.")

/**
 * A `tel` field's value as a call (provisional): its digits and a leading `+`, once the separators
 * people write (spaces, dots, dashes, brackets) are gone; null when anything else is left. The
 * header said it is a phone number; this only spells it the way a `tel:` link wants. Brainstorm's
 * `telHref` (`lib/dlistFields.ts`).
 */
fun telHref(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    val cleaned = StringBuilder(trimmed.length)
    for (c in trimmed) {
        if (c.isWhitespace() || c == '(' || c == ')' || c == '.' || c == '-') continue
        cleaned.append(c)
    }
    if (cleaned.isEmpty()) return null
    val digitsFrom = if (cleaned[0] == '+') 1 else 0
    if (digitsFrom == cleaned.length) return null
    for (i in digitsFrom until cleaned.length) {
        if (cleaned[i] !in '0'..'9') return null
    }
    return "tel:$cleaned"
}

private val SINGLE_EMAIL = Regex("[^\\s@?#:,;]+@[^\\s@?#:,;]+\\.[^\\s@?#:,;]+")

/**
 * An `email` field's value as a mail (provisional): one address, `name@host.tld`, nothing around it
 * (no second address, no `?` headers, no `mailto:` of its own); null otherwise. Brainstorm's `emailHref`.
 */
fun emailHref(value: String?): String? {
    val trimmed = value?.trim() ?: return null
    return if (SINGLE_EMAIL.matches(trimmed)) "mailto:$trimmed" else null
}

/**
 * What a typed field's value opens: one obvious rendering per type, none for any other. A `url`
 * field links an http(s) value, a `tel` field calls, an `email` field mails. `text`, no type, or a
 * type no reader knows is text: a value's look never decides its type.
 */
fun fieldHref(
    type: String?,
    value: String,
): String? =
    when (type) {
        FieldTypeTag.URL -> httpUrl(value)
        FieldTypeTag.TEL -> telHref(value)
        FieldTypeTag.EMAIL -> emailHref(value)
        else -> null
    }

/** A field name as words: `opening-hours` -> "Opening hours". */
fun fieldWords(name: String): String =
    name
        .replace('-', ' ')
        .replace('_', ' ')
        .trim()
        .replaceFirstChar { it.uppercase() }

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "wav", "flac", "aac", "opus")
private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v", "m3u8")

/** What a URL plays as, by its file extension: audio, video, or nothing (Brainstorm `mediaKindOfUrl`). */
fun mediaKindOfUrl(url: String): DListMediaKind? {
    val path =
        url
            .substringBefore('#')
            .substringBefore('?')
    val extension = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()
    return when (extension) {
        in AUDIO_EXTENSIONS -> DListMediaKind.AUDIO
        in VIDEO_EXTENSIONS -> DListMediaKind.VIDEO
        else -> null
    }
}

private fun TagArray.firstValue(name: String): String? = firstOrNull { it.size > 1 && it[0] == name && it[1].isNotBlank() }?.get(1)

/**
 * Every non-blank value of an item's tags, by tag name, read in one pass: a header can declare
 * hundreds of fields and an item carry thousands of tags, and looking each field up with its own
 * scan multiplies the two.
 */
private fun TagArray.valuesByName(): Map<String, List<String>> {
    val out = HashMap<String, MutableList<String>>()
    for (tag in this) {
        if (tag.size > 1 && tag[1].isNotBlank()) out.getOrPut(tag[0]) { ArrayList(1) }.add(tag[1])
    }
    return out
}

fun dListHeaderView(
    tags: TagArray,
    dTag: String?,
): DListHeaderView {
    val names = tags.firstOrNull { it.size > 1 && it[0] == "names" && it[1].isNotBlank() }
    val singular = names?.get(1) ?: tags.firstValue("name") ?: dTag ?: ""
    val plural = names?.getOrNull(2)?.ifBlank { null } ?: singular
    val fields = tags.fieldDeclarations()
    val declared = fields.mapTo(HashSet()) { it.name }
    return DListHeaderView(
        singular = singular,
        plural = plural,
        description = tags.description(),
        image = httpUrl(tags.listImage()),
        fields = fields,
        hints = tags.displayHints(declared),
        links = tags.linkRefs(declared),
        pointers = tags.inheritFrom().map { it.target },
        unaffiliated = tags.isDeliberatelyUnaffiliated(),
    )
}

/** Protocol tags and NIP metadata every event may carry: never fields. */
private val METADATA_TAGS = setOf("alt", "client", "expiration", "json", "polarity")

/** The item-level tags an item reader shows on their own, not as facts. */
private val ITEM_TEXT_TAGS = setOf("name", "title", "description", "comments", "slug")

/**
 * [dListItemView] for a whole event: a Tapestry tag element keeps its name and description in a
 * JSON `content` (`{"tag": {"slug", "name", "description"}}`) rather than in tags, so when the
 * tags give no title the content is read for one.
 */
fun dListItemView(
    item: TagArray,
    content: String,
    header: DListHeaderView?,
): DListItemView {
    val view = dListItemView(item, header)
    if (view.title != null || !content.startsWith("{")) return view
    val tag = TagElementContent.parse(content)?.tag ?: return view
    return view.copy(
        title = tag.name?.ifBlank { null } ?: tag.slug.ifBlank { null },
        summary = view.summary ?: tag.description?.ifBlank { null },
    )
}

/** Of a field's values, the longest that decodes: an event carries `g` once per precision, in any order. */
private fun mostPreciseGeohash(values: List<String>?): DListLocation? {
    var best: DListLocation? = null
    values?.forEach { raw ->
        val hash = raw.trim().lowercase()
        if (hash.length <= (best?.geohash?.length ?: 0)) return@forEach
        val cell = GeoHash.decode(hash) ?: return@forEach
        best = DListLocation(hash, cell.centerLat, cell.centerLon)
    }
    return best
}

fun dListItemView(
    item: TagArray,
    header: DListHeaderView?,
): DListItemView {
    if (header == null || header.fields.isEmpty()) return rawItemView(item)

    val fields = header.fields
    val declared = fields.associateBy { it.name }
    val values = item.valuesByName()

    fun valueOf(field: String?): String? = field?.takeIf { it in declared }?.let { values[it]?.firstOrNull() }

    fun factOf(
        name: String,
        label: String?,
    ): DListFact? {
        val fieldValues = values[name] ?: return null
        val value = fieldValues.firstOrNull() ?: return null
        return DListFact(name, label, value, fieldValues.size - 1, fieldHref(declared[name]?.type, value))
    }

    val titleField = header.hint(DisplayHintTag.TITLE) ?: fields.firstOrNull { it.requirement == FieldRequirement.REQUIRED }?.name ?: fields.first().name
    val summaryField =
        header.hint(DisplayHintTag.SUMMARY)
            ?: listOf("summary", "description").firstOrNull { it in declared && it != titleField }
    val imageField = header.hint(DisplayHintTag.IMAGE)
    val linkField = header.hint(DisplayHintTag.LINK)
    val mediaField = header.hint(DisplayHintTag.MEDIA)
    val locationField = header.hint(DisplayHintTag.LOCATION)

    // Each field shows once: the ones a role already draws are not facts as well.
    val byRole = setOfNotNull(titleField, summaryField, imageField, linkField, mediaField, locationField)
    val factHints = header.hints.filter { it.role == DisplayHintTag.FACT && it.field !in byRole }
    val facts = factHints.mapNotNull { factOf(it.field, it.label) }

    val linkHref = httpUrl(valueOf(linkField))
    val mediaUrl = httpUrl(valueOf(mediaField))
    val mediaKind = mediaUrl?.let { mediaKindOfUrl(it) }

    val used = HashSet<String>()
    used.addAll(byRole)
    factHints.mapTo(used) { it.field }
    header.links.forEach { ref -> ref.bindings.mapTo(used) { it.second } }

    // Declared fields nothing above shows; single-letter ones are protocol tags (`t`, `g`, `p`...)
    // the card draws its own way, so they wait in the fold too rather than leading the page.
    val moreFields = fields.filter { it.name !in used && it.name !in METADATA_TAGS }.mapNotNull { factOf(it.name, null) }

    return DListItemView(
        defined = true,
        title = valueOf(titleField),
        summary = if (summaryField == titleField) null else valueOf(summaryField),
        image = httpUrl(valueOf(imageField)),
        link = linkHref?.let { DListLink(declared[linkField]?.description ?: httpHost(it), it, httpHost(it)) },
        media = if (mediaUrl != null && mediaKind != null) DListMedia(mediaUrl, mediaKind) else null,
        location = locationField?.takeIf { it in declared }?.let { mostPreciseGeohash(values[it]) },
        facts = facts,
        moreFields = moreFields,
        undeclared = undeclaredFacts(item, declared.keys),
        missing = fields.filter { it.requirement == FieldRequirement.REQUIRED && values[it.name] == null }.map { it.name },
        usedFields = used,
    )
}

/**
 * Tier one: an item whose header is unknown or declares nothing. Its own name, title and
 * description head it, so a feed card still says what it is; every other tag is shown as it is.
 */
private fun rawItemView(item: TagArray): DListItemView =
    DListItemView(
        defined = false,
        title = item.firstValue("name") ?: item.firstValue("title") ?: item.firstValue("t"),
        summary = item.firstValue("description") ?: item.firstValue("comments"),
        image = null,
        link = null,
        facts = emptyList(),
        undeclared = undeclaredFacts(item, ITEM_TEXT_TAGS),
        missing = emptyList(),
    )

/**
 * The item's tags its header never declared, in tag order. Single-letter tags are the protocol's
 * (`d`, `z`, `p`, `e`, `t`, `g`, ...), not fields, and are left out, as is NIP metadata.
 */
private fun undeclaredFacts(
    item: TagArray,
    declared: Set<String>,
): List<DListFact> {
    // Keyed by field, in first-seen order, so repeats are counted without rescanning the list.
    val out = LinkedHashMap<String, DListFact>()
    for (tag in item) {
        if (tag.size < 2) continue
        val name = tag[0]
        if (name.length < 2 || name in declared || name in METADATA_TAGS) continue
        val value = tag[1]
        if (value.isBlank()) continue
        val existing = out[name]
        out[name] =
            if (existing != null) {
                existing.copy(extra = existing.extra + 1)
            } else {
                // Undeclared, so untyped: text, even when it looks like a link.
                DListFact(name, null, value, 0, null)
            }
    }
    return out.values.toList()
}
