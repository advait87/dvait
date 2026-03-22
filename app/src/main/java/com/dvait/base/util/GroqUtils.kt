package com.dvait.base.util

object GroqUtils {
    const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

    val MODELS = listOf(
        "llama-3.3-70b-versatile",
        "llama-3.1-8b-instant",
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3-32b",
    )

    fun getDisplayName(model: String): String {
        return if (model == DEFAULT_MODEL) {
            "$model (recommended)"
        } else {
            model
        }
    }
}
