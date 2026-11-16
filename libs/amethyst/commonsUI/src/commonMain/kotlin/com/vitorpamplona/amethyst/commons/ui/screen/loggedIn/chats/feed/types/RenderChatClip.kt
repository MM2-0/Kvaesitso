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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.ui.StreamSystemCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_clip_created_a_clip
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CrossfadeToDisplayComment
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.platform.VideoView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip53LiveActivities.clip.LiveActivitiesClipEvent

@Composable
fun RenderChatClip(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val clip = baseNote.event as? LiveActivitiesClipEvent ?: return

    val videoUrl = remember(clip) { clip.videoUrl() }
    val title = remember(clip) { clip.title() }
    val authorHex = remember(clip) { clip.pubKey }

    val accent = MaterialTheme.colorScheme.primary
    val accentAlpha = 0.12f

    StreamSystemCard(accent = accent, accentAlpha = accentAlpha) {
        val backgroundColor = remember(accent) { mutableStateOf(accent.copy(alpha = accentAlpha)) }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserPicture(authorHex, Size20dp, Modifier, accountViewModel, nav)
                Spacer(StdHorzSpacer)
                LoadUser(baseUserHex = authorHex) { user ->
                    if (user != null) {
                        UsernameDisplay(
                            baseUser = user,
                            fontWeight = FontWeight.Bold,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
                Spacer(StdHorzSpacer)
                Text(
                    text = stringRes(Res.string.chat_clip_created_a_clip),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (!title.isNullOrBlank()) {
                Text(text = title)
            }

            val caption = clip.content
            if (caption.isNotBlank()) {
                CrossfadeToDisplayComment(
                    comment = caption,
                    backgroundColor = backgroundColor,
                    nav = nav,
                    accountViewModel = accountViewModel,
                )
            }

            if (!videoUrl.isNullOrBlank()) {
                VideoView(
                    videoUri = videoUrl,
                    mimeType = null,
                    title = title,
                    roundedCorner = true,
                    contentScale = ContentScale.FillWidth,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
