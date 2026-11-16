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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * PROVISIONAL links built from data, not code (Brainstorm `lib/linkTemplates.ts`,
 * `docs/dictionary/dlist-presentation-conventions.md` §5). A header names a URL template and says
 * which of its declared fields fills each placeholder:
 *
 *     ["link", <template event id>, <relay hint>, <placeholder>, <field>, <placeholder>, <field>, …]
 *
 * The template is an item of the URL Templates concept
 * (`39998:2efaa715…:url-templates`): `["url-template", "https://github.com/{username}"]` and
 * `["name", "GitHub profile"]`. It is pinned by event id, never by address, so nobody can re-point
 * it under every list that uses it.
 *
 * Templates are RFC 6570 level 1 only (`{name}`), must start with a literal `https://host/`, and
 * every value is percent-encoded, so no value can choose the host or add a path, query or fragment.
 */
@Immutable
data class LinkRef(
    val templateId: HexKey,
    /** Where the template can be fetched; null when the header gave none. */
    val relay: String?,
    /** Placeholder to the declared field that fills it, in tag order. */
    val bindings: List<Pair<String, String>>,
)

@Immutable
data class UrlTemplate(
    val id: HexKey,
    val name: String,
    val template: String,
)

/** A link an item opens: what it says, where it goes, and the host shown beside it. */
@Immutable
data class DListLink(
    val label: String,
    val href: String,
    val host: String,
)

const val LINK_TAG = "link"
const val URL_TEMPLATE_FIELD = "url-template"

/** The canonical URL Templates concept: its items are the templates headers pin. */
const val URL_TEMPLATES_CONCEPT = "39998:2efaa715bbb46dd5be6b7da8d7700266d11674b913b8178addb5c2e63d987331:url-templates"

private val HEX64 = Regex("^[0-9a-fA-F]{64}$")
private val VARNAME = Regex("^[A-Za-z0-9_]+$")
private val PLACEHOLDER = Regex("\\{([^{}]*)\\}")

// The host is letters, digits, dots and dashes only: `@` (userinfo) or `\\` would let a template
// read as one host and open another.
private val HTTPS_HEAD = Regex("^https://([A-Za-z0-9.-]+(?::[0-9]{1,5})?)(/|$)", RegexOption.IGNORE_CASE)

/** The header's link templates. A pair may only bind a declared field; an id that isn't one is no link. */
fun TagArray.linkRefs(declared: Set<String>): List<LinkRef> {
    val out = ArrayList<LinkRef>()
    for (tag in this) {
        if (tag.size < 2 || tag[0] != LINK_TAG || !HEX64.matches(tag[1])) continue
        val bindings = ArrayList<Pair<String, String>>()
        var i = 3
        while (i + 1 < tag.size) {
            if (VARNAME.matches(tag[i]) && tag[i + 1] in declared) bindings.add(tag[i] to tag[i + 1])
            i += 2
        }
        val relay = tag.getOrNull(2)?.takeIf { it.startsWith("wss://") || it.startsWith("ws://") }
        out.add(LinkRef(tag[1].lowercase(), relay, bindings))
    }
    return out
}

/** A URL Templates item, read; null when it carries no template. */
fun urlTemplateOf(
    id: HexKey,
    tags: TagArray,
): UrlTemplate? {
    val template =
        tags
            .firstOrNull { it.size > 1 && it[0] == URL_TEMPLATE_FIELD }
            ?.get(1)
            ?.trim()
            ?.ifEmpty { null } ?: return null
    // A template with no name is labelled by where it goes.
    val name =
        tags
            .firstOrNull { it.size > 1 && it[0] == "name" }
            ?.get(1)
            ?.trim()
            ?.ifEmpty { null } ?: httpsHost(template) ?: return null
    return UrlTemplate(id, name, template)
}

/**
 * A template's placeholders, in order, without repeats; null when it breaks the rules: no literal
 * `https://host/` start, a placeholder in the host, anything beyond level 1 (`{+path}`, `{?q}`,
 * `{a,b}`) or a stray brace.
 */
fun templatePlaceholders(template: String): List<String>? {
    if (HTTPS_HEAD.find(template) == null) return null
    val names = LinkedHashSet<String>()
    for (match in PLACEHOLDER.findAll(template)) {
        val name = match.groupValues[1]
        if (!VARNAME.matches(name)) return null
        names.add(name)
    }
    val rest = template.replace(PLACEHOLDER, "")
    if ('{' in rest || '}' in rest) return null
    return names.toList()
}

private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
private const val HEX = "0123456789ABCDEF"

/** RFC 6570 simple expansion: everything but RFC 3986's unreserved characters is percent-encoded. */
fun percentEncode(value: String): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val c = (byte.toInt() and 0xFF).toChar()
        if (c in UNRESERVED) {
            out.append(c)
        } else {
            out.append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
        }
    }
    return out.toString()
}

/** The host of an https URL, lowercased; null for anything else. */
fun httpsHost(url: String): String? =
    HTTPS_HEAD
        .find(url)
        ?.groupValues
        ?.get(1)
        ?.lowercase()

/** The link, or null when the template breaks the rules or a placeholder has no value. */
fun expandTemplate(
    template: String,
    values: Map<String, String?>,
): String? {
    val names = templatePlaceholders(template) ?: return null
    // `.` and `..` survive percent-encoding and would climb the template's path.
    if (names.any { values[it].isNullOrBlank() || values[it]!!.trim().let { v -> v == "." || v == ".." } }) return null
    val href = template.replace(PLACEHOLDER) { percentEncode(values.getValue(it.groupValues[1])!!.trim()) }
    val host = httpsHost(template.replace(PLACEHOLDER, "x")) ?: return null
    return if (httpsHost(href) == host) href else null
}

/** An item's links: each ref whose template was found and whose placeholders the item fills, once per address. */
fun itemLinks(
    item: TagArray,
    refs: List<LinkRef>,
    templates: Map<HexKey, UrlTemplate>,
): List<DListLink> {
    val out = ArrayList<DListLink>()
    for (ref in refs) {
        val template = templates[ref.templateId] ?: continue
        val values = ref.bindings.associate { (placeholder, field) -> placeholder to item.firstOrNull { it.size > 1 && it[0] == field && it[1].isNotBlank() }?.get(1) }
        val href = expandTemplate(template.template, values) ?: continue
        if (out.any { it.href == href }) continue
        out.add(DListLink(template.name, href, httpsHost(href) ?: continue))
    }
    return out
}
