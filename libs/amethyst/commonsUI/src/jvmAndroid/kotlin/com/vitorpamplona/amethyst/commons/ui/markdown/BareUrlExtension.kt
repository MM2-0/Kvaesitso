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
package com.vitorpamplona.amethyst.commons.ui.markdown

import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.parser.PostProcessor
import org.commonmark.parser.beta.InlineContentParser
import org.commonmark.parser.beta.InlineContentParserFactory
import org.commonmark.parser.beta.InlineParserState
import org.commonmark.parser.beta.ParsedInline
import org.commonmark.parser.beta.Scanner
import org.nibor.autolink.LinkExtractor
import org.nibor.autolink.LinkType
import java.util.EnumSet

/** The options every markdown note is parsed with: [CommonMarkdownParseOptions.MarkdownWithLinks] plus [BareUrlExtension]. */
val AmethystMarkdownParseOptions = CommonMarkdownParseOptions(CommonMarkdownParseOptions.MarkdownWithLinks.extensions + BareUrlExtension)

/**
 * Keeps bare `http(s)` URLs whole in markdown.
 *
 * commonmark's autolink extension links a bare URL only after the inline parse, in the finished
 * text nodes, so the `_`, `*` and `~` in a URL's path were read as emphasis first:
 * `https://m.media-amazon.com/images/I/71zvSJXs2KL._SL1499_.jpg` became a link to
 * `…/71zvSJXs2KL`, an italic `SL1499` and a stray `.jpg`, and the image never showed.
 *
 * This parses such a URL as a [Link] during the inline parse, before emphasis is resolved, so
 * its markers stay in it. Being part of the parser, it never runs inside code, an autolink or a
 * link's destination. A URL with no marker in it is left to the autolink extension as before.
 */
object BareUrlExtension : Parser.ParserExtension {
    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customInlineContentParserFactory(Factory)
        parserBuilder.postProcessor(Finisher)
    }

    // The finder commonmark's autolink extension uses, so a URL found here ends where its would.
    private val urlFinder = LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build()

    // Longer than any real URL; bounds the look-ahead on a pathological run of non-space text.
    private const val MAX_URL_LENGTH = 4096

    // `https://`, the longer of the two schemes taken.
    private const val SCHEME_LENGTH = 8

    // Left out of the link, as GFM does: they usually close the emphasis the URL sits in.
    // [Finisher] gives them back when nothing used them.
    private const val TRAILING = "*_~?!.,:;"

    /** A link made here; [held] is the trailing text left out of it, for [Finisher]. */
    private class BareUrlLink(
        url: String,
        val held: String,
    ) : Link(url, null)

    private object Factory : InlineContentParserFactory {
        override fun getTriggerCharacters(): Set<Char> = setOf('h', 'H')

        override fun create(): InlineContentParser = UrlParser
    }

    private object UrlParser : InlineContentParser {
        // ParsedInline.none() is null: the Java API means "not mine" by it.
        override fun tryParse(inlineParserState: InlineParserState): ParsedInline? {
            val scanner = inlineParserState.scanner()

            // Not the middle of a word (`xhttps://…`).
            val before = scanner.peekPreviousCodePoint()
            if (before != 0 && Character.isLetterOrDigit(before)) return ParsedInline.none()

            val start = scanner.position()
            val word = StringBuilder()
            // Most words starting with `h` aren't URLs: rule them out on the scheme alone, so
            // each costs a few characters rather than a read to the end of the word.
            var limit = SCHEME_LENGTH
            while (word.length < limit) {
                val c = scanner.peek()
                if (c == Scanner.END || c == '<' || c.isWhitespace()) break
                word.append(c)
                scanner.next()
                if (word.length == SCHEME_LENGTH) {
                    if (!word.startsWith("http://", ignoreCase = true) && !word.startsWith("https://", ignoreCase = true)) break
                    limit = MAX_URL_LENGTH
                }
            }
            scanner.setPosition(start)

            if (!word.startsWith("http://", ignoreCase = true) && !word.startsWith("https://", ignoreCase = true)) return ParsedInline.none()

            val span = urlFinder.extractLinks(word).firstOrNull()
            if (span == null || span.beginIndex != 0) return ParsedInline.none()

            var end = span.endIndex
            while (end > 0 && word[end - 1] in TRAILING) end--
            val url = word.substring(0, end)

            // Nothing in it the emphasis parser would take: the autolink extension does it as before.
            if (url.none { it == '_' || it == '*' || it == '~' }) return ParsedInline.none()
            // Escapes and entities are decoded in text but literal in a link: leave those to the
            // text path too, rather than link to a URL with a backslash or `&amp;` in it.
            if ('\\' in url || hasEntity(url)) return ParsedInline.none()

            repeat(end) { scanner.next() }
            val link = BareUrlLink(url, word.substring(end, span.endIndex))
            link.appendChild(Text(url))
            return ParsedInline.of(link, scanner.position())
        }

        private fun hasEntity(url: String): Boolean {
            var amp = url.indexOf('&')
            while (amp >= 0) {
                var j = amp + 1
                while (j < url.length && (url[j].isLetterOrDigit() || url[j] == '#')) j++
                if (j > amp + 1 && j < url.length && url[j] == ';') return true
                amp = url.indexOf('&', j)
            }
            return false
        }
    }

    /**
     * Gives a link back the trailing text it held out when no emphasis took it (`…/a_b_` with no
     * `_` opening before it), and turns a link inside another link's text or an image's alt back
     * into text, since a link can't hold a link.
     */
    private object Finisher : PostProcessor {
        override fun process(node: Node): Node {
            val links = ArrayList<BareUrlLink>()
            node.accept(
                object : AbstractVisitor() {
                    override fun visit(link: Link) {
                        if (link is BareUrlLink) links.add(link)
                        visitChildren(link)
                    }
                },
            )

            links.forEach { link ->
                val heldIsUnused = link.next.startsWithText(link.held)
                if (insideLinkOrImage(link)) {
                    link.insertBefore(Text(link.destination))
                    link.unlink()
                } else if (heldIsUnused) {
                    link.next.dropText(link.held)
                    link.destination += link.held
                    (link.firstChild as? Text)?.let { it.literal += link.held }
                }
            }
            return node
        }

        private fun insideLinkOrImage(link: Link): Boolean {
            var parent = link.parent
            while (parent != null) {
                if (parent is Link || parent is Image) return true
                parent = parent.parent
            }
            return false
        }

        private fun Node?.startsWithText(prefix: String) = prefix.isNotEmpty() && this is Text && literal.startsWith(prefix)

        private fun Node?.dropText(prefix: String) {
            val text = this as? Text ?: return
            text.literal = text.literal.substring(prefix.length)
            if (text.literal.isEmpty()) text.unlink()
        }
    }
}
