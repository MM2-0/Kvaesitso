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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiPackState
import com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis.EmojiSuggestionState
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_add_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_closes_at
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_create
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_duplicate_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_has_deadline
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_invalid
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_invalid_text
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_multiple_answers
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_new
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_not_anonymous
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_option_count
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_options
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_pick_many
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_pick_one
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_question
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_stays_open
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.components.rememberModalSheetState
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollChoiceGlyph
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollDeadlineChip
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollDeadlineChips
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollOptionFrame
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollSettingRow
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollSettingsCard
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.PollSettingsDivider
import com.vitorpamplona.amethyst.commons.ui.note.creators.polls.pollDeadlineLabel
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.text.TransformedTextInputGuard
import com.vitorpamplona.amethyst.commons.ui.text.currentWord
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightChat
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.marmot.foundation.appEvents.MarmotPolls
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Deadlines offered by the sheet, in seconds; MDK caps a poll at 30 days. */
private val DEADLINES = listOf(null, 3_600L, 24 * 3_600L, 7 * 24 * 3_600L, MarmotPolls.MAX_LIFETIME_SECONDS)

/** One option being written. [id] keys its row, so removing another keeps this one's text and focus. */
@Stable
private class DraftOption(
    val id: Int,
) {
    val state = TextFieldState()
    val focus = FocusRequester()

    val label: String get() = state.text.toString().trim()

    val isEmpty: Boolean get() = state.text.isEmpty()
}

/**
 * What the sheet holds while a poll is being written. There is always one empty row at the end
 * (up to [MarmotPolls.MAX_OPTIONS]) to type the next option into, so adding one is just typing.
 * Text is checked trimmed, the way it is sent: MDK refuses padded labels outright.
 */
@Stable
private class PollDraft {
    val question = TextFieldState()
    val questionFocus = FocusRequester()
    var multiple by mutableStateOf(false)
    var deadline by mutableStateOf<Long?>(null)

    /** The field being typed in: the `@` and `:` suggestion lists open under it. */
    var active by mutableStateOf<TextFieldState?>(null)

    private var nextId = 0
    val options = mutableStateListOf(DraftOption(nextId++), DraftOption(nextId++))

    fun remove(option: DraftOption) {
        options.remove(option)
        while (options.size < MarmotPolls.MIN_OPTIONS) options.add(DraftOption(nextId++))
        keepTrailingRow()
    }

    fun keepTrailingRow() {
        if (!options.last().isEmpty && options.size < MarmotPolls.MAX_OPTIONS) options.add(DraftOption(nextId++))
    }

    /** The empty row at the end is where the next option goes, not an option to remove; one emptied mid-list is. */
    fun canRemove(option: DraftOption) = options.size > MarmotPolls.MIN_OPTIONS && !(option === options.last() && option.isEmpty)

    fun isTrailing(option: DraftOption) = option === options.last() && option.isEmpty && options.size > MarmotPolls.MIN_OPTIONS

    val questionText: String get() = question.text.toString().trim()

    val labels: List<String> get() = options.map { it.label }.filter { it.isNotEmpty() }

    val questionInvalid: Boolean
        get() = questionText.isNotEmpty() && !MarmotPolls.isValidDisplayText(questionText, MarmotPolls.MAX_QUESTION_BYTES)

    fun isInvalid(option: DraftOption) = option.label.isNotEmpty() && !MarmotPolls.isValidDisplayText(option.label, MarmotPolls.MAX_OPTION_BYTES)

    /** Two options voters cannot tell apart split the vote for nothing. */
    fun isDuplicate(option: DraftOption) = option.label.isNotEmpty() && options.count { it.label.equals(option.label, ignoreCase = true) } > 1

    val isReady: Boolean
        get() =
            questionText.isNotEmpty() &&
                !questionInvalid &&
                labels.size in MarmotPolls.MIN_OPTIONS..MarmotPolls.MAX_OPTIONS &&
                options.none { isInvalid(it) || isDuplicate(it) }
}

