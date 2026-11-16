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
package com.vitorpamplona.amethyst.commons.decentralizedLists.tiers

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListImageShape
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListItemView
import com.vitorpamplona.amethyst.commons.decentralizedLists.httpUrl
import com.vitorpamplona.amethyst.commons.util.languageDisplayName
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * A book, read from its list item (Brainstorm `lib/books.ts`). Fields are read through the
 * governing definition, as every view's are, with one allowance: while the community `books`
 * header declares no fields at all, a field is read by the name Open Library's seed gave it, which
 * is also the name a copy that declares the fields gives it (`docs/dictionary/house-copies.md`), so
 * the two read the same values: reading the tags is reading the definition, until a copy renames
 * them.
 */
@Immutable
data class Book(
    val title: String,
    /** As on the cover: "Michael Duncan". */
    val author: String?,
    /** ISBN-13, digits only. */
    val isbn: String?,
    val isbn10: String?,
    /** Open Library work id, e.g. OL19722168W. */
    val workId: String?,
    val cover: String?,
    val year: String?,
    val pages: Int?,
    /** "English", from the ISO 639-2 code the item gives. */
    val language: String?,
    val subjects: List<String>,
)

object BookFields {
    const val TITLE = "title"
    const val AUTHOR = "author"
    const val ISBN = "isbn"
    const val ISBN10 = "isbn10"
    const val WORK_ID = "open-library-id"
    const val COVER = "cover"
    const val YEAR = "year"
    const val PAGES = "pages"
    const val LANG = "lang"
    const val SUBJECT = "subject"

    val ALL = setOf(TITLE, AUTHOR, ISBN, ISBN10, WORK_ID, COVER, YEAR, PAGES, LANG, SUBJECT)
}

/**
 * Every value the item gives a field. A declared field and the seed's tag are read from the same tag
 * name, so the declaration, when there is one, reads exactly these values.
 */
private fun TagArray.bookValues(name: String): List<String> = mapNotNull { tag -> if (tag.size > 1 && tag[0] == name) tag[1].trim().ifEmpty { null } else null }

private val NOT_ISBN = Regex("[^0-9Xx]")

fun bookOf(item: TagArray): Book? {
    fun one(name: String) = item.bookValues(name).firstOrNull()

    val title = one(BookFields.TITLE) ?: return null
    val author = one(BookFields.AUTHOR)?.let { authorName(it).ifEmpty { null } }
    return Book(
        title = title,
        author = author,
        isbn = one(BookFields.ISBN)?.replace(NOT_ISBN, "")?.ifEmpty { null },
        isbn10 = one(BookFields.ISBN10)?.replace(NOT_ISBN, "")?.ifEmpty { null },
        workId = one(BookFields.WORK_ID),
        cover = httpUrl(one(BookFields.COVER)),
        year = one(BookFields.YEAR),
        pages = one(BookFields.PAGES)?.toIntOrNull()?.takeIf { it > 0 },
        language = languageName(one(BookFields.LANG)),
        subjects = cleanSubjects(item.bookValues(BookFields.SUBJECT)),
    )
}

private val PARENS = Regex("\\s*\\([^)]*\\)\\s*")
private val SURNAME_FIRST = Regex("^([^,]+),\\s*(.+)$")
private val AUTHOR_SEPARATOR = Regex("\\s*&\\s*|\\s+and\\s+")
private val SPACES = Regex("\\s+")

/**
 * "Duncan, Michael (Podcaster)" -> "Michael Duncan"; "Kennedy, X. & Gioia, D." -> "X. Kennedy & D. Gioia".
 * A name already in cover order is left alone.
 */
fun authorName(raw: String): String =
    raw
        .split(AUTHOR_SEPARATOR)
        .map { part ->
            val bare = part.replace(PARENS, " ").trim()
            val m = SURNAME_FIRST.find(bare)
            (if (m != null) "${m.groupValues[2].trim()} ${m.groupValues[1].trim()}" else bare).replace(SPACES, " ")
        }.filter { it.isNotBlank() }
        .joinToString(" & ")

