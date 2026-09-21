package de.mm20.launcher2.database.ktx

import androidx.annotation.IntRange
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteStatement

fun SQLiteStatement.bindTextOrNull(@IntRange(from = 1) index: Int, value: String?) {
    if (value == null) {
        bindNull(index)
    } else {
        bindText(index, value)
    }
}

fun SQLiteStatement.getTextOrNull(@IntRange(from = 0) index: Int): String? {
    if (isNull(index)) {
        return null
    }
    if (getColumnType(index) != SQLITE_DATA_TEXT) return null
    return getText(index)
}

fun SQLiteStatement.getIntOrNull(@IntRange(from = 0) index: Int): Int? {
    if (isNull(index)) {
        return null
    }
    if (getColumnType(index) != SQLITE_DATA_INTEGER) return null
    return getInt(index)
}