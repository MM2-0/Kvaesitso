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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.ui.material3.RichText
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.preview.UrlInfoItem
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunk
import com.vitorpamplona.amethyst.commons.richtext.MarkdownChunks
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.UrlCachedPreviewer
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.DefaultParagraphSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.MarkdownTextStyle
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.markdownStyle
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip92IMeta.imetasByUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
fun RenderContentAsMarkdown(
    content: String,
    tags: ImmutableListOfLists<String>?,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val anchors = LocalMarkdownAnchors.current
    val uriHandler = LocalUriHandler.current
    val onClick =
        remember(uriHandler, accountViewModel, nav, anchors) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    // `[link](#section)`: a section of the document being read, when it can be reached.
                    val section = anchors?.anchorOfLink(uri)
                    if (section != null && anchors.jumpTo(section)) return
                    val route = uriToRoute(uri, accountViewModel.account)
                    if (route != null) {
                        nav.nav(route)
                    } else {
                        runCatching { uriHandler.openUri(uri) }
                    }
                }
            }
        }

    // Split only when it buys something: highlighted code, or sections a reader can jump to.
    val chunks = remember(content, anchors != null) { MarkdownChunks.cachedSplit(content, atHeadings = anchors != null) }

    CompositionLocalProvider(LocalUriHandler provides onClick) {
        if (chunks == null) {
            MarkdownBlock(content, tags, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)
        } else {
            val spacing = with(LocalDensity.current) { DefaultParagraphSpacing.toDp() }
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                var headingPosition = 0
                chunks.forEach { chunk ->
                    when (chunk) {
                        is MarkdownChunk.Prose ->
                            Box(Modifier.markdownAnchor(anchors, chunk.anchor, if (chunk.anchor != null) headingPosition++ else -1)) {
                                if (chunk.markdown.isNotBlank()) {
                                    MarkdownBlock(chunk.markdown, tags, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)
                                }
                            }

                        is MarkdownChunk.Code -> HighlightedCodeBlock(chunk)
                    }
                }
            }
        }
    }
}

