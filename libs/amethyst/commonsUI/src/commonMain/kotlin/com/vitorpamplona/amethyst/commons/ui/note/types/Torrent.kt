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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.torrent_failure
import com.vitorpamplona.amethyst.commons.resources.torrent_no_apps
import com.vitorpamplona.amethyst.commons.resources.torrent_no_info
import com.vitorpamplona.amethyst.commons.ui.components.ShowMoreButton
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.commons.ui.note.getGradient
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip35Torrents.TorrentEvent
import com.vitorpamplona.quartz.nip35Torrents.tags.FileTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@Preview
@Composable
fun TorrentPreview() {
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()

    val torrent =
        runBlocking {
            withContext(Dispatchers.IO) {
                val torrent =
                    TorrentEvent(
                        id = "e1ab66dd66e6ac4f32119deacf80d7c50787705d343d3bb896c099cf93821757",
                        pubKey = "0aa39e5aef99a000a7bdb0b499158c92bc4aa20fb65931a52d055b5eb6dff738",
                        createdAt = 1724177932,
                        tags =
                            arrayOf(
                                arrayOf("title", "bitcoin-core-27.0"),
                                arrayOf("btih", "2b2d123e5e831b245fb1dc5b8b71f89de4a90d00"),
                                arrayOf("t", "application"),
                                arrayOf("file", "SHA256SUMS", "2842"),
                                arrayOf("file", "SHA256SUMS.asc", "7590"),
                                arrayOf("file", "SHA256SUMS.ots", "573"),
                                arrayOf("file", "bitcoin-27.0-aarch64-linux-gnu-debug.tar.gz", "456216269"),
                                arrayOf("file", "bitcoin-27.0-aarch64-linux-gnu.tar.gz", "46953175"),
                                arrayOf("file", "bitcoin-27.0-arm-linux-gnueabihf-debug.tar.gz", "449092874"),
                                arrayOf("file", "bitcoin-27.0-arm-linux-gnueabihf.tar.gz", "42887090"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin-unsigned.tar.gz", "16417606"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin-unsigned.zip", "16451447"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin.tar.gz", "36433324"),
                                arrayOf("file", "bitcoin-27.0-arm64-apple-darwin.zip", "16217968"),
                                arrayOf("file", "bitcoin-27.0-codesignatures-27.0.tar.gz", "335808"),
                                arrayOf("file", "bitcoin-27.0-powerpc64-linux-gnu-debug.tar.gz", "467821947"),
                                arrayOf("file", "bitcoin-27.0-powerpc64-linux-gnu.tar.gz", "53252604"),
                                arrayOf("file", "bitcoin-27.0-powerpc64le-linux-gnu-debug.tar.gz", "460024501"),
                                arrayOf("file", "bitcoin-27.0-powerpc64le-linux-gnu.tar.gz", "52020438"),
                                arrayOf("file", "bitcoin-27.0-riscv64-linux-gnu-debug.tar.gz", "355544520"),
                                arrayOf("file", "bitcoin-27.0-riscv64-linux-gnu.tar.gz", "47063263"),
                                arrayOf("file", "bitcoin-27.0-win64-debug.zip", "522357199"),
                                arrayOf("file", "bitcoin-27.0-win64-setup-unsigned.exe", "32348633"),
                                arrayOf("file", "bitcoin-27.0-win64-setup.exe", "32359120"),
                                arrayOf("file", "bitcoin-27.0-win64-unsigned.tar.gz", "32296875"),
                                arrayOf("file", "bitcoin-27.0-win64.zip", "46821837"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin-unsigned.tar.gz", "17160472"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin-unsigned.zip", "17213924"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin.tar.gz", "38428165"),
                                arrayOf("file", "bitcoin-27.0-x86_64-apple-darwin.zip", "17474503"),
                                arrayOf("file", "bitcoin-27.0-x86_64-linux-gnu-debug.tar.gz", "471529844"),
                                arrayOf("file", "bitcoin-27.0-x86_64-linux-gnu.tar.gz", "48849225"),
                                arrayOf("file", "bitcoin-27.0.tar.gz", "13082621"),
                            ),
                        content = "bitcoin-core-27.0",
                        sig = "40e1ccfdc38a32e6c164bb66e50df0cd3769e0431137a07709534a72b462dfcbc40106560d0dd66841fef4cbb7aece7db64e83a0fbe414759d4d9a799e522c57",
                    )

                LocalCache.justConsume(torrent, null, false)

                LocalCache.getOrCreateNote("e1ab66dd66e6ac4f32119deacf80d7c50787705d343d3bb896c099cf93821757")
            }
        }

    ThemeComparisonColumn(
        toPreview = {
            RenderTorrent(
                torrent,
                remember { mutableStateOf(Color.Transparent) },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    )
}

@Composable
fun RenderTorrent(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? TorrentEvent ?: return

    val title =
        remember(noteEvent) {
            val name = noteEvent.title() ?: TorrentEvent.ALT_DESCRIPTION
            val size = " (" + countToHumanReadableBytes(noteEvent.totalSizeBytes()) + ")"
            name + size
        }

    val description =
        remember(noteEvent) {
            val name = noteEvent.title() ?: TorrentEvent.ALT_DESCRIPTION
            if (noteEvent.content != name) noteEvent.content else null
        }

    val files = remember(noteEvent) { noteEvent.files().toImmutableList() }

    DisplayFileList(
        files,
        title,
        description,
        noteEvent::toMagnetLink,
        backgroundColor,
        accountViewModel,
        nav,
    )
}

@Composable
fun DisplayFileList(
    files: ImmutableList<FileTag>,
    name: String,
    description: String?,
    link: () -> String?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var expanded by remember { mutableStateOf(false) }

    val toMembersShow =
        if (expanded) {
            files
        } else {
            files.take(6)
        }

    Column {
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier =
                Modifier
                    .padding(horizontal = Size5dp)
                    .fillMaxWidth(),
        ) {
            Text(
                text = name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            val uriHandler = LocalUriHandler.current

            IconButton(
                onClick = {
                    try {
                        val linkStr = link()
                        if (linkStr != null) {
                            uriHandler.openUri(linkStr)
                        } else {
                            accountViewModel.toastManager.toast(Res.string.torrent_failure, Res.string.torrent_no_info)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        accountViewModel.toastManager.toast(Res.string.torrent_failure, Res.string.torrent_no_apps)
                    }
                },
                Modifier.size(Size30dp),
            ) {
                DownloadForOfflineIcon(Size20dp, MaterialTheme.colorScheme.onBackground)
            }
        }

        description?.let {
            Text(
                text = it,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color.Gray,
            )
        }

        Box {
            Column(modifier = Modifier.padding(top = 5.dp)) {
                toMembersShow.forEach { fileName ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = fileName.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .padding(start = 10.dp, end = 5.dp)
                                    .weight(1f),
                        )

                        fileName.bytes?.let {
                            Text(
                                text = countToHumanReadableBytes(it),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier =
                                    Modifier
                                        .padding(start = 5.dp, end = 10.dp),
                            )
                        }
                    }
                }
            }

            if (files.size > 3 && !expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(getGradient(backgroundColor)),
                ) {
                    ShowMoreButton { expanded = !expanded }
                }
            }
        }
    }
}
