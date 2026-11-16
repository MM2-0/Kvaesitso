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
package com.vitorpamplona.amethyst.commons.buzz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaContent
import com.vitorpamplona.quartz.buzz.apPersonas.PersonaEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backing ViewModel for [AgentPersonaEditScreen] — creates or edits a Buzz Agent Persona
 * (NIP-AP `kind:30175`), a world-readable addressable event authored by the workspace
 * owner and addressed by `(owner, 30175, slug)`.
 *
 * On edit, the existing [PersonaEvent] is loaded from [LocalCache] so fields the form does
 * not expose (ACP command, name pool, respond-to allowlist, parallelism) are preserved rather
 * than dropped on the next publish, and the new head is stamped after the one it replaces. The
 * slug (the `d` tag) is immutable once chosen — a different slug is a different persona.
 *
 * Published to the workspace's Buzz-dialect relays (falling back to the owner's outbox when
 * none are known). The relay serves it to other members only when [FormState.shared] is on
 * (the `["shared","true"]` tag); otherwise only the owner's own devices can read it.
 */
class AgentPersonaEditViewModel : ViewModel() {
    @Volatile private var account: Account? = null

    /** Set on edit; preserves fields the form doesn't surface. Null for a new persona. */
    private var existing: PersonaContent? = null
    private var existingCreatedAt: Long? = null
    private var editingSlug: String? = null

    private val _state = MutableStateFlow(FormState())
    val state: StateFlow<FormState> = _state.asStateFlow()

    /** Binds the account and, when [slug] names an existing persona, seeds the form from it. */
    fun bind(
        account: Account,
        slug: String?,
    ) {
        if (this.account != null) return
        this.account = account

        if (slug.isNullOrBlank()) return
        val note = LocalCache.getAddressableNoteIfExists(Address(PersonaEvent.KIND, account.userProfile().pubkeyHex, slug))
        val event = note?.event as? PersonaEvent ?: return
        val content = event.personaOrNull() ?: return
        existing = content
        existingCreatedAt = event.createdAt
        editingSlug = slug
        _state.value =
            FormState(
                slug = slug,
                slugLocked = true,
                displayName = content.displayName,
                systemPrompt = content.systemPrompt.orEmpty(),
                model = content.model.orEmpty(),
                runtime = content.runtime.orEmpty(),
                provider = content.provider.orEmpty(),
                avatarUrl = content.avatarUrl.orEmpty(),
                description = content.description.orEmpty(),
                shared = event.isShared(),
                threadSessions = content.isThreadSessionPolicy(),
            )
    }

    fun onSlugChange(v: String) = _state.update { it.copy(slug = v.trim(), error = null) }

    fun onDisplayNameChange(v: String) = _state.update { it.copy(displayName = v, error = null) }

    fun onSystemPromptChange(v: String) = _state.update { it.copy(systemPrompt = v, error = null) }

    fun onModelChange(v: String) = _state.update { it.copy(model = v.trim(), error = null) }

    fun onRuntimeChange(v: String) = _state.update { it.copy(runtime = v.trim(), error = null) }

    fun onProviderChange(v: String) = _state.update { it.copy(provider = v.trim(), error = null) }

    fun onAvatarUrlChange(v: String) = _state.update { it.copy(avatarUrl = v.trim(), error = null) }

    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v.take(PersonaContent.DESCRIPTION_MAX_CHARS), error = null) }

    fun onSharedChange(v: Boolean) = _state.update { it.copy(shared = v, error = null) }

    fun onThreadSessionsChange(v: Boolean) = _state.update { it.copy(threadSessions = v, error = null) }

    /**
     * Validates, builds a [PersonaEvent] (preserving unexposed fields on edit), signs and
     * publishes it to the Buzz relays. Calls [onDone] on the main thread on success.
     */
    fun save(onDone: () -> Unit) {
        val account = account ?: return
        val current = _state.value

        val slug = current.slug.trim()
        if (!isValidSlug(slug)) {
            _state.update { it.copy(error = "Slug must match a-z, 0-9, '-' or '_' (max 64 chars).") }
            return
        }
        if (current.displayName.isBlank()) {
            _state.update { it.copy(error = "Display name is required.") }
            return
        }

        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base = existing ?: PersonaContent(displayName = current.displayName)
                val content =
                    base.copy(
                        displayName = current.displayName.trim(),
                        // Buzz writes an empty prompt as "" (not absent) to keep the content hash
                        // stable, so a blank field keeps whichever form the persona already had.
                        systemPrompt = current.systemPrompt.blankToNull() ?: base.systemPrompt?.takeIf { it.isBlank() },
                        model = current.model.blankToNull(),
                        runtime = current.runtime.blankToNull(),
                        provider = current.provider.blankToNull(),
                        avatarUrl = current.avatarUrl.blankToNull(),
                        description = current.description.blankToNull(),
                        // Absent is the default `channel` policy; only `thread` is written.
                        sessionPolicy = if (current.threadSessions) PersonaContent.SESSION_POLICY_THREAD else null,
                    )

                val template = PersonaEvent.build(content, slug, shared = current.shared, priorHeadCreatedAt = existingCreatedAt)
                account.signAndSendPrivatelyOrBroadcast(template) {
                    BuzzRelayDialect.flow.value
                        .toList()
                        .ifEmpty { null }
                }

                viewModelScope.launch(Dispatchers.Main) { onDone() }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(isSaving = false, error = "Failed to publish: ${e.message ?: e::class.simpleName}") }
            }
        }
    }

    private fun String.blankToNull(): String? = trim().takeIf { it.isNotBlank() }

    data class FormState(
        val slug: String = "",
        /** True when editing an existing persona — the slug (address `d` tag) can't change. */
        val slugLocked: Boolean = false,
        val displayName: String = "",
        val systemPrompt: String = "",
        val model: String = "",
        val runtime: String = "",
        val provider: String = "",
        val avatarUrl: String = "",
        /** Short public description, at most [PersonaContent.DESCRIPTION_MAX_CHARS] characters. */
        val description: String = "",
        /** Publish to the community catalog (`["shared","true"]`) instead of owner-only. */
        val shared: Boolean = false,
        /** A separate ACP conversation per channel thread (`session_policy: "thread"`). */
        val threadSessions: Boolean = false,
        val isSaving: Boolean = false,
        val error: String? = null,
    ) {
        val canSave: Boolean get() = slug.isNotBlank() && displayName.isNotBlank() && !isSaving
    }

    companion object {
        private val SLUG_REGEX = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

        /** The persona slug grammar enforced by the relay (`validate_persona_envelope`). */
        fun isValidSlug(slug: String): Boolean = SLUG_REGEX.matches(slug)
    }
}
