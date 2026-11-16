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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.sno_avatar_default
import com.vitorpamplona.amethyst.commons.resources.sno_avatar_default_details
import com.vitorpamplona.amethyst.commons.resources.sno_avatar_title
import com.vitorpamplona.amethyst.commons.resources.sno_avatar_unpaid
import com.vitorpamplona.amethyst.commons.resources.sno_object_details
import com.vitorpamplona.amethyst.commons.resources.sno_object_scale
import com.vitorpamplona.amethyst.commons.sno.SnoDefaultAvatar
import com.vitorpamplona.amethyst.commons.sno.ui.SnoThumbnail
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.quartz.cyberspace.CyberspaceScale
import com.vitorpamplona.quartz.cyberspace.deck0003Sno.SnoPayload
import org.jetbrains.compose.resources.stringResource

private val AVATAR_THUMBNAIL_SIZE = 96.dp

/**
 * A cyberspace avatar (`CYBERSPACE_V2.md` §8.10 kind 11333), which is an SNO
 * payload in a replaceable container.
 */
@Composable
fun SnoAvatarCard(
    payload: SnoPayload,
    eventId: String,
    name: String?,
    onClick: (() -> Unit)? = null,
) {
    val modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp)

    Row(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnoThumbnail(
            payload = payload,
            eventId = eventId,
            size = AVATAR_THUMBNAIL_SIZE,
            contentDescription = name ?: payload.name.ifBlank { null },
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = name ?: payload.name.ifBlank { stringResource(Res.string.sno_avatar_title) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.sno_object_details, payload.vertexCount, payload.faceCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // An avatar's `unit` is what it was priced on (§8.10 pays for reach
            // as well as detail), so it is the one card where the scale is not
            // only informative but the reason the work came out as it did.
            Text(
                text = stringResource(Res.string.sno_object_scale, CyberspaceScale.describeUnit(payload.unit)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The avatar of someone who has not published a shape.
 *
 * §8.10 makes a `kind 11333` with empty content the default avatar: no
 * geometry, no work owed, and what everyone is until they adopt something.
 * Drawing nothing for it is correct and unhelpful — the note disappears out of
 * the feed, which reads as a fault — so it is drawn as the wireframe
 * icosahedron the reference puts in its place, which is also a shape nobody
 * could mistake for one somebody made.
 */
@Composable
fun SnoAvatarDefaultCard(eventId: String) {
    Row(
        modifier = MaterialTheme.colorScheme.replyModifier.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnoThumbnail(
            payload = SnoDefaultAvatar.payload,
            eventId = eventId,
            size = AVATAR_THUMBNAIL_SIZE,
            contentDescription = stringResource(Res.string.sno_avatar_default),
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.sno_avatar_default),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.sno_avatar_default_details),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What stands in for an avatar a client may not draw.
 *
 * `CYBERSPACE_V2.md` §8.10 is normative here: "a client MUST NOT draw an avatar
 * event that is not paid, or that carries content it cannot read". An avatar is
 * the one thing in cyberspace that lands on other people's screens whether they
 * asked for it or not, so its size and its detail are paid for in proof of work
 * on the event that publishes it, and a client draws nothing it cannot verify
 * has paid.
 */
@Composable
fun SnoAvatarUnpaidCard() {
    Column(MaterialTheme.colorScheme.replyModifier.padding(10.dp)) {
        Text(
            text = stringResource(Res.string.sno_avatar_unpaid),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}
