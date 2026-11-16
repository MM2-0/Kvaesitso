package de.mm20.launcher2.ui.settings.art

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.mm20.launcher2.ui.component.SharedAppFilter
import de.mm20.launcher2.ui.component.TelosSearchAndFilterBar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import de.mm20.launcher2.i18n.R as I18nR

/** Reproduce lazy composition of the below-the-fold filters, without running ART commands. */
class ArtOptimizerScrollTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.let {
        it.createConfigurationContext(Configuration(it.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("el"))
        })
    }

    @Test fun artFiltersRemainReachableAfterScrollingDown() {
        val selected = mutableStateOf(ArtAppFilter.ALL)
        val query = mutableStateOf("")
        val labels = listOf(
            I18nR.string.art_optimizer_filter_all,
            I18nR.string.art_optimizer_filter_needed,
            I18nR.string.art_optimizer_filter_optimized,
            I18nR.string.art_optimizer_filter_degraded,
            I18nR.string.art_optimizer_filter_frozen,
        ).map(context::getString)
        scrollToAndExerciseFilters(labels) { modifier ->
            ArtOptimizerAppSearch(query.value, { query.value = it }, selected.value, { selected.value = it }, modifier)
        }
        compose.runOnIdle {
            assertEquals(ArtAppFilter.FROZEN, selected.value)
            assertEquals("com.example", query.value)
        }
    }

    @Test fun sharedAppFiltersUseTheSameSingleScrollingOwner() {
        val selected = mutableStateOf(SharedAppFilter.ALL)
        val query = mutableStateOf("")
        scrollToAndExerciseFilters(SharedAppFilter.entries.map { context.getString(it.labelRes) }) { modifier ->
            TelosSearchAndFilterBar(query.value, { query.value = it }, selected.value, { selected.value = it }, modifier)
        }
        compose.runOnIdle {
            assertEquals(SharedAppFilter.NEEDS_ATTENTION, selected.value)
            assertEquals("com.example", query.value)
        }
    }

    private fun scrollToAndExerciseFilters(labels: List<String>, header: @Composable (Modifier) -> Unit) {
        val scale = mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalDensity provides Density(density.density, scale.floatValue),
            ) {
                MaterialTheme(colorScheme = if (scale.floatValue == 1f) lightColorScheme() else darkColorScheme()) {
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.size(320.dp, 480.dp).testTag("art-list")) {
                            items(20) { Spacer(Modifier.height(64.dp)) }
                            item(key = "per_app_header") { header(Modifier.testTag("art-search")) }
                            item { Spacer(Modifier.height(320.dp)) }
                        }
                    }
                }
            }
        }
        for (fontScale in listOf(1f, 2f)) {
            compose.runOnIdle { scale.floatValue = fontScale }
            compose.onNodeWithTag("art-list").performScrollToIndex(0)
            // Measuring this item on 1.44.19 throws the reported infinite-width exception.
            compose.onNodeWithTag("art-list").performScrollToNode(hasTestTag("art-search"))
            compose.onNodeWithTag("art-search").assertIsDisplayed()
            compose.onNodeWithContentDescription(context.getString(de.mm20.launcher2.i18n.R.string.art_optimizer_search_apps_hint))
                .performClick()
            for (label in labels) {
                compose.onNodeWithContentDescription(label).performScrollTo().performClick().assertIsSelected()
            }
            compose.onNode(hasSetTextAction()).performTextReplacement("com.example")
        }
    }
}
