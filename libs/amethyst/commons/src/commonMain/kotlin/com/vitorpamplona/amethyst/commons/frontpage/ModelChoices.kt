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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable

/** One model as the picker lists it: a name a person reads, and the [id] that is sent. */
@Immutable
data class ModelChoice(
    val id: String,
    val name: String,
)

/**
 * The models of one vendor ([vendor] is the part of the id before `/`, or what the id's name
 * says, "" when neither does), named [label] ("Anthropic", "OpenAI").
 */
@Immutable
data class ModelVendorGroup(
    val vendor: String,
    val label: String,
    val models: List<ModelChoice>,
)

/**
 * The model picker's rules, kept out of the composable so they are tested: models grouped by
 * vendor in the ranked order they came in, readable names, and the search a long list (OpenRouter
 * lists hundreds) needs. A vendor nobody can tell is "" and goes last; the screen names it.
 */
object ModelChoices {
    /** From this many models on, the picker offers a search field. */
    const val SEARCH_FROM = 12

    private val LABELS =
        mapOf(
            "anthropic" to "Anthropic",
            "openai" to "OpenAI",
            "google" to "Google",
            "meta-llama" to "Meta",
            "meta" to "Meta",
            "mistralai" to "Mistral",
            "mistral" to "Mistral",
            "x-ai" to "xAI",
            "xai" to "xAI",
            "deepseek" to "DeepSeek",
            "qwen" to "Qwen",
            "cohere" to "Cohere",
            "microsoft" to "Microsoft",
            "nvidia" to "NVIDIA",
            "amazon" to "Amazon",
            "perplexity" to "Perplexity",
            "moonshotai" to "Moonshot AI",
            "z-ai" to "Z.ai",
            "nousresearch" to "Nous Research",
            "openrouter" to "OpenRouter",
            "ai21" to "AI21",
            "minimax" to "MiniMax",
            "ibm-granite" to "IBM",
            "baidu" to "Baidu",
        )

    /** Ids with no vendor prefix, as a provider's own API lists them: "claude-…" is Anthropic's. */
    private val BY_NAME =
        listOf(
            Regex("^claude") to "anthropic",
            Regex("^(gpt|chatgpt|o\\d|text-|davinci|dall-e|whisper|tts-|omni-)") to "openai",
            Regex("^(gemini|gemma|learnlm)") to "google",
            Regex("^(llama|codellama)") to "meta",
            Regex("^(mistral|mixtral|codestral|ministral|magistral|devstral|pixtral|open-mistral)") to "mistral",
            Regex("^grok") to "xai",
            Regex("^deepseek") to "deepseek",
            Regex("^(qwen|qwq)") to "qwen",
            Regex("^(command|c4ai)") to "cohere",
            Regex("^phi") to "microsoft",
        )

    private val UPPER = setOf("gpt", "glm", "oss", "vl", "r1", "ai", "it")

    /** Words a vendor writes its own way. */
    private val CASED = mapOf("deepseek" to "DeepSeek", "chatgpt" to "ChatGPT", "qwq" to "QwQ", "openai" to "OpenAI")
    private val SIZE = Regex("^\\d+(\\.\\d+)?[bkmBKM]$")

    /** Release dates in ids: "-2024-08-06", "-20250929", "-2411" (year and month). */
    private val DATE = Regex("[-_](\\d{4}-\\d{2}-\\d{2}|\\d{8}|\\d{4})(?=$|[-_])")
    private val SHORT_NUMBER = Regex("^\\d{1,2}$")
    private val VERSION = Regex("^\\d+(\\.\\d+)*$")
    private val O_SERIES = Regex("^[oO]\\d+$")

    /** The vendor key of [id]: before its `/`, else read off its name; "" when it can't be told. */
    fun vendorOf(id: String): String {
        val slash = id.indexOf('/')
        if (slash > 0) return id.substring(0, slash).lowercase()
        val bare = id.lowercase()
        return BY_NAME.firstOrNull { (pattern, _) -> pattern.containsMatchIn(bare) }?.second ?: ""
    }

