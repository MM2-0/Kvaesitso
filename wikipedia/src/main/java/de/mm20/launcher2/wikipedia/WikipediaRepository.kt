package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.data.Wikipedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

interface WikipediaRepository {
    fun search(query: String): Flow<Wikipedia?>
}

class WikipediaRepositoryImpl(
    private val context: Context
) : WikipediaRepository, KoinComponent {

    private val dataStore: LauncherDataStore by inject()

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

    private val wikipediaService by lazy {
        retrofit.create(WikipediaApi::class.java)
    }


    override fun search(query: String): Flow<Wikipedia?> = channelFlow {
        send(null)
        withContext(Dispatchers.IO) {
            httpClient.dispatcher.cancelAll()
        }
        if (query.isBlank()) return@channelFlow

        dataStore.data.map { it.wikipediaSearch }.collectLatest {
            if (it.enabled) {
                send(queryWikipedia(query, it.images))
            } else {
                send(null)
            }
        }
    }

    private suspend fun queryWikipedia(query: String, loadImages: Boolean): Wikipedia? {


        val result = try {
            wikipediaService.search(query)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }

        val page = result.query?.pages?.values?.toList()?.getOrNull(0) ?: return null

        val image = if (loadImages) {
            val width = context.resources.displayMetrics.widthPixels / 2
            val imageResult = try {
                wikipediaService.getPageImage(page.pageid, width)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                return null
            }
            imageResult.query?.pages?.values?.toList()?.getOrNull(0)?.thumbnail?.source
        } else null

        return Wikipedia(
            label = page.title,
            id = page.pageid,
            text = page.extract,
            image = image
        )
    }

}