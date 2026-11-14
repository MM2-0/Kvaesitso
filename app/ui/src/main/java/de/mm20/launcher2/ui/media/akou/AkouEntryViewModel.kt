package de.mm20.launcher2.ui.media.akou

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Retain across rotation, but do not restore an old attempt after process death. */
internal class AkouEntryViewModel : ViewModel() {
    var autoAttempted by mutableStateOf(false)
}
