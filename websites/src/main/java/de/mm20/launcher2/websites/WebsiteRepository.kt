package de.mm20.launcher2.websites

import android.content.Context
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.Website
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class WebsiteRepository private constructor(val context: Context): BaseSearchableRepository() {

    val website = MutableLiveData<Website?>()

    private val httpClient = OkHttpClient
            .Builder()
            .connectTimeout(200, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .writeTimeout(1000, TimeUnit.MILLISECONDS)
            .build()

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
        website.value = null
        if (query.isBlank()) return
        val wiki = withContext(Dispatchers.IO) {
            Website.search(context, query, httpClient)
        }
        website.value = wiki
    }

    companion object {
        private lateinit var instance: WebsiteRepository

        fun getInstance(context: Context): WebsiteRepository {
            if(!::instance.isInitialized) instance = WebsiteRepository(context.applicationContext)
            return instance
        }
    }
}