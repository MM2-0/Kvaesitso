package de.mm20.launcher2.searchactions.flow

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.*

/** Local data only: resolving or matching an alias never executes its target. */
data class FlowAlias(val id: String, val alias: String, val targetKey: String, val additionalTargetKeys: List<String> = emptyList()) {
    val targetKeys: List<String> get() = listOf(targetKey) + additionalTargetKeys
}

enum class FlowAliasError { Empty, ControlCharacter, TooLong, Duplicate, InvalidTarget }

object FlowAliases {
    private val whitespace = Regex("[\\s\\p{Z}]+")
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(whitespace, " ").trim().lowercase(Locale.ROOT)

    fun validate(alias: FlowAlias, others: List<FlowAlias> = emptyList()): FlowAliasError? {
        if (alias.alias.codePoints().anyMatch { Character.isISOControl(it) }) return FlowAliasError.ControlCharacter
        val normalized = normalize(alias.alias)
        if (normalized.isEmpty()) return FlowAliasError.Empty
        if (normalized.codePointCount(0, normalized.length) > 64) return FlowAliasError.TooLong
        if (others.any { it.id != alias.id && normalize(it.alias) == normalized }) return FlowAliasError.Duplicate
        if (alias.id.isBlank() || alias.targetKeys.size !in 1..4 || alias.targetKeys.distinct().size != alias.targetKeys.size ||
            alias.targetKeys.any { !it.startsWith("app://") || it.length > 2048 || it.codePoints().anyMatch { c -> Character.isISOControl(c) } }) return FlowAliasError.InvalidTarget
        return null
    }

    fun matches(alias: FlowAlias, query: String): Boolean =
        validate(alias) == null && normalize(alias.alias) == normalize(query)

    fun encode(alias: FlowAlias): String = buildJsonObject {
        put("version", if (alias.additionalTargetKeys.isEmpty()) 1 else 2)
        put("id", alias.id)
        put("alias", alias.alias)
        put("targetKey", alias.targetKey)
        if (alias.additionalTargetKeys.isNotEmpty()) putJsonArray("additionalTargetKeys") { alias.additionalTargetKeys.forEach { add(it) } }
    }.toString()

    fun decode(payload: String?): FlowAlias? = try {
        val obj = Json.parseToJsonElement(payload ?: "").jsonObject
        val version = obj["version"]?.jsonPrimitive
        if (version?.isString != false || version.intOrNull !in 1..2) null else {
            fun text(key: String): String? = obj[key]?.jsonPrimitive?.takeIf { it.isString }?.content
            val extra = if (version.intOrNull == 2) obj["additionalTargetKeys"]!!.jsonArray.map {
                require(it.jsonPrimitive.isString); it.jsonPrimitive.content
            } else emptyList()
            val alias = FlowAlias(text("id") ?: "", text("alias") ?: "", text("targetKey") ?: "", extra)
            alias.takeIf { validate(it) == null }
        }
    } catch (_: IllegalArgumentException) { null } catch (_: NullPointerException) { null }
}

data class FlowAliasTarget(
    val key: String,
    val label: String,
    val personal: Boolean = true,
    val hidden: Boolean = false,
    val suspended: Boolean = false,
    val privateProfile: Boolean = false,
) {
    val eligible: Boolean get() = personal && !hidden && !suspended && !privateProfile
}
