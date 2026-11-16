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
package com.vitorpamplona.amethyst.commons.ui.richtext

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * What a front end plugs into the shared rich-text viewer: the [RichTextSegmentRenderer] for its
 * platform leaves (zoomable media, LaTeX, payments, link previews, Blossom links) and its markdown
 * renderer. The shared viewer parses, lays out paragraphs and renders text, emoji, hashtags and
 * mentions itself, and asks this for everything else.
 *
 * A front end installs its own through [LocalRichTextPlatform] at its composition root (Android
 * does so in its theme). Without one, [Plain] renders every platform segment as text, which is
 * what previews and a front end still wiring its leaves get.
 */
@Stable
interface RichTextPlatform {
    /** The segment renderer for one viewer call; it may close over every argument. */
    fun segmentRenderer(
        accountViewModel: AccountViewModel,
        nav: INav,
        backgroundColor: MutableState<Color>,
        callbackUri: String?,
        canPreview: Boolean,
    ): RichTextSegmentRenderer

    /** Renders [content] that the parser recognised as markdown. */
    @Composable
    fun Markdown(
        content: String,
        tags: ImmutableListOfLists<String>,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav,
    )

    /**
     * Renders a decoded secret message (text hidden in an emoji's variation selectors) with the
     * platform's leaves, the way the viewer renders a note body.
     */
    @Composable
    fun SecretMessage(
        content: RichTextViewerState,
        callbackUri: String?,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    )

    /** Text for every platform segment, and markdown shown as its source. */
    object Plain : RichTextPlatform {
        override fun segmentRenderer(
            accountViewModel: AccountViewModel,
            nav: INav,
            backgroundColor: MutableState<Color>,
            callbackUri: String?,
            canPreview: Boolean,
        ): RichTextSegmentRenderer = PlainTextSegmentRenderer

        @Composable
        override fun Markdown(
            content: String,
            tags: ImmutableListOfLists<String>,
            canPreview: Boolean,
            quotesLeft: Int,
            backgroundColor: MutableState<Color>,
            callbackUri: String?,
            accountViewModel: AccountViewModel,
            nav: INav,
        ) = Text(content)

        @Composable
        override fun SecretMessage(
            content: RichTextViewerState,
            callbackUri: String?,
            quotesLeft: Int,
            backgroundColor: MutableState<Color>,
            accountViewModel: AccountViewModel,
            nav: INav,
        ) = Text(content.paragraphs.joinToString("\n") { paragraph -> paragraph.words.joinToString(" ") { it.segmentText } })
    }
}

/** The front end's [RichTextPlatform]. Static: it is set once at the root and never changes. */
val LocalRichTextPlatform = staticCompositionLocalOf<RichTextPlatform> { RichTextPlatform.Plain }
