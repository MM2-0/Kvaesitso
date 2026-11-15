package de.mm20.launcher2.ui.freeze

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.ui.settings.freeze.AutoFreezeImportDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AutoFreezeImportDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun importAndSkipRequireTheCorrespondingExplicitChoice() {
        var imports = 0
        var skips = 0
        compose.setContent {
            MaterialTheme { AutoFreezeImportDialog(3, { imports++ }, { skips++ }, {}) }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnIdle {
            assertEquals(0, imports)
            assertEquals(0, skips)
        }
        compose.onNodeWithText(context.getString(de.mm20.launcher2.i18n.R.string.skip)).performClick()
        compose.runOnIdle {
            assertEquals(0, imports)
            assertEquals(1, skips)
        }
        compose.onNodeWithText(context.getString(de.mm20.launcher2.i18n.R.string.action_import)).performClick()
        compose.runOnIdle {
            assertEquals(1, imports)
            assertEquals(1, skips)
        }
    }
}
