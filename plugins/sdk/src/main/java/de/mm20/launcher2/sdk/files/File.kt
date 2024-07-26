package de.mm20.launcher2.sdk.files

import android.net.Uri

data class FileDimensions(
    val width: Int,
    val height: Int,
)

data class FileMetadata(
    /**
     * Song title, for audio files.
     */
    val title: String? = null,
    /**
     * Artist name, for audio files.
     */
    val artist: String? = null,
    /**
     * Album name, for audio files.
     */
    val album: String? = null,
    /**
     * Duration in milliseconds, for audio and video files.
     */
    val duration: Long? = null,
    /**
     * Year, for media files.
     */
    val year: Int? = null,
    /**
     * Dimensions in pixels, for image and video files.
     */
    val dimensions: FileDimensions? = null,
    /**
     * Geo location, for image and video files.
     */
    val location: String? = null,
    /**
     * App name, for APK files.
     */
    val appName: String? = null,
    /**
     * App package name, for APK files.
     */
    val appPackageName: String? = null,
)

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
    val path: String?,
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

    /**
     * Name of the owner of this file (i.e. when this file was shared with the user by another person).
     */
    val owner: String? = null,

    /**
     * Additional metadata for this file.
     */
    val metadata: FileMetadata = FileMetadata(),
)