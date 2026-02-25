package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class FavoritesWidgetConfig(
    val customTags: Boolean = false,
    val editButton: Boolean = true,
    val tagsMultiline: Boolean = false,
    val compactTags: Boolean = false,
    val tagList: List<String> = emptyList(),
    val latestButton: Boolean = false
)

data class AppsWidget(
    override val id: UUID,
    val config: FavoritesWidgetConfig = FavoritesWidgetConfig(),
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    companion object {
        const val Type = "favorites"
    }
}