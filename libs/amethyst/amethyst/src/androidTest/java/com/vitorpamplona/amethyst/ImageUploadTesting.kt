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
package com.vitorpamplona.amethyst

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerType
import com.vitorpamplona.amethyst.commons.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.amethyst.commons.service.http.DefaultContentTypeInterceptor
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.uploads.ImageDownloader
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.Nip96Uploader
import com.vitorpamplona.amethyst.commons.service.uploads.nip96.ServerInfoRetriever
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.amethyst.service.uploads.prepare
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip98HttpAuth.HTTPAuthorizationEvent
import junit.framework.TestCase.fail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class ImageUploadTesting {
    companion object {
        val accountSettings = AccountSettings(KeyPair())
        val signer = NostrSignerInternal(accountSettings.keyPair)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val cache = LocalCache

        val blossomServerListState =
            BlossomServerListState(
                signer = signer,
                cache = cache,
                scope = scope,
                settings = accountSettings,
            )
    }

    val client =
        OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(DefaultContentTypeInterceptor("Amethyst/${BuildConfig.VERSION_NAME}"))
            .build()

    private fun getBitmap(): ByteArray {
        val bitmap = createBitmap(200, 300)
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                bitmap.setPixel(x, y, Color.rgb(Random.nextInt(), Random.nextInt(), Random.nextInt()))
            }
        }

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    private suspend fun testBase(server: ServerName) {
        if (server.type == ServerType.NIP96) {
            testNip96(server)
        } else {
            testBlossom(server)
        }
    }

    private suspend fun testBlossom(server: ServerName) {
        val paylod = getBitmap()
        val initialHash = sha256(paylod).toHexKey()
        val result =
            BlossomUploader()
                .upload(
                    openStream = { paylod.inputStream() },
                    hash = initialHash,
                    length = paylod.size.toLong(),
                    baseFileName = "filename.png",
                    contentType = "image/png",
                    alt = null,
                    sensitiveContent = null,
                    serverBaseUrl = server.baseUrl,
                    okHttpClient = { client },
                    httpAuth = blossomServerListState::createBlossomUploadAuth,
                    context = InstrumentationRegistry.getInstrumentation().targetContext,
                )

        assertEquals(server.baseUrl, "image/png", result.type)
        assertEquals(server.baseUrl, paylod.size.toLong(), result.size)
        assertEquals(server.baseUrl, initialHash, result.sha256)
        // assertEquals(server.baseUrl, "${server.baseUrl}/$initialHash", result.url?.removeSuffix(".png"))

        val imageData: ByteArray =
            ImageDownloader().waitAndGetImage(result.url!!) { client }?.bytes
                ?: run {
                    fail("${server.name}: Should not be null")
                    return
                }

        val downloadedHash = sha256(imageData).toHexKey()
        assertEquals(server.baseUrl, initialHash, downloadedHash)
    }

    private suspend fun testNip96(server: ServerName) {
        val serverInfo =
            ServerInfoRetriever()
                .loadInfo(
                    server.baseUrl,
                ) { client }

        val payload = getBitmap()
        val result =
            Nip96Uploader()
                .upload(
                    openStream = { payload.inputStream() },
                    length = payload.size.toLong(),
                    contentType = "image/png",
                    alt = null,
                    sensitiveContent = null,
                    server = serverInfo,
                    okHttpClient = { client },
                    onProgress = {},
                    httpAuth = { url, method, body ->
                        signer.sign(HTTPAuthorizationEvent.build(url, method, body))
                    },
                )

        val url = result.url!!
        val size = result.size?.toInt()
        val dim = result.dimension
        val hash = result.sha256

        Assert.assertTrue("${server.name}: Invalid result url", url.startsWith("http"))

        val imageData: ByteArray =
            ImageDownloader().waitAndGetImage(url) { client }?.bytes
                ?: run {
                    fail("${server.name}: Should not be null")
                    return
                }

        val prepared =
            FileHeader.prepare(
                imageData,
                "image/png",
                null,
            )

        prepared.fold(
            onSuccess = {
                if (dim != null) {
                    assertEquals("${server.name}: Invalid dimensions", it.dim.toString(), dim.toString())
                }
                if (size != null) {
                    assertEquals("${server.name}: Invalid size", it.size, size)
                }
            },
            onFailure = { fail("${server.name}: It should not fail") },
        )

        // delay(1000)

        // assertTrue(Nip96Uploader(account).delete(ox, contentType, serverInfo))
    }

    @Test
    fun runTestOnDefaultServers() =
        runBlocking {
            DEFAULT_MEDIA_SERVERS.forEach {
                // skip paid servers and primal server is buggy.
                try {
                    if (!it.name.contains("Paid") && !it.name.contains("Primal")) {
                        testBase(it)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    fail("Could not upload to: ${it.baseUrl}: ${e.message}")
                }
            }
        }

    @Test()
    fun testNostrCheck() =
        runBlocking {
            testBase(ServerName("nostrcheck.me", "https://nostrcheck.me", ServerType.NIP96))
        }

    @Test()
    @Ignore("Not Working anymore")
    fun testNostrage() =
        runBlocking {
            testBase(ServerName("nostrage", "https://nostrage.com", ServerType.NIP96))
        }

    @Test()
    fun testNostrBuild() =
        runBlocking {
            testBase(ServerName("nostr.build", "https://nostr.build", ServerType.NIP96))
        }

    @Test()
    @Ignore("Returns invalid hash")
    fun testSovbit() =
        runBlocking {
            testBase(ServerName("sovbit", "https://files.sovbit.host", ServerType.Blossom))
        }

    @Test()
    @Ignore("Returns invalid image size")
    fun testNostrPic() =
        runBlocking {
            testBase(ServerName("nostpic.com", "https://nostpic.com", ServerType.NIP96))
        }

    @Test(expected = RuntimeException::class)
    fun testSprovoostNl() =
        runBlocking {
            testBase(ServerName("sprovoost.nl", "https://img.sprovoost.nl/", ServerType.NIP96))
        }

    @Ignore("Changes sha256")
    fun testPrimalBlossom() =
        runBlocking {
            testBase(ServerName("primal.net", "https://blossom.primal.net", ServerType.Blossom))
        }

    @Test()
    @Ignore("Not Working anymore/ Timeout")
    fun testNostrCheckBlossom() =
        runBlocking {
            testBase(ServerName("nostrcheck", "https://cdn.nostrcheck.me", ServerType.Blossom))
        }

    @Ignore("Requires Payment")
    fun testSatelliteBlossom() =
        runBlocking {
            testBase(ServerName("satellite", "https://cdn.satellite.earth", ServerType.Blossom))
        }
}
