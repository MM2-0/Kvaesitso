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
package com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add_to_emoji_list
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.emoji_copy_shortcode
import com.vitorpamplona.amethyst.commons.resources.emoji_from_pack
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_add
import com.vitorpamplona.amethyst.commons.resources.emoji_pack_added
import com.vitorpamplona.amethyst.commons.resources.kind_emoji_packs
import com.vitorpamplona.amethyst.commons.resources.my_emoji_list_by_author
import com.vitorpamplona.amethyst.commons.resources.remove_from_emoji_list
import com.vitorpamplona.amethyst.commons.search.EmojiSearch
import com.vitorpamplona.amethyst.commons.ui.components.ShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.EmojiPackHeader
import com.vitorpamplona.amethyst.commons.ui.note.types.EmojiTile
import com.vitorpamplona.amethyst.commons.ui.note.types.EmojiTileSize
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.listTitle
import com.vitorpamplona.amethyst.commons.ui.note.types.lists.loadPrivateItems
import com.vitorpamplona.amethyst.commons.ui.note.types.rememberEmojiTileModifier
import com.vitorpamplona.amethyst.commons.ui.note.types.rememberPackEmojis
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import kotlinx.coroutines.launch

/**
 * The shortcode fragments the reader is searching for, so a pack card drawn inside search results
 * can lead with, and ring, the emojis that matched. Empty everywhere else.
 *
 * Not static: the terms change with every search, and only the pack cards read them, so only they
 * should recompose, not every result the provider wraps.
 */
val LocalEmojiSearchTerms = compositionLocalOf { EmojiSearch.Terms.EMPTY }

/**
 * Whether [packNote] is in the account's emoji list (kind 10030). False while that list has not
 * arrived: adding is still safe then, because `Account.addEmojiPack` fetches the list before it
 * would build a new one, and refuses when no relay answers.
 */
@Composable
fun EmojiPackInMyList(
    packNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable (inList: Boolean) -> Unit,
) {
    val selected by accountViewModel.account.emoji.selectedPackIds
        .collectAsStateWithLifecycle()
    content(packNote.idHex in selected)
}

/** Adds or removes [packNote] from the account's emoji list. */
fun toggleEmojiPack(
    packNote: Note,
    inList: Boolean,
    accountViewModel: AccountViewModel,
) {
    if (inList) accountViewModel.removeEmojiPack(packNote) else accountViewModel.addEmojiPack(packNote)
}

/** A round ＋ that turns into a ✓ once the pack is in the reader's emoji list. Sized for a grid card. */
@Composable
fun EmojiPackInListToggle(
    packNote: Note,
    accountViewModel: AccountViewModel,
) {
    EmojiPackInMyList(packNote, accountViewModel) { inList ->
        val container = if (inList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
        val content = if (inList) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(container)
                    .clickable { toggleEmojiPack(packNote, inList, accountViewModel) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                symbol = if (inList) MaterialSymbols.Check else MaterialSymbols.Add,
                contentDescription = stringRes(if (inList) Res.string.remove_from_emoji_list else Res.string.add_to_emoji_list),
                modifier = Modifier.size(18.dp),
                tint = content,
            )
        }
    }
}

/**
 * One emoji, large: its `:shortcode:` (what a reader types to use it), the pack it belongs to and
 * who made it, with Copy and Add pack. Opened by tapping an emoji in a pack card or in search.
 */
@Composable
fun EmojiPreviewDialog(
    emoji: EmojiUrlTag,
    packNote: Note?,
    accountViewModel: AccountViewModel,
    nav: INav?,
    onDismiss: () -> Unit,
) {
    val shortcode = remember(emoji.code) { ":${emoji.code}:" }
    val clipboard = LocalClipboard.current
    val notice: ShortNotice = rememberShortNotice()
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = emoji.url,
                        contentDescription = shortcode,
                        modifier = Modifier.size(128.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Text(
                    text = shortcode,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                packNote?.let { PackOrigin(it, accountViewModel, nav, onDismiss) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                clipboard.setText(shortcode)
                                notice.show(loadStringRes(Res.string.copied_to_clipboard))
                            }
                        },
                    ) {
                        Icon(MaterialSymbols.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringRes(Res.string.emoji_copy_shortcode), modifier = Modifier.padding(start = 8.dp))
                    }

                    if (packNote != null && packNote.event is EmojiPackEvent) {
                        EmojiPackInMyList(packNote, accountViewModel) { inList ->
                            Button(onClick = { toggleEmojiPack(packNote, inList, accountViewModel) }) {
                                Icon(
                                    symbol = if (inList) MaterialSymbols.Check else MaterialSymbols.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringRes(if (inList) Res.string.emoji_pack_added else Res.string.emoji_pack_add),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "From <pack> · by <author>", which opens the pack when [nav] is given. */
@Composable
private fun PackOrigin(
    packNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav?,
    onDismiss: () -> Unit,
) {
    val pack by observeNoteEvent<EmojiPackEvent>(packNote, accountViewModel)
    val event = pack ?: return
    val title = listTitle(event.titleOrName(), event.dTag(), Res.string.kind_emoji_packs)
    val cover = remember(event) { event.image()?.takeIf { it.isNotBlank() } }
    val author = remember(packNote) { packNote.author?.toBestDisplayName() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (nav != null) {
                        Modifier.clickable {
                            onDismiss()
                            nav.nav(Route.Note(packNote.idHex))
                        }
                    } else {
                        Modifier
                    },
                ).padding(8.dp),
    ) {
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(MaterialSymbols.EmojiEmotions, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringRes(Res.string.emoji_from_pack, title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!author.isNullOrBlank()) {
                Text(
                    text = stringRes(Res.string.my_emoji_list_by_author, author),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (nav != null) {
            Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The whole pack in a bottom sheet, opened by long-pressing a pack card, so browsing does not
 * navigate. A lazy grid, not the feed card's FlowRow: a pack can hold a thousand emojis, and only
 * the rows on screen should be composed and fetched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPackSheet(
    packNote: Note,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val noteEvent by observeNoteEvent<EmojiPackEvent>(packNote, accountViewModel)
        noteEvent?.let { EmojiPackSheetGrid(it, packNote, accountViewModel) }
    }
}

@Composable
private fun EmojiPackSheetGrid(
    noteEvent: EmojiPackEvent,
    packNote: Note,
    accountViewModel: AccountViewModel,
) {
    val privateEmojis by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateEmojis(it) }
    val allEmojis = rememberPackEmojis(noteEvent, privateEmojis)
    val tileModifier = rememberEmojiTileModifier()
    var preview by remember { mutableStateOf<EmojiUrlTag?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(EmojiTileSize),
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.padding(bottom = 4.dp)) {
                EmojiPackHeader(noteEvent, packNote, true, allEmojis, privateEmojis, accountViewModel)
            }
        }
        items(allEmojis) { emoji ->
            Box(contentAlignment = Alignment.Center) {
                EmojiTile(emoji, tileModifier) { preview = it }
            }
        }
    }

    preview?.let { emoji ->
        EmojiPreviewDialog(emoji, packNote, accountViewModel, nav = null) { preview = null }
    }
}
