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
import com.vitorpamplona.amethyst.commons.routstr.RoutstrJson
import com.vitorpamplona.amethyst.commons.routstr.SatsPricing
import com.vitorpamplona.amethyst.commons.util.formatDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.round

/*
 * The writer calls: one fixed system prompt, the digest as data, a plan and then one HTML
 * document back, sent with the reader's own AI key (kind 12000, NIP-FA) to an OpenAI Chat
 * Completions endpoint. The prompt is the Observer's editorial brief and house stylesheet
 * (MIT, see ./LICENSE.observer); the helpers match Brainstorm's writer.ts.
 */

/** The paper's house name: its masthead until an edition announces another ([standingPaper]). */
const val MASTHEAD = "The Nostr Observer"

/**
 * The system message, the same in every call of a print (the plan, the page, each repair), so a
 * provider that caches a prefix reads it once: the brief, then the house stylesheet for reference.
 * The page carries an empty `<style id="house">` that [insertHouseCss] fills.
 */
fun systemPrompt(): String = "$EDITORIAL_BRIEF\n\n## The house stylesheet\n\nFor reference: the app puts this into your page's `<style id=\"house\">`. Do not copy it.\n\n```css\n$HOUSE_CSS\n```"

/** The size of the day, as the dateline prints it: events read, the voices among them, and the events kept in the digest. */
class DaySize(
    val read: Int,
    val voices: Int,
    val kept: Int,
)

/**
 * What the writer is told about the edition, above the corpus and outside it, because it is ours:
 * the paper as the last edition left it, the reader, the language (a BCP 47 tag, "pt-BR": the
 * brief says what to do with one), the edition's number and code, the date and the window's close
 * on the reader's clock with the zone named, the size of the day and the last few front pages.
 */
fun editionHeader(
    readerName: String,
    language: String? = null,
    context: EditionContext = EditionContext(),
    corpus: Corpus? = null,
    size: DaySize? = null,
): String =
    buildList {
        add("Masthead: ${context.paper.masthead}")
        context.paper.motto?.let { add("Motto: $it") }
        if (context.paper.sections.isNotEmpty()) add("Standing sections: " + context.paper.sections.joinToString(" \u00b7 "))
        add("Reader: $readerName")
        language?.trim()?.ifEmpty { null }?.let { add("Language: $it") }
        context.number?.let { add("Edition: Vol. I \u00b7 No. $it") }
        corpus?.let {
            val clock = context.clock
            add(linksLine(it.links))
            add("Edition code: ${it.code}")
            add("Date: ${clock.date(it.until)}")
            add("Window: the 24 hours to ${clock.time(it.until)}, on the reader's clock (${clock.zoneLabel(it.until)}). Every time below is on that clock.")
        }
        size?.let { add("The day: ${it.read} events read, ${it.voices} voices, ${it.kept} kept in the digest.") }
        if (context.recent.isNotEmpty()) {
            add("Recent front pages (do not run a story again unless it moved):")
            context.recent.forEach { add("- ${longDay(it.day)}: ${it.headline}") }
        }
    }.joinToString("\n")

/**
 * The part of the user message that is the same in every call of a print, so a provider that
 * caches a prefix reads it once: the edition's header and the digest in its delimited block.
 */
fun corpusPrefix(
    digest: String,
    header: String,
): String = "$header\n\n<corpus>\n$digest\n</corpus>"

/** The single-pass message: the header, the digest in its block, the instruction. */
fun userMessage(
    digest: String,
    readerName: String,
    language: String? = null,
    context: EditionContext = EditionContext(),
): String = corpusPrefix(digest, editionHeader(readerName, language, context)) + "\n\n" + WRITE_INSTRUCTION

