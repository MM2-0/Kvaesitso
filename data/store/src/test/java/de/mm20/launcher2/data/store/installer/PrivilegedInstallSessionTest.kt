package de.mm20.launcher2.data.store.installer

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PrivilegedInstallSessionTest {
    private fun apk() = File.createTempFile("session-", ".apk").apply { writeBytes(ByteArray(32)) }

    @Test fun writesThroughStdinAndCommitsTheCreatedSession() = runBlocking {
        val file = apk()
        val calls = mutableListOf<List<String>>()
        try {
            val result = installPrivilegedSession(file, 10) { args, input ->
                calls += args
                when (args[1]) {
                    "install-create" -> { assertNull(input); assertTrue(args.contains("10")); InstallCommandResult(0, "Success: created install session [321]") }
                    "install-write" -> { assertSame(file, input); assertEquals(listOf("321", "base.apk", "-"), args.takeLast(3)); InstallCommandResult(0, "Success: streamed 32 bytes") }
                    else -> { assertEquals(listOf("pm", "install-commit", "321"), args); InstallCommandResult(0, "Success\n") }
                }
            }
            assertEquals(BackendInstallResult.Success, result)
            assertEquals(3, calls.size)
        } finally { file.delete() }
    }

    @Test fun failedWriteAbandonsWithoutCommitting() = runBlocking {
        val file = apk()
        val calls = mutableListOf<String>()
        try {
            val result = installPrivilegedSession(file, 0) { args, _ ->
                calls += args[1]
                when (args[1]) {
                    "install-create" -> InstallCommandResult(0, "Success: created install session [9]")
                    "install-write" -> InstallCommandResult(1, "Failure [INSTALL_FAILED_INVALID_APK]")
                    "install-abandon" -> InstallCommandResult(0, "Success")
                    else -> InstallCommandResult(1, "Failure [DENIED]")
                }
            }
            assertTrue(result is BackendInstallResult.Failed)
            assertEquals(listOf("install-create", "install-write", "install-abandon", "install"), calls)
        } finally { file.delete() }
    }

    @Test fun parsesSessionIdsWithoutBracketsAndTreatsPrefixedSuccessAsOk() {
        assertEquals("44", parseInstallSessionId("Success: created install session [44]"))
        assertEquals("9", parseInstallSessionId("created session 9"))
        assertTrue(installCommandSucceeded(0, "pkg: /\nSuccess: streamed 12 bytes"))
        assertTrue(installCommandSucceeded(0, ""))
        assertFalse(installCommandSucceeded(0, "Failure [INSTALL_FAILED_INVALID_APK]"))
    }

    @Test fun malformedCreateDoesNotInventASession() = runBlocking {
        val file = apk()
        try {
            var calls = 0
            val result = installPrivilegedSession(file, 0) { _, _ -> calls++; InstallCommandResult(255, "Permission denied") }
            assertTrue(result is BackendInstallResult.Failed)
            assertEquals(2, calls)
        } finally { file.delete() }
    }
}
