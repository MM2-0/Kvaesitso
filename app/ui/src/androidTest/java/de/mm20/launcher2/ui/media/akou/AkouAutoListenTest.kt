package de.mm20.launcher2.ui.media.akou

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AkouAutoListenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun optedInEntryWaitsForPreferencesAndNeverRepeatsDuringRecomposition() {
        val ready = mutableStateOf(false)
        val foreground = mutableStateOf(true)
        val attempted = mutableStateOf(false)
        var samples = 0
        compose.setContent {
            AkouAutoListenEffect(
                true,
                ready.value,
                foreground.value,
                attempted.value,
                { true },
                { attempted.value = true },
                { samples++ },
                { error("Unexpected denial") },
            )
        }
        compose.runOnIdle {
            assertEquals(0, samples)
            ready.value = true
        }
        compose.runOnIdle {
            assertEquals(1, samples)
            ready.value = false
        }
        compose.runOnIdle { ready.value = true }
        compose.runOnIdle {
            assertEquals(1, samples)
            foreground.value = false
            attempted.value = false
        }
        compose.runOnIdle { foreground.value = true }
        compose.runOnIdle { assertEquals(2, samples) }
    }

    @Test
    fun enablingAppliesOnNextEntryAndDenialDoesNotRecordOrRepeat() {
        val enabled = mutableStateOf(false)
        val foreground = mutableStateOf(true)
        val attempted = mutableStateOf(false)
        var samples = 0
        var denials = 0
        compose.setContent {
            AkouAutoListenEffect(
                enabled.value,
                true,
                foreground.value,
                attempted.value,
                { false },
                { attempted.value = true },
                { samples++ },
                { denials++ },
            )
        }
        compose.runOnIdle { enabled.value = true }
        compose.runOnIdle {
            assertEquals(0, denials)
            foreground.value = false
        }
        compose.runOnIdle { foreground.value = true }
        compose.runOnIdle {
            assertEquals(1, denials)
            assertEquals(0, samples)
        }
    }
}
