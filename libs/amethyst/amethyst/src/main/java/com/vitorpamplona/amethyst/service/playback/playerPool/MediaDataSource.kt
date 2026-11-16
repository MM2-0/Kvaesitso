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
package com.vitorpamplona.amethyst.service.playback.playerPool

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.vitorpamplona.amethyst.commons.service.http.DynamicCallFactory
import com.vitorpamplona.amethyst.commons.service.http.LocalBlossomCacheRedirectInterceptor
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import kotlinx.coroutines.runBlocking

/**
 * Where video bytes come from: OkHttp through [okHttpClient] (direct or Tor, chosen per call), with
 * `blossom:` URIs resolved to a server first. The player and the feed prefetcher both read through
 * this, so a video's head that the prefetcher cached is what the player finds under the same key.
 */
@OptIn(UnstableApi::class)
fun mediaDataSourceFactory(
    okHttpClient: DynamicCallFactory,
    blossomServerResolver: BlossomServerResolver,
): DataSource.Factory {
    // Marked as media so the local Blossom cache may serve these downloads.
    val dataSourceFactory =
        OkHttpDataSource
            .Factory(okHttpClient)
            .setDefaultRequestProperties(mapOf(LocalBlossomCacheRedirectInterceptor.MEDIA_HEADER to LocalBlossomCacheRedirectInterceptor.MEDIA))

    return ResolvingDataSource.Factory(
        dataSourceFactory,
        ResolvingDataSource.Resolver { dataSpec: DataSpec ->
            val originalUri: Uri = dataSpec.uri
            val scheme = originalUri.scheme
            if (scheme != null && blossomServerResolver.canResolve(scheme)) {
                val serverUrl =
                    runBlocking {
                        blossomServerResolver.findServers(originalUri.toString())
                    }
                if (serverUrl != null) {
                    return@Resolver dataSpec.withUri(serverUrl.serverUrl.toUri())
                }
            }
            dataSpec
        },
    )
}
