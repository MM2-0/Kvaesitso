package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.data.Wikipedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface WikipediaRepository {
    fun search(query: String): Flow<Wikipedia?>
}

class WikipediaRepositoryImpl(
    private val context: Context
): WikipediaRepository {

    private val httpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(200, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .writeTimeout(1000, TimeUnit.MILLISECONDS)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .client(httpClient)
            .baseUrl(context.getString(R.string.wikipedia_url))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val wikipediaService by lazy {
        retrofit.create(WikipediaApi::class.java)
    }


    override fun search(query: String): Flow<Wikipedia?> = channelFlow {
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

        val result = try {
            wikipediaService.search(query)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return@channelFlow
        }

        val page = result.query?.pages?.values?.toList()?.getOrNull(0) ?: return@channelFlow

        val image = if (LauncherPreferences.instance.searchWikipediaPictures) {
            val width = context.resources.displayMetrics.widthPixels / 2
            val imageResult = try {
                wikipediaService.getPageImage(page.pageid, width)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                return@channelFlow
            }
            imageResult.query?.pages?.values?.toList()?.getOrNull(0)?.thumbnail?.source
        } else null

        val wiki = Wikipedia(
            label = page.title,
            id = page.pageid,
            text = page.extract,
            image = image
        )
        send(wiki)
    }

}