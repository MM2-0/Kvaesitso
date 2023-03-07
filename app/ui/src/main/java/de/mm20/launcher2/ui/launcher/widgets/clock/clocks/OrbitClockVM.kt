package de.mm20.launcher2.ui.launcher.widgets.clock.clocks

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent

class OrbitClockVM : ViewModel(), KoinComponent {
    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                time.value = System.currentTimeMillis()
            }
        }
    }

    val time = mutableStateOf(System.currentTimeMillis())
}