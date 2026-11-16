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

import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.search.calendar.LocalClock
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.kiko.cart.CartLoader
import com.vitorpamplona.kiko.player.KikoPlayer
import com.vitorpamplona.kiko.player.KikoPlayerHost
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.Volatile

/**
 * Holds the [player] of one cart for the screen, so a rotation or a closed dialog keeps the game
 * where it was. The cart comes through the image HTTP client (Tor settings apply) and must match
 * the note's [expectedSha256] when it gives one; what the game saves (`cartdata`) goes to
 * [saveStore]. The other carts of a game its note lists in [links] come the same way when the game
 * starts, each only if it matches its hash, ready for when the game asks for them.
 */
class CartPlayerViewModel(
    private val url: String,
    private val expectedSha256: String?,
    private val httpClients: IRoleBasedHttpClientBuilder,
    private val saveStore: CartSaveStore?,
    private val links: CartLinks?,
) : ViewModel() {
    private var current: KikoPlayer? = null

    private val _differentFile = MutableStateFlow<DifferentFile?>(null)

    /**
     * Set when the server sent a file other than the one the note recorded (its sha256 differs):
     * the screen names the server, over the game when it still loads as a cart, or in place of the
     * download error when it does not.
     */
    val differentFile: StateFlow<DifferentFile?> = _differentFile.asStateFlow()

    /** The user read the warning. */
    fun dismissDifferentFile() {
        _differentFile.value = null
    }

    /**
     * The player of this cart, made again if it was let go while the dialog was closed (see
     * [LivePlayers]). Main thread only.
     */
    fun player(): KikoPlayer {
        val player =
            current ?: Game().let { game ->
                KikoPlayer(source = game::download, saves = saveStore?.forCart { game.saveKey }, host = game).also { current = it }
            }
        live.touch(this)
        return player
    }

    /** Ends the game and lets its memory go; [player] starts it again. Main thread only. */
    fun release() {
        current?.close()
        current = null
    }

    /**
     * What one [KikoPlayer] needs and nothing else may touch: a frame of a closing player can outlive
     * [release], and it must not use up the carts of the player made after it. Its memory goes with
     * its player.
     */
    private inner class Game : KikoPlayerHost {
        // What this game keeps its saves under (see [CartSaveStore] and [CartLinks.saveKey]).
        @Volatile
        var saveKey: String? = null
            private set

        // Every other cart of the game, in memory until the game is let go.
        @Volatile
        private var carts: GameCarts? = null

        override fun log(message: String) = Log.d(TAG) { message }

        override fun utcOffsetSeconds(): Int = LocalClock.utcOffsetSeconds(TimeUtils.now()).toInt()

        // On the game thread, inside a frame, so it only ever answers from memory: a download here
        // would race the hang watchdog. A name with no cart stops the game with a message.
        override fun loadCart(name: String): ByteArray? = carts?.get(name).also { Log.i(TAG) { "The game asked for $name: ${it?.let { bytes -> "${bytes.size} bytes" } ?: "not available"}" } }

        suspend fun download(): ByteArray? {
            val bytes = httpClients.fetchMedia(url, MAX_CART_BYTES) ?: return null
            val hash = sha256(bytes).toHexKey()
            when (CartIntegrity.of(hash, expectedSha256) { CartLoader.isCart(bytes) }) {
                CartIntegrity.VERIFIED, CartIntegrity.UNRECORDED -> {}
                CartIntegrity.DIFFERENT_CART -> {
                    Log.w(TAG) { "The cart at $url does not match its hash (expected $expectedSha256, got $hash, ${bytes.size} bytes); playing it with a warning" }
                    _differentFile.value = DifferentFile(hostOf(url), playable = true)
                }
                CartIntegrity.DIFFERENT_FILE -> {
                    Log.w(TAG) { "The file at $url does not match its hash (expected $expectedSha256, got $hash) and is not a cart" }
                    _differentFile.value = DifferentFile(hostOf(url), playable = false)
                    return null
                }
            }
            carts = links?.fetchCarts(bytes) { httpClients.fetchMedia(it, MAX_CART_BYTES) }
            saveKey = links?.saveKey(hash) ?: hash
            Log.i(TAG) { "Playing $url, saves under $saveKey" }
            return bytes
        }
    }

    override fun onCleared() {
        live.remove(this)
        release()
    }

    companion object {
        /** The host of [url], to name the server in the warning. */
        fun hostOf(url: String): String =
            url
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .substringAfterLast('@')

        /** A cart PNG is tens of KiB; anything far past that is not one. */
        private const val MAX_CART_BYTES = 1 shl 20

        /**
         * Games kept paused at once. A closed dialog keeps its game where it was, but every game holds
         * its whole Lua heap, frames and a thread: a feed of carts opened one after another must not
         * keep them all. Past this, the least recently shown one is let go.
         */
        const val MAX_LIVE_PLAYERS = 2

        private val live = LivePlayers<CartPlayerViewModel>(MAX_LIVE_PLAYERS) { it.release() }

        private const val TAG = "CartPlayer"
    }
}

/** A file from [host] that is not the one the note recorded; [playable] when it still loads as a cart. */
data class DifferentFile(
    val host: String,
    val playable: Boolean,
)

/**
 * The [max] most recently used of something that holds a lot, by use: [touch] marks one used and lets
 * the least recently used go past [max] (calling [release] on it). Main thread only.
 */
class LivePlayers<T>(
    private val max: Int,
    private val release: (T) -> Unit,
) {
    private val order = ArrayDeque<T>()

    fun touch(item: T) {
        order.remove(item)
        order.addLast(item)
        while (order.size > max) release(order.removeFirst())
    }

    fun remove(item: T) {
        order.remove(item)
    }

    val size: Int get() = order.size
}
