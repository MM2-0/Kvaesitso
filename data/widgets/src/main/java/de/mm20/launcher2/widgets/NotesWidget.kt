package de.mm20.launcher2.widgets

import de.mm20.launcher2.database.entities.PartialWidgetEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class NotesWidgetConfig(
    /**
     * Text content of the widget. If the widget is linked to a file, this is the last saved content.
     */
    val storedText: String = "",
    val linkedFile: String? = null,
    /**
     * Indicates whether the last read/write operation on the linked file was successful.
     * If false, a conflict resolver will be shown if the note content differs from the file content.
     */
    val lastSyncSuccessful: Boolean = false,
)

data class NotesWidget(
    override val id: UUID,
    val config: NotesWidgetConfig = NotesWidgetConfig(),
) : Widget() {

    override fun toDatabaseEntity(): PartialWidgetEntity {
        return PartialWidgetEntity(
            id = id,
            type = Type,
            config = Json.encodeToString(config),
        )
    }

    companion object {
        const val Type = "notes"
    }
}