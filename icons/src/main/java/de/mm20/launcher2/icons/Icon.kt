package de.mm20.launcher2.icons

import android.content.ComponentName
import de.mm20.launcher2.database.entities.IconEntity

data class Icon(
        val type: String,
        val componentName: ComponentName?,
        val drawable: String?,
        val iconPack: String,
        val scale: Float? = null
) {
    constructor(entity: IconEntity) : this(
            type = entity.type,
            componentName = entity.componentName,
            drawable = entity.drawable,
            iconPack = entity.iconPack,
            scale = entity.scale
    )

    fun toDatabaseEntity(): IconEntity {
        return IconEntity(
                type = type,
                componentName = componentName,
                drawable = drawable,
                iconPack = iconPack,
                scale = scale
        )
    }
}