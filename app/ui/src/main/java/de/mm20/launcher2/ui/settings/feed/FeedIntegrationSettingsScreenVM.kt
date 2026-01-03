package de.mm20.launcher2.ui.settings.feed

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.feed.FeedProvider
import de.mm20.launcher2.feed.FeedService
import de.mm20.launcher2.preferences.feed.FeedSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.Collator

class FeedIntegrationSettingsScreenVM : ViewModel(), KoinComponent {

    private val feedService: FeedService by inject()
    private val feedSettings: FeedSettings by inject()

    val providerPackage = feedSettings.providerPackage
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(0), 1)

    fun setProviderPackage(providerPackage: String?) {
        feedSettings.setProviderPackage(providerPackage)
    }

    fun getFeedProviders(context: Context): List<FeedProvider> {
        return feedService.getAvailableFeedProviders()
    }
}