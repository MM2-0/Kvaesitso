package de.mm20.launcher2.gservices

import com.google.api.services.drive.model.File
import java.util.*

data class DriveFile(
        val fileId : String,
        val label: String,
        val size: Long,
        val mimeType : String,
        val isDirectory : Boolean,
        val directoryColor: String?,
        val viewUri: String,
        val metadata: DriveFileMeta
) {
    companion object {
        fun fromApiDriveFile(file: File): DriveFile {
            return DriveFile(
                    fileId = file.id,
                    label = file.name,
                    size = file.getSize() ?: 0,
                    isDirectory = file.mimeType == "application/vnd.google-apps.folder",
                    mimeType = file.mimeType,
                    metadata = DriveFileMeta(
                            owners = file.owners?.map { it.displayName ?: it.emailAddress ?: "" } ?: emptyList(),
                            width = file.imageMediaMetadata?.width ?: file.videoMediaMetadata?.width,
                            height = file.imageMediaMetadata?.height ?: file.videoMediaMetadata?.height
                    ),
                    directoryColor = file.folderColorRgb?.lowercase(Locale.ROOT),
                    viewUri = file.webViewLink ?: ""
            )
        }
    }
}

data class DriveFileMeta(
        val owners : List<String>,
        val width: Int?,
        val height: Int?
)