package de.mm20.launcher2.msservices

import com.microsoft.graph.extensions.DriveItem as MSDriveItem

data class DriveItem(
        val id : String,
        val label : String,
        val mimeType : String,
        val size: Long,
        val isDirectory : Boolean,
        val webUrl: String,
        val meta: DriveItemMeta
) {
    companion object {
        fun fromApiDriveItem(driveItem: MSDriveItem) : DriveItem? {
            return DriveItem(
                    id = driveItem.id ?: return null,
                    label = driveItem.name ?: return null,
                    mimeType = driveItem.file?.mimeType ?: "inode/directory",
                    size = driveItem.size ?: 0,
                    isDirectory = driveItem.file == null,
                    webUrl = driveItem.webUrl ?: return null,
                    meta = DriveItemMeta(
                            owner = driveItem.shared?.owner?.user?.displayName,
                            createdBy = driveItem.createdBy?.user?.displayName,
                            width = driveItem.image?.width ?: driveItem.video?.width,
                            height = driveItem.image?.height ?: driveItem.video?.height
                    )
            )
        }
    }
}

data class DriveItemMeta(
        val owner: String?,
        val createdBy: String?,
        val width: Int?,
        val height: Int?
)