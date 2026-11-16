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
package com.vitorpamplona.amethyst.commons.rendering.unknown

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.kinds.KindNames
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.UnrecognizedShape
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64

/**
 * What the `content` of an event of a kind the app has no renderer for looks like, so the fallback
 * card can decide between drawing it as text, as fields, or not at all.
 */
@Immutable
sealed interface ContentShape {
    /** No content, or only whitespace. */
    data object Empty : ContentShape

    /** Words for people: drawn by the rich-text viewer (links, mentions and media work). */
    data class Prose(
        val text: String,
    ) : ContentShape

    /**
     * A JSON document. [fields] are the top-level members of an object with their scalar values
     * (nested objects and arrays are summarized), for drawing as a table; [pretty] is the document
     * re-indented for a code block, built only when asked for.
     */
    class Json(
        val fields: List<JsonField>,
        val isArray: Boolean,
        val size: Int,
        private val element: JsonElement,
    ) : ContentShape {
        val pretty: String by lazy {
            PRETTY_JSON.encodeToString(JsonElement.serializer(), element).let { if (it.length > MAX_PRETTY_CHARS) it.take(MAX_PRETTY_CHARS) + "\n…" else it }
        }
    }

    /** Ciphertext: only the recipients can read it, so nothing of it is shown. */
    data class Encrypted(
        val scheme: EncryptionScheme,
    ) : ContentShape

    /** A blob for software (hex, base64, an unbroken token): shown, at most, as a short code preview. */
    data class Encoded(
        val encoding: BlobEncoding,
        val length: Int,
    ) : ContentShape
}

enum class EncryptionScheme { NIP04, NIP44 }

/** [JSON] is a document too large or too deeply nested to parse while drawing a feed. */
enum class BlobEncoding { HEX, BASE64, JSON, OPAQUE }

/** One top-level member of a JSON object. [nested] when the value is an object or array, summarized in [value]. */
@Immutable
data class JsonField(
    val key: String,
    val value: String,
    val nested: Boolean,
)

@Immutable
data class DigestPerson(
    val pubKey: HexKey,
    val role: String?,
)

@Immutable
data class DigestEventRef(
    val id: HexKey,
    val relay: String?,
    val marker: String?,
    /** True for a NIP-18 `q` quote, false for an `e` reference. */
    val isQuote: Boolean,
)

@Immutable
data class DigestAddressRef(
    val address: Address,
    val relay: String?,
    val marker: String?,
)

@Immutable
data class DigestMedia(
    val url: String,
    val mimeType: String?,
    val alt: String?,
    val dimensions: String?,
    val blurhash: String?,
)

/** A tag the digest has no slot for, as it was written. */
@Immutable
data class RawTag(
    val name: String,
    val values: List<String>,
)

/**
 * Everything the fallback card can say about an event of a kind the app has no renderer for,
 * read from the conventions most kinds share: NIP-31 `alt`, `title`/`name`/`subject`,
 * `summary`/`description`, `image`/`imeta`, `p`/`e`/`q`/`a` references, `t` hashtags, `r` links,
 * the `d` identifier and NIP-40 expiration. Tags it has no slot for stay in [otherTags], in order;
 * `nonce`, `zap` and `-` are left out of it, since the note row around the card already shows them.
 */
