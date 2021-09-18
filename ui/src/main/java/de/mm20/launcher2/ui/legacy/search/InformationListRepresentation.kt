package de.mm20.launcher2.ui.legacy.search

import android.widget.TextView
import androidx.transition.Scene
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.data.InformationText
import de.mm20.launcher2.ui.legacy.searchable.SearchableView
import de.mm20.launcher2.ui.legacy.view.InnerCardView

class InformationListRepresentation: Representation {
    override fun getScene(rootView: SearchableView, searchable: Searchable, previousRepresentation: Int?): Scene {
        val informationText = searchable as InformationText
        val scene = Scene.getSceneForLayout(rootView, R.layout.view_information_list, rootView.context)
        scene.setEnterAction {
            rootView.findViewById<TextView>(R.id.informationText).text = informationText.label
            if (informationText.clickAction != null) {
                rootView.findViewById<InnerCardView>(R.id.card).setOnClickListener {
                    informationText.clickAction.invoke()
                }
            }
        }
        return scene
    }
}