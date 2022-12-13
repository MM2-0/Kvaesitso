package de.mm20.launcher2.data.customattrs

import android.util.Log
import de.mm20.launcher2.database.entities.CustomAttributeEntity
import de.mm20.launcher2.ktx.jsonObjectOf
import org.json.JSONObject

sealed interface CustomAttribute {
    fun toDatabaseEntity(key: String): CustomAttributeEntity

    companion object {
        internal fun fromDatabaseEntity(entity: CustomAttributeEntity?): CustomAttribute? {
            if (entity == null) return null
            return when (entity.type) {
                CustomAttributeType.Label.value -> CustomLabel(
                    label = entity.value,
                    key = entity.key
                )
                CustomAttributeType.Tag.value -> CustomTag(
                    tagName = entity.value
                )
                CustomAttributeType.Icon.value -> CustomIcon.fromDatabaseEntity(entity)
                else -> {
                    Log.e("MM20", "Invalid custom attribute type: ${entity.type}")
                    null
                }
            }
        }
    }

}


class CustomLabel(
    val key: String,
    val label: String,
) : CustomAttribute {
    override fun toDatabaseEntity(key: String): CustomAttributeEntity {
        return CustomAttributeEntity(
            key = key,
            type = CustomAttributeType.Label.value,
            value = label,
        )
    }

}

class CustomTag(
    val tagName: String
): CustomAttribute {
    override fun toDatabaseEntity(key: String): CustomAttributeEntity {
        return CustomAttributeEntity(
            key = key,
            type = CustomAttributeType.Tag.value,
            value = tagName,
        )
    }
}


sealed class CustomIcon : CustomAttribute {

    override fun toDatabaseEntity(key: String): CustomAttributeEntity {
        return CustomAttributeEntity(
            key = key,
            type = CustomAttributeType.Icon.value,
            value = this.toDatabaseValue()
        )
    }

    internal abstract fun toDatabaseValue(): String

    companion object {
        internal fun fromDatabaseEntity(entity: CustomAttributeEntity): CustomIcon? {
            val payload = JSONObject(entity.value)
            val type = payload.getString("type")
            return when (type) {
                "custom_icon_pack_icon" -> {
                    CustomIconPackIcon(
                        iconComponentName = payload.getString("icon"),
                        iconPackPackage = payload.getString("icon_pack")
                    )
                }
                "custom_themed_icon" -> {
                    CustomThemedIcon(
                        iconPackageName = payload.getString("icon"),
                    )
                }
                "default_icon" -> {
                    UnmodifiedSystemDefaultIcon
                }
                "adaptified_legacy_icon" -> {
                    AdaptifiedLegacyIcon(
                        fgScale = payload.getDouble("fg_scale").toFloat(),
                        bgColor = payload.getInt("bg_color")
                    )
                }
                "force_themed_icon" -> ForceThemedIcon
                "default_placeholder_icon" -> DefaultPlaceholderIcon
                else -> null
            }
        }
    }
}

data class CustomIconPackIcon(
    val iconPackPackage: String,
    val iconComponentName: String,
) : CustomIcon() {
    override fun toDatabaseValue(): String {
        return jsonObjectOf(
            "type" to "custom_icon_pack_icon",
            "icon" to iconComponentName,
            "icon_pack" to iconPackPackage,
        ).toString()
    }
}

data class AdaptifiedLegacyIcon(
    val fgScale: Float,
    /**
     * The background color in ARGB format or [UnspecifiedColor] or [ThemeColor]
     */
    val bgColor: Int = UnspecifiedColor,
): CustomIcon() {
    override fun toDatabaseValue(): String {
        return jsonObjectOf(
            "type" to "adaptified_legacy_icon",
            "fg_scale" to fgScale,
            "bg_color" to bgColor,
        ).toString()
    }

    companion object {
        /**
         * Extract color from foreground icon
         */
        const val UnspecifiedColor = 1

        /**
         * Use color from theme
         */
        const val ThemeColor = 0
    }

}

data class CustomThemedIcon(
    val iconPackageName: String,
) : CustomIcon() {
    override fun toDatabaseValue(): String {
        return jsonObjectOf(
            "type" to "custom_themed_icon",
            "icon" to iconPackageName,
        ).toString()
    }
}

object ForceThemedIcon : CustomIcon() {
    override fun toDatabaseValue(): String {
        return jsonObjectOf(
            "type" to "force_themed_icon"
        ).toString()
    }
}

/**
 * Use default icon, ignore any icon pack, themed icon or force adaptive settings.
 */
object UnmodifiedSystemDefaultIcon: CustomIcon() {
    override fun toDatabaseValue(): String {
        return jsonObjectOf(
            "type" to "default_icon"
        ).toString()
    }

}

/**
 * Use the default placeholder icon
 */
object DefaultPlaceholderIcon: CustomIcon() {
    override fun toDatabaseValue(): String {
        return jsonObjectOf(
            "type" to "default_placeholder_icon"
        ).toString()
    }
}