/** One repair round: only what the check found, and a request for the whole page again. */
fun repairMessage(violations: List<Violation>): String =
    buildList {
        add("The check found these to fix:")
        add("")
        violations.take(40).forEach { add("- ${it.kind}: ${it.detail}\n  ${it.excerpt.replace(Regex("\\s+"), " ")}") }
        add("")
        add("QUOTE: copy the words exactly from one event, or paraphrase without <q>/<blockquote>.")
        add("IMAGE: use <img src=\"art-N\"> ids from the shortlist only.")
        add("LINK: only the citation and byline forms the brief allows, on the two link bases you were given.")
        add("MARKUP: no scripts, forms, frames or handlers.")
        add("")
        add("Fix each one and send the whole corrected page back; nothing else about it needs to change. Keep <style id=\"house\"></style> empty: the app still puts the house stylesheet in.")
        add("Return the corrected complete HTML document and nothing else.")
    }.joinToString("\n")

/**
 * One message. [prefix], when set, is the part of the text a provider may cache (the header and
 * the digest, the same in every call of a print): sent first, marked where the provider takes a
 * mark ([cachesPrefixes]), else simply joined to [content].
 */
class ChatMessage(
    val role: String,
    val content: String,
    val prefix: String? = null,
) {
    /** The whole text, as a provider without caching reads it. */
    val text: String get() = if (prefix == null) content else prefix + content
}

class Usage(
    val promptTokens: Long?,
    val completionTokens: Long?,
) {
    val total get() = if (promptTokens == null && completionTokens == null) null else (promptTokens ?: 0) + (completionTokens ?: 0)

    operator fun plus(other: Usage?): Usage =
        if (other == null) {
            this
        } else {
            Usage(sum(promptTokens, other.promptTokens), sum(completionTokens, other.completionTokens))
        }

    private fun sum(
        a: Long?,
        b: Long?,
    ) = if (a == null && b == null) null else (a ?: 0) + (b ?: 0)
}

class Written(
    val text: String,
    val usage: Usage?,
)

/** The output limit of a page when the model's own isn't known. */
const val DEFAULT_OUTPUT_TOKENS = 32_000

/** The most a page call ever asks for, whatever the model allows. */
const val MAX_OUTPUT_TOKENS = 64_000

/** The least a page call asks for when only the context window is known: room for a page. */
const val MIN_PAGE_TOKENS = 8_000

/** Kept free of the context window, beyond the prompt's estimate, when the limit is worked out from it. */
private const val CONTEXT_MARGIN_TOKENS = 2_000

/** On Anthropic, Deep's thinking budget: at most this, and less than half the call's limit. */
const val DEEP_THINKING_TOKENS = 16_000

fun isAnthropic(url: String) = hostOf(url) == "api.anthropic.com"

fun isOpenRouter(url: String) = hostOf(url) == "openrouter.ai"

fun isOpenAI(url: String) = hostOf(url) == "api.openai.com"

/**
 * Whether a call to [model] at [url] marks the cacheable prefix with `cache_control`: only where
 * it is documented, OpenRouter's Anthropic models (explicit breakpoints on content blocks).
 * Anthropic's own OpenAI-compatible endpoint says prompt caching isn't supported there; OpenAI
 * and the rest cache a prefix on their own, unmarked.
 */
fun cachesPrefixes(
    url: String,
    model: String,
): Boolean = isOpenRouter(url) && model.lowercase().startsWith("anthropic/")

/**
 * A page call's output limit: the model's own (OpenRouter's `top_provider.max_completion_tokens`,
 * Anthropic's `max_tokens`) up to [MAX_OUTPUT_TOKENS]; else what its context window leaves after
 * [promptTokens], within [MIN_PAGE_TOKENS]..[MAX_OUTPUT_TOKENS]; else [DEFAULT_OUTPUT_TOKENS].
 */
fun outputTokenLimit(
    model: AgentModel?,
    promptTokens: Long,
): Int {
    model?.maxOutputTokens?.takeIf { it > 0 }?.let { return it.coerceAtMost(MAX_OUTPUT_TOKENS.toLong()).toInt() }
    model?.contextLength?.takeIf { it > 0 }?.let { context ->
        return (context - promptTokens - CONTEXT_MARGIN_TOKENS).coerceIn(MIN_PAGE_TOKENS.toLong(), MAX_OUTPUT_TOKENS.toLong()).toInt()
    }
    return DEFAULT_OUTPUT_TOKENS
}

