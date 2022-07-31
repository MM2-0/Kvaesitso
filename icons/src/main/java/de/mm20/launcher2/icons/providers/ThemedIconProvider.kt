package de.mm20.launcher2.icons.providers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import androidx.core.content.res.ResourcesCompat
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.database.AppDatabase
import de.mm20.launcher2.icons.*
import de.mm20.launcher2.ktx.obtainTypedArrayOrNull
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.Searchable

internal class ThemedIconProvider(
    private val iconPackManager: IconPackManager,
) : IconProvider {

    override suspend fun getIcon(searchable: Searchable, size: Int): LauncherIcon? {
        if (searchable !is Application) return null
        return iconPackManager.getThemedIcon(searchable.`package`)
    }
}