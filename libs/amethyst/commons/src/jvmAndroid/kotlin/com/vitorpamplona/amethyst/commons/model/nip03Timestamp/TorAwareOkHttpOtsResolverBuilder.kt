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
package com.vitorpamplona.amethyst.commons.model.nip03Timestamp

import com.vitorpamplona.quartz.nip03Timestamp.OtsResolver
import com.vitorpamplona.quartz.nip03Timestamp.OtsResolverBuilder
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpBitcoinExplorer
import com.vitorpamplona.quartz.nip03Timestamp.okhttp.OkHttpCalendar
import com.vitorpamplona.quartz.nip03Timestamp.ots.OtsBlockHeightCache
import okhttp3.OkHttpClient

class TorAwareOkHttpOtsResolverBuilder(
    val okHttpClient: (url: String) -> OkHttpClient,
    val isTorActive: (url: String) -> Boolean,
    val cache: OtsBlockHeightCache,
    val customExplorerUrl: () -> String? = { null },
) : OtsResolverBuilder {
    fun getAPI(usingTor: Boolean): String = BitcoinExplorerEndpoint.resolve(customExplorerUrl(), usingTor)

    override fun build(): OtsResolver =
        OtsResolver(
            explorer =
                OkHttpBitcoinExplorer(
                    baseUrl = {
                        getAPI(usingTor = isTorActive(OkHttpBitcoinExplorer.MEMPOOL_API_URL))
                    },
                    client = okHttpClient(OkHttpBitcoinExplorer.MEMPOOL_API_URL),
                    cache = cache,
                ),
            calendar = OkHttpCalendar(okHttpClient),
        )
}
