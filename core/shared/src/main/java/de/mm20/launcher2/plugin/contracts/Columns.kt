package de.mm20.launcher2.plugin.contracts

import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import de.mm20.launcher2.serialization.Json
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

abstract class Columns {
    internal val columns = mutableSetOf<String>()
}

interface ColumnsScope {
    operator fun <T : Any> Cursor.get(column: Column<T>): T?
    operator fun Cursor.contains(column: Column<*>): Boolean
}

internal class ColumnsScopeImpl(
    private val columnIndices: Map<String, Int>,
    private val cursor: Cursor
) : ColumnsScope {
    @Suppress("UNCHECKED_CAST")
    override operator fun <T : Any> Cursor.get(column: Column<T>): T? {
        val index = columnIndices[column.name] ?: return null
        if (index == -1) return null
        if (cursor.isNull(index)) return null
        try {
            when (column.type) {
                String::class -> return cursor.getString(index) as T
                Int::class -> return cursor.getInt(index) as T
                Long::class -> return cursor.getLong(index) as T
                Short::class -> return cursor.getShort(index) as T
                Float::class -> return cursor.getFloat(index) as T
                Double::class -> return cursor.getDouble(index) as T
                ByteArray::class -> return cursor.getBlob(index) as T
                Boolean::class -> return (cursor.getInt(index) != 0) as T
                else -> {
                    if (column.type.java.isEnum) {
                        val value = cursor.getString(index)
                        val enumConstants = column.type.java.enumConstants
                        return enumConstants?.find { it.toString() == value } as T
                    }
                    Json.Lenient.decodeFromString(
                        serializer(column.type.java),
                        cursor.getString(index)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MM20", "Failed to get column value", e)
        }
        return null
    }

    override fun Cursor.contains(column: Column<*>): Boolean {
        return columnIndices.containsKey(column.name)
    }
}

interface WritableColumnsScope {
    fun MatrixCursor.appendRow(builder: () -> Unit)
}

fun Cursor.withColumns(columns: Columns, block: ColumnsScope.() -> Unit) {
    val scope = ColumnsScopeImpl(
        columns.columns.associateWith { name -> getColumnIndex(name) },
        this
    )
    scope.block()
}

internal inline fun <reified T : Any> Columns.column(name: String): Column<T> {
    return Column(name, T::class).also {
        columns.add(it.name)
    }
}

fun Cursor(columns: Columns): Cursor {
    return MatrixCursor(columns.columns.toTypedArray())
}

data class Column<T : Any>(
    val name: String,
    val type: KClass<T>,
)