package de.mm20.launcher2.plugin.data

import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import de.mm20.launcher2.plugin.contracts.Column
import de.mm20.launcher2.plugin.contracts.Columns

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
            return with(column) { cursor.readAtIndex(index) }
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



fun <T> buildCursor(
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
    fun <T : Any> put(column: Column<T>, value: T?)
}

internal class RowBuilderScopeImpl(
    private val columnIndices: Map<String, Int>,
    internal val values: Array<Any?>
) : RowBuilderScope {
    override fun <T : Any> put(column: Column<T>, value: T?) {
        value ?: return
        val index = columnIndices[column.name] ?: return
        values[index] = with(column) { toCursorValue(value) }
    }
}