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
package com.vitorpamplona.amethyst.commons.geocaches.hunt

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nipCCGeocaching.curation.GeocacheCurationListEvent
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.ListTheme
import com.vitorpamplona.quartz.nipCCGeocaching.curation.tags.MapStyle
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The hunt composer's state.
 *
 * The cache list is a [mutableStateListOf] of addresses rather than a set, because NIP-CC makes
 * the order of a curation list's `a` tags meaningful: a hunt is a route someone designed, and
 * shuffling it into a set would quietly destroy the one thing that makes it a hunt rather than a
 * bookmark folder.
 */
class NewGeocacheHuntViewModel : ViewModel() {
    private lateinit var account: Account

    val title = mutableStateOf("")
    val description = mutableStateOf("")
    val bannerUrl = mutableStateOf("")
    val caches = mutableStateListOf<Address>()
    val theme = mutableStateOf<ListTheme?>(null)
    val mapStyle = mutableStateOf<MapStyle?>(null)
    val isPublishing = mutableStateOf(false)

    private var editAddress: Address? = null

    val isEditing: Boolean
        get() = editAddress != null

    fun init(account: Account) {
        if (!::account.isInitialized) this.account = account
    }

    fun loadForEdit(
        kind: Int,
        pubKeyHex: String,
        dTag: String,
    ) {
        if (editAddress != null) return
        val address = Address(kind, pubKeyHex, dTag)
        val existing = LocalCache.addressables.get(address)?.event as? GeocacheCurationListEvent ?: return

        editAddress = address
        title.value = existing.title().orEmpty()
        description.value = existing.description().orEmpty()
        bannerUrl.value = existing.image().orEmpty()
        caches.clear()
        caches.addAll(existing.geocaches())
        theme.value = existing.theme()
        mapStyle.value = existing.mapStyle()
    }

    fun addCache(address: Address) {
        if (!caches.contains(address)) caches.add(address)
    }

    fun removeCache(address: Address) {
        caches.remove(address)
    }

    fun move(
        from: Int,
        to: Int,
    ) {
        if (from !in caches.indices || to !in caches.indices) return
        caches.add(to, caches.removeAt(from))
    }

    fun isValid() = title.value.isNotBlank() && caches.isNotEmpty()

    @OptIn(ExperimentalUuidApi::class)
    suspend fun publish(): Boolean {
        if (!isValid()) return false

        isPublishing.value = true
        try {
            val template =
                GeocacheCurationListEvent.build(
                    title = title.value.trim(),
                    geocaches = caches.toList(),
                    description = description.value.trim().ifBlank { null },
                    image = bannerUrl.value.trim().ifBlank { null },
                    theme = theme.value,
                    mapStyle = mapStyle.value,
                    dTag = editAddress?.dTag ?: Uuid.random().toString(),
                )

            return runCatchingCancellable { account.signAndComputeBroadcast(template) }.isSuccess
        } finally {
            isPublishing.value = false
        }
    }
}
