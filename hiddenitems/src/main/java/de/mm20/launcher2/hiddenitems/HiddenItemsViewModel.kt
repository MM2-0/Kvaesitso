package de.mm20.launcher2.hiddenitems

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class HiddenItemsViewModel(app: Application): AndroidViewModel(app) {
    val hiddenItemsKeys = HiddenItemsRepository.getInstance(app).hiddenItemsKeys

}