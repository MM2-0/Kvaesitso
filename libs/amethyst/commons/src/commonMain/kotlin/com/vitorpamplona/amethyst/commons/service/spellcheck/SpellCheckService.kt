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
package com.vitorpamplona.amethyst.commons.service.spellcheck

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** A word the engine rejected, at [start] (inclusive) to [end] (exclusive) of the checked text. */
@Immutable
data class Misspelling(
    val start: Int,
    val end: Int,
    val word: String,
)

/** Words the user taught the checker. Kept by the platform, read once and rewritten on change. */
interface UserDictionary {
    fun load(): Set<String>

    fun save(words: Set<String>)

    object None : UserDictionary {
        override fun load(): Set<String> = emptySet()

        override fun save(words: Set<String>) {}
    }
}

/**
 * Spell checking for the text fields, on top of whichever [SpellChecker] the platform picked.
 *
 * It adds what every engine needs and none provides the same way: the Nostr-aware
 * [SpellCheckTokenizer], the [NostrVocabulary], the user's own dictionary (kept by Amethyst rather
 * than the OS, so it survives switching engines and teaches nothing to other apps), and a cache so
 * a keystroke only sends the new word to the engine.
 *
 * Every engine call runs on [dispatcher], which must be single-threaded: engines are not
 * thread-safe, and the native ones are slow to cross into.
 */
class SpellCheckService(
    private val dispatcher: CoroutineDispatcher,
    private val userDictionary: UserDictionary = UserDictionary.None,
    private val onActivate: () -> Unit = {},
) {
    private var engine: SpellChecker? = null

    private val _activeEngine = MutableStateFlow<String?>(null)

    /** The engine in use, by [SpellChecker.name], or null when spell checking is off. */
    val activeEngine: StateFlow<String?> = _activeEngine.asStateFlow()

    private val _revision = MutableStateFlow(0)

    /** Bumps whenever earlier answers may be stale: a new engine, or a word added to the dictionary. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private var learned: MutableSet<String>? = null

    private val verdicts = LruCache<String, Boolean>(VERDICT_CACHE_SIZE)
    private val suggestionCache = LruCache<String, List<String>>(SUGGESTION_CACHE_SIZE)

    /**
     * Called by each text field that wants spell checking. The platform loads its engine on the
     * first call, so nothing is loaded for users who never open a composer. Safe to call often.
     */
    fun activate() = onActivate()

    suspend fun setEngine(checker: SpellChecker?) =
        withContext(dispatcher) {
            engine = checker
            verdicts.clear()
            suggestionCache.clear()
            _activeEngine.value = checker?.name
            _revision.value++
        }

    /** The misspelled words in [text], in order. Empty when no engine is set. */
    suspend fun findMisspellings(text: String): List<Misspelling> =
        withContext(dispatcher) {
            val checker = engine ?: return@withContext emptyList()
            val ranges = SpellCheckTokenizer.words(text)
            val words = ranges.map { text.substring(it.start, it.end) }

            // This check's answers, apart from the cache so a long text cannot evict its own.
            val answers = HashMap<String, Boolean>()
            val unknown = ArrayList<String>()
            for (word in words) {
                if (word in answers) continue
                val known = verdicts.get(word) ?: answerWithoutEngine(checker, word)
                if (known != null) {
                    answers[word] = known
                } else {
                    answers[word] = true
                    unknown.add(word)
                }
            }

            // The engine hears only new words, in batches: one crossing into a native checker each.
            unknown.chunked(ENGINE_BATCH_SIZE).forEach { batch ->
                // A newer keystroke cancelled this check: stop instead of finishing a stale one.
                ensureActive()
                val verdict =
                    try {
                        checker.areCorrect(batch.map(::normalize))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // Words the engine choked on are not the user's mistake.
                        Log.w(TAG, "Spell check failed for ${batch.size} words", e)
                        BooleanArray(batch.size) { true }
                    }
                batch.forEachIndexed { index, word ->
                    answers[word] = verdict[index]
                    verdicts.put(word, verdict[index])
                }
            }

            ranges.indices.mapNotNull { index ->
                val word = words[index]
                if (answers[word] == false) Misspelling(ranges[index].start, ranges[index].end, word) else null
            }
        }

    suspend fun suggestions(word: String): List<String> =
        withContext(dispatcher) {
            val checker = engine ?: return@withContext emptyList()
            suggestionCache.get(word)?.let { return@withContext it }
            val result =
                try {
                    checker.suggest(normalize(word)).filter { it != word }.take(MAX_SUGGESTIONS)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Suggestions failed for a word", e)
                    emptyList()
                }
            suggestionCache.put(word, result)
            result
        }

    suspend fun addToDictionary(word: String) =
        withContext(dispatcher) {
            val words = learnedWords()
            // Stored as looked up: "y’all" and "y'all" are the same word.
            val learnedWord = normalize(word).lowercase()
            if (words.add(learnedWord)) {
                try {
                    userDictionary.save(words.toSet())
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Could not save the user dictionary", e)
                }
                // Only this word's answers change; every other cached verdict still holds.
                verdicts.removeIf { normalize(it).lowercase() == learnedWord }
                _revision.value++
            }
        }

    /** Settles [word] without the engine when it can: Nostr words, learned words, and scripts the engine cannot judge. */
    private fun answerWithoutEngine(
        checker: SpellChecker,
        word: String,
    ): Boolean? {
        val normalized = normalize(word)
        val accepted =
            NostrVocabulary.contains(normalized) ||
                normalized.lowercase() in learnedWords() ||
                !checker.handles(normalized)
        return if (accepted) true else null
    }

    private fun learnedWords(): MutableSet<String> =
        learned ?: try {
            userDictionary.load().mapTo(HashSet()) { normalize(it).lowercase() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not read the user dictionary", e)
            HashSet()
        }.also { learned = it }

    // Dictionaries spell contractions with the ASCII apostrophe; keyboards often type U+2019.
    private fun normalize(word: String) = word.replace('’', '\'')

    companion object {
        private const val TAG = "SpellCheckService"
        private const val VERDICT_CACHE_SIZE = 8192
        private const val SUGGESTION_CACHE_SIZE = 256
        const val MAX_SUGGESTIONS = 5

        // Small enough that a native checker's thread (macOS's main thread) is never held long.
        private const val ENGINE_BATCH_SIZE = 64
    }
}

/** A small least-recently-used map. Only touched from the service's single thread. */
private class LruCache<K, V>(
    private val capacity: Int,
) {
    private val map = LinkedHashMap<K, V>()

    fun get(key: K): V? {
        val value = map.remove(key) ?: return null
        map[key] = value
        return value
    }

    fun put(
        key: K,
        value: V,
    ) {
        map.remove(key)
        map[key] = value
        if (map.size > capacity) {
            map.remove(map.keys.first())
        }
    }

    fun removeIf(predicate: (K) -> Boolean) {
        map.keys.removeAll(predicate)
    }

    fun clear() = map.clear()
}
