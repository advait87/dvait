package com.dvait.base.engine

import android.content.Context
import android.util.Log
import com.dvait.base.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import com.dvait.base.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import java.util.Collections
import com.dvait.base.util.FileUtils

/**
 * Semantic text embedding engine.
 * Supports TFLite (MiniLM) and ONNX (Vyakarth).
 */
class EmbeddingEngine(private val context: Context, private val settingsDataStore: SettingsDataStore, private val scope: CoroutineScope) {

    private var dimensions = 384
    private var modelPath = "all-MiniLM-L6-v2-quant.tflite"
    private var vocabPath = "vocab.txt"


    companion object {
        private const val TAG = "EmbeddingEngine"
        private const val MAX_SEQ_LEN = 128
    }

    private var interpreter: Interpreter? = null

    private var tokenizer: BertTokenizer? = null
    private val mutex = Mutex()

    // Pre-allocated buffers to reuse for inference to avoid GC overhead during re-indexing
    private var resultBuffer3D: Array<Array<FloatArray>>? = null
    private var resultBuffer2D: Array<FloatArray>? = null

    init {
        scope.launch {
            mutex.withLock {
                reinitialize()
            }
        }
    }

    private fun reinitialize() {
        closeInternal()
        
        dimensions = 384
        modelPath = "all-MiniLM-L6-v2-quant.tflite"
        vocabPath = "vocab.txt"

        try {
            tokenizer = BertTokenizer(context, vocabPath)
            
            val modelBuffer = loadModelFile(context, modelPath)
            val options = Interpreter.Options()
            interpreter = Interpreter(modelBuffer, options)
            Log.i("EmbeddingEngine", "Initialized TFLite Interpreter with \$modelPath (\$dimensions dims)")
            
            // Pre-allocate buffers once after model loaded
            val outputTensor = interpreter?.getOutputTensor(0) ?: throw Exception("Invalid model output")
            val outputShape = outputTensor.shape()
            if (outputShape.size == 3) {
                resultBuffer3D = Array(1) { Array(MAX_SEQ_LEN) { FloatArray(dimensions) } }
                resultBuffer2D = null
            } else if (outputShape.size == 2) {
                resultBuffer2D = Array(1) { FloatArray(dimensions) }
                resultBuffer3D = null
            }
        } catch (e: Exception) {
            Log.e("EmbeddingEngine", "Failed to initialize embedding model: \${e.message}")
        }
    }

    private fun closeInternal() {
        interpreter?.close()
        interpreter = null
        tokenizer = null
    }


    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val currentDims = dimensions
        val vector = FloatArray(currentDims)
        if (text.isBlank()) return@withContext vector

        val safeTokenizer = tokenizer ?: return@withContext vector

        try {
            mutex.withLock {
                // Pre-truncate string to avoid tokenizer memory blowup on massive entries
                val truncatedText = if (text.length > 1024) text.substring(0, 1024) else text
                
                val inputIds = safeTokenizer.tokenize(truncatedText, MAX_SEQ_LEN).map { it.toLong() }.toLongArray()
                val attentionMask = LongArray(MAX_SEQ_LEN) { if (inputIds[it] != 0L) 1L else 0L }
                val tokenTypeIds = LongArray(MAX_SEQ_LEN) { 0L }

                    val safeInterpreter = interpreter ?: return@withContext vector
                    
                    // Dynamic input handling based on model requirements
                    val inputCount = safeInterpreter.inputTensorCount
                    val inputs = arrayOfNulls<Any>(inputCount)
                    
                    // Convert back to IntArray for TFLite
                    val intInputIds = inputIds.map { it.toInt() }.toIntArray()
                    val intAttentionMask = attentionMask.map { it.toInt() }.toIntArray()
                    val intTokenTypeIds = tokenTypeIds.map { it.toInt() }.toIntArray()

                    if (inputCount >= 1) inputs[0] = arrayOf(intInputIds)
                    if (inputCount >= 2) inputs[1] = arrayOf(intAttentionMask)
                    if (inputCount >= 3) inputs[2] = arrayOf(intTokenTypeIds)

                    val outputTensor = safeInterpreter.getOutputTensor(0)
                    val outputShape = outputTensor.shape() // e.g. [1, 128, 384] or [1, 384]
                    
                    if (outputShape.size == 3) {
                        val buffer = resultBuffer3D ?: Array(1) { Array(MAX_SEQ_LEN) { FloatArray(currentDims) } }
                        safeInterpreter.runForMultipleInputsOutputs(inputs.filterNotNull().toTypedArray(), mapOf(0 to buffer))
                        // Mean pooling over non-padding tokens (weighted by attention mask)
                        val pooled = FloatArray(currentDims)
                        var tokenCount = 0
                        for (t in 0 until MAX_SEQ_LEN) {
                            if (intAttentionMask[t] == 1) {
                                for (d in 0 until currentDims) {
                                    pooled[d] += buffer[0][t][d]
                                }
                                tokenCount++
                            }
                        }
                        if (tokenCount > 0) {
                            for (d in 0 until currentDims) {
                                pooled[d] /= tokenCount.toFloat()
                            }
                        }
                        return@withContext normalize(pooled)
                    } else if (outputShape.size == 2) {
                        val buffer = resultBuffer2D ?: Array(1) { FloatArray(currentDims) }
                        safeInterpreter.runForMultipleInputsOutputs(inputs.filterNotNull().toTypedArray(), mapOf(0 to buffer))
                        val clsEmbedding = buffer[0].clone()
                        return@withContext normalize(clsEmbedding)
                    }
            }

        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to embed text", e)
        }

        vector
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = Math.sqrt(norm.toDouble()).toFloat()
        if (norm > 1e-9) {
            for (i in v.indices) v[i] /= norm
        }
        return v
    }

    fun close() {
        closeInternal()
    }
}
