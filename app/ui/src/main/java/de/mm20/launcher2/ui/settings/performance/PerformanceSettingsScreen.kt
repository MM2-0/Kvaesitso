package de.mm20.launcher2.ui.settings.performance
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.settings.art.ArtOptimizerRoute
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data object PerformanceSettingsRoute : NavKey

@Composable
private fun SettingHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = androidx.compose.ui.Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun PerformanceSettingsScreen() {
    val backStack = LocalBackStack.current
    val viewModel: PerformanceSettingsScreenVM = viewModel()

    val reduceAnimations by viewModel.reduceAnimations.collectAsStateWithLifecycle()
    val animationSpeed by viewModel.animationSpeed.collectAsStateWithLifecycle()
    val searchDebounceMs by viewModel.searchDebounceMs.collectAsStateWithLifecycle()
    val iconCacheSize by viewModel.iconCacheSize.collectAsStateWithLifecycle()
    val bouncePhysics by viewModel.bouncePhysics.collectAsStateWithLifecycle()

    PreferenceScreen(title = stringResource(I18nR.string.preference_screen_performance)) {
        item {
            PreferenceCategory {
                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                var forcing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                Preference(
                    icon = BaseR.drawable.memory_24px,
                    title = stringResource(I18nR.string.self_tuning_engine_title),
                    summary = if (forcing) stringResource(I18nR.string.self_tuning_engine_busy) else stringResource(I18nR.string.self_tuning_engine_summary),
                    onClick = {
                        if (forcing) return@Preference
                        forcing = true
                        de.mm20.launcher2.ktx.TelosShellEngine.scope.launch(kotlinx.coroutines.NonCancellable) {
                            try {
                                val success = de.mm20.launcher2.ui.performance.TelosSelfOptimizer.forceOptimizeNow(context)
                                android.widget.Toast.makeText(
                                    context,
                                    if (success) context.getString(I18nR.string.self_tuning_engine_ok)
                                    else context.getString(I18nR.string.self_tuning_engine_fail),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                forcing = false
                            }
                        }
                    },
                )
                Preference(
                    icon = BaseR.drawable.bolt_24px,
                    title = stringResource(I18nR.string.art_optimizer_title),
                    summary = stringResource(I18nR.string.art_optimizer_summary),
                    onClick = { backStack.add(ArtOptimizerRoute) },
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_performance_category_animations)) {
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_performance_reduce_animations),
                    summary = stringResource(I18nR.string.preference_performance_reduce_animations_summary),
                    value = reduceAnimations == true,
                    onValueChanged = { viewModel.setReduceAnimations(it) },
                )
                AnimatedVisibility(reduceAnimations == false) {
                    androidx.compose.foundation.layout.Column {
                        SliderPreference(
                            title = stringResource(I18nR.string.preference_performance_animation_speed),
                            value = animationSpeed ?: 1f,
                            min = 0.5f,
                            max = 2f,
                            step = 0.25f,
                            onValueChanged = { viewModel.setAnimationSpeed(it) },
                            label = { Text("${it}x") }
                        )
                        SliderPreference(
                            title = stringResource(I18nR.string.preference_performance_bounce_physics),
                            value = (((1f - (bouncePhysics ?: 1f)) / 0.7f) * 100).roundToInt().coerceIn(0, 100),
                            min = 0,
                            max = 100,
                            step = 10,
                            onValueChanged = { viewModel.setBouncePhysics(1f - (it / 100f) * 0.7f) },
                            label = { Text("$it%") }
                        )
                    }
                }
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_performance_category_search)) {
                SliderPreference(
                    title = stringResource(I18nR.string.preference_performance_search_debounce),
                    value = searchDebounceMs ?: 0,
                    min = 0,
                    max = 500,
                    step = 50,
                    onValueChanged = { viewModel.setSearchDebounceMs(it) },
                    label = { Text(if (it == 0) stringResource(I18nR.string.preference_performance_debounce_off) else "${it}ms") }
                )
                SettingHint(stringResource(I18nR.string.preference_performance_search_debounce_summary))
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_performance_category_caching)) {
                SliderPreference(
                    title = stringResource(I18nR.string.preference_performance_icon_cache_size),
                    value = iconCacheSize ?: 200,
                    min = 50,
                    max = 500,
                    step = 50,
                    onValueChanged = { viewModel.setIconCacheSize(it) },
                )
                SettingHint(stringResource(I18nR.string.preference_performance_icon_cache_size_summary))
                SettingHint(stringResource(I18nR.string.preference_performance_background_note))
            }
        }
    }
}
