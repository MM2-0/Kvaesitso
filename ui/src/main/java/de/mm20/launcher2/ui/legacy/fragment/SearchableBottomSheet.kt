package de.mm20.launcher2.ui.legacy.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.mm20.launcher2.search.data.Searchable
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.legacy.searchable.SearchableView

class SearchableBottomSheet(val searchable: Searchable) : BottomSheetDialogFragment() {

    private var view: SearchableView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.TransparentBottomSheetTheme)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        view = SearchableView(requireContext(), SearchableView.REPRESENTATION_FULL)
        view?.searchable = searchable
        view?.onBack = {
            dismiss()
        }
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        view?.searchable = null
        view = null
    }
}