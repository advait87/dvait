package com.dvait.base.service

import com.dvait.base.data.model.CapturedText
import com.dvait.base.data.repository.CapturedTextRepository
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.util.FileLogger
import com.dvait.base.engine.EmbeddingEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class DataCollector(
    private val repository: CapturedTextRepository,
    private val embeddingEngine: EmbeddingEngine,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "DataCollector"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val recentTexts = LinkedHashMap<String, Long>(100, 0.75f, true)
    private val DEDUP_COOLDOWN_MS = 5000L

    fun onTextCaptured(text: String, sourceApp: String, sourceType: CapturedText.SourceType) {
        if (text.isBlank() || text.length < 10) return

        scope.launch {
            try {
                if (!shouldCapture(sourceApp, sourceType)) return@launch

                val cleanedText = AppDataCleaner.cleanText(text, sourceApp)
                if (cleanedText.isBlank() || cleanedText.length < 10) return@launch

                if (isDuplicateByTimeAndText(cleanedText, sourceApp, DEDUP_COOLDOWN_MS)) return@launch

                // Compute embedding once — reuse for both dedup check and insert
                val embedding = embeddingEngine.embed(cleanedText)

                if (isSemanticDuplicate(embedding)) {
                    FileLogger.d(TAG, "Discarded semantic duplicate")
                    return@launch
                }

                repository.insert(cleanedText, sourceApp, sourceType.name, embedding)
            } catch (e: Exception) {
                FileLogger.e(TAG, "Error processing captured text", e)
            }
        }
    }

    private suspend fun isSemanticDuplicate(embedding: FloatArray): Boolean {
        val matches = repository.searchByVector(embedding, limit = 1)
        if (matches.isEmpty()) return false

        val bestMatch = matches.first()
        val similarity = 1f - bestMatch.score.toFloat()

        // Tier 1: Near-identical content — always discard regardless of age
        if (similarity > 0.98f) {
            FileLogger.d(TAG, "Discarding exact duplicate (sim=${"%.4f".format(similarity)})")
            return true
        }
        // Tier 2: Very similar content within a 10-minute window
        if (similarity > 0.92f && System.currentTimeMillis() - bestMatch.capturedText.timestamp < 600_000L) {
            FileLogger.d(TAG, "Discarding near duplicate (sim=${"%.4f".format(similarity)}, recent)")
            return true
        }
        return false
    }

    private suspend fun shouldCapture(
        sourceApp: String,
        sourceType: CapturedText.SourceType
    ): Boolean {
        if (sourceType == CapturedText.SourceType.NOTIFICATION) {
            if (!settingsDataStore.captureNotifications.first()) return false
        }

        return sourceApp in settingsDataStore.whitelistedApps.first()
    }

    private fun isDuplicateByTimeAndText(text: String, sourceApp: String, cooldown: Long): Boolean {
        val key = "$sourceApp:${text.hashCode()}"
        val now = System.currentTimeMillis()
        synchronized(recentTexts) {
            val lastSeen = recentTexts[key]
            if (lastSeen != null && now - lastSeen < cooldown) return true
            recentTexts[key] = now
            if (recentTexts.size > 500) {
                val iter = recentTexts.entries.iterator()
                while (iter.hasNext() && recentTexts.size > 400) {
                    val entry = iter.next()
                    if (now - entry.value > DEDUP_COOLDOWN_MS * 10) iter.remove()
                    else break
                }
            }
            return false
        }
    }

    fun destroy() { scope.cancel() }
}
