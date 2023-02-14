package de.mm20.launcher2.icons

import android.content.ComponentName
import de.mm20.launcher2.database.entities.IconEntity

data class IconPackIcon(
    val type: String,
    val componentName: ComponentName?,
    val drawable: String?,
    val iconPack: String,
    val name: String? = null,
) {
    constructor(entity: IconEntity) : this(
        type = entity.type,
        componentName = entity.componentName,
        drawable = entity.drawable,
        iconPack = entity.iconPack,
        name = entity.name,
    )

    fun toDatabaseEntity(): IconEntity {
        return IconEntity(
            type = type,
            componentName = componentName,
            drawable = drawable,
            iconPack = iconPack,
            name = name,
        )
    }
}