package de.mm20.launcher2.plugin.contracts

import android.database.Cursor
import android.database.MatrixCursor
import de.mm20.launcher2.serialization.Json
import kotlinx.serialization.serializer

fun <T> cursorOf(
    columns: Columns,
    data: List<T>,
    rowBuilder: RowBuilderScope.(item: T) -> Unit
): Cursor {
    val cursor = MatrixCursor(columns.columns.toTypedArray(), data.size)
    val values = Array<Any?>(columns.columns.size) { null }
    val scope = RowBuilderScopeImpl(
        columnIndices = columns.columns.withIndex().associate { (index, name) -> name to index },
        values = values
    )
    repeat(data.size) {
        values.fill(null)
        scope.rowBuilder(data[it])
        cursor.addRow(scope.values)
    }
    return cursor
}

interface RowBuilderScope {
    fun <T : Any> Column<T>.set(v: T?)
}

internal class RowBuilderScopeImpl(
    private val columnIndices: Map<String, Int>,
    internal val values: Array<Any?>
) : RowBuilderScope {
    override fun <T : Any> Column<T>.set(v: T?) {
        v ?: return
        val index = columnIndices[name] ?: return
        values[index] = when (this.type) {
            String::class -> v as String
            Int::class -> v as Int
            Long::class -> v as Long
            Boolean::class -> if (v as Boolean) 1 else 0
            Short::class -> v as Short
            Float::class -> v as Float
            Double::class -> v as Double
            else -> {
                if (this.type.java.isEnum) {
                    v.toString()
                } else {
                    Json.Lenient.encodeToString(
                        serializer(this.type.java),
                        v as T,
                    )
                }
            }
        }
    }
}