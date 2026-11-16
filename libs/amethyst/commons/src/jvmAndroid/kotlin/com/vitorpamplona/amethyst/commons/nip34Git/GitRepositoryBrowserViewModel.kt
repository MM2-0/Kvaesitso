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
package com.vitorpamplona.amethyst.commons.nip34Git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.nip34Git.state.GitSignedState
import com.vitorpamplona.quartz.nip34Git.git.GitBlame
import com.vitorpamplona.quartz.nip34Git.git.GitCommit
import com.vitorpamplona.quartz.nip34Git.git.GitContentMatch
import com.vitorpamplona.quartz.nip34Git.git.GitHttpAuth
import com.vitorpamplona.quartz.nip34Git.git.GitHttpClient
import com.vitorpamplona.quartz.nip34Git.git.GitHttpException
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

/** UI state for the git repository code/README browser. */
sealed interface GitBrowseState {
    object Loading : GitBrowseState

    /**
     * [mismatch] is set when no clone URL served the commit the maintainers signed for this branch
     * (kind 30618): the snapshot comes from a server that may be stale, or not theirs.
     */
    class Loaded(
        val snapshot: GitRepoSnapshot,
        val mismatch: GitStateMismatch? = null,
    ) : GitBrowseState

    /**
     * Nothing could be loaded. [isEmptyRepository] is true when a clone URL answered that the
     * repository does not exist yet (HTTP 404, as a GRASP server does before the first push) or
     * has no branch, and no URL failed for another reason: the repository is waiting for code,
     * rather than unreachable.
     */
    class Error(
        val message: String,
        val isEmptyRepository: Boolean = false,
    ) : GitBrowseState
}

/** The maintainers signed [signedCommit] as the tip of [branch]; the server shown serves [servedCommit]. */
class GitStateMismatch(
    val branch: String,
    val signedCommit: String,
    val servedCommit: String,
)

/**
 * Loads a shallow snapshot of a NIP-34 repository over git smart-HTTP and serves
 * both the README tab and the Code browser. The snapshot lets the UI walk
 * directories offline; [readBlob] lazily fetches file contents on demand.
 */
