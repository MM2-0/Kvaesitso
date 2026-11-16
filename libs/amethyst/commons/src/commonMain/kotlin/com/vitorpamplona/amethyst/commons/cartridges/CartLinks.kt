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
package com.vitorpamplona.amethyst.commons.cartridges

import androidx.compose.runtime.Immutable
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip68Picture.dims
import com.vitorpamplona.quartz.nip68Picture.fallback
import com.vitorpamplona.quartz.nip68Picture.hash
import com.vitorpamplona.quartz.nip68Picture.mimeType
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Another cart of a game: the [names] the game's carts call it by, exactly as they pass them to `load`
 * or `reload`, where to get it, in order, and the [sha256] its bytes must have.
 */
@Immutable
class ExtraCart(
    val names: List<String>,
    val urls: List<String>,
    val sha256: String,
)

/**
 * The carts a running game reaches by the names it uses. A name the note gave is in [named]. Two carts
 * posted with no names come as [unnamed], the other cart first and then the entry: kiko doesn't say
 * which cart is running, but a game starts on the entry, so its first new name is taken to mean the
 * other cart and the next to mean the way back. A third name has no cart. Asked on the game thread only.
 */
class GameCarts(
    named: Map<String, ByteArray>,
    unnamed: List<ByteArray> = emptyList(),
) {
    private val byName = HashMap(named)
    private val unclaimed = ArrayDeque(unnamed)

    operator fun get(name: String): ByteArray? = byName[name] ?: unclaimed.removeFirstOrNull()?.also { byName[name] = it }
}

/**
 * What one cart of a note can reach through `load` and `reload`, read from the note's `imeta` tags:
 * [extras] by the names they list, and itself by [ownNames], so a game can come back to its first
 * cart. Only the carts its note lists: a name is looked up, and never becomes a URL.
 *
 * ```json
 * ["imeta", "url https://example.com/front.png", "x <sha256>", "m image/png", "cart #game"]
 * ["imeta", "url https://example.com/main.png", "x <sha256>", "m image/png", "cart #game_main-7"]
 * ```
 *
 * The entry cart is the one in the note's text. An extra cart is any other `imeta` with a `cart` field
 * and a valid `x`, and the field repeats for a game that spells a name two ways.
 *
 * Someone who just uploads both carts of a game writes no `cart` field, and a game's names say nothing
 * about which file they mean. With exactly two carts that doesn't matter: [partner] is the only other
 * cart the note declares. Three or more can't be told apart, so those notes need the field.
 */
