package de.mm20.launcher2.backup

import de.mm20.launcher2.ktx.jsonObjectOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream

data class BackupMetadata(
    val deviceName: String,
    val timestamp: Long,
    val appVersionName: String,
    /**
     * Backup schema version in format x.y.
     */
    val format: String,
    val components: Set<BackupComponent>,
) {

    internal suspend fun writeToFile(file: File) {
        val json = jsonObjectOf(
            "device" to deviceName,
            "timestamp" to timestamp,
            "format" to format,
            "versionName" to appVersionName,
            "components" to JSONArray(components.map { it.value })
        )
        withContext(Dispatchers.IO) {
            file.outputStream().bufferedWriter().use {
                it.write(json.toString())
            }
        }
    }

    companion object {
        internal suspend fun fromInputStream(inputStream: InputStream): BackupMetadata? {
            return withContext(Dispatchers.IO) {
                val text = inputStream.reader().readText()
                try {
                    val json = JSONObject(text)
                    return@withContext BackupMetadata(
                        deviceName = json.optString("device"),
                        timestamp = json.optLong("timestamp"),
                        format = json.optString("format"),
                        appVersionName = json.optString("versionName"),
                        components = json.getJSONArray("components").let {
                            val set = mutableSetOf<BackupComponent>()
                            for (i in 0 until it.length()) {
                                val component = BackupComponent.fromValue(it.getString(i))
                                if (component != null) set.add(component)
                            }
                            set
                        }
                    )
                } catch (e: JSONException) {
                    return@withContext null
                }
            }
        }
    }
}