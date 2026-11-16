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
package com.vitorpamplona.amethyst.commons.prodbench

import com.vitorpamplona.amethyst.commons.preview.MetaTagsParser
import com.vitorpamplona.amethyst.commons.preview.OpenGraphParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Measures the `<meta>` scan behind every link preview.
 *
 * Every URL in a rendered note can reach [MetaTagsParser] with a whole HTML document in hand, so
 * the scan runs on user-visible paths with attacker-shaped input (any site can serve a 1 MB head).
 * The numbers below are the guard against that: the parser must stay linear and allocation-light,
 * scanning at hundreds of MB/s rather than degrading with page size.
 *
 * Deterministic and offline. Prints ns/op and MB/s; the assertions only check that the scan still
 * finds the right tags, never wall time (CI machines vary).
 */
class MetaTagsParserBenchmark {
    companion object {
        /** The shape of a Vite/React SPA head: theme comment, inline script, then the og: block. */
        fun spaHead(): String =
            """
            <!DOCTYPE html>
            <html lang="en">
              <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <!-- No-flash theme: set class="dark" synchronously before first paint.
                     Fresh visitors (no stored choice) stay LIGHT -- we don't auto-dark a
                     dark-OS visitor until dark mode is fully reviewed. -->
                <script>
                  (function () {
                    var t = localStorage.getItem("app_theme");
                    for (var i = 0; i < 2; i++) {
                      if (t === "dark") document.documentElement.classList.add("dark");
                    }
                  })();
                </script>
                <title>Example - A Site</title>
                <meta name="description" content="A description that is long enough to look real." />
                <meta property="og:title" content="Example &mdash; Your Network. Your Rules." />
                <meta property="og:description" content="The decentralized layer for everything." />
                <meta property="og:image" content="https://example.com/og-image.png" />
                <meta property="og:image:width" content="1200" />
                <meta property="og:image:height" content="630" />
                <meta name="twitter:card" content="summary_large_image" />
                <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
                <link rel="manifest" href="/site.webmanifest" />
                <link rel="stylesheet" crossorigin href="/assets/index-Cxc5egnN.css" />
              </head>
              <body><div id="root"></div></body>
            </html>
            """.trimIndent()

        /**
         * A news/CMS head: [tags] worth of meta+link noise, analytics scripts, JSON-LD and
         * boilerplate comments, with the og: block near the end -- the worst realistic ordering.
         */
        fun heavyHead(tags: Int): String {
            val sb = StringBuilder(64 * 1024)
            sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n")
            sb.append("<meta charset=\"utf-8\">\n")
            repeat(tags) { i ->
                sb.append("<!-- section $i: don't reorder, the CMS won't regenerate it -->\n")
                sb.append("<meta name=\"cms:field-$i\" content=\"value $i for a field nobody reads\">\n")
                sb.append("<link rel=\"preload\" as=\"font\" href=\"/fonts/f$i.woff2\" crossorigin>\n")
                sb.append("<script>window.__cfg$i = { id: $i, path: \"/a/b/c\", t: 0 < 1 };</script>\n")
                sb.append("<style>.cls-$i { font-family: 'Figtree', sans-serif; }</style>\n")
            }
            sb.append("<script type=\"application/ld+json\">{\"@type\":\"Article\",\"headline\":\"x < y\"}</script>\n")
            sb.append("<meta property=\"og:title\" content=\"The Headline\">\n")
            sb.append("<meta property=\"og:description\" content=\"The standfirst, in full.\">\n")
            sb.append("<meta property=\"og:image\" content=\"https://example.com/lead.jpg\">\n")
            sb.append("</head>\n<body>\n")
            // Body the parser must never reach: it stops at </head>.
            repeat(tags * 40) { i -> sb.append("<p class=\"para\">Paragraph $i with <em>markup</em> and \"quotes\".</p>\n") }
            sb.append("</body>\n</html>\n")
            return sb.toString()
        }

        /** Same content, but with no `</head>` to stop at -- the scan runs over the whole document. */
        fun unterminatedHead(tags: Int): String = heavyHead(tags).replace("</head>", "")

        fun bench(
            label: String,
            input: String,
            reps: Int,
            op: (String) -> Int,
        ) {
            repeat(maxOf(reps / 4, 2)) { op(input) } // warmup
            val t0 = System.nanoTime()
            var sink = 0
            repeat(reps) { sink += op(input) }
            val ns = (System.nanoTime() - t0) / reps
            val mbps = input.length.toDouble() / ns * 1000.0 // bytes/ns -> MB/s
            println(
                String.format(
                    "%-34s %9d B %9d ns/op %8.1f MB/s   (hits=%d)",
                    label,
                    input.length,
                    ns,
                    mbps,
                    sink / reps,
                ),
            )
        }
    }

    @Test
    fun metaScans() {
        val spa = spaHead()
        val heavy = heavyHead(60)
        val open = unterminatedHead(60)

        // correctness first: a benchmark that finds nothing measures nothing
        val spaInfo = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(spa))
        assertEquals("Example — Your Network. Your Rules.", spaInfo.title)
        assertEquals("https://example.com/og-image.png", spaInfo.image)

        val heavyInfo = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(heavy))
        assertEquals("The Headline", heavyInfo.title)
        assertEquals("https://example.com/lead.jpg", heavyInfo.image)

        // the body after </head> is never scanned
        assertEquals(1 + 60 + 3, MetaTagsParser.parse(heavy).count())

        // Note: the two "heavy head" rows report MB/s over the whole document, of which only the
        // ~46 KB head is actually scanned -- the scan stops at </head>. The last row is the same
        // page with no </head>, i.e. what a hostile server can force us to read end to end.
        println("MetaTagsParser")
        bench("spa head, all tags", spa, 50_000) { MetaTagsParser.parse(it).count() }
        bench("spa head, og: extraction", spa, 50_000) {
            OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(it)).title.length
        }
        bench("heavy head, to </head>", heavy, 2_000) { MetaTagsParser.parse(it).count() }
        bench("heavy head, og: extraction", heavy, 2_000) {
            OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(it)).title.length
        }
        bench("no </head>, whole doc", open, 2_000) { MetaTagsParser.parse(it).count() }

        // What production runs (HtmlParser asks for the <title> and icon <link>s too). Should sit
        // on top of the rows above: the 60 preload links never reach the attribute parser.
        bench("spa head, +title/icons", spa, 50_000) { MetaTagsParser.parse(it, includeTitleAndIcons = true).count() }
        bench("spa head, og: + fallbacks", spa, 50_000) {
            OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(it, includeTitleAndIcons = true)).title.length
        }
        bench("heavy head, +title/icons", heavy, 2_000) { MetaTagsParser.parse(it, includeTitleAndIcons = true).count() }
        bench("no </head>, +title/icons", open, 2_000) { MetaTagsParser.parse(it, includeTitleAndIcons = true).count() }

        val spaFallbacks = OpenGraphParser().extractUrlInfo(MetaTagsParser.parse(spa, includeTitleAndIcons = true))
        assertEquals("Example — Your Network. Your Rules.", spaFallbacks.title)
        assertEquals("/favicon.svg", spaFallbacks.icon)

        assertTrue(MetaTagsParser.parse(open).count() >= 64)
    }
}
