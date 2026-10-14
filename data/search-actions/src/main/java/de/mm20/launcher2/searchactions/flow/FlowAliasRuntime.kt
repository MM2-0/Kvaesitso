package de.mm20.launcher2.searchactions.flow

import android.content.Context
import android.os.Process
import android.widget.Toast
import de.mm20.launcher2.applications.AppRepository
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.searchactions.R
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.actions.SearchActionIcon
import de.mm20.launcher2.searchable.SavableSearchableRepository
import de.mm20.launcher2.searchable.VisibilityLevel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FlowAliasRuntime(
    private val apps: AppRepository,
    private val saved: SavableSearchableRepository,
    private val ownPackage: String,
) {
    private val executor = FlowAliasExecutor()
    private fun eligibleApps() = combine(
        apps.findMany(),
        saved.getKeys(includeTypes = listOf("app"), minVisibility = VisibilityLevel.Hidden,
            maxVisibility = VisibilityLevel.Hidden, limit = Int.MAX_VALUE),
    ) { installed, hidden ->
        val hiddenKeys = hidden.toHashSet()
        installed.filter { it.componentName.packageName != ownPackage && target(it, hiddenKeys).eligible }
    }

    private fun target(app: Application, hidden: Set<String>) = FlowAliasTarget(
        key = app.key, label = app.labelOverride ?: app.label,
        personal = app.user == Process.myUserHandle(), hidden = app.key in hidden,
        suspended = app.isSuspended, privateProfile = app.isPrivate,
    )

    fun targets() = eligibleApps().map { eligible ->
        eligible.map { FlowAliasTarget(it.key, it.labelOverride ?: it.label) }.sortedBy { it.label }
    }

    suspend fun activate(context: Context, alias: FlowAlias): FlowAliasExecutor.Result = executor.activate(
        alias,
        resolve = { key -> eligibleApps().first().find { it.key == key }?.let { FlowAliasTarget(it.key, it.label) } },
        launch = { selected ->
            // Resolve again at dispatch; do not retain Application objects in persisted actions.
            val app = eligibleApps().first().find { it.key == selected.key }
            currentCoroutineContext().ensureActive()
            app?.launch(context, null) ?: false
        },
    )
}

class FlowAliasAction(
    val alias: FlowAlias,
    target: FlowAliasTarget,
    private val runtime: FlowAliasRuntime,
) : SearchAction {
    override val label = "${alias.alias} → ${target.label}"
    override val icon = SearchActionIcon.Search
    override val iconColor = 0
    override val customIcon: String? = null

    // Legacy synchronous activation has no owner for resolving work; fail closed.
    override fun start(context: Context) {
        Toast.makeText(context, R.string.flow_alias_tap_to_launch, Toast.LENGTH_SHORT).show()
    }

    override suspend fun activate(context: Context) {
        when (runtime.activate(context, alias)) {
            FlowAliasExecutor.Result.Unavailable -> Toast.makeText(context, R.string.flow_alias_unavailable, Toast.LENGTH_SHORT).show()
            FlowAliasExecutor.Result.Failed -> Toast.makeText(context, R.string.flow_alias_launch_failed, Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }
}
