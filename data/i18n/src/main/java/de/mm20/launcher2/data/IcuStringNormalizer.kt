package de.mm20.launcher2.data

import android.content.Context
import android.icu.text.Transliterator
import android.icu.util.ULocale
import androidx.annotation.RequiresApi
import de.mm20.launcher2.crashreporter.CrashReporter
import de.mm20.launcher2.preferences.ui.LocaleSettings
import de.mm20.launcher2.search.StringNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.apache.commons.lang3.StringUtils
import java.util.Locale

@RequiresApi(29)
internal class IcuStringNormalizer(
    private val context: Context,
    localeSettings: LocaleSettings,
) : StringNormalizer {

    override val id: String
        get() = transliteratorId.value ?: "null"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val transliteratorId = localeSettings.transliterator
        .map {
            getTransliteratorId(it)
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun normalize(input: String): String {
        val id = transliteratorId.value

        val transliterator = try {
            Transliterator.getInstance(id)
        } catch (e: IllegalArgumentException) {
            CrashReporter.logException(e)
            null
        }

        if (transliterator ==  null) {
            return StringUtils.stripAccents(input.lowercase(Locale.getDefault()))
                .replace("æ", "ae")
                .replace("œ", "oe")
                .replace("ß", "ss")
        }

        return transliterator.transliterate(input).lowercase()
    }

    private fun getTransliteratorId(preferenceValue: String?): String {
        val id = preferenceValue ?: return DisabledTransliteratorId

        if (id.isNotBlank()) {
            return "$id;$BaseTransliteratorId"
        }

        val locales = context.resources.configuration.locales

        if (locales.isEmpty) {
            return BaseTransliteratorId
        }

        val scripts = mutableSetOf<String>()
        val languages = mutableSetOf<String>()

        val availableIds = Transliterator.getAvailableIDs().toList()

        for (i in 0..<locales.size()) {
            val locale = locales.get(i)
            val ulocale = ULocale.addLikelySubtags(ULocale.forLocale(locale))

            val lng = ulocale.language
            val scr = ulocale.script

            if (!languages.contains(lng)) {
                val filter = "${lng}-${lng}_Latn"

                val id = availableIds.find { it.startsWith(filter) }

                if (id != null) {
                    return "$id;$BaseTransliteratorId"
                }

                languages.add(lng)
            }

            if (!scripts.contains(ulocale.script)) {
                val filter = "${scr}-Latn"

                val id = availableIds.find { it.startsWith(filter) }

                if (id != null) {
                    return "$id;$BaseTransliteratorId"
                }
                scripts.add(ulocale.script)
            }
        }
        return BaseTransliteratorId
    }

    companion object {
        /**
         * Transliterator that is used when transliteration is disabled
         */
        private const val DisabledTransliteratorId = "Latin-ASCII"

        /**
         * Transliterator that is used when no script or language is specified
         */
        private const val BaseTransliteratorId = "Any-Latin;Latin-ASCII"
    }
}