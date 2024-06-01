package de.mm20.launcher2.plugin.contracts

abstract class FilePluginContract {

    object FileColumns: Columns() {
        /**
         * The unique ID of the file.
         * Type: String
         */
        val Id = column<String>("id")

        /**
         * The display name of the file.
         * Type: String
         */
        val DisplayName = column<String>("display_name")

        /**
         * The MIME type of the file. This is used to determine how to open the file. Make sure that
         * this is either a common MIME type or that your app can handle this MIME type.
         * Type: String?
         */
        val MimeType = column<String>("mime_type")

        /**
         * The size of the file in bytes.
         * Type: Long?
         */
        val Size = column<Long>("size")

        /**
         * The display path of the file.
         * Type: String?
         */
        val Path = column<String>("path")

        /**
         * The URI to view the file.
         * Type: String?
         */
        val ContentUri = column<String>("uri")

        val ThumbnailUri = column<String>("thumbnail_uri")

        /**
         * Whether the file is a directory.
         * Type: Int
         */
        val IsDirectory = column<Boolean>("is_directory")

        val Owner = column<String>("owner")

        val MetaTitle = column<String>("meta_title")
        val MetaArtist = column<String>("meta_artist")
        val MetaAlbum = column<String>("meta_album")
        val MetaDuration = column<Long>("meta_duration")
        val MetaYear = column<Int>("meta_year")
        val MetaWidth = column<Int>("meta_width")
        val MetaHeight = column<Int>("meta_height")
        val MetaLocation = column<String>("meta_location")
        val MetaAppName = column<String>("meta_app_name")
        val MetaAppPackageName = column<String>("meta_app_package_name")
    }
}