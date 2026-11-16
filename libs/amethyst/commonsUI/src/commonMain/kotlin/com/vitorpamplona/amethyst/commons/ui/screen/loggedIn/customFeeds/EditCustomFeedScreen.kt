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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.customFeeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.feeds.custom.FeedBuilderState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.add
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_authors
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_authors_explainer
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_edit_title
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_emoji
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_exclude_authors
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_exclude_keywords
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_hashtags
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_hashtags_explainer
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_invalid
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kind_comments
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kind_highlights
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kind_long_form
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kind_notes
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kind_pictures
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kind_reposts
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kind_videos
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kinds
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_kinds_explainer
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_name
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_new_title
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_relays
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_relays_explainer
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_remove
import com.vitorpamplona.amethyst.commons.resources.custom_feeds_search_people
import com.vitorpamplona.amethyst.commons.ui.components.PlatformBackHandler
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrlOrNull
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import org.jetbrains.compose.resources.StringResource

// Room for a family or flag emoji (several code points joined), never half of a surrogate pair.
private const val MAX_EMOJI_CODE_POINTS = 8

private fun String.takeCodePoints(max: Int): String {
    var end = 0
    var count = 0
    while (end < length && count < max) {
        end += if (this[end].isHighSurrogate() && end + 1 < length && this[end + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return substring(0, end)
}

/** The kinds a feed can be narrowed to; each chip stands for one or more event kinds. */
private val kindPresets: List<Pair<StringResource, List<Int>>> =
    listOf(
        Res.string.custom_feeds_kind_notes to listOf(TextNoteEvent.KIND),
        Res.string.custom_feeds_kind_reposts to listOf(RepostEvent.KIND, GenericRepostEvent.KIND),
        Res.string.custom_feeds_kind_comments to listOf(CommentEvent.KIND),
        Res.string.custom_feeds_kind_long_form to listOf(LongFormContentEvent.KIND),
        Res.string.custom_feeds_kind_pictures to listOf(PictureEvent.KIND),
        Res.string.custom_feeds_kind_videos to listOf(VideoNormalEvent.KIND, VideoShortEvent.KIND, AddressableNormalVideoEvent.KIND, AddressableShortVideoEvent.KIND),
        Res.string.custom_feeds_kind_highlights to listOf(HighlightEvent.KIND),
    )

/**
 * Creates a custom feed, or edits the one with [id]: a name and emoji, the people, hashtags and
 * relays it reads (all that are set must match), the kinds it shows, and the people and words it
 * hides. Saving a new feed also shows it on Home.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCustomFeedScreen(
    id: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val settings = accountViewModel.account.settings
    val state = remember(id) { FeedBuilderState(settings.customFeeds.value.firstOrNull { it.id == id }) }

    PlatformBackHandler { nav.popBack() }

    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = if (id == null) Res.string.custom_feeds_new_title else Res.string.custom_feeds_edit_title,
                isActive = { state.isValid },
                onCancel = { nav.popBack() },
                onPost = {
                    val feed = state.toDefinition()
                    settings.saveCustomFeed(feed)
                    if (id == null) settings.changeDefaultHomeFollowList(TopFilter.CustomFeed(feed.id))
                    nav.popBack()
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.emoji,
                    onValueChange = { state.emoji = it.takeCodePoints(MAX_EMOJI_CODE_POINTS) },
                    label = { Text(stringRes(Res.string.custom_feeds_emoji)) },
                    singleLine = true,
                    modifier = Modifier.width(96.dp),
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    label = { Text(stringRes(Res.string.custom_feeds_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            if (!state.isValid) {
                Text(
                    stringRes(Res.string.custom_feeds_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(Res.string.custom_feeds_authors, Res.string.custom_feeds_authors_explainer) {
                PeoplePicker(state.authors, accountViewModel, nav)
            }

            Section(Res.string.custom_feeds_hashtags, Res.string.custom_feeds_hashtags_explainer) {
                WordList(state.hashtags, prefix = "#", normalize = {
                    it
                        .trim()
                        .removePrefix("#")
                        .lowercase()
                        .ifBlank { null }
                })
            }

            Section(Res.string.custom_feeds_kinds, Res.string.custom_feeds_kinds_explainer) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kindPresets.forEach { (label, kinds) ->
                        val selected = kinds.all { it in state.kinds }
                        FilterChip(
                            selected = selected,
                            onClick = { if (selected) state.kinds.removeAll(kinds) else state.kinds.addAll(kinds.filterNot { it in state.kinds }) },
                            label = { Text(stringRes(label)) },
                        )
                    }
                }
            }

            Section(Res.string.custom_feeds_relays, Res.string.custom_feeds_relays_explainer) {
                WordList(state.relays, normalize = { it.trim().normalizeRelayUrlOrNull()?.url })
            }

            Section(Res.string.custom_feeds_exclude_authors, null) {
                PeoplePicker(state.excludeAuthors, accountViewModel, nav)
            }

            Section(Res.string.custom_feeds_exclude_keywords, null) {
                WordList(state.excludeKeywords, normalize = { it.trim().ifBlank { null } })
            }
        }
    }
}

@Composable
private fun Section(
    title: StringResource,
    explainer: StringResource?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringRes(title), style = MaterialTheme.typography.titleMedium)
        explainer?.let {
            Text(stringRes(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/** Free-text entries shown as removable chips; [normalize] cleans an entry or rejects it with null. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordList(
    items: SnapshotStateList<String>,
    prefix: String = "",
    normalize: (String) -> String?,
) {
    var input by remember { mutableStateOf("") }
    val add = {
        normalize(input)?.let { if (it !in items) items.add(it) }
        input = ""
    }

    if (items.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.toList().forEach { item ->
                InputChip(
                    selected = false,
                    onClick = { items.remove(item) },
                    label = { Text(prefix + item) },
                    trailingIcon = {
                        Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.custom_feeds_remove), modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = add, enabled = normalize(input) != null) { Text(stringRes(Res.string.add)) }
    }
}

/** People chosen by searching names, NIP-05 addresses or npubs, shown as removable chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeoplePicker(
    selected: SnapshotStateList<HexKey>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var input by remember { mutableStateOf("") }
    val suggestions = remember { UserSuggestionState(accountViewModel.account, accountViewModel.nip05ClientBuilder()) }
    DisposableEffect(Unit) { onDispose { suggestions.reset() } }

    if (selected.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.toList().forEach { hex ->
                val user = remember(hex) { accountViewModel.checkGetOrCreateUser(hex) }
                InputChip(
                    selected = false,
                    onClick = { selected.remove(hex) },
                    label = { Text(user?.toBestDisplayName() ?: hex.take(8)) },
                    avatar = { UserPicture(userHex = hex, size = 24.dp, accountViewModel = accountViewModel, nav = nav) },
                    trailingIcon = {
                        Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.custom_feeds_remove), modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
    }

    OutlinedTextField(
        value = input,
        onValueChange = {
            input = it
            if (it.length > 2) suggestions.processCurrentWord(it) else suggestions.reset()
        },
        placeholder = { Text(stringRes(Res.string.custom_feeds_search_people)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    if (input.length > 2) {
        ShowUserSuggestionList(
            userSuggestions = suggestions,
            onSelect = { user ->
                if (user.pubkeyHex !in selected) selected.add(user.pubkeyHex)
                input = ""
                suggestions.reset()
            },
            accountViewModel = accountViewModel,
            modifier = SuggestionListDefaultHeightPage,
        )
    }
}
