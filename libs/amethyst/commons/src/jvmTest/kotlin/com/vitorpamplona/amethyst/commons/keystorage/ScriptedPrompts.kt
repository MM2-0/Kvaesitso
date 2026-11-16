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
package com.vitorpamplona.amethyst.commons.keystorage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Answers [prompts] from a script, one entry per question in order, and records what was asked.
 * An entry is the answer for that question: a [NewPasswordAnswer] for CreatePassword, a String or
 * null for Unlock and EnterKey.
 */
class ScriptedPrompts(
    vararg answers: Any?,
) {
    val prompts = KeyStoragePrompts()
    val asked = mutableListOf<KeyPrompt>()
    private val queue = ArrayDeque(answers.toList())

    fun start(scope: CoroutineScope): Job =
        scope.launch(Dispatchers.Default) {
            prompts.pending.filterNotNull().collect { prompt ->
                asked += prompt
                check(queue.isNotEmpty()) { "Unexpected prompt $prompt" }
                when (prompt) {
                    is KeyPrompt.CreatePassword -> prompt.answer(queue.removeFirst() as NewPasswordAnswer)
                    is KeyPrompt.Unlock -> prompt.answer(queue.removeFirst() as String?)
                    is KeyPrompt.EnterKey -> prompt.answer(queue.removeFirst() as String?)
                }
            }
        }
}
