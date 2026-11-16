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
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.vitorpamplona.crypto.SymmetricCipher
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.io.InputStream

class EncryptFilesResult(
    val uri: Uri,
    val contentType: String?,
    val originalHash: String,
    val encryptedHash: String,
    val size: Long?,
)

class EncryptFiles {
    /**
     * Encrypts [inputFile] into a temp file. [SymmetricCipher] only takes a whole array, so the
     * file and its ciphertext are both in memory at once: a file too big for that throws
     * [OutOfMemoryError], and the temp file is deleted on any failure.
     */
    fun encryptFile(
        context: Context,
        inputFile: Uri,
        cipher: SymmetricCipher,
    ): EncryptFilesResult {
        val resolver = context.contentResolver

        val encryptedFile = File.createTempFile("EncryptFiles", ".encrypted", context.getCacheDir())

        try {
            return encrypt(resolver.openInputStream(inputFile)!!, encryptedFile, cipher)
        } catch (e: Throwable) {
            if (!encryptedFile.delete() && encryptedFile.exists()) Log.w("EncryptFiles") { "Could not delete ${encryptedFile.path}" }
            throw e
        }
    }

    private fun encrypt(
        input: InputStream,
        encryptedFile: File,
        cipher: SymmetricCipher,
    ): EncryptFilesResult {
        input.use { inputStream ->
            val bytes = inputStream.readBytes()
            val originalHash = sha256(bytes).toHexKey()
            val encrypted = cipher.encrypt(bytes)
            val encryptedHash = sha256(encrypted).toHexKey()

            encryptedFile.outputStream().use { outputStream ->
                outputStream.write(encrypted)
            }

            return EncryptFilesResult(
                encryptedFile.toUri(),
                "application/octet-stream",
                originalHash,
                encryptedHash,
                encrypted.size.toLong(),
            )
        }
    }
}
