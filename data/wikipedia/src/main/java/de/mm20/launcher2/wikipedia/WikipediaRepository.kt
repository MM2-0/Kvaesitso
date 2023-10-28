package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


internal class WikipediaRepository(
    private val context: Context,
    private val dataStore: LauncherDataStore
) : SearchableRepository<Article> {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val httpClient = OkHttpClient
        .Builder()
        .connectTimeout(200, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .writeTimeout(1000, TimeUnit.MILLISECONDS)
        .build()

    private lateinit var retrofit: Retrofit

    init {
        scope.launch {
            dataStore.data
                .map { it.wikipediaSearch.customUrl }
                .distinctUntilChanged()
                .collectLatest {
                    try { retrofit = Retrofit.Builder()
                            .client(httpClient)
                            .baseUrl(it.takeIf { !it.isNullOrBlank() }
                                ?: context.getString(R.string.wikipedia_url))
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                        wikipediaService = retrofit.create(WikipediaApi::class.java)
                    } catch (e: IllegalArgumentException) {
                        CrashReporter.logException(e)
                    }
                }
        }
    }

    private lateinit var wikipediaService: WikipediaApi


    override fun search(query: String): Flow<ImmutableList<Wikipedia>> = channelFlow {
        send(persistentListOf())
        withContext(Dispatchers.IO) {
            httpClient.dispatcher.cancelAll()
        }

        if (query.length < 4) return@channelFlow

        if (!::wikipediaService.isInitialized) return@channelFlow
        if (query.isBlank()) return@channelFlow

        dataStore.data.map { it.wikipediaSearch.images }.collectLatest {
            val wikipedia = queryWikipedia(query, false)
            send(wikipedia?.let { persistentListOf(it) } ?: persistentListOf())
        }
    }

    private suspend fun queryWikipedia(query: String, loadImages: Boolean): Wikipedia? {

        val wikipediaService = wikipediaService
        val wikipediaUrl = retrofit.baseUrl().toString()

        val result = try {
            val imageWidth = context.resources.displayMetrics.widthPixels / 2
            wikipediaService.search(query, imageWidth)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return null
        }

        val page = result.query?.pages?.values?.toList()?.getOrNull(0) ?: return null

        val image = if (loadImages) {
            result.query.pages.values.toList().getOrNull(0)?.thumbnail?.source
        } else null

        return Wikipedia(
            label = page.title,
            id = page.pageid,
            text = page.extract,
            imageUrl = image,
            sourceUrl = page.fullurl,
            wikipediaUrl = wikipediaUrl,
            sourceName = context.getString(R.string.wikipedia_source),
        )
    }

}