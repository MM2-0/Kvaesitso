package de.mm20.launcher2.sdk.permissions

import android.content.Context
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import java.io.InputStream
import java.io.OutputStream

internal val Context.permissionsDataStore by dataStore(
    fileName = "plugin_permissions",
    serializer = PermissionsSerializer,
)

internal object PermissionsSerializer : Serializer<Set<String>> {
    override val defaultValue: Set<String>
        get() = emptySet()

    override suspend fun readFrom(input: InputStream): Set<String> {
        return input.bufferedReader().readLines().toSet()
    }

    override suspend fun writeTo(t: Set<String>, output: OutputStream) {
        output.bufferedWriter().write(t.joinToString("\n"))
    }
}