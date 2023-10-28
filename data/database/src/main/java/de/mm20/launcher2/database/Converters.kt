package de.mm20.launcher2.database

import android.content.ComponentName
import androidx.room.TypeConverter
import org.json.JSONArray

class ComponentNameConverter {
    @TypeConverter
    fun toString(componentName: ComponentName?): String? {
        return componentName?.flattenToString()
    }

    @TypeConverter
    fun toComponentName(string: String?) : ComponentName? {
        string ?: return null
        return ComponentName.unflattenFromString(string)
    }

}

class StringListConverter {
    @TypeConverter
    fun toString(list: List<String>): String {
        val json = JSONArray()
        list.forEach { json.put(it) }
        return json.toString()
    }

    @TypeConverter
    fun toStringList(string: String): List<String> {
        val json = JSONArray(string)
        return (0..json.length()).map { json.getString(it) }
    }
}
