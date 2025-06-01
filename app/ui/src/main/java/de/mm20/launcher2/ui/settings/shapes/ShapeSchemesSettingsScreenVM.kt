package de.mm20.launcher2.ui.settings.shapes

import android.content.Context
import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.ShapesDescriptor
import de.mm20.launcher2.preferences.ui.UiSettings
import de.mm20.launcher2.themes.CutShapesId
import de.mm20.launcher2.themes.DefaultThemeId
import de.mm20.launcher2.themes.ExtraRoundShapesId
import de.mm20.launcher2.themes.RectShapesId
import de.mm20.launcher2.themes.Shapes
import de.mm20.launcher2.themes.ThemeRepository
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID
import kotlin.getValue

class ShapeSchemesSettingsScreenVM : ViewModel(), KoinComponent {

    private val themeRepository: ThemeRepository by inject()
    private val uiSettings: UiSettings by inject()

    val selectedShapes = uiSettings.shapes.map {
        when(it) {
            ShapesDescriptor.Default -> DefaultThemeId
            ShapesDescriptor.Cut -> CutShapesId
            ShapesDescriptor.ExtraRound -> ExtraRoundShapesId
            ShapesDescriptor.Rect -> RectShapesId
            is ShapesDescriptor.Custom -> UUID.fromString(it.id)
        }
    }
    val shapes: Flow<List<Shapes>> = themeRepository.getAllShapes()

    fun getShapes(id: UUID): Flow<Shapes?> {
        return themeRepository.getShapes(id)
    }

    fun updateShapes(shapes: Shapes) {
        themeRepository.updateShapes(shapes)
    }

    fun selectShapes(shapes: Shapes) {
        uiSettings.setShapes(when(shapes.id) {
            DefaultThemeId -> ShapesDescriptor.Default
            else -> ShapesDescriptor.Custom(shapes.id.toString())
        })
    }

    fun duplicate(shapes: Shapes) {
        themeRepository.createShapes(shapes.copy(id = UUID.randomUUID()))
    }

    fun delete(shapes: Shapes) {
        themeRepository.deleteShapes(shapes)
    }

    fun createNew(context: Context) {
        themeRepository.createShapes(
            Shapes(
                id = UUID.randomUUID(),
                name = context.getString(R.string.new_shapes_name)
            )
        )
    }
}