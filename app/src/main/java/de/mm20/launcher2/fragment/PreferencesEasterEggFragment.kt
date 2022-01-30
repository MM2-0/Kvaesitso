package de.mm20.launcher2.fragment

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.ToggleButton
import androidx.fragment.app.Fragment
import de.mm20.launcher2.R

class PreferencesEasterEggFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_easteregg, null, false)
        val root = view.findViewById<FrameLayout>(R.id.easterEggRoot)
        val toggle = view.findViewById<ToggleButton>(R.id.magicModeToggle)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(requireContext(), if (isChecked) R.string.easter_egg_activated else R.string.easter_egg_deactivated, Toast.LENGTH_SHORT).show()
        }
        return view
    }

}
