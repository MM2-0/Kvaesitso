package de.mm20.launcher2.search.data

import android.content.Intent
import android.net.Uri
import de.mm20.launcher2.database.entities.WebsearchEntity

class Websearch(
        var urlTemplate: String,
        var label: String,
        var color: Int,
        var icon: String?,
        var id: Long? = null,
        val query: String? = null
) {

    constructor(entity: WebsearchEntity, query: String? = null) : this(
            urlTemplate = entity.urlTemplate,
            label = entity.label,
            icon = entity.icon,
            color = entity.color,
            id = entity.id,
            query = query
    )

    fun toDatabaseEntity(): WebsearchEntity {
        return WebsearchEntity(
                urlTemplate = urlTemplate,
                color = color,
                icon = icon,
                label = label,
                id = id
        )
    }

    fun getLaunchIntent(): Intent? {
        if(query == null) return null
        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val url = urlTemplate.replace("\${1}", Uri.encode(query))
        intent.data = Uri.parse(url)
        return intent
    }
}