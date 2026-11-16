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
package com.vitorpamplona.amethyst.commons.nip34Git.create

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.viewmodels.BaseAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip34Git.grasp.GraspServer
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.NostrCloneUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One GRASP server on the form, ticked or not. */
@Immutable
data class GraspChoice(
    val server: String,
    val selected: Boolean,
)

/**
 * State of the New repository screen. The identifier follows the name until the user types
 * one; the GRASP servers start as the user's kind:10317 list (ngit's defaults when there is
 * none), all ticked, as `ngit init` offers them. [NewGitRepository] turns it into the event.
 */
@Stable
class NewGitRepositoryViewModel : ViewModel() {
    private var account: Account? = null

    var name by mutableStateOf("")
    private var typedIdentifier by mutableStateOf<String?>(null)
    var description by mutableStateOf("")

    val topics = mutableStateListOf<String>()
    var topicDraft by mutableStateOf("")

    val graspServers = mutableStateListOf<GraspChoice>()
    var serverDraft by mutableStateOf("")
    private var graspServersTouched = false

    var cloneUrls by mutableStateOf("")
    var webUrls by mutableStateOf("")
    var relays by mutableStateOf("")
    var earliestCommit by mutableStateOf("")

    /** The user's existing repository identifiers, once [init]'s lookup returns. */
    private var ownIdentifiers by mutableStateOf<Set<String>>(emptySet())
    private var ownIdentifiersJob: Job? = null

    var isCreating by mutableStateOf(false)
        private set

    var npub by mutableStateOf("")
        private set

    fun init(account: Account) {
        if (this.account === account) return
        this.account = account
        npub = account.pubKey.hexToByteArray().toNpub()
        graspServers.clear()
        graspServers.addAll(DEFAULT_GRASP_SERVERS.map { GraspChoice(it, true) })

        // Fetched once up front, so a taken identifier shows while typing and Create needs no
        // round trip. GRASP servers serve repositories that already have code.
        val graspRelays = DEFAULT_GRASP_SERVERS.mapNotNull { GraspServer.relayUrl(it)?.let(RelayUrlNormalizer::normalizeOrNull) }.toSet()
        ownIdentifiersJob =
            viewModelScope.launch(Dispatchers.IO) {
                val found = account.myGitRepositoryIdentifiers(graspRelays) ?: return@launch
                withContext(Dispatchers.Main) { ownIdentifiers = ownIdentifiers + found }
            }

        viewModelScope.launch(Dispatchers.IO) {
            val mine = account.myGraspServers()?.mapNotNull { GraspServer.normalize(it) }?.distinct()
            if (!mine.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    if (!graspServersTouched) {
                        graspServers.clear()
                        graspServers.addAll(mine.map { GraspChoice(it, true) })
                    }
                }
            }
        }
    }

    val identifier: String get() = typedIdentifier ?: GitRepositoryEvent.suggestIdentifier(name)

    fun updateIdentifier(value: String) {
        typedIdentifier = value
    }

    val isIdentifierTaken: Boolean get() = identifier.trim() in ownIdentifiers

    // ---- topics ----

    /** Turns whatever is in [topicDraft] into topics: split on commas and spaces, lowercase, no `#`. */
    fun commitTopicDraft() {
        NewGitRepository
            .splitList(topicDraft)
            .map { it.removePrefix("#").lowercase() }
            .filter { it.isNotEmpty() && it !in topics }
            .forEach { topics.add(it) }
        topicDraft = ""
    }

    fun removeTopic(topic: String) {
        topics.remove(topic)
    }

    // ---- hosting ----

    val selectedGraspServers: List<String> get() = graspServers.filter { it.selected }.map { it.server }

    fun toggleGraspServer(server: String) {
        graspServersTouched = true
        val index = graspServers.indexOfFirst { it.server == server }
        if (index >= 0) graspServers[index] = graspServers[index].copy(selected = !graspServers[index].selected)
    }

    val isServerDraftValid: Boolean get() = serverDraft.isBlank() || NewGitRepository.isValidGraspServer(serverDraft)

    /** Adds [serverDraft] as a ticked server. False when it is not a server address. */
    fun addServerDraft(): Boolean {
        val normalized = GraspServer.normalize(serverDraft) ?: return false
        graspServersTouched = true
        val index = graspServers.indexOfFirst { it.server == normalized }
        if (index >= 0) {
            graspServers[index] = graspServers[index].copy(selected = true)
        } else {
            graspServers.add(GraspChoice(normalized, true))
        }
        serverDraft = ""
        return true
    }

    // ---- advanced ----

    val invalidRelay: String? get() = NewGitRepository.splitList(relays).firstOrNull { !NewGitRepository.isValidRelay(it) }

    val isCommitValid: Boolean get() = NewGitRepository.isValidCommitId(earliestCommit)

    /** True when an advanced field holds something, so the section opens to show it. */
    val hasAdvancedInput: Boolean get() = cloneUrls.isNotBlank() || webUrls.isNotBlank() || relays.isNotBlank() || earliestCommit.isNotBlank()

    // ---- preview ----

    /**
     * The `nostr://` URL `git clone` takes, hinting the first GRASP server as ngit does. It ends
     * in a bare `/` while the identifier is still empty.
     */
    val nostrCloneUrl: String get() {
        val hint = selectedGraspServers.firstOrNull()?.let { GraspServer.relayUrl(it) }
        return NostrCloneUrl.build(npub, identifier.trim(), hint)
    }

    fun canCreate(): Boolean =
        !isCreating &&
            name.isNotBlank() &&
            identifier.isNotBlank() &&
            !isIdentifierTaken &&
            invalidRelay == null &&
            isCommitValid

    fun draft() =
        NewGitRepository.Draft(
            name = name,
            identifier = identifier,
            description = description,
            graspServers = selectedGraspServers,
            cloneUrls = NewGitRepository.splitList(cloneUrls),
            webUrls = NewGitRepository.splitList(webUrls),
            relays = NewGitRepository.splitList(relays),
            earliestUniqueCommit = earliestCommit,
            hashtags = topics.toList() + NewGitRepository.splitList(topicDraft),
        )

    /**
     * Signs, sends and hands back the new repository's route, unless the identifier turns out to
     * be one of the user's repositories: publishing over it would replace it. The check uses the
     * list [init] fetched (waiting for it up to 10 s if it is still on its way) plus the cache.
     * It is best effort: a relay that never answers cannot report a repository it holds.
     */
    fun create(
        accountViewModel: BaseAccountViewModel,
        onCreated: (Route.GitRepository) -> Unit,
    ) {
        val account = account ?: return
        if (!canCreate()) return
        val draft = draft()
        val identifier = draft.identifier.trim()
        isCreating = true

        viewModelScope.launch {
            try {
                withTimeoutOrNull(10_000L) { ownIdentifiersJob?.join() }
                if (isIdentifierTaken || account.hasCachedGitRepository(identifier)) {
                    ownIdentifiers = ownIdentifiers + identifier
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    accountViewModel.reportSignerErrors {
                        val signed = account.signer.sign(NewGitRepository.build(draft, account.pubKey))
                        account.sendAutomatic(signed)
                        withContext(Dispatchers.Main) {
                            onCreated(Route.GitRepository(signed.kind, signed.pubKey, signed.dTag()))
                        }
                    }
                }
            } finally {
                isCreating = false
            }
        }
    }

    companion object {
        /** ngit's default GRASP servers, offered when the user has no kind:10317 list. */
        val DEFAULT_GRASP_SERVERS = listOf("relay.ngit.dev", "gitnostr.com")
    }
}