/** A plan call's output limit: [PLAN_MAX_TOKENS], or the model's own when that is less. */
fun planTokenLimit(model: AgentModel?): Int =
    model
        ?.maxOutputTokens
        ?.takeIf { it > 0 }
        ?.coerceAtMost(PLAN_MAX_TOKENS.toLong())
        ?.toInt() ?: PLAN_MAX_TOKENS

/** Anthropic's thinking budget for Deep within a call limited to [maxTokens]: at least 1,024, under half the limit. */
fun anthropicThinkingBudget(maxTokens: Int): Int = minOf(DEEP_THINKING_TOKENS, maxTokens / 2).coerceAtLeast(1_024)

private fun hostOf(url: String) =
    url
        .substringAfter("://", "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore(':')
        .lowercase()

/**
 * One Chat Completions request. [maxTokens] goes out on every provider (OpenAI's own API takes it
 * as `max_completion_tokens`, which its reasoning models require); Anthropic's endpoint needs one,
 * so it gets [DEFAULT_OUTPUT_TOKENS] when none is given. [effort] Deep maps to each provider's
 * knob: OpenRouter's `reasoning: {effort: "high"}`, Anthropic's documented `thinking` budget, and
 * OpenAI's `reasoning_effort: "high"` elsewhere. Standard sends nothing: on OpenRouter a
 * `reasoning` object may turn thinking on (and bill it) for a model where it is optional, and a
 * model that reasons anyway streams it unasked, which [reasoningOf] reads when it comes.
 */
fun chatRequestBody(
    url: String,
    model: String,
    messages: List<ChatMessage>,
    stream: Boolean,
    maxTokens: Int? = null,
    effort: WritingEffort = WritingEffort.STANDARD,
): String =
    buildJsonObject {
        val marks = cachesPrefixes(url, model)
        put("model", model)
        put(
            "messages",
            buildJsonArray {
                messages.forEach { message ->
                    add(
                        buildJsonObject {
                            put("role", message.role)
                            if (marks && message.prefix != null) {
                                // OpenRouter's explicit breakpoint: everything through this block is the cached prefix.
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "text")
                                                put("text", message.prefix)
                                                put("cache_control", buildJsonObject { put("type", "ephemeral") })
                                            },
                                        )
                                        add(
                                            buildJsonObject {
                                                put("type", "text")
                                                put("text", message.content)
                                            },
                                        )
                                    },
                                )
                            } else {
                                put("content", message.text)
                            }
                        },
                    )
                }
            },
        )
        put("stream", stream)
        if (stream) put("stream_options", buildJsonObject { put("include_usage", true) })
        val limit = maxTokens ?: if (isAnthropic(url)) DEFAULT_OUTPUT_TOKENS else null
        if (limit != null) put(if (isOpenAI(url)) "max_completion_tokens" else "max_tokens", limit)
        if (effort == WritingEffort.DEEP) {
            when {
                isOpenRouter(url) -> put("reasoning", buildJsonObject { put("effort", "high") })
                // Documented on Anthropic's OpenAI SDK compatibility page; on models that reject a
                // budget the layer turns it into adaptive thinking. Its `reasoning_effort` is ignored.
                isAnthropic(url) ->
                    put(
                        "thinking",
                        buildJsonObject {
                            put("type", "enabled")
                            put("budget_tokens", anthropicThinkingBudget(limit ?: DEFAULT_OUTPUT_TOKENS))
                        },
                    )
                else -> put("reasoning_effort", "high")
            }
        }
    }.toString()

private val lenient = Json { ignoreUnknownKeys = true }

private fun parse(text: String): JsonElement? =
    try {
        lenient.parseToJsonElement(text)
    } catch (e: IllegalArgumentException) {
        null
    }

