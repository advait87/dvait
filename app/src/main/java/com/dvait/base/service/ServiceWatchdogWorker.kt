package com.dvait.base.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dvait.base.MainActivity
import com.dvait.base.R
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.util.FileLogger
import kotlinx.coroutines.flow.first

class ServiceWatchdogWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        FileLogger.i("Watchdog", "Watchdog checking service status...")

        // The service should always be running if it's enabled in Android settings.

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isAccessibilityServiceEnabled(context, ScreenCaptureService::class.java)) {
            FileLogger.i("Watchdog", "Service is enabled. Clearing any alert notifications.")
            notificationManager.cancel(1002)
            return Result.success()
        } else {
            FileLogger.w("Watchdog", "Service is NOT enabled! Showing notification.")
            showServiceDeadNotification(notificationManager)
            return Result.success()
        }
    }

    private fun showServiceDeadNotification(notificationManager: NotificationManager) {
        val channelId = "dvait_base_watchdog_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "dvait Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when the dvait service stops running"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        settingsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("dvait Service Stopped")
            .setContentText("The accessibility service is disabled. Tap to re-enable it.")
            .setSmallIcon(R.drawable.dvait_logo_orange)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (enabledService in enabledServices) {
            val serviceInfo = enabledService.resolveInfo.serviceInfo
            if (serviceInfo.packageName == context.packageName && serviceInfo.name == service.name) {
                return true
            }
        }
        return false
    }
}
