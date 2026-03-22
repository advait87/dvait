package com.dvait.base.data.repository

import androidx.test.platform.app.InstrumentationRegistry
import com.dvait.base.data.db.ObjectBoxStore
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

class VectorSearchTest {

    private lateinit var store: BoxStore
    private lateinit var repository: CapturedTextRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ObjectBoxStore.init(context)
        store = ObjectBoxStore.store
        repository = CapturedTextRepository()
    }

    @After
    fun tearDown() {
        if (::store.isInitialized && !store.isClosed) {
            store.close()
            store.deleteAllFiles()
        }
    }

    @Test
    fun testVectorSortingOrder() = runBlocking {
        // Create a fake 1024-dimensional query vector.
        val queryVector = FloatArray(1024) { 0f }
        queryVector[0] = 1.0f // Let's say this implies "exact match" along dimension 0

        // Create a perfectly matching context
        val exactMatchVector = FloatArray(1024) { 0f }
        exactMatchVector[0] = 1.0f

        // Create a somewhat matching context
        val partialMatchVector = FloatArray(1024) { 0f }
        partialMatchVector[0] = 0.5f

        // Create a completely unrelated context
        val poorMatchVector = FloatArray(1024) { 0f }
        poorMatchVector[10] = 1.0f

        // Insert out of order using suspending function
        repository.insert("Partial", "App", "Type", partialMatchVector)
        repository.insert("Poor", "App", "Type", poorMatchVector)
        repository.insert("Exact", "App", "Type", exactMatchVector)

        // Perform search
        val results = repository.searchByVector(queryVector, limit = 3)

        // Validate exactly 3 results are returned
        assertEquals(3, results.size)

        // Validate priority/sorting logic. The most relevant must be at index 0.
        // For VectorDistanceType.COSINE, exact match should have distance ~ 0.0
        assertEquals("Exact", results[0].text)
        assertEquals("Partial", results[1].text)
        assertEquals("Poor", results[2].text)

        // Validate the raw scores if needed
        val box = store.boxFor(CapturedText::class.java)
        val query = box.query(CapturedText_.embedding.nearestNeighbors(queryVector, 3)).build()
        val resultsWithScores = query.findWithScores()
        query.close()

        val sorted = resultsWithScores.sortedBy { it.score }

        assertTrue("Exact match should have lower score than partial", sorted[0].score < sorted[1].score)
        assertTrue("Partial match should have lower score than poor", sorted[1].score < sorted[2].score)
    }
}
