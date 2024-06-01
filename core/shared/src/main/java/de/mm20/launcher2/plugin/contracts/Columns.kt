package de.mm20.launcher2.plugin.contracts

import android.database.Cursor
import android.util.Log
import de.mm20.launcher2.serialization.Json
import kotlinx.serialization.encodeToString

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
    override operator fun <T : Any> Cursor.get(column: Column<T>): T? {
        val index = columnIndices[column.name] ?: return null
        if (index == -1) return null
        if (cursor.isNull(index)) return null
        try {
            return column.read(cursor, index)
        } catch (e: Exception) {
            Log.e("MM20", "Failed to get column value", e)
        }
        return null
    }

    override fun Cursor.contains(column: Column<*>): Boolean {
        return columnIndices.containsKey(column.name)
    }
}

fun Cursor.withColumns(columns: Columns, block: ColumnsScope.() -> Unit) {
    val scope = ColumnsScopeImpl(
        columns.columns.associateWith { name -> getColumnIndex(name) },
        this
    )
    scope.block()
}

@Suppress("UNCHECKED_CAST")
internal inline fun <reified T : Any> Columns.column(name: String): Column<T> {
    val column = when (T::class) {
        Int::class -> IntColumn(name)
        Long::class -> LongColumn(name)
        String::class -> StringColumn(name)
        Short::class -> ShortColumn(name)
        Boolean::class -> BooleanColumn(name)
        Double::class -> DoubleColumn(name)
        Float::class -> FloatColumn(name)
        ByteArray::class -> BlobColumn(name)
        else -> {
            if (T::class.java.isEnum) {
                SerializableColumn(
                    name,
                    { it.toString() },
                    { v -> T::class.java.enumConstants?.find { it.toString() == v } }
                )
            } else {
                SerializableColumn(
                    name,
                    { Json.Lenient.encodeToString(it) },
                    { Json.Lenient.decodeFromString(it) }
                )
            }
        }
    } as Column<T>
    columns.add(name)
    return column
}

sealed interface Column<T> {
    val name: String
    fun read(cursor: Cursor, index: Int): T?
    fun write(value: T?): Any?
}

internal class IntColumn(
    override val name: String
) : Column<Int> {
    override fun read(cursor: Cursor, index: Int): Int {
        return cursor.getInt(index)
    }

    override fun write(value: Int?): Any? {
        return value
    }
}


internal class LongColumn(
    override val name: String
) : Column<Long> {
    override fun read(cursor: Cursor, index: Int): Long {
        return cursor.getLong(index)
    }

    override fun write(value: Long?): Any? {
        return value
    }
}

internal class DoubleColumn(
    override val name: String
) : Column<Double> {
    override fun read(cursor: Cursor, index: Int): Double {
        return cursor.getDouble(index)
    }

    override fun write(value: Double?): Any? {
        return value
    }
}

internal class FloatColumn(
    override val name: String
) : Column<Float> {
    override fun read(cursor: Cursor, index: Int): Float {
        return cursor.getFloat(index)
    }

    override fun write(value: Float?): Any? {
        return value
    }
}

internal class StringColumn(
    override val name: String
) : Column<String> {
    override fun read(cursor: Cursor, index: Int): String {
        return cursor.getString(index)
    }

    override fun write(value: String?): Any? {
        return value
    }
}

internal class ShortColumn(
    override val name: String
) : Column<Short> {
    override fun read(cursor: Cursor, index: Int): Short {
        return cursor.getShort(index)
    }

    override fun write(value: Short?): Any? {
        return value
    }
}

internal class BooleanColumn(
    override val name: String
) : Column<Boolean> {
    override fun read(cursor: Cursor, index: Int): Boolean {
        return cursor.getInt(index) != 0
    }

    override fun write(value: Boolean?): Any? {
        value ?: return null
        return if (value == true) 1 else 0
    }
}


internal class BlobColumn(
    override val name: String
) : Column<ByteArray> {
    override fun read(cursor: Cursor, index: Int): ByteArray {
        return cursor.getBlob(index)
    }

    override fun write(value: ByteArray?): Any? {
        return value
    }
}

internal class SerializableColumn<T>(
    override val name: String,
    val serialize: (T) -> String,
    val deserialize: (String) -> T
) : Column<T> {
    override fun read(cursor: Cursor, index: Int): T? {
        val string = cursor.getString(index)
        try {
            return deserialize(string)
        } catch (e: Exception) {
            Log.e("MM20", "Failed to read column value", e)
            return null
        }
    }

    override fun write(value: T?): Any? {
        return serialize(value ?: return null)
    }
}