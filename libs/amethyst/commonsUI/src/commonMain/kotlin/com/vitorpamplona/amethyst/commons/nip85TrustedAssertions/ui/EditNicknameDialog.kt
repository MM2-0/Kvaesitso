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
package com.vitorpamplona.amethyst.commons.nip85TrustedAssertions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.nickname_cancel
import com.vitorpamplona.amethyst.commons.resources.nickname_dialog_description
import com.vitorpamplona.amethyst.commons.resources.nickname_label
import com.vitorpamplona.amethyst.commons.resources.nickname_note_label
import com.vitorpamplona.amethyst.commons.resources.nickname_save
import com.vitorpamplona.amethyst.commons.ui.components.PromptDialog
import com.vitorpamplona.amethyst.commons.ui.components.PromptShape
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

/**
 * Edits the nickname (petname) and private note (summary) the account keeps for
 * [user] in its own kind:30382 contact card, and publishes the change. Both fields
 * are saved NIP-44 encrypted, so only this account can read them. Blank fields
 * clear the value.
 *
 * Typing `:` offers the account's NIP-30 custom emojis; the mappings for any
 * shortcode used are embedded (also encrypted) so the nickname renders with them.
 */
@Composable
fun EditNicknameDialog(
    user: User,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    // keeps the account's selected emoji packs loaded for the : autocomplete
    WatchAndLoadMyEmojiList(accountViewModel)

    val userAssertions = accountViewModel.account.userAssertions
    val nickname = rememberTextFieldState()
    val summary = rememberTextFieldState()
    val emojiSuggestions = remember(userAssertions) { EmojiSuggestionState(userAssertions.emojiPacks) }
    // which field the emoji autocomplete should insert into: the last one edited
    val emojiTarget = remember { mutableStateOf<TextFieldState?>(null) }
    // what the card holds now, so Save only lights up for a real change
    var saved by remember { mutableStateOf<Pair<String?, String?>>(null to null) }
    val focusRequester = remember { FocusRequester() }

    // Prefill with the card's current encrypted values, if any. Decryption can
    // be slow on external signers, so don't clobber anything already typed.
    LaunchedEffect(user) {
        try {
            focusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            // Not attached yet; the user can still tap into the field.
        }
        val petName = userAssertions.petName(user.pubkeyHex)
        val note = userAssertions.summary(user.pubkeyHex)
        // compared against the trimmed fields, so a card written untrimmed elsewhere isn't a change
        saved = petName?.cleaned() to note?.cleaned()
        petName?.takeIf { nickname.text.isEmpty() }?.let { nickname.setTextAndPlaceCursorAtEnd(it) }
        note?.takeIf { summary.text.isEmpty() }?.let { summary.setTextAndPlaceCursorAtEnd(it) }
    }

    // Feed the word under the cursor of the last-edited field to the autocomplete.
    LaunchedEffect(nickname, summary) {
        fun watch(field: TextFieldState) {
            emojiTarget.value = field
            if (field.selection.collapsed) {
                emojiSuggestions.processCurrentWord(field.currentWord())
            }
        }
        launch { snapshotFlow { nickname.text }.collect { watch(nickname) } }
        launch { snapshotFlow { summary.text }.collect { watch(summary) } }
    }

    // Derived so a keystroke only recomposes the dialog when these flip, not on every character.
    val changed by remember { derivedStateOf { (nickname.text.cleaned() to summary.text.cleaned()) != saved } }
    val hasNickname by remember { derivedStateOf { nickname.text.isNotEmpty() } }

    PromptDialog(
        badge = { BaseUserPicture(user, 64.dp, accountViewModel) },
        title = user.toBestDisplayName(),
        description = stringRes(Res.string.nickname_dialog_description),
        confirmText = stringRes(Res.string.nickname_save),
        confirmEnabled = changed,
        onConfirm = {
            accountViewModel.updateUserAssertionPetName(user, nickname.text.cleaned(), summary.text.cleaned())
            onDismiss()
        },
        dismissText = stringRes(Res.string.nickname_cancel),
        onDismiss = onDismiss,
        dismissible = true,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                state = nickname,
                lineLimits = TextFieldLineLimits.SingleLine,
                label = { Text(stringRes(Res.string.nickname_label)) },
                trailingIcon =
                    if (hasNickname) {
                        {
                            IconButton(
                                onClick = {
                                    nickname.clearText()
                                    // the click took focus; hand it back so the user can type the new one
                                    focusRequester.requestFocus()
                                },
                            ) {
                                Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.clear))
                            }
                        }
                    } else {
                        null
                    },
                shape = PromptShape,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            SpellCheckedOutlinedTextField(
                state = summary,
                label = { Text(stringRes(Res.string.nickname_note_label)) },
                minLines = 2,
                maxLines = 5,
                shape = PromptShape,
                modifier = Modifier.fillMaxWidth(),
            )
            ShowEmojiSuggestionList(
                emojiSuggestions,
                onSelect = { emoji ->
                    emojiTarget.value?.let { emojiSuggestions.autocompleteInto(it, emoji) }
                },
                onFullSize = { emoji ->
                    emojiTarget.value?.let { emojiSuggestions.autocompleteInto(it, emoji) }
                },
                modifier = Modifier.heightIn(max = 200.dp),
            )
        }
    }
}

private fun CharSequence.cleaned(): String? = toString().trim().ifBlank { null }
