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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

private val PictureToRowGap = 10.dp

/**
 * The header of a repost in the full-width layouts: two first rows, the booster's and the reposted
 * note's, beside the author column's repost picture ([RepostNoteAuthorPicture]). The picture gets a
 * square as tall as the two rows, so its booster sits by the first row and its author by the second
 * at any font size.
 */
@Composable
fun FullWidthRepostHeader(
    repost: Note,
    reposted: Note,
    isPinned: Boolean,
    editState: State<GenericLoadable<EditState>>,
    accountViewModel: AccountViewModel,
    nav: INav,
    moreOptions: (@Composable () -> Unit)? = null,
) {
    val repostedEditState = observeEdits(baseNote = reposted, accountViewModel = accountViewModel)

    Layout(
        contents =
            listOf(
                // Drawn at its 55dp size, then fitted into the square the rows give it.
                { RepostNoteAuthorPicture(repost, reposted, Size55dp, accountViewModel, nav) },
                { FirstUserInfoRow(repost, false, isPinned, editState, accountViewModel, nav, moreOptions) },
                { FirstUserInfoRow(reposted, false, false, repostedEditState, accountViewModel, nav) },
            ),
    ) { (picture, boosterRow, authorRow), constraints ->
        // The rows are one line each, so their height does not depend on the width the picture
        // leaves them: size the picture to it before measuring the rows.
        val rows = boosterRow + authorRow
        val side = rows.sumOf { it.minIntrinsicHeight(constraints.maxWidth) }
        val picturePlaceable = picture.firstOrNull()?.measure(Constraints.fixed(side, side))

        val rowX = side + PictureToRowGap.roundToPx()
        val rowConstraints = Constraints(maxWidth = (constraints.maxWidth - rowX).coerceAtLeast(0))
        val placedRows = rows.map { it.measure(rowConstraints) }
        val rowsHeight = placedRows.sumOf { it.height }
        val height = maxOf(rowsHeight, side)

        layout(constraints.maxWidth, height) {
            picturePlaceable?.placeRelative(0, (height - side) / 2)
            var y = (height - rowsHeight) / 2
            placedRows.forEach {
                it.placeRelative(rowX, y)
                y += it.height
            }
        }
    }
}
