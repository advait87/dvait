package com.dvait.base.engine

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * A basic WordPiece tokenizer for BERT-based models.
 */
class BertTokenizer(context: Context, vocabAssetPath: String) {
    private val vocab = mutableMapOf<String, Int>()
    private val inverseVocab = mutableMapOf<Int, String>()

    init {
        context.assets.open(vocabAssetPath).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var index = 0
                reader.forEachLine { line ->
                    val token = line.trim()
                    if (token.isNotEmpty()) {
                        vocab[token] = index
                        inverseVocab[index] = token
                        index++
                    }
                }
            }
        }
    }

    /**
     * Tokenizes the input text and returns a list of token IDs.
     * Optimized to avoid massive string allocations (no split/regex/lowercase on full text).
     */
    fun tokenize(text: String, maxLen: Int = 128): IntArray {
        val result = mutableListOf<Int>()
        
        // Add [CLS] token
        result.add(vocab["[CLS]"] ?: 101)

        var i = 0
        val n = text.length
        
        while (i < n && result.size < maxLen - 1) {
            // Skip whitespace
            while (i < n && text[i].isWhitespace()) {
                i++
            }
            if (i >= n) break
            
            // Find word boundary
            val start = i
            while (i < n && !text[i].isWhitespace()) {
                i++
            }
            
            // Extract and tokenize word
            val word = text.substring(start, i).lowercase()
            val subTokens = wordPieceTokenize(word)
            for (subToken in subTokens) {
                if (result.size >= maxLen - 1) break
                val id = vocab[subToken] ?: vocab["[UNK]"] ?: 100
                result.add(id)
            }
        }

        // Add [SEP] token
        if (result.size < maxLen) {
            result.add(vocab["[SEP]"] ?: 102)
        }

        // Pad with 0
        while (result.size < maxLen) {
            result.add(0)
        }

        return result.toIntArray()
    }

    private fun wordPieceTokenize(word: String): List<String> {
        val tokens = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var curToken: String? = null
            while (start < end) {
                var subStr = word.substring(start, end)
                if (start > 0) subStr = "##$subStr"
                
                if (vocab.containsKey(subStr)) {
                    curToken = subStr
                    break
                }
                end--
            }
            
            if (curToken == null) {
                return listOf("[UNK]")
            }
            
            tokens.add(curToken)
            start = end
        }
        return tokens
    }
}
