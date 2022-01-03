package de.mm20.launcher2.ui.legacy.search

import android.app.Activity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.Scene
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.InnerCardView
import de.mm20.launcher2.ui.legacy.view.LauncherIconView
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class PermissionListRepresentation : Representation, KoinComponent {
    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val missingPermission = searchable as MissingPermission
        val context = rootView.context
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_permission_list, rootView.context)
        scene.setEnterAction {
            val permissionsManager: PermissionsManager = get()
            rootView.findViewById<TextView>(R.id.permissionText).text = missingPermission.label
            rootView.findViewById<LauncherIconView>(R.id.permissionIcon).icon = missingPermission.getPlaceholderIcon(context)
            rootView.findViewById<InnerCardView>(R.id.card).setOnClickListener {
                permissionsManager.requestPermission(context as AppCompatActivity, missingPermission.permissionGroup)
            }
        }
        return scene
    }
}