    /** "Anthropic", "OpenAI"; an unknown vendor key as words ("liquid" -> "Liquid"); "" stays "". */
    fun vendorLabel(vendor: String): String = LABELS[vendor] ?: vendor.split('-', '_').filter { it.isNotEmpty() }.joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }

    /**
     * A name to read: the endpoint's own [listed] name without its "Vendor: " prefix, else the id
     * in words ("anthropic/claude-sonnet-4-5-20250929" -> "Claude Sonnet 4.5").
     */
    fun readableName(
        id: String,
        listed: String? = null,
    ): String {
        listed?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            val colon = name.indexOf(": ")
            return if (colon in 1..39) name.substring(colon + 2).trim().ifEmpty { name } else name
        }
        val bare = id.substringAfterLast('/')
        val variant = bare.substringAfter(':', "").takeIf { it.isNotEmpty() && it != "latest" }
        val words =
            bare
                .substringBefore(':')
                .replace(DATE, "")
                .split('-', '_', ' ')
                .filter { it.isNotEmpty() && it != "latest" }
        if (words.isEmpty()) return id
        val joined = mutableListOf<String>()
        for (word in words) {
            val last = joined.lastOrNull()
            // "4", "5" -> "4.5": versions are split by the same dash as the words.
            if (last != null && SHORT_NUMBER.matches(word) && VERSION.matches(last)) {
                joined[joined.lastIndex] = "$last.$word"
            } else {
                joined += word
            }
        }
        val name =
            joined.joinToString(" ") { word ->
                when {
                    word.lowercase() in UPPER -> word.uppercase()
                    word.lowercase() in CASED -> CASED.getValue(word.lowercase())
                    // OpenAI's reasoning models are written lower case: o3, o4-mini.
                    O_SERIES.matches(word) -> word.lowercase()
                    SIZE.matches(word) -> word.uppercase()
                    word.first().isLetter() -> word.replaceFirstChar { it.uppercaseChar() }
                    else -> word
                }
            }
        return if (variant != null) "$name ($variant)" else name
    }

    /** [ids] in their order, as choices with names from [byId] where the endpoint gave them. */
    fun choices(
        ids: List<String>,
        byId: Map<String, AgentModel> = emptyMap(),
    ): List<ModelChoice> = ids.map { ModelChoice(it, readableName(it, byId[it]?.name)) }

    /**
     * [ids] (ranked: the best first) grouped by vendor, each group in that order and the groups
     * by their best model; "" (unknown) last. Vendors with the same label ("meta", "meta-llama")
     * are one group. Only what matches [query] ([matches]); a group left empty goes.
     */
    fun group(
        ids: List<String>,
        byId: Map<String, AgentModel> = emptyMap(),
        query: String = "",
    ): List<ModelVendorGroup> = catalog(ids, byId).group(query)

    /** [ids] read once (names, vendors, what a search looks in), for a picker that filters them as the reader types. */
    fun catalog(
        ids: List<String>,
        byId: Map<String, AgentModel> = emptyMap(),
    ): ModelCatalog =
        ModelCatalog(
            choices(ids, byId).map { choice ->
                val vendor = vendorOf(choice.id)
                val label = vendorLabel(vendor)
                ModelCatalog.Entry(choice, vendor, label, "${choice.id} ${choice.name} $label".lowercase())
            },
        )

    /** Every word of [query] is in the id, the name or the vendor, ignoring case. */
    fun matches(
        choice: ModelChoice,
        vendorLabel: String,
        query: String,
    ): Boolean {
        val words =
            query
                .trim()
                .lowercase()
                .split(' ')
                .filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = "${choice.id} ${choice.name} $vendorLabel".lowercase()
        return words.all { it in hay }
    }

    /** [query] as the lower-case words a search must all find. */
    internal fun words(query: String): List<String> =
        query
            .trim()
            .lowercase()
            .split(' ')
            .filter { it.isNotEmpty() }

    /**
     * What the reader typed, as a model id to use when the list doesn't hold it (a provider that
     * lists only some of its models): one word, and not already one of [ids]. Null otherwise.
     */
    fun typedId(
        query: String,
        ids: List<String>,
    ): String? {
        val typed = query.trim()
        if (typed.isEmpty() || typed.any { it.isWhitespace() }) return null
        return typed.takeIf { it !in ids }
    }
}

/**
 * One endpoint's models, read once: each choice with its vendor, the vendor's label and the
 * lower-case text a search looks in. A keystroke only filters ([group]); nothing is named or
 * matched against a pattern again.
 */
@Immutable
class ModelCatalog(
    val entries: List<Entry>,
) {
    @Immutable
    data class Entry(
        val choice: ModelChoice,
        val vendor: String,
        val label: String,
        val haystack: String,
    )

    /** The groups [ModelChoices.group] makes, of the entries every word of [query] is found in. */
    fun group(query: String = ""): List<ModelVendorGroup> {
        val words = ModelChoices.words(query)
        val groups = LinkedHashMap<String, MutableList<ModelChoice>>()
        val vendors = HashMap<String, String>()
        entries.forEach { entry ->
            if (words.isNotEmpty() && !words.all { it in entry.haystack }) return@forEach
            vendors.getOrPut(entry.label) { entry.vendor }
            groups.getOrPut(entry.label) { mutableListOf() } += entry.choice
        }
        val known = groups.filterKeys { it.isNotEmpty() }.map { (label, models) -> ModelVendorGroup(vendors.getValue(label), label, models) }
        val unknown = groups[""]?.let { listOf(ModelVendorGroup("", "", it)) }.orEmpty()
        return known + unknown
    }
}
