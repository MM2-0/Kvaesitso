package de.mm20.launcher2.wikipedia

import android.content.Context
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.search.WikipediaSearchSettings
import de.mm20.launcher2.search.Article
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.flowOf


internal class WikipediaRepository(
    private val context: Context,
    private val settings: WikipediaSearchSettings,
) : SearchableRepository<Article> {

    private val wikipediaApi = WikipediaApi(context, null)

    override fun search(query: String): Flow<ImmutableList<Wikipedia>> {
        if (query.length < 4) return flowOf(persistentListOf())

        return combineTransform(settings.enabled, settings.customUrl) { enabled, url ->
            emit(persistentListOf())

            if (query.isBlank()) return@combineTransform

            val baseUrl =
                url.takeIf { !it.isNullOrBlank() } ?: context.getString(R.string.wikipedia_url)

            val results = queryWikipedia(baseUrl, query)
            if (results != null) {
                emit(persistentListOf(results))
            }
        }

    }

    private suspend fun queryWikipedia(baseUrl: String, query: String): Wikipedia? {

        val result = try {
            val imageWidth = context.resources.displayMetrics.widthPixels / 2
            wikipediaApi.search(baseUrl, query, imageWidth)
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
            wikipediaUrl = baseUrl,
            sourceName = context.getString(R.string.wikipedia_source),
        )
    }

}