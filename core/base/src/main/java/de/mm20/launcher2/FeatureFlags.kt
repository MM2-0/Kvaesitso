package de.mm20.launcher2

import de.mm20.launcher2.base.BuildConfig

object FeatureFlags {
    val feed = BuildConfig.BUILD_TYPE != "release"
    val smartspacerIntegration = BuildConfig.BUILD_TYPE != "release"
}