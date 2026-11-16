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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.gallery.GalleryThumbnail
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.profileGallery.ProfileGalleryEntryEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent

@Composable
fun GalleryCardCompose(
    baseNote: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    WatchNoteEvent(baseNote = baseNote, accountViewModel = accountViewModel, nav, shortPreview = true) {
        CheckHiddenFeedWatchBlockAndReport(
            note = baseNote,
            modifier = modifier,
            ignoreAllBlocksAndReports = false,
            showHiddenWarning = true,
            accountViewModel = accountViewModel,
            nav = nav,
        ) { _ ->
            val galleryEvent = baseNote.event

            val redirectToEventId =
                if (galleryEvent is ProfileGalleryEntryEvent) {
                    galleryEvent.fromEvent()
                } else {
                    null
                }

            if (redirectToEventId != null) {
                LoadNote(baseNoteHex = redirectToEventId) { baseSourceNote ->
                    if (baseSourceNote != null) {
                        val sourceNote by observeNote(baseSourceNote, accountViewModel)
                        RedirectableGalleryCard(
                            galleryNote = baseNote,
                            sourceNote = sourceNote.note,
                            modifier = modifier,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    } else {
                        RedirectableGalleryCard(
                            galleryNote = baseNote,
                            sourceNote = baseNote,
                            modifier = modifier,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            } else {
                RedirectableGalleryCard(
                    galleryNote = baseNote,
                    sourceNote = baseNote,
                    modifier = modifier,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
fun RedirectableGalleryCard(
    galleryNote: Note,
    sourceNote: Note,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    QuickActionGallery(baseNote = galleryNote, accountViewModel = accountViewModel) { showPopup ->
        ClickableNote(
            baseNote = sourceNote,
            modifier = modifier,
            accountViewModel = accountViewModel,
            showPopup = showPopup,
            nav = nav,
        ) {
            if (sourceNote != galleryNote) {
                // preloads target note
                EventFinderFilterAssemblerSubscription(sourceNote, accountViewModel)
            }

            SensitivityWarning(
                note = galleryNote,
                accountViewModel = accountViewModel,
            ) {
                GalleryThumbnail(galleryNote, accountViewModel, nav)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ClickableNote(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: INav,
    content: @Composable () -> Unit,
) {
    val updatedModifier =
        remember(baseNote, modifier) {
            modifier
                .combinedClickable(
                    onClick = {
                        val redirectToNote =
                            if (baseNote.event is RepostEvent || baseNote.event is GenericRepostEvent) {
                                baseNote.replyTo?.lastOrNull() ?: baseNote
                            } else {
                                baseNote
                            }
                        routeFor(redirectToNote, accountViewModel.account)?.let { nav.nav(it) }
                    },
                    onLongClick = showPopup,
                )
        }

    Column(modifier = updatedModifier) { content() }
}
