package eu.kanade.tachiyomi.data.updater

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.util.system.notificationManager
import kotlinx.coroutines.coroutineScope
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AutoUpdaterJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                return@coroutineScope Result.failure()
            }
            val result = AppUpdateChecker.getUpdateChecker().checkForUpdate()
            if (result is AppUpdateResult.NewUpdate<*> && !AppUpdateService.isRunning()) {
                AppUpdateNotifier(context).cancel()
                AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                AppUpdateService.start(context, result.release.downloadLink, false)
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    fun foregrounded(): Boolean {
        val appProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == IMPORTANCE_FOREGROUND || appProcessInfo.importance == IMPORTANCE_VISIBLE
    }

    fun NotificationCompat.Builder.update(block: NotificationCompat.Builder.() -> Unit) {
        block()
        context.notificationManager.notify(Notifications.ID_UPDATER, build())
    }

    companion object {
        private const val TAG = "AutoUpdateRunner"
        const val ALWAYS = 0
        const val ONLY_ON_UNMETERED = 1
        const val NEVER = 2

        fun setupTask(context: Context) {
            val preferences = Injekt.get<PreferencesHelper>()
            val restrictions = preferences.appShouldAutoUpdate()
            val wifiRestriction = if (restrictions == ONLY_ON_UNMETERED) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(wifiRestriction)
                .setRequiresDeviceIdle(true)
                .build()

            val request = OneTimeWorkRequestBuilder<AutoUpdaterJob>()
                .addTag(TAG)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelTask(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        }
    }
}
