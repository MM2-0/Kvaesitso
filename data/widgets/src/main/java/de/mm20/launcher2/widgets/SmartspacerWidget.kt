package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.PartialWidgetEntity
import java.util.UUID

data class SmartspacerWidget(
    override val id: UUID
) : Widget() {
    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = null
        )
    }

    companion object {
        const val Type = "smartspacer"
    }
}