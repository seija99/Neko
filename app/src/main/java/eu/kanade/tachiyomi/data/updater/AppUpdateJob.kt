package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.coroutineScope
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class AppUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            val preferences: PreferencesHelper by injectLazy()
            val result = AppUpdateChecker.getUpdateChecker().checkForUpdate()
            if (result is AppUpdateResult.NewUpdate<*>) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    preferences.appShouldAutoUpdate() != AutoUpdaterJob.NEVER
                ) {
                    AutoUpdaterJob.setupTask(context)
                }
                AppUpdateNotifier(context).promptUpdate(
                    result.release.info,
                    result.release.downloadLink,
                    result.release.releaseLink
                )
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    fun NotificationCompat.Builder.update(block: NotificationCompat.Builder.() -> Unit) {
        block()
        context.notificationManager.notify(Notifications.ID_UPDATER, build())
    }

    companion object {
        private const val TAG = "UpdateChecker"

        fun doWorkNow(context: Context) {

            val request = OneTimeWorkRequestBuilder<AppUpdateJob>()
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun setupTask(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AppUpdateJob>(
                2,
                TimeUnit.DAYS,
                3,
                TimeUnit.HOURS
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.REPLACE, request)
        }

        fun cancelTask(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}
