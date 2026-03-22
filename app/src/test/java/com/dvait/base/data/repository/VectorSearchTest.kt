package com.dvait.base.data.repository

import com.dvait.base.data.model.CapturedText
import com.dvait.base.data.model.CapturedText_
import com.dvait.base.data.model.MyObjectBox
import io.objectbox.BoxStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class VectorSearchTest {

    private lateinit var store: BoxStore
    private lateinit var repository: CapturedTextRepository
    private val testDbDir = File("test-db")

    @Before
    fun setUp() {
        BoxStore.deleteAllFiles(testDbDir)
        store = MyObjectBox.builder()
            .directory(testDbDir)
            .build()
        repository = CapturedTextRepository(store)
    }

    @After
    fun tearDown() {
        if (::store.isInitialized && !store.isClosed) {
            store.close()
            BoxStore.deleteAllFiles(testDbDir)
        }
    }

    @Test
    fun testVectorSortingOrder() = runBlocking {
        // Create a fake 384-dimensional query vector.
        val queryVector = FloatArray(384) { 0f }
        queryVector[0] = 1.0f // Dimension 0

        // Create a perfectly matching context (identical direction)
        val exactMatchVector = FloatArray(384) { 0f }
        exactMatchVector[0] = 1.0f

        // Create a somewhat matching context (45 degree angle)
        val partialMatchVector = FloatArray(384) { 0f }
        partialMatchVector[0] = 1.0f
        partialMatchVector[1] = 1.0f // Points diagonally

        // Create a completely unrelated context (90 degree angle)
        val poorMatchVector = FloatArray(384) { 0f }
        poorMatchVector[2] = 1.0f // Points along dimension 2

        repository.insert("Partial", "App", "Type", partialMatchVector)
        repository.insert("Poor", "App", "Type", poorMatchVector)
        repository.insert("Exact", "App", "Type", exactMatchVector)

        val results = repository.searchByVector(queryVector, limit = 3)
        assertEquals(3, results.size)

        assertEquals("Exact", results[0].capturedText.text)
        assertEquals("Partial", results[1].capturedText.text)
        assertEquals("Poor", results[2].capturedText.text)

        val box = store.boxFor(CapturedText::class.java)
        val query = box.query(CapturedText_.embedding.nearestNeighbors(queryVector, 3)).build()
        val resultsWithScores = query.findWithScores()
        query.close()

        val sorted = resultsWithScores.sortedBy { it.score }
        assertTrue("Exact match should have lower score than partial", sorted[0].score < sorted[1].score)
        assertTrue("Partial match should have lower score than poor", sorted[1].score < sorted[2].score)
    }
}
