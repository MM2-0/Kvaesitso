package de.mm20.launcher2.search

import android.content.Context

interface Website: SavableSearchable {

    val url: String
    val description: String?
    val imageUrl: String?
    val faviconUrl: String?
    val color: Int?

    override val preferDetailsOverLaunch: Boolean
        get() = false

    val canShare: Boolean
    fun share(context: Context) {}
}