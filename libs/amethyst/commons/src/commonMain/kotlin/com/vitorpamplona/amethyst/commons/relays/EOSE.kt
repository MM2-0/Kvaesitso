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
package com.vitorpamplona.amethyst.commons.relays

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

open class EOSEByKey<U : Any>(
    cacheSize: Int = 200,
) {
    var followList: LruCache<U, EOSERelayList> = LruCache(cacheSize)

    fun addOrUpdate(
        listCode: U,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val relayList = followList[listCode]
        if (relayList == null) {
            val newList = EOSERelayList()
            newList.addOrUpdate(relayUrl, time)
            followList.put(listCode, newList)
        } else {
            relayList.addOrUpdate(relayUrl, time)
        }
    }

    fun since(listCode: U) = followList[listCode]?.relayList

    /** Drops the EOSE state for [listCode] so the next filter assembly starts fresh. */
    fun clear(listCode: U) {
        followList.remove(listCode)
    }

    fun newEose(
        listCode: U,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(listCode, relayUrl, time)
}

open class EOSEAccountKey<U : Any>(
    cacheSize: Int = 20,
) {
    var users: LruCache<User, EOSEByKey<U>> = LruCache<User, EOSEByKey<U>>(cacheSize)

    fun addOrUpdate(
        user: User,
        listCode: U,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val followList = users[user]
        if (followList == null) {
            val newList = EOSEByKey<U>()
            users.put(user, newList)
            newList.addOrUpdate(listCode, relayUrl, time)
        } else {
            followList.addOrUpdate(listCode, relayUrl, time)
        }
    }

    fun removeDataFor(user: User) {
        users.remove(user)
    }

    /**
     * Drops the EOSE state for the (user, list) pair so the next assembly will ask the relay
     * from scratch. Used by feeds that need to force a re-fetch — e.g. calendar / pictures
     * where [LargeSoftCache]'s WeakReference store may have evicted the previously-loaded notes
     * while the user was viewing a different list.
     */
    fun clear(
        user: User,
        listCode: U,
    ) {
        users[user]?.clear(listCode)
    }

    fun since(
        key: User,
        listCode: U,
    ) = users[key]?.followList?.get(listCode)?.relayList

    fun newEose(
        user: User,
        listCode: U,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(user, listCode, relayUrl, time)
}
