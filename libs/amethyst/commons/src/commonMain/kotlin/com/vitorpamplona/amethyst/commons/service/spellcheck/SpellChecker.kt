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

/**
 * One spelling engine: the operating system's checker or a bundled dictionary.
 *
 * Calls block and need not be thread-safe: [SpellCheckService] runs every call on its own
 * single-thread dispatcher.
 */
interface SpellChecker {
    /** Shown in logs and diagnostics, e.g. "macOS", "Windows (en-US)", "Hunspell (en_US)". */
    val name: String

    /**
     * Whether this engine can judge [word] at all. A bundled English dictionary cannot judge a
     * Cyrillic word and would flag every one, so it says no and the word is left alone.
     */
    fun handles(word: String): Boolean = true

    fun isCorrect(word: String): Boolean

    /**
     * [isCorrect] for each of [words]. Engines that are costly to cross into (a native checker on
     * another thread) override it to answer the whole batch in one crossing.
     */
    fun areCorrect(words: List<String>): BooleanArray = BooleanArray(words.size) { isCorrect(words[it]) }

    /** Replacements for a misspelled [word], best first. */
    fun suggest(word: String): List<String>
}