/**
 * The `@` and `:` autocomplete of every compose screen (the shared [UserSuggestionState] and
 * [EmojiSuggestionState]), pointed at whichever field of the poll is being typed in. Members of
 * the group come first among the people suggested.
 */
@Stable
private class PollSuggestions(
    val users: UserSuggestionState,
    val emojis: EmojiSuggestionState,
) {
    fun onEdited(state: TextFieldState) {
        if (!state.selection.collapsed) return
        val word = state.currentWord()
        if (word.startsWith("@")) users.processCurrentWord(word) else users.reset()
        emojis.processCurrentWord(word)
    }

    fun pickUser(
        state: TextFieldState,
        user: User,
    ) {
        users.replaceCurrentWord(state, state.currentWord(), user)
        users.reset()
    }

    fun pickEmoji(
        state: TextFieldState,
        emoji: EmojiPackState.EmojiMedia,
    ) = emojis.autocompleteInto(state, emoji)

    fun reset() {
        users.reset()
        emojis.reset()
    }
}

/**
 * Write an encrypted NIP-88 poll for a Marmot group: a question, 2-10 options, one answer or
 * several, and an optional deadline of at most 30 days.
 *
 * The options are drawn the way the poll card draws them, with the glyph a voter will tap
 * (round for one answer, square for several), so what is written is what members see.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMarmotPollSheet(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val draft = remember { PollDraft() }
    val suggestions =
        remember {
            val account = accountViewModel.account
            PollSuggestions(
                users =
                    UserSuggestionState(
                        account,
                        accountViewModel.nip05ClientBuilder(),
                        priorityPubkeys = {
                            account.marmotGroupList
                                .getOrCreateGroup(nostrGroupId)
                                .members.value
                                .mapTo(mutableSetOf()) { it.pubkey }
                        },
                    ),
                emojis = EmojiSuggestionState(account.emoji),
            )
        }
    DisposableEffect(Unit) { onDispose { suggestions.reset() } }
    var sending by remember { mutableStateOf(false) }
    // Reads every field: derived, so the sheet does not recompose on each keystroke for it.
    val ready by remember(draft) { derivedStateOf { draft.isReady } }
    var refused by remember { mutableStateOf(false) }
    val sheetState = rememberModalSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardSurfaces.color,
    ) {
        // Insets before the scroll, so its viewport ends above the keyboard and the nav bar: inside
        // it they would only add space at the end, and a focused option could sit under the
        // keyboard. imePaddingSafe, not imePadding (see SafeImeInsets).
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePaddingSafe()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PollSheetHeader(onClose = ::close)

            QuestionField(
                draft = draft,
                suggestions = suggestions,
                accountViewModel = accountViewModel,
                onNext = {
                    draft.options
                        .first()
                        .focus
                        .requestFocus()
                },
            )

            OptionsSection(draft, suggestions, accountViewModel)

            SettingsCard(draft)

            AnimatedVisibility(visible = refused, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(stringRes(Res.string.marmot_poll_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                // One poll per tap: a second tap while the first is being sent would post it twice.
                enabled = ready && !sending,
                onClick = {
                    sending = true
                    refused = false
                    val type = if (draft.multiple) MarmotPolls.PollType.MULTIPLE_CHOICE else MarmotPolls.PollType.SINGLE_CHOICE
                    val endsAt = draft.deadline?.let { TimeUtils.now() + it }
                    scope.launch {
                        if (accountViewModel.createMarmotPoll(nostrGroupId, draft.questionText, draft.labels, type, endsAt)) {
                            close()
                        } else {
                            refused = true
                            sending = false
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(MaterialSymbols.Poll, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(Res.string.marmot_poll_create), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PollSheetHeader(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MaterialSymbols.Poll, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stringRes(Res.string.marmot_poll_new), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    MaterialSymbols.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringRes(Res.string.marmot_poll_not_anonymous),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(MaterialSymbols.Close, contentDescription = stringRes(Res.string.close))
        }
    }
}

/** The question, set like a headline: it is what the poll is. Enter moves on to the options. */
@Composable
private fun QuestionField(
    draft: PollDraft,
    suggestions: PollSuggestions,
    accountViewModel: AccountViewModel,
    onNext: () -> Unit,
) {
    LaunchedEffect(Unit) { draft.questionFocus.requestFocus() }
    val style = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)

    Column {
        PollTextField(
            state = draft.question,
            placeholder = stringRes(Res.string.marmot_poll_question),
            style = style,
            maxLines = 4,
            onEdited = {
                draft.active = draft.question
                suggestions.onEdited(draft.question)
            },
            onEnter = onNext,
            modifier = Modifier.fillMaxWidth().focusRequester(draft.questionFocus),
        )
        AnimatedVisibility(visible = draft.questionInvalid, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(
                stringRes(Res.string.marmot_poll_invalid_text),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FieldSuggestions(draft.question, draft.questionFocus, draft, suggestions, accountViewModel)
    }
}

/**
 * The `@` and `:` suggestion lists for [state], open while it is the field being typed in. A
 * pick completes the word under the cursor, as in every other composer.
 */
@Composable
private fun FieldSuggestions(
    state: TextFieldState,
    focus: FocusRequester,
    draft: PollDraft,
    suggestions: PollSuggestions,
    accountViewModel: AccountViewModel,
) {
    if (draft.active !== state) return
    ShowUserSuggestionList(
        suggestions.users,
        { user ->
            suggestions.pickUser(state, user)
            // Tapping the list took the cursor away; give it back to go on typing.
            focus.requestFocus()
        },
        accountViewModel,
        SuggestionListDefaultHeightChat,
    )
    ShowEmojiSuggestionList(
        suggestions.emojis,
        { emoji ->
            suggestions.pickEmoji(state, emoji)
            focus.requestFocus()
        },
        { emoji ->
            suggestions.pickEmoji(state, emoji)
            focus.requestFocus()
        },
        SuggestionListDefaultHeightChat,
    )
}

/**
 * A borderless field for the poll: wraps rather than scrolls sideways, keeps `@` mentions whole
 * and shows them as names, and turns a line break into the next field (a question or option is
 * one line of text; MDK refuses control characters in it).
 */
@Composable
private fun PollTextField(
    state: TextFieldState,
    placeholder: String,
    style: TextStyle,
    maxLines: Int,
    onEdited: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() to state.selection }
            .drop(1)
            .collect { onEdited() }
    }
    TransformedTextInputGuard {
        BasicTextField(
            state = state,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            onKeyboardAction = { onEnter() },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = maxLines),
            inputTransformation = PollInputTransformation,
            outputTransformation = rememberUrlUserTagOutputTransformation(),
            // The field focus moves to (Enter, Tab, a tap) is the one the suggestions follow.
            modifier =
                modifier.onFocusChanged { if (it.isFocused) onEdited() }.onPreviewKeyEvent { event ->
                    // Tab moves on like Enter: typed, it would put a tab in one-line text MDK refuses.
                    if ((event.key == Key.Enter || event.key == Key.Tab) && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                        onEnter()
                        true
                    } else {
                        false
                    }
                },
            decorator = { inner ->
                Box {
                    if (state.text.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.placeholderText)
                    inner()
                }
            },
        )
    }
}

