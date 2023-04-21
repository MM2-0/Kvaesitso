package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.getSerialNumber
import de.mm20.launcher2.search.data.LauncherApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * A fake implementation of [AppRepository] to simulate many installed apps.
 */
class FakeAppRepository(private val context: Context, private val fakePackages: Int) : AppRepository {


    private val fakeApp: LauncherApp

    init {
        val launcherApps = context.getSystemService<LauncherApps>()!!
        fakeApp = LauncherApp(
            context,
            launcherApps.resolveActivity(
                Intent().apply {
                    component = ComponentName(
                        context.packageName,
                        "de.mm20.launcher2.ui.launcher.LauncherActivity"
                    )
                },
                Process.myUserHandle()
            ),
        )
    }

    private fun randomString(): String {
        val charset = "abcdefghijklmnopqrstuvwxyz"
        return (1..10)
            .map { charset.random() }
            .joinToString("")
    }

    override fun getAllInstalledApps(): Flow<List<LauncherApp>> {
        return flowOf(buildList {
            repeat(fakePackages) {
                add(fakeApp.copy(`package` = randomString(), activity = randomString()))
            }
        })
    }

    override fun getSuspendedPackages(): Flow<List<String>> {
        return flowOf(emptyList())
    }

    override fun search(query: String): Flow<ImmutableList<LauncherApp>> {
        return if (query.isEmpty()) {
            getAllInstalledApps().map { it.toImmutableList() }
        } else {
            flowOf(persistentListOf())
        }
    }
}