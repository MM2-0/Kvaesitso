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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.buzz.AgentPersonaEditViewModel
import com.vitorpamplona.amethyst.commons.buzz.ui.DropdownOption
import com.vitorpamplona.amethyst.commons.buzz.ui.EditableSuggestDropdown
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_avatar
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_description
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_description_help
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_display_name
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_edit_title
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_model
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_new_title
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_provider
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_publish
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_publishing
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_runtime
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_shared
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_shared_help
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_slug
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_slug_help
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_system_prompt
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_thread_sessions
import com.vitorpamplona.amethyst.commons.resources.buzz_persona_thread_sessions_help
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.creators.contentWarning.SettingSwitchItem
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

// Well-known values for the persona's optional model / provider / runtime — suggestions only; any
// string is still accepted (the fields are free-form both in NIP-AP and in Buzz's persona events).
private val MODEL_OPTIONS =
    listOf(
        "claude-opus-4-8",
        "claude-opus-4",
        "claude-sonnet-4-5",
        "claude-haiku-4-5",
        "gpt-4o",
        "gpt-4.1",
        "o3",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "llama-3.3-70b",
    ).map { DropdownOption(it) }

private val PROVIDER_OPTIONS =
    listOf("anthropic", "openai", "google", "groq", "openrouter", "ollama", "bedrock", "azure").map { DropdownOption(it) }

private val RUNTIME_OPTIONS =
    listOf("goose", "claude-code", "custom").map { DropdownOption(it) }

/**
 * Create or edit a Buzz Agent Persona (NIP-AP `kind:30175`). [slug] null → a new persona;
 * otherwise edits the existing one (slug locked). Publishes to the workspace's Buzz relays
 * on save and pops back. Backed by [AgentPersonaEditViewModel], keyed by owner+slug so an
 * in-progress edit survives recomposition.
 */
@Composable
fun AgentPersonaEditScreen(
    slug: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pubkey = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: AgentPersonaEditViewModel = rememberViewModel(key = "PersonaEdit-$pubkey-${slug ?: "new"}") { AgentPersonaEditViewModel() }
    viewModel.bind(accountViewModel.account, slug)

    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = if (slug == null) stringRes(Res.string.buzz_persona_new_title) else stringRes(Res.string.buzz_persona_edit_title)

    Scaffold(
        topBar = { TopBarWithBackButton(title, nav) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.slug,
                onValueChange = viewModel::onSlugChange,
                label = { Text(stringRes(Res.string.buzz_persona_slug)) },
                singleLine = true,
                enabled = !state.slugLocked,
                supportingText = { Text(stringRes(Res.string.buzz_persona_slug_help)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text(stringRes(Res.string.buzz_persona_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SpellCheckedOutlinedTextField(
                value = state.systemPrompt,
                onValueChange = viewModel::onSystemPromptChange,
                label = { Text(stringRes(Res.string.buzz_persona_system_prompt)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            // Model / provider / runtime are free-form strings upstream, but their real-world values
            // are a small known set — offer them as suggestions while keeping free entry.
            EditableSuggestDropdown(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = stringRes(Res.string.buzz_persona_model),
                options = MODEL_OPTIONS,
            )
            EditableSuggestDropdown(
                value = state.provider,
                onValueChange = viewModel::onProviderChange,
                label = stringRes(Res.string.buzz_persona_provider),
                options = PROVIDER_OPTIONS,
            )
            EditableSuggestDropdown(
                value = state.runtime,
                onValueChange = viewModel::onRuntimeChange,
                label = stringRes(Res.string.buzz_persona_runtime),
                options = RUNTIME_OPTIONS,
            )
            OutlinedTextField(
                value = state.avatarUrl,
                onValueChange = viewModel::onAvatarUrlChange,
                label = { Text(stringRes(Res.string.buzz_persona_avatar)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SpellCheckedOutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringRes(Res.string.buzz_persona_description)) },
                supportingText = { Text(stringRes(Res.string.buzz_persona_description_help)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingSwitchItem(
                modifier = Modifier.fillMaxWidth(),
                checked = state.shared,
                onCheckedChange = viewModel::onSharedChange,
                title = Res.string.buzz_persona_shared,
                description = Res.string.buzz_persona_shared_help,
            )
            SettingSwitchItem(
                modifier = Modifier.fillMaxWidth(),
                checked = state.threadSessions,
                onCheckedChange = viewModel::onThreadSessionsChange,
                title = Res.string.buzz_persona_thread_sessions,
                description = Res.string.buzz_persona_thread_sessions_help,
            )

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = { viewModel.save(onDone = nav::popBack) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) stringRes(Res.string.buzz_persona_publishing) else stringRes(Res.string.buzz_persona_publish))
            }
        }
    }
}
