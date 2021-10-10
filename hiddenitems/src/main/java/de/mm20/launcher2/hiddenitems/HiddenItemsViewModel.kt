package de.mm20.launcher2.hiddenitems

import androidx.lifecycle.ViewModel

class HiddenItemsViewModel(
    hiddenItemsRepository: HiddenItemsRepository
): ViewModel() {
    val hiddenItemsKeys = hiddenItemsRepository.hiddenItemsKeys

}