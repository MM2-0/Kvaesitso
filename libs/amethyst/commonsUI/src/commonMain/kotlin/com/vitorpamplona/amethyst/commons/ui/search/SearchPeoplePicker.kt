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
package com.vitorpamplona.amethyst.commons.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.search_group_filtered_on_results
import com.vitorpamplona.amethyst.commons.resources.search_people_shared_id
import com.vitorpamplona.amethyst.commons.search.KindCandidate
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.collections.immutable.ImmutableList

/** One row the people picker offers: who they are, and how to tell them from a namesake. */
@Immutable
data class PersonCandidate(
    val pubkeyHex: String,
    val name: String,
    val subtitle: String? = null,
    val pictureUrl: String? = null,
)

/** One row the group picker offers: what the group is called, and where it lives. */
@Immutable
data class GroupCandidate(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    /**
     * More than one group on the reader's relays carries this id. A search filters on the id
     * alone, so a pick here really does return all of them — the row says so rather than
     * pretending the pick is precise.
     */
    val ambiguous: Boolean = false,
)

/** The people picker that opens under a half-written `from:`/`to:` token. */
@Composable
fun SearchPeoplePicker(
    candidates: ImmutableList<PersonCandidate>,
    highlighted: Int,
    onPick: (PersonCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    LazyColumn(modifier.heightIn(max = 280.dp)) {
        itemsIndexed(candidates, key = { _, candidate -> candidate.pubkeyHex }) { index, candidate ->
            PickerRow(
                highlighted = index == highlighted,
                onClick = { onPick(candidate) },
                leading = {
                    UserAvatar(
                        userHex = candidate.pubkeyHex,
                        pictureUrl = candidate.pictureUrl,
                        size = 28.dp,
                        contentDescription = candidate.name,
                    )
                },
                title = candidate.name,
                subtitle = candidate.subtitle,
            )
        }
    }
}

/** The group picker that opens under a half-written `group:` token. */
@Composable
fun SearchGroupPicker(
    candidates: ImmutableList<GroupCandidate>,
    highlighted: Int,
    onPick: (GroupCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    LazyColumn(modifier.heightIn(max = 280.dp)) {
        itemsIndexed(candidates, key = { _, candidate -> candidate.id }) { index, candidate ->
            PickerRow(
                highlighted = index == highlighted,
                onClick = { onPick(candidate) },
                title = candidate.name,
                subtitle = candidate.subtitle,
                trailing = if (candidate.ambiguous) stringRes(Res.string.search_people_shared_id) else null,
            )
        }
    }
}

/**
 * The kind picker that opens under a half-written `kind:` token.
 *
 * The only picker whose rows `commons` can fill in itself: "which kinds are there" is a constant
 * in [KindRegistry], not an account-scoped question, so there is no query callback to answer.
 */
@Composable
fun SearchKindPicker(
    candidates: ImmutableList<KindCandidate>,
    highlighted: Int,
    onPick: (KindCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidates.isEmpty()) return
    LazyColumn(modifier.heightIn(max = 280.dp)) {
        itemsIndexed(candidates, key = { _, candidate -> candidate.alias }) { index, candidate ->
            PickerRow(
                highlighted = index == highlighted,
                onClick = { onPick(candidate) },
                title = candidate.alias,
                // What the token will actually ask for. `kind:video` is four kinds and
                // `kind:21` is one; a reader picking between them deserves to see which.
                subtitle =
                    if (candidate.isPseudo) {
                        stringRes(Res.string.search_group_filtered_on_results)
                    } else {
                        candidate.kinds.joinToString(", ")
                    },
            )
        }
    }
}

@Composable
private fun PickerRow(
    highlighted: Boolean,
    onClick: () -> Unit,
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)? = null,
    trailing: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (highlighted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
