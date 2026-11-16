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

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_add
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_empty
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_hint
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_manage_packs
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_mine
import com.vitorpamplona.amethyst.commons.resources.reaction_choices_remove
import com.vitorpamplona.amethyst.commons.resources.reactions
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.FlushPickerEditsOnExit
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.unicodeEmoji.EmojiChoices
import com.vitorpamplona.amethyst.commons.unicodeEmoji.toggleEmoji
import com.vitorpamplona.amethyst.commons.unicodeEmoji.ui.EmojiPicker
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The account's reaction choices on top, removable with a tap, over the full emoji picker: one
 * search across Unicode names and the shortcodes of the selected custom emoji packs, a section per
 * Unicode group and per pack, and every current choice marked so a second tap takes it out.
 *
 * Every tap saves: the choices apply at once and publish once the taps stop (see
 * [AccountViewModel.changeReactionTypes]), flushed when the screen goes away.
 */
@Composable
fun UpdateReactionTypeScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val choices by accountViewModel.reactionChoicesFlow().collectAsStateWithLifecycle()
    val chosen = remember(choices) { EmojiChoices.of(choices) }

    // The picker reads the selected packs from the cache; this fetches them while the screen is up.
    WatchAndLoadMyEmojiList(accountViewModel)
    FlushPickerEditsOnExit(accountViewModel)

    // Each edit starts from the account's current list, not this composition's copy, so two taps
    // in one frame both land.
    fun save(edit: (List<String>) -> List<String>) = accountViewModel.changeReactionTypes(edit(accountViewModel.reactionChoices()))

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.reactions), nav)
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePaddingSafe()
                .fillMaxSize(),
        ) {
            // Every copy: older versions could save one twice.
            CurrentReactionChoices(choices) { reaction -> save { list -> list.filter { it != reaction } } }

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            ) {
                Text(
                    text = stringRes(Res.string.reaction_choices_add),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { nav.nav(Route.MyEmojiList) }) {
                    Text(stringRes(Res.string.reaction_choices_manage_packs))
                }
            }

            EmojiPicker(
                emojiPacks = accountViewModel.account.emoji,
                onPickUnicode = { emoji -> save { it.toggleEmoji(emoji) } },
                onPickCustom = { custom -> save { it.toggleEmoji(custom) } },
                modifier = Modifier.fillMaxWidth().weight(1f),
                choosing = chosen,
                fillHeight = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentReactionChoices(
    choices: List<String>,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringRes(Res.string.reaction_choices_mine, choices.size),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringRes(Res.string.reaction_choices_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        if (choices.isEmpty()) {
            Text(
                text = stringRes(Res.string.reaction_choices_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            // A long list scrolls in place so the picker below keeps most of the screen.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 152.dp)
                        .verticalScroll(rememberScrollState())
                        .animateContentSize(),
            ) {
                val remove = stringRes(Res.string.reaction_choices_remove)
                choices.forEach { reactionType ->
                    ReactionChoiceChip(reactionType, remove) { onRemove(reactionType) }
                }
            }
        }
    }
}

@Composable
private fun ReactionChoiceChip(
    reactionType: String,
    remove: String,
    onRemove: () -> Unit,
) {
    // A custom emoji is an image: name it by its shortcode. Text emojis are read out by themselves.
    val shortcode = remember(reactionType) { if (reactionType.customEmojiUrlOrNull() != null) reactionType.substringAfter(':').substringBefore(':') else null }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = remove, onClick = onRemove)
                .then(if (shortcode != null) Modifier.semantics { contentDescription = ":$shortcode:" } else Modifier)
                .heightIn(min = 40.dp)
                .padding(start = 10.dp, end = 6.dp),
    ) {
        RenderReaction(reactionType)
        Icon(
            symbol = MaterialSymbols.Close,
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp).size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
