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
package com.vitorpamplona.amethyst.commons.nip23LongContent.ui.article

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.article_author_description
import com.vitorpamplona.amethyst.commons.resources.article_banner_description
import com.vitorpamplona.amethyst.commons.resources.article_reading_time_minutes
import com.vitorpamplona.amethyst.commons.ui.stringRes

@Composable
fun ArticleHeader(
    title: String,
    authorName: String?,
    authorPicture: String?,
    publishedAt: String?,
    readingTimeMinutes: Int?,
    bannerUrl: String?,
    modifier: Modifier = Modifier,
    onAuthorClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Banner image
        if (!bannerUrl.isNullOrBlank() &&
            (bannerUrl.startsWith("https://") || bannerUrl.startsWith("http://"))
        ) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = stringRes(Res.string.article_banner_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(300.dp),
            )
            Spacer(Modifier.height(24.dp))
        }

        // Title
        Text(
            text = title,
            style =
                MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.5).sp,
                ),
        )

        Spacer(Modifier.height(16.dp))

        // Author + metadata row
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!authorPicture.isNullOrBlank() &&
                (authorPicture.startsWith("https://") || authorPicture.startsWith("http://"))
            ) {
                AsyncImage(
                    model = authorPicture,
                    contentDescription = stringRes(Res.string.article_author_description),
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }

            Column {
                if (!authorName.isNullOrBlank()) {
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                val metaParts = mutableListOf<String>()
                readingTimeMinutes?.let { metaParts.add(stringRes(Res.string.article_reading_time_minutes, it)) }
                publishedAt?.let { metaParts.add(it) }

                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
