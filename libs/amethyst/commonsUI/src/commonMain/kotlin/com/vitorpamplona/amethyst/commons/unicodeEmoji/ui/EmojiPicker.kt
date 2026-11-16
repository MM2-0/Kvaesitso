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
package com.vitorpamplona.amethyst.commons.unicodeEmoji.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiMedia
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.EmojiPackGroup
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState.Favorite
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.custom_emoji_picker_no_match
import com.vitorpamplona.amethyst.commons.resources.custom_emoji_picker_search
import com.vitorpamplona.amethyst.commons.resources.emoji_group_activities
import com.vitorpamplona.amethyst.commons.resources.emoji_group_animals_nature
import com.vitorpamplona.amethyst.commons.resources.emoji_group_flags
import com.vitorpamplona.amethyst.commons.resources.emoji_group_food_drink
import com.vitorpamplona.amethyst.commons.resources.emoji_group_objects
import com.vitorpamplona.amethyst.commons.resources.emoji_group_people_body
import com.vitorpamplona.amethyst.commons.resources.emoji_group_smileys_emotion
import com.vitorpamplona.amethyst.commons.resources.emoji_group_symbols
import com.vitorpamplona.amethyst.commons.resources.emoji_group_travel_places
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_close
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_favorites
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_open
import com.vitorpamplona.amethyst.commons.resources.emoji_picker_skin_tone
import com.vitorpamplona.amethyst.commons.resources.emoji_skin_tone_dark
import com.vitorpamplona.amethyst.commons.resources.emoji_skin_tone_default
import com.vitorpamplona.amethyst.commons.resources.emoji_skin_tone_light
import com.vitorpamplona.amethyst.commons.resources.emoji_skin_tone_medium
import com.vitorpamplona.amethyst.commons.resources.emoji_skin_tone_medium_dark
import com.vitorpamplona.amethyst.commons.resources.emoji_skin_tone_medium_light
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.insertAtCursor
import com.vitorpamplona.amethyst.commons.ui.text.insertWordAtCursor
import com.vitorpamplona.amethyst.commons.unicodeEmoji.EmojiChoices
import com.vitorpamplona.amethyst.commons.unicodeEmoji.SkinTone
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmoji
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmojiCatalog
import com.vitorpamplona.amethyst.commons.unicodeEmoji.UnicodeEmojiGroup
import com.vitorpamplona.amethyst.commons.unicodeEmoji.typedEmoji
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

private val PickerWidth = 352.dp
private val GridHeight = 280.dp
private val CellSize = 40.dp
private val SectionIconSize = 36.dp
private val EmojiStyle by lazy { TextStyle(fontFamily = EmojiFontFamily, fontSize = 24.sp, lineHeight = 28.sp, textAlign = TextAlign.Center) }
private val TabEmojiStyle by lazy { TextStyle(fontFamily = EmojiFontFamily, fontSize = 18.sp, lineHeight = 22.sp, textAlign = TextAlign.Center) }

private val NoFavorites: StateFlow<List<Favorite>> = MutableStateFlow(emptyList())
private val NoPacks: StateFlow<List<EmojiPackGroup>> = MutableStateFlow(emptyList())
private val NoEmojis: StateFlow<List<EmojiMedia>> = MutableStateFlow(emptyList())

private const val TAG = "EmojiPicker"

/**
 * A composer button that opens the emoji picker and writes the choice into [field] at the
 * cursor: a Unicode emoji as itself, a NIP-30 custom emoji as its `:shortcode:` (sending turns
 * that into an `emoji` tag, as typing `:word` and picking a suggestion does).
 *
 * Desktop has no system emoji keyboard, so this is the only way to type an emoji there. With
 * [emojiPacks] the picker also shows the account's favorites (its reaction choices) and a section
 * per selected custom emoji pack.
 */
@Composable
fun EmojiPickerButton(
    emojiPacks: EmojiPackState?,
    field: TextFieldState,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    modifier: Modifier = Modifier,
) {
    EmojiPickerMenuButton(
        emojiPacks = emojiPacks,
        onPickUnicode = field::insertAtCursor,
        onPickCustom = { field.insertWordAtCursor(":${it.code}:") },
        tint = tint,
        modifier = modifier,
    )
}

