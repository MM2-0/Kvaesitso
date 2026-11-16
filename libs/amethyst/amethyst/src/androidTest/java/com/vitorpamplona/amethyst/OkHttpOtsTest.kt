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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.OtsEvent
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpOtsResolverBuilder
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OkHttpOtsTest {
    val otsEvent = "{\"content\":\"AE9wZW5UaW1lc3RhbXBzAABQcm9vZgC/ieLohOiSlAEIqCiiW0FlsU9lqK5f1A+cL6CGJ1Ah4V/A1yNJY/stUE3wECJz6ng/QxU5Z6xwaMx97qkI//AQqJv8bEMrGTplGWRv5qm4DgjxIHkcQqzpL0Fjr9VBAAijDe0IsQYpOhw1SIjZIgQa6i16CPEEZck7CvAIxR0AloJzCZoAg9/jDS75DI4uLWh0dHBzOi8vYWxpY2UuYnRjLmNhbGVuZGFyLm9wZW50aW1lc3RhbXBzLm9yZ//wEOwPtjIkKI1hmtv9t1kuxZcI8QRlyTsK8Ahl0wrCSggZzgCD3+MNLvkMjiwraHR0cHM6Ly9ib2IuYnRjLmNhbGVuZGFyLm9wZW50aW1lc3RhbXBzLm9yZ//wEE1dVGa8JCuf2ek0c5ybDKII8SCBoVz8Sal45Kd1O8STWIGJTcl5JPtAZBZitqk3BE9MqAjxBGXJOwrwCHoGVgAZi9q9AIPf4w0u+QyOKShodHRwczovL2Zpbm5leS5jYWxlbmRhci5ldGVybml0eXdhbGwuY29t8BAFZFXFYg7DJJ0OzjmJ0FKWCPEEZck7CvAI0M49IcBR5bf/AIPf4w0u+QyOIyJodHRwczovL2J0Yy5jYWxlbmRhci5jYXRhbGxheHkuY29tCPEgoE3IfYTmxxo4W/x/QYp/NGX6Wu93gSQkwbpjpOhZORcI8SDWLLurVQaXHdUuwivCfTfuxYCaq+AzypSGqLDAVocrEgjwIBfgjta16y13Gp4etQOCa9YiKEcM+/9AieG/vZolr3IDCPAgMR2zFCb384CEi8tVuI2fHgLT3I9zpe7oqJTzCcEqxWEI8SAJSdgeeosr7IxdOt8r7f0ipWc8FI6GAhgep8zSRgWikAjxIGxYmtCsC79Tx4z4YsT1WuMo+ycMkwhGQsQltF597cchCPAgCLrBf9vR1Aex6yY+vSkXAvLjMKdMqM/a1g8zNPwLeJcI8SBDCbTk4CczTuiIyZeUyYRVh31BZdjaSd2nU/pBQxu+6QjwIOwqE9/WqGC6CHH0i+tr7edvYX5PstSDf08KmnMqsqCoCPEgQIdEBg358vfek3Qjfyrgl51iCU6WUWmThsGLPDTcB0QI8SAX4dp64iI8pBx+zBqAQwUN6XgZ1cEfT8+2vha/9I1vzwjxWQEAAAAB8YJxvxJJth8OnxIV6UOXveIZAcJPTHcAkWgnucpuYqYAAAAAAP3///8CRhoMAAAAAAAWABTUUZK82uAvbU3vVyaaPIOddZBicwAAAAAAAAAAImog8ARCqgwACAjwIB9TcMDLhzgeS1Uw647lNvCfWECkkUvfrrOe6nay0sGdCAjwICYfs90sbPggoMICyOHGYbmOzop2L9mlnh4xqiBLY7yPCAjxINDiVWOBHnRmGJleQdB9myvJAJbNJ9kciZlTOkgJy89mCAjwIHfxqDLdwycj1Vtyth2CaSDdLQwiey9oV6Xov4stLpWNCAjxIGurJYpKJnKp9+y7MAdC+gXgHOiAu5P3RRUFW9l5hGaCCAjxICXqs9hdY0QMP/MNeqlt6s7xaIYtEXZ1CLvou5gaZNEICAjxIHdYxVeI76NXbT2zHcv6lw+v819Ooib7KWxc1GAsiX2fCAjwICPWdi3uBXOlIdmYi+V9C7wAqLyGE4DMoHD+GtvLizqiCAjwIOF2vENtWN5okEMMS+JSf1SGTY9yYP9j0JjXLbC1s+N1CAjxINWsgCtsPxhRNbe372k8/20WDbiL9e8934hGF256DvRECAjwII3mi+Li06j10ORxg0dYMkcsyGb115Jiqq1YEV3K/u+aCAgABYiWDXPXGQEDw9Qy\",\"created_at\":1707690688,\"id\":\"759f9da5846e936fab06766a524b36ba71c03bbc69ad0944fb8ee4bb1f3dd705\",\"kind\":1040,\"pubkey\":\"82fbb08c8ef45c4d71c88368d0ae805bc62fb92f166ab04a0b7a0c83d8cbc29a\",\"sig\":\"07c7896c8cbb97b5d7483097590c9d31b73f35c1ad9e752002bb5c1776cbd852e1d32704333d6930c9bc3e40f8b899a1f2e9f91cc3bf797d86acdecba7792576\",\"tags\":[[\"e\",\"a828a25b4165b14f65a8ae5fd40f9c2fa086275021e15fc0d7234963fb2d504d\"],[\"p\",\"595ca8eaace5899cb6ab7e2542bfc972136376f2eabc09287f1857eb8f167e53\"],[\"alt\",\"NIP-03 time stamp\"]]}"
    val otsEvent2 = "{\"content\":\"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\",\"created_at\":1706324334,\"id\":\"2ad074ddb7724eb13b4244b49cf2321b1057f37fdf8ce102e6329b839cf763a9\",\"kind\":1040,\"pubkey\":\"82fbb08c8ef45c4d71c88368d0ae805bc62fb92f166ab04a0b7a0c83d8cbc29a\",\"sig\":\"ad7274bb32ba9e9cfdbd52f4887e8a2fda1047c75a7185b2ab7ff254ebac14ed48a2b60737494d655e24c9400eeeec7e29293a77bfcaafaecd94b350c9a2c22b\",\"tags\":[[\"e\",\"a8634f5368e17789e8fb3836cad0e4b9fe4f2288afafc28723c04bebc68a18d6\"],[\"p\",\"c31e22c3715c1bde5608b7e0d04904f22f5fc453ba1806d21c9f2382e1e58c6c\"],[\"alt\",\"NIP-03 time stamp\"]]}"
    val otsPendingEvent = "{\"id\":\"12fa15ad4b4cf9dc5940389325b69b93c5c1f59c049c701ee669b275299fdaf1\",\"pubkey\":\"dcaa6c8a2f47b6fef4a34b20e8843c59dbe7c5f07a402338c09fd147dd01d22b\",\"created_at\":1708877521,\"kind\":1040,\"tags\":[[\"e\",\"a8634f5368e17789e8fb3836cad0e4b9fe4f2288afafc28723c04bebc68a18d6\"],[\"alt\",\"Opentimestamps Attestation\"]],\"content\":\"AE9wZW5UaW1lc3RhbXBzAABQcm9vZgC/ieLohOiSlAEIqGNPU2jhd4no+zg2ytDkuf5PIoivr8KHI8BL68aKGNbwELidvzr0usf55CkpKf6OABQI//AQK3sWd2tq+7KO8YNJIARJugjxBGXbZtPwCL0H4/7GL5+SAIPf4w0u+QyOLCtodHRwczovL2JvYi5idGMuY2FsZW5kYXIub3BlbnRpbWVzdGFtcHMub3Jn//AQPDZsJgN1TnJXoUzlsgo93wjwIIfBc7LUqkCbC1BLZRZ+6LXztK50UdH5xe7fn40bupkrCPEEZdtm0/AI0CADXN5ZIncAg9/jDS75DI4uLWh0dHBzOi8vYWxpY2UuYnRjLmNhbGVuZGFyLm9wZW50aW1lc3RhbXBzLm9yZ/AQcELcSrE04cuGKlZQf2LeVwjwILUDSf9vK2GaefKTpn/LV2oUsQaA5WbqaP3C+1ZxQfRNCPEEZdtm0/AIbCtb+yRXFqUAg9/jDS75DI4pKGh0dHBzOi8vZmlubmV5LmNhbGVuZGFyLmV0ZXJuaXR5d2FsbC5jb20=\",\"sig\":\"f6854c0228c15c08aeb70bbabe9ed87bbb7289fab31b13cabac15138bb71179553e06080b83f4a813fbdaf614f63293beea3fc73fe865da6551193fa4d38de04\"}"

    val otsEvent2Digest = "a8634f5368e17789e8fb3836cad0e4b9fe4f2288afafc28723c04bebc68a18d6"
    val otsCache = OtsBlockHeightCache()

    val resolver =
        OkHttpOtsResolverBuilder(
            otsCache,
        ) { OkHttpClient.Builder().build() }.build()

    @Test
    fun verifyNostrEvent() =
        runBlocking {
            val ots = OptimizedJsonMapper.fromJson(otsEvent) as OtsEvent
            println(resolver.info(ots.otsByteArray()))
            assertEquals(1707688818L, ots.verify(resolver))
        }

    @Test
    fun verifyNostrEvent2() =
        runBlocking {
            val ots = OptimizedJsonMapper.fromJson(otsEvent2) as OtsEvent
            println(resolver.info(ots.otsByteArray()))
            assertEquals(1706322179L, ots.verify(resolver))
        }

    @Test
    fun verifyNostrPendingEvent() =
        runBlocking {
            val ots = OptimizedJsonMapper.fromJson(otsPendingEvent) as OtsEvent
            println(resolver.info(ots.otsByteArray()))
            assertEquals(null, ots.verify(resolver))
        }

    @Test
    fun createOTSEventAndVerify() =
        runBlocking {
            val signer = NostrSignerInternal(KeyPair())

            val otsFile = OtsEvent.stamp(otsEvent2Digest, resolver)

            val ots =
                runBlocking {
                    signer.sign(OtsEvent.build(otsEvent2Digest, otsFile))
                }

            println(ots.toJson())
            println(resolver.info(ots.otsByteArray()))

            // Should not be valid because we need to wait for confirmations
            assertEquals(null, ots.verify(resolver))
        }
}
