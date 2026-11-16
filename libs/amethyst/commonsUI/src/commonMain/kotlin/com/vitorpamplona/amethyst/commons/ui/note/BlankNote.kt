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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.post_not_found
import com.vitorpamplona.amethyst.commons.resources.post_not_found_short
import com.vitorpamplona.amethyst.commons.resources.post_was_flagged_as_inappropriate_by
import com.vitorpamplona.amethyst.commons.resources.post_was_hidden
import com.vitorpamplona.amethyst.commons.resources.post_was_hidden_due_to_too_many_hashtags
import com.vitorpamplona.amethyst.commons.resources.show_anyway
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Composable
@Preview
fun BlankNotePreview() {
    ThemeComparisonColumn { BlankNote() }
}

@Composable
fun BlankNote(
    modifier: Modifier = Modifier,
    shortPreview: Boolean = false,
) {
    Column(modifier = modifier) {
        Row {
            Column {
                Row(
                    modifier =
                        Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 8.dp,
                            top = 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text =
                            if (shortPreview) {
                                stringRes(Res.string.post_not_found_short)
                            } else {
                                stringRes(Res.string.post_not_found)
                            },
                        modifier = Modifier.padding(30.dp),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun HiddenNotePreview() {
    ThemeComparisonColumn(
        toPreview = {
            HiddenNote(
                reports = persistentSetOf(),
                isHiddenAuthor = true,
                accountViewModel = mockAccountViewModel(),
                nav = EmptyNav(),
            ) {}
        },
    )
}

@Composable
@Preview
fun HiddenNoteExcessiveHashtagsPreview() {
    ThemeComparisonColumn(
        toPreview = {
            HiddenNote(
                reports = persistentSetOf(),
                isHiddenAuthor = false,
                hasExcessiveHashtags = true,
                hashtagLimit = 8,
                accountViewModel = mockAccountViewModel(),
                nav = EmptyNav(),
            ) {}
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiddenNote(
    reports: ImmutableSet<Note>,
    isHiddenAuthor: Boolean,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    nav: INav,
    hasExcessiveHashtags: Boolean = false,
    hashtagLimit: Int = 0,
    onClick: () -> Unit,
) {
    val hasReporters = isHiddenAuthor || reports.isNotEmpty()
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp),
            ) {
                if (hasExcessiveHashtags) {
                    Text(
                        text = pluralStringRes(Res.plurals.post_was_hidden_due_to_too_many_hashtags, hashtagLimit, hashtagLimit),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                    )
                }

                if (hasReporters || !hasExcessiveHashtags) {
                    Text(
                        text = stringRes(Res.string.post_was_flagged_as_inappropriate_by),
                        color = Color.Gray,
                        modifier =
                            if (hasExcessiveHashtags) {
                                Modifier.padding(top = 10.dp)
                            } else {
                                Modifier
                            },
                    )
                    FlowRow(modifier = Modifier.padding(top = 10.dp)) {
                        if (isHiddenAuthor) {
                            UserPicture(
                                user = accountViewModel.userProfile(),
                                size = Size35dp,
                                nav = nav,
                                accountViewModel = accountViewModel,
                            )
                        }
                        reports.forEach {
                            NoteAuthorPicture(
                                baseNote = it,
                                size = Size35dp,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                    }
                }

                FilledTonalButton(
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = onClick,
                    shape = ButtonBorder,
                    contentPadding = ButtonPadding,
                ) {
                    Text(text = stringRes(Res.string.show_anyway))
                }
            }
        }
    }
}

@Preview
@Composable
fun HiddenNoteByMePreview() {
    ThemeComparisonColumn(
        toPreview = { HiddenNoteByMe {} },
    )
}

@Composable
fun HiddenNoteByMe(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp),
            ) {
                Text(
                    text = stringRes(Res.string.post_was_hidden),
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )

                FilledTonalButton(
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = onClick,
                    shape = ButtonBorder,
                    contentPadding = ButtonPadding,
                ) {
                    Text(text = stringRes(Res.string.show_anyway))
                }
            }
        }
    }
}
