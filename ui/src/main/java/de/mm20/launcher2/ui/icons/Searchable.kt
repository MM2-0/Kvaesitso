package de.mm20.launcher2.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import de.mm20.launcher2.search.data.Application
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.*

data class PlaceholderIcon(
    val color: Color,
    val icon: ImageVector
)

@Composable
fun Searchable.getPlaceholderIcon(): PlaceholderIcon {
    return when (this) {
        is Application -> getPlaceholderIcon()
        is File -> getPlaceholderIcon()
        else -> PlaceholderIcon(
            Color.LightGray,
            Icons.Rounded.Circle
        )
    }
}

@Composable
fun Application.getPlaceholderIcon(): PlaceholderIcon {
    return PlaceholderIcon(
        colorResource(id = R.color.android_green),
        Icons.Rounded.Android
    )
}

@Composable
fun File.getPlaceholderIcon(): PlaceholderIcon {
    return when {
        isDirectory -> PlaceholderIcon(
            colorResource(id = R.color.lightblue),
            Icons.Rounded.Folder
        )
        mimeType.startsWith("image/") -> PlaceholderIcon(
            colorResource(id = R.color.teal),
            Icons.Rounded.Image
        )
        mimeType.startsWith("audio/") -> PlaceholderIcon(
            colorResource(id = R.color.orange),
            Icons.Rounded.Audiotrack
        )
        mimeType.startsWith("video/") -> PlaceholderIcon(
            colorResource(id = R.color.purple),
            Icons.Rounded.Movie
        )
        /*
        else -> when (mimeType) {





            "application/vnd.google-apps.drawing" -> R.drawable.ic_file_picture to R.color.teal
        }*/
        else -> when (mimeType) {
            "application/pdf" -> PlaceholderIcon(
                colorResource(id = R.color.red),
                Icons.Rounded.Pdf
            )
            "application/zip",
            "application/x-gtar",
            "application/x-tar",
            "application/java-archive",
            "application/x-7z-compressed",
            "application/x-compressed-tar",
            "application/x-zip-compressed",
            "application/x-gzip",
            "application/x-bzip2" -> PlaceholderIcon(
                colorResource(id = R.color.brown),
                Icons.Rounded.Archive
            )
            "application/vnd.oasis.opendocument.text",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "application/x-iwork-pages-sffpages",
            "application/vnd.apple.pages",
            "application/vnd.google-apps.document" -> PlaceholderIcon(
                colorResource(id = R.color.blue),
                Icons.Rounded.Notes
            )
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/x-iwork-numbers-sffnumbers",
            "application/vnd.apple.numbers",
            "application/vnd.google-apps.spreadsheet" -> PlaceholderIcon(
                colorResource(id = R.color.lightgreen),
                Icons.Rounded.BorderAll
            )
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/x-iwork-keynote-sffkey",
            "application/vnd.apple.keynote",
            "application/vnd.google-apps.presentation" -> PlaceholderIcon(
                colorResource(id = R.color.amber),
                Icons.Rounded.Slideshow
            )
            "application/vnd.android.package-archive" -> PlaceholderIcon(
                colorResource(id = R.color.android_green),
                Icons.Rounded.Android
            )
            "text/x-asm",
            "text/x-c",
            "text/x-java-source",
            "text/x-script.phyton",
            "text/x-pascal",
            "text/x-script.perl",
            "text/javascript",
            "application/json" -> PlaceholderIcon(
                colorResource(id = R.color.pink),
                Icons.Rounded.Code
            )
            "text/xml",
            "text/html" -> PlaceholderIcon(
                colorResource(id = R.color.deeporange),
                Icons.Rounded.Code
            )
            "application/vnd.google-apps.form" -> PlaceholderIcon(
                colorResource(id = R.color.deeppurple),
                Icons.Rounded.ViewList
            )
            "application/epub+zip" -> PlaceholderIcon(
                colorResource(id = R.color.blue),
                Icons.Rounded.Book
            )
            else -> PlaceholderIcon(
                colorResource(id = R.color.bluegrey),
                Icons.Rounded.InsertDriveFile
            )
        }
    }
}