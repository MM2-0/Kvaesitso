package de.mm20.launcher2.ui.legacy.component

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import de.mm20.launcher2.files.FilesViewModel
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.File
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.search.SearchListView

class FileView : FrameLayout {
    private val files: LiveData<List<File>?>

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleRes: Int) : super(context, attrs, defStyleRes)

    init {
        View.inflate(context, R.layout.view_search_category_list, this)
        layoutTransition = LayoutTransition()
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val card = findViewById<ViewGroup>(R.id.card)
        card.layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
        val list = findViewById<SearchListView>(R.id.list)
        files = ViewModelProvider(context as AppCompatActivity).get(FilesViewModel::class.java).files
        files.observe(context as AppCompatActivity, {
            if (it == null) {
                visibility = View.GONE
                return@observe
            }
            if (it.isEmpty() && !PermissionsManager.checkPermission(context, PermissionsManager.EXTERNAL_STORAGE)) {
                visibility = View.VISIBLE
                list.submitItems(listOf(
                        MissingPermission(
                                context.getString(R.string.permission_files_search),
                                PermissionsManager.EXTERNAL_STORAGE
                        )
                ))
                return@observe
            }
            visibility = if (it.isEmpty()) View.GONE else View.VISIBLE
            list.submitItems(it)
        })
    }
}