/** A fenced code block drawn like the Markdown library draws one, with the JSON-ish highlighter's colors. */
@Composable
private fun HighlightedCodeBlock(chunk: MarkdownChunk.Code) {
    val style = MaterialTheme.colorScheme.markdownStyle.codeBlockStyle
    val colors = MaterialTheme.colorScheme.codeColors
    val text =
        remember(chunk, colors) {
            if (MarkdownChunks.isHighlightable(chunk.language, chunk.code)) {
                highlightCode(chunk.code, chunk.language, colors)
            } else {
                AnnotatedString(chunk.code)
            }
        }
    // Code keeps its lines, as on GitHub: aligned comments and indentation stay readable, and a
    // long line scrolls sideways instead of wrapping.
    Box(modifier = style?.modifier ?: Modifier) {
        Text(
            text = text,
            style = style?.textStyle ?: MarkdownTextStyle,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/** One Markdown document, parsed once, drawn by the Markdown library with the app's media renderer. */
@Composable
private fun MarkdownBlock(
    content: String,
    tags: ImmutableListOfLists<String>?,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    callbackUri: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ProvideTextStyle(MarkdownTextStyle) {
        val astNode =
            remember(content) {
                CommonmarkAstNodeParser(AmethystMarkdownParseOptions).parse(content)
            }

        val forCapture = LocalMediaCapture.current != null
        val mediaBleed = LocalMediaBleed.current
        val renderer =
            remember(content, tags, canPreview, quotesLeft, callbackUri, accountViewModel, nav, forCapture, mediaBleed) {
                MarkdownMediaRenderer(
                    startOfText = content.take(100),
                    imetaByUrl = tags?.lists?.imetasByUrl() ?: emptyMap(),
                    canPreview = canPreview,
                    quotesLeft = quotesLeft,
                    backgroundColor = backgroundColor,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    forCapture = forCapture,
                    mediaBleed = mediaBleed,
                    topLevelUrls = if (mediaBleed > 0.dp) markdownTopLevelUrls(content) else emptySet(),
                )
            }

        // The renderer hands the bleed back to its top-level media; nothing else in here bleeds.
        ProvideMediaBleed(0.dp) {
            RichText(
                style = MaterialTheme.colorScheme.markdownStyle,
                renderer = renderer,
            ) {
                BasicMarkdown(astNode)
            }
        }
    }
}

private const val PREVIEW_CALLBACK_URI = "nostr:something"

@Preview
@Composable
fun RenderContentAsMarkdownPreview() {
    val accountViewModel = mockAccountViewModel()

    val nav = EmptyNav()

    ThemeComparisonRow {
        val background = MaterialTheme.colorScheme.background
        RenderContentAsMarkdown(
            "# Hello 1\n" +
                "## Hello 2\n" +
                "### Hello 3\n" +
                "#### Hello 4\n" +
                "##### Hello 5\n" +
                "###### Hello 6\n" +
                "This is regular text\n" +
                "\n" +
                "**This is bold text**\n" +
                "\n" +
                "__This is bold text__\n" +
                "\n" +
                "*This is italic text*\n" +
                "\n" +
                "_This is italic text_\n" +
                "\n" +
                "~~Strikethrough~~\n" +
                "\n" +
                "\n" +
                "## Blockquotes\n" +
                "\n" +
                "\n" +
                "> Blockquotes can also be nested...\n" +
                ">> ...by using additional greater-than signs right next to each other...\n" +
                "> > > ...or with spaces between arrows.\n" +
                "\n" +
                "\n" +
                "## End\n",
            tags = EmptyTagList,
            canPreview = true,
            quotesLeft = 2,
            backgroundColor =
                remember {
                    mutableStateOf(background)
                },
            callbackUri = PREVIEW_CALLBACK_URI,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Preview
@Composable
fun RenderContentAsMarkdownListsPreview() {
    val accountViewModel = mockAccountViewModel()

    val nav = EmptyNav()

    ThemeComparisonRow {
        val background = MaterialTheme.colorScheme.background
        RenderContentAsMarkdown(
            "## Lists\n" +
                "\n" +
                "Unordered\n" +
                "\n" +
                "+ Create a list by starting a line with `+`, `-`, or `*`\n" +
                "+ Sub-lists 2 spaces:\n" +
                "  - Character change forces new list:\n" +
                "    * Ac tristique libero volutpat at\n" +
                "    + Facilisis in pretium nisl aliquet\n" +
                "    - Nulla volutpat aliquam velit\n" +
                "+ Very easy!\n" +
                "\n" +
                "Ordered\n" +
                "\n" +
                "1. Lorem ipsum dolor sit amet\n" +
                "2. Consectetur adipiscing elit\n" +
                "3. Integer molestie lorem at massa\n" +
                "\n" +
                "\n" +
                "1. You can use sequential numbers...\n" +
                "1. ...or keep all the numbers as `1.`\n" +
                "\n" +
                "Start number with offset:\n" +
                "\n" +
                "57. foo\n" +
                "1. bar\n",
            tags = EmptyTagList,
            canPreview = true,
            quotesLeft = 2,
            backgroundColor =
                remember {
                    mutableStateOf(background)
                },
            callbackUri = PREVIEW_CALLBACK_URI,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Preview
@Composable
fun RenderContentAsMarkdownCodePreview() {
    val accountViewModel = mockAccountViewModel()

    val nav = EmptyNav()

    ThemeComparisonRow {
        val background = MaterialTheme.colorScheme.background
        RenderContentAsMarkdown(
            "## Code\n" +
                "\n" +
                "Inline `code`\n" +
                "\n" +
                "Indented code\n" +
                "\n" +
                "    // Some comments\n" +
                "    line 1 of code\n" +
                "    line 2 of code\n" +
                "    line 3 of code\n" +
                "\n" +
                "\n" +
                "Block code \"fences\"\n" +
                "\n" +
                "```\n" +
                "Sample text here...\n" +
                "```\n" +
                "\n" +
                "Syntax highlighting\n" +
                "\n" +
                "``` js\n" +
                "var foo = function (bar) {\n" +
                "  return bar++;\n" +
                "};\n" +
                "var veryVeryVeryLongVariableNameWithALongValueAsWell = 123456789012345678901234567890;\n" +
                "console.log(foo(5));\n" +
                "```\n" +
                "\n" +
                "\n" +
                "### End\n",
            tags = EmptyTagList,
            canPreview = true,
            quotesLeft = 2,
            backgroundColor =
                remember {
                    mutableStateOf(background)
                },
            callbackUri = PREVIEW_CALLBACK_URI,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Preview
@Composable
fun RenderContentAsMarkdownTablesPreview() {
    val accountViewModel = mockAccountViewModel()

    val nav = EmptyNav()

    ThemeComparisonRow {
        val background = MaterialTheme.colorScheme.background
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            RenderContentAsMarkdown(
                "## Tables\n" +
                    "\n" +
                    "| Option | Description |\n" +
                    "| ------ | ----------- |\n" +
                    "| data   | path to data files to supply the data that will be passed into templates. |\n" +
                    "| engine | engine to be used for processing templates. Handlebars is the default. |\n" +
                    "| ext    | extension to be used for dest files. |\n" +
                    "\n" +
                    "Right aligned columns\n" +
                    "\n" +
                    "| Option | Description |\n" +
                    "| ------:| -----------:|\n" +
                    "| data   | path to data files to supply the data that will be passed into templates. |\n" +
                    "| engine | engine to be used for processing templates. Handlebars is the default. |\n" +
                    "| ext    | extension to be used for dest files. |\n",
                tags = EmptyTagList,
                canPreview = true,
                quotesLeft = 2,
                backgroundColor =
                    remember {
                        mutableStateOf(background)
                    },
                callbackUri = PREVIEW_CALLBACK_URI,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Preview
@Composable
fun RenderContentAsMarkdownFootNotesPreview() {
    val accountViewModel = mockAccountViewModel()

    val nav = EmptyNav()

    ThemeComparisonRow {
        val background = MaterialTheme.colorScheme.background
        RenderContentAsMarkdown(
            "### [Footnotes]\n" +
                "\n" +
                "Footnote 1 link[^first].\n" +
                "\n" +
                "Footnote 2 link[^second].\n" +
                "\n" +
                "Duplicated footnote reference[^second].\n" +
                "\n" +
                "[^first]: Footnote **can have markup**\n" +
                "\n" +
                "    and multiple paragraphs.\n" +
                "\n" +
                "[^second]: Footnote text.\n",
            tags = EmptyTagList,
            canPreview = true,
            quotesLeft = 2,
            backgroundColor =
                remember {
                    mutableStateOf(background)
                },
            callbackUri = PREVIEW_CALLBACK_URI,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Preview
@Composable
fun RenderContentAsMarkdownUserPreview() {
    val accountViewModel = mockAccountViewModel()

    val nav = EmptyNav()

    runBlocking {
        withContext(Dispatchers.IO) {
            val qa =
                MetadataEvent(
                    id = "4d5e1c614e18b915e6899fff93b5345253a5e2a8d0cbdb9ca98758da1612e05b",
                    pubKey = "88cc134b1a65f54ef48acc1df3665063d3ea45f04eab8af4646e561c5ae99079",
                    createdAt = 1722306330,
                    tags = emptyArray(),
                    content = "{\"lud16\":\"qa@primal.net\",\"website\":\"https:\\/\\/primal.net\",\"lud06\":\"\",\"name\":\"qauser\",\"banner\":\"https:\\/\\/m.primal.net\\/HQTd.jpg\",\"display_name\":\"qa\",\"nip05\":\"qa@primal.net\",\"picture\":\"https:\\/\\/m.primal.net\\/JidC.jpg\",\"about\":\"If you are following this account for quality content, you are going to have a bad time. #qa #primal \"}",
                    sig = "2a5fbd16e8fd67873cee1e60fc7b1b09d8a3cb5d503528507fa47d29669b259153ba710acce725d1f1bec867d7f86cc72820e496604e9183e5f58d0f2fb98f58",
                )

            UrlCachedPreviewer.cache.put(
                "https://duckduckgo.com/",
                UrlPreviewState.Loaded(
                    UrlInfoItem(
                        url = "https://duckduckgo.com/",
                        image = "https://duckduckgo.com/assets/logo_social-media.png",
                        title = "DuckDuckGo — Privacy, simplified.",
                        description = "The Internet privacy company that empowers you to seamlessly take control of your personal information online, without any tradeoffs.",
                        mimeType = "text/html",
                    ),
                ),
            )

            LocalCache.justConsume(qa, null, false)
        }
    }

    ThemeComparisonRow {
        val background = MaterialTheme.colorScheme.background

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            RenderContentAsMarkdown(
                "This is a test by nostr:npub13rxpxjc6vh65aay2eswlxejsv0f7530sf64c4arydetpckhfjpustsjeaf that tests multiple lines of markdown\n" +
                    "\n" +
                    "nostr:npub13rxpxjc6vh65aay2eswlxejsv0f7530sf64c4arydetpckhfjpustsjeaf\n" +
                    "\n" +
                    "here is a [link to somewhere in text](https://duckduckgo.com/) so as to see how it would render and here it is bare-bone https://duckduckgo.com/ as preview just to make sure it is nicely parsed.\n" +
                    "\n ## END \n",
                tags = EmptyTagList,
                canPreview = true,
                quotesLeft = 2,
                backgroundColor =
                    remember {
                        mutableStateOf(background)
                    },
                callbackUri = PREVIEW_CALLBACK_URI,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Preview
@Composable
fun RenderContentAsMarkdownNotePreview() {
    val accountViewModel = mockAccountViewModel()

    val nav = EmptyNav()

    runBlocking {
        withContext(Dispatchers.IO) {
            val blogPost =
                LongFormContentEvent(
                    id = "e69fa72a221d0b7362f8e63fd6ab84cc6b5e7b505fe49cefe6823a47a6a4b583",
                    pubKey = "88cc134b1a65f54ef48acc1df3665063d3ea45f04eab8af4646e561c5ae99079",
                    createdAt = 1718785725,
                    tags = arrayOf(arrayOf("d", "1718785686085"), arrayOf("title", "Test again"), arrayOf("summary", "Testing...."), arrayOf("t", "test"), arrayOf("t", "testing"), arrayOf("published_at", "1718785712"), arrayOf("alt", "This is a long form article, you can read it in https://habla.news/a/naddr1qvzqqqr4gupzpzxvzd935e04fm6g4nqa7dn9qc7nafzlqn4t3t6xgmjkr3dwnyreqqxnzde38qmnsdfk8qmrqwp405km0k"), arrayOf("p", "88cc134b1a65f54ef48acc1df3665063d3ea45f04eab8af4646e561c5ae99079"), arrayOf("e", "ab002ebdf49a06f9d94959d955c419964403339fde473ebd144321574a105811", "", "mention"), arrayOf("e", "466e2c6e5a59042b44799899885edd112074141e49cc50f9e2ead06f43bfc424", "", "mention"), arrayOf("e", "d6236a9108896ccf7882ce52cd8c53466d2c1018e7e85190f28310aa3dd5b554", "", "mention"), arrayOf("e", "c1896afdf528e2a62ab0fc15b8e0320ef58f10685363a9c6b2d549b79102f446", "", "mention")),
                    content = "lets try this out nostr:note14vqza005ngr0nk2ft8v4t3qejezqxvulmerna0g5gvs4wjsstqgsmeupgy and nostr:note1gehzcmj6tyzzk3renzvcshkazys8g9q7f8x9p70zatgx7salcsjqax60pz or nostr:note16c3k4ygg39kv77yzeefvmrzngekjcyqcul59ry8jsvg250w4k42qk9d4dw or nostr:note1cxyk4l049r32v24sls2m3cpjpm6c7yrg2d36n34j64ym0ygz73rqzplrn7 and some users nostr:npub13rxpxjc6vh65aay2eswlxejsv0f7530sf64c4arydetpckhfjpustsjeaf  and nostr:npub13rxpxjc6vh65aay2eswlxejsv0f7530sf64c4arydetpckhfjpustsjeaf",
                    sig = "4c85e0eb0c46c5e3023431ad4ed8efa0abd66447ff757d246154e2349ac01ae0f88f213d02efa0a77f307f305d4a608c785ae1ca080c01cd3a9e7b8dffea6f9c",
                )

            LocalCache.justConsume(blogPost, null, false)
        }
    }

    ThemeComparisonRow {
        val background = MaterialTheme.colorScheme.background

        RenderContentAsMarkdown(
            "here is a mention of another article nostr:naddr1qvzqqqr4gupzpzxvzd935e04fm6g4nqa7dn9qc7nafzlqn4t3t6xgmjkr3dwnyreqqxnzde38qmnsdfk8qmrqwp405km0k\n" +
                "\n" +
                "## Images\n" +
                "\n" +
                "![landscape](https://primal.b-cdn.net/media-cache?s=o&a=1&u=https%3A%2F%2Fm.primal.net%2FHUmC.jpg)\n" +
                "\n" +
                "and here is one in the middle of the text ![rose](https://primal.b-cdn.net/media-cache?s=o&a=1&u=https%3A%2F%2Fm.primal.net%2FHUmM.jpg) that continues right after. Just to see how this will render\n" +
                "\n",
            tags = EmptyTagList,
            canPreview = true,
            quotesLeft = 2,
            backgroundColor =
                remember {
                    mutableStateOf(background)
                },
            callbackUri = PREVIEW_CALLBACK_URI,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
