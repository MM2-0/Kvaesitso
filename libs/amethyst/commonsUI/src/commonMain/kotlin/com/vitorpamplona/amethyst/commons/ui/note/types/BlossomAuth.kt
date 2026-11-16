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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_any_file
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_any_server
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_delete
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_event_title
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_file
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_get
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_list
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_media
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_server
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_unknown
import com.vitorpamplona.amethyst.commons.resources.blossom_auth_upload
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent

/**
 * Blossom kind 24242: a token that lets whoever holds it act on the user's media server. What the user
 * agrees to is the verb, the file and where it works, so the card says those three; the event's
 * content is a free-text label the app wrote and its tags are hashes, neither of which a person reads.
 * It used to render as a short note ("Upload image #upload").
 */
@Composable
fun RenderBlossomAuth(baseNote: Note) {
    val event = baseNote.event as? BlossomAuthorizationEvent ?: return
    BlossomAuthCard(type = event.type(), hashes = event.hashes(), servers = event.servers())
}

@Composable
fun BlossomAuthCard(
    type: String?,
    hashes: List<String>,
    servers: List<String>,
) {
    // A delete is the one a stray token hurts: unscoped, it erases the file on every server that holds
    // it. An unscoped upload of a known hash only lets someone else store the same file.
    val destructive = type == "delete"
    KeyEventCard(title = stringRes(Res.string.blossom_auth_event_title), explainer = null) {
        Text(
            text =
                when (type) {
                    "upload" -> stringRes(Res.string.blossom_auth_upload)
                    "delete" -> stringRes(Res.string.blossom_auth_delete)
                    "list" -> stringRes(Res.string.blossom_auth_list)
                    "get" -> stringRes(Res.string.blossom_auth_get)
                    "media" -> stringRes(Res.string.blossom_auth_media)
                    else -> stringRes(Res.string.blossom_auth_unknown, type ?: "?")
                },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (type != "list") {
            Text(
                text =
                    if (hashes.isEmpty()) {
                        stringRes(Res.string.blossom_auth_any_file)
                    } else {
                        stringRes(Res.string.blossom_auth_file, hashes.joinToString(", ") { it.take(8) + "…" + it.takeLast(4) })
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (hashes.isEmpty()) null else FontFamily.Monospace,
                color = if (hashes.isEmpty() && destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                if (servers.isEmpty()) {
                    stringRes(Res.string.blossom_auth_any_server)
                } else {
                    stringRes(Res.string.blossom_auth_server, servers.joinToString(", "))
                },
            style = MaterialTheme.typography.bodyMedium,
            color = if (servers.isEmpty() && destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
