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
package com.vitorpamplona.amethyst.commons.moderation.notifications

/**
 * The specs of the notifications this run posted, by the key the OS reports a click with (a
 * request identifier, a launch argument, a server's notification id), so a click opens what its
 * notification was about. Bounded: the oldest are forgotten first, since the OS drops a
 * notification long before hundreds more are posted. Called from the OS's own threads.
 */
class PostedNotifications<K : Any>(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val specs =
        object : LinkedHashMap<K, NotificationSpec>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, NotificationSpec>?) = size > capacity
        }

    fun put(
        key: K,
        spec: NotificationSpec,
    ) {
        synchronized(specs) { specs[key] = spec }
    }

    /** The spec posted under [key], forgotten as it is read: a notification is opened once. */
    fun take(key: K): NotificationSpec? = synchronized(specs) { specs.remove(key) }

    companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
