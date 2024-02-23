package de.mm20.launcher2.applications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import androidx.core.content.getSystemService
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SearchableRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * App repository that returns a fixed number of fake apps to simulate a large number of apps.
 */
class FakeAppRepository(private val context: Context, private val fakePackages: Int) : SearchableRepository<Application> {


    override fun search(query: String, allowNetwork: Boolean): Flow<ImmutableList<Application>> {
        return if (query.isEmpty()) {
            buildList {
                repeat(fakePackages) {
                    add(FakeApp())
                }
            }.toImmutableList().let { flowOf(it) }
        } else {
            flowOf(persistentListOf())
        }
    }
}