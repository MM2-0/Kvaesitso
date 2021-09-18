package de.mm20.launcher2.ui.icons

import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
        MaterialTheme.colors.androidGreen,
        Icons.Rounded.Android
    )
}

@Composable
fun File.getPlaceholderIcon(): PlaceholderIcon {
    return when {
        isDirectory -> PlaceholderIcon(
            MaterialTheme.colors.lightBlue,
            Icons.Rounded.Folder
        )
        mimeType.startsWith("image/") -> PlaceholderIcon(
            MaterialTheme.colors.teal,
            Icons.Rounded.Image
        )
        mimeType.startsWith("audio/") -> PlaceholderIcon(
            MaterialTheme.colors.orange,
            Icons.Rounded.Audiotrack
        )
        mimeType.startsWith("video/") -> PlaceholderIcon(
            MaterialTheme.colors.purple,
            Icons.Rounded.Movie
        )
        /*
        else -> when (mimeType) {





            "application/vnd.google-apps.drawing" -> R.drawable.ic_file_picture to R.color.teal
        }*/
        else -> when (mimeType) {
            "application/pdf" -> PlaceholderIcon(
                MaterialTheme.colors.red,
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
                MaterialTheme.colors.brown,
                Icons.Rounded.Archive
            )
            "application/vnd.oasis.opendocument.text",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "application/x-iwork-pages-sffpages",
            "application/vnd.apple.pages",
            "application/vnd.google-apps.document" -> PlaceholderIcon(
                MaterialTheme.colors.blue,
                Icons.Rounded.Notes
            )
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/x-iwork-numbers-sffnumbers",
            "application/vnd.apple.numbers",
            "application/vnd.google-apps.spreadsheet" -> PlaceholderIcon(
                MaterialTheme.colors.lightGreen,
                Icons.Rounded.BorderAll
            )
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/x-iwork-keynote-sffkey",
            "application/vnd.apple.keynote",
            "application/vnd.google-apps.presentation" -> PlaceholderIcon(
                MaterialTheme.colors.amber,
                Icons.Rounded.Slideshow
            )
            "application/vnd.android.package-archive" -> PlaceholderIcon(
                MaterialTheme.colors.androidGreen,
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
                MaterialTheme.colors.pink,
                Icons.Rounded.Code
            )
            "text/xml",
            "text/html" -> PlaceholderIcon(
                MaterialTheme.colors.deepOrange,
                Icons.Rounded.Code
            )
            "application/vnd.google-apps.form" -> PlaceholderIcon(
                MaterialTheme.colors.deepPurple,
                Icons.Rounded.ViewList
            )
            "application/epub+zip" -> PlaceholderIcon(
                MaterialTheme.colors.blue,
                Icons.Rounded.Book
            )
            else -> PlaceholderIcon(
                MaterialTheme.colors.blueGray,
                Icons.Rounded.InsertDriveFile
            )
        }
    }
}