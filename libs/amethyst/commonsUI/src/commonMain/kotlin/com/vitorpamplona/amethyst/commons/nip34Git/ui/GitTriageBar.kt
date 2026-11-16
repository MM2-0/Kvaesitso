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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_merge_cancel
import com.vitorpamplona.amethyst.commons.resources.git_triage_add_label
import com.vitorpamplona.amethyst.commons.resources.git_triage_label
import com.vitorpamplona.amethyst.commons.resources.git_triage_label_hint
import com.vitorpamplona.amethyst.commons.resources.git_triage_rename
import com.vitorpamplona.amethyst.commons.resources.git_triage_save
import com.vitorpamplona.amethyst.commons.resources.git_triage_title
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip32Labeling.tags.LabelTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Triage on an issue, patch or PR, for whoever's labels and renames count (its author and the
 * repository's members): rename it, add a label, or remove a label they added. Written as
 * gitworkshop writes them: one `#t` label per event (lowercase, `a-z0-9-`), a `#subject` label for a
 * rename, and a deletion (NIP-09) to take back one's own label, since no event removes a label.
 */
@Composable
fun GitTriageBar(
    item: Note,
    accountViewModel: AccountViewModel,
) {
    if (!rememberCanGitTriage(item, accountViewModel)) return
    val me = accountViewModel.account.signer.pubKey

    // Hashtag labels change the item's labels, not its replies: follow that flow, so an added or
    // removed label shows at once.
    val labelState by remember(item) { item.flow().labels.stateFlow }.collectAsStateWithLifecycle()
    val labels = observeGitItemLabels(item)
    // A label deleted on another device names the label event, not the item: watch each one.
    WatchGitDeletions(
        remember(labelState) {
            item.labels.values
                .flatten()
                .distinct()
        },
    )
    // The labels this user added (theirs to take back); one in the item's own tags is not.
    val mine = remember(labelState, me) { item.labels.filterValues { notes -> notes.any { it.author?.pubkeyHex == me } }.keys }

    var renaming by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            InputChip(
                selected = false,
                onClick = { if (label in mine) accountViewModel.launchSigner { removeLabel(item, label, accountViewModel) } },
                label = { Text(label) },
                trailingIcon = if (label in mine) ({ Icon(MaterialSymbols.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }) else null,
            )
        }
        TextButton(onClick = { adding = true }) { Text(stringRes(Res.string.git_triage_add_label)) }
        TextButton(onClick = { renaming = true }) { Text(stringRes(Res.string.git_triage_rename)) }
    }

    if (adding) {
        TextDialog(
            title = stringRes(Res.string.git_triage_add_label),
            label = stringRes(Res.string.git_triage_label),
            hint = stringRes(Res.string.git_triage_label_hint),
            initial = "",
            onDismiss = { adding = false },
            onSave = { text ->
                adding = false
                val label = normalizeLabel(text)
                if (label.isNotEmpty() && label !in labels) accountViewModel.launchSigner { addLabel(item, label, accountViewModel) }
            },
        )
    }
    if (renaming) {
        TextDialog(
            title = stringRes(Res.string.git_triage_rename),
            label = stringRes(Res.string.git_triage_title),
            hint = null,
            initial = GitItemTitles.titleOf(item) ?: "",
            onDismiss = { renaming = false },
            onSave = { text ->
                renaming = false
                val title = text.trim()
                if (title.isNotEmpty() && title != GitItemTitles.titleOf(item)) accountViewModel.launchSigner { rename(item, title, accountViewModel) }
            },
        )
    }
}

/**
 * Whether the user may rename and label [item]: a repository member who can sign. Membership is
 * read from the repository's announcements, which can arrive after the item.
 */
@Composable
fun rememberCanGitTriage(
    item: Note,
    accountViewModel: AccountViewModel,
): Boolean {
    val itemEvent = item.event ?: return false
    val me = accountViewModel.account.signer.pubKey
    val announcements = observeGitRepositoryAnnouncements(itemEvent)
    return remember(itemEvent, me, announcements) { accountViewModel.account.isWriteable() && LocalCache.gitAuthority.isMemberAt(me, itemEvent, TimeUtils.now()) }
}

/**
 * Asks the relays for deletions of [notes] while on screen. A deletion names the event it takes
 * back, so one of a label, a resolution or a review reaches no query for the item it is about.
 */
@Composable
fun WatchGitDeletions(notes: List<Note>) {
    notes.forEach { note -> key(note.idHex) { EventFinderFilterAssemblerSubscription(note) } }
}

/** gitworkshop's label form: lowercase, with anything but letters, digits and dashes made a dash. */
fun normalizeLabel(text: String): String =
    text
        .trim()
        .removePrefix("#")
        .lowercase()
        .replace(Regex("[^a-z0-9-]+"), "-")
        .trim('-')

private suspend fun addLabel(
    item: Note,
    label: String,
    accountViewModel: AccountViewModel,
) {
    val hint = item.toEventHint<Event>() ?: return
    accountViewModel.account.signAndComputeBroadcast(LabelEvent.buildHashtagLabel(hint, label))
}

/**
 * Takes back [label]: deletes this user's label events that added it. One that also carried other
 * labels is deleted whole, so those are published again, one per event, as gitworkshop does.
 */
private suspend fun removeLabel(
    item: Note,
    label: String,
    accountViewModel: AccountViewModel,
) {
    val me = accountViewModel.account.signer.pubKey
    val hint = item.toEventHint<Event>() ?: return
    val mine = item.labels[label].orEmpty().filter { it.author?.pubkeyHex == me }
    if (mine.isEmpty()) return
    val others =
        mine
            .flatMap { note -> (note.event as? LabelEvent)?.labelsByNamespace(LabelEvent.HASHTAG_NAMESPACE).orEmpty().map { it.label } }
            .filter { it != label }
            .distinct()
    accountViewModel.account.delete(mine)
    others.forEach { accountViewModel.account.signAndComputeBroadcast(LabelEvent.buildHashtagLabel(hint, it)) }
}

private suspend fun rename(
    item: Note,
    title: String,
    accountViewModel: AccountViewModel,
) {
    val event = item.event ?: return
    accountViewModel.account.signAndComputeBroadcast(
        LabelEvent.buildEventLabel(event.id, item.relayHintUrl()?.url, event.pubKey, listOf(LabelTag(title, LabelEvent.SUBJECT_NAMESPACE))),
    )
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    hint: String?,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                supportingText = hint?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text(stringRes(Res.string.git_triage_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_merge_cancel)) } },
    )
}
