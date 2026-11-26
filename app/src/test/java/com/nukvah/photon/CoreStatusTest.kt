package com.nukvah.photon

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreStatusTest {
    @Test
    fun loadedSummaryNamesVersionAndArch() {
        val status = CoreStatus.Loaded("0.1.0", "aarch64", "android", "pong")
        assertEquals("photon-core 0.1.0 loaded (android, aarch64)", status.summary())
    }

    @Test
    fun failedSummaryGivesReason() {
        assertEquals("photon-core failed to load: missing .so", CoreStatus.Failed("missing .so").summary())
    }
}

class BenchmarkFormatTest {
    @Test
    fun formatsOneLine() {
        val line = formatBenchmark("Standard", 1224u, 816u, 1.2f, 102400uL, 8.25, "abc123")
        assertEquals("Standard 1224x816 d=1.2: 100.0 KB in 8.3 ms [abc123]", line)
    }
}
