package de.mm20.launcher2.provider.files

import android.content.ContentUris
import android.net.Uri

object FilePluginContract {
    object Roots {
        const val Id = "id"
        const val DisplayName = "display_name"
        const val Description = "description"
        const val Status = "status"
        const val AccountAuthority = "account_authority"

        const val PathSegment = "roots"

        fun getContentUri(authority: String): Uri {
            return Uri.Builder()
                .scheme("content")
                .authority(authority)
                .path(PathSegment)
                .build()
        }
    }

    object Files {
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
         * The MIME type of the file.
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