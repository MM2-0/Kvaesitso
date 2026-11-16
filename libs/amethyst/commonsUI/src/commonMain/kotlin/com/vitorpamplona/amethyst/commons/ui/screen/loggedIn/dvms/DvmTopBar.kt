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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.dvms

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.MyExtensibleTopAppBar
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.SimpleImage35Modifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip89AppHandlers.definition.AppDefinitionEvent

@Composable
fun DvmTopBar(
    appDefinitionId: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    MyExtensibleTopAppBar(
        title = {
            LoadNote(baseNoteHex = appDefinitionId) { appDefinitionNote ->
                if (appDefinitionNote != null) {
                    val card = observeAppDefinition(appDefinitionNote, accountViewModel)

                    card.cover?.let {
                        MyAsyncImage(
                            imageUrl = it,
                            contentDescription = card.name,
                            contentScale = ContentScale.Crop,
                            mainImageModifier = Modifier,
                            loadedImageModifier = SimpleImage35Modifier,
                            accountViewModel = accountViewModel,
                            onLoadingBackground = {
                                appDefinitionNote.author?.let { author ->
                                    BannerImage(author, SimpleImage35Modifier, accountViewModel)
                                }
                            },
                            onError = {
                                appDefinitionNote.author?.let { author ->
                                    BannerImage(author, SimpleImage35Modifier, accountViewModel)
                                }
                            },
                        )
                    } ?: run {
                        appDefinitionNote.author?.let { author ->
                            BannerImage(author, SimpleImage35Modifier, accountViewModel)
                        }
                    }

                    Spacer(modifier = DoubleHorzSpacer)

                    Text(
                        text = card.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (nav.canPop()) {
                IconButton(onClick = nav::popBack) { ArrowBackIcon() }
            }
        },
        actions = {
            // The route passes the event's hex id, so LoadNote returns a plain Note,
            // not the AddressableNote the toggle needs. Derive the AddressableNote
            // from the loaded AppDefinitionEvent's address() once the event exists.
            LoadNote(baseNoteHex = appDefinitionId) { appDefinitionNote ->
                if (appDefinitionNote != null) {
                    val addressableNote by
                        observeNoteAndMap(appDefinitionNote, accountViewModel) { note ->
                            (note.event as? AppDefinitionEvent)?.let {
                                LocalCache.getOrCreateAddressableNote(it.address())
                            }
                        }
                    addressableNote?.let { target ->
                        FavoriteAlgoFeedToggle(
                            appDefinitionNote = target,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        },
    )
}
