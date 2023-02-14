package de.mm20.launcher2.icons

import de.mm20.launcher2.database.entities.IconPackEntity

data class IconPack(
    val name: String,
    val packageName: String,
    val version: String,
    var scale: Float = 1f,
    val themed: Boolean = false,
    ) {
    constructor(entity: IconPackEntity) : this(
        name = entity.name,
        packageName = entity.packageName,
        version = entity.packageName,
        scale = entity.scale,
        themed = entity.themed,
    )

    fun toDatabaseEntity(): IconPackEntity {
        return IconPackEntity(
            name = name,
            scale = scale,
            version = version,
            packageName = packageName,
            themed = themed,
        )
    }
}