@Immutable
class CartLinks(
    val ownNames: List<String>,
    val extras: List<ExtraCart>,
    val partner: ExtraCart? = null,
) {
    // Length-prefixed, because a name is a stranger's text: a line break in one must not make it read
    // as two pairs. Sorted, so listing the same carts in another order is the same game.
    private fun extraPairs(): String =
        extras
            .flatMap { cart -> cart.names.map { name -> "${lengthPrefixed(name)} ${cart.sha256}" } }
            .sorted()
            .joinToString("\n")

    /** Sets these links apart from another note's for the same entry cart, which may reach other carts. */
    fun fingerprint(): String = ownNames.map(::lengthPrefixed).sorted().joinToString("\n", postfix = "\n") + extraPairs() + partner?.sha256.orEmpty()

    private fun lengthPrefixed(name: String) = "${name.length}:$name"

    /**
     * What the game's saves are kept under (see [CartSaveStore]), given the hash of the entry cart it
     * loaded. Reusing a game's front cart in another note, with a hostile cart under the name the
     * game loads, makes a different game with saves of its own. A game with no other carts keeps the
     * hash of its cart.
     *
     * It covers the carts the note declares, not the ones that downloaded, so a failed download does
     * not move a game's saves. Two unnamed carts share one key, whichever of them was started.
     */
    fun saveKey(entryHash: String): String =
        when {
            partner != null -> hash("pair\n" + listOf(entryHash, partner.sha256).sorted().joinToString("\n"))
            extras.isEmpty() -> entryHash
            else -> hash("$entryHash\n${extraPairs()}")
        }

    private fun hash(text: String) = sha256(text.encodeToByteArray()).toHexKey()

    /**
     * Every cart of the game: [ownNames] answering with [entry], and each extra cart or [partner] that
     * could be fetched with a matching hash. One that could not is left out, so the game plays until
     * it asks for it. Only a cancellation throws.
     */
    suspend fun fetchCarts(
        entry: ByteArray,
        fetch: suspend (url: String) -> ByteArray?,
    ): GameCarts {
        if (partner != null) return GameCarts(emptyMap(), partner.fetchFirstMatch(fetch)?.let { listOf(it, entry) }.orEmpty())
        val named =
            coroutineScope {
                val fetched = extras.map { cart -> async { cart to cart.fetchFirstMatch(fetch) } }.awaitAll()
                buildMap {
                    ownNames.forEach { put(it, entry) }
                    for ((cart, bytes) in fetched) {
                        if (bytes != null) cart.names.forEach { put(it, bytes) }
                    }
                }
            }
        return GameCarts(named)
    }

    private suspend fun ExtraCart.fetchFirstMatch(fetch: suspend (url: String) -> ByteArray?): ByteArray? {
        for (url in urls) {
            val bytes =
                runCatchingCancellable { fetch(url) }
                    .onFailure { Log.w(TAG, "Could not fetch the cart at $url", it) }
                    .getOrNull() ?: continue
            if (sha256(bytes).toHexKey() == sha256) {
                Log.i(TAG) { "Fetched a cart from $url, ${bytes.size} bytes" }
                return bytes
            }
            Log.w(TAG) { "The cart at $url does not match its hash, $sha256" }
        }
        Log.w(TAG) { "Could not get cart $sha256 from any of its ${urls.size} URLs" }
        return null
    }

    companion object {
        /** The `imeta` field with a name a cart goes by. Custom, like `webxdc`. */
        const val CART_FIELD = "cart"

        /** Our own limit: enough for any real game, and 16 MiB of them is what one note can add. */
        const val MAX_EXTRA_CARTS = 16

        /** The `url` and its `fallback`s, so one note's extra carts make at most 48 requests. */
        const val MAX_URLS_PER_CART = 3

        private const val TAG = "CartLinks"

        /**
         * [imetas] if any of them names a cart or exactly two of them look like carts, else null. This
         * is what a parsed note keeps for [forEntry]: one reference shared by its images, because
         * building the links of every image of a stranger's note would make it cost its tags times its
         * images, in time and in memory.
         */
        fun tagsToKeep(imetas: Map<String, IMetaTag>): Map<String, IMetaTag>? = imetas.takeIf { tags -> tags.values.any { CART_FIELD in it.properties } || tags.values.count { it.looksLikeACart() } == 2 }

        /**
         * What the cart at [entryUrl] can reach, from the `imeta` tags of its note; null when there
         * is nothing to reach. Past the limits, entries are ignored. On a name clash the first entry
         * wins, and the entry cart's own names beat the rest. With no [CART_FIELD] at all, a note of
         * exactly two carts links them to each other: see [partner].
         */
        fun forEntry(
            entryUrl: String,
            imetas: Map<String, IMetaTag>,
        ): CartLinks? {
            if (imetas.values.none { CART_FIELD in it.properties }) return unnamedPair(entryUrl, imetas)

            val taken = HashSet<String>()
            val ownNames = imetas[entryUrl]?.names().orEmpty().filter(taken::add)
            val extras = ArrayList<ExtraCart>()
            for (imeta in imetas.values) {
                if (extras.size == MAX_EXTRA_CARTS) break
                if (imeta.url == entryUrl) continue
                val sha256 = imeta.sha256() ?: continue
                val names = imeta.names().filter(taken::add)
                if (names.isEmpty()) continue
                extras.add(ExtraCart(names, imeta.urls(), sha256))
            }
            return if (ownNames.isEmpty() && extras.isEmpty()) null else CartLinks(ownNames, extras)
        }

        private fun unnamedPair(
            entryUrl: String,
            imetas: Map<String, IMetaTag>,
        ): CartLinks? {
            val carts = imetas.values.filter { it.looksLikeACart() }
            // One file posted from two servers is one cart.
            if (carts.size != 2 || carts[0].sha256() == carts[1].sha256()) return null
            val other = carts.singleOrNull { it.url != entryUrl } ?: return null
            return CartLinks(emptyList(), emptyList(), ExtraCart(emptyList(), other.urls(), other.sha256() ?: return null))
        }

        /** What a plain upload of a cart says about it: enough to fetch it on Play and check it. */
        private fun IMetaTag.looksLikeACart(): Boolean {
            val dim = dims()?.firstOrNull()?.let { DimensionTag.parse(it) } ?: return false
            return CartDetector.isCartSize(dim) &&
                mimeType()?.firstOrNull()?.equals("image/png", ignoreCase = true) != false &&
                sha256() != null
        }

        private fun IMetaTag.urls() = (listOf(url) + fallback().orEmpty()).take(MAX_URLS_PER_CART)

        private fun IMetaTag.names() = properties[CART_FIELD].orEmpty().filter { it.isNotEmpty() }

        private fun IMetaTag.sha256(): String? =
            hash()
                ?.firstOrNull()
                // Not `isHexKey`: it reads past the lookup table on a non-Latin char and catches the
                // exception, and this runs on every tag of a stranger's note.
                ?.takeIf { it.length == 64 && Hex.isHex(it, 0, 64) }
                ?.lowercase()
    }
}
