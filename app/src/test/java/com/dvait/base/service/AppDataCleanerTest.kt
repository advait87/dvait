package com.dvait.base.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDataCleanerTest {

    @Test
    fun `test cleanText returns original text`() {
        val input = "Some text that would have been cleaned"
        val actual = AppDataCleaner.cleanText(input, "com.example.app")
        assertEquals(input, actual)
    }

    @Test
    fun `test non-matching package is not cleaned`() {
        val input = "Random text"
        val actual = AppDataCleaner.cleanText(input, "com.example.app")
        assertEquals(input, actual)
    }
}
