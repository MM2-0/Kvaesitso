package de.mm20.launcher2.plugin.data

import android.os.Bundle
import de.mm20.launcher2.plugin.contracts.Column

operator fun <T> Bundle.get(column: Column<T>) {
    return with(column) {
        get()
    }
}

operator fun <T> Bundle.set(column: Column<T>, value: T) {
    with(column) {
        put(value)
    }
}