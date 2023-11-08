package de.mm20.launcher2.sdk.files

import android.net.Uri
import de.mm20.launcher2.plugin.config.StorageStrategy

data class File(
    /**
     * A unique and stable identifier for this file.
     */
    val id: String,

    /**
     * The URI to this file. To open this file, an intent with this URI as data and ACTION_VIEW as action
     * is used.
     */
    val uri: Uri,

    /**
     * The display name of this file.
     */
    val displayName: String,

    /**
     * The MIME type that is shown to the user and that is used to determine the icon.
     */
    val mimeType: String,

    /**
     * The size of this file in bytes.
     */
    val size: Long,
    /**
     * A path to this file. This is shown to the user purely for informational purposes.
     * It is not used to open the file.
     */
    val path: String,
    /**
     * Whether this file is a directory. If set, a folder icon will be shown instead of a file icon.
     */
    val isDirectory: Boolean,
    /**
     * An URI to a thumbnail of this file. This is used to show a preview of the file.
     * Supported schemes:
     *  - content
     *  - file
     *  - android.resource
     *  - http
     *  - https
     *
     *  If null, a default icon will be shown, depending on the file type.
     */
    val thumbnailUri: Uri? = null,
)