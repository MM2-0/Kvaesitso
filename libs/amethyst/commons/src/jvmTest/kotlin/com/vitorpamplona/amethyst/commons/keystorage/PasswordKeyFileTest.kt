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
package com.vitorpamplona.amethyst.commons.keystorage

import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordKeyFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val key = "7f".repeat(32)
    private val password = "correct horse battery"

    // A cheap scrypt cost: the format and the flow are under test here, not NIP-49's KDF.
    private fun newFile(legacy: File? = null) =
        PasswordKeyFile(
            file = File(folder.root, "keys.ncryptsec"),
            legacyFile = legacy,
            legacyDecrypt = { c, p -> SecureKeyStorage.create().decryptData(c, p) },
            logN = 4,
        )

    @Test
    fun theFirstKeyAsksForAPasswordAndReadsBack() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val job = script.start(this)
            val file = newFile()

            file.put("npub1a", key, script.prompts)
            assertEquals(key, file.get("npub1a", script.prompts))
            assertTrue(file.isUnlocked)
            assertEquals(KeyStorageStatus.PasswordFile(hasPassword = true, unlocked = true), file.status())
            assertEquals(1, script.asked.size)
            job.cancel()
        }

    @Test
    fun aNewRunAsksOnceAndAgainAfterAWrongPassword() =
        runBlocking<Unit> {
            val setup = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val setupJob = setup.start(this)
            newFile().put("npub1a", key, setup.prompts)
            setupJob.cancel()

            val script = ScriptedPrompts("wrong password", password)
            val job = script.start(this)
            val file = newFile()
            assertFalse(file.isUnlocked)
            assertEquals(key, file.get("npub1a", script.prompts))
            assertEquals(listOf(false, true), script.asked.map { (it as KeyPrompt.Unlock).wrongPassword })
            // Unlocked for the run: no more questions.
            assertEquals(key, file.get("npub1a", script.prompts))
            assertEquals(2, script.asked.size)
            job.cancel()
        }

    @Test
    fun dontRememberKeepsNothingAndDoesNotAskAgain() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.DontRemember)
            val job = script.start(this)
            val file = newFile()

            assertFailsWith<KeyNotKeptException> { file.put("npub1a", key, script.prompts) }
            assertFailsWith<KeyNotKeptException> { file.put("npub1b", key, script.prompts) }
            assertEquals(1, script.asked.size)
            assertEquals(KeyStorageStatus.NotKept, file.status())
            assertFalse(File(folder.root, "keys.ncryptsec").exists())
            job.cancel()
        }

    /** Save keys (login screen, Key Backup): a "Don't save" taken back asks for a password again. */
    @Test
    fun allowKeepingAfterDontRememberAsksAgainAndKeeps() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.DontRemember, NewPasswordAnswer.Password("correct horse"))
            val job = script.start(this)
            val file = newFile()

            assertFailsWith<KeyNotKeptException> { file.put("npub1a", key, script.prompts) }
            assertEquals(KeyStorageStatus.NotKept, file.status())

            file.allowKeeping()
            assertEquals(KeyStorageStatus.PasswordFile(hasPassword = false, unlocked = false), file.status())

            file.put("npub1a", key, script.prompts)
            assertEquals(2, script.asked.size)
            assertEquals(key, file.get("npub1a", script.prompts))
            assertEquals(KeyStorageStatus.PasswordFile(hasPassword = true, unlocked = true), file.status())
            job.cancel()
        }

    @Test
    fun skippingTheUnlockReadsNothingForTheRun() =
        runBlocking<Unit> {
            val setup = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val setupJob = setup.start(this)
            newFile().put("npub1a", key, setup.prompts)
            setupJob.cancel()

            val script = ScriptedPrompts(null)
            val job = script.start(this)
            val file = newFile()
            assertNull(file.get("npub1a", script.prompts))
            assertNull(file.get("npub1a", script.prompts))
            assertEquals(1, script.asked.size)
            assertIs<KeyStorageStatus.PasswordFile>(file.status())
            job.cancel()
        }

    @Test
    fun keepingAKeyAfterASkippedUnlockFailsAsLockedNotAsDeclined() =
        runBlocking<Unit> {
            val setup = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val setupJob = setup.start(this)
            newFile().put("npub1a", key, setup.prompts)
            setupJob.cancel()

            val script = ScriptedPrompts(null)
            val job = script.start(this)
            val file = newFile()
            assertNull(file.get("npub1a", script.prompts))
            // Logging in again with the key: the user skipped the password, they did not decline.
            val failure = assertFailsWith<SecureStorageException> { file.put("npub1a", key, script.prompts) }
            assertIs<KeyFileLockedException>(failure)
            assertEquals(1, script.asked.size)
            // The key already kept stays for the next run.
            assertTrue(File(folder.root, "keys.ncryptsec").readText().contains("npub1a="))
            job.cancel()
        }

    @Test
    fun withNothingToAskNoKeyIsKept() =
        runBlocking<Unit> {
            assertFailsWith<SecureStorageException> { newFile().put("npub1a", key, null) }
        }

    @Test
    fun onlyPrivateKeysFit() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val job = script.start(this)
            assertFailsWith<SecureStorageException> { newFile().put("metadata", "not a key", script.prompts) }
            assertTrue(script.asked.isEmpty())
            job.cancel()
        }

    @Test
    fun theOldConsoleFileMovesIntoTheNewOneOnUnlock() =
        runBlocking<Unit> {
            val legacy = File(folder.root, "keys.enc")
            legacy.writeText("npub1old:" + SecureKeyStorage.create().encryptData(key, password))

            val script = ScriptedPrompts(password)
            val job = script.start(this)
            val file = newFile(legacy)
            assertTrue(file.hasPassword())

            file.unlock(script.prompts)
            assertEquals(key, file.get("npub1old", script.prompts))
            assertFalse(legacy.exists())
            assertTrue(File(folder.root, "keys.enc.migrated").exists())
            job.cancel()
        }

    @Test
    fun aKeyOnlyTheOldConsoleFileHoldsIsReadNotReportedAbsent() =
        runBlocking<Unit> {
            val metadataKey = "c2VjcmV0LWFlcy1rZXktYnl0ZXMtMzItbG9uZy4uLi4="
            val cipher = SecureKeyStorage.create()
            val legacy = File(folder.root, "keys.enc")
            legacy.writeText(
                "npub1old:" + cipher.encryptData(key, password) + "\n" +
                    "account-metadata-key:" + cipher.encryptData(metadataKey, password),
            )

            val script = ScriptedPrompts(password)
            val job = script.start(this)
            val file = newFile(legacy)
            // Nothing in the new file yet: before, both read as absent without asking.
            assertEquals(key, file.get("npub1old", script.prompts))
            assertEquals(metadataKey, file.get("account-metadata-key", script.prompts))
            assertEquals(1, script.asked.size)
            // The metadata key cannot move into the NIP-49 file, so the old file stays readable.
            assertTrue(legacy.exists())
            assertEquals(metadataKey, file.get("account-metadata-key", script.prompts))
            job.cancel()
        }

    @Test
    fun anUnreadableOldConsoleFileDoesNotLockTheDevice() =
        runBlocking<Unit> {
            val legacy = File(folder.root, "keys.enc")
            legacy.writeText("not the old format\n")

            val script = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val job = script.start(this)
            val file = newFile(legacy)
            assertFalse(file.hasPassword())
            // A new password can be chosen instead of an unlock nothing could ever pass.
            file.put("npub1a", key, script.prompts)
            assertIs<KeyPrompt.CreatePassword>(script.asked.single())
            assertEquals(key, file.get("npub1a", script.prompts))
            job.cancel()
        }

    @Test
    fun deletingTheLastKeyRemovesTheFile() =
        runBlocking<Unit> {
            val script = ScriptedPrompts(NewPasswordAnswer.Password(password))
            val job = script.start(this)
            val file = newFile()
            file.put("npub1a", key, script.prompts)
            assertTrue(file.delete("npub1a"))
            assertFalse(file.delete("npub1a"))
            assertFalse(File(folder.root, "keys.ncryptsec").exists())
            job.cancel()
        }
}
