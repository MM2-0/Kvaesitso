package de.mm20.launcher2.icons

import android.content.Context
import android.content.pm.ResolveInfo
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

    internal constructor(
        context: Context,
        resolveInfo: ResolveInfo,
        themed: Boolean = false
    ): this(
        name = resolveInfo.loadLabel(context.packageManager).toString(),
        packageName = resolveInfo.activityInfo.packageName,
        version = context.packageManager.getPackageInfo(resolveInfo.activityInfo.packageName, 0).versionName,
        themed = themed,
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