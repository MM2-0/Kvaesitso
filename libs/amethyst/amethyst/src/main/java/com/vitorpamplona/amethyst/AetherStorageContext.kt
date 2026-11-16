/*
 * Copyright (c) 2026 Telos contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vitorpamplona.amethyst

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Storage boundary for the embedded client. Construct and use storage APIs on IO. */
class AetherStorageContext(context: Context) : ContextWrapper(applicationBase(context)) {
    private val privateRoot = File(baseContext.noBackupFilesDir, "aether")
    private val preferences by lazy { baseContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE) }

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = File(privateRoot, "files").apply { mkdirs() }

    override fun getNoBackupFilesDir(): File = privateRoot.apply { mkdirs() }

    override fun getDataDir(): File = noBackupFilesDir

    override fun getDir(name: String, mode: Int): File {
        require(mode == Context.MODE_PRIVATE && File(name).name == name)
        return File(privateRoot, "app_$name").apply { mkdirs() }
    }

    override fun getFileStreamPath(name: String): File {
        require(File(name).name == name)
        return File(filesDir, name)
    }

    override fun openFileInput(name: String): FileInputStream = FileInputStream(getFileStreamPath(name))

    override fun openFileOutput(name: String, mode: Int): FileOutputStream {
        require(mode == Context.MODE_PRIVATE || mode == Context.MODE_APPEND)
        return FileOutputStream(getFileStreamPath(name), mode == Context.MODE_APPEND)
    }

    override fun deleteFile(name: String): Boolean = getFileStreamPath(name).delete()

    override fun fileList(): Array<String> {
        val names = filesDir.list().orEmpty()
        return Array<String>(names.size) { names[it] }
    }

    override fun getCacheDir(): File = File(baseContext.cacheDir, "aether").apply { mkdirs() }

    override fun getDatabasePath(name: String): File {
        val directory = File(privateRoot, "databases").apply { mkdirs() }
        val file = File(name)
        return if (file.isAbsolute) {
            require(file.canonicalFile.toPath().startsWith(directory.canonicalFile.toPath()))
            file
        } else {
            require(File(name).name == name) { "Database name must be a filename" }
            File(directory, name)
        }
    }

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory)

    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
        baseContext.openOrCreateDatabase(getDatabasePath(name).absolutePath, mode, factory, errorHandler)

    override fun deleteDatabase(name: String): Boolean = baseContext.deleteDatabase(getDatabasePath(name).absolutePath)

    override fun databaseList(): Array<String> {
        val names = File(privateRoot, "databases").list().orEmpty()
        return Array<String>(names.size) { names[it] }
    }

    // One explicitly excluded file keeps every legacy encrypted account and Tink keyset
    // out of backup without excluding the launcher's own preference files.
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        require(mode == Context.MODE_PRIVATE) { "Aether preferences must be private" }
        return ScopedPreferences(preferences, "${name.length}:$name:")
    }

    override fun deleteSharedPreferences(name: String): Boolean =
        getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()

    companion object {
        const val PREFERENCES_FILE = "aether_private"

        internal fun applicationBase(context: Context): Context {
            var application = context.applicationContext
            while (application is AetherStorageContext) application = application.baseContext
            return application
        }
    }
}

/** Preserve Android SharedPreferences semantics for each original upstream filename. */
private class ScopedPreferences(private val backing: SharedPreferences, private val prefix: String) : SharedPreferences {
    private val listeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = backing.all.filterKeys { it.startsWith(prefix) }.mapKeys { it.key.removePrefix(prefix) }
    override fun getString(key: String?, defValue: String?): String? = backing.getString(prefix + key, defValue)
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = backing.getStringSet(prefix + key, defValues)
    override fun getInt(key: String?, defValue: Int): Int = backing.getInt(prefix + key, defValue)
    override fun getLong(key: String?, defValue: Long): Long = backing.getLong(prefix + key, defValue)
    override fun getFloat(key: String?, defValue: Float): Float = backing.getFloat(prefix + key, defValue)
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = backing.getBoolean(prefix + key, defValue)
    override fun contains(key: String?): Boolean = backing.contains(prefix + key)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener == null) return
        synchronized(listeners) {
            if (listeners.containsKey(listener)) return
            val reference = WeakReference(listener)
            val wrapped = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key?.startsWith(prefix) == true) reference.get()?.onSharedPreferenceChanged(this, key.removePrefix(prefix))
            }
            listeners[listener] = wrapped
            backing.registerOnSharedPreferenceChangeListener(wrapped)
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        synchronized(listeners) { listeners.remove(listener)?.let(backing::unregisterOnSharedPreferenceChangeListener) }
    }

    override fun edit(): SharedPreferences.Editor = Editor()

    private inner class Editor : SharedPreferences.Editor {
        private var clearNamespace = false
        private val changes: MutableMap<String, SharedPreferences.Editor.() -> Unit> = LinkedHashMap()

        private fun change(key: String?, action: SharedPreferences.Editor.() -> Unit): SharedPreferences.Editor = apply {
            changes[prefix + key] = action
        }

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = change(key) { putString(prefix + key, value) }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            val snapshot = values?.toMutableSet()
            return change(key) { putStringSet(prefix + key, snapshot) }
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = change(key) { putInt(prefix + key, value) }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = change(key) { putLong(prefix + key, value) }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = change(key) { putFloat(prefix + key, value) }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = change(key) { putBoolean(prefix + key, value) }
        override fun remove(key: String?): SharedPreferences.Editor = change(key) { remove(prefix + key) }
        override fun clear(): SharedPreferences.Editor = apply { clearNamespace = true }

        private fun pending(): SharedPreferences.Editor {
            val editor = backing.edit()
            if (clearNamespace) backing.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
            changes.values.forEach { it(editor) }
            clearNamespace = false
            changes.clear()
            return editor
        }

        override fun commit(): Boolean = synchronized(backing) { pending().commit() }
        override fun apply() { synchronized(backing) { pending().apply() } }
    }
}
