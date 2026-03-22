package com.dvait.base.data.repository

import com.dvait.base.data.db.ObjectBoxStore
import com.dvait.base.data.model.CapturedText
import com.dvait.base.data.model.CapturedText_
import io.objectbox.Box
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import io.objectbox.kotlin.toFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.dvait.base.engine.EmbeddingEngine
import com.dvait.base.util.FileLogger

class CapturedTextRepository(
    private val store: io.objectbox.BoxStore = ObjectBoxStore.store
) {
    data class ScoredCapturedText(
        val capturedText: CapturedText,
        val score: Double
    )

    private val box: Box<CapturedText> = store.boxFor(CapturedText::class.java)

    suspend fun insert(text: String, sourceApp: String, sourceType: String, embedding: FloatArray) =
        withContext(Dispatchers.IO) {
            val capturedText = CapturedText(
                text = text,
                sourceApp = sourceApp,
                sourceType = sourceType,
                timestamp = System.currentTimeMillis()
            )
            if (embedding.size == 768) {
                capturedText.embeddingVyakyarth = embedding
            } else {
                capturedText.embedding = embedding
            }
            box.put(capturedText)
        }

    suspend fun searchByVector(queryEmbedding: FloatArray, limit: Int = 10): List<ScoredCapturedText> =
        withContext(Dispatchers.IO) {
            val property = if (queryEmbedding.size == 768) CapturedText_.embeddingVyakyarth else CapturedText_.embedding
            val query = box.query(
                property.nearestNeighbors(queryEmbedding, limit)
            ).build()

            
            // For COSINE distance, lower score = more similar. We explicitly sort to guarantee order.
            val resultsWithScores = query.findWithScores()
            query.close()
            resultsWithScores.sortedBy { it.score }.map { 
                ScoredCapturedText(it.get(), it.score)
            }
        }

    suspend fun deleteOlderThan(timestamp: Long) = withContext(Dispatchers.IO) {
        val query = box.query(
            CapturedText_.timestamp.less(timestamp)
        ).build()
        query.remove()
        query.close()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        box.removeAll()
    }

    suspend fun count(): Long = withContext(Dispatchers.IO) {
        box.count()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getRecentFlow(limit: Int = 100): Flow<List<CapturedText>> {
        val query = box.query()
            .order(CapturedText_.timestamp, io.objectbox.query.QueryBuilder.DESCENDING)
            .build()
        return query.subscribe().toFlow()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun countFlow(): Flow<Long> {
        return box.query().build().subscribe().toFlow().map { box.count() }
    }

    suspend fun reindexAll(embeddingEngine: EmbeddingEngine, onProgress: (Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val total = box.count().toInt()
        FileLogger.i("Repository", "Starting re-indexing of $total items...")
        
        val pageSize = 10
        var processed = 0
        val allIds = box.query().build().use { it.findIds() }
        
        for (i in allIds.indices step pageSize) {
            val end = if (i + pageSize > allIds.size) allIds.size else i + pageSize
            val batchIds = allIds.sliceArray(i until end)
            
            val pageItems = box.get(batchIds) ?: emptyList()
            if (pageItems.isEmpty()) continue
            
            // Process the page items in-place to save memory allocation
            pageItems.forEach { item ->
                try {
                    val embedding = embeddingEngine.embed(item.text)
                    if (embedding.size == 768) {
                        item.embeddingVyakyarth = embedding
                    } else {
                        item.embedding = embedding
                    }
                } catch (e: Exception) {
                    FileLogger.e("Repository", "Failed to embed item ${item.id} during re-indexing: ${e.message}")
                }
            }
            
            // Delete old entities and insert as new to bypass HNSW "update not supported" error
            box.remove(pageItems)
            pageItems.forEach { it.id = 0 }
            box.put(pageItems)
            
            processed += pageItems.size
            
            // Only update progress every 100 items to reduce Binder pressure/UI lag
            if (processed % 100 == 0 || processed == total) {
                onProgress(processed, total)
            }

            store.closeThreadResources()
        }

        FileLogger.i("Repository", "Re-indexing complete.")
    }

    suspend fun deduplicate(embeddingEngine: EmbeddingEngine, onProgress: (Int, Int, Int) -> Unit) = withContext(Dispatchers.IO) {
        val total = box.count().toInt()
        if (total == 0) return@withContext
        FileLogger.i("Repository", "Starting deduplication of $total items...")

        val allIds = box.query()
            .order(CapturedText_.timestamp) // oldest first
            .build().use { it.findIds() }

        val removedIds = mutableSetOf<Long>()
        var processed = 0
        var duplicatesRemoved = 0

        for (id in allIds) {
            if (id in removedIds) {
                processed++
            } else {
                val item = box.get(id)
                if (item == null) {
                    processed++
                } else {
                    val embedding = if (item.embedding.isNotEmpty()) item.embedding else item.embeddingVyakyarth

                    if (embedding == null || embedding.isEmpty()) {
                        processed++
                    } else {
                        // Search for near-duplicates
                        val matches = try {
                            searchByVector(embedding, limit = 10)
                        } catch (e: Exception) {
                            null
                        }

                        if (matches != null) {
                            for (match in matches) {
                                if (match.capturedText.id != id && match.capturedText.id !in removedIds) {
                                    val similarity = 1f - match.score.toFloat()
                                    if (similarity > 0.95f) {
                                        removedIds.add(match.capturedText.id)
                                        duplicatesRemoved++
                                    }
                                }
                            }
                        }

                        processed++
                        if (processed % 50 == 0 || processed == total) {
                            onProgress(processed, total, duplicatesRemoved)
                        }
                    }
                }
            }
        }

        // Batch remove all duplicates
        if (removedIds.isNotEmpty()) {
            for (rid in removedIds) {
                box.remove(rid)
            }
            FileLogger.i("Repository", "Deduplication complete: removed $duplicatesRemoved duplicates out of $total entries.")
        } else {
            FileLogger.i("Repository", "Deduplication complete: no duplicates found.")
        }
    }

    suspend fun getRecentItems(sinceTimestamp: Long): List<CapturedText> = withContext(Dispatchers.IO) {
        box.query()
            .greater(CapturedText_.timestamp, sinceTimestamp)
            .build()
            .use { it.find() }
    }
}
