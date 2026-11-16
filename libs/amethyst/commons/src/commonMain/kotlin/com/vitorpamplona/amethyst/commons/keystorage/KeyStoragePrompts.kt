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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The questions key storage has to ask the user: the password of the key file, or a key this
 * device does not keep. Headless: the storage suspends on these calls, and a UI (commonsUI's
 * `KeyStoragePromptHost`) shows [pending] and answers it. One question at a time.
 *
 * With no UI attached nothing answers, so a front end without one (the CLI, a test) must not
 * install this; pass `null` where a prompter is optional instead.
 */
class KeyStoragePrompts {
    private val oneAtATime = Mutex()
    private val _pending = MutableStateFlow<KeyPrompt?>(null)

    /** The question on screen now, or null. */
    val pending: StateFlow<KeyPrompt?> = _pending

    /** Asks for a new password for the key file. */
    suspend fun createPassword(): NewPasswordAnswer = ask { KeyPrompt.CreatePassword(it) }

    /** Asks for the key file's password; null when the user skips. */
    suspend fun unlock(wrongPassword: Boolean): String? = ask { KeyPrompt.Unlock(wrongPassword, it) }

    /** Asks for [npub]'s private key, which this device does not keep; null when the user skips. */
    suspend fun enterKey(
        npub: String,
        wrongKey: Boolean,
    ): String? = ask { KeyPrompt.EnterKey(npub, wrongKey, it) }

    private suspend fun <T> ask(build: (CompletableDeferred<T>) -> KeyPrompt): T =
        oneAtATime.withLock {
            val answer = CompletableDeferred<T>()
            val prompt = build(answer)
            _pending.value = prompt
            try {
                answer.await()
            } finally {
                _pending.compareAndSet(prompt, null)
            }
        }
}

/** What the user chose when asked for a new key-file password. */
sealed interface NewPasswordAnswer {
    data class Password(
        val password: String,
    ) : NewPasswordAnswer

    /** Keep no key on this device: it is asked for at each launch instead. */
    data object DontRemember : NewPasswordAnswer
}

/** One question to the user. Answer it exactly once. */
sealed class KeyPrompt {
    class CreatePassword internal constructor(
        private val answer: CompletableDeferred<NewPasswordAnswer>,
    ) : KeyPrompt() {
        fun answer(value: NewPasswordAnswer) {
            answer.complete(value)
        }
    }

    class Unlock internal constructor(
        val wrongPassword: Boolean,
        private val answer: CompletableDeferred<String?>,
    ) : KeyPrompt() {
        fun answer(password: String?) {
            answer.complete(password)
        }
    }

    class EnterKey internal constructor(
        val npub: String,
        val wrongKey: Boolean,
        private val answer: CompletableDeferred<String?>,
    ) : KeyPrompt() {
        fun answer(key: String?) {
            answer.complete(key)
        }
    }
}
