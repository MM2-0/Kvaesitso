package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.WidgetEntity

data class Widget(
        val type: WidgetType,
        var data: String,
        var height: Int,
        val label: String = ""
) {
    constructor(entity: WidgetEntity) : this(
            type = if (entity.type == "internal") WidgetType.INTERNAL else WidgetType. THIRD_PARTY,
            data = entity.data,
            height = entity.height,
            label = entity.label
    )

    fun toDatabaseEntity(position: Int): WidgetEntity {
        return WidgetEntity(
                type = if (type == WidgetType.INTERNAL) "internal" else "3rdparty",
                label = label,
                position = position,
                height = height,
                data = data
        )
    }
}

enum class WidgetType {
    INTERNAL,
    THIRD_PARTY
}