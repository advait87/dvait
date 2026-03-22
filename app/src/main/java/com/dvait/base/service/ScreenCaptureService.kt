package com.dvait.base.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.dvait.base.util.FileLogger
import com.dvait.base.DvaitApp
import com.dvait.base.data.model.CapturedText
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dvait.base.MainActivity
import com.dvait.base.R
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ScreenCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenCapture"
        private const val CHANNEL_ID = "dvait_base_service_channel"
        private const val NOTIFICATION_ID = 1001
    }

    private val dataCollector: DataCollector?
        get() = (application as? DvaitApp)?.dataCollector

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentPackageName: String = ""

    override fun onCreate() {
        super.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FileLogger.i(TAG, "Service connected")
        startAsForeground()
    }

    private fun startAsForeground() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "dvait Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "dvait background service"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("dvait is Active")
            .setContentText("Capturing information in the background")
            .setSmallIcon(R.drawable.dvait_logo_orange)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        currentPackageName = packageName

        // Accessibility capture is now ALWAYS active when the service is running.

        val eventType = event.eventType
        scope.launch(Dispatchers.Default) {
            when (eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    try {
                        val rootNode = rootInActiveWindow ?: return@launch
                        val sb = StringBuilder()
                        extractText(rootNode, sb)
                        rootNode.recycle()

                        val text = sb.toString().trim()
                        if (text.isNotEmpty()) {
                            dataCollector?.onTextCaptured(
                                text = text,
                                sourceApp = packageName,
                                sourceType = CapturedText.SourceType.SCREEN
                            )
                        }
                    } catch (e: Exception) {
                        FileLogger.e(TAG, "Error processing accessibility event", e)
                    }
                }
            }
        }
    }

    private fun extractText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int = 0) {
        if (depth > 20) return

        node.text?.let { text ->
            if (text.isNotBlank()) sb.append(text).append(" ")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractText(child, sb, depth + 1)
            child.recycle()
        }
    }

    override fun onInterrupt() {
        FileLogger.w(TAG, "Service onInterrupt called - Accessibility service interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        FileLogger.w(TAG, "Service onUnbind called - Accessibility service unbound by system")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        FileLogger.i(TAG, "Service onDestroy called")
        scope.cancel()
        super.onDestroy()
    }
}
