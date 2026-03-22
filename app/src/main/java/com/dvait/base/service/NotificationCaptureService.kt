package com.dvait.base.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dvait.base.DvaitApp
import com.dvait.base.data.model.CapturedText
import com.dvait.base.util.FileLogger

class NotificationCaptureService : NotificationListenerService() {

    private val dataCollector: DataCollector?
        get() = (application as? DvaitApp)?.dataCollector

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val content = buildString {
            if (title.isNotBlank()) append("$title: ")
            if (bigText.isNotBlank()) append(bigText)
            else if (text.isNotBlank()) append(text)
        }.trim()

        if (content.isNotEmpty()) {
            FileLogger.i("NotificationCapture", "Captured notification from $packageName")
            dataCollector?.onTextCaptured(
                text = content,
                sourceApp = packageName,
                sourceType = CapturedText.SourceType.NOTIFICATION
            )
        }
    }

    override fun onDestroy() {
        FileLogger.i("NotificationCapture", "Service onDestroy")
        super.onDestroy()
    }
}
