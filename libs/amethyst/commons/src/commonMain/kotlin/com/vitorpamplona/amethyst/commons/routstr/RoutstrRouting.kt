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
package com.vitorpamplona.amethyst.commons.routstr

import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Where Routstr calls go, and what each node said about itself.
 *
 * An announcement (kind 38421) is anyone's word: it can claim any https host, so it never decides
 * where a key, a token or a payment goes. The rules:
 *
 * - A saved key is only ever sent to its own URL (the one in kind 12000), or to the onion that
 *   URL's own `/v1/info`, asked over https, names in `onion_url`.
 * - `/v1/info` is asked over https only, and kept per https root ([info]). A node is verified
 *   when that answer's npub is the announcement's author ([verify]), never through an onion.
 * - An onion is a real Tor v3 address by Quartz's rule ([RoutstrNodes.onionRoot]), never a string
 *   test.
 * - A key is a Routstr key only when its own URL said so: `/v1/info` with an npub and mints, or
 *   `/v1/models` with `sats_pricing` ([isRoutstrKey]). A preset provider's key (OpenRouter,
 *   Anthropic, ...) never is.
 */
class RoutstrRouting(
    private val api: RoutstrApi,
    private val torEnabled: () -> Boolean,
) {
    private val _info = MutableStateFlow<Map<String, RoutstrInfo?>>(emptyMap())

    /** `/v1/info` per https root (lowercase), asked over https: null where the node answered as something else. */
    val info: StateFlow<Map<String, RoutstrInfo?>> = _info.asStateFlow()

    private val _pricedModels = MutableStateFlow<Set<String>>(emptySet())

    /** The https roots (lowercase) whose own `/v1/models` listed `sats_pricing`. */
    val pricedModels: StateFlow<Set<String>> = _pricedModels.asStateFlow()

    private fun cacheKey(httpsRoot: String) = httpsRoot.lowercase()

    /**
     * `/v1/info` of the https node at [url] (a root or a key URL), asked over https and kept for
     * this run; throws when the node doesn't answer. Refuses anything that isn't https: an onion's
     * answer could be anyone's.
     */
    suspend fun ask(url: String): RoutstrInfo {
        val https = RoutstrNodes.httpsRoot(url) ?: throw RoutstrException(RoutstrError(null, RoutstrFailure.UNEXPECTED))
        val answer = api.info(https)
        _info.update { it + (cacheKey(https) to answer.takeIf { a -> a.isRoutstr }) }
        return answer
    }

    /** [ask], once per run; null when it isn't an https Routstr node or didn't answer (asked again next time). */
    suspend fun info(url: String): RoutstrInfo? {
        val https = RoutstrNodes.httpsRoot(url) ?: return null
        val key = cacheKey(https)
        if (_info.value.containsKey(key)) return _info.value[key]
        return try {
            ask(https).takeIf { it.isRoutstr }
        } catch (e: RoutstrException) {
            // No answer says nothing about what it is: ask again next time.
            if (e.failure != RoutstrFailure.UNREACHABLE && e.failure != RoutstrFailure.UNSUPPORTED) {
                _info.update { it + (key to null) }
            }
            null
        }
    }

    /** What [url]'s https node said, when it was asked already. */
    fun knownInfo(url: String): RoutstrInfo? = RoutstrNodes.httpsRoot(url)?.let { _info.value[cacheKey(it)] }

    /** The onion [url]'s own https `/v1/info` names, when it is a real v3 onion. Never from an announcement. */
    fun onionOf(url: String): String? = knownInfo(url)?.onionUrl?.let(RoutstrNodes::onionRoot)

    /**
     * Where to call the node at [url] (a saved key's URL, or an https root): the onion its own
     * https `/v1/info` names when Tor is on, else [url]'s own root. Asks `/v1/info` first when Tor
     * is on and it hasn't been asked.
     */
    suspend fun baseFor(url: String): String {
        val root = RoutstrNodes.root(url)
        if (!torEnabled()) return root
        info(root)
        return onionOf(root) ?: root
    }

    /** Where a purchase at [httpsRoot] goes: its own https root, and the onion its https `/v1/info` names. */
    suspend fun target(
        httpsRoot: String,
        name: String?,
    ): RoutstrTarget {
        info(httpsRoot)
        return RoutstrTarget(RoutstrNodes.root(httpsRoot), onionOf(httpsRoot), name)
    }

    /**
     * Whether [node] is who announced it: its https `/v1/info`, asked over https, names the
     * announcement's author. An onion-only node is never verified.
     */
    suspend fun verify(node: RoutstrNode): RoutstrVerification {
        val https = node.httpsUrl ?: return RoutstrVerification.Unknown
        val answer = info(https) ?: return RoutstrVerification.Unknown
        return if (answer.pubKeyHex == node.pubKey) RoutstrVerification.Verified else RoutstrVerification.Mismatch
    }

    /**
     * The announcement for the node at [keyUrl], among [events]: one whose https address is
     * [keyUrl]'s and whose author is the npub that node's own `/v1/info` gave. Null until that
     * answer is in, and for an announcement by anyone else.
     */
    fun nodeForKey(
        events: List<RoutstrNodeEvent>,
        keyUrl: String,
    ): RoutstrNode? {
        val owner = knownInfo(keyUrl)?.pubKeyHex ?: return null
        return events
            .asSequence()
            .filter { it.pubKey == owner }
            .map(RoutstrNode::from)
            .firstOrNull { RoutstrNodes.sameNode(keyUrl, it.httpsUrl) }
    }

    /**
     * Whether [key] is a Routstr key: its own URL answered `/v1/info` with an npub and mints, or
     * `/v1/models` with `sats_pricing`. An announcement never makes it one, and a preset
     * provider's key never is one.
     */
    fun isRoutstrKey(key: AgentKeyTag): Boolean {
        if (!key.hasKey() || isOtherProvider(key.url)) return false
        val https = RoutstrNodes.httpsRoot(key.url) ?: return false
        return _info.value[cacheKey(https)] != null || cacheKey(https) in _pricedModels.value
    }

    /** Whether to ask [key]'s endpoint whether it is a Routstr node: a custom endpoint or Routstr's own, https, `/v1`. */
    fun mayBeRoutstr(key: AgentKeyTag): Boolean {
        if (!key.hasKey() || !key.isUrlAllowed() || AgentKeyTag.isLoopback(key.url)) return false
        if (isOtherProvider(key.url) || RoutstrNodes.httpsRoot(key.url) == null) return false
        return key.url
            .trim()
            .trimEnd('/')
            .endsWith("/v1")
    }

    /**
     * Asks [key]'s own URL whether it is a Routstr node: `/v1/info`, then, when that answered as
     * something else, `/v1/models` (no key sent). True when it is.
     */
    suspend fun confirm(key: AgentKeyTag): Boolean {
        if (isRoutstrKey(key)) return true
        if (!mayBeRoutstr(key)) return false
        val https = RoutstrNodes.httpsRoot(key.url) ?: return false
        if (info(https) != null) return true
        // Unreachable: nothing was learned, and the models call would fail the same way.
        if (!_info.value.containsKey(cacheKey(https))) return false
        try {
            notePricing(https, api.models(https, null))
        } catch (e: RoutstrException) {
            return false
        }
        return isRoutstrKey(key)
    }

    /** Remembers that [base], a node's own URL, listed models priced in sats. */
    fun notePricing(
        base: String,
        models: List<RoutstrModel>,
    ) {
        val https = RoutstrNodes.httpsRoot(base) ?: return
        if (models.any { it.sats != null }) _pricedModels.update { it + cacheKey(https) }
    }

    /** The name of the node at [keyUrl]: what its own `/v1/info` says, else its verified announcement's. */
    fun nodeName(
        events: List<RoutstrNodeEvent>,
        keyUrl: String,
    ): String? = knownInfo(keyUrl)?.name?.trim()?.ifEmpty { null } ?: nodeForKey(events, keyUrl)?.name

    companion object {
        const val ROUTSTR_PRESET = "routstr"

        /** A preset provider other than Routstr's own (OpenRouter, Anthropic, ...): never a Routstr node. */
        fun isOtherProvider(url: String): Boolean = AgentProviderPresets.forUrl(url)?.let { it.id != ROUTSTR_PRESET } ?: false
    }
}
