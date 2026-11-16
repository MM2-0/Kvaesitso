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

import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag

/**
 * A provider the setup screen offers. Only [url] reaches the event; the rest is UI.
 * Every one serves the OpenAI Chat Completions format at `{url}/chat/completions`.
 */
data class AgentProviderPreset(
    val id: String,
    val name: String,
    val url: String,
    /** Local runners take no key. */
    val needsKey: Boolean = true,
    /** The provider's page for creating a key, opened by the screen's "Get a key". */
    val keyPage: String? = null,
    /** Runs on the user's own machine: only reachable from that device. */
    val isLocal: Boolean = false,
    /**
     * The cheapest call, under [url], that fails on a bad key. `/models` for most providers;
     * null where every free call answers without checking the key (checked 2026-11-10).
     */
    val keyCheckPath: String? = "/models",
    /** Two letters that tell it apart from the other presets (OpenRouter, OpenAI and Ollama all start with O). */
    val monogram: String = name.take(2).uppercase(),
    /** Where the picker lists it. */
    val group: AgentProviderGroup = AgentProviderGroup.MORE,
)

/** How the provider picker groups presets: the main ones first, the rest one tap away. */
enum class AgentProviderGroup { MAIN, MORE, LOCAL, CUSTOM }

object AgentProviderPresets {
    // OpenRouter lists models without a key; `/key` describes the key and 401s on a bad one.
    val OPENROUTER = AgentProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", keyPage = "https://openrouter.ai/keys", keyCheckPath = "/key", monogram = "OR", group = AgentProviderGroup.MAIN)
    val CUSTOM = AgentProviderPreset("custom", "Custom", "", needsKey = false, monogram = "+", group = AgentProviderGroup.CUSTOM)

    /** OpenRouter first: one key reaches every major model and can be capped. Custom last. */
    val ALL =
        listOf(
            OPENROUTER,
            AgentProviderPreset("anthropic", "Anthropic", "https://api.anthropic.com/v1", keyPage = "https://console.anthropic.com/settings/keys", monogram = "AN", group = AgentProviderGroup.MAIN),
            AgentProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", keyPage = "https://platform.openai.com/api-keys", monogram = "OA", group = AgentProviderGroup.MAIN),
            AgentProviderPreset("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", keyPage = "https://aistudio.google.com/apikey", monogram = "GE", group = AgentProviderGroup.MAIN),
            AgentProviderPreset("routstr", "Routstr", "https://api.routstr.com/v1", monogram = "RS", group = AgentProviderGroup.MAIN),
            AgentProviderPreset("ppq", "PPQ.ai", "https://api.ppq.ai/v1", keyPage = "https://ppq.ai", keyCheckPath = null, monogram = "PQ", group = AgentProviderGroup.MAIN),
            AgentProviderPreset("venice", "Venice", "https://api.venice.ai/api/v1", keyPage = "https://venice.ai/settings/api", keyCheckPath = "/api_keys/rate_limits", monogram = "VE"),
            AgentProviderPreset("nearai", "NEAR AI", "https://cloud-api.near.ai/v1", keyPage = "https://cloud.near.ai", keyCheckPath = "/users/me", monogram = "NE"),
            AgentProviderPreset("mistral", "Mistral", "https://api.mistral.ai/v1", keyPage = "https://console.mistral.ai/api-keys", monogram = "MI"),
            AgentProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1", keyPage = "https://console.groq.com/keys", monogram = "GQ"),
            AgentProviderPreset("xai", "xAI", "https://api.x.ai/v1", keyPage = "https://console.x.ai", monogram = "XA"),
            AgentProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", keyPage = "https://platform.deepseek.com/api_keys", monogram = "DS"),
            AgentProviderPreset("together", "Together", "https://api.together.xyz/v1", keyPage = "https://api.together.ai/settings/api-keys", monogram = "TO"),
            AgentProviderPreset("fireworks", "Fireworks", "https://api.fireworks.ai/inference/v1", keyPage = "https://fireworks.ai/account/api-keys", monogram = "FW"),
            AgentProviderPreset("cerebras", "Cerebras", "https://api.cerebras.ai/v1", keyPage = "https://cloud.cerebras.ai", monogram = "CB"),
            AgentProviderPreset("ollama", "Ollama", "http://localhost:11434/v1", needsKey = false, isLocal = true, monogram = "OL", group = AgentProviderGroup.LOCAL),
            AgentProviderPreset("lmstudio", "LM Studio", "http://localhost:1234/v1", needsKey = false, isLocal = true, monogram = "LM", group = AgentProviderGroup.LOCAL),
            CUSTOM,
        )

    /** The preset whose URL this is, ignoring a trailing slash and case; null for a custom endpoint. */
    fun forUrl(url: String): AgentProviderPreset? {
        val normalized = normalizeUrl(url)
        return ALL.firstOrNull { it.url.isNotEmpty() && normalizeUrl(it.url) == normalized }
    }

    /** Two letters for a saved key: its preset's monogram, or the host's first two. */
    fun monogramFor(url: String): String =
        forUrl(url)?.monogram
            ?: hostOf(url)
                ?.filter { it.isLetterOrDigit() }
                ?.take(2)
                ?.uppercase()
                ?.ifEmpty { null }
            ?: "?"

    /** On the user's own machine: a local preset, or a loopback host. */
    fun isLocalUrl(url: String): Boolean {
        if (forUrl(url)?.isLocal == true) return true
        val host = hostOf(url)?.lowercase() ?: return false
        val name = if (host.startsWith("[")) host.substringBefore(']') + "]" else host.substringBefore(':')
        return name == "localhost" || name.startsWith("127.") || name == "[::1]"
    }

    /** A name for a saved key: the preset's, or the URL's host. */
    fun displayName(url: String): String = forUrl(url)?.name ?: hostOf(url) ?: url

    fun normalizeUrl(url: String) = url.trim().trimEnd('/').lowercase()

    fun hostOf(url: String): String? =
        url
            .trim()
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .ifBlank { null }

    /**
     * An http(s) URL with a host that NIP-FA lets [key] go to: `https`, or `http` only to this
     * device (loopback) or with no key at all. The only shape the event accepts from this screen.
     */
    fun isValidUrl(
        url: String,
        key: String = "",
    ): Boolean {
        val trimmed = url.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        if (scheme != "https" && scheme != "http") return false
        if (hostOf(trimmed) == null || trimmed.any { it.isWhitespace() }) return false
        return AgentKeyTag.isUrlAllowed(key, trimmed)
    }

    /**
     * `sk-or-v1-…a1b2`: enough to tell keys apart, never enough to use one. At most the
     * provider's prefix (`sk-`, `sk-or-v1-`, `sk-ant-`: the dash-ended start, up to 9
     * characters) and the last 4, and never more than a third of the key in all: a short key
     * shows less, down to nothing but the ellipsis.
     */
    fun maskKey(key: String): String {
        if (key.isEmpty()) return ""
        val budget = key.length / 3
        val prefix = key.take(MASK_PREFIX_MAX).substringBeforeLast('-', "").let { if (it.isEmpty()) "" else "$it-" }
        val tail = minOf(MASK_TAIL, budget)
        val shownPrefix = prefix.takeIf { it.length + tail <= budget }.orEmpty()
        return shownPrefix + "…" + key.takeLast(tail)
    }

    private const val MASK_PREFIX_MAX = 10
    private const val MASK_TAIL = 4
}
