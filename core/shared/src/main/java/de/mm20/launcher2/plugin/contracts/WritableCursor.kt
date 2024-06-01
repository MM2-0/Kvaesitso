package de.mm20.launcher2.plugin.contracts

import android.database.Cursor
import android.database.MatrixCursor

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
        values[index] = this.write(v)
    }
}