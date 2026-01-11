package de.mm20.launcher2.feed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.text.Collator

class FeedService(
    private val context: Context,
) {
    fun createFeedInstance(
        activity: AppCompatActivity,
        feedProvider: String,
    ): FeedConnection {
        val intent = Intent(
            "com.android.launcher3.WINDOW_OVERLAY",
        ).also {
            it.`package` = feedProvider
            it.data = "app://${context.packageName}:${Process.myUid()}".toUri()
                .buildUpon()
                .appendQueryParameter("v", "7")
                .appendQueryParameter("cv", "9")
                .build()
        }
        return FeedConnectionImpl(
            activity,
            intent.setPackage(feedProvider),
        )
    }

    fun getAvailableFeedProviders(): List<FeedProvider> {
        val services = context.packageManager.queryIntentServices(
            Intent(
                "com.android.launcher3.WINDOW_OVERLAY",
            ).also {
                it.data = "app://${context.packageName}:${Process.myUid()}".toUri()
                    .buildUpon()
                    .appendQueryParameter("v", "7")
                    .appendQueryParameter("cv", "9")
                    .build()
            }, 0
        )

        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        return services.map {
            FeedProvider(
                it.loadLabel(context.packageManager).toString(),
                it.serviceInfo.packageName,
            )
        }
            .filter {
                it.packageName !in BlocklistedPackages
            }
            .sortedWith { el1, el2 ->
                collator.compare(el1.label, el2.label)
            }
    }

    companion object {
        /**
         * These overlay providers are known to not work with third party apps; block them to avoid
         * confusion.
         */
        private val BlocklistedPackages = if (BuildConfig.DEBUG) {
            emptySet()
        } else {
            setOf(
                "com.google.android.googlequicksearchbox",
                "app.lawnchair.lawnfeed",
            )
        }
    }
}