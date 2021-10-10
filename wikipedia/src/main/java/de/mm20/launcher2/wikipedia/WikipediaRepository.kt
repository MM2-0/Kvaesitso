package de.mm20.launcher2.wikipedia

import android.content.Context
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.LauncherPreferences
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.Wikipedia
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WikipediaRepository(val context: Context) : BaseSearchableRepository() {

    val wikipedia = MutableLiveData<Wikipedia?>()

    private val httpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(200, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .writeTimeout(1000, TimeUnit.MILLISECONDS)
            .build()
    }

    val retrofit by lazy {
        Retrofit.Builder()
            .client(httpClient)
            .baseUrl(context.getString(R.string.wikipedia_url))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val wikipediaService by lazy {
        retrofit.create(WikipediaApi::class.java)
    }

    override fun onCancel() {
        super.onCancel()

        httpClient.dispatcher.run {
            runningCalls().forEach {
                it.cancel()
            }
            queuedCalls().forEach {
                it.cancel()
            }
        }
    }

    override suspend fun search(query: String) {
        wikipedia.value = null
        if (query.isBlank()) return

        val result = try {
            wikipediaService.search(query)
        } catch (e: Exception) {
            CrashReporter.logException(e)
            return
        }

        val page = result.query?.pages?.values?.toList()?.getOrNull(0) ?: return

        val image = if (LauncherPreferences.instance.searchWikipediaPictures) {
            val width = context.resources.displayMetrics.widthPixels / 2
            val imageResult = try {
                wikipediaService.getPageImage(page.pageid, width)
            } catch (e: Exception) {
                CrashReporter.logException(e)
                return
            }
            imageResult.query?.pages?.values?.toList()?.getOrNull(0)?.thumbnail?.source
        } else null

        val wiki = Wikipedia(
            label = page.title,
            id = page.pageid,
            text = page.extract,
            image = image
        )

        wikipedia.value = wiki
    }
}