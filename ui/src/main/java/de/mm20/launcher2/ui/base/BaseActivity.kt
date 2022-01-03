package de.mm20.launcher2.ui.base

import androidx.appcompat.app.AppCompatActivity
import de.mm20.launcher2.permissions.PermissionsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

abstract class BaseActivity : AppCompatActivity(), KoinComponent {
    private val permissionsManager: PermissionsManager by inject()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}