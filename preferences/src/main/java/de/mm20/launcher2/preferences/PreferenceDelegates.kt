package de.mm20.launcher2.preferences

import androidx.core.content.edit
import de.mm20.launcher2.crashreporter.CrashReporter
import org.json.JSONArray
import org.json.JSONException
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StringPreference(val key: String, val default: String) : ReadWriteProperty<LauncherPreferences, String> {
    override fun getValue(thisRef: LauncherPreferences, property: KProperty<*>): String {
        return thisRef.preferences.getString(key, default)!!
    }

    override fun setValue(thisRef: LauncherPreferences, property: KProperty<*>, value: String) {
        thisRef.preferences.edit {
            putString(key, value)
        }
    }
}

class BooleanPreference(val key: String, val default: Boolean) : ReadWriteProperty<LauncherPreferences, Boolean> {
    override fun getValue(thisRef: LauncherPreferences, property: KProperty<*>): Boolean {
        return thisRef.preferences.getBoolean(key, default)
    }

    override fun setValue(thisRef: LauncherPreferences, property: KProperty<*>, value: Boolean) {
        thisRef.preferences.edit {
            putBoolean(key, value)
        }
    }
}

class IntPreference(val key: String, val default: Int) : ReadWriteProperty<LauncherPreferences, Int> {
    override fun getValue(thisRef: LauncherPreferences, property: KProperty<*>): Int {
        return thisRef.preferences.getInt(key, default)
    }

    override fun setValue(thisRef: LauncherPreferences, property: KProperty<*>, value: Int) {
        thisRef.preferences.edit {
            putInt(key, value)
        }
    }
}

class LongListPreference(val key: String, val default: List<Long>) : ReadWriteProperty<LauncherPreferences, List<Long>> {
    override fun getValue(thisRef: LauncherPreferences, property: KProperty<*>): List<Long> {
        val list = mutableListOf<Long>()
        try {
            val jsonArray = JSONArray(thisRef.preferences.getString(key, null) ?: return default)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getLong(i))
            }
        } catch (e: JSONException) {
            CrashReporter.logException(e)
            return default
        }
        return list
    }

    override fun setValue(thisRef: LauncherPreferences, property: KProperty<*>, value: List<Long>) {
        val jsonArray = JSONArray()
        for (i in value) {
            jsonArray.put(i)
        }
        thisRef.preferences.edit {
            putString(key, jsonArray.toString())
        }
    }
}


interface PreferenceEnum {
    val value: String
}

class EnumPreference<T>(val key: String, val default: T) : ReadWriteProperty<LauncherPreferences, T> where T : Enum<T>, T : PreferenceEnum {
    override fun getValue(thisRef: LauncherPreferences, property: KProperty<*>): T {
        val value = thisRef.preferences.getString(key, null) ?: return default
        val enumConstants = default::class.java.enumConstants as Array<out T>
        return enumConstants.firstOrNull { it.value == value } ?: default
    }

    override fun setValue(thisRef: LauncherPreferences, property: KProperty<*>, value: T) {
        thisRef.preferences.edit {
            putString(key, value.value)
        }
    }
}