private val MACHINE_TAG = Regex("^[a-zA-Z]+:")

/** The subjects a reader would recognise: no machine tags (`nyt:…=2017-11-12`), no repeats. */
fun cleanSubjects(subjects: List<String>): List<String> {
    val seen = HashSet<String>()
    return subjects.mapNotNull { s ->
        val subject = s.trim()
        if (subject.isEmpty() || MACHINE_TAG.containsMatchIn(subject) || !seen.add(subject.lowercase())) null else subject
    }
}

private val CATALOGUE = Regex("\\.\\s|\\s--\\s")
private const val LONGEST_SUBJECT = 40

/** The few subjects a reader would recognise: the shortest (broadest) first, no catalogue headings, at most [n]. */
fun topSubjects(
    subjects: List<String>,
    n: Int,
): List<String> =
    subjects
        .filter { it.length <= LONGEST_SUBJECT && !CATALOGUE.containsMatchIn(it) }
        .sortedWith(compareBy<String> { it.length }.thenBy { it })
        .take(n)

/** ISO 639-2 codes (what Open Library's records carry) to the two letters a platform names. */
private val ISO_639_1 =
    mapOf(
        "eng" to "en",
        "spa" to "es",
        "fre" to "fr",
        "fra" to "fr",
        "ger" to "de",
        "deu" to "de",
        "por" to "pt",
        "ita" to "it",
        "dut" to "nl",
        "nld" to "nl",
        "rus" to "ru",
        "jpn" to "ja",
        "chi" to "zh",
        "zho" to "zh",
        "kor" to "ko",
        "ara" to "ar",
        "heb" to "he",
        "hin" to "hi",
        "pol" to "pl",
        "swe" to "sv",
        "nor" to "no",
        "dan" to "da",
        "fin" to "fi",
        "tur" to "tr",
        "gre" to "el",
        "ell" to "el",
        "lat" to "la",
        "cze" to "cs",
        "ces" to "cs",
        "hun" to "hu",
        "ukr" to "uk",
        "cat" to "ca",
        "ind" to "id",
        "vie" to "vi",
        "tha" to "th",
        "per" to "fa",
        "fas" to "fa",
        "rum" to "ro",
        "ron" to "ro",
        "bul" to "bg",
        "srp" to "sr",
        "hrv" to "hr",
    )

/** "eng" -> "English", in the reader's own language; null for a code the platform can't name. */
fun languageName(code: String?): String? {
    val c = code?.trim()?.lowercase()?.ifEmpty { null } ?: return null
    val tag = ISO_639_1[c] ?: c.takeIf { it.length == 2 } ?: return null
    return languageDisplayName(tag).takeIf { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
}

/** A book among many: its cover (a portrait), its title, its author. From the item alone. */
fun bookPresentation(
    item: TagArray,
    shown: DListItemView,
): DListItemView {
    val book = bookOf(item) ?: return shown
    return shown.copy(
        title = book.title,
        summary = book.author ?: shown.summary,
        image = book.cover ?: shown.image,
        imageShape = if (book.cover != null) DListImageShape.PORTRAIT else shown.imageShape,
    )
}

/** The list's other books by the same author, as the cover names them, in the list's order. Each book read once by the caller. */
fun <T> booksByAuthor(
    book: Book,
    others: List<Pair<T, Book>>,
): List<T> {
    val author = book.author?.trim()?.lowercase() ?: return emptyList()
    return others.filter { (_, other) -> other.author?.trim()?.lowercase() == author }.map { it.first }
}

/** The list's books sharing the most subjects with this one; sharing none, none. */
fun <T> similarBooks(
    book: Book,
    others: List<Pair<T, Book>>,
): List<T> {
    val mine = book.subjects.mapTo(HashSet()) { it.lowercase() }
    if (mine.isEmpty()) return emptyList()
    return others
        .map { (key, other) -> key to other.subjects.count { it.lowercase() in mine } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
}
