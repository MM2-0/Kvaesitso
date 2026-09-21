package de.mm20.launcher2.database

import android.content.ComponentName
import androidx.room3.ColumnTypeConverter
import org.json.JSONArray

class ComponentNameConverter {
    @ColumnTypeConverter
    fun toString(componentName: ComponentName?): String? {
        return componentName?.flattenToString()
    }

    @ColumnTypeConverter
    fun toComponentName(string: String?) : ComponentName? {
        string ?: return null
        return ComponentName.unflattenFromString(string)
    }

}

class StringListConverter {
    @ColumnTypeConverter
    fun toString(list: List<String>): String {
        val json = JSONArray()
        list.forEach { json.put(it) }
        return json.toString()
    }

    @ColumnTypeConverter
    fun toStringList(string: String): List<String> {
        val json = JSONArray(string)
        return (0..json.length()).map { json.getString(it) }
    }
}
