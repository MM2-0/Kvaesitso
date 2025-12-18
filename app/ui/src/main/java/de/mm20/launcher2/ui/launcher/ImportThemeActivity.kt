package de.mm20.launcher2.ui.launcher

import android.os.Bundle
import androidx.activity.compose.setContent
import de.mm20.launcher2.ui.base.BaseActivity
import de.mm20.launcher2.ui.base.ProvideSettings
import de.mm20.launcher2.ui.common.ImportThemeSheet
import de.mm20.launcher2.ui.overlays.OverlayHost
import de.mm20.launcher2.ui.settings.appearance.ImportThemeSettingsScreen
import de.mm20.launcher2.ui.theme.LauncherTheme

class ImportThemeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data ?: return finish()

        setContent {
            LauncherTheme {
                ProvideSettings {
                    OverlayHost {
                        ImportThemeSettingsScreen(uri)
                    }
                }
            }
        }
    }
}