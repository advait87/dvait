package com.dvait.base

import android.app.Application
import com.dvait.base.data.db.ObjectBoxStore
import com.dvait.base.data.repository.CapturedTextRepository
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.engine.EmbeddingEngine
import com.dvait.base.engine.QueryEngine
import com.dvait.base.service.DataCollector
import com.dvait.base.util.FileLogger
import com.dvait.base.service.ServiceWatchdogWorker
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DvaitApp : Application() {

    internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var settingsDataStore: SettingsDataStore
        private set
    lateinit var repository: CapturedTextRepository
        private set
    lateinit var embeddingEngine: EmbeddingEngine
        private set
    lateinit var queryEngine: QueryEngine
        private set
    lateinit var groqEngine: com.dvait.base.engine.GroqEngine
        private set
    lateinit var conversationRepository: com.dvait.base.data.repository.ConversationRepository
        private set
    lateinit var dataCollector: DataCollector
        private set

    override fun onCreate() {
        super.onCreate()

        FileLogger.init(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            FileLogger.e("DvaitApp", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        ObjectBoxStore.init(this)

        settingsDataStore = SettingsDataStore(this)
        repository = CapturedTextRepository()
        embeddingEngine = EmbeddingEngine(this, settingsDataStore, appScope)

        groqEngine = com.dvait.base.engine.GroqEngine()
        conversationRepository = com.dvait.base.data.repository.ConversationRepository(ObjectBoxStore.store)
        queryEngine = QueryEngine(embeddingEngine, groqEngine, repository, settingsDataStore)
        dataCollector = DataCollector(repository, embeddingEngine, settingsDataStore)

        setupWatchdog()
        logProcessExitReasons()
    }

    private fun setupWatchdog() {
        val watchdogRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES)
            // .setInitialDelay(1, TimeUnit.MINUTES) // Can't go below 15 min for periodic, but can delay initial
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ServiceWatchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            watchdogRequest
        )
    }

    private fun logProcessExitReasons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // Query for both main process and accessibility process
            val exitReasons = am.getHistoricalProcessExitReasons(null, 0, 0)

            if (exitReasons.isNotEmpty()) {
                FileLogger.i("DvaitApp", "--- Historical Process Exit Reasons ---")
                exitReasons.take(5).forEach { reason ->
                    val reasonStr = when (reason.reason) {
                        ActivityManager.RunningAppProcessInfo.REASON_UNKNOWN -> "UNKNOWN"
                        1 -> "EXIT_SELF"
                        2 -> "SIGNALED"
                        3 -> "CRASH"
                        4 -> "CRASH_NATIVE"
                        5 -> "ANR"
                        6 -> "USER_REQUESTED"
                        7 -> "USER_STOPPED"
                        8 -> "DEPENDENCY_DIED"
                        9 -> "OTHER_KILLED_BY_APP"
                        10 -> "USER_STOPPED"
                        11 -> "LOW_MEMORY"
                        12 -> "FREEZER"
                        13 -> "DEVICE_CONFIG_CHANGED"
                        14 -> "PACKAGE_STATE_CHANGE"
                        15 -> "PACKAGE_UPDATED"
                        else -> "CODE_${reason.reason}"
                    }
                    FileLogger.i("DvaitApp", "Process: ${reason.processName}, Reason: $reasonStr ($reason), Description: ${reason.description}, Importance: ${reason.importance}")
                }
                FileLogger.i("DdvaitApp", "---------------------------------------")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        dataCollector.destroy()
        embeddingEngine.close()
    }
}
