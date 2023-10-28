package de.mm20.launcher2.searchable

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdatePinEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.preferences.LauncherDataStore
import de.mm20.launcher2.preferences.Settings.SearchResultOrderingSettings.WeightFactor
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.error.InstanceCreationException
import org.koin.core.error.NoBeanDefFoundException
import org.koin.core.qualifier.named
import java.io.File

interface SavableSearchableRepository {

    fun insert(
        searchable: SavableSearchable,
    )

    fun upsert(
        searchable: SavableSearchable,
        hidden: Boolean? = null,
        pinned: Boolean? = null,
        launchCount: Int? = null,
        weight: Double? = null,
    )

    fun update(
        searchable: SavableSearchable,
        hidden: Boolean? = null,
        pinned: Boolean? = null,
        launchCount: Int? = null,
        weight: Double? = null,
    )

    /**
     * Touch a searchable to update its weight and launch counter
     **/
    fun touch(
        searchable: SavableSearchable,
    )

    fun get(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        hidden: Boolean = false,
        limit: Int = 100,
    ): Flow<List<SavableSearchable>>

    fun getKeys(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        manuallySorted: Boolean = false,
        automaticallySorted: Boolean = false,
        frequentlyUsed: Boolean = false,
        hidden: Boolean = false,
        limit: Int = 100,
    ): Flow<List<String>>


    fun isPinned(searchable: SavableSearchable): Flow<Boolean>
    fun isHidden(searchable: SavableSearchable): Flow<Boolean>
    fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>,
    )

    /**
     * Returns the given keys sorted by relevance.
     * The first item in the list is the most relevant.
     * Unknown keys will not be included in the result.
     */
    fun sortByRelevance(keys: List<String>): Flow<List<String>>

    fun sortByWeight(keys: List<String>): Flow<List<String>>

    /**
     * Remove this item from the Searchable database
     */
    fun delete(searchable: SavableSearchable)

    /**
     * Get items with the given keys from the favorites database.
     * Items that don't exist in the database will not be returned.
     */
    suspend fun getByKeys(keys: List<String>): List<SavableSearchable>

    suspend fun export(toDir: File)
    suspend fun import(fromDir: File)

    /**
     * Remove database entries that are invalid. This includes
     * - entries that cannot be deserialized anymore
     * - entries that are inconsistent (the key column is not equal to the key of the searchable)
     */
    suspend fun cleanupDatabase(): Int
}

