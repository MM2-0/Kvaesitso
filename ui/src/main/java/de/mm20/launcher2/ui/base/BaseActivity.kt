package de.mm20.launcher2.ui.base

import android.os.Bundle
import android.view.Window
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.preferences.Settings
import de.mm20.launcher2.ui.R
import org.koin.android.ext.android.inject

abstract class BaseActivity : AppCompatActivity() {
    private val permissionsManager: PermissionsManager by inject()

    private val viewModel: BaseActivityVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val theme = viewModel.getTheme()
        when (theme) {
            Settings.AppearanceSettings.Theme.Light -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
            Settings.AppearanceSettings.Theme.Dark -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
            else -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
        viewModel.theme.observe(this) {
            if (it != theme && it != null) recreate()
        }

        val colorScheme = viewModel.getColorScheme()
        val colorSchemeThemeId = when (colorScheme) {
            Settings.AppearanceSettings.ColorScheme.BlackAndWhite -> R.style.BlackWhiteColors
            else -> R.style.DefaultColors
        }
        this.theme.applyStyle(colorSchemeThemeId, true)

        viewModel.colorScheme.observe(this) {
            if (it != colorScheme && it != null) recreate()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        permissionsManager.onResume()
    }
}