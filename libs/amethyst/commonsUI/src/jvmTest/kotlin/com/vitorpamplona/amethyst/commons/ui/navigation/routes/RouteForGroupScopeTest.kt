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
package com.vitorpamplona.amethyst.commons.ui.navigation.routes

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.utils.EventFactory
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A NIP-29 `h` tag only makes a note open its group chat when the note is group *content*
 * (chat, thread, comment, poll). Zapstore puts an `h` tag on every app it publishes, and
 * routing those by it opened an empty group chat instead of the app.
 */
class RouteForGroupScopeTest {
    private val account = mockk<Account>(relaxed = true)
    private val relay = "wss://relay.zapstore.dev".normalizeRelayUrl()

    /** Grace Launcher (kind 32267) as Zapstore published it, with its `h` tag. */
    private val graceLauncher =
        """{"id":"64a6c4b2f36c83841be158df382769c7cbebdcd9d42e495294cf039d8f945508","pubkey":"78ce6faa72264387284e647ba6938995735ec8c7d5c5a65737e55130f026307d","created_at":1791290975,"kind":32267,"tags":[["d","com.galaxyrio.gracelauncher"],["name","Grace Launcher"],["icon","https://cdn.zapstore.dev/fd81778b32119e7f4469a2009c1365ce3a00dfab06e2b8dbb6f13c2554cc2b0c"],["repository","https://github.com/Galaxy-rio/GraceLauncher"],["f","android-x86_64"],["f","android-arm64-v8a"],["f","android-armeabi-v7a"],["f","android-x86"],["h","acfeaea6e51420e8068fac446ca9d17d7a9ef6a5d20d93894e50fee3d4902a84"]],"content":"","sig":"cb24823cb32a8c5e3e18c74913f318b12d632c417b25a384ca34bc7efcfc25908b8ee92a1b90b0be003629279ab750993ba593f2a788b388212da2882706a04b"}"""

    @Test
    fun anAppWithAnHTagOpensTheAppNotAGroupChat() {
        val event = Event.fromJson(graceLauncher)
        LocalCache.justConsume(event, null, true)
        val note = LocalCache.getNoteIfExists("32267:${event.pubKey}:com.galaxyrio.gracelauncher")!!
        note.addRelay(relay)

        assertEquals(
            Route.SoftwareAppDetail(32267, event.pubKey, "com.galaxyrio.gracelauncher"),
            routeFor(note, account),
        )
    }

    @Test
    fun groupChatContentStillOpensItsGroup() {
        val chat: Event =
            EventFactory.create(
                id = "9".repeat(64),
                pubKey = "8".repeat(64),
                createdAt = 1,
                kind = 9,
                tags = arrayOf(arrayOf("h", "my-group")),
                content = "gm",
                sig = "7".repeat(128),
            )
        LocalCache.justConsume(chat, null, true)
        val note = LocalCache.getNoteIfExists(chat.id)!!
        note.addRelay(relay)

        assertEquals(Route.RelayGroup("my-group", relay.url), routeFor(note, account))
    }
}
