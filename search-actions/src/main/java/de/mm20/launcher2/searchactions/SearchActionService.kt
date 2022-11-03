package de.mm20.launcher2.searchactions

import android.content.Context
import de.mm20.launcher2.preferences.Settings.SearchActionSettings
import de.mm20.launcher2.searchactions.actions.SearchAction
import de.mm20.launcher2.searchactions.builders.CallActionBuilder
import de.mm20.launcher2.searchactions.builders.CreateContactActionBuilder
import de.mm20.launcher2.searchactions.builders.EmailActionBuilder
import de.mm20.launcher2.searchactions.builders.MessageActionBuilder
import de.mm20.launcher2.searchactions.builders.OpenUrlActionBuilder
import de.mm20.launcher2.searchactions.builders.ScheduleEventActionBuilder
import de.mm20.launcher2.searchactions.builders.SearchActionBuilder
import de.mm20.launcher2.searchactions.builders.SetAlarmActionBuilder
import de.mm20.launcher2.searchactions.builders.TimerActionBuilder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface SearchActionService {
    fun search(settings: SearchActionSettings, query: String): Flow<ImmutableList<SearchAction>>
}

internal class SearchActionServiceImpl(
    private val context: Context,
    private val repository: SearchActionRepository,
    private val textClassifier: TextClassifier,
) : SearchActionService {
    override fun search(settings: SearchActionSettings, query: String): Flow<ImmutableList<SearchAction>> = flow {
        if (query.isBlank()) {
            emit(persistentListOf())
            return@flow
        }

        val builders = mutableListOf<SearchActionBuilder>()

        if (settings.call) builders.add(CallActionBuilder)
        if (settings.message) builders.add(MessageActionBuilder)
        if (settings.contact) builders.add(CreateContactActionBuilder)
        if (settings.email) builders.add(EmailActionBuilder)
        if (settings.openUrl) builders.add(OpenUrlActionBuilder)
        if (settings.scheduleEvent) builders.add(ScheduleEventActionBuilder)
        if (settings.setAlarm) builders.add(SetAlarmActionBuilder)
        if (settings.startTimer) builders.add(TimerActionBuilder)

        val classificationResult = textClassifier.classify(context, query)


        emit(builders.mapNotNull { it.build(context, classificationResult) }.toImmutableList())
    }

}