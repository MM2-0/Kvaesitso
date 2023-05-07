package de.mm20.launcher2.sdk.files

import android.content.ContentProvider
import android.database.Cursor
import android.net.Uri

abstract class FileProvider: ContentProvider() {
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }
}