@Immutable
class UnknownEventDigest(
    val kind: Int,
    /** English label from quartz's kind registry, or null when no NIP names this kind. */
    val kindName: String?,
    val alt: String?,
    val title: String?,
    val summary: String?,
    val content: ContentShape,
    val media: List<DigestMedia>,
    val people: List<DigestPerson>,
    val events: List<DigestEventRef>,
    val addresses: List<DigestAddressRef>,
    val hashtags: List<String>,
    val links: List<String>,
    val identifier: String?,
    val kinds: List<Int>,
    val relays: List<String>,
    val publishedAt: Long?,
    val startsAt: Long?,
    val endsAt: Long?,
    val expiration: Long?,
    val geohash: String?,
    val location: String?,
    val contentWarning: String?,
    val client: String?,
    val otherTags: List<RawTag>,
    val tagCount: Int,
) {
    /** Text a person wrote about this event: the content when it is prose, else the `alt` description. */
    val readableText: String?
        get() = (content as? ContentShape.Prose)?.text ?: alt

    /** The hashtags the prose content doesn't already show inline as `#tag`, for chips under it. */
    val uncitedHashtags: List<String> by lazy {
        val text = (content as? ContentShape.Prose)?.text ?: return@lazy hashtags
        hashtags.filterNot { text.contains("#$it", ignoreCase = true) }
    }

    companion object {
        fun of(event: Event): UnknownEventDigest {
            var alt: String? = null
            var title: String? = null
            var summary: String? = null
            var identifier: String? = null
            var publishedAt: Long? = null
            var startsAt: Long? = null
            var endsAt: Long? = null
            var expiration: Long? = null
            var geohash: String? = null
            var location: String? = null
            var contentWarning: String? = null
            var client: String? = null
            // Fallbacks for the title and summary: shown only when the preferred tag is missing,
            // else they stay with the other tags.
            var nameTag: Array<String>? = null
            var subjectTag: Array<String>? = null
            var descriptionTag: Array<String>? = null
            val media = LinkedHashMap<String, DigestMedia>()
            val people = ArrayList<DigestPerson>()
            val events = ArrayList<DigestEventRef>()
            val addresses = ArrayList<DigestAddressRef>()
            val hashtags = LinkedHashSet<String>()
            val links = LinkedHashSet<String>()
            val kinds = LinkedHashSet<Int>()
            val relays = LinkedHashSet<String>()
            val other = ArrayList<RawTag>()

            for (tag in event.tags) {
                if (tag.isEmpty()) continue
                val v = tag.getOrNull(1)?.trim()?.ifEmpty { null }
                // A slot keeps the first value it sees; a repeat of the tag stays with the other tags.
                val consumed =
                    when (tag[0]) {
                        "alt" -> v != null && alt == null && true.also { alt = v }
                        "title" -> v != null && title == null && true.also { title = v }
                        "name" -> v != null && nameTag == null && true.also { nameTag = tag }
                        "subject" -> v != null && subjectTag == null && true.also { subjectTag = tag }
                        "summary" -> v != null && summary == null && true.also { summary = v }
                        "description", "about" -> v != null && descriptionTag == null && true.also { descriptionTag = tag }
                        "d" -> identifier == null && true.also { identifier = v }
                        "image", "thumb", "picture", "banner", "cover" ->
                            v != null && v.isWebUrl() && media.addIfNew(DigestMedia(v, null, null, tag.getOrNull(2), null))
                        "imeta" -> parseImeta(tag)?.let { media.addIfNew(it) } ?: false
                        "p" -> v != null && Hex.isHex64(v) && true.also { people += DigestPerson(v, tag.getOrNull(3)?.ifBlank { null }) }
                        "e", "q" ->
                            if (v != null && Hex.isHex64(v)) {
                                events += DigestEventRef(v, tag.getOrNull(2)?.ifBlank { null }, tag.getOrNull(3)?.ifBlank { null }, isQuote = tag[0] == "q")
                                true
                            } else {
                                // `q` may carry an address.
                                v?.let { Address.parse(it) }?.also { addresses += DigestAddressRef(it, tag.getOrNull(2)?.ifBlank { null }, null) } != null
                            }
                        "a" -> v?.let { Address.parse(it) }?.also { addresses += DigestAddressRef(it, tag.getOrNull(2)?.ifBlank { null }, tag.getOrNull(3)?.ifBlank { null }) } != null
                        "t" -> v != null && true.also { hashtags += v }
                        "r", "u", "url", "web", "website" -> v != null && v.isWebUrl() && true.also { links += v }
                        "k" -> v?.toIntOrNull()?.also { kinds += it } != null
                        "relay", "relays" -> {
                            val urls = tag.drop(1).filter { it.startsWith("ws") }
                            relays += urls
                            urls.isNotEmpty()
                        }
                        "published_at" -> publishedAt == null && v?.toLongOrNull()?.also { publishedAt = it } != null
                        "start", "starts" -> startsAt == null && v?.toLongOrNull()?.also { startsAt = it } != null
                        "end", "ends" -> endsAt == null && v?.toLongOrNull()?.also { endsAt = it } != null
                        "expiration" -> expiration == null && v?.toLongOrNull()?.also { expiration = it } != null
                        // The most precise of the geohash prefixes an event carries.
                        "g" -> v != null && true.also { if (geohash.let { g -> g == null || v.length > g.length }) geohash = v }
                        "location" -> v != null && location == null && true.also { location = v }
                        "content-warning" -> contentWarning == null && true.also { contentWarning = v ?: "" }
                        "client" -> v != null && client == null && true.also { client = v }
                        // Drawn by every note row already (PoW, zap splits) or meant for relays (NIP-70).
                        "nonce", "zap", "-" -> true
                        else -> false
                    }
                if (!consumed) other += RawTag(tag[0], tag.drop(1))
            }

            // The fallbacks that didn't fill the title or summary are left with the other tags.
            val name = nameTag?.get(1)?.trim()
            val subject = subjectTag?.get(1)?.trim()
            val description = descriptionTag?.get(1)?.trim()
            val titleSource =
                if (title != null) {
                    null
                } else if (name != null) {
                    nameTag
                } else {
                    subjectTag
                }
            listOf(nameTag, subjectTag).forEach { if (it != null && it !== titleSource) other += RawTag(it[0], it.drop(1)) }
            if (summary != null) descriptionTag?.let { other += RawTag(it[0], it.drop(1)) }

            return UnknownEventDigest(
                kind = event.kind,
                kindName = if (event.isNotTheKindsNamedShape()) null else KindNames.nameFor(event.kind),
                alt = alt,
                title = title ?: name ?: subject,
                summary = summary ?: description,
                content = classifyContent(event.content),
                media = media.values.toList(),
                people = people.distinctBy { it.pubKey },
                events = events.distinctBy { it.id },
                addresses = addresses.distinctBy { it.address },
                hashtags = hashtags.toList(),
                links = links.toList(),
                identifier = identifier,
                kinds = kinds.toList(),
                relays = relays.toList(),
                publishedAt = publishedAt,
                startsAt = startsAt,
                endsAt = endsAt,
                expiration = expiration,
                geohash = geohash,
                location = location,
                contentWarning = contentWarning,
                client = client,
                otherTags = other,
                tagCount = event.tags.size,
            )
        }

        /** Adds [item] under its url unless that url is already there (putIfAbsent is JVM-only). */
        private fun MutableMap<String, DigestMedia>.addIfNew(item: DigestMedia): Boolean {
            if (item.url in this) return false
            this[item.url] = item
            return true
        }

        private fun parseImeta(tag: Array<String>): DigestMedia? {
            var url: String? = null
            var mime: String? = null
            var alt: String? = null
            var dim: String? = null
            var blurhash: String? = null
            for (i in 1 until tag.size) {
                val entry = tag[i]
                val space = entry.indexOf(' ')
                if (space <= 0) continue
                val value = entry.substring(space + 1).trim()
                when (entry.substring(0, space)) {
                    "url" -> url = url ?: value
                    "m" -> mime = mime ?: value
                    "alt" -> alt = alt ?: value
                    "dim" -> dim = dim ?: value
                    "blurhash" -> blurhash = blurhash ?: value
                }
            }
            return url?.takeIf { it.isWebUrl() }?.let { DigestMedia(it, mime, alt, dim, blurhash) }
        }

        private fun String.isWebUrl() = startsWith("https://", ignoreCase = true) || startsWith("http://", ignoreCase = true)
    }
}

