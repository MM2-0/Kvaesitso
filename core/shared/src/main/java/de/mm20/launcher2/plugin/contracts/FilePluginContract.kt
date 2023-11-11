package de.mm20.launcher2.plugin.contracts

abstract class FilePluginContract {

    object FileColumns {
        /**
         * The unique ID of the file.
         * Type: String
         */
        const val Id = "id"

        /**
         * The display name of the file.
         * Type: String
         */
        const val DisplayName = "display_name"

        /**
         * The MIME type of the file. This is used to determine how to open the file. Make sure that
         * this is either a common MIME type or that your app can handle this MIME type.
         * Type: String?
         */
        const val MimeType = "mime_type"

        /**
         * The size of the file in bytes.
         * Type: Long?
         */
        const val Size = "size"

        /**
         * The display path of the file.
         * Type: String?
         */
        const val Path = "path"

        /**
         * The URI to view the file.
         * Type: String?
         */
        const val ContentUri = "uri"

        const val ThumbnailUri = "thumbnail_uri"

        /**
         * Whether the file is a directory.
         * Type: Int
         */
        const val IsDirectory = "is_directory"

        const val MetaTitle = "meta_title"
        const val MetaArtist = "meta_artist"
        const val MetaAlbum = "meta_album"
        const val MetaDuration = "meta_duration"
        const val MetaYear = "meta_year"
        const val MetaWidth = "meta_width"
        const val MetaHeight = "meta_height"
        const val MetaLocation = "meta_location"
        const val MetaAppName = "meta_app_name"
        const val MetaAppPackageName = "meta_app_package_name"
        const val MetaAppMinSdkVersion = "meta_app_min_sdk_version"
        const val MetaOwner = "meta_owner"
    }
}