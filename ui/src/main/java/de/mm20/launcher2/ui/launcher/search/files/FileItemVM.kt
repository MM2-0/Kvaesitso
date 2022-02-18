package de.mm20.launcher2.ui.launcher.search.files

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.mm20.launcher2.files.FileRepository
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.LocalFile
import de.mm20.launcher2.ui.launcher.search.common.SearchableItemVM
import org.koin.core.component.inject

class FileItemVM(
    private val file: File
) : SearchableItemVM(file) {
    private val fileRepository: FileRepository by inject()

    val canShare = file is LocalFile
    val canDelete = file.isDeletable

    fun delete() {
        fileRepository.deleteFile(file)
    }

    fun share(context: Context) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".fileprovider",
            java.io.File(file.path)
        )
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        shareIntent.type = file.mimeType
        context.startActivity(Intent.createChooser(shareIntent, null))
    }
}