/**
 * True when the kind's name in `KindNames` belongs to another shape of the kind: another app's event
 * on a shared kind ([UnrecognizedShape], such as a voting round on drss.io's 30101, "DRSS List
 * Folder"). The card names those by number instead.
 */
private fun Event.isNotTheKindsNamedShape(): Boolean = this is UnrecognizedShape

/** True when [event] is of a kind quartz has no class for: EventFactory's plain [Event]. */
fun isUnknownKind(event: Event): Boolean = event::class == Event::class

/**
 * The unknown kinds the cache keeps, so the fallback card can draw them when they are quoted,
 * linked or tagged. Not ephemeral ones (20000-29999): relays don't store them, nothing can link to them.
 */
fun isStorableUnknownKind(event: Event): Boolean = isUnknownKind(event) && event.kind !in 20_000..29_999

/**
 * Where NIP-01 says an event of an unknown kind lives when it is replaceable (0, 3, 10000-19999:
 * one per author) or addressable (30000-39999: one per author and `d` tag), or null for a regular
 * kind, which lives only by its id.
 */
fun unknownKindAddress(event: Event): Address? =
    when (event.kind) {
        0, 3, in 10_000..19_999 -> Address(event.kind, event.pubKey, "")
        in 30_000..39_999 -> Address(event.kind, event.pubKey, event.tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: "")
        else -> null
    }

/** Long single tokens that are meant to be read: links, invoices, ecash and bech32 entities. */
private val READABLE_TOKEN_PREFIXES =
    listOf("nostr:", "lightning:", "lnbc", "lntb", "lnurl", "lno1", "cashu", "npub1", "nprofile1", "note1", "nevent1", "naddr1", "nrelay1")

private val PRETTY_JSON = Json { prettyPrint = true }

