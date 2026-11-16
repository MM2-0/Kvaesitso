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
package com.vitorpamplona.amethyst.service.images

import android.content.Context
import android.os.Build
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import coil3.util.Logger
import coil3.video.VideoFrameDecoder
import com.vitorpamplona.amethyst.commons.service.http.BlossomReadAuthTokenProvider
import com.vitorpamplona.amethyst.commons.service.image.buildAmethystImageLoader
import com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10.BlossomServerResolver
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import okhttp3.Call

class ImageLoaderSetup {
    companion object {
        val gifFactory =
            if (Build.VERSION.SDK_INT >= 28) {
                // Not Coil's AnimatedImageDecoder.Factory: see [AnimatedImageDecoderFactory] for
                // why decoding straight from the disk-cache file matters here.
                AnimatedImageDecoderFactory()
            } else {
                GifDecoder.Factory()
            }
        val svgFactory = SvgDecoder.Factory()

        val debugLogger = if (isDebug) MyDebugLogger() else null

        @OptIn(DelicateCoilApi::class, ExperimentalCoilApi::class)
        fun setup(
            app: Context,
            diskCache: () -> DiskCache,
            memoryCache: () -> MemoryCache,
            blossomServerResolver: () -> BlossomServerResolver,
            callFactory: (url: String) -> Call.Factory,
            thumbnailCache: ThumbnailDiskCache,
            backgroundScope: CoroutineScope,
            // Signs the BUD-01 retry when a gated host answers 401. Null keeps every
            // fetch anonymous (tests, pre-configuration call sites).
            readAuth: BlossomReadAuthTokenProvider? = null,
        ) {
            SingletonImageLoader.setUnsafe(
                buildAmethystImageLoader(
                    context = app,
                    diskCache = diskCache,
                    memoryCache = memoryCache,
                    callFactory = callFactory,
                    blossomServerResolver = blossomServerResolver,
                    readAuth = readAuth,
                    logger = debugLogger,
                    // Keeps the platform ImageDecoder reachable for whatever comes back; see
                    // [SystemFileSystemFetcher].
                    wrapNetworkFetcher = { fetcher, key -> fetcher.onSystemFileSystem(key) },
                    decoders = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            add(AvifAnimatedDecoderFactory()) // handle animated AVIF that Coil's AnimatedImageDecoder misses
                        }
                        add(gifFactory)
                        add(svgFactory)
                        add(VideoFrameDecoder.Factory())
                    },
                    fetchers = { concurrentRequests ->
                        add(ProfilePictureFetcher.Factory(thumbnailCache, callFactory, backgroundScope, concurrentRequests, readAuth))
                        add(ProfilePictureFetcher.BKeyer)
                    },
                ),
            )
        }
    }
}

/**
 * Copied from Coil to block the printout of stack traces
 */
class MyDebugLogger(
    override var minLevel: Logger.Level = Logger.Level.Debug,
) : Logger {
    override fun log(
        tag: String,
        level: Logger.Level,
        message: String?,
        throwable: Throwable?,
    ) {
        val msg = message ?: throwable?.message
        if (msg != null) {
            when (level) {
                Logger.Level.Error -> Log.e(tag, msg)
                Logger.Level.Verbose -> Log.d(tag, msg)
                Logger.Level.Debug -> Log.d(tag, msg)
                Logger.Level.Info -> Log.i(tag, msg)
                Logger.Level.Warn -> Log.w(tag, msg)
            }
        }
    }
}

internal fun <P, T> singleParameterLazy(initializer: (P) -> T) = SingleParameterLazy(initializer)

internal class SingleParameterLazy<P, T>(
    initializer: (P) -> T,
) : Any() {
    private var initializer: ((P) -> T)? = initializer
    private var value: Any? = UNINITIALIZED

    @Suppress("UNCHECKED_CAST")
    fun get(parameter: P): T {
        val value1 = value
        if (value1 !== UNINITIALIZED) {
            return value1 as T
        }

        return synchronized(this) {
            val value2 = value
            if (value2 !== UNINITIALIZED) {
                value2 as T
            } else {
                val newValue = initializer!!(parameter)
                value = newValue
                initializer = null
                newValue
            }
        }
    }
}

private object UNINITIALIZED
