package de.mm20.launcher2.ui.settings.websearch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import de.mm20.launcher2.search.WebsearchRepository
import de.mm20.launcher2.search.data.Websearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream

class WebSearchSettingsScreenVM: ViewModel(), KoinComponent {
    private val repository: WebsearchRepository by inject()

    val websearches = repository.getWebsearches().asLiveData()

    fun createWebsearch(websearch: Websearch) {
        repository.insertWebsearch(websearch)
    }

    fun updateWebsearch(websearch: Websearch) {
        repository.insertWebsearch(websearch)
    }

    fun deleteWebsearch(websearch: Websearch) {
        websearch.icon?.let { deleteIcon(it) }
        repository.deleteWebsearch(websearch)
    }


    /**
     * Read a user-selected icon, scale it down and copy it to the app's data dir
     * @return the absolute path of the copied file
     */
    suspend fun createIcon(context: Context, uri: Uri, size: Int): String? = withContext(
        Dispatchers.IO) {
        val file = File(context.dataDir, System.currentTimeMillis().toString())
        val imageRequest = ImageRequest.Builder(context)
            .data(uri)
            .size(size)
            .scale(Scale.FIT)
            .build()
        val drawable = context.imageLoader.execute(imageRequest).drawable ?: return@withContext null
        val scaledIcon = drawable.toBitmap()
        val out = FileOutputStream(file)
        scaledIcon.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.close()
        return@withContext file.absolutePath
    }


    fun deleteIcon(path: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(path).delete()
            }
        }
    }
}