package com.dvait.base.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GroqEngine {

    companion object {
        private const val TAG = "GroqEngine"
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
    }

    suspend fun generate(
        apiKey: String,
        model: String,
        question: String,
        context: String,
        history: List<Pair<String, String>>
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext "Error: Groq API Key is missing. Please configure it in Settings."
        }

        try {
            val url = URL(GROQ_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            val messages = JSONArray()
            
            // System prompt
            messages.put(JSONObject().apply {
                put("role", "system")
                put("content", "You are dvait, a helpful and natural personal assistant. " +
                    "Your goal is to be a seamless extension of the user's digital memory.\n\n" +
                    "GUIDELINES:\n" +
                    "1. Respond naturally, like a real human assistant. If the user says 'hi' or greets you, respond with a friendly greeting.\n" +
                    "2. You have access to the user's recent device activity (screen text and notifications) as context below.\n" +
                    "3. Use this context to provide accurate answers about what the user has seen or heard on their device.\n" +
                    "4. Integrate the context naturally. Do NOT always start with 'Based on the context...' or explicitly mention the technical source if not needed.\n" +
                    "5. If the context doesn't contain the answer, politely say you couldn't find that in their recent activity.\n\n" +
                    "Context from device:\n$context")
            })

            // History
            history.forEach { (role, content) ->
                messages.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "assistant")
                    put("content", content)
                })
            }

            // Current question
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            })

            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.7)
            }

            connection.outputStream.use { os ->
                os.write(body.toString().toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val errorStream = connection.errorStream
                val error = errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Groq error $responseCode: $error")
                return@withContext "Groq API error ($responseCode): $error"
            }

            val response = BufferedReader(InputStreamReader(connection.inputStream)).readText()
            val json = JSONObject(response)
            
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                choices.getJSONObject(0).getJSONObject("message").getString("content")
            } else {
                "Error: No response from Groq API."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Groq Engine error", e)
            "Error calling Groq: ${e.message}"
        }
    }
}