private val PollInputTransformation = MentionPreservingInputTransformation.then(NoLineBreaks)

/** A pasted line break becomes a space: a question or option is one line of text. */
private object NoLineBreaks : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        for (i in text.length - 1 downTo 0) {
            if (text[i] == '\n' || text[i] == '\r') replace(i, i + 1, " ")
        }
    }
}

@Composable
private fun OptionsSection(
    draft: PollDraft,
    suggestions: PollSuggestions,
    accountViewModel: AccountViewModel,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringRes(Res.string.marmot_poll_options),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringRes(Res.string.marmot_poll_option_count, draft.labels.size, MarmotPolls.MAX_OPTIONS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
        }

        draft.options.forEachIndexed { index, option ->
            key(option.id) {
                // New rows grow in; the two the sheet opens with are simply there.
                val appear = remember { MutableTransitionState(index < MarmotPolls.MIN_OPTIONS) }.apply { targetState = true }
                AnimatedVisibility(visibleState = appear, enter = fadeIn() + expandVertically()) {
                    OptionRow(
                        option = option,
                        placeholder =
                            if (draft.isTrailing(option)) {
                                stringRes(Res.string.marmot_poll_add_option)
                            } else {
                                stringRes(Res.string.marmot_poll_option, index + 1)
                            },
                        multiple = draft.multiple,
                        trailing = draft.isTrailing(option),
                        error = draft.isInvalid(option) || draft.isDuplicate(option),
                        canRemove = draft.canRemove(option),
                        focusRequester = option.focus,
                        onEdited = {
                            draft.active = option.state
                            draft.keepTrailingRow()
                            suggestions.onEdited(option.state)
                        },
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onRemove = { draft.remove(option) },
                    )
                }
                FieldSuggestions(option.state, option.focus, draft, suggestions, accountViewModel)
            }
        }

        val problem =
            when {
                draft.options.any { draft.isInvalid(it) } -> Res.string.marmot_poll_invalid_text
                draft.options.any { draft.isDuplicate(it) } -> Res.string.marmot_poll_duplicate_option
                else -> null
            }
        AnimatedVisibility(visible = problem != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            problem?.let { Text(stringRes(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** One option, shaped like the row a voter will tap in the poll card. */
@Composable
private fun OptionRow(
    option: DraftOption,
    placeholder: String,
    multiple: Boolean,
    trailing: Boolean,
    error: Boolean,
    canRemove: Boolean,
    focusRequester: FocusRequester,
    onEdited: () -> Unit,
    onNext: () -> Unit,
    onRemove: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    PollOptionFrame(
        focused = focused,
        error = error,
        trailing = trailing,
        onRemove = if (canRemove) onRemove else null,
        leading = { PollChoiceGlyph(multiple = multiple, active = option.label.isNotEmpty()) },
    ) {
        PollTextField(
            state = option.state,
            placeholder = placeholder,
            style = style,
            maxLines = 3,
            onEdited = onEdited,
            onEnter = onNext,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
private fun SettingsCard(draft: PollDraft) {
    PollSettingsCard {
        PollSettingRow(
            symbol = if (draft.multiple) MaterialSymbols.DoneAll else MaterialSymbols.RadioButtonChecked,
            title = stringRes(Res.string.marmot_poll_multiple_answers),
            subtitle = stringRes(if (draft.multiple) Res.string.marmot_poll_pick_many else Res.string.marmot_poll_pick_one),
            modifier = Modifier.toggleable(value = draft.multiple, role = Role.Switch, onValueChange = { draft.multiple = it }),
        ) {
            Switch(checked = draft.multiple, onCheckedChange = null)
        }

        PollSettingsDivider()

        val closesAt = remember(draft.deadline) { draft.deadline?.let { (TimeUtils.now() + it) * 1000 } }
        PollSettingRow(
            symbol = if (draft.deadline == null) MaterialSymbols.TimerOff else MaterialSymbols.Timer,
            title = stringRes(Res.string.marmot_poll_has_deadline),
            subtitle =
                closesAt?.let { stringRes(Res.string.marmot_poll_closes_at, formatDateTime(it, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT)) }
                    ?: stringRes(Res.string.marmot_poll_stays_open),
        )
        PollDeadlineChips {
            DEADLINES.forEach { seconds ->
                PollDeadlineChip(
                    selected = draft.deadline == seconds,
                    label = pollDeadlineLabel(seconds),
                    onClick = { draft.deadline = seconds },
                )
            }
        }
    }
}
