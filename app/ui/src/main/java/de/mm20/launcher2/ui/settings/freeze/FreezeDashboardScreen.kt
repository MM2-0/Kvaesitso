package de.mm20.launcher2.ui.settings.freeze
import de.mm20.launcher2.i18n.R as I18nR


import android.text.format.DateUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.freeze.AppFreezeState
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.component.ShapedLauncherIcon
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import kotlinx.serialization.Serializable

@Serializable
data object FreezeDashboardRoute : NavKey

@Composable
fun FreezeDashboardScreen() {
    val viewModel: FreezeDashboardScreenVM = viewModel()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val totalFreezes by viewModel.totalFreezes.collectAsStateWithLifecycle()
    val totalUnfreezes by viewModel.totalUnfreezes.collectAsStateWithLifecycle()

    val hasUsageAccess by viewModel.hasUsageAccess.collectAsStateWithLifecycle()
    val selectedState by viewModel.selectedState.collectAsStateWithLifecycle()
    val runtimeRows by viewModel.runtimeRows.collectAsStateWithLifecycle()

    // Runtime/currently-running data goes stale the moment this screen isn't looking at it -
    // refresh whenever it (re)appears, rather than polling continuously in the background.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    val frozen = rows.filter { it.isFrozen }

    PreferenceScreen(
        title = stringResource(I18nR.string.preference_freeze_dashboard),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.au3_commsfreeze_overview)) {
                Preference(
                    title = stringResource(I18nR.string.freeze_dashboard_counts, totalFreezes, totalUnfreezes),
                    summary = stringResource(I18nR.string.preference_freeze_dashboard_summary),
                )
            }
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.freeze_dashboard_currently_frozen)) {
                if (frozen.isEmpty()) {
                    Text(
                        text = stringResource(I18nR.string.freeze_dashboard_none_frozen),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(frozen, key = { _, it -> "frozen_" + it.app.key }) { _, row ->
            val icon by viewModel.getIcon(row.app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            Preference(
                title = row.app.label,
                icon = { ShapedLauncherIcon(size = 32.dp, icon = { icon }) },
                summary = "❄ " + stringResource(I18nR.string.freeze_dashboard_frozen_badge),
            )
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.freeze_dashboard_history)) {
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(I18nR.string.freeze_dashboard_no_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(rows, key = { _, it -> "history_" + it.app.key }) { _, row ->
            val icon by viewModel.getIcon(row.app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
            val lastFrozenAt = row.lastFrozenAt ?: 0L
            val lastUnfrozenAt = row.lastUnfrozenAt ?: 0L
            val lastAt = maxOf(lastFrozenAt, lastUnfrozenAt)
            val countsLine = stringResource(I18nR.string.freeze_dashboard_counts, row.freezeCount, row.unfreezeCount)
            val summary = if (lastAt > 0L) {
                val relativeTime = DateUtils.getRelativeTimeSpanString(lastAt).toString()
                val lastActionLine = if (lastFrozenAt >= lastUnfrozenAt) {
                    stringResource(I18nR.string.freeze_dashboard_last_frozen, relativeTime)
                } else {
                    stringResource(I18nR.string.freeze_dashboard_last_unfrozen, relativeTime)
                }
                "$countsLine\n$lastActionLine"
            } else {
                countsLine
            }
            Preference(
                title = row.app.label,
                icon = { ShapedLauncherIcon(size = 32.dp, icon = { icon }) },
                summary = summary,
            )
        }
        item {
            PreferenceCategory(title = stringResource(I18nR.string.freeze_dashboard_app_status)) {
                if (!hasUsageAccess) {
                    MissingPermissionBanner(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        text = stringResource(I18nR.string.preference_freeze_usage_access_summary_not_granted),
                        onClick = {
                            (context as? AppCompatActivity)?.let { viewModel.requestUsageAccess(it) }
                        }
                    )
                } else {
                    Text(
                        text = stringResource(I18nR.string.freeze_dashboard_app_status_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    de.mm20.launcher2.ui.component.TelosCommandHud(
                        actions = listOf(
                            AppFreezeState.Normal,
                            AppFreezeState.Suspended,
                            AppFreezeState.Disabled,
                            AppFreezeState.Hidden,
                        ).map { state ->
                            val count = runtimeRows.count { it.state == state }
                            de.mm20.launcher2.ui.component.TelosHudAction(
                                id = state.name,
                                icon = when (state) {
                                    AppFreezeState.Normal -> de.mm20.launcher2.base.R.drawable.apps_24px
                                    AppFreezeState.Suspended -> de.mm20.launcher2.base.R.drawable.pause_24px
                                    AppFreezeState.Disabled -> de.mm20.launcher2.base.R.drawable.error_24px
                                    AppFreezeState.Hidden -> de.mm20.launcher2.base.R.drawable.visibility_off_24px
                                },
                                label = "${stateLabel(state)} ($count)",
                                selected = state == selectedState,
                                badge = count.takeIf { it > 0 }?.toString(),
                                onClick = { viewModel.selectState(state) },
                            )
                        } + de.mm20.launcher2.ui.component.TelosHudAction(
                            id = "refresh",
                            icon = de.mm20.launcher2.base.R.drawable.restart_alt_24px,
                            label = stringResource(I18nR.string.freeze_dashboard_refresh),
                            onClick = { viewModel.refresh() },
                        ),
                    )
                }
            }
        }
        if (hasUsageAccess) {
            val filteredRuntimeRows = runtimeRows.filter { it.state == selectedState }
            if (filteredRuntimeRows.isEmpty()) {
                item {
                    Text(
                        text = stringResource(I18nR.string.freeze_dashboard_no_apps_in_category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            itemsIndexed(
                filteredRuntimeRows,
                key = { _, it -> "runtime_" + it.app.key },
            ) { _, row ->
                val icon by viewModel.getIcon(row.app, 32.dp.value.toInt()).collectAsStateWithLifecycle(null)
                val runtimeText = if (row.totalTimeMs <= 0L) {
                    stringResource(I18nR.string.freeze_dashboard_not_used_today)
                } else {
                    stringResource(I18nR.string.freeze_dashboard_runtime_today, formatDuration(row.totalTimeMs))
                }
                val summary = if (row.isRunning) {
                    "$runtimeText • " + stringResource(I18nR.string.freeze_dashboard_running_now)
                } else {
                    runtimeText
                }
                Preference(
                    title = row.app.label,
                    icon = { ShapedLauncherIcon(size = 32.dp, icon = { icon }) },
                    summary = summary,
                )
            }
        }
    }
}

@Composable
private fun stateLabel(state: AppFreezeState): String = when (state) {
    AppFreezeState.Normal -> stringResource(I18nR.string.freeze_dashboard_state_normal)
    AppFreezeState.Suspended -> stringResource(I18nR.string.freeze_dashboard_state_suspended)
    AppFreezeState.Disabled -> stringResource(I18nR.string.freeze_dashboard_state_disabled)
    AppFreezeState.Hidden -> stringResource(I18nR.string.freeze_dashboard_state_hidden)
}

@Composable
private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> stringResource(I18nR.string.freeze_dashboard_runtime_hours_minutes, hours, minutes)
        totalMinutes > 0 -> stringResource(I18nR.string.freeze_dashboard_runtime_minutes, totalMinutes)
        else -> stringResource(I18nR.string.freeze_dashboard_runtime_under_a_minute)
    }
}
