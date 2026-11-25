package com.example

import com.example.util.formatDuration
import com.example.util.formatFileSize
import com.example.util.formatSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatters_produceExpectedOutput() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("01:05", formatDuration(65_000L))
        assertEquals("1:01:01", formatDuration(3_661_000L))
        assertEquals("1.0x", formatSpeed(1.0f))
        assertEquals("1.25x", formatSpeed(1.25f))
        assertEquals("1.5 MB", formatFileSize(1_572_864L))
    }
}
