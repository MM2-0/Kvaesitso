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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.create.NewGitRepository
import com.vitorpamplona.amethyst.commons.nip34Git.settings.GitRepositorySettings
import com.vitorpamplona.amethyst.commons.nip34Git.settings.GitSettingsProblem
import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitIconTile
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_cancel
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_remove_topic
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_topics_placeholder
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_bad_maintainers
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_bad_relays
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_changed_elsewhere
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_clone_urls
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_default_branch
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_description
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_maintainers
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_name
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_relays
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_save
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_title
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_topics
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_web_urls
import com.vitorpamplona.amethyst.commons.ui.components.ChipsInputField
import com.vitorpamplona.amethyst.commons.ui.components.FormFieldShape
import com.vitorpamplona.amethyst.commons.ui.components.FormTextField
import com.vitorpamplona.amethyst.commons.ui.components.LabeledField
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.formFieldColors
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.state.GitRepositoryStateEvent
import com.vitorpamplona.quartz.utils.Hex

/**
 * Edit form for a NIP-34 repository announcement (kind 30617). Because the event
 * is addressable, saving republishes it under the same `d` tag — preserving the
 * earliest-unique-commit, relays, maintainers, personal-fork flag and every tag the
 * form does not manage (ngit's role tags, `u`, `blossoms`, …) while letting the owner
 * edit the name, description, web/clone URLs and topics. Fields share the look of the
 * New repository screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitRepoSettingsDialog(
    event: GitRepositoryEvent,
    signedState: GitSignedState?,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    // The fields start from the announcement as it was when the dialog opened; a newer one (another
    // device's edit) must not be overwritten with those values.
    val opened = remember { event }
    val changedElsewhere = event.id != opened.id
    var relays by rememberSaveable { mutableStateOf(event.relays().joinToString("\n")) }
    var maintainers by rememberSaveable {
        mutableStateOf(event.maintainers().filter { it != event.pubKey }.joinToString("\n") { Hex.decode(it).toNpub() })
    }
    val branches =
        remember(signedState) {
            signedState
                ?.event
                ?.branches()
                ?.map { it.shortName }
                .orEmpty()
        }
    // Null until the user picks one: a newer signed state that arrives while the dialog is open
    // must not be overwritten with the HEAD shown when it opened.
    var pickedBranch by rememberSaveable { mutableStateOf<String?>(null) }
    val defaultBranch = pickedBranch ?: signedState?.headBranch
    val problems = GitRepositorySettings.problems(relays, maintainers)
    var name by rememberSaveable { mutableStateOf(event.name() ?: event.dTag()) }
    var description by rememberSaveable { mutableStateOf(event.description() ?: "") }
    var webUrls by rememberSaveable { mutableStateOf(event.webs().joinToString("\n")) }
    var cloneUrls by rememberSaveable { mutableStateOf(event.clones().joinToString("\n")) }
    // personal-fork is a flag that save() writes back on its own, not a topic to edit.
    var topics by rememberSaveable {
        mutableStateOf(
            event
                .hashtags()
                .filter { it != GitRepositoryEvent.PERSONAL_FORK }
                .distinct()
                .joinToString("\n"),
        )
    }
    var topicDraft by rememberSaveable { mutableStateOf("") }
    val topicList = splitLines(topics)

    fun commitTopicDraft() {
        val added = NewGitRepository.splitList(topicDraft).map { it.removePrefix("#").lowercase() }.filter { it.isNotEmpty() && it !in topicList }
        if (added.isNotEmpty()) topics = (topicList + added).joinToString("\n")
        topicDraft = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // A dialog's default surfaceContainerHigh is the fields' own fill; one step lower keeps them visible.
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GitIconTile(symbol = MaterialSymbols.Edit, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringRes(Res.string.git_repo_settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = event.dTag(),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabeledField(Res.string.git_repo_settings_name) {
                    FormTextField(
                        value = name,
                        onValueChange = { name = it },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    )
                }
                LabeledField(Res.string.git_repo_settings_description) {
                    SpellCheckedOutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        minLines = 3,
                        maxLines = 6,
                        shape = FormFieldShape,
                        colors = formFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LabeledField(Res.string.git_repo_settings_topics) {
                    ChipsInputField(
                        items = topicList,
                        draft = topicDraft,
                        onDraftChange = { topicDraft = it },
                        onCommit = { commitTopicDraft() },
                        onRemove = { removed -> topics = topicList.filter { it != removed }.joinToString("\n") },
                        removeDescription = { stringRes(Res.string.git_new_repository_remove_topic, it) },
                        placeholder = stringRes(Res.string.git_new_repository_topics_placeholder),
                        leadingIcon = MaterialSymbols.Tag,
                        chipLabel = { "#$it" },
                    )
                }
                LabeledField(Res.string.git_repo_settings_clone_urls) {
                    FormTextField(
                        value = cloneUrls,
                        onValueChange = { cloneUrls = it },
                        placeholder = "https://",
                        singleLine = false,
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                LabeledField(Res.string.git_repo_settings_web_urls) {
                    FormTextField(
                        value = webUrls,
                        onValueChange = { webUrls = it },
                        placeholder = "https://",
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                LabeledField(Res.string.git_repo_settings_relays) {
                    FormTextField(
                        value = relays,
                        onValueChange = { relays = it },
                        placeholder = "wss://",
                        singleLine = false,
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                LabeledField(Res.string.git_repo_settings_maintainers) {
                    FormTextField(
                        value = maintainers,
                        onValueChange = { maintainers = it },
                        placeholder = "npub1…",
                        singleLine = false,
                        minLines = 2,
                    )
                }
                if (changedElsewhere) {
                    Text(stringRes(Res.string.git_repo_settings_changed_elsewhere), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                problems.forEach { problem ->
                    val label = if (problem.field == GitSettingsProblem.Field.RELAYS) Res.string.git_repo_settings_bad_relays else Res.string.git_repo_settings_bad_maintainers
                    Text(stringRes(label, problem.entries.joinToString(", ")), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (branches.size > 1) {
                    LabeledField(Res.string.git_repo_settings_default_branch) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            branches.forEach { branch ->
                                FilterChip(
                                    selected = branch == defaultBranch,
                                    onClick = { pickedBranch = branch },
                                    label = { Text(branch, fontFamily = FontFamily.Monospace) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && problems.isEmpty() && !changedElsewhere,
                onClick = {
                    val allTopics = (topicList + NewGitRepository.splitList(topicDraft).map { it.removePrefix("#").lowercase() }).distinct()
                    val newHead = pickedBranch?.takeIf { it != signedState?.headBranch }
                    saveRepository(accountViewModel, event, name, description, webUrls, cloneUrls, relays, maintainers, allTopics, signedState, newHead)
                    onDismiss()
                },
            ) {
                Text(stringRes(Res.string.git_repo_settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_new_issue_cancel)) }
        },
    )
}

private fun splitLines(raw: String): List<String> =
    raw
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Republishes the announcement when one of its fields changed, and, when [newHead] is set, the
 * maintainers' signed state (kind 30618) with HEAD on that branch, every ref kept as signed.
 */
private fun saveRepository(
    accountViewModel: AccountViewModel,
    event: GitRepositoryEvent,
    name: String,
    description: String,
    webUrls: String,
    cloneUrls: String,
    relays: String,
    maintainers: String,
    topics: List<String>,
    signedState: GitSignedState?,
    newHead: String?,
) {
    accountViewModel.launchSigner {
        val template = GitRepositorySettings.edit(event, name, description, webUrls, cloneUrls, relays, maintainers, topics)
        val account = accountViewModel.account
        // Changing only the default branch publishes only the signed state.
        if (!GitRepositorySettings.isUnchanged(event, template)) account.sendAutomatic(account.signer.sign(template))
        if (newHead != null && signedState != null) {
            account.sendAutomatic(account.signer.sign(GitRepositoryStateEvent.changeHead(signedState.event, newHead)))
        }
    }
}