/**
 * The emoji button. Under an [EmojiPanelHost] it opens the docked panel and is hidden while the
 * on-screen keyboard is up, since that keyboard has an emoji key of its own: the panel is for
 * when there is no on-screen keyboard (Desktop, a hardware keyboard) or it was put away. Outside a
 * host it falls back to a menu that closes once an emoji is picked.
 */
@Composable
private fun EmojiPickerMenuButton(
    emojiPacks: EmojiPackState?,
    onPickUnicode: (String) -> Unit,
    onPickCustom: (EmojiMedia) -> Unit,
    tint: Color,
    modifier: Modifier,
) {
    val host = LocalEmojiPanel.current
    val region = LocalEmojiPanelRegion.current
    if (host != null && region != null) {
        EmojiPanelButton(host, region, emojiPacks, onPickUnicode, onPickCustom, tint, modifier)
        return
    }

    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        EmojiPickerIcon(tint) { open = true }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EmojiPicker(
                emojiPacks = emojiPacks,
                onPickUnicode = {
                    onPickUnicode(it)
                    open = false
                },
                onPickCustom = {
                    onPickCustom(it)
                    open = false
                },
                onClose = { open = false },
            )
        }
    }
}

@Composable
internal fun EmojiPickerIcon(
    tint: Color,
    onClick: () -> Unit,
) {
    val description = stringRes(Res.string.emoji_picker_open)
    HoverTooltip(description) {
        IconButton(onClick = onClick) {
            Icon(
                symbol = MaterialSymbols.EmojiEmotions,
                contentDescription = description,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
    }
}

@Immutable
private sealed class PaletteItem {
    /** A palette emoji, drawn in the picked skin tone. */
    @Immutable
    class Palette(
        val entry: UnicodeEmoji,
    ) : PaletteItem()

    /** A favorite, or the emoji typed in the search, inserted exactly as written. */
    @Immutable
    class Literal(
        val emoji: String,
        /** Its palette entry, for the name and to match a choice saved in another tone. */
        val entry: UnicodeEmoji?,
    ) : PaletteItem()

    @Immutable
    class Custom(
        val media: EmojiMedia,
    ) : PaletteItem()
}

@Immutable
private sealed class SectionIcon {
    @Immutable
    class Glyph(
        val emoji: String,
    ) : SectionIcon()

    @Immutable
    object Favorites : SectionIcon()

    @Immutable
    class Pack(
        val image: String?,
    ) : SectionIcon()
}

@Immutable
private class PaletteSection(
    val key: String,
    val label: String,
    val icon: SectionIcon,
    val items: List<PaletteItem>,
)

/**
 * One scrolling grid: the favorites, the Unicode groups, then one section per selected pack, with
 * a strip of section icons to jump between them, a name search across all of them, and the skin
 * tone. Without [emojiPacks] it shows only the Unicode groups.
 *
 * [choosing] turns it into an editor of a set of emojis, such as the reaction choices, rather than
 * a way to type one: every chosen emoji is marked and handed back in its saved form, so a tap can
 * remove it; the favorites section (that set itself) is left out; the search text is offered when
 * it is an emoji itself (the system keyboard's, one the palette lacks, an EmojiCoder message); and
 * every pack emoji is offered, since a choice keeps its image instead of resolving its shortcode
 * against the first pack that uses it. [fillHeight] stretches the grid over the height [modifier]
 * gives it instead of the menu's fixed one.
 */
@Composable
fun EmojiPicker(
    emojiPacks: EmojiPackState?,
    onPickUnicode: (String) -> Unit,
    onPickCustom: (EmojiMedia) -> Unit,
    modifier: Modifier = Modifier.width(PickerWidth),
    onClose: (() -> Unit)? = null,
    choosing: EmojiChoices? = null,
    fillHeight: Boolean = false,
) {
    val catalog by produceState(UnicodeEmojiPalette.cachedOrNull()) {
        if (value == null) {
            value =
                try {
                    UnicodeEmojiPalette.load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the favorites and packs usable; the next open tries the file again.
                    Log.w(TAG, "Could not load the Unicode emoji palette", e)
                    UnicodeEmojiCatalog(emptyList())
                }
        }
    }
    val favorites by (emojiPacks?.favorites ?: NoFavorites).collectAsStateWithLifecycle()
    val packs by (emojiPacks?.myPacks ?: NoPacks).collectAsStateWithLifecycle()
    val shortcodes by (emojiPacks?.myEmojis ?: NoEmojis).collectAsStateWithLifecycle()
    val search = rememberTextFieldState()
    val tone = UnicodeEmojiPalette.skinTone

    Column(modifier.padding(horizontal = 8.dp)) {
        // The grid is a direct child of this column, so it can take the height that is left.
        val gridModifier = if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(GridHeight)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            EmojiSearchField(search, Modifier.weight(1f))
            SkinToneSelector(tone) { UnicodeEmojiPalette.skinTone = it }
            onClose?.let { EmojiPickerCloseButton(it) }
        }

        val loaded = catalog
        if (loaded == null) {
            Box(gridModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val onPick: (PaletteItem) -> Unit = { item ->
            when (item) {
                is PaletteItem.Palette -> onPickUnicode(choosing?.savedForm(item.entry, tone) ?: item.entry.withTone(tone))
                is PaletteItem.Literal -> onPickUnicode(choosing?.savedForm(item.emoji, item.entry) ?: item.emoji)
                is PaletteItem.Custom -> onPickCustom(item.media)
            }
        }

        // A choice keeps its image, so a pack emoji whose shortcode another pack took is still one.
        val customEmojis = if (choosing != null) remember(packs) { packs.flatMap { it.emojis }.distinctBy { it.link } } else shortcodes

        val text = search.text.toString().trim()
        val query = text.removePrefix(":").removeSuffix(":")
        if (query.isEmpty()) {
            val sections = rememberSections(loaded, if (choosing == null) favorites else emptyList(), packs, shortcodes, everyPackEmoji = choosing != null)
            SectionedPalette(sections, tone, choosing, gridModifier, onPick)
        } else {
            val typed = if (choosing != null) remember(text, loaded) { typedEmoji(text, loaded) } else null
            SearchResults(loaded, customEmojis, query, typed, tone, choosing, gridModifier, onPick)
        }
    }
}

/**
 * Closes the picker. The docked panel also closes on Back, Esc and its highlighted smiley, and the
 * menu on a tap outside it, but none of that shows: a panel spanning the window looked stuck.
 */
@Composable
private fun EmojiPickerCloseButton(onClose: () -> Unit) {
    val description = stringRes(Res.string.emoji_picker_close)
    HoverTooltip(description) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(start = 4.dp)
                    .size(CellSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = description },
        ) {
            Icon(
                symbol = MaterialSymbols.Close,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * A compact filled pill, as the settings search bar draws it: a Material text field's 56dp
 * outlined box dwarfed the emoji rows. Its height matches the skin-tone button beside it.
 */
@Composable
private fun EmojiSearchField(
    search: TextFieldState,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    BasicTextField(
        state = search,
        modifier = modifier,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colorScheme.onSurface),
        cursorBrush = SolidColor(colorScheme.primary),
        decorator = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CellSize)
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHighest)
                        .padding(start = 12.dp, end = 4.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (search.text.isEmpty()) {
                        Text(
                            text = stringRes(Res.string.custom_emoji_picker_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (search.text.isNotEmpty()) {
                    val clear = stringRes(Res.string.clear)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = clear) { search.clearText() },
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = clear,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun rememberSections(
    catalog: UnicodeEmojiCatalog,
    favorites: List<Favorite>,
    packs: List<EmojiPackGroup>,
    shortcodes: List<EmojiMedia>,
    everyPackEmoji: Boolean,
): List<PaletteSection> {
    val favoritesLabel = stringRes(Res.string.emoji_picker_favorites)
    val groupLabels = catalog.groups.map { it.label() }

    return remember(catalog, favorites, packs, shortcodes, everyPackEmoji, favoritesLabel, groupLabels) {
        // A note carries one emoji tag per shortcode, and the first pack using a code wins it, so
        // a later pack's emoji under a taken code would post the other image: leave it out.
        val imageOf = shortcodes.associate { it.code to it.link }
        buildList {
            if (favorites.isNotEmpty()) {
                val items =
                    favorites.map {
                        when (it) {
                            is Favorite.Unicode -> PaletteItem.Literal(it.emoji, catalog.find(it.emoji))
                            is Favorite.Custom -> PaletteItem.Custom(it.media)
                        }
                    }
                add(PaletteSection("favorites", favoritesLabel, SectionIcon.Favorites, items))
            }
            catalog.groups.forEachIndexed { index, group ->
                add(PaletteSection("group:${group.name}", groupLabels[index], SectionIcon.Glyph(GroupStyles[group.name]?.icon ?: group.emojis.first().emoji), group.emojis.map { PaletteItem.Palette(it) }))
            }
            packs.forEach { pack ->
                val postable = if (everyPackEmoji) pack.emojis.distinctBy { it.link } else pack.emojis.filter { imageOf[it.code] == it.link }
                if (postable.isEmpty()) return@forEach
                val title = pack.title?.ifBlank { null } ?: postable.first().code
                add(PaletteSection("pack:${pack.address}", title, SectionIcon.Pack(pack.image ?: postable.first().link), postable.map { PaletteItem.Custom(it) }))
            }
        }
    }
}

@Composable
private fun SectionedPalette(
    sections: List<PaletteSection>,
    tone: SkinTone,
    choosing: EmojiChoices?,
    gridModifier: Modifier,
    onPick: (PaletteItem) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    // Each section is laid out as its header followed by its items.
    val headerIndices =
        remember(sections) {
            var next = 0
            sections.map { section -> next.also { next += 1 + section.items.size } }
        }
    // A short last section can never reach the top of the grid, so the section a tap jumped to
    // stays current until the grid moves away from where that jump left it.
    var jumped by remember(headerIndices) { mutableStateOf<Pair<Int, Int>?>(null) }
    val currentSection by remember(headerIndices) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex
            jumped?.takeIf { it.second == first }?.first
                ?: headerIndices.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }

    // A lazy grid keeps its place by key, so favorites that load after the picker opened would sit
    // above the first visible row. Still parked on the old first section means nobody scrolled: go up.
    val firstKey = sections.firstOrNull()?.key
    LaunchedEffect(firstKey) {
        withFrameNanos { }
        if (headerIndices.size > 1 && gridState.firstVisibleItemIndex == headerIndices[1] && gridState.firstVisibleItemScrollOffset == 0) {
            gridState.scrollToItem(0)
        }
    }

    // The strip scrolls once packs push it past the picker's width; keep the current section in view.
    val stripScroll = rememberScrollState()
    val iconPx = with(LocalDensity.current) { SectionIconSize.roundToPx() }
    LaunchedEffect(currentSection, stripScroll.maxValue) {
        val centered = currentSection * iconPx - (stripScroll.viewportSize - iconPx) / 2
        stripScroll.animateScrollTo(centered.coerceIn(0, stripScroll.maxValue))
    }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(stripScroll)) {
        sections.forEachIndexed { index, section ->
            HoverTooltip(section.label) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .size(SectionIconSize)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (index == currentSection) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable {
                                scope.launch {
                                    gridState.scrollToItem(headerIndices[index])
                                    // Where layout actually left it: the end of the grid clamps the jump.
                                    withFrameNanos { }
                                    jumped = index to gridState.firstVisibleItemIndex
                                }
                            },
                ) {
                    SectionIconContent(section)
                }
            }
        }
    }

    EmojiGrid(gridState, gridModifier) {
        sections.forEach { section ->
            item(key = "header:" + section.key, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            paletteItems(section.key, section.items, tone, choosing, onPick)
        }
    }
}

@Composable
private fun SectionIconContent(section: PaletteSection) {
    when (val icon = section.icon) {
        is SectionIcon.Glyph -> {
            Text(icon.emoji, style = TabEmojiStyle)
        }

        SectionIcon.Favorites -> {
            Icon(
                symbol = MaterialSymbols.Star,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is SectionIcon.Pack -> {
            AsyncImage(model = icon.image, contentDescription = null, modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun SearchResults(
    catalog: UnicodeEmojiCatalog,
    shortcodes: List<EmojiMedia>,
    query: String,
    typed: String?,
    tone: SkinTone,
    choosing: EmojiChoices?,
    gridModifier: Modifier,
    onPick: (PaletteItem) -> Unit,
) {
    val found =
        remember(catalog, shortcodes, query, typed) {
            // The account's own shortcodes first: a search for a shortcode means one of those.
            val custom = shortcodes.filter { it.code.contains(query, ignoreCase = true) }
            val (starts, contains) = custom.partition { it.code.startsWith(query, ignoreCase = true) }
            val typedItem = typed?.let { listOf(PaletteItem.Literal(it, catalog.find(it))) } ?: emptyList()
            typedItem + (starts + contains).map { PaletteItem.Custom(it) } + catalog.search(query).map { PaletteItem.Palette(it) }
        }

    if (found.isEmpty()) {
        Text(
            text = stringRes(Res.string.custom_emoji_picker_no_match),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = gridModifier.padding(16.dp),
        )
        return
    }

    EmojiGrid(rememberLazyGridState(), gridModifier) {
        paletteItems("search", found, tone, choosing, onPick)
    }
}

/** The name of the cell under the pointer, which [EmojiGrid] shows in one label over the grid. */
private val LocalHoveredEmoji = staticCompositionLocalOf<MutableState<String?>?> { null }

/**
 * Bounded by [modifier]: the menu scrolls its content, and a lazy grid needs a bounded height.
 *
 * The cells name themselves in one label drawn over the grid, not in a tooltip each. A tooltip is
 * a popup layer, and Compose Desktop (CMP 1.12) crashes with "RootNodeOwner is already disposed"
 * when a lazy cell scrolls away while its popup is open: the grid disposes the cell, and with it
 * the popup, in the middle of the measure pass that is about to measure that popup.
 */
@Composable
private fun EmojiGrid(
    state: LazyGridState,
    modifier: Modifier,
    content: LazyGridScope.() -> Unit,
) {
    val hovered = remember { mutableStateOf<String?>(null) }
    Box(modifier) {
        CompositionLocalProvider(LocalHoveredEmoji provides hovered) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(CellSize),
                state = state,
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxSize(),
                content = content,
            )
        }
        hovered.value?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Reports the cell to [LocalHoveredEmoji] while the pointer is over it, and clears it when it leaves or the cell goes away. */
@Composable
private fun Modifier.namesHoveredEmoji(name: String): Modifier {
    val hovered = LocalHoveredEmoji.current ?: return this
    DisposableEffect(hovered, name) {
        onDispose { if (hovered.value == name) hovered.value = null }
    }
    return pointerInput(hovered, name) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> hovered.value = name
                    PointerEventType.Exit -> if (hovered.value == name) hovered.value = null
                }
            }
        }
    }
}

private fun LazyGridScope.paletteItems(
    sectionKey: String,
    items: List<PaletteItem>,
    tone: SkinTone,
    choosing: EmojiChoices?,
    onPick: (PaletteItem) -> Unit,
) {
    // The same emoji can sit in several sections (a favorite is also in its group), so keys are per section.
    itemsIndexed(
        items,
        key = { index, _ -> "$sectionKey#$index" },
        contentType = { _, item -> if (item is PaletteItem.Custom) "custom" else "emoji" },
    ) { _, item ->
        PaletteCell(item, tone, choosing?.let { item.isIn(it, tone) }, onPick)
    }
}

private fun PaletteItem.isIn(
    choices: EmojiChoices,
    tone: SkinTone,
): Boolean =
    when (this) {
        is PaletteItem.Palette -> choices.savedForm(entry, tone) != null
        is PaletteItem.Literal -> choices.savedForm(emoji, entry) != null
        is PaletteItem.Custom -> choices.contains(media)
    }

@Composable
private fun PaletteCell(
    item: PaletteItem,
    tone: SkinTone,
    /** Null outside [EmojiPicker]'s `choosing` mode, where nothing can be chosen. */
    isPicked: Boolean?,
    onPick: (PaletteItem) -> Unit,
) {
    val name =
        when (item) {
            is PaletteItem.Palette -> item.entry.name
            is PaletteItem.Literal -> item.entry?.name ?: item.emoji
            is PaletteItem.Custom -> ":${item.media.code}:"
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .namesHoveredEmoji(name)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isPicked == true) {
                        Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    },
                ).clickable { onPick(item) }
                .then(if (isPicked != null) Modifier.semantics { selected = isPicked } else Modifier),
    ) {
        when (item) {
            is PaletteItem.Palette -> {
                Text(item.entry.withTone(tone), style = EmojiStyle)
            }

            is PaletteItem.Literal -> {
                // `+` and `-` are a like and a dislike, drawn as the heart and the thumbs down everywhere else.
                when (item.emoji) {
                    "+" -> LikedIcon(Modifier.size(24.dp))
                    "-" -> Text("\uD83D\uDC4E", style = EmojiStyle)
                    else -> Text(item.emoji, style = EmojiStyle)
                }
            }

            is PaletteItem.Custom -> {
                AsyncImage(model = item.media.link, contentDescription = name, modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(6.dp))
            }
        }
    }
}

/** The tone's swatch (Twemoji's skin colors, and its yellow for the default) and name. */
private class ToneOption(
    val swatch: Color,
    val label: StringResource,
    val sample: String,
)

private val ToneOptions =
    mapOf(
        SkinTone.NONE to ToneOption(Color(0xFFFFCC4D), Res.string.emoji_skin_tone_default, "👋"),
        SkinTone.LIGHT to ToneOption(Color(0xFFF7DECE), Res.string.emoji_skin_tone_light, "👋🏻"),
        SkinTone.MEDIUM_LIGHT to ToneOption(Color(0xFFF3D2A2), Res.string.emoji_skin_tone_medium_light, "👋🏼"),
        SkinTone.MEDIUM to ToneOption(Color(0xFFD5AB88), Res.string.emoji_skin_tone_medium, "👋🏽"),
        SkinTone.MEDIUM_DARK to ToneOption(Color(0xFFAF7E57), Res.string.emoji_skin_tone_medium_dark, "👋🏾"),
        SkinTone.DARK to ToneOption(Color(0xFF7C533E), Res.string.emoji_skin_tone_dark, "👋🏿"),
    )

@Composable
private fun ToneSwatch(
    tone: SkinTone,
    size: Dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(ToneOptions.getValue(tone).swatch)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

/**
 * The skin tone the palette draws its people and hands in: a swatch of the current tone with a
 * dropdown arrow, opening a menu of the named tones. A hand glyph alone did not read as a setting.
 */
@Composable
private fun SkinToneSelector(
    tone: SkinTone,
    onChange: (SkinTone) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val title = stringRes(Res.string.emoji_picker_skin_tone)
    val current = stringRes(ToneOptions.getValue(tone).label)

    Box {
        HoverTooltip("$title: $current") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(start = 4.dp)
                        .height(CellSize)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = title) { open = true }
                        .semantics { contentDescription = "$title: $current" }
                        .padding(start = 6.dp, end = 2.dp),
            ) {
                ToneSwatch(tone, 20.dp)
                Icon(
                    symbol = MaterialSymbols.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            SkinTone.entries.forEach { option ->
                val details = ToneOptions.getValue(option)
                DropdownMenuItem(
                    leadingIcon = { ToneSwatch(option, 18.dp) },
                    text = { Text(stringRes(details.label)) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(details.sample, style = TabEmojiStyle)
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                if (option == tone) {
                                    Icon(
                                        symbol = MaterialSymbols.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        onChange(option)
                        open = false
                    },
                )
            }
        }
    }
}

private class GroupStyle(
    val label: StringResource,
    val icon: String,
)

/** Keyed by the group names emoji-test.txt writes. An unknown group shows its own name and first emoji. */
private val GroupStyles: Map<String, GroupStyle> =
    mapOf(
        "Smileys & Emotion" to GroupStyle(Res.string.emoji_group_smileys_emotion, "😀"),
        "People & Body" to GroupStyle(Res.string.emoji_group_people_body, "👋"),
        "Animals & Nature" to GroupStyle(Res.string.emoji_group_animals_nature, "🐻"),
        "Food & Drink" to GroupStyle(Res.string.emoji_group_food_drink, "🍔"),
        "Travel & Places" to GroupStyle(Res.string.emoji_group_travel_places, "🚗"),
        "Activities" to GroupStyle(Res.string.emoji_group_activities, "⚽"),
        "Objects" to GroupStyle(Res.string.emoji_group_objects, "💡"),
        "Symbols" to GroupStyle(Res.string.emoji_group_symbols, "🔣"),
        "Flags" to GroupStyle(Res.string.emoji_group_flags, "🏁"),
    )

@Composable
private fun UnicodeEmojiGroup.label(): String = GroupStyles[name]?.let { stringRes(it.label) } ?: name
