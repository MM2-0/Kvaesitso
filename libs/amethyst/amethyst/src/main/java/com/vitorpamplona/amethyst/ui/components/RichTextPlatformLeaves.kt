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
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.commons.ui.components.ClickablePhone
import com.vitorpamplona.amethyst.commons.ui.components.ClickableRelayUrl
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.CreateClickableText
import com.vitorpamplona.amethyst.commons.ui.components.HashTag
import com.vitorpamplona.amethyst.commons.ui.components.LoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.components.RenderCustomEmoji
import com.vitorpamplona.amethyst.commons.ui.components.RenderRegular
import com.vitorpamplona.amethyst.commons.ui.components.RenderTextParagraph
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Preview
@Composable
fun RenderStrangeNamePreview() {
    Column(modifier = Modifier.padding(10.dp)) {
        RenderRegular(
            "If you want to FreeFrom Official \uD80C\uDD66 stream or download the music from  nostr:npub1sctag667a7np6p6ety2up94pnwwxhd2ep8n8afr2gtr47cwd4ewsvdmmjm at wss://relay.damus.io can you here",
            EmptyTagList,
        ) { paragraph, _, spaceWidth, modifier ->
            RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
                when (word) {
                    is BechSegment -> {
                        Text(
                            "FreeFrom Official \uD80C\uDD66",
                            modifier = Modifier.border(1.dp, Color.Red),
                        )
                    }

                    is RegularTextSegment -> {
                        Text(word.segmentText)
                    }

                    is RelayUrlSegment -> {
                        ClickableRelayUrl(word.segmentText, EmptyNav())
                    }

                    is BlossomUriSegment -> {
                        ClickableRelayUrl(word.segmentText, EmptyNav())
                    }

                    is SchemelessUrlSegment -> {
                        NoProtocolUrlRenderer(word.segmentText)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview() {
    Column(modifier = Modifier.padding(10.dp)) {
        RenderRegular(
            "nostr:npub1e0z776cpe0gllgktjk54fuzv8pdfxmq6smsmh8xd7t8s7n474n9smk0txy but i'm Monthly funding" +
                " 7 other humans vitor@vitorpamplona.com at the moment so spread #test a bit thin, but won't always be the case.",
            EmptyTagList,
        ) { paragraph, state, spaceWidth, modifier ->
            RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
                when (word) {
                    // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
                    // is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
                    is EmojiSegment -> {
                        RenderCustomEmoji(word.segmentText, state)
                    }

                    // is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
                    // is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
                    // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
                    is EmailSegment -> {
                        ClickableEmail(word.segmentText)
                    }

                    is PhoneSegment -> {
                        ClickablePhone(word.segmentText)
                    }

                    is BechSegment -> {
                        CreateClickableText(
                            word.segmentText.substring(0, 10),
                            "",
                            1,
                            route = Route.EventRedirect(word.segmentText),
                            nav = EmptyNav(),
                        )
                    }

                    is HashTagSegment -> {
                        HashTag(word, EmptyNav())
                    }

                    // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
                    // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)
                    is LinkSegment -> {
                        ClickableUrlOrBlossom(word.segmentText, word.segmentText)
                    }

                    is RegularTextSegment -> {
                        Text(word.segmentText)
                    }

                    is SchemelessUrlSegment -> {
                        NoProtocolUrlRenderer(word.segmentText)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview2() {
    RenderRegular(
        "#Amethyst v0.84.1: ncryptsec support (NIP-49)",
        EmptyTagList,
    ) { paragraph, state, spaceWidth, modifier ->
        RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
            when (word) {
                // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
                // is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, accountViewModel)
                is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)

                // is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
                // is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
                // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
                is EmailSegment -> ClickableEmail(word.segmentText)

                is PhoneSegment -> ClickablePhone(word.segmentText)

                // is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
                is HashTagSegment -> HashTag(word, EmptyNav())

                // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
                // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)
                is LinkSegment -> ClickableUrlOrBlossom(word.segmentText, word.segmentText)

                is RegularTextSegment -> Text(word.segmentText)

                is RelayUrlSegment -> ClickableRelayUrl(word.segmentText, EmptyNav())

                is SchemelessUrlSegment -> NoProtocolUrlRenderer(word.segmentText)
            }
        }
    }
}

@Preview
@Composable
fun RenderRegularPreview3() {
    val tags =
        ImmutableListOfLists(
            arrayOf(
                arrayOf("t", "ioメシヨソイゲーム"),
                arrayOf("emoji", "_ri", "https://media.misskeyusercontent.com/emoji/_ri.png"),
                arrayOf("emoji", "petthex_japanesecake", "https://media.misskeyusercontent.com/emoji/petthex_japanesecake.gif"),
                arrayOf("emoji", "ai_nomming", "https://media.misskeyusercontent.com/misskey/f6294900-f678-43cc-bc36-3ee5deeca4c2.gif"),
                arrayOf("proxy", "https://misskey.io/notes/9q0x6gtdysir03qh", "activitypub"),
            ),
        )
    val accountViewModel = mockAccountViewModel()

    RenderRegular(
        "\u200B:_ri:\u200B\u200B:_ri:\u200Bはﾍﾞｲｸﾄﾞﾓﾁｮﾁｮ\u200B:petthex_japanesecake:\u200Bを食べました\u200B:ai_nomming:\u200B\n" +
            "#ioメシヨソイゲーム\n" +
            "https://misskey.io/play/9g3qza4jow",
        tags,
    ) { paragraph, state, spaceWidth, modifier ->
        RenderTextParagraph(paragraph, spaceWidth, modifier) { word ->
            when (word) {
                // is ImageSegment -> ZoomableContentView(word.segmentText, state, accountViewModel)
                is LinkSegment -> LoadUrlPreview(word.segmentText, word.segmentText, null, accountViewModel)

                is EmojiSegment -> RenderCustomEmoji(word.segmentText, state)

                // is InvoiceSegment -> MayBeInvoicePreview(word.segmentText)
                // is WithdrawSegment -> MayBeWithdrawal(word.segmentText)
                // is CashuSegment -> CashuPreview(word.segmentText, accountViewModel)
                is EmailSegment -> ClickableEmail(word.segmentText)

                is PhoneSegment -> ClickablePhone(word.segmentText)

                // is BechSegment -> BechLink(word.segmentText, true, backgroundColor, accountViewModel, nav)
                is HashTagSegment -> HashTag(word, EmptyNav())

                // is HashIndexUserSegment -> TagLink(word, accountViewModel, nav)
                // is HashIndexEventSegment -> TagLink(word, true, backgroundColorState, accountViewModel, nav)

                is RegularTextSegment -> Text(word.segmentText)

                is RelayUrlSegment -> ClickableRelayUrl(word.segmentText, EmptyNav())

                is SchemelessUrlSegment -> NoProtocolUrlRenderer(word.segmentText)
            }
        }
    }
}

@Composable
private fun NoProtocolUrlRenderer(url: String) {
    ClickableUrlOrBlossom(url, "https://$url")
}
