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

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferencesSerializer
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.kiko.player.KikoPlayer
import com.vitorpamplona.kiko.player.KikoPlayerHost
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Path.Companion.toPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Saves outlive a killed app: a separate JVM plays a game that saves, is killed with SIGKILL
 * (no shutdown hooks, no flush), and the next one must read the save back through `dget`.
 * How soon a save is written is kiko-player's own test, on virtual time: here a kill waits for the
 * write to reach the disk, so a busy machine cannot make the test flaky.
 *
 * "Reached the disk" is read the way the app reads it: the file decoded and the game's number
 * checked. Waiting for the bytes to change from a snapshot raced the game, which could write before
 * the snapshot was taken, and on Windows a read can hit the file while DataStore replaces it and
 * fail with a sharing violation; both failed the Windows CI job.
 */
class CartSaveKillTest {
    @get:Rule
    val folder = TemporaryFolder()

    // Killed after each test even when an assertion fails first, so no game outlives the test.
    private val games = ArrayList<Game>()

    @After
    fun killGames() = games.forEach { it.kill() }

    private fun play(mode: String) = Game(folder.root, mode).also { games.add(it) }

    private fun saveFile() = File(folder.root, "datastore/cart_saves.preferences_pb")

    /**
     * The game's `dget(0)` as the save file holds it (raw 16:16 bits), or null while there is no
     * readable save: no file yet, a file another process is replacing (Windows refuses the read),
     * or one this read caught half-written.
     */
    private fun savedNumber(): Int? {
        val bytes =
            try {
                saveFile().takeIf { it.exists() }?.readBytes()
            } catch (_: IOException) {
                null
            } ?: return null
        val prefs =
            try {
                runBlocking { PreferencesSerializer.readFrom(Buffer().write(bytes)) }
            } catch (_: CorruptionException) {
                return null
            } catch (_: IOException) {
                return null
            }
        val save =
            prefs
                .asMap()
                .entries
                .firstOrNull { it.key.name == SAVE_KEY }
                ?.value as? String ?: return null
        return CartSaveStore.decode(save)?.get(0)
    }

    /** Waits until the save on disk holds [number] in `dget(0)`, for up to 10 s. */
    private fun awaitSaved(number: Int) {
        val raw = number shl 16
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (savedNumber() == raw) return
            Thread.sleep(10)
        }
        error("the save never reached the disk: dget(0) is ${savedNumber()?.let { it shr 16 }}, expected $number")
    }

    @Test
    fun aCheckpointSurvivesAKillRightAfterIt() {
        for (life in 0 until 3) {
            val game = play("checkpoint")
            assertEquals("loaded $life", game.waitFor { it.startsWith("loaded") })
            game.waitFor { it == "checkpoint" }
            // A player who swipes the app away as soon as the checkpoint is on disk.
            awaitSaved(life + 1)
            game.kill()
        }
        val last = play("checkpoint")
        assertEquals("loaded 3", last.waitFor { it.startsWith("loaded") })
        last.kill()
    }

    @Test
    fun aGameThatSavesEveryFrameKeepsAWholeSave() {
        val game = play("everyframe")
        assertEquals("loaded 0", game.waitFor { it.startsWith("loaded") })
        game.waitFor { it == "frame 90" }
        game.kill()

        val next = play("everyframe")
        val loaded = next.waitFor { it.startsWith("loaded") }.removePrefix("loaded ").toInt()
        next.kill()
        // Some frame's save, whole: a kill in the middle of a write leaves the previous one. The game
        // keeps playing while the kill lands, so that frame can be a few past 90 (93 on a CI runner).
        assertTrue("reloaded $loaded", loaded >= 1)
    }

    companion object {
        /** Where [CartSaveStore] keeps the `killtest` id of the cart [CartSaveKillChild] plays. */
        private const val SAVE_KEY = "cart_test-cart_killtest"
    }

    /** One run of [CartSaveKillChild] in its own process. */
    private class Game(
        root: File,
        mode: String,
    ) {
        private val process =
            ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").path,
                "-cp",
                System.getProperty("java.class.path"),
                CartSaveKillChild::class.java.name,
                root.path,
                mode,
            ).redirectErrorStream(true).start()
        private val lines = process.inputStream.bufferedReader()

        fun waitFor(match: (String) -> Boolean): String {
            while (true) {
                val line = lines.readLine() ?: error("the game exited before printing what was expected")
                if (match(line)) return line
            }
        }

        fun kill() {
            process.destroyForcibly()
            process.waitFor(10, TimeUnit.SECONDS)
        }
    }
}

/** A game that saves, run the way the app runs it: a [KikoPlayer] over the app's DataStore file. */
object CartSaveKillChild {
    private val CARTS =
        mapOf(
            // Saves once, like a game reaching a checkpoint or a high score.
            "checkpoint" to
                """
                cartdata("killtest")
                printh("loaded " .. dget(0))
                n = 0
                function _update()
                  n += 1
                  if (n == 30) dset(0, dget(0) + 1) printh("checkpoint")
                end
                """,
            // Saves its progress every frame.
            "everyframe" to
                """
                cartdata("killtest")
                printh("loaded " .. dget(0))
                n = dget(0)
                function _update()
                  n += 1
                  dset(0, n)
                  printh("frame " .. n)
                end
                """,
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val stores = AppPreferenceStores(rootFilesDir = { args[0].toPath() })
        val host =
            object : KikoPlayerHost {
                override fun log(message: String) {
                    println(message)
                    System.out.flush()
                }
            }
        val code = CARTS.getValue(args[1]).trimIndent()
        val player =
            KikoPlayer(
                source = { "__lua__\n$code\n".encodeToByteArray() },
                saves = stores.cartSaveStore().forCart { "test-cart" },
                host = host,
                openAudio = { null },
            )
        player.resume()
        // The game's thread is a daemon: this one keeps the process up until the test kills it.
        while (true) Thread.sleep(1000)
    }
}
