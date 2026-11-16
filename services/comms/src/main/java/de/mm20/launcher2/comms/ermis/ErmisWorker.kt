package de.mm20.launcher2.comms.ermis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import de.mm20.launcher2.comms.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class ErmisWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        val repo=ErmisRepository.get(applicationContext)
        val before=repo.state.first { !it.loading }.messages.filter { it.folder == "INBOX" }.map { it.accountId to it.uid }.toSet()
        return try {
            repo.sync()
            val fresh=repo.state.value.messages.filter { it.folder == "INBOX" && !it.read && (it.accountId to it.uid) !in before }
            if(fresh.isNotEmpty()) {
                val manager=applicationContext.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("ermis-mail",applicationContext.getString(de.mm20.launcher2.i18n.R.string.ermis_new_mail),NotificationManager.IMPORTANCE_DEFAULT))
                val intent=Intent().setClassName(applicationContext.packageName,"de.mm20.launcher2.ui.settings.SettingsActivity").putExtra(de.mm20.launcher2.applock.SettingsDeepLinkContract.EXTRA_ROUTE,de.mm20.launcher2.applock.SettingsDeepLinkContract.ROUTE_ERMIS)
                val pending=PendingIntent.getActivity(applicationContext,8231,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification=NotificationCompat.Builder(applicationContext,"ermis-mail").let { de.mm20.launcher2.search.VirtualAppAppearance.decorate(it, applicationContext, "telos_ermis_app") }
                    .setContentTitle(applicationContext.getString(de.mm20.launcher2.i18n.R.string.ermis_new_mail)).setContentText(applicationContext.getString(de.mm20.launcher2.i18n.R.string.ermis_new_mail_count,fresh.size))
                    .setContentIntent(pending).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
                try { manager.notify(8231,notification) } catch (_: SecurityException) { /* Notification permission is optional. */ }
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun schedule(context: Context) { WorkManager.getInstance(context).enqueueUniquePeriodicWork("ermis-mail",ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ErmisWorker>(30,TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build()) }
        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork("ermis-mail") }
    }
}