fun readUsage(raw: JsonElement?): Usage? {
    val usage = raw as? JsonObject ?: return null

    fun n(key: String) = (usage[key] as? JsonPrimitive)?.longOrNull
    return Usage(n("prompt_tokens"), n("completion_tokens"))
}

class StreamChunk(
    val text: String,
    val usage: Usage?,
    val done: Boolean,
    /** The choice's `finish_reason`, on the chunk that ends it. */
    val finishReason: String? = null,
    /** An `{"error": …}` frame's message: the provider gave up mid-answer. */
    val error: String? = null,
    /** The model's reasoning this chunk adds (OpenRouter's `reasoning`, DeepSeek's `reasoning_content`). */
    val reasoning: String = "",
    /** An SSE comment (OpenRouter's `: OPENROUTER PROCESSING`): nothing to read, but the provider is there. */
    val keepAlive: Boolean = false,
)

/** One `data:` line of a Chat Completions stream: the text it adds and any usage it carried. */
fun parseStreamLine(line: String): StreamChunk {
    val trimmed = line.trim()
    if (trimmed.startsWith(":")) return StreamChunk("", null, false, keepAlive = true)
    if (!trimmed.startsWith("data:")) return StreamChunk("", null, false)
    val data = trimmed.removePrefix("data:").trim()
    if (data == "[DONE]") return StreamChunk("", null, true)
    val json = parse(data) as? JsonObject ?: return StreamChunk("", null, false)
    errorOf(json)?.let { return StreamChunk("", null, false, error = it) }
    val choice = (json["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
    val delta = choice?.get("delta") as? JsonObject
    val content = (delta?.get("content") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    return StreamChunk(content, readUsage(json["usage"]), false, finishReason = finishReasonOf(choice), reasoning = reasoningOf(delta))
}

/**
 * The reasoning a stream delta carries, read once: OpenRouter sends the same text as `reasoning`
 * and in `reasoning_details` (text or summary parts; encrypted ones say nothing), DeepSeek and
 * others as `reasoning_content`.
 */
private fun reasoningOf(delta: JsonObject?): String {
    if (delta == null) return ""
    stringOf(delta["reasoning"])?.let { return it }
    stringOf(delta["reasoning_content"])?.let { return it }
    val details = delta["reasoning_details"] as? JsonArray ?: return ""
    return details.joinToString("") { part ->
        val obj = part as? JsonObject ?: return@joinToString ""
        when (stringOf(obj["type"])) {
            "reasoning.text" -> stringOf(obj["text"]).orEmpty()
            "reasoning.summary" -> stringOf(obj["summary"]).orEmpty()
            else -> ""
        }
    }
}

private fun stringOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

private fun errorOf(json: JsonObject): String? {
    val error = json["error"] ?: return null
    if (error is JsonNull) return null
    val message = ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.content ?: (error as? JsonPrimitive)?.content
    return message?.replace(Regex("\\s+"), " ")?.trim()?.take(160) ?: "error"
}

private fun finishReasonOf(choice: JsonObject?) = (choice?.get("finish_reason") as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * A whole stream's lines, as one answer: stops at `[DONE]`; an error frame, a `finish_reason`
 * other than `stop`, or a stream that ends with neither `[DONE]` nor `stop` is a failure, never a
 * paper (a cut-off page could still pass the boundary, missing its second half).
 */
class StreamReader(
    private val onEvent: (WriterEvent) -> Unit,
) {
    private val text = StringBuilder()
    private var usage: Usage? = null
    private var finish: String? = null
    private var done = false
    private var heard = false

    /** Reads one line; true when the stream has ended. */
    fun read(line: String): Boolean {
        if (!heard) {
            heard = true
            onEvent(WriterEvent.FirstByte)
        }
        val chunk = parseStreamLine(line)
        chunk.error?.let { throw WriterException(WriterFailure.CUT_OFF, providerSaid = it) }
        if (chunk.keepAlive) onEvent(WriterEvent.KeepAlive)
        if (chunk.reasoning.isNotEmpty()) onEvent(WriterEvent.Reasoning(chunk.reasoning))
        if (chunk.text.isNotEmpty()) {
            text.append(chunk.text)
            onEvent(WriterEvent.Text(chunk.text, text.length))
        }
        if (chunk.usage != null) usage = chunk.usage
        chunk.finishReason?.let { finish = it }
        if (chunk.done) done = true
        return done
    }

    fun finish(): Written {
        val reason = finish
        if (reason != null && reason != "stop") throw WriterException(WriterFailure.CUT_OFF, providerSaid = reason)
        if (reason == null && !done) throw WriterException(WriterFailure.CUT_OFF)
        return Written(text.toString(), usage)
    }
}

/**
 * A non-streamed answer's text. An error body or a `finish_reason` other than `stop` is a failure;
 * a missing `finish_reason` is accepted here, as some providers leave it out of whole answers.
 */
fun readCompletion(body: String): Written {
    val json = parse(body) as? JsonObject
    json?.let { errorOf(it) }?.let { throw WriterException(WriterFailure.CUT_OFF, providerSaid = it) }
    val choice = (json?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
    finishReasonOf(choice)?.takeIf { it != "stop" }?.let { throw WriterException(WriterFailure.CUT_OFF, providerSaid = it) }
    val message = choice?.get("message") as? JsonObject
    val content = (message?.get("content") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    return Written(content, readUsage(json?.get("usage")))
}

private val FENCE = Regex("^```(?:html)?\\s*\\n([\\s\\S]*?)\\n?```\\s*$", RegexOption.IGNORE_CASE)
private val DOC_START = Regex("<!doctype html|<html[\\s>]", RegexOption.IGNORE_CASE)

/** The document without the wrapping a model adds despite being told not to. */
fun extractDocument(text: String): String {
    var out = text.trim()
    FENCE.find(out)?.let { out = it.groupValues[1].trim() }
    val start = DOC_START.find(out)?.range?.first ?: -1
    if (start > 0) out = out.substring(start)
    val end = out.lowercase().lastIndexOf("</html>")
    if (end >= 0) out = out.substring(0, end + "</html>".length)
    return out
}

// --- choosing a model --------------------------------------------------------

/**
 * Families a front page is worth writing with, best first: a page is a long, careful
 * document, so long-form writers lead and small/fast variants trail.
 */
private val PREFERENCE =
    listOf(
        // Opus-class first: a page is the longest, most careful thing the reader pays a model for.
        Regex("claude[-.\\w]*opus|opus", RegexOption.IGNORE_CASE),
        Regex("claude[-.\\w]*sonnet|sonnet[-.\\w]*claude", RegexOption.IGNORE_CASE),
        Regex("gpt-5(?![-.\\w]*(mini|nano))", RegexOption.IGNORE_CASE),
        Regex("gemini[-.\\w]*pro", RegexOption.IGNORE_CASE),
        Regex("gpt-4\\.1(?![-.\\w]*(mini|nano))|gpt-4o(?![-.\\w]*mini)", RegexOption.IGNORE_CASE),
        Regex("deepseek[-.\\w]*(chat|v3|r1)", RegexOption.IGNORE_CASE),
        Regex("qwen[-.\\w]*(max|72b|235b)|llama[-.\\w]*(405b|70b)|mistral[-.\\w]*large|grok", RegexOption.IGNORE_CASE),
        Regex("claude|gpt|gemini|llama|qwen|mistral|deepseek", RegexOption.IGNORE_CASE),
    )

private val NOT_CHAT = Regex("embed|whisper|tts|transcri|audio|realtime|image|dall-?e|moderation|rerank|vision-preview|search-preview", RegexOption.IGNORE_CASE)
private val SMALL = Regex("mini|nano|haiku|flash|lite|small|8b|7b|3b|1b", RegexOption.IGNORE_CASE)
private val NUMBER = Regex("\\d+(?:\\.\\d+)?")

private fun versionKey(id: String) = NUMBER.findAll(id).map { it.value.toDouble() }.toList()

private fun compareVersionsDesc(
    a: String,
    b: String,
): Int {
    val va = versionKey(a)
    val vb = versionKey(b)
    for (i in 0 until maxOf(va.size, vb.size)) {
        val d = (vb.getOrNull(i) ?: -1.0).compareTo(va.getOrNull(i) ?: -1.0)
        if (d != 0) return d
    }
    return a.compareTo(b)
}

/** Every listed model a page could be written with, best guess first. */
fun rankModels(ids: List<String>): List<String> {
    fun tier(id: String): Double {
        val at = PREFERENCE.indexOfFirst { it.containsMatchIn(id) }
        return (if (at == -1) PREFERENCE.size else at) + if (SMALL.containsMatchIn(id)) 0.5 else 0.0
    }
    return ids
        .distinct()
        .filter { it.isNotEmpty() && !NOT_CHAT.containsMatchIn(it) }
        .sortedWith { a, b -> tier(a).compareTo(tier(b)).takeIf { it != 0 } ?: compareVersionsDesc(a, b) }
}

/**
 * A model the endpoint lists, with its prices when the endpoint publishes them (OpenRouter
 * does, per token, as decimal strings). Null prices mean "not said", never "free".
 */
@Immutable
class AgentModel(
    val id: String,
    val promptPerToken: Double? = null,
    val completionPerToken: Double? = null,
    /** The endpoint's own name for it ("Anthropic: Claude Sonnet 4.5"), when it gives one beside the id. */
    val name: String? = null,
    /** A Routstr node's `sats_pricing`: what the model costs in sats, which is what that key pays in. */
    val sats: SatsPricing? = null,
    /** The most it writes in one answer: OpenRouter's `top_provider.max_completion_tokens`, Anthropic's `max_tokens`. */
    val maxOutputTokens: Long? = null,
    /** Its context window, prompt and answer together: `context_length` (OpenRouter, Routstr). */
    val contextLength: Long? = null,
) {
    val priced get() = promptPerToken != null && completionPerToken != null

    /** Priced in sats (a Routstr node): the screen shows sats, not dollars. */
    val pricedInSats get() = sats?.priced == true
}

/** `GET {url}/models` to models, with OpenRouter's `pricing` read where it is there. */
fun readModels(body: String): List<AgentModel> {
    val json = parse(body) as? JsonObject ?: return emptyList()
    val list = (json["data"] as? JsonArray) ?: (json["models"] as? JsonArray) ?: return emptyList()
    return list.mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive ->
                entry
                    .takeIf { it.isString }
                    ?.content
                    ?.removePrefix("models/")
                    ?.ifEmpty { null }
                    ?.let { AgentModel(it) }
            is JsonObject -> {
                val id = ((entry["id"] as? JsonPrimitive)?.content ?: (entry["name"] as? JsonPrimitive)?.content)?.removePrefix("models/")?.ifEmpty { null }
                val pricing = entry["pricing"] as? JsonObject

                fun price(name: String) = (pricing?.get(name) as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

                fun count(
                    obj: JsonObject?,
                    name: String,
                ) = (obj?.get(name) as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
                val topProvider = entry["top_provider"] as? JsonObject
                // Gemini names its models in `name` ("models/…"): only a name beside an id is a label.
                val label = (entry["name"] as? JsonPrimitive)?.content?.trim()?.takeIf { entry["id"] != null && it.isNotEmpty() && it != id }
                id?.let {
                    AgentModel(
                        it,
                        price("prompt"),
                        price("completion"),
                        label,
                        RoutstrJson.satsPricing(entry["sats_pricing"])?.takeIf { sats -> sats.priced || sats.maxCost != null },
                        maxOutputTokens = count(topProvider, "max_completion_tokens") ?: count(entry, "max_tokens"),
                        contextLength = count(entry, "context_length") ?: count(topProvider, "context_length"),
                    )
                }
            }
            else -> null
        }
    }
}

// --- cost --------------------------------------------------------------------

/** What a page usually takes to write: a full HTML document. A repair round is not counted. */
const val EXPECTED_OUTPUT_TOKENS = 14_000

/** What the posts in full usually come to, for the estimate: half the block's cap. */
const val EXPECTED_FULL_CHARS = FULL_BLOCK_CHARS / 2

/** What Deep adds to each of the two calls, for the estimate: the reasoning, billed as output. */
const val DEEP_REASONING_TOKENS = 12_000

/** A rough token count: about four characters a token, for English-heavy text. */
fun approxTokens(chars: Int): Long = (chars + 3L) / 4

/** The tokens one print is estimated at, in and out. */
class EditionTokens(
    val input: Long,
    val output: Long,
)

/**
 * One print, two passes: the plan reads the brief and the digest and writes about
 * [PLAN_OUTPUT_TOKENS]; the page reads them again with the posts in full and writes a page. Deep
 * adds [DEEP_REASONING_TOKENS] of reasoning to each.
 */
fun editionTokens(
    digestChars: Int,
    briefChars: Int = systemPrompt().length,
    effort: WritingEffort = WritingEffort.STANDARD,
): EditionTokens {
    val prompt = approxTokens(digestChars + briefChars)
    val reasoning = if (effort == WritingEffort.DEEP) 2L * DEEP_REASONING_TOKENS else 0L
    return EditionTokens(
        input = prompt + prompt + approxTokens(EXPECTED_FULL_CHARS),
        output = PLAN_OUTPUT_TOKENS + EXPECTED_OUTPUT_TOKENS + reasoning,
    )
}

/**
 * The cost of one print before it runs ([editionTokens]). Null when the endpoint publishes no
 * prices: an estimate is never invented.
 */
fun estimateCost(
    model: AgentModel?,
    digestChars: Int,
    briefChars: Int = systemPrompt().length,
    effort: WritingEffort = WritingEffort.STANDARD,
): Double? {
    val prompt = model?.promptPerToken ?: return null
    val completion = model.completionPerToken ?: return null
    val tokens = editionTokens(digestChars, briefChars, effort)
    return tokens.input * prompt + tokens.output * completion
}

/** What a print cost, from the usage the provider reported and its own prices. */
fun actualCost(
    model: AgentModel?,
    usage: Usage?,
): Double? {
    val prompt = model?.promptPerToken ?: return null
    val completion = model.completionPerToken ?: return null
    val inTokens = usage?.promptTokens ?: return null
    val outTokens = usage.completionTokens ?: return null
    return inTokens * prompt + outTokens * completion
}

/**
 * `$0.08`, `$1.20`, or `<$0.01`: dollars to the cent, which is what a reader budgets in, with the
 * reader's decimal mark and grouping (`$1.234,50` in German).
 */
fun formatUsd(amount: Double): String {
    if (amount < 0.01) return "<$" + formatDecimal(0.01, maxFractionDigits = 2, minFractionDigits = 2)
    return "$" + formatDecimal(round(amount * 100) / 100, maxFractionDigits = 2, minFractionDigits = 2)
}

/** `GET {url}/models` to ids, whichever of the two common shapes it comes in. */
fun readModelIds(body: String): List<String> {
    val json = parse(body) as? JsonObject ?: return emptyList()
    val list = (json["data"] as? JsonArray) ?: (json["models"] as? JsonArray) ?: return emptyList()
    return list
        .mapNotNull { entry ->
            when (entry) {
                is JsonPrimitive -> entry.takeIf { it.isString }?.content
                is JsonObject -> (entry["id"] as? JsonPrimitive)?.content ?: (entry["name"] as? JsonPrimitive)?.content
                else -> null
            }
        }.map { it.removePrefix("models/") }
        .filter { it.isNotEmpty() }
}
