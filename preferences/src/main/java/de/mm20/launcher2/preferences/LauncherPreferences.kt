package de.mm20.launcher2.preferences

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager


class LauncherPreferences(val context: Application, version: Int = 3) {
    internal val preferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }


    var version by IntPreference("preferences_version", default = 1)

    init {
        val oldVersion = this.version
        if ( version >= 2 && oldVersion <= 1) {
            val appStartAnim = preferences.getInt("app_start_anim", 0)
            preferences.edit {
                remove("app_start_anim")
                putString("app_start_anim", appStartAnim.toString())
            }
        }
        if (version >= 3 && oldVersion <= 2) {
            val translucentCards = preferences.getBoolean("translucent_cards", false)
            val theme = preferences.getString("theme", "2")

            preferences.edit {
                remove("translucent_cards")
                putInt("card_opacity", if (translucentCards) 0xCC else 0xFF)
                if (theme == "4") {
                    putString("theme", "1")
                    putString("card_background", "2")
                }
            }
        }
        if (version >= 4 && oldVersion <= 3) {
            val appStartAnim = preferences.getString("app_start_anim", "5")
            if (appStartAnim == "0" || appStartAnim == "1")
            preferences.edit {
                putString("app_start_anim", "2")
            }
        }
        this.version = version
    }


    var appStartAnim by EnumPreference("app_start_anim", default = AppStartAnimation.M)

    var cardOpacity by IntPreference("card_opacity", default = 0xFF)
    var cardStrokeWidth by IntPreference("card_stroke_width", default = 0)
    var cardRadius by IntPreference("card_radius", default = 8)

    var easterEggEnabled by BooleanPreference("easter_egg", default = false)

    companion object {
        lateinit var instance: LauncherPreferences
        fun initialize(app: Application) {
            instance = LauncherPreferences(app)
        }
    }
}

enum class AppStartAnimation(override val value: String): PreferenceEnum {
    DEFAULT("5"),
    M("2"),
    FADE("3"),
    SLIDE_BOTTOM("4")
}