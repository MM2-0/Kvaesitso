package de.mm20.launcher2.websites

import android.content.Context
import de.mm20.launcher2.search.data.Website
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface WebsiteRepository {
    fun search(query: String): Flow<Website?>
}

class WebsiteRepositoryImpl(val context: Context) : WebsiteRepository {

    private val httpClient = OkHttpClient
        .Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(1000, TimeUnit.MILLISECONDS)
        .build()

    override fun search(query: String): Flow<Website?> = channelFlow {
        send(null)
        httpClient.dispatcher.run {
            runningCalls().forEach {
                it.cancel()
            }
            queuedCalls().forEach {
                it.cancel()
            }
        }
        if (query.isBlank()) return@channelFlow
        val website = withContext(Dispatchers.IO) {
            Website.search(context, query, httpClient)
        }
        send(website)
    }
}