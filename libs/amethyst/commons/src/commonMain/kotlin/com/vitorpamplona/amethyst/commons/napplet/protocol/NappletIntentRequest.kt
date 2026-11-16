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
package com.vitorpamplona.amethyst.commons.napplet.protocol

/**
 * A NAP-INTENT (`intent.*`) request, decoded by [NappletProtocolJson.decodeIntentRequest]. Like
 * NAP-INC it never reaches the broker: the host's `NappletIntentDispatcher` resolves the handler
 * among the napplets the user installed, starts it and delivers to it.
 */
sealed interface NappletIntentRequest {
    /**
     * `intent.invoke`: hand [convention] (`napplet:<archetype>/<action>`, queryless) to a handler.
     * [payload] is raw JSON (null when absent); [handler] is `default`, `choose`, or a catalog id;
     * [hintAddress] a recommended `35129:<pubkey>:<d>` handler.
     */
    data class Invoke(
        val id: String,
        val archetype: String,
        val action: String,
        val convention: String,
        val payload: String?,
        val handler: String?,
        val hintAddress: String?,
    ) : NappletIntentRequest

    /** `intent.available`: the installed handlers of [archetype]. */
    data class Available(
        val id: String,
        val archetype: String,
    ) : NappletIntentRequest

    /** `intent.handlers`: the installed handlers of every archetype. */
    data class Handlers(
        val id: String,
    ) : NappletIntentRequest

    /**
     * Amethyst's binding is up and can take `intent.deliver` (it keeps any it gets until the napplet
     * registers `onDelivery`). Sent by the runtime-provided shim when it installs the domain, so a
     * delivery waits for the target to be ready, as NAP-INTENT requires.
     */
    data object Ready : NappletIntentRequest
}

/** NAP-INTENT's `IntentContract`: one convention a handler accepts, with its advertised parameter names. */
data class IntentContract(
    val convention: String,
    val params: List<String> = emptyList(),
) {
    /** The action: the convention's segment after `napplet:<archetype>/`. */
    val action: String get() = convention.substringAfter('/')
}

/** NAP-INTENT's `IntentCandidate`: an installed handler of one archetype, by its catalog [id]. */
data class IntentCandidate(
    val id: String,
    val title: String?,
    val contracts: List<IntentContract>,
    val isDefault: Boolean,
)

/** NAP-INTENT's `IntentAvailability` for [archetype]. */
data class IntentAvailability(
    val archetype: String,
    val candidates: List<IntentCandidate>,
)
