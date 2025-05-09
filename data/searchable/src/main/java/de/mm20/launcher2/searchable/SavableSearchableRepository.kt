package de.mm20.launcher2.searchable

import android.util.Log
import androidx.room.withTransaction
import de.mm20.launcher2.backup.Backupable
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.database.entities.SavedSearchableEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdateContentEntity
import de.mm20.launcher2.database.entities.SavedSearchableUpdatePinEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import de.mm20.launcher2.preferences.WeightFactor
import de.mm20.launcher2.preferences.search.RankingSettings
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.SearchableDeserializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.qualifier.named
import java.io.File

interface SavableSearchableRepository : Backupable {

    fun insert(
        searchable: SavableSearchable,
    )

    fun upsert(
        searchable: SavableSearchable,
        visibility: VisibilityLevel? = null,
        pinned: Boolean? = null,
        launchCount: Int? = null,
        weight: Double? = null,
    )

    fun update(
        searchable: SavableSearchable,
        visibility: VisibilityLevel? = null,
        pinned: Boolean? = null,
        launchCount: Int? = null,
        weight: Double? = null,
    )

    /**
     * Replace a searchable in the database.
     * The new entry will inherit the visibility, launch count, weight and pin position of the old entry,
     * but it will have a different key and searchable.
     */
    fun replace(
        key: String,
        newSearchable: SavableSearchable,
    )

    /**
     * Touch a searchable to update its weight and launch counter
     **/
    fun touch(
        searchable: SavableSearchable,
    )

    /**
     * @param minVisibility the minimum visibility of the searchables to return. A visible is
     * considered to be "lower" when it makes an item less visible.
     * @param maxVisibility the maximum visibility of the searchables to return. A visible is
     * considered to be "higher" when it makes an item more visible.
     */
    fun get(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        minPinnedLevel: PinnedLevel = PinnedLevel.NotPinned,
        maxPinnedLevel: PinnedLevel = PinnedLevel.ManuallySorted,
        minVisibility: VisibilityLevel = VisibilityLevel.Hidden,
        maxVisibility: VisibilityLevel = VisibilityLevel.Default,
        limit: Int = 9999,
    ): Flow<List<SavableSearchable>>

    fun getKeys(
        includeTypes: List<String>? = null,
        excludeTypes: List<String>? = null,
        minPinnedLevel: PinnedLevel = PinnedLevel.NotPinned,
        maxPinnedLevel: PinnedLevel = PinnedLevel.ManuallySorted,
        minVisibility: VisibilityLevel = VisibilityLevel.Hidden,
        maxVisibility: VisibilityLevel = VisibilityLevel.Default,
        limit: Int = 9999,
    ): Flow<List<String>>


    fun isPinned(searchable: SavableSearchable): Flow<Boolean>
    fun getVisibility(searchable: SavableSearchable): Flow<VisibilityLevel>
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

    fun getWeights(keys: List<String>): Flow<Map<String, Double>>

    /**
     * Remove this item from the Searchable database
     */
    fun delete(searchable: SavableSearchable)

    /**
     * Get items with the given keys from the favorites database.
     * Items that don't exist in the database will not be returned.
     */
    fun getByKeys(keys: List<String>): Flow<List<SavableSearchable>>

    /**
     * Remove database entries that are invalid. This includes
     * - entries that cannot be deserialized anymore
     * - entries that are inconsistent (the key column is not equal to the key of the searchable)
     */
    suspend fun cleanupDatabase(): Int
}

internal class SavableSearchableRepositoryImpl(
    private val database: AppDatabase,
    private val settings: RankingSettings,
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
                    visibility = VisibilityLevel.Default.value,
                    launchCount = 0,
                    weight = 0.0,
                    pinPosition = 0,
                )
            )
        }
    }


    override fun upsert(
        searchable: SavableSearchable,
        visibility: VisibilityLevel?,
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
                    visibility = visibility?.value ?: entity?.visibility ?: 0,
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
        visibility: VisibilityLevel?,
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
                    visibility = visibility?.value ?: entity?.visibility ?: 0,
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
                when (settings.weightFactor.firstOrNull()) {
                    WeightFactor.Low -> WEIGHT_FACTOR_LOW
                    WeightFactor.High -> WEIGHT_FACTOR_HIGH
                    else -> WEIGHT_FACTOR_MEDIUM
                }
            val item =
                SavedSearchable(searchable.key, searchable, 0, 0, VisibilityLevel.Default, 0.0)
            item.toDatabaseEntity()?.let {
                database.searchableDao()
                    .touch(it, weightFactor)
            }
        }
    }

    override fun get(
        includeTypes: List<String>?,
        excludeTypes: List<String>?,
        minPinnedLevel: PinnedLevel,
        maxPinnedLevel: PinnedLevel,
        minVisibility: VisibilityLevel,
        maxVisibility: VisibilityLevel,
        limit: Int
    ): Flow<List<SavableSearchable>> {
        val dao = database.searchableDao()
        val entities = when {
            includeTypes == null && excludeTypes == null -> dao.get(
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            includeTypes == null -> dao.getExcludeTypes(
                excludeTypes = excludeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            excludeTypes == null -> dao.getIncludeTypes(
                includeTypes = includeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
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
        minPinnedLevel: PinnedLevel,
        maxPinnedLevel: PinnedLevel,
        minVisibility: VisibilityLevel,
        maxVisibility: VisibilityLevel,
        limit: Int
    ): Flow<List<String>> {
        val dao = database.searchableDao()
        return when {
            includeTypes == null && excludeTypes == null -> dao.getKeys(
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            includeTypes == null -> dao.getKeysExcludeTypes(
                excludeTypes = excludeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            excludeTypes == null -> dao.getKeysIncludeTypes(
                includeTypes = includeTypes,
                manuallySorted = PinnedLevel.ManuallySorted in minPinnedLevel..maxPinnedLevel,
                automaticallySorted = PinnedLevel.AutomaticallySorted in minPinnedLevel..maxPinnedLevel,
                frequentlyUsed = PinnedLevel.FrequentlyUsed in minPinnedLevel..maxPinnedLevel,
                unused = PinnedLevel.NotPinned in minPinnedLevel..maxPinnedLevel,
                minVisibility = minVisibility.value,
                maxVisibility = maxVisibility.value,
                limit = limit
            )

            else -> throw IllegalArgumentException("Cannot specify both includeTypes and excludeTypes")
        }
    }

    override fun isPinned(searchable: SavableSearchable): Flow<Boolean> {
        return database.searchableDao().isPinned(searchable.key)
    }

    override fun getVisibility(searchable: SavableSearchable): Flow<VisibilityLevel> {
        return database.searchableDao().getVisibility(searchable.key).map {
            VisibilityLevel.fromInt(it)
        }
    }

    override fun delete(searchable: SavableSearchable) {
        scope.launch {
            database.searchableDao().delete(searchable.key)
        }
    }

    override fun replace(key: String, newSearchable: SavableSearchable) {
        scope.launch {
            database.searchableDao().replace(
                key,
                SavedSearchableUpdateContentEntity(
                    key = newSearchable.key,
                    type = newSearchable.domain,
                    serializedSearchable = newSearchable.serialize() ?: return@launch
                )
            )
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

    override fun getWeights(keys: List<String>): Flow<Map<String, Double>> {
        if (keys.size > 999) return flowOf(emptyMap())
        return database.searchableDao().getWeights(keys)
    }

    private suspend fun fromDatabaseEntity(entity: SavedSearchableEntity): SavedSearchable {
        val deserializer: SearchableDeserializer? = try {
            get(named(entity.type))
        } catch (e: NoDefinitionFoundException) {
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
            visibility = VisibilityLevel.fromInt(entity.visibility),
            weight = entity.weight
        )
    }

    private fun removeInvalidItem(key: String) {
        scope.launch {
            database.searchableDao().delete(key)
        }
    }

    override fun getByKeys(keys: List<String>): Flow<List<SavableSearchable>> {
        val dao = database.searchableDao()
        if (keys.size > 999) {
            return combine(keys.chunked(999).map {
                dao.getByKeys(it)
                    .map {
                        it.mapNotNull { fromDatabaseEntity(it).searchable }
                    }
            }) { results ->
                results.flatMap { it }
            }
        }
        return dao.getByKeys(keys)
            .map { it.mapNotNull { fromDatabaseEntity(it).searchable } }
    }

    override suspend fun backup(toDir: File) = withContext(Dispatchers.IO) {
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
                        "visibility" to fav.visibility,
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

    override suspend fun restore(fromDir: File) = withContext(Dispatchers.IO) {
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
                        visibility = json.optInt("visibility", 0),
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