private const val MAX_PRETTY_CHARS = 4_000
private const val MAX_JSON_CHARS = 32_000
private const val MAX_JSON_DEPTH = 32
private const val MAX_FIELD_CHARS = 160

/**
 * Sorts [content] into one of the [ContentShape]s. The deciding question is "are these words?":
 * natural language has spaces between its words, a payload for software doesn't. So a whitespace-free
 * run is checked for ciphertext, hex and base64, and anything with spaces is prose, unless it is a
 * JSON document or base64 wrapped onto several lines.
 */
fun classifyContent(content: String): ContentShape {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return ContentShape.Empty

    if ((trimmed.startsWith('{') && trimmed.endsWith('}')) || (trimmed.startsWith('[') && trimmed.endsWith(']'))) {
        parseJson(trimmed)?.let { return it }
        // "[spam] buy now" is not JSON: fall through to the prose checks.
    }

    val hasSpaces = trimmed.any { it == ' ' || it == '\t' }
    if (!hasSpaces) {
        val oneLine = trimmed.none { it == '\n' || it == '\r' }
        if (oneLine && EncryptedInfo.isNIP04(trimmed)) return ContentShape.Encrypted(EncryptionScheme.NIP04)
        if (READABLE_TOKEN_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) } || trimmed.contains("://")) {
            return ContentShape.Prose(content)
        }
        val joined = if (oneLine) trimmed else trimmed.filterNot { it == '\n' || it == '\r' }
        if (oneLine && isNip44Payload(joined)) return ContentShape.Encrypted(EncryptionScheme.NIP44)
        if (joined.length >= 32 && Hex.isHex(joined)) return ContentShape.Encoded(BlobEncoding.HEX, joined.length)
        if (joined.length >= 64 && joined.all { it.isBase64Char() }) return ContentShape.Encoded(BlobEncoding.BASE64, joined.length)
        if (joined.length >= 120) return ContentShape.Encoded(BlobEncoding.OPAQUE, joined.length)
    }
    // A short word ("gm"), a sentence, a paragraph: words for people.
    return ContentShape.Prose(content)
}

/** NIP-44 v2: base64 of a payload that starts with the version byte 2 and is at least 99 bytes long. */
private fun isNip44Payload(text: String): Boolean {
    // Padding is not required: some clients strip it. Only the version byte decides.
    if (text.length < 132) return false
    if (!text.all { it.isBase64Char() }) return false
    return try {
        Base64.decode(text, 0, 4)[0] == 2.toByte()
    } catch (_: IllegalArgumentException) {
        false
    }
}

private fun Char.isBase64Char() = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '+' || this == '/' || this == '=' || this == '-' || this == '_'

/**
 * Parses [text] as JSON, or null when it isn't. A document over [MAX_JSON_CHARS] or nested deeper
 * than [MAX_JSON_DEPTH] is not parsed: the parser recurses (a relay can send `[[[[…` a hundred
 * thousand deep and overflow the stack) and this runs while a feed draws.
 */
private fun parseJson(text: String): ContentShape? {
    if (text.length > MAX_JSON_CHARS || jsonDepthExceeds(text, MAX_JSON_DEPTH)) return ContentShape.Encoded(BlobEncoding.JSON, text.length)
    val element =
        try {
            Json.parseToJsonElement(text)
        } catch (_: IllegalArgumentException) {
            return null
        }
    return when (element) {
        is JsonObject -> ContentShape.Json(element.map { (k, v) -> jsonField(k, v) }, isArray = false, size = element.size, element = element)
        is JsonArray -> ContentShape.Json(emptyList(), isArray = true, size = element.size, element = element)
        else -> null
    }
}

/** True when brackets outside of strings nest deeper than [max] anywhere in [text]. */
private fun jsonDepthExceeds(
    text: String,
    max: Int,
): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (c in text) {
        if (inString) {
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{', '[' -> if (++depth > max) return true
                '}', ']' -> depth--
            }
        }
    }
    return false
}

private fun jsonField(
    key: String,
    value: JsonElement,
): JsonField =
    when (value) {
        is JsonNull -> JsonField(key, "null", nested = false)
        is JsonPrimitive -> JsonField(key, value.content.let { if (it.length > MAX_FIELD_CHARS) it.take(MAX_FIELD_CHARS) + "…" else it }, nested = false)
        is JsonArray -> JsonField(key, "[${value.size}]", nested = true)
        is JsonObject -> JsonField(key, "{${value.keys.take(4).joinToString(", ")}${if (value.size > 4) ", …" else ""}}", nested = true)
    }