internal class SavableSearchableRepositoryImpl(
    private val context: Context,
    private val database: AppDatabase,
    private val dataStore: LauncherDataStore
) : SavableSearchableRepository, KoinComponent {

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    override fun insert(searchable: SavableSearchable) {
        val dao = database.searchableDao()
        scope.launch {
            dao.insert(
                SavedSearchableEntity(
                    key = searchable.key,
                    type = searchable.domain,
                    serializedSearchable = searchable.serialize() ?: return@launch,
                    hidden = false,
                    launchCount = 0,
                    weight = 0.0,
                    pinPosition = 0,
                )
            )
        }
    }


    override fun upsert(
        searchable: SavableSearchable,
        hidden: Boolean?,
        pinned: Boolean?,
        launchCount: Int?,
        weight: Double?
    ) {
        val dao = database.searchableDao()
        scope.launch {
            val entity = dao.getByKey(searchable.key).firstOrNull()
            dao.upsert(
                SavedSearchableEntity(
                    key = searchable.key,
                    type = searchable.domain,
                    hidden = hidden ?: entity?.hidden ?: false,
                    pinPosition = pinned?.let { if (it) 1 else 0 } ?: entity?.pinPosition ?: 0,
                    launchCount = launchCount ?: entity?.launchCount ?: 0,
                    weight = weight ?: entity?.weight ?: 0.0,
                    serializedSearchable = searchable.serialize() ?: return@launch,
                )
            )
        }
    }

    override fun update(
        searchable: SavableSearchable,
        hidden: Boolean?,
        pinned: Boolean?,
        launchCount: Int?,
        weight: Double?
    ) {
        val dao = database.searchableDao()
        scope.launch {
            val entity = dao.getByKey(searchable.key).firstOrNull()
            dao.upsert(
                SavedSearchableEntity(
                    key = searchable.key,
                    type = searchable.domain,
                    hidden = hidden ?: entity?.hidden ?: false,
                    pinPosition = pinned?.let { if (it) 1 else 0 } ?: entity?.pinPosition ?: 0,
                    launchCount = launchCount ?: entity?.launchCount ?: 0,
                    weight = weight ?: entity?.weight ?: 0.0,
                    serializedSearchable = searchable.serialize() ?: return@launch,
                )
            )
        }
    }

    override fun touch(searchable: SavableSearchable) {
        scope.launch {
            val weightFactor =
                when (dataStore.data.map { it.resultOrdering.weightFactor }.firstOrNull()) {
                    WeightFactor.Low -> WEIGHT_FACTOR_LOW
                    WeightFactor.High -> WEIGHT_FACTOR_HIGH
                    else -> WEIGHT_FACTOR_MEDIUM
                }
            val item = SavedSearchable(searchable.key, searchable, 0, 0, false, 0.0)
            item.toDatabaseEntity()?.let {
                database.searchableDao()
                    .touch(it, weightFactor)
            }
        }
    }

    override fun get(
        includeTypes: List<String>?,
        excludeTypes: List<String>?,
        manuallySorted: Boolean,
        automaticallySorted: Boolean,
        frequentlyUsed: Boolean,
        hidden: Boolean,
        limit: Int
    ): Flow<List<SavableSearchable>> {
        val dao = database.searchableDao()
        val entities = when {
            includeTypes == null && excludeTypes == null -> dao.get(
                manuallySorted = manuallySorted,
                automaticallySorted = automaticallySorted,
                frequentlyUsed = frequentlyUsed,
                hidden = hidden,
                limit = limit
            )

            includeTypes == null -> dao.getExcludeTypes(
                excludeTypes = excludeTypes,
                manuallySorted = manuallySorted,
                automaticallySorted = automaticallySorted,
                frequentlyUsed = frequentlyUsed,
                hidden = hidden,
                limit = limit
            )

            excludeTypes == null -> dao.getIncludeTypes(
                includeTypes = includeTypes,
                manuallySorted = manuallySorted,
                automaticallySorted = automaticallySorted,
                frequentlyUsed = frequentlyUsed,
                hidden = hidden,
                limit = limit
            )

            else -> throw IllegalArgumentException("Cannot specify both includeTypes and excludeTypes")
        }

        return entities.map {
            it.mapNotNull { fromDatabaseEntity(it).searchable }
        }
    }

    override fun getKeys(
        includeTypes: List<String>?,
        excludeTypes: List<String>?,
        manuallySorted: Boolean,
        automaticallySorted: Boolean,
        frequentlyUsed: Boolean,
        hidden: Boolean,
        limit: Int
    ): Flow<List<String>> {
        val dao = database.searchableDao()
        return when {
            includeTypes == null && excludeTypes == null -> dao.getKeys(
                manuallySorted = manuallySorted,
                automaticallySorted = automaticallySorted,
                frequentlyUsed = frequentlyUsed,
                hidden = hidden,
                limit = limit
            )

            includeTypes == null -> dao.getKeysExcludeTypes(
                excludeTypes = excludeTypes,
                manuallySorted = manuallySorted,
                automaticallySorted = automaticallySorted,
                frequentlyUsed = frequentlyUsed,
                hidden = hidden,
                limit = limit
            )

            excludeTypes == null -> dao.getKeysIncludeTypes(
                includeTypes = includeTypes,
                manuallySorted = manuallySorted,
                automaticallySorted = automaticallySorted,
                frequentlyUsed = frequentlyUsed,
                hidden = hidden,
                limit = limit
            )

            else -> throw IllegalArgumentException("Cannot specify both includeTypes and excludeTypes")
        }
    }

    override fun isPinned(searchable: SavableSearchable): Flow<Boolean> {
        return database.searchableDao().isPinned(searchable.key)
    }

    override fun isHidden(searchable: SavableSearchable): Flow<Boolean> {
        return database.searchableDao().isHidden(searchable.key)
    }

    override fun delete(searchable: SavableSearchable) {
        scope.launch {
            database.searchableDao().delete(searchable.key)
        }
    }

    override fun updateFavorites(
        manuallySorted: List<SavableSearchable>,
        automaticallySorted: List<SavableSearchable>
    ) {
        val dao = database.searchableDao()
        scope.launch {
            database.withTransaction {
                dao.unpinAll()
                dao.upsert(
                    manuallySorted.mapIndexedNotNull { index, savableSearchable ->
                        SavedSearchableUpdatePinEntity(
                            key = savableSearchable.key,
                            type = savableSearchable.domain,
                            pinPosition = manuallySorted.size - index + 1,
                            serializedSearchable = savableSearchable.serialize()
                                ?: return@mapIndexedNotNull null,
                        )
                    }
                )
                dao.upsert(
                    automaticallySorted.mapNotNull { savableSearchable ->
                        SavedSearchableUpdatePinEntity(
                            key = savableSearchable.key,
                            type = savableSearchable.domain,
                            pinPosition = 1,
                            serializedSearchable = savableSearchable.serialize()
                                ?: return@mapNotNull null,
                        )
                    }
                )
            }
        }
    }

    override fun sortByRelevance(keys: List<String>): Flow<List<String>> {
        if (keys.size > 999) return flowOf(emptyList())
        return database.searchableDao().sortByRelevance(keys)
    }

    override fun sortByWeight(keys: List<String>): Flow<List<String>> {
        if (keys.size > 999) return flowOf(emptyList())
        return database.searchableDao().sortByWeight(keys)
    }

    private suspend fun fromDatabaseEntity(entity: SavedSearchableEntity): SavedSearchable {
        val deserializer: SearchableDeserializer? = try {
            get(named(entity.type))
        } catch (e: NoBeanDefFoundException) {
            CrashReporter.logException(e)
            null
        } catch (e: InstanceCreationException) {
            CrashReporter.logException(e)
            null
        }
        val searchable = deserializer?.deserialize(entity.serializedSearchable)
        if (searchable == null) removeInvalidItem(entity.key)
        return SavedSearchable(
            key = entity.key,
            searchable = searchable,
            launchCount = entity.launchCount,
            pinPosition = entity.pinPosition,
            hidden = entity.hidden,
            weight = entity.weight
        )
    }

    private fun removeInvalidItem(key: String) {
        scope.launch {
            database.searchableDao().delete(key)
        }
    }

    override suspend fun getByKeys(keys: List<String>): List<SavableSearchable> {
        val dao = database.searchableDao()
        if (keys.size > 999) {
            return keys.chunked(999).flatMap {
                dao.getByKeys(it)
                    .mapNotNull { fromDatabaseEntity(it).searchable }
            }
        }
        return dao.getByKeys(keys)
            .mapNotNull { fromDatabaseEntity(it).searchable }
    }

    override suspend fun export(toDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        var page = 0
        do {
            val favorites = dao.exportFavorites(limit = 100, offset = page * 100)
            val jsonArray = JSONArray()
            for (fav in favorites) {
                jsonArray.put(
                    jsonObjectOf(
                        "key" to fav.key,
                        "type" to fav.type,
                        "hidden" to fav.hidden,
                        "launchCount" to fav.launchCount,
                        "pinPosition" to fav.pinPosition,
                        "searchable" to fav.serializedSearchable,
                        "weight" to fav.weight,
                    )
                )
            }

            val file = File(toDir, "favorites.${page.toString().padStart(4, '0')}")
            file.bufferedWriter().use {
                it.write(jsonArray.toString())
            }
            page++
        } while (favorites.size == 100)
    }

    override suspend fun import(fromDir: File) = withContext(Dispatchers.IO) {
        val dao = database.backupDao()
        dao.wipeFavorites()

        val files =
            fromDir.listFiles { _, name -> name.startsWith("favorites.") } ?: return@withContext

        for (file in files) {
            val favorites = mutableListOf<SavedSearchableEntity>()
            try {
                val jsonArray = JSONArray(file.inputStream().reader().readText())

                for (i in 0 until jsonArray.length()) {
                    val json = jsonArray.getJSONObject(i)
                    val entity = SavedSearchableEntity(
                        key = json.getString("key"),
                        type = json.optString("type").takeIf { it.isNotEmpty() } ?: continue,
                        serializedSearchable = json.getString("searchable"),
                        launchCount = json.getInt("launchCount"),
                        hidden = json.getBoolean("hidden"),
                        pinPosition = json.getInt("pinPosition"),
                        weight = json.optDouble("weight").takeIf { !it.isNaN() } ?: 0.0
                    )
                    favorites.add(entity)
                }

                dao.importFavorites(favorites)

            } catch (e: JSONException) {
                CrashReporter.logException(e)
            }
        }
    }

    override suspend fun cleanupDatabase(): Int {
        var removed = 0
        val job = scope.launch {
            val dao = database.backupDao()
            var page = 0
            do {
                val favorites = dao.exportFavorites(limit = 100, offset = page * 100)
                for (fav in favorites) {
                    val item = fromDatabaseEntity(fav)
                    if (item.searchable == null || item.searchable.key != item.key) {
                        removeInvalidItem(item.key)
                        removed++
                        Log.i(
                            "MM20",
                            "SearchableDatabase cleanup: removed invalid item ${item.key}"
                        )
                    }
                }
                page++
            } while (favorites.size == 100)
        }
        job.join()
        return removed
    }

    companion object {
        private const val WEIGHT_FACTOR_LOW = 0.01
        private const val WEIGHT_FACTOR_MEDIUM = 0.03
        private const val WEIGHT_FACTOR_HIGH = 0.1
    }
}