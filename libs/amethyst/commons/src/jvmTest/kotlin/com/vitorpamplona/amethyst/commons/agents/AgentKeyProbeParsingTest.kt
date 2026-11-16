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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentKeyProbeParsingTest {
    @Test
    fun badKeyAnswersAreRejections() {
        assertTrue(isKeyRejection(401, ""))
        assertTrue(isKeyRejection(403, ""))
        // xAI and Gemini, as they answered a bad key on 2026-11-10.
        assertTrue(isKeyRejection(400, """{"code":"invalid-argument","error":"Incorrect API key provided."}"""))
        assertTrue(isKeyRejection(400, """{"error":{"code":400,"message":"Please pass a valid API key"}}"""))
        assertFalse(isKeyRejection(400, """{"error":"model is required"}"""))
        assertFalse(isKeyRejection(500, "api key"))
    }

    @Test
    fun modelsListIsCounted() {
        assertEquals(2, countModels("""{"object":"list","data":[{"id":"a","owned_by":{"id":"x"}},{"id":"b"}]}"""))
        assertNull(countModels("""{"data":{"label":"my key"}}"""))
        assertNull(countModels("not json"))
    }

    @Test
    fun providersWithPublicModelListsCheckTheKeyElsewhere() {
        assertEquals("/key", AgentProviderPresets.OPENROUTER.keyCheckPath)
        assertNull(AgentProviderPresets.ALL.first { it.id == "ppq" }.keyCheckPath)
        assertEquals("/models", AgentProviderPresets.ALL.first { it.id == "openai" }.keyCheckPath)
    }
}
