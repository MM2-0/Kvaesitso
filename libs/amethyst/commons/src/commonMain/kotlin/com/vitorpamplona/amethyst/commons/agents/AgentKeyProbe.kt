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
package com.vitorpamplona.amethyst.commons.agents

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag

/** What `GET {url}/models` said about a key. */
sealed interface AgentKeyProbeResult {
    /**
     * The provider answered. [modelCount] is null when the answer wasn't a models list.
     * [keyChecked] is false for providers whose free calls don't look at the key: the URL
     * works, but a bad key only shows up on first use.
     */
    data class Ok(
        val modelCount: Int?,
        val keyChecked: Boolean = true,
    ) : AgentKeyProbeResult

    /** 401/403, or a 400 that blames the key (xAI, Gemini): wrong, revoked or out of credit. */
    data class Rejected(
        val code: Int,
    ) : AgentKeyProbeResult

    /** Any other HTTP status. */
    data class Failed(
        val code: Int,
    ) : AgentKeyProbeResult

    /** No answer: offline, a wrong URL, or a local runner on another machine. */
    data object Unreachable : AgentKeyProbeResult

    /** This platform can't make the call yet. */
    data object Unsupported : AgentKeyProbeResult
}

/**
 * Checks [key] against its provider with one free GET: the preset's
 * [AgentProviderPreset.keyCheckPath], else `{url}/models`, which every OpenAI-compatible API
 * serves. Spends nothing.
 */
expect suspend fun IRoleBasedHttpClientBuilder.probeAgentKey(key: AgentKeyTag): AgentKeyProbeResult
