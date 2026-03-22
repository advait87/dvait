package com.dvait.base.engine

import com.dvait.base.util.FileLogger

import com.dvait.base.data.model.CapturedText
import com.dvait.base.data.repository.CapturedTextRepository
import com.dvait.base.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

sealed class GenerationEvent {
    data class Token(val text: String, val totalTokens: Int) : GenerationEvent()
    data class Complete(val tokensPerSecond: Float, val totalTokens: Int) : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
}

data class GenerationResult(
    val fullText: String,
    val tokensPerSecond: Float,
    val tokenCount: Int
)

class QueryEngine(
    private val embeddingEngine: EmbeddingEngine,
    private val groqEngine: GroqEngine,
    private val repository: CapturedTextRepository,
    private val settingsDataStore: SettingsDataStore
) {
    private val TAG = "QueryEngine"

    data class DebugInfo(
        val matches: List<CapturedTextRepository.ScoredCapturedText>,
        val contextString: String,
        val fullPrompt: String
    )

    suspend fun getDebugInfo(userQuestion: String): DebugInfo {
        // Increase limit for debug view to 100
        val (matches, contextStr) = getRelevantContext(userQuestion, limit = 100)

        val prompt = "You are dvait, a helpful and natural personal assistant. " +
            "Your goal is to be a seamless extension of the user's digital memory. " +
            "Integrate the provided device context naturally into your responses.\n\n" +
            "Context from device:\n${contextStr}\n\n" +
            "Question: ${userQuestion}"

        return DebugInfo(matches, contextStr, prompt)
    }

    suspend fun getRelevantContext(userQuestion: String, limit: Int = 20, onStatus: (String) -> Unit = {}): Pair<List<CapturedTextRepository.ScoredCapturedText>, String> {
        onStatus("Vectorizing query...")
        FileLogger.i(TAG, "Vectorizing query...")
        val queryEmbedding = embeddingEngine.embed(userQuestion)
        FileLogger.i(TAG, "Query embedding: ${queryEmbedding.contentToString()}")

        onStatus("Performing vector search...")
        val results = repository.searchByVector(queryEmbedding, limit = limit)

        // Filter by similarity > 0.4 (similarity = 1 - score)
        val filteredResults = results.filter { (1f - it.score) > 0.4f }

        onStatus("Fetching relevant data (${filteredResults.size} matches)...")
        FileLogger.i(TAG, "Found ${filteredResults.size} relevant context items.")

        // Format a single context string for the LLM prompt (only top 20 for actual prompt)
        val promptLimit = 20
        val promptResults = filteredResults.take(promptLimit)

        var contextStr = if (promptResults.isEmpty()) {
            "No captured data available yet or no matches found above similarity threshold."
        } else {
            promptResults.mapIndexed { index, item ->
                val time = java.text.SimpleDateFormat(
                    "MMM dd, HH:mm", java.util.Locale.getDefault()
                ).format(java.util.Date(item.capturedText.timestamp))
                "${index + 1}. [${item.capturedText.sourceType} from ${item.capturedText.sourceApp} at $time] Sim: ${"%.4f".format(1f - item.score)}\n${item.capturedText.text}"
            }.joinToString("\n\n")
        }

        return Pair(filteredResults, contextStr)
    }


    suspend fun queryStream(
        userQuestion: String,
        onStatus: (String) -> Unit = {}
    ): Flow<GenerationEvent>? {
        // Local model streaming removed. Only Groq is supported currently for this flow.
        return null
    }

    data class QueryResponse(
        val answer: String,
        val fullMessagePrompt: String,
        val debugContext: String,
        val followUpQuestion: String? = null,
        val followUpOptions: List<String> = emptyList(),
        val contextSearch: String? = null,
        val needsFollowUp: Boolean = false,
        // Stash these for re-query after follow-up
        val originalQuestion: String? = null,
        val originalContext: String? = null,
        val originalEndpoint: String? = null,
        val originalHistory: List<Pair<String, String>>? = null,
        val additionalContext: String? = null
    )

    suspend fun query(
        history: List<Pair<String, String>>,
        images: List<String>? = null,
        mode: com.dvait.base.ui.chat.InferenceMode = com.dvait.base.ui.chat.InferenceMode.AUTO,
        onStatus: (String) -> Unit = {}
    ): QueryResponse {
        if (history.isEmpty()) return QueryResponse("No question provided.", "", "")

        val lastUserMessage = history.last { it.first == "user" }.second

        // Fetch context based ONLY on the last question to keep it relevant
        val (_, context) = getRelevantContext(lastUserMessage, onStatus = onStatus)

        // Order: System Prompt -> Context -> History -> Question
        val systemPrompt = "You are dvait, a helpful and natural personal assistant. " +
            "Your goal is to be a seamless extension of the user's digital memory. " +
            "Respond naturally, handle greetings warmly, and integrate the provided device context " +
            "without being repetitive or overtly technical."

        val contextSection = "Context from device:\n${context}"

        // Truncate PREVIOUS history (history minus the last user message) to keep it within ~1000 words
        val previousHistory = history.dropLast(1)
        val truncatedPreviousHistory = truncateHistory(previousHistory, 1000)
        FileLogger.i(TAG, "Truncated previous history to ${truncatedPreviousHistory.size} messages.")

        // Construct the full history for the engine
        val fullMessageHistory = mutableListOf("system" to systemPrompt)
        fullMessageHistory.add("system" to contextSection)
        fullMessageHistory.addAll(truncatedPreviousHistory)
        fullMessageHistory.add("user" to lastUserMessage)

        // Single source of truth for the raw prompt string (used for debug view and localhost inference)
        val rawPromptString = fullMessageHistory.joinToString("\n\n") { "${it.first.uppercase()}: ${it.second}" }

        onStatus("Generating response...")

        val apiKey = settingsDataStore.groqApiKey.first()
        val model = settingsDataStore.groqModel.first()
        val answer = groqEngine.generate(apiKey, model, lastUserMessage, context, truncatedPreviousHistory)
        return QueryResponse(
            answer = answer,
            fullMessagePrompt = rawPromptString,
            debugContext = context
        )
    }

    /**
     * Re-query after the user answers a follow-up question.
     * Sends the follow-up answer + any additional context from context_search.
     */
    suspend fun queryWithFollowUp(
        originalQuestion: String,
        originalContext: String,
        endpoint: String,
        history: List<Pair<String, String>>,
        followUpAnswer: String?,
        additionalContext: String?,
        onStatus: (String) -> Unit = {}
    ): QueryResponse {
        return QueryResponse(
            answer = "Not implemented",
            fullMessagePrompt = "Not implemented",
            debugContext = originalContext
        )
    }



    private fun truncateHistory(history: List<Pair<String, String>>, maxWords: Int): List<Pair<String, String>> {
        var totalWords = 0
        val result = mutableListOf<Pair<String, String>>()

        // Iterate from newest to oldest
        for (i in history.indices.reversed()) {
            val msg = history[i]
            // Simple word count by splitting whitespace
            val wordCount = msg.second.split(Regex("\\s+")).filter { it.isNotBlank() }.size

            if (totalWords + wordCount <= maxWords) {
                result.add(0, msg)
                totalWords += wordCount
            } else if (result.isEmpty()) {
                // If even the last message is too long, we keep it but it will likely be truncated by the engine/provider
                // or we could partially truncate it here. For safety, let's keep it as is if it's the only one.
                result.add(msg)
                break
            } else {
                break
            }
        }
        return result
    }
}
