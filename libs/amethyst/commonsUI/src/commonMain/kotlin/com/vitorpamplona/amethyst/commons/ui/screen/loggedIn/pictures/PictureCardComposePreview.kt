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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures.PictureCardCaption
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.pictures.PictureCardCompose
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val SAMPLE_PUBKEY_FRAGMENT =
    "\"pubkey\":\"460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c\","

private const val SAMPLE_KIND_20_FRAGMENT = "\"kind\":20,"

private const val SAMPLE_PICTURE_EVENT_JSON =
    "{\"id\":\"a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2\"," +
        SAMPLE_PUBKEY_FRAGMENT +
        "\"created_at\":1708695717," +
        SAMPLE_KIND_20_FRAGMENT +
        "\"tags\":[[\"title\",\"Sunset at the Beach\"]," +
        "[\"imeta\",\"url https://image.nostr.build/sample-sunset.jpg\"," +
        "\"m image/jpeg\",\"dim 1200x800\",\"alt A beautiful sunset over the ocean\"," +
        "\"blurhash LKO2:N%2Tw=w]~RBVZRi};RPxuwH\"]]," +
        "\"content\":\"Caught this amazing sunset while walking along the shore. The colors were absolutely breathtaking, painting the sky in shades of orange and purple.\"," +
        "\"sig\":\"a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2\"}"

private const val SAMPLE_PICTURE_EVENT_NO_TITLE_JSON =
    "{\"id\":\"b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3\"," +
        SAMPLE_PUBKEY_FRAGMENT +
        "\"created_at\":1708695000," +
        SAMPLE_KIND_20_FRAGMENT +
        "\"tags\":[[\"imeta\",\"url https://image.nostr.build/sample-mountain.jpg\"," +
        "\"m image/jpeg\",\"dim 1080x1080\",\"alt Mountain landscape\"]]," +
        "\"content\":\"Mountain vibes today. Fresh air and clear skies.\"," +
        "\"sig\":\"b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3\"}"

private const val SAMPLE_MULTI_IMAGE_EVENT_JSON =
    "{\"id\":\"c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4\"," +
        SAMPLE_PUBKEY_FRAGMENT +
        "\"created_at\":1708694000," +
        SAMPLE_KIND_20_FRAGMENT +
        "\"tags\":[[\"title\",\"Travel Photos\"]," +
        "[\"imeta\",\"url https://image.nostr.build/sample-travel1.jpg\"," +
        "\"m image/jpeg\",\"dim 800x600\",\"alt City street\"]," +
        "[\"imeta\",\"url https://image.nostr.build/sample-travel2.jpg\"," +
        "\"m image/jpeg\",\"dim 800x600\",\"alt Market square\"]]," +
        "\"content\":\"Exploring the old town district. Every corner has a story to tell. The architecture here is incredible and the food is even better.\"," +
        "\"sig\":\"c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4\"}"

@Preview
@Composable
private fun PictureCardComposePreview() {
    val event = Event.fromJson(SAMPLE_PICTURE_EVENT_JSON) as PictureEvent
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
        }
    }

    LoadNote(
        baseNoteHex = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
    ) { baseNote ->
        ThemeComparisonColumn {
            if (baseNote != null) {
                PictureCardCompose(
                    baseNote = baseNote,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PictureCardComposeNoTitlePreview() {
    val event = Event.fromJson(SAMPLE_PICTURE_EVENT_NO_TITLE_JSON) as PictureEvent
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
        }
    }

    LoadNote(
        baseNoteHex = "b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3",
    ) { baseNote ->
        ThemeComparisonColumn {
            if (baseNote != null) {
                PictureCardCompose(
                    baseNote = baseNote,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PictureCardComposeMultiImagePreview() {
    val event = Event.fromJson(SAMPLE_MULTI_IMAGE_EVENT_JSON) as PictureEvent
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()

    runBlocking {
        withContext(Dispatchers.IO) {
            LocalCache.justConsume(event, null, false)
        }
    }

    LoadNote(
        baseNoteHex = "c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4",
    ) { baseNote ->
        ThemeComparisonColumn {
            if (baseNote != null) {
                PictureCardCompose(
                    baseNote = baseNote,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PictureCardCaptionWithTitlePreview() {
    val event = Event.fromJson(SAMPLE_PICTURE_EVENT_JSON) as PictureEvent

    ThemeComparisonColumn {
        PictureCardCaption(event)
    }
}

@Preview
@Composable
private fun PictureCardCaptionNoTitlePreview() {
    val event = Event.fromJson(SAMPLE_PICTURE_EVENT_NO_TITLE_JSON) as PictureEvent

    ThemeComparisonColumn {
        PictureCardCaption(event)
    }
}

@Preview
@Composable
private fun PictureCardCaptionLongContentPreview() {
    ThemeComparisonColumn {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = "A Very Long Title That Should Be Truncated When It Exceeds The Available Width",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    "This is a long description that spans multiple lines to test the three-line " +
                        "truncation behavior. The text should be cut off after three lines with an " +
                        "ellipsis. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do " +
                        "eiusmod tempor incididunt ut labore et dolore magna aliqua.",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
