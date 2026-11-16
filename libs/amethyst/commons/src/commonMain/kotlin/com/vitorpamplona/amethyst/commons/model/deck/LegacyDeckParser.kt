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
package com.vitorpamplona.amethyst.commons.model.deck

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the legacy desktop app's deck (its `workspaces` and older `deck_columns` JSON) into a
 * [DeckLayout]. Every legacy column type maps to a shared [Route] except the ones without a shared
 * screen (the global and custom-feed columns, the local highlights and the editor), which are
 * listed in [DeckLayout.importNotice].
 */
object LegacyDeckParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** A legacy column as a shared route, or null when there is no shared screen for it. */
    fun toRoute(
        typeKey: String,
        param: String?,
        myPubKey: String,
    ): Route? =
        when (typeKey) {
            "home" -> Route.Home
            "notifications" -> Route.Notification()
            "messages" -> Route.Message
            "search" -> Route.Search()
            "reads" -> Route.Articles
            "bookmarks" -> Route.Bookmarks
            "my_profile" -> Route.Profile(myPubKey)
            "chess" -> Route.Chess
            "settings" -> Route.AllSettings
            "notification_settings" -> Route.NotificationSettings
            "relays" -> Route.EditRelays
            "wallet" -> Route.Wallet
            "drafts" -> Route.Drafts
            "discover" -> Route.Discover()
            "follow_packs" -> Route.FollowPacks
            "profile" -> param?.let { Route.Profile(it) }
            "thread", "article" -> param?.let { Route.Note(it) }
            "hashtag" -> param?.let { Route.Hashtag(it) }
            else -> null
        }

    /** What the user is told about a column that could not come over. */
    private fun describe(
        typeKey: String,
        param: String?,
        feedName: (String) -> String?,
    ): String =
        when (typeKey) {
            "global" -> "Global"
            "custom_feed" -> param?.let(feedName) ?: "Custom feed"
            "highlights" -> "My highlights"
            "editor" -> "Editor"
            else -> typeKey
        }

    /**
     * The deck from the legacy `workspaces` JSON, else from its older `deck_columns` list; null
     * when neither holds any column.
     */
    fun parse(
        workspacesJson: String,
        deckColumnsJson: String,
        myPubKey: String,
        feedName: (String) -> String? = { null },
    ): DeckLayout? {
        val dropped = linkedSetOf<String>()

        fun column(
            typeKey: String?,
            param: String?,
            width: Float?,
        ): DeckColumn? {
            val key = typeKey ?: return null
            val route = toRoute(key, param, myPubKey)
            if (route == null) {
                dropped += describe(key, param, feedName)
                return null
            }
            val clamped = (width ?: DeckLayout.DEFAULT_COLUMN_WIDTH).coerceIn(DeckLayout.MIN_COLUMN_WIDTH, DeckLayout.MAX_COLUMN_WIDTH)
            return DeckColumn(DeckLayout.newId(), route, clamped)
        }

        val workspaces = runCatching { json.parseToJsonElement(workspacesJson).jsonObject }.getOrNull()
        if (workspaces != null) {
            val list =
                (workspaces["workspaces"] as? JsonArray).orEmpty().mapNotNull { element ->
                    val ws = element as? JsonObject ?: return@mapNotNull null
                    val columns =
                        (ws["columns"] as? JsonArray).orEmpty().mapNotNull { c ->
                            val col = c as? JsonObject ?: return@mapNotNull null
                            column(col.string("typeKey"), col.string("param"), col.float("width"))
                        }
                    DeckWorkspace(DeckLayout.newId(), ws.string("name").orEmpty(), columns)
                }
            if (list.isNotEmpty() && list.any { it.columns.isNotEmpty() }) {
                val active = workspaces["activeIndex"]?.jsonPrimitive?.intOrNull ?: 0
                return DeckLayout(list.take(DeckLayout.MAX_WORKSPACES), active.coerceIn(0, list.lastIndex), dropped.toList())
            }
        }

        val columns =
            runCatching { json.parseToJsonElement(deckColumnsJson).jsonArray }
                .getOrNull()
                .orEmpty()
                .mapNotNull { c ->
                    val col = c as? JsonObject ?: return@mapNotNull null
                    column(col.string("type"), col.string("param"), col.float("width"))
                }
        if (columns.isEmpty()) return null
        return DeckLayout(listOf(DeckWorkspace(DeckLayout.newId(), "", columns)), 0, dropped.toList())
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.float(key: String): Float? = runCatching { this[key]?.jsonPrimitive?.floatOrNull }.getOrNull()
}
