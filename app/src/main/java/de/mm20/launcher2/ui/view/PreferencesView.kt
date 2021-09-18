package de.mm20.launcher2.ui.view

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import de.mm20.launcher2.R
import de.mm20.launcher2.ktx.castTo

class PreferencesView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val fragment = PreferenceViewFragment()

    init {
        if (id == View.NO_ID) id = View.generateViewId()
        context.castTo<AppCompatActivity>().supportFragmentManager.beginTransaction()
                .add(id, fragment)
                .commit()
        attrs?.let {
            val ta = context.theme.obtainStyledAttributes(it, R.styleable.PreferencesView, 0, defStyleAttr)
            val preferenceScreen = ta.getResourceId(R.styleable.SearchGridView_columnCount, 0)
            setPreferenceResource(preferenceScreen)
            ta.recycle()
        }
    }

    fun setPreferenceResource(@XmlRes resId: Int) {
        if (resId == 0) return
        fragment.setPreferenceResource(resId)
    }

    fun <T : Preference> findPreference(key: String): T? {
        return fragment.findPreference<T>(key)
    }

    var onPreferencesReady: (() -> Unit)? = null
        set(value) {
            field = value
            fragment.onPreferencesReady = value
        }

}

class PreferenceViewFragment : PreferenceFragmentCompat() {

    private var isInitialized = false
    var onPreferencesReady: (() -> Unit)? = null

    @XmlRes
    private var preferenceResource = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        if (preferenceResource != 0) addPreferencesFromResource(preferenceResource)
        isInitialized = true
        onPreferencesReady?.invoke()
        onPreferencesReady = null
    }

    internal fun setPreferenceResource(@XmlRes resId: Int) {
        preferenceResource = resId
        if (isInitialized && resId != 0) {
            addPreferencesFromResource(resId)
        }
    }

}