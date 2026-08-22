package de.mm20.launcher2.plugin.contracts

object IconRendererPluginContract {
    object Methods {
        const val RenderIcon = "renderIcon"
    }

    object Extras {
        const val PackageName = "packageName"
        const val ActivityName = "activityName"
        const val Size = "size"
        const val Png = "png"
    }
}