class GitRepositoryBrowserViewModel(
    private val okHttpClient: (String) -> OkHttpClient,
    /** Signs the requests of a private repository (GRASP-08); null when none needs it. */
    auth: GitHttpAuth? = null,
) : ViewModel() {
    private val client = GitHttpClient(okHttpClient, auth)

    private val _state = MutableStateFlow<GitBrowseState>(GitBrowseState.Loading)
    val state = _state.asStateFlow()

    private var cloneUrls: List<String> = emptyList()
    private var started = false
    private var cacheKey: String? = null

    /**
     * The tips the repository's maintainers signed (kind 30618). With it, the default branch is the
     * one their HEAD names, and a server that serves their commit is preferred over one that
     * answers first. See [updateSignedState].
     */
    private var signedState: GitSignedState? = null

    /**
     * Follows the maintainers' signed state as the screen observes it. A newer one can name another
     * tip or branch, so a repository already loaded is loaded again against it.
     */
    fun updateSignedState(state: GitSignedState?) {
        if (state?.event?.id == signedState?.event?.id) return
        signedState = state
        if (!started) return
        // A newer state that signs what is already shown changes nothing on screen: a reload would
        // drop the open file, its blame and an edit in progress for the same tree.
        val shown = (_state.value as? GitBrowseState.Loaded)?.snapshot
        if (shown != null) {
            val ref = currentRef ?: state?.headBranch ?: shown.branch
            val wanted = state?.commitFor(ref)
            val sameBranch = currentRef != null || state?.headBranch == null || state.headBranch == shown.branch
            if (sameBranch && (wanted == null || wanted == shown.headCommit)) return
        }
        // Keep what is shown until the newer tree arrives.
        reload(keepShowing = shown != null)
    }

    /** The branch/tag currently displayed (null = the server's default branch). */
    var currentRef: String? = null
        private set

    /**
     * Loads the repository once, using the announcement's clone URLs. Safe to call
     * on every recomposition; only the first call (after the event has arrived) runs.
     *
     * When [cacheKey] (the repository address) has an already-fetched default-branch snapshot
     * in [GitRepoSnapshotCache], it is served synchronously and no new clone is performed — so
     * revisiting a repo across screens, and the share-to-image renderer, reuse it instantly.
     */
    fun loadOnce(
        cloneUrls: List<String>,
        cacheKey: String? = null,
    ) {
        if (started) return
        started = true
        this.cloneUrls = cloneUrls
        this.cacheKey = cacheKey

        // A cached snapshot is only as good as the server it came from: when the maintainers' signed
        // state is already known and names another tip, look again instead of showing it.
        val cached = GitRepoSnapshotCache.get(cacheKey)
        if (cached != null && agreesWithSignedState(cached)) {
            _state.value = GitBrowseState.Loaded(cached)
            return
        }
        reload()
    }

    /** Reloads the tree at [ref] (a branch or tag name; null = default branch). */
    fun switchRef(ref: String?) {
        if (ref == currentRef) return
        currentRef = ref
        reload()
    }

    fun reload() = reload(keepShowing = false)

    private fun reload(keepShowing: Boolean) {
        if (!keepShowing) _state.value = GitBrowseState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val servers = candidateServers()
            if (servers.isEmpty()) {
                _state.value = GitBrowseState.Error(NO_CLONE_URL)
                return@launch
            }
            val ref = currentRef ?: signedState?.headBranch

            // The first server usually serves what the maintainers signed, and opening one is a
            // shallow clone, so it goes alone. Only when it disagrees (or fails) are the mirrors
            // asked, all at once rather than one clone after another.
            val first = openServer(servers.first(), ref)
            if (first is Opened && first.mismatch == null) return@launch show(first)
            val rest = servers.drop(1)
            val others =
                coroutineScope {
                    val results = Channel<ServerResult>(rest.size)
                    val jobs = rest.map { server -> launch { results.send(openServer(server, ref)) } }
                    val collected = ArrayList<ServerResult>(rest.size)
                    repeat(rest.size) {
                        val result = results.receive()
                        collected.add(result)
                        if (result is Opened && result.mismatch == null) {
                            jobs.forEach { it.cancel() }
                            return@coroutineScope collected
                        }
                    }
                    collected
                }

            val all = listOf(first) + others
            all.firstOrNull { it is Opened && it.mismatch == null }?.let { return@launch show(it as Opened) }
            // No server serves the signed tip: show the first that answered, with the mismatch.
            all.firstOrNull { it is Opened }?.let { return@launch show(it as Opened) }

            val failures = all.filterIsInstance<Failed>()
            val isEmpty = failures.isNotEmpty() && failures.all { it.isEmptyRepository }
            _state.value = GitBrowseState.Error(failures.joinToString("\n") { it.message }, isEmptyRepository = isEmpty)
        }
    }

    private sealed interface ServerResult

    private class Opened(
        val snapshot: GitRepoSnapshot,
        val mismatch: GitStateMismatch?,
    ) : ServerResult

    private class Failed(
        val message: String,
        val isEmptyRepository: Boolean,
    ) : ServerResult

    private fun show(opened: Opened) {
        // Only the default branch is cached; named refs are transient selections.
        if (opened.mismatch == null && currentRef == null) GitRepoSnapshotCache.put(cacheKey, opened.snapshot)
        _state.value = GitBrowseState.Loaded(opened.snapshot, opened.mismatch)
    }

    /** True unless the maintainers signed another tip for [snapshot]'s branch. */
    private fun agreesWithSignedState(snapshot: GitRepoSnapshot): Boolean {
        val signedCommit = signedState?.commitFor(snapshot.branch ?: signedState?.headBranch) ?: return true
        return signedCommit == snapshot.headCommit
    }

    /** Opens one server, trying its URLs (as announced, then with `.git`) in order. */
    private suspend fun openServer(
        urls: List<String>,
        ref: String?,
    ): ServerResult {
        var last: Failed? = null
        for (url in urls) {
            try {
                val snapshot = client.open(url, ref)
                val branch = snapshot.branch ?: ref
                val signedCommit = signedState?.commitFor(branch)
                val mismatch = if (signedCommit == null || signedCommit == snapshot.headCommit) null else GitStateMismatch(branch ?: "", signedCommit, snapshot.headCommit)
                return Opened(snapshot, mismatch)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                last = Failed(url + " → " + (e.message ?: e.toString()), e is GitHttpException && (e.noBranches || e.httpCode == 404))
            }
        }
        return last ?: Failed(urls.joinToString(), false)
    }

    suspend fun readBlob(
        snapshot: GitRepoSnapshot,
        oid: String,
    ): ByteArray = snapshot.readBlob(oid)

    /** Recent commits ending at the snapshot's tip, most recent first. */
    suspend fun loadHistory(snapshot: GitRepoSnapshot): List<GitCommit> = withContext(Dispatchers.IO) { client.loadHistory(snapshot.cloneUrl, snapshot.headCommit) }

    /** The commits that changed the file at [path] (segments) before the snapshot's tip, newest first. */
    suspend fun fileHistory(
        snapshot: GitRepoSnapshot,
        path: List<String>,
    ): List<GitCommit> = withContext(Dispatchers.IO) { client.files.history(snapshot.cloneUrl, snapshot.headCommit, path).map { it.commit } }

    // Blame reads hundreds of commits and dozens of file versions: kept for the last few files, so
    // turning it off and on, or coming back from a file's history, does not read them again.
    private val blames =
        object : LinkedHashMap<String, GitBlame?>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GitBlame?>?) = size > MAX_CACHED_BLAMES
        }

    /** Who last changed each line of the file at [path], or null when it has no history here. */
    suspend fun blame(
        snapshot: GitRepoSnapshot,
        path: List<String>,
    ): GitBlame? {
        val key = snapshot.headCommit + ":" + path.joinToString("/")
        synchronized(blames) { if (blames.containsKey(key)) return blames[key] }
        val blame = withContext(Dispatchers.IO) { client.files.blame(snapshot.cloneUrl, snapshot.headCommit, path) }
        synchronized(blames) { blames[key] = blame }
        return blame
    }

    /** The last text search that read every file: what a longer query can be filtered from. */
    private class CompletedSearch(
        val headCommit: String,
        val needle: String,
        val matches: List<GitContentMatch>,
    )

    @Volatile private var lastSearch: CompletedSearch? = null

    /**
     * Lines of the snapshot's files that contain [query], fetched in batches
     * ([GitRepoSnapshot.searchContents]). A query that extends the last complete search ("foo"
     * after "fo") is answered from its matches, without reading the repository again.
     */
    suspend fun searchContents(
        snapshot: GitRepoSnapshot,
        query: String,
        onProgress: (read: Int, total: Int) -> Unit,
    ): List<GitContentMatch> {
        val needle = query.lowercase()
        lastSearch?.let { last ->
            // Only when nothing was cut: under the match limit, no line shortened for display, and
            // no space at the query's ends (the kept lines are trimmed).
            if (last.headCommit == snapshot.headCommit && needle == needle.trim() && needle.contains(last.needle) && last.matches.none { it.text.length >= MATCH_TEXT_LIMIT }) {
                return last.matches.filter { it.text.lowercase().contains(needle) }
            }
        }
        val matches = withContext(Dispatchers.IO) { snapshot.searchContents(query, limit = SEARCH_LIMIT, onProgress = onProgress) }
        // A blank query matches every line: nothing later narrows from it usefully.
        if (needle.isNotBlank() && matches.size < SEARCH_LIMIT) lastSearch = CompletedSearch(snapshot.headCommit, needle, matches)
        return matches
    }

    /** The diff a commit introduced (commit vs its first parent). */
    suspend fun commitDiff(
        snapshot: GitRepoSnapshot,
        commit: GitCommit,
    ): ParsedPatch =
        withContext(Dispatchers.IO) {
            client.computeDiff(snapshot.cloneUrl, commit.oid, commit.parents.firstOrNull())
        }

    /**
     * The http(s) clone URLs to try, one entry per server in announcement order: the URL as announced,
     * then with `.git` when it lacks one (the same server, so tried in turn, never at once).
     */
    private fun candidateServers(): List<List<String>> =
        cloneUrls
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
            .map { url -> if (url.removeSuffix("/").endsWith(".git")) listOf(url) else listOf(url, url.removeSuffix("/") + ".git") }

    companion object {
        const val NO_CLONE_URL = "no-clone-url"
        private const val MAX_CACHED_BLAMES = 8
        private const val SEARCH_LIMIT = 200

        // searchContents keeps the first 300 characters of a matching line.
        private const val MATCH_TEXT_LIMIT = 300
    }
}
