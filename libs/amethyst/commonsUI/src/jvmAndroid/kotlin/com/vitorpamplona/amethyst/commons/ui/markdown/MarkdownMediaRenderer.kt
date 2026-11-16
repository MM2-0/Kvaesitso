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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.ui.MediaRenderer
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.vitorpamplona.amethyst.commons.cartridges.CartLinks
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.uri.fragmentHashtagOrNull
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.BleedingMedia
import com.vitorpamplona.amethyst.commons.ui.components.DisplayFullNote
import com.vitorpamplona.amethyst.commons.ui.components.DisplayUser
import com.vitorpamplona.amethyst.commons.ui.components.LoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.richtext.HashtagIcon
import com.vitorpamplona.amethyst.commons.ui.richtext.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.commons.ui.theme.Font17SP
import com.vitorpamplona.amethyst.commons.ui.theme.Size17Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.LoadedBechLink
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NRelay
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag

class MarkdownMediaRenderer(
    val startOfText: String,
    val imetaByUrl: Map<String, IMetaTag>,
    val canPreview: Boolean,
    val quotesLeft: Int,
    val backgroundColor: MutableState<Color>,
    val callbackUri: String? = null,
    val accountViewModel: AccountViewModel,
    val nav: INav,
    /** Rendering for a share-as-image capture, which shows link previews whatever the settings say. */
    val forCapture: Boolean = false,
    /** How far media on a top-level line may run past the column; see [LocalMediaBleed]. */
    val mediaBleed: Dp = 0.dp,
    /** The URLs on the post's top-level lines ([markdownTopLevelUrls]): the only media that bleeds. */
    val topLevelUrls: Set<String> = emptySet(),
) : MediaRenderer {
    private fun bleedFor(uri: String): Dp = if (uri in topLevelUrls) mediaBleed else 0.dp

    val parser = RichTextParser()

    private val cartImetas by lazy { CartLinks.tagsToKeep(imetaByUrl) }

    override fun shouldRenderLinkPreview(
        title: String?,
        uri: String,
    ): Boolean =
        if (fragmentHashtagOrNull(uri) != null) {
            // claims [text](#hashtag) links so renderLinkPreview can retarget them to the hashtag feed
            true
        } else if (canPreview && uri.startsWith("http")) {
            title.isNullOrBlank() || title == uri
        } else {
            false
        }

    override fun shouldSanitizeUriLabel(): Boolean = true

    override fun sanitizeUriLabel(label: String): String = label.filterNot { it == '#' || it == '@' }

    override fun renderImage(
        title: String?,
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        if (canPreview) {
            val content =
                parser.createMediaContent(
                    fullUrl = uri,
                    eventTags = imetaByUrl,
                    description = title?.ifEmpty { null } ?: startOfText,
                    cartImetas = cartImetas,
                ) ?: MediaUrlImage(url = uri, description = title?.ifEmpty { null } ?: startOfText)

            renderInlineFullWidth(richTextStringBuilder) {
                RenderMedia(content, bleedFor(uri))
            }
        } else {
            renderAsCompleteLink(title ?: uri, uri, richTextStringBuilder)
        }
    }

    @Composable
    private fun RenderMedia(
        content: BaseMediaContent,
        bleed: Dp,
    ) {
        ProvideMediaBleed(bleed) {
            BleedingMedia(enabled = content !is MediaUrlPdf) { roundedCorner ->
                ZoomableContentView(
                    content = content,
                    roundedCorner = roundedCorner,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }

    override fun renderLinkPreview(
        title: String?,
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        val fragmentHashtag = fragmentHashtagOrNull(uri)
        if (fragmentHashtag != null) {
            renderAsCompleteLink(title ?: uri, "nostr:hashtag?id=$fragmentHashtag", richTextStringBuilder)
            return
        }

        val content = parser.createMediaContent(uri, imetaByUrl, startOfText, callbackUri, cartImetas = cartImetas)

        if (canPreview) {
            if (content != null) {
                renderInlineFullWidth(richTextStringBuilder) {
                    RenderMedia(content, bleedFor(uri))
                }
            } else {
                if (!forCapture && !accountViewModel.settings.showUrlPreview()) {
                    renderAsCompleteLink(title ?: uri, uri, richTextStringBuilder)
                } else {
                    renderInlineFullWidth(richTextStringBuilder) {
                        ProvideMediaBleed(bleedFor(uri)) {
                            LoadUrlPreview(uri, title ?: uri, callbackUri, accountViewModel, nav)
                        }
                    }
                }
            }
        } else {
            renderAsCompleteLink(title ?: uri, uri, richTextStringBuilder)
        }
    }

    override fun renderNostrUri(
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        val loadedLink = accountViewModel.bechLinkCache.cachedOrLoad(uri)

        val baseNote = loadedLink?.baseNote

        if (canPreview && quotesLeft > 0 && baseNote != null) {
            renderInlineFullWidth(richTextStringBuilder) {
                Row {
                    DisplayFullNote(
                        note = baseNote,
                        extraChars = loadedLink.nip19.additionalChars?.ifBlank { null },
                        quotesLeft = quotesLeft,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        } else if (loadedLink?.nip19 != null) {
            when (val entity = loadedLink.nip19.entity) {
                is NPub -> renderObservableUser(entity.hex, loadedLink.nip19.nip19raw, richTextStringBuilder)
                is NProfile -> renderObservableUser(entity.hex, loadedLink.nip19.nip19raw, richTextStringBuilder)
                is NNote -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is NEvent -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is NEmbed -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is NAddress -> renderObservableShortNoteUri(loadedLink, uri, richTextStringBuilder)
                is NRelay -> renderShortNostrURI(uri, richTextStringBuilder)
                is NSec -> renderShortNostrURI(uri, richTextStringBuilder)
                else -> renderShortNostrURI(uri, richTextStringBuilder)
            }
        } else {
            renderShortNostrURI(uri, richTextStringBuilder)
        }
    }

    override fun renderHashtag(
        tag: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        val tagWithoutHash = tag.removePrefix("#")
        renderAsCompleteLink(tag, "nostr:hashtag?id=$tagWithoutHash", richTextStringBuilder)

        val hashtagIcon: HashtagIcon? = checkForHashtagWithIcon(tagWithoutHash)
        if (hashtagIcon != null) {
            renderInline(richTextStringBuilder) {
                Box(Size17Modifier) {
                    Icon(
                        imageVector = hashtagIcon.icon,
                        contentDescription = hashtagIcon.description,
                        tint = Color.Unspecified,
                        modifier = hashtagIcon.modifier,
                    )
                }
            }
        }
    }

    fun renderObservableUser(
        userHex: String,
        nip19: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        renderInline(richTextStringBuilder) {
            DisplayUser(userHex, nip19, null, accountViewModel, nav)
        }
    }

    fun renderObservableShortNoteUri(
        loadedLink: LoadedBechLink,
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        loadedLink.baseNote?.let { renderNoteObserver(it, richTextStringBuilder) }
        renderShortNostrURI(uri, richTextStringBuilder)
    }

    private fun renderNoteObserver(
        baseNote: Note,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        renderInvisible(richTextStringBuilder) {
            // Preloads note if not loaded yet.
            EventFinderFilterAssemblerSubscription(baseNote, accountViewModel)
        }
    }

    private fun renderShortNostrURI(
        uri: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        val nip19 = "@" + uri.removePrefix("nostr:")

        renderAsCompleteLink(
            title =
                if (nip19.length > 16) {
                    nip19.replaceRange(8, nip19.length - 8, ":")
                } else {
                    nip19
                },
            destination = uri,
            richTextStringBuilder = richTextStringBuilder,
        )
    }

    private fun renderInvisible(
        richTextStringBuilder: RichTextString.Builder,
        innerComposable: @Composable () -> Unit,
    ) {
        richTextStringBuilder.appendInlineContent(
            content =
                InlineContent(
                    initialSize = {
                        IntSize(0.dp.roundToPx(), 0.dp.roundToPx())
                    },
                ) {
                    innerComposable()
                },
        )
    }

    private fun renderInline(
        richTextStringBuilder: RichTextString.Builder,
        innerComposable: @Composable () -> Unit,
    ) {
        richTextStringBuilder.appendInlineContent(
            content =
                InlineContent(
                    initialSize = {
                        IntSize(Font17SP.roundToPx(), Font17SP.roundToPx())
                    },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ) {
                    innerComposable()
                },
        )
    }

    private fun renderInlineFullWidth(
        richTextStringBuilder: RichTextString.Builder,
        innerComposable: @Composable () -> Unit,
    ) {
        richTextStringBuilder.appendInlineContentFullWidth(
            content =
                InlineContent(
                    initialSize = {
                        IntSize(Font17SP.roundToPx(), Font17SP.roundToPx())
                    },
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ) {
                    innerComposable()
                },
        )
    }

    private fun renderAsCompleteLink(
        title: String,
        destination: String,
        richTextStringBuilder: RichTextString.Builder,
    ) {
        richTextStringBuilder.pushFormat(
            RichTextString.Format.Link(destination = destination),
        )
        richTextStringBuilder.append(title)
        richTextStringBuilder.pop()
    }
}
