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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One deck column: the screen it starts on ([root], any [Route]) and its width in dp. */
@Immutable
@Serializable
data class DeckColumn(
    val id: String,
    val root: Route,
    val width: Float = DeckLayout.DEFAULT_COLUMN_WIDTH,
)

/** A named set of columns. A blank [name] shows as "Workspace N". */
@Immutable
@Serializable
data class DeckWorkspace(
    val id: String,
    val name: String,
    val columns: List<DeckColumn>,
)

/**
 * The deck's columns, kept as workspaces the user switches between. Every operation returns a new
 * layout, so the account settings can hold it in one StateFlow and persist it as JSON.
 *
 * [importNotice] lists the legacy desktop columns that could not be brought over, shown once.
 */
@Immutable
@Serializable
data class DeckLayout(
    val workspaces: List<DeckWorkspace> = listOf(DeckWorkspace(newId(), "", listOf(DeckColumn(newId(), Route.Notification())))),
    val active: Int = 0,
    val importNotice: List<String> = emptyList(),
) {
    val current: DeckWorkspace get() = workspaces[active.coerceIn(0, workspaces.lastIndex)]

    private fun mapCurrent(transform: (List<DeckColumn>) -> List<DeckColumn>): DeckLayout {
        val index = active.coerceIn(0, workspaces.lastIndex)
        return copy(workspaces = workspaces.mapIndexed { i, w -> if (i == index) w.copy(columns = transform(w.columns)) else w })
    }

    /** Adds a column showing [root] after [afterId], or at the end. */
    fun addColumn(
        root: Route,
        afterId: String? = null,
    ): DeckLayout =
        mapCurrent { columns ->
            val column = DeckColumn(newId(), root)
            val at = columns.indexOfFirst { it.id == afterId }
            if (at < 0) columns + column else columns.take(at + 1) + column + columns.drop(at + 1)
        }

    fun removeColumn(id: String) = mapCurrent { columns -> columns.filterNot { it.id == id } }

    /** Moves the column [id] by [delta] places, stopping at either end. */
    fun moveColumn(
        id: String,
        delta: Int,
    ) = mapCurrent { columns ->
        val from = columns.indexOfFirst { it.id == id }
        if (from < 0) return@mapCurrent columns
        val to = (from + delta).coerceIn(0, columns.lastIndex)
        if (to == from) return@mapCurrent columns
        columns.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Sets the column's width, kept between [MIN_COLUMN_WIDTH] and [MAX_COLUMN_WIDTH]. */
    fun resizeColumn(
        id: String,
        width: Float,
    ) = mapCurrent { columns ->
        columns.map { if (it.id == id) it.copy(width = width.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)) else it }
    }

    fun switchTo(index: Int) = if (index in workspaces.indices) copy(active = index) else this

    /** Saves the current columns as a new workspace named [name] and switches to it; at most [MAX_WORKSPACES]. */
    fun saveAsWorkspace(name: String): DeckLayout {
        if (workspaces.size >= MAX_WORKSPACES) return this
        val copyOfColumns = current.columns.map { it.copy(id = newId()) }
        return copy(workspaces = workspaces + DeckWorkspace(newId(), name.trim(), copyOfColumns), active = workspaces.size)
    }

    fun renameWorkspace(
        index: Int,
        name: String,
    ) = copy(workspaces = workspaces.mapIndexed { i, w -> if (i == index) w.copy(name = name.trim()) else w })

    /** Deletes the workspace at [index]; the last one stays. */
    fun deleteWorkspace(index: Int): DeckLayout {
        if (workspaces.size <= 1 || index !in workspaces.indices) return this
        val left = workspaces.filterIndexed { i, _ -> i != index }
        val newActive =
            when {
                active > index -> active - 1
                active == index -> (index - 1).coerceAtLeast(0)
                else -> active
            }
        return copy(workspaces = left, active = newActive.coerceIn(0, left.lastIndex))
    }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        const val MIN_COLUMN_WIDTH = 300f
        const val MAX_COLUMN_WIDTH = 800f
        const val DEFAULT_COLUMN_WIDTH = 400f
        const val MAX_WORKSPACES = 9

        private val json = Json { ignoreUnknownKeys = true }

        @OptIn(ExperimentalUuidApi::class)
        fun newId(): String = Uuid.random().toString()

        /**
         * What [toJson] wrote, or null when nothing can be read. Columns are read one by one: a
         * column whose screen this build does not know (renamed, or added by a newer version) is
         * dropped, not every workspace with it.
         */
        fun fromJson(value: String): DeckLayout? =
            runCatching {
                val root = json.parseToJsonElement(value).jsonObject
                val workspaces =
                    (root["workspaces"] as? JsonArray).orEmpty().mapNotNull { element ->
                        val workspace = element as? JsonObject ?: return@mapNotNull null
                        val columns =
                            (workspace["columns"] as? JsonArray).orEmpty().mapNotNull { column ->
                                runCatching { json.decodeFromJsonElement(DeckColumn.serializer(), column) }.getOrNull()
                            }
                        DeckWorkspace(workspace.string("id") ?: newId(), workspace.string("name").orEmpty(), columns)
                    }
                if (workspaces.isEmpty()) return@runCatching null
                val active = (root["active"] as? JsonPrimitive)?.intOrNull ?: 0
                val notice = (root["importNotice"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                DeckLayout(workspaces, active.coerceIn(0, workspaces.lastIndex), notice)
            }.getOrNull()

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
