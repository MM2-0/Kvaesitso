package de.mm20.launcher2.fragment

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import de.mm20.launcher2.R

class PreferencesLicenseFragment : Fragment() {
    var library: Int = 0

    @SuppressLint("ResourceType")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_license, null, false)
        val license = resources.obtainTypedArray(library)
        (activity as AppCompatActivity).supportActionBar?.title = license.getString(0)
        val icon = view.findViewById<ImageView>(R.id.icon)
        val iconUri = license.getString(2)
        if (iconUri == null) icon.visibility = View.GONE
        else {
            Glide
                    .with(icon)
                    .load(iconUri)
                    .into(icon)
            icon.visibility = View.VISIBLE
        }
        val url = license.getString(6)
        val website = view.findViewById<TextView>(R.id.website)
        website.setOnClickListener {
            val intent = CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(CustomTabColorSchemeParams
                            .Builder()
                            .setToolbarColor(-0x9f8275)
                            .build())
                    .setShowTitle(true)
                    .build()
            intent.launchUrl(activity as AppCompatActivity, Uri.parse(url))
        }
        val description = view.findViewById<TextView>(R.id.description)
        description.text = license.getString(1)
        val licenseTitle = view.findViewById<TextView>(R.id.licenseTitle)
        val licenseText = view.findViewById<TextView>(R.id.licenseText)
        val licenseCopyright = view.findViewById<TextView>(R.id.licenseCopyright)
        licenseTitle.text = license.getString(3)
        licenseCopyright.text = license.getString(4)
        licenseText.text = resources.openRawResource(license.getResourceId(5, 0)).reader().readText()
        license.recycle()
        return view
    }
}
