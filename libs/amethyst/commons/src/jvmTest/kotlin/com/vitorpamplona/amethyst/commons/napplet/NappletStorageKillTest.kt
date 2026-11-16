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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * A game's save must outlive the app being killed: once `storage.setItem` has answered, the save is on
 * disk, and a kill in the middle of the next one leaves a whole save, never a torn file. The writer
 * runs in its own JVM, as the app does, and is killed with SIGKILL while it keeps saving.
 */
class NappletStorageKillTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun everyAnsweredSaveSurvivesTheAppBeingKilled() {
        // Kill at different points of the stream: early, while the file is being rewritten, later.
        for (killAfter in listOf(1, 7, 23)) {
            val writer = Writer(start(folder.root, "write"))
            val acknowledged = writer.awaitSaves(killAfter)
            writer.process.destroyForcibly()
            assertTrue("the writer died", writer.process.waitFor(30, TimeUnit.SECONDS))

            // The app starts again, in a new process, and loads.
            val reader = start(folder.root, "read")
            val loaded =
                reader.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
            assertTrue("the reader finished", reader.waitFor(60, TimeUnit.SECONDS))
            val slot = loaded.removePrefix("loaded ").substringBefore(' ').toIntOrNull()
            assertTrue("no save survived the kill after save $acknowledged answered: $loaded", slot != null)
            assertTrue("save $slot is older than save $acknowledged, which the game was told was done", slot!! >= acknowledged)
            assertEquals("save $slot came back torn", "loaded $slot whole", loaded)
        }
    }

    private class Writer(
        val process: Process,
    ) {
        private val lines = process.inputStream.bufferedReader()

        /** Reads the writer's acknowledgements until it has answered [count] saves; returns the last save's number. */
        fun awaitSaves(count: Int): Int {
            var answered = 0
            var last = 0
            while (answered < count) {
                val line = lines.readLine() ?: error("writer exited: ${process.errorStream.bufferedReader().readText()}")
                if (line.startsWith("saved ")) {
                    last = line.removePrefix("saved ").toInt()
                    answered++
                }
            }
            return last
        }
    }

    private fun start(
        root: File,
        mode: String,
    ): Process {
        val java =
            ProcessHandle
                .current()
                .info()
                .command()
                .orElse("java")
        return ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), NappletStorageKillApp::class.java.name, root.absolutePath, mode)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    companion object {
        const val COORDINATE = "35128:ab:game"
        const val KEY = "save"
        val ACCOUNT = "0f".repeat(32)

        /** The production file, through the production store. */
        fun storageAt(root: File) = DataStoreNappletStorage(AppPreferenceStores(rootFilesDir = { root.absolutePath.toPath() }).getDataStore("napplet_storage")) { ACCOUNT }

        /** Save number [slot]: a game-sized payload anyone can rebuild from its number to check it is whole. */
        fun save(slot: Int): String {
            val bytes = ByteArray(48 * 1024).also { Random(slot.toLong()).nextBytes(it) }
            return "$slot:" + Base64.getEncoder().encodeToString(bytes)
        }
    }
}

/**
 * The app side, one process per run. `write` saves forever, saying so after each `set` returns, until
 * it is killed; `read` loads the save and says whether it is whole.
 */
object NappletStorageKillApp {
    @JvmStatic
    fun main(args: Array<String>) {
        val storage = NappletStorageKillTest.storageAt(File(args[0]))
        runBlocking {
            val saved = storage.get(NappletStorageKillTest.COORDINATE, NappletStorageKillTest.KEY)
            val start = saved?.substringBefore(':')?.toInt() ?: 0
            if (args[1] == "read") {
                println(if (saved == null) "loaded nothing" else "loaded $start " + if (saved == NappletStorageKillTest.save(start)) "whole" else "torn")
                System.out.flush()
                // DataStore's scope keeps the JVM alive; this run is done.
                Runtime.getRuntime().halt(0)
            }
            // Numbered on from the save it finds, as a game picks up where it left off.
            for (slot in start + 1..start + 100_000) {
                storage.set(NappletStorageKillTest.COORDINATE, NappletStorageKillTest.KEY, NappletStorageKillTest.save(slot))
                println("saved $slot")
                System.out.flush()
            }
        }
    }
}
