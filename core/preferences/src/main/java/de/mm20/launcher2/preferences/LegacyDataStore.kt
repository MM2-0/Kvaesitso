package de.mm20.launcher2.preferences

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.migrations.*
import java.io.InputStream
import java.io.OutputStream

internal typealias LegacyDataStore = DataStore<LegacySettings>

internal val Context.legacyDataStore: LegacyDataStore by dataStore(
    fileName = "settings.pb",
    serializer = LegacySettingsSerializer,
    produceMigrations = {
        getMigrations(it)
    },
    corruptionHandler = ReplaceFileCorruptionHandler {
        CrashReporter.logException(it)
        LegacySettings.getDefaultInstance()
    }
)

internal const val SchemaVersion = 18

internal fun getMigrations(context: Context): List<DataMigration<LegacySettings>> {
    return listOf()
}



object LegacySettingsSerializer : Serializer<LegacySettings> {
    override val defaultValue: LegacySettings = LegacySettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LegacySettings {
        try {
            return LegacySettings.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", e)
        }
    }

    override suspend fun writeTo(t: LegacySettings, output: OutputStream) {
        t.writeTo(output)
    }
}