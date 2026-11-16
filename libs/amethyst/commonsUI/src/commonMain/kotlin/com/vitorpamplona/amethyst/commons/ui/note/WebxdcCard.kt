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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.webxdc_card_kind
import com.vitorpamplona.amethyst.commons.resources.webxdc_start
import com.vitorpamplona.amethyst.commons.resources.webxdc_unverified
import com.vitorpamplona.amethyst.commons.ui.platform.InlineWebxdcSurface
import com.vitorpamplona.amethyst.commons.ui.platform.rememberAppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.runsNappletsInline
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import org.jetbrains.compose.resources.stringResource

/**
 * A webxdc app attached to a post ([postUri], the post's `nostr:` URI, written by [author] when the
 * note knows better than the URI): its name and size, and
 * **Start**, which hands it to the platform's sandbox. The card runs nothing itself. Where there is
 * no sandbox, or the post can't be told apart, it is a link to the file, as any other attachment.
 */
@Composable
fun WebxdcPostCard(
    app: WebxdcApp,
    postUri: String?,
    author: HexKey?,
) {
    val post = remember(postUri) { postOf(postUri) }
    WebxdcCard(app, post?.first, author ?: post?.second)
}

/** The app card for a post whose id and author are at hand (a kind-1063 file event). */
@Composable
fun WebxdcCard(
    app: WebxdcApp,
    postId: HexKey?,
    poster: HexKey?,
) {
    val launcher = rememberAppLauncher()
    val uriHandler = LocalUriHandler.current
    val title = app.title ?: remember(app.url) { app.url.substringBefore('?').substringAfterLast('/') }
    val unverified = stringResource(Res.string.webxdc_unverified)
    val details =
        remember(app, unverified) {
            listOfNotNull(app.sizeBytes?.let(::countToHumanReadableBytes), unverified.takeIf { app.hash == null })
                .joinToString(" · ")
                .ifEmpty { null }
        }
    val stageKey = remember(app, postId, poster) { if (postId != null && poster != null) app.stageKey(poster, postId) else null }
    val start =
        if (launcher.opensNapplets && postId != null && poster != null && stageKey != null) {
            {
                // One run of an app at a time: the note's would write over the window's state.
                InlineNappletRuns.stopApp(stageKey)
                launcher.openWebxdc(app, postId, poster)
            }
        } else {
            null
        }
    val inline = runsNappletsInline

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                .let { if (start == null) it.clickable { uriHandler.openUri(app.url) } else it }
                .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.image, title)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.webxdc_card_kind).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                details?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            stageKey?.let { InlineStopButton(it) }
            start?.let {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = it) { Text(stringResource(Res.string.webxdc_start)) }
            }
        }

        // Played right here in the note, where the platform can; the window is a tap away.
        if (start != null && inline && postId != null && poster != null && stageKey != null) {
            InlineNappletStage(
                coordinate = stageKey,
                title = title,
                icon = app.image,
            ) { instanceId, onStatus, modifier ->
                InlineWebxdcSurface(app, postId, poster, instanceId, onStatus, modifier)
            }
        }
    }
}

/** The post's id and author, from its `nostr:` URI; null when the URI doesn't name both. */
private fun postOf(postUri: String?): Pair<HexKey, HexKey>? =
    when (val entity = Nip19Parser.uriToRoute(postUri)?.entity) {
        is NEvent -> entity.author?.let { entity.hex to it }
        is NAddress -> entity.aTag() to entity.author
        else -> null
    }
