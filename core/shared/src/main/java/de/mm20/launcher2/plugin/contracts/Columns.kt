package de.mm20.launcher2.plugin.contracts

import android.database.Cursor
import android.os.Bundle
import android.util.Log
import de.mm20.launcher2.plugin.data.RowBuilderScope
import de.mm20.launcher2.serialization.Json
import kotlinx.serialization.encodeToString

abstract class Columns {
    internal val columns = mutableSetOf<String>()
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
    fun Cursor.readAtIndex(index: Int): T?
    fun RowBuilderScope.toCursorValue(value: T?): Any? {
        return value
    }

    fun Bundle.put(value: T?)
    fun Bundle.get(): T?
}

internal class IntColumn(
    override val name: String
) : Column<Int> {
    override fun Cursor.readAtIndex(index: Int): Int {
        return getInt(index)
    }

    override fun Bundle.put(value: Int?) {
        if (value == null) {
            remove(name)
        } else {
            putInt(name, value)
        }
    }

    override fun Bundle.get(): Int {
        return getInt(name)
    }
}


internal class LongColumn(
    override val name: String
) : Column<Long> {
    override fun Cursor.readAtIndex(index: Int): Long {
        return getLong(index)
    }

    override fun Bundle.put(value: Long?) {
        if (value == null) {
            remove(name)
        } else {
            putLong(name, value)
        }
    }

    override fun Bundle.get(): Long {
        return getLong(name)
    }
}

internal class DoubleColumn(
    override val name: String
) : Column<Double> {
    override fun Cursor.readAtIndex(index: Int): Double {
        return getDouble(index)
    }

    override fun Bundle.put(value: Double?) {
        if (value == null) {
            remove(name)
        } else {
            putDouble(name, value)
        }
    }

    override fun Bundle.get(): Double {
        return getDouble(name)
    }
}

internal class FloatColumn(
    override val name: String
) : Column<Float> {
    override fun Cursor.readAtIndex(index: Int): Float {
        return getFloat(index)
    }

    override fun Bundle.put(value: Float?) {
        if (value == null) {
            remove(name)
        } else {
            putFloat(name, value)
        }
    }

    override fun Bundle.get(): Float {
        return getFloat(name)
    }
}

internal class StringColumn(
    override val name: String
) : Column<String> {
    override fun Cursor.readAtIndex(index: Int): String? {
        return getString(index)
    }

    override fun Bundle.put(value: String?) {
        if (value == null) {
            remove(name)
        } else {
            putString(name, value)
        }
    }

    override fun Bundle.get(): String? {
        return getString(name)
    }
}

internal class ShortColumn(
    override val name: String
) : Column<Short> {
    override fun Cursor.readAtIndex(index: Int): Short {
        return getShort(index)
    }

    override fun Bundle.put(value: Short?) {
        if (value == null) {
            remove(name)
        } else {
            putShort(name, value)
        }
    }

    override fun Bundle.get(): Short {
        return getShort(name)
    }
}

internal class BooleanColumn(
    override val name: String
) : Column<Boolean> {
    override fun Cursor.readAtIndex(index: Int): Boolean {
        return getInt(index) != 0
    }

    override fun RowBuilderScope.toCursorValue(value: Boolean?): Any? {
        value ?: return null
        return if (value) 1 else 0
    }

    override fun Bundle.put(value: Boolean?) {
        if (value == null) {
            remove(name)
        } else {
            putBoolean(name, value)
        }
    }

    override fun Bundle.get(): Boolean? {
        return getBoolean(name)
    }
}


internal class BlobColumn(
    override val name: String
) : Column<ByteArray> {

    override fun Cursor.readAtIndex(index: Int): ByteArray? {
        return getBlob(index)
    }

    override fun Bundle.put(value: ByteArray?) {
        if (value == null) {
            remove(name)
        } else {
            putByteArray(name, value)
        }
    }

    override fun Bundle.get(): ByteArray? {
        return getByteArray(name)
    }
}

internal class SerializableColumn<T>(
    override val name: String,
    val serialize: (T) -> String,
    val deserialize: (String) -> T
) : Column<T> {
    override fun Cursor.readAtIndex(index: Int): T? {
        val string = getString(index)
        try {
            return deserialize(string)
        } catch (e: Exception) {
            Log.e("MM20", "Failed to read column value", e)
            return null
        }
    }

    override fun RowBuilderScope.toCursorValue(value: T?): Any? {
        return try {
            serialize(value ?: return null)
        } catch (e: Exception) {
            Log.e("MM20", "Failed to write column value", e)
            null
        }
    }

    override fun Bundle.put(value: T?) {
        val serialized = try {
            value?.let(serialize)
        } catch (e: Exception) {
            Log.e("MM20", "Failed to serialize column value", e)
            null
        }
        if (serialized == null) {
            remove(name)
        } else {
            putString(name, serialized)
        }
    }

    override fun Bundle.get(): T? {
        val string = getString(name) ?: return null
        return try {
            deserialize(string)
        } catch (e: Exception) {
            Log.e("MM20", "Failed to deserialize column value", e)
            null
        }
    }
}