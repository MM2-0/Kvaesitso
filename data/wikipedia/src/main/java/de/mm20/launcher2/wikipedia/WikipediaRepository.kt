package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.search.WikipediaSearchSettings
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


internal class WikipediaRepository(
    private val context: Context,
    private val settings: WikipediaSearchSettings,
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
            settings.customUrl
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


    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<Wikipedia>> {
        if (query.length < 4 || !allowNetwork) return flowOf(persistentListOf())

        return settings.enabled.transformLatest {
            emit(persistentListOf())
            withContext(Dispatchers.IO) {
                httpClient.dispatcher.cancelAll()
            }

            if (!it || !::wikipediaService.isInitialized) return@transformLatest
            if (query.isBlank()) return@transformLatest

            val results = queryWikipedia(query)
            if (results != null) {
                emit(persistentListOf(results))
            }
        }

    }

    private suspend fun queryWikipedia(query: String): Wikipedia? {

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

        val image = result.query.pages.values.toList().getOrNull(0)?.thumbnail?.source

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