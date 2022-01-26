package de.mm20.launcher2.ui.legacy.search

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.ComposeView
import androidx.transition.Scene
import de.mm20.launcher2.permissions.PermissionsManager
import de.mm20.launcher2.search.data.MissingPermission
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.LegacyLauncherTheme
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.MissingPermissionBanner
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class PermissionListRepresentation : Representation, KoinComponent {
    override fun getScene(
        rootView: SearchableView,
        searchable: Searchable,
        previousRepresentation: Int?
    ): Scene {
        val missingPermission = searchable as MissingPermission
        val context = rootView.context
        val scene =
            Scene.getSceneForLayout(rootView, R.layout.view_permission_list, rootView.context)
        scene.setEnterAction {
            val permissionsManager: PermissionsManager = get()
            rootView.findViewById<ComposeView>(R.id.composeView).setContent {
                LegacyLauncherTheme {
                    MissingPermissionBanner(
                        text = missingPermission.label,
                        onClick = {
                            permissionsManager.requestPermission(
                                context as AppCompatActivity,
                                missingPermission.permissionGroup
                            )
                        },
                        secondaryAction = {
                            val secondaryAction = missingPermission.secondaryAction
                            val secondaryActionLabel = missingPermission.secondaryActionLabel
                            if (secondaryAction != null && secondaryActionLabel != null)
                            TextButton(onClick = secondaryAction) {
                                Text(text = secondaryActionLabel, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    )
                }
            }
        }
        return scene
    }
}