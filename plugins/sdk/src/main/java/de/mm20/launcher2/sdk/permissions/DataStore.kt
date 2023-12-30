package de.mm20.launcher2.sdk.permissions

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal val Context.permissionsDataStore by dataStore(
    fileName = "plugin_permissions.json",
    serializer = PermissionsSerializer,
)

internal object PermissionsSerializer : Serializer<PermissionData> {

    override val defaultValue: PermissionData
        get() = PermissionData()

    override suspend fun readFrom(input: InputStream): PermissionData {
        try {
            return Json.decodeFromStream(input)
        } catch (e: IllegalArgumentException) {
            throw (CorruptionException("Cannot read json.", e))
        } catch (e: SerializationException) {
            throw (CorruptionException("Cannot read json.", e))
        } catch (e: IOException) {
            throw (CorruptionException("Cannot read json.", e))
        }
    }

    override suspend fun writeTo(t: PermissionData, output: OutputStream) {
        Json.encodeToStream(t, output)
    }
}