package de.mm20.launcher2.appsearch

import android.app.appsearch.AppSearchManager
import android.app.appsearch.GlobalSearchSession
import android.app.appsearch.SearchResult
import android.app.appsearch.SearchSpec
import android.content.Context
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import de.mm20.launcher2.hiddenitems.HiddenItemsRepository
import de.mm20.launcher2.ktx.isAtLeastApiLevel
import de.mm20.launcher2.search.BaseSearchableRepository
import de.mm20.launcher2.search.data.AppSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.suspendCoroutine

class AppSearchRepository private constructor(val context: Context) : BaseSearchableRepository() {

    private var session: GlobalSearchSession? = null

    val appSearchResults = MediatorLiveData<List<AppSearchResult>?>()

    private val allAppSearchResults = MutableLiveData<List<AppSearchResult>?>(emptyList())
    private val hiddenItemKeys = HiddenItemsRepository.getInstance(context).hiddenItemsKeys

    init {
        appSearchResults.addSource(hiddenItemKeys) { keys ->
            appSearchResults.value = allAppSearchResults.value?.filter { !keys.contains(it.key) }
        }
        appSearchResults.addSource(allAppSearchResults) { c ->
            appSearchResults.value = c?.filter { hiddenItemKeys.value?.contains(it.key) != true }
        }
    }

    override suspend fun search(query: String) {
        val results = emptyList<AppSearchResult>()
        if (!isAtLeastApiLevel(31)) return
        val executor = Executors.newSingleThreadExecutor()
        if (session == null) {
            val appSearchManager = context.getSystemService(AppSearchManager::class.java)
            session = suspendCoroutine { cont ->
                appSearchManager.createGlobalSearchSession(executor) {
                    cont.resumeWith(Result.success(it.resultValue))
                }
            }
        }

        withContext(Dispatchers.IO) {
            val searchResults = session?.search(query, SearchSpec.Builder().build())
            val page = suspendCoroutine<List<SearchResult>?> { cont ->
                searchResults?.getNextPage(executor) {
                    cont.resumeWith(Result.success(it.resultValue))
                }
            }
        }
        allAppSearchResults.value = results
    }

    companion object {
        private lateinit var instance: AppSearchRepository
        fun getInstance(context: Context): AppSearchRepository {
            if (!::instance.isInitialized) instance = AppSearchRepository(context.applicationContext)
            return instance
        }
    }

}