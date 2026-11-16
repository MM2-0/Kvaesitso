/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.favorites

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.util.ConcurrentSet
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile

/**
 * The user's device-local list of [FavoriteApp]s — the single source of truth shared by the bottom
 * bar, the Favorite Apps grid, and the browser launcher. Ordered (the user can reorder); de-duplicated
 * by [FavoriteApp.id].
 *
 * An in-memory [StateFlow] is authoritative for the session so Compose can observe it synchronously,
 * with write-through persistence to [store] on [scope]. The list is stored as a single JSON array
 * under one key (small, bounded, hand-curated data — no need for one key per entry).
 *
 * Takes its [DataStore] rather than reaching for one, for the same reason
 * `DataStoreSearchHistoryStorage` does: DataStore refuses a second live instance on a path that
 * already has one, so the caller's store holder stays the single registry. That is also what keeps
 * this class off any one front end — the Android app builds it in `AppModules` from
 * `appStores.getDataStore(FILE_NAME)`, and nothing here knows about `Context` or the app singleton.
 *
 * One instance per process. On Android the launcher/UI in the **main** process own it; the keyless
 * `:napplet` sandbox never builds one.
 */
class FavoriteAppsRegistry(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    private val _favorites = MutableStateFlow<List<FavoriteApp>>(emptyList())
    val favorites: StateFlow<List<FavoriteApp>> = _favorites.asStateFlow()

    private val manifestCache = MutableStateFlow<Map<String, String>>(emptyMap())

    // Gates persistence until init() has been called: writing before hydration has been scheduled
    // would flush a partial list over the stored one. Set synchronously in init(), so the merge it
    // launches still persists whatever the session added in the meantime.
    @Volatile private var started = false

    // Hydration runs async on [scope], so the user can add/remove before the disk list merges in.
    // [removedBeforeHydration] tombstones any id removed in that window, so the merge can't
    // resurrect a just-deleted favorite from disk.
    @Volatile private var hydrated = false
    private val removedBeforeHydration = ConcurrentSet<String>()

    /** Hydrates the on-disk list into [favorites]. Idempotent. */
    fun init() {
        if (started) return
        started = true
        scope.launch {
            val prefs = store.data.first()
            val loaded = prefs[KEY]?.let { decode(it) } ?: emptyList()
            // Don't clobber adds made in this session before hydration finished, and don't resurrect
            // anything the user removed in that same window.
            update { current -> (loaded.filterNot { removedBeforeHydration.contains(it.id) } + current).distinctBy { it.id } }

            // Same race rules for the manifest cache: a cacheManifest() in this session wins over the
            // disk copy, and a manifest whose favorite was removed pre-hydration must not come back.
            val loadedManifests = prefs[MANIFESTS_KEY]?.let { decodeManifests(it) } ?: emptyMap()
            updateManifests { current -> loadedManifests.filterKeys { !removedBeforeHydration.contains("nostr:$it") } + current }

            hydrated = true
            removedBeforeHydration.clear()
        }
    }

    fun isFavorite(id: String): Boolean = _favorites.value.any { it.id == id }

    /** Adds [app] to the end if not already present (by [FavoriteApp.id]). */
    fun add(app: FavoriteApp) = update { current -> if (current.any { it.id == app.id }) current else current + app }

    fun remove(id: String) {
        if (!hydrated) removedBeforeHydration.add(id)
        update { current -> current.filterNot { it.id == id } }
        // Drop the cached manifest too — favorite ids for nsites/napplets are "nostr:<coordinate>".
        if (id.startsWith("nostr:")) updateManifests { it - id.removePrefix("nostr:") }
    }

    /** The cached manifest event JSON for a favorited nsite/napplet [coordinate], or null if none. */
    fun cachedManifest(coordinate: String): String? = manifestCache.value[coordinate]

    /**
     * Caches the raw manifest event [eventJson] for a favorited nsite/napplet [coordinate] so the next
     * launch can resolve it instantly / offline. Write-through; no-ops when the JSON is unchanged.
     */
    fun cacheManifest(
        coordinate: String,
        eventJson: String,
    ) = updateManifests { if (it[coordinate] == eventJson) it else it + (coordinate to eventJson) }

    /** Replaces the whole list, e.g. after a drag-reorder. */
    fun setOrder(newOrder: List<FavoriteApp>) = update { newOrder }

    private inline fun update(transform: (List<FavoriteApp>) -> List<FavoriteApp>) {
        val next = transform(_favorites.value)
        if (next == _favorites.value) return
        _favorites.value = next
        persist(KEY, encode(next))
    }

    private inline fun updateManifests(transform: (Map<String, String>) -> Map<String, String>) {
        val next = transform(manifestCache.value)
        if (next == manifestCache.value) return
        manifestCache.value = next
        persist(MANIFESTS_KEY, encodeManifests(next))
    }

    private fun persist(
        key: Preferences.Key<String>,
        json: String,
    ) {
        if (!started) return
        scope.launch {
            store.edit { it[key] = json }
        }
    }

    // --- Persistence DTO ------------------------------------------------------------------------
    // A flat, type-tagged record so we serialize one concrete shape instead of relying on
    // polymorphic (sealed) (de)serialization. Mapping to/from the sealed model lives here.

    @Serializable
    private data class Entry(
        val type: String,
        val ref: String,
        val label: String,
        val addedAt: Long,
        val iconUrl: String? = null,
    )

    private fun encode(list: List<FavoriteApp>): String =
        JsonMapper.toJson(
            list.map {
                when (it) {
                    is FavoriteApp.NostrApp -> Entry(TYPE_NOSTR, it.coordinate, it.label, it.addedAt, it.iconUrl)
                    is FavoriteApp.WebApp -> Entry(TYPE_URL, it.url, it.label, it.addedAt, it.iconUrl)
                }
            },
        )

    private fun decode(json: String): List<FavoriteApp> =
        try {
            JsonMapper.fromJson<List<Entry>>(json).mapNotNull { entry ->
                when (entry.type) {
                    TYPE_NOSTR -> FavoriteApp.NostrApp(entry.ref, entry.label, entry.addedAt, entry.iconUrl)
                    TYPE_URL -> FavoriteApp.WebApp(entry.ref, entry.label, entry.addedAt, entry.iconUrl)
                    else -> null
                }
            }
        } catch (e: Exception) {
            Log.w("FavoriteAppsRegistry", "Failed to decode favorites", e)
            emptyList()
        }

    // --- Manifest cache persistence -------------------------------------------------------------
    // Stored as a flat list of (coordinate, json) records under one key — same single-key, hand-curated
    // shape as the favorites list, so we never serialize a raw polymorphic map.

    @Serializable
    private data class ManifestEntry(
        val coordinate: String,
        val json: String,
    )

    private fun encodeManifests(manifests: Map<String, String>): String = JsonMapper.toJson(manifests.map { ManifestEntry(it.key, it.value) })

    private fun decodeManifests(json: String): Map<String, String> =
        try {
            JsonMapper.fromJson<List<ManifestEntry>>(json).associate { it.coordinate to it.json }
        } catch (e: Exception) {
            Log.w("FavoriteAppsRegistry", "Failed to decode favorite manifests", e)
            emptyMap()
        }

    companion object {
        /** Same file the `Context.preferencesDataStore("favorite_apps")` delegate resolved to. */
        const val FILE_NAME = "favorite_apps"

        private val KEY = stringPreferencesKey("favorites")

        // Raw manifest event JSON for each favorited [FavoriteApp.NostrApp], keyed by its addressable
        // coordinate. Cached so a pinned nsite/napplet resolves instantly on the next cold start — and
        // offline — instead of waiting on a relay round-trip the way a [FavoriteApp.WebApp]'s URL never
        // has to. The relay subscription that warms these favorites keeps the cache fresh.
        private val MANIFESTS_KEY = stringPreferencesKey("manifests")

        private const val TYPE_NOSTR = "nostr"
        private const val TYPE_URL = "url"
    }
}
