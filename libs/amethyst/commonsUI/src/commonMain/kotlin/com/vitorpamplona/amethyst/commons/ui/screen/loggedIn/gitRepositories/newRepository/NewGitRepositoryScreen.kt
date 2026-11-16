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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories.newRepository

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.create.NewGitRepositoryViewModel
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitIconTile
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_new_repository
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_add_server
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_advanced_summary
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_description_placeholder
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_euc_invalid
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_hosting_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier_help
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_identifier_taken
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_invalid_server
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_name_placeholder
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_no_hosting
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_other_clone_urls
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_relays
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_remove_topic
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_section_about
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_section_advanced
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_section_hosting
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_topics
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_topics_placeholder
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_untitled
import com.vitorpamplona.amethyst.commons.resources.git_new_repository_web_urls
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_description
import com.vitorpamplona.amethyst.commons.resources.git_repo_settings_name
import com.vitorpamplona.amethyst.commons.ui.components.ChipsInputField
import com.vitorpamplona.amethyst.commons.ui.components.CommandLine
import com.vitorpamplona.amethyst.commons.ui.components.FormFieldShape
import com.vitorpamplona.amethyst.commons.ui.components.FormTextField
import com.vitorpamplona.amethyst.commons.ui.components.LabeledField
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.formFieldColors
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

private val PreviewShape = RoundedCornerShape(24.dp)
private val InsetShape = RoundedCornerShape(12.dp)
private val Mono = TextStyle(fontFamily = FontFamily.Monospace)

/**
 * Announces a new NIP-34 repository. A live preview of the repository card sits on top and
 * follows the form: name, `npub/identifier` address, description, topics and the
 * `git clone nostr://…` line to copy. Below it, About, Hosting (GRASP servers as chips) and a
 * folded Advanced section for the fields most people never touch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGitRepositoryScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val model: NewGitRepositoryViewModel = rememberViewModel { NewGitRepositoryViewModel() }

    LaunchedEffect(accountViewModel.account) {
        model.init(accountViewModel.account)
    }

    val shortNpub = remember(accountViewModel) { accountViewModel.userProfile().pubkeyDisplayHex() }

    Scaffold(
        topBar = {
            CreatingTopBar(
                titleRes = Res.string.git_new_repository,
                isActive = model::canCreate,
                onCancel = { nav.popBack() },
                onPost = {
                    model.create(accountViewModel) { route ->
                        nav.popBack()
                        nav.nav(route)
                    }
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepositoryPreviewCard(model, shortNpub)
                    if (model.isCreating) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(InsetShape))
                    }
                }

                FormSection(Res.string.git_new_repository_section_about) {
                    AboutFields(model, shortNpub)
                }

                FormSection(Res.string.git_new_repository_section_hosting, Res.string.git_new_repository_hosting_help) {
                    HostingFields(model)
                }

                AdvancedSection(model)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live preview
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepositoryPreviewCard(
    model: NewGitRepositoryViewModel,
    shortNpub: String,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PreviewShape)
                .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.26f), colors.secondary.copy(alpha = 0.08f))))
                .border(1.dp, colors.primary.copy(alpha = 0.30f), PreviewShape)
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GitIconTile()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val untitled = model.name.isBlank()
                Text(
                    text = if (untitled) stringRes(Res.string.git_new_repository_untitled) else model.name.trim(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (untitled) colors.grayText else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$shortNpub/${model.identifier.trim().ifEmpty { "…" }}",
                    style = MaterialTheme.typography.labelMedium.merge(Mono),
                    color = colors.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (model.description.isNotBlank()) {
            Text(
                text = model.description.trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (model.topics.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                model.topics.forEach { TopicPill(it) }
            }
        }

        // npub is "" for the frame before init() runs; a command without it would be wrong to copy.
        if (model.npub.isNotEmpty()) {
            val cloneUrl = model.nostrCloneUrl
            CommandLine(
                command = "git clone $cloneUrl",
                display = "git clone ${cloneUrl.replace(model.npub, shortNpub)}" + if (model.identifier.isBlank()) "…" else "",
            )
        }
    }
}

@Composable
private fun TopicPill(topic: String) {
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

@Composable
private fun FormSection(
    title: StringResource,
    subtitle: StringResource? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringRes(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = stringRes(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutFields(
    model: NewGitRepositoryViewModel,
    shortNpub: String,
) {
    LabeledField(Res.string.git_repo_settings_name) {
        FormTextField(
            value = model.name,
            onValueChange = { model.name = it },
            placeholder = stringRes(Res.string.git_new_repository_name_placeholder),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
    }

    LabeledField(
        label = Res.string.git_new_repository_identifier,
        helper = stringRes(if (model.isIdentifierTaken) Res.string.git_new_repository_identifier_taken else Res.string.git_new_repository_identifier_help),
        isError = model.isIdentifierTaken,
    ) {
        FormTextField(
            value = model.identifier,
            onValueChange = { model.updateIdentifier(it) },
            prefix = "$shortNpub/",
            textStyle = monoBody(),
            isError = model.isIdentifierTaken,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
    }

    LabeledField(Res.string.git_repo_settings_description) {
        SpellCheckedOutlinedTextField(
            value = model.description,
            onValueChange = { model.description = it },
            placeholder = { Text(stringRes(Res.string.git_new_repository_description_placeholder)) },
            minLines = 3,
            maxLines = 6,
            shape = FormFieldShape,
            colors = formFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    LabeledField(Res.string.git_new_repository_topics) {
        ChipsInputField(
            items = model.topics,
            draft = model.topicDraft,
            onDraftChange = { model.topicDraft = it },
            onCommit = model::commitTopicDraft,
            onRemove = model::removeTopic,
            removeDescription = { stringRes(Res.string.git_new_repository_remove_topic, it) },
            placeholder = stringRes(Res.string.git_new_repository_topics_placeholder),
            leadingIcon = MaterialSymbols.Tag,
            chipLabel = { "#$it" },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostingFields(model: NewGitRepositoryViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        model.graspServers.forEach { choice ->
            FilterChip(
                selected = choice.selected,
                onClick = { model.toggleGraspServer(choice.server) },
                label = { Text(choice.server) },
                leadingIcon = {
                    Icon(
                        if (choice.selected) MaterialSymbols.Check else MaterialSymbols.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                shape = RoundedCornerShape(50),
            )
        }
    }

    val canAdd = model.serverDraft.isNotBlank() && model.isServerDraftValid
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FormTextField(
            value = model.serverDraft,
            onValueChange = { model.serverDraft = it },
            placeholder = stringRes(Res.string.git_new_repository_add_server),
            leadingIcon = MaterialSymbols.Dns,
            trailingIcon = {
                IconButton(onClick = { model.addServerDraft() }, enabled = canAdd) {
                    Icon(
                        MaterialSymbols.Add,
                        contentDescription = stringRes(Res.string.git_new_repository_add_server),
                        tint = if (canAdd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
                    )
                }
            },
            isError = !model.isServerDraftValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { model.addServerDraft() }),
        )
        if (!model.isServerDraftValid) {
            Text(
                text = stringRes(Res.string.git_new_repository_invalid_server, model.serverDraft.trim()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    AnimatedVisibility(visible = model.selectedGraspServers.isEmpty() && model.cloneUrls.isBlank()) {
        Text(
            text = stringRes(Res.string.git_new_repository_no_hosting),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun AdvancedSection(model: NewGitRepositoryViewModel) {
    var expanded by rememberSaveable { mutableStateOf(model.hasAdvancedInput) }
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PreviewShape)
                .border(1.dp, colors.outlineVariant, PreviewShape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(MaterialSymbols.Tune, contentDescription = null, modifier = Modifier.size(22.dp), tint = colors.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringRes(Res.string.git_new_repository_section_advanced),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringRes(Res.string.git_new_repository_advanced_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.grayText,
                )
            }
            Icon(
                if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = null,
                tint = colors.grayText,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LabeledField(
                    label = Res.string.git_new_repository_euc,
                    helper = stringRes(if (model.isCommitValid) Res.string.git_new_repository_euc_help else Res.string.git_new_repository_euc_invalid),
                    isError = !model.isCommitValid,
                ) {
                    FormTextField(
                        value = model.earliestCommit,
                        onValueChange = { model.earliestCommit = it },
                        placeholder = "3c7af0b95a53d7d9…",
                        textStyle = monoBody(),
                        isError = !model.isCommitValid,
                    )
                }
                LabeledField(Res.string.git_new_repository_other_clone_urls) {
                    FormTextField(
                        value = model.cloneUrls,
                        onValueChange = { model.cloneUrls = it },
                        placeholder = "https://",
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                LabeledField(Res.string.git_new_repository_web_urls) {
                    FormTextField(
                        value = model.webUrls,
                        onValueChange = { model.webUrls = it },
                        placeholder = "https://",
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
                val invalidRelay = model.invalidRelay
                LabeledField(
                    label = Res.string.git_new_repository_relays,
                    helper = invalidRelay?.let { stringRes(Res.string.git_new_repository_invalid_server, it) },
                    isError = invalidRelay != null,
                ) {
                    FormTextField(
                        value = model.relays,
                        onValueChange = { model.relays = it },
                        placeholder = "wss://",
                        isError = invalidRelay != null,
                        singleLine = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                }
            }
        }
    }
}

@Composable
private fun monoBody(): TextStyle = MaterialTheme.typography.bodyLarge.merge(Mono)
