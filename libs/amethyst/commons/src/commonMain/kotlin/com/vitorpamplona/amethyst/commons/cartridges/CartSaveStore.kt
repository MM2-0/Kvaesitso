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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.kiko.player.KikoSaveStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * What games save with `cartdata(id)`: 64 numbers per id (high scores, progress, settings). Kept for
 * the install, not per account.
 *
 * The id is the game's own name for its save, and any cart may name any id: on the original console
 * that is how a game's sequel reads its progress, but here carts come from strangers, and a joke cart
 * naming a popular game's id would wipe that game's high scores. So saves are kept per cart: a
 * [forCart] view sees only the ids its own cart (by the sha256 of its bytes) wrote. Two posts of the
 * same file share their saves; another cart never sees them. A game in several carts saves under a
 * key that covers every cart its note lists ([CartLinks.saveKey]): otherwise a stranger's note could
 * list a hostile cart beside a game's front cart and reach that game's saves.
 */
class CartSaveStore(
    private val store: DataStore<Preferences>,
) {
    /** The saves of the game whose key is what [saveKey] returns; nothing while it returns null. */
    fun forCart(saveKey: () -> String?): KikoSaveStore =
        object : KikoSaveStore {
            override suspend fun loadAll(): Map<String, IntArray> = saveKey()?.let { loadAll(it) } ?: emptyMap()

            override suspend fun save(saves: Map<String, IntArray>) {
                saveKey()?.let { save(it, saves) }
            }
        }

    /** Every save of [cart], by id. They are 512 characters each, and few games use one. */
    suspend fun loadAll(cart: String): Map<String, IntArray> {
        val prefix = prefixOf(cart)
        val prefs = store.data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }.first()
        val saves = HashMap<String, IntArray>()
        for ((key, value) in prefs.asMap()) {
            if (!key.name.startsWith(prefix) || value !is String) continue
            decode(value)?.let { saves[key.name.removePrefix(prefix)] = it }
        }
        return saves
    }

    /**
     * Writes [saves] for [cart]. Ids the store doesn't have yet are dropped once it holds
     * [MAX_STORED_SAVES], so carts played one after another can't grow it without bound.
     */
    suspend fun save(
        cart: String,
        saves: Map<String, IntArray>,
    ) {
        val prefix = prefixOf(cart)
        store.edit { prefs ->
            var count = prefs.asMap().keys.count { it.name.startsWith(PREFIX) }
            for ((id, values) in saves) {
                val key = stringPreferencesKey(prefix + id)
                if (key !in prefs) {
                    if (count >= MAX_STORED_SAVES) continue
                    count++
                }
                prefs[key] = encode(values)
            }
        }
    }

    companion object {
        private const val PREFIX = "cart_"
        const val VALUES = KikoSaveStore.VALUES

        /** Save ids kept across every cart; each is about 600 bytes. */
        const val MAX_STORED_SAVES = 1_024

        private fun prefixOf(cart: String) = "$PREFIX${cart.lowercase()}_"

        /** Eight hex digits per number, the raw 16:16 bits. */
        internal fun encode(values: IntArray): String = values.joinToString("") { it.toUInt().toString(16).padStart(8, '0') }

        internal fun decode(text: String): IntArray? {
            if (text.length != VALUES * 8) return null
            val values = IntArray(VALUES)
            for (i in 0 until VALUES) values[i] = text.substring(i * 8, i * 8 + 8).toUIntOrNull(16)?.toInt() ?: return null
            return values
        }
    }
}

/** Where the app keeps what games save. */
fun AppPreferenceStores.cartSaveStore(): CartSaveStore = CartSaveStore(getDataStore("cart_saves"))
