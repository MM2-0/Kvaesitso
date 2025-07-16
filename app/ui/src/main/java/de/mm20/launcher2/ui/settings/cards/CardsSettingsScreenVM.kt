package de.mm20.launcher2.ui.settings.cards

import androidx.lifecycle.ViewModel
import de.mm20.launcher2.preferences.ui.UiSettings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CardsSettingsScreenVM : ViewModel(), KoinComponent {
    private val uiSettings: UiSettings by inject()

    val cardStyle = uiSettings.cardStyle

    fun setOpacity(opacity: Float) {
        uiSettings.setCardOpacity(opacity)
    }

    fun setBorderWidth(borderWidth: Int) {
        uiSettings.setCardBorderWidth(borderWidth)
    }

}