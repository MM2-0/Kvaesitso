package de.mm20.launcher2.unitconverter.converters

import android.content.Context
import de.mm20.launcher2.unitconverter.Dimension

class TemperatureConverter(context: Context): SimpleFactorConverter() {
    override val dimension = Dimension.Temperature
}