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
package com.vitorpamplona.amethyst.commons.ui.note.creators.polls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.closing_time
import com.vitorpamplona.amethyst.commons.resources.confirm
import com.vitorpamplona.amethyst.commons.resources.kind_poll
import com.vitorpamplona.amethyst.commons.resources.kind_zap_poll
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_add_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_closes_at
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_duplicate_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_has_deadline
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_multiple_answers
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_stays_open
import com.vitorpamplona.amethyst.commons.resources.next
import com.vitorpamplona.amethyst.commons.resources.poll_deadline_custom
import com.vitorpamplona.amethyst.commons.resources.poll_deadline_passed
import com.vitorpamplona.amethyst.commons.resources.poll_pick_many
import com.vitorpamplona.amethyst.commons.resources.poll_pick_one
import com.vitorpamplona.amethyst.commons.resources.poll_remove
import com.vitorpamplona.amethyst.commons.resources.poll_zap_value_any
import com.vitorpamplona.amethyst.commons.resources.poll_zap_value_max
import com.vitorpamplona.amethyst.commons.resources.poll_zap_value_min
import com.vitorpamplona.amethyst.commons.resources.poll_zap_value_range
import com.vitorpamplona.amethyst.commons.resources.poll_zap_value_range_explainer
import com.vitorpamplona.amethyst.commons.resources.poll_zap_value_range_invalid
import com.vitorpamplona.amethyst.commons.resources.poll_zap_votes
import com.vitorpamplona.amethyst.commons.resources.poll_zap_votes_off
import com.vitorpamplona.amethyst.commons.resources.poll_zap_votes_on
import com.vitorpamplona.amethyst.commons.resources.sats
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.search.calendar.SearchDate
import com.vitorpamplona.amethyst.commons.ui.actions.rememberUrlUserTagOutputTransformation
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.note.rememberIs24HourClock
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.PollDeadline
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.PollOptionDraft
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home.ShortNotePostViewModel
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.text.MentionPreservingInputTransformation
import com.vitorpamplona.amethyst.commons.ui.text.TransformedTextInputGuard
import com.vitorpamplona.amethyst.commons.ui.theme.CardSurfaces
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.currentTimeMillis
import kotlinx.coroutines.flow.drop

/** The lengths offered as chips, in seconds; null is "Never". "Custom" opens the calendar. */
private val DEADLINES = listOf(null, TimeUtils.ONE_HOUR.toLong(), TimeUtils.ONE_DAY.toLong(), TimeUtils.ONE_WEEK.toLong(), TimeUtils.ONE_MONTH.toLong())

/**
 * The poll part of New Post. The post's text is the question; this adds the options, drawn the
 * way the poll card draws them (round glyph for one answer, square for several, a bolt when
 * votes are zaps), and one card of settings under them: zaps or free votes, how many answers,
 * the zap range and when voting closes.
 */
@Composable
fun PollComposer(
    postViewModel: ShortNotePostViewModel,
    showImages: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PollOptionsSection(postViewModel, showImages)
        PollSettings(postViewModel)
    }
}

@Composable
private fun PollOptionsSection(
    postViewModel: ShortNotePostViewModel,
    showImages: Boolean,
) {
    val focusManager = LocalFocusManager.current
    val options = postViewModel.pollOptions
    val multiple = postViewModel.pollType == PollType.MULTI_CHOICE
    val zaps = postViewModel.wantsZapPoll

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Poll, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                stringRes(if (zaps) Res.string.kind_zap_poll else Res.string.kind_poll),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = postViewModel::disablePoll,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.poll_remove),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // One pass over the options per edit, rather than one per row.
        val duplicates by remember(options) { derivedStateOf { postViewModel.duplicatePollOptions() } }

        val initialCount = remember { options.size }
        options.forEachIndexed { index, option ->
            key(option.code) {
                // Rows typed into existence grow in; the ones the composer opens with are simply there.
                val appear = remember { MutableTransitionState(index < initialCount) }.apply { targetState = true }
                AnimatedVisibility(visibleState = appear, enter = fadeIn() + expandVertically()) {
                    val trailing = postViewModel.isTrailingPollOption(option)
                    PollOptionRow(
                        option = option,
                        placeholder =
                            if (trailing) {
                                stringRes(Res.string.marmot_poll_add_option)
                            } else {
                                stringRes(Res.string.marmot_poll_option, index + 1)
                            },
                        trailing = trailing,
                        error = option in duplicates,
                        showImages = showImages,
                        onRemove = if (postViewModel.canRemovePollOption(option)) ({ postViewModel.removePollOption(option) }) else null,
                        onEdited = { textChanged -> postViewModel.onPollOptionChanged(option, textChanged) },
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        leading = {
                            if (zaps) {
                                Icon(
                                    MaterialSymbols.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (option.isEmpty) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                PollChoiceGlyph(multiple = multiple, active = !option.isEmpty)
                            }
                        },
                    )
                }
            }
        }

        AnimatedVisibility(visible = duplicates.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(stringRes(Res.string.marmot_poll_duplicate_option), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * One option: a borderless field in the option frame, with the `@` mentions and `:emoji:` of the
 * message field (a mention stays whole and shows as a name). An option is one line of text, so
 * Enter and Tab move on to the next one and a pasted line break becomes a space. An image link
 * shows the picture under it, the way the poll card will.
 */
@Composable
private fun PollOptionRow(
    option: PollOptionDraft,
    placeholder: String,
    trailing: Boolean,
    error: Boolean,
    showImages: Boolean,
    onRemove: (() -> Unit)?,
    onEdited: (textChanged: Boolean) -> Unit,
    onNext: () -> Unit,
    leading: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    val state = option.state
    val imageUrl by remember(option, showImages) {
        // The data-saver setting that keeps feed images from loading keeps this one too.
        derivedStateOf { if (showImages) findURLs(state.text.toString()).firstOrNull { RichTextParser.isImageUrl(it) } else null }
    }

    LaunchedEffect(state) {
        var lastText = state.text.toString()
        snapshotFlow { state.text.toString() to state.selection }
            .drop(1)
            .collect { (text, _) ->
                onEdited(text != lastText)
                lastText = text
            }
    }

    PollOptionFrame(
        focused = focused,
        error = error,
        trailing = trailing,
        onRemove = onRemove,
        leading = leading,
        below = imageUrl?.let { url -> { OptionImagePreview(url) } },
    ) {
        TransformedTextInputGuard {
            BasicTextField(
                state = state,
                textStyle = style,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                onKeyboardAction = { onNext() },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                inputTransformation = OptionInputTransformation,
                outputTransformation = rememberUrlUserTagOutputTransformation(),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(vertical = 14.dp)
                        // The field focus moves to (Enter, Tab, a tap) is the one the suggestions complete into.
                        .onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) onEdited(false)
                        }.onPreviewKeyEvent { event ->
                            if ((event.key == Key.Enter || event.key == Key.Tab) && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                                onNext()
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
}

/** The first image link in the option, drawn as the poll card will draw it. */
@Composable
private fun OptionImagePreview(url: String) {
    // Its own shape, at most this tall: a crop could hide the part of the picture the option is about.
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier =
            Modifier
                .heightIn(max = 160.dp)
                .clip(RoundedCornerShape(12.dp)),
    )
}

private val OptionInputTransformation = MentionPreservingInputTransformation.then(NoLineBreaks)

/** A pasted line break becomes a space: an option is one line of text. */
private object NoLineBreaks : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence()
        for (i in text.length - 1 downTo 0) {
            if (text[i] == '\n' || text[i] == '\r') replace(i, i + 1, " ")
        }
    }
}

@Composable
private fun PollSettings(postViewModel: ShortNotePostViewModel) {
    PollSettingsCard {
        if (postViewModel.canUseZapPoll) {
            val zaps = postViewModel.wantsZapPoll
            PollSettingRow(
                symbol = MaterialSymbols.Bolt,
                title = stringRes(Res.string.poll_zap_votes),
                subtitle = stringRes(if (zaps) Res.string.poll_zap_votes_on else Res.string.poll_zap_votes_off),
                modifier =
                    Modifier.toggleable(value = zaps, role = Role.Switch, onValueChange = {
                        postViewModel.wantsZapPoll = it
                        postViewModel.wantsPoll = !it
                    }),
            ) {
                Switch(checked = zaps, onCheckedChange = null)
            }
            PollSettingsDivider()
        }

        AnimatedVisibility(visible = !postViewModel.wantsZapPoll, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                val multiple = postViewModel.pollType == PollType.MULTI_CHOICE
                PollSettingRow(
                    symbol = if (multiple) MaterialSymbols.DoneAll else MaterialSymbols.RadioButtonChecked,
                    title = stringRes(Res.string.marmot_poll_multiple_answers),
                    subtitle = stringRes(if (multiple) Res.string.poll_pick_many else Res.string.poll_pick_one),
                    modifier =
                        Modifier.toggleable(value = multiple, role = Role.Switch, onValueChange = {
                            postViewModel.pollType = if (it) PollType.MULTI_CHOICE else PollType.SINGLE_CHOICE
                        }),
                ) {
                    Switch(checked = multiple, onCheckedChange = null)
                }
                PollSettingsDivider()
            }
        }

        AnimatedVisibility(visible = postViewModel.wantsZapPoll, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                ZapRangeSetting(postViewModel)
                PollSettingsDivider()
            }
        }

        DeadlineSetting(postViewModel)
    }
}

@Composable
private fun ZapRangeSetting(postViewModel: ShortNotePostViewModel) {
    val valid = postViewModel.isValidValueMinimum.value && postViewModel.isValidValueMaximum.value
    PollSettingRow(
        symbol = MaterialSymbols.Tune,
        title = stringRes(Res.string.poll_zap_value_range),
        subtitle = stringRes(Res.string.poll_zap_value_range_explainer),
    )
    PollSettingDetail {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SatsField(
                label = stringRes(Res.string.poll_zap_value_min),
                value = postViewModel.zapPollValueMinimum,
                error = !valid,
                onValueChange = { postViewModel.updateMinZapAmountForPoll(it) },
                modifier = Modifier.weight(1f),
            )
            SatsField(
                label = stringRes(Res.string.poll_zap_value_max),
                value = postViewModel.zapPollValueMaximum,
                error = !valid,
                onValueChange = { postViewModel.updateMaxZapAmountForPoll(it) },
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(visible = !valid, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(stringRes(Res.string.poll_zap_value_range_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A small amount field: its label over the number, "sats" after it, "Any" while empty. */
@Composable
private fun SatsField(
    label: String,
    value: Long?,
    error: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val borderColor =
        when {
            error -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    val style: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(CardSurfaces.color)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value?.toString() ?: "",
                onValueChange = onValueChange,
                textStyle = style,
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Box {
                        if (value == null) Text(stringRes(Res.string.poll_zap_value_any), style = style, color = MaterialTheme.colorScheme.placeholderText)
                        inner()
                    }
                },
            )
            Text(stringRes(Res.string.sats), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeadlineSetting(postViewModel: ShortNotePostViewModel) {
    val deadline = postViewModel.pollDeadline
    val scheduledFor = postViewModel.scheduledForSec
    var picking by remember { mutableStateOf(false) }

    // A length counts from when the poll goes out, so a scheduled post moves its closing time.
    val closesAt = remember(deadline, scheduledFor) { deadline.closesAt(scheduledFor ?: TimeUtils.now()) }
    val passed = remember(deadline, scheduledFor) { postViewModel.isPollDeadlinePassed() }
    PollSettingRow(
        symbol = if (deadline == PollDeadline.Never) MaterialSymbols.TimerOff else MaterialSymbols.Timer,
        title = stringRes(Res.string.marmot_poll_has_deadline),
        subtitle =
            when {
                passed -> stringRes(Res.string.poll_deadline_passed)
                closesAt != null -> stringRes(Res.string.marmot_poll_closes_at, formatDateTime(closesAt * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT))
                else -> stringRes(Res.string.marmot_poll_stays_open)
            },
    )
    PollDeadlineChips {
        DEADLINES.forEach { seconds ->
            val option = seconds?.let { PollDeadline.After(it) } ?: PollDeadline.Never
            PollDeadlineChip(
                selected = deadline == option,
                label = pollDeadlineLabel(seconds),
                onClick = { postViewModel.pollDeadline = option },
            )
        }
        PollDeadlineChip(
            selected = deadline is PollDeadline.At,
            label = stringRes(Res.string.poll_deadline_custom),
            onClick = { picking = true },
        )
    }

    if (picking) {
        PollDeadlineDialogs(
            initial = closesAt?.takeIf { !passed } ?: TimeUtils.oneDayAhead(),
            onPicked = {
                postViewModel.pollDeadline = PollDeadline.At(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** A date, then a time, for a deadline the chips don't offer. [initial] and the result are epoch seconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PollDeadlineDialogs(
    initial: Long,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingTime by remember { mutableStateOf(false) }

    // The deadline as a local wall-clock time: seconds since the epoch shifted by this zone's offset.
    val localSeconds = initial + LocalClock.utcOffsetSeconds(initial)
    val localSecondOfDay = localSeconds.mod(TimeUtils.ONE_DAY)
    val currentYear = SearchDate.civilFromDays(localSeconds.floorDiv(TimeUtils.ONE_DAY)).year

    val datePickerState =
        rememberDatePickerState(
            // The picker works in UTC days: hand it the local date, or late in the evening west of
            // UTC (early morning east of it) it would preselect the wrong day.
            initialSelectedDateMillis = localSeconds * 1000,
            yearRange = currentYear..2050,
            selectableDates =
                object : SelectableDates {
                    // Only today and later, with a day's slack for the time zones ahead of UTC.
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= currentTimeMillis() - 86400000
                },
        )
    val timePickerState =
        rememberTimePickerState(
            initialHour = localSecondOfDay / TimeUtils.ONE_HOUR,
            initialMinute = (localSecondOfDay % TimeUtils.ONE_HOUR) / TimeUtils.ONE_MINUTE,
            is24Hour = rememberIs24HourClock(),
        )

    if (!pickingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickingTime = true }) { Text(stringRes(Res.string.next)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        TimePickerDialog(
            title = { Text(stringRes(Res.string.closing_time)) },
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        val datetimeLocalTimeZone =
                            datePickerState.selectedDateMillis?.let { localDayAtZeroHourMillis ->
                                (localDayAtZeroHourMillis / 1000) +
                                    (timePickerState.hour * TimeUtils.ONE_HOUR) +
                                    (timePickerState.minute * TimeUtils.ONE_MINUTE)
                            } ?: TimeUtils.oneDayAhead()

                        // Back to UTC with the offset in force on that date, not today's: they
                        // differ across a daylight-saving change.
                        val approximate = datetimeLocalTimeZone - LocalClock.utcOffsetSeconds(TimeUtils.now())
                        onPicked(datetimeLocalTimeZone - LocalClock.utcOffsetSeconds(approximate))
                    },
                ) { Text(stringRes(Res.string.confirm)) }
            },
        ) {
            // Material picks the landscape clock on any wide window, which on desktop fills the screen.
            TimePicker(state = timePickerState, layoutType = TimePickerLayoutType.Vertical)
        }
    }
}

@Suppress("ViewModelConstructorInComposable")
@Preview
@Composable
fun PollComposerPreview() {
    PollComposer(ShortNotePostViewModel(), showImages